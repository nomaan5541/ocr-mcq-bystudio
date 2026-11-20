package com.omnisolve.overlay.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.Settings
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.auth
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.api.CircleToSearchEngine
import com.omnisolve.overlay.api.FormSolveException
import com.omnisolve.overlay.api.GeminiVisionClient
import com.omnisolve.overlay.api.GoogleFormExtractResult
import com.omnisolve.overlay.api.GoogleFormSolveResult
import com.omnisolve.overlay.api.GoogleFormsModelOption
import com.omnisolve.overlay.api.GoogleFormsSessionStore
import com.omnisolve.overlay.api.GoogleFormsSolverEngine
import com.omnisolve.overlay.api.MultiProviderApiSolver
import com.omnisolve.overlay.api.OnlineConsensusRaceEngine
import com.omnisolve.overlay.api.PrismAiSolverEngine
import com.omnisolve.overlay.capture.OcrEngine
import com.omnisolve.overlay.data.FirebaseTelemetryRepository
import com.omnisolve.overlay.data.GoogleAuthManager
import com.omnisolve.overlay.data.LocalThemeRepository
import com.omnisolve.overlay.data.SettingsDataStoreRepository
import com.omnisolve.overlay.data.SolvedQuestionRecord
import com.omnisolve.overlay.data.SolvedQuestionRepository
import com.omnisolve.overlay.data.TelemetryBackend
import com.omnisolve.overlay.data.TelemetryFeature
import com.omnisolve.overlay.data.TelemetrySessionSource
import com.omnisolve.overlay.data.TelemetryStatus
import com.omnisolve.overlay.data.UserActivityProfile
import com.omnisolve.overlay.data.UserRequestLog
import com.omnisolve.overlay.data.local.OfflineStudyHistoryRepository
import com.omnisolve.overlay.util.PrismCrashlytics
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import com.omnisolve.overlay.f1.F1MasterEngine
import com.omnisolve.overlay.f1.F1PerformanceMonitor
import com.omnisolve.overlay.f1.F1ProviderHealthManager
import com.omnisolve.overlay.f1.F1ProviderHealthSnapshot
import com.omnisolve.overlay.f1.F1RaceMode
import com.omnisolve.overlay.f1.F1RaceReport
import com.omnisolve.overlay.f1.F1SolveRequest
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.EnginePerformanceMode
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.service.CustomPinnedNotifConfig
import com.omnisolve.overlay.service.GoogleFormsNotificationHelper
import com.omnisolve.overlay.service.OverlayService
import com.omnisolve.overlay.service.PrismEngagementNotificationManager
import com.omnisolve.overlay.ui.components.AppTab
import com.omnisolve.overlay.ui.theme.NORMAL_DEFAULT_THEME
import com.omnisolve.overlay.ui.theme.RemoteThemeSpec
import com.omnisolve.overlay.ui.theme.ThemeAnimationIntensity
import com.omnisolve.overlay.ui.theme.ThemeColorAppearance
import com.omnisolve.overlay.ui.theme.findThemePresetById
import com.omnisolve.overlay.util.SecureKeyStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PracticeSample(
    val category: String,
    val question: String,
    val options: List<String>,
    val correctLetter: String,
    val explanation: String,
    val subjectMode: SubjectMode = SubjectMode.GENERAL
)

data class SolvedHistoryEntry(
    val id: Long,
    val questionPreview: String,
    val answerLetter: String,
    val sourceLabel: String,
    val timestamp: String,
    val explanation: String = "",
    val subject: String = "GENERAL",
    val bookmarked: Boolean = false,
    val isCachedResult: Boolean = false,
    val solveLatencyMs: Long = 0L,
    val modelUsed: String = ""
)

enum class HomeSubScreen(val title: String, val subtitle: String) {
    MAIN("Prism Solver", "Instant MCQ, Math & Forms screen helper"),
    ANSWER_MODELS("Solving Speed & Style", "Smart Auto, Double-Check Accuracy, or Math Vision"),
    DYNAMIC_ISLAND_STUDIO("Floating Pill Look", "Pick pill size, look & transparency"),
    GOOGLE_FORMS_SOLVE("Google Forms Solve", "Paste a quiz link to get answers & auto-fill"),
    NOTIFICATION_STUDIO("Notification Studio", "Pin answer strips & quick alerts"),
    THEME_STUDIO("Themes", "Switch themes & complete layouts instantly"),
    EXPORT_QA("History & Offline Cache", "Browse saved answers offline or export study sheets"),
    PRIVACY_POLICY("Privacy Policy & Security", "Only reads screen when you tap the pill")
}

data class MainUiState(
    val selectedTab: AppTab = AppTab.Home,
    val homeSubScreen: HomeSubScreen = HomeSubScreen.MAIN,
    val isOverlayRunning: Boolean = false,
    val userConsentAccepted: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isCaptureAuthorized: Boolean = false,
    val isGoogleWebLoggedIn: Boolean = false,
    val hasSecretApiKey: Boolean = true,

    // Google Cloud Account & Live Firebase Telemetry Dashboard State
    val firebaseUserId: String? = null,
    val firebaseUserName: String? = null,
    val firebaseUserEmail: String? = null,
    val isSigningInGoogle: Boolean = false,
    val signInPromptDismissedForOcrOnly: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val cloudActivityProfile: UserActivityProfile? = null,
    val recentCloudRequestLogs: List<UserRequestLog> = emptyList(),
    val cloudTelemetryError: String? = null,
    val latestF1RaceReport: F1RaceReport? = null,
    val f1HealthSnapshots: List<F1ProviderHealthSnapshot> = emptyList(),

    // Preferences (Screenshot + ML Kit OCR default)
    val extractionEngine: String = MainActivity.ENGINE_OCR,
    val nativeFallbackOcr: Boolean = true,
    val solverMethod: SolverMethod = SolverMethod.BACKGROUND_GEMINI,
    val autoFailoverEnabled: Boolean = true,
    val nvidiaApiKey: String = "",
    val openRouterApiKey: String = "",
    val geminiApiKey: String = "",
    val openAiApiKey: String = "",
    val configuredKeyCount: Int = 0,
    val isTestingApiKeys: Boolean = false,
    val apiKeysTestSummary: String? = null,
    val activePreset: String = "STUDY",
    val hudStyle: String = MainActivity.HUD_STYLE_ISLAND,
    val islandOpacityIndex: Int = 0,
    val customOpacityPct: Int = 100,
    val questionSolveMode: QuestionSolveMode = QuestionSolveMode.STANDARD,
    val enginePerformanceMode: EnginePerformanceMode = EnginePerformanceMode.NORMAL,
    val displayMode: String = MainActivity.MODE_STEALTH,
    val naturalTimingEnabled: Boolean = true,
    val scanIntervalSec: Int = 3,
    val nextDelayMs: Int = 1500,
    val instantTriggerEnabled: Boolean = true,
    val autoCopyClipboard: Boolean = false,
    val hapticEnabled: Boolean = true,
    val batteryCamouflage: Boolean = false,
    val companionSyncEnabled: Boolean = false,
    val companionHost: String = "192.168.1.100",
    val companionPort: Int = 59124,
    val customApiKey: String = "",
    val geminiModel: String = PrismAiSolverEngine.DEFAULT_CLOUD_MODEL,
    val subjectMode: SubjectMode = SubjectMode.GENERAL,
    val dualVerifyEnabled: Boolean = false,
    val translateToEnglish: Boolean = false,

    // Advanced Options (ALL OFF by default — enabled explicitly in Advanced Settings)
    val advCropToScanEnabled: Boolean = false,
    val advIslandGesturesEnabled: Boolean = false,
    val advSplitPillEnabled: Boolean = false,
    val advMinimalShapesEnabled: Boolean = false,
    val advNetSpeedDisguiseEnabled: Boolean = false,
    val advAutoFadeIdleEnabled: Boolean = false,
    val advFlashcardsExportEnabled: Boolean = false,
    val advDarkObsidianEnabled: Boolean = false,
    val fluidAnimationsEnabled: Boolean = true,
    val advSpringPhysicsEnabled: Boolean = false,
    val springDamping: Float = 0.72f,
    val springStiffness: Float = 650f,
    val advSplitScreenRailEnabled: Boolean = false,
    val advQuickTileNotifEnabled: Boolean = false,

    // Dynamic Flow Bar Options (Controllable ON/OFF in Settings -> Dynamic Flow Options)
    val dfTwoPartCaptureEnabled: Boolean = true,
    val dfScrollTimerEnabled: Boolean = true,
    val dfComplexModeEnabled: Boolean = true,
    val dfDiagramVisionEnabled: Boolean = false,
    val dfQuickCropEnabled: Boolean = false,
    val dfSwipeMethodLoopEnabled: Boolean = true,

    // Dynamic Island Studio Extended Customization
    val islandAccentTheme: String = com.omnisolve.overlay.ui.DynamicIslandBorderDrawable.ACCENT_CRIMSON,
    val islandSizePreset: String = MainActivity.ISLAND_SIZE_STANDARD,
    val islandScalePct: Int = 112,
    val islandAnswerScale: String = MainActivity.ANSWER_SCALE_LARGE,
    val islandEdgeSnap: Boolean = true,
    val islandIdleFadeSec: Int = 4,
    val islandWidthDp: Int = 0, // 0 = Auto wrap capsule width
    val islandHeightDp: Int = 56,
    val islandCornerRadiusDp: Int = 32,
    val islandAnimSpeed: Float = 1.0f,
    val islandCutoutAlign: Boolean = false,
    val islandCutoutOffsetXDp: Int = 0,
    val islandCutoutOffsetYDp: Int = 0,

    // Flashcard Deck Search & Filters
    val flashcardSearchQuery: String = "",
    val flashcardSubjectFilter: String = "ALL",
    val flashcardBookmarkedOnly: Boolean = false,

    // Google Forms URL Extractor & Pinned Notification State
    val googleFormUrlInput: String = "",
    val googleFormPastedTextInput: String = "",
    val googleFormsModel: String = GoogleFormsSolverEngine.DEFAULT_FORMS_MODEL,
    val googleFormsNeedsWebLogin: Boolean = false,
    val googleFormAppendMode: Boolean = false,
    val isSolvingGoogleForm: Boolean = false,
    val reSolvingFormQuestionNumber: Int? = null,
    val googleFormError: String? = null,
    val googleFormResult: GoogleFormSolveResult? = null,
    val isGoogleFormPinned: Boolean = false,
    val isFormsKeyPinnedOnIsland: Boolean = false,

    // Onboarding Feature Guide & Custom Notification Studio
    val onboardingGuideCompleted: Boolean = false,
    val showOnboardingGuide: Boolean = false,
    val customPinConfig: CustomPinnedNotifConfig = CustomPinnedNotifConfig(),
    val autoWeeklyEngagementEnabled: Boolean = true,
    val hasNotificationPermission: Boolean = true,

    // Dynamic Offline Theme Engine (50 /unslop themes + Normal Default)
    val effectiveThemeSpec: RemoteThemeSpec = NORMAL_DEFAULT_THEME,
    val preferLocalDefaultTheme: Boolean = false,
    val selectedThemePresetId: String = RemoteThemeSpec.DEFAULT_THEME_ID,
    val themeAnimationOverride: ThemeAnimationIntensity = ThemeAnimationIntensity.AUTO,
    val themeColorAppearanceOverride: ThemeColorAppearance = ThemeColorAppearance.AUTO,

    // Practice, Offline Room Cache & Metrics
    val solvedCount: Int = 12,
    val offlineCacheCount: Int = 0,
    val offlineCacheSavedCalls: Int = 0,
    val currentSampleIndex: Int = 0,
    val customPracticeInput: String = "",
    val isPracticeScanning: Boolean = false,
    val practiceSolvedLetter: String? = null,
    val practiceExplanation: String? = null,
    val practiceStatusNote: String? = null,
    val historyEntries: List<SolvedHistoryEntry> = defaultHistoryEntries(),
    val statusBannerMessage: String? = null
)

private fun defaultHistoryEntries(bookmarkedIds: Set<Long> = emptySet()): List<SolvedHistoryEntry> = listOf(
    SolvedHistoryEntry(
        id = 1L,
        questionPreview = "Which cell structure produces most ATP energy?",
        answerLetter = "B",
        sourceLabel = "Biology • Cloud Gemini",
        timestamp = "Just now",
        explanation = "Mitochondria generate ATP via oxidative phosphorylation.",
        subject = SubjectMode.MEDICAL.id,
        bookmarked = 1L in bookmarkedIds
    ),
    SolvedHistoryEntry(
        id = 2L,
        questionPreview = "Search time in a balanced binary search tree?",
        answerLetter = "B",
        sourceLabel = "CS • Cloud Gemini",
        timestamp = "Earlier",
        explanation = "O(log n) because each step halves the remaining search space.",
        subject = SubjectMode.CODING.id,
        bookmarked = 2L in bookmarkedIds
    )
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
    private val geminiClient = GeminiVisionClient()
    private val themeRepository = LocalThemeRepository(prefs)
    private val settingsDataStore = SettingsDataStoreRepository(application)
    private val offlineHistoryRepo = OfflineStudyHistoryRepository(application)
    private val localSettingsEpoch = java.util.concurrent.atomic.AtomicLong(0L)
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var googleFormSolveJob: Job? = null
    private var telemetryProfileJob: Job? = null
    private var telemetryLogsJob: Job? = null
    private var appSessionRequestsCount: Long = 0L
    private val sharedOcrEngine: com.omnisolve.overlay.capture.OcrEngine by lazy {
        com.omnisolve.overlay.capture.OcrEngine()
    }

    private fun launchDataStoreWrite(block: suspend () -> Unit) {
        localSettingsEpoch.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (_: Exception) {
            }
        }
    }

    val samples = listOf(
        PracticeSample(
            category = "Biology • Sample 1 of 4",
            question = "Which part of a cell produces most of the chemical energy (ATP) needed for life?",
            options = listOf(
                "A) Ribosome",
                "B) Mitochondrion",
                "C) Golgi Apparatus",
                "D) Lysosome"
            ),
            correctLetter = "B",
            explanation = "Mitochondria are the powerhouses of the cell, converting nutrients into ATP through cellular respiration.",
            subjectMode = SubjectMode.MEDICAL
        ),
        PracticeSample(
            category = "Computer Science • Sample 2 of 4",
            question = "How fast is searching for an item inside a balanced Binary Search Tree with n items?",
            options = listOf(
                "A) O(1) constant time",
                "B) O(log n) logarithmic time",
                "C) O(n) linear time",
                "D) O(n²) quadratic time"
            ),
            correctLetter = "B",
            explanation = "Each comparison halves the remaining search space in a balanced tree, giving O(log n) worst-case lookup.",
            subjectMode = SubjectMode.CODING
        ),
        PracticeSample(
            category = "Chemistry • Sample 3 of 4",
            question = "What is the shape and approximate bond angle of a water (H₂O) molecule?",
            options = listOf(
                "A) Linear, 180°",
                "B) Trigonal planar, 120°",
                "C) Bent, ~104.5°",
                "D) Square planar, 90°"
            ),
            correctLetter = "C",
            explanation = "Oxygen's two lone electron pairs push the two hydrogen atoms closer together into a bent ~104.5° shape.",
            subjectMode = SubjectMode.SCIENCE
        ),
        PracticeSample(
            category = "Physics • Sample 4 of 4",
            question = "Which law states that every action has an equal and opposite reaction?",
            options = listOf(
                "A) Newton's First Law",
                "B) Ohm's Law",
                "C) Kepler's Second Law",
                "D) Newton's Third Law"
            ),
            correctLetter = "D",
            explanation = "Newton's Third Law of Motion states that forces between two interacting objects act in equal and opposite pairs.",
            subjectMode = SubjectMode.SCIENCE
        )
    )

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        F1ProviderHealthManager.initialize(getApplication())
        attachFirebaseAuthObserver()
        observeF1Diagnostics()
        initializePersistentRepositories()
        persistWarmCacheSnapshotAsync()
        _uiState.value.firebaseUserId?.takeIf { it.isNotBlank() }?.let { uid ->
            restoreSolvedHistoryFromCloudIfNeeded(uid)
        }
    }

    private fun observeF1Diagnostics() {
        _uiState.update { it.copy(f1HealthSnapshots = F1ProviderHealthManager.getAllSnapshots()) }
        viewModelScope.launch {
            F1PerformanceMonitor.latestRace.collect { report ->
                _uiState.update { cur ->
                    cur.copy(
                        latestF1RaceReport = report,
                        f1HealthSnapshots = F1ProviderHealthManager.getAllSnapshots()
                    )
                }
            }
        }
    }

    private fun initializePersistentRepositories() {
        val startEpoch = localSettingsEpoch.get()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (localSettingsEpoch.get() == startEpoch) {
                    settingsDataStore.migrateFromLegacySharedPreferencesIfNeeded(prefs)
                }
            } catch (e: Exception) {
                PrismCrashlytics.recordNonFatalSolverFailure(
                    context = getApplication(),
                    solverMethod = _uiState.value.solverMethod.id,
                    failedStage = "DATASTORE_MIGRATION",
                    throwable = e
                )
            }
            try {
                offlineHistoryRepo.migrateFromLegacyPrefsIfNeeded(prefs)
            } catch (e: Exception) {
                PrismCrashlytics.recordNonFatalSolverFailure(
                    context = getApplication(),
                    solverMethod = _uiState.value.solverMethod.id,
                    failedStage = "ROOM_HISTORY_MIGRATION",
                    throwable = e
                )
            }

            try {
                val ds = settingsDataStore.getSettings()
                if (localSettingsEpoch.get() == startEpoch) {
                    _uiState.update { cur ->
                        val effectiveMethod = SolverMethod.fromId(ds.selectedSolverMethodId)
                        val nextEffectiveSpec = computeEffectiveSpec(
                            preferLocalDefault = ds.preferLocalDefaultTheme,
                            selectedPresetId = ds.selectedThemePresetId,
                            animationOverride = ds.themeAnimationOverride,
                            appearanceOverride = ds.themeColorAppearanceOverride
                        )
                        cur.copy(
                            solverMethod = effectiveMethod,
                            preferLocalDefaultTheme = ds.preferLocalDefaultTheme,
                            selectedThemePresetId = ds.selectedThemePresetId,
                            themeAnimationOverride = ds.themeAnimationOverride,
                            themeColorAppearanceOverride = ds.themeColorAppearanceOverride,
                            effectiveThemeSpec = nextEffectiveSpec,
                            advDarkObsidianEnabled = ds.advDarkObsidian,
                            islandAccentTheme = ds.islandAccentTheme,
                            islandSizePreset = ds.islandSizePreset,
                            islandScalePct = ds.islandScalePct,
                            islandAnswerScale = ds.islandAnswerScale,
                            islandWidthDp = ds.islandWidthDp,
                            islandHeightDp = ds.islandHeightDp,
                            islandCornerRadiusDp = ds.islandCornerRadiusDp,
                            islandAnimSpeed = ds.islandAnimSpeed,
                            islandCutoutAlign = ds.islandCutoutAlign,
                            islandCutoutOffsetXDp = ds.islandCutoutOffsetXDp,
                            islandCutoutOffsetYDp = ds.islandCutoutOffsetYDp,
                            islandEdgeSnap = ds.islandEdgeSnap,
                            islandIdleFadeSec = ds.islandIdleFadeSec,
                            customOpacityPct = ds.customOpacityPct
                        )
                    }
                }
            } catch (_: Exception) {
            }

            launch {
                offlineHistoryRepo.observeHistoryEntries().collect { roomRows ->
                    val bookmarked = loadBookmarkedIds()
                    val clearedByUser = prefs.getBoolean("PREF_HISTORY_CLEARED_V1", false)
                    val nextEntries = when {
                        roomRows.isNotEmpty() -> roomRows
                        clearedByUser -> emptyList()
                        else -> defaultHistoryEntries(bookmarked)
                    }
                    _uiState.update { cur ->
                        cur.copy(historyEntries = nextEntries)
                    }
                }
            }

            launch {
                try {
                    val cacheDao = com.omnisolve.overlay.data.local.PrismStudyDatabase
                        .getInstance(getApplication<Application>())
                        .sharedAnswerCacheDao()
                    launch {
                        cacheDao.observeCacheCount().collect { count ->
                            _uiState.update { cur -> cur.copy(offlineCacheCount = count) }
                        }
                    }
                    launch {
                        cacheDao.observeTotalSavedCalls().collect { savedCalls ->
                            _uiState.update { cur -> cur.copy(offlineCacheSavedCalls = savedCalls) }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun attachFirebaseAuthObserver() {
        val ctx = getApplication<Application>()
        if (FirebaseApp.getApps(ctx).isEmpty()) return
        try {
            val auth = Firebase.auth
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    PrismAiSolverEngine.resetCloudCooldown()
                    restoreSolvedHistoryFromCloudIfNeeded(user.uid)
                    viewModelScope.launch(Dispatchers.IO) {
                        FirebaseTelemetryRepository.ensureUserProfileSynced(ctx, user)
                    }
                    startCloudTelemetryObservers(user.uid)
                } else {
                    stopCloudTelemetryObservers()
                    F1MasterEngine.onAccountSignedOut()
                    prefs.edit()
                        .putString(MainActivity.PREF_KEY_SOLVER_METHOD, SolverMethod.BACKGROUND_GEMINI.id)
                        .putString(MainActivity.PREF_KEY_DISPLAY_MODE, MainActivity.MODE_STEALTH)
                        .apply()
                }
                _uiState.update { cur ->
                    val signedIn = user != null
                    cur.copy(
                        firebaseUserId = user?.uid,
                        firebaseUserName = user?.displayName,
                        firebaseUserEmail = user?.email,
                        signInPromptDismissedForOcrOnly = if (signedIn) false else cur.signInPromptDismissedForOcrOnly,
                        solverMethod = if (signedIn) cur.solverMethod else SolverMethod.BACKGROUND_GEMINI,
                        displayMode = if (signedIn) cur.displayMode else MainActivity.MODE_STEALTH,
                        cloudActivityProfile = if (signedIn) cur.cloudActivityProfile else null,
                        recentCloudRequestLogs = if (signedIn) cur.recentCloudRequestLogs else emptyList(),
                        cloudTelemetryError = if (signedIn) cur.cloudTelemetryError else null
                    )
                }
                persistWarmCacheSnapshotAsync()
            }
            authStateListener = listener
            auth.addAuthStateListener(listener)
        } catch (_: Exception) {
        }
    }

    private fun startCloudTelemetryObservers(uid: String) {
        stopCloudTelemetryObservers()
        if (uid.isBlank()) return
        val ctx = getApplication<Application>()
        if (FirebaseApp.getApps(ctx).isEmpty()) return

        telemetryProfileJob = viewModelScope.launch {
            FirebaseTelemetryRepository.observeUserProfile(ctx, uid)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(cloudTelemetryError = FirebaseTelemetryRepository.handleFirestoreError(e, "profile"))
                    }
                }
                .collect { profile ->
                    _uiState.update {
                        it.copy(
                            cloudActivityProfile = profile,
                            cloudTelemetryError = null
                        )
                    }
                }
        }

        telemetryLogsJob = viewModelScope.launch {
            FirebaseTelemetryRepository.observeRecentRequestLogs(ctx, uid, limit = 15L)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(cloudTelemetryError = FirebaseTelemetryRepository.handleFirestoreError(e, "requestLogs"))
                    }
                }
                .collect { logs ->
                    _uiState.update {
                        it.copy(
                            recentCloudRequestLogs = logs,
                            cloudTelemetryError = null
                        )
                    }
                }
        }
    }

    private fun stopCloudTelemetryObservers() {
        telemetryProfileJob?.cancel()
        telemetryProfileJob = null
        telemetryLogsJob?.cancel()
        telemetryLogsJob = null
    }

    fun recordAppForegroundSession(durationSeconds: Long) {
        if (durationSeconds < 2L) return
        val ctx = getApplication<Application>()
        val reqs = appSessionRequestsCount
        appSessionRequestsCount = 0L
        viewModelScope.launch(Dispatchers.IO) {
            FirebaseTelemetryRepository.recordActiveSession(
                context = ctx,
                source = TelemetrySessionSource.APP_FOREGROUND,
                durationSeconds = durationSeconds,
                requestsCount = reqs
            )
        }
    }

    /**
     * Maintains warm startup cache files inside Android app storage (`files/warm_cache/` + `cache/`),
     * backed up by Android Auto Backup & Device Transfer so preferences, theme, and stats recover cleanly
     * across app reinstalls.
     */
    private fun persistWarmCacheSnapshotAsync() {
        val snapshot = _uiState.value
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("version", 1)
                    put("savedAtMs", System.currentTimeMillis())
                    put("solverMethod", snapshot.solverMethod.id)
                    put("geminiModel", snapshot.geminiModel)
                    put("subjectMode", snapshot.subjectMode.id)
                    put("hudStyle", snapshot.hudStyle)
                    put("customOpacityPct", snapshot.customOpacityPct)
                    put("islandSizePreset", snapshot.islandSizePreset)
                    put("islandScalePct", snapshot.islandScalePct)
                    put("preferLocalDefaultTheme", snapshot.preferLocalDefaultTheme)
                    put("selectedThemePresetId", snapshot.selectedThemePresetId)
                    put("solvedCount", snapshot.solvedCount)
                    put("firebaseUserId", snapshot.firebaseUserId.orEmpty())
                }.toString()

                val warmBackupDir = File(ctx.filesDir, "warm_cache").apply { mkdirs() }
                File(warmBackupDir, "app_state_snapshot.json").writeText(payload)
                File(ctx.cacheDir, "warm_startup_cache.json").writeText(payload)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Restores solved question history from Cloud Firestore into local preferences on startup or after
     * reinstalling and signing back in with Google.
     */
    private fun restoreSolvedHistoryFromCloudIfNeeded(uid: String) {
        if (uid.isBlank()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (FirebaseApp.getApps(ctx).isEmpty()) return@launch
                val cloudRecords = SolvedQuestionRepository(ctx)
                    .getSolvedQuestions(uid, 25L)
                    .getOrNull()
                    .orEmpty()
                if (cloudRecords.isEmpty()) return@launch

                val existingRaw = prefs.getString("PREF_SOLVE_HISTORY", "").orEmpty()
                val existingLines = existingRaw.lines().filter { it.isNotBlank() }
                val existingPreviews = existingLines.mapNotNull { line ->
                    line.split("||").getOrNull(2)?.trim()?.lowercase()
                }.toSet()

                val mergedNewLines = cloudRecords.mapNotNull { record ->
                    val snippet = record.questionText
                        .replace("\n", " ")
                        .replace("||", " ")
                        .trim()
                        .take(60)
                    if (snippet.isBlank() || snippet.lowercase() in existingPreviews) return@mapNotNull null
                    val ts = record.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    val cleanExp = record.explanation
                        .replace("\n", " ")
                        .replace("||", " ")
                        .trim()
                        .take(120)
                    val subj = record.subject.ifBlank { SubjectMode.GENERAL.id }
                    "$ts||${record.answerBadge.ifBlank { "?" }}||$snippet||$cleanExp||$subj"
                }

                if (mergedNewLines.isNotEmpty() || prefs.getInt("PREF_SOLVED_COUNT", 0) < cloudRecords.size) {
                    val combinedLines = (existingLines + mergedNewLines).take(25)
                    val restoredCount = maxOf(prefs.getInt("PREF_SOLVED_COUNT", 0), combinedLines.size)
                    prefs.edit()
                        .putString("PREF_SOLVE_HISTORY", combinedLines.joinToString("\n"))
                        .putInt("PREF_SOLVED_COUNT", restoredCount)
                        .apply()

                    val refreshedHistory = loadPersistedOverlayHistory()
                    _uiState.update { cur ->
                        cur.copy(
                            solvedCount = maxOf(cur.solvedCount, restoredCount),
                            historyEntries = refreshedHistory
                        )
                    }
                    persistWarmCacheSnapshotAsync()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun isSystemInDarkMode(): Boolean {
        val mask = getApplication<Application>().resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mask != android.content.res.Configuration.UI_MODE_NIGHT_NO
    }

    private fun computeEffectiveSpec(
        preferLocalDefault: Boolean,
        selectedPresetId: String,
        animationOverride: ThemeAnimationIntensity,
        appearanceOverride: ThemeColorAppearance
    ): RemoteThemeSpec {
        val base = if (preferLocalDefault) {
            NORMAL_DEFAULT_THEME
        } else {
            findThemePresetById(selectedPresetId)
        }
        return base.resolveWithOverrides(
            systemIsDark = isSystemInDarkMode(),
            animationOverride = animationOverride,
            appearanceOverride = appearanceOverride
        )
    }

    private fun persistIslandCustomHexForTheme(spec: RemoteThemeSpec) {
        val editor = prefs.edit()
        if (spec.isNormalDefault) {
            editor.remove(MainActivity.PREF_KEY_ISLAND_CUSTOM_HEX)
        } else {
            editor.putString(MainActivity.PREF_KEY_ISLAND_CUSTOM_HEX, spec.islandAccentHex)
        }
        editor.putBoolean(MainActivity.PREF_KEY_ISLAND_LIGHT_MODE, !spec.isDark)
        editor.putString(MainActivity.PREF_KEY_ISLAND_SURFACE_HEX, spec.surfacePrimaryHex)
        editor.apply()

        // Immediately sync active app theme (including White iOS Liquid Glass) to live floating Dynamic Island
        try {
            val ctx = getApplication<Application>()
            if (OverlayService.isServiceRunning()) {
                val updateIntent = android.content.Intent(ctx, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_UPDATE_CONFIG
                    putExtra(
                        OverlayService.EXTRA_ISLAND_CUSTOM_HEX,
                        if (spec.isNormalDefault) "" else spec.islandAccentHex
                    )
                    putExtra(OverlayService.EXTRA_ISLAND_LIGHT_MODE, !spec.isDark)
                    putExtra(OverlayService.EXTRA_ISLAND_SURFACE_HEX, spec.surfacePrimaryHex)
                }
                ctx.startService(updateIntent)
            }
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCloudTelemetryObservers()
        try {
            sharedOcrEngine.close()
        } catch (_: Exception) {
        }
        val ctx = getApplication<Application>()
        if (FirebaseApp.getApps(ctx).isNotEmpty()) {
            try {
                authStateListener?.let { Firebase.auth.removeAuthStateListener(it) }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadBookmarkedIds(): Set<Long> {
        val raw = prefs.getString("PREF_BOOKMARKED_IDS", "")?.trim().orEmpty()
        if (raw.isEmpty()) return emptySet()
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
    }

    private fun loadPersistedOverlayHistory(): List<SolvedHistoryEntry> {
        val bookmarked = loadBookmarkedIds()
        val raw = prefs.getString("PREF_SOLVE_HISTORY", "")?.trim().orEmpty()
        if (raw.isBlank()) return defaultHistoryEntries(bookmarked)
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val parsed = raw.lines().mapNotNull { line ->
            val parts = line.split("||")
            if (parts.size >= 3) {
                val ts = parts[0].toLongOrNull() ?: System.currentTimeMillis()
                val badge = parts[1].trim().ifBlank { "?" }
                val snippet = parts[2].trim().ifBlank { "Screen Question" }
                val exp = parts.getOrNull(3)?.trim().orEmpty()
                val subj = parts.getOrNull(4)?.trim()?.ifBlank { SubjectMode.GENERAL.id } ?: SubjectMode.GENERAL.id
                SolvedHistoryEntry(
                    id = ts,
                    questionPreview = snippet,
                    answerLetter = badge,
                    sourceLabel = "Prism Island",
                    timestamp = fmt.format(Date(ts)),
                    explanation = exp,
                    subject = subj,
                    bookmarked = ts in bookmarked
                )
            } else {
                null
            }
        }
        return parsed.ifEmpty { defaultHistoryEntries(bookmarked) }
    }

    private fun loadInitialState(): MainUiState {
        val advDarkObsidian = prefs.getBoolean(MainActivity.PREF_KEY_ADV_DARK_OBSIDIAN, false)
        prefs.edit().putString(
            MainActivity.PREF_KEY_THEME_MODE,
            if (advDarkObsidian) MainActivity.THEME_DARK else MainActivity.THEME_LIGHT
        ).apply()

        val rawSavedModel = prefs.getString(MainActivity.PREF_KEY_GEMINI_MODEL, PrismAiSolverEngine.DEFAULT_CLOUD_MODEL)
            ?: PrismAiSolverEngine.DEFAULT_CLOUD_MODEL
        val normalizedModel = GeminiVisionClient.sanitizeModel(rawSavedModel)
        if (normalizedModel != rawSavedModel) {
            prefs.edit().putString(MainActivity.PREF_KEY_GEMINI_MODEL, normalizedModel).apply()
        }

        val rawSavedFormsModel = prefs.getString(
            MainActivity.PREF_KEY_GOOGLE_FORMS_MODEL,
            GoogleFormsSolverEngine.DEFAULT_FORMS_MODEL
        ) ?: GoogleFormsSolverEngine.DEFAULT_FORMS_MODEL
        val normalizedFormsModel = GoogleFormsSolverEngine.normalizeFormsModel(rawSavedFormsModel)
        if (normalizedFormsModel != rawSavedFormsModel) {
            prefs.edit().putString(MainActivity.PREF_KEY_GOOGLE_FORMS_MODEL, normalizedFormsModel).apply()
        }

        val savedSubject = SubjectMode.fromId(
            prefs.getString(MainActivity.PREF_KEY_SUBJECT_MODE, SubjectMode.GENERAL.id)
        )
        val dualVerify = prefs.getBoolean(MainActivity.PREF_KEY_DUAL_VERIFY, false)
        val translate = prefs.getBoolean(MainActivity.PREF_KEY_TRANSLATE_ENGLISH, false)

        val migratedOcrDefault = prefs.getBoolean("PREF_OCR_DEFAULT_MIGRATED_V2", false)
        if (!migratedOcrDefault) {
            prefs.edit()
                .putString(MainActivity.PREF_KEY_SOLVER_METHOD, SolverMethod.BACKGROUND_GEMINI.id)
                .putBoolean("PREF_OCR_DEFAULT_MIGRATED_V2", true)
                .apply()
        }
        val savedMethod = SolverMethod.fromId(
            prefs.getString(MainActivity.PREF_KEY_SOLVER_METHOD, SolverMethod.BACKGROUND_GEMINI.id)
        )
        val savedEngineMode = EnginePerformanceMode.fromId(
            prefs.getString(MainActivity.PREF_KEY_ENGINE_PERFORMANCE_MODE, EnginePerformanceMode.NORMAL.id)
        )
        val autoFailover = prefs.getBoolean(MainActivity.PREF_KEY_AUTO_FAILOVER, true)
        val nvidiaKey = SecureKeyStorage.getKey(getApplication(), MainActivity.PREF_KEY_NVIDIA_API_KEY)
        val openRouterKey = SecureKeyStorage.getKey(getApplication(), MainActivity.PREF_KEY_OPENROUTER_API_KEY)
        val geminiKey = SecureKeyStorage.getKey(getApplication(), MainActivity.PREF_KEY_GEMINI_API_KEY)
        val openAiKey = SecureKeyStorage.getKey(getApplication(), MainActivity.PREF_KEY_OPENAI_API_KEY)
        val configuredKeys = MultiProviderApiSolver.getConfiguredProviders(getApplication()).size

        val currentUser = try {
            if (FirebaseApp.getApps(getApplication()).isNotEmpty()) Firebase.auth.currentUser else null
        } catch (_: Exception) {
            null
        }
        val effectiveMethod = savedMethod
        val savedDisplayMode = prefs.getString(MainActivity.PREF_KEY_DISPLAY_MODE, MainActivity.MODE_STEALTH)
            ?: MainActivity.MODE_STEALTH

        return MainUiState(
            isOverlayRunning = OverlayService.isServiceRunning(),
            userConsentAccepted = prefs.getBoolean(MainActivity.PREF_KEY_USER_CONSENT_ACCEPTED, false),
            isCaptureAuthorized = OverlayService.hasScreenCapture(),
            firebaseUserId = currentUser?.uid,
            firebaseUserName = currentUser?.displayName,
            firebaseUserEmail = currentUser?.email,
            extractionEngine = prefs.getString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, MainActivity.ENGINE_OCR)
                ?: MainActivity.ENGINE_OCR,
            nativeFallbackOcr = prefs.getBoolean(MainActivity.PREF_KEY_NATIVE_FALLBACK_OCR, true),
            solverMethod = effectiveMethod,
            autoFailoverEnabled = autoFailover,
            nvidiaApiKey = nvidiaKey,
            openRouterApiKey = openRouterKey,
            geminiApiKey = geminiKey,
            openAiApiKey = openAiKey,
            configuredKeyCount = configuredKeys,
            hudStyle = prefs.getString(MainActivity.PREF_KEY_HUD_STYLE, MainActivity.HUD_STYLE_ISLAND)
                ?: MainActivity.HUD_STYLE_ISLAND,
            islandOpacityIndex = prefs.getInt("PREF_ISLAND_OPACITY_INDEX", 0).coerceIn(0, 3),
            customOpacityPct = prefs.getInt(MainActivity.PREF_KEY_CUSTOM_OPACITY_PCT, 100).coerceIn(20, 100),
            questionSolveMode = QuestionSolveMode.fromId(
                prefs.getString(MainActivity.PREF_KEY_QUESTION_SOLVE_MODE, QuestionSolveMode.STANDARD.id)
            ),
            enginePerformanceMode = savedEngineMode,
            displayMode = savedDisplayMode,
            naturalTimingEnabled = prefs.getBoolean(MainActivity.PREF_KEY_ANTI_CHEAT, true),
            scanIntervalSec = prefs.getInt(MainActivity.PREF_KEY_SCAN_INTERVAL_SEC, 3).coerceIn(1, 10),
            nextDelayMs = prefs.getInt(MainActivity.PREF_KEY_NEXT_DELAY_MS, 1500).coerceIn(500, 3500),
            instantTriggerEnabled = prefs.getBoolean(MainActivity.PREF_KEY_INSTANT_TRIGGER, true),
            autoCopyClipboard = prefs.getBoolean(MainActivity.PREF_KEY_AUTO_COPY_CLIPBOARD, false),
            hapticEnabled = prefs.getBoolean(MainActivity.PREF_KEY_HAPTIC, true),
            batteryCamouflage = prefs.getBoolean(MainActivity.PREF_KEY_CAMOUFLAGE, false),
            companionSyncEnabled = prefs.getBoolean(MainActivity.PREF_KEY_COMPANION_SYNC_ENABLED, false),
            companionHost = prefs.getString(MainActivity.PREF_KEY_COMPANION_HOST, "192.168.1.100") ?: "192.168.1.100",
            companionPort = prefs.getInt(MainActivity.PREF_KEY_COMPANION_PORT, 59124),
            hasSecretApiKey = true,
            geminiModel = normalizedModel,
            subjectMode = savedSubject,
            dualVerifyEnabled = dualVerify,
            translateToEnglish = translate,
            advCropToScanEnabled = prefs.getBoolean(MainActivity.PREF_KEY_ADV_CROP_TO_SCAN, false),
            advIslandGesturesEnabled = prefs.getBoolean(MainActivity.PREF_KEY_ADV_ISLAND_GESTURES, false),
            advSplitPillEnabled = prefs.getBoolean(MainActivity.PREF_KEY_ADV_SPLIT_PILL, false),
            advMinimalShapesEnabled = prefs.getBoolean(MainActivity.PREF_KEY_ADV_MINIMAL_SHAPES, false),
            advNetSpeedDisguiseEnabled = prefs.getBoolean(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE, false),
            advAutoFadeIdleEnabled = prefs.getBoolean(MainActivity.PREF_KEY_ADV_AUTO_FADE_IDLE, false),
            advFlashcardsExportEnabled = prefs.getBoolean(MainActivity.PREF_KEY_ADV_FLASHCARDS_EXPORT, false),
            advDarkObsidianEnabled = advDarkObsidian,
            fluidAnimationsEnabled = prefs.getBoolean(MainActivity.PREF_KEY_FLUID_ANIMATIONS_ENABLED, true),
            advSpringPhysicsEnabled = prefs.getBoolean(MainActivity.PREF_KEY_ADV_SPRING_PHYSICS, false),
            springDamping = prefs.getFloat(MainActivity.PREF_KEY_SPRING_DAMPING, 0.72f).coerceIn(0.35f, 1.0f),
            springStiffness = prefs.getFloat(MainActivity.PREF_KEY_SPRING_STIFFNESS, 650f).coerceIn(200f, 1500f),
            advSplitScreenRailEnabled = prefs.getBoolean(MainActivity.PREF_KEY_ADV_SPLIT_SCREEN_RAIL, false),
            advQuickTileNotifEnabled = prefs.getBoolean(MainActivity.PREF_KEY_ADV_QUICK_TILE_NOTIF, false),
            dfTwoPartCaptureEnabled = prefs.getBoolean(MainActivity.PREF_KEY_DF_TWO_PART_CAPTURE, true),
            dfScrollTimerEnabled = prefs.getBoolean(MainActivity.PREF_KEY_DF_SCROLL_TIMER, true),
            dfComplexModeEnabled = prefs.getBoolean(MainActivity.PREF_KEY_DF_COMPLEX_MODE, true),
            dfDiagramVisionEnabled = prefs.getBoolean(MainActivity.PREF_KEY_DF_DIAGRAM_VISION, false),
            dfQuickCropEnabled = prefs.getBoolean(MainActivity.PREF_KEY_DF_QUICK_CROP, false),
            dfSwipeMethodLoopEnabled = prefs.getBoolean(MainActivity.PREF_KEY_DF_SWIPE_METHOD_LOOP, true),
            islandAccentTheme = prefs.getString(
                MainActivity.PREF_KEY_ISLAND_ACCENT_THEME,
                com.omnisolve.overlay.ui.DynamicIslandBorderDrawable.ACCENT_CRIMSON
            ) ?: com.omnisolve.overlay.ui.DynamicIslandBorderDrawable.ACCENT_CRIMSON,
            islandSizePreset = prefs.getString(
                MainActivity.PREF_KEY_ISLAND_SIZE_PRESET,
                MainActivity.ISLAND_SIZE_STANDARD
            ) ?: MainActivity.ISLAND_SIZE_STANDARD,
            islandScalePct = prefs.getInt(MainActivity.PREF_KEY_ISLAND_SCALE_PCT, 112).coerceIn(85, 145),
            islandAnswerScale = prefs.getString(
                MainActivity.PREF_KEY_ISLAND_ANSWER_SCALE,
                MainActivity.ANSWER_SCALE_LARGE
            ) ?: MainActivity.ANSWER_SCALE_LARGE,
            islandEdgeSnap = prefs.getBoolean(MainActivity.PREF_KEY_ISLAND_EDGE_SNAP, true),
            islandIdleFadeSec = prefs.getInt(MainActivity.PREF_KEY_ISLAND_IDLE_FADE_SEC, 4),
            islandWidthDp = prefs.getInt(MainActivity.PREF_KEY_ISLAND_WIDTH_DP, 0).let { if (it <= 0) 0 else it.coerceIn(180, 360) },
            islandHeightDp = prefs.getInt(MainActivity.PREF_KEY_ISLAND_HEIGHT_DP, 56).coerceIn(44, 92),
            islandCornerRadiusDp = prefs.getInt(MainActivity.PREF_KEY_ISLAND_CORNER_RADIUS_DP, 32).coerceIn(12, 42),
            islandAnimSpeed = prefs.getFloat(MainActivity.PREF_KEY_ISLAND_ANIM_SPEED, 1.0f).coerceIn(0.5f, 2.2f),
            islandCutoutAlign = prefs.getBoolean(MainActivity.PREF_KEY_ISLAND_CUTOUT_ALIGN, false),
            islandCutoutOffsetXDp = prefs.getInt(MainActivity.PREF_KEY_ISLAND_CUTOUT_OFFSET_X_DP, 0).coerceIn(-60, 60),
            islandCutoutOffsetYDp = prefs.getInt(MainActivity.PREF_KEY_ISLAND_CUTOUT_OFFSET_Y_DP, 0).coerceIn(-24, 60),
            googleFormUrlInput = GoogleFormsSessionStore.getLastUrl(getApplication()),
            googleFormsModel = normalizedFormsModel,
            googleFormResult = GoogleFormsSessionStore.getLastResult(getApplication()),
            isGoogleFormPinned = GoogleFormsNotificationHelper.isNotificationPinned(getApplication()),
            onboardingGuideCompleted = prefs.getBoolean("PREF_ONBOARDING_GUIDE_SEEN", false),
            showOnboardingGuide = false,
            customPinConfig = PrismEngagementNotificationManager.loadCustomPinConfig(getApplication()),
            autoWeeklyEngagementEnabled = PrismEngagementNotificationManager.isAutoEngagementEnabled(getApplication()),
            hasNotificationPermission = PrismEngagementNotificationManager.hasNotificationPermission(getApplication()),
            effectiveThemeSpec = run {
                val cache = themeRepository.loadPreferences()
                computeEffectiveSpec(
                    preferLocalDefault = cache.preferLocalDefault,
                    selectedPresetId = cache.selectedPresetId,
                    animationOverride = cache.animationIntensityOverride,
                    appearanceOverride = cache.colorAppearanceOverride
                )
            },
            preferLocalDefaultTheme = themeRepository.loadPreferences().preferLocalDefault,
            selectedThemePresetId = themeRepository.loadPreferences().selectedPresetId,
            themeAnimationOverride = themeRepository.loadPreferences().animationIntensityOverride,
            themeColorAppearanceOverride = themeRepository.loadPreferences().colorAppearanceOverride,
            solvedCount = prefs.getInt("PREF_SOLVED_COUNT", 12),
            historyEntries = loadPersistedOverlayHistory()
        )
    }

    fun refreshSystemStatuses() {
        val ctx = getApplication<Application>()
        val canDraw = Settings.canDrawOverlays(ctx)
        val hasGoogleSession = try {
            val cookie = CookieManager.getInstance().getCookie("https://gemini.google.com")
            !cookie.isNullOrBlank() && (
                cookie.contains("SID") || cookie.contains("HSID") ||
                    cookie.contains("SSID") || cookie.contains("SAPISID") ||
                    cookie.contains("ACCOUNT_CHOOSER")
                )
        } catch (_: Exception) {
            false
        }
        val currentUser = try {
            if (FirebaseApp.getApps(ctx).isNotEmpty()) Firebase.auth.currentUser else null
        } catch (_: Exception) {
            null
        }
        if (authStateListener == null && FirebaseApp.getApps(ctx).isNotEmpty()) {
            attachFirebaseAuthObserver()
        }

        val configuredKeys = MultiProviderApiSolver.getConfiguredProviders(ctx).size
        val rawLatestMethod = SolverMethod.fromId(
            prefs.getString(MainActivity.PREF_KEY_SOLVER_METHOD, _uiState.value.solverMethod.id)
        )
        val latestMethod = if (currentUser == null && rawLatestMethod != SolverMethod.BACKGROUND_GEMINI) {
            prefs.edit()
                .putString(MainActivity.PREF_KEY_SOLVER_METHOD, SolverMethod.BACKGROUND_GEMINI.id)
                .putString(MainActivity.PREF_KEY_DISPLAY_MODE, MainActivity.MODE_STEALTH)
                .apply()
            SolverMethod.BACKGROUND_GEMINI
        } else {
            rawLatestMethod
        }
        val latestSolveMode = QuestionSolveMode.fromId(
            prefs.getString(MainActivity.PREF_KEY_QUESTION_SOLVE_MODE, _uiState.value.questionSolveMode.id)
        )
        val latestOpacityPct = prefs.getInt(
            MainActivity.PREF_KEY_CUSTOM_OPACITY_PCT,
            _uiState.value.customOpacityPct
        ).coerceIn(20, 100)
        val overlayRunning = OverlayService.isServiceRunning()
        val captureActive = OverlayService.hasScreenCapture()
        _uiState.update {
            it.copy(
                isOverlayRunning = overlayRunning,
                isCaptureAuthorized = if (overlayRunning) captureActive else false,
                hasOverlayPermission = canDraw,
                isGoogleWebLoggedIn = hasGoogleSession,
                firebaseUserId = currentUser?.uid,
                firebaseUserName = currentUser?.displayName,
                firebaseUserEmail = currentUser?.email,
                signInPromptDismissedForOcrOnly = if (currentUser != null) false else it.signInPromptDismissedForOcrOnly,
                solverMethod = latestMethod,
                displayMode = if (currentUser == null) MainActivity.MODE_STEALTH else it.displayMode,
                questionSolveMode = latestSolveMode,
                customOpacityPct = latestOpacityPct,
                configuredKeyCount = configuredKeys,
                hasSecretApiKey = true,
                googleFormResult = GoogleFormsSessionStore.getLastResult(ctx) ?: it.googleFormResult,
                googleFormUrlInput = it.googleFormUrlInput.ifBlank { GoogleFormsSessionStore.getLastUrl(ctx) },
                isGoogleFormPinned = GoogleFormsNotificationHelper.isNotificationPinned(ctx),
                customPinConfig = PrismEngagementNotificationManager.loadCustomPinConfig(ctx),
                autoWeeklyEngagementEnabled = PrismEngagementNotificationManager.isAutoEngagementEnabled(ctx),
                hasNotificationPermission = PrismEngagementNotificationManager.hasNotificationPermission(ctx),
                solvedCount = prefs.getInt("PREF_SOLVED_COUNT", it.solvedCount),
                historyEntries = loadPersistedOverlayHistory()
            )
        }
    }

    fun setGoogleSignInLoading(loading: Boolean) {
        _uiState.update { it.copy(isSigningInGoogle = loading) }
    }

    fun continueWithOcrOnly() {
        prefs.edit()
            .putString(MainActivity.PREF_KEY_SOLVER_METHOD, SolverMethod.BACKGROUND_GEMINI.id)
            .putString(MainActivity.PREF_KEY_DISPLAY_MODE, MainActivity.MODE_STEALTH)
            .apply()
        _uiState.update {
            it.copy(
                signInPromptDismissedForOcrOnly = true,
                solverMethod = SolverMethod.BACKGROUND_GEMINI,
                displayMode = MainActivity.MODE_STEALTH
            )
        }
        showBannerMessage("Free Screen Reader active • Sign in anytime for extra styles")
    }

    fun requestGoogleSignInGate() {
        _uiState.update { it.copy(signInPromptDismissedForOcrOnly = false) }
    }

    fun resetF1ProviderHealth() {
        F1ProviderHealthManager.resetCooldownsAndStats()
        _uiState.update { cur ->
            cur.copy(f1HealthSnapshots = F1ProviderHealthManager.getAllSnapshots())
        }
        showBannerMessage("F1 provider health & cooldowns reset")
    }

    fun deleteGoogleAccount(
        onDeleted: () -> Unit = {},
        onRequireReauth: () -> Unit = {}
    ) {
        if (_uiState.value.isDeletingAccount) return
        val ctx = getApplication<Application>()
        _uiState.update { it.copy(isDeletingAccount = true) }
        viewModelScope.launch {
            try {
                val user = if (FirebaseApp.getApps(ctx).isNotEmpty()) Firebase.auth.currentUser else null
                if (user != null) {
                    try {
                        SolvedQuestionRepository(ctx).deleteAllUserQuestions(user.uid)
                    } catch (_: Exception) {
                    }
                    try {
                        FirebaseTelemetryRepository.deleteUserTelemetry(ctx, user.uid)
                    } catch (_: Exception) {
                    }
                    user.delete().await()
                }
                stopCloudTelemetryObservers()
                F1MasterEngine.onAccountSignedOut()
                GoogleAuthManager.signOut(ctx)
                prefs.edit()
                    .putString(MainActivity.PREF_KEY_SOLVER_METHOD, SolverMethod.BACKGROUND_GEMINI.id)
                    .putString(MainActivity.PREF_KEY_DISPLAY_MODE, MainActivity.MODE_STEALTH)
                    .apply()
                _uiState.update {
                    it.copy(
                        isDeletingAccount = false,
                        firebaseUserId = null,
                        firebaseUserName = null,
                        firebaseUserEmail = null,
                        cloudActivityProfile = null,
                        recentCloudRequestLogs = emptyList(),
                        cloudTelemetryError = null,
                        signInPromptDismissedForOcrOnly = false,
                        solverMethod = SolverMethod.BACKGROUND_GEMINI,
                        displayMode = MainActivity.MODE_STEALTH
                    )
                }
                showBannerMessage("Google account & cloud history deleted")
                onDeleted()
            } catch (_: FirebaseAuthRecentLoginRequiredException) {
                _uiState.update { it.copy(isDeletingAccount = false) }
                showBannerMessage("Please sign in with Google again before deleting your account.")
                onRequireReauth()
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeletingAccount = false) }
                showBannerMessage("Could not delete account: ${e.localizedMessage ?: "Try again"}")
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                homeSubScreen = if (tab != AppTab.Home) HomeSubScreen.MAIN else it.homeSubScreen
            )
        }
    }

    fun openHomeSubScreen(subScreen: HomeSubScreen) {
        _uiState.update {
            it.copy(
                selectedTab = AppTab.Home,
                homeSubScreen = subScreen
            )
        }
    }

    fun acceptUserConsent() {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_USER_CONSENT_ACCEPTED, true).apply()
        _uiState.update { it.copy(userConsentAccepted = true) }
    }

    fun setOverlayServiceState(running: Boolean, captureAuthorized: Boolean = _uiState.value.isCaptureAuthorized) {
        _uiState.update {
            it.copy(
                isOverlayRunning = running,
                isCaptureAuthorized = captureAuthorized
            )
        }
    }

    fun showBannerMessage(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
        viewModelScope.launch {
            delay(2800)
            _uiState.update { cur ->
                if (cur.statusBannerMessage == message) cur.copy(statusBannerMessage = null) else cur
            }
        }
    }

    fun setExtractionEngine(engine: String, onSyncService: () -> Unit) {
        prefs.edit().putString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, engine).apply()
        _uiState.update { it.copy(extractionEngine = engine) }
        onSyncService()
    }

    fun setNativeFallbackOcr(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_NATIVE_FALLBACK_OCR, enabled).apply()
        _uiState.update { it.copy(nativeFallbackOcr = enabled) }
        onSyncService()
    }

    fun selectSolverMethod(
        method: SolverMethod,
        onSyncService: () -> Unit,
        onRequireGoogleSignIn: () -> Unit = {}
    ) {
        val targetDisplayMode = if (method == SolverMethod.OVERLAPPING_GOOGLE) {
            MainActivity.MODE_WINDOW
        } else {
            MainActivity.MODE_STEALTH
        }
        prefs.edit()
            .putString(MainActivity.PREF_KEY_SOLVER_METHOD, method.id)
            .putString(MainActivity.PREF_KEY_DISPLAY_MODE, targetDisplayMode)
            .apply()
        launchDataStoreWrite { settingsDataStore.updateSolverMethod(method) }
        _uiState.update {
            it.copy(
                solverMethod = method,
                displayMode = targetDisplayMode
            )
        }
        showBannerMessage("Selected: ${method.title}")
        onSyncService()
    }

    fun setAutoFailoverEnabled(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_AUTO_FAILOVER, enabled).apply()
        _uiState.update { it.copy(autoFailoverEnabled = enabled) }
        onSyncService()
    }

    fun saveMultiProviderApiKeys(
        nvidiaKey: String,
        openRouterKey: String,
        geminiKey: String,
        openAiKey: String,
        onSyncService: () -> Unit
    ) {
        val cleanNvidia = nvidiaKey.trim()
        val cleanOpenRouter = openRouterKey.trim()
        val cleanGemini = geminiKey.trim()
        val cleanOpenAi = openAiKey.trim()

        SecureKeyStorage.saveKeys(
            getApplication(),
            mapOf(
                MainActivity.PREF_KEY_NVIDIA_API_KEY to cleanNvidia,
                MainActivity.PREF_KEY_OPENROUTER_API_KEY to cleanOpenRouter,
                MainActivity.PREF_KEY_GEMINI_API_KEY to cleanGemini,
                MainActivity.PREF_KEY_OPENAI_API_KEY to cleanOpenAi
            )
        )

        val activeCount = MultiProviderApiSolver.getConfiguredProviders(getApplication()).size
        _uiState.update {
            it.copy(
                nvidiaApiKey = cleanNvidia,
                openRouterApiKey = cleanOpenRouter,
                geminiApiKey = cleanGemini,
                openAiApiKey = cleanOpenAi,
                configuredKeyCount = activeCount
            )
        }
        showBannerMessage(
            if (activeCount > 0) "Saved $activeCount encrypted API key(s) on device"
            else "No API keys saved locally"
        )
        onSyncService()
    }

    fun clearLocalApiKeys(onSyncService: () -> Unit) {
        SecureKeyStorage.clearKeys(
            getApplication(),
            listOf(
                MainActivity.PREF_KEY_NVIDIA_API_KEY,
                MainActivity.PREF_KEY_OPENROUTER_API_KEY,
                MainActivity.PREF_KEY_GEMINI_API_KEY,
                MainActivity.PREF_KEY_OPENAI_API_KEY
            )
        )

        _uiState.update {
            it.copy(
                nvidiaApiKey = "",
                openRouterApiKey = "",
                geminiApiKey = "",
                openAiApiKey = "",
                configuredKeyCount = 0
            )
        }
        showBannerMessage("Cleared local API keys from device")
        onSyncService()
    }

    fun testSavedApiKeys(
        nvidiaKey: String,
        openRouterKey: String,
        geminiKey: String,
        openAiKey: String,
        onSyncService: () -> Unit
    ) {
        if (_uiState.value.isTestingApiKeys) return
        saveMultiProviderApiKeys(nvidiaKey, openRouterKey, geminiKey, openAiKey, onSyncService)
        _uiState.update { it.copy(isTestingApiKeys = true, apiKeysTestSummary = "Testing configured AI keys...") }
        viewModelScope.launch {
            val summary = MultiProviderApiSolver.verifyConfiguredProviders(getApplication())
            _uiState.update {
                it.copy(
                    isTestingApiKeys = false,
                    apiKeysTestSummary = summary
                )
            }
        }
    }

    fun applyStudyPreset(preset: String, onSyncService: () -> Unit) {
        val editor = prefs.edit()
        when (preset) {
            "STUDY" -> {
                editor.putString(MainActivity.PREF_KEY_HUD_STYLE, MainActivity.HUD_STYLE_ISLAND)
                    .putString(MainActivity.PREF_KEY_DISPLAY_MODE, MainActivity.MODE_STEALTH)
                    .putBoolean(MainActivity.PREF_KEY_CAMOUFLAGE, false)
                    .putBoolean(MainActivity.PREF_KEY_HAPTIC, true)
                    .putBoolean(MainActivity.PREF_KEY_ANTI_CHEAT, true)
                    .putBoolean(MainActivity.PREF_KEY_INSTANT_TRIGGER, true)
                    .putInt(MainActivity.PREF_KEY_SCAN_INTERVAL_SEC, 3)
                    .putInt(MainActivity.PREF_KEY_NEXT_DELAY_MS, 1500)
                    .putString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, MainActivity.ENGINE_OCR)
                    .apply()
                _uiState.update {
                    it.copy(
                        activePreset = "STUDY",
                        hudStyle = MainActivity.HUD_STYLE_ISLAND,
                        displayMode = MainActivity.MODE_STEALTH,
                        batteryCamouflage = false,
                        hapticEnabled = true,
                        naturalTimingEnabled = true,
                        instantTriggerEnabled = true,
                        scanIntervalSec = 3,
                        nextDelayMs = 1500,
                        extractionEngine = MainActivity.ENGINE_OCR
                    )
                }
                showBannerMessage("Standard Pill active — tap top pill to solve")
            }
            "FAST" -> {
                editor.putString(MainActivity.PREF_KEY_HUD_STYLE, MainActivity.HUD_STYLE_ISLAND)
                    .putString(MainActivity.PREF_KEY_DISPLAY_MODE, MainActivity.MODE_STEALTH)
                    .putBoolean(MainActivity.PREF_KEY_CAMOUFLAGE, false)
                    .putBoolean(MainActivity.PREF_KEY_HAPTIC, false)
                    .putBoolean(MainActivity.PREF_KEY_ANTI_CHEAT, false)
                    .putBoolean(MainActivity.PREF_KEY_INSTANT_TRIGGER, true)
                    .putInt(MainActivity.PREF_KEY_SCAN_INTERVAL_SEC, 1)
                    .putInt(MainActivity.PREF_KEY_NEXT_DELAY_MS, 500)
                    .putString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, MainActivity.ENGINE_OCR)
                    .apply()
                _uiState.update {
                    it.copy(
                        activePreset = "FAST",
                        hudStyle = MainActivity.HUD_STYLE_ISLAND,
                        displayMode = MainActivity.MODE_STEALTH,
                        batteryCamouflage = false,
                        hapticEnabled = false,
                        naturalTimingEnabled = false,
                        instantTriggerEnabled = true,
                        scanIntervalSec = 1,
                        nextDelayMs = 500,
                        extractionEngine = MainActivity.ENGINE_OCR
                    )
                }
                showBannerMessage("Quick Check active — rapid screen answers")
            }
            "SUBTLE" -> {
                editor.putString(MainActivity.PREF_KEY_HUD_STYLE, MainActivity.HUD_STYLE_ISLAND)
                    .putString(MainActivity.PREF_KEY_DISPLAY_MODE, MainActivity.MODE_STEALTH)
                    .putBoolean(MainActivity.PREF_KEY_CAMOUFLAGE, true)
                    .putBoolean(MainActivity.PREF_KEY_HAPTIC, true)
                    .putBoolean(MainActivity.PREF_KEY_ANTI_CHEAT, true)
                    .putBoolean(MainActivity.PREF_KEY_INSTANT_TRIGGER, false)
                    .putInt(MainActivity.PREF_KEY_SCAN_INTERVAL_SEC, 6)
                    .putInt(MainActivity.PREF_KEY_NEXT_DELAY_MS, 2500)
                    .putString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, MainActivity.ENGINE_OCR)
                    .apply()
                _uiState.update {
                    it.copy(
                        activePreset = "SUBTLE",
                        hudStyle = MainActivity.HUD_STYLE_ISLAND,
                        displayMode = MainActivity.MODE_STEALTH,
                        batteryCamouflage = true,
                        hapticEnabled = true,
                        naturalTimingEnabled = true,
                        instantTriggerEnabled = false,
                        scanIntervalSec = 6,
                        nextDelayMs = 2500,
                        extractionEngine = MainActivity.ENGINE_OCR
                    )
                }
                showBannerMessage("Stealth Mode active: battery disguise & gentle pulse")
            }
        }
        onSyncService()
    }

    fun setHudStyle(style: String, onSyncService: () -> Unit) {
        prefs.edit().putString(MainActivity.PREF_KEY_HUD_STYLE, style).apply()
        _uiState.update { it.copy(hudStyle = style) }
        onSyncService()
    }

    fun setIslandOpacityIndex(index: Int, onSyncService: () -> Unit) {
        val safe = index.coerceIn(0, 3)
        val mappedPct = listOf(100, 90, 50, 25)[safe]
        prefs.edit()
            .putInt("PREF_ISLAND_OPACITY_INDEX", safe)
            .putInt(MainActivity.PREF_KEY_CUSTOM_OPACITY_PCT, mappedPct)
            .apply()
        launchDataStoreWrite { settingsDataStore.updateIslandAppearance(customOpacityPct = mappedPct) }
        _uiState.update { it.copy(islandOpacityIndex = safe, customOpacityPct = mappedPct) }
        onSyncService()
    }

    fun setCustomOpacityPct(pct: Int, onSyncService: () -> Unit) {
        val safePct = pct.coerceIn(20, 100)
        prefs.edit().putInt(MainActivity.PREF_KEY_CUSTOM_OPACITY_PCT, safePct).apply()
        launchDataStoreWrite { settingsDataStore.updateIslandAppearance(customOpacityPct = safePct) }
        _uiState.update { it.copy(customOpacityPct = safePct) }
        onSyncService()
    }

    fun setQuestionSolveMode(mode: QuestionSolveMode, onSyncService: () -> Unit) {
        prefs.edit().putString(MainActivity.PREF_KEY_QUESTION_SOLVE_MODE, mode.id).apply()
        _uiState.update { it.copy(questionSolveMode = mode) }
        showBannerMessage("Solve Mode: ${mode.fullLabel}")
        onSyncService()
    }

    fun setEnginePerformanceMode(mode: EnginePerformanceMode, onSyncService: (() -> Unit)? = null) {
        prefs.edit().putString(MainActivity.PREF_KEY_ENGINE_PERFORMANCE_MODE, mode.id).apply()
        _uiState.update { it.copy(enginePerformanceMode = mode) }
        showBannerMessage("Speed mode: ${mode.title}")
        onSyncService?.invoke()
    }

    fun setDfTwoPartCapture(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_DF_TWO_PART_CAPTURE, enabled).apply()
        _uiState.update { it.copy(dfTwoPartCaptureEnabled = enabled) }
        onSyncService()
    }

    fun setDfScrollTimer(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_DF_SCROLL_TIMER, enabled).apply()
        _uiState.update { it.copy(dfScrollTimerEnabled = enabled) }
        onSyncService()
    }

    fun setDfComplexMode(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_DF_COMPLEX_MODE, enabled).apply()
        _uiState.update { it.copy(dfComplexModeEnabled = enabled) }
        onSyncService()
    }

    fun setDfDiagramVision(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_DF_DIAGRAM_VISION, enabled).apply()
        _uiState.update { it.copy(dfDiagramVisionEnabled = enabled) }
        onSyncService()
    }

    fun setDfQuickCrop(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_DF_QUICK_CROP, enabled).apply()
        _uiState.update { it.copy(dfQuickCropEnabled = enabled) }
        onSyncService()
    }

    fun setDfSwipeMethodLoop(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_DF_SWIPE_METHOD_LOOP, enabled).apply()
        _uiState.update { it.copy(dfSwipeMethodLoopEnabled = enabled) }
        onSyncService()
    }

    fun setDisplayMode(mode: String, onSyncService: () -> Unit) {
        prefs.edit().putString(MainActivity.PREF_KEY_DISPLAY_MODE, mode).apply()
        _uiState.update { it.copy(displayMode = mode) }
        onSyncService()
    }

    fun setNaturalTiming(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_ANTI_CHEAT, enabled).apply()
        _uiState.update { it.copy(naturalTimingEnabled = enabled) }
        onSyncService()
    }

    fun setScanIntervalSec(sec: Int, onSyncService: () -> Unit) {
        val safe = sec.coerceIn(1, 10)
        prefs.edit().putInt(MainActivity.PREF_KEY_SCAN_INTERVAL_SEC, safe).apply()
        _uiState.update { it.copy(scanIntervalSec = safe) }
        onSyncService()
    }

    fun setNextDelayMs(ms: Int, onSyncService: () -> Unit) {
        val safe = ms.coerceIn(500, 3500)
        prefs.edit().putInt(MainActivity.PREF_KEY_NEXT_DELAY_MS, safe).apply()
        _uiState.update { it.copy(nextDelayMs = safe) }
        onSyncService()
    }

    fun setInstantTrigger(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_INSTANT_TRIGGER, enabled).apply()
        _uiState.update { it.copy(instantTriggerEnabled = enabled) }
        onSyncService()
    }

    fun setAutoCopyClipboard(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_AUTO_COPY_CLIPBOARD, enabled).apply()
        _uiState.update { it.copy(autoCopyClipboard = enabled) }
        onSyncService()
    }

    fun setHapticEnabled(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_HAPTIC, enabled).apply()
        _uiState.update { it.copy(hapticEnabled = enabled) }
        onSyncService()
    }

    fun setBatteryCamouflage(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_CAMOUFLAGE, enabled).apply()
        _uiState.update { it.copy(batteryCamouflage = enabled) }
        showBannerMessage(
            if (enabled) "Answer format: Charging 91, 92, 93, 94"
            else "Answer format: Option ABCD"
        )
        onSyncService()
    }

    fun updateCompanionConnection(
        enabled: Boolean,
        host: String,
        portStr: String,
        onSyncService: () -> Unit
    ) {
        val cleanHost = host.trim().ifEmpty { "192.168.1.100" }
        val cleanPort = portStr.trim().toIntOrNull()?.coerceIn(1024, 65535) ?: 59124
        prefs.edit()
            .putBoolean(MainActivity.PREF_KEY_COMPANION_SYNC_ENABLED, enabled)
            .putString(MainActivity.PREF_KEY_COMPANION_HOST, cleanHost)
            .putInt(MainActivity.PREF_KEY_COMPANION_PORT, cleanPort)
            .apply()
        _uiState.update {
            it.copy(
                companionSyncEnabled = enabled,
                companionHost = cleanHost,
                companionPort = cleanPort
            )
        }
        onSyncService()
    }

    fun applyDeepLinkSync(host: String, port: Int, onSyncService: () -> Unit) {
        prefs.edit()
            .putString(MainActivity.PREF_KEY_COMPANION_HOST, host)
            .putInt(MainActivity.PREF_KEY_COMPANION_PORT, port)
            .putBoolean(MainActivity.PREF_KEY_COMPANION_SYNC_ENABLED, true)
            .apply()
        _uiState.update {
            it.copy(
                companionHost = host,
                companionPort = port,
                companionSyncEnabled = true
            )
        }
        showBannerMessage("Paired with computer helper at $host:$port")
        onSyncService()
    }

    fun selectGeminiModel(modelId: String) {
        val safeModel = GeminiVisionClient.sanitizeModel(modelId)
        prefs.edit().putString(MainActivity.PREF_KEY_GEMINI_MODEL, safeModel).apply()
        _uiState.update { it.copy(geminiModel = safeModel) }
        showBannerMessage("Updated solving helper")
    }

    fun selectGoogleFormsModel(modelId: String) {
        val option = GoogleFormsModelOption.fromId(modelId)
        prefs.edit().putString(MainActivity.PREF_KEY_GOOGLE_FORMS_MODEL, option.id).apply()
        _uiState.update {
            it.copy(
                googleFormsModel = option.id,
                googleFormsNeedsWebLogin = false,
                googleFormError = null
            )
        }
        showBannerMessage("Google Forms AI set to ${option.label}")
    }

    fun selectSubjectMode(mode: SubjectMode) {
        prefs.edit().putString(MainActivity.PREF_KEY_SUBJECT_MODE, mode.id).apply()
        _uiState.update { it.copy(subjectMode = mode) }
        showBannerMessage("Subject focus set to ${mode.label}")
    }

    fun setDualVerifyEnabled(enabled: Boolean, onSyncService: () -> Unit = {}) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_DUAL_VERIFY, enabled).apply()
        _uiState.update { it.copy(dualVerifyEnabled = enabled) }
        onSyncService()
    }

    fun setTranslateToEnglish(enabled: Boolean) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_TRANSLATE_ENGLISH, enabled).apply()
        _uiState.update { it.copy(translateToEnglish = enabled) }
    }

    // ─── Advanced Options Setters (All Default OFF, Toggled in Advanced Settings) ───

    fun setAdvCropToScan(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_ADV_CROP_TO_SCAN, enabled).apply()
        _uiState.update { it.copy(advCropToScanEnabled = enabled) }
        showBannerMessage(if (enabled) "Crop-to-Scan Region Selector ON" else "Crop-to-Scan OFF")
        onSyncService()
    }

    fun setAdvIslandGestures(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_ADV_ISLAND_GESTURES, enabled).apply()
        _uiState.update { it.copy(advIslandGesturesEnabled = enabled) }
        showBannerMessage(if (enabled) "Pill Swipe & Long-Press Gestures ON" else "Pill Gestures OFF")
        onSyncService()
    }

    fun setAdvSplitPill(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_ADV_SPLIT_PILL, enabled).apply()
        _uiState.update { it.copy(advSplitPillEnabled = enabled) }
        showBannerMessage(if (enabled) "Split Pill Mode (Badge • Short Text) ON" else "Split Pill Mode OFF")
        onSyncService()
    }

    fun setAdvMinimalShapes(enabled: Boolean, onSyncService: () -> Unit) {
        val currentHud = _uiState.value.hudStyle
        val nextHud = if (enabled) {
            if (currentHud == MainActivity.HUD_STYLE_TOP_STRIP || currentHud == MainActivity.HUD_STYLE_EDGE_TAB) {
                currentHud
            } else {
                MainActivity.HUD_STYLE_TOP_STRIP
            }
        } else {
            if (currentHud == MainActivity.HUD_STYLE_TOP_STRIP || currentHud == MainActivity.HUD_STYLE_EDGE_TAB) {
                MainActivity.HUD_STYLE_ISLAND
            } else {
                currentHud
            }
        }
        prefs.edit()
            .putBoolean(MainActivity.PREF_KEY_ADV_MINIMAL_SHAPES, enabled)
            .putString(MainActivity.PREF_KEY_HUD_STYLE, nextHud)
            .apply()
        _uiState.update { it.copy(advMinimalShapesEnabled = enabled, hudStyle = nextHud) }
        onSyncService()
    }

    fun setAdvNetSpeedDisguise(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE, enabled).apply()
        _uiState.update { it.copy(advNetSpeedDisguiseEnabled = enabled) }
        showBannerMessage(
            if (enabled) "Network Speed Disguise ON (0.91–0.94 MB/s)"
            else "Network Speed Disguise OFF"
        )
        onSyncService()
    }

    fun setAdvAutoFadeIdle(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_ADV_AUTO_FADE_IDLE, enabled).apply()
        _uiState.update { it.copy(advAutoFadeIdleEnabled = enabled) }
        onSyncService()
    }

    fun setAdvFlashcardsExport(enabled: Boolean) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_ADV_FLASHCARDS_EXPORT, enabled).apply()
        _uiState.update { it.copy(advFlashcardsExportEnabled = enabled) }
    }

    fun setAdvDarkObsidian(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit()
            .putBoolean(MainActivity.PREF_KEY_ADV_DARK_OBSIDIAN, enabled)
            .putString(MainActivity.PREF_KEY_THEME_MODE, if (enabled) MainActivity.THEME_DARK else MainActivity.THEME_LIGHT)
            .apply()
        _uiState.update { it.copy(advDarkObsidianEnabled = enabled) }
        showBannerMessage(if (enabled) "Dark Obsidian Liquid Glass Active" else "Daylight White Glass Active")
        onSyncService()
    }

    fun setFluidAnimationsEnabled(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_FLUID_ANIMATIONS_ENABLED, enabled).apply()
        _uiState.update { it.copy(fluidAnimationsEnabled = enabled) }
        showBannerMessage(
            if (enabled) "Fluid, Glitch & Island Animations ON"
            else "Optimization Mode: Heavy Animations OFF"
        )
        onSyncService()
    }

    fun setAdvSpringPhysics(enabled: Boolean) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_ADV_SPRING_PHYSICS, enabled).apply()
        _uiState.update { it.copy(advSpringPhysicsEnabled = enabled) }
    }

    fun updateSpringPhysics(damping: Float, stiffness: Float) {
        val safeDamping = damping.coerceIn(0.35f, 1.0f)
        val safeStiffness = stiffness.coerceIn(200f, 1500f)
        prefs.edit()
            .putFloat(MainActivity.PREF_KEY_SPRING_DAMPING, safeDamping)
            .putFloat(MainActivity.PREF_KEY_SPRING_STIFFNESS, safeStiffness)
            .apply()
        _uiState.update { it.copy(springDamping = safeDamping, springStiffness = safeStiffness) }
    }

    fun setIslandAccentTheme(themeId: String, onSyncService: () -> Unit) {
        val normalized = themeId.uppercase().ifBlank {
            com.omnisolve.overlay.ui.DynamicIslandBorderDrawable.ACCENT_CRIMSON
        }
        prefs.edit().putString(MainActivity.PREF_KEY_ISLAND_ACCENT_THEME, normalized).apply()
        launchDataStoreWrite { settingsDataStore.updateIslandAppearance(accentTheme = normalized) }
        _uiState.update { it.copy(islandAccentTheme = normalized) }
        onSyncService()
    }

    fun setIslandSizePreset(presetId: String, onSyncService: () -> Unit) {
        val normalized = presetId.uppercase().ifBlank { MainActivity.ISLAND_SIZE_STANDARD }
        val mappedPct = when (normalized) {
            MainActivity.ISLAND_SIZE_COMPACT -> 92
            MainActivity.ISLAND_SIZE_LARGE -> 128
            MainActivity.ISLAND_SIZE_XL -> 145
            else -> 112
        }
        prefs.edit()
            .putString(MainActivity.PREF_KEY_ISLAND_SIZE_PRESET, normalized)
            .putInt(MainActivity.PREF_KEY_ISLAND_SCALE_PCT, mappedPct)
            .apply()
        launchDataStoreWrite {
            settingsDataStore.updateIslandAppearance(sizePreset = normalized, scalePct = mappedPct)
        }
        _uiState.update {
            it.copy(
                islandSizePreset = normalized,
                islandScalePct = mappedPct
            )
        }
        onSyncService()
    }

    fun setIslandScalePct(pct: Int, onSyncService: () -> Unit) {
        val safePct = pct.coerceIn(85, 145)
        val matchedPreset = when {
            safePct <= 98 -> MainActivity.ISLAND_SIZE_COMPACT
            safePct <= 118 -> MainActivity.ISLAND_SIZE_STANDARD
            safePct <= 134 -> MainActivity.ISLAND_SIZE_LARGE
            else -> MainActivity.ISLAND_SIZE_XL
        }
        prefs.edit()
            .putInt(MainActivity.PREF_KEY_ISLAND_SCALE_PCT, safePct)
            .putString(MainActivity.PREF_KEY_ISLAND_SIZE_PRESET, matchedPreset)
            .apply()
        launchDataStoreWrite {
            settingsDataStore.updateIslandAppearance(sizePreset = matchedPreset, scalePct = safePct)
        }
        _uiState.update {
            it.copy(
                islandScalePct = safePct,
                islandSizePreset = matchedPreset
            )
        }
        onSyncService()
    }

    fun setIslandAnswerScale(scaleId: String, onSyncService: () -> Unit) {
        val normalized = when (scaleId.uppercase()) {
            MainActivity.ANSWER_SCALE_NORMAL -> MainActivity.ANSWER_SCALE_NORMAL
            MainActivity.ANSWER_SCALE_XL_BOLD -> MainActivity.ANSWER_SCALE_XL_BOLD
            else -> MainActivity.ANSWER_SCALE_LARGE
        }
        prefs.edit().putString(MainActivity.PREF_KEY_ISLAND_ANSWER_SCALE, normalized).apply()
        launchDataStoreWrite { settingsDataStore.updateIslandAppearance(answerScale = normalized) }
        _uiState.update { it.copy(islandAnswerScale = normalized) }
        onSyncService()
    }

    fun setIslandEdgeSnap(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_ISLAND_EDGE_SNAP, enabled).apply()
        launchDataStoreWrite { settingsDataStore.updateIslandAppearance(edgeSnap = enabled) }
        _uiState.update { it.copy(islandEdgeSnap = enabled) }
        onSyncService()
    }

    fun setIslandIdleFadeSec(seconds: Int, onSyncService: () -> Unit) {
        val safeSec = seconds.coerceIn(0, 30)
        val fadeEnabled = safeSec > 0
        prefs.edit()
            .putInt(MainActivity.PREF_KEY_ISLAND_IDLE_FADE_SEC, safeSec)
            .putBoolean(MainActivity.PREF_KEY_ADV_AUTO_FADE_IDLE, fadeEnabled)
            .apply()
        launchDataStoreWrite { settingsDataStore.updateIslandAppearance(idleFadeSec = safeSec) }
        _uiState.update {
            it.copy(
                islandIdleFadeSec = safeSec,
                advAutoFadeIdleEnabled = fadeEnabled
            )
        }
        onSyncService()
    }

    fun setIslandWidthDp(widthDp: Int, onSyncService: () -> Unit) {
        val safeWidth = if (widthDp <= 0) 0 else widthDp.coerceIn(180, 360)
        prefs.edit().putInt(MainActivity.PREF_KEY_ISLAND_WIDTH_DP, safeWidth).apply()
        launchDataStoreWrite { settingsDataStore.updateIslandDimensions(widthDp = safeWidth) }
        _uiState.update { it.copy(islandWidthDp = safeWidth) }
        onSyncService()
    }

    fun setIslandHeightDp(heightDp: Int, onSyncService: () -> Unit) {
        val safeHeight = heightDp.coerceIn(44, 92)
        prefs.edit().putInt(MainActivity.PREF_KEY_ISLAND_HEIGHT_DP, safeHeight).apply()
        launchDataStoreWrite { settingsDataStore.updateIslandDimensions(heightDp = safeHeight) }
        _uiState.update { it.copy(islandHeightDp = safeHeight) }
        onSyncService()
    }

    fun setIslandCornerRadiusDp(radiusDp: Int, onSyncService: () -> Unit) {
        val safeRadius = radiusDp.coerceIn(12, 42)
        prefs.edit().putInt(MainActivity.PREF_KEY_ISLAND_CORNER_RADIUS_DP, safeRadius).apply()
        launchDataStoreWrite { settingsDataStore.updateIslandDimensions(cornerRadiusDp = safeRadius) }
        _uiState.update { it.copy(islandCornerRadiusDp = safeRadius) }
        onSyncService()
    }

    fun setIslandAnimSpeed(speed: Float, onSyncService: () -> Unit) {
        val safeSpeed = speed.coerceIn(0.5f, 2.2f)
        prefs.edit().putFloat(MainActivity.PREF_KEY_ISLAND_ANIM_SPEED, safeSpeed).apply()
        launchDataStoreWrite { settingsDataStore.updateIslandDimensions(animSpeed = safeSpeed) }
        _uiState.update { it.copy(islandAnimSpeed = safeSpeed) }
        onSyncService()
    }

    fun setIslandCutoutAlign(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_ISLAND_CUTOUT_ALIGN, enabled).apply()
        launchDataStoreWrite { settingsDataStore.updateIslandCutoutCalibration(cutoutAlign = enabled) }
        _uiState.update { it.copy(islandCutoutAlign = enabled) }
        showBannerMessage(if (enabled) "Dynamic Island aligned to Camera Cutout" else "Standard floating top alignment restored")
        onSyncService()
    }

    fun setIslandCutoutOffsets(offsetXDp: Int, offsetYDp: Int, onSyncService: () -> Unit) {
        val safeX = offsetXDp.coerceIn(-60, 60)
        val safeY = offsetYDp.coerceIn(-24, 60)
        prefs.edit()
            .putInt(MainActivity.PREF_KEY_ISLAND_CUTOUT_OFFSET_X_DP, safeX)
            .putInt(MainActivity.PREF_KEY_ISLAND_CUTOUT_OFFSET_Y_DP, safeY)
            .apply()
        launchDataStoreWrite {
            settingsDataStore.updateIslandCutoutCalibration(offsetXDp = safeX, offsetYDp = safeY)
        }
        _uiState.update { it.copy(islandCutoutOffsetXDp = safeX, islandCutoutOffsetYDp = safeY) }
        onSyncService()
    }

    fun resetIslandGeometryDefaults(onSyncService: () -> Unit) {
        prefs.edit()
            .putInt(MainActivity.PREF_KEY_ISLAND_WIDTH_DP, 0)
            .putInt(MainActivity.PREF_KEY_ISLAND_HEIGHT_DP, 56)
            .putInt(MainActivity.PREF_KEY_ISLAND_CORNER_RADIUS_DP, 32)
            .putFloat(MainActivity.PREF_KEY_ISLAND_ANIM_SPEED, 1.0f)
            .putBoolean(MainActivity.PREF_KEY_ISLAND_CUTOUT_ALIGN, false)
            .putInt(MainActivity.PREF_KEY_ISLAND_CUTOUT_OFFSET_X_DP, 0)
            .putInt(MainActivity.PREF_KEY_ISLAND_CUTOUT_OFFSET_Y_DP, 0)
            .apply()
        launchDataStoreWrite {
            settingsDataStore.updateIslandDimensions(
                widthDp = 0,
                heightDp = 56,
                cornerRadiusDp = 32,
                animSpeed = 1.0f
            )
            settingsDataStore.updateIslandCutoutCalibration(
                cutoutAlign = false,
                offsetXDp = 0,
                offsetYDp = 0
            )
        }
        _uiState.update {
            it.copy(
                islandWidthDp = 0,
                islandHeightDp = 56,
                islandCornerRadiusDp = 32,
                islandAnimSpeed = 1.0f,
                islandCutoutAlign = false,
                islandCutoutOffsetXDp = 0,
                islandCutoutOffsetYDp = 0
            )
        }
        showBannerMessage("Dynamic Island geometry & cutout calibration reset")
        onSyncService()
    }

    fun setAdvSplitScreenRail(enabled: Boolean) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_ADV_SPLIT_SCREEN_RAIL, enabled).apply()
        _uiState.update { it.copy(advSplitScreenRailEnabled = enabled) }
    }

    fun setAdvQuickTileNotif(enabled: Boolean, onSyncService: () -> Unit) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_ADV_QUICK_TILE_NOTIF, enabled).apply()
        _uiState.update { it.copy(advQuickTileNotifEnabled = enabled) }
        showBannerMessage(
            if (enabled) "Quick Settings Tile & Notification Actions ON"
            else "Notification Action Buttons OFF"
        )
        onSyncService()
    }

    fun updateFlashcardSearch(query: String) {
        _uiState.update { it.copy(flashcardSearchQuery = query) }
    }

    fun updateFlashcardSubjectFilter(subject: String) {
        _uiState.update { it.copy(flashcardSubjectFilter = subject) }
    }

    fun setFlashcardBookmarkedOnly(enabled: Boolean) {
        _uiState.update { it.copy(flashcardBookmarkedOnly = enabled) }
    }

    fun toggleBookmarkHistoryEntry(entryId: Long) {
        val currentSet = loadBookmarkedIds().toMutableSet()
        val nowBookmarked = if (entryId in currentSet) {
            currentSet.remove(entryId)
            false
        } else {
            currentSet.add(entryId)
            true
        }
        prefs.edit().putString("PREF_BOOKMARKED_IDS", currentSet.joinToString(",")).apply()
        viewModelScope.launch(Dispatchers.IO) {
            try { offlineHistoryRepo.setBookmarked(entryId, nowBookmarked) } catch (_: Exception) {}
        }
        _uiState.update { cur ->
            cur.copy(
                historyEntries = cur.historyEntries.map { item ->
                    if (item.id == entryId) item.copy(bookmarked = nowBookmarked) else item
                }
            )
        }
    }

    fun deleteHistoryEntry(entryId: Long) {
        val targetEntry = _uiState.value.historyEntries.firstOrNull { it.id == entryId }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                offlineHistoryRepo.deleteEntry(entryId)
                if (targetEntry != null && targetEntry.questionPreview.isNotBlank()) {
                    com.omnisolve.overlay.cache.PersistentQuizCacheManager.evictAnswer(
                        context = getApplication<Application>(),
                        questionText = targetEntry.questionPreview,
                        subject = SubjectMode.fromId(targetEntry.subject)
                    )
                }
            } catch (_: Exception) {
            }
        }
        _uiState.update { cur ->
            cur.copy(historyEntries = cur.historyEntries.filterNot { it.id == entryId })
        }
        showBannerMessage("Removed from history & offline cache")
    }

    fun clearOfflineRoomCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.omnisolve.overlay.data.local.PrismStudyDatabase
                    .getInstance(getApplication<Application>())
                    .sharedAnswerCacheDao()
                    .clearAll()
            } catch (_: Exception) {
            }
        }
        _uiState.update { it.copy(offlineCacheCount = 0, offlineCacheSavedCalls = 0) }
        showBannerMessage("Cleared offline answer cache")
    }

    fun clearHistory() {
        prefs.edit()
            .remove("PREF_SOLVE_HISTORY")
            .remove("PREF_BOOKMARKED_IDS")
            .putBoolean("PREF_HISTORY_CLEARED_V1", true)
            .apply()
        viewModelScope.launch(Dispatchers.IO) {
            try { offlineHistoryRepo.clearAll() } catch (_: Exception) {}
        }
        _uiState.update { it.copy(historyEntries = emptyList()) }
        showBannerMessage("Local study history cleared")
    }

    fun appendSampleQuestionToExport() {
        val state = _uiState.value
        val sampleIdx = state.currentSampleIndex % samples.size
        val sample = samples[sampleIdx]
        val nowMs = System.currentTimeMillis()
        val timeLabel = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(nowMs))
        val previewTitle = sample.question.take(90)
        val newSolvedCount = state.solvedCount + 1

        try {
            val existing = prefs.getString("PREF_SOLVE_HISTORY", "") ?: ""
            val cleanExp = sample.explanation.replace("\n", " ").replace("||", " ").take(120)
            val entryLine = "$nowMs||${sample.correctLetter}||$previewTitle||$cleanExp||${sample.subjectMode.id}"
            val merged = (listOf(entryLine) + existing.split("\n").filter { it.isNotBlank() })
                .take(25)
                .joinToString("\n")
            prefs.edit()
                .putInt("PREF_SOLVED_COUNT", newSolvedCount)
                .putString("PREF_SOLVE_HISTORY", merged)
                .putBoolean("PREF_HISTORY_CLEARED_V1", false)
                .apply()
        } catch (_: Exception) {
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                offlineHistoryRepo.recordCustomEntry(
                    questionText = previewTitle,
                    answerBadge = sample.correctLetter,
                    explanation = sample.explanation,
                    subject = sample.subjectMode.id,
                    sourceLabel = "Screen Reader (4.9★)",
                    sourceMethod = "SAMPLE_EXPORT"
                )
            } catch (_: Exception) {}
        }

        val newEntry = SolvedHistoryEntry(
            id = nowMs,
            questionPreview = previewTitle,
            answerLetter = sample.correctLetter,
            sourceLabel = "Screen Reader (4.9★)",
            timestamp = timeLabel,
            explanation = sample.explanation,
            subject = sample.subjectMode.id,
            bookmarked = false
        )

        _uiState.update { cur ->
            cur.copy(
                solvedCount = newSolvedCount,
                currentSampleIndex = (cur.currentSampleIndex + 1) % samples.size,
                historyEntries = listOf(newEntry) + cur.historyEntries.take(24)
            )
        }
        showBannerMessage("Added solved Q&A (#${state.historyEntries.size + 1}) to Export list")
    }

    fun updateCustomPracticeInput(text: String) {
        _uiState.update {
            it.copy(
                customPracticeInput = text,
                practiceSolvedLetter = null,
                practiceExplanation = null,
                practiceStatusNote = null
            )
        }
    }

    fun nextSampleQuestion() {
        _uiState.update { state ->
            val nextIdx = (state.currentSampleIndex + 1) % samples.size
            state.copy(
                currentSampleIndex = nextIdx,
                customPracticeInput = "",
                practiceSolvedLetter = null,
                practiceExplanation = null,
                practiceStatusNote = null
            )
        }
    }

    fun solvePracticeQuestion() {
        val state = _uiState.value
        if (state.isPracticeScanning) return

        val isCustom = state.customPracticeInput.isNotBlank()
        val sample = samples[state.currentSampleIndex]
        val fullQuestionText = if (isCustom) {
            state.customPracticeInput.trim()
        } else {
            buildString {
                appendLine(sample.question)
                sample.options.forEach { appendLine(it) }
            }
        }
        val effectiveSubject = if (isCustom) state.subjectMode else sample.subjectMode

        _uiState.update {
            it.copy(
                isPracticeScanning = true,
                practiceSolvedLetter = null,
                practiceExplanation = null,
                practiceStatusNote = null
            )
        }

        viewModelScope.launch {
            var resolvedModel: AnswerModel? = null
            var finalLetter = sample.correctLetter
            var finalExplanation = sample.explanation
            var sourceBadge = "Cloud Gemini • ${state.geminiModel}"

            try {
                val appContext = getApplication<Application>()
                val f1Result = F1MasterEngine.solve(
                    context = appContext,
                    request = F1SolveRequest(
                        preExtractedText = fullQuestionText,
                        subjectMode = effectiveSubject,
                        solveMode = state.questionSolveMode,
                        raceMode = if (state.dualVerifyEnabled) F1RaceMode.ACCURACY_CONSENSUS else F1RaceMode.FAST,
                        requestedModel = state.geminiModel,
                        dualVerify = state.dualVerifyEnabled,
                        translateToEnglish = state.translateToEnglish,
                        allowGhostDomExtraction = false,
                        requireGoogleAuth = isCustom
                    ),
                    ocrEngine = sharedOcrEngine
                )

                if (f1Result.authRequired) {
                    _uiState.update {
                        it.copy(
                            isPracticeScanning = false,
                            practiceStatusNote = f1Result.authErrorMessage.ifBlank {
                                "Sign in with Google to solve custom questions with Prism F1."
                            }
                        )
                    }
                    return@launch
                }

                val aiAnswer = f1Result.answerModel ?: PrismAiSolverEngine.solveGuaranteedFallback(
                    extractedText = fullQuestionText,
                    subjectMode = effectiveSubject,
                    solveMode = state.questionSolveMode,
                    engineLabel = "F1 • Smart Core"
                )
                resolvedModel = aiAnswer
                finalLetter = aiAnswer.correctChoice
                finalExplanation = buildString {
                    if (aiAnswer.shortAnswer.isNotBlank()) {
                        append(aiAnswer.shortAnswer)
                        if (aiAnswer.explanation.isNotBlank()) append(" — ")
                    }
                    if (aiAnswer.explanation.isNotBlank()) {
                        append(aiAnswer.explanation)
                    } else if (!isCustom) {
                        append(sample.explanation)
                    } else {
                        append("Solved live via ${aiAnswer.engineLabel}.")
                    }
                    if (aiAnswer.translatedQuestion.isNotBlank()) {
                        append("\nTranslation: ${aiAnswer.translatedQuestion}")
                    }
                }
                sourceBadge = aiAnswer.engineLabel
            } catch (ce: CancellationException) {
                if (ce !is TimeoutCancellationException) throw ce
                val fallback = PrismAiSolverEngine.solveGuaranteedFallback(
                    extractedText = fullQuestionText,
                    subjectMode = effectiveSubject,
                    solveMode = state.questionSolveMode,
                    engineLabel = "Smart Core"
                )
                resolvedModel = fallback
                finalLetter = if (isCustom) fallback.correctChoice else sample.correctLetter
                finalExplanation = if (isCustom) fallback.explanation else sample.explanation
                sourceBadge = fallback.engineLabel
            } catch (e: Exception) {
                val fallback = PrismAiSolverEngine.solveGuaranteedFallback(
                    extractedText = fullQuestionText,
                    subjectMode = effectiveSubject,
                    solveMode = state.questionSolveMode,
                    engineLabel = "Smart Core"
                )
                resolvedModel = fallback
                finalLetter = if (isCustom) fallback.correctChoice else sample.correctLetter
                finalExplanation = if (isCustom) fallback.explanation else sample.explanation
                sourceBadge = fallback.engineLabel
                delay(240)
            }

            // Persist to Cloud Firestore if signed in with Google
            val appCtx = getApplication<Application>()
            val signedInUid = try {
                if (FirebaseApp.getApps(appCtx).isNotEmpty()) Firebase.auth.currentUser?.uid else null
            } catch (_: Exception) {
                null
            }
            if (!signedInUid.isNullOrBlank()) {
                val answerForCloud = resolvedModel ?: AnswerModel(
                    correctChoice = finalLetter,
                    shortAnswer = "",
                    explanation = finalExplanation,
                    subject = effectiveSubject.id
                )
                try {
                    val repo = SolvedQuestionRepository(appCtx)
                    repo.saveSolvedQuestion(
                        SolvedQuestionRecord.fromAnswerModel(
                            userId = signedInUid,
                            questionText = fullQuestionText,
                            answer = answerForCloud
                        )
                    )
                } catch (_: Exception) {
                }
            }

            val nowMs = System.currentTimeMillis()
            val newSolvedCount = _uiState.value.solvedCount + 1
            val timeLabel = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(nowMs))
            val previewTitle = if (isCustom) {
                fullQuestionText.lineSequence().firstOrNull()?.take(60) ?: "Custom Question"
            } else {
                sample.question.take(60)
            }

            try {
                val existing = prefs.getString("PREF_SOLVE_HISTORY", "") ?: ""
                val cleanExp = finalExplanation.replace("\n", " ").replace("||", " ").take(120)
                val entryLine = "$nowMs||$finalLetter||$previewTitle||$cleanExp||${effectiveSubject.id}"
                val merged = (listOf(entryLine) + existing.split("\n").filter { it.isNotBlank() })
                    .take(25)
                    .joinToString("\n")
                prefs.edit()
                    .putInt("PREF_SOLVED_COUNT", newSolvedCount)
                    .putString("PREF_SOLVE_HISTORY", merged)
                    .apply()
            } catch (_: Exception) {
                prefs.edit().putInt("PREF_SOLVED_COUNT", newSolvedCount).apply()
            }

            prefs.edit().putBoolean("PREF_HISTORY_CLEARED_V1", false).apply()
            try {
                withContext(Dispatchers.IO) {
                    offlineHistoryRepo.recordCustomEntry(
                        questionText = previewTitle,
                        answerBadge = finalLetter,
                        explanation = finalExplanation,
                        subject = effectiveSubject.id,
                        sourceLabel = sourceBadge,
                        sourceMethod = _uiState.value.solverMethod.id
                    )
                }
            } catch (_: Exception) {}

            val newEntry = SolvedHistoryEntry(
                id = nowMs,
                questionPreview = previewTitle,
                answerLetter = finalLetter,
                sourceLabel = sourceBadge,
                timestamp = timeLabel,
                explanation = finalExplanation,
                subject = effectiveSubject.id,
                bookmarked = false
            )

            _uiState.update { cur ->
                cur.copy(
                    isPracticeScanning = false,
                    solvedCount = newSolvedCount,
                    practiceSolvedLetter = finalLetter,
                    practiceExplanation = finalExplanation,
                    practiceStatusNote = sourceBadge,
                    historyEntries = listOf(newEntry) + cur.historyEntries.take(24)
                )
            }
        }
    }

    // ─── Google Forms URL Extraction, Multi-Page, WebView Sync, Image OCR & Pinning ───

    fun updateGoogleFormUrl(url: String) {
        val cleanInput = url.replace('\r', ' ').replace('\n', ' ')
        _uiState.update {
            it.copy(
                googleFormUrlInput = cleanInput,
                googleFormError = null,
                googleFormsNeedsWebLogin = false
            )
        }
    }

    fun updateGoogleFormPastedText(text: String) {
        _uiState.update {
            it.copy(
                googleFormPastedTextInput = text,
                googleFormError = null,
                googleFormsNeedsWebLogin = false
            )
        }
    }

    fun toggleGoogleFormAppendMode() {
        _uiState.update {
            val next = !it.googleFormAppendMode
            showBannerMessage(
                if (next) "Multi-page combine ON: next page adds to your answers"
                else "Multi-page combine OFF: next scan starts fresh"
            )
            it.copy(googleFormAppendMode = next)
        }
    }

    fun clearGoogleFormResult() {
        val ctx = getApplication<Application>()
        GoogleFormsSessionStore.clearResult(ctx)
        if (OverlayService.isServiceRunning()) {
            try {
                ctx.startService(Intent(ctx, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_UNPIN_FORMS_KEY
                })
            } catch (_: Exception) {
            }
        }
        _uiState.update {
            it.copy(
                googleFormResult = null,
                googleFormError = null,
                googleFormsNeedsWebLogin = false,
                isFormsKeyPinnedOnIsland = false
            )
        }
    }

    fun refreshGoogleFormPinStatus() {
        val ctx = getApplication<Application>()
        val pinned = GoogleFormsNotificationHelper.isNotificationPinned(ctx)
        val latestStored = GoogleFormsSessionStore.getLastResult(ctx)
        val storedUrl = GoogleFormsSessionStore.getLastUrl(ctx)
        _uiState.update {
            val shouldAdoptStoredUrl = it.googleFormUrlInput.isBlank() ||
                (storedUrl.contains("docs.google.com/forms") && it.googleFormUrlInput.contains("forms.gle"))
            it.copy(
                isGoogleFormPinned = pinned,
                googleFormResult = latestStored ?: it.googleFormResult,
                googleFormUrlInput = if (shouldAdoptStoredUrl) storedUrl else it.googleFormUrlInput
            )
        }
    }

    private suspend fun applyExtractedAndSolve(
        extracted: GoogleFormExtractResult,
        appendMode: Boolean,
        telemetryFeature: String = TelemetryFeature.GOOGLE_FORMS
    ) {
        val startedAtMs = System.currentTimeMillis()
        val appContext = getApplication<Application>()
        val state = _uiState.value
        val existing = if (appendMode) state.googleFormResult else null
        val targetExtract = if (existing != null) {
            val freshQuestions = GoogleFormsSolverEngine.filterAndRenumberNewQuestions(
                existingItems = existing.items,
                incomingQuestions = extracted.questions
            )
            if (freshQuestions.isEmpty()) {
                throw IllegalStateException("All extracted questions are already in the current list.")
            }
            extracted.copy(
                formTitle = existing.formTitle.ifBlank { extracted.formTitle },
                questions = freshQuestions
            )
        } else {
            extracted
        }

        val solvedBatch = GoogleFormsSolverEngine.solveExtractedForm(
            context = appContext,
            extractResult = targetExtract,
            subjectMode = state.subjectMode,
            cloudModelName = state.googleFormsModel
        )
        val latencyMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
        val finalResult = GoogleFormsSolverEngine.combineSolvedResults(existing, solvedBatch)
        GoogleFormsSessionStore.saveResult(appContext, finalResult)

        val updatedCount = _uiState.value.solvedCount + solvedBatch.items.size
        prefs.edit().putInt("PREF_SOLVED_COUNT", updatedCount).apply()
        appSessionRequestsCount++

        viewModelScope.launch(Dispatchers.IO) {
            val backend = TelemetryBackend.classifyFromSourceLabel(solvedBatch.engineLabel)
            FirebaseTelemetryRepository.recordSolveRequest(
                context = appContext,
                feature = telemetryFeature,
                backend = backend,
                modelUsed = solvedBatch.engineLabel.ifBlank { "Cloudflare • Workers AI" },
                latencyMs = latencyMs,
                status = if (backend == TelemetryBackend.LOCAL_CACHE || backend == TelemetryBackend.SHARED_CACHE) {
                    TelemetryStatus.CACHE_HIT
                } else {
                    TelemetryStatus.SUCCESS
                },
                questionPreview = "${finalResult.formTitle} (${solvedBatch.items.size} Qs)",
                answerBadge = finalResult.compactNotificationOutput.take(40)
            )
        }

        if (GoogleFormsNotificationHelper.isNotificationPinned(appContext)) {
            GoogleFormsNotificationHelper.pinAnswersNotification(
                context = appContext,
                formTitle = finalResult.formTitle,
                compactOutput = finalResult.compactNotificationOutput,
                items = finalResult.items
            )
        }

        _uiState.update { cur ->
            cur.copy(
                isSolvingGoogleForm = false,
                googleFormResult = finalResult,
                googleFormError = null,
                googleFormsNeedsWebLogin = false,
                solvedCount = updatedCount,
                isGoogleFormPinned = GoogleFormsNotificationHelper.isNotificationPinned(appContext)
            )
        }
        showBannerMessage("Solved ${finalResult.items.size} Qs • ${finalResult.compactNotificationOutput}")
    }

    fun cancelGoogleFormSolve() {
        googleFormSolveJob?.cancel()
        googleFormSolveJob = null
        _uiState.update {
            it.copy(
                isSolvingGoogleForm = false,
                googleFormError = null,
                googleFormsNeedsWebLogin = false
            )
        }
        showBannerMessage("Cancelled Google Form solve")
    }

    fun solveGoogleFormFromUrl(onRequireGoogleSignIn: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isSolvingGoogleForm) return
        val rawInput = state.googleFormUrlInput.trim()
        if (rawInput.isEmpty()) {
            _uiState.update { it.copy(googleFormError = "Paste a Google Forms URL first.") }
            return
        }
        val cleanedUrl = GoogleFormsSolverEngine.extractGoogleFormUrl(rawInput)
        val targetUrl = cleanedUrl.ifBlank { rawInput }
        if (GoogleFormsSolverEngine.isValidGoogleFormUrl(targetUrl)) {
            GoogleFormsSessionStore.saveLastUrl(getApplication(), targetUrl)
        }

        _uiState.update {
            it.copy(
                googleFormUrlInput = targetUrl,
                isSolvingGoogleForm = true,
                googleFormError = null,
                googleFormsNeedsWebLogin = false
            )
        }

        googleFormSolveJob?.cancel()
        googleFormSolveJob = viewModelScope.launch {
            try {
                withTimeout(55_000L) {
                    val extracted = GoogleFormsSolverEngine.fetchAndExtractForm(cleanedUrl.ifBlank { rawInput })
                    applyExtractedAndSolve(extracted, appendMode = state.googleFormAppendMode)
                }
            } catch (_: TimeoutCancellationException) {
                _uiState.update { cur ->
                    cur.copy(
                        googleFormError = "Google Form solve timed out — try opening Form Browser, switching to Gemini 3.8 Flash, or pasting questions directly."
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (fse: FormSolveException) {
                _uiState.update { cur ->
                    cur.copy(
                        googleFormError = fse.message,
                        googleFormsNeedsWebLogin = fse.needsGeminiWebLogin
                    )
                }
            } catch (e: Exception) {
                _uiState.update { cur ->
                    cur.copy(
                        googleFormError = e.message ?: "Unable to extract questions from this Google Form URL."
                    )
                }
            } finally {
                googleFormSolveJob = null
                _uiState.update { cur ->
                    cur.copy(isSolvingGoogleForm = false)
                }
            }
        }
    }

    fun solveGoogleFormFromPastedText(onRequireGoogleSignIn: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isSolvingGoogleForm) return
        val rawText = state.googleFormPastedTextInput.trim()
        if (rawText.isEmpty()) {
            _uiState.update { it.copy(googleFormError = "Paste Google Form questions first.") }
            return
        }

        _uiState.update {
            it.copy(
                isSolvingGoogleForm = true,
                googleFormError = null,
                googleFormsNeedsWebLogin = false
            )
        }

        googleFormSolveJob?.cancel()
        googleFormSolveJob = viewModelScope.launch {
            try {
                withTimeout(55_000L) {
                    val extracted = GoogleFormsSolverEngine.parsePlainFormText(rawText)
                    applyExtractedAndSolve(extracted, appendMode = state.googleFormAppendMode)
                }
            } catch (_: TimeoutCancellationException) {
                _uiState.update { cur ->
                    cur.copy(
                        googleFormError = "Google Form solve timed out — please retry with fewer questions or switch model."
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (fse: FormSolveException) {
                _uiState.update { cur ->
                    cur.copy(
                        googleFormError = fse.message,
                        googleFormsNeedsWebLogin = fse.needsGeminiWebLogin
                    )
                }
            } catch (e: Exception) {
                _uiState.update { cur ->
                    cur.copy(
                        googleFormError = e.message ?: "Unable to parse pasted form questions."
                    )
                }
            } finally {
                googleFormSolveJob = null
                _uiState.update { cur ->
                    cur.copy(isSolvingGoogleForm = false)
                }
            }
        }
    }

    fun solveGoogleFormFromImageUri(imageUri: Uri, onRequireGoogleSignIn: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isSolvingGoogleForm) return
        _uiState.update {
            it.copy(
                isSolvingGoogleForm = true,
                googleFormError = null,
                googleFormsNeedsWebLogin = false
            )
        }

        googleFormSolveJob?.cancel()
        googleFormSolveJob = viewModelScope.launch {
            var bitmap: Bitmap? = null
            try {
                withTimeout(55_000L) {
                    val appContext = getApplication<Application>()
                    bitmap = appContext.contentResolver.openInputStream(imageUri)?.use { stream ->
                        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                        BitmapFactory.decodeStream(stream, null, opts)
                    } ?: throw IllegalStateException("Could not decode the selected quiz image.")

                    val ocrText = sharedOcrEngine.extractText(bitmap!!)
                    if (ocrText.isBlank()) {
                        throw IllegalStateException("No readable quiz text found in the selected image.")
                    }
                    val extracted = GoogleFormsSolverEngine.parsePlainFormText(
                        rawText = ocrText,
                        title = "Scanned Quiz Photo"
                    )
                    applyExtractedAndSolve(
                        extracted = extracted,
                        appendMode = state.googleFormAppendMode,
                        telemetryFeature = TelemetryFeature.PHOTO_SOLVER
                    )
                }
            } catch (_: TimeoutCancellationException) {
                _uiState.update { cur ->
                    cur.copy(
                        googleFormError = "Quiz image solve timed out — please retry with a clearer screenshot."
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (fse: FormSolveException) {
                _uiState.update { cur ->
                    cur.copy(
                        googleFormError = fse.message,
                        googleFormsNeedsWebLogin = fse.needsGeminiWebLogin
                    )
                }
            } catch (e: Exception) {
                _uiState.update { cur ->
                    cur.copy(
                        googleFormError = e.message ?: "Unable to scan questions from selected image."
                    )
                }
            } finally {
                try { bitmap?.recycle() } catch (_: Exception) {}
                googleFormSolveJob = null
                _uiState.update { cur ->
                    cur.copy(isSolvingGoogleForm = false)
                }
            }
        }
    }

    /**
     * Renders up to 4 pages of a selected PDF document locally via Android `PdfRenderer`,
     * extracts question text via on-device ML Kit OCR, and solves the batch on Cloudflare Workers AI PRIMARY.
     */
    fun solveGoogleFormFromPdfUri(pdfUri: Uri, onRequireGoogleSignIn: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isSolvingGoogleForm) return
        _uiState.update {
            it.copy(
                isSolvingGoogleForm = true,
                googleFormError = null,
                googleFormsNeedsWebLogin = false
            )
        }

        googleFormSolveJob?.cancel()
        googleFormSolveJob = viewModelScope.launch {
            try {
                withTimeout(55_000L) {
                    val appContext = getApplication<Application>()
                    val combinedPdfText = withContext(Dispatchers.IO) {
                        val sb = StringBuilder()
                        appContext.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                            PdfRenderer(pfd).use { renderer ->
                                val maxPages = renderer.pageCount.coerceAtMost(4)
                                for (pageIndex in 0 until maxPages) {
                                    renderer.openPage(pageIndex).use { page ->
                                        val scale = (1400f / page.width.coerceAtLeast(1)).coerceIn(1.2f, 2.2f)
                                        val bmpW = (page.width * scale).toInt().coerceIn(600, 1600)
                                        val bmpH = (page.height * scale).toInt().coerceIn(800, 2200)
                                        val pageBmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                        try {
                                            Canvas(pageBmp).drawColor(Color.WHITE)
                                            page.render(pageBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                            val pageText = sharedOcrEngine.extractText(pageBmp)
                                            if (pageText.isNotBlank()) {
                                                sb.appendLine(pageText.trim())
                                            }
                                        } finally {
                                            try { pageBmp.recycle() } catch (_: Exception) {}
                                        }
                                    }
                                }
                            }
                        } ?: throw IllegalStateException("Could not open the selected PDF file.")
                        sb.toString().trim()
                    }

                    if (combinedPdfText.isBlank()) {
                        throw IllegalStateException("No readable quiz text found in the selected PDF.")
                    }
                    val extracted = GoogleFormsSolverEngine.parsePlainFormText(
                        rawText = combinedPdfText,
                        title = "Scanned PDF Worksheet"
                    )
                    applyExtractedAndSolve(
                        extracted = extracted,
                        appendMode = state.googleFormAppendMode,
                        telemetryFeature = TelemetryFeature.PDF_SOLVER
                    )
                }
            } catch (_: TimeoutCancellationException) {
                _uiState.update { cur ->
                    cur.copy(
                        googleFormError = "PDF solve timed out — try a smaller PDF or fewer pages."
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (fse: FormSolveException) {
                _uiState.update { cur ->
                    cur.copy(
                        googleFormError = fse.message,
                        googleFormsNeedsWebLogin = fse.needsGeminiWebLogin
                    )
                }
            } catch (e: Exception) {
                _uiState.update { cur ->
                    cur.copy(
                        googleFormError = e.message ?: "Unable to read questions from selected PDF."
                    )
                }
            } finally {
                googleFormSolveJob = null
                _uiState.update { cur ->
                    cur.copy(isSolvingGoogleForm = false)
                }
            }
        }
    }

    fun reSolveSingleGoogleFormQuestion(questionNumber: Int, onRequireGoogleSignIn: () -> Unit = {}) {
        val state = _uiState.value
        val currentResult = state.googleFormResult ?: return
        val targetItem = currentResult.items.firstOrNull { it.number == questionNumber } ?: return
        if (state.reSolvingFormQuestionNumber != null) return

        _uiState.update { it.copy(reSolvingFormQuestionNumber = questionNumber) }
        viewModelScope.launch {
            try {
                val appContext = getApplication<Application>()
                val refreshed = GoogleFormsSolverEngine.reSolveSingleItem(
                    context = appContext,
                    item = targetItem,
                    subjectMode = state.subjectMode,
                    cloudModelName = state.googleFormsModel
                )
                val updatedItems = currentResult.items.map {
                    if (it.number == questionNumber) refreshed else it
                }
                val updatedResult = currentResult.copy(
                    items = updatedItems,
                    compactNotificationOutput = GoogleFormsSolverEngine.formatCompactAnswerOutput(updatedItems)
                )
                GoogleFormsSessionStore.saveResult(appContext, updatedResult)
                if (GoogleFormsNotificationHelper.isNotificationPinned(appContext)) {
                    GoogleFormsNotificationHelper.pinAnswersNotification(
                        context = appContext,
                        formTitle = updatedResult.formTitle,
                        compactOutput = updatedResult.compactNotificationOutput,
                        items = updatedResult.items
                    )
                }
                _uiState.update { cur ->
                    cur.copy(
                        reSolvingFormQuestionNumber = null,
                        googleFormResult = updatedResult,
                        googleFormsNeedsWebLogin = false
                    )
                }
                showBannerMessage("Re-solved Q$questionNumber -> ${refreshed.number}-${refreshed.answerLetter}")
            } catch (ce: CancellationException) {
                throw ce
            } catch (fse: FormSolveException) {
                _uiState.update {
                    it.copy(
                        reSolvingFormQuestionNumber = null,
                        googleFormError = fse.message,
                        googleFormsNeedsWebLogin = fse.needsGeminiWebLogin
                    )
                }
                showBannerMessage(fse.message ?: "Could not re-solve Q$questionNumber right now")
            } catch (_: Exception) {
                _uiState.update { it.copy(reSolvingFormQuestionNumber = null) }
                showBannerMessage("Could not re-solve Q$questionNumber right now")
            }
        }
    }

    fun pinFormsKeyToDynamicIsland() {
        val ctx = getApplication<Application>()
        val result = _uiState.value.googleFormResult ?: return
        if (!OverlayService.isServiceRunning()) {
            showBannerMessage("Start Solving on Home first to show key on Dynamic Island")
            return
        }
        val breakdown = result.items.joinToString("\n") { "${it.number}-${it.answerLetter}: ${it.shortAnswer}" }.take(240)
        try {
            ctx.startService(Intent(ctx, OverlayService::class.java).apply {
                action = OverlayService.ACTION_PIN_FORMS_KEY
                putExtra(OverlayService.EXTRA_FORMS_COMPACT_KEY, result.compactNotificationOutput)
                putExtra(OverlayService.EXTRA_FORMS_TITLE, result.formTitle)
                putExtra(OverlayService.EXTRA_FORMS_BREAKDOWN, breakdown)
            })
            _uiState.update { it.copy(isFormsKeyPinnedOnIsland = true) }
            showBannerMessage("Displayed ${result.compactNotificationOutput} on Dynamic Island")
        } catch (_: Exception) {
            showBannerMessage("Unable to update floating Dynamic Island")
        }
    }

    fun unpinFormsKeyFromDynamicIsland() {
        val ctx = getApplication<Application>()
        if (OverlayService.isServiceRunning()) {
            try {
                ctx.startService(Intent(ctx, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_UNPIN_FORMS_KEY
                })
            } catch (_: Exception) {
            }
        }
        _uiState.update { it.copy(isFormsKeyPinnedOnIsland = false) }
        showBannerMessage("Cleared Form Key from Dynamic Island")
    }

    fun saveGoogleFormItemsToExportHistory() {
        val result = _uiState.value.googleFormResult ?: return
        if (result.items.isEmpty()) return

        val baseTime = System.currentTimeMillis()
        val timeLabel = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(baseTime))
        val subjId = _uiState.value.subjectMode.id

        val newEntries = result.items.mapIndexed { idx, item ->
            val preview = "Q${item.number}: ${item.questionText}".take(92)
            val exp = buildString {
                if (item.shortAnswer.isNotBlank()) {
                    append(item.shortAnswer)
                    if (item.explanation.isNotBlank()) append(" — ")
                }
                append(item.explanation)
            }.take(160)
            SolvedHistoryEntry(
                id = baseTime + idx,
                questionPreview = preview,
                answerLetter = item.answerLetter,
                sourceLabel = "Google Forms • ${result.engineLabel}",
                timestamp = timeLabel,
                explanation = exp,
                subject = subjId,
                bookmarked = false
            )
        }

        try {
            val existing = prefs.getString("PREF_SOLVE_HISTORY", "") ?: ""
            val newLines = newEntries.map { entry ->
                val cleanExp = entry.explanation.replace("\n", " ").replace("||", " ").take(120)
                val cleanPreview = entry.questionPreview.replace("\n", " ").replace("||", " ").take(90)
                "${entry.id}||${entry.answerLetter}||$cleanPreview||$cleanExp||${entry.subject}"
            }
            val merged = (newLines + existing.split("\n").filter { it.isNotBlank() })
                .take(40)
                .joinToString("\n")
            prefs.edit().putString("PREF_SOLVE_HISTORY", merged).apply()
        } catch (_: Exception) {
        }

        _uiState.update { cur ->
            cur.copy(
                historyEntries = (newEntries + cur.historyEntries).take(40)
            )
        }
        showBannerMessage("Saved ${newEntries.size} solved Google Form items to Export queue")
    }

    // ─── New-User Onboarding Feature Guide & Custom Notification Studio ───────

    fun maybeStartNewUserGuideAfterEntry() {
        val st = _uiState.value
        if (st.userConsentAccepted && !st.onboardingGuideCompleted) {
            prefs.edit().putBoolean("PREF_ONBOARDING_GUIDE_SEEN", true).apply()
            _uiState.update { it.copy(onboardingGuideCompleted = true, showOnboardingGuide = false) }
        }
    }

    fun replayOnboardingGuide() {
        _uiState.update { it.copy(showOnboardingGuide = true) }
    }

    fun completeOnboardingGuide(sendWelcomeNotification: Boolean = true) {
        val ctx = getApplication<Application>()
        prefs.edit().putBoolean("PREF_ONBOARDING_GUIDE_SEEN", true).apply()
        _uiState.update {
            it.copy(
                onboardingGuideCompleted = true,
                showOnboardingGuide = false,
                hasNotificationPermission = PrismEngagementNotificationManager.hasNotificationPermission(ctx)
            )
        }
        if (sendWelcomeNotification) {
            val sent = PrismEngagementNotificationManager.sendWelcomeNotificationIfNew(ctx)
            if (sent) {
                showBannerMessage("Welcome to Solve Anything • Check your notification shade")
            }
        }
        PrismEngagementNotificationManager.recordAppOpened(ctx)
    }

    fun updateCustomNotifConfig(transform: (CustomPinnedNotifConfig) -> CustomPinnedNotifConfig) {
        val ctx = getApplication<Application>()
        val updated = transform(_uiState.value.customPinConfig)
        PrismEngagementNotificationManager.saveCustomPinConfig(ctx, updated)
        _uiState.update { it.copy(customPinConfig = updated) }
    }

    fun applyCustomNotifPreset(templateId: String) {
        val latestCompact = _uiState.value.googleFormResult?.compactNotificationOutput
            ?: "(1-A|2-B|3-D|4-A)"
        val preset = when (templateId) {
            CustomPinnedNotifConfig.TEMPLATE_STUDY_NOTE -> CustomPinnedNotifConfig(
                title = "Quick Revision Key",
                body = latestCompact,
                templateId = templateId,
                showCopy = true,
                showSolveNow = false,
                showStealth = false,
                stealthDisguise = false,
                isPinned = _uiState.value.customPinConfig.isPinned
            )
            CustomPinnedNotifConfig.TEMPLATE_QUICK_HUD -> CustomPinnedNotifConfig(
                title = "Red Prism • Instant Remote",
                body = "Tap Solve Now or Copy anytime while reading questions.",
                templateId = templateId,
                showCopy = false,
                showSolveNow = true,
                showStealth = true,
                stealthDisguise = false,
                isPinned = _uiState.value.customPinConfig.isPinned
            )
            CustomPinnedNotifConfig.TEMPLATE_BATTERY_STEALTH -> CustomPinnedNotifConfig(
                title = "Battery Saver • Active",
                body = latestCompact,
                templateId = templateId,
                showCopy = true,
                showSolveNow = false,
                showStealth = true,
                stealthDisguise = true,
                isPinned = _uiState.value.customPinConfig.isPinned
            )
            else -> CustomPinnedNotifConfig(
                title = _uiState.value.googleFormResult?.formTitle?.ifBlank { "Google Form Answers" }
                    ?: "Solve Anything • Forms Key",
                body = latestCompact,
                templateId = CustomPinnedNotifConfig.TEMPLATE_FORMS_KEY,
                showCopy = true,
                showSolveNow = true,
                showStealth = false,
                stealthDisguise = false,
                isPinned = _uiState.value.customPinConfig.isPinned
            )
        }
        val ctx = getApplication<Application>()
        PrismEngagementNotificationManager.saveCustomPinConfig(ctx, preset)
        _uiState.update { it.copy(customPinConfig = preset) }
    }

    fun importLatestSolvedIntoCustomNotif() {
        val st = _uiState.value
        val forms = st.googleFormResult
        val nextTitle: String
        val nextBody: String
        if (forms != null && forms.items.isNotEmpty()) {
            nextTitle = forms.formTitle.ifBlank { "Google Form Answers" }
            nextBody = forms.compactNotificationOutput
        } else if (st.historyEntries.isNotEmpty()) {
            nextTitle = "Recent Solved Key (${st.historyEntries.size})"
            nextBody = st.historyEntries.take(5).mapIndexed { idx, entry ->
                "${idx + 1}-${entry.answerLetter}"
            }.joinToString("|", prefix = "(", postfix = ")")
        } else {
            showBannerMessage("No recent solved answers found yet")
            return
        }
        updateCustomNotifConfig { it.copy(title = nextTitle, body = nextBody) }
        showBannerMessage("Loaded latest solved answers into Notification Editor")
    }

    fun pinCustomBarNotification(): Boolean {
        val ctx = getApplication<Application>()
        val cfg = _uiState.value.customPinConfig
        val ok = PrismEngagementNotificationManager.pinCustomNotification(ctx, cfg)
        _uiState.update {
            it.copy(
                customPinConfig = cfg.copy(isPinned = ok),
                hasNotificationPermission = PrismEngagementNotificationManager.hasNotificationPermission(ctx)
            )
        }
        showBannerMessage(
            if (ok) "Pinned custom notification to Android status bar"
            else "Grant notification permission to pin in status bar"
        )
        return ok
    }

    fun unpinCustomBarNotification() {
        val ctx = getApplication<Application>()
        PrismEngagementNotificationManager.unpinCustomNotification(ctx)
        _uiState.update {
            it.copy(customPinConfig = it.customPinConfig.copy(isPinned = false))
        }
        showBannerMessage("Removed custom pinned notification")
    }

    fun setAutoWeeklyEngagement(enabled: Boolean) {
        val ctx = getApplication<Application>()
        PrismEngagementNotificationManager.setAutoEngagementEnabled(ctx, enabled)
        _uiState.update { it.copy(autoWeeklyEngagementEnabled = enabled) }
        showBannerMessage(
            if (enabled) "Smart inactivity check-ins ON (2+/week random when away)"
            else "Inactivity check-in notifications paused"
        )
    }

    fun sendOneRandomTestEngagementNotification(): Boolean {
        val ctx = getApplication<Application>()
        val ok = PrismEngagementNotificationManager.sendRandomEngagementTestNotification(ctx)
        _uiState.update {
            it.copy(hasNotificationPermission = PrismEngagementNotificationManager.hasNotificationPermission(ctx))
        }
        showBannerMessage(
            if (ok) "Sent 1 test check-in notification — pull down your shade!"
            else "Enable notification permission first to receive test notification"
        )
        return ok
    }

    fun sendWelcomeNotificationManual(): Boolean {
        val ctx = getApplication<Application>()
        val ok = PrismEngagementNotificationManager.sendWelcomeNotificationNow(ctx)
        _uiState.update {
            it.copy(hasNotificationPermission = PrismEngagementNotificationManager.hasNotificationPermission(ctx))
        }
        showBannerMessage(
            if (ok) "Sent 'Welcome to Solve Anything' notification"
            else "Enable notification permission first"
        )
        return ok
    }

    // ─── Dynamic Offline /unslop Theme Studio Engine ────────────────────────

    fun selectLocalThemePreset(spec: RemoteThemeSpec, onSyncService: () -> Unit = {}) {
        if (spec.isNormalDefault) {
            revertToNormalDefaultTheme(onSyncService)
            return
        }
        val cur = _uiState.value
        val nextEffective = computeEffectiveSpec(
            preferLocalDefault = false,
            selectedPresetId = spec.themeId,
            animationOverride = cur.themeAnimationOverride,
            appearanceOverride = cur.themeColorAppearanceOverride
        )
        persistIslandCustomHexForTheme(nextEffective)
        _uiState.update {
            it.copy(
                preferLocalDefaultTheme = false,
                selectedThemePresetId = spec.themeId,
                effectiveThemeSpec = nextEffective
            )
        }
        themeRepository.savePreferences(
            preferLocalDefault = false,
            selectedPresetId = spec.themeId,
            animationIntensityOverride = cur.themeAnimationOverride,
            colorAppearanceOverride = cur.themeColorAppearanceOverride
        )
        launchDataStoreWrite {
            settingsDataStore.updateThemeSettings(
                preferLocalDefault = false,
                selectedPresetId = spec.themeId,
                animationIntensityOverride = cur.themeAnimationOverride,
                colorAppearanceOverride = cur.themeColorAppearanceOverride
            )
        }
        onSyncService()
        showBannerMessage("Applied offline theme: ${spec.name}")
    }

    fun revertToNormalDefaultTheme(onSyncService: () -> Unit = {}) {
        val nextEffective = computeEffectiveSpec(
            preferLocalDefault = true,
            selectedPresetId = NORMAL_DEFAULT_THEME.themeId,
            animationOverride = ThemeAnimationIntensity.AUTO,
            appearanceOverride = ThemeColorAppearance.AUTO
        )
        persistIslandCustomHexForTheme(nextEffective)
        _uiState.update {
            it.copy(
                preferLocalDefaultTheme = true,
                selectedThemePresetId = NORMAL_DEFAULT_THEME.themeId,
                themeAnimationOverride = ThemeAnimationIntensity.AUTO,
                themeColorAppearanceOverride = ThemeColorAppearance.AUTO,
                effectiveThemeSpec = nextEffective
            )
        }
        themeRepository.revertToNormalDefaultTheme()
        launchDataStoreWrite {
            settingsDataStore.updateThemeSettings(
                preferLocalDefault = true,
                selectedPresetId = NORMAL_DEFAULT_THEME.themeId,
                animationIntensityOverride = ThemeAnimationIntensity.AUTO,
                colorAppearanceOverride = ThemeColorAppearance.AUTO
            )
        }
        onSyncService()
        showBannerMessage("Restored Normal Red Prism Default Theme")
    }

    fun setThemeAnimationOverride(intensity: ThemeAnimationIntensity, onSyncService: () -> Unit = {}) {
        val cur = _uiState.value
        val nextEffective = computeEffectiveSpec(
            preferLocalDefault = cur.preferLocalDefaultTheme,
            selectedPresetId = cur.selectedThemePresetId,
            animationOverride = intensity,
            appearanceOverride = cur.themeColorAppearanceOverride
        )
        _uiState.update {
            it.copy(
                themeAnimationOverride = intensity,
                effectiveThemeSpec = nextEffective
            )
        }
        themeRepository.savePreferences(
            preferLocalDefault = cur.preferLocalDefaultTheme,
            selectedPresetId = cur.selectedThemePresetId,
            animationIntensityOverride = intensity,
            colorAppearanceOverride = cur.themeColorAppearanceOverride
        )
        launchDataStoreWrite {
            settingsDataStore.updateThemeSettings(
                preferLocalDefault = cur.preferLocalDefaultTheme,
                selectedPresetId = cur.selectedThemePresetId,
                animationIntensityOverride = intensity,
                colorAppearanceOverride = cur.themeColorAppearanceOverride
            )
        }
        onSyncService()
    }

    fun setThemeColorAppearanceOverride(mode: ThemeColorAppearance, onSyncService: () -> Unit = {}) {
        val cur = _uiState.value
        val nextEffective = computeEffectiveSpec(
            preferLocalDefault = cur.preferLocalDefaultTheme,
            selectedPresetId = cur.selectedThemePresetId,
            animationOverride = cur.themeAnimationOverride,
            appearanceOverride = mode
        )
        persistIslandCustomHexForTheme(nextEffective)
        _uiState.update {
            it.copy(
                themeColorAppearanceOverride = mode,
                effectiveThemeSpec = nextEffective
            )
        }
        themeRepository.savePreferences(
            preferLocalDefault = cur.preferLocalDefaultTheme,
            selectedPresetId = cur.selectedThemePresetId,
            animationIntensityOverride = cur.themeAnimationOverride,
            colorAppearanceOverride = mode
        )
        launchDataStoreWrite {
            settingsDataStore.updateThemeSettings(
                preferLocalDefault = cur.preferLocalDefaultTheme,
                selectedPresetId = cur.selectedThemePresetId,
                animationIntensityOverride = cur.themeAnimationOverride,
                colorAppearanceOverride = mode
            )
        }
        onSyncService()
    }
}

