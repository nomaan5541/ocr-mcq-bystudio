package com.omnisolve.overlay.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shared_answer_cache",
    indices = [
        Index(value = ["updatedAtMs"]),
        Index(value = ["visualHash"])
    ]
)
data class SharedAnswerCacheEntity(
    @PrimaryKey
    val questionHash: String,
    val questionPreview: String,
    val answerBadge: String,
    val shortAnswer: String,
    val explanation: String,
    val subjectId: String,
    val confidence: Int,
    val sourceLabel: String,
    val hitCount: Int = 1,
    val updatedAtMs: Long = System.currentTimeMillis(),
    // Added in Schema Version 4 via MIGRATION_3_4 for instant Math/Diagram screenshot lookups
    val visualHash: String = ""
)
