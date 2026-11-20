package com.omnisolve.overlay.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SubjectMode

/**
 * On-device offline Room table for solved study results & flashcards.
 * Separate from optional Cloud Firestore account sync so study history & bookmarks
 * always work reliably offline.
 */
@Entity(
    tableName = "solved_questions",
    indices = [
        Index(value = ["createdAtMs"]),
        Index(value = ["bookmarked"])
    ]
)
data class SolvedQuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val questionText: String,
    val answerBadge: String,
    val shortAnswer: String = "",
    val explanation: String = "",
    val questionType: String = AnswerModel.QUESTION_TYPE_MCQ,
    val subject: String = SubjectMode.GENERAL.id,
    val confidence: Int = 95,
    val sourceLabel: String = "OCR Solver",
    val bookmarked: Boolean = false,
    val mistakeDeck: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = createdAtMs,
    // Added in Schema Version 2 via MIGRATION_1_2
    val sourceMethod: String = "BACKGROUND_GEMINI",
    val isCachedResult: Boolean = false,
    // Added in Schema Version 4 via MIGRATION_3_4
    val solveLatencyMs: Long = 0L,
    val modelUsed: String = ""
)
