package com.omnisolve.overlay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SolvedQuestionDao {

    @Query("SELECT * FROM solved_questions ORDER BY createdAtMs DESC, id DESC LIMIT :limit")
    fun observeHistory(limit: Int = 200): Flow<List<SolvedQuestionEntity>>

    @Query("SELECT * FROM solved_questions ORDER BY createdAtMs DESC, id DESC LIMIT :limit")
    suspend fun getHistoryOnce(limit: Int = 200): List<SolvedQuestionEntity>

    @Query("SELECT * FROM solved_questions WHERE bookmarked = 1 ORDER BY createdAtMs DESC, id DESC")
    fun observeBookmarked(): Flow<List<SolvedQuestionEntity>>

    @Query(
        """
        SELECT * FROM solved_questions
        WHERE questionText LIKE '%' || :query || '%'
           OR explanation LIKE '%' || :query || '%'
           OR answerBadge LIKE '%' || :query || '%'
           OR subject LIKE '%' || :query || '%'
        ORDER BY createdAtMs DESC, id DESC
        LIMIT :limit
        """
    )
    fun searchHistory(query: String, limit: Int = 200): Flow<List<SolvedQuestionEntity>>

    @Query("SELECT COUNT(*) FROM solved_questions WHERE isCachedResult = 1")
    fun observeCachedSolveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM solved_questions")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SolvedQuestionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SolvedQuestionEntity>)

    @Query("UPDATE solved_questions SET bookmarked = :bookmarked, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun setBookmarked(id: Long, bookmarked: Boolean, updatedAtMs: Long = System.currentTimeMillis())

    @Query("UPDATE solved_questions SET mistakeDeck = :mistakeDeck, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun setMistakeDeck(id: Long, mistakeDeck: Boolean, updatedAtMs: Long = System.currentTimeMillis())

    @Query("DELETE FROM solved_questions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM solved_questions")
    suspend fun clearAll()
}
