package com.omnisolve.overlay.cache

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.omnisolve.overlay.R
import com.omnisolve.overlay.api.ExtractedFormQuestion
import com.omnisolve.overlay.api.GoogleFormSolvedItem
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

data class CachedFormQuestionEntry(
    val canonicalStem: String,
    val winningOptionTexts: List<String>,
    val isMultiSelect: Boolean,
    val isShortAnswer: Boolean,
    val explanation: String
)

data class RemappedFormAnswer(
    val answerToken: String,
    val shortAnswer: String,
    val explanation: String,
    val wasOptionOrderRemapped: Boolean
)

data class SharedFormLookupResult(
    val formHash: String,
    val matchedByQuestionNumber: Map<Int, RemappedFormAnswer>,
    val remappedOptionCount: Int,
    val existingRevision: Int
)

internal data class SharedFormRemoteRecord(
    val formHash: String,
    val formTitle: String,
    val questionCount: Int,
    val revision: Int,
    val entriesJson: String
)

internal interface SharedFormRemoteStore {
    suspend fun get(formHash: String): SharedFormRemoteRecord?
    suspend fun upsert(
        formHash: String,
        formTitle: String,
        questionCount: Int,
        revision: Int,
        entriesJson: String,
        isUpdate: Boolean
    )
}

/**
 * Class-wide, jumble-proof Google Forms server cache backed by Firestore `/sharedFormSolutions/{formHash}`.
 *
 * Key properties:
 * 1. Canonical Form ID hashing: `forms.gle/...` redirects, `docs.google.com/forms/d/e/<ID>/viewform?usp=...`,
 *    and `/u/1/d/e/<ID>/viewform` all resolve to the exact same 64-char SHA-256 `formHash`.
 * 2. Jumble-proof matching: Stores canonical question stems + exact winning option text(s) rather than
 *    hardcoded letters (A/B/C/D). When a classmate opens the same link with shuffled question order OR
 *    shuffled option order, Prism dynamically maps the cached winning option text(s) onto that student's
 *    live option indices to compute their exact personal letter(s).
 * 3. Self-healing: When any user taps "Re-solve" on a question (in the Forms screen or inside the live
 *    Form Browser), the freshly verified answer merges into `/sharedFormSolutions/{formHash}` with a
 *    monotonic `revision` increment so future classmates receive the corrected answer automatically.
 */
object SharedFormCacheRepository {

    private const val TAG = "SharedFormCache"
    private const val COLLECTION_SHARED_FORMS = "sharedFormSolutions"
    private const val LOOKUP_TIMEOUT_MS = 1_800L
    private const val WRITE_TIMEOUT_MS = 3_500L
    private const val MAX_ENTRIES_JSON_LEN = 88_000
    private const val MAX_QUESTIONS = 200
    private const val MAX_REVISION = 10_000

    @Volatile
    internal var disableNetworkForTest: Boolean = false

    @Volatile
    internal var remoteStoreOverrideForTest: SharedFormRemoteStore? = null

    private val leadingQuestionNumRegex = Regex("""^(?:q(?:uestion)?\s*)?\d+\s*[.):\-]\s*""", RegexOption.IGNORE_CASE)
    private val leadingOptionPrefixRegex = Regex("""^\(?[a-f1-6]\s*[.):\-]\s+""", RegexOption.IGNORE_CASE)
    private val trailingPointsRegex = Regex("""\s*\(?\d+\s*(?:point|points|pt|pts|mark|marks)\)?\s*$""", RegexOption.IGNORE_CASE)
    private val nonWordRegex = Regex("""[^\p{L}\p{N}\s]+""")
    private val multiSpaceRegex = Regex("""\s+""")
    private val googleFormIdRegex = Regex(
        """docs\.google\.com/forms/(?:u/\d+/)?d/(?:e/)?([a-zA-Z0-9_\-]{12,})""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Computes a deterministic 64-char SHA-256 form hash from a resolved Google Forms or Microsoft Forms URL.
     * Returns `null` for non-URL inputs (such as `"Pasted Form Text"` or `"Scanned Quiz Photo"`).
     */
    fun computeCanonicalFormHash(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank() || !trimmed.startsWith("http", ignoreCase = true)) return null

        val gMatch = googleFormIdRegex.find(trimmed)
        if (gMatch != null) {
            val formId = gMatch.groupValues[1].trim()
            if (formId.length >= 12) {
                return sha256Hex("gform_id_v1:$formId")
            }
        }

        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val host = parsed.host?.lowercase(Locale.ROOT)?.trim().orEmpty()
        if (host.isBlank()) return null

        val isSupportedFormHost = host.contains("docs.google.com") ||
            host.contains("forms.gle") ||
            host.contains("forms.office.com")
        if (!isSupportedFormHost) return null

        val cleanPath = parsed.path.orEmpty().trimEnd('/')
        val officeId = if (host.contains("forms.office.com")) {
            parsed.getQueryParameter("id")?.trim().orEmpty()
        } else {
            ""
        }
        val canonicalMaterial = if (officeId.isNotBlank()) {
            "oform_id_v1:$host$cleanPath?id=$officeId"
        } else {
            "form_url_v1:$host$cleanPath"
        }
        return sha256Hex(canonicalMaterial)
    }

    fun canonicalizeStem(raw: String): String {
        val stripped = raw
            .replace("\u00A0", " ")
            .replace("required question", " ", ignoreCase = true)
            .replace("*", " ")
            .trim()
            .replace(leadingQuestionNumRegex, "")
            .replace(trailingPointsRegex, "")
            .lowercase(Locale.ROOT)
            .replace(nonWordRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()
        return stripped
    }

    fun canonicalizeOption(raw: String): String {
        return raw
            .replace("\u00A0", " ")
            .trim()
            .replace(leadingOptionPrefixRegex, "")
            .lowercase(Locale.ROOT)
            .replace(nonWordRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()
    }

    /**
     * Looks up a shared class-wide solution for [resolvedUrl] and remaps every matching question
     * onto [liveQuestions] (accounting for shuffled question order AND shuffled option order).
     */
    suspend fun lookupAndRemap(
        context: Context,
        resolvedUrl: String,
        liveQuestions: List<ExtractedFormQuestion>
    ): SharedFormLookupResult? = withContext(Dispatchers.IO) {
        if (liveQuestions.isEmpty()) return@withContext null
        val formHash = computeCanonicalFormHash(resolvedUrl) ?: return@withContext null
        val record = fetchRecord(context, formHash) ?: return@withContext null
        val cachedEntries = deserializeEntries(record.entriesJson)
        if (cachedEntries.isEmpty()) return@withContext null

        val byExactStem = linkedMapOf<String, CachedFormQuestionEntry>()
        cachedEntries.forEach { entry ->
            if (entry.canonicalStem.isNotBlank()) {
                byExactStem[entry.canonicalStem] = entry
            }
        }

        val matched = linkedMapOf<Int, RemappedFormAnswer>()
        var remappedCount = 0

        for (question in liveQuestions) {
            val liveStem = canonicalizeStem(question.questionText)
            if (liveStem.isBlank()) continue

            val cachedEntry = byExactStem[liveStem]
                ?: findBestFuzzyStemMatch(liveStem, cachedEntries)
                ?: continue

            val remapped = remapEntryToLiveQuestion(cachedEntry, question) ?: continue
            matched[question.number] = remapped
            if (remapped.wasOptionOrderRemapped) {
                remappedCount++
            }
        }

        SharedFormLookupResult(
            formHash = formHash,
            matchedByQuestionNumber = matched,
            remappedOptionCount = remappedCount,
            existingRevision = record.revision
        )
    }

    /**
     * Merges newly solved or single-question re-verified items into `/sharedFormSolutions/{formHash}`.
     * Unverified (`?`) answers are never written to the shared server cache.
     */
    suspend fun upsertSolvedForm(
        context: Context,
        resolvedUrl: String,
        formTitle: String,
        solvedItems: List<GoogleFormSolvedItem>,
        isSingleQuestionCorrection: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val formHash = computeCanonicalFormHash(resolvedUrl) ?: return@withContext false
        val validEntries = solvedItems.mapNotNull { itemToCachedEntry(it) }
        if (validEntries.isEmpty()) return@withContext false

        val existingRecord = fetchRecord(context, formHash)
        val mergedMap = linkedMapOf<String, CachedFormQuestionEntry>()

        if (existingRecord != null) {
            deserializeEntries(existingRecord.entriesJson).forEach { entry ->
                if (entry.canonicalStem.isNotBlank()) {
                    mergedMap[entry.canonicalStem] = entry
                }
            }
        }

        var changed = existingRecord == null
        for (entry in validEntries) {
            val prev = mergedMap[entry.canonicalStem]
            if (prev == null || isSingleQuestionCorrection || prev.winningOptionTexts != entry.winningOptionTexts) {
                mergedMap[entry.canonicalStem] = entry
                changed = true
            }
        }

        if (!changed && existingRecord != null) {
            return@withContext true
        }

        val boundedEntries = mergedMap.values.take(MAX_QUESTIONS)
        val serialized = serializeEntriesBounded(boundedEntries)
        if (serialized.length < 2 || serialized.length > MAX_ENTRIES_JSON_LEN) {
            return@withContext false
        }

        val nextRevision = if (existingRecord == null) {
            1
        } else {
            (existingRecord.revision + 1).coerceIn(1, MAX_REVISION)
        }
        val cleanTitle = formTitle.trim().ifBlank { existingRecord?.formTitle.orEmpty().ifBlank { "Google Form Quiz" } }
            .take(200)

        writeRecord(
            context = context,
            formHash = formHash,
            formTitle = cleanTitle,
            questionCount = boundedEntries.size.coerceIn(1, MAX_QUESTIONS),
            revision = nextRevision,
            entriesJson = serialized,
            isUpdate = existingRecord != null
        )
    }

    internal fun itemToCachedEntry(item: GoogleFormSolvedItem): CachedFormQuestionEntry? {
        val badge = item.answerLetter.trim()
        if (badge.isBlank() || badge == "?" || badge.equals("UNSOLVED", ignoreCase = true)) {
            return null
        }
        val stem = canonicalizeStem(item.questionText)
        if (stem.isBlank()) return null

        val isShort = item.options.isEmpty()
        val isMulti = !isShort && badge.contains(",")

        val winningTexts: List<String> = if (isShort) {
            val valText = item.shortAnswer.trim().ifBlank { badge }.take(220)
            if (valText.isBlank()) return null else listOf(valText)
        } else {
            val letterTokens = badge.split(",")
                .map { it.trim().uppercase(Locale.ROOT) }
                .filter { it.length == 1 && it[0] in 'A'..'Z' }
            val resolvedFromIndices = letterTokens.mapNotNull { tok ->
                val idx = tok[0] - 'A'
                item.options.getOrNull(idx)?.trim()?.takeIf { it.isNotBlank() }?.take(220)
            }
            if (resolvedFromIndices.isNotEmpty()) {
                resolvedFromIndices.distinct()
            } else {
                val fallbackShort = item.shortAnswer.trim().take(220)
                if (fallbackShort.isBlank()) return null else listOf(fallbackShort)
            }
        }

        return CachedFormQuestionEntry(
            canonicalStem = stem.take(360),
            winningOptionTexts = winningTexts,
            isMultiSelect = isMulti,
            isShortAnswer = isShort,
            explanation = item.explanation.trim().take(220)
        )
    }

    internal fun remapEntryToLiveQuestion(
        entry: CachedFormQuestionEntry,
        question: ExtractedFormQuestion
    ): RemappedFormAnswer? {
        if (entry.winningOptionTexts.isEmpty()) return null

        // Short-answer / numeric question with no selectable options
        if (question.options.isEmpty()) {
            val answerVal = entry.winningOptionTexts.first().trim()
            if (answerVal.isBlank()) return null
            return RemappedFormAnswer(
                answerToken = answerVal.take(28),
                shortAnswer = answerVal,
                explanation = entry.explanation,
                wasOptionOrderRemapped = false
            )
        }

        // MCQ / Checkbox / Dropdown / Scale / Grid: match EVERY winning option text onto live options
        val normalizedLiveOptions = question.options.mapIndexed { idx, opt ->
            idx to canonicalizeOption(opt)
        }

        val matchedIndices = mutableListOf<Int>()
        for (winText in entry.winningOptionTexts) {
            val winNorm = canonicalizeOption(winText)
            if (winNorm.isBlank()) return null

            // Pass 1: exact canonical match
            val exactMatches = normalizedLiveOptions.filter { it.second == winNorm }
            val matchedIdx = when {
                exactMatches.size == 1 -> exactMatches.first().first
                exactMatches.size > 1 -> exactMatches.first().first
                winNorm.length >= 4 -> {
                    // Pass 2: unique containment match (guards against teacher trimming minor suffix)
                    val containMatches = normalizedLiveOptions.filter { (_, liveNorm) ->
                        liveNorm.length >= 4 && (liveNorm.contains(winNorm) || winNorm.contains(liveNorm))
                    }
                    if (containMatches.size == 1) containMatches.first().first else -1
                }
                else -> -1
            }
            if (matchedIdx !in question.options.indices) {
                // Option text no longer exists on this form (e.g., teacher edited options) -> safe cache miss
                return null
            }
            if (matchedIdx !in matchedIndices) {
                matchedIndices.add(matchedIdx)
            }
        }

        if (matchedIndices.isEmpty()) return null
        val sortedIndices = matchedIndices.sorted()
        val letters = sortedIndices.map { ('A' + it).toString() }
        val answerToken = letters.joinToString(", ")
        val matchedShortTexts = sortedIndices.mapNotNull { question.options.getOrNull(it)?.trim() }
            .joinToString(", ")

        return RemappedFormAnswer(
            answerToken = answerToken,
            shortAnswer = matchedShortTexts,
            explanation = entry.explanation,
            wasOptionOrderRemapped = true
        )
    }

    private fun findBestFuzzyStemMatch(
        liveStem: String,
        entries: List<CachedFormQuestionEntry>
    ): CachedFormQuestionEntry? {
        val liveTokens = liveStem.split(" ").filter { it.length >= 2 }.toSet()
        if (liveTokens.size < 4) return null

        var bestEntry: CachedFormQuestionEntry? = null
        var bestScore = 0.0
        for (entry in entries) {
            val candTokens = entry.canonicalStem.split(" ").filter { it.length >= 2 }.toSet()
            if (candTokens.size < 4) continue
            val intersection = liveTokens.intersect(candTokens).size
            val union = liveTokens.union(candTokens).size
            if (union == 0) continue
            val jaccard = intersection.toDouble() / union.toDouble()
            if (jaccard >= 0.85 && jaccard > bestScore) {
                bestScore = jaccard
                bestEntry = entry
            }
        }
        return bestEntry
    }

    internal fun serializeEntriesBounded(entries: List<CachedFormQuestionEntry>): String {
        val arr = JSONArray()
        var approxBytes = 2
        for (entry in entries) {
            val obj = JSONObject().apply {
                put("k", entry.canonicalStem)
                put("a", JSONArray(entry.winningOptionTexts))
                put("m", entry.isMultiSelect)
                put("s", entry.isShortAnswer)
                put("e", entry.explanation)
            }
            val itemStr = obj.toString()
            if (approxBytes + itemStr.length + 2 > MAX_ENTRIES_JSON_LEN) break
            arr.put(obj)
            approxBytes += itemStr.length + 1
        }
        return arr.toString()
    }

    internal fun deserializeEntries(rawJson: String): List<CachedFormQuestionEntry> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(rawJson)
            val result = mutableListOf<CachedFormQuestionEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val stem = obj.optString("k", "").trim()
                val ansArr = obj.optJSONArray("a") ?: continue
                val answers = mutableListOf<String>()
                for (j in 0 until ansArr.length()) {
                    val v = ansArr.optString(j, "").trim()
                    if (v.isNotBlank()) answers.add(v)
                }
                if (stem.isNotBlank() && answers.isNotEmpty()) {
                    result.add(
                        CachedFormQuestionEntry(
                            canonicalStem = stem,
                            winningOptionTexts = answers,
                            isMultiSelect = obj.optBoolean("m", false),
                            isShortAnswer = obj.optBoolean("s", false),
                            explanation = obj.optString("e", "").trim()
                        )
                    )
                }
            }
            result
        }.getOrDefault(emptyList())
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

    private suspend fun fetchRecord(context: Context, formHash: String): SharedFormRemoteRecord? {
        remoteStoreOverrideForTest?.let { override ->
            return try {
                override.get(formHash)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                null
            }
        }
        val (db, _) = resolveFirestoreOrNull(context) ?: return null
        return try {
            withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                val snap = db.collection(COLLECTION_SHARED_FORMS).document(formHash).get().await()
                if (!snap.exists()) return@withTimeoutOrNull null
                val entriesJson = snap.getString("entriesJson")?.takeIf { it.isNotBlank() } ?: return@withTimeoutOrNull null
                val data = snap.data ?: return@withTimeoutOrNull null
                val qCount = (data["questionCount"] as? Number)?.toInt()?.coerceIn(1, MAX_QUESTIONS) ?: 1
                val rev = (data["revision"] as? Number)?.toInt()?.coerceIn(1, MAX_REVISION) ?: 1
                SharedFormRemoteRecord(
                    formHash = snap.getString("formHash") ?: formHash,
                    formTitle = snap.getString("formTitle").orEmpty(),
                    questionCount = qCount,
                    revision = rev,
                    entriesJson = entriesJson
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Shared form lookup skipped: ${e.message}")
            null
        }
    }

    private suspend fun writeRecord(
        context: Context,
        formHash: String,
        formTitle: String,
        questionCount: Int,
        revision: Int,
        entriesJson: String,
        isUpdate: Boolean
    ): Boolean {
        remoteStoreOverrideForTest?.let { override ->
            return try {
                override.upsert(formHash, formTitle, questionCount, revision, entriesJson, isUpdate)
                true
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                false
            }
        }
        val (db, uid) = resolveFirestoreOrNull(context) ?: return false
        return try {
            withTimeoutOrNull(WRITE_TIMEOUT_MS) {
                val docRef = db.collection(COLLECTION_SHARED_FORMS).document(formHash)
                if (isUpdate) {
                    val updatePayload = hashMapOf<String, Any>(
                        "formTitle" to formTitle,
                        "updatedByUid" to uid,
                        "questionCount" to questionCount,
                        "revision" to revision,
                        "entriesJson" to entriesJson,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.update(updatePayload).await()
                } else {
                    val createPayload = hashMapOf<String, Any>(
                        "formHash" to formHash,
                        "formTitle" to formTitle,
                        "createdByUid" to uid,
                        "updatedByUid" to uid,
                        "questionCount" to questionCount,
                        "revision" to revision,
                        "entriesJson" to entriesJson,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.set(createPayload).await()
                }
                true
            } ?: false
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Shared form upsert skipped (non-fatal): ${e.message}")
            false
        }
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
}
