package com.omnisolve.overlay.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.omnisolve.overlay.R
import com.omnisolve.overlay.model.SubjectMode
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudHistoryFilterState(
    val searchQuery: String = "",
    val selectedSubject: String = "ALL",
    val bookmarkedOnly: Boolean = false,
    val mistakeDeckOnly: Boolean = false
)

sealed interface CloudHistoryUiState {
    data object Loading : CloudHistoryUiState
    data class Success(
        val allRecords: List<SolvedQuestionRecord>,
        val filteredRecords: List<SolvedQuestionRecord>,
        val mistakeDeckRecords: List<SolvedQuestionRecord>,
        val filter: CloudHistoryFilterState
    ) : CloudHistoryUiState
    data class Error(val message: String) : CloudHistoryUiState
}

/**
 * Auth-gated ViewModel managing real-time Cloud Firestore solved history, bookmarks,
 * mistake flashcards, search/subject filtering, and study sheet Markdown export.
 */
class CloudHistoryViewModel(
    private val repository: SolvedQuestionRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _filterState = MutableStateFlow(CloudHistoryFilterState())
    val filterState: StateFlow<CloudHistoryFilterState> = _filterState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val uiState: StateFlow<CloudHistoryUiState> = combine(
        repository.observeSolvedQuestions(currentUserId),
        _filterState
    ) { records, filter ->
        val query = filter.searchQuery.trim().lowercase(Locale.ROOT)
        val filtered = records.filter { rec ->
            val matchesQuery = query.isEmpty() ||
                rec.questionText.lowercase(Locale.ROOT).contains(query) ||
                rec.shortAnswer.lowercase(Locale.ROOT).contains(query) ||
                rec.explanation.lowercase(Locale.ROOT).contains(query) ||
                rec.answerBadge.lowercase(Locale.ROOT).contains(query)
            val matchesSubject = filter.selectedSubject == "ALL" ||
                rec.subject.equals(filter.selectedSubject, ignoreCase = true)
            val matchesBookmark = !filter.bookmarkedOnly || rec.bookmarked
            val matchesMistake = !filter.mistakeDeckOnly || rec.mistakeDeck
            matchesQuery && matchesSubject && matchesBookmark && matchesMistake
        }
        val deck = records.filter { it.mistakeDeck || it.bookmarked }.ifEmpty { records.take(20) }
        CloudHistoryUiState.Success(
            allRecords = records,
            filteredRecords = filtered,
            mistakeDeckRecords = deck,
            filter = filter
        ) as CloudHistoryUiState
    }
        .catch { e ->
            emit(CloudHistoryUiState.Error(e.localizedMessage ?: "Unable to sync cloud history."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CloudHistoryUiState.Loading
        )

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun selectSubjectFilter(subjectId: String) {
        _filterState.update { it.copy(selectedSubject = subjectId) }
    }

    fun toggleBookmarkedOnly() {
        _filterState.update { it.copy(bookmarkedOnly = !it.bookmarkedOnly) }
    }

    fun toggleMistakeDeckOnly() {
        _filterState.update { it.copy(mistakeDeckOnly = !it.mistakeDeckOnly) }
    }

    fun toggleBookmark(record: SolvedQuestionRecord) {
        if (record.id.isBlank()) return
        viewModelScope.launch {
            val result = repository.updateStudyFlags(
                userId = currentUserId,
                questionId = record.id,
                bookmarked = !record.bookmarked,
                mistakeDeck = record.mistakeDeck
            )
            result.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Failed to update bookmark"
            }
        }
    }

    fun toggleMistakeDeck(record: SolvedQuestionRecord) {
        if (record.id.isBlank()) return
        viewModelScope.launch {
            val result = repository.updateStudyFlags(
                userId = currentUserId,
                questionId = record.id,
                bookmarked = record.bookmarked,
                mistakeDeck = !record.mistakeDeck
            )
            result.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Failed to update Mistake Deck"
            }
        }
    }

    fun deleteRecord(record: SolvedQuestionRecord) {
        if (record.id.isBlank()) return
        viewModelScope.launch {
            repository.deleteSolvedQuestion(currentUserId, record.id)
        }
    }

    fun buildMarkdownStudySheet(records: List<SolvedQuestionRecord>): String {
        val formatter = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US)
        return buildString {
            appendLine("# Prism AI — Study & Revision Sheet")
            appendLine("Generated items: ${records.size}")
            appendLine()
            records.forEachIndexed { idx, rec ->
                val subjectLabel = SubjectMode.fromId(rec.subject).label
                val timeStr = rec.createdAt?.toDate()?.let { formatter.format(it) } ?: "Recent"
                appendLine("## ${idx + 1}. [$subjectLabel • ${rec.questionType}] (${rec.confidence}% Confidence)")
                appendLine("**Question:** ${rec.questionText}")
                if (!rec.translatedQuestion.isNullOrBlank()) {
                    appendLine("**English Translation:** ${rec.translatedQuestion}")
                }
                val shortSuffix = if (rec.shortAnswer.isNotBlank()) " — ${rec.shortAnswer}" else ""
                appendLine("**Answer:** `${rec.answerBadge}`$shortSuffix")
                if (rec.explanation.isNotBlank()) {
                    appendLine("**Explanation:** ${rec.explanation}")
                }
                appendLine("*Saved: $timeStr*")
                appendLine()
            }
        }
    }

    companion object {
        fun provideFactory(context: Context, userId: String): ViewModelProvider.Factory {
            val appCtx = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val databaseId = appCtx.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    val repo = SolvedQuestionRepository(db)
                    return CloudHistoryViewModel(repo, userId) as T
                }
            }
        }
    }
}
