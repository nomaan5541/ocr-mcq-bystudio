package com.omnisolve.overlay.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SubjectMode

/**
 * Cloud-synced solved question record stored at `/users/{userId}/solvedQuestions/{questionId}`.
 * Every property is immutable (`val`) with a default value so Firestore deserialization works cleanly.
 */
@IgnoreExtraProperties
data class SolvedQuestionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val questionText: String = "",
    val answerBadge: String = "?",
    val shortAnswer: String = "",
    val explanation: String = "",
    val questionType: String = AnswerModel.QUESTION_TYPE_MCQ,
    val subject: String = SubjectMode.GENERAL.id,
    val confidence: Int = 95,
    val language: String = "English",
    val translatedQuestion: String? = null,
    val bookmarked: Boolean = false,
    val mistakeDeck: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromAnswerModel(
            userId: String,
            questionText: String,
            answer: AnswerModel,
            bookmarked: Boolean = false,
            mistakeDeck: Boolean = false
        ): SolvedQuestionRecord {
            val cleanQuestion = questionText.trim().ifBlank { "Captured Screen Question" }.take(3900)
            val cleanBadge = answer.correctChoice.trim().ifBlank { "?" }.take(40)
            val cleanShort = answer.shortAnswer.trim().take(300)
            val cleanExplanation = answer.explanation.trim().take(2000)
            val cleanType = if (answer.questionType in AnswerModel.VALID_QUESTION_TYPES) {
                answer.questionType
            } else {
                AnswerModel.QUESTION_TYPE_MCQ
            }
            val cleanSubject = SubjectMode.fromId(answer.subject).id
            val cleanConfidence = answer.confidence.coerceIn(0, 100)
            val cleanLang = answer.language.trim().ifBlank { "English" }.take(40)
            val cleanTranslated = answer.translatedQuestion.trim().take(3900).ifBlank { null }

            return SolvedQuestionRecord(
                userId = userId,
                questionText = cleanQuestion,
                answerBadge = cleanBadge,
                shortAnswer = cleanShort,
                explanation = cleanExplanation,
                questionType = cleanType,
                subject = cleanSubject,
                confidence = cleanConfidence,
                language = cleanLang,
                translatedQuestion = cleanTranslated,
                bookmarked = bookmarked,
                mistakeDeck = mistakeDeck,
                createdAt = null,
                updatedAt = null
            )
        }
    }
}
