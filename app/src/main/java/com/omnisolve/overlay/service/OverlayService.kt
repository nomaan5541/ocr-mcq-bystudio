package com.omnisolve.overlay.service

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.content.ComponentCallbacks2
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.R
import com.omnisolve.overlay.api.GeminiVisionClient
import com.omnisolve.overlay.api.MultiProviderApiSolver
import com.omnisolve.overlay.api.PrismAiSolverEngine
import com.omnisolve.overlay.capture.OcrEngine
import com.omnisolve.overlay.capture.ScreenCaptureManager
import com.omnisolve.overlay.data.SolvedQuestionRecord
import com.omnisolve.overlay.data.SolvedQuestionRepository
import com.omnisolve.overlay.firebase.configureAppCheck
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.model.SubjectMode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import java.util.regex.Pattern
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val CHANNEL_ID = "OmniSolveOverlayChannel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_DISPLAY_MODE = "EXTRA_DISPLAY_MODE"
        const val EXTRA_AUTO_CLICK = "EXTRA_AUTO_CLICK"
        const val EXTRA_AFK_MODE = "EXTRA_AFK_MODE"
        const val EXTRA_SCAN_INTERVAL_SEC = "EXTRA_SCAN_INTERVAL_SEC"
        const val EXTRA_NEXT_DELAY_MS = "EXTRA_NEXT_DELAY_MS"
        const val EXTRA_INSTANT_TRIGGER = "EXTRA_INSTANT_TRIGGER"
        const val EXTRA_EXTRACTION_ENGINE = "EXTRA_EXTRACTION_ENGINE"
        const val EXTRA_SOLVER_METHOD = "EXTRA_SOLVER_METHOD"
        const val EXTRA_HUD_STYLE = "EXTRA_HUD_STYLE"
        const val EXTRA_CAMOUFLAGE = "EXTRA_CAMOUFLAGE"
        const val EXTRA_HAPTIC = "EXTRA_HAPTIC"
        const val EXTRA_ANTI_CHEAT = "EXTRA_ANTI_CHEAT"
        const val EXTRA_THEME_MODE = "EXTRA_THEME_MODE"
        const val EXTRA_ISLAND_OPACITY_INDEX = "EXTRA_ISLAND_OPACITY_INDEX"
        const val ACTION_UPDATE_CONFIG = "com.omnisolve.overlay.ACTION_UPDATE_CONFIG"
        const val ACTION_COMPANION_SYNC = "com.omnisolve.overlay.ACTION_COMPANION_SYNC"
        const val EXTRA_COMPANION_HOST = "EXTRA_COMPANION_HOST"
        const val EXTRA_COMPANION_PORT = "EXTRA_COMPANION_PORT"
        const val EXTRA_COMPANION_SYNC_ENABLED = "EXTRA_COMPANION_SYNC_ENABLED"

        private const val GEMINI_WEB_URL = "https://gemini.google.com/app"
        private const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        @Volatile
        private var instance: OverlayService? = null

        fun isServiceRunning(): Boolean = instance != null

        fun hasScreenCapture(): Boolean = instance?.screenCaptureManager != null

        /**
         * Triggered from hardware volume buttons or external shortcuts.
         */
        fun triggerSolveFromHardware(): Boolean {
            val svc = instance ?: return false
            svc.mainHandler.post {
                if (svc.isAutoClickEnabled) {
                    if (!svc.isScanning) {
                        svc.triggerSolveFlow(autoClickMode = true)
                    }
                } else {
                    svc.onUserTapSolve()
                }
            }
            return true
        }

        /**
         * Converts an answer badge (A, B, C, D, E, F, multi-select "A, C", True/False, or numeric)
         * into Charging Camouflage numbers (91% = A, 92% = B, 93% = C, 94% = D, 95% = E, 96% = F).
         * Returns Pair(displayBadge, isResolvedAnswer). Idle/error badges ("?", "...", "RETRY", "ERR")
         * safely return the real device battery percentage so "?" or "ERR" never leaks on screen.
         */
        internal fun formatChargingCamouflage(rawBadge: String?, fallbackBatteryPct: Int): Pair<String, Boolean> {
            val safeFallback = "${fallbackBatteryPct.coerceIn(1, 100)}%"
            val cleaned = rawBadge
                ?.trim()
                ?.removePrefix("(")
                ?.removeSuffix(")")
                ?.trim()
                .orEmpty()

            if (cleaned.isEmpty()) return safeFallback to false

            val upper = cleaned.uppercase()
            if (upper in setOf("?", "...", "SCAN", "ERR", "RETRY", "LOGIN", "KEY", "EMPTY", "WAIT")) {
                return safeFallback to false
            }

            if (upper == "TRUE") return "91%" to true
            if (upper == "FALSE") return "92%" to true

            // Single letter A-F -> 91%-96%
            if (upper.length == 1 && upper[0] in 'A'..'F') {
                val code = 91 + (upper[0] - 'A')
                return "${code}%" to true
            }

            // Multi-select e.g. "A, C" or "B, D" -> "91/93%"
            val parts = upper.split(Regex("[,/&\\s]+")).filter { it.isNotBlank() }
            if (parts.size >= 2 && parts.all { it.length == 1 && it[0] in 'A'..'F' }) {
                val joined = parts.joinToString("/") { part -> (91 + (part[0] - 'A')).toString() }
                return "${joined}%" to true
            }

            // Numeric answer e.g. "42" or "42.5"
            if (cleaned.matches(Regex("^[+-]?\\d+(?:\\.\\d+)?%?$"))) {
                val withPct = if (cleaned.endsWith("%")) cleaned else "${cleaned}%"
                return withPct to true
            }

            return safeFallback to false
        }
    }

    private lateinit var windowManager: WindowManager

    // HUD & Solver Configuration
    private var extractionEngine = MainActivity.ENGINE_OCR
    private var solverMethod: SolverMethod = SolverMethod.BACKGROUND_GEMINI
    private var hudStyle = MainActivity.HUD_STYLE_ISLAND
    private var isCamouflageEnabled = false
    private var isHapticFeedbackEnabled = true
    private var isAntiCheatEnabled = true
    private var isAfkAutoAdvanceEnabled = false
    private var scanIntervalMs = 3000L
    private var nextDelayMs = 1500L
    private var isInstantTriggerEnabled = true
    private var themeMode = MainActivity.THEME_DARK

    // PC Companion Live Pocket Sync
    private var isCompanionSyncActive = false
    private var companionSyncJob: Job? = null
    private val companionHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    // AEI Bot Question Signature Tracking (avoids duplicate solves on same question)
    @Volatile
    private var lastSolvedQuestionSignature: String = ""

    private fun computeQuestionSignature(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(70)
    }

    // Dynamic Island specific view references
    private var tvIslandTitle: TextView? = null
    private var ivIslandIcon: ImageView? = null
    private var btnIslandOpacity: TextView? = null
    private var llIslandExplanationDrawer: View? = null
    private var tvIslandMeta: TextView? = null
    private var tvIslandExplanation: TextView? = null

    // Dynamic Island Visibility / Opacity Presets: 100%, 90%, 50%, 25%
    private var currentIslandOpacityIndex = 0
    private val islandOpacityLevels = floatArrayOf(1.0f, 0.90f, 0.50f, 0.25f)
    private val islandOpacityLabels = arrayOf("100%", "90%", "50%", "25%")

    private fun applyIslandOpacity(index: Int, showToast: Boolean = true) {
        currentIslandOpacityIndex = (index + islandOpacityLevels.size) % islandOpacityLevels.size
        val targetAlpha = islandOpacityLevels[currentIslandOpacityIndex]
        val label = islandOpacityLabels[currentIslandOpacityIndex]

        bubbleView?.animate()?.cancel()
        bubbleView?.animate()
            ?.alpha(targetAlpha)
            ?.setDuration(240)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()

        btnIslandOpacity?.text = label

        try {
            getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .edit().putInt("PREF_ISLAND_OPACITY_INDEX", currentIslandOpacityIndex).apply()
        } catch (_: Exception) {}

        if (showToast) {
            val pct = (targetAlpha * 100).toInt()
            Toast.makeText(this, "Floating pill brightness: $pct%", Toast.LENGTH_SHORT).show()
        }
    }

    // Bubble overlay (Small stealth HUD)
    private var bubbleView: View? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var tvAnswerDisplay: TextView? = null
    private var ivScanIcon: ImageView? = null

    private fun getDeviceBatteryPercentage(): Int {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85
            if (pct in 1..100) pct else 85
        } catch (_: Exception) {
            85
        }
    }

    // Floating Gemini Window overlay
    private var geminiWindowView: View? = null
    private lateinit var windowParams: WindowManager.LayoutParams
    private var geminiWebView: WebView? = null
    private var tvWindowAnswerBadge: TextView? = null
    private var progressBar: ProgressBar? = null
    private var isWindowVisible = false
    private var currentOpacityIndex = 0
    private val opacityLevels = floatArrayOf(1.0f, 0.75f, 0.45f)
    private val opacityLabels = arrayOf("100%", "75%", "45%")

    // Sizing presets: 0 = S (Compact), 1 = M (Medium), 2 = L (Large), 3 = FULL (Complete Overlap)
    private var currentSizeIndex = 0
    private val sizeLabels = arrayOf("S", "M", "L", "FULL")

    private var displayMode = MainActivity.MODE_STEALTH

    private var screenCaptureManager: ScreenCaptureManager? = null
    private val ocrEngine = OcrEngine()
    private val geminiVisionClient = GeminiVisionClient()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // High-RAM Engine & Background-to-Front Answer Sync State
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var highRamSyncJob: Job? = null
    private var currentSolveFlowJob: Job? = null
    private var activeSolvePollJob: Job? = null
    private var lastManualTapMs: Long = 0L

    @Volatile
    private var latestCachedAnswer: String = ""

    @Volatile
    private var latestAnswerModel: AnswerModel? = null

    private fun isValidAnswerBadge(badge: String?): Boolean {
        val clean = badge?.trim().orEmpty()
        if (clean.isEmpty() || clean.length > 24) return false
        return clean.uppercase() !in setOf("?", "...", "ERR", "CAP", "TXT", "KEY", "NET", "RETRY", "LOGIN")
    }

    @Volatile
    private var lastSyncedDomTurnSignature: String = ""

    @Volatile
    private var activeSolveId: Long = 0L

    @Volatile
    private var deliveredSolveId: Long = -1L

    @Volatile
    private var isScanning = false

    @Volatile
    private var isGeminiReady = false

    @Volatile
    private var isForegroundStarted = false

    // Auto-click bot state
    private var isAutoClickEnabled = false
    private var autoClickJob: Job? = null
    private var btnBotToggle: ImageView? = null
    private val AUTO_CLICK_INTERVAL_MS = 5000L // Scan every 5 seconds

    // Pending auto-click data (set before JS injection, consumed by bridge callback)
    @Volatile
    private var pendingAutoClickBlocks: List<Pair<String, Rect>>? = null

    // Hardware volume button fallback receiver
    private var volumeReceiver: BroadcastReceiver? = null
    private var lastVolumeTriggerTime = 0L

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        configureAppCheck(this, null)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Always start fresh so a previous session's answer is never displayed as current
        latestCachedAnswer = ""
        latestAnswerModel = null
        createNotificationChannel()
        acquireHighPerformanceWakeLock()
        registerVolumeButtonReceiver()
        startHighRamKeepAliveAndSync()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireHighPerformanceWakeLock() {
        try {
            if (cpuWakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            cpuWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "OmniSolve:HighRamSolverWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L) // 4 hours continuous high-RAM background execution
            }
            Log.d(TAG, "High-RAM CPU WakeLock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire CPU WakeLock: ${e.message}")
        }
    }

    private fun releaseHighPerformanceWakeLock() {
        try {
            if (cpuWakeLock?.isHeld == true) {
                cpuWakeLock?.release()
            }
            cpuWakeLock = null
        } catch (_: Exception) {}
    }

    override fun onTrimMemory(level: Int) {
        // High-RAM Mode: intentionally retain WebView DOM, tiles, and JS V8 heap in background
        // instead of releasing caches when UI is hidden or backgrounded.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            mainHandler.post {
                geminiWebView?.onResume()
                geminiWebView?.resumeTimers()
            }
        }
    }

    private fun registerVolumeButtonReceiver() {
        if (volumeReceiver != null) return
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val now = System.currentTimeMillis()
                    if (now - lastVolumeTriggerTime > 1500) { // 1.5s debounce
                        lastVolumeTriggerTime = now
                        Log.d(TAG, "Hardware volume change detected via broadcast receiver")
                        triggerSolveFromHardware()
                    }
                }
            }
        }
        try {
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            registerReceiver(volumeReceiver, filter)
            Log.d(TAG, "Registered volume button fallback receiver")
        } catch (e: Exception) {
            Log.w(TAG, "Could not register volume receiver: ${e.message}")
        }
    }

    private fun unregisterVolumeButtonReceiver() {
        try {
            if (volumeReceiver != null) {
                unregisterReceiver(volumeReceiver)
                volumeReceiver = null
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE_CONFIG || intent?.action == ACTION_COMPANION_SYNC) {
            if (!isForegroundStarted) {
                stopSelf()
                return START_NOT_STICKY
            }
            applyUpdatedConfig(intent)
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val resultData: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        val hasValidProjectionToken = (resultCode == Activity.RESULT_OK && resultData != null)

        if (!hasValidProjectionToken && !isForegroundStarted) {
            Log.w(TAG, "OverlayService started without MediaProjection grant — stopping cleanly")
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundSafely()
        isForegroundStarted = true
        acquireHighPerformanceWakeLock()

        val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)

        displayMode = intent?.getStringExtra(EXTRA_DISPLAY_MODE)
            ?: prefs.getString(MainActivity.PREF_KEY_DISPLAY_MODE, MainActivity.MODE_STEALTH)
            ?: MainActivity.MODE_STEALTH

        isAutoClickEnabled = intent?.getBooleanExtra(EXTRA_AUTO_CLICK, false)
            ?: prefs.getBoolean(MainActivity.PREF_KEY_AUTO_CLICK, false)

        isAfkAutoAdvanceEnabled = intent?.getBooleanExtra(EXTRA_AFK_MODE, false)
            ?: prefs.getBoolean(MainActivity.PREF_KEY_AFK_MODE, false)

        val scanSec = intent?.getIntExtra(EXTRA_SCAN_INTERVAL_SEC, -1) ?: -1
        val savedScanSec = if (scanSec > 0) scanSec else prefs.getInt(MainActivity.PREF_KEY_SCAN_INTERVAL_SEC, 3)
        scanIntervalMs = (savedScanSec.coerceIn(1, 10) * 1000L)

        val nextDelay = intent?.getIntExtra(EXTRA_NEXT_DELAY_MS, -1) ?: -1
        nextDelayMs = if (nextDelay > 0) nextDelay.toLong() else prefs.getInt(MainActivity.PREF_KEY_NEXT_DELAY_MS, 1500).toLong().coerceIn(500L, 4000L)

        isInstantTriggerEnabled = intent?.getBooleanExtra(EXTRA_INSTANT_TRIGGER, true)
            ?: prefs.getBoolean(MainActivity.PREF_KEY_INSTANT_TRIGGER, true)

        extractionEngine = intent?.getStringExtra(EXTRA_EXTRACTION_ENGINE)
            ?: prefs.getString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, MainActivity.ENGINE_OCR)
            ?: MainActivity.ENGINE_OCR

        solverMethod = SolverMethod.fromId(
            intent?.getStringExtra(EXTRA_SOLVER_METHOD)
                ?: prefs.getString(MainActivity.PREF_KEY_SOLVER_METHOD, SolverMethod.BACKGROUND_GEMINI.id)
        )

        hudStyle = intent?.getStringExtra(EXTRA_HUD_STYLE)
            ?: prefs.getString(MainActivity.PREF_KEY_HUD_STYLE, MainActivity.HUD_STYLE_ISLAND)
            ?: MainActivity.HUD_STYLE_ISLAND

        isCamouflageEnabled = intent?.getBooleanExtra(EXTRA_CAMOUFLAGE, false)
            ?: prefs.getBoolean(MainActivity.PREF_KEY_CAMOUFLAGE, false)

        isHapticFeedbackEnabled = intent?.getBooleanExtra(EXTRA_HAPTIC, true)
            ?: prefs.getBoolean(MainActivity.PREF_KEY_HAPTIC, true)

        isAntiCheatEnabled = intent?.getBooleanExtra(EXTRA_ANTI_CHEAT, true)
            ?: prefs.getBoolean(MainActivity.PREF_KEY_ANTI_CHEAT, true)

        themeMode = intent?.getStringExtra(EXTRA_THEME_MODE)
            ?: prefs.getString(MainActivity.PREF_KEY_THEME_MODE, MainActivity.THEME_DARK)
            ?: MainActivity.THEME_DARK

        val opacityIdx = intent?.getIntExtra(EXTRA_ISLAND_OPACITY_INDEX, -1) ?: -1
        if (opacityIdx in islandOpacityLevels.indices) {
            currentIslandOpacityIndex = opacityIdx
        }

        // Companion Pocket Sync Initialization
        val syncEnabled = intent?.getBooleanExtra(EXTRA_COMPANION_SYNC_ENABLED, false)
            ?: prefs.getBoolean(MainActivity.PREF_KEY_COMPANION_SYNC_ENABLED, false)
        val companionHost = intent?.getStringExtra(EXTRA_COMPANION_HOST)
            ?: prefs.getString(MainActivity.PREF_KEY_COMPANION_HOST, "192.168.1.100") ?: "192.168.1.100"
        val companionPort = intent?.getIntExtra(EXTRA_COMPANION_PORT, prefs.getInt(MainActivity.PREF_KEY_COMPANION_PORT, 59124))
            ?: 59124

        if (syncEnabled) {
            startCompanionSync(companionHost, companionPort)
        } else {
            stopCompanionSync()
        }

        if (hasValidProjectionToken) {
            initScreenCapture(resultCode, resultData!!)
        }

        mainHandler.postDelayed({
            if (solverMethod == SolverMethod.OVERLAPPING_GOOGLE) {
                currentSizeIndex = 3
                displayMode = MainActivity.MODE_WINDOW
            }
            // Initialize WebView immediately when OCR+Gemini, Overlapping Google, or Window mode is active
            if (solverMethod == SolverMethod.BACKGROUND_GEMINI ||
                solverMethod == SolverMethod.OVERLAPPING_GOOGLE ||
                displayMode == MainActivity.MODE_WINDOW
            ) {
                setupGeminiWindow()
            }
            setupFloatingBubble()
            bringFrontPillToTop()
            if (isValidAnswerBadge(latestCachedAnswer) && !isScanning) {
                setAnswerUI(latestCachedAnswer, "#047857", triggerFeedback = false)
            } else if (!isScanning) {
                setAnswerUI("?", "#0F172A", triggerFeedback = false)
            }
            if (isAutoClickEnabled) startAutoClickBot()
        }, 120)

        return START_NOT_STICKY
    }

    private fun applyUpdatedConfig(intent: Intent?) {
        val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        if (intent == null) return

        if (intent.hasExtra(EXTRA_EXTRACTION_ENGINE)) {
            extractionEngine = intent.getStringExtra(EXTRA_EXTRACTION_ENGINE) ?: extractionEngine
        }
        if (intent.hasExtra(EXTRA_SOLVER_METHOD)) {
            solverMethod = SolverMethod.fromId(intent.getStringExtra(EXTRA_SOLVER_METHOD))
            if (solverMethod == SolverMethod.OVERLAPPING_GOOGLE) {
                currentSizeIndex = 3
                displayMode = MainActivity.MODE_WINDOW
                if (geminiWindowView == null) {
                    setupGeminiWindow()
                } else {
                    showGeminiWindow(true)
                }
                bringFrontPillToTop()
            } else if (solverMethod == SolverMethod.BACKGROUND_GEMINI && geminiWindowView == null) {
                setupGeminiWindow()
                bringFrontPillToTop()
            }
        }
        if (intent.hasExtra(EXTRA_SCAN_INTERVAL_SEC)) {
            scanIntervalMs = intent.getIntExtra(EXTRA_SCAN_INTERVAL_SEC, 3).coerceIn(1, 10) * 1000L
        }
        if (intent.hasExtra(EXTRA_NEXT_DELAY_MS)) {
            nextDelayMs = intent.getIntExtra(EXTRA_NEXT_DELAY_MS, 1500).toLong().coerceIn(500L, 4000L)
        }
        if (intent.hasExtra(EXTRA_INSTANT_TRIGGER)) {
            isInstantTriggerEnabled = intent.getBooleanExtra(EXTRA_INSTANT_TRIGGER, isInstantTriggerEnabled)
        }
        if (intent.hasExtra(EXTRA_AFK_MODE)) {
            isAfkAutoAdvanceEnabled = intent.getBooleanExtra(EXTRA_AFK_MODE, isAfkAutoAdvanceEnabled)
        }
        if (intent.hasExtra(EXTRA_HAPTIC)) {
            isHapticFeedbackEnabled = intent.getBooleanExtra(EXTRA_HAPTIC, isHapticFeedbackEnabled)
        }
        if (intent.hasExtra(EXTRA_ANTI_CHEAT)) {
            isAntiCheatEnabled = intent.getBooleanExtra(EXTRA_ANTI_CHEAT, isAntiCheatEnabled)
        }

        var rebuiltBubble = false
        if (intent.hasExtra(EXTRA_HUD_STYLE)) {
            val newHudStyle = intent.getStringExtra(EXTRA_HUD_STYLE) ?: hudStyle
            if (newHudStyle != hudStyle) {
                hudStyle = newHudStyle
                try { bubbleView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
                bubbleView = null
                setupFloatingBubble()
                bringFrontPillToTop()
                rebuiltBubble = true
            }
        }

        if (intent.hasExtra(EXTRA_ISLAND_OPACITY_INDEX)) {
            val opacityIdx = intent.getIntExtra(EXTRA_ISLAND_OPACITY_INDEX, currentIslandOpacityIndex)
            if (opacityIdx in islandOpacityLevels.indices) {
                applyIslandOpacity(opacityIdx, showToast = false)
            }
        }

        if (intent.hasExtra(EXTRA_CAMOUFLAGE)) {
            val newCamo = intent.getBooleanExtra(EXTRA_CAMOUFLAGE, isCamouflageEnabled)
            isCamouflageEnabled = newCamo
            ivIslandIcon?.setImageResource(
                if (isCamouflageEnabled) R.drawable.ic_battery_camouflage else R.drawable.ic_prism_orb
            )
            if (!isScanning) {
                if (isValidAnswerBadge(latestCachedAnswer)) {
                    setAnswerUI(latestCachedAnswer, "#047857", triggerFeedback = false)
                } else {
                    setAnswerUI("?", "#0F172A", triggerFeedback = false)
                }
            }
        } else if (rebuiltBubble && !isScanning) {
            if (isValidAnswerBadge(latestCachedAnswer)) {
                setAnswerUI(latestCachedAnswer, "#047857", triggerFeedback = false)
            } else {
                setAnswerUI("?", "#0F172A", triggerFeedback = false)
            }
        }

        if (intent.hasExtra(EXTRA_DISPLAY_MODE)) {
            val newMode = if (solverMethod == SolverMethod.OVERLAPPING_GOOGLE) {
                MainActivity.MODE_WINDOW
            } else {
                intent.getStringExtra(EXTRA_DISPLAY_MODE) ?: displayMode
            }
            displayMode = newMode
            val shouldShowWin = (displayMode == MainActivity.MODE_WINDOW)
            if (shouldShowWin && geminiWindowView == null) {
                setupGeminiWindow()
                bringFrontPillToTop()
            } else if (geminiWindowView != null && (shouldShowWin != isWindowVisible || solverMethod == SolverMethod.OVERLAPPING_GOOGLE)) {
                showGeminiWindow(shouldShowWin)
            }
        }

        if (intent.hasExtra(EXTRA_AUTO_CLICK)) {
            val newAuto = intent.getBooleanExtra(EXTRA_AUTO_CLICK, isAutoClickEnabled)
            isAutoClickEnabled = newAuto
            updateBotToggleUI()
            if (isAutoClickEnabled) {
                startAutoClickBot()
            } else {
                stopAutoClickBot()
            }
        }

        if (intent.hasExtra(EXTRA_COMPANION_SYNC_ENABLED)) {
            val syncEnabled = intent.getBooleanExtra(EXTRA_COMPANION_SYNC_ENABLED, false)
            val host = intent.getStringExtra(EXTRA_COMPANION_HOST)
                ?: prefs.getString(MainActivity.PREF_KEY_COMPANION_HOST, "192.168.1.100") ?: "192.168.1.100"
            val port = intent.getIntExtra(EXTRA_COMPANION_PORT, prefs.getInt(MainActivity.PREF_KEY_COMPANION_PORT, 59124))
            if (syncEnabled) {
                startCompanionSync(host, port)
            } else {
                stopCompanionSync()
            }
        }
    }

    private fun startCompanionSync(host: String, port: Int) {
        if (isCompanionSyncActive && companionSyncJob?.isActive == true) return
        isCompanionSyncActive = true
        companionSyncJob?.cancel()
        companionSyncJob = serviceScope.launch(Dispatchers.IO) {
            val url = "http://$host:$port/events"
            Log.d(TAG, "Starting Companion SSE sync at $url")
            while (isActive && isCompanionSyncActive) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Accept", "text/event-stream")
                        .build()
                    val response = companionHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        delay(3000)
                        continue
                    }
                    withContext(Dispatchers.Main) {
                        tvIslandTitle?.text = "💻 Sync Active"
                    }
                    val source = response.body?.source() ?: break
                    var currentEvent = "message"
                    while (isActive && isCompanionSyncActive && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("event:")) {
                            currentEvent = line.substring(6).trim()
                        } else if (line.startsWith("data:")) {
                            val dataStr = line.substring(5).trim()
                            if (currentEvent == "solve") {
                                try {
                                    val json = JSONObject(dataStr)
                                    val answer = json.optString("answer", "").trim().uppercase()
                                    if (answer.length == 1 && answer in "ABCD") {
                                        withContext(Dispatchers.Main) {
                                            deliverResolvedAnswer(answer, isAutoClick = isAutoClickEnabled, solveId = 0L)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Companion event JSON parse error: $dataStr", e)
                                }
                            } else if (currentEvent == "panic") {
                                withContext(Dispatchers.Main) {
                                    setAnswerUI("?", "#0F172A", triggerFeedback = false)
                                    if (isWindowVisible) toggleGeminiWindow()
                                }
                            }
                        } else if (line.isEmpty()) {
                            currentEvent = "message"
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Companion sync connection dropped: ${e.message}. Reconnecting in 4s...")
                    delay(4000)
                }
            }
        }
    }

    private fun stopCompanionSync() {
        isCompanionSyncActive = false
        companionSyncJob?.cancel()
        companionSyncJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        currentSolveFlowJob?.cancel()
        activeSolvePollJob?.cancel()
        highRamSyncJob?.cancel()
        stopCompanionSync()
        stopAutoClickBot()
        serviceScope.cancel()
        unregisterVolumeButtonReceiver()
        releaseHighPerformanceWakeLock()
        instance = null
        try { screenCaptureManager?.stopCapture() } catch (_: Exception) {}
        try { ocrEngine.close() } catch (_: Exception) {}
        try { bubbleView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        try { geminiWebView?.destroy() } catch (_: Exception) {}
        try { geminiWindowView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        Log.d(TAG, "OverlayService destroyed")
    }

    // ─── Init Screen Capture & Foreground ─────────────────────────────────────

    private fun startForegroundSafely() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            try { startForeground(NOTIFICATION_ID, notification) } catch (_: Exception) {}
        }
    }

    private fun initScreenCapture(resultCode: Int, resultData: Intent) {
        try {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mgr.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                Log.e(TAG, "getMediaProjection returned null")
                return
            }
            screenCaptureManager = ScreenCaptureManager(this)
            screenCaptureManager!!.setMediaProjection(projection)
            Log.d(TAG, "Screen capture ready")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init screen capture", e)
            screenCaptureManager = null
        }
    }

    // ─── 1. Setup Floating Bubble (Stealth HUD) ───────────────────────────────

    private fun setupFloatingBubble() {
        if (bubbleView != null) return

        val isIsland = (hudStyle == MainActivity.HUD_STYLE_ISLAND)
        val layoutRes = if (isIsland) R.layout.layout_dynamic_island else R.layout.layout_floating_bubble

        bubbleView = LayoutInflater.from(this).inflate(layoutRes, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            val statusBarH = getStatusBarHeight()
            if (isIsland) {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = statusBarH + dpToPx(8)
            } else {
                gravity = Gravity.TOP or Gravity.START
                x = 24
                y = statusBarH + dpToPx(80)
            }
        }

        val bubbleContainer: View
        val expandedToolbar: View
        val btnScan: View
        val btnToggleWindow: View?
        val btnClose: View

        if (isIsland) {
            bubbleContainer = bubbleView!!.findViewById(R.id.island_icon_container)
            expandedToolbar = bubbleView!!.findViewById(R.id.island_expanded_controls)
            btnScan         = bubbleView!!.findViewById(R.id.btn_island_scan)
            tvAnswerDisplay = bubbleView!!.findViewById(R.id.tv_island_answer)
            ivScanIcon      = bubbleView!!.findViewById(R.id.iv_island_scan_icon)
            btnToggleWindow = bubbleView!!.findViewById(R.id.btn_island_toggle_window)
            btnBotToggle    = bubbleView!!.findViewById(R.id.btn_island_bot_toggle)
            btnClose        = bubbleView!!.findViewById(R.id.btn_island_close)
            tvIslandTitle   = bubbleView!!.findViewById(R.id.tv_island_title)
            ivIslandIcon    = bubbleView!!.findViewById(R.id.iv_island_icon)
            llIslandExplanationDrawer = bubbleView!!.findViewById(R.id.ll_island_explanation_drawer)
            tvIslandMeta    = bubbleView!!.findViewById(R.id.tv_island_meta)
            tvIslandExplanation = bubbleView!!.findViewById(R.id.tv_island_explanation)

            if (isCamouflageEnabled) {
                ivIslandIcon?.setImageResource(R.drawable.ic_battery_camouflage)
                tvIslandTitle?.text = "Charging"
                val (camoText, isSolved) = formatChargingCamouflage(latestCachedAnswer, getDeviceBatteryPercentage())
                tvAnswerDisplay?.text = camoText
                tvAnswerDisplay?.textSize = 13f
                tvAnswerDisplay?.setTextColor(Color.parseColor(if (isSolved) "#047857" else "#0F172A"))
                llIslandExplanationDrawer?.visibility = View.GONE
            } else {
                ivIslandIcon?.setImageResource(R.drawable.ic_prism_orb)
                if (isValidAnswerBadge(latestCachedAnswer)) {
                    tvIslandTitle?.text = "Answer"
                    tvAnswerDisplay?.text = latestCachedAnswer
                } else {
                    tvIslandTitle?.text = "Tap to Solve"
                    tvAnswerDisplay?.text = "?"
                }
            }

            // Tapping explanation drawer collapses it cleanly
            llIslandExplanationDrawer?.setOnClickListener {
                val group = bubbleView as? ViewGroup
                if (group != null) {
                    TransitionManager.beginDelayedTransition(group, AutoTransition().apply { duration = 200 })
                }
                llIslandExplanationDrawer?.visibility = View.GONE
                try { windowManager.updateViewLayout(bubbleView, bubbleParams) } catch (_: Exception) {}
            }

            // Tapping island body always scans & solves immediately (cancelling any stuck in-flight try)
            bubbleView!!.findViewById<View>(R.id.ll_island_body)?.setOnClickListener {
                onUserTapSolve()
            }
        } else {
            llIslandExplanationDrawer = null
            tvIslandMeta = null
            tvIslandExplanation = null
            bubbleContainer = bubbleView!!.findViewById(R.id.bubble_icon_container)
            expandedToolbar = bubbleView!!.findViewById(R.id.expanded_toolbar)
            btnScan         = bubbleView!!.findViewById(R.id.btn_scan)
            tvAnswerDisplay = bubbleView!!.findViewById(R.id.tv_answer_display)
            ivScanIcon      = bubbleView!!.findViewById(R.id.iv_scan_icon)
            btnToggleWindow = bubbleView!!.findViewById(R.id.btn_toggle_window)
            btnBotToggle    = bubbleView!!.findViewById(R.id.btn_bot_toggle)
            btnClose        = bubbleView!!.findViewById(R.id.btn_close_toolbar)

            if (isCamouflageEnabled) {
                val (camoText, isSolved) = formatChargingCamouflage(latestCachedAnswer, getDeviceBatteryPercentage())
                tvAnswerDisplay?.text = camoText
                tvAnswerDisplay?.textSize = 13f
                tvAnswerDisplay?.setTextColor(Color.parseColor(if (isSolved) "#047857" else "#0F172A"))
            } else if (isValidAnswerBadge(latestCachedAnswer)) {
                tvAnswerDisplay?.text = latestCachedAnswer
            } else {
                tvAnswerDisplay?.text = "?"
            }
        }

        // Opacity / Visibility Toggle Button (100% -> 90% -> 50% -> 25%)
        val btnOpacityView: TextView? = if (isIsland) {
            bubbleView!!.findViewById(R.id.btn_island_opacity)
        } else {
            bubbleView!!.findViewById(R.id.btn_bubble_opacity)
        }
        btnIslandOpacity = btnOpacityView

        val savedOpacity = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
            .getInt("PREF_ISLAND_OPACITY_INDEX", currentIslandOpacityIndex)
        applyIslandOpacity(savedOpacity, showToast = false)

        btnOpacityView?.setOnClickListener {
            applyIslandOpacity(currentIslandOpacityIndex + 1)
        }

        // Update bot toggle icon state
        updateBotToggleUI()

        // Single tap on orb icon toggles expanded toolbar;
        // Double tap cycles pill visibility (100% -> 90% -> 50% -> 25%)
        bubbleContainer.setOnTouchListener(createDragAndTapListener(
            bubbleParams,
            { bubbleView },
            onSingleTap = {
                val group = bubbleView as? ViewGroup
                if (group != null) {
                    val transition = AutoTransition().apply {
                        duration = 240
                        interpolator = OvershootInterpolator(1.2f)
                    }
                    TransitionManager.beginDelayedTransition(group, transition)
                }
                expandedToolbar.visibility = if (expandedToolbar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                try { windowManager.updateViewLayout(bubbleView, bubbleParams) } catch (_: Exception) {}
            },
            onDoubleTap = {
                applyIslandOpacity(currentIslandOpacityIndex + 1)
            }
        ))

        btnScan.setOnClickListener {
            onUserTapSolve()
        }

        // Tapping the window toggle button opens/hides the floating Gemini window
        btnToggleWindow?.setOnClickListener {
            toggleGeminiWindow()
        }

        tvAnswerDisplay?.setOnClickListener {
            val drawer = llIslandExplanationDrawer
            val hasCurrentExplanation = latestAnswerModel != null && !tvIslandExplanation?.text.isNullOrBlank()
            if (isIsland && !isCamouflageEnabled && drawer != null && hasCurrentExplanation && !isScanning) {
                val group = bubbleView as? ViewGroup
                if (group != null) {
                    val transition = AutoTransition().apply {
                        duration = 220
                        interpolator = OvershootInterpolator(1.1f)
                    }
                    TransitionManager.beginDelayedTransition(group, transition)
                }
                drawer.visibility = if (drawer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                try { windowManager.updateViewLayout(bubbleView, bubbleParams) } catch (_: Exception) {}
            } else {
                onUserTapSolve()
            }
        }

        // Bot toggle button
        btnBotToggle?.setOnClickListener {
            isAutoClickEnabled = !isAutoClickEnabled
            // Save preference
            getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .edit().putBoolean(MainActivity.PREF_KEY_AUTO_CLICK, isAutoClickEnabled).apply()
            updateBotToggleUI()
            if (isAutoClickEnabled) {
                if (!AutoClickAccessibilityService.isServiceRunning()) {
                    Toast.makeText(this@OverlayService, "Enable Screen Text Helper in Settings first to use Auto-Tap", Toast.LENGTH_LONG).show()
                    isAutoClickEnabled = false
                    updateBotToggleUI()
                } else {
                    startAutoClickBot()
                    val modeMsg = if (isAfkAutoAdvanceEnabled) "Auto-Tap + Auto-Next ON" else "Auto-Tap ON"
                    Toast.makeText(this@OverlayService, modeMsg, Toast.LENGTH_SHORT).show()
                }
            } else {
                stopAutoClickBot()
                Toast.makeText(this@OverlayService, "Auto-Tap OFF", Toast.LENGTH_SHORT).show()
            }
        }

        // Long press bot button toggles AFK Auto-Advance mode on the fly
        btnBotToggle?.setOnLongClickListener {
            isAfkAutoAdvanceEnabled = !isAfkAutoAdvanceEnabled
            getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .edit().putBoolean(MainActivity.PREF_KEY_AFK_MODE, isAfkAutoAdvanceEnabled).apply()
            val status = if (isAfkAutoAdvanceEnabled) "Auto-Move to Next Question: ON" else "Auto-Move to Next Question: OFF"
            Toast.makeText(this@OverlayService, status, Toast.LENGTH_SHORT).show()
            true
        }

        btnClose.setOnClickListener {
            val group = bubbleView as? ViewGroup
            if (group != null) {
                val transition = AutoTransition().apply {
                    duration = 200
                }
                TransitionManager.beginDelayedTransition(group, transition)
            }
            expandedToolbar.visibility = View.GONE
            try { windowManager.updateViewLayout(bubbleView, bubbleParams) } catch (_: Exception) {}
        }

        try {
            windowManager.addView(bubbleView, bubbleParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating bubble view", e)
        }
    }

    // ─── 2. Setup Floating Gemini Window / Headless Engine ────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupGeminiWindow() {
        if (geminiWindowView != null) return

        geminiWindowView = LayoutInflater.from(this)
            .inflate(R.layout.layout_floating_gemini_window, null)

        val initialWidth = dpToPx(290)
        val initialHeight = dpToPx(340)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        windowParams = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = getStatusBarHeight() + dpToPx(58)
        }

        // Header controls
        val headerDragArea = geminiWindowView!!.findViewById<View>(R.id.header_drag_area)
        val btnWindowScan = geminiWindowView!!.findViewById<TextView>(R.id.btn_window_scan)
        val btnSize = geminiWindowView!!.findViewById<TextView>(R.id.btn_window_size)
        val btnOpacity = geminiWindowView!!.findViewById<TextView>(R.id.btn_window_opacity)
        val btnMinimize = geminiWindowView!!.findViewById<View>(R.id.btn_window_minimize)
        val btnClose = geminiWindowView!!.findViewById<View>(R.id.btn_window_close)
        val btnResizeGrip = geminiWindowView!!.findViewById<View>(R.id.btn_resize_grip)
        tvWindowAnswerBadge = geminiWindowView!!.findViewById(R.id.tv_window_answer_badge)
        progressBar = geminiWindowView!!.findViewById(R.id.web_progress_bar)
        geminiWebView = geminiWindowView!!.findViewById(R.id.gemini_webview)

        if (isValidAnswerBadge(latestCachedAnswer)) {
            tvWindowAnswerBadge?.text = latestCachedAnswer
        }

        tvWindowAnswerBadge?.setOnClickListener {
            onUserTapSolve()
        }

        headerDragArea.setOnTouchListener(createDragListener(windowParams, { geminiWindowView }))

        btnWindowScan.setOnClickListener {
            onUserTapSolve()
        }

        val btnNewChat = geminiWindowView!!.findViewById<View?>(R.id.btn_window_new_chat)
        btnNewChat?.setOnClickListener {
            serviceScope.launch(Dispatchers.Main) {
                lastSyncedDomTurnSignature = ""
                forceResetGeminiWebChat()
                Toast.makeText(this@OverlayService, "Opened fresh Gemini chat", Toast.LENGTH_SHORT).show()
            }
        }

        btnSize.setOnClickListener {
            currentSizeIndex = (currentSizeIndex + 1) % sizeLabels.size
            applySizePreset(currentSizeIndex)
            btnSize.text = sizeLabels[currentSizeIndex]
        }

        btnResizeGrip.setOnTouchListener(createResizeListener())

        btnOpacity.setOnClickListener {
            currentOpacityIndex = (currentOpacityIndex + 1) % opacityLevels.size
            geminiWindowView?.alpha = opacityLevels[currentOpacityIndex]
            btnOpacity.text = opacityLabels[currentOpacityIndex]
        }

        // Minimize / Close window back to front pill while keeping WebView hot in RAM
        btnMinimize.setOnClickListener {
            showGeminiWindow(false)
        }

        btnClose.setOnClickListener {
            showGeminiWindow(false)
        }

        configureWebView(geminiWebView!!)
        geminiWebView!!.loadUrl(GEMINI_WEB_URL)

        try {
            windowManager.addView(geminiWindowView, windowParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add gemini window view", e)
        }

        if (displayMode == MainActivity.MODE_WINDOW) {
            showGeminiWindow(true)
        } else {
            parkGeminiWindowInRam()
        }
    }

    /**
     * Ensures the front overlapping pill (`bubbleView`) always sits above `geminiWindowView`
     * in WindowManager Z-order so its answer display and controls are never occluded or unresponsive.
     */
    private fun bringFrontPillToTop() {
        val pill = bubbleView ?: return
        if (!pill.isAttachedToWindow) return
        try {
            windowManager.removeViewImmediate(pill)
            windowManager.addView(pill, bubbleParams)
        } catch (e: Exception) {
            try {
                windowManager.updateViewLayout(pill, bubbleParams)
            } catch (_: Exception) {}
        }
    }

    /**
     * Parks the Gemini WebView in a full 340dp x 440dp in-RAM viewport shifted mostly off-screen
     * (`x = -336dp` so 4dp overlaps at imperceptible `alpha = 0.015f` with `FLAG_NOT_TOUCHABLE`).
     * Keeping real 340dp x 440dp window dimensions ensures Angular's `<rich-textarea>` and Send
     * button never collapse into a 0px layout while intercepting zero touches on screen.
     */
    private fun parkGeminiWindowInRam() {
        val winView = geminiWindowView ?: return
        isWindowVisible = false
        winView.findViewById<View>(R.id.header_drag_area)?.visibility = View.GONE
        winView.findViewById<View>(R.id.btn_resize_grip)?.visibility = View.GONE

        geminiWebView?.layoutParams = RelativeLayout.LayoutParams(
            dpToPx(340),
            dpToPx(440)
        )

        windowParams.width = dpToPx(340)
        windowParams.height = dpToPx(440)
        windowParams.x = -dpToPx(336)
        windowParams.y = getStatusBarHeight()
        windowParams.flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        winView.scaleX = 1.0f
        winView.scaleY = 1.0f
        winView.alpha = 0.015f
        winView.visibility = View.VISIBLE

        try {
            windowManager.updateViewLayout(winView, windowParams)
        } catch (_: Exception) {}

        geminiWebView?.onResume()
        geminiWebView?.resumeTimers()
    }

    private fun applySizePreset(index: Int) {
        val metrics = resources.displayMetrics
        when (index) {
            0 -> {
                windowParams.width = dpToPx(270)
                windowParams.height = dpToPx(310)
            }
            1 -> {
                windowParams.width = dpToPx(340).coerceAtMost((metrics.widthPixels * 0.85).toInt())
                windowParams.height = dpToPx(440).coerceAtMost((metrics.heightPixels * 0.55).toInt())
            }
            2 -> {
                windowParams.width = (metrics.widthPixels * 0.92).toInt().coerceAtMost(dpToPx(420))
                windowParams.height = (metrics.heightPixels * 0.65).toInt().coerceAtMost(dpToPx(580))
            }
            else -> {
                // Preset 3 ("FULL"): Overlapping Google Completely over screen
                windowParams.width = (metrics.widthPixels * 0.96).toInt()
                windowParams.height = (metrics.heightPixels * 0.82).toInt()
            }
        }
        try { windowManager.updateViewLayout(geminiWindowView, windowParams) } catch (_: Exception) {}
    }

    private fun createResizeListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var startW = 0
            private var startH = 0
            private var rawStartX = 0f
            private var rawStartY = 0f

            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                val metrics = resources.displayMetrics
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startW = windowParams.width
                        startH = windowParams.height
                        rawStartX = e.rawX
                        rawStartY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - rawStartX).toInt()
                        val dy = (e.rawY - rawStartY).toInt()
                        val newW = (startW + dx).coerceIn(dpToPx(180), metrics.widthPixels)
                        val newH = (startH + dy).coerceIn(dpToPx(160), metrics.heightPixels)
                        windowParams.width = newW
                        windowParams.height = newH
                        try { windowManager.updateViewLayout(geminiWindowView, windowParams) } catch (_: Exception) {}
                    }
                }
                return true
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }

        // Use LAYER_TYPE_SOFTWARE without offscreenPreRaster so background Service WebView
        // composites cleanly on CPU without probing /dev/dri/renderD128 DRM rendernodes on emulators.
        geminiWindowView?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            userAgentString = CHROME_USER_AGENT
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.onResume()
        webView.resumeTimers()

        // Unified bridge to receive answers from Gemini Web DOM
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onAnswerResolved(answerLetter: String) {
                val letter = answerLetter.trim().uppercase()
                Log.d(TAG, "Bridge onAnswerResolved: $letter")
                mainHandler.post {
                    deliverResolvedAnswer(letter, isAutoClick = false, solveId = activeSolveId)
                }
            }

            @JavascriptInterface
            fun onAutoClickAnswer(answerLetter: String) {
                val letter = answerLetter.trim().uppercase()
                Log.d(TAG, "AutoClick Bridge received: $letter")
                mainHandler.post {
                    deliverResolvedAnswer(letter, isAutoClick = true, solveId = activeSolveId)
                }
            }
        }, "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar?.visibility = View.VISIBLE
                    progressBar?.progress = newProgress
                } else {
                    progressBar?.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar?.visibility = View.GONE
                isGeminiReady = true
                view?.onResume()
                view?.resumeTimers()
                Log.d(TAG, "Gemini page fully loaded and warm in RAM")
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                Log.w(TAG, "WebView renderer process gone — recovering automatically in RAM")
                isGeminiReady = false
                mainHandler.post {
                    try {
                        val host = geminiWindowView?.findViewById<RelativeLayout>(R.id.webview_host_container)
                        if (host != null) {
                            geminiWebView?.let { host.removeView(it) }
                            val replacement = WebView(this@OverlayService).apply {
                                id = R.id.gemini_webview
                                layoutParams = if (isWindowVisible) {
                                    RelativeLayout.LayoutParams(
                                        RelativeLayout.LayoutParams.MATCH_PARENT,
                                        RelativeLayout.LayoutParams.MATCH_PARENT
                                    )
                                } else {
                                    RelativeLayout.LayoutParams(dpToPx(340), dpToPx(440))
                                }
                            }
                            host.addView(replacement, 0)
                            geminiWebView = replacement
                            configureWebView(replacement)
                            replacement.loadUrl(GEMINI_WEB_URL)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to recover WebView renderer", e)
                    }
                }
                return true
            }
        }

        webView.setOnTouchListener { _, _ ->
            setWindowFocusable(true)
            false
        }
    }

    private fun toggleGeminiWindow() {
        if (geminiWindowView == null) {
            displayMode = MainActivity.MODE_WINDOW
            setupGeminiWindow()
            bringFrontPillToTop()
            return
        }
        if (isWindowVisible) {
            showGeminiWindow(false)
        } else {
            showGeminiWindow(true)
        }
    }

    private suspend fun ensureGeminiWindowReady(): Boolean {
        withContext(Dispatchers.Main) {
            if (geminiWindowView == null) {
                setupGeminiWindow()
                bringFrontPillToTop()
            }
        }
        if (isGeminiReady) return true
        repeat(25) {
            delay(160)
            if (isGeminiReady) return true
        }
        return geminiWebView != null
    }

    private fun showGeminiWindow(show: Boolean) {
        isWindowVisible = show
        val winView = geminiWindowView ?: return
        if (show) {
            winView.animate().cancel()
            winView.visibility = View.VISIBLE
            winView.findViewById<View>(R.id.header_drag_area)?.visibility = View.VISIBLE
            winView.findViewById<View>(R.id.btn_resize_grip)?.visibility = View.VISIBLE

            geminiWebView?.layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )

            windowParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            applySizePreset(currentSizeIndex)
            val screenW = resources.displayMetrics.widthPixels
            windowParams.x = ((screenW - windowParams.width) / 2).coerceAtLeast(8)
            windowParams.y = getStatusBarHeight() + dpToPx(54)
            setWindowFocusable(true)
            try { windowManager.updateViewLayout(winView, windowParams) } catch (_: Exception) {}

            // Ensure WebView is immediately active (already pre-rasterized in RAM)
            geminiWebView?.onResume()
            geminiWebView?.resumeTimers()

            // Keep front overlapping pill above the window
            bringFrontPillToTop()
            if (isValidAnswerBadge(latestCachedAnswer) && !isScanning) {
                setAnswerUI(latestCachedAnswer, "#047857", triggerFeedback = false)
            }

            // Crisp Spring Pop In Animation
            winView.scaleX = 0.90f
            winView.scaleY = 0.90f
            winView.alpha = 0f
            winView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(opacityLevels[currentOpacityIndex])
                .setDuration(260)
                .setInterpolator(OvershootInterpolator(1.3f))
                .start()
        } else {
            winView.animate().cancel()
            winView.animate()
                .scaleX(0.90f)
                .scaleY(0.90f)
                .alpha(0.02f)
                .setDuration(180)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    parkGeminiWindowInRam()
                }
                .start()
        }
    }

    private fun setWindowFocusable(focusable: Boolean) {
        if (geminiWindowView == null) return
        val currentFlags = windowParams.flags
        val newFlags = if (focusable) {
            (currentFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()) or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            currentFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (currentFlags != newFlags) {
            windowParams.flags = newFlags
            try { windowManager.updateViewLayout(geminiWindowView, windowParams) } catch (_: Exception) {}
        }
    }

    /**
     * Temporarily grants input focus to the parked off-screen WebView while keeping `FLAG_NOT_TOUCHABLE`
     * so `document.execCommand('insertText')` updates Angular's Quill/ProseMirror `FormControl`
     * and enables the Send button without intercepting user touches.
     */
    private fun setParkedWebViewKeyboardFocus(focusable: Boolean) {
        val winView = geminiWindowView ?: return
        if (isWindowVisible) return
        windowParams.flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        try {
            windowManager.updateViewLayout(winView, windowParams)
        } catch (_: Exception) {}
        if (focusable) {
            geminiWebView?.onResume()
            geminiWebView?.resumeTimers()
            geminiWebView?.requestFocus()
        }
    }

    // ─── 3. Solving Flow: Screenshot + ML Kit OCR → 3 Answer Methods → Pill ──

    /**
     * Handles any manual tap on the Floating Pill / Scan button / Answer badge.
     * If a previous solve is still running or stuck, immediately cancels it and starts a fresh try!
     */
    private fun onUserTapSolve() {
        val now = System.currentTimeMillis()
        if (now - lastManualTapMs < 350L) return
        lastManualTapMs = now
        if (isScanning) {
            Log.d(TAG, "User tapped pill while scanning — cancelling in-flight solve and starting a fresh attempt immediately")
            currentSolveFlowJob?.cancel()
            activeSolvePollJob?.cancel()
            isScanning = false
        }
        triggerSolveFlow(autoClickMode = false)
    }

    /**
     * Core solve flow:
     * 1. Captures screenshot and extracts full screen text via ML Kit OCR (with optional Native A11y mode).
     * 2. Formats a strictly SINGLE-LINE prompt containing the full OCR question + options (zero newlines).
     * 3. Routes to the user's chosen Answer Method:
     *    - Method 1 (`FIREBASE_SERVER`): Logged-in Firebase Cloud Server
     *    - Method 2 (`BACKGROUND_GEMINI`): Screenshot -> ML Kit OCR -> Built-in Background Gemini Flash
     *    - Method 3 (`API_KEYS`): Multi-AI Keys (NVIDIA Free, OpenRouter Free, Gemini, OpenAI) with automatic quota shifting
     */
    private fun triggerSolveFlow(autoClickMode: Boolean = false) {
        val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        val method = SolverMethod.fromId(
            prefs.getString(MainActivity.PREF_KEY_SOLVER_METHOD, solverMethod.id)
        )
        solverMethod = method
        val autoFailover = prefs.getBoolean(MainActivity.PREF_KEY_AUTO_FAILOVER, true)

        val selectedModel = prefs.getString(
            MainActivity.PREF_KEY_GEMINI_MODEL,
            PrismAiSolverEngine.DEFAULT_CLOUD_MODEL
        ) ?: PrismAiSolverEngine.DEFAULT_CLOUD_MODEL
        val subjectMode = SubjectMode.fromId(
            prefs.getString(MainActivity.PREF_KEY_SUBJECT_MODE, SubjectMode.GENERAL.id)
        )
        val dualVerify = prefs.getBoolean(MainActivity.PREF_KEY_DUAL_VERIFY, false)
        val translateToEnglish = prefs.getBoolean(MainActivity.PREF_KEY_TRANSLATE_ENGLISH, false)

        val currentEngine = prefs.getString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, MainActivity.ENGINE_OCR)
            ?: MainActivity.ENGINE_OCR
        val a11yActive = AutoClickAccessibilityService.isServiceRunning()
        // Screenshot + ML Kit OCR is always used for Method 2 (when screen capture is active) and as default
        val useScreenOcr = (method == SolverMethod.BACKGROUND_GEMINI && screenCaptureManager != null) ||
            currentEngine != MainActivity.ENGINE_NATIVE ||
            !a11yActive
        val allowOcrFallback = prefs.getBoolean(MainActivity.PREF_KEY_NATIVE_FALLBACK_OCR, true)
        val autoCopyClipboard = prefs.getBoolean(MainActivity.PREF_KEY_AUTO_COPY_CLIPBOARD, false)

        if (useScreenOcr && screenCaptureManager == null && !a11yActive) {
            if (!autoClickMode) {
                Toast.makeText(this, "Screen capture not ready. Tap Start Floating Pill inside Prism AI.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val currentSolveId = ++activeSolveId
        currentSolveFlowJob?.cancel()
        activeSolvePollJob?.cancel()
        isScanning = true

        // Clear previous question's answer immediately so stale answers can NEVER reappear
        if (!autoClickMode) {
            latestCachedAnswer = ""
            latestAnswerModel = null
            mainHandler.post {
                llIslandExplanationDrawer?.visibility = View.GONE
                tvIslandExplanation?.text = ""
                tvIslandMeta?.text = ""
            }
        }
        setAnswerUI("...", "#0369A1", loading = true, triggerFeedback = false)
        val btnWindowScan = geminiWindowView?.findViewById<TextView>(R.id.btn_window_scan)
        btnWindowScan?.text = "OCR..."

        currentSolveFlowJob = serviceScope.launch {
            var bitmapToRecycle: Bitmap? = null
            try {
                var extractedText = ""
                var textBlocks = emptyList<Pair<String, Rect>>()

                if (!useScreenOcr && a11yActive) {
                    val scraped = withContext(Dispatchers.Default) {
                        AutoClickAccessibilityService.extractScreenHierarchy()
                    }
                    val meaningfulChars = scraped?.first?.count { it.isLetterOrDigit() } ?: 0
                    if (scraped != null && meaningfulChars >= 25) {
                        extractedText = scraped.first
                        textBlocks = scraped.second
                        Log.d(TAG, "Native accessibility scraping succeeded: ${textBlocks.size} blocks, ${extractedText.length} chars")
                        val mentionsVisual = Regex(
                            """\b(diagram|figure|fig|graph|image|chart|table|picture|shown|circuit|plot|map|triangle|angle)\b""",
                            RegexOption.IGNORE_CASE
                        ).containsMatchIn(extractedText)
                        if (screenCaptureManager != null && mentionsVisual) {
                            bitmapToRecycle = captureFrameOnly()
                        }
                    } else if (allowOcrFallback && screenCaptureManager != null) {
                        val ocrData = captureAndOcr()
                        if (ocrData == null) {
                            setAnswerUI("CAP", "#B45309", triggerFeedback = false)
                            btnWindowScan?.text = "Solve"
                            isScanning = false
                            return@launch
                        }
                        extractedText = ocrData.first
                        textBlocks = ocrData.second
                        bitmapToRecycle = ocrData.third
                    } else {
                        setAnswerUI("TXT", "#B45309", triggerFeedback = false)
                        btnWindowScan?.text = "Solve"
                        isScanning = false
                        return@launch
                    }
                } else if (screenCaptureManager != null) {
                    val ocrData = captureAndOcr()
                    if (ocrData == null) {
                        setAnswerUI("CAP", "#B45309", triggerFeedback = false)
                        btnWindowScan?.text = "Solve"
                        isScanning = false
                        return@launch
                    }
                    extractedText = ocrData.first
                    textBlocks = ocrData.second
                    bitmapToRecycle = ocrData.third
                } else if (a11yActive) {
                    val scraped = withContext(Dispatchers.Default) {
                        AutoClickAccessibilityService.extractScreenHierarchy()
                    }
                    extractedText = scraped?.first.orEmpty()
                    textBlocks = scraped?.second.orEmpty()
                }

                val meaningfulCount = extractedText.count { it.isLetterOrDigit() }
                if (meaningfulCount < 4) {
                    Log.w(TAG, "OCR extracted insufficient text ($meaningfulCount chars) — aborting instead of sending empty prompt")
                    setAnswerUI("TXT", "#B45309", triggerFeedback = false)
                    btnWindowScan?.text = "Solve"
                    isScanning = false
                    return@launch
                }

                // Check if this looks like an MCQ when Auto-Click Bot is polling
                val isMcq = Regex("""\b[A-Fa-f][.)\s]""").containsMatchIn(extractedText) ||
                            Regex("""\b(option|choice)\s*[A-Fa-f]""", RegexOption.IGNORE_CASE).containsMatchIn(extractedText) ||
                            Regex("""\b[1-6][.)\s]""").containsMatchIn(extractedText)

                if (autoClickMode && !isMcq) {
                    if (isValidAnswerBadge(latestCachedAnswer)) {
                        setAnswerUI(latestCachedAnswer, "#047857", triggerFeedback = false)
                    } else {
                        setAnswerUI("?", "#0F172A", triggerFeedback = false)
                    }
                    btnWindowScan?.text = "Solve"
                    isScanning = false
                    return@launch
                }

                val questionSignature = computeQuestionSignature(extractedText)
                if (autoClickMode && questionSignature == lastSolvedQuestionSignature && lastSolvedQuestionSignature.isNotEmpty()) {
                    if (isValidAnswerBadge(latestCachedAnswer)) {
                        setAnswerUI(latestCachedAnswer, "#047857", triggerFeedback = false)
                    }
                    btnWindowScan?.text = "Solve"
                    isScanning = false
                    return@launch
                }

                if (autoClickMode) {
                    lastSolvedQuestionSignature = questionSignature
                }

                // Strictly SINGLE-LINE prompt containing the full OCR question + options (zero '\n' so Quill never submits early)
                val promptText = MultiProviderApiSolver.buildSingleLineSolverPrompt(extractedText)

                if (autoCopyClipboard) {
                    try {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Prism AI Question", promptText))
                    } catch (e: Exception) {
                        Log.w(TAG, "Clipboard copy error: ${e.message}")
                    }
                }

                var failureBadge = "RETRY"
                var failureToast: String? = null

                suspend fun tryFirebaseServer(isPrimary: Boolean): Boolean {
                    val uid = try {
                        if (FirebaseApp.getApps(this@OverlayService).isNotEmpty()) Firebase.auth.currentUser?.uid else null
                    } catch (_: Exception) {
                        null
                    }
                    // Require Google Sign-In when user explicitly selected Method 1 (Firebase Server);
                    // allow App Check-authenticated emergency failover when used as a secondary backup.
                    if (isPrimary && uid.isNullOrBlank()) {
                        failureBadge = "LOGIN"
                        failureToast = "Log in with Google on the AI Engine tab to use Firebase Server."
                        return false
                    }
                    withContext(Dispatchers.Main) {
                        btnWindowScan?.text = if (dualVerify) "Verifying..." else "Firebase..."
                    }
                    return try {
                        val answerModel = PrismAiSolverEngine.solveQuestion(
                            extractedText = extractedText,
                            screenBitmap = bitmapToRecycle,
                            requestedModel = selectedModel,
                            subjectMode = subjectMode,
                            dualVerify = dualVerify,
                            translateToEnglish = translateToEnglish
                        )
                        val resolvedBadge = answerModel.correctChoice.trim()
                        if (isValidAnswerBadge(resolvedBadge)) {
                            withContext(Dispatchers.Main) {
                                if (autoClickMode) pendingAutoClickBlocks = textBlocks
                                deliverResolvedAnswer(
                                    rawLetter = resolvedBadge,
                                    isAutoClick = autoClickMode,
                                    solveId = currentSolveId,
                                    questionPreview = extractedText,
                                    answerModel = answerModel
                                )
                            }
                            true
                        } else {
                            false
                        }
                    } catch (ce: CancellationException) {
                        if (ce !is TimeoutCancellationException) throw ce
                        Log.w(TAG, "Firebase Server timed out: ${ce.message}")
                        false
                    } catch (e: Exception) {
                        Log.w(TAG, "Firebase Server failed: ${e.message}")
                        false
                    }
                }

                suspend fun tryApiKeys(isPrimary: Boolean): Boolean {
                    if (!MultiProviderApiSolver.hasAnyConfiguredKey(this@OverlayService)) {
                        if (isPrimary) {
                            failureBadge = "KEY"
                            failureToast = "Add a free NVIDIA, OpenRouter, Gemini, or OpenAI key in AI Engine."
                        }
                        return false
                    }
                    withContext(Dispatchers.Main) {
                        btnWindowScan?.text = "API..."
                    }
                    return try {
                        val answerModel = MultiProviderApiSolver.solveWithFailover(
                            context = this@OverlayService,
                            extractedText = extractedText,
                            subjectMode = subjectMode
                        )
                        val resolvedBadge = answerModel.correctChoice.trim()
                        if (isValidAnswerBadge(resolvedBadge)) {
                            withContext(Dispatchers.Main) {
                                if (autoClickMode) pendingAutoClickBlocks = textBlocks
                                deliverResolvedAnswer(
                                    rawLetter = resolvedBadge,
                                    isAutoClick = autoClickMode,
                                    solveId = currentSolveId,
                                    questionPreview = extractedText,
                                    answerModel = answerModel
                                )
                            }
                            true
                        } else {
                            false
                        }
                    } catch (ce: CancellationException) {
                        if (ce !is TimeoutCancellationException) throw ce
                        false
                    } catch (e: Exception) {
                        Log.w(TAG, "Multi-AI API Keys solver failed: ${e.message}")
                        if (isPrimary) {
                            failureToast = e.message
                        }
                        false
                    }
                }

                suspend fun tryBackgroundGemini(isPrimary: Boolean): Boolean {
                    if (!ensureGeminiWindowReady()) return false
                    withContext(Dispatchers.Main) {
                        pendingAutoClickBlocks = if (autoClickMode) textBlocks else null
                    }
                    return solveViaGeminiWebSession(
                        promptText = promptText,
                        isAutoClick = autoClickMode,
                        solveId = currentSolveId,
                        questionPreview = extractedText,
                        isPrimaryMethod = isPrimary,
                        suppressFailureUi = true
                    )
                }

                val solved = when (method) {
                    SolverMethod.FIREBASE_SERVER -> {
                        tryFirebaseServer(isPrimary = true) ||
                            (autoFailover && (tryApiKeys(isPrimary = false) || tryBackgroundGemini(isPrimary = false)))
                    }
                    SolverMethod.BACKGROUND_GEMINI -> {
                        tryBackgroundGemini(isPrimary = true) ||
                            (autoFailover && (tryApiKeys(isPrimary = false) || tryFirebaseServer(isPrimary = false)))
                    }
                    SolverMethod.API_KEYS -> {
                        tryApiKeys(isPrimary = true) ||
                            (autoFailover && (tryFirebaseServer(isPrimary = false) || tryBackgroundGemini(isPrimary = false)))
                    }
                    SolverMethod.OVERLAPPING_GOOGLE -> {
                        withContext(Dispatchers.Main) {
                            if (!isWindowVisible && geminiWindowView != null) {
                                currentSizeIndex = 3
                                showGeminiWindow(true)
                            }
                        }
                        tryBackgroundGemini(isPrimary = true) ||
                            (autoFailover && (tryApiKeys(isPrimary = false) || tryFirebaseServer(isPrimary = false)))
                    }
                }

                if (!solved && activeSolveId == currentSolveId && deliveredSolveId != currentSolveId) {
                    withContext(Dispatchers.Main) {
                        val color = if (failureBadge == "LOGIN" || failureBadge == "KEY") "#B45309" else "#BE123C"
                        setAnswerUI(failureBadge, color, loading = false, triggerFeedback = false)
                        btnWindowScan?.text = "Solve"
                        isScanning = false
                        if (!autoClickMode && !failureToast.isNullOrBlank()) {
                            Toast.makeText(this@OverlayService, failureToast, Toast.LENGTH_LONG).show()
                        }
                    }
                }

            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "SolveFlow error: ${e.message}", e)
                if (activeSolveId == currentSolveId) {
                    withContext(Dispatchers.Main) {
                        setAnswerUI("ERR", "#BE123C", triggerFeedback = false)
                        btnWindowScan?.text = "Solve"
                        isScanning = false
                    }
                }
            } finally {
                try {
                    bitmapToRecycle?.recycle()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Captures a clean screenshot frame without OCR when Multimodal Vision needs visual context
     * alongside Native Accessibility text.
     */
    private suspend fun captureFrameOnly(): Bitmap? {
        val scm = screenCaptureManager ?: return null
        val wasWindowOpen = isWindowVisible
        withContext(Dispatchers.Main) {
            bubbleView?.visibility = View.INVISIBLE
            geminiWindowView?.visibility = View.INVISIBLE
        }
        delay(150)

        val bitmap: Bitmap? = withContext(Dispatchers.IO) {
            scm.captureCurrentFrame()
        }

        withContext(Dispatchers.Main) {
            bubbleView?.visibility = View.VISIBLE
            if (wasWindowOpen) {
                geminiWindowView?.visibility = View.VISIBLE
                setWindowFocusable(true)
                geminiWebView?.requestFocus()
            } else {
                parkGeminiWindowInRam()
            }
            bringFrontPillToTop()
        }
        return bitmap
    }

    /**
     * Fallback helper: captures screen via MediaProjection, hides both overlays briefly, and extracts text via ML Kit OCR.
     */
    private suspend fun captureAndOcr(): Triple<String, List<Pair<String, Rect>>, Bitmap?>? {
        val scm = screenCaptureManager ?: return null
        val wasWindowOpen = isWindowVisible
        withContext(Dispatchers.Main) {
            bubbleView?.visibility = View.INVISIBLE
            geminiWindowView?.visibility = View.INVISIBLE
        }
        delay(200)

        val bitmap: Bitmap? = withContext(Dispatchers.IO) {
            scm.captureCurrentFrame()
        }

        withContext(Dispatchers.Main) {
            bubbleView?.visibility = View.VISIBLE
            if (wasWindowOpen) {
                geminiWindowView?.visibility = View.VISIBLE
                setWindowFocusable(true)
                geminiWebView?.requestFocus()
            } else {
                parkGeminiWindowInRam()
            }
            bringFrontPillToTop()
        }

        if (bitmap == null) return null

        val ocrResult = withContext(Dispatchers.Default) {
            ocrEngine.extractTextWithBounds(bitmap)
        }
        return Triple(ocrResult.first, ocrResult.second, bitmap)
    }

    private fun parseJsEvalJson(rawResult: String?): JSONObject? {
        if (rawResult.isNullOrBlank() || rawResult == "null") return null
        return try {
            when (val parsed = JSONTokener(rawResult).nextValue()) {
                is JSONObject -> parsed
                is String -> if (parsed.startsWith("{")) JSONObject(parsed) else null
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Centralized, idempotent answer delivery that updates BOTH:
     * 1. The front overlapping pill (`tvAnswerDisplay` on Dynamic Island / Floating Bubble)
     * 2. The floating Gemini window header badge (`tvWindowAnswerBadge`)
     * plus persists the solved answer in RAM & SharedPreferences so it is never lost.
     */
    private fun deliverResolvedAnswer(
        rawLetter: String,
        isAutoClick: Boolean,
        solveId: Long,
        questionPreview: String? = null,
        answerModel: AnswerModel? = null
    ) {
        val trimmed = rawLetter.trim()
        if (!isValidAnswerBadge(trimmed)) return
        val formattedBadge = if (trimmed.length == 1 && trimmed[0].uppercaseChar() in 'A'..'F') {
            trimmed.uppercase()
        } else {
            trimmed.take(24)
        }

        if (solveId > 0L && deliveredSolveId == solveId) return
        if (solveId > 0L) {
            deliveredSolveId = solveId
        }

        activeSolvePollJob?.cancel()
        latestCachedAnswer = formattedBadge
        latestAnswerModel = answerModel

        try {
            val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
            val newCount = prefs.getInt("PREF_SOLVED_COUNT", 0) + 1
            val editor = prefs.edit()
                .putString("PREF_LAST_SOLVED_ANSWER", formattedBadge)
                .putInt("PREF_SOLVED_COUNT", newCount)

            if (!questionPreview.isNullOrBlank()) {
                val snippet = questionPreview.lines()
                    .map { it.trim() }
                    .firstOrNull { it.length >= 6 }
                    ?.take(72) ?: "Screen Question"
                val existing = prefs.getString("PREF_SOLVE_HISTORY", "") ?: ""
                val entry = "${System.currentTimeMillis()}||$formattedBadge||$snippet"
                val merged = (listOf(entry) + existing.split("\n").filter { it.isNotBlank() })
                    .take(25)
                    .joinToString("\n")
                editor.putString("PREF_SOLVE_HISTORY", merged)
            }
            editor.apply()
        } catch (_: Exception) {}

        // Sync solved question to Firestore Cloud Deck when signed in with Google
        if (!questionPreview.isNullOrBlank()) {
            try {
                if (FirebaseApp.getApps(this).isNotEmpty()) {
                    val uid = Firebase.auth.currentUser?.uid
                    if (!uid.isNullOrBlank()) {
                        val modelToSave = answerModel ?: AnswerModel(
                            correctChoice = formattedBadge,
                            engineLabel = "Gemini Web Session"
                        )
                        val record = SolvedQuestionRecord.fromAnswerModel(
                            userId = uid,
                            questionText = questionPreview,
                            answer = modelToSave
                        )
                        serviceScope.launch(Dispatchers.IO) {
                            SolvedQuestionRepository(applicationContext).saveSolvedQuestion(record)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud history save skipped: ${e.message}")
            }
        }

        val blocks = pendingAutoClickBlocks
        pendingAutoClickBlocks = null

        mainHandler.post {
            // Update collapsible 2-line explanation drawer on Dynamic Island
            if (answerModel != null && (answerModel.explanation.isNotBlank() || answerModel.shortAnswer.isNotBlank())) {
                val verifiedPrefix = if (answerModel.verified) "Verified • " else ""
                tvIslandMeta?.text = "${verifiedPrefix}${answerModel.confidence}% Sure • tap badge to hide"
                val bodyText = buildString {
                    if (answerModel.shortAnswer.isNotBlank() &&
                        !answerModel.shortAnswer.equals(formattedBadge, ignoreCase = true)
                    ) {
                        append(answerModel.shortAnswer.trim())
                        if (answerModel.explanation.isNotBlank()) append(" — ")
                    }
                    append(answerModel.explanation.trim())
                    if (answerModel.translatedQuestion.isNotBlank()) {
                        append("\nEN: ")
                        append(answerModel.translatedQuestion.trim())
                    }
                }.trim()
                tvIslandExplanation?.text = bodyText
            } else {
                tvIslandExplanation?.text = ""
                llIslandExplanationDrawer?.visibility = View.GONE
            }

            geminiWindowView?.findViewById<TextView>(R.id.btn_window_scan)?.text = "Solve"
            setAnswerUI(formattedBadge, "#047857", loading = false, triggerFeedback = true)
            bringFrontPillToTop()
            if ((isAutoClick || isAutoClickEnabled) && blocks != null) {
                val targetLetters = answerModel?.extractTargetLetters()
                    ?.ifEmpty { listOf(formattedBadge.uppercase()) }
                    ?: listOf(formattedBadge.uppercase())
                val primaryTarget = targetLetters.firstOrNull().orEmpty()
                if (primaryTarget.length == 1 && primaryTarget[0] in 'A'..'F') {
                    performAutoClick(primaryTarget, blocks)
                }
            }
        }
    }

    private suspend fun evaluateJsJson(js: String): JSONObject? = withContext(Dispatchers.Main) {
        val wv = geminiWebView ?: return@withContext null
        wv.onResume()
        wv.resumeTimers()
        suspendCancellableCoroutine { cont ->
            wv.evaluateJavascript(js) { raw ->
                if (cont.isActive) {
                    cont.resume(parseJsEvalJson(raw))
                }
            }
        }
    }

    /**
     * Forces Gemini WebView onto a clean New Chat session:
     * 1. Clicks visible top-bar New Chat icon if present.
     * 2. On mobile User-Agent where New Chat lives inside the collapsed hamburger ("burger") drawer,
     *    opens the menu drawer if needed or navigates cleanly to `/app` while strictly awaiting
     *    `onPageFinished` (`isGeminiReady == true`) so prompt injection never races with page unload.
     */
    private suspend fun forceResetGeminiWebChat(): Boolean {
        val triggerSpaNewChatJs = """
            (function() {
                try {
                    function isTrulyVisible(el) {
                        if (!el || el.offsetParent === null) return false;
                        let st = window.getComputedStyle(el);
                        if (st.visibility === 'hidden' || st.display === 'none' || parseFloat(st.opacity || '1') <= 0.05) return false;
                        let r = el.getBoundingClientRect();
                        return r.width > 0 && r.height > 0;
                    }

                    let stopBtns = Array.from(document.querySelectorAll(
                        'button[aria-label*="Stop" i], [data-test-id="stop-button"]'
                    ));
                    stopBtns.filter(isTrulyVisible).forEach(btn => {
                        try { btn.click(); } catch(_) {}
                    });

                    let newChatSelectors = [
                        '[data-test-id="new-chat-button"]',
                        'a[aria-label*="New chat" i]',
                        'button[aria-label*="New chat" i]',
                        'a[href="/app"]',
                        'mat-icon[fonticon="edit_square"]',
                        'mat-icon[data-mat-icon-name="edit_square"]',
                        'mat-icon[fonticon="add_comment"]',
                        'mat-icon[data-mat-icon-name="add_comment"]'
                    ];
                    for (let sel of newChatSelectors) {
                        let matches = Array.from(document.querySelectorAll(sel));
                        for (let el of matches) {
                            let clickable = el.closest('a, button, [role="button"]') || el;
                            if (clickable && isTrulyVisible(clickable)) {
                                try { clickable.click(); } catch(_) {}
                                return JSON.stringify({ clickedSpa: true, openedDrawer: false });
                            }
                        }
                    }

                    // Mobile layout fallback: open hamburger ("burger") menu to expose New Chat button
                    let burger = document.querySelector(
                        'button[aria-label*="Main menu" i], button[aria-label*="Menu" i], [data-test-id="side-nav-menu-button"]'
                    );
                    if (burger && isTrulyVisible(burger)) {
                        try { burger.click(); } catch(_) {}
                        return JSON.stringify({ clickedSpa: false, openedDrawer: true });
                    }
                    return JSON.stringify({ clickedSpa: false, openedDrawer: false });
                } catch (e) {
                    return JSON.stringify({ clickedSpa: false, openedDrawer: false, error: e.message });
                }
            })();
        """.trimIndent()

        val clickDrawerNewChatJs = """
            (function() {
                try {
                    let selectors = [
                        '[data-test-id="new-chat-button"]',
                        'a[aria-label*="New chat" i]',
                        'button[aria-label*="New chat" i]',
                        'a[href="/app"]'
                    ];
                    for (let sel of selectors) {
                        let el = document.querySelector(sel);
                        let clickable = el ? (el.closest('a, button, [role="button"]') || el) : null;
                        if (clickable && clickable.offsetParent !== null) {
                            try { clickable.click(); } catch(_) {}
                            return JSON.stringify({ clicked: true });
                        }
                    }
                    return JSON.stringify({ clicked: false });
                } catch (e) {
                    return JSON.stringify({ clicked: false });
                }
            })();
        """.trimIndent()

        val checkCleanStateJs = """
            (function() {
                try {
                    let byTag = Array.from(document.querySelectorAll('model-response'));
                    let turns = byTag.length > 0
                        ? byTag
                        : Array.from(document.querySelectorAll('[data-message-author-role="model"], [data-test-id="model-turn"]'));
                    let editor = document.querySelector('rich-textarea div[contenteditable="true"], div.ql-editor, div[contenteditable="true"], textarea');
                    let signInBtn = document.querySelector('a[href*="ServiceLogin"], a[aria-label*="Sign in" i], button[aria-label*="Sign in" i]');
                    return JSON.stringify({
                        turnCount: turns.length,
                        hasEditor: !!editor,
                        needsLogin: !editor && !!signInBtn,
                        readyState: document.readyState || ""
                    });
                } catch (e) {
                    return JSON.stringify({ turnCount: -1, hasEditor: false, needsLogin: false });
                }
            })();
        """.trimIndent()

        val spaRes = evaluateJsJson(triggerSpaNewChatJs)
        val clickedDirect = spaRes?.optBoolean("clickedSpa", false) == true
        val openedDrawer = spaRes?.optBoolean("openedDrawer", false) == true

        if (openedDrawer) {
            delay(220)
            evaluateJsJson(clickDrawerNewChatJs)
        }

        if (clickedDirect || openedDrawer) {
            repeat(7) {
                delay(150)
                val check = evaluateJsJson(checkCleanStateJs)
                if (check?.optBoolean("hasEditor", false) == true && check.optInt("turnCount", 99) == 0) {
                    isGeminiReady = true
                    return true
                }
            }
        }

        // Clean navigation fallback: wait until onPageFinished sets isGeminiReady = true so we NEVER race the old unloading DOM!
        withContext(Dispatchers.Main) {
            isGeminiReady = false
            geminiWebView?.loadUrl(GEMINI_WEB_URL)
        }
        delay(350)
        repeat(35) {
            delay(200)
            if (!isGeminiReady) return@repeat
            val check = evaluateJsJson(checkCleanStateJs)
            if (check?.optBoolean("needsLogin", false) == true) {
                return false
            }
            if (check?.optBoolean("hasEditor", false) == true) {
                return true
            }
        }
        return evaluateJsJson(checkCleanStateJs)?.optBoolean("hasEditor", false) == true
    }

    /**
     * End-to-end Race-Free Gemini WebView Fallback Solver Pipeline:
     * 1. Reuses the warm, hydrated Gemini chat directly for fast consecutive solves (0ms reload overhead),
     *    only resetting via `forceResetGeminiWebChat()` when an active stream is stuck, the editor is missing,
     *    or the conversation exceeds 8 turns.
     * 2. Records both distinct model turn count AND latest turn text signature before injecting the prompt
     *    so new answers are detected immediately even if Gemini virtualizes older DOM nodes.
     * 3. Polls until the Send button is enabled and invokes native `.click()` once (never cancelling generation).
     */
    /**
     * End-to-end Race-Free Gemini WebView Fallback Solver Pipeline:
     * 1. Resets to a clean SPA chat whenever a prior turn exists so previous answers can NEVER bleed in.
     * 2. Temporarily enables keyboard focus on the parked 340dp x 440dp WebView while keeping
     *    `FLAG_NOT_TOUCHABLE` so Angular's `<rich-textarea>` `FormControl` registers the prompt
     *    and activates the Send button 100% reliably, then immediately releases focus.
     * 3. Strictly requires `turns.length > baselineModelTurns` before reading any answer, and fails
     *    fast with `"RETRY"` (never displaying a previous answer) if sending or generation fails.
     */
    private suspend fun solveViaGeminiWebSession(
        promptText: String,
        isAutoClick: Boolean,
        solveId: Long,
        questionPreview: String,
        isPrimaryMethod: Boolean = true,
        suppressFailureUi: Boolean = false
    ): Boolean = withContext(Dispatchers.Main) {
            val btnWindowScan = geminiWindowView?.findViewById<TextView>(R.id.btn_window_scan)

            val inspectStateJs = """
                (function() {
                    try {
                        function isTrulyVisible(el) {
                            if (!el) return false;
                            let st = window.getComputedStyle(el);
                            if (st.visibility === 'hidden' || st.display === 'none' || parseFloat(st.opacity || '1') <= 0.05) return false;
                            let r = el.getBoundingClientRect();
                            return r.width > 0 && r.height > 0;
                        }
                        let byTag = Array.from(document.querySelectorAll('model-response'));
                        let turns = byTag.length > 0
                            ? byTag
                            : Array.from(document.querySelectorAll('[data-message-author-role="model"], [data-test-id="model-turn"]'));
                        let stopBtns = Array.from(document.querySelectorAll('button[aria-label*="Stop" i], [data-test-id="stop-button"]'));
                        let generating = stopBtns.some(isTrulyVisible);
                        let editor = document.querySelector('rich-textarea div[contenteditable="true"], div.ql-editor, div[contenteditable="true"], textarea');
                        let signInBtn = document.querySelector('a[href*="ServiceLogin"], a[aria-label*="Sign in" i], button[aria-label*="Sign in" i]');
                        return JSON.stringify({
                            turnCount: turns.length,
                            generating: generating,
                            hasEditor: !!editor,
                            needsLogin: !editor && !!signInBtn
                        });
                    } catch (e) {
                        return JSON.stringify({ turnCount: 0, generating: false, hasEditor: false, needsLogin: false });
                    }
                })();
            """.trimIndent()

            var preState = evaluateJsJson(inspectStateJs)
            val preTurns = preState?.optInt("turnCount", 0) ?: 0
            val wasGenerating = preState?.optBoolean("generating", false) ?: false
            val hasEditorInitially = preState?.optBoolean("hasEditor", false) ?: false

            // Fast zero-reload SPA "New chat" click whenever a previous answer turn is on screen
            if (!wasGenerating && hasEditorInitially && preTurns in 1..5) {
                val quickSpaNewChatJs = """
                    (function() {
                        try {
                            let selectors = [
                                '[data-test-id="new-chat-button"]',
                                'a[aria-label*="New chat" i]',
                                'button[aria-label*="New chat" i]',
                                'a[href="/app"]'
                            ];
                            for (let sel of selectors) {
                                let el = document.querySelector(sel);
                                let clickable = el ? (el.closest('a, button, [role="button"]') || el) : null;
                                if (clickable && clickable.offsetParent !== null) {
                                    clickable.click();
                                    return JSON.stringify({ reset: true });
                                }
                            }
                            return JSON.stringify({ reset: false });
                        } catch (e) {
                            return JSON.stringify({ reset: false });
                        }
                    })();
                """.trimIndent()
                val quickRes = evaluateJsJson(quickSpaNewChatJs)
                if (quickRes?.optBoolean("reset", false) == true) {
                    lastSyncedDomTurnSignature = ""
                    delay(180)
                    preState = evaluateJsJson(inspectStateJs)
                }
            } else if (wasGenerating || !hasEditorInitially || preTurns > 5) {
                btnWindowScan?.text = "New Chat..."
                Log.d(TAG, "Resetting Gemini WebView chat (preTurns=$preTurns, generating=$wasGenerating, hasEditor=$hasEditorInitially)")
                lastSyncedDomTurnSignature = ""
                forceResetGeminiWebChat()
                delay(140)
                preState = evaluateJsJson(inspectStateJs)
            }

            if (!isActive || activeSolveId != solveId) return@withContext false

            if (preState?.optBoolean("needsLogin", false) == true || preState?.optBoolean("hasEditor", false) != true) {
                if (!suppressFailureUi) {
                    isScanning = false
                    btnWindowScan?.text = "Solve"
                    setAnswerUI("KEY", "#B45309", triggerFeedback = false)
                }
                if (isPrimaryMethod && !isAutoClick) {
                    Toast.makeText(
                        this@OverlayService,
                        "Sign in to Built-in Gemini on the AI Engine tab (or add a free API key)",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@withContext false
            }

            val baselineTurnsJs = """
                (function() {
                    let byTag = Array.from(document.querySelectorAll('model-response'));
                    let m = byTag.length > 0
                        ? byTag
                        : Array.from(document.querySelectorAll('[data-message-author-role="model"], [data-test-id="model-turn"]'))
                              .filter((el, _, arr) => !arr.some(other => other !== el && other.contains(el)));
                    let u = Array.from(document.querySelectorAll('user-query, [data-message-author-role="user"]'));
                    let lastTxt = m.length > 0 ? (m[m.length - 1].innerText || m[m.length - 1].textContent || '').trim() : '';
                    return JSON.stringify({ modelTurns: m.length, userTurns: u.length, lastText: lastTxt });
                })();
            """.trimIndent()
            val baselineObj = evaluateJsJson(baselineTurnsJs)
            val baselineModelTurns = baselineObj?.optInt("modelTurns", 0) ?: 0
            val baselineUserTurns = baselineObj?.optInt("userTurns", 0) ?: 0
            val baselineLastText = baselineObj?.optString("lastText", "")?.trim().orEmpty()
            val escapedBaselineText = JSONObject.quote(baselineLastText)

            val escapedPrompt = JSONObject.quote(promptText)
            val insertPromptJs = """
                (function() {
                    try {
                        const prompt = $escapedPrompt;
                        let richTextarea = document.querySelector('rich-textarea');
                        if (richTextarea) {
                            try { richTextarea.click(); } catch(_) {}
                        }

                        let input = document.querySelector('rich-textarea div[contenteditable="true"]') ||
                                    document.querySelector('div.ql-editor[contenteditable="true"]') ||
                                    document.querySelector('div[contenteditable="true"][role="textbox"]') ||
                                    document.querySelector('div[contenteditable="true"]') ||
                                    document.querySelector('textarea[aria-label*="Prompt" i]') ||
                                    document.querySelector('textarea');

                        if (!input) return JSON.stringify({ status: "INPUT_NOT_FOUND" });

                        try { input.focus(); } catch(_) {}

                        if (input.tagName && (input.tagName.toLowerCase() === 'textarea' || input.tagName.toLowerCase() === 'input')) {
                            let setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value')?.set;
                            if (setter) setter.call(input, prompt); else input.value = prompt;
                            input.dispatchEvent(new Event('input', { bubbles: true }));
                            input.dispatchEvent(new Event('change', { bubbles: true }));
                        } else {
                            try {
                                let sel = window.getSelection();
                                let range = document.createRange();
                                range.selectNodeContents(input);
                                sel.removeAllRanges();
                                sel.addRange(range);
                                document.execCommand('delete', false, null);
                            } catch(_) {}

                            let inserted = false;
                            try {
                                inserted = document.execCommand('insertText', false, prompt);
                            } catch(_) {}

                            let currentText = (input.innerText || input.textContent || '').trim();
                            if (!inserted || !currentText) {
                                try {
                                    let dt = new DataTransfer();
                                    dt.setData('text/plain', prompt);
                                    let pasteEvt = new ClipboardEvent('paste', {
                                        bubbles: true,
                                        cancelable: true,
                                        clipboardData: dt
                                    });
                                    input.dispatchEvent(pasteEvt);
                                } catch(_) {}
                            }

                            currentText = (input.innerText || input.textContent || '').trim();
                            if (!currentText) {
                                input.innerHTML = '';
                                let p = document.createElement('p');
                                p.textContent = prompt;
                                input.appendChild(p);
                                input.classList.remove('ql-blank');
                                try {
                                    input.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertText', data: prompt }));
                                    input.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: prompt }));
                                } catch(_) {
                                    input.dispatchEvent(new Event('input', { bubbles: true }));
                                }
                            } else {
                                input.dispatchEvent(new Event('input', { bubbles: true }));
                            }
                        }

                        let len = (input.innerText || input.textContent || input.value || '').trim().length;
                        return JSON.stringify({ status: len > 0 ? "INSERTED" : "EMPTY_AFTER_INSERT", length: len });
                    } catch (e) {
                        return JSON.stringify({ status: "ERROR", message: e.message });
                    }
                })();
            """.trimIndent()

            val clickStrictSendOnceJs = """
                (function() {
                    try {
                        function isTrulyVisible(el) {
                            if (!el) return false;
                            let st = window.getComputedStyle(el);
                            if (st.visibility === 'hidden' || st.display === 'none' || parseFloat(st.opacity || '1') <= 0.05) return false;
                            let r = el.getBoundingClientRect();
                            return r.width > 0 && r.height > 0;
                        }

                        function isForbiddenButton(btn) {
                            if (!btn) return true;
                            let meta = (
                                (btn.getAttribute('aria-label') || '') + ' ' +
                                (btn.getAttribute('mattooltip') || '') + ' ' +
                                (btn.getAttribute('title') || '') + ' ' +
                                (btn.getAttribute('data-test-id') || '')
                            ).toLowerCase();
                            if (meta.includes('stop') || meta.includes('cancel') || meta.includes('mic') || meta.includes('voice') || meta.includes('upload') || meta.includes('menu')) {
                                return true;
                            }
                            let icon = btn.querySelector('mat-icon, i, span.google-symbols, span.material-symbols-outlined');
                            if (icon) {
                                let iconName = (
                                    (icon.getAttribute('fonticon') || '') + ' ' +
                                    (icon.getAttribute('data-mat-icon-name') || '') + ' ' +
                                    (icon.textContent || '')
                                ).toLowerCase();
                                if (iconName.includes('stop') || iconName.includes('mic')) return true;
                            }
                            return false;
                        }

                        let candidates = [];
                        let sendIconBtn = document.querySelector('mat-icon[fonticon="send"], mat-icon[data-mat-icon-name="send"]')?.closest('button');
                        if (sendIconBtn) candidates.push(sendIconBtn);
                        document.querySelectorAll(
                            'button[aria-label*="Send" i], button[aria-label*="Submit" i], button[data-test-id="send-button"], button.send-button, button.send-button-container'
                        ).forEach(b => candidates.push(b));

                        for (let btn of candidates) {
                            if (!btn || !isTrulyVisible(btn) || isForbiddenButton(btn)) continue;
                            if (btn.disabled || btn.getAttribute('aria-disabled') === 'true') continue;
                            try { btn.click(); } catch(_) {
                                ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(type => {
                                    btn.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, view: window }));
                                });
                            }
                            return JSON.stringify({ sent: true, method: "button" });
                        }
                        return JSON.stringify({ sent: false, reason: "send_button_not_ready" });
                    } catch (e) {
                        return JSON.stringify({ sent: false, error: e.message });
                    }
                })();
            """.trimIndent()

            val pressEnterFallbackJs = """
                (function() {
                    try {
                        let input = document.querySelector('rich-textarea div[contenteditable="true"]') ||
                                    document.querySelector('div.ql-editor[contenteditable="true"]') ||
                                    document.querySelector('textarea');
                        if (!input) return JSON.stringify({ sent: false });
                        input.focus();
                        ['keydown', 'keypress', 'keyup'].forEach(type => {
                            input.dispatchEvent(new KeyboardEvent(type, {
                                key: 'Enter',
                                code: 'Enter',
                                keyCode: 13,
                                which: 13,
                                bubbles: true,
                                cancelable: true
                            }));
                        });
                        return JSON.stringify({ sent: true, method: "enter_key" });
                    } catch (e) {
                        return JSON.stringify({ sent: false });
                    }
                })();
            """.trimIndent()

            var insertStatus = ""
            var sendRes: JSONObject? = null
            try {
                // Grant keyboard focus to the parked 340dp x 440dp WebView so execCommand updates Angular FormControl
                if (!isWindowVisible) {
                    setParkedWebViewKeyboardFocus(true)
                    delay(55)
                } else {
                    setWindowFocusable(true)
                    geminiWebView?.requestFocus()
                    delay(35)
                }

                val insertRes = evaluateJsJson(insertPromptJs)
                insertStatus = insertRes?.optString("status", "") ?: ""
                Log.d(TAG, "Gemini prompt insert status: $insertRes")

                if (insertStatus != "INPUT_NOT_FOUND" && insertStatus != "EMPTY_AFTER_INSERT") {
                    for (sendAttempt in 1..8) {
                        delay(65)
                        sendRes = evaluateJsJson(clickStrictSendOnceJs)
                        if (sendRes?.optBoolean("sent", false) == true) break
                    }
                    if (sendRes?.optBoolean("sent", false) != true) {
                        sendRes = evaluateJsJson(pressEnterFallbackJs)
                    }
                    delay(70)
                }
            } finally {
                if (!isWindowVisible) {
                    setParkedWebViewKeyboardFocus(false)
                    bringFrontPillToTop()
                }
            }

            if (insertStatus == "INPUT_NOT_FOUND") {
                if (!suppressFailureUi) {
                    isScanning = false
                    btnWindowScan?.text = "Solve"
                    setAnswerUI("KEY", "#B45309", triggerFeedback = false)
                }
                return@withContext false
            }

            if (sendRes?.optBoolean("sent", false) != true) {
                Log.w(TAG, "Gemini WebView send button never activated ($sendRes) — aborting fast without stale fallback")
                if (!suppressFailureUi) {
                    isScanning = false
                    btnWindowScan?.text = "Solve"
                    setAnswerUI("RETRY", "#BE123C", triggerFeedback = false)
                }
                return@withContext false
            }

            Log.d(TAG, "Gemini strict single-shot send result: $sendRes")
            btnWindowScan?.text = "Solving..."

            var lastCleanText = ""
            var stableTicks = 0
            val maxTicks = 75 // ~13.5s max

            val inspectTurnJs = """
                (function() {
                    try {
                        const prevLastRaw = $escapedBaselineText;
                        function isTrulyVisible(el) {
                            if (!el) return false;
                            let st = window.getComputedStyle(el);
                            if (st.visibility === 'hidden' || st.display === 'none' || parseFloat(st.opacity || '1') <= 0.05) return false;
                            let r = el.getBoundingClientRect();
                            return r.width > 0 && r.height > 0;
                        }

                        let byTag = Array.from(document.querySelectorAll('model-response'));
                        let turns = byTag.length > 0
                            ? byTag
                            : Array.from(document.querySelectorAll('[data-message-author-role="model"], [data-test-id="model-turn"]'))
                                  .filter((el, _, arr) => !arr.some(other => other !== el && other.contains(el)));
                        if (turns.length === 0) {
                            turns = Array.from(document.querySelectorAll('message-content')).filter(el => !el.closest('user-query'));
                        }
                        let userTurns = document.querySelectorAll('user-query, [data-message-author-role="user"]').length;

                        let stopCandidates = Array.from(document.querySelectorAll(
                            'button[aria-label*="Stop" i], [data-test-id="stop-button"]'
                        ));
                        let stopVisible = stopCandidates.some(isTrulyVisible);

                        let editor = document.querySelector('rich-textarea div[contenteditable="true"], div.ql-editor, textarea');
                        let editorRemainingLen = editor ? (editor.innerText || editor.textContent || editor.value || '').trim().length : 0;

                        if (turns.length <= $baselineModelTurns) {
                            return JSON.stringify({
                                hasNewTurn: false,
                                cleanText: "",
                                isGenerating: stopVisible,
                                isThinking: stopVisible,
                                turnCount: turns.length,
                                userTurnCount: userTurns,
                                editorLen: editorRemainingLen
                            });
                        }

                        let latestTurn = turns[turns.length - 1];
                        let rawTurnText = (latestTurn.innerText || latestTurn.textContent || '').trim();
                        if ($baselineModelTurns > 0 && rawTurnText === prevLastRaw) {
                            return JSON.stringify({
                                hasNewTurn: false,
                                cleanText: "",
                                isGenerating: stopVisible,
                                isThinking: stopVisible,
                                turnCount: turns.length,
                                userTurnCount: userTurns,
                                editorLen: editorRemainingLen
                            });
                        }

                        let hasWorkingBanner = /gemini[^\n]*is\s+working/i.test(rawTurnText);

                        let nonThoughtMarkdown = Array.from(
                            latestTurn.querySelectorAll('.markdown-main-panel, .markdown')
                        ).find(el => !el.closest('model-thoughts, .thoughts-container, thinking-process'));

                        let answerRoot = nonThoughtMarkdown ||
                                         latestTurn.querySelector('message-content') ||
                                         latestTurn.querySelector('.model-response-text') ||
                                         latestTurn;

                        let clone = answerRoot.cloneNode(true);
                        clone.querySelectorAll(
                            'model-thoughts, .thoughts-container, thinking-process, button, mat-icon, svg, script, style'
                        ).forEach(n => n.remove());

                        let cleanText = (clone.innerText || clone.textContent || '')
                            .replace(/gemini[^\n]*is\s+working\.{0,3}/gi, ' ')
                            .replace(/^\s*(show|hide)\s+(thinking|thoughts|work).*$/gim, ' ')
                            .replace(/^\s*(thinking|working|analyzing|searching)\.{0,3}\s*$/gim, ' ')
                            .trim();

                        return JSON.stringify({
                            hasNewTurn: true,
                            cleanText: cleanText,
                            isGenerating: stopVisible,
                            isThinking: (hasWorkingBanner || stopVisible) && cleanText.length === 0,
                            turnCount: turns.length,
                            userTurnCount: userTurns,
                            editorLen: editorRemainingLen
                        });
                    } catch (e) {
                        return JSON.stringify({ hasNewTurn: false, cleanText: "", isGenerating: false, isThinking: false, error: e.message });
                    }
                })();
            """.trimIndent()

            for (tick in 1..maxTicks) {
                delay(180)
                if (!isActive || deliveredSolveId == solveId || activeSolveId != solveId) return@withContext (deliveredSolveId == solveId)

                val pollJson = evaluateJsJson(inspectTurnJs) ?: continue
                val hasNewTurn = pollJson.optBoolean("hasNewTurn", false)
                val cleanText = pollJson.optString("cleanText", "").trim()
                val isGenerating = pollJson.optBoolean("isGenerating", false)
                val isThinking = pollJson.optBoolean("isThinking", false)
                val turnCount = pollJson.optInt("turnCount", 0)
                val userTurnCount = pollJson.optInt("userTurnCount", 0)
                val editorLen = pollJson.optInt("editorLen", 0)

                // Fast dead-send abort: if after ~2.7s no user turn was added and Gemini isn't generating, fail fast
                if (tick == 15 && !hasNewTurn && !isGenerating && userTurnCount <= baselineUserTurns && editorLen > 0) {
                    Log.w(TAG, "Prompt remained unsent in editor after 2.7s — failing fast instead of stalling")
                    break
                }

                if (isThinking && cleanText.isEmpty()) {
                    btnWindowScan?.text = "Thinking..."
                    tvIslandTitle?.text = if (isCamouflageEnabled) "Charging" else "Thinking..."
                    continue
                }

                if (hasNewTurn && cleanText.isNotEmpty()) {
                    val stripped = cleanText.replace(Regex("[*_`~#]+"), " ").trim()

                    val explicitFast = Regex(
                        """(?:FINAL[\s_]*ANSWER|CORRECT[\s_]*OPTION)\s*[:=\-]?\s*\(?([A-Fa-f](?:\s*,\s*[A-Fa-f])*|True|False|[+-]?\d+(?:\.\d+)?)\)?(?:\b|$)""",
                        RegexOption.IGNORE_CASE
                    ).find(stripped)

                    if (explicitFast != null && (!isGenerating || explicitFast.groupValues[1].length == 1)) {
                        val rawMatch = explicitFast.groupValues[1].trim()
                        val fastBadge = when {
                            rawMatch.equals("true", ignoreCase = true) -> "True"
                            rawMatch.equals("false", ignoreCase = true) -> "False"
                            rawMatch.all { it.isLetter() || it == ',' || it.isWhitespace() } ->
                                rawMatch.uppercase().replace(Regex("\\s+"), " ")
                            else -> rawMatch
                        }
                        lastSyncedDomTurnSignature = "$turnCount:${cleanText.hashCode()}"
                        deliverResolvedAnswer(
                            rawLetter = fastBadge,
                            isAutoClick = isAutoClick,
                            solveId = solveId,
                            questionPreview = questionPreview
                        )
                        return@withContext true
                    }

                    if (cleanText == lastCleanText) {
                        stableTicks++
                    } else {
                        lastCleanText = cleanText
                        stableTicks = 0
                    }

                    if ((!isGenerating && stableTicks >= 2) || stableTicks >= 4) {
                        val parsedLetter = try {
                            geminiVisionClient.extractAnswerLetter(cleanText).correctChoice
                        } catch (_: Exception) {
                            null
                        }
                        if (isValidAnswerBadge(parsedLetter)) {
                            lastSyncedDomTurnSignature = "$turnCount:${cleanText.hashCode()}"
                            deliverResolvedAnswer(
                                rawLetter = parsedLetter!!,
                                isAutoClick = isAutoClick,
                                solveId = solveId,
                                questionPreview = questionPreview
                            )
                            return@withContext true
                        }
                    }
                }
            }

            if (!suppressFailureUi && deliveredSolveId != solveId && activeSolveId == solveId) {
                isScanning = false
                btnWindowScan?.text = "Solve"
                setAnswerUI("RETRY", "#BE123C", loading = false, triggerFeedback = false)
            }
            return@withContext (deliveredSolveId == solveId)
    }

    /**
     * Continuous High-RAM Keep-Alive Loop: keeps WebView renderer & V8 timers active in RAM
     * without ever reading old DOM turns or overwriting the Dynamic Island with a previous answer.
     */
    private fun startHighRamKeepAliveAndSync() {
        if (highRamSyncJob?.isActive == true) return
        highRamSyncJob = serviceScope.launch(Dispatchers.Main) {
            while (isActive) {
                geminiWebView?.onResume()
                geminiWebView?.resumeTimers()
                delay(1200)
            }
        }
    }

    private fun setAnswerUI(
        text: String,
        colorHex: String,
        loading: Boolean = false,
        triggerFeedback: Boolean = true
    ) {
        mainHandler.post {
            val normalized = text.trim()
            if (isCamouflageEnabled) {
                ivIslandIcon?.setImageResource(R.drawable.ic_battery_camouflage)
                llIslandExplanationDrawer?.visibility = View.GONE
                val batteryNow = getDeviceBatteryPercentage()
                if (loading) {
                    tvIslandTitle?.text = "Charging"
                    val loadingCamo = "${batteryNow}%"
                    tvAnswerDisplay?.text = loadingCamo
                    tvAnswerDisplay?.textSize = 13f
                    tvAnswerDisplay?.setTextColor(Color.parseColor("#0369A1"))
                    tvWindowAnswerBadge?.text = loadingCamo
                    tvWindowAnswerBadge?.setTextColor(Color.parseColor("#0369A1"))
                } else {
                    val (camoText, isResolved) = formatChargingCamouflage(normalized, batteryNow)
                    tvIslandTitle?.text = "Charging"
                    tvAnswerDisplay?.text = camoText
                    tvAnswerDisplay?.textSize = if (camoText.length <= 4) 13.5f else 11.5f
                    val badgeColor = Color.parseColor(if (isResolved) "#047857" else "#0F172A")
                    tvAnswerDisplay?.setTextColor(badgeColor)
                    tvWindowAnswerBadge?.text = camoText
                    tvWindowAnswerBadge?.setTextColor(badgeColor)
                }
            } else {
                ivIslandIcon?.setImageResource(R.drawable.ic_prism_orb)
                if (loading) {
                    tvIslandTitle?.text = "Solving..."
                    tvAnswerDisplay?.text = normalized
                    tvAnswerDisplay?.textSize = 14f
                    tvAnswerDisplay?.setTextColor(Color.parseColor(colorHex))
                    tvWindowAnswerBadge?.text = normalized
                    tvWindowAnswerBadge?.setTextColor(Color.parseColor(colorHex))
                } else {
                    if (isValidAnswerBadge(normalized)) {
                        val displayVal = if (normalized.length == 1) normalized.uppercase() else normalized
                        val hasDrawerInfo = !tvIslandExplanation?.text.isNullOrBlank()
                        tvIslandTitle?.text = if (hasDrawerInfo) "Answer • Info" else "Answer"
                        tvAnswerDisplay?.text = displayVal
                        tvAnswerDisplay?.textSize = when {
                            displayVal.length <= 2 -> 15f
                            displayVal.length <= 7 -> 12.5f
                            else -> 10.5f
                        }
                        tvAnswerDisplay?.setTextColor(Color.parseColor("#047857"))
                        tvWindowAnswerBadge?.text = displayVal
                        tvWindowAnswerBadge?.setTextColor(Color.parseColor("#047857"))
                    } else {
                        tvIslandTitle?.text = "Tap to Solve"
                        tvAnswerDisplay?.text = normalized
                        tvAnswerDisplay?.textSize = 14f
                        tvAnswerDisplay?.setTextColor(Color.parseColor(colorHex))
                        tvWindowAnswerBadge?.text = normalized
                        tvWindowAnswerBadge?.setTextColor(Color.parseColor(colorHex))
                    }
                }
            }

            if (loading) {
                startScanBreathingAnim()
            } else {
                stopScanBreathingAnim()
                ivScanIcon?.setImageResource(android.R.drawable.ic_media_play)
                isScanning = false
                if (triggerFeedback && isValidAnswerBadge(normalized)) {
                    animateAnswerPop(tvAnswerDisplay)
                    animateAnswerPop(tvWindowAnswerBadge)
                    triggerHapticFeedback(normalized)
                }
            }

            try { bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) } } catch (_: Exception) {}
        }
    }

    /**
     * Silent Haptic Vibration Feedback:
     * 1..6 short pulses = A..F (1 pulse fallback for True/False/numerical answers)
     */
    private fun triggerHapticFeedback(answerLetter: String) {
        if (!isHapticFeedbackEnabled) return
        val pulseCount = when (answerLetter.trim().uppercase()) {
            "A" -> 1
            "B" -> 2
            "C" -> 3
            "D" -> 4
            "E" -> 5
            "F" -> 6
            else -> if (isValidAnswerBadge(answerLetter)) 1 else 0
        }
        if (pulseCount == 0) return

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Crisp modern haptic composition
                val composition = VibrationEffect.startComposition()
                for (i in 0 until pulseCount) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 0)
                    if (i < pulseCount - 1) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f, 110)
                    }
                }
                vibrator.vibrate(composition.compose())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = mutableListOf<Long>(0)
                val pulseMs = 80L
                val pauseMs = 120L
                for (i in 0 until pulseCount) {
                    timings.add(pulseMs)
                    if (i < pulseCount - 1) timings.add(pauseMs)
                }
                val effect = VibrationEffect.createWaveform(timings.toLongArray(), -1)
                vibrator.vibrate(effect)
            } else {
                val timings = mutableListOf<Long>(0)
                for (i in 0 until pulseCount) {
                    timings.add(80L)
                    if (i < pulseCount - 1) timings.add(120L)
                }
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings.toLongArray(), -1)
            }
            Log.d(TAG, "Haptic feedback delivered for option $answerLetter ($pulseCount pulses)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deliver haptic feedback: ${e.message}")
        }
    }

    // ─── Auto-Click Bot Logic ─────────────────────────────────────────────────

    private fun updateBotToggleUI() {
        mainHandler.post {
            if (isAutoClickEnabled) {
                btnBotToggle?.setColorFilter(Color.parseColor("#047857")) // Deep emerald = active
            } else {
                btnBotToggle?.setColorFilter(Color.parseColor("#1E293B")) // Dark slate = inactive
            }
        }
    }

    private fun startAutoClickBot() {
        if (autoClickJob?.isActive == true) return
        Log.d(TAG, "Auto-click bot STARTED (interval=${scanIntervalMs}ms, nextDelay=${nextDelayMs}ms, instantTrigger=$isInstantTriggerEnabled)")
        autoClickJob = serviceScope.launch {
            while (isActive && isAutoClickEnabled) {
                if (!isScanning) {
                    triggerSolveFlow(autoClickMode = true)
                }
                // When instant trigger is active and waiting for new question, check rapidly (800ms)
                val checkInterval = if (isInstantTriggerEnabled && lastSolvedQuestionSignature.isNotEmpty()) {
                    800L
                } else {
                    scanIntervalMs
                }
                delay(checkInterval)
            }
        }
    }

    private fun stopAutoClickBot() {
        autoClickJob?.cancel()
        autoClickJob = null
        Log.d(TAG, "Auto-click bot STOPPED")
    }

    /**
     * Intelligently locates the option on screen corresponding to the answer letter (A..F).
     */
    private fun findOptionTargetRect(targetLetter: String, textBlocks: List<Pair<String, Rect>>): Rect? {
        val letter = targetLetter.uppercase().trim()
        val numStr = when (letter) {
            "A" -> "1"; "B" -> "2"; "C" -> "3"; "D" -> "4"; "E" -> "5"; "F" -> "6"
            else -> ""
        }

        val allLetters = listOf("A", "B", "C", "D", "E", "F")
        val allNumbers = listOf("1", "2", "3", "4", "5", "6")

        // Screen geometry cutoffs to avoid top browser bar and bottom system bar
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val topCutoff = (screenHeight * 0.08).toInt()

        // Filter out blocks in top 8% of screen (browser URL bar area)
        val validBlocks = textBlocks.filter { it.second.top >= topCutoff }

        fun getCandidates(keys: List<String>): Map<String, List<Rect>> {
            val map = mutableMapOf<String, MutableList<Rect>>()
            for (k in keys) map[k] = mutableListOf()

            for (block in validBlocks) {
                val t = block.first.trim()
                for (k in keys) {
                    val isMatch = t.matches(Regex("""^\s*\(?${k}\s*[.)\]:-].*""", RegexOption.IGNORE_CASE)) ||
                                  t.matches(Regex("""^\s*Option\s*${k}\b.*""", RegexOption.IGNORE_CASE)) ||
                                  t.matches(Regex("""^\s*\(?${k}\)?\s*$""", RegexOption.IGNORE_CASE))
                    if (isMatch) {
                        map[k]?.add(block.second)
                    }
                }
            }
            return map
        }

        val letterMap = getCandidates(allLetters)
        val numberMap = if (numStr.isNotEmpty()) getCandidates(allNumbers) else emptyMap()

        val letterCount = allLetters.count { (letterMap[it]?.size ?: 0) > 0 }
        val numberCount = allNumbers.count { (numberMap[it]?.size ?: 0) > 0 }

        val useNumbers = (numberCount > letterCount && numberCount >= 2)
        val activeMap = if (useNumbers) numberMap else letterMap
        val activeKey = if (useNumbers) numStr else letter

        val candidates = activeMap[activeKey] ?: emptyList()
        if (candidates.isNotEmpty()) {
            if (candidates.size == 1) return candidates.first()

            // When multiple candidates exist, pick the one vertically aligned with the option cluster
            val otherCandidates = activeMap.filter { it.key != activeKey }.values.flatten()
            if (otherCandidates.isNotEmpty()) {
                val avgX = otherCandidates.map { it.left }.average()
                return candidates.minByOrNull { Math.abs(it.left - avgX) } ?: candidates.first()
            }
            return candidates.first()
        }

        // Secondary fallback: "Option X" anywhere in valid text
        for (block in validBlocks) {
            val t = block.first.trim()
            if (t.contains(Regex("""\bOption\s*${letter}\b""", RegexOption.IGNORE_CASE))) {
                return block.second
            }
        }

        // Tertiary fallback: Starts with letter on its own
        for (block in validBlocks) {
            val t = block.first.trim()
            if (t.matches(Regex("""^${letter}\b.*""", RegexOption.IGNORE_CASE))) {
                return block.second
            }
        }

        return null
    }

    /**
     * Find the bounding box for the correct answer option and tap it via AccessibilityService.
     */
    private fun performAutoClick(answerLetter: String, textBlocks: List<Pair<String, Rect>>) {
        val targetRect = findOptionTargetRect(answerLetter, textBlocks)

        if (targetRect == null) {
            Log.w(TAG, "Could not locate option $answerLetter on screen — text blocks: ${textBlocks.size}")
            val msg = if (isCamouflageEnabled) "🔋 Battery: ${when(answerLetter){"A"->91;"B"->92;"C"->93;"D"->94;else->90}}%" else "🤖 Answer: $answerLetter (could not locate to click)"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }

        serviceScope.launch {
            // Natural human delay: 350ms - 650ms if anti-cheat, 50ms otherwise
            val readingDelay = if (isAntiCheatEnabled) {
                350L + kotlin.random.Random.nextLong(300L)
            } else {
                50L
            }
            delay(readingDelay)

            // Click coordinate calculation:
            // For wide blocks (full question option row), the radio button/checkbox circle is on the left side (~24dp in).
            // For narrow badges (a circular button or letter badge), use horizontal center.
            val clickX = if (targetRect.width() > dpToPx(70)) {
                (targetRect.left + dpToPx(24).toFloat()).coerceIn(targetRect.left + 4f, targetRect.right - 4f)
            } else {
                targetRect.centerX().toFloat()
            }
            val clickY = targetRect.centerY().toFloat()

            Log.d(TAG, "Auto-clicking option $answerLetter at ($clickX, $clickY) [rect=$targetRect]")
            val clicked = AutoClickAccessibilityService.performClick(clickX, clickY, antiCheat = isAntiCheatEnabled)
            withContext(Dispatchers.Main) {
                if (clicked) {
                    val successMsg = if (isCamouflageEnabled) "🔋 Option Synced" else "🤖 Selected: $answerLetter ✅"
                    Toast.makeText(this@OverlayService, successMsg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@OverlayService, "⚠️ Answer: $answerLetter — Enable Accessibility Service to auto-click", Toast.LENGTH_LONG).show()
                }
            }

            // AEI AFK Hands-Free Auto-Advance ("Save & Next" / "Submit" / "Next")
            if (clicked && isAfkAutoAdvanceEnabled) {
                val delayTime = if (isAntiCheatEnabled) {
                    (nextDelayMs + kotlin.random.Random.nextLong(300L) - 150L).coerceAtLeast(300L)
                } else {
                    nextDelayMs
                }
                delay(delayTime)

                val nextRect = findNextButtonRect(textBlocks)
                if (nextRect != null) {
                    val nextClickX = nextRect.centerX().toFloat()
                    val nextClickY = nextRect.centerY().toFloat()
                    Log.d(TAG, "AEI AFK Bot: Auto-advancing via button at ($nextClickX, $nextClickY)")
                    val nextClicked = AutoClickAccessibilityService.performClick(nextClickX, nextClickY, antiCheat = isAntiCheatEnabled)
                    withContext(Dispatchers.Main) {
                        if (nextClicked) {
                            Toast.makeText(this@OverlayService, "🤖 Auto-Advanced ⏩", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.d(TAG, "AEI AFK Bot: Next/Submit button not found on screen.")
                }
            }
        }
    }

    /**
     * Find "Save & Next", "Next Question", "Submit", "Continue" button on screen.
     * Excludes final destructive actions like "Finish Exam", "Submit Test".
     */
    private fun findNextButtonRect(textBlocks: List<Pair<String, Rect>>): Rect? {
        val excludePattern = Regex("""\b(Finish\s*Test|Finish\s*Exam|End\s*Test|End\s*Exam|Exit|Quit|Cancel|Close|Previous|Prev|Back)\b""", RegexOption.IGNORE_CASE)

        val p1 = Regex("""\bSave\s*(?:&|and)\s*(?:Next|Continue)\b""", RegexOption.IGNORE_CASE)
        val p2 = Regex("""\b(?:Next\s*Question|Submit\s*(?:&|and)\s*Next|Submit\s*Answer)\b""", RegexOption.IGNORE_CASE)
        val p3 = Regex("""^\s*(?:Next|Submit|Continue|Proceed|Confirm)\s*[$>→»]?\s*$""", RegexOption.IGNORE_CASE)
        val p4 = Regex("""\b(?:Next|Submit|Continue|Proceed)\b""", RegexOption.IGNORE_CASE)
        val p5 = Regex("""^[>→»]$""")

        val screenHeight = resources.displayMetrics.heightPixels

        // Prioritize bottom-half of screen where navigation/submit buttons reside
        val candidateBlocks = textBlocks.filter { !excludePattern.containsMatchIn(it.first) }
            .sortedByDescending { it.second.top }

        // 1. High-priority "Save & Next" / "Save & Continue"
        for (block in candidateBlocks) {
            if (p1.containsMatchIn(block.first)) return block.second
        }
        // 2. "Next Question" / "Submit & Next" / "Submit Answer"
        for (block in candidateBlocks) {
            if (p2.containsMatchIn(block.first)) return block.second
        }
        // 3. Exact "Next" / "Submit" / "Continue"
        for (block in candidateBlocks) {
            if (p3.containsMatchIn(block.first)) return block.second
        }
        // 4. Any words containing Next / Submit / Continue
        for (block in candidateBlocks) {
            if (p4.containsMatchIn(block.first)) return block.second
        }
        // 5. Arrow icon symbols (>, →, ») near bottom
        for (block in candidateBlocks) {
            if (p5.matches(block.first.trim()) && block.second.top > screenHeight * 0.45) return block.second
        }
        return null
    }

    // ─── Touch Listeners with Fluid Jelly Physics ──────────────────────────────

    private fun createDragListener(
        params: WindowManager.LayoutParams,
        viewProvider: () -> View?
    ): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var startX = 0; private var startY = 0
            private var rawX = 0f;  private var rawY = 0f
            private var lastRawX = 0f; private var lastRawY = 0f

            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                val targetView = viewProvider()
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        rawX = e.rawX; rawY = e.rawY
                        lastRawX = e.rawX; lastRawY = e.rawY
                        targetView?.animate()?.cancel()
                        targetView?.animate()
                            ?.scaleX(0.98f)
                            ?.scaleY(0.98f)
                            ?.setDuration(120)
                            ?.setInterpolator(DecelerateInterpolator())
                            ?.start()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val moveDx = e.rawX - lastRawX
                        val moveDy = e.rawY - lastRawY
                        lastRawX = e.rawX; lastRawY = e.rawY

                        val dx = (e.rawX - rawX).toInt()
                        val dy = (e.rawY - rawY).toInt()
                        params.x = startX + dx
                        params.y = startY + dy
                        try {
                            targetView?.let { windowManager.updateViewLayout(it, params) }
                        } catch (_: Exception) {}

                        // Subtle velocity tilt and dynamic stretching on dragging window
                        if (targetView != null) {
                            val tilt = (moveDx * 0.08f).coerceIn(-6f, 6f)
                            targetView.rotation = tilt
                            targetView.scaleX = (0.98f + Math.abs(moveDx) * 0.002f).coerceIn(0.96f, 1.02f)
                            targetView.scaleY = (0.98f + Math.abs(moveDy) * 0.002f).coerceIn(0.96f, 1.02f)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        targetView?.animate()
                            ?.scaleX(1.0f)
                            ?.scaleY(1.0f)
                            ?.rotation(0f)
                            ?.setDuration(350)
                            ?.setInterpolator(OvershootInterpolator(2.2f))
                            ?.start()
                    }
                }
                return true
            }
        }
    }

    private fun createDragAndTapListener(
        params: WindowManager.LayoutParams,
        viewProvider: () -> View?,
        onSingleTap: () -> Unit,
        onDoubleTap: () -> Unit
    ): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var startX = 0; private var startY = 0
            private var rawX = 0f;  private var rawY = 0f
            private var lastRawX = 0f; private var lastRawY = 0f
            private var dragging = false
            private var t0 = 0L
            private var lastTapTime = 0L
            private val doubleTapTimeout = 300L
            private var pendingSingleTapRunnable: Runnable? = null

            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                val targetView = v ?: viewProvider()
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        rawX = e.rawX; rawY = e.rawY
                        lastRawX = e.rawX; lastRawY = e.rawY
                        dragging = false
                        t0 = System.currentTimeMillis()

                        // Tactile Jelly Squish on Touch Down
                        targetView?.animate()?.cancel()
                        targetView?.animate()
                            ?.scaleX(0.90f)
                            ?.scaleY(0.90f)
                            ?.setDuration(120)
                            ?.setInterpolator(DecelerateInterpolator())
                            ?.start()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val moveDx = e.rawX - lastRawX
                        val moveDy = e.rawY - lastRawY
                        lastRawX = e.rawX; lastRawY = e.rawY

                        val totalDx = (e.rawX - rawX).toInt()
                        val totalDy = (e.rawY - rawY).toInt()
                        if (Math.abs(totalDx) > 8 || Math.abs(totalDy) > 8) dragging = true

                        params.x = startX + totalDx
                        val safeMinY = getStatusBarHeight()
                        params.y = (startY + totalDy).coerceAtLeast(safeMinY)
                        try {
                            viewProvider()?.let { windowManager.updateViewLayout(it, params) }
                        } catch (_: Exception) {}

                        if (dragging && targetView != null) {
                            // Dynamic jelly tilt and directional velocity stretching
                            val tilt = (moveDx * 0.25f).coerceIn(-14f, 14f)
                            targetView.rotation = tilt

                            val stretchX = (1.0f + Math.abs(moveDx) * 0.004f).coerceIn(0.88f, 1.15f)
                            val stretchY = (1.0f + Math.abs(moveDy) * 0.004f).coerceIn(0.88f, 1.15f)
                            targetView.scaleX = stretchX
                            targetView.scaleY = stretchY
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Drag-to-dismiss check: if dragged to the bottom 10% of screen, minimize overlay
                        val screenH = resources.displayMetrics.heightPixels
                        if (dragging && params.y > (screenH * 0.88)) {
                            targetView?.animate()
                                ?.scaleX(0f)
                                ?.scaleY(0f)
                                ?.alpha(0f)
                                ?.setDuration(240)
                                ?.withEndAction {
                                    stopSelf()
                                }
                                ?.start()
                            Toast.makeText(this@OverlayService, "Prism AI Island Closed", Toast.LENGTH_SHORT).show()
                            return true
                        }

                        // Decaying Elastic Wobble Spring Rebound upon finger release
                        targetView?.animate()
                            ?.scaleX(1.0f)
                            ?.scaleY(1.0f)
                            ?.rotation(0f)
                            ?.setDuration(450)
                            ?.setInterpolator(OvershootInterpolator(3.5f))
                            ?.start()

                        if (e.action == MotionEvent.ACTION_UP) {
                            val pressDuration = System.currentTimeMillis() - t0
                            if (!dragging && pressDuration < 350) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime < doubleTapTimeout) {
                                    // Double tap detected
                                    pendingSingleTapRunnable?.let { mainHandler.removeCallbacks(it) }
                                    pendingSingleTapRunnable = null
                                    lastTapTime = 0L
                                    onDoubleTap()
                                } else {
                                    lastTapTime = now
                                    pendingSingleTapRunnable?.let { mainHandler.removeCallbacks(it) }
                                    val runnable = Runnable {
                                        onSingleTap()
                                        pendingSingleTapRunnable = null
                                    }
                                    pendingSingleTapRunnable = runnable
                                    mainHandler.postDelayed(runnable, doubleTapTimeout)
                                }
                            }
                        }
                    }
                }
                return true
            }
        }
    }

    // ─── Animation Helpers: Scanning Breath & Answer Spring Pop ───────────────

    private var scanBreathingAnimator: ObjectAnimator? = null

    private fun startScanBreathingAnim() {
        val target = ivScanIcon ?: return
        if (scanBreathingAnimator?.isRunning == true) return

        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.25f, 1.0f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.25f, 1.0f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f, 0.6f, 1.0f)

        scanBreathingAnimator = ObjectAnimator.ofPropertyValuesHolder(target, scaleX, scaleY, alpha).apply {
            duration = 750
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopScanBreathingAnim() {
        scanBreathingAnimator?.cancel()
        scanBreathingAnimator = null
        ivScanIcon?.let {
            it.scaleX = 1.0f
            it.scaleY = 1.0f
            it.alpha = 1.0f
        }
    }

    private fun animateAnswerPop(view: View?) {
        if (view == null) return
        view.scaleX = 0.35f
        view.scaleY = 0.35f
        view.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(2.8f))
            .start()
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dpToPx(36)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Prism AI Overlay", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Prism AI Liquid Glass Dynamic Island solver" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Prism AI Island Active")
            .setContentText("Auto-fresh Gemini solver ready. Tap the Dynamic Island to solve.")
            .setSmallIcon(R.drawable.ic_prism_orb)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
