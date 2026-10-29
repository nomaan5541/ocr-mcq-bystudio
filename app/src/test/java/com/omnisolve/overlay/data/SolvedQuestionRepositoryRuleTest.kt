package com.omnisolve.overlay.data

import com.google.firebase.firestore.FirebaseFirestoreException
import com.omnisolve.overlay.base.FirestoreEmulatorTestBase
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SubjectMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolvedQuestionRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveObserveUpdateAndDelete_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser("alice_${System.currentTimeMillis()}@example.com")
        val repository = SolvedQuestionRepository(firestore, auth)

        val record = SolvedQuestionRecord.fromAnswerModel(
            userId = aliceUid,
            questionText = "Which sorting algorithm guarantees O(n log n) worst-case time?",
            answer = AnswerModel(
                correctChoice = "C",
                shortAnswer = "Merge Sort",
                explanation = "Merge Sort divides the array in half recursively and merges in linear time.",
                questionType = AnswerModel.QUESTION_TYPE_MCQ,
                confidence = 98,
                language = "English",
                subject = SubjectMode.CODING.id
            )
        )

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveSolvedQuestion(record)
        }
        assertTrue("Expected saveSolvedQuestion to succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        val createdId = createResult.getOrThrow()
        assertTrue(createdId.isNotBlank())

        // Observe via real-time Flow
        val observedList = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.observeSolvedQuestions(aliceUid).first { it.any { item -> item.id == createdId } }
        }
        val fetched = observedList.first { it.id == createdId }
        assertEquals("C", fetched.answerBadge)
        assertEquals("Merge Sort", fetched.shortAnswer)

        // Update bookmark & mistakeDeck flags
        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateStudyFlags(
                userId = aliceUid,
                questionId = createdId,
                bookmarked = true,
                mistakeDeck = true
            )
        }
        assertTrue("Expected updateStudyFlags to succeed: ${updateResult.exceptionOrNull()}", updateResult.isSuccess)

        val singleGet = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getSolvedQuestionById(aliceUid, createdId).getOrThrow()
        }
        assertNotNull(singleGet)
        assertTrue(singleGet!!.bookmarked)
        assertTrue(singleGet.mistakeDeck)

        // Delete
        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteSolvedQuestion(aliceUid, createdId)
        }
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun crossUserReadAndWrite_deniedBySecurityRules() = runBlocking {
        val bobUid = signInTestUser("bob_${System.currentTimeMillis()}@example.com")
        val repository = SolvedQuestionRepository(firestore, auth)

        val bobDocId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveSolvedQuestion(
                SolvedQuestionRecord.fromAnswerModel(
                    userId = bobUid,
                    questionText = "What is Avogadro's constant?",
                    answer = AnswerModel(
                        correctChoice = "A",
                        shortAnswer = "6.022 x 10^23",
                        explanation = "Number of particles per mole.",
                        subject = SubjectMode.SCIENCE.id
                    )
                )
            ).getOrThrow()
        }

        // Switch to Alice and verify she cannot read or delete Bob's question
        signInTestUser("alice_cross_${System.currentTimeMillis()}@example.com")

        val crossRead = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getSolvedQuestionById(bobUid, bobDocId)
        }
        assertTrue(crossRead.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            crossRead.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        val crossDelete = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteSolvedQuestion(bobUid, bobDocId)
        }
        assertTrue(crossDelete.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            crossDelete.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun unauthenticatedList_deniedBySecurityRules() = runBlocking {
        val tempUid = signInTestUser("temp_${System.currentTimeMillis()}@example.com")
        auth.signOut()

        val repository = SolvedQuestionRepository(firestore, auth)
        val unauthResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getSolvedQuestions(tempUid)
        }
        assertTrue(unauthResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            unauthResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }
}
