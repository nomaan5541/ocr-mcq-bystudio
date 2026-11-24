package com.omnisolve.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.omnisolve.overlay.api.AdaptiveFreeModelRouter
import com.omnisolve.overlay.api.GoogleFormsModelOption
import com.omnisolve.overlay.api.MathSymbolDetector
import com.omnisolve.overlay.api.PrismAiSolverEngine
import com.omnisolve.overlay.cache.PersistentQuizCacheManager
import com.omnisolve.overlay.cache.SolveResultCache
import com.omnisolve.overlay.data.local.OfflineStudyHistoryRepository
import com.omnisolve.overlay.data.local.PrismStudyDatabase
import com.omnisolve.overlay.data.local.SharedAnswerCacheEntity
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.service.OverlaySolveOrchestrator
import com.omnisolve.overlay.ui.DynamicIslandBorderDrawable
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.PracticeSample
import com.omnisolve.overlay.ui.SolvedHistoryEntry
import com.omnisolve.overlay.ui.components.LiveInteractiveDemoCard
import com.omnisolve.overlay.ui.components.NewUserFeatureGuideOverlay
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.screens.ExportQuestionsScreen
import com.omnisolve.overlay.ui.screens.IosLiquidLightHomeLayout
import com.omnisolve.overlay.ui.screens.SettingsScreen
import com.omnisolve.overlay.ui.screens.ThemeStudioScreen
import com.omnisolve.overlay.ui.theme.ALL_50_UNSLOP_THEMES
import com.omnisolve.overlay.ui.theme.IOS_LIQUID_LIGHT_THEME
import com.omnisolve.overlay.ui.theme.NORMAL_DEFAULT_THEME
import com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype
import com.omnisolve.overlay.ui.theme.WhiteGlassTheme
import com.omnisolve.overlay.ui.theme.findThemePresetById
import com.omnisolve.overlay.util.BatterySurvivalHelper
import com.omnisolve.overlay.util.PlayUpdateUiState
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CeoProductUpgradeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun roomV4Cache_storesAndMatchesByTextHashAndVisualHashOffline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = PrismStudyDatabase.buildInMemoryForTesting(context)
        val cacheDao = db.sharedAnswerCacheDao()
        val repo = OfflineStudyHistoryRepository(db.solvedQuestionDao())

        try {
            PersistentQuizCacheManager.disableNetworkForTest = true

            // Store a solved math question with a perceptual visualHash
            val rawQuestion = "Evaluate Σ (k=1 to 3) (1/2) * 2k\nA) 3\nB) 6\nC) 9\nD) 12"
            val sampleVisualHash = "f0e1d2c3b4a59687"
            PersistentQuizCacheManager.storeVerifiedAnswer(
                context = context,
                questionText = rawQuestion,
                subject = SubjectMode.MATH,
                answerBadge = "B",
                shortAnswer = "6",
                explanation = "(1/2)*2k simplifies to k; sum 1+2+3 = 6.",
                confidence = 98,
                visualHash = sampleVisualHash
            )

            // 1. Exact text-hash lookup hits offline Room cache without network
            val textHit = PersistentQuizCacheManager.lookupAcrossDatabases(
                context = context,
                questionText = rawQuestion,
                subject = SubjectMode.MATH,
                visualHash = ""
            )
            assertNotNull("Expected offline Room cache hit by text hash", textHit)
            assertEquals("B", textHit!!.answerBadge)
            assertEquals("6", textHit.shortAnswer)
            assertTrue(textHit.sourceLabel.contains("Offline", ignoreCase = true))

            // 2. Perceptual visualHash lookup hits even when OCR text was garbled!
            val garbledOcr = "n E i=1 1 2 k ?"
            val visualHit = PersistentQuizCacheManager.lookupAcrossDatabases(
                context = context,
                questionText = garbledOcr,
                subject = SubjectMode.MATH,
                visualHash = sampleVisualHash
            )
            assertNotNull("Expected offline Room cache hit by visualHash for garbled math OCR", visualHit)
            assertEquals("B", visualHit!!.answerBadge)
            assertTrue(visualHit.sourceLabel.contains("Offline", ignoreCase = true))

            // 3. Offline history repository persists isCachedResult, solveLatencyMs, modelUsed, and deletes single entry
            val savedId = repo.recordSolve(
                questionText = rawQuestion,
                answer = AnswerModel(
                    correctChoice = "B",
                    shortAnswer = "6",
                    explanation = "(1/2)*2k = k -> 6",
                    subject = SubjectMode.MATH.id,
                    engineLabel = "Offline Room Cache",
                    isCachedResult = true
                ),
                sourceLabel = "Offline Room Cache",
                solveLatencyMs = 0L
            )
            val entries = repo.observeHistoryEntries(limit = 10).first()
            assertEquals(1, entries.size)
            assertEquals(savedId, entries.first().id)
            assertTrue(entries.first().isCachedResult)
            assertEquals(0L, entries.first().solveLatencyMs)
            assertEquals("Offline Room Cache", entries.first().modelUsed)

            repo.deleteEntry(savedId)
            assertTrue("Single-entry delete should remove the item", repo.observeHistoryEntries(limit = 10).first().isEmpty())

            // Verify in-memory DAO visualHash query directly as well
            cacheDao.upsert(
                SharedAnswerCacheEntity(
                    questionHash = "hash_math_v4",
                    questionPreview = "1/2 + 1/2",
                    answerBadge = "A",
                    shortAnswer = "1",
                    explanation = "Half plus half equals one.",
                    subjectId = SubjectMode.MATH.id,
                    confidence = 99,
                    sourceLabel = "Offline Room Cache",
                    visualHash = "aabbccddeeff0011"
                )
            )
            val byVisual = cacheDao.getByVisualHash("aabbccddeeff0011")
            assertNotNull(byVisual)
            assertEquals("A", byVisual!!.answerBadge)
        } finally {
            PersistentQuizCacheManager.disableNetworkForTest = false
            db.close()
        }
    }

    @Test
    fun mathSymbolDetector_escalatesFractionsSigmaCalculusMatchAndGarbledOcrOnly() {
        // Unicode summation & fractions -> Escalates!
        assertEquals(
            MathSymbolDetector.EscalationReason.UNICODE_MATH_SYMBOL,
            MathSymbolDetector.analyze("Compute Σ (i=1 to 5) of 2i")
        )
        assertEquals(
            MathSymbolDetector.EscalationReason.UNICODE_MATH_SYMBOL,
            MathSymbolDetector.analyze("Simplify ½ + ¼")
        )
        assertEquals(
            MathSymbolDetector.EscalationReason.FRACTION_OR_EXPONENT,
            MathSymbolDetector.analyze("What is 1/2 + 3/4? A) 5/4 B) 3/2 C) 1 D) 2")
        )
        assertEquals(
            MathSymbolDetector.EscalationReason.CALCULUS_OR_TRIG,
            MathSymbolDetector.analyze("Find the derivative of sin(x) with respect to x. A) cos(x) B) -cos(x)")
        )
        assertEquals(
            MathSymbolDetector.EscalationReason.MATCH_THE_FOLLOWING,
            MathSymbolDetector.analyze("Match the following Column I with Column II:\n1. Water  A. NaCl\n2. Salt   B. H2O")
        )
        assertEquals(
            MathSymbolDetector.EscalationReason.GARBLED_OCR_LAYOUT,
            MathSymbolDetector.analyze("n\nE\ni=1\n1\n2\n+\nx\n=")
        )

        // Plain English MCQ -> Does NOT waste vision tokens!
        assertEquals(
            MathSymbolDetector.EscalationReason.NONE,
            MathSymbolDetector.analyze("Which planet in our solar system is known as the Red Planet? A) Venus B) Mars C) Jupiter D) Saturn")
        )
        assertFalse(
            MathSymbolDetector.needsMathOrVisualVision(
                "Which planet in our solar system is known as the Red Planet? A) Venus B) Mars C) Jupiter D) Saturn"
            )
        )
    }

    @Test
    fun adaptiveFreeModelRouter_neverUsesArgonAndAdaptsToMathVsRapidMcq() {
        AdaptiveFreeModelRouter.resetForTesting()
        PrismAiSolverEngine.resetCloudCooldown()

        // 1. Legacy "argon" / "pro" inputs safely normalize to Gemini 3.8 Flash (never Argon!)
        assertEquals(PrismAiSolverEngine.MODEL_FLASH, PrismAiSolverEngine.normalizeCloudModel("gemini-4-argon"))
        assertEquals(PrismAiSolverEngine.MODEL_FLASH, PrismAiSolverEngine.normalizeCloudModel("argon"))
        assertEquals(PrismAiSolverEngine.MODEL_FLASH, PrismAiSolverEngine.normalizeCloudModel("gemini-2.5-pro"))
        assertEquals(PrismAiSolverEngine.MODEL_LITE, PrismAiSolverEngine.normalizeCloudModel("gemini-3.5-flash-lite"))

        // 2. GoogleFormsModelOption has zero Argon options and remaps legacy argon safely
        assertTrue(GoogleFormsModelOption.entries.none { it.id.contains("argon", ignoreCase = true) })
        assertEquals(GoogleFormsModelOption.GEMINI_38_FLASH, GoogleFormsModelOption.fromId("gemini-4-argon"))

        // 3. Math question with screenshot -> auto-escalates to Gemini 3.8 Flash Vision, bypasses text-only Cloudflare
        val mathRoute = AdaptiveFreeModelRouter.selectRoute(
            extractedText = "Find Σ (k=1..n) 1/2^k",
            hasBitmap = true,
            subjectMode = SubjectMode.MATH,
            solveMode = QuestionSolveMode.STANDARD,
            nowMs = 10_000L
        )
        assertEquals(PrismAiSolverEngine.MODEL_FLASH, mathRoute.primaryGeminiModel)
        assertTrue(mathRoute.attachMathVision)
        assertFalse(mathRoute.preferCloudflareFirst)

        // 4. Rapid plain MCQ streak -> switches dynamically to Gemini 3.5 Flash-Lite + Workers AI fast lane
        val plainMcq = "What is the capital city of France? A) Lyon B) Paris C) Marseille D) Nice"
        AdaptiveFreeModelRouter.recordOutcome(PrismAiSolverEngine.MODEL_LITE, success = true, latencyMs = 380L, wasPlainMcq = true, nowMs = 20_000L)
        AdaptiveFreeModelRouter.recordOutcome(PrismAiSolverEngine.MODEL_LITE, success = true, latencyMs = 360L, wasPlainMcq = true, nowMs = 25_000L)
        AdaptiveFreeModelRouter.recordOutcome(PrismAiSolverEngine.MODEL_LITE, success = true, latencyMs = 350L, wasPlainMcq = true, nowMs = 30_000L)

        val rapidRoute = AdaptiveFreeModelRouter.selectRoute(
            extractedText = plainMcq,
            hasBitmap = true,
            subjectMode = SubjectMode.GENERAL,
            solveMode = QuestionSolveMode.STANDARD,
            nowMs = 34_000L
        )
        assertEquals(PrismAiSolverEngine.MODEL_LITE, rapidRoute.primaryGeminiModel)
        assertFalse(rapidRoute.attachMathVision)
        assertFalse(rapidRoute.preferCloudflareFirst)
        assertFalse(rapidRoute.primaryGeminiModel.contains("argon", ignoreCase = true))
        assertFalse(rapidRoute.fallbackGeminiModel.contains("argon", ignoreCase = true))

        AdaptiveFreeModelRouter.resetForTesting()
    }

    @Test
    fun interactiveDemo_rendersCleanlyAndSolvesOnNarrowAccessibleViewport() {
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    LocalReducedMotion provides true,
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.30f)
                ) {
                    Box(modifier = Modifier.width(280.dp)) {
                        LiveInteractiveDemoCard()
                    }
                }
            }
        }
        composeTestRule.onNodeWithTag("card_interactive_live_demo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_live_demo_pill").performClick()
        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("box_live_demo_result").assertIsDisplayed()
    }

    @Test
    fun offlineRoomCacheVault_displaysSummaryAndDeletesSingleEntry() {
        var deletedEntryId: Long? = null

        val sampleHistory = listOf(
            SolvedHistoryEntry(
                id = 42L,
                questionPreview = "Which organelle generates ATP?",
                answerLetter = "B",
                sourceLabel = "Offline Room Cache",
                timestamp = "Just now",
                explanation = "Mitochondria generate ATP.",
                subject = "MEDICAL",
                bookmarked = true,
                isCachedResult = true,
                solveLatencyMs = 0L,
                modelUsed = "Offline Room Cache"
            )
        )

        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                ExportQuestionsScreen(
                    entries = sampleHistory,
                    offlineCacheCount = 12,
                    offlineCacheSavedCalls = 29,
                    onBackToHome = {},
                    onToggleBookmarkEntry = {},
                    onDeleteEntry = { deletedEntryId = it },
                    onClearOfflineCache = {},
                    onAddSampleExport = {},
                    onClearExportHistory = {},
                    onShowBannerMessage = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("card_offline_room_cache_vault").assertIsDisplayed()
        composeTestRule.onNodeWithTag("export_questions_screen_list")
            .performScrollToNode(hasTestTag("btn_delete_history_42"))
        composeTestRule.onNodeWithTag("btn_delete_history_42").performClick()
        assertEquals(42L, deletedEntryId)
    }

    @Test
    fun settingsScreen_showsPlayUpdatesAndShareCardWithoutDeveloperDb3Field() {
        var checkedUpdates = false
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                SettingsScreen(
                    state = MainUiState(
                        offlineCacheCount = 8,
                        offlineCacheSavedCalls = 15
                    ),
                    sample = PracticeSample(
                        category = "Math",
                        question = "2 + 2 = ?",
                        options = listOf("A) 3", "B) 4", "C) 5", "D) 6"),
                        correctLetter = "B",
                        explanation = "2 + 2 = 4"
                    ),
                    listState = rememberLazyListState(),
                    onSaveApiKeys = { _, _, _, _ -> },
                    onTestApiKeys = { _, _, _, _ -> },
                    onClearApiKeys = {},
                    onToggleAutoFailover = {},
                    onToggleChargingCamo = {},
                    onSignInGoogle = {},
                    onSignOutGoogle = {},
                    onOpenGeminiWebLogin = {},
                    onRequestOverlayPermission = {},
                    onSelectHudStyle = {},
                    onSetCustomOpacityPct = {},
                    onToggleDfTwoPartCapture = {},
                    onToggleDfScrollTimer = {},
                    onToggleDfComplexMode = {},
                    onToggleDfDiagramVision = {},
                    onToggleDfQuickCrop = {},
                    onToggleDfSwipeMethodLoop = {},
                    onToggleHaptics = {},
                    onTestVibration = {},
                    onToggleInstantDetect = {},
                    onSolvePractice = {},
                    onNextPractice = {},
                    onClearHistory = {},
                    onSelectSolverMethod = {},
                    onToggleAdvCropToScan = {},
                    onToggleAdvIslandGestures = {},
                    onToggleAdvSplitPill = {},
                    onToggleAdvMinimalShapes = {},
                    onToggleAdvNetSpeedDisguise = {},
                    onToggleAdvAutoFadeIdle = {},
                    onToggleAdvFlashcardsExport = {},
                    onToggleAdvDarkObsidian = {},
                    onToggleAdvSpringPhysics = {},
                    onUpdateSpringPhysics = { _, _ -> },
                    onToggleAdvSplitScreenRail = {},
                    onToggleAdvQuickTileNotif = {},
                    onToggleDualVerify = {},
                    onFlashcardSearchChange = {},
                    onFlashcardSubjectFilterChange = {},
                    onToggleFlashcardBookmarkedOnly = {},
                    onToggleBookmarkEntry = {},
                    onShowBannerMessage = {},
                    playUpdateState = PlayUpdateUiState.Available(isImmediate = false, availableVersionCode = 99),
                    onCheckForPlayUpdates = { checkedUpdates = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_screen_list")
            .performScrollToNode(hasTestTag("card_settings_updates_and_share"))
        composeTestRule.onNodeWithTag("card_settings_updates_and_share").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_check_play_updates").performClick()
        assertTrue(checkedUpdates)
        composeTestRule.onNodeWithTag("btn_share_with_friends").assertIsDisplayed()
        // Developer-only DB3 HTTPS URL field must no longer exist
        composeTestRule.onNodeWithText("Shared Edge Cache DB (DB 3 • Optional)").assertDoesNotExist()
    }

    @Test
    fun onboardingOverlay_rendersClearCopyAndEmbeddedLiveDemo() {
        var finished = false
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    NewUserFeatureGuideOverlay(
                        visible = true,
                        onFinishGuide = { finished = true }
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("new_user_feature_guide_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_interactive_live_demo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_skip_feature_guide").performClick()
        assertTrue(finished)
    }

    @Test
    fun antiPoisonSelfHealingCache_detectsRescanEvictsWrongAnswerAndOverwritesWithVerified() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = PrismStudyDatabase.buildInMemoryForTesting(context)
        val orchestrator = OverlaySolveOrchestrator(
            appContext = context,
            offlineHistoryRepository = OfflineStudyHistoryRepository(db.solvedQuestionDao()),
            solveResultCache = SolveResultCache()
        )
        // Create a non-uniform 64x64 synthetic quiz screenshot so dHash is non-zero and deterministic
        val frameBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                val shade = ((x * 3 + y * 5) % 255)
                frameBitmap.setPixel(x, y, Color.rgb(shade, (shade + 40) % 255, (shade + 80) % 255))
            }
        }
        val rawQuestion = "Which value is NOT a prime number?\nA) 7\nB) 11\nC) 21\nD) 29"
        val visualHex = java.lang.Long.toUnsignedString(SolveResultCache.computePerceptualHash(frameBitmap), 16)

        try {
            PersistentQuizCacheManager.disableNetworkForTest = true
            PersistentQuizCacheManager.clearRejectedHashesForTesting()

            // 1. AI initially returns a WRONG answer ("B") and caches it across RAM + Room SQLite
            val wrongAnswer = AnswerModel(
                correctChoice = "B",
                shortAnswer = "11",
                explanation = "Initial misread of NOT prime.",
                subject = SubjectMode.MATH.id,
                confidence = 88,
                engineLabel = "Gemini AI"
            )
            orchestrator.cacheResolvedSolve(
                bitmap = frameBitmap,
                cropRect = null,
                rawText = rawQuestion,
                modelId = PrismAiSolverEngine.MODEL_FLASH,
                subjectMode = SubjectMode.MATH,
                solveMode = QuestionSolveMode.STANDARD,
                answer = wrongAnswer,
                overwriteAfterRescan = false
            )
            // Also persist synchronously to Room for deterministic verification
            PersistentQuizCacheManager.storeVerifiedAnswer(
                context = context,
                questionText = rawQuestion,
                subject = SubjectMode.MATH,
                answerBadge = "B",
                shortAnswer = "11",
                explanation = "Initial misread of NOT prime.",
                confidence = 88,
                visualHash = visualHex
            )

            // Confirm initial cache hit returns "B"
            val initialRoomHit = PersistentQuizCacheManager.lookupAcrossDatabases(
                context = context,
                questionText = rawQuestion,
                subject = SubjectMode.MATH,
                visualHash = visualHex
            )
            assertNotNull(initialRoomHit)
            assertEquals("B", initialRoomHit!!.answerBadge)

            // 2. User spots the error and taps Solve AGAIN on the same question!
            // Both visual frame matching AND text matching detect it automatically!
            assertEquals("B", orchestrator.detectSameQuestionRescan(frameBitmap, null))
            assertEquals("B", orchestrator.detectSameQuestionRescan(null, rawQuestion))

            // Critical Re-Check prompt explicitly warns AI about the flagged wrong answer "B"
            val recheckPrompt = OverlaySolveOrchestrator.buildCriticalRecheckPrompt(rawQuestion, "B")
            assertTrue(recheckPrompt.contains("CRITICAL RE-CHECK"))
            assertTrue(recheckPrompt.contains("'B'"))
            assertTrue(recheckPrompt.contains("NOT"))

            // 3. Evict wrong answer everywhere -> RAM LRU, Room SQLite (text + visualHash), and session crowd-replay block
            orchestrator.invalidateWrongAnswerEverywhere(
                bitmap = frameBitmap,
                rawText = rawQuestion,
                subjectMode = SubjectMode.MATH
            )

            assertNull(
                "Same-Capture RAM cache must be purged after eviction",
                orchestrator.solveResultCache.getSameCapture(
                    bitmap = frameBitmap,
                    cropRect = null,
                    modelId = PrismAiSolverEngine.MODEL_FLASH,
                    subjectMode = SubjectMode.MATH.id,
                    solveMode = QuestionSolveMode.STANDARD.id
                )
            )
            val (normQ, normOpts) = SolveResultCache.splitNormalizedQuestionAndOptions(rawQuestion)
            assertNull(
                "AI Answer RAM cache must be purged after eviction",
                orchestrator.solveResultCache.getAiAnswer(
                    modelId = PrismAiSolverEngine.MODEL_FLASH,
                    subjectMode = SubjectMode.MATH.id,
                    solveMode = QuestionSolveMode.STANDARD.id,
                    normalizedQuestion = normQ,
                    normalizedOptions = normOpts
                )
            )
            assertNull(
                "Room cache lookup by text hash must return null after eviction",
                PersistentQuizCacheManager.lookupAcrossDatabases(
                    context = context,
                    questionText = rawQuestion,
                    subject = SubjectMode.MATH,
                    visualHash = ""
                )
            )
            assertNull(
                "Room cache lookup by visualHash must return null after eviction",
                PersistentQuizCacheManager.lookupAcrossDatabases(
                    context = context,
                    questionText = "garbled ocr",
                    subject = SubjectMode.MATH,
                    visualHash = visualHex
                )
            )

            // 4. Fresh Vision Dual-Verify resolves the TRUE answer ("C" = 21) and SELF-HEALS Room + RAM!
            val verifiedAnswer = AnswerModel(
                correctChoice = "C",
                shortAnswer = "21",
                explanation = "21 = 3 × 7, so 21 is NOT prime.",
                subject = SubjectMode.MATH.id,
                confidence = 99,
                engineLabel = "Vision Re-Check • Gemini AI"
            )
            PersistentQuizCacheManager.storeVerifiedAnswer(
                context = context,
                questionText = rawQuestion,
                subject = SubjectMode.MATH,
                answerBadge = verifiedAnswer.correctChoice,
                shortAnswer = verifiedAnswer.shortAnswer,
                explanation = verifiedAnswer.explanation,
                confidence = verifiedAnswer.confidence,
                visualHash = visualHex,
                allowOverwriteRejected = true
            )
            orchestrator.cacheResolvedSolve(
                bitmap = frameBitmap,
                cropRect = null,
                rawText = rawQuestion,
                modelId = PrismAiSolverEngine.MODEL_FLASH,
                subjectMode = SubjectMode.MATH,
                solveMode = QuestionSolveMode.STANDARD,
                answer = verifiedAnswer,
                overwriteAfterRescan = true
            )

            // 5. Subsequent offline lookup now returns the CORRECTED "C" (21) permanently!
            val healedHit = PersistentQuizCacheManager.lookupAcrossDatabases(
                context = context,
                questionText = rawQuestion,
                subject = SubjectMode.MATH,
                visualHash = visualHex
            )
            assertNotNull("Expected self-healed Room cache hit after verified re-solve", healedHit)
            assertEquals("C", healedHit!!.answerBadge)
            assertEquals("21", healedHit.shortAnswer)
        } finally {
            PersistentQuizCacheManager.clearRejectedHashesForTesting()
            PersistentQuizCacheManager.disableNetworkForTest = false
            if (!frameBitmap.isRecycled) frameBitmap.recycle()
            db.close()
        }
    }

    @Test
    fun dynamicIslandLayout_includesSourceTagAndOneTapWrongResolveButton() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = LayoutInflater.from(context).inflate(R.layout.layout_dynamic_island, null, false)

        val actionRow = view.findViewById<View>(R.id.ll_island_resolve_action_row)
        val sourceTag = view.findViewById<TextView>(R.id.tv_island_source_tag)
        val wrongResolveBtn = view.findViewById<TextView>(R.id.btn_island_wrong_resolve)

        assertNotNull("Dynamic Island drawer must include re-solve action row", actionRow)
        assertNotNull("Dynamic Island drawer must include answer source badge", sourceTag)
        assertNotNull("Dynamic Island drawer must include 1-tap Wrong? Re-Solve button", wrongResolveBtn)
        assertTrue("Wrong? Re-Solve button must be clickable", wrongResolveBtn.isClickable)
        assertTrue(wrongResolveBtn.text.toString().contains("Re-Solve", ignoreCase = true))
    }

    @Test
    fun dynamicIsland_inPlaceIconToolbarAndLightSurfaceDrawableWorkCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = LayoutInflater.from(context).inflate(R.layout.layout_dynamic_island, null, false)

        // All 7 in-place toolbar buttons must inflate inside Row 1 alongside ll_island_body
        assertNotNull(view.findViewById<View>(R.id.ll_island_body))
        assertNotNull(view.findViewById<View>(R.id.ll_island_inline_tools))
        val inlineIds = listOf(
            R.id.btn_island_tool_scan,
            R.id.btn_island_tool_crop,
            R.id.btn_island_tool_resolve,
            R.id.btn_island_tool_mode,
            R.id.btn_island_tool_methods,
            R.id.btn_island_tool_explain,
            R.id.btn_island_tool_stop
        )
        inlineIds.forEach { id ->
            val btn = view.findViewById<View>(id)
            assertNotNull("Expected inline Dynamic Island button id=$id", btn)
            assertTrue("Inline Dynamic Island button id=$id must be clickable", btn.isClickable)
        }

        // DynamicIslandBorderDrawable adapts cleanly between Light iOS Liquid Glass & Dark themes
        val drawable = DynamicIslandBorderDrawable(context.resources.displayMetrics.density)
        assertFalse(drawable.isLightSurfaceMode())
        drawable.setThemeSurfaceMode(isLight = true, surfaceHex = "#FFFFFF")
        assertTrue(drawable.isLightSurfaceMode())
        drawable.setThemeSurfaceMode(isLight = false, surfaceHex = "#0A0A0C")
        assertFalse(drawable.isLightSurfaceMode())
    }

    @Test
    fun iosLiquidGlassLight_optInThemeRendersInteractiveMorphingIslandAndPreservesDarkDefault() {
        // Default theme stays dark; iOS Liquid Glass Light is opt-in and resolves cleanly by ID
        assertTrue(NORMAL_DEFAULT_THEME.isDark)
        assertFalse(IOS_LIQUID_LIGHT_THEME.isDark)
        assertEquals(ThemeLayoutArchetype.IOS_LIQUID_LIGHT, IOS_LIQUID_LIGHT_THEME.layoutArchetype)
        assertEquals(IOS_LIQUID_LIGHT_THEME, findThemePresetById("ios_liquid_light"))
        assertEquals(2, ALL_50_UNSLOP_THEMES.size)

        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = IOS_LIQUID_LIGHT_THEME) {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    IosLiquidLightHomeLayout(
                        state = MainUiState(
                            effectiveThemeSpec = IOS_LIQUID_LIGHT_THEME,
                            offlineCacheCount = 14,
                            offlineCacheSavedCalls = 31
                        ),
                        listState = rememberLazyListState(),
                        onToggleOverlay = {},
                        onRequestOverlayPermission = {},
                        onOpenHomeSubScreen = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("home_ios_liquid_light_layout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_ios_kinetic_island_showcase").assertIsDisplayed()
        // Tapping the Prism logo morphs text into inline feature icons in-place!
        composeTestRule.onNodeWithTag("btn_ios_preview_logo_morph").performClick()
        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("row_ios_preview_inline_icons").assertIsDisplayed()
    }

    @Test
    fun themeStudio_showsFlagshipIosLiquidLightOptInCardAndSelectsIt() {
        var selectedId: String? = null
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    ThemeStudioScreen(
                        state = MainUiState(effectiveThemeSpec = NORMAL_DEFAULT_THEME),
                        onSelectLocalPreset = { selectedId = it.themeId },
                        onRevertToNormalDefault = {},
                        onSelectAnimationOverride = {},
                        onSelectAppearanceOverride = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("theme_studio_screen_list")
            .performScrollToNode(hasTestTag("card_theme_ios_liquid_light"))
        composeTestRule.onNodeWithTag("card_theme_ios_liquid_light").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_theme_ios_liquid_light").performClick()
        assertEquals(IOS_LIQUID_LIGHT_THEME.themeId, selectedId)
    }

    @Test
    fun batterySurvivalHelperAndSettingsKeepAliveCard_renderAndExposeActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val status = BatterySurvivalHelper.inspect(context)
        assertTrue(status.oemDisplayName.isNotBlank())
        assertTrue(status.oemStepsHint.isNotBlank())
        assertTrue(status.shortBadge.isNotBlank())

        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                SettingsScreen(
                    state = MainUiState(isOverlayRunning = true),
                    sample = PracticeSample(
                        category = "Math",
                        question = "2 + 2 = ?",
                        options = listOf("A) 3", "B) 4"),
                        correctLetter = "B",
                        explanation = "4"
                    ),
                    listState = rememberLazyListState(),
                    onSaveApiKeys = { _, _, _, _ -> },
                    onTestApiKeys = { _, _, _, _ -> },
                    onClearApiKeys = {},
                    onToggleAutoFailover = {},
                    onToggleChargingCamo = {},
                    onSignInGoogle = {},
                    onSignOutGoogle = {},
                    onOpenGeminiWebLogin = {},
                    onRequestOverlayPermission = {},
                    onSelectHudStyle = {},
                    onSetCustomOpacityPct = {},
                    onToggleDfTwoPartCapture = {},
                    onToggleDfScrollTimer = {},
                    onToggleDfComplexMode = {},
                    onToggleDfDiagramVision = {},
                    onToggleDfQuickCrop = {},
                    onToggleDfSwipeMethodLoop = {},
                    onToggleHaptics = {},
                    onTestVibration = {},
                    onToggleInstantDetect = {},
                    onSolvePractice = {},
                    onNextPractice = {},
                    onClearHistory = {},
                    onSelectSolverMethod = {},
                    onToggleAdvCropToScan = {},
                    onToggleAdvIslandGestures = {},
                    onToggleAdvSplitPill = {},
                    onToggleAdvMinimalShapes = {},
                    onToggleAdvNetSpeedDisguise = {},
                    onToggleAdvAutoFadeIdle = {},
                    onToggleAdvFlashcardsExport = {},
                    onToggleAdvDarkObsidian = {},
                    onToggleAdvSpringPhysics = {},
                    onUpdateSpringPhysics = { _, _ -> },
                    onToggleAdvSplitScreenRail = {},
                    onToggleAdvQuickTileNotif = {},
                    onToggleDualVerify = {},
                    onFlashcardSearchChange = {},
                    onFlashcardSubjectFilterChange = {},
                    onToggleFlashcardBookmarkedOnly = {},
                    onToggleBookmarkEntry = {},
                    onShowBannerMessage = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_screen_list")
            .performScrollToNode(hasTestTag("card_settings_battery_keep_alive"))
        composeTestRule.onNodeWithTag("card_settings_battery_keep_alive").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_disable_battery_optimization").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_settings_stop_island").assertIsDisplayed()
    }
}
