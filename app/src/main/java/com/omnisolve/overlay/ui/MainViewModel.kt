package com.omnisolve.overlay.ui

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.api.GeminiVisionClient
import com.omnisolve.overlay.service.AutoClickAccessibilityService
import com.omnisolve.overlay.ui.components.AppTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PracticeSample(
    val category: String,
    val question: String,
    val options: List<String>,
    val correctLetter: String,
    val explanation: String
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
    val hasSecretApiKey: Boolean = false,

    // Preferences
    val extractionEngine: String = MainActivity.ENGINE_NATIVE,
    val nativeFallbackOcr: Boolean = true,
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
    val geminiModel: String = GeminiVisionClient.DEFAULT_MODEL,

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
        sourceLabel = "Biology Practice",
        timestamp = "Just now"
    ),
    SolvedHistoryEntry(
        id = 2L,
        questionPreview = "Search time in a balanced binary search tree?",
        answerLetter = "B",
        sourceLabel = "CS Practice",
        timestamp = "Earlier"
    )
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
    private val geminiClient = GeminiVisionClient()

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
            explanation = "Mitochondria are the powerhouses of the cell, converting nutrients into ATP through cellular respiration."
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
            explanation = "Each comparison halves the remaining search space in a balanced tree, giving O(log n) worst-case lookup."
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
            explanation = "Oxygen's two lone electron pairs push the two hydrogen atoms closer together into a bent ~104.5° shape."
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
            explanation = "Newton's Third Law of Motion states that forces between two interacting objects act in equal and opposite pairs."
        )
    )

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private fun loadInitialState(): MainUiState {
        // Ensure White Glass light mode is saved as default for OverlayService consistency
        prefs.edit().putString(MainActivity.PREF_KEY_THEME_MODE, MainActivity.THEME_LIGHT).apply()

        val customKey = prefs.getString(MainActivity.PREF_KEY_GEMINI_API_KEY, "") ?: ""
        val secretAvailable = GeminiVisionClient.resolveApiKey(null) != null

        return MainUiState(
            extractionEngine = prefs.getString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, MainActivity.ENGINE_NATIVE)
                ?: MainActivity.ENGINE_NATIVE,
            nativeFallbackOcr = prefs.getBoolean(MainActivity.PREF_KEY_NATIVE_FALLBACK_OCR, true),
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
            customApiKey = customKey,
            hasSecretApiKey = secretAvailable,
            geminiModel = prefs.getString(MainActivity.PREF_KEY_GEMINI_MODEL, GeminiVisionClient.DEFAULT_MODEL)
                ?: GeminiVisionClient.DEFAULT_MODEL,
            solvedCount = prefs.getInt("PREF_SOLVED_COUNT", 12)
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
        val secretAvailable = GeminiVisionClient.resolveApiKey(null) != null

        _uiState.update {
            it.copy(
                hasOverlayPermission = canDraw,
                isAccessibilityEnabled = a11yRunning,
                isGoogleWebLoggedIn = hasGoogleSession,
                hasSecretApiKey = secretAvailable
            )
        }
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
                    .putString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, MainActivity.ENGINE_NATIVE)
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
                        extractionEngine = MainActivity.ENGINE_NATIVE
                    )
                }
                showBannerMessage("Study Mode active: balanced speed and clear top pill")
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
                    .putString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, MainActivity.ENGINE_NATIVE)
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
                        extractionEngine = MainActivity.ENGINE_NATIVE
                    )
                }
                showBannerMessage("Fast Mode active: 1-second checks for rapid review")
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
                    .putString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, MainActivity.ENGINE_NATIVE)
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
                        extractionEngine = MainActivity.ENGINE_NATIVE
                    )
                }
                showBannerMessage("Subtle Mode active: battery indicator look & gentle buzz")
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

    fun saveCustomApiKey(keyInput: String) {
        val trimmed = keyInput.trim()
        prefs.edit().putString(MainActivity.PREF_KEY_GEMINI_API_KEY, trimmed).apply()
        _uiState.update { it.copy(customApiKey = trimmed) }
        showBannerMessage(if (trimmed.isNotEmpty()) "Gemini API key saved" else "Custom API key cleared")
    }

    fun selectGeminiModel(modelId: String) {
        prefs.edit().putString(MainActivity.PREF_KEY_GEMINI_MODEL, modelId).apply()
        _uiState.update { it.copy(geminiModel = modelId) }
    }

    fun clearHistory() {
        _uiState.update { it.copy(historyEntries = emptyList()) }
        showBannerMessage("Recent solved history cleared")
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

        _uiState.update {
            it.copy(
                isPracticeScanning = true,
                practiceSolvedLetter = null,
                practiceExplanation = null,
                practiceStatusNote = null
            )
        }

        viewModelScope.launch {
            val resolvedKey = GeminiVisionClient.resolveApiKey(state.customApiKey)
            var finalLetter = sample.correctLetter
            var finalExplanation = sample.explanation
            var sourceBadge = "Instant Study Walkthrough"

            if (resolvedKey != null) {
                try {
                    val result = geminiClient.analyzeMCQText(
                        extractedText = fullQuestionText,
                        apiKey = resolvedKey,
                        modelName = state.geminiModel
                    )
                    finalLetter = result.correctChoice
                    finalExplanation = if (isCustom) {
                        "Solved live using ${state.geminiModel}. Option $finalLetter best matches your question."
                    } else {
                        sample.explanation
                    }
                    sourceBadge = "Solved Live via Gemini AI"
                } catch (e: Exception) {
                    if (isCustom) {
                        _uiState.update {
                            it.copy(
                                isPracticeScanning = false,
                                practiceStatusNote = "Could not reach Gemini AI (${e.message ?: "check API key in AI & History"})."
                            )
                        }
                        return@launch
                    }
                    delay(420)
                }
            } else {
                if (isCustom) {
                    _uiState.update {
                        it.copy(
                            isPracticeScanning = false,
                            practiceStatusNote = "Add a Gemini API key in AI & History (or AI Studio Secrets) to solve custom typed questions."
                        )
                    }
                    return@launch
                }
                delay(480)
            }

            val newSolvedCount = _uiState.value.solvedCount + 1
            prefs.edit().putInt("PREF_SOLVED_COUNT", newSolvedCount).apply()

            val timeLabel = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val previewTitle = if (isCustom) {
                fullQuestionText.lineSequence().firstOrNull()?.take(60) ?: "Custom Practice Question"
            } else {
                sample.question.take(60)
            }

            val newEntry = SolvedHistoryEntry(
                id = System.currentTimeMillis(),
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
