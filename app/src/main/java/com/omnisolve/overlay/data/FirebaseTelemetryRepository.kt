package com.omnisolve.overlay.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import com.omnisolve.overlay.BuildConfig
import com.omnisolve.overlay.R
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object TelemetryFeature {
    const val DYNAMIC_ISLAND = "DYNAMIC_ISLAND"
    const val GOOGLE_FORMS = "GOOGLE_FORMS"
    const val PHOTO_SOLVER = "PHOTO_SOLVER"
    const val PDF_SOLVER = "PDF_SOLVER"
    const val PRACTICE = "PRACTICE"
    const val APP_OPEN = "APP_OPEN"
}

object TelemetryBackend {
    const val FIREBASE_AI = "FIREBASE_AI"
    const val CLOUDFLARE_EDGE = "CLOUDFLARE_EDGE"
    const val SHARED_CACHE = "SHARED_CACHE"
    const val LOCAL_CACHE = "LOCAL_CACHE"
    const val FAILED = "FAILED"
    const val NONE = "NONE"

    fun classifyFromSourceLabel(sourceLabel: String?): String {
        val raw = sourceLabel.orEmpty().lowercase()
        return when {
            raw.contains("local") || raw.contains("offline") || raw.contains("room") -> LOCAL_CACHE
            raw.contains("shared") || raw.contains("crowd") || raw.contains("cloud cache") || raw.contains("class cache") || raw.contains("instant cache") -> SHARED_CACHE
            raw.contains("cloudflare") || raw.contains("workers") || raw.contains("llama") || raw.contains("qwen") -> CLOUDFLARE_EDGE
            raw.contains("firebase") || raw.contains("gemini") || raw.contains("vision") || raw.contains("prism") -> FIREBASE_AI
            raw.isBlank() -> FAILED
            else -> FIREBASE_AI
        }
    }
}

object TelemetryStatus {
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
    const val CACHE_HIT = "CACHE_HIT"
}

object TelemetrySessionSource {
    const val APP_FOREGROUND = "APP_FOREGROUND"
    const val DYNAMIC_ISLAND = "DYNAMIC_ISLAND"
}

@Keep
@IgnoreExtraProperties
data class UserActivityProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val totalRequests: Long = 0L,
    val firebaseRequests: Long = 0L,
    val cloudflareRequests: Long = 0L,
    val cacheHits: Long = 0L,
    val failedRequests: Long = 0L,
    val totalLatencyMs: Long = 0L,
    val avgLatencyMs: Long = 0L,
    val totalActiveSeconds: Long = 0L,
    val activeSessionsCount: Long = 0L,
    val lastFeatureUsed: String = TelemetryFeature.APP_OPEN,
    val lastBackendUsed: String = TelemetryBackend.NONE,
    val lastActiveAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserRequestLog(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val feature: String = TelemetryFeature.DYNAMIC_ISLAND,
    val backend: String = TelemetryBackend.FIREBASE_AI,
    val modelUsed: String = "",
    val latencyMs: Long = 0L,
    val status: String = TelemetryStatus.SUCCESS,
    val questionPreview: String = "",
    val answerBadge: String = "",
    val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserActiveSession(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val source: String = TelemetrySessionSource.APP_FOREGROUND,
    val durationSeconds: Long = 1L,
    val requestsCount: Long = 0L,
    val deviceModel: String = "",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val endedAt: Timestamp? = null
)

/**
 * Firebase Firestore telemetry & live backend dashboard repository.
 *
 * Writes per-user profiles (`/users/{uid}`), immutable per-request logs
 * (`/users/{uid}/requestLogs/{requestId}`), and active duration sessions
 * (`/users/{uid}/activeSessions/{sessionId}`) into the configured named
 * Firestore database so the developer can monitor every user's requests,
 * backend routing, latency, and active time directly in the Firebase Console
 * and in-app Status Dashboard.
 */
object FirebaseTelemetryRepository {

    private const val TAG = "PrismTelemetryRepo"
    private const val USERS_COLLECTION = "users"
    private const val REQUEST_LOGS_SUBCOLLECTION = "requestLogs"
    private const val ACTIVE_SESSIONS_SUBCOLLECTION = "activeSessions"

    @Volatile
    private var cachedDb: FirebaseFirestore? = null

    private fun getDatabase(context: Context): FirebaseFirestore {
        cachedDb?.let { return it }
        val dbId = context.getString(R.string.firestore_database_id).trim()
        require(dbId.isNotBlank()) { "Missing firestore_database_id" }
        val db = FirebaseFirestore.getInstance(dbId)
        cachedDb = db
        return db
    }

    private fun currentUser(): FirebaseUser? {
        return try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeDeviceModel(): String {
        val mfg = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val combined = if (model.startsWith(mfg, ignoreCase = true)) model else "$mfg $model"
        return combined.trim().ifBlank { "Android Device" }.take(100)
    }

    private fun safeDocId(prefix: String): String {
        return "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(20)}"
    }

    private fun sanitizeFeature(raw: String): String = when (raw) {
        TelemetryFeature.DYNAMIC_ISLAND,
        TelemetryFeature.GOOGLE_FORMS,
        TelemetryFeature.PHOTO_SOLVER,
        TelemetryFeature.PDF_SOLVER,
        TelemetryFeature.PRACTICE,
        TelemetryFeature.APP_OPEN -> raw
        else -> TelemetryFeature.DYNAMIC_ISLAND
    }

    private fun sanitizeRequestFeature(raw: String): String = when (raw) {
        TelemetryFeature.DYNAMIC_ISLAND,
        TelemetryFeature.GOOGLE_FORMS,
        TelemetryFeature.PHOTO_SOLVER,
        TelemetryFeature.PDF_SOLVER,
        TelemetryFeature.PRACTICE -> raw
        else -> TelemetryFeature.DYNAMIC_ISLAND
    }

    private fun sanitizeBackend(raw: String): String = when (raw) {
        TelemetryBackend.FIREBASE_AI,
        TelemetryBackend.CLOUDFLARE_EDGE,
        TelemetryBackend.SHARED_CACHE,
        TelemetryBackend.LOCAL_CACHE,
        TelemetryBackend.FAILED -> raw
        else -> TelemetryBackend.FIREBASE_AI
    }

    private fun sanitizeStatus(raw: String): String = when (raw) {
        TelemetryStatus.SUCCESS,
        TelemetryStatus.FAILED,
        TelemetryStatus.CACHE_HIT -> raw
        else -> TelemetryStatus.SUCCESS
    }

    private fun sanitizeSessionSource(raw: String): String = when (raw) {
        TelemetrySessionSource.APP_FOREGROUND,
        TelemetrySessionSource.DYNAMIC_ISLAND -> raw
        else -> TelemetrySessionSource.APP_FOREGROUND
    }

    fun handleFirestoreError(error: Throwable, operation: String): String {
        if (error is FirebaseFirestoreException) {
            return when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Cloud dashboard permission denied ($operation). Verify Google Sign-In."
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "Cloud dashboard temporarily offline ($operation). Syncs automatically when back online."
                FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    "Sign in with Google to sync live cloud telemetry."
                else -> "Cloud telemetry error ($operation): ${error.code.name}"
            }
        }
        return "Cloud telemetry error ($operation): ${error.message ?: "unknown"}"
    }

    /**
     * Ensures `/users/{uid}` exists and refreshes display name, email, app version, and lastActiveAt.
     */
    suspend fun ensureUserProfileSynced(
        context: Context,
        user: FirebaseUser? = currentUser()
    ): Result<Unit> = runCatching {
        val activeUser = user ?: return@runCatching
        val uid = activeUser.uid
        if (uid.isBlank()) return@runCatching

        val db = getDatabase(context)
        val userRef = db.collection(USERS_COLLECTION).document(uid)
        val displayName = activeUser.displayName.orEmpty().trim().take(120)
        val email = activeUser.email.orEmpty().trim().take(254)
        val appVersion = BuildConfig.VERSION_NAME.ifBlank { "3.5.2" }.take(32)

        db.runTransaction { tx ->
            val snap = tx.get(userRef)
            if (!snap.exists()) {
                val payload = hashMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "appVersion" to appVersion,
                    "totalRequests" to 0L,
                    "firebaseRequests" to 0L,
                    "cloudflareRequests" to 0L,
                    "cacheHits" to 0L,
                    "failedRequests" to 0L,
                    "totalLatencyMs" to 0L,
                    "avgLatencyMs" to 0L,
                    "totalActiveSeconds" to 0L,
                    "activeSessionsCount" to 0L,
                    "lastFeatureUsed" to TelemetryFeature.APP_OPEN,
                    "lastBackendUsed" to TelemetryBackend.NONE,
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                tx.set(userRef, payload)
            } else {
                tx.update(
                    userRef,
                    mapOf(
                        "displayName" to displayName,
                        "email" to email,
                        "appVersion" to appVersion,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            null
        }.await()
    }.onFailure { err ->
        Log.w(TAG, handleFirestoreError(err, "ensureUserProfileSynced"))
    }

    /**
     * Records a single solve request into `/users/{uid}/requestLogs/{requestId}` and
     * updates the user's aggregate profile counters in `/users/{uid}`.
     */
    suspend fun recordSolveRequest(
        context: Context,
        feature: String,
        backend: String,
        modelUsed: String,
        latencyMs: Long,
        status: String,
        questionPreview: String,
        answerBadge: String,
        user: FirebaseUser? = currentUser()
    ): Result<Unit> = runCatching {
        val activeUser = user ?: return@runCatching
        val uid = activeUser.uid
        if (uid.isBlank()) return@runCatching

        val db = getDatabase(context)
        val safeFeature = sanitizeRequestFeature(feature)
        val safeBackend = sanitizeBackend(backend)
        val safeStatus = sanitizeStatus(status)
        val clampedLatency = latencyMs.coerceIn(0L, 120_000L)
        val cleanModel = modelUsed.trim().take(120)
        val cleanPreview = questionPreview.replace(Regex("\\s+"), " ").trim().take(240)
        val cleanBadge = answerBadge.trim().take(48)
        val displayName = activeUser.displayName.orEmpty().trim().take(120)
        val email = activeUser.email.orEmpty().trim().take(254)
        val appVersion = BuildConfig.VERSION_NAME.ifBlank { "3.5.2" }.take(32)

        val userRef = db.collection(USERS_COLLECTION).document(uid)
        val logRef = userRef.collection(REQUEST_LOGS_SUBCOLLECTION).document(safeDocId("req"))

        val logPayload = hashMapOf<String, Any>(
            "userId" to uid,
            "userEmail" to email,
            "feature" to safeFeature,
            "backend" to safeBackend,
            "modelUsed" to cleanModel,
            "latencyMs" to clampedLatency,
            "status" to safeStatus,
            "questionPreview" to cleanPreview,
            "answerBadge" to cleanBadge,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.runTransaction { tx ->
            val snap = tx.get(userRef)
            val prevTotal = snap.getLong("totalRequests") ?: 0L
            val prevFirebase = snap.getLong("firebaseRequests") ?: 0L
            val prevCloudflare = snap.getLong("cloudflareRequests") ?: 0L
            val prevCache = snap.getLong("cacheHits") ?: 0L
            val prevFailed = snap.getLong("failedRequests") ?: 0L
            val prevTotalLatency = snap.getLong("totalLatencyMs") ?: 0L
            val prevActiveSeconds = snap.getLong("totalActiveSeconds") ?: 0L
            val prevSessions = snap.getLong("activeSessionsCount") ?: 0L

            val nextTotal = (prevTotal + 1L).coerceAtMost(10_000_000L)
            val nextFirebase = prevFirebase + if (safeBackend == TelemetryBackend.FIREBASE_AI) 1L else 0L
            val nextCloudflare = prevCloudflare + if (safeBackend == TelemetryBackend.CLOUDFLARE_EDGE) 1L else 0L
            val nextCache = prevCache + if (
                safeStatus == TelemetryStatus.CACHE_HIT ||
                safeBackend == TelemetryBackend.SHARED_CACHE ||
                safeBackend == TelemetryBackend.LOCAL_CACHE
            ) 1L else 0L
            val nextFailed = prevFailed + if (safeStatus == TelemetryStatus.FAILED) 1L else 0L
            val nextTotalLatency = prevTotalLatency + clampedLatency
            val nextAvgLatency = (nextTotalLatency / nextTotal.coerceAtLeast(1L)).coerceIn(0L, 120_000L)

            if (!snap.exists()) {
                tx.set(
                    userRef,
                    hashMapOf<String, Any>(
                        "userId" to uid,
                        "displayName" to displayName,
                        "email" to email,
                        "appVersion" to appVersion,
                        "totalRequests" to nextTotal,
                        "firebaseRequests" to nextFirebase,
                        "cloudflareRequests" to nextCloudflare,
                        "cacheHits" to nextCache,
                        "failedRequests" to nextFailed,
                        "totalLatencyMs" to nextTotalLatency,
                        "avgLatencyMs" to nextAvgLatency,
                        "totalActiveSeconds" to prevActiveSeconds,
                        "activeSessionsCount" to prevSessions,
                        "lastFeatureUsed" to safeFeature,
                        "lastBackendUsed" to safeBackend,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                tx.update(
                    userRef,
                    mapOf(
                        "displayName" to displayName,
                        "email" to email,
                        "appVersion" to appVersion,
                        "totalRequests" to nextTotal,
                        "firebaseRequests" to nextFirebase,
                        "cloudflareRequests" to nextCloudflare,
                        "cacheHits" to nextCache,
                        "failedRequests" to nextFailed,
                        "totalLatencyMs" to nextTotalLatency,
                        "avgLatencyMs" to nextAvgLatency,
                        "lastFeatureUsed" to safeFeature,
                        "lastBackendUsed" to safeBackend,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            tx.set(logRef, logPayload)
            null
        }.await()
    }.onFailure { err ->
        Log.w(TAG, handleFirestoreError(err, "recordSolveRequest"))
    }

    /**
     * Records an active foreground app or Dynamic Island session duration slice.
     */
    suspend fun recordActiveSession(
        context: Context,
        source: String,
        durationSeconds: Long,
        requestsCount: Long = 0L,
        user: FirebaseUser? = currentUser()
    ): Result<Unit> = runCatching {
        val activeUser = user ?: return@runCatching
        val uid = activeUser.uid
        if (uid.isBlank()) return@runCatching

        val clampedSeconds = durationSeconds.coerceIn(1L, 86_400L)
        if (clampedSeconds < 2L) return@runCatching

        val db = getDatabase(context)
        val safeSource = sanitizeSessionSource(source)
        val clampedRequests = requestsCount.coerceIn(0L, 100_000L)
        val displayName = activeUser.displayName.orEmpty().trim().take(120)
        val email = activeUser.email.orEmpty().trim().take(254)
        val appVersion = BuildConfig.VERSION_NAME.ifBlank { "3.5.2" }.take(32)
        val deviceModel = sanitizeDeviceModel()

        val userRef = db.collection(USERS_COLLECTION).document(uid)
        val sessionRef = userRef.collection(ACTIVE_SESSIONS_SUBCOLLECTION).document(safeDocId("sess"))

        val sessionPayload = hashMapOf<String, Any>(
            "userId" to uid,
            "userEmail" to email,
            "source" to safeSource,
            "durationSeconds" to clampedSeconds,
            "requestsCount" to clampedRequests,
            "deviceModel" to deviceModel,
            "appVersion" to appVersion,
            "endedAt" to FieldValue.serverTimestamp()
        )

        db.runTransaction { tx ->
            val snap = tx.get(userRef)
            val prevActiveSeconds = snap.getLong("totalActiveSeconds") ?: 0L
            val prevSessions = snap.getLong("activeSessionsCount") ?: 0L
            val nextActiveSeconds = (prevActiveSeconds + clampedSeconds).coerceAtMost(1_000_000_000L)
            val nextSessions = (prevSessions + 1L).coerceAtMost(10_000_000L)

            if (!snap.exists()) {
                tx.set(
                    userRef,
                    hashMapOf<String, Any>(
                        "userId" to uid,
                        "displayName" to displayName,
                        "email" to email,
                        "appVersion" to appVersion,
                        "totalRequests" to 0L,
                        "firebaseRequests" to 0L,
                        "cloudflareRequests" to 0L,
                        "cacheHits" to 0L,
                        "failedRequests" to 0L,
                        "totalLatencyMs" to 0L,
                        "avgLatencyMs" to 0L,
                        "totalActiveSeconds" to nextActiveSeconds,
                        "activeSessionsCount" to nextSessions,
                        "lastFeatureUsed" to if (safeSource == TelemetrySessionSource.DYNAMIC_ISLAND) {
                            TelemetryFeature.DYNAMIC_ISLAND
                        } else {
                            TelemetryFeature.APP_OPEN
                        },
                        "lastBackendUsed" to TelemetryBackend.NONE,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                tx.update(
                    userRef,
                    mapOf(
                        "displayName" to displayName,
                        "email" to email,
                        "appVersion" to appVersion,
                        "totalActiveSeconds" to nextActiveSeconds,
                        "activeSessionsCount" to nextSessions,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            tx.set(sessionRef, sessionPayload)
            null
        }.await()
    }.onFailure { err ->
        Log.w(TAG, handleFirestoreError(err, "recordActiveSession"))
    }

    /**
     * Real-time Firestore listener for the signed-in user's activity dashboard profile.
     */
    fun observeUserProfile(context: Context, userId: String): Flow<UserActivityProfile?> = callbackFlow {
        if (userId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val db = getDatabase(context)
        val listener = db.collection(USERS_COLLECTION)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val profile = snapshot.toObject(
                    UserActivityProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                trySend(profile)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Real-time Firestore listener for the signed-in user's most recent solve requests.
     */
    fun observeRecentRequestLogs(
        context: Context,
        userId: String,
        limit: Long = 20L
    ): Flow<List<UserRequestLog>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val db = getDatabase(context)
        val listener = db.collection(USERS_COLLECTION)
            .document(userId)
            .collection(REQUEST_LOGS_SUBCOLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.coerceIn(1L, 50L))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val logs = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(
                        UserRequestLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.copy(id = doc.id)
                }.orEmpty()
                trySend(logs)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Deletes all user telemetry documents during Play-compliant account deletion.
     */
    suspend fun deleteUserTelemetry(context: Context, userId: String): Result<Unit> = runCatching {
        if (userId.isBlank()) return@runCatching
        val db = getDatabase(context)
        val userRef = db.collection(USERS_COLLECTION).document(userId)

        val logs = userRef.collection(REQUEST_LOGS_SUBCOLLECTION).limit(200).get().await()
        if (!logs.isEmpty) {
            val batch = db.batch()
            logs.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }

        val sessions = userRef.collection(ACTIVE_SESSIONS_SUBCOLLECTION).limit(200).get().await()
        if (!sessions.isEmpty) {
            val batch = db.batch()
            sessions.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }

        val profileSnap = userRef.get().await()
        if (profileSnap.exists()) {
            userRef.delete().await()
        }
    }.onFailure { err ->
        Log.w(TAG, handleFirestoreError(err, "deleteUserTelemetry"))
    }
}
