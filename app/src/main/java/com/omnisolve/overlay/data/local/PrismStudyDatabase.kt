package com.omnisolve.overlay.data.local

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SolvedQuestionEntity::class, SharedAnswerCacheEntity::class],
    version = 4,
    exportSchema = false
)
abstract class PrismStudyDatabase : RoomDatabase() {

    abstract fun solvedQuestionDao(): SolvedQuestionDao
    abstract fun sharedAnswerCacheDao(): SharedAnswerCacheDao

    companion object {
        const val DB_NAME = "prism_study_history.db"

        @Volatile
        private var instance: PrismStudyDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE solved_questions ADD COLUMN sourceMethod TEXT NOT NULL DEFAULT 'BACKGROUND_GEMINI'"
                )
                db.execSQL(
                    "ALTER TABLE solved_questions ADD COLUMN isCachedResult INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shared_answer_cache` (
                        `questionHash` TEXT NOT NULL,
                        `questionPreview` TEXT NOT NULL,
                        `answerBadge` TEXT NOT NULL,
                        `shortAnswer` TEXT NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `subjectId` TEXT NOT NULL,
                        `confidence` INTEGER NOT NULL,
                        `sourceLabel` TEXT NOT NULL,
                        `hitCount` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`questionHash`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shared_answer_cache_updatedAtMs` ON `shared_answer_cache` (`updatedAtMs`)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE shared_answer_cache ADD COLUMN visualHash TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shared_answer_cache_visualHash` ON `shared_answer_cache` (`visualHash`)"
                )
                db.execSQL(
                    "ALTER TABLE solved_questions ADD COLUMN solveLatencyMs INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE solved_questions ADD COLUMN modelUsed TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun getInstance(context: Context): PrismStudyDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrismStudyDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }

        @VisibleForTesting
        fun buildInMemoryForTesting(context: Context): PrismStudyDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                PrismStudyDatabase::class.java
            )
                .allowMainThreadQueries()
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }
}
