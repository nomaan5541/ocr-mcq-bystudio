package com.omnisolve.overlay.data

import android.content.Context
import androidx.annotation.Keep
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.omnisolve.overlay.R
import com.omnisolve.overlay.api.GoogleFormSolveResult
import com.omnisolve.overlay.api.GoogleFormSolvedItem
import com.omnisolve.overlay.api.GoogleFormsSolverEngine
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SubjectMode
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Keep
@IgnoreExtraProperties
data class LiveClassRoom(
    val roomCode: String = "",
    val roomName: String = "",
    val subject: String = SubjectMode.GENERAL.id,
    val hostUid: String = "",
    val hostName: String = "",
    val hostPhotoUrl: String = "",
    val quizUrl: String = "",
    val maxMembers: Int = LiveClassRoomRepository.MAX_ROOM_MEMBERS,
    val memberCount: Int = 1,
    val questionCount: Int = 0,
    val active: Boolean = true,
    val expiresAtMs: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        expiresAtMs > 0L && nowMs > expiresAtMs

    fun formattedCode(): String {
        val clean = roomCode.uppercase().filter { it.isLetterOrDigit() }.take(6)
        return if (clean.length == 6) "${clean.substring(0, 3)}-${clean.substring(3)}" else clean
    }

    fun remainingHours(nowMs: Long = System.currentTimeMillis()): Int {
        if (expiresAtMs <= nowMs) return 0
        return (((expiresAtMs - nowMs) + 3_599_999L) / 3_600_000L).toInt().coerceIn(0, 24)
    }
}

/**
 * Privacy-first room member profile. Stores ONLY displayName, circular avatar photoUrl,
 * role ("host" | "member"), and host-controlled approval status ("pending" | "approved" | "rejected").
 * Never stores or exposes email, phone number, or inspectable profile data.
 */
@Keep
@IgnoreExtraProperties
data class LiveRoomMember(
    val uid: String = "",
    val roomCode: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val role: String = ROLE_MEMBER,
    val status: String = STATUS_PENDING,
    val joinedAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    companion object {
        const val ROLE_HOST = "host"
        const val ROLE_MEMBER = "member"
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
    }
}

@Keep
@IgnoreExtraProperties
data class LiveRoomAnswer(
    @DocumentId
    val id: String = "",
    val roomCode: String = "",
    val questionNumber: Int = 1,
    val questionText: String = "",
    val optionsJson: String = "[]",
    val answerBadge: String = "",
    val shortAnswer: String = "",
    val explanation: String = "",
    val sourceLabel: String = "Host Solved",
    val sharedByUid: String = "",
    val sharedByName: String = "",
    val confirmCount: Int = 1,
    val disputeCount: Int = 0,
    val verifiedByAi: Boolean = false,
    val updatedByUid: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun parsedOptions(): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson<List<String>>(optionsJson, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * Shuffled-option-proof display summary for a live host-solved answer.
 * - For 1-word or short-phrase answers (<= 32 chars and <= 4 words): displays the exact word/text
 *   directly so randomized A/B/C/D ordering across students never confuses anyone.
 * - For lengthy answers: flags [isShortDirectAnswer] = false so the student's browser locates and
 *   highlights the exact matching option on their own screen.
 */
data class SmartRoomAnswerDisplay(
    val isShortDirectAnswer: Boolean,
    val directWordOrPhrase: String,
    val compactTopLabel: String,
    val matchTargetText: String
)

data class RoomJoinOutcome(
    val room: LiveClassRoom,
    val memberStatus: String
)

interface LiveClassRoomBackend {
    suspend fun createRoom(room: LiveClassRoom, hostMember: LiveRoomMember): Result<LiveClassRoom>
    suspend fun getRoom(roomCode: String): Result<LiveClassRoom?>
    suspend fun getMember(roomCode: String, uid: String): Result<LiveRoomMember?>
    suspend fun upsertMemberRequest(roomCode: String, member: LiveRoomMember): Result<LiveRoomMember>
    suspend fun updateMemberStatus(roomCode: String, targetUid: String, newStatus: String): Result<Unit>
    suspend fun deleteMember(roomCode: String, targetUid: String): Result<Unit>
    suspend fun updateRoomMemberCount(roomCode: String, newMemberCount: Int): Result<Unit>
    suspend fun updateRoomQuizUrl(roomCode: String, newQuizUrl: String): Result<Unit>
    suspend fun updateRoomQuestionCount(roomCode: String, newQuestionCount: Int): Result<Unit>
    suspend fun closeRoom(roomCode: String): Result<Unit>
    suspend fun upsertAnswer(roomCode: String, answerId: String, answer: LiveRoomAnswer): Result<Unit>
    suspend fun updateVote(roomCode: String, answerId: String, confirmDelta: Int, disputeDelta: Int, voterUid: String): Result<Unit>
    suspend fun applyAiVerification(
        roomCode: String,
        answerId: String,
        answerBadge: String,
        shortAnswer: String,
        explanation: String,
        verifierUid: String
    ): Result<Unit>
    fun observeRoom(roomCode: String): Flow<LiveClassRoom?>
    fun observeMembers(roomCode: String): Flow<List<LiveRoomMember>>
    fun observeAnswers(roomCode: String): Flow<List<LiveRoomAnswer>>
}

/**
 * Real-time Discord-style Live Class Room repository backed by Cloud Firestore
 * (`/liveClassRooms/{roomCode}`, `/members/{uid}`, and `/answers/{answerId}`).
 *
 * Security & privacy guarantees:
 * - Strictly capped at 40 members (`MAX_ROOM_MEMBERS = 40`).
 * - Host-only authority: ONLY the creator can lock/update the quiz URL, approve/remove members,
 *   broadcast answers, and close the room.
 * - Privacy-safe members: only `displayName` and `photoUrl` are stored/shown; zero email/phone PII.
 * - Uses the provisioned named database (`R.string.firestore_database_id`).
 * - Rooms auto-expire after 24 hours.
 */
object LiveClassRoomRepository {

    private const val PREFS_NAME = "OmniSolvePrefs"
    private const val PREF_ACTIVE_ROOM_CODE = "PREF_ACTIVE_CLASS_ROOM_CODE"
    private const val PREF_AUTO_SYNC_ENABLED = "PREF_CLASS_ROOM_AUTO_SYNC"
    private const val PREF_VOTED_PREFIX = "PREF_CLASS_ROOM_VOTED_"
    const val ROOM_TTL_MS = 24L * 60L * 60L * 1000L
    const val MAX_ROOM_MEMBERS = 40

    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val secureRandom = SecureRandom()
    private val gson = Gson()

    @Volatile
    internal var backendOverrideForTest: LiveClassRoomBackend? = null

    @Volatile
    internal var uidOverrideForTest: (() -> Pair<String, String>?)? = null

    @Volatile
    internal var photoUrlOverrideForTest: (() -> String)? = null

    fun normalizeRoomCode(raw: String): String =
        raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(6)

    fun formatRoomCode(raw: String): String {
        val clean = normalizeRoomCode(raw)
        return if (clean.length == 6) "${clean.substring(0, 3)}-${clean.substring(3)}" else clean
    }

    fun isValidRoomCode(raw: String): Boolean =
        normalizeRoomCode(raw).matches(Regex("^[A-Z0-9]{6}$"))

    fun generateRoomCode(): String = buildString(6) {
        repeat(6) {
            append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)])
        }
    }

    /**
     * Classifies whether a solved answer is short enough to show directly as the exact word/phrase
     * at the top of the quiz browser (e.g., "Mitochondria", "UDP", "O(log n)"), or lengthy enough
     * that each student's browser should locate & highlight the matching shuffled option on their page.
     */
    fun isShortDirectAnswer(answerText: String): Boolean {
        val clean = answerText.trim().replace(Regex("\\s+"), " ")
        if (clean.isBlank()) return true
        val words = clean.split(' ').filter { it.isNotBlank() }
        return clean.length <= 32 && words.size <= 4
    }

    private val PERSONAL_FIELD_KEYWORDS = listOf(
        "your name", "full name", "student name", "candidate name", "enter name",
        "name of the student", "name of student", "first name", "last name",
        "roll no", "roll number", "roll#", "reg no", "register no", "register number",
        "registration no", "registration number", "enrollment", "enrolment",
        "student id", "college id", "id number", "usn", "hall ticket", "admit card",
        "email address", "your email", "enter email", "e-mail",
        "phone number", "mobile number", "contact number", "whatsapp",
        "date of birth", "father's name", "fathers name", "mother's name", "mothers name",
        "home address", "college name", "institution name", "class & section",
        "branch & section", "department name"
    )

    private val EXACT_PERSONAL_PROMPTS = setOf(
        "name", "email", "roll", "roll no", "roll number", "reg no", "usn",
        "id", "student id", "phone", "mobile", "section", "branch", "department",
        "semester", "dob", "gender", "age", "class", "division", "batch"
    )

    /**
     * Prevents personal student form fields (Name, Roll Number, Reg No, Email, Section)
     * from ever being broadcast by the host into a Live Class Room. Every member keeps
     * their own separate personal inputs in their own browser session.
     *
     * Real MCQs (2+ options) are never treated as personal free-text fields.
     */
    fun isPersonalIdentityField(questionText: String, options: List<String> = emptyList()): Boolean {
        if (options.size >= 2) return false
        val cleaned = questionText
            .lowercase()
            .replace(Regex("[^a-z0-9'&\\s#]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isBlank()) return false
        if (cleaned in EXACT_PERSONAL_PROMPTS) return true
        // Strip leading question number like "1 " or "q1 "
        val stripped = cleaned.replace(Regex("^(q\\s*)?\\d+\\s+"), "").trim()
        if (stripped in EXACT_PERSONAL_PROMPTS) return true
        return PERSONAL_FIELD_KEYWORDS.any { kw -> stripped.contains(kw) }
    }

    fun formatSmartAnswerDisplay(answer: LiveRoomAnswer): SmartRoomAnswerDisplay {
        val rawWord = answer.shortAnswer.trim().replace(Regex("\\s+"), " ")
        val rawExpl = answer.explanation.trim().replace(Regex("\\s+"), " ")
        val effectiveText = rawWord
            .ifBlank { answer.answerBadge.trim().takeUnless { it == "?" || it.equals("Text", ignoreCase = true) }.orEmpty() }
            .ifBlank { rawExpl }
            .ifBlank { "?" }
        val isShort = isShortDirectAnswer(effectiveText)
        return if (isShort) {
            SmartRoomAnswerDisplay(
                isShortDirectAnswer = true,
                directWordOrPhrase = effectiveText,
                compactTopLabel = "Q${answer.questionNumber}: $effectiveText",
                matchTargetText = effectiveText
            )
        } else {
            val preview = effectiveText.take(28).trimEnd() + "…"
            SmartRoomAnswerDisplay(
                isShortDirectAnswer = false,
                directWordOrPhrase = preview,
                compactTopLabel = "Q${answer.questionNumber}: $preview",
                matchTargetText = effectiveText
            )
        }
    }

    fun getSavedActiveRoomCode(context: Context): String? {
        return try {
            val raw = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_ACTIVE_ROOM_CODE, null)
                ?.trim()
            raw?.takeIf { isValidRoomCode(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun saveActiveRoomCode(context: Context, roomCode: String?) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (roomCode.isNullOrBlank()) {
                prefs.edit().remove(PREF_ACTIVE_ROOM_CODE).apply()
            } else {
                prefs.edit().putString(PREF_ACTIVE_ROOM_CODE, normalizeRoomCode(roomCode)).apply()
            }
        } catch (_: Exception) {
        }
    }

    fun isAutoSyncEnabled(context: Context): Boolean {
        return try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_AUTO_SYNC_ENABLED, true)
        } catch (_: Exception) {
            true
        }
    }

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_AUTO_SYNC_ENABLED, enabled)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun getVotedAnswerIds(context: Context, roomCode: String): Set<String> {
        val code = normalizeRoomCode(roomCode)
        return try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getStringSet("$PREF_VOTED_PREFIX$code", emptySet())
                .orEmpty()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun markAnswerVoted(context: Context, roomCode: String, answerId: String) {
        val code = normalizeRoomCode(roomCode)
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val updated = getVotedAnswerIds(context, code).toMutableSet()
            updated.add(answerId)
            prefs.edit().putStringSet("$PREF_VOTED_PREFIX$code", updated).apply()
        } catch (_: Exception) {
        }
    }

    private data class ActorProfile(
        val uid: String,
        val displayName: String,
        val photoUrl: String
    )

    private fun requireActor(context: Context): ActorProfile {
        uidOverrideForTest?.invoke()?.let { (uid, name) ->
            return ActorProfile(
                uid = uid,
                displayName = name.take(60).ifBlank { "Student" },
                photoUrl = photoUrlOverrideForTest?.invoke().orEmpty().take(500)
            )
        }
        val user = try {
            Firebase.auth.currentUser
        } catch (_: Exception) {
            null
        } ?: throw IllegalStateException("Sign in with Google first to use Live Class Rooms.")
        val uid = user.uid.trim()
        if (uid.isBlank()) throw IllegalStateException("Sign in with Google first to use Live Class Rooms.")
        val friendlyName = user.displayName?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(60)
            ?: user.email?.substringBefore("@")?.take(60)
            ?: "Student"
        val safePhoto = user.photoUrl?.toString()?.trim().orEmpty().take(500)
        return ActorProfile(uid = uid, displayName = friendlyName, photoUrl = safePhoto)
    }

    private fun backend(context: Context): LiveClassRoomBackend {
        backendOverrideForTest?.let { return it }
        return FirestoreLiveClassRoomBackend(context.applicationContext)
    }

    fun computeAnswerId(questionText: String): String {
        val norm = questionText.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .ifBlank { "question" }
        val digest = MessageDigest.getInstance("SHA-256").digest(norm.toByteArray(Charsets.UTF_8))
        val shortHex = digest.take(12).joinToString("") { "%02x".format(it) }
        return "q_$shortHex"
    }

    suspend fun createRoom(
        context: Context,
        roomName: String,
        subject: SubjectMode,
        initialQuizUrl: String = "",
        nowMs: Long = System.currentTimeMillis()
    ): Result<LiveClassRoom> = withContext(Dispatchers.IO) {
        try {
            val actor = requireActor(context)
            val cleanName = roomName.trim().ifBlank { "${subject.label} Live Room" }.take(80)
            val normalizedUrl = if (initialQuizUrl.isBlank()) {
                ""
            } else {
                GoogleFormsSolverEngine.normalizeUniversalQuizUrl(initialQuizUrl).take(2048)
            }
            val code = generateRoomCode()
            val room = LiveClassRoom(
                roomCode = code,
                roomName = cleanName,
                subject = subject.id,
                hostUid = actor.uid,
                hostName = actor.displayName,
                hostPhotoUrl = actor.photoUrl,
                quizUrl = normalizedUrl,
                maxMembers = MAX_ROOM_MEMBERS,
                memberCount = 1,
                questionCount = 0,
                active = true,
                expiresAtMs = nowMs + ROOM_TTL_MS
            )
            val hostMember = LiveRoomMember(
                uid = actor.uid,
                roomCode = code,
                displayName = actor.displayName,
                photoUrl = actor.photoUrl,
                role = LiveRoomMember.ROLE_HOST,
                status = LiveRoomMember.STATUS_APPROVED
            )
            val res = backend(context).createRoom(room, hostMember)
            if (res.isSuccess) {
                saveActiveRoomCode(context, code)
            }
            res
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Requests entry into a Live Class Room by 6-char invite code.
     * - Enforces the strict 40-member capacity limit.
     * - If the user is the host or already approved, enters immediately.
     * - Otherwise submits a `"pending"` join request containing ONLY displayName + photoUrl
     *   so the host can approve or decline them in real time.
     */
    suspend fun requestJoinRoom(
        context: Context,
        rawCode: String,
        nowMs: Long = System.currentTimeMillis()
    ): Result<RoomJoinOutcome> = withContext(Dispatchers.IO) {
        try {
            val actor = requireActor(context)
            val code = normalizeRoomCode(rawCode)
            if (!isValidRoomCode(code)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Enter a valid 6-character room code (example: K7M-29Q).")
                )
            }
            val store = backend(context)
            val room = store.getRoom(code).getOrElse { return@withContext Result.failure(it) }
                ?: return@withContext Result.failure(
                    IllegalStateException("Room ${formatRoomCode(code)} not found. Double-check the code with the host.")
                )
            if (!room.active || room.isExpired(nowMs)) {
                return@withContext Result.failure(
                    IllegalStateException("Room ${formatRoomCode(code)} has ended or expired (rooms clear after 24 hours).")
                )
            }

            if (room.hostUid == actor.uid) {
                saveActiveRoomCode(context, code)
                return@withContext Result.success(RoomJoinOutcome(room, LiveRoomMember.STATUS_APPROVED))
            }

            val existingMember = store.getMember(code, actor.uid).getOrNull()
            if (existingMember?.status == LiveRoomMember.STATUS_APPROVED) {
                saveActiveRoomCode(context, code)
                return@withContext Result.success(RoomJoinOutcome(room, LiveRoomMember.STATUS_APPROVED))
            }

            if (room.memberCount >= MAX_ROOM_MEMBERS) {
                return@withContext Result.failure(
                    IllegalStateException("This room is full (${MAX_ROOM_MEMBERS}/${MAX_ROOM_MEMBERS} members max).")
                )
            }

            val pendingMember = LiveRoomMember(
                uid = actor.uid,
                roomCode = code,
                displayName = actor.displayName,
                photoUrl = actor.photoUrl,
                role = LiveRoomMember.ROLE_MEMBER,
                status = LiveRoomMember.STATUS_PENDING
            )
            store.upsertMemberRequest(code, pendingMember).getOrElse {
                return@withContext Result.failure(it)
            }
            saveActiveRoomCode(context, code)
            Result.success(RoomJoinOutcome(room, LiveRoomMember.STATUS_PENDING))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinRoom(
        context: Context,
        rawCode: String,
        nowMs: Long = System.currentTimeMillis()
    ): Result<LiveClassRoom> = requestJoinRoom(context, rawCode, nowMs).map { it.room }

    /**
     * Host-only action: approves a pending classmate request if the room has fewer than 40 members.
     */
    suspend fun approveMember(
        context: Context,
        roomCode: String,
        targetUid: String,
        currentApprovedCount: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val actor = requireActor(context)
            val code = normalizeRoomCode(roomCode)
            val store = backend(context)
            val room = store.getRoom(code).getOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Room no longer exists."))
            if (room.hostUid != actor.uid) {
                return@withContext Result.failure(IllegalStateException("Only the room host can approve members."))
            }
            val effectiveCount = maxOf(room.memberCount, currentApprovedCount)
            if (effectiveCount >= MAX_ROOM_MEMBERS) {
                return@withContext Result.failure(
                    IllegalStateException("Room reached the maximum limit of $MAX_ROOM_MEMBERS members.")
                )
            }
            store.updateMemberStatus(code, targetUid, LiveRoomMember.STATUS_APPROVED)
                .getOrElse { return@withContext Result.failure(it) }
            val nextCount = (effectiveCount + 1).coerceIn(1, MAX_ROOM_MEMBERS)
            store.updateRoomMemberCount(code, nextCount)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Host-only action: declines a join request or removes an existing member and syncs memberCount.
     */
    suspend fun rejectOrRemoveMember(
        context: Context,
        roomCode: String,
        targetUid: String,
        wasApproved: Boolean,
        currentApprovedCount: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val actor = requireActor(context)
            val code = normalizeRoomCode(roomCode)
            val store = backend(context)
            val room = store.getRoom(code).getOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Room no longer exists."))
            if (room.hostUid != actor.uid) {
                return@withContext Result.failure(IllegalStateException("Only the room host can manage members."))
            }
            if (targetUid == room.hostUid) {
                return@withContext Result.failure(IllegalStateException("Host cannot be removed from the room."))
            }
            store.deleteMember(code, targetUid).getOrElse { return@withContext Result.failure(it) }
            if (wasApproved) {
                val nextCount = (currentApprovedCount - 1).coerceIn(1, MAX_ROOM_MEMBERS)
                store.updateRoomMemberCount(code, nextCount)
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Host-only action: locks or changes the exam/quiz URL for everyone in the room.
     */
    suspend fun updateLockedQuizUrl(
        context: Context,
        roomCode: String,
        rawUrl: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val actor = requireActor(context)
            val code = normalizeRoomCode(roomCode)
            val store = backend(context)
            val room = store.getRoom(code).getOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Room not found."))
            if (room.hostUid != actor.uid) {
                return@withContext Result.failure(
                    IllegalStateException("Only the room host can set or change the exam link.")
                )
            }
            val normalized = GoogleFormsSolverEngine.normalizeUniversalQuizUrl(rawUrl).take(2048)
            if (normalized.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Enter a valid exam or quiz link first.")
                )
            }
            store.updateRoomQuizUrl(code, normalized).getOrElse { return@withContext Result.failure(it) }
            Result.success(normalized)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveRoom(context: Context, roomCode: String? = null) {
        val code = roomCode?.let { normalizeRoomCode(it) } ?: getSavedActiveRoomCode(context)
        saveActiveRoomCode(context, null)
        if (!code.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val actor = requireActor(context)
                    val store = backend(context)
                    val room = store.getRoom(code).getOrNull()
                    if (room != null && room.hostUid != actor.uid) {
                        store.deleteMember(code, actor.uid)
                    }
                }
            }
        }
    }

    suspend fun endRoom(context: Context, roomCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val actor = requireActor(context)
            val code = normalizeRoomCode(roomCode)
            val store = backend(context)
            val room = store.getRoom(code).getOrNull()
            if (room != null && room.hostUid != actor.uid) {
                return@withContext Result.failure(
                    IllegalStateException("Only the classmate who created this room can end it.")
                )
            }
            val res = store.closeRoom(code)
            if (res.isSuccess) {
                saveActiveRoomCode(context, null)
            }
            res
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeRoom(context: Context, roomCode: String): Flow<LiveClassRoom?> {
        return backend(context).observeRoom(normalizeRoomCode(roomCode))
    }

    fun observeMembers(context: Context, roomCode: String): Flow<List<LiveRoomMember>> {
        return backend(context).observeMembers(normalizeRoomCode(roomCode))
    }

    fun observeAnswers(context: Context, roomCode: String): Flow<List<LiveRoomAnswer>> {
        return backend(context).observeAnswers(normalizeRoomCode(roomCode))
    }

    /**
     * Host-only broadcast: streams a single solved MCQ into the active Live Class Room so every
     * approved member receives the exact answer word/text simultaneously.
     */
    suspend fun publishSingleAnswerIfActive(
        context: Context,
        questionText: String,
        answer: AnswerModel,
        options: List<String> = emptyList(),
        sourceLabel: String = "Host Solved",
        explicitRoomCode: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val code = explicitRoomCode?.let { normalizeRoomCode(it) } ?: getSavedActiveRoomCode(context)
            if (code.isNullOrBlank() || !isValidRoomCode(code)) {
                return@withContext Result.success(false)
            }
            if (explicitRoomCode == null && !isAutoSyncEnabled(context)) {
                return@withContext Result.success(false)
            }
            val actor = requireActor(context)
            val store = backend(context)
            val room = store.getRoom(code).getOrNull()
            if (room == null || !room.active || room.isExpired()) {
                return@withContext Result.success(false)
            }
            // Only the host broadcasts live room answers
            if (room.hostUid != actor.uid) {
                return@withContext Result.success(false)
            }
            val cleanQ = questionText.trim().ifBlank { "Live Exam Question" }.take(2000)
            // Never broadcast host's personal Name / Roll Number / Email fields to classmates
            if (isPersonalIdentityField(cleanQ, options)) {
                return@withContext Result.success(false)
            }
            val cleanBadge = answer.correctChoice.trim().ifBlank { "?" }.take(40)
            val answerId = computeAnswerId(cleanQ)
            val nextNum = (room.questionCount + 1).coerceIn(1, 200)
            val optionsJson = gson.toJson(options.take(10)).take(6000)
            val record = LiveRoomAnswer(
                id = answerId,
                roomCode = code,
                questionNumber = nextNum,
                questionText = cleanQ,
                optionsJson = optionsJson.ifBlank { "[]" },
                answerBadge = cleanBadge,
                shortAnswer = answer.shortAnswer.trim().take(600),
                explanation = answer.explanation.trim().take(1000),
                sourceLabel = sourceLabel.trim().ifBlank { "Host Solved" }.take(60),
                sharedByUid = actor.uid,
                sharedByName = actor.displayName,
                confirmCount = 1,
                disputeCount = 0,
                verifiedByAi = false,
                updatedByUid = actor.uid
            )
            store.upsertAnswer(code, answerId, record).getOrElse { return@withContext Result.failure(it) }
            store.updateRoomQuestionCount(code, nextNum)
            Result.success(true)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Host-only broadcast: streams all solved questions from the Universal Smart Quiz Browser
     * into the active Live Class Room so all approved members see the answers at the same time.
     */
    suspend fun publishQuizResultToRoom(
        context: Context,
        result: GoogleFormSolveResult,
        sourceLabel: String = "Host Solved",
        explicitRoomCode: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val code = explicitRoomCode?.let { normalizeRoomCode(it) } ?: getSavedActiveRoomCode(context)
            if (code.isNullOrBlank() || !isValidRoomCode(code)) {
                return@withContext Result.success(0)
            }
            val actor = requireActor(context)
            val store = backend(context)
            val room = store.getRoom(code).getOrNull()
            if (room == null || !room.active || room.isExpired()) {
                return@withContext Result.success(0)
            }
            // Only the host broadcasts live room answers
            if (room.hostUid != actor.uid) {
                return@withContext Result.success(0)
            }
            var pushed = 0
            for (item in result.items.take(60)) {
                val cleanQ = item.questionText.trim().ifBlank { continue }.take(2000)
                // Keep student personal fields (Name, Roll No, Reg No, Email) private per user
                if (isPersonalIdentityField(cleanQ, item.options)) continue
                val cleanBadge = item.answerLetter.trim().ifBlank { "?" }.take(40)
                val answerId = computeAnswerId(cleanQ)
                val optionsJson = gson.toJson(item.options.take(10)).take(6000)
                val record = LiveRoomAnswer(
                    id = answerId,
                    roomCode = code,
                    questionNumber = item.number.coerceIn(1, 200),
                    questionText = cleanQ,
                    optionsJson = optionsJson.ifBlank { "[]" },
                    answerBadge = cleanBadge,
                    shortAnswer = item.shortAnswer.trim().take(600),
                    explanation = item.explanation.trim().take(1000),
                    sourceLabel = sourceLabel.trim().ifBlank { "Host Solved" }.take(60),
                    sharedByUid = actor.uid,
                    sharedByName = actor.displayName,
                    confirmCount = 1,
                    disputeCount = 0,
                    verifiedByAi = false,
                    updatedByUid = actor.uid
                )
                if (store.upsertAnswer(code, answerId, record).isSuccess) {
                    pushed++
                }
            }
            if (pushed > 0) {
                val newTotal = maxOf(room.questionCount, result.items.size).coerceIn(1, 200)
                store.updateRoomQuestionCount(code, newTotal)
            }
            Result.success(pushed)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun voteAnswer(
        context: Context,
        roomCode: String,
        answerId: String,
        confirm: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val actor = requireActor(context)
            val code = normalizeRoomCode(roomCode)
            if (answerId in getVotedAnswerIds(context, code)) {
                return@withContext Result.success(Unit)
            }
            val res = backend(context).updateVote(
                roomCode = code,
                answerId = answerId,
                confirmDelta = if (confirm) 1 else 0,
                disputeDelta = if (confirm) 0 else 1,
                voterUid = actor.uid
            )
            if (res.isSuccess) {
                markAnswerVoted(context, code, answerId)
            }
            res
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Host-only AI tie-breaker verification on a room question.
     */
    suspend fun verifyAnswerWithAiTieBreaker(
        context: Context,
        roomCode: String,
        answer: LiveRoomAnswer,
        subjectMode: SubjectMode = SubjectMode.GENERAL
    ): Result<LiveRoomAnswer> = withContext(Dispatchers.IO) {
        try {
            val actor = requireActor(context)
            val code = normalizeRoomCode(roomCode)
            val store = backend(context)
            val room = store.getRoom(code).getOrNull()
            if (room != null && room.hostUid != actor.uid) {
                return@withContext Result.failure(
                    IllegalStateException("Only the room host can update verified room answers.")
                )
            }
            val solvedItem = GoogleFormSolvedItem(
                number = answer.questionNumber,
                questionText = answer.questionText,
                options = answer.parsedOptions(),
                answerLetter = answer.answerBadge,
                shortAnswer = answer.shortAnswer,
                explanation = answer.explanation
            )
            val reVerified = GoogleFormsSolverEngine.reSolveSingleItem(
                context = context,
                item = solvedItem,
                subjectMode = subjectMode
            )
            val cleanBadge = reVerified.answerLetter.trim().ifBlank { answer.answerBadge }.take(40)
            val cleanShort = reVerified.shortAnswer.trim().take(600)
            val cleanExpl = reVerified.explanation.trim().ifBlank { "AI verified this answer." }.take(1000)

            store.applyAiVerification(
                roomCode = code,
                answerId = answer.id,
                answerBadge = cleanBadge,
                shortAnswer = cleanShort,
                explanation = cleanExpl,
                verifierUid = actor.uid
            ).getOrElse { return@withContext Result.failure(it) }

            Result.success(
                answer.copy(
                    answerBadge = cleanBadge,
                    shortAnswer = cleanShort,
                    explanation = cleanExpl,
                    sourceLabel = "AI Verified",
                    verifiedByAi = true,
                    updatedByUid = actor.uid
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private class FirestoreLiveClassRoomBackend(
    private val appContext: Context
) : LiveClassRoomBackend {

    private val db: FirebaseFirestore by lazy {
        val dbId = appContext.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }

    private fun roomDoc(roomCode: String) =
        db.collection("liveClassRooms").document(roomCode)

    private fun memberDoc(roomCode: String, uid: String) =
        roomDoc(roomCode).collection("members").document(uid)

    private fun answerDoc(roomCode: String, answerId: String) =
        roomDoc(roomCode).collection("answers").document(answerId)

    override suspend fun createRoom(room: LiveClassRoom, hostMember: LiveRoomMember): Result<LiveClassRoom> {
        val path = "liveClassRooms/${room.roomCode}"
        return try {
            val payload = hashMapOf<String, Any>(
                "roomCode" to room.roomCode,
                "roomName" to room.roomName,
                "subject" to room.subject,
                "hostUid" to room.hostUid,
                "hostName" to room.hostName,
                "hostPhotoUrl" to room.hostPhotoUrl,
                "quizUrl" to room.quizUrl,
                "maxMembers" to LiveClassRoomRepository.MAX_ROOM_MEMBERS,
                "memberCount" to 1,
                "questionCount" to 0,
                "active" to true,
                "expiresAtMs" to room.expiresAtMs,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            roomDoc(room.roomCode).set(payload).await()

            val hostMemberPayload = hashMapOf<String, Any>(
                "uid" to hostMember.uid,
                "roomCode" to room.roomCode,
                "displayName" to hostMember.displayName,
                "photoUrl" to hostMember.photoUrl,
                "role" to LiveRoomMember.ROLE_HOST,
                "status" to LiveRoomMember.STATUS_APPROVED,
                "joinedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            memberDoc(room.roomCode, hostMember.uid).set(hostMemberPayload).await()
            Result.success(room)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path, Firebase.auth)
            Result.failure(e)
        }
    }

    override suspend fun getRoom(roomCode: String): Result<LiveClassRoom?> {
        val path = "liveClassRooms/$roomCode"
        return try {
            val snap = roomDoc(roomCode).get().await()
            if (!snap.exists()) return Result.success(null)
            val model = snap.toObject(
                LiveClassRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(model)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path, Firebase.auth)
            Result.failure(e)
        }
    }

    override suspend fun getMember(roomCode: String, uid: String): Result<LiveRoomMember?> {
        val path = "liveClassRooms/$roomCode/members/$uid"
        return try {
            val snap = memberDoc(roomCode, uid).get().await()
            if (!snap.exists()) return Result.success(null)
            val model = snap.toObject(
                LiveRoomMember::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(model)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path, Firebase.auth)
            Result.failure(e)
        }
    }

    override suspend fun upsertMemberRequest(roomCode: String, member: LiveRoomMember): Result<LiveRoomMember> {
        val path = "liveClassRooms/$roomCode/members/${member.uid}"
        return try {
            val docRef = memberDoc(roomCode, member.uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val curStatus = existing.getString("status") ?: LiveRoomMember.STATUS_PENDING
                if (curStatus == LiveRoomMember.STATUS_REJECTED) {
                    docRef.update(
                        mapOf(
                            "status" to LiveRoomMember.STATUS_PENDING,
                            "displayName" to member.displayName,
                            "photoUrl" to member.photoUrl,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    docRef.update(
                        mapOf(
                            "displayName" to member.displayName,
                            "photoUrl" to member.photoUrl,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } else {
                val payload = hashMapOf<String, Any>(
                    "uid" to member.uid,
                    "roomCode" to roomCode,
                    "displayName" to member.displayName,
                    "photoUrl" to member.photoUrl,
                    "role" to LiveRoomMember.ROLE_MEMBER,
                    "status" to LiveRoomMember.STATUS_PENDING,
                    "joinedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(member)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path, Firebase.auth)
            Result.failure(e)
        }
    }

    override suspend fun updateMemberStatus(roomCode: String, targetUid: String, newStatus: String): Result<Unit> {
        val path = "liveClassRooms/$roomCode/members/$targetUid"
        return try {
            memberDoc(roomCode, targetUid).update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, Firebase.auth)
            Result.failure(e)
        }
    }

    override suspend fun deleteMember(roomCode: String, targetUid: String): Result<Unit> {
        val path = "liveClassRooms/$roomCode/members/$targetUid"
        return try {
            memberDoc(roomCode, targetUid).delete().await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path, Firebase.auth)
            Result.failure(e)
        }
    }

    override suspend fun updateRoomMemberCount(roomCode: String, newMemberCount: Int): Result<Unit> {
        val path = "liveClassRooms/$roomCode"
        return try {
            roomDoc(roomCode).update(
                mapOf(
                    "memberCount" to newMemberCount.coerceIn(1, LiveClassRoomRepository.MAX_ROOM_MEMBERS),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, Firebase.auth)
            Result.failure(e)
        }
    }

    override suspend fun updateRoomQuizUrl(roomCode: String, newQuizUrl: String): Result<Unit> {
        val path = "liveClassRooms/$roomCode"
        return try {
            roomDoc(roomCode).update(
                mapOf(
                    "quizUrl" to newQuizUrl.take(2048),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, Firebase.auth)
            Result.failure(e)
        }
    }

    override suspend fun updateRoomQuestionCount(roomCode: String, newQuestionCount: Int): Result<Unit> {
        val path = "liveClassRooms/$roomCode"
        return try {
            roomDoc(roomCode).update(
                mapOf(
                    "questionCount" to newQuestionCount.coerceIn(0, 200),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, Firebase.auth)
            Result.failure(e)
        }
    }

    override suspend fun closeRoom(roomCode: String): Result<Unit> {
        val path = "liveClassRooms/$roomCode"
        return try {
            roomDoc(roomCode).update(
                mapOf(
                    "active" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, Firebase.auth)
            Result.failure(e)
        }
    }

    override suspend fun upsertAnswer(roomCode: String, answerId: String, answer: LiveRoomAnswer): Result<Unit> {
        val path = "liveClassRooms/$roomCode/answers/$answerId"
        return try {
            val docRef = answerDoc(roomCode, answerId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val curConfirm = (existing.getLong("confirmCount") ?: 0L).toInt()
                docRef.update(
                    mapOf(
                        "answerBadge" to answer.answerBadge,
                        "shortAnswer" to answer.shortAnswer,
                        "explanation" to answer.explanation,
                        "sourceLabel" to answer.sourceLabel,
                        "confirmCount" to (curConfirm + 1).coerceAtMost(500),
                        "updatedByUid" to answer.updatedByUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = hashMapOf<String, Any>(
                    "roomCode" to answer.roomCode,
                    "questionNumber" to answer.questionNumber,
                    "questionText" to answer.questionText,
                    "optionsJson" to answer.optionsJson,
                    "answerBadge" to answer.answerBadge,
                    "shortAnswer" to answer.shortAnswer,
                    "explanation" to answer.explanation,
                    "sourceLabel" to answer.sourceLabel,
                    "sharedByUid" to answer.sharedByUid,
                    "sharedByName" to answer.sharedByName,
                    "confirmCount" to answer.confirmCount.coerceIn(0, 500),
                    "disputeCount" to answer.disputeCount.coerceIn(0, 500),
                    "verifiedByAi" to answer.verifiedByAi,
                    "updatedByUid" to answer.updatedByUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path, Firebase.auth)
            Result.failure(e)
        }
    }

    override suspend fun updateVote(
        roomCode: String,
        answerId: String,
        confirmDelta: Int,
        disputeDelta: Int,
        voterUid: String
    ): Result<Unit> {
        val path = "liveClassRooms/$roomCode/answers/$answerId"
        return try {
            val updates = hashMapOf<String, Any>(
                "updatedByUid" to voterUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (confirmDelta > 0) updates["confirmCount"] = FieldValue.increment(confirmDelta.toLong())
            if (disputeDelta > 0) updates["disputeCount"] = FieldValue.increment(disputeDelta.toLong())
            answerDoc(roomCode, answerId).update(updates).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, Firebase.auth)
            Result.failure(e)
        }
    }

    override suspend fun applyAiVerification(
        roomCode: String,
        answerId: String,
        answerBadge: String,
        shortAnswer: String,
        explanation: String,
        verifierUid: String
    ): Result<Unit> {
        val path = "liveClassRooms/$roomCode/answers/$answerId"
        return try {
            answerDoc(roomCode, answerId).update(
                mapOf(
                    "answerBadge" to answerBadge,
                    "shortAnswer" to shortAnswer,
                    "explanation" to explanation,
                    "sourceLabel" to "AI Verified",
                    "verifiedByAi" to true,
                    "updatedByUid" to verifierUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, Firebase.auth)
            Result.failure(e)
        }
    }

    override fun observeRoom(roomCode: String): Flow<LiveClassRoom?> = callbackFlow {
        val path = "liveClassRooms/$roomCode"
        val reg = roomDoc(roomCode).addSnapshotListener { snap, err ->
            if (err != null) {
                handleFirestoreError(err, OperationType.GET, path, Firebase.auth)
                close(err)
                return@addSnapshotListener
            }
            val model = snap?.takeIf { it.exists() }?.toObject(
                LiveClassRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            trySend(model)
        }
        awaitClose { reg.remove() }
    }

    override fun observeMembers(roomCode: String): Flow<List<LiveRoomMember>> = callbackFlow {
        val path = "liveClassRooms/$roomCode/members"
        val reg = roomDoc(roomCode)
            .collection("members")
            .orderBy("joinedAt", Query.Direction.ASCENDING)
            .limit(80)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, path, Firebase.auth)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().mapNotNull { doc ->
                    doc.toObject(
                        LiveRoomMember::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override fun observeAnswers(roomCode: String): Flow<List<LiveRoomAnswer>> = callbackFlow {
        val path = "liveClassRooms/$roomCode/answers"
        val reg = roomDoc(roomCode)
            .collection("answers")
            .orderBy("questionNumber", Query.Direction.ASCENDING)
            .limit(200)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.LIST, path, Firebase.auth)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().mapNotNull { doc ->
                    doc.toObject(
                        LiveRoomAnswer::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.copy(id = doc.id)
                }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }
}
