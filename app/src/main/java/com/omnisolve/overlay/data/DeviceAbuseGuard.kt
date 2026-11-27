package com.omnisolve.overlay.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
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
import java.security.MessageDigest
import java.util.UUID

sealed class AbuseEnforcementState {
    data object Clear : AbuseEnforcementState()

    data class Warning(
        val strikeCount: Int,
        val reason: String
    ) : AbuseEnforcementState()

    data class Banned(
        val strikeCount: Int,
        val reason: String
    ) : AbuseEnforcementState()
}

data class DeviceGuardState(
    val deviceHash: String = "",
    val dateKeyUtc: String = "",
    val lastUid: String = "",
    val seenUidCountToday: Int = 0,
    val overlayMcqUsed: Int = 0,
    val askTextUsed: Int = 0,
    val askPhotoUsed: Int = 0,
    val askPdfUsed: Int = 0,
    val googleFormsUsed: Int = 0,
    val strikeCount: Int = 0,
    val banned: Boolean = false,
    val lastStrikeReason: String = "",
    val lastDeletedAccountAtMs: Long = 0L
) {
    val totalUsedToday: Int
        get() = overlayMcqUsed + askTextUsed + askPhotoUsed + askPdfUsed + googleFormsUsed

    fun usedFor(feature: UsageLimitFeature): Int = when (feature) {
        UsageLimitFeature.OVERLAY_MCQ -> overlayMcqUsed
        UsageLimitFeature.ASK_AI_TEXT -> askTextUsed
        UsageLimitFeature.ASK_AI_PHOTO -> askPhotoUsed
        UsageLimitFeature.ASK_AI_PDF -> askPdfUsed
        UsageLimitFeature.GOOGLE_FORMS -> googleFormsUsed
    }

    fun anyFeatureExhausted(): Boolean =
        UsageLimitFeature.entries.any { usedFor(it) >= it.dailyLimit }
}

/**
 * Hardware-bound fair-use anti-abuse guard keyed by salted SHA-256 of ANDROID_ID.
 *
 * Defends against real-world credit-farming behavior:
 * 1. Shared Device Daily Pool: Switching Google accounts on the same phone NEVER resets daily limits.
 * 2. Strike 1 (Fair-Use Warning): Switching accounts after consuming daily credits, or deleting
 *    and recreating/re-signing in within 24 hours after using credits, triggers Strike 1 + warning dialog.
 * 3. Strike 2 (Device Restriction / Ban): Repeating the bypass behavior sets a monotonic,
 *    irreversible device ban synced to `/deviceGuards/{deviceHash}` so clearing app data or
 *    reinstalling cannot lift the restriction.
 */
object DeviceAbuseGuard {

    private const val TAG = "PrismDeviceGuard"
    private const val PREFS_NAME = "prism_device_guard_v1"
    private const val COLLECTION_DEVICE_GUARDS = "deviceGuards"
    private const val HASH_SALT = "prism_dev_v1:"
    private const val DELETE_RECREATE_WINDOW_MS = 24L * 60L * 60L * 1000L
    private const val SWITCH_CREDIT_THRESHOLD = 3
    private const val CLOUD_SYNC_STALE_MS = 60_000L

    private const val KEY_FALLBACK_DEVICE_ID = "fallback_device_uuid"
    private const val KEY_DATE = "guard_date_key"
    private const val KEY_LAST_UID = "guard_last_uid"
    private const val KEY_SEEN_UIDS = "guard_seen_uids"
    private const val KEY_SEEN_UID_COUNT = "guard_seen_uid_count"
    private const val KEY_STRIKE_COUNT = "guard_strike_count"
    private const val KEY_BANNED = "guard_banned"
    private const val KEY_LAST_STRIKE_REASON = "guard_last_strike_reason"
    private const val KEY_LAST_DELETED_AT_MS = "guard_last_deleted_at_ms"
    private const val KEY_HANDLED_DELETE_AT_MS = "guard_handled_delete_at_ms"

    private val lock = Any()

    @Volatile
    private var lastCloudSyncMs: Long = 0L

    @Volatile
    internal var deviceHashOverrideForTest: String? = null

    @Volatile
    internal var nowMsProviderForTest: (() -> Long)? = null

    @Volatile
    internal var disableCloudSyncForTest: Boolean = false

    private val _enforcementFlow = MutableStateFlow<AbuseEnforcementState>(AbuseEnforcementState.Clear)
    val enforcementFlow: StateFlow<AbuseEnforcementState> = _enforcementFlow.asStateFlow()

    private val _pendingDialogFlow = MutableStateFlow<AbuseEnforcementState?>(null)
    val pendingDialogFlow: StateFlow<AbuseEnforcementState?> = _pendingDialogFlow.asStateFlow()

    private fun nowMs(): Long = nowMsProviderForTest?.invoke() ?: System.currentTimeMillis()

    private fun todayDateKey(): String = UserUsageLimitManager.todayUtcDateKey()

    private fun featurePrefKey(dateKey: String, feature: UsageLimitFeature): String =
        "dev_${dateKey}_${feature.prefSuffix}"

    private fun getDatabaseOrNull(context: Context): FirebaseFirestore? {
        if (disableCloudSyncForTest) return null
        return try {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id).trim()
            if (dbId.isBlank()) null else FirebaseFirestore.getInstance(dbId)
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("HardwareIds")
    fun computeDeviceHash(context: Context): String {
        deviceHashOverrideForTest?.let { return it }
        val appCtx = context.applicationContext
        val androidId = runCatching {
            Settings.Secure.getString(appCtx.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim().orEmpty()

        val rawSeed = if (androidId.isNotBlank() && androidId != "9774d56d682e549c") {
            "$HASH_SALT$androidId"
        } else {
            val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val fallback = synchronized(lock) {
                prefs.getString(KEY_FALLBACK_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
                    ?: UUID.randomUUID().toString().also {
                        prefs.edit().putString(KEY_FALLBACK_DEVICE_ID, it).apply()
                    }
            }
            "$HASH_SALT$fallback"
        }
        return sha256Hex(rawSeed)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    fun getLocalState(context: Context): DeviceGuardState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = todayDateKey()
        val devHash = computeDeviceHash(context)
        return synchronized(lock) {
            val storedDate = prefs.getString(KEY_DATE, "") ?: ""
            val sameDay = storedDate == today
            val state = DeviceGuardState(
                deviceHash = devHash,
                dateKeyUtc = today,
                lastUid = if (sameDay) (prefs.getString(KEY_LAST_UID, "") ?: "") else "",
                seenUidCountToday = if (sameDay) prefs.getInt(KEY_SEEN_UID_COUNT, 0).coerceAtLeast(0) else 0,
                overlayMcqUsed = prefs.getInt(featurePrefKey(today, UsageLimitFeature.OVERLAY_MCQ), 0).coerceAtLeast(0),
                askTextUsed = prefs.getInt(featurePrefKey(today, UsageLimitFeature.ASK_AI_TEXT), 0).coerceAtLeast(0),
                askPhotoUsed = prefs.getInt(featurePrefKey(today, UsageLimitFeature.ASK_AI_PHOTO), 0).coerceAtLeast(0),
                askPdfUsed = prefs.getInt(featurePrefKey(today, UsageLimitFeature.ASK_AI_PDF), 0).coerceAtLeast(0),
                googleFormsUsed = prefs.getInt(featurePrefKey(today, UsageLimitFeature.GOOGLE_FORMS), 0).coerceAtLeast(0),
                strikeCount = prefs.getInt(KEY_STRIKE_COUNT, 0).coerceIn(0, 10),
                banned = prefs.getBoolean(KEY_BANNED, false),
                lastStrikeReason = prefs.getString(KEY_LAST_STRIKE_REASON, "") ?: "",
                lastDeletedAccountAtMs = prefs.getLong(KEY_LAST_DELETED_AT_MS, 0L).coerceAtLeast(0L)
            )
            publishEnforcement(state)
            state
        }
    }

    fun isDeviceBanned(context: Context): Boolean = getLocalState(context).banned

    fun getDeviceUsedToday(context: Context, feature: UsageLimitFeature): Int =
        getLocalState(context).usedFor(feature)

    fun dismissPendingDialog() {
        _pendingDialogFlow.value = null
    }

    fun buildBannedMessage(): String =
        "Cloud solving is restricted on this device due to repeated attempts to bypass daily limits by switching or recreating accounts. You can still use saved history or your own personal API key in Settings."

    fun buildWarningMessage(reason: String): String =
        "Fair-Use Warning (1 of 2): $reason. Daily limits are shared per device. Repeating this will permanently restrict cloud solving on this device."

    private fun publishEnforcement(state: DeviceGuardState) {
        _enforcementFlow.value = when {
            state.banned -> AbuseEnforcementState.Banned(
                strikeCount = state.strikeCount.coerceAtLeast(2),
                reason = state.lastStrikeReason.ifBlank {
                    "Repeated account switching or recreation to bypass daily limits"
                }
            )
            state.strikeCount >= 1 -> AbuseEnforcementState.Warning(
                strikeCount = state.strikeCount,
                reason = state.lastStrikeReason.ifBlank {
                    "Account switching detected after using daily credits"
                }
            )
            else -> AbuseEnforcementState.Clear
        }
    }

    /**
     * Records device-wide daily consumption alongside per-UID tracking so switching accounts
     * on the same phone never grants extra daily allowance.
     */
    suspend fun recordDeviceUsage(
        context: Context,
        uid: String,
        feature: UsageLimitFeature,
        delta: Int = 1
    ): DeviceGuardState {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank() || delta <= 0) return getLocalState(context)

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = todayDateKey()
        val updated = synchronized(lock) {
            val storedDate = prefs.getString(KEY_DATE, "") ?: ""
            val seenSet = if (storedDate == today) {
                prefs.getStringSet(KEY_SEEN_UIDS, emptySet())?.toMutableSet() ?: mutableSetOf()
            } else {
                mutableSetOf()
            }
            seenSet.add(cleanUid)
            val prevCount = if (storedDate == today) prefs.getInt(KEY_SEEN_UID_COUNT, 0) else 0
            val nextSeenCount = maxOf(prevCount, seenSet.size).coerceIn(1, 100)

            val fKey = featurePrefKey(today, feature)
            val nextFeatureVal = (prefs.getInt(fKey, 0) + delta).coerceIn(0, 1000)

            prefs.edit()
                .putString(KEY_DATE, today)
                .putString(KEY_LAST_UID, cleanUid)
                .putStringSet(KEY_SEEN_UIDS, seenSet)
                .putInt(KEY_SEEN_UID_COUNT, nextSeenCount)
                .putInt(fKey, nextFeatureVal)
                .apply()

            getLocalState(context)
        }
        pushMonotonicToCloud(context, cleanUid, updated)
        return updated
    }

    /**
     * Called right BEFORE deleting a Firebase user account.
     * If this device or user consumed cloud credits today, timestamps the deletion both
     * locally and in `/deviceGuards/{deviceHash}` so deleting & recreating an account to
     * farm credits triggers Strike 1 (Warning) -> Strike 2 (Device Ban).
     */
    suspend fun recordAccountDeletionAttempt(context: Context, uid: String) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return
        val local = getLocalState(context)
        val userSnap = UserUsageLimitManager.getSnapshot(context, cleanUid)
        val userTotalToday = userSnap.overlayMcqUsed + userSnap.askTextUsed +
            userSnap.askPhotoUsed + userSnap.askPdfUsed + userSnap.googleFormsUsed
        val consumedAnyCredits = local.totalUsedToday > 0 || userTotalToday > 0 || local.strikeCount > 0
        if (!consumedAnyCredits) return

        val deleteTs = nowMs()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = synchronized(lock) {
            val prevDelete = prefs.getLong(KEY_LAST_DELETED_AT_MS, 0L)
            val monotonicTs = maxOf(prevDelete, deleteTs)
            prefs.edit()
                .putLong(KEY_LAST_DELETED_AT_MS, monotonicTs)
                .apply()
            getLocalState(context)
        }
        pushMonotonicToCloud(context, cleanUid, updated)
    }

    /**
     * Evaluates sign-in / active-session human-behavior signals on this device:
     * - Pulls `/deviceGuards/{deviceHash}` from Firestore (reinstall/clear-data tamper proof).
     * - Detects account-switching credit hopping on the same device after consuming credits.
     * - Detects delete-and-recreate within 24 hours of consuming credits.
     * - Escalates strictly: Strike 1 = Fair-Use Warning Dialog, Strike 2 = Permanent Device Ban.
     */
    suspend fun evaluateSignIn(
        context: Context,
        uid: String,
        forceCloudSync: Boolean = true
    ): AbuseEnforcementState {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return getLocalState(context).let { _enforcementFlow.value }

        if (forceCloudSync || nowMs() - lastCloudSyncMs >= CLOUD_SYNC_STALE_MS) {
            syncFromCloud(context, cleanUid)
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = todayDateKey()
        val now = nowMs()
        var newlyIssuedState: AbuseEnforcementState? = null
        var needsCloudPush = false

        val finalState = synchronized(lock) {
            val current = getLocalState(context)
            val storedDate = prefs.getString(KEY_DATE, "") ?: ""
            val seenSet = if (storedDate == today) {
                prefs.getStringSet(KEY_SEEN_UIDS, emptySet())?.toMutableSet() ?: mutableSetOf()
            } else {
                mutableSetOf()
            }
            val prevLastUid = if (storedDate == today) current.lastUid else ""
            val handledDeleteMs = prefs.getLong(KEY_HANDLED_DELETE_AT_MS, 0L)

            var strikeCount = current.strikeCount
            var banned = current.banned
            var strikeReason = current.lastStrikeReason

            // Signal 1: Account deleted after using credits, then signed back in within 24 hours
            val deleteTriggerActive = current.lastDeletedAccountAtMs > 0L &&
                current.lastDeletedAccountAtMs > handledDeleteMs &&
                (now - current.lastDeletedAccountAtMs) in 0L..DELETE_RECREATE_WINDOW_MS

            // Signal 2: Account switched to a different Google account on the same device today
            // after already consuming cloud credits (>= 3 solves or any feature limit reached)
            val accountSwitchAbuse = prevLastUid.isNotBlank() &&
                prevLastUid != cleanUid &&
                (current.totalUsedToday >= SWITCH_CREDIT_THRESHOLD || current.anyFeatureExhausted())

            val abuseReason = when {
                deleteTriggerActive ->
                    "Deleting and recreating an account to reset daily credits"
                accountSwitchAbuse ->
                    "Switching Google accounts on this device after using daily credits"
                else -> null
            }

            if (abuseReason != null && !banned) {
                strikeCount = (strikeCount + 1).coerceIn(1, 10)
                strikeReason = abuseReason
                if (strikeCount >= 2) {
                    banned = true
                }
                needsCloudPush = true
                newlyIssuedState = if (banned) {
                    AbuseEnforcementState.Banned(strikeCount = strikeCount, reason = strikeReason)
                } else {
                    AbuseEnforcementState.Warning(strikeCount = strikeCount, reason = strikeReason)
                }
            } else if (banned) {
                newlyIssuedState = AbuseEnforcementState.Banned(
                    strikeCount = strikeCount.coerceAtLeast(2),
                    reason = strikeReason.ifBlank {
                        "Repeated attempts to bypass daily limits on this device"
                    }
                )
            }

            seenSet.add(cleanUid)
            val nextSeenCount = maxOf(current.seenUidCountToday, seenSet.size).coerceIn(1, 100)
            if (prevLastUid != cleanUid || storedDate != today) {
                needsCloudPush = true
            }

            val editor = prefs.edit()
                .putString(KEY_DATE, today)
                .putString(KEY_LAST_UID, cleanUid)
                .putStringSet(KEY_SEEN_UIDS, seenSet)
                .putInt(KEY_SEEN_UID_COUNT, nextSeenCount)
                .putInt(KEY_STRIKE_COUNT, strikeCount)
                .putBoolean(KEY_BANNED, banned)
                .putString(KEY_LAST_STRIKE_REASON, strikeReason)

            if (deleteTriggerActive) {
                editor.putLong(KEY_HANDLED_DELETE_AT_MS, current.lastDeletedAccountAtMs)
            }
            editor.apply()
            getLocalState(context)
        }

        if (newlyIssuedState != null) {
            _pendingDialogFlow.value = newlyIssuedState
        }

        if (needsCloudPush) {
            pushMonotonicToCloud(context, cleanUid, finalState)
        }

        return _enforcementFlow.value
    }

    suspend fun syncFromCloud(context: Context, uid: String): DeviceGuardState = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        val devHash = computeDeviceHash(context)
        if (cleanUid.isBlank() || devHash.length != 64) return@withContext getLocalState(context)

        val db = getDatabaseOrNull(context) ?: return@withContext getLocalState(context)
        val path = "$COLLECTION_DEVICE_GUARDS/$devHash"
        try {
            val doc = db.collection(COLLECTION_DEVICE_GUARDS)
                .document(devHash)
                .get()
                .await()

            lastCloudSyncMs = nowMs()
            if (doc.exists()) {
                val today = todayDateKey()
                val cloudDate = doc.getString("dateKey").orEmpty()
                val cloudLastUid = doc.getString("lastUid").orEmpty()
                val cloudSeenCount = ((doc.getLong("seenUidCountToday") ?: 1L).toInt()).coerceIn(1, 100)
                val cloudStrikes = ((doc.getLong("strikeCount") ?: 0L).toInt()).coerceIn(0, 10)
                val cloudBanned = doc.getBoolean("banned") ?: false
                val cloudReason = doc.getString("lastStrikeReason").orEmpty().take(240)
                val cloudDeletedAt = (doc.getLong("lastDeletedAccountAtMs") ?: 0L).coerceAtLeast(0L)

                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                synchronized(lock) {
                    val editor = prefs.edit()
                    val mergedStrikes = maxOf(prefs.getInt(KEY_STRIKE_COUNT, 0), cloudStrikes)
                    val mergedBanned = prefs.getBoolean(KEY_BANNED, false) || cloudBanned || mergedStrikes >= 2
                    val mergedDeleteMs = maxOf(prefs.getLong(KEY_LAST_DELETED_AT_MS, 0L), cloudDeletedAt)

                    editor.putInt(KEY_STRIKE_COUNT, mergedStrikes)
                    editor.putBoolean(KEY_BANNED, mergedBanned)
                    if (cloudReason.isNotBlank() && prefs.getString(KEY_LAST_STRIKE_REASON, "").isNullOrBlank()) {
                        editor.putString(KEY_LAST_STRIKE_REASON, cloudReason)
                    } else if (cloudStrikes >= prefs.getInt(KEY_STRIKE_COUNT, 0) && cloudReason.isNotBlank()) {
                        editor.putString(KEY_LAST_STRIKE_REASON, cloudReason)
                    }
                    editor.putLong(KEY_LAST_DELETED_AT_MS, mergedDeleteMs)

                    if (cloudDate == today) {
                        val existingDate = prefs.getString(KEY_DATE, "") ?: ""
                        editor.putString(KEY_DATE, today)
                        if (cloudLastUid.isNotBlank() && (existingDate != today || prefs.getString(KEY_LAST_UID, "").isNullOrBlank())) {
                            editor.putString(KEY_LAST_UID, cloudLastUid)
                        }
                        val localSeen = if (existingDate == today) prefs.getInt(KEY_SEEN_UID_COUNT, 0) else 0
                        editor.putInt(KEY_SEEN_UID_COUNT, maxOf(localSeen, cloudSeenCount))

                        fun mergeFeature(f: UsageLimitFeature) {
                            val cloudVal = ((doc.getLong(f.firestoreField) ?: 0L).toInt()).coerceIn(0, 1000)
                            val k = featurePrefKey(today, f)
                            editor.putInt(k, maxOf(prefs.getInt(k, 0), cloudVal))
                        }
                        UsageLimitFeature.entries.forEach(::mergeFeature)
                    }
                    editor.apply()
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Log.w(TAG, "Cloud device guard sync skipped: ${e.message}")
        }
        getLocalState(context)
    }

    private suspend fun pushMonotonicToCloud(
        context: Context,
        uid: String,
        local: DeviceGuardState
    ) = withContext(Dispatchers.IO) {
        val db = getDatabaseOrNull(context) ?: return@withContext
        val devHash = local.deviceHash
        val today = local.dateKeyUtc
        if (uid.isBlank() || devHash.length != 64 || today.length != 10) return@withContext

        val docRef = db.collection(COLLECTION_DEVICE_GUARDS).document(devHash)
        val path = "$COLLECTION_DEVICE_GUARDS/$devHash"

        try {
            db.runTransaction { tx ->
                val existing = tx.get(docRef)
                if (!existing.exists()) {
                    val createMap = hashMapOf<String, Any>(
                        "deviceHash" to devHash,
                        "dateKey" to today,
                        "lastUid" to uid,
                        "seenUidCountToday" to local.seenUidCountToday.coerceIn(1, 100),
                        UsageLimitFeature.OVERLAY_MCQ.firestoreField to local.overlayMcqUsed,
                        UsageLimitFeature.ASK_AI_TEXT.firestoreField to local.askTextUsed,
                        UsageLimitFeature.ASK_AI_PHOTO.firestoreField to local.askPhotoUsed,
                        UsageLimitFeature.ASK_AI_PDF.firestoreField to local.askPdfUsed,
                        UsageLimitFeature.GOOGLE_FORMS.firestoreField to local.googleFormsUsed,
                        "strikeCount" to local.strikeCount.coerceIn(0, 10),
                        "banned" to local.banned,
                        "lastStrikeReason" to local.lastStrikeReason.take(240),
                        "lastDeletedAccountAtMs" to local.lastDeletedAccountAtMs.coerceAtLeast(0L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    tx.set(docRef, createMap)
                } else {
                    val exDate = existing.getString("dateKey").orEmpty()
                    val exStrikes = ((existing.getLong("strikeCount") ?: 0L).toInt()).coerceIn(0, 10)
                    val exBanned = existing.getBoolean("banned") ?: false
                    val exDeleteMs = (existing.getLong("lastDeletedAccountAtMs") ?: 0L).coerceAtLeast(0L)
                    val exReason = existing.getString("lastStrikeReason").orEmpty()

                    val effectiveDate = if (today >= exDate) today else exDate
                    val sameDay = effectiveDate == exDate

                    fun mergedCount(f: UsageLimitFeature, localVal: Int): Int {
                        if (!sameDay) return localVal.coerceIn(0, 1000)
                        val exVal = ((existing.getLong(f.firestoreField) ?: 0L).toInt()).coerceIn(0, 1000)
                        return maxOf(exVal, localVal).coerceIn(0, 1000)
                    }

                    val mergedSeen = if (sameDay) {
                        maxOf((existing.getLong("seenUidCountToday") ?: 1L).toInt(), local.seenUidCountToday).coerceIn(1, 100)
                    } else {
                        local.seenUidCountToday.coerceIn(1, 100)
                    }
                    val mergedStrikes = maxOf(exStrikes, local.strikeCount).coerceIn(0, 10)
                    val mergedBanned = exBanned || local.banned || mergedStrikes >= 2
                    val mergedReason = if (local.lastStrikeReason.isNotBlank()) {
                        local.lastStrikeReason.take(240)
                    } else {
                        exReason.take(240)
                    }
                    val mergedDeleteMs = maxOf(exDeleteMs, local.lastDeletedAccountAtMs)

                    val updateMap = hashMapOf<String, Any>(
                        "dateKey" to effectiveDate,
                        "lastUid" to uid,
                        "seenUidCountToday" to mergedSeen,
                        UsageLimitFeature.OVERLAY_MCQ.firestoreField to mergedCount(UsageLimitFeature.OVERLAY_MCQ, local.overlayMcqUsed),
                        UsageLimitFeature.ASK_AI_TEXT.firestoreField to mergedCount(UsageLimitFeature.ASK_AI_TEXT, local.askTextUsed),
                        UsageLimitFeature.ASK_AI_PHOTO.firestoreField to mergedCount(UsageLimitFeature.ASK_AI_PHOTO, local.askPhotoUsed),
                        UsageLimitFeature.ASK_AI_PDF.firestoreField to mergedCount(UsageLimitFeature.ASK_AI_PDF, local.askPdfUsed),
                        UsageLimitFeature.GOOGLE_FORMS.firestoreField to mergedCount(UsageLimitFeature.GOOGLE_FORMS, local.googleFormsUsed),
                        "strikeCount" to mergedStrikes,
                        "banned" to mergedBanned,
                        "lastStrikeReason" to mergedReason,
                        "lastDeletedAccountAtMs" to mergedDeleteMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    tx.update(docRef, updateMap)
                }
                null
            }.await()
            lastCloudSyncMs = nowMs()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Log.w(TAG, "Cloud device guard push deferred: ${e.message}")
        }
    }

    internal fun resetForTests(context: Context? = null) {
        synchronized(lock) {
            lastCloudSyncMs = 0L
            context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()?.clear()?.apply()
            _enforcementFlow.value = AbuseEnforcementState.Clear
            _pendingDialogFlow.value = null
        }
        deviceHashOverrideForTest = null
        nowMsProviderForTest = null
        disableCloudSyncForTest = false
    }
}
