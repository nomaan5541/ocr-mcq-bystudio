package com.omnisolve.overlay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedAnswerCacheDao {

    @Query("SELECT * FROM shared_answer_cache WHERE questionHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): SharedAnswerCacheEntity?

    @Query("SELECT * FROM shared_answer_cache WHERE visualHash = :visualHash AND visualHash != '' ORDER BY updatedAtMs DESC LIMIT 1")
    suspend fun getByVisualHash(visualHash: String): SharedAnswerCacheEntity?

    @Query("SELECT COUNT(*) FROM shared_answer_cache")
    fun observeCacheCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(CASE WHEN hitCount > 1 THEN hitCount - 1 ELSE 0 END), 0) FROM shared_answer_cache")
    fun observeTotalSavedCalls(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SharedAnswerCacheEntity)

    @Query("UPDATE shared_answer_cache SET hitCount = hitCount + 1, updatedAtMs = :nowMs WHERE questionHash = :hash")
    suspend fun touchHit(hash: String, nowMs: Long = System.currentTimeMillis())

    @Query("DELETE FROM shared_answer_cache WHERE questionHash = :hash")
    suspend fun deleteByHash(hash: String)

    @Query("DELETE FROM shared_answer_cache WHERE visualHash = :visualHash AND visualHash != ''")
    suspend fun deleteByVisualHash(visualHash: String)

    @Query("DELETE FROM shared_answer_cache WHERE visualHash = :visualHash AND visualHash != '' AND questionHash != :keepHash")
    suspend fun deleteOtherVisualHashEntries(visualHash: String, keepHash: String)

    @Transaction
    suspend fun upsertAtomic(entity: SharedAnswerCacheEntity, replaceOtherVisualMatches: Boolean) {
        if (replaceOtherVisualMatches && entity.visualHash.isNotBlank()) {
            deleteOtherVisualHashEntries(entity.visualHash, entity.questionHash)
        }
        upsert(entity)
    }

    @Query("DELETE FROM shared_answer_cache WHERE questionHash NOT IN (SELECT questionHash FROM shared_answer_cache ORDER BY updatedAtMs DESC LIMIT :keepLimit)")
    suspend fun pruneOldEntries(keepLimit: Int = 500)

    @Query("DELETE FROM shared_answer_cache WHERE updatedAtMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("DELETE FROM shared_answer_cache")
    suspend fun clearAll()
}
