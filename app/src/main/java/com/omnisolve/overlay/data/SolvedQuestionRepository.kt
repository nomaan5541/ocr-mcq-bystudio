package com.omnisolve.overlay.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.omnisolve.overlay.R
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Cloud Firestore repository for `/users/{userId}/solvedQuestions/{questionId}`.
 * Uses constructor-injected `FirebaseFirestore` (for emulator testing) and resolves the
 * provisioned named database ID (`R.string.firestore_database_id`) in production.
 */
class SolvedQuestionRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {

    constructor(context: Context) : this(
        db = resolveFirestore(context),
        auth = Firebase.auth
    )

    companion object {
        private fun resolveFirestore(context: Context): FirebaseFirestore {
            val appCtx = context.applicationContext
            val databaseId = appCtx.getString(R.string.firestore_database_id)
            return FirebaseFirestore.getInstance(databaseId)
        }
    }

    private fun requireUserId(explicitUserId: String = ""): String {
        if (explicitUserId.isNotBlank()) return explicitUserId
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userQuestionsCollection(userId: String) =
        db.collection("users").document(userId).collection("solvedQuestions")

    /**
     * Real-time reactive stream of a signed-in user's solved questions, ordered newest-first.
     */
    fun observeSolvedQuestions(userId: String, limit: Long = 100L): Flow<List<SolvedQuestionRecord>> = callbackFlow {
        val path = "users/$userId/solvedQuestions"
        val registration = userQuestionsCollection(userId)
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path, auth)
                    close(error)
                    return@addSnapshotListener
                }
                val records = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    doc.toObject(
                        SolvedQuestionRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.copy(id = doc.id)
                }
                trySend(records)
            }
        awaitClose { registration.remove() }
    }

    /**
     * One-shot fetch of a user's solved questions (aligned with `existing().userId == request.auth.uid`).
     */
    suspend fun getSolvedQuestions(userId: String, limit: Long = 50L): Result<List<SolvedQuestionRecord>> {
        val path = "users/$userId/solvedQuestions"
        return try {
            val snapshot = userQuestionsCollection(userId)
                .whereEqualTo("userId", userId)
                .limit(limit)
                .get()
                .await()
            val items = snapshot.documents.mapNotNull { doc ->
                doc.toObject(
                    SolvedQuestionRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )?.copy(id = doc.id)
            }
            Result.success(items)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path, auth)
            Result.failure(e)
        }
    }

    /**
     * Fetches a single solved question by ID.
     */
    suspend fun getSolvedQuestionById(userId: String, questionId: String): Result<SolvedQuestionRecord?> {
        val path = "users/$userId/solvedQuestions/$questionId"
        return try {
            val doc = userQuestionsCollection(userId).document(questionId).get().await()
            val parsed = if (doc.exists()) {
                doc.toObject(
                    SolvedQuestionRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )?.copy(id = doc.id)
            } else {
                null
            }
            Result.success(parsed)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path, auth)
            Result.failure(e)
        }
    }

    /**
     * Creates a new solved question document under `/users/{userId}/solvedQuestions/{docId}`.
     * Nullifies `createdAt` and `updatedAt` so `@ServerTimestamp` populates `request.time`.
     */
    suspend fun saveSolvedQuestion(record: SolvedQuestionRecord): Result<String> {
        val targetUserId = try {
            requireUserId(record.userId)
        } catch (e: IllegalStateException) {
            return Result.failure(e)
        }
        val col = userQuestionsCollection(targetUserId)
        val docRef = if (record.id.isNotBlank()) col.document(record.id) else col.document()
        val path = "users/$targetUserId/solvedQuestions/${docRef.id}"

        val sanitized = record.copy(
            id = docRef.id,
            userId = targetUserId,
            createdAt = null,
            updatedAt = null
        )

        return try {
            docRef.set(sanitized).await()
            Result.success(docRef.id)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path, auth)
            Result.failure(e)
        }
    }

    /**
     * Updates bookmark and mistake-deck flags with a fresh server timestamp.
     */
    suspend fun updateStudyFlags(
        userId: String,
        questionId: String,
        bookmarked: Boolean,
        mistakeDeck: Boolean
    ): Result<Unit> {
        val path = "users/$userId/solvedQuestions/$questionId"
        return try {
            val updates = mapOf(
                "bookmarked" to bookmarked,
                "mistakeDeck" to mistakeDeck,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userQuestionsCollection(userId).document(questionId).update(updates).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    /**
     * Deletes a single solved question document.
     */
    suspend fun deleteSolvedQuestion(userId: String, questionId: String): Result<Unit> {
        val path = "users/$userId/solvedQuestions/$questionId"
        return try {
            userQuestionsCollection(userId).document(questionId).delete().await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path, auth)
            Result.failure(e)
        }
    }
}
