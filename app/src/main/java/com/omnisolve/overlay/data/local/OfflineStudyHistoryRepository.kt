package com.omnisolve.overlay.data.local

import android.content.Context
import android.content.SharedPreferences
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.ui.SolvedHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed offline persistence repository for solved study questions, bookmarks,
 * and flashcards. Works identically whether signed in or completely offline.
 */
class OfflineStudyHistoryRepository(
    private val dao: SolvedQuestionDao
) {

    constructor(context: Context) : this(
        PrismStudyDatabase.getInstance(context.applicationContext).solvedQuestionDao()
    )

    companion object {
        private const val KEY_ROOM_HISTORY_MIGRATED = "PREF_ROOM_HISTORY_MIGRATED_V1"
    }

    fun observeHistoryEntries(limit: Int = 200): Flow<List<SolvedHistoryEntry>> {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        return dao.observeHistory(limit).map { rows ->
            rows.map { row ->
                SolvedHistoryEntry(
                    id = row.id,
                    questionPreview = row.questionText,
                    answerLetter = row.answerBadge,
                    sourceLabel = row.sourceLabel,
                    timestamp = fmt.format(Date(row.createdAtMs)),
                    explanation = row.explanation,
                    subject = row.subject,
                    bookmarked = row.bookmarked,
                    isCachedResult = row.isCachedResult,
                    solveLatencyMs = row.solveLatencyMs,
                    modelUsed = row.modelUsed.ifBlank { row.sourceMethod }
                )
            }
        }
    }

    suspend fun getHistoryCount(): Int = dao.countAll()

    suspend fun recordSolve(
        questionText: String,
        answer: AnswerModel,
        sourceLabel: String = "Prism Island",
        sourceMethod: String = answer.engineLabel,
        bookmarked: Boolean = false,
        solveLatencyMs: Long = 0L
    ): Long {
        val now = System.currentTimeMillis()
        val cleanQuestion = questionText.trim().ifBlank { "Screen Question" }.take(2000)
        val cleanBadge = answer.correctChoice.trim().ifBlank { "?" }.take(40)
        val entity = SolvedQuestionEntity(
            questionText = cleanQuestion,
            answerBadge = cleanBadge,
            shortAnswer = answer.shortAnswer.trim().take(300),
            explanation = answer.explanation.trim().take(2000),
            questionType = if (answer.questionType in AnswerModel.VALID_QUESTION_TYPES) {
                answer.questionType
            } else {
                AnswerModel.QUESTION_TYPE_MCQ
            },
            subject = SubjectMode.fromId(answer.subject).id,
            confidence = answer.confidence.coerceIn(0, 100),
            sourceLabel = sourceLabel.trim().ifBlank { "Prism Island" }.take(48),
            bookmarked = bookmarked,
            mistakeDeck = false,
            createdAtMs = now,
            updatedAtMs = now,
            sourceMethod = sourceMethod.trim().ifBlank { "BACKGROUND_GEMINI" }.take(48),
            isCachedResult = answer.isCachedResult,
            solveLatencyMs = if (answer.isCachedResult) 0L else solveLatencyMs.coerceAtLeast(0L),
            modelUsed = answer.engineLabel.trim().ifBlank { sourceMethod }.take(64)
        )
        return dao.insert(entity)
    }

    suspend fun recordCustomEntry(
        questionText: String,
        answerBadge: String,
        explanation: String = "",
        subject: String = SubjectMode.GENERAL.id,
        sourceLabel: String = "Practice",
        sourceMethod: String = "PRACTICE",
        bookmarked: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            SolvedQuestionEntity(
                questionText = questionText.trim().ifBlank { "Study Question" }.take(2000),
                answerBadge = answerBadge.trim().ifBlank { "?" }.take(40),
                explanation = explanation.trim().take(2000),
                subject = SubjectMode.fromId(subject).id,
                sourceLabel = sourceLabel.take(48),
                sourceMethod = sourceMethod.take(48),
                bookmarked = bookmarked,
                createdAtMs = now,
                updatedAtMs = now,
                modelUsed = sourceMethod.take(64)
            )
        )
    }

    suspend fun setBookmarked(id: Long, bookmarked: Boolean) {
        dao.setBookmarked(id = id, bookmarked = bookmarked, updatedAtMs = System.currentTimeMillis())
    }

    suspend fun deleteEntry(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    /**
     * One-time migration of legacy `PREF_SOLVE_HISTORY` + `PREF_BOOKMARKED_IDS` lines
     * from SharedPreferences into Room.
     */
    suspend fun migrateFromLegacyPrefsIfNeeded(prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_ROOM_HISTORY_MIGRATED, false)) return
        val existingCount = dao.countAll()
        if (existingCount == 0) {
            val bookmarked = prefs.getString("PREF_BOOKMARKED_IDS", "")
                ?.trim()
                .orEmpty()
                .split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .toSet()
            val raw = prefs.getString("PREF_SOLVE_HISTORY", "")?.trim().orEmpty()
            if (raw.isNotBlank()) {
                val entities = raw.lines().mapNotNull { line ->
                    val parts = line.split("||")
                    if (parts.size >= 3) {
                        val ts = parts[0].toLongOrNull() ?: System.currentTimeMillis()
                        val badge = parts[1].trim().ifBlank { "?" }.take(40)
                        val snippet = parts[2].trim().ifBlank { "Screen Question" }.take(2000)
                        val exp = parts.getOrNull(3)?.trim().orEmpty().take(2000)
                        val subj = parts.getOrNull(4)?.trim()?.ifBlank { SubjectMode.GENERAL.id }
                            ?: SubjectMode.GENERAL.id
                        SolvedQuestionEntity(
                            questionText = snippet,
                            answerBadge = badge,
                            explanation = exp,
                            subject = SubjectMode.fromId(subj).id,
                            sourceLabel = "Prism Island",
                            bookmarked = ts in bookmarked,
                            createdAtMs = ts,
                            updatedAtMs = ts,
                            sourceMethod = "MIGRATED_LEGACY"
                        )
                    } else {
                        null
                    }
                }
                if (entities.isNotEmpty()) {
                    dao.insertAll(entities)
                }
            }
        }
        prefs.edit().putBoolean(KEY_ROOM_HISTORY_MIGRATED, true).apply()
    }
}
