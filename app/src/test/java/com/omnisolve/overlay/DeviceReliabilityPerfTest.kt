package com.omnisolve.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.omnisolve.overlay.api.OnlineConsensusRaceEngine
import com.omnisolve.overlay.api.PrismAiSolverEngine
import com.omnisolve.overlay.capture.OcrEngine
import com.omnisolve.overlay.capture.OcrExtractionResult
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.service.OverlayService
import com.omnisolve.overlay.ui.DynamicIslandBorderDrawable
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.MainViewModel
import com.omnisolve.overlay.ui.PracticeSample
import com.omnisolve.overlay.ui.components.AppTab
import com.omnisolve.overlay.ui.components.LiquidMorphBottomDock
import com.omnisolve.overlay.ui.screens.AnswerModelScreen
import com.omnisolve.overlay.ui.screens.DynamicIslandStudioScreen
import com.omnisolve.overlay.ui.screens.GoogleFormsSolveScreen
import com.omnisolve.overlay.ui.screens.HomeScreen
import com.omnisolve.overlay.ui.screens.SettingsScreen
import com.omnisolve.overlay.ui.screens.StatsScreen
import com.omnisolve.overlay.ui.theme.NORMAL_DEFAULT_THEME
import com.omnisolve.overlay.ui.theme.PrismCurves
import com.omnisolve.overlay.ui.theme.WhiteGlassTheme
import com.omnisolve.overlay.util.PrismPerfLogger
import java.net.UnknownHostException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceReliabilityPerfTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private class FakeDiagnosticOcrEngine(
        private val rawText: String,
        private val blocks: List<Pair<String, Rect>>
    ) : OcrEngine() {
        override suspend fun extractTextWithDiagnostics(
            bitmap: Bitmap,
            sessionId: Long
        ): OcrExtractionResult {
            return OcrExtractionResult(
                text = rawText,
                blocks = blocks,
                durationMs = 18L,
                passCount = 1
            )
        }
    }

    @Test
    fun testPrismPerfLogger_SanitizesKeysTokensEmailsAndNewlines() {
        val raw = """
            auth=AIzaSyDummyGoogleKey1234567890
            openai=sk-projAbCdEfGhIjKlMnOpQrStUvWxYz
            nvidia=nvapi-1234567890abcdef1234567890
            jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.sigValue123
            hdr="Bearer super_secret_token_999" user=student.test@example.com
        """.trimIndent()

        val sanitized = PrismPerfLogger.sanitizeDetail(raw)
        assertFalse("Google key must be redacted", sanitized.contains("AIzaSy"))
        assertFalse("OpenAI key must be redacted", sanitized.contains("sk-proj"))
        assertFalse("Nvidia key must be redacted", sanitized.contains("nvapi-"))
        assertFalse("JWT must be redacted", sanitized.contains("eyJhbG"))
        assertFalse("Bearer token must be redacted", sanitized.contains("super_secret_token_999"))
        assertFalse("Email must be redacted", sanitized.contains("student.test@example.com"))
        assertFalse("Double quotes must be sanitized", sanitized.contains('"'))
        assertFalse("Newlines must be stripped", sanitized.contains('\n'))
        assertTrue("Max detail length bounded", sanitized.length <= 160)
    }

    @Test
    fun testPrismAiSolverEngine_ClassifiesPermanentVsTransientFailures() = runBlocking {
        val appCheckErr = IllegalStateException("HTTP 403 App Check token is invalid")
        assertEquals(
            PrismAiSolverEngine.CloudFailureCategory.PERMANENT_AUTH_OR_APP_CHECK,
            PrismAiSolverEngine.classifyCloudFailure(appCheckErr)
        )
        assertTrue(PrismAiSolverEngine.isNonRetryableCloudFailure(appCheckErr))

        val unauthErr = IllegalStateException("UNAUTHENTICATED: 401 token rejected")
        assertEquals(
            PrismAiSolverEngine.CloudFailureCategory.PERMANENT_AUTH_OR_APP_CHECK,
            PrismAiSolverEngine.classifyCloudFailure(unauthErr)
        )
        assertTrue(PrismAiSolverEngine.isNonRetryableCloudFailure(unauthErr))

        val quotaErr = IllegalStateException("RESOURCE_EXHAUSTED: 429 quota exceeded")
        assertEquals(
            PrismAiSolverEngine.CloudFailureCategory.QUOTA_RATE_LIMIT,
            PrismAiSolverEngine.classifyCloudFailure(quotaErr)
        )
        assertTrue(PrismAiSolverEngine.isNonRetryableCloudFailure(quotaErr))

        val dnsErr = UnknownHostException("Unable to resolve host firestore.googleapis.com")
        assertEquals(
            PrismAiSolverEngine.CloudFailureCategory.TRANSIENT_TIMEOUT_OR_NETWORK,
            PrismAiSolverEngine.classifyCloudFailure(dnsErr)
        )
        assertFalse(
            "Transient DNS error must remain retryable and not poison cooldown",
            PrismAiSolverEngine.isNonRetryableCloudFailure(dnsErr)
        )

        var caughtTimeout: Throwable? = null
        withTimeoutOrNull(1L) {
            try {
                kotlinx.coroutines.withTimeout(1L) {
                    delay(500L)
                }
            } catch (t: Throwable) {
                caughtTimeout = t
            }
        }
        assertNotNull(caughtTimeout)
        assertEquals(
            PrismAiSolverEngine.CloudFailureCategory.TRANSIENT_TIMEOUT_OR_NETWORK,
            PrismAiSolverEngine.classifyCloudFailure(caughtTimeout!!)
        )
        assertFalse(PrismAiSolverEngine.isNonRetryableCloudFailure(caughtTimeout!!))
    }

    @Test
    fun testOnlineConsensusRaceEngine_HandlesHangingWebEngineAndTerminatesCleanly() = runBlocking {
        val blocks = listOf(
            "What is the boiling point of water at standard atmospheric pressure?" to Rect(40, 200, 960, 280),
            "A) 50 C" to Rect(40, 320, 600, 380),
            "B) 100 C" to Rect(40, 400, 600, 460),
            "C) 150 C" to Rect(40, 480, 600, 540),
            "D) 212 C" to Rect(40, 560, 600, 620)
        )
        val rawText = blocks.joinToString("\n") { it.first }
        val bmp = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.BLACK)
        }
        val fakeOcr = FakeDiagnosticOcrEngine(rawText, blocks)
        val emittedUpdates = mutableListOf<OnlineConsensusRaceEngine.ConsensusProgress>()
        val sessionId = PrismPerfLogger.nextSessionId()

        try {
            val result = OnlineConsensusRaceEngine.solveWithAllOnlineEngines(
                context = context,
                rawBitmap = bmp,
                preExtractedText = rawText,
                preExtractedBlocks = blocks,
                ocrEngine = fakeOcr,
                subjectMode = SubjectMode.SCIENCE,
                solveMode = QuestionSolveMode.STANDARD,
                solveSessionId = sessionId,
                geminiWebSolver = { _, _, _ ->
                    // Simulate a fast WebView reply while other network engines fail/timeout in offline test JVM
                    AnswerModel(
                        correctChoice = "B",
                        shortAnswer = "100 C",
                        explanation = "Water boils at 100 C at 1 atm.",
                        engineLabel = "Gemini Web AI"
                    )
                },
                onConsensusUpdate = { progress ->
                    emittedUpdates.add(progress)
                }
            )

            assertEquals(sessionId, result.solveSessionId)
            assertTrue("Engine lifecycle telemetry must be recorded", result.engineStates.isNotEmpty())
            assertTrue(
                "Gemini Web AI should record SUCCESS state",
                result.engineStates.any {
                    it.engineName == "Gemini Web AI" &&
                        it.state == OnlineConsensusRaceEngine.EngineLifecycleState.SUCCESS &&
                        it.badge == "B"
                }
            )
            assertEquals("B", result.winningOption)
            assertTrue("Live updates must have unique (displayBadge, totalVotesSoFar) pairs",
                emittedUpdates.map { it.displayBadge to it.totalVotesSoFar }.distinct().size == emittedUpdates.size
            )
        } finally {
            fakeOcr.close()
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    @Test
    fun testDynamicIslandBorderDrawable_PausesWhenHostHiddenAndStopsCleanly() {
        val drawable = DynamicIslandBorderDrawable(density = 3.0f)
        drawable.setBounds(0, 0, 500, 110)

        drawable.setScanning(true)
        assertTrue(drawable.isScanning())
        assertTrue(drawable.isAnimating())

        // Hiding host (e.g. during screenshot capture) stops animators while preserving scanning intent
        drawable.setHostVisible(false)
        assertTrue("Scanning state retained while host hidden", drawable.isScanning())
        assertFalse("Animators must stop when host is invisible", drawable.isAnimating())

        // Re-showing host resumes orbit animation smoothly
        drawable.setHostVisible(true)
        assertTrue(drawable.isScanning())
        assertTrue("Orbit animator must resume when host returns visible", drawable.isAnimating())

        // Idempotent full stop clears everything cleanly
        drawable.stopOrbitAnimation()
        assertFalse(drawable.isScanning())
        assertFalse(drawable.isAnimating())
        drawable.stopOrbitAnimation()
        assertFalse(drawable.isAnimating())
    }

    @Test
    fun testOcrEngine_RecycledBitmapReturnsInvalidBitmapWithoutHanging() = runBlocking {
        val ocrEngine = OcrEngine()
        val recycled = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        recycled.recycle()

        try {
            val result = ocrEngine.extractTextWithDiagnostics(recycled, sessionId = 999L)
            assertEquals("INVALID_BITMAP", result.errorReason)
            assertEquals("", result.text)
            assertTrue(result.blocks.isEmpty())
            assertFalse(result.timedOut)
        } finally {
            ocrEngine.close()
        }
    }

    @Test
    fun testPrismCurves_ConcentricHierarchyAndSharpPresetPreservation() {
        val rounded = NORMAL_DEFAULT_THEME
        val hero = PrismCurves.heroCorner(rounded)
        val card = PrismCurves.cardCorner(rounded)
        val row = PrismCurves.rowCorner(rounded)
        val iconWell = PrismCurves.iconWellCorner(rounded)
        val pill = PrismCurves.pillOrButtonCorner(rounded)

        assertTrue("Hero curve should be wider than card curve", hero > card)
        assertTrue("Card curve should be wider than row curve", card > row)
        assertTrue("Row curve should be at least icon well curve", row >= iconWell)
        assertEquals(999.dp, pill)
        assertEquals(32.dp, PrismCurves.dockCorner(rounded))
        assertEquals(26.dp, PrismCurves.dockPillCorner(rounded))

        val sharpTheme = NORMAL_DEFAULT_THEME.copy(cornerRadiusDp = 4)
        assertEquals(4.dp, PrismCurves.heroCorner(sharpTheme))
        assertEquals(4.dp, PrismCurves.cardCorner(sharpTheme))
        assertEquals(4.dp, PrismCurves.rowCorner(sharpTheme))
        assertEquals(4.dp, PrismCurves.pillOrButtonCorner(sharpTheme))
        assertEquals(8.dp, PrismCurves.dockCorner(sharpTheme))
    }

    @Test
    fun testFluidAnimationsPreference_DefaultsTrueAndTogglesCleanly() {
        val prefs = context.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        prefs.edit().remove(MainActivity.PREF_KEY_FLUID_ANIMATIONS_ENABLED).commit()

        val vm = MainViewModel(ApplicationProvider.getApplicationContext())
        assertTrue(
            "Fluid/glitch/island animations should be enabled by default",
            vm.uiState.value.fluidAnimationsEnabled
        )

        var syncCalls = 0
        vm.setFluidAnimationsEnabled(false) { syncCalls++ }
        assertFalse(vm.uiState.value.fluidAnimationsEnabled)
        assertFalse(prefs.getBoolean(MainActivity.PREF_KEY_FLUID_ANIMATIONS_ENABLED, true))
        assertEquals(1, syncCalls)

        vm.setFluidAnimationsEnabled(true) { syncCalls++ }
        assertTrue(vm.uiState.value.fluidAnimationsEnabled)
        assertTrue(prefs.getBoolean(MainActivity.PREF_KEY_FLUID_ANIMATIONS_ENABLED, false))
        assertEquals(2, syncCalls)
        assertEquals("EXTRA_FLUID_ANIMATIONS", OverlayService.EXTRA_FLUID_ANIMATIONS)
    }

    @Test
    fun testOverlayService_SafeBoundsKeepDynamicIslandInsideScreenAndOutOfStatusBar() {
        // 1. Standard phone usable screen (1080x2100 after status bar / cutout insets)
        val phoneBounds = OverlayService.computeSafeOverlayBounds(
            usableWidthPx = 1080,
            usableHeightPx = 2100,
            viewWidthPx = 560,
            viewHeightPx = 132,
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            edgeMarginPx = 18,
            topBufferPx = 24
        )
        assertTrue("Centered island minX should be symmetric negative", phoneBounds.minX < 0)
        assertEquals("Centered island minX/maxX should be balanced", -phoneBounds.minX, phoneBounds.maxX)
        assertEquals("minY must never enter status bar / cutout pull-down zone", 24, phoneBounds.minY)
        assertTrue("maxY must leave bottom margin", phoneBounds.maxY <= 2100 - 132 - 18)

        // Dragging way off top-left or bottom-right must clamp safely inside screen bounds
        val (topClampX, topClampY) = OverlayService.computeClampedOverlayPosition(
            candidateX = -900,
            candidateY = -120,
            usableWidthPx = 1080,
            usableHeightPx = 2100,
            viewWidthPx = 560,
            viewHeightPx = 132,
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            edgeMarginPx = 18,
            topBufferPx = 24
        )
        assertEquals(phoneBounds.minX, topClampX)
        assertEquals(24, topClampY)

        val (bottomClampX, bottomClampY) = OverlayService.computeClampedOverlayPosition(
            candidateX = 1200,
            candidateY = 2500,
            usableWidthPx = 1080,
            usableHeightPx = 2100,
            viewWidthPx = 560,
            viewHeightPx = 132,
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            edgeMarginPx = 18,
            topBufferPx = 24
        )
        assertEquals(phoneBounds.maxX, bottomClampX)
        assertEquals(phoneBounds.maxY, bottomClampY)

        // 2. Tiny Wear OS / compact watch usable screen (260x260) with large expanded drawer (300x320)
        val watchBounds = OverlayService.computeSafeOverlayBounds(
            usableWidthPx = 260,
            usableHeightPx = 260,
            viewWidthPx = 300,
            viewHeightPx = 320,
            gravity = Gravity.TOP or Gravity.START,
            edgeMarginPx = 12,
            topBufferPx = 18
        )
        assertTrue("Watch bounds must never invert maxX < minX", watchBounds.maxX >= watchBounds.minX)
        assertTrue("Watch bounds must never invert maxY < minY", watchBounds.maxY >= watchBounds.minY)
        assertEquals(12, watchBounds.minX)
        assertEquals(18, watchBounds.minY)
    }

    @Test
    fun testLiquidMorphBottomDock_RendersWithoutIntrinsicSubcomposeCrashAcrossSizes() {
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)
                ) {
                    Box(modifier = Modifier.width(260.dp)) {
                        LiquidMorphBottomDock(
                            selectedTab = AppTab.Home,
                            onSelectTab = {}
                        )
                    }
                }
            }
        }
        composeTestRule.onNodeWithTag("bottom_navigation_dock").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_stats").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_settings").assertIsDisplayed()
    }

    @Test
    fun testLaunchScaffoldWithDockAndHomeScreen_ComposesWithoutSubcomposeCrash() {
        val sampleState = MainUiState()
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)
                ) {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            LiquidMorphBottomDock(
                                selectedTab = AppTab.Home,
                                onSelectTab = {}
                            )
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = innerPadding.calculateTopPadding())
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                HomeScreen(
                                    state = sampleState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 600.dp),
                                    onToggleOverlay = {},
                                    onRequestOverlayPermission = {},
                                    onSelectSolverMethod = {},
                                    onSetCustomOpacityPct = {},
                                    onSelectQuestionSolveMode = {}
                                )
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onNodeWithTag("bottom_navigation_dock").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()
    }

    @Test
    fun testHomeScreen_ComposesCleanlyOnCompactWithLargeFontScale() {
        val sampleState = MainUiState()
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)
                ) {
                    Box(modifier = Modifier.width(280.dp)) {
                        HomeScreen(
                            state = sampleState,
                            onToggleOverlay = {},
                            onRequestOverlayPermission = {},
                            onSelectSolverMethod = {},
                            onSetCustomOpacityPct = {},
                            onSelectQuestionSolveMode = {}
                        )
                    }
                }
            }
        }
        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_toggle_overlay").assertIsDisplayed()
    }

    @Test
    fun testStatsScreen_ComposesCleanlyOnCompactWithLargeFontScale() {
        val sampleState = MainUiState()
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)
                ) {
                    Box(modifier = Modifier.width(300.dp)) {
                        StatsScreen(state = sampleState, onOpenSettings = {})
                    }
                }
            }
        }
        composeTestRule.onNodeWithTag("stats_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_ask_plus_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_ask_plus_toggle").performClick()
        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_ask_upload_photo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_ask_upload_pdf").assertIsDisplayed()
    }

    @Test
    fun testSettingsScreen_ComposesCleanlyOnCompactWithLargeFontScale() {
        val sampleState = MainUiState()
        val practice = PracticeSample(
            category = "Biology",
            question = "What is the powerhouse of the cell?",
            options = listOf("Golgi", "Mitochondria", "Ribosome", "Nucleus"),
            correctLetter = "B",
            explanation = "Mitochondria generate ATP via cellular respiration."
        )
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)
                ) {
                    val listState = rememberLazyListState()
                    Box(modifier = Modifier.width(300.dp)) {
                        SettingsScreen(
                            state = sampleState,
                            sample = practice,
                            listState = listState,
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
            }
        }
        composeTestRule.onNodeWithTag("settings_screen_list").assertIsDisplayed()
    }

    @Test
    fun testDynamicIslandStudioScreen_ComposesCleanlyOnCompactWithLargeFontScale() {
        val sampleState = MainUiState()
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)
                ) {
                    Box(modifier = Modifier.width(290.dp)) {
                        DynamicIslandStudioScreen(
                            state = sampleState,
                            onBackToHome = {},
                            onSetCustomOpacityPct = {},
                            onSelectQuestionSolveMode = {},
                            onSelectHudStyle = {},
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
        composeTestRule.onNodeWithTag("dynamic_island_studio_screen_list").assertIsDisplayed()
    }

    @Test
    fun testAnswerModelScreen_ComposesCleanlyOnCompactWithLargeFontScale() {
        val sampleState = MainUiState()
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)
                ) {
                    Box(modifier = Modifier.width(290.dp)) {
                        AnswerModelScreen(
                            state = sampleState,
                            onBackToHome = {},
                            onSelectSolverMethod = {},
                            onOpenSettings = {},
                            onSignInServer = {},
                            onOpenGeminiLogin = {}
                        )
                    }
                }
            }
        }
        composeTestRule.onNodeWithTag("answer_model_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_models_ocr_limited_signin").assertIsDisplayed()
    }

    @Test
    fun testUnauthenticatedMainViewModel_LocksNonOcrMethodsAndAllowsOcrOnlyMode() {
        val prefs = context.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove(MainActivity.PREF_KEY_SOLVER_METHOD)
            .commit()

        val vm = MainViewModel(ApplicationProvider.getApplicationContext())
        assertEquals(SolverMethod.BACKGROUND_GEMINI, vm.uiState.value.solverMethod)
        assertFalse(vm.uiState.value.signInPromptDismissedForOcrOnly)

        var requireSignInCalls = 0
        var syncCalls = 0

        listOf(
            SolverMethod.TURBO_HYBRID,
            SolverMethod.FIREBASE_SERVER,
            SolverMethod.CIRCLE_TO_SEARCH
        ).forEach { unlockedMethod ->
            vm.selectSolverMethod(
                method = unlockedMethod,
                onSyncService = { syncCalls++ },
                onRequireGoogleSignIn = { requireSignInCalls++ }
            )
            assertEquals(
                "Signed-out user must be able to pick $unlockedMethod freely with Workers AI",
                unlockedMethod,
                vm.uiState.value.solverMethod
            )
        }
        assertEquals(0, requireSignInCalls)
        assertEquals(3, syncCalls)

        vm.selectSolverMethod(
            method = SolverMethod.BACKGROUND_GEMINI,
            onSyncService = { syncCalls++ },
            onRequireGoogleSignIn = { requireSignInCalls++ }
        )
        assertEquals(SolverMethod.BACKGROUND_GEMINI, vm.uiState.value.solverMethod)
        assertEquals(4, syncCalls)
        assertEquals(0, requireSignInCalls)

        vm.continueWithOcrOnly()
        assertTrue(vm.uiState.value.signInPromptDismissedForOcrOnly)
    }

    @Test
    fun testDynamicIslandScaling_PresetsAndClampingBoundsWorkCorrectly() {
        val vm = MainViewModel(ApplicationProvider.getApplicationContext())
        var syncCount = 0

        vm.setIslandSizePreset(MainActivity.ISLAND_SIZE_XL) { syncCount++ }
        assertEquals(MainActivity.ISLAND_SIZE_XL, vm.uiState.value.islandSizePreset)
        assertEquals(145, vm.uiState.value.islandScalePct)

        vm.setIslandScalePct(50) { syncCount++ }
        assertEquals(85, vm.uiState.value.islandScalePct)

        vm.setIslandScalePct(200) { syncCount++ }
        assertEquals(145, vm.uiState.value.islandScalePct)

        vm.setIslandAnswerScale(MainActivity.ANSWER_SCALE_XL_BOLD) { syncCount++ }
        assertEquals(MainActivity.ANSWER_SCALE_XL_BOLD, vm.uiState.value.islandAnswerScale)
        assertEquals(4, syncCount)
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w411dp-h915dp-xhdpi")
    fun testHomeAndDynamicIslandStudio_DisplayOcrBannerAndScaleControlsOnCompactFontScale() {
        val signedOutState = MainUiState(firebaseUserId = null, firebaseUserEmail = null)
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)
                ) {
                    Box(modifier = Modifier.width(290.dp)) {
                        HomeScreen(
                            state = signedOutState,
                            onToggleOverlay = {},
                            onRequestOverlayPermission = {},
                            onSelectSolverMethod = {},
                            onSetCustomOpacityPct = {},
                            onSelectQuestionSolveMode = {}
                        )
                    }
                }
            }
        }
        composeTestRule.onNodeWithTag("home_screen_list")
            .performScrollToNode(hasTestTag("card_home_ocr_limited_signin"))
        composeTestRule.onNodeWithTag("card_home_ocr_limited_signin").assertIsDisplayed()
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w411dp-h915dp-xhdpi")
    fun testDynamicIslandStudioScreen_DisplaysOpacitySliderAndSolveModesOnCompact() {
        val sampleState = MainUiState()
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)
                ) {
                    Box(modifier = Modifier.width(295.dp)) {
                        DynamicIslandStudioScreen(
                            state = sampleState,
                            onBackToHome = {},
                            onSetCustomOpacityPct = {},
                            onSelectQuestionSolveMode = {},
                            onSelectHudStyle = {},
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
        composeTestRule.onNodeWithTag("dynamic_island_studio_screen_list")
            .performScrollToNode(hasTestTag("section_home_opacity"))
        composeTestRule.onNodeWithTag("section_home_opacity").assertIsDisplayed()
        composeTestRule.onNodeWithTag("slider_home_opacity_pct").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_home_mode_standard").assertIsDisplayed()
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w411dp-h915dp-xhdpi")
    fun testSettingsScreen_WhenSignedIn_DisplaysDeleteGoogleAccountButton() {
        val signedInState = MainUiState(
            firebaseUserId = "uid_123",
            firebaseUserName = "Test Student",
            firebaseUserEmail = "test@example.com"
        )
        val practice = PracticeSample(
            category = "Biology",
            question = "What is the powerhouse of the cell?",
            options = listOf("Golgi", "Mitochondria", "Ribosome", "Nucleus"),
            correctLetter = "B",
            explanation = "Mitochondria generate ATP via cellular respiration."
        )
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)
                ) {
                    val listState = rememberLazyListState()
                    Box(modifier = Modifier.width(300.dp)) {
                        SettingsScreen(
                            state = signedInState,
                            sample = practice,
                            listState = listState,
                            onSaveApiKeys = { _, _, _, _ -> },
                            onTestApiKeys = { _, _, _, _ -> },
                            onClearApiKeys = {},
                            onToggleAutoFailover = {},
                            onToggleChargingCamo = {},
                            onSignInGoogle = {},
                            onSignOutGoogle = {},
                            onDeleteGoogleAccount = {},
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
            }
        }
        composeTestRule.onNodeWithTag("settings_screen_list")
            .performScrollToNode(hasTestTag("btn_delete_google_account"))
        composeTestRule.onNodeWithTag("btn_delete_google_account").assertIsDisplayed()
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w411dp-h915dp-xhdpi")
    fun testHomeScreen_UsesSimpleThemesTitleWithout50ThemeClutter() {
        val state = MainUiState(firebaseUserId = "uid_123", firebaseUserEmail = "user@example.com")
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                HomeScreen(
                    state = state,
                    onToggleOverlay = {},
                    onRequestOverlayPermission = {},
                    onSelectSolverMethod = {},
                    onSetCustomOpacityPct = {},
                    onSelectQuestionSolveMode = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("home_screen_list")
            .performScrollToNode(hasTestTag("card_open_theme_studio"))
        composeTestRule.onNodeWithTag("card_open_theme_studio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Themes").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithText("50-Theme", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w411dp-h915dp-xhdpi")
    fun testGoogleFormsSolveScreen_PasteRemainsVisibleWhenUrlNotEmptyAndSampleChipLoadsUrl() {
        var latestUrl = com.omnisolve.overlay.api.GoogleFormsSolverEngine.SAMPLE_TEST_FORM_URL
        val nonEmptyState = MainUiState(
            firebaseUserId = "uid_123",
            googleFormUrlInput = latestUrl
        )
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                GoogleFormsSolveScreen(
                    state = nonEmptyState,
                    onBackToHome = {},
                    onUpdateUrl = { latestUrl = it },
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
                    onShowBannerMessage = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("btn_paste_google_form_url").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_clear_google_form_url").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_forms_more_options").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_load_sample_google_form").assertIsDisplayed()

        latestUrl = ""
        composeTestRule.onNodeWithTag("btn_load_sample_google_form").performClick()
        assertEquals(
            com.omnisolve.overlay.api.GoogleFormsSolverEngine.SAMPLE_TEST_FORM_URL,
            latestUrl
        )
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w411dp-h915dp-xhdpi")
    fun testThemeStudioScreen_LiveStageAndSignatureAtelierEditionsComposeAndSelectOnCompact() {
        var selectedThemeId: String? = null
        val state = MainUiState()
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)
                ) {
                    Box(modifier = Modifier.width(290.dp)) {
                        com.omnisolve.overlay.ui.screens.ThemeStudioScreen(
                            state = state,
                            onSelectLocalPreset = { selectedThemeId = it.themeId },
                            onRevertToNormalDefault = {},
                            onSelectAnimationOverride = {},
                            onSelectAppearanceOverride = {}
                        )
                    }
                }
            }
        }
        composeTestRule.onNodeWithTag("theme_studio_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_live_preview_stage").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_stage_play_motion").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("theme_studio_screen_list")
            .performScrollToNode(hasTestTag("section_atelier_themes"))
        composeTestRule.onNodeWithTag("card_atelier_atelier_prism_noir")
            .assertIsDisplayed()
            .performClick()
        assertEquals("atelier_prism_noir", selectedThemeId)
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w411dp-h915dp-xhdpi")
    fun testHomeScreen_RearrangesCleanlyAcrossAllFiveThemeLayoutArchetypesOnCompact() {
        val archetypeSpecs = listOf(
            NORMAL_DEFAULT_THEME,
            com.omnisolve.overlay.ui.theme.findThemePresetById("atelier_prism_noir"),
            com.omnisolve.overlay.ui.theme.findThemePresetById("atelier_swiss_glacier"),
            com.omnisolve.overlay.ui.theme.findThemePresetById("atelier_kyoto_amber"),
            com.omnisolve.overlay.ui.theme.findThemePresetById("atelier_velvet_indigo")
        )
        val activeSpecState = androidx.compose.runtime.mutableStateOf(archetypeSpecs.first())

        composeTestRule.setContent {
            val activeSpec = activeSpecState.value
            WhiteGlassTheme(themeSpec = activeSpec) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)
                ) {
                    Box(modifier = Modifier.width(280.dp)) {
                        HomeScreen(
                            state = MainUiState(
                                firebaseUserId = "uid_123",
                                preferLocalDefaultTheme = activeSpec.isNormalDefault
                            ),
                            onToggleOverlay = {},
                            onRequestOverlayPermission = {},
                            onSelectSolverMethod = {},
                            onSetCustomOpacityPct = {},
                            onSelectQuestionSolveMode = {}
                        )
                    }
                }
            }
        }

        archetypeSpecs.forEach { spec ->
            composeTestRule.runOnUiThread {
                activeSpecState.value = spec
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()
            composeTestRule.onNodeWithTag("home_layout_${spec.layoutArchetype.id}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("home_screen_list")
                .performScrollToNode(hasTestTag("btn_toggle_overlay"))
            composeTestRule.onNodeWithTag("btn_toggle_overlay").assertIsDisplayed()
            composeTestRule.onNodeWithTag("home_screen_list")
                .performScrollToNode(hasTestTag("card_open_theme_studio"))
            composeTestRule.onNodeWithTag("card_open_theme_studio").assertIsDisplayed()
        }
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w411dp-h915dp-xhdpi")
    fun testLiquidMorphBottomDock_MorphsCleanlyAcrossAllFiveArchetypesAt260dp() {
        val archetypeSpecs = listOf(
            NORMAL_DEFAULT_THEME,
            com.omnisolve.overlay.ui.theme.findThemePresetById("atelier_prism_noir"),
            com.omnisolve.overlay.ui.theme.findThemePresetById("atelier_swiss_glacier"),
            com.omnisolve.overlay.ui.theme.findThemePresetById("atelier_kyoto_amber"),
            com.omnisolve.overlay.ui.theme.findThemePresetById("atelier_velvet_indigo")
        )
        val activeSpecState = androidx.compose.runtime.mutableStateOf(archetypeSpecs.first())

        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = activeSpecState.value) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f)
                ) {
                    Box(modifier = Modifier.width(260.dp)) {
                        LiquidMorphBottomDock(
                            selectedTab = AppTab.Home,
                            onSelectTab = {}
                        )
                    }
                }
            }
        }

        archetypeSpecs.forEach { spec ->
            composeTestRule.runOnUiThread {
                activeSpecState.value = spec
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("bottom_navigation_dock").assertIsDisplayed()
            composeTestRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()
            composeTestRule.onNodeWithTag("nav_tab_stats").assertIsDisplayed()
            composeTestRule.onNodeWithTag("nav_tab_settings").assertIsDisplayed()
        }
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w411dp-h915dp-xhdpi")
    fun testSettingsScreen_QuickThemeSwitchTriggersCompleteLayoutPresetSelection() {
        var selectedSpec: com.omnisolve.overlay.ui.theme.RemoteThemeSpec? = null
        val practice = PracticeSample(
            category = "Physics",
            question = "Unit of force?",
            options = listOf("Joule", "Newton", "Watt", "Pascal"),
            correctLetter = "B",
            explanation = "Force is measured in Newtons."
        )
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                val listState = rememberLazyListState()
                SettingsScreen(
                    state = MainUiState(),
                    sample = practice,
                    listState = listState,
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
                    onSelectLocalThemePreset = { selectedSpec = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_screen_list")
            .performScrollToNode(hasTestTag("card_settings_theme_control"))
        composeTestRule.onNodeWithTag("card_settings_theme_control").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_settings_quick_theme_atelier_prism_noir")
            .assertIsDisplayed()
            .performClick()
        assertEquals("atelier_prism_noir", selectedSpec?.themeId)
        assertEquals(
            com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype.ATELIER_BENTO,
            selectedSpec?.layoutArchetype
        )
    }

    @Test
    fun testQuickPlusTreeOverlay_RendersThreeBranchesWithSizeLimitsAndActions() {
        var photoTapped = false
        var pdfTapped = false
        var linkTapped = false
        var cameraTapped = false
        var floatingPillTapped = false
        var liveRoomTapped = false
        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    com.omnisolve.overlay.ui.physics.LocalReducedMotion provides true
                ) {
                    Box(modifier = Modifier.width(340.dp)) {
                        com.omnisolve.overlay.ui.components.QuickPlusTreeOverlay(
                            visible = true,
                            dailyUsage = com.omnisolve.overlay.data.DailyUsageSnapshot(),
                            onDismiss = {},
                            onPickPhoto = { photoTapped = true },
                            onPickPdf = { pdfTapped = true },
                            onOpenFormLink = { linkTapped = true },
                            onPickCamera = { cameraTapped = true },
                            onToggleFloatingPill = { floatingPillTapped = true },
                            isOverlayRunning = false,
                            onOpenLiveClassRoom = { liveRoomTapped = true }
                        )
                    }
                }
            }
        }
        composeTestRule.onNodeWithTag("btn_plus_tree_link").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("btn_plus_tree_camera").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("btn_plus_tree_photo").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("btn_plus_tree_pdf").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("btn_plus_tree_live_room").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("btn_plus_tree_floating_pill").assertIsDisplayed().performClick()
        assertTrue("Quiz Browser tile must trigger onOpenFormLink", linkTapped)
        assertTrue("Camera tile must trigger onPickCamera", cameraTapped)
        assertTrue("Photo tile must trigger onPickPhoto", photoTapped)
        assertTrue("PDF tile must trigger onPickPdf", pdfTapped)
        assertTrue("Live Class Room capsule must trigger onOpenLiveClassRoom", liveRoomTapped)
        assertTrue("Floating Pill strip must trigger onToggleFloatingPill", floatingPillTapped)
    }

    @Test
    fun testLiveClassRoomScreen_LobbyAndActiveRoomRenderCleanlyOnCompactWithLargeFontScale() {
        val sampleRoom = com.omnisolve.overlay.data.LiveClassRoom(
            roomCode = "K7M29Q",
            roomName = "CSE-B Networks Quiz",
            subject = com.omnisolve.overlay.model.SubjectMode.CODING.id,
            hostUid = "uid_host",
            hostName = "Alice",
            memberCount = 12,
            questionCount = 1,
            active = true,
            expiresAtMs = System.currentTimeMillis() + 12L * 3_600_000L
        )
        val sampleAnswer = com.omnisolve.overlay.data.LiveRoomAnswer(
            id = "q_1",
            roomCode = "K7M29Q",
            questionNumber = 1,
            questionText = "Which layer handles routing across networks?",
            answerBadge = "C",
            shortAnswer = "Network Layer",
            explanation = "IP routing operates at OSI Layer 3.",
            sourceLabel = "Microsoft Forms",
            sharedByUid = "uid_host",
            sharedByName = "Alice",
            confirmCount = 4,
            disputeCount = 1,
            verifiedByAi = false,
            updatedByUid = "uid_host"
        )
        val showActiveRoom = androidx.compose.runtime.mutableStateOf(false)
        var confirmClicked = false
        var disputeClicked = false
        var aiVerifyClicked = false

        val sampleMembers = listOf(
            com.omnisolve.overlay.data.LiveRoomMember(
                uid = "uid_host",
                roomCode = "K7M29Q",
                displayName = "Alice",
                photoUrl = "",
                role = com.omnisolve.overlay.data.LiveRoomMember.ROLE_HOST,
                status = com.omnisolve.overlay.data.LiveRoomMember.STATUS_APPROVED
            ),
            com.omnisolve.overlay.data.LiveRoomMember(
                uid = "uid_pending",
                roomCode = "K7M29Q",
                displayName = "Bob",
                photoUrl = "",
                role = com.omnisolve.overlay.data.LiveRoomMember.ROLE_MEMBER,
                status = com.omnisolve.overlay.data.LiveRoomMember.STATUS_PENDING
            )
        )
        var approvedUid: String? = null

        composeTestRule.setContent {
            WhiteGlassTheme(themeSpec = NORMAL_DEFAULT_THEME) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 2.0f, fontScale = 1.30f),
                    com.omnisolve.overlay.ui.physics.LocalReducedMotion provides true
                ) {
                    val state = MainUiState(
                        firebaseUserId = "uid_host",
                        activeLiveClassRoom = if (showActiveRoom.value) sampleRoom.copy(quizUrl = "https://forms.gle/demo") else null,
                        liveClassRoomMembers = if (showActiveRoom.value) sampleMembers else emptyList(),
                        myLiveRoomMemberStatus = if (showActiveRoom.value) com.omnisolve.overlay.data.LiveRoomMember.STATUS_APPROVED else null,
                        liveClassRoomAnswers = if (showActiveRoom.value) listOf(sampleAnswer) else emptyList()
                    )
                    Box(modifier = Modifier.width(340.dp).height(420.dp)) {
                        com.omnisolve.overlay.ui.screens.LiveClassRoomScreen(
                            state = state,
                            onBackToHome = {},
                            onSignInGoogle = {},
                            onCreateRoom = { _, _, _ -> },
                            onJoinRoom = {},
                            onApproveMember = { approvedUid = it },
                            onRejectOrRemoveMember = { _, _ -> },
                            onUpdateLockedQuizUrl = {},
                            onLeaveRoom = {},
                            onEndRoom = {},
                            onToggleAutoSync = {},
                            onVoteConfirm = { confirmClicked = true },
                            onVoteDispute = { disputeClicked = true },
                            onAiVerifyAnswer = { aiVerifyClicked = true },
                            onShowBannerMessage = {}
                        )
                    }
                }
            }
        }

        // Verify Lobby state renders cleanly
        composeTestRule.onNodeWithTag("input_live_room_code").assertExists()
        composeTestRule.onNodeWithTag("btn_live_room_join").assertExists()
        composeTestRule.onNodeWithTag("live_class_room_list")
            .performScrollToKey("live_room_create_section")
        composeTestRule.onNodeWithTag("btn_live_room_create").assertExists()

        // Switch to Active Discord-style Room state and verify banner + locked exam browser + live answer card
        composeTestRule.runOnIdle { showActiveRoom.value = true }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("live_class_room_list")
            .performScrollToKey("active_room_banner")
        composeTestRule.onNodeWithTag("text_active_room_code").assertExists()
        composeTestRule.onNodeWithTag("btn_live_room_share").assertExists()
        composeTestRule.onNodeWithTag("live_class_room_list")
            .performScrollToKey("live_room_locked_url_card")
        composeTestRule.onNodeWithTag("btn_live_room_open_browser").assertExists()
        composeTestRule.onNodeWithTag("live_class_room_list")
            .performScrollToKey("q_1")
        composeTestRule.onNodeWithTag("card_live_room_answer_1").assertExists()
        composeTestRule.onNodeWithTag("btn_room_confirm_1").performClick()
        composeTestRule.onNodeWithTag("btn_room_dispute_1").performClick()
        composeTestRule.onNodeWithTag("btn_room_ai_verify_1").performClick()

        assertTrue("Confirm button must fire callback", confirmClicked)
        assertTrue("Dispute button must fire callback", disputeClicked)
        assertTrue("AI Verify button must fire callback", aiVerifyClicked)

        // Switch to # members channel and verify pending host approval + non-clickable member row
        composeTestRule.onNodeWithTag("live_class_room_list")
            .performScrollToKey("active_room_banner")
        composeTestRule.onNodeWithTag("tab_room_channel_members").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("live_class_room_list")
            .performScrollToKey("pending_uid_pending")
        composeTestRule.onNodeWithTag("btn_approve_member_uid_pending").performClick()
        assertEquals("uid_pending", approvedUid)
        composeTestRule.onNodeWithTag("live_class_room_list")
            .performScrollToKey("member_uid_host")
        composeTestRule.onNodeWithTag("member_row_uid_host").assertExists()
    }
}


