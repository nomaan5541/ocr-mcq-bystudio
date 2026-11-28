package com.omnisolve.overlay.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.omnisolve.overlay.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * Features governed by per-user UTC daily limits to protect cloud AI budgets
 * while keeping everyday student usage generous and predictable.
 *
 * Unlimited by design:
 * - Local & shared cache hits (zero cloud AI spend)
 * - Personal API Keys mode (user supplies their own key)
 * - Saved history, bookmarks, flashcards, offline practice, and themes
 */
enum class UsageLimitFeature(
    val dailyLimit: Int,
    val bonusPerReward: Int,
    val displayName: String,
    val firestoreField: String,
    val prefSuffix: String
) {
    OVERLAY_MCQ(
        dailyLimit = 20,
        bonusPerReward = 5,
        displayName = "Screen Solves (Cloud)",
        firestoreField = "overlayMcqCount",
        prefSuffix = "overlay_mcq"
    ),
    ASK_AI_TEXT(
        dailyLimit = 6,
        bonusPerReward = 3,
        displayName = "Quick Follow-Ups",
        firestoreField = "askTextCount",
        prefSuffix = "ask_text"
    ),
    ASK_AI_PHOTO(
        dailyLimit = 4,
        bonusPerReward = 2,
        displayName = "Photo Answer Keys",
        firestoreField = "askPhotoCount",
        prefSuffix = "ask_photo"
    ),
    ASK_AI_PDF(
        dailyLimit = 2,
        bonusPerReward = 1,
        displayName = "PDF Answer Keys",
        firestoreField = "askPdfCount",
        prefSuffix = "ask_pdf"
    ),
    GOOGLE_FORMS(
        dailyLimit = 4,
        bonusPerReward = 2,
        displayName = "Google Forms",
        firestoreField = "googleFormsCount",
        prefSuffix = "google_forms"
    )
}

data class FeatureUsageItem(
    val feature: UsageLimitFeature,
    val used: Int,
    val limit: Int
) {
    val remaining: Int get() = (limit - used).coerceAtLeast(0)
    val isExhausted: Boolean get() = used >= limit
}

data class DailyUsageSnapshot(
    val uid: String = "",
    val dateKeyUtc: String = "",
    val overlayMcqUsed: Int = 0,
    val askTextUsed: Int = 0,
    val askPhotoUsed: Int = 0,
    val askPdfUsed: Int = 0,
    val googleFormsUsed: Int = 0,
    val rewardClaimsToday: Int = 0
) {
    val remainingRewardClaims: Int
        get() = (UserUsageLimitManager.MAX_DAILY_REWARD_CLAIMS - rewardClaimsToday).coerceAtLeast(0)

    val canClaimReward: Boolean
        get() = remainingRewardClaims > 0

    fun bonusFor(feature: UsageLimitFeature): Int =
        rewardClaimsToday.coerceIn(0, UserUsageLimitManager.MAX_DAILY_REWARD_CLAIMS) * feature.bonusPerReward

    fun effectiveLimitFor(feature: UsageLimitFeature): Int =
        feature.dailyLimit + bonusFor(feature)

    fun usedFor(feature: UsageLimitFeature): Int = when (feature) {
        UsageLimitFeature.OVERLAY_MCQ -> overlayMcqUsed
        UsageLimitFeature.ASK_AI_TEXT -> askTextUsed
        UsageLimitFeature.ASK_AI_PHOTO -> askPhotoUsed
        UsageLimitFeature.ASK_AI_PDF -> askPdfUsed
        UsageLimitFeature.GOOGLE_FORMS -> googleFormsUsed
    }

    fun remainingFor(feature: UsageLimitFeature): Int =
        (effectiveLimitFor(feature) - usedFor(feature)).coerceAtLeast(0)

    fun items(): List<FeatureUsageItem> = UsageLimitFeature.entries.map { f ->
        FeatureUsageItem(feature = f, used = usedFor(f), limit = effectiveLimitFor(f))
    }
}

sealed class UsageLimitCheckResult {
    data class Allowed(
        val feature: UsageLimitFeature,
        val used: Int,
        val limit: Int
    ) : UsageLimitCheckResult() {
        val remaining: Int get() = (limit - used).coerceAtLeast(0)
    }

    data class LimitReached(
        val feature: UsageLimitFeature,
        val used: Int,
        val limit: Int,
        val userMessage: String,
        val isDeviceBanned: Boolean = false
    ) : UsageLimitCheckResult()
}

/**
 * Dual-layer (instant local + monotonic Cloud Firestore) per-user + per-device daily quota manager.
 *
 * Security & durability guarantees:
 * 1. Scoped per Firebase UID + UTC date (`yyyy-MM-dd`) AND merged monotonically with
 *    [DeviceAbuseGuard] so switching Google accounts on the same phone NEVER resets daily credits.
 * 2. Syncs monotonically (`max(local, cloud)`) with `/users/{uid}/dailyUsage/{dateKey}` and
 *    `/deviceGuards/{deviceHash}`.
 * 3. Backed by hardened `firestore.rules` prohibiting counter decrements, unbans, or doc deletion.
 */
object UserUsageLimitManager {

    private const val TAG = "PrismUsageLimits"
    private const val PREFS_NAME = "prism_user_daily_limits_v1"
    private const val USERS_COLLECTION = "users"
    private const val DAILY_USAGE_SUBCOLLECTION = "dailyUsage"
    private const val CLOUD_SYNC_STALE_MS = 60_000L

    /** Soft notice threshold (15/20 cloud solves) alerting users before auto-switch. */
    const val SERVER_SOFT_WARNING_THRESHOLD = 15

    /** Automatic switch threshold (20/20 cloud solves) transitioning users to unlimited OCR + Phone Gemini. */
    const val SERVER_AUTO_SWITCH_THRESHOLD = 20

    /** Maximum number of rewarded ad bonus packs a user/device can claim per UTC day. */
    const val MAX_DAILY_REWARD_CLAIMS = 5

    private const val REWARD_CLAIMS_FIELD = "rewardClaimsToday"

    fun isApproachingAutoSwitch(overlayUsed: Int, effectiveLimit: Int = SERVER_AUTO_SWITCH_THRESHOLD): Boolean =
        overlayUsed in (effectiveLimit - 5).coerceAtLeast(0) until effectiveLimit

    fun shouldAutoSwitchToPhoneGemini(overlayUsed: Int, effectiveLimit: Int = SERVER_AUTO_SWITCH_THRESHOLD): Boolean =
        overlayUsed >= effectiveLimit

    private val lock = Any()
    private val lastCloudSyncByUidDate = ConcurrentHashMap<String, Long>()

    @Volatile
    internal var clockUtcDateProviderForTest: (() -> String)? = null

    @Volatile
    internal var disableCloudSyncForTest: Boolean = false

    private val _snapshotFlow = MutableStateFlow(DailyUsageSnapshot())
    val snapshotFlow: StateFlow<DailyUsageSnapshot> = _snapshotFlow.asStateFlow()

    fun todayUtcDateKey(): String {
        clockUtcDateProviderForTest?.let { return it() }
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date())
    }

    private fun getDatabaseOrNull(context: Context): FirebaseFirestore? {
        if (disableCloudSyncForTest) return null
        return try {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id).trim()
            if (dbId.isBlank()) null else FirebaseFirestore.getInstance(dbId)
        } catch (_: Exception) {
            null
        }
    }

    private fun prefKey(uid: String, dateKey: String, feature: UsageLimitFeature): String =
        "u_${uid}_${dateKey}_${feature.prefSuffix}"

    private fun rewardClaimsKey(uid: String, dateKey: String): String =
        "u_${uid}_${dateKey}_reward_claims"

    private fun deviceRewardClaimsKey(dateKey: String): String =
        "dev_${dateKey}_reward_claims"

    fun getSnapshot(context: Context, uid: String): DailyUsageSnapshot {
        val cleanUid = uid.trim()
        val dateKey = todayUtcDateKey()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val devRewardClaims = prefs.getInt(deviceRewardClaimsKey(dateKey), 0).coerceIn(0, MAX_DAILY_REWARD_CLAIMS)
        if (cleanUid.isBlank()) {
            val empty = DailyUsageSnapshot(
                uid = "",
                dateKeyUtc = dateKey,
                rewardClaimsToday = devRewardClaims
            )
            _snapshotFlow.value = empty
            return empty
        }
        val devState = DeviceAbuseGuard.getLocalState(context)
        val snap = synchronized(lock) {
            val userRewardClaims = prefs.getInt(rewardClaimsKey(cleanUid, dateKey), 0).coerceIn(0, MAX_DAILY_REWARD_CLAIMS)
            DailyUsageSnapshot(
                uid = cleanUid,
                dateKeyUtc = dateKey,
                overlayMcqUsed = maxOf(
                    prefs.getInt(prefKey(cleanUid, dateKey, UsageLimitFeature.OVERLAY_MCQ), 0).coerceAtLeast(0),
                    devState.overlayMcqUsed
                ),
                askTextUsed = maxOf(
                    prefs.getInt(prefKey(cleanUid, dateKey, UsageLimitFeature.ASK_AI_TEXT), 0).coerceAtLeast(0),
                    devState.askTextUsed
                ),
                askPhotoUsed = maxOf(
                    prefs.getInt(prefKey(cleanUid, dateKey, UsageLimitFeature.ASK_AI_PHOTO), 0).coerceAtLeast(0),
                    devState.askPhotoUsed
                ),
                askPdfUsed = maxOf(
                    prefs.getInt(prefKey(cleanUid, dateKey, UsageLimitFeature.ASK_AI_PDF), 0).coerceAtLeast(0),
                    devState.askPdfUsed
                ),
                googleFormsUsed = maxOf(
                    prefs.getInt(prefKey(cleanUid, dateKey, UsageLimitFeature.GOOGLE_FORMS), 0).coerceAtLeast(0),
                    devState.googleFormsUsed
                ),
                rewardClaimsToday = maxOf(userRewardClaims, devRewardClaims)
            )
        }
        _snapshotFlow.value = snap
        return snap
    }

    /**
     * Checks whether the signed-in user & device are allowed to run [feature].
     * Enforces:
     * 1. Permanent device ban from repeated credit-bypass abuse (Strike 2+).
     * 2. Shared device + per-account UTC daily pool so switching Gmail accounts never resets limits.
     */
    suspend fun checkQuota(
        context: Context,
        uid: String,
        feature: UsageLimitFeature,
        forceCloudSync: Boolean = false
    ): UsageLimitCheckResult {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) {
            return UsageLimitCheckResult.Allowed(feature, used = 0, limit = feature.dailyLimit)
        }

        val dateKey = todayUtcDateKey()
        val syncKey = "${cleanUid}_$dateKey"
        val nowMs = System.currentTimeMillis()
        val lastSync = lastCloudSyncByUidDate[syncKey] ?: 0L
        if (forceCloudSync || nowMs - lastSync >= CLOUD_SYNC_STALE_MS) {
            syncFromCloud(context, cleanUid)
            DeviceAbuseGuard.syncFromCloud(context, cleanUid)
        }

        if (DeviceAbuseGuard.isDeviceBanned(context)) {
            return UsageLimitCheckResult.LimitReached(
                feature = feature,
                used = feature.dailyLimit,
                limit = feature.dailyLimit,
                userMessage = DeviceAbuseGuard.buildBannedMessage(),
                isDeviceBanned = true
            )
        }

        val snap = getSnapshot(context, cleanUid)
        val used = snap.usedFor(feature)
        val effectiveLimit = snap.effectiveLimitFor(feature)
        return if (used >= effectiveLimit) {
            UsageLimitCheckResult.LimitReached(
                feature = feature,
                used = used,
                limit = effectiveLimit,
                userMessage = buildLimitReachedMessage(feature, used, effectiveLimit),
                isDeviceBanned = false
            )
        } else {
            UsageLimitCheckResult.Allowed(
                feature = feature,
                used = used,
                limit = effectiveLimit
            )
        }
    }

    /**
     * Grants 1 Rewarded Ad Bonus Pack for the current UTC day (up to [MAX_DAILY_REWARD_CLAIMS] per day).
     * Adds +5 Cloud Solves, +3 Follow-Ups, +2 Photo Keys, +1 PDF Key, +2 Google Forms.
     */
    suspend fun claimRewardedAdBonus(
        context: Context,
        uid: String
    ): DailyUsageSnapshot {
        val cleanUid = uid.trim()
        val dateKey = todayUtcDateKey()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        synchronized(lock) {
            val devKey = deviceRewardClaimsKey(dateKey)
            val currentDev = prefs.getInt(devKey, 0).coerceIn(0, MAX_DAILY_REWARD_CLAIMS)
            val currentUser = if (cleanUid.isNotBlank()) {
                prefs.getInt(rewardClaimsKey(cleanUid, dateKey), 0).coerceIn(0, MAX_DAILY_REWARD_CLAIMS)
            } else {
                0
            }
            val nextClaims = (maxOf(currentDev, currentUser) + 1).coerceIn(0, MAX_DAILY_REWARD_CLAIMS)
            val editor = prefs.edit().putInt(devKey, nextClaims)
            if (cleanUid.isNotBlank()) {
                editor.putInt(rewardClaimsKey(cleanUid, dateKey), nextClaims)
            }
            editor.apply()
        }

        val updatedSnap = getSnapshot(context, cleanUid)
        _snapshotFlow.value = updatedSnap
        if (cleanUid.isNotBlank()) {
            pushMonotonicToCloud(context, updatedSnap)
        }
        return updatedSnap
    }

    /**
     * Records 1 unit of usage locally & on the shared device pool immediately,
     * and pushes monotonic syncs to both `/users/{uid}/dailyUsage/{dateKey}`
     * and `/deviceGuards/{deviceHash}`.
     */
    suspend fun recordUsage(
        context: Context,
        uid: String,
        feature: UsageLimitFeature,
        delta: Int = 1
    ): DailyUsageSnapshot {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank() || delta <= 0) return getSnapshot(context, cleanUid)

        val dateKey = todayUtcDateKey()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        synchronized(lock) {
            val key = prefKey(cleanUid, dateKey, feature)
            val nextVal = (prefs.getInt(key, 0) + delta).coerceIn(0, 1000)
            prefs.edit().putInt(key, nextVal).apply()
        }

        // Record against shared device pool as well (prevents account-switching quota reset)
        DeviceAbuseGuard.recordDeviceUsage(context, cleanUid, feature, delta)

        val updatedSnap = getSnapshot(context, cleanUid)
        _snapshotFlow.value = updatedSnap

        // Best-effort monotonic sync to Firestore (survives reinstalls & multi-device use)
        pushMonotonicToCloud(context, updatedSnap)
        return updatedSnap
    }

    /**
     * Pulls today's usage document from `/users/{uid}/dailyUsage/{dateKey}` and merges
     * monotonically (`max(local, cloud)`) into local storage.
     */
    suspend fun syncFromCloud(context: Context, uid: String): DailyUsageSnapshot = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        val dateKey = todayUtcDateKey()
        if (cleanUid.isBlank()) return@withContext getSnapshot(context, "")

        val db = getDatabaseOrNull(context) ?: return@withContext getSnapshot(context, cleanUid)
        val path = "$USERS_COLLECTION/$cleanUid/$DAILY_USAGE_SUBCOLLECTION/$dateKey"
        try {
            val doc = db.collection(USERS_COLLECTION)
                .document(cleanUid)
                .collection(DAILY_USAGE_SUBCOLLECTION)
                .document(dateKey)
                .get()
                .await()

            lastCloudSyncByUidDate["${cleanUid}_$dateKey"] = System.currentTimeMillis()
            if (doc.exists()) {
                val cloudOverlay = ((doc.getLong(UsageLimitFeature.OVERLAY_MCQ.firestoreField) ?: 0L).toInt()).coerceIn(0, 1000)
                val cloudAskText = ((doc.getLong(UsageLimitFeature.ASK_AI_TEXT.firestoreField) ?: 0L).toInt()).coerceIn(0, 1000)
                val cloudAskPhoto = ((doc.getLong(UsageLimitFeature.ASK_AI_PHOTO.firestoreField) ?: 0L).toInt()).coerceIn(0, 1000)
                val cloudAskPdf = ((doc.getLong(UsageLimitFeature.ASK_AI_PDF.firestoreField) ?: 0L).toInt()).coerceIn(0, 1000)
                val cloudForms = ((doc.getLong(UsageLimitFeature.GOOGLE_FORMS.firestoreField) ?: 0L).toInt()).coerceIn(0, 1000)
                val cloudRewardClaims = ((doc.getLong(REWARD_CLAIMS_FIELD) ?: 0L).toInt()).coerceIn(0, MAX_DAILY_REWARD_CLAIMS)

                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                synchronized(lock) {
                    val editor = prefs.edit()
                    fun mergeMax(f: UsageLimitFeature, cloudVal: Int) {
                        val k = prefKey(cleanUid, dateKey, f)
                        val merged = maxOf(prefs.getInt(k, 0), cloudVal).coerceIn(0, 1000)
                        editor.putInt(k, merged)
                    }
                    mergeMax(UsageLimitFeature.OVERLAY_MCQ, cloudOverlay)
                    mergeMax(UsageLimitFeature.ASK_AI_TEXT, cloudAskText)
                    mergeMax(UsageLimitFeature.ASK_AI_PHOTO, cloudAskPhoto)
                    mergeMax(UsageLimitFeature.ASK_AI_PDF, cloudAskPdf)
                    mergeMax(UsageLimitFeature.GOOGLE_FORMS, cloudForms)
                    val mergedRewards = maxOf(
                        prefs.getInt(rewardClaimsKey(cleanUid, dateKey), 0),
                        prefs.getInt(deviceRewardClaimsKey(dateKey), 0),
                        cloudRewardClaims
                    ).coerceIn(0, MAX_DAILY_REWARD_CLAIMS)
                    editor.putInt(rewardClaimsKey(cleanUid, dateKey), mergedRewards)
                    editor.putInt(deviceRewardClaimsKey(dateKey), mergedRewards)
                    editor.apply()
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Log.w(TAG, "Cloud usage sync skipped (offline or unavailable): ${e.message}")
        }
        getSnapshot(context, cleanUid)
    }

    private suspend fun pushMonotonicToCloud(
        context: Context,
        localSnap: DailyUsageSnapshot
    ) = withContext(Dispatchers.IO) {
        val db = getDatabaseOrNull(context) ?: return@withContext
        val uid = localSnap.uid
        val dateKey = localSnap.dateKeyUtc
        if (uid.isBlank() || dateKey.length != 10) return@withContext

        val docRef = db.collection(USERS_COLLECTION)
            .document(uid)
            .collection(DAILY_USAGE_SUBCOLLECTION)
            .document(dateKey)
        val path = "$USERS_COLLECTION/$uid/$DAILY_USAGE_SUBCOLLECTION/$dateKey"

        try {
            db.runTransaction { tx ->
                val existing = tx.get(docRef)
                if (!existing.exists()) {
                    val createMap = hashMapOf<String, Any>(
                        "userId" to uid,
                        "dateKey" to dateKey,
                        UsageLimitFeature.OVERLAY_MCQ.firestoreField to localSnap.overlayMcqUsed,
                        UsageLimitFeature.ASK_AI_TEXT.firestoreField to localSnap.askTextUsed,
                        UsageLimitFeature.ASK_AI_PHOTO.firestoreField to localSnap.askPhotoUsed,
                        UsageLimitFeature.ASK_AI_PDF.firestoreField to localSnap.askPdfUsed,
                        UsageLimitFeature.GOOGLE_FORMS.firestoreField to localSnap.googleFormsUsed,
                        REWARD_CLAIMS_FIELD to localSnap.rewardClaimsToday,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    tx.set(docRef, createMap)
                } else {
                    val mOverlay = maxOf((existing.getLong(UsageLimitFeature.OVERLAY_MCQ.firestoreField) ?: 0L).toInt(), localSnap.overlayMcqUsed).coerceIn(0, 1000)
                    val mAskText = maxOf((existing.getLong(UsageLimitFeature.ASK_AI_TEXT.firestoreField) ?: 0L).toInt(), localSnap.askTextUsed).coerceIn(0, 1000)
                    val mAskPhoto = maxOf((existing.getLong(UsageLimitFeature.ASK_AI_PHOTO.firestoreField) ?: 0L).toInt(), localSnap.askPhotoUsed).coerceIn(0, 1000)
                    val mAskPdf = maxOf((existing.getLong(UsageLimitFeature.ASK_AI_PDF.firestoreField) ?: 0L).toInt(), localSnap.askPdfUsed).coerceIn(0, 1000)
                    val mForms = maxOf((existing.getLong(UsageLimitFeature.GOOGLE_FORMS.firestoreField) ?: 0L).toInt(), localSnap.googleFormsUsed).coerceIn(0, 1000)
                    val mRewards = maxOf((existing.getLong(REWARD_CLAIMS_FIELD) ?: 0L).toInt(), localSnap.rewardClaimsToday).coerceIn(0, MAX_DAILY_REWARD_CLAIMS)

                    val updateMap = hashMapOf<String, Any>(
                        UsageLimitFeature.OVERLAY_MCQ.firestoreField to mOverlay,
                        UsageLimitFeature.ASK_AI_TEXT.firestoreField to mAskText,
                        UsageLimitFeature.ASK_AI_PHOTO.firestoreField to mAskPhoto,
                        UsageLimitFeature.ASK_AI_PDF.firestoreField to mAskPdf,
                        UsageLimitFeature.GOOGLE_FORMS.firestoreField to mForms,
                        REWARD_CLAIMS_FIELD to mRewards,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    tx.update(docRef, updateMap)
                }
                null
            }.await()
            lastCloudSyncByUidDate["${uid}_$dateKey"] = System.currentTimeMillis()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Log.w(TAG, "Cloud usage push deferred: ${e.message}")
        }
    }

    fun buildLimitReachedMessage(
        feature: UsageLimitFeature,
        used: Int = feature.dailyLimit,
        effectiveLimit: Int = feature.dailyLimit
    ): String {
        val capped = used.coerceAtLeast(effectiveLimit)
        return when (feature) {
            UsageLimitFeature.OVERLAY_MCQ ->
                "Daily cloud server limit reached ($capped/$effectiveLimit). Auto-switched to OCR + Phone Gemini (Unlimited), or watch a short Rewarded Ad in Settings for +${feature.bonusPerReward} Cloud Solves!"
            UsageLimitFeature.ASK_AI_TEXT ->
                "Daily Quick Follow-Up limit reached ($capped/$effectiveLimit). Watch a Rewarded Ad for +${feature.bonusPerReward} more or wait for midnight UTC."
            UsageLimitFeature.ASK_AI_PHOTO ->
                "Daily Photo Answer Key limit reached ($capped/$effectiveLimit). Watch a Rewarded Ad for +${feature.bonusPerReward} more or wait for midnight UTC."
            UsageLimitFeature.ASK_AI_PDF ->
                "Daily PDF Answer Key limit reached ($capped/$effectiveLimit). Watch a Rewarded Ad for +${feature.bonusPerReward} more or wait for midnight UTC."
            UsageLimitFeature.GOOGLE_FORMS ->
                "Daily Google Forms limit reached ($capped/$effectiveLimit). Watch a Rewarded Ad for +${feature.bonusPerReward} more or wait for midnight UTC."
        }
    }

    internal fun resetForTests(context: Context? = null) {
        synchronized(lock) {
            lastCloudSyncByUidDate.clear()
            context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()?.clear()?.apply()
            _snapshotFlow.value = DailyUsageSnapshot()
        }
        clockUtcDateProviderForTest = null
        disableCloudSyncForTest = false
        DeviceAbuseGuard.resetForTests(context)
    }
}
