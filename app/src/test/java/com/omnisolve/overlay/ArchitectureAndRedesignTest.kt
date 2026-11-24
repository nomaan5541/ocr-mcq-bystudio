package com.omnisolve.overlay

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.omnisolve.overlay.cache.SolveResultCache
import com.omnisolve.overlay.capture.OcrQualityValidator
import com.omnisolve.overlay.capture.ScreenCaptureManager
import com.omnisolve.overlay.data.SettingsDataStoreRepository
import com.omnisolve.overlay.data.local.OfflineStudyHistoryRepository
import com.omnisolve.overlay.data.local.PrismStudyDatabase
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.ui.DynamicIslandBorderDrawable
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.screens.DynamicIslandStudioScreen
import com.omnisolve.overlay.ui.screens.GoogleFormsSolveScreen
import com.omnisolve.overlay.ui.theme.NORMAL_DEFAULT_THEME
import com.omnisolve.overlay.ui.theme.WhiteGlassTheme
import com.omnisolve.overlay.util.PrismCrashlytics
import com.omnisolve.overlay.util.SecureKeyStorage
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArchitectureAndRedesignTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createSyntheticBitmap(seedColor: Int, stripeColor: Int = Color.WHITE): Bitmap {
        val bmp = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(seedColor)
        val paint = android.graphics.Paint().apply { color = stripeColor }
        canvas.drawRect(10f, 15f, 95f, 35f, paint)
        return bmp
    }

    @Test
    fun testScreenCaptureManager_ClearsLastFrameOnStopAndRejectsStaleRetry() {
        val manager = ScreenCaptureManager(context)
        val frame = createSyntheticBitmap(Color.DKGRAY, Color.CYAN)
        val frameTimestamp = 1_700_000_000_000L

        manager.injectTestFrameForTesting(frame, frameTimestamp)
        assertTrue(manager.hasCachedFrameForTesting())
        assertEquals(frameTimestamp, manager.getLastFrameTimestampMsForTesting())

        // Simulate MediaProjection callback onStop()
        manager.simulateProjectionStopForTesting()
        assertFalse("lastValidFrame must be cleared on projection stop", manager.hasCachedFrameForTesting())
        assertEquals(0L, manager.getLastFrameTimestampMsForTesting())
        assertNull(
            "Retry after stop must never return previous screen frame",
            manager.captureGuaranteedFreshFrame(minTimestampMs = frameTimestamp + 1L, sessionId = 101L)
        )

        // Inject again and verify explicit stopCapture() also clears cache
        val frame2 = createSyntheticBitmap(Color.BLUE, Color.YELLOW)
        manager.injectTestFrameForTesting(frame2, frameTimestamp + 500L)
        assertTrue(manager.hasCachedFrameForTesting())
        manager.stopCapture()
        assertFalse("stopCapture() must clear frame cache", manager.hasCachedFrameForTesting())
        assertEquals(0L, manager.getLastFrameTimestampMsForTesting())
    }

    @Test
    fun testOcrQualityValidator_RejectsStatusBarChromeAndAcceptsRealMcq() {
        val chromeText = "10:42\n85%\n5G\nLTE\nWi-Fi"
        assertTrue(OcrQualityValidator.stripUiChromeLines(chromeText).isEmpty())
        assertFalse(
            "Status bar chrome alone must never pass Pass 1 OCR acceptance",
            OcrQualityValidator.shouldAcceptPassOne(chromeText)
        )

        val validMcq = """
            10:42
            88% 5G
            What is the chemical formula of water?
            A) NaCl
            B) H2O
            C) CO2
        """.trimIndent()
        val assessment = OcrQualityValidator.assess(validMcq)
        assertTrue("Valid MCQ must pass Pass 1 OCR acceptance", assessment.isUsableQuestion)
        assertTrue(assessment.optionMarkerCount >= 3)
        assertTrue(assessment.hasQuestionCue)
        assertTrue(
            OcrQualityValidator.scoreExtraction(validMcq) > OcrQualityValidator.scoreExtraction(chromeText)
        )
    }

    @Test
    fun testSolveResultCache_InMemoryTtlInvalidationAndZeroDiskWrites() {
        var fakeNow = 10_000L
        val cache = SolveResultCache(
            clockMs = { fakeNow },
            sameCaptureTtlMs = 20_000L,
            answerTtlMs = 180_000L
        )
        val bmp = createSyntheticBitmap(Color.BLACK, Color.GREEN)
        val crop = Rect(0, 0, 120, 80)
        val answer = AnswerModel(
            correctChoice = "B",
            shortAnswer = "H2O",
            explanation = "Water consists of two hydrogen atoms and one oxygen atom.",
            confidence = 97,
            engineLabel = "Background Gemini",
            subject = SubjectMode.MEDICAL.id
        )

        val filesBefore = context.filesDir.listFiles()?.map { it.name }.orEmpty().toSet()
        val cacheBefore = context.cacheDir.listFiles()?.map { it.name }.orEmpty().toSet()

        cache.putSameCapture(
            bitmap = bmp,
            cropRect = crop,
            modelId = SolverMethod.BACKGROUND_GEMINI.id,
            subjectMode = SubjectMode.MEDICAL.id,
            solveMode = QuestionSolveMode.STANDARD.id,
            ocrText = "What is water?\nA) Salt\nB) H2O",
            answer = answer
        )

        val hit = cache.getSameCapture(
            bitmap = bmp,
            cropRect = crop,
            modelId = SolverMethod.BACKGROUND_GEMINI.id,
            subjectMode = SubjectMode.MEDICAL.id,
            solveMode = QuestionSolveMode.STANDARD.id
        )
        assertNotNull("Same bitmap + config must hit Same-Capture Cache", hit)
        assertTrue("Cache hit must flag isCachedResult = true", hit!!.first.isCachedResult)
        assertEquals("B", hit.first.correctChoice)

        // Changing solver model must miss
        assertNull(
            cache.getSameCapture(
                bitmap = bmp,
                cropRect = crop,
                modelId = SolverMethod.TURBO_HYBRID.id,
                subjectMode = SubjectMode.MEDICAL.id,
                solveMode = QuestionSolveMode.STANDARD.id
            )
        )

        // Expiring TTL must miss
        fakeNow += 25_000L
        assertNull(
            cache.getSameCapture(
                bitmap = bmp,
                cropRect = crop,
                modelId = SolverMethod.BACKGROUND_GEMINI.id,
                subjectMode = SubjectMode.MEDICAL.id,
                solveMode = QuestionSolveMode.STANDARD.id
            )
        )

        // Put again and verify projection stop invalidation clears Same-Capture Cache
        cache.putSameCapture(
            bitmap = bmp,
            cropRect = crop,
            modelId = SolverMethod.BACKGROUND_GEMINI.id,
            subjectMode = SubjectMode.MEDICAL.id,
            solveMode = QuestionSolveMode.STANDARD.id,
            ocrText = "What is water?",
            answer = answer
        )
        assertEquals(1, cache.sameCaptureCount())
        cache.invalidateSameCaptureCache()
        assertEquals(0, cache.sameCaptureCount())

        // AI Answer Cache canonical normalized key checks
        val (normQ, normOpts) = SolveResultCache.splitNormalizedQuestionAndOptions(
            "  What   is  Water? \n A) Salt \n B) H2O "
        )
        cache.putAiAnswer(
            modelId = SolverMethod.BACKGROUND_GEMINI.id,
            subjectMode = SubjectMode.MEDICAL.id,
            solveMode = QuestionSolveMode.STANDARD.id,
            normalizedQuestion = normQ,
            normalizedOptions = normOpts,
            answer = answer
        )
        val ansHit = cache.getAiAnswer(
            modelId = SolverMethod.BACKGROUND_GEMINI.id,
            subjectMode = SubjectMode.MEDICAL.id,
            solveMode = QuestionSolveMode.STANDARD.id,
            normalizedQuestion = "what is water?",
            normalizedOptions = "a) salt | b) h2o"
        )
        assertNotNull("Canonical normalized Q+options must hit AI Answer Cache", ansHit)
        assertTrue(ansHit!!.isCachedResult)
        assertNull(
            "Changed promptVersion must miss AI Answer Cache",
            cache.getAiAnswer(
                modelId = SolverMethod.BACKGROUND_GEMINI.id,
                subjectMode = SubjectMode.MEDICAL.id,
                solveMode = QuestionSolveMode.STANDARD.id,
                normalizedQuestion = "what is water?",
                normalizedOptions = "a) salt | b) h2o",
                promptVersion = "v999"
            )
        )
        fakeNow += 185_000L
        assertNull(
            "Expired AI Answer Cache entry must miss",
            cache.getAiAnswer(
                modelId = SolverMethod.BACKGROUND_GEMINI.id,
                subjectMode = SubjectMode.MEDICAL.id,
                solveMode = QuestionSolveMode.STANDARD.id,
                normalizedQuestion = "what is water?",
                normalizedOptions = "a) salt | b) h2o"
            )
        )

        val filesAfter = context.filesDir.listFiles()?.map { it.name }.orEmpty().toSet()
        val cacheAfter = context.cacheDir.listFiles()?.map { it.name }.orEmpty().toSet()
        assertEquals("Cache must NEVER write files to filesDir", filesBefore, filesAfter)
        assertEquals("Cache must NEVER write files to cacheDir", cacheBefore, cacheAfter)
        bmp.recycle()
    }

    @Test
    fun testSettingsDataStore_MigratesNonSecretSettingsAndExcludesApiKeys() = runBlocking {
        val dsFile = File(context.filesDir, "test_prism_settings_${System.nanoTime()}.preferences_pb")
        try {
            val dataStore = PreferenceDataStoreFactory.create(produceFile = { dsFile })
            val repo = SettingsDataStoreRepository(dataStore)

            val legacyPrefs = context.getSharedPreferences("LegacyMigrateTest_${System.nanoTime()}", Context.MODE_PRIVATE)
            legacyPrefs.edit()
                .putString(MainActivity.PREF_KEY_SOLVER_METHOD, SolverMethod.API_KEYS.id)
                .putString("PREF_THEME_SELECTED_PRESET_ID", "jarvis_ai")
                .putBoolean("PREF_THEME_PREFER_LOCAL_DEFAULT", false)
                .putInt(MainActivity.PREF_KEY_ISLAND_WIDTH_DP, 290)
                .putInt(MainActivity.PREF_KEY_ISLAND_HEIGHT_DP, 68)
                .putInt(MainActivity.PREF_KEY_ISLAND_CORNER_RADIUS_DP, 24)
                .putFloat(MainActivity.PREF_KEY_ISLAND_ANIM_SPEED, 1.45f)
                .putBoolean(MainActivity.PREF_KEY_ISLAND_CUTOUT_ALIGN, true)
                .putInt(MainActivity.PREF_KEY_ISLAND_CUTOUT_OFFSET_X_DP, -12)
                .putInt(MainActivity.PREF_KEY_ISLAND_CUTOUT_OFFSET_Y_DP, 14)
                .putString(MainActivity.PREF_KEY_GEMINI_API_KEY, "AIzaSecretGeminiKeyShouldStayOut")
                .putString(MainActivity.PREF_KEY_NVIDIA_API_KEY, "nvapi-SecretNvidiaKeyShouldStayOut")
                .putString(MainActivity.PREF_KEY_OPENROUTER_API_KEY, "sk-or-v1-SecretOpenRouterKey")
                .putString(MainActivity.PREF_KEY_OPENAI_API_KEY, "sk-proj-SecretOpenAiKey")
                .commit()

            repo.migrateFromLegacySharedPreferencesIfNeeded(legacyPrefs)
            val migrated = repo.getSettings()
            assertEquals(SolverMethod.API_KEYS.id, migrated.selectedSolverMethodId)
            assertEquals("jarvis_ai", migrated.selectedThemePresetId)
            assertEquals(290, migrated.islandWidthDp)
            assertEquals(68, migrated.islandHeightDp)
            assertEquals(24, migrated.islandCornerRadiusDp)
            assertEquals(1.45f, migrated.islandAnimSpeed, 0.01f)
            assertTrue(migrated.islandCutoutAlign)
            assertEquals(-12, migrated.islandCutoutOffsetXDp)
            assertEquals(14, migrated.islandCutoutOffsetYDp)

            val rawPrefs = dataStore.data.first().asMap()
            val serialized = rawPrefs.entries.joinToString("|") { "${it.key.name}=${it.value}" }
            assertFalse(serialized.contains("AIzaSecret"))
            assertFalse(serialized.contains("nvapi-Secret"))
            assertFalse(serialized.contains("sk-or-v1"))
            assertFalse(serialized.contains("sk-proj-Secret"))
        } finally {
            dsFile.delete()
        }
    }

    @Test
    fun testRoomDatabase_ExplicitMigration1To2AndOfflineHistoryRepo() = runBlocking {
        val dbName = "test_prism_migration_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        try {
            // Create v1 SQLite database manually with schema v1 columns
            val v1Helper = object : SQLiteOpenHelper(context, dbName, null, 1) {
                override fun onCreate(db: SQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS solved_questions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            questionText TEXT NOT NULL,
                            answerBadge TEXT NOT NULL,
                            shortAnswer TEXT NOT NULL,
                            explanation TEXT NOT NULL,
                            questionType TEXT NOT NULL,
                            subject TEXT NOT NULL,
                            confidence INTEGER NOT NULL,
                            sourceLabel TEXT NOT NULL,
                            bookmarked INTEGER NOT NULL,
                            mistakeDeck INTEGER NOT NULL,
                            createdAtMs INTEGER NOT NULL,
                            updatedAtMs INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_solved_questions_createdAtMs ON solved_questions (createdAtMs)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_solved_questions_bookmarked ON solved_questions (bookmarked)")
                    db.execSQL(
                        """
                        INSERT INTO solved_questions (
                            questionText, answerBadge, shortAnswer, explanation,
                            questionType, subject, confidence, sourceLabel,
                            bookmarked, mistakeDeck, createdAtMs, updatedAtMs
                        ) VALUES (
                            'Solve 2 + 2?', 'B', '4', 'Basic arithmetic.',
                            'MCQ', 'MATH', 99, 'Legacy Capture',
                            1, 0, 1700000000000, 1700000000000
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }
            v1Helper.writableDatabase.close()
            v1Helper.close()

            // Open with Room v4 + explicit v1->v2->v3->v4 migrations (fails loudly if schema mismatch)
            val migratedDb = Room.databaseBuilder(context, PrismStudyDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .addMigrations(
                    PrismStudyDatabase.MIGRATION_1_2,
                    PrismStudyDatabase.MIGRATION_2_3,
                    PrismStudyDatabase.MIGRATION_3_4
                )
                .build()
            try {
                val rows = migratedDb.solvedQuestionDao().observeHistory(20).first()
                assertEquals(1, rows.size)
                assertEquals("Solve 2 + 2?", rows[0].questionText)
                assertEquals("B", rows[0].answerBadge)
                assertEquals("BACKGROUND_GEMINI", rows[0].sourceMethod)
                assertFalse(rows[0].isCachedResult)
                assertTrue(rows[0].bookmarked)
            } finally {
                migratedDb.close()
            }

            // Exercise OfflineStudyHistoryRepository insert, bookmark toggle, and clearAll
            val inMemoryDb = PrismStudyDatabase.buildInMemoryForTesting(context)
            try {
                val repo = OfflineStudyHistoryRepository(inMemoryDb.solvedQuestionDao())
                val insertedId = repo.recordSolve(
                    questionText = "Which planet is closest to the Sun?",
                    answer = AnswerModel(
                        correctChoice = "A",
                        shortAnswer = "Mercury",
                        explanation = "Mercury orbits closest to the Sun.",
                        engineLabel = "All-In-One",
                        isCachedResult = true
                    ),
                    sourceLabel = "Cached Solve"
                )
                assertTrue(insertedId > 0L)
                repo.setBookmarked(insertedId, true)
                val entries = repo.observeHistoryEntries().first()
                assertEquals(1, entries.size)
                assertEquals("A", entries[0].answerLetter)
                assertTrue(entries[0].bookmarked)

                repo.clearAll()
                assertEquals(0, repo.getHistoryCount())
            } finally {
                inMemoryDb.close()
            }
        } finally {
            dbFile.delete()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
        }
    }

    @Test
    fun testPrismCrashlytics_AllowlistsFieldsAndRedactsSecretsQuestionsAndPii() {
        val ctx = PrismCrashlytics.buildSafeFailureContext(
            solverMethod = "BACKGROUND_GEMINI",
            failedStage = "SOLVER_ERROR",
            ocrStatus = "OK",
            appVersion = "1.0 (1)"
        )
        assertEquals(setOf("app_version", "solver_method", "failed_stage", "ocr_status"), ctx.keys)

        val dangerousError = IllegalStateException(
            "Failed auth AIzaSyFakeKey123456 sk-projSecretOpenAiKey nvapi-123456789012 " +
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.sig user@example.com " +
                "question=What is 2+2 answer=4"
        )
        val summary = PrismCrashlytics.sanitizeThrowableSummary(dangerousError)
        assertFalse(summary.contains("AIzaSyFakeKey"))
        assertFalse(summary.contains("sk-projSecret"))
        assertFalse(summary.contains("nvapi-123456"))
        assertFalse(summary.contains("eyJhbGci"))
        assertFalse(summary.contains("user@example.com"))
        assertFalse(summary.contains("What is 2+2"))
    }

    @Test
    fun testDynamicIslandOverlayLayoutAndBorderDrawable_RestoredBackupViewsAndScanningState() {
        val root = LayoutInflater.from(context).inflate(R.layout.layout_dynamic_island, null, false)
        val drawable = DynamicIslandBorderDrawable(context.resources.displayMetrics.density)
        root.background = drawable

        val tvTitle = root.findViewById<TextView>(R.id.tv_island_title)
        val tvAnswer = root.findViewById<TextView>(R.id.tv_island_answer)
        val tvMeta = root.findViewById<TextView>(R.id.tv_island_meta)
        val tvExplanation = root.findViewById<TextView>(R.id.tv_island_explanation)
        val drawer = root.findViewById<View>(R.id.ll_island_explanation_drawer)
        val methodLoopDrawer = root.findViewById<View>(R.id.ll_island_method_loop_drawer)
        val expandedControls = root.findViewById<View>(R.id.island_expanded_controls)
        val btnClose = root.findViewById<View>(R.id.btn_island_close)

        assertNotNull(tvTitle)
        assertNotNull(tvAnswer)
        assertNotNull(tvMeta)
        assertNotNull(tvExplanation)
        assertNotNull(drawer)
        assertNotNull(methodLoopDrawer)
        assertNotNull(expandedControls)
        assertNotNull(btnClose)
        assertEquals(1, tvAnswer.maxLines)

        drawable.setScanning(true)
        assertTrue(drawable.isScanning())
        drawable.setScanning(false)
        assertFalse(drawable.isScanning())
    }

    @Test
    fun testDynamicIslandStudioScreen_RestoredBackupStudioCardsAndControls() {
        var backClicked = false
        var selectedMode: QuestionSolveMode? = null
        var selectedHudStyle: String? = null

        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .width(300.dp)
                    ) {
                        DynamicIslandStudioScreen(
                            state = MainUiState(),
                            onBackToHome = { backClicked = true },
                            onSetCustomOpacityPct = {},
                            onSelectQuestionSolveMode = { selectedMode = it },
                            onSelectHudStyle = { selectedHudStyle = it },
                            onToggleDfTwoPartCapture = {},
                            onToggleDfScrollTimer = {},
                            onToggleDfComplexMode = {},
                            onToggleDfDiagramVision = {},
                            onToggleDfQuickCrop = {},
                            onToggleDfSwipeMethodLoop = {},
                            onToggleAdvSplitPill = {},
                            onToggleAdvIslandGestures = {},
                            onToggleChargingFormat = {}
                        )
                    }
                }
            }
        }

        val studioTags = listOf(
            "section_home_opacity",
            "input_home_opacity_pct",
            "slider_home_opacity_pct",
            "btn_home_mode_standard",
            "btn_home_mode_match",
            "btn_home_mode_complex_math",
            "btn_island_studio_done"
        )
        studioTags.forEach { tag ->
            composeTestRule.onNodeWithTag("dynamic_island_studio_screen_list")
                .performScrollToNode(hasTestTag(tag))
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
        }

        composeTestRule.onNodeWithTag("dynamic_island_studio_screen_list")
            .performScrollToNode(hasTestTag("btn_home_mode_complex_math"))
        composeTestRule.onNodeWithTag("btn_home_mode_complex_math").performClick()
        assertEquals(QuestionSolveMode.COMPLEX_MATH, selectedMode)

        composeTestRule.onNodeWithTag("dynamic_island_studio_screen_list")
            .performScrollToNode(hasTestTag("btn_island_studio_done"))
        composeTestRule.onNodeWithTag("btn_island_studio_done").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun testSecureKeyStorage_SaveGetMigrateAndClearKeys() {
        val testKeys = listOf(
            MainActivity.PREF_KEY_NVIDIA_API_KEY,
            MainActivity.PREF_KEY_GEMINI_API_KEY
        )
        SecureKeyStorage.clearKeys(context, testKeys)

        val legacyPrefs = context.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        legacyPrefs.edit()
            .putString(MainActivity.PREF_KEY_NVIDIA_API_KEY, "nvapi-legacy-test-123456")
            .commit()

        assertEquals("nvapi-legacy-test-123456", SecureKeyStorage.getKey(context, MainActivity.PREF_KEY_NVIDIA_API_KEY))
        assertFalse(legacyPrefs.contains(MainActivity.PREF_KEY_NVIDIA_API_KEY))

        SecureKeyStorage.saveKeys(
            context,
            mapOf(MainActivity.PREF_KEY_GEMINI_API_KEY to "AIzaSyTestKey1234567890")
        )
        assertEquals("AIzaSyTestKey1234567890", SecureKeyStorage.getKey(context, MainActivity.PREF_KEY_GEMINI_API_KEY))

        SecureKeyStorage.clearKeys(context, testKeys)
        assertEquals("", SecureKeyStorage.getKey(context, MainActivity.PREF_KEY_NVIDIA_API_KEY))
        assertEquals("", SecureKeyStorage.getKey(context, MainActivity.PREF_KEY_GEMINI_API_KEY))
    }

    @Test
    fun testGoogleFormsSolveScreen_DisplaysCancelButtonWhileSolving() {
        var cancelClicked = false
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                GoogleFormsSolveScreen(
                    state = MainUiState(
                        firebaseUserId = "uid_test",
                        isSolvingGoogleForm = true,
                        googleFormUrlInput = "https://docs.google.com/forms/d/e/test/viewform"
                    ),
                    onBackToHome = {},
                    onUpdateUrl = {},
                    onUpdatePastedText = {},
                    onSelectSubjectMode = {},
                    onToggleAppendMode = {},
                    onSolveFromUrl = {},
                    onSolveFromPastedText = {},
                    onSolveFromImageUri = {},
                    onReSolveQuestion = {},
                    onPinToDynamicIsland = {},
                    onUnpinFromDynamicIsland = {},
                    onClearFormsResult = {},
                    onRefreshPinStatus = {},
                    onSaveToExportQueue = {},
                    onCancelSolve = { cancelClicked = true },
                    onShowBannerMessage = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("google_forms_solve_screen")
            .performScrollToNode(hasTestTag("btn_cancel_google_form"))
        composeTestRule.onNodeWithTag("btn_cancel_google_form").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_google_form").performClick()
        assertTrue(cancelClicked)
    }

    @Test
    fun testGoogleFormsSolveScreen_ErrorRecoveryAndOfflineFallbackActions() {
        var retriedSolve = false
        val solvedResult = com.omnisolve.overlay.api.GoogleFormSolveResult(
            formTitle = "Sample Form",
            resolvedUrl = "https://docs.google.com/forms/d/e/test/viewform",
            items = listOf(
                com.omnisolve.overlay.api.GoogleFormSolvedItem(
                    number = 1,
                    questionText = "Select prime numbers",
                    options = listOf("2", "4", "5", "9"),
                    answerLetter = "A, C",
                    shortAnswer = "2, 5",
                    explanation = "2 and 5 are prime."
                )
            ),
            compactNotificationOutput = "(1-A, C)",
            engineLabel = "Offline Fallback (Check Internet / Sign-In)"
        )

        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                GoogleFormsSolveScreen(
                    state = MainUiState(
                        firebaseUserId = "uid_test",
                        isSolvingGoogleForm = false,
                        googleFormUrlInput = "https://docs.google.com/forms/d/e/test/viewform",
                        googleFormError = "This form is restricted. Open Form Browser to sign in.",
                        googleFormResult = solvedResult
                    ),
                    onBackToHome = {},
                    onUpdateUrl = {},
                    onUpdatePastedText = {},
                    onSelectSubjectMode = {},
                    onToggleAppendMode = {},
                    onSolveFromUrl = { retriedSolve = true },
                    onSolveFromPastedText = {},
                    onSolveFromImageUri = {},
                    onReSolveQuestion = {},
                    onPinToDynamicIsland = {},
                    onUnpinFromDynamicIsland = {},
                    onClearFormsResult = {},
                    onRefreshPinStatus = {},
                    onSaveToExportQueue = {},
                    onCancelSolve = {},
                    onShowBannerMessage = {}
                )
            }
        }

        // Error recovery actions are visible; clicking Paste Text Instead opens manual paste field
        composeTestRule.onNodeWithTag("google_forms_solve_screen")
            .performScrollToNode(hasTestTag("tv_google_form_error"))
        composeTestRule.onNodeWithTag("btn_error_open_browser").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_error_paste_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_error_paste_text").performClick()
        composeTestRule.onNodeWithTag("google_forms_solve_screen")
            .performScrollToNode(hasTestTag("input_google_form_pasted_text"))
        composeTestRule.onNodeWithTag("input_google_form_pasted_text").assertIsDisplayed()

        // Offline fallback banner shows Retry AI and invokes re-solve
        composeTestRule.onNodeWithTag("google_forms_solve_screen")
            .performScrollToNode(hasTestTag("btn_retry_forms_with_ai"))
        composeTestRule.onNodeWithTag("btn_retry_forms_with_ai").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_retry_forms_with_ai").performClick()
        assertTrue(retriedSolve)
    }

    @Test
    fun testGoogleFormsSolveScreen_CollapsedBreakdownAndModelSelector() {
        var selectedModelId = ""
        val solvedResult = com.omnisolve.overlay.api.GoogleFormSolveResult(
            formTitle = "SE Midterm",
            resolvedUrl = "https://docs.google.com/forms/d/e/se/viewform",
            items = listOf(
                com.omnisolve.overlay.api.GoogleFormSolvedItem(
                    number = 1,
                    questionText = "Which model supports incremental delivery?",
                    options = listOf("Waterfall", "Agile", "Big Bang", "V-Model"),
                    answerLetter = "B",
                    shortAnswer = "Agile",
                    explanation = "Agile delivers working software iteratively."
                ),
                com.omnisolve.overlay.api.GoogleFormSolvedItem(
                    number = 2,
                    questionText = "Which diagram models message sequence over time?",
                    options = listOf("Class", "Sequence", "Component", "State"),
                    answerLetter = "B",
                    shortAnswer = "Sequence",
                    explanation = "Sequence diagrams show time-ordered lifelines."
                )
            ),
            compactNotificationOutput = "(1-B|2-B)",
            engineLabel = "Gemini 3.8 Flash (Forms)"
        )

        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                GoogleFormsSolveScreen(
                    state = MainUiState(
                        firebaseUserId = "uid_test",
                        googleFormsModel = com.omnisolve.overlay.api.GoogleFormsModelOption.GEMINI_38_FLASH.id,
                        googleFormUrlInput = "https://docs.google.com/forms/d/e/se/viewform",
                        googleFormResult = solvedResult
                    ),
                    onBackToHome = {},
                    onUpdateUrl = {},
                    onUpdatePastedText = {},
                    onSelectSubjectMode = {},
                    onSelectFormsModel = { selectedModelId = it },
                    onToggleAppendMode = {},
                    onSolveFromUrl = {},
                    onSolveFromPastedText = {},
                    onSolveFromImageUri = {},
                    onReSolveQuestion = {},
                    onPinToDynamicIsland = {},
                    onUnpinFromDynamicIsland = {},
                    onClearFormsResult = {},
                    onRefreshPinStatus = {},
                    onSaveToExportQueue = {},
                    onCancelSolve = {},
                    onShowBannerMessage = {}
                )
            }
        }

        // Expand More Ways & Options -> model selector triggers callback when switching to Gemini Web AI
        composeTestRule.onNodeWithTag("google_forms_solve_screen")
            .performScrollToNode(hasTestTag("btn_forms_more_options"))
        composeTestRule.onNodeWithTag("btn_forms_more_options").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("google_forms_solve_screen")
            .performScrollToNode(hasTestTag("chip_forms_model_gemini-web-ai"))
        composeTestRule.onNodeWithTag("chip_forms_model_gemini-web-ai").performClick()
        assertEquals("gemini-web-ai", selectedModelId)

        // Step 03 rows start collapsed — details hidden until a user taps a question row
        composeTestRule.onNodeWithTag("google_forms_solve_screen")
            .performScrollToNode(hasTestTag("form_q_row_2"))
        composeTestRule.onNodeWithTag("form_q_row_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("form_q_row_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("form_q_detail_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("form_q_detail_2").assertDoesNotExist()

        // Tap Q2 row -> ONLY Q2 opens, Q1 stays collapsed
        composeTestRule.onNodeWithTag("form_q_row_2").performClick()
        composeTestRule.onNodeWithTag("google_forms_solve_screen")
            .performScrollToNode(hasTestTag("form_q_detail_2"))
        composeTestRule.onNodeWithTag("form_q_detail_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_forms_solve_screen")
            .performScrollToNode(hasTestTag("btn_resolve_form_q_2"))
        composeTestRule.onNodeWithTag("btn_resolve_form_q_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("form_q_detail_1").assertDoesNotExist()
    }
}


