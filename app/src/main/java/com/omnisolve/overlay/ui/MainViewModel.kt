package com.omnisolve.overlay.ui

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.api.GeminiVisionClient
import com.omnisolve.overlay.api.MultiProviderApiSolver
import com.omnisolve.overlay.api.PrismAiSolverEngine
import com.omnisolve.overlay.data.SolvedQuestionRecord
import com.omnisolve.overlay.data.SolvedQuestionRepository
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.service.AutoClickAccessibilityService
import com.omnisolve.overlay.service.OverlayService
import com.omnisolve.overlay.ui.components.AppTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val timestamp: String
)

data class MainUiState(
    val selectedTab: AppTab = AppTab.Home,
    val isOverlayRunning: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isCaptureAuthorized: Boolean = false,
    val isGoogleWebLoggedIn: Boolean = false,
    val hasSecretApiKey: Boolean = true,

    // Google Cloud Account State
    val firebaseUserId: String? = null,
    val firebaseUserName: String? = null,
    val firebaseUserEmail: String? = null,
    val isSigningInGoogle: Boolean = false,

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
    val activePreset: String = "STUDY",
    val hudStyle: String = MainActivity.HUD_STYLE_ISLAND,
    val islandOpacityIndex: Int = 0,
    val displayMode: String = MainActivity.MODE_STEALTH,
    val autoClickEnabled: Boolean = false,
    val afkAutoAdvanceEnabled: Boolean = false,
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

    // Practice & Metrics
    val solvedCount: Int = 12,
    val currentSampleIndex: Int = 0,
    val customPracticeInput: String = "",
    val isPracticeScanning: Boolean = false,
    val practiceSolvedLetter: String? = null,
    val practiceExplanation: String? = null,
    val practiceStatusNote: String? = null,
    val historyEntries: List<SolvedHistoryEntry> = defaultHistoryEntries(),
    val statusBannerMessage: String? = null
)

private fun defaultHistoryEntries(): List<SolvedHistoryEntry> = listOf(
    SolvedHistoryEntry(
        id = 1L,
        questionPreview = "Which cell structure produces most ATP energy?",
        answerLetter = "B",
        sourceLabel = "Biology • Cloud Gemini",
        timestamp = "Just now"
    ),
    SolvedHistoryEntry(
        id = 2L,
        questionPreview = "Search time in a balanced binary search tree?",
        answerLetter = "B",
        sourceLabel = "CS • Cloud Gemini",
        timestamp = "Earlier"
    )
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
    private val geminiClient = GeminiVisionClient()
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

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
        attachFirebaseAuthObserver()
    }

    private fun attachFirebaseAuthObserver() {
        val ctx = getApplication<Application>()
        if (FirebaseApp.getApps(ctx).isEmpty()) return
        try {
            val auth = Firebase.auth
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                _uiState.update {
                    it.copy(
                        firebaseUserId = user?.uid,
                        firebaseUserName = user?.displayName,
                        firebaseUserEmail = user?.email
                    )
                }
            }
            authStateListener = listener
            auth.addAuthStateListener(listener)
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        val ctx = getApplication<Application>()
        if (FirebaseApp.getApps(ctx).isNotEmpty()) {
            try {
                authStateListener?.let { Firebase.auth.removeAuthStateListener(it) }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadPersistedOverlayHistory(): List<SolvedHistoryEntry> {
        val raw = prefs.getString("PREF_SOLVE_HISTORY", "")?.trim().orEmpty()
        if (raw.isBlank()) return defaultHistoryEntries()
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val parsed = raw.lines().mapNotNull { line ->
            val parts = line.split("||")
            if (parts.size >= 3) {
                val ts = parts[0].toLongOrNull() ?: System.currentTimeMillis()
                val badge = parts[1].trim().ifBlank { "?" }
                val snippet = parts[2].trim().ifBlank { "Screen Question" }
                SolvedHistoryEntry(
                    id = ts,
                    questionPreview = snippet,
                    answerLetter = badge,
                    sourceLabel = "Prism Island",
                    timestamp = fmt.format(Date(ts))
                )
            } else {
                null
            }
        }
        return parsed.ifEmpty { defaultHistoryEntries() }
    }

    private fun loadInitialState(): MainUiState {
        prefs.edit().putString(MainActivity.PREF_KEY_THEME_MODE, MainActivity.THEME_LIGHT).apply()

        val rawSavedModel = prefs.getString(MainActivity.PREF_KEY_GEMINI_MODEL, PrismAiSolverEngine.DEFAULT_CLOUD_MODEL)
            ?: PrismAiSolverEngine.DEFAULT_CLOUD_MODEL
        val normalizedModel = GeminiVisionClient.sanitizeModel(rawSavedModel)
        if (normalizedModel != rawSavedModel) {
            prefs.edit().putString(MainActivity.PREF_KEY_GEMINI_MODEL, normalizedModel).apply()
        }

        val savedSubject = SubjectMode.fromId(
            prefs.getString(MainActivity.PREF_KEY_SUBJECT_MODE, SubjectMode.GENERAL.id)
        )
        val dualVerify = prefs.getBoolean(MainActivity.PREF_KEY_DUAL_VERIFY, false)
        val translate = prefs.getBoolean(MainActivity.PREF_KEY_TRANSLATE_ENGLISH, false)

        val savedMethod = SolverMethod.fromId(
            prefs.getString(MainActivity.PREF_KEY_SOLVER_METHOD, SolverMethod.BACKGROUND_GEMINI.id)
        )
        val autoFailover = prefs.getBoolean(MainActivity.PREF_KEY_AUTO_FAILOVER, true)
        val nvidiaKey = prefs.getString(MainActivity.PREF_KEY_NVIDIA_API_KEY, "")?.trim().orEmpty()
        val openRouterKey = prefs.getString(MainActivity.PREF_KEY_OPENROUTER_API_KEY, "")?.trim().orEmpty()
        val geminiKey = prefs.getString(MainActivity.PREF_KEY_GEMINI_API_KEY, "")?.trim().orEmpty()
        val openAiKey = prefs.getString(MainActivity.PREF_KEY_OPENAI_API_KEY, "")?.trim().orEmpty()
        val configuredKeys = MultiProviderApiSolver.getConfiguredProviders(getApplication()).size

        val currentUser = try {
            if (FirebaseApp.getApps(getApplication()).isNotEmpty()) Firebase.auth.currentUser else null
        } catch (_: Exception) {
            null
        }

        return MainUiState(
            isOverlayRunning = OverlayService.isServiceRunning(),
            isCaptureAuthorized = OverlayService.hasScreenCapture(),
            firebaseUserId = currentUser?.uid,
            firebaseUserName = currentUser?.displayName,
            firebaseUserEmail = currentUser?.email,
            extractionEngine = prefs.getString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, MainActivity.ENGINE_OCR)
                ?: MainActivity.ENGINE_OCR,
            nativeFallbackOcr = prefs.getBoolean(MainActivity.PREF_KEY_NATIVE_FALLBACK_OCR, true),
            solverMethod = savedMethod,
            autoFailoverEnabled = autoFailover,
            nvidiaApiKey = nvidiaKey,
            openRouterApiKey = openRouterKey,
            geminiApiKey = geminiKey,
            openAiApiKey = openAiKey,
            configuredKeyCount = configuredKeys,
            hudStyle = prefs.getString(MainActivity.PREF_KEY_HUD_STYLE, MainActivity.HUD_STYLE_ISLAND)
                ?: MainActivity.HUD_STYLE_ISLAND,
            islandOpacityIndex = prefs.getInt("PREF_ISLAND_OPACITY_INDEX", 0).coerceIn(0, 3),
            displayMode = prefs.getString(MainActivity.PREF_KEY_DISPLAY_MODE, MainActivity.MODE_STEALTH)
                ?: MainActivity.MODE_STEALTH,
            autoClickEnabled = prefs.getBoolean(MainActivity.PREF_KEY_AUTO_CLICK, false),
            afkAutoAdvanceEnabled = prefs.getBoolean(MainActivity.PREF_KEY_AFK_MODE, false),
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
            solvedCount = prefs.getInt("PREF_SOLVED_COUNT", 12),
            historyEntries = loadPersistedOverlayHistory()
        )
    }

    fun refreshSystemStatuses() {
        val ctx = getApplication<Application>()
        val canDraw = Settings.canDrawOverlays(ctx)
        val a11yRunning = AutoClickAccessibilityService.isServiceRunning()
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
        val overlayRunning = OverlayService.isServiceRunning()
        val captureActive = OverlayService.hasScreenCapture()
        _uiState.update {
            it.copy(
                isOverlayRunning = overlayRunning,
                isCaptureAuthorized = if (overlayRunning) captureActive else false,
                hasOverlayPermission = canDraw,
                isAccessibilityEnabled = a11yRunning,
                isGoogleWebLoggedIn = hasGoogleSession,
                firebaseUserId = currentUser?.uid,
                firebaseUserName = currentUser?.displayName,
                firebaseUserEmail = currentUser?.email,
                configuredKeyCount = configuredKeys,
                hasSecretApiKey = true,
                solvedCount = prefs.getInt("PREF_SOLVED_COUNT", it.solvedCount),
                historyEntries = loadPersistedOverlayHistory()
            )
        }
    }

    fun setGoogleSignInLoading(loading: Boolean) {
        _uiState.update { it.copy(isSigningInGoogle = loading) }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(selectedTab = tab) }
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

    fun selectSolverMethod(method: SolverMethod, onSyncService: () -> Unit) {
        val targetDisplayMode = if (method == SolverMethod.OVERLAPPING_GOOGLE) {
            MainActivity.MODE_WINDOW
        } else {
            MainActivity.MODE_STEALTH
        }
        prefs.edit()
            .putString(MainActivity.PREF_KEY_SOLVER_METHOD, method.id)
            .putString(MainActivity.PREF_KEY_DISPLAY_MODE, targetDisplayMode)
            .apply()
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

        prefs.edit()
            .putString(MainActivity.PREF_KEY_NVIDIA_API_KEY, cleanNvidia)
            .putString(MainActivity.PREF_KEY_OPENROUTER_API_KEY, cleanOpenRouter)
            .putString(MainActivity.PREF_KEY_GEMINI_API_KEY, cleanGemini)
            .putString(MainActivity.PREF_KEY_OPENAI_API_KEY, cleanOpenAi)
            .apply()

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
            if (activeCount > 0) "Saved $activeCount local API key(s) on device"
            else "No API keys saved locally"
        )
        onSyncService()
    }

    fun clearLocalApiKeys(onSyncService: () -> Unit) {
        prefs.edit()
            .remove(MainActivity.PREF_KEY_NVIDIA_API_KEY)
            .remove(MainActivity.PREF_KEY_OPENROUTER_API_KEY)
            .remove(MainActivity.PREF_KEY_GEMINI_API_KEY)
            .remove(MainActivity.PREF_KEY_OPENAI_API_KEY)
            .apply()

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
                showBannerMessage("Prism Mode active: Screenshot OCR & crystal top pill")
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
                showBannerMessage("Turbo Mode active: rapid Screenshot OCR checks")
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
        prefs.edit().putInt("PREF_ISLAND_OPACITY_INDEX", safe).apply()
        _uiState.update { it.copy(islandOpacityIndex = safe) }
        onSyncService()
    }

    fun setDisplayMode(mode: String, onSyncService: () -> Unit) {
        prefs.edit().putString(MainActivity.PREF_KEY_DISPLAY_MODE, mode).apply()
        _uiState.update { it.copy(displayMode = mode) }
        onSyncService()
    }

    fun setAutoClick(enabled: Boolean, onRequireAccessibility: () -> Unit, onSyncService: () -> Unit) {
        if (enabled && !AutoClickAccessibilityService.isServiceRunning()) {
            onRequireAccessibility()
            return
        }
        prefs.edit().putBoolean(MainActivity.PREF_KEY_AUTO_CLICK, enabled).apply()
        _uiState.update { it.copy(autoClickEnabled = enabled) }
        onSyncService()
    }

    fun setAfkAutoAdvance(enabled: Boolean, onRequireAccessibility: () -> Unit, onSyncService: () -> Unit) {
        if (enabled && !AutoClickAccessibilityService.isServiceRunning()) {
            onRequireAccessibility()
            return
        }
        prefs.edit().putBoolean(MainActivity.PREF_KEY_AFK_MODE, enabled).apply()
        _uiState.update { it.copy(afkAutoAdvanceEnabled = enabled) }
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
        showBannerMessage("Switched Cloud AI engine to $safeModel")
    }

    fun selectSubjectMode(mode: SubjectMode) {
        prefs.edit().putString(MainActivity.PREF_KEY_SUBJECT_MODE, mode.id).apply()
        _uiState.update { it.copy(subjectMode = mode) }
        showBannerMessage("Subject focus set to ${mode.label}")
    }

    fun setDualVerifyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_DUAL_VERIFY, enabled).apply()
        _uiState.update { it.copy(dualVerifyEnabled = enabled) }
    }

    fun setTranslateToEnglish(enabled: Boolean) {
        prefs.edit().putBoolean(MainActivity.PREF_KEY_TRANSLATE_ENGLISH, enabled).apply()
        _uiState.update { it.copy(translateToEnglish = enabled) }
    }

    fun clearHistory() {
        prefs.edit().remove("PREF_SOLVE_HISTORY").apply()
        _uiState.update { it.copy(historyEntries = emptyList()) }
        showBannerMessage("Local recent history cleared")
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
                val aiAnswer = when (state.solverMethod) {
                    SolverMethod.API_KEYS -> {
                        if (MultiProviderApiSolver.hasAnyConfiguredKey(appContext)) {
                            MultiProviderApiSolver.solveWithFailover(
                                context = appContext,
                                extractedText = fullQuestionText,
                                subjectMode = effectiveSubject
                            )
                        } else if (state.autoFailoverEnabled) {
                            geminiClient.analyzeMCQText(
                                extractedText = fullQuestionText,
                                modelName = state.geminiModel,
                                subjectMode = effectiveSubject,
                                dualVerify = state.dualVerifyEnabled,
                                translateToEnglish = state.translateToEnglish
                            )
                        } else {
                            throw IllegalStateException("Enter at least one local API key (NVIDIA, OpenRouter, Gemini, or OpenAI) in Settings.")
                        }
                    }

                    SolverMethod.FIREBASE_SERVER,
                    SolverMethod.BACKGROUND_GEMINI,
                    SolverMethod.OVERLAPPING_GOOGLE -> {
                        try {
                            geminiClient.analyzeMCQText(
                                extractedText = fullQuestionText,
                                modelName = state.geminiModel,
                                subjectMode = effectiveSubject,
                                dualVerify = state.dualVerifyEnabled,
                                translateToEnglish = state.translateToEnglish
                            )
                        } catch (cloudEx: Exception) {
                            if (state.autoFailoverEnabled && MultiProviderApiSolver.hasAnyConfiguredKey(appContext)) {
                                MultiProviderApiSolver.solveWithFailover(
                                    context = appContext,
                                    extractedText = fullQuestionText,
                                    subjectMode = effectiveSubject
                                )
                            } else {
                                throw cloudEx
                            }
                        }
                    }
                }
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
                if (ce is TimeoutCancellationException) {
                    if (isCustom) {
                        _uiState.update {
                            it.copy(
                                isPracticeScanning = false,
                                practiceStatusNote = "AI request timed out. Check your connection or switch AI method."
                            )
                        }
                        return@launch
                    }
                } else {
                    throw ce
                }
            } catch (e: Exception) {
                if (isCustom) {
                    _uiState.update {
                        it.copy(
                            isPracticeScanning = false,
                            practiceStatusNote = e.localizedMessage ?: "Could not solve right now. Try adding a free NVIDIA/OpenRouter key."
                        )
                    }
                    return@launch
                }
                sourceBadge = "Offline Walkthrough"
                delay(320)
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
                val entryLine = "$nowMs||$finalLetter||$previewTitle"
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

            val newEntry = SolvedHistoryEntry(
                id = nowMs,
                questionPreview = previewTitle,
                answerLetter = finalLetter,
                sourceLabel = sourceBadge,
                timestamp = timeLabel
            )

            _uiState.update { cur ->
                cur.copy(
                    isPracticeScanning = false,
                    solvedCount = newSolvedCount,
                    practiceSolvedLetter = finalLetter,
                    practiceExplanation = finalExplanation,
                    practiceStatusNote = sourceBadge,
                    historyEntries = listOf(newEntry) + cur.historyEntries.take(14)
                )
            }
        }
    }
}
