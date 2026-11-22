package com.omnisolve.overlay.cache

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.omnisolve.overlay.R
import com.omnisolve.overlay.data.local.PrismStudyDatabase
import com.omnisolve.overlay.data.local.SharedAnswerCacheEntity
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SubjectMode
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class CachedQuizAnswer(
    val questionHash: String,
    val answerBadge: String,
    val shortAnswer: String,
    val explanation: String,
    val confidence: Int,
    val sourceLabel: String
) {
    fun toAnswerModel(): AnswerModel = AnswerModel(
        correctChoice = answerBadge,
        shortAnswer = shortAnswer,
        explanation = explanation,
        confidence = confidence.coerceIn(70, 100),
        engineLabel = sourceLabel,
        verified = true,
        isCachedResult = true
    )
}

/**
 * Multi-Database Zero-Quota Answer Cache Manager:
 * - DB 1: Local Room SQLite (`shared_answer_cache` table — <2ms, offline, zero quota)
 * - DB 2: Shared Cloud Firestore crowd cache (`/sharedQuizCache/{questionHash}` — zero AI quota)
 * - DB 3: Optional external Edge/Worker cache URL (`PREF_KEY_EXTERNAL_CACHE_URL`) for company-wide scale
 */
object PersistentQuizCacheManager {

    private const val TAG = "PersistentQuizCache"
    const val PREF_KEY_EXTERNAL_CACHE_URL = "PREF_EXTERNAL_CACHE_URL"
    private const val REMOTE_LOOKUP_TIMEOUT_MS = 450L
    private const val REMOTE_WRITE_TIMEOUT_MS = 3_000L
    private const val MIN_VALID_CONFIDENCE = 75

    private val remoteSyncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    internal var disableNetworkForTest: Boolean = false

    private val externalCooldownUntilMs = ConcurrentHashMap<String, Long>()
    private val rejectedHashesSession = ConcurrentHashMap.newKeySet<String>()

    internal fun clearRejectedHashesForTesting() {
        rejectedHashesSession.clear()
    }

    private val externalHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1200, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .writeTimeout(1500, TimeUnit.MILLISECONDS)
            .callTimeout(2000, TimeUnit.MILLISECONDS)
            .build()
    }

    private val nonWordRegex = Regex("""[^\p{L}\p{N}\s]+""")
    private val multiSpaceRegex = Regex("""\s+""")

    private fun canonicalizeText(raw: String): String {
        return SolveResultCache.normalizeForKey(raw)
            .replace(nonWordRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()
    }

    fun computeCanonicalQuestionHash(
        questionText: String,
        options: List<String> = emptyList(),
        subject: SubjectMode = SubjectMode.GENERAL
    ): String {
        val rawStem = if (options.isEmpty()) {
            val (splitStem, _) = SolveResultCache.splitNormalizedQuestionAndOptions(questionText)
            splitStem
        } else {
            questionText
        }
        val cleanStem = canonicalizeText(rawStem)
        val normOpts = if (options.isNotEmpty()) {
            options.joinToString(" | ") { canonicalizeText(it) }
        } else {
            val (_, parsedOpts) = SolveResultCache.splitNormalizedQuestionAndOptions(questionText)
            canonicalizeText(parsedOpts)
        }
        val material = listOf(
            "qcache_v1",
            subject.id.uppercase(),
            cleanStem,
            normOpts
        ).joinToString("|")
        return sha256Hex(material)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val i = b.toInt() and 0xFF
            if (i < 16) sb.append('0')
            sb.append(i.toString(16))
        }
        return sb.toString()
    }

    private fun normalizeValidBadge(badge: String?): String {
        val clean = badge?.trim().orEmpty()
        if (clean.isBlank() || clean.length > 40) return ""
        if (clean.uppercase() in setOf("?", "...", "ERR", "RETRY", "CAP", "TXT", "KEY", "NET", "LOGIN")) {
            return ""
        }
        return clean
    }

    private fun resolveFirestoreOrNull(context: Context): Pair<FirebaseFirestore, String>? {
        if (disableNetworkForTest) return null
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) return null
            val uid = Firebase.auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return null
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
                .takeIf { it.isNotBlank() } ?: return null
            FirebaseFirestore.getInstance(dbId) to uid
        } catch (_: Throwable) {
            null
        }
    }

    private fun readExternalCacheEndpoint(context: Context): String? {
        if (disableNetworkForTest) return null
        val prefs = context.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        val raw = prefs.getString(PREF_KEY_EXTERNAL_CACHE_URL, "")?.trim().orEmpty()
        if (!raw.startsWith("https://", ignoreCase = true)) return null
        val until = externalCooldownUntilMs[raw] ?: 0L
        if (System.currentTimeMillis() < until) return null
        return raw.trimEnd('/')
    }

    suspend fun lookupAcrossDatabases(
        context: Context,
        questionText: String,
        options: List<String> = emptyList(),
        subject: SubjectMode = SubjectMode.GENERAL,
        visualHash: String = "",
        bypassCache: Boolean = false
    ): CachedQuizAnswer? = withContext(Dispatchers.IO) {
        if (bypassCache) return@withContext null
        if (questionText.isBlank() && visualHash.isBlank()) return@withContext null
        // Tier 1A: Exact visual screenshot fingerprint hit for Math/Symbol/Diagram questions (<2ms)
        if (visualHash.isNotBlank() && visualHash.trim() !in rejectedHashesSession) {
            lookupByVisualHash(context, visualHash)?.let { return@withContext it }
        }
        if (questionText.isBlank()) return@withContext null
        val hash = computeCanonicalQuestionHash(questionText, options, subject)
        if (hash in rejectedHashesSession) return@withContext null
        lookupByHashAcrossDatabases(context, hash)
    }

    /**
     * Self-healing anti-poison eviction: purges a wrong/flagged answer from local Room SQLite
     * (by both canonical questionHash and perceptual visualHash) and blocks stale crowd-cache
     * replay for this hash until a freshly verified answer overwrites it.
     */
    suspend fun evictAnswer(
        context: Context,
        questionText: String = "",
        options: List<String> = emptyList(),
        subject: SubjectMode = SubjectMode.GENERAL,
        visualHash: String = ""
    ) = withContext(Dispatchers.IO) {
        val cleanVisual = visualHash.trim()
        val hashesToEvict = mutableSetOf<String>()
        if (questionText.isNotBlank()) {
            hashesToEvict += computeCanonicalQuestionHash(questionText, options, subject)
            if (subject != SubjectMode.GENERAL) {
                hashesToEvict += computeCanonicalQuestionHash(questionText, options, SubjectMode.GENERAL)
            }
        }
        hashesToEvict.forEach { rejectedHashesSession.add(it) }
        if (cleanVisual.isNotBlank()) {
            rejectedHashesSession.add(cleanVisual)
        }
        runCatching {
            val dao = PrismStudyDatabase.getInstance(context).sharedAnswerCacheDao()
            hashesToEvict.forEach { dao.deleteByHash(it) }
            if (cleanVisual.isNotBlank()) {
                dao.deleteByVisualHash(cleanVisual)
            }
        }
    }

    suspend fun lookupByVisualHash(
        context: Context,
        visualHash: String
    ): CachedQuizAnswer? = withContext(Dispatchers.IO) {
        val cleanVisualHash = visualHash.trim()
        if (cleanVisualHash.isBlank() || cleanVisualHash in rejectedHashesSession) return@withContext null
        val dao = runCatching { PrismStudyDatabase.getInstance(context).sharedAnswerCacheDao() }.getOrNull()
        val hit = runCatching { dao?.getByVisualHash(cleanVisualHash) }.getOrNull()
        if (hit != null && normalizeValidBadge(hit.answerBadge).isNotBlank()) {
            runCatching { dao?.touchHit(hit.questionHash) }
            return@withContext CachedQuizAnswer(
                questionHash = hit.questionHash,
                answerBadge = hit.answerBadge,
                shortAnswer = hit.shortAnswer,
                explanation = hit.explanation,
                confidence = hit.confidence.coerceIn(70, 100),
                sourceLabel = "Offline Room Cache"
            )
        }
        null
    }

    suspend fun lookupByHashAcrossDatabases(
        context: Context,
        hash: String
    ): CachedQuizAnswer? = withContext(Dispatchers.IO) {
        if (hash.length != 64 || hash in rejectedHashesSession) return@withContext null
        val dao = runCatching { PrismStudyDatabase.getInstance(context).sharedAnswerCacheDao() }.getOrNull()

        // Tier 1: Local Room SQLite (<2ms, offline)
        val localHit = runCatching { dao?.getByHash(hash) }.getOrNull()
        if (localHit != null && normalizeValidBadge(localHit.answerBadge).isNotBlank()) {
            runCatching { dao?.touchHit(hash) }
            return@withContext CachedQuizAnswer(
                questionHash = hash,
                answerBadge = localHit.answerBadge,
                shortAnswer = localHit.shortAnswer,
                explanation = localHit.explanation,
                confidence = localHit.confidence.coerceIn(70, 100),
                sourceLabel = "Offline Room Cache"
            )
        }

        if (disableNetworkForTest) return@withContext null

        // Tier 2 & 3: Fast-race Shared Firestore Crowd Cache + Optional External Worker Cache (capped at 450ms)
        val remoteHit = withTimeoutOrNull(REMOTE_LOOKUP_TIMEOUT_MS) {
            coroutineScope {
                val ch = Channel<CachedQuizAnswer?>(Channel.UNLIMITED)
                val fsJob = launch { ch.trySend(lookupFirestoreCache(context, hash)) }
                val extJob = launch { ch.trySend(lookupExternalEdgeCache(context, hash)) }
                var found: CachedQuizAnswer? = null
                for (i in 0 until 2) {
                    val cand = ch.receive()
                    if (cand != null) {
                        found = cand
                        break
                    }
                }
                fsJob.cancel()
                extJob.cancel()
                ch.close()
                found
            }
        }

        if (remoteHit != null && normalizeValidBadge(remoteHit.answerBadge).isNotBlank()) {
            runCatching {
                dao?.upsert(
                    SharedAnswerCacheEntity(
                        questionHash = hash,
                        questionPreview = "",
                        answerBadge = remoteHit.answerBadge,
                        shortAnswer = remoteHit.shortAnswer,
                        explanation = remoteHit.explanation,
                        subjectId = SubjectMode.GENERAL.id,
                        confidence = remoteHit.confidence,
                        sourceLabel = remoteHit.sourceLabel,
                        hitCount = 1,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
            }
            return@withContext remoteHit
        }
        null
    }

    private suspend fun lookupFirestoreCache(context: Context, hash: String): CachedQuizAnswer? {
        val (db, _) = resolveFirestoreOrNull(context) ?: return null
        return try {
            val snap = db.collection("sharedQuizCache").document(hash).get().await()
            if (!snap.exists()) return null
            val badge = normalizeValidBadge(snap.getString("answerBadge"))
            if (badge.isBlank()) return null
            val conf = (snap.getLong("confidence") ?: 90L).toInt().coerceIn(70, 100)
            CachedQuizAnswer(
                questionHash = hash,
                answerBadge = badge,
                shortAnswer = snap.getString("shortAnswer").orEmpty().take(300),
                explanation = snap.getString("explanation").orEmpty().take(500),
                confidence = conf,
                sourceLabel = "Shared Crowd Cache"
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.d(TAG, "Shared Firestore cache miss/unavailable: ${e.message}")
            null
        }
    }

    private fun lookupExternalEdgeCache(context: Context, hash: String): CachedQuizAnswer? {
        val baseUrl = readExternalCacheEndpoint(context) ?: return null
        return try {
            val encoded = URLEncoder.encode(hash, "UTF-8")
            val sep = if (baseUrl.contains("?")) "&" else "?"
            val req = Request.Builder()
                .url("$baseUrl${sep}hash=$encoded")
                .header("Accept", "application/json")
                .build()
            externalHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    if (resp.code == 429 || resp.code >= 500) {
                        externalCooldownUntilMs[baseUrl] = System.currentTimeMillis() + 60_000L
                    }
                    return null
                }
                val raw = resp.body?.string()?.trim().orEmpty()
                if (!raw.startsWith("{")) return null
                val json = JSONObject(raw)
                val badge = normalizeValidBadge(json.optString("answerBadge", ""))
                if (badge.isBlank()) return null
                CachedQuizAnswer(
                    questionHash = hash,
                    answerBadge = badge,
                    shortAnswer = json.optString("shortAnswer", "").take(300),
                    explanation = json.optString("explanation", "").take(500),
                    confidence = json.optInt("confidence", 90).coerceIn(70, 100),
                    sourceLabel = "Edge Quiz Cache"
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun storeVerifiedAnswer(
        context: Context,
        questionText: String,
        options: List<String> = emptyList(),
        subject: SubjectMode = SubjectMode.GENERAL,
        answerBadge: String,
        shortAnswer: String,
        explanation: String,
        confidence: Int = 92,
        sourceLabel: String = "Prism AI",
        visualHash: String = "",
        allowOverwriteRejected: Boolean = false,
        awaitRemoteSync: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val cleanBadge = normalizeValidBadge(answerBadge)
        if (cleanBadge.isBlank() || (questionText.isBlank() && visualHash.isBlank())) return@withContext
        val safeConf = confidence.coerceIn(0, 100)
        if (safeConf < MIN_VALID_CONFIDENCE && !allowOverwriteRejected) return@withContext

        val appContext = context.applicationContext
        val effectiveText = questionText.ifBlank { "visual:$visualHash" }
        val hash = computeCanonicalQuestionHash(effectiveText, options, subject)
        val cleanShort = shortAnswer.trim().take(300)
        val cleanExpl = explanation.trim().take(500)
        val cleanVisualHash = visualHash.trim().take(64)

        // Tier 1: Store/Overwrite in Local Room SQLite cache atomically (<2ms, self-healing)
        runCatching {
            val dao = PrismStudyDatabase.getInstance(appContext).sharedAnswerCacheDao()
            dao.upsertAtomic(
                entity = SharedAnswerCacheEntity(
                    questionHash = hash,
                    questionPreview = questionText.trim().ifBlank { "Visual Math / Diagram Question" }.take(200),
                    answerBadge = cleanBadge,
                    shortAnswer = cleanShort,
                    explanation = cleanExpl,
                    subjectId = subject.id,
                    confidence = safeConf.coerceAtLeast(if (allowOverwriteRejected) 85 else safeConf),
                    sourceLabel = sourceLabel.take(64),
                    hitCount = 1,
                    updatedAtMs = System.currentTimeMillis(),
                    visualHash = cleanVisualHash
                ),
                replaceOtherVisualMatches = allowOverwriteRejected
            )
            dao.pruneOldEntries(500)
            // Once a freshly verified answer overwrites the old entry, lift the session block so it serves cleanly offline
            if (allowOverwriteRejected) {
                rejectedHashesSession.remove(hash)
                if (cleanVisualHash.isNotBlank()) rejectedHashesSession.remove(cleanVisualHash)
            }
        }

        if (disableNetworkForTest) return@withContext

        // Tier 2 & 3: Write to Firestore Shared Crowd Cache & External Edge Cache asynchronously
        // so user-facing answer delivery is never stalled by remote network writes.
        val performRemoteSync: suspend () -> Unit = {
            withTimeoutOrNull(REMOTE_WRITE_TIMEOUT_MS) {
                resolveFirestoreOrNull(appContext)?.let { (db, uid) ->
                    runCatching {
                        val payload = hashMapOf<String, Any>(
                            "questionHash" to hash,
                            "createdByUid" to uid,
                            "answerBadge" to cleanBadge,
                            "shortAnswer" to cleanShort,
                            "explanation" to cleanExpl,
                            "subject" to subject.id.uppercase(),
                            "confidence" to safeConf.coerceIn(70, 100),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        db.collection("sharedQuizCache").document(hash).set(payload).await()
                    }
                }

                readExternalCacheEndpoint(appContext)?.let { baseUrl ->
                    runCatching {
                        val json = JSONObject().apply {
                            put("questionHash", hash)
                            put("answerBadge", cleanBadge)
                            put("shortAnswer", cleanShort)
                            put("explanation", cleanExpl)
                            put("subject", subject.id.uppercase())
                            put("confidence", safeConf.coerceIn(70, 100))
                        }.toString()
                        val req = Request.Builder()
                            .url(baseUrl)
                            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()
                        externalHttpClient.newCall(req).execute().close()
                    }
                }
            }
        }

        if (awaitRemoteSync) {
            performRemoteSync()
        } else {
            remoteSyncScope.launch { performRemoteSync() }
        }
    }

    suspend fun lookupBatchAcrossDatabases(
        context: Context,
        questions: List<Pair<Int, Pair<String, List<String>>>>,
        subject: SubjectMode
    ): Map<Int, CachedQuizAnswer> = withContext(Dispatchers.IO) {
        if (questions.isEmpty()) return@withContext emptyMap()
        val results = ConcurrentHashMap<Int, CachedQuizAnswer>()
        withTimeoutOrNull(2_000L) {
            coroutineScope {
                questions.map { (qNum, stemAndOpts) ->
                    async {
                        val hit = lookupAcrossDatabases(
                            context = context,
                            questionText = stemAndOpts.first,
                            options = stemAndOpts.second,
                            subject = subject
                        )
                        if (hit != null) {
                            results[qNum] = hit
                        }
                    }
                }.awaitAll()
            }
        }
        results
    }

    /**
     * Automatically clears local cached MCQ entries older than 7 days.
     */
    suspend fun purgeExpiredLocalCache(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): Int = withContext(Dispatchers.IO) {
        val cutoffMs = (nowMs - 7L * 24L * 60L * 60L * 1000L).coerceAtLeast(0L)
        runCatching {
            PrismStudyDatabase.getInstance(context.applicationContext)
                .sharedAnswerCacheDao()
                .deleteOlderThan(cutoffMs)
        }.getOrDefault(0)
    }
}
