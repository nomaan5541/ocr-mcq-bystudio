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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
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
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
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
import com.omnisolve.overlay.api.CircleToSearchEngine
import com.omnisolve.overlay.api.DirectVisionSolverEngine
import com.omnisolve.overlay.api.GeminiVisionClient
import com.omnisolve.overlay.api.GhostDomSolverEngine
import com.omnisolve.overlay.api.MultiProviderApiSolver
import com.omnisolve.overlay.api.OnlineConsensusRaceEngine
import com.omnisolve.overlay.api.PrismAiSolverEngine
import com.omnisolve.overlay.capture.OcrEngine
import com.omnisolve.overlay.capture.ScreenCaptureManager
import com.omnisolve.overlay.data.FirebaseTelemetryRepository
import com.omnisolve.overlay.data.SolvedQuestionRecord
import com.omnisolve.overlay.data.SolvedQuestionRepository
import com.omnisolve.overlay.data.TelemetryBackend
import com.omnisolve.overlay.data.TelemetryFeature
import com.omnisolve.overlay.data.TelemetrySessionSource
import com.omnisolve.overlay.data.TelemetryStatus
import com.omnisolve.overlay.f1.F1FailureCategory
import com.omnisolve.overlay.f1.F1MasterEngine
import com.omnisolve.overlay.f1.F1ProviderHealthManager
import com.omnisolve.overlay.f1.F1RaceMode
import com.omnisolve.overlay.f1.F1SolveRequest
import com.omnisolve.overlay.f1.F1WebsiteFallbackAdapter
import com.omnisolve.overlay.firebase.configureAppCheck
import com.omnisolve.overlay.firebase.getAppCheckDiagnosticStatus
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.EnginePerformanceMode
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.ui.DynamicIslandBorderDrawable
import com.omnisolve.overlay.util.PerformanceBenchmarkTracker
import com.omnisolve.overlay.util.PrismPerfLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import java.util.regex.Pattern
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

class OverlayService : Service() {

    internal enum class IslandTouchAction {
        OPEN_METHOD_DRAWER,
        CLOSE_METHOD_DRAWER,
        HISTORY_NEXT,
        HISTORY_PREV,
        DISMISS,
        TAP,
        REPOSITION
    }

    companion object {
        private const val TAG = "OverlayService"
        const val CHANNEL_ID = "OmniSolveOverlayChannel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_DISPLAY_MODE = "EXTRA_DISPLAY_MODE"
        const val EXTRA_SCAN_INTERVAL_SEC = "EXTRA_SCAN_INTERVAL_SEC"
        const val EXTRA_NEXT_DELAY_MS = "EXTRA_NEXT_DELAY_MS"
        const val EXTRA_INSTANT_TRIGGER = "EXTRA_INSTANT_TRIGGER"
        const val EXTRA_EXTRACTION_ENGINE = "EXTRA_EXTRACTION_ENGINE"
        const val EXTRA_SOLVER_METHOD = "EXTRA_SOLVER_METHOD"
        const val EXTRA_HUD_STYLE = "EXTRA_HUD_STYLE"
        const val EXTRA_CAMOUFLAGE = "EXTRA_CAMOUFLAGE"
        const val EXTRA_HAPTIC = "EXTRA_HAPTIC"
        const val EXTRA_THEME_MODE = "EXTRA_THEME_MODE"
        const val EXTRA_ISLAND_OPACITY_INDEX = "EXTRA_ISLAND_OPACITY_INDEX"
        const val EXTRA_CUSTOM_OPACITY_PCT = "EXTRA_CUSTOM_OPACITY_PCT"
        const val EXTRA_ISLAND_ACCENT_THEME = "EXTRA_ISLAND_ACCENT_THEME"
        const val EXTRA_ISLAND_CUSTOM_HEX = "EXTRA_ISLAND_CUSTOM_HEX"
        const val EXTRA_ISLAND_LIGHT_MODE = "EXTRA_ISLAND_LIGHT_MODE"
        const val EXTRA_ISLAND_SURFACE_HEX = "EXTRA_ISLAND_SURFACE_HEX"
        const val EXTRA_ISLAND_SIZE_PRESET = "EXTRA_ISLAND_SIZE_PRESET"
        const val EXTRA_ISLAND_SCALE_PCT = "EXTRA_ISLAND_SCALE_PCT"
        const val EXTRA_ISLAND_ANSWER_SCALE = "EXTRA_ISLAND_ANSWER_SCALE"
        const val EXTRA_ISLAND_EDGE_SNAP = "EXTRA_ISLAND_EDGE_SNAP"
        const val EXTRA_ISLAND_IDLE_FADE_SEC = "EXTRA_ISLAND_IDLE_FADE_SEC"
        const val EXTRA_SPRING_DAMPING = "EXTRA_SPRING_DAMPING"
        const val EXTRA_SPRING_STIFFNESS = "EXTRA_SPRING_STIFFNESS"
        const val EXTRA_FLUID_ANIMATIONS = "EXTRA_FLUID_ANIMATIONS"
        const val EXTRA_ISLAND_WIDTH_DP = "EXTRA_ISLAND_WIDTH_DP"
        const val EXTRA_ISLAND_HEIGHT_DP = "EXTRA_ISLAND_HEIGHT_DP"
        const val EXTRA_ISLAND_CORNER_RADIUS_DP = "EXTRA_ISLAND_CORNER_RADIUS_DP"
        const val EXTRA_ISLAND_ANIM_SPEED = "EXTRA_ISLAND_ANIM_SPEED"
        const val EXTRA_ISLAND_CUTOUT_ALIGN = "EXTRA_ISLAND_CUTOUT_ALIGN"
        const val EXTRA_ISLAND_CUTOUT_OFFSET_X_DP = "EXTRA_ISLAND_CUTOUT_OFFSET_X_DP"
        const val EXTRA_ISLAND_CUTOUT_OFFSET_Y_DP = "EXTRA_ISLAND_CUTOUT_OFFSET_Y_DP"
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
                svc.onUserTapSolve()
            }
            return true
        }

        const val ACTION_NOTIF_SOLVE_NOW = "com.omnisolve.overlay.ACTION_NOTIF_SOLVE_NOW"
        const val ACTION_NOTIF_TOGGLE_STEALTH = "com.omnisolve.overlay.ACTION_NOTIF_TOGGLE_STEALTH"
        const val ACTION_NOTIF_TOGGLE_HIDE_ALL = "com.omnisolve.overlay.ACTION_NOTIF_TOGGLE_HIDE_ALL"
        const val ACTION_NOTIF_STOP = "com.omnisolve.overlay.ACTION_NOTIF_STOP"

        fun isOverlayHidden(): Boolean = instance?.isStealthHiddenAll == true

        fun setOverlayHiddenFromUi(hidden: Boolean): Boolean {
            val svc = instance ?: return false
            svc.mainHandler.post {
                svc.setOverlayHidden(hidden)
            }
            return true
        }
        const val ACTION_PIN_FORMS_KEY = "com.omnisolve.overlay.ACTION_PIN_FORMS_KEY"
        const val ACTION_UNPIN_FORMS_KEY = "com.omnisolve.overlay.ACTION_UNPIN_FORMS_KEY"
        const val EXTRA_FORMS_COMPACT_KEY = "EXTRA_FORMS_COMPACT_KEY"
        const val EXTRA_FORMS_TITLE = "EXTRA_FORMS_TITLE"
        const val EXTRA_FORMS_BREAKDOWN = "EXTRA_FORMS_BREAKDOWN"

        /**
         * Converts an answer badge (A..F, multi-select "A, C", True/False) into Network Speed Disguise:
         * 0.91 MB/s = A, 0.92 MB/s = B, 0.93 MB/s = C, 0.94 MB/s = D, 0.95 MB/s = E, 0.96 MB/s = F.
         * Idle/loading states safely return "0.48 MB/s" so "?" or "ERR" never leaks on screen.
         */
        private fun stripConsensusCountPrefix(cleaned: String): String {
            val match = Regex("""^\d+x?\s+(.+)$""", RegexOption.IGNORE_CASE).matchEntire(cleaned)
            return match?.groupValues?.getOrNull(1)?.trim().takeIf { !it.isNullOrBlank() } ?: cleaned
        }

        internal fun formatNetworkSpeedDisguise(rawBadge: String?): Pair<String, Boolean> {
            val idleFallback = "0.48 MB/s"
            val cleaned = stripConsensusCountPrefix(
                rawBadge
                    ?.trim()
                    ?.removePrefix("(")
                    ?.removeSuffix(")")
                    ?.trim()
                    .orEmpty()
            )
            if (cleaned.isEmpty()) return idleFallback to false

            val upper = cleaned.uppercase()
            if (upper in setOf("?", "...", "SCAN", "ERR", "RETRY", "LOGIN", "KEY", "EMPTY", "WAIT", "CAP", "TXT")) {
                return idleFallback to false
            }
            if (upper == "TRUE") return "0.91 MB/s" to true
            if (upper == "FALSE") return "0.92 MB/s" to true

            if (upper.length == 1 && upper[0] in 'A'..'F') {
                val code = 91 + (upper[0] - 'A')
                return "0.${code} MB/s" to true
            }

            val parts = upper.split(Regex("[,/&\\s]+")).filter { it.isNotBlank() }
            if (parts.size >= 2 && parts.all { it.length == 1 && it[0] in 'A'..'F' }) {
                val firstCode = 91 + (parts[0][0] - 'A')
                val restCodes = parts.drop(1).joinToString("/") { (91 + (it[0] - 'A')).toString() }
                return "0.${firstCode}/${restCodes} MB/s" to true
            }

            if (cleaned.matches(Regex("^[+-]?\\d+(?:\\.\\d+)?$"))) {
                return "$cleaned MB/s" to true
            }

            return idleFallback to false
        }

        /**
         * Converts an answer badge (A, B, C, D, E, F, multi-select "A, C", True/False, or numeric)
         * into Charging Camouflage numbers (91% = A, 92% = B, 93% = C, 94% = D, 95% = E, 96% = F).
         * Returns Pair(displayBadge, isResolvedAnswer). Idle/error badges ("?", "...", "RETRY", "ERR")
         * safely return the real device battery percentage so "?" or "ERR" never leaks on screen.
         */
        internal fun formatChargingCamouflage(rawBadge: String?, fallbackBatteryPct: Int): Pair<String, Boolean> {
            val safeFallback = "${fallbackBatteryPct.coerceIn(1, 100)}%"
            val cleaned = stripConsensusCountPrefix(
                rawBadge
                    ?.trim()
                    ?.removePrefix("(")
                    ?.removeSuffix(")")
                    ?.trim()
                    .orEmpty()
            )

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

        internal data class SafeOverlayBounds(
            val minX: Int,
            val maxX: Int,
            val minY: Int,
            val maxY: Int
        )

        internal fun computeSafeOverlayBounds(
            usableWidthPx: Int,
            usableHeightPx: Int,
            viewWidthPx: Int,
            viewHeightPx: Int,
            gravity: Int,
            edgeMarginPx: Int,
            topBufferPx: Int
        ): SafeOverlayBounds {
            val safeW = usableWidthPx.coerceAtLeast(1)
            val safeH = usableHeightPx.coerceAtLeast(1)
            val margin = edgeMarginPx.coerceAtLeast(0)
            val topBuf = topBufferPx.coerceAtLeast(0)
            val maxAllowedW = (safeW - margin * 2).coerceAtLeast(1)
            val maxAllowedH = (safeH - topBuf - margin).coerceAtLeast(1)
            val vw = viewWidthPx.coerceIn(1, maxAllowedW)
            val vh = viewHeightPx.coerceIn(1, maxAllowedH)

            val isCenteredH = (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL
            val minX: Int
            val maxX: Int
            if (isCenteredH) {
                val maxOffset = ((safeW - vw) / 2 - margin).coerceAtLeast(0)
                minX = -maxOffset
                maxX = maxOffset
            } else {
                minX = margin
                maxX = (safeW - vw - margin).coerceAtLeast(minX)
            }

            val minY = topBuf
            val maxY = (safeH - vh - margin).coerceAtLeast(minY)
            return SafeOverlayBounds(minX = minX, maxX = maxX, minY = minY, maxY = maxY)
        }

        internal fun computeClampedOverlayPosition(
            candidateX: Int,
            candidateY: Int,
            usableWidthPx: Int,
            usableHeightPx: Int,
            viewWidthPx: Int,
            viewHeightPx: Int,
            gravity: Int,
            edgeMarginPx: Int,
            topBufferPx: Int
        ): Pair<Int, Int> {
            val bounds = computeSafeOverlayBounds(
                usableWidthPx = usableWidthPx,
                usableHeightPx = usableHeightPx,
                viewWidthPx = viewWidthPx,
                viewHeightPx = viewHeightPx,
                gravity = gravity,
                edgeMarginPx = edgeMarginPx,
                topBufferPx = topBufferPx
            )
            return candidateX.coerceIn(bounds.minX, bounds.maxX) to
                candidateY.coerceIn(bounds.minY, bounds.maxY)
        }

        internal fun classifyIslandReleaseTouch(
            isActionUp: Boolean,
            wasDragging: Boolean,
            durationMs: Long,
            dxPx: Float,
            dyPx: Float,
            verticalFlickMinPx: Float,
            verticalFlickMaxPx: Float,
            horizontalSwipeMinPx: Float,
            swipeLoopEnabled: Boolean,
            methodDrawerOpen: Boolean,
            horizontalHistoryEnabled: Boolean,
            candidateY: Int,
            bottomDismissThresholdY: Int
        ): IslandTouchAction {
            if (isActionUp && durationMs < 290L) {
                if (swipeLoopEnabled &&
                    dyPx > verticalFlickMinPx &&
                    dyPx < verticalFlickMaxPx &&
                    kotlin.math.abs(dyPx) > kotlin.math.abs(dxPx) * 1.45f
                ) {
                    return IslandTouchAction.OPEN_METHOD_DRAWER
                }
                if (methodDrawerOpen &&
                    dyPx < -verticalFlickMinPx &&
                    dyPx > -verticalFlickMaxPx &&
                    kotlin.math.abs(dyPx) > kotlin.math.abs(dxPx) * 1.45f
                ) {
                    return IslandTouchAction.CLOSE_METHOD_DRAWER
                }
                if (horizontalHistoryEnabled &&
                    kotlin.math.abs(dxPx) > horizontalSwipeMinPx &&
                    kotlin.math.abs(dxPx) > kotlin.math.abs(dyPx) * 1.35f
                ) {
                    return if (dxPx < 0f) IslandTouchAction.HISTORY_NEXT else IslandTouchAction.HISTORY_PREV
                }
            }
            if (wasDragging && candidateY > bottomDismissThresholdY) {
                return IslandTouchAction.DISMISS
            }
            if (isActionUp && !wasDragging && durationMs < 350L) {
                return IslandTouchAction.TAP
            }
            return IslandTouchAction.REPOSITION
        }
    }

    private lateinit var windowManager: WindowManager

    // HUD & Solver Configuration
    private var extractionEngine = MainActivity.ENGINE_OCR
    private var solverMethod: SolverMethod = SolverMethod.BACKGROUND_GEMINI
    private var hudStyle = MainActivity.HUD_STYLE_ISLAND
    private var isCamouflageEnabled = false
    private var isHapticFeedbackEnabled = true
    private var scanIntervalMs = 3000L
    private var nextDelayMs = 1500L
    private var isInstantTriggerEnabled = true
    private var themeMode = MainActivity.THEME_DARK
    private var islandAccentTheme = DynamicIslandBorderDrawable.ACCENT_CRIMSON
    private var islandCustomHex = ""
    private var islandLightMode = false
    private var islandSurfaceHex = "#121316"
    private var islandIdleFadeSec = 4
    private var islandEdgeSnap = true
    private var islandScalePct = 100

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

    // Dynamic Island & Dynamic Flow Bar view references
    private var tvIslandTitle: TextView? = null
    private var ivIslandIcon: ImageView? = null
    private var ivBubbleIcon: ImageView? = null
    private var llIslandBodyView: View? = null
    private var llIslandInlineTools: LinearLayout? = null
    private var btnIslandToolScan: ImageView? = null
    private var btnIslandToolCrop: ImageView? = null
    private var btnIslandToolResolve: ImageView? = null
    private var btnIslandToolMode: ImageView? = null
    private var btnIslandToolMethods: ImageView? = null
    private var btnIslandToolExplain: ImageView? = null
    private var btnIslandToolStop: ImageView? = null
    private var isInlineToolsVisible = false
    private val inlineToolsAutoHideRunnable = Runnable {
        if (isInlineToolsVisible) {
            setInlineIslandToolsVisible(false)
        }
    }
    private var llIslandExplanationDrawer: View? = null
    private var tvIslandMeta: TextView? = null
    private var tvIslandExplanation: TextView? = null
    private var tvIslandSourceTag: TextView? = null
    private var btnIslandWrongResolve: TextView? = null
    private var lastSolvedCropRect: Rect? = null
    private var lastSolvedQuestionText: String = ""
    private var btnDfPart1: TextView? = null
    private var btnDfPart2: TextView? = null
    private var btnDfScroll2s: TextView? = null
    private var btnDfQuestionMode: TextView? = null
    private var btnDfDiagram: TextView? = null
    private var btnDfCrop: TextView? = null
    private var llMethodLoopDrawer: LinearLayout? = null
    private var svMethodLoop: ScrollView? = null
    private var llMethodLoopItems: LinearLayout? = null
    private var islandBorderDrawable: DynamicIslandBorderDrawable? = null
    private var viewIslandLiveDot: View? = null

    // Start-Time Custom Opacity (20% to 100%, configured on Home Screen & Settings)
    private var customOpacityPct: Int = 100
    private var overlayOpenStartMs: Long = 0L
    private var overlayActiveSessionStartMs: Long = 0L
    private var overlaySessionRequestsCount: Long = 0L

    // Dynamic Flow 2-View Split Capture & 2s Scroll-Capture State
    @Volatile
    private var pendingPartOneText: String? = null
    @Volatile
    private var pendingAuthSignInRequired: Boolean = false
    @Volatile
    private var pendingWebsiteFallbackQuestion: String? = null
    @Volatile
    private var pendingPartOneBitmap: Bitmap? = null
    private var scrollCaptureTimerJob: Job? = null
    private var forceDiagramVisionActive: Boolean = false

    private fun isGoogleSignedIn(): Boolean = try {
        FirebaseApp.getApps(this).isNotEmpty() && Firebase.auth.currentUser != null
    } catch (_: Exception) {
        false
    }

    private fun applyCustomOpacityPct(pct: Int) {
        customOpacityPct = pct.coerceIn(20, 100)
        val targetAlpha = (customOpacityPct / 100f).coerceIn(0.20f, 1.0f)

        bubbleView?.animate()?.cancel()
        bubbleView?.animate()
            ?.alpha(targetAlpha)
            ?.setDuration(200)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()

        try {
            getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .edit().putInt(MainActivity.PREF_KEY_CUSTOM_OPACITY_PCT, customOpacityPct).apply()
        } catch (_: Exception) {}
    }

    // Bubble overlay (Small stealth HUD)
    private var bubbleView: View? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var tvAnswerDisplay: TextView? = null

    // Advanced Overlay State (Crop-to-Scan, Swipe History, Auto-Fade Idle)
    private var cropOverlayView: CropRegionOverlayView? = null
    private var historyBrowseIndex: Int = -1
    private val idleFadeRunnable = Runnable { applyIdleFadeNow() }

    private fun isAdvEnabled(prefKey: String): Boolean {
        return try {
            getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE).getBoolean(prefKey, false)
        } catch (_: Exception) {
            false
        }
    }

    private fun isDfEnabled(prefKey: String, defaultValue: Boolean): Boolean {
        return try {
            getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE).getBoolean(prefKey, defaultValue)
        } catch (_: Exception) {
            defaultValue
        }
    }

    private fun applyIdleFadeNow() {
        if (!isAdvEnabled(MainActivity.PREF_KEY_ADV_AUTO_FADE_IDLE) || isScanning) return
        val target = bubbleView ?: return
        target.animate().cancel()
        target.animate()
            .alpha(0.16f)
            .setDuration(320L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun wakeAndScheduleIdleFade() {
        mainHandler.removeCallbacks(idleFadeRunnable)
        val target = bubbleView
        if (target != null) {
            val normalAlpha = (customOpacityPct.coerceIn(20, 100) / 100f)
            if (target.alpha < normalAlpha - 0.04f || target.alpha > normalAlpha + 0.04f) {
                target.animate().cancel()
                target.animate()
                    .alpha(normalAlpha)
                    .setDuration(160L)
                    .start()
            }
        }
        if (isAdvEnabled(MainActivity.PREF_KEY_ADV_AUTO_FADE_IDLE) && !isScanning && islandIdleFadeSec > 0) {
            mainHandler.postDelayed(idleFadeRunnable, (islandIdleFadeSec.coerceIn(2, 20) * 1000L))
        }
    }

    private fun showCropRegionSelector() {
        dismissCropRegionSelector()
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        val view = CropRegionOverlayView(
            context = this,
            onRegionSelected = { selectedRect ->
                dismissCropRegionSelector()
                triggerSolveFlow(cropScreenRect = selectedRect)
            },
            onCancelled = {
                dismissCropRegionSelector()
            }
        )
        cropOverlayView = view
        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show CropRegionOverlayView", e)
            cropOverlayView = null
            triggerSolveFlow(cropScreenRect = null)
        }
    }

    private fun dismissCropRegionSelector() {
        val view = cropOverlayView ?: return
        cropOverlayView = null
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
    }

    private fun browseSolvedHistoryOnPill(delta: Int) {
        val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("PREF_SOLVE_HISTORY", "")?.trim().orEmpty()
        val entries = raw.lines().mapNotNull { line ->
            val parts = line.split("||")
            if (parts.size >= 3) {
                val badge = parts[1].trim()
                val snippet = parts[2].trim()
                if (badge.isNotEmpty()) badge to snippet else null
            } else null
        }
        if (entries.isEmpty()) {
            Toast.makeText(this, "No solved history yet", Toast.LENGTH_SHORT).show()
            return
        }
        historyBrowseIndex = if (historyBrowseIndex < 0) {
            0
        } else {
            (historyBrowseIndex + delta + entries.size) % entries.size
        }
        val (badge, snippet) = entries[historyBrowseIndex]
        val rankNumber = entries.size - historyBrowseIndex
        tvIslandTitle?.text = "#$rankNumber: ${snippet.take(12)}"
        setAnswerUI(
            text = badge,
            colorHex = "#047857",
            loading = false,
            triggerFeedback = false,
            preserveIslandTitle = true
        )
    }

    private fun cycleSubjectOrEngineOnLongPress() {
        val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        val currentSubj = SubjectMode.fromId(
            prefs.getString(MainActivity.PREF_KEY_SUBJECT_MODE, SubjectMode.GENERAL.id)
        )
        val allSubjects = SubjectMode.entries
        val nextIdx = (allSubjects.indexOf(currentSubj) + 1) % allSubjects.size
        val nextSubj = allSubjects[nextIdx]

        // When wrapping back to General, toggle Direct Text vs Vision OCR cleanly
        if (nextIdx == 0) {
            extractionEngine = if (extractionEngine == MainActivity.ENGINE_OCR) {
                MainActivity.ENGINE_NATIVE
            } else {
                MainActivity.ENGINE_OCR
            }
            prefs.edit().putString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, extractionEngine).apply()
        }
        val engineSuffix = if (extractionEngine == MainActivity.ENGINE_NATIVE) "Direct" else "Reader"

        prefs.edit().putString(MainActivity.PREF_KEY_SUBJECT_MODE, nextSubj.id).apply()
        tvIslandTitle?.text = "${nextSubj.label} • $engineSuffix"
        if (isHapticFeedbackEnabled) {
            triggerHapticFeedback("A")
        }
        Toast.makeText(
            this,
            "Subject: ${nextSubj.label} ($engineSuffix)",
            Toast.LENGTH_SHORT
        ).show()
    }

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
    private val solveOrchestrator by lazy { OverlaySolveOrchestrator(applicationContext) }

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

    private fun isValidAnswerBadge(badge: String?): Boolean =
        OverlaySolveOrchestrator.isValidAnswerBadge(badge)

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
    private var isGeminiWebEditorHydrated = false

    @Volatile
    private var isGeminiLoading = false

    @Volatile
    private var geminiWebLastError: String? = null

    private var lastBubbleLayoutSignature: String = ""

    @Volatile
    private var isForegroundStarted = false

    private var latestScreenTextBlocks: List<Pair<String, Rect>> = emptyList()

    // Hardware volume double-press stealth trigger & 1-tap Hide All panic state
    private var volumeReceiver: BroadcastReceiver? = null
    private var lastVolumePressTimeMs = 0L
    private var lastVolumeSolveFireMs = 0L
    @Volatile
    private var isStealthHiddenAll = false
    private var lastSilentNotificationAnswer: String? = null

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
        overlayActiveSessionStartMs = System.currentTimeMillis()
        overlaySessionRequestsCount = 0L
        createNotificationChannel()
        registerVolumeButtonReceiver()
        PrismQuickSettingsTileService.requestTileRefresh(this)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireSolveWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val wl = cpuWakeLock ?: pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "OmniSolve:ActiveSolveWakeLock"
            ).apply {
                setReferenceCounted(false)
            }.also { cpuWakeLock = it }
            // Bounded 45-second CPU wake lock strictly for the active solve
            wl.acquire(45_000L)
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire CPU WakeLock: ${e.message}")
        }
    }

    private fun releaseHighPerformanceWakeLock() {
        try {
            if (cpuWakeLock?.isHeld == true) {
                cpuWakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && !isWindowVisible && !isScanning) {
            mainHandler.post {
                try {
                    geminiWebView?.onPause()
                    geminiWebView?.pauseTimers()
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                        geminiWebView?.clearCache(false)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun setOverlayHidden(hidden: Boolean) {
        isStealthHiddenAll = hidden
        try {
            getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(MainActivity.PREF_KEY_STEALTH_HIDE_ALL, hidden)
                .apply()
        } catch (_: Exception) {}

        bubbleView?.let { pill ->
            pill.animate().cancel()
            pill.visibility = if (hidden) View.GONE else View.VISIBLE
            if (!hidden) {
                pill.alpha = (customOpacityPct.coerceIn(20, 100) / 100f)
            }
        }
        if (hidden) {
            if (isWindowVisible) {
                geminiWindowView?.visibility = View.GONE
                isWindowVisible = false
            }
            cropOverlayView?.visibility = View.GONE
        }
        refreshOngoingNotification()
    }

    private fun registerVolumeButtonReceiver() {
        if (volumeReceiver != null) return
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != "android.media.VOLUME_CHANGED_ACTION") return
                val prefs = try {
                    getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                } catch (_: Exception) {
                    null
                }
                // Only fire when the user explicitly enabled Double-Press Volume Trigger in Settings
                val doublePressEnabled = prefs?.getBoolean(MainActivity.PREF_KEY_VOLUME_DOUBLE_PRESS, false) ?: false
                if (!doublePressEnabled) return

                val newVol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
                val oldVol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_PREV_VALUE", -1)
                // Prefer Volume Down presses (or same-level press at 0 volume) so turning volume UP never misfires
                if (newVol >= 0 && oldVol >= 0 && newVol > oldVol) return

                val now = System.currentTimeMillis()
                if (now - lastVolumeSolveFireMs < 2200L) return

                if (now - lastVolumePressTimeMs in 80L..680L) {
                    lastVolumePressTimeMs = 0L
                    lastVolumeSolveFireMs = now
                    Log.d(TAG, "Double-press Volume Down stealth trigger fired")
                    if (isHapticFeedbackEnabled) {
                        triggerHapticFeedback("A")
                    }
                    triggerSolveFromHardware()
                } else {
                    lastVolumePressTimeMs = now
                }
            }
        }
        try {
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            registerReceiver(volumeReceiver, filter)
            Log.d(TAG, "Registered double-press volume stealth receiver")
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
        when (intent?.action) {
            ACTION_NOTIF_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_NOTIF_SOLVE_NOW -> {
                if (isForegroundStarted) {
                    onUserTapSolve()
                } else {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_NOTIF_TOGGLE_HIDE_ALL -> {
                if (isForegroundStarted) {
                    mainHandler.post {
                        setOverlayHidden(!isStealthHiddenAll)
                    }
                } else {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_NOTIF_TOGGLE_STEALTH -> {
                if (isForegroundStarted) {
                    isCamouflageEnabled = !isCamouflageEnabled
                    getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(MainActivity.PREF_KEY_CAMOUFLAGE, isCamouflageEnabled)
                        .apply()
                    if (!isScanning) {
                        if (isValidAnswerBadge(latestCachedAnswer)) {
                            setAnswerUI(latestCachedAnswer, "#047857", triggerFeedback = false)
                        } else {
                            setAnswerUI("?", "#0F172A", triggerFeedback = false)
                        }
                    }
                    refreshOngoingNotification()
                } else {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_UPDATE_CONFIG, ACTION_COMPANION_SYNC -> {
                if (!isForegroundStarted) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                applyUpdatedConfig(intent)
                return START_NOT_STICKY
            }
            ACTION_PIN_FORMS_KEY -> {
                if (!isForegroundStarted) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val compactKey = intent.getStringExtra(EXTRA_FORMS_COMPACT_KEY).orEmpty()
                val formTitle = intent.getStringExtra(EXTRA_FORMS_TITLE).orEmpty()
                val breakdown = intent.getStringExtra(EXTRA_FORMS_BREAKDOWN).orEmpty()
                if (compactKey.isNotBlank()) {
                    mainHandler.post {
                        latestCachedAnswer = compactKey
                        tvIslandTitle?.text = formTitle.ifBlank { "Form Key" }.take(14)
                        tvAnswerDisplay?.maxWidth = dpToPx(245)
                        tvAnswerDisplay?.text = compactKey
                        tvAnswerDisplay?.textSize = if (compactKey.length <= 16) 13f else 11.5f
                        tvAnswerDisplay?.setTextColor(Color.parseColor("#34D399"))
                        tvWindowAnswerBadge?.text = compactKey
                        tvWindowAnswerBadge?.setTextColor(Color.parseColor("#34D399"))
                        if (breakdown.isNotBlank()) {
                            tvIslandExplanation?.text = breakdown
                        }
                        animateAnswerPop(tvAnswerDisplay)
                        wakeAndScheduleIdleFade()
                        try { bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) } } catch (_: Exception) {}
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_UNPIN_FORMS_KEY -> {
                if (isForegroundStarted) {
                    mainHandler.post {
                        latestCachedAnswer = ""
                        tvIslandExplanation?.text = ""
                        llIslandExplanationDrawer?.visibility = View.GONE
                        setAnswerUI("?", "#0F172A", triggerFeedback = false)
                    }
                }
                return START_NOT_STICKY
            }
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

        val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)

        displayMode = intent?.getStringExtra(EXTRA_DISPLAY_MODE)
            ?: prefs.getString(MainActivity.PREF_KEY_DISPLAY_MODE, MainActivity.MODE_STEALTH)
            ?: MainActivity.MODE_STEALTH

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
                ?: prefs.getString(MainActivity.PREF_KEY_SOLVER_METHOD, SolverMethod.FIREBASE_SERVER.id)
        )

        hudStyle = intent?.getStringExtra(EXTRA_HUD_STYLE)
            ?: prefs.getString(MainActivity.PREF_KEY_HUD_STYLE, MainActivity.HUD_STYLE_ISLAND)
            ?: MainActivity.HUD_STYLE_ISLAND

        isCamouflageEnabled = intent?.getBooleanExtra(EXTRA_CAMOUFLAGE, false)
            ?: prefs.getBoolean(MainActivity.PREF_KEY_CAMOUFLAGE, false)

        isHapticFeedbackEnabled = intent?.getBooleanExtra(EXTRA_HAPTIC, true)
            ?: prefs.getBoolean(MainActivity.PREF_KEY_HAPTIC, true)

        themeMode = intent?.getStringExtra(EXTRA_THEME_MODE)
            ?: prefs.getString(MainActivity.PREF_KEY_THEME_MODE, MainActivity.THEME_DARK)
            ?: MainActivity.THEME_DARK

        islandAccentTheme = intent?.getStringExtra(EXTRA_ISLAND_ACCENT_THEME)
            ?: prefs.getString(MainActivity.PREF_KEY_ISLAND_ACCENT_THEME, DynamicIslandBorderDrawable.ACCENT_CRIMSON)
            ?: DynamicIslandBorderDrawable.ACCENT_CRIMSON

        islandCustomHex = intent?.getStringExtra(EXTRA_ISLAND_CUSTOM_HEX)
            ?: prefs.getString(MainActivity.PREF_KEY_ISLAND_CUSTOM_HEX, "")
            ?: ""

        islandLightMode = if (intent?.hasExtra(EXTRA_ISLAND_LIGHT_MODE) == true) {
            intent.getBooleanExtra(EXTRA_ISLAND_LIGHT_MODE, false)
        } else {
            prefs.getBoolean(MainActivity.PREF_KEY_ISLAND_LIGHT_MODE, false)
        }

        islandSurfaceHex = intent?.getStringExtra(EXTRA_ISLAND_SURFACE_HEX)
            ?: prefs.getString(MainActivity.PREF_KEY_ISLAND_SURFACE_HEX, "#121316")
            ?: "#121316"

        val passedFadeSec = intent?.getIntExtra(EXTRA_ISLAND_IDLE_FADE_SEC, -1) ?: -1
        islandIdleFadeSec = if (passedFadeSec >= 0) {
            passedFadeSec
        } else {
            prefs.getInt(MainActivity.PREF_KEY_ISLAND_IDLE_FADE_SEC, 4)
        }.coerceIn(0, 20)

        islandEdgeSnap = intent?.getBooleanExtra(EXTRA_ISLAND_EDGE_SNAP, prefs.getBoolean(MainActivity.PREF_KEY_ISLAND_EDGE_SNAP, true))
            ?: prefs.getBoolean(MainActivity.PREF_KEY_ISLAND_EDGE_SNAP, true)

        val passedScalePct = intent?.getIntExtra(EXTRA_ISLAND_SCALE_PCT, -1) ?: -1
        islandScalePct = if (passedScalePct in 75..125) {
            passedScalePct
        } else {
            prefs.getInt(MainActivity.PREF_KEY_ISLAND_SCALE_PCT, 100).coerceIn(75, 125)
        }

        val passedOpacityPct = intent?.getIntExtra(EXTRA_CUSTOM_OPACITY_PCT, -1) ?: -1
        customOpacityPct = if (passedOpacityPct in 20..100) {
            passedOpacityPct
        } else {
            prefs.getInt(MainActivity.PREF_KEY_CUSTOM_OPACITY_PCT, 100).coerceIn(20, 100)
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

        if (bubbleView == null && overlayOpenStartMs == 0L) {
            overlayOpenStartMs = PrismPerfLogger.nowMs()
        }

        mainHandler.postDelayed({
            if (solverMethod == SolverMethod.OVERLAPPING_GOOGLE) {
                currentSizeIndex = 3
                displayMode = MainActivity.MODE_WINDOW
            }
            // Lazy WebView initialization: ONLY create Gemini WebView at startup when Window/Overlapping mode is active
            if ((solverMethod == SolverMethod.OVERLAPPING_GOOGLE || displayMode == MainActivity.MODE_WINDOW) &&
                geminiWindowView == null
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
        }, 120)

        // START_STICKY keeps Dynamic Island resilient under Android memory/battery saver pressure;
        // explicit user Stop always calls stopSelf() cleanly.
        return START_STICKY
    }

    private fun applyUpdatedConfig(intent: Intent?) {
        val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        if (intent == null) return

        if (intent.hasExtra(EXTRA_EXTRACTION_ENGINE)) {
            extractionEngine = intent.getStringExtra(EXTRA_EXTRACTION_ENGINE) ?: extractionEngine
        }
        if (intent.hasExtra(EXTRA_SOLVER_METHOD)) {
            val updatedMethod = SolverMethod.fromId(intent.getStringExtra(EXTRA_SOLVER_METHOD))
            solverMethod = updatedMethod
            val syncedDisplayMode = if (updatedMethod == SolverMethod.OVERLAPPING_GOOGLE) {
                MainActivity.MODE_WINDOW
            } else {
                MainActivity.MODE_STEALTH
            }
            prefs.edit()
                .putString(MainActivity.PREF_KEY_SOLVER_METHOD, updatedMethod.id)
                .putString(MainActivity.PREF_KEY_DISPLAY_MODE, syncedDisplayMode)
                .apply()
            if (updatedMethod == SolverMethod.OVERLAPPING_GOOGLE) {
                currentSizeIndex = 3
                displayMode = MainActivity.MODE_WINDOW
                if (geminiWindowView == null) {
                    setupGeminiWindow()
                } else {
                    showGeminiWindow(true)
                }
                bringFrontPillToTop()
            } else {
                displayMode = MainActivity.MODE_STEALTH
                if (isWindowVisible) {
                    showGeminiWindow(false)
                }
            }
            if (!isScanning && !isCamouflageEnabled && !isAdvEnabled(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE)) {
                tvIslandTitle?.text = "${updatedMethod.shortName} Ready"
            }
            if (llMethodLoopDrawer?.visibility == View.VISIBLE) {
                openMethodLoopDrawer()
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
        if (intent.hasExtra(EXTRA_HAPTIC)) {
            isHapticFeedbackEnabled = intent.getBooleanExtra(EXTRA_HAPTIC, isHapticFeedbackEnabled)
        }
        if (intent.hasExtra(EXTRA_ISLAND_ACCENT_THEME) ||
            intent.hasExtra(EXTRA_ISLAND_CUSTOM_HEX) ||
            intent.hasExtra(EXTRA_ISLAND_LIGHT_MODE) ||
            intent.hasExtra(EXTRA_ISLAND_SURFACE_HEX)
        ) {
            if (intent.hasExtra(EXTRA_ISLAND_ACCENT_THEME)) {
                islandAccentTheme = intent.getStringExtra(EXTRA_ISLAND_ACCENT_THEME)
                    ?: DynamicIslandBorderDrawable.ACCENT_CRIMSON
            }
            if (intent.hasExtra(EXTRA_ISLAND_CUSTOM_HEX)) {
                islandCustomHex = intent.getStringExtra(EXTRA_ISLAND_CUSTOM_HEX).orEmpty()
            }
            if (intent.hasExtra(EXTRA_ISLAND_LIGHT_MODE)) {
                islandLightMode = intent.getBooleanExtra(EXTRA_ISLAND_LIGHT_MODE, islandLightMode)
            }
            if (intent.hasExtra(EXTRA_ISLAND_SURFACE_HEX)) {
                islandSurfaceHex = intent.getStringExtra(EXTRA_ISLAND_SURFACE_HEX) ?: islandSurfaceHex
            }
            applyActiveThemeToIsland()
        }
        if (intent.hasExtra(EXTRA_ISLAND_IDLE_FADE_SEC)) {
            islandIdleFadeSec = intent.getIntExtra(EXTRA_ISLAND_IDLE_FADE_SEC, islandIdleFadeSec).coerceIn(0, 20)
        }
        if (intent.hasExtra(EXTRA_ISLAND_EDGE_SNAP)) {
            islandEdgeSnap = intent.getBooleanExtra(EXTRA_ISLAND_EDGE_SNAP, islandEdgeSnap)
        }
        if (intent.hasExtra(EXTRA_ISLAND_SCALE_PCT)) {
            islandScalePct = intent.getIntExtra(EXTRA_ISLAND_SCALE_PCT, islandScalePct).coerceIn(75, 125)
            val (sx, sy) = computeBasePillScale()
            bubbleView?.scaleX = sx
            bubbleView?.scaleY = sy
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

        if (intent.hasExtra(EXTRA_CUSTOM_OPACITY_PCT)) {
            val pct = intent.getIntExtra(EXTRA_CUSTOM_OPACITY_PCT, customOpacityPct)
            applyCustomOpacityPct(pct)
        } else {
            applyCustomOpacityPct(prefs.getInt(MainActivity.PREF_KEY_CUSTOM_OPACITY_PCT, customOpacityPct))
        }

        refreshDynamicFlowChipsUI()
        if (!isDfEnabled(MainActivity.PREF_KEY_DF_SWIPE_METHOD_LOOP, true)) {
            closeMethodLoopDrawer()
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

        // Refresh pill display & notification actions whenever Advanced Settings change
        if (!isScanning) {
            if (isValidAnswerBadge(latestCachedAnswer)) {
                setAnswerUI(latestCachedAnswer, "#047857", triggerFeedback = false)
            } else {
                setAnswerUI("?", "#0F172A", triggerFeedback = false)
            }
        }
        wakeAndScheduleIdleFade()
        refreshOngoingNotification()
    }

    /**
     * Morphs the center of the Dynamic Island IN-PLACE between status/answer text (`ll_island_body`)
     * and crisp feature icons (`ll_island_inline_tools`) on the exact same compact capsule row—
     * eliminating awkward vertical expansion while keeping every feature 1 tap away.
     */
    private fun setInlineIslandToolsVisible(show: Boolean) {
        val body = llIslandBodyView ?: return
        val tools = llIslandInlineTools ?: return
        if (isInlineToolsVisible == show && (tools.visibility == View.VISIBLE) == show) return

        mainHandler.removeCallbacks(inlineToolsAutoHideRunnable)
        isInlineToolsVisible = show

        val group = bubbleView as? ViewGroup
        if (group != null) {
            val transition = TransitionSet().apply {
                addTransition(ChangeBounds())
                addTransition(Fade())
                duration = 230L
                interpolator = OvershootInterpolator(1.08f)
            }
            TransitionManager.beginDelayedTransition(group, transition)
        }

        if (show) {
            body.visibility = View.GONE
            tools.visibility = View.VISIBLE
            mainHandler.postDelayed(inlineToolsAutoHideRunnable, 6_500L)
        } else {
            tools.visibility = View.GONE
            body.visibility = View.VISIBLE
        }
        try {
            bubbleView?.let { v ->
                (v.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
                    windowManager.updateViewLayout(v, lp)
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Synchronizes the floating Dynamic Island's capsule surface, accent sweep ring, typography,
     * and in-place tool icon tints with the user's active theme (including iOS Liquid Glass Light).
     */
    private fun applyActiveThemeToIsland() {
        val drawable = islandBorderDrawable
        drawable?.setCustomAccentHex(islandCustomHex, islandAccentTheme)
        drawable?.setThemeSurfaceMode(islandLightMode, islandSurfaceHex)

        val accentColor = drawable?.getAccentColorInt() ?: Color.parseColor("#D71921")
        val primaryInk = if (islandLightMode) Color.parseColor("#1C1C1E") else Color.parseColor("#F2F2F7")
        val secondaryInk = if (islandLightMode) Color.parseColor("#636366") else Color.parseColor("#AEAEB2")
        val toolIconTint = if (islandLightMode) Color.parseColor("#1C1C1E") else Color.parseColor("#F2F2F7")
        val stopIconTint = Color.parseColor("#FF3B30")

        tvIslandTitle?.setTextColor(primaryInk)
        tvIslandExplanation?.setTextColor(primaryInk)
        tvIslandSourceTag?.setTextColor(secondaryInk)

        btnIslandToolScan?.setColorFilter(accentColor)
        btnIslandToolCrop?.setColorFilter(toolIconTint)
        btnIslandToolResolve?.setColorFilter(Color.parseColor("#FF9500"))
        btnIslandToolMode?.setColorFilter(toolIconTint)
        btnIslandToolMethods?.setColorFilter(toolIconTint)
        btnIslandToolExplain?.setColorFilter(if (islandLightMode) Color.parseColor("#007AFF") else Color.parseColor("#34D399"))
        btnIslandToolStop?.setColorFilter(stopIconTint)
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
                                            deliverResolvedAnswer(answer, solveId = 0L)
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
        mainHandler.removeCallbacks(idleFadeRunnable)
        mainHandler.removeCallbacks(inlineToolsAutoHideRunnable)
        scrollCaptureTimerJob?.cancel()
        clearPendingPartOne()
        dismissCropRegionSelector()
        currentSolveFlowJob?.cancel()
        F1MasterEngine.cancelActiveSolve("OVERLAY_DESTROYED")
        F1MasterEngine.invalidateVisualCache()
        activeSolvePollJob?.cancel()
        highRamSyncJob?.cancel()
        stopCompanionSync()
        val sessionElapsedSec = if (overlayActiveSessionStartMs > 0L) {
            ((System.currentTimeMillis() - overlayActiveSessionStartMs) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }
        val sessionReqs = overlaySessionRequestsCount
        if (sessionElapsedSec >= 2L) {
            val appCtx = applicationContext
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                FirebaseTelemetryRepository.recordActiveSession(
                    context = appCtx,
                    source = TelemetrySessionSource.DYNAMIC_ISLAND,
                    durationSeconds = sessionElapsedSec,
                    requestsCount = sessionReqs
                )
            }
        }
        serviceScope.cancel()
        unregisterVolumeButtonReceiver()
        releaseHighPerformanceWakeLock()
        cpuWakeLock = null
        instance = null
        PrismQuickSettingsTileService.requestTileRefresh(this)
        try { solveOrchestrator.invalidateCachesOnCaptureStop() } catch (_: Exception) {}
        try { screenCaptureManager?.stopCapture() } catch (_: Exception) {}
        try { ocrEngine.close() } catch (_: Exception) {}
        try { islandBorderDrawable?.stopOrbitAnimation() } catch (_: Exception) {}
        islandBorderDrawable = null
        try { bubbleView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        try {
            geminiWebView?.let { wv ->
                wv.stopLoading()
                wv.removeJavascriptInterface("AndroidBridge")
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.clearHistory()
                wv.destroy()
            }
        } catch (_: Exception) {}
        geminiWebView = null
        try { geminiWindowView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        geminiWindowView = null
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
            val projMgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projMgr.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                Log.e(TAG, "getMediaProjection returned null")
                return
            }
            val mgr = ScreenCaptureManager(this)
            mgr.onProjectionStoppedListener = {
                mainHandler.post {
                    isScanning = false
                    stopScanBreathingAnim()
                    setAnswerUI("CAP", "#B45309", loading = false, triggerFeedback = false)
                    tvIslandTitle?.text = "Re-enable Capture"
                }
            }
            screenCaptureManager = mgr
            mgr.setMediaProjection(projection)
            Log.d(TAG, "Screen capture ready")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init screen capture", e)
            screenCaptureManager = null
        }
    }

    // ─── 1. Setup Floating Bubble (Stealth HUD) ───────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingBubble() {
        if (bubbleView != null) return

        val dm = resources.displayMetrics
        val isTopStrip = (hudStyle == MainActivity.HUD_STYLE_TOP_STRIP)
        val isEdgeTab = (hudStyle == MainActivity.HUD_STYLE_EDGE_TAB)
        val isIsland = (hudStyle == MainActivity.HUD_STYLE_ISLAND || isTopStrip)
        val layoutRes = if (isIsland) R.layout.layout_dynamic_island else R.layout.layout_floating_bubble

        bubbleView = LayoutInflater.from(this).inflate(layoutRes, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        // IMPORTANT: Omit FLAG_LAYOUT_IN_SCREEN and cutout short-edges so WindowManager (0, 0)
        // begins strictly BELOW the system status bar, camera notch, and Notification Control Panel
        // pull-down intercept zone on all OEM phones (Vivo/Oppo/Xiaomi/Samsung/Pixel).
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = when {
                isIsland -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                isEdgeTab -> Gravity.TOP or Gravity.END
                else -> Gravity.TOP or Gravity.START
            }
            x = if (isIsland) 0 else if (isEdgeTab) dpToPx(2) else dpToPx(16)
            // Place Dynamic Island comfortably below the status bar / notification pull-down zone
            y = when {
                isTopStrip -> dpToPx(6)
                isIsland -> dpToPx(10)
                isEdgeTab -> (dm.heightPixels * 0.28).toInt()
                else -> (dm.heightPixels * 0.22).toInt()
            }
        }

        val bubbleContainer: View
        val expandedToolbar: View
        val btnClose: View

        if (isIsland) {
            bubbleContainer = bubbleView!!.findViewById(R.id.island_icon_container)
            expandedToolbar = bubbleView!!.findViewById(R.id.island_expanded_controls)
            tvAnswerDisplay = bubbleView!!.findViewById(R.id.tv_island_answer)
            btnClose        = bubbleView!!.findViewById(R.id.btn_island_close)
            tvIslandTitle   = bubbleView!!.findViewById(R.id.tv_island_title)
            ivIslandIcon    = bubbleView!!.findViewById(R.id.iv_island_icon)
            ivBubbleIcon    = null
            llIslandExplanationDrawer = bubbleView!!.findViewById(R.id.ll_island_explanation_drawer)
            tvIslandMeta    = bubbleView!!.findViewById(R.id.tv_island_meta)
            tvIslandExplanation = bubbleView!!.findViewById(R.id.tv_island_explanation)
            tvIslandSourceTag = bubbleView!!.findViewById(R.id.tv_island_source_tag)
            btnIslandWrongResolve = bubbleView!!.findViewById(R.id.btn_island_wrong_resolve)
            llIslandBodyView = bubbleView!!.findViewById(R.id.ll_island_body)
            llIslandInlineTools = bubbleView!!.findViewById(R.id.ll_island_inline_tools)
            btnIslandToolScan = bubbleView!!.findViewById(R.id.btn_island_tool_scan)
            btnIslandToolCrop = bubbleView!!.findViewById(R.id.btn_island_tool_crop)
            btnIslandToolResolve = bubbleView!!.findViewById(R.id.btn_island_tool_resolve)
            btnIslandToolMode = bubbleView!!.findViewById(R.id.btn_island_tool_mode)
            btnIslandToolMethods = bubbleView!!.findViewById(R.id.btn_island_tool_methods)
            btnIslandToolExplain = bubbleView!!.findViewById(R.id.btn_island_tool_explain)
            btnIslandToolStop = bubbleView!!.findViewById(R.id.btn_island_tool_stop)
            isInlineToolsVisible = false
            llMethodLoopDrawer = bubbleView!!.findViewById(R.id.ll_island_method_loop_drawer)
            svMethodLoop    = bubbleView!!.findViewById(R.id.sv_island_method_loop)
            llMethodLoopItems = bubbleView!!.findViewById(R.id.ll_island_method_loop_items)
            viewIslandLiveDot = bubbleView!!.findViewById(R.id.view_island_live_dot)
            (btnClose as? ImageView)?.setColorFilter(Color.parseColor("#EEEEEE"))

            val borderDrawable = DynamicIslandBorderDrawable(resources.displayMetrics.density)
            val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
            val savedAccent = islandAccentTheme.ifBlank {
                prefs.getString(MainActivity.PREF_KEY_ISLAND_ACCENT_THEME, DynamicIslandBorderDrawable.ACCENT_CRIMSON)
                    ?: DynamicIslandBorderDrawable.ACCENT_CRIMSON
            }
            val savedCustomHex = islandCustomHex.ifBlank {
                prefs.getString(MainActivity.PREF_KEY_ISLAND_CUSTOM_HEX, "").orEmpty()
            }
            islandLightMode = prefs.getBoolean(MainActivity.PREF_KEY_ISLAND_LIGHT_MODE, islandLightMode)
            islandSurfaceHex = prefs.getString(MainActivity.PREF_KEY_ISLAND_SURFACE_HEX, islandSurfaceHex) ?: islandSurfaceHex
            borderDrawable.setCustomAccentHex(savedCustomHex, savedAccent)
            borderDrawable.setThemeSurfaceMode(islandLightMode, islandSurfaceHex)
            bubbleView!!.background = borderDrawable
            islandBorderDrawable = borderDrawable

            // Bind In-Place Dynamic Island Tool Icons (replaces text inside capsule without awkward vertical expansion)
            btnIslandToolScan?.setOnClickListener {
                wakeAndScheduleIdleFade()
                setInlineIslandToolsVisible(false)
                onUserTapSolve()
            }
            btnIslandToolCrop?.setOnClickListener {
                wakeAndScheduleIdleFade()
                setInlineIslandToolsVisible(false)
                if (screenCaptureManager != null) showCropRegionSelector() else onUserTapSolve()
            }
            btnIslandToolResolve?.setOnClickListener {
                wakeAndScheduleIdleFade()
                setInlineIslandToolsVisible(false)
                val suspectedWrong = latestCachedAnswer.takeIf { isValidAnswerBadge(it) }
                llIslandExplanationDrawer?.visibility = View.GONE
                triggerSolveFlow(
                    cropScreenRect = lastSolvedCropRect,
                    forceFreshResolve = true,
                    previousWrongAnswer = suspectedWrong
                )
            }
            btnIslandToolMode?.setOnClickListener {
                wakeAndScheduleIdleFade()
                cycleQuestionSolveModeChip()
                setInlineIslandToolsVisible(false)
            }
            btnIslandToolMethods?.setOnClickListener {
                wakeAndScheduleIdleFade()
                setInlineIslandToolsVisible(false)
                if (llMethodLoopDrawer?.visibility == View.VISIBLE) closeMethodLoopDrawer() else openMethodLoopDrawer()
            }
            btnIslandToolExplain?.setOnClickListener {
                wakeAndScheduleIdleFade()
                setInlineIslandToolsVisible(false)
                val drawer = llIslandExplanationDrawer
                if (drawer != null) {
                    val group = bubbleView as? ViewGroup
                    if (group != null) {
                        TransitionManager.beginDelayedTransition(group, AutoTransition().apply { duration = 200 })
                    }
                    drawer.visibility = if (drawer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
            }
            btnIslandToolStop?.setOnClickListener {
                setInlineIslandToolsVisible(false)
                Toast.makeText(this@OverlayService, "Dynamic Island Stopped", Toast.LENGTH_SHORT).show()
                stopSelf()
            }

            applyActiveThemeToIsland()

            val (baseSX, baseSY) = computeBasePillScale()
            bubbleView!!.scaleX = baseSX
            bubbleView!!.scaleY = baseSY

            if (isAdvEnabled(MainActivity.PREF_KEY_ADV_SPLIT_PILL)) {
                tvIslandTitle?.visibility = View.GONE
            } else {
                tvIslandTitle?.visibility = View.VISIBLE
            }

            if (isAdvEnabled(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE)) {
                ivIslandIcon?.setImageResource(R.drawable.ic_prism_orb)
                tvIslandTitle?.text = "Net Speed"
                val (netText, isSolved) = formatNetworkSpeedDisguise(latestCachedAnswer)
                tvAnswerDisplay?.text = netText
                tvAnswerDisplay?.textSize = 11.5f
                tvAnswerDisplay?.setTextColor(Color.parseColor(if (isSolved) "#34D399" else "#EEEEEE"))
                llIslandExplanationDrawer?.visibility = View.GONE
            } else if (isCamouflageEnabled) {
                ivIslandIcon?.setImageResource(R.drawable.ic_battery_camouflage)
                tvIslandTitle?.text = "Charging"
                val (camoText, isSolved) = formatChargingCamouflage(latestCachedAnswer, getDeviceBatteryPercentage())
                tvAnswerDisplay?.text = camoText
                tvAnswerDisplay?.textSize = if (camoText.length > 4) 13f else 15f
                tvAnswerDisplay?.setTextColor(Color.parseColor(if (isSolved) "#34D399" else "#EEEEEE"))
                llIslandExplanationDrawer?.visibility = View.GONE
            } else {
                ivIslandIcon?.setImageResource(R.drawable.ic_prism_orb)
                if (isValidAnswerBadge(latestCachedAnswer)) {
                    tvIslandTitle?.text = "Review answer"
                    tvAnswerDisplay?.text = latestCachedAnswer
                    tvAnswerDisplay?.textSize = if (latestCachedAnswer.length > 2) 13f else 16f
                } else {
                    tvIslandTitle?.text = "Tap to Solve"
                    tvAnswerDisplay?.text = "?"
                    tvAnswerDisplay?.textSize = 16f
                }
            }

            // Tapping explanation drawer collapses it cleanly
            llIslandExplanationDrawer?.setOnClickListener {
                wakeAndScheduleIdleFade()
                val group = bubbleView as? ViewGroup
                if (group != null) {
                    TransitionManager.beginDelayedTransition(group, AutoTransition().apply { duration = 200 })
                }
                llIslandExplanationDrawer?.visibility = View.GONE
            }

            // 1-Tap "Wrong? Re-Solve": Evicts cached answer everywhere & forces fresh Vision + Dual-Verify re-solve!
            btnIslandWrongResolve?.setOnClickListener {
                wakeAndScheduleIdleFade()
                val suspectedWrong = latestCachedAnswer.takeIf { isValidAnswerBadge(it) }
                llIslandExplanationDrawer?.visibility = View.GONE
                triggerSolveFlow(
                    cropScreenRect = lastSolvedCropRect,
                    forceFreshResolve = true,
                    previousWrongAnswer = suspectedWrong
                )
            }

            // Island body supports:
            // - Instant tap to Solve (0ms instant response)
            // - Free Drag anywhere on the pill to reposition
            // - Quick Swipe DOWN flick -> 3-Method Looping Switcher (Online / Circle / Gemini)
            // - Quick Swipe UP flick -> Collapse method switcher drawer if open
            // - Long-press -> Switch Subject/OCR (when Advanced Gestures ON)
            val islandBody = bubbleView!!.findViewById<View>(R.id.ll_island_body)
            islandBody?.setOnTouchListener(object : View.OnTouchListener {
                private var startX = 0
                private var startY = 0
                private var downX = 0f
                private var downY = 0f
                private var lastRawX = 0f
                private var lastRawY = 0f
                private var smoothTilt = 0f
                private var smoothStretchX = 1.0f
                private var smoothStretchY = 1.0f
                private var dragging = false
                private var t0 = 0L
                private var consumedLongPress = false
                private val longPressRunnable = Runnable {
                    if (isAdvEnabled(MainActivity.PREF_KEY_ADV_ISLAND_GESTURES)) {
                        consumedLongPress = true
                        cycleSubjectOrEngineOnLongPress()
                    }
                }

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    val rootView = bubbleView ?: return false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            wakeAndScheduleIdleFade()
                            startX = bubbleParams.x
                            startY = bubbleParams.y
                            downX = event.rawX
                            downY = event.rawY
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                            smoothTilt = 0f
                            smoothStretchX = 1.0f
                            smoothStretchY = 1.0f
                            dragging = false
                            t0 = System.currentTimeMillis()
                            consumedLongPress = false
                            if (isAdvEnabled(MainActivity.PREF_KEY_ADV_ISLAND_GESTURES)) {
                                mainHandler.postDelayed(longPressRunnable, 480L)
                            }
                            rootView.animate()?.cancel()
                            rootView.animate()
                                ?.scaleX(0.95f)
                                ?.scaleY(0.95f)
                                ?.setDuration(110)
                                ?.setInterpolator(DecelerateInterpolator())
                                ?.start()
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val moveDx = event.rawX - lastRawX
                            val moveDy = event.rawY - lastRawY
                            lastRawX = event.rawX
                            lastRawY = event.rawY

                            val totalDx = (event.rawX - downX).toInt()
                            val totalDy = (event.rawY - downY).toInt()
                            if (Math.abs(totalDx) > dpToPx(6) || Math.abs(totalDy) > dpToPx(6)) {
                                mainHandler.removeCallbacks(longPressRunnable)
                                dragging = true
                            }
                            if (dragging && !consumedLongPress) {
                                val sign = if ((bubbleParams.gravity and Gravity.END) == Gravity.END) -1 else 1
                                bubbleParams.x = startX + (totalDx * sign)
                                bubbleParams.y = (startY + totalDy).coerceIn(dpToPx(6), (dm.heightPixels * 0.90).toInt())
                                try {
                                    windowManager.updateViewLayout(rootView, bubbleParams)
                                } catch (_: Exception) {}

                                val targetTilt = (moveDx * 0.18f).coerceIn(-9f, 9f)
                                val targetSX = (1.0f + Math.abs(moveDx) * 0.0028f).coerceIn(0.94f, 1.08f)
                                val targetSY = (1.0f + Math.abs(moveDy) * 0.0028f).coerceIn(0.94f, 1.08f)
                                smoothTilt += (targetTilt - smoothTilt) * 0.35f
                                smoothStretchX += (targetSX - smoothStretchX) * 0.35f
                                smoothStretchY += (targetSY - smoothStretchY) * 0.35f
                                rootView.rotation = smoothTilt
                                rootView.scaleX = smoothStretchX
                                rootView.scaleY = smoothStretchY
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            mainHandler.removeCallbacks(longPressRunnable)
                            val dur = System.currentTimeMillis() - t0
                            val dx = event.rawX - downX
                            val dy = event.rawY - downY
                            val gesturesOn = isAdvEnabled(MainActivity.PREF_KEY_ADV_ISLAND_GESTURES)
                            val swipeLoopOn = isDfEnabled(MainActivity.PREF_KEY_DF_SWIPE_METHOD_LOOP, true)

                            val (baseSX, baseSY) = computeBasePillScale()
                            rootView.animate()
                                ?.scaleX(baseSX)
                                ?.scaleY(baseSY)
                                ?.rotation(0f)
                                ?.setDuration(420)
                                ?.setInterpolator(OvershootInterpolator(3.2f))
                                ?.start()

                            if (consumedLongPress) return true

                            val action = classifyIslandReleaseTouch(
                                isActionUp = (event.action == MotionEvent.ACTION_UP),
                                wasDragging = dragging,
                                durationMs = dur,
                                dxPx = dx,
                                dyPx = dy,
                                verticalFlickMinPx = dpToPx(20).toFloat(),
                                verticalFlickMaxPx = dpToPx(72).toFloat(),
                                horizontalSwipeMinPx = dpToPx(30).toFloat(),
                                swipeLoopEnabled = swipeLoopOn,
                                methodDrawerOpen = (llMethodLoopDrawer?.visibility == View.VISIBLE),
                                horizontalHistoryEnabled = gesturesOn,
                                candidateY = bubbleParams.y,
                                bottomDismissThresholdY = (dm.heightPixels * 0.86).toInt()
                            )

                            when (action) {
                                IslandTouchAction.OPEN_METHOD_DRAWER -> {
                                    bubbleParams.x = startX
                                    bubbleParams.y = startY
                                    try { windowManager.updateViewLayout(rootView, bubbleParams) } catch (_: Exception) {}
                                    openMethodLoopDrawer()
                                    return true
                                }
                                IslandTouchAction.CLOSE_METHOD_DRAWER -> {
                                    bubbleParams.x = startX
                                    bubbleParams.y = startY
                                    try { windowManager.updateViewLayout(rootView, bubbleParams) } catch (_: Exception) {}
                                    closeMethodLoopDrawer()
                                    return true
                                }
                                IslandTouchAction.HISTORY_NEXT -> {
                                    bubbleParams.x = startX
                                    bubbleParams.y = startY
                                    try { windowManager.updateViewLayout(rootView, bubbleParams) } catch (_: Exception) {}
                                    browseSolvedHistoryOnPill(+1)
                                    return true
                                }
                                IslandTouchAction.HISTORY_PREV -> {
                                    bubbleParams.x = startX
                                    bubbleParams.y = startY
                                    try { windowManager.updateViewLayout(rootView, bubbleParams) } catch (_: Exception) {}
                                    browseSolvedHistoryOnPill(-1)
                                    return true
                                }
                                IslandTouchAction.DISMISS -> {
                                    rootView.animate()
                                        ?.scaleX(0f)
                                        ?.scaleY(0f)
                                        ?.alpha(0f)
                                        ?.setDuration(240)
                                        ?.withEndAction { stopSelf() }
                                        ?.start()
                                    Toast.makeText(this@OverlayService, "Floating Pill Closed", Toast.LENGTH_SHORT).show()
                                    return true
                                }
                                IslandTouchAction.TAP -> {
                                    if (llMethodLoopDrawer?.visibility == View.VISIBLE) {
                                        closeMethodLoopDrawer()
                                    }
                                    onUserTapSolve()
                                }
                                IslandTouchAction.REPOSITION -> {
                                    if (islandEdgeSnap && kotlin.math.abs(bubbleParams.x) < dpToPx(24)) {
                                        bubbleParams.x = 0
                                        try { windowManager.updateViewLayout(rootView, bubbleParams) } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    }
                    return true
                }
            })
        } else {
            llIslandExplanationDrawer = null
            tvIslandMeta = null
            tvIslandExplanation = null
            tvIslandSourceTag = null
            btnIslandWrongResolve = null
            llIslandBodyView = null
            llIslandInlineTools = null
            btnIslandToolScan = null
            btnIslandToolCrop = null
            btnIslandToolResolve = null
            btnIslandToolMode = null
            btnIslandToolMethods = null
            btnIslandToolExplain = null
            btnIslandToolStop = null
            isInlineToolsVisible = false
            tvIslandTitle = null
            ivIslandIcon = null
            llMethodLoopDrawer = null
            svMethodLoop = null
            islandBorderDrawable?.stopOrbitAnimation()
            islandBorderDrawable = null
            viewIslandLiveDot = null
            bubbleContainer = bubbleView!!.findViewById(R.id.bubble_icon_container)
            expandedToolbar = bubbleView!!.findViewById(R.id.expanded_toolbar)
            tvAnswerDisplay = bubbleView!!.findViewById(R.id.tv_answer_display)
            ivBubbleIcon    = bubbleView!!.findViewById(R.id.iv_bubble_icon)
            btnClose        = bubbleView!!.findViewById(R.id.btn_close_toolbar)

            if (isEdgeTab) {
                bubbleView!!.scaleX = 0.86f
                bubbleView!!.scaleY = 0.86f
            } else {
                bubbleView!!.scaleX = 1.0f
                bubbleView!!.scaleY = 1.0f
            }

            if (isAdvEnabled(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE)) {
                val (netText, isSolved) = formatNetworkSpeedDisguise(latestCachedAnswer)
                tvAnswerDisplay?.text = netText
                tvAnswerDisplay?.textSize = 11.5f
                tvAnswerDisplay?.setTextColor(Color.parseColor(if (isSolved) "#34D399" else "#EEEEEE"))
            } else if (isCamouflageEnabled) {
                val (camoText, isSolved) = formatChargingCamouflage(latestCachedAnswer, getDeviceBatteryPercentage())
                tvAnswerDisplay?.text = camoText
                tvAnswerDisplay?.textSize = if (camoText.length > 4) 13f else 15f
                tvAnswerDisplay?.setTextColor(Color.parseColor(if (isSolved) "#34D399" else "#EEEEEE"))
            } else if (isValidAnswerBadge(latestCachedAnswer)) {
                tvAnswerDisplay?.text = latestCachedAnswer
                tvAnswerDisplay?.textSize = if (latestCachedAnswer.length > 2) 13f else 16f
            }
        }

        // Bind Dynamic Flow Helper Chips
        val activeBubble = bubbleView ?: return
        btnDfPart1 = activeBubble.findViewById(R.id.btn_df_part1)
        btnDfPart2 = activeBubble.findViewById(R.id.btn_df_part2)
        btnDfScroll2s = activeBubble.findViewById(R.id.btn_df_scroll_2s)
        btnDfQuestionMode = activeBubble.findViewById(R.id.btn_df_question_mode)
        btnDfDiagram = activeBubble.findViewById(R.id.btn_df_diagram)
        btnDfCrop = activeBubble.findViewById(R.id.btn_df_crop)

        val savedPct = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
            .getInt(MainActivity.PREF_KEY_CUSTOM_OPACITY_PCT, customOpacityPct)
            .coerceIn(20, 100)
        bubbleView?.alpha = (savedPct / 100f).coerceIn(0.20f, 1.0f)
        customOpacityPct = savedPct

        refreshDynamicFlowChipsUI()

        btnDfPart1?.setOnClickListener {
            wakeAndScheduleIdleFade()
            capturePartOneView()
        }

        btnDfPart2?.setOnClickListener {
            wakeAndScheduleIdleFade()
            capturePartTwoAndSolve()
        }

        btnDfScroll2s?.setOnClickListener {
            wakeAndScheduleIdleFade()
            startTwoSecondScrollCaptureAndSolve()
        }

        btnDfQuestionMode?.setOnClickListener {
            wakeAndScheduleIdleFade()
            cycleQuestionSolveModeChip()
        }

        btnDfDiagram?.setOnClickListener {
            wakeAndScheduleIdleFade()
            forceDiagramVisionActive = !forceDiagramVisionActive
            refreshDynamicFlowChipsUI()
            val statusTxt = if (forceDiagramVisionActive) "Diagram Vision: ON" else "Diagram Vision: Auto"
            tvIslandTitle?.text = statusTxt
            Toast.makeText(this, statusTxt, Toast.LENGTH_SHORT).show()
        }

        btnDfCrop?.setOnClickListener {
            wakeAndScheduleIdleFade()
            if (screenCaptureManager != null) {
                showCropRegionSelector()
            } else {
                onUserTapSolve()
            }
        }

        // Single tap on Prism Logo:
        // - In Dynamic Island mode: smoothly replaces status text IN-PLACE with crisp feature icons (zero ugly vertical expansion!)
        // - In Legacy Bubble mode: toggles compact toolbar
        bubbleContainer.setOnTouchListener(createDragAndTapListener(
            bubbleParams,
            { bubbleView },
            onSingleTap = {
                if (isIsland && llIslandInlineTools != null && llIslandBodyView != null) {
                    setInlineIslandToolsVisible(!isInlineToolsVisible)
                    return@createDragAndTapListener
                }
                refreshDynamicFlowChipsUI()
                val hasAnyEnabledChip = listOfNotNull(
                    btnDfPart1, btnDfPart2, btnDfScroll2s, btnDfQuestionMode, btnDfDiagram, btnDfCrop
                ).any { it.visibility == View.VISIBLE }
                if (!hasAnyEnabledChip) {
                    onUserTapSolve()
                    return@createDragAndTapListener
                }
                val group = bubbleView as? ViewGroup
                val willExpand = expandedToolbar.visibility != View.VISIBLE
                if (group != null) {
                    val transition = TransitionSet().apply {
                        addTransition(ChangeBounds())
                        addTransition(Fade())
                        duration = 260L
                        interpolator = OvershootInterpolator(1.05f)
                    }
                    TransitionManager.beginDelayedTransition(group, transition)
                }
                expandedToolbar.visibility = if (willExpand) View.VISIBLE else View.GONE
                try { windowManager.updateViewLayout(bubbleView, bubbleParams) } catch (_: Exception) {}
            },
            onDoubleTap = {
                if (isDfEnabled(MainActivity.PREF_KEY_DF_SWIPE_METHOD_LOOP, true) && isIsland) {
                    if (llMethodLoopDrawer?.visibility == View.VISIBLE) closeMethodLoopDrawer() else openMethodLoopDrawer()
                } else {
                    onUserTapSolve()
                }
            }
        ))

        tvAnswerDisplay?.setOnClickListener {
            val drawer = llIslandExplanationDrawer
            val hasCurrentExplanation = latestAnswerModel != null && !tvIslandExplanation?.text.isNullOrBlank()
            val solvedBadge = latestCachedAnswer.trim()
            val hasValidBadge = isValidAnswerBadge(solvedBadge) && !isScanning

            if (hasValidBadge) {
                if (isIsland && !isCamouflageEnabled && drawer != null && hasCurrentExplanation) {
                    val group = bubbleView as? ViewGroup
                    if (group != null) {
                        val transition = TransitionSet().apply {
                            addTransition(ChangeBounds())
                            addTransition(Fade())
                            duration = 240L
                            interpolator = OvershootInterpolator(1.05f)
                        }
                        TransitionManager.beginDelayedTransition(group, transition)
                    }
                    drawer.visibility = if (drawer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    try { windowManager.updateViewLayout(bubbleView, bubbleParams) } catch (_: Exception) {}
                } else {
                    try {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Prism Answer", solvedBadge))
                        Toast.makeText(this, "Copied answer: $solvedBadge", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                }
            } else {
                onUserTapSolve()
            }
        }

        btnClose.setOnClickListener {
            val group = bubbleView as? ViewGroup
            if (group != null) {
                val transition = TransitionSet().apply {
                    addTransition(ChangeBounds())
                    addTransition(Fade())
                    duration = 220L
                }
                TransitionManager.beginDelayedTransition(group, transition)
            }
            expandedToolbar.visibility = View.GONE
            if (isIsland) {
                btnClose.visibility = View.GONE
            }
            closeMethodLoopDrawer()
            try { windowManager.updateViewLayout(bubbleView, bubbleParams) } catch (_: Exception) {}
        }

        try {
            windowManager.addView(bubbleView, bubbleParams)
            if (overlayOpenStartMs > 0L) {
                PerformanceBenchmarkTracker.recordOverlayOpenLatency(overlayOpenStartMs)
                overlayOpenStartMs = 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating bubble view", e)
        }
    }

    // ─── Dynamic Flow Helper Tools & Swipe-Down 3-Method Loop Switcher ────────

    private fun refreshDynamicFlowChipsUI() {
        val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        val twoPartOn = isDfEnabled(MainActivity.PREF_KEY_DF_TWO_PART_CAPTURE, true)
        val scrollTimerOn = isDfEnabled(MainActivity.PREF_KEY_DF_SCROLL_TIMER, true)
        val complexModeOn = isDfEnabled(MainActivity.PREF_KEY_DF_COMPLEX_MODE, true)
        val diagramOn = isDfEnabled(MainActivity.PREF_KEY_DF_DIAGRAM_VISION, false)
        val quickCropOn = isDfEnabled(MainActivity.PREF_KEY_DF_QUICK_CROP, false)

        val solveMode = QuestionSolveMode.fromId(
            prefs.getString(MainActivity.PREF_KEY_QUESTION_SOLVE_MODE, QuestionSolveMode.STANDARD.id)
        )

        btnDfPart1?.visibility = if (twoPartOn) View.VISIBLE else View.GONE
        btnDfPart2?.visibility = if (twoPartOn) View.VISIBLE else View.GONE
        val hasPart1 = !pendingPartOneText.isNullOrBlank() || pendingPartOneBitmap != null
        btnDfPart1?.text = if (hasPart1) "1✓" else "1st"
        btnDfPart1?.setTextColor(Color.parseColor(if (hasPart1) "#34D399" else "#EEEEEE"))

        btnDfScroll2s?.visibility = if (scrollTimerOn) View.VISIBLE else View.GONE

        btnDfQuestionMode?.visibility = if (complexModeOn) View.VISIBLE else View.GONE
        btnDfQuestionMode?.text = solveMode.chipLabel
        btnDfQuestionMode?.setTextColor(
            Color.parseColor(
                when (solveMode) {
                    QuestionSolveMode.STANDARD -> "#D71921"
                    QuestionSolveMode.MATCH_FOLLOWING -> "#34D399"
                    QuestionSolveMode.COMPLEX_MATH -> "#FBBF24"
                }
            )
        )

        btnDfDiagram?.visibility = if (diagramOn) View.VISIBLE else View.GONE
        btnDfDiagram?.text = if (forceDiagramVisionActive) "IMG✓" else "IMG"
        btnDfDiagram?.setTextColor(Color.parseColor(if (forceDiagramVisionActive) "#34D399" else "#EEEEEE"))

        btnDfCrop?.visibility = if (quickCropOn) View.VISIBLE else View.GONE
    }

    private fun cycleQuestionSolveModeChip() {
        val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        val current = QuestionSolveMode.fromId(
            prefs.getString(MainActivity.PREF_KEY_QUESTION_SOLVE_MODE, QuestionSolveMode.STANDARD.id)
        )
        val modes = QuestionSolveMode.entries
        val next = modes[(modes.indexOf(current) + 1) % modes.size]
        prefs.edit().putString(MainActivity.PREF_KEY_QUESTION_SOLVE_MODE, next.id).apply()
        refreshDynamicFlowChipsUI()
        tvIslandTitle?.text = next.fullLabel
        if (isHapticFeedbackEnabled) triggerHapticFeedback("A")
        Toast.makeText(this, "Mode: ${next.fullLabel}", Toast.LENGTH_SHORT).show()
        try { bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) } } catch (_: Exception) {}
    }

    private fun openMethodLoopDrawer() {
        val drawer = llMethodLoopDrawer ?: return
        val itemsContainer = llMethodLoopItems ?: return
        val scrollView = svMethodLoop ?: return

        val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        val activeMethod = SolverMethod.fromId(
            prefs.getString(MainActivity.PREF_KEY_SOLVER_METHOD, solverMethod.id)
        )
        solverMethod = activeMethod

        val loopMethods = listOf(
            Triple(SolverMethod.BACKGROUND_GEMINI, "Smart Auto", "Fast + Auto-Math & Offline Cache"),
            Triple(SolverMethod.TURBO_HYBRID, "Double-Check Accuracy", "Multi-engine verified consensus"),
            Triple(SolverMethod.DIRECT_VISION, "Photo & Math Vision", "Reads formulas, charts & diagrams"),
            Triple(SolverMethod.FIREBASE_SERVER, "Cloud Solver", "Fast cloud inference with backup"),
            Triple(SolverMethod.CIRCLE_TO_SEARCH, "Web Facts Lookup", "Checks live web facts with AI"),
            Triple(SolverMethod.API_KEYS, "Your Own API Keys", "Uses your keys from Settings")
        )

        itemsContainer.removeAllViews()
        var targetScrollRow: View? = null
        val accentHex = if (islandLightMode) "#1B6EF3" else "#D71921"
        val selectedFillHex = if (islandLightMode) "#221B6EF3" else "#2AD71921"
        val idleFillHex = if (islandLightMode) "#F4F8FF" else "#141418"
        val idleBorderHex = if (islandLightMode) "#9AB9EA" else "#26262C"
        val primaryTextHex = if (islandLightMode) "#0E172A" else "#FFFFFF"
        val idleTextHex = if (islandLightMode) "#1E293B" else "#EEEEEE"
        val subActiveHex = if (islandLightMode) "#1B6EF3" else "#F87171"
        val subIdleHex = if (islandLightMode) "#52627A" else "#8E93A0"

        for (item in loopMethods) {
            val (methodItem, title, subtitle) = item
            val isSelected = (methodItem == activeMethod)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = dpToPx(48)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dpToPx(10).toFloat()
                    setColor(Color.parseColor(if (isSelected) selectedFillHex else idleFillHex))
                    setStroke(
                        dpToPx(1),
                        Color.parseColor(if (isSelected) accentHex else idleBorderHex)
                    )
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(5)
                }
                isClickable = true
                isFocusable = true
            }

            val titleTv = TextView(this).apply {
                text = if (isSelected) "● $title (Active)" else "○ $title"
                textSize = 11.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor(if (isSelected) primaryTextHex else idleTextHex))
            }
            val subTv = TextView(this).apply {
                text = subtitle
                textSize = 9.5f
                setTextColor(Color.parseColor(if (isSelected) subActiveHex else subIdleHex))
            }
            row.addView(titleTv)
            row.addView(subTv)

            row.setOnClickListener {
                selectSolverMethodFromIslandLoop(methodItem)
            }

            itemsContainer.addView(row)
            if (isSelected && targetScrollRow == null) {
                targetScrollRow = row
            }
        }

        llIslandExplanationDrawer?.visibility = View.GONE
        val group = bubbleView as? ViewGroup
        if (group != null) {
            TransitionManager.beginDelayedTransition(
                group,
                AutoTransition().apply {
                    duration = 210
                    interpolator = OvershootInterpolator(1.1f)
                }
            )
        }
        drawer.visibility = View.VISIBLE
        try { windowManager.updateViewLayout(bubbleView, bubbleParams) } catch (_: Exception) {}

        scrollView.post {
            val topY = targetScrollRow?.top ?: 0
            scrollView.scrollTo(0, (topY - dpToPx(12)).coerceAtLeast(0))
        }
    }

    private fun closeMethodLoopDrawer() {
        val drawer = llMethodLoopDrawer ?: return
        if (drawer.visibility != View.VISIBLE) return
        val group = bubbleView as? ViewGroup
        if (group != null) {
            TransitionManager.beginDelayedTransition(group, AutoTransition().apply { duration = 180 })
        }
        drawer.visibility = View.GONE
        try { bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) } } catch (_: Exception) {}
    }

    private fun selectSolverMethodFromIslandLoop(chosen: SolverMethod) {
        solverMethod = chosen
        val nextDisplayMode = if (chosen == SolverMethod.OVERLAPPING_GOOGLE) {
            MainActivity.MODE_WINDOW
        } else {
            MainActivity.MODE_STEALTH
        }
        displayMode = nextDisplayMode
        val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(MainActivity.PREF_KEY_SOLVER_METHOD, chosen.id)
            .putString(MainActivity.PREF_KEY_DISPLAY_MODE, nextDisplayMode)
            .apply()
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                com.omnisolve.overlay.data.SettingsDataStoreRepository(applicationContext)
                    .updateSolverMethod(chosen)
            }
        }

        if (chosen == SolverMethod.OVERLAPPING_GOOGLE) {
            if (geminiWindowView == null) {
                setupGeminiWindow()
                bringFrontPillToTop()
            } else if (!isWindowVisible) {
                showGeminiWindow(true)
            }
        } else if (isWindowVisible) {
            showGeminiWindow(false)
        }

        closeMethodLoopDrawer()
        tvIslandTitle?.text = "${chosen.shortName} OK"
        animateAnswerPop(tvIslandTitle)
        if (isHapticFeedbackEnabled) triggerHapticFeedback("A")
        Toast.makeText(
            this,
            "Active Method: ${chosen.title}. Tap Island to solve continuously.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearPendingPartOne() {
        pendingPartOneText = null
        try {
            pendingPartOneBitmap?.let { if (!it.isRecycled) it.recycle() }
        } catch (_: Exception) {}
        pendingPartOneBitmap = null
        mainHandler.post { refreshDynamicFlowChipsUI() }
    }

    private fun downscaleCaptureCopy(src: Bitmap, maxDim: Int = 920): Bitmap {
        val w = src.width
        val h = src.height
        val longest = maxOf(w, h)
        if (longest <= maxDim) return src.copy(Bitmap.Config.ARGB_8888, false)
        val ratio = maxDim.toFloat() / longest.toFloat()
        val tw = (w * ratio).toInt().coerceAtLeast(1)
        val th = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, tw, th, true)
    }

    private fun stitchBitmapsVertically(top: Bitmap?, bottom: Bitmap?): Bitmap? {
        if (top == null && bottom == null) return null
        if (top == null) return bottom?.let { downscaleCaptureCopy(it, 960) }
        if (bottom == null) return downscaleCaptureCopy(top, 960)
        return try {
            val sTop = downscaleCaptureCopy(top, 800)
            val sBot = downscaleCaptureCopy(bottom, 800)
            val width = maxOf(sTop.width, sBot.width).coerceAtLeast(1)
            val height = (sTop.height + sBot.height).coerceAtLeast(1)
            val combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(combined)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(sTop, 0f, 0f, null)
            canvas.drawBitmap(sBot, 0f, sTop.height.toFloat(), null)
            if (sTop !== top && !sTop.isRecycled) sTop.recycle()
            if (sBot !== bottom && !sBot.isRecycled) sBot.recycle()
            combined
        } catch (_: Exception) {
            downscaleCaptureCopy(bottom, 960)
        }
    }

    private fun capturePartOneView() {
        if (screenCaptureManager == null) {
            Toast.makeText(this, "Screen capture not active yet.", Toast.LENGTH_SHORT).show()
            return
        }
        scrollCaptureTimerJob?.cancel()
        currentSolveFlowJob?.cancel()
        isScanning = true
        setAnswerUI("...", "#D71921", loading = true, triggerFeedback = false)
        tvIslandTitle?.text = "Saving 1st View..."

        serviceScope.launch {
            try {
                val captured = captureAndOcr()

                clearPendingPartOne()
                if (captured == null) {
                    setAnswerUI("CAP", "#B45309", loading = false, triggerFeedback = false)
                    return@launch
                }

                pendingPartOneText = captured.first
                pendingPartOneBitmap = captured.third?.let { bmp ->
                    val copy = downscaleCaptureCopy(bmp, 860)
                    try { if (copy !== bmp && !bmp.isRecycled) bmp.recycle() } catch (_: Exception) {}
                    copy
                }

                withContext(Dispatchers.Main) {
                    isScanning = false
                    stopScanBreathingAnim()
                    refreshDynamicFlowChipsUI()
                    tvIslandTitle?.text = "Scroll ↓ Tap 2nd"
                    tvAnswerDisplay?.text = "1/2"
                    tvAnswerDisplay?.setTextColor(Color.parseColor("#D71921"))
                    Toast.makeText(
                        this@OverlayService,
                        "Part 1 (Question/Diagram) saved! Scroll to Options and tap '2nd'.",
                        Toast.LENGTH_SHORT
                    ).show()
                    try { bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) } } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                isScanning = false
                setAnswerUI("ERR", "#BE123C", loading = false, triggerFeedback = false)
            }
        }
    }

    private fun capturePartTwoAndSolve() {
        val part1Txt = pendingPartOneText
        val part1Bmp = pendingPartOneBitmap
        if (part1Txt.isNullOrBlank() && part1Bmp == null) {
            onUserTapSolve()
            return
        }

        scrollCaptureTimerJob?.cancel()
        currentSolveFlowJob?.cancel()
        isScanning = true
        setAnswerUI("...", "#D71921", loading = true, triggerFeedback = false)
        tvIslandTitle?.text = "Merging 1st + 2nd..."

        serviceScope.launch {
            val captured2 = if (screenCaptureManager != null) captureAndOcr() else null

            if (captured2 == null) {
                isScanning = false
                setAnswerUI("CAP", "#B45309", loading = false, triggerFeedback = false)
                return@launch
            }

            val mergedText = buildString {
                if (!part1Txt.isNullOrBlank()) {
                    append("View 1 (Question / Diagram): ")
                    append(part1Txt.trim())
                    append("  ")
                }
                if (captured2.first.isNotBlank()) {
                    append("View 2 (Options / Continuation): ")
                    append(captured2.first.trim())
                }
            }.trim()

            val stitched = withContext(Dispatchers.Default) {
                stitchBitmapsVertically(part1Bmp, captured2.third)
            }
            try {
                val cap2Bmp = captured2.third
                if (cap2Bmp != null && cap2Bmp !== stitched && !cap2Bmp.isRecycled) {
                    cap2Bmp.recycle()
                }
            } catch (_: Exception) {}
            clearPendingPartOne()

            withContext(Dispatchers.Main) {
                triggerSolveFlow(
                    cropScreenRect = null,
                    preCapturedData = Triple(mergedText, captured2.second, stitched),
                    forceVisionOverride = stitched != null
                )
            }
        }
    }

    private fun startTwoSecondScrollCaptureAndSolve() {
        if (screenCaptureManager == null) {
            Toast.makeText(this, "Screen capture not active yet.", Toast.LENGTH_SHORT).show()
            return
        }
        scrollCaptureTimerJob?.cancel()
        currentSolveFlowJob?.cancel()
        activeSolvePollJob?.cancel()
        isScanning = true
        setAnswerUI("...", "#D71921", loading = true, triggerFeedback = false)
        tvIslandTitle?.text = "Capturing View 1..."

        scrollCaptureTimerJob = serviceScope.launch {
            val view1 = if (screenCaptureManager != null) captureAndOcr() else null

            if (view1 == null) {
                isScanning = false
                setAnswerUI("CAP", "#B45309", loading = false, triggerFeedback = false)
                return@launch
            }

            val bmp1Copy = view1.third?.let { bmp ->
                val c = downscaleCaptureCopy(bmp, 860)
                try { if (c !== bmp && !bmp.isRecycled) bmp.recycle() } catch (_: Exception) {}
                c
            }

            // 2-Second interactive scroll window so user can scroll down to options!
            withContext(Dispatchers.Main) {
                stopScanBreathingAnim()
                tvIslandTitle?.text = "Scroll ↓ Options"
                tvAnswerDisplay?.text = "2s"
                tvAnswerDisplay?.setTextColor(Color.parseColor("#D71921"))
                if (isHapticFeedbackEnabled) triggerHapticFeedback("A")
            }
            delay(1000L)
            withContext(Dispatchers.Main) {
                tvIslandTitle?.text = "Scroll ↓ Options"
                tvAnswerDisplay?.text = "1s"
            }
            delay(1000L)

            withContext(Dispatchers.Main) {
                setAnswerUI("...", "#D71921", loading = true, triggerFeedback = false)
                tvIslandTitle?.text = "Solving 1+2..."
            }

            val view2 = if (screenCaptureManager != null) captureAndOcr() else null

            val mergedText = buildString {
                if (view1.first.isNotBlank()) {
                    append("View 1 (Question / Diagram): ")
                    append(view1.first.trim())
                    append("  ")
                }
                if (view2 != null && view2.first.isNotBlank()) {
                    append("View 2 (Scrolled Options): ")
                    append(view2.first.trim())
                }
            }.trim()

            val stitched = withContext(Dispatchers.Default) {
                stitchBitmapsVertically(bmp1Copy, view2?.third)
            }
            try { bmp1Copy?.let { if (it !== stitched && !it.isRecycled) it.recycle() } } catch (_: Exception) {}
            try { view2?.third?.let { if (it !== stitched && !it.isRecycled) it.recycle() } } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                triggerSolveFlow(
                    cropScreenRect = null,
                    preCapturedData = Triple(mergedText, view2?.second ?: view1.second, stitched),
                    forceVisionOverride = stitched != null
                )
            }
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
            x = dpToPx(8)
            y = dpToPx(58)
        }

        // Header controls
        val winRoot = geminiWindowView ?: return
        val headerDragArea = winRoot.findViewById<View>(R.id.header_drag_area)
        val btnWindowScan = winRoot.findViewById<TextView>(R.id.btn_window_scan)
        val btnSize = winRoot.findViewById<TextView>(R.id.btn_window_size)
        val btnOpacity = winRoot.findViewById<TextView>(R.id.btn_window_opacity)
        val btnMinimize = winRoot.findViewById<View>(R.id.btn_window_minimize)
        val btnClose = winRoot.findViewById<View>(R.id.btn_window_close)
        val btnResizeGrip = winRoot.findViewById<View>(R.id.btn_resize_grip)
        tvWindowAnswerBadge = winRoot.findViewById(R.id.tv_window_answer_badge)
        progressBar = winRoot.findViewById(R.id.web_progress_bar)
        geminiWebView = winRoot.findViewById(R.id.gemini_webview)

        if (isValidAnswerBadge(latestCachedAnswer)) {
            tvWindowAnswerBadge?.text = latestCachedAnswer
        }

        tvWindowAnswerBadge?.setOnClickListener {
            onUserTapSolve()
        }

        headerDragArea.setOnTouchListener(createDragListener(windowParams, { geminiWindowView }))

        btnWindowScan.setOnClickListener {
            val fallbackQ = pendingWebsiteFallbackQuestion
            if (pendingAuthSignInRequired) {
                launchPrismSignInActivity()
            } else if (!fallbackQ.isNullOrBlank() && btnWindowScan.text?.toString() == "Open Web") {
                openExternalFallbackForQuestion(fallbackQ)
            } else {
                onUserTapSolve()
            }
        }

        val btnNewChat = winRoot.findViewById<View?>(R.id.btn_window_new_chat)
        btnNewChat?.setOnClickListener {
            serviceScope.launch(Dispatchers.Main) {
                lastSyncedDomTurnSignature = ""
                forceResetGeminiWebChat()
                Toast.makeText(this@OverlayService, "Opened fresh chat", Toast.LENGTH_SHORT).show()
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

        geminiWebView?.let { wv ->
            configureWebView(wv)
            wv.loadUrl(GEMINI_WEB_URL)
        }

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

        // Keep software layer only on generic emulator images to avoid DRM node probes; use fast GPU hardware layer on physical phones
        val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")
        val layerType = if (isEmulator) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_NONE
        geminiWindowView?.setLayerType(layerType, null)
        webView.setLayerType(layerType, null)
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

        // Unified bridge to receive answers from Gemini Web DOM with solveId validation
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onAnswerResolved(answerLetter: String) {
                val letter = answerLetter.trim().uppercase()
                val capturedSolveId = activeSolveId
                if (capturedSolveId <= 0L) return
                Log.d(TAG, "Bridge onAnswerResolved: $letter (solve=$capturedSolveId)")
                mainHandler.post {
                    if (activeSolveId == capturedSolveId && deliveredSolveId != capturedSolveId) {
                        deliverResolvedAnswer(letter, solveId = capturedSolveId)
                    }
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

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isGeminiLoading = true
                isGeminiWebEditorHydrated = false
                geminiWebLastError = null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar?.visibility = View.GONE
                isGeminiLoading = false
                isGeminiReady = true
                Log.d(TAG, "Gemini WebView page finished")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    isGeminiLoading = false
                    geminiWebLastError = "WEB_ERROR_${error?.errorCode ?: -1}:${error?.description}"
                    Log.w(TAG, "Gemini WebView main frame error: $geminiWebLastError")
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                    val code = errorResponse?.statusCode ?: 0
                    if (code >= 400) {
                        isGeminiLoading = false
                        geminiWebLastError = "HTTP_$code"
                        Log.w(TAG, "Gemini WebView HTTP error: $geminiWebLastError")
                    }
                }
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                Log.w(TAG, "WebView renderer process gone — recovering lazily")
                isGeminiReady = false
                isGeminiWebEditorHydrated = false
                isGeminiLoading = false
                geminiWebLastError = "RENDERER_GONE"
                mainHandler.post {
                    try {
                        val host = geminiWindowView?.findViewById<RelativeLayout>(R.id.webview_host_container)
                        if (host != null) {
                            geminiWebView?.let { old ->
                                old.stopLoading()
                                host.removeView(old)
                                old.destroy()
                            }
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
        val initStart = PrismPerfLogger.nowMs()
        val coldInit = (geminiWindowView == null)
        withContext(Dispatchers.Main) {
            if (geminiWindowView == null) {
                setupGeminiWindow()
                bringFrontPillToTop()
            } else {
                geminiWebView?.onResume()
                geminiWebView?.resumeTimers()
            }
        }
        if (coldInit) {
            PrismPerfLogger.logStage(activeSolveId, "WEBVIEW_LAZY_INIT", PrismPerfLogger.nowMs() - initStart, status = "STARTED")
        }
        if (!isGeminiReady) {
            // Bounded wait (max ~3.0s) that exits immediately when page finishes or errors
            for (attempt in 0 until 20) {
                delay(150)
                if (isGeminiReady) break
                if (geminiWebLastError != null && !isGeminiLoading) {
                    PrismPerfLogger.logStage(
                        activeSolveId,
                        "WEBVIEW_READY",
                        PrismPerfLogger.nowMs() - initStart,
                        status = "FAILED",
                        detail = geminiWebLastError.orEmpty()
                    )
                    return false
                }
            }
        }
        if (geminiWebLastError != null && !isGeminiReady) {
            return false
        }
        val checkDomJs = """
            (function() {
                try {
                    let editor = document.querySelector('rich-textarea div[contenteditable="true"], div.ql-editor, div[contenteditable="true"], textarea');
                    let signInBtn = document.querySelector('a[href*="ServiceLogin"], a[aria-label*="Sign in" i], button[aria-label*="Sign in" i]');
                    return JSON.stringify({ hasEditor: !!editor, needsLogin: !editor && !!signInBtn });
                } catch (e) {
                    return JSON.stringify({ hasEditor: false, needsLogin: false });
                }
            })();
        """.trimIndent()
        repeat(12) {
            val dom = evaluateJsJson(checkDomJs)
            if (dom?.optBoolean("hasEditor", false) == true && dom.optBoolean("needsLogin", false) != true) {
                isGeminiWebEditorHydrated = true
                PrismPerfLogger.logStage(activeSolveId, "WEBVIEW_READY", PrismPerfLogger.nowMs() - initStart, status = "EDITOR_READY")
                return true
            }
            if (dom?.optBoolean("needsLogin", false) == true) {
                isGeminiWebEditorHydrated = false
                PrismPerfLogger.logStage(activeSolveId, "WEBVIEW_READY", PrismPerfLogger.nowMs() - initStart, status = "NEEDS_LOGIN")
                return false
            }
            delay(150)
        }
        return geminiWebView != null && geminiWebLastError == null && isGeminiWebEditorHydrated
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
     * When Crop-to-Scan is enabled in Advanced Settings, opens the glowing Liquid Glass region selector first.
     */
    private fun launchPrismSignInActivity() {
        pendingAuthSignInRequired = false
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Open Prism to sign in with Google.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openExternalFallbackForQuestion(questionText: String) {
        pendingWebsiteFallbackQuestion = null
        val opened = F1WebsiteFallbackAdapter.openExternalBrowserFallback(this, questionText)
        if (opened) {
            Toast.makeText(this, "Question copied & opened in browser.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Question copied to clipboard. Open your browser to continue.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelActiveSolveFromUser() {
        scrollCaptureTimerJob?.cancel()
        currentSolveFlowJob?.cancel()
        activeSolvePollJob?.cancel()
        F1MasterEngine.cancelActiveSolve("USER_CANCELLED")
        activeSolveId = PrismPerfLogger.nextSessionId()
        isScanning = false
        releaseHighPerformanceWakeLock()
        mainHandler.post {
            stopScanBreathingAnim()
            if (!isCamouflageEnabled && !isAdvEnabled(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE)) {
                tvIslandTitle?.text = "Cancelled"
            }
            setAnswerUI("RETRY", "#B45309", loading = false, triggerFeedback = false, preserveIslandTitle = true)
            geminiWindowView?.findViewById<TextView>(R.id.btn_window_scan)?.text = "Solve"
        }
    }

    private fun onUserTapSolve() {
        val now = System.currentTimeMillis()
        if (now - lastManualTapMs < 350L) return
        lastManualTapMs = now
        if (pendingAuthSignInRequired) {
            launchPrismSignInActivity()
            return
        }
        pendingWebsiteFallbackQuestion = null
        historyBrowseIndex = -1
        if (isInlineToolsVisible) {
            setInlineIslandToolsVisible(false)
        }
        wakeAndScheduleIdleFade()
        if (isScanning) {
            Log.d(TAG, "Ignoring duplicate tap while solve is already in flight (use Stop tool to cancel)")
            val isDisguised = isCamouflageEnabled || isAdvEnabled(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE)
            if (!isDisguised) {
                mainHandler.post {
                    val currentLabel = tvIslandTitle?.text?.toString().orEmpty()
                    if (!currentLabel.contains("Solving", ignoreCase = true) && !currentLabel.contains("Reading", ignoreCase = true)) {
                        tvIslandTitle?.text = "Solving..."
                    }
                }
            }
            return
        }
        if (isAdvEnabled(MainActivity.PREF_KEY_ADV_CROP_TO_SCAN) && screenCaptureManager != null) {
            showCropRegionSelector()
        } else {
            triggerSolveFlow(cropScreenRect = null)
        }
    }

    /**
     * Core solve flow:
     * 1. Captures screenshot (or cropped Liquid Glass region) and extracts text via ML Kit OCR.
     * 2. Routes strictly to the user's selected Answer Method without cross-jumping from Server Sol
     *    into the slow Background Gemini WebView:
     *    - Method 1 (`FIREBASE_SERVER`): Fast single-pass Firebase Cloud Server (`<2s`, zero double-solve).
     *    - Method 2 (`BACKGROUND_GEMINI`): Screenshot -> ML Kit OCR -> Built-in Background Gemini Flash.
     *    - Method 3 (`API_KEYS`): Local API Keys (NVIDIA Free, OpenRouter, Gemini, OpenAI) with automatic quota shifting.
     */
    private fun triggerSolveFlow(
        cropScreenRect: Rect? = null,
        preCapturedData: Triple<String, List<Pair<String, Rect>>, Bitmap?>? = null,
        forceVisionOverride: Boolean = false,
        forceFreshResolve: Boolean = false,
        previousWrongAnswer: String? = null
    ) {
        val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        val method = SolverMethod.fromId(
            prefs.getString(MainActivity.PREF_KEY_SOLVER_METHOD, solverMethod.id)
        )
        solverMethod = method
        lastSolvedCropRect = cropScreenRect
        val autoFailover = prefs.getBoolean(MainActivity.PREF_KEY_AUTO_FAILOVER, true)
        val solveMode = QuestionSolveMode.fromId(
            prefs.getString(MainActivity.PREF_KEY_QUESTION_SOLVE_MODE, QuestionSolveMode.STANDARD.id)
        )

        val selectedModel = prefs.getString(
            MainActivity.PREF_KEY_GEMINI_MODEL,
            PrismAiSolverEngine.DEFAULT_CLOUD_MODEL
        ) ?: PrismAiSolverEngine.DEFAULT_CLOUD_MODEL
        val subjectMode = SubjectMode.fromId(
            prefs.getString(MainActivity.PREF_KEY_SUBJECT_MODE, SubjectMode.GENERAL.id)
        )
        val perfModeId = prefs.getString(MainActivity.PREF_KEY_ENGINE_PERFORMANCE_MODE, EnginePerformanceMode.NORMAL.id)
        val perfMode = EnginePerformanceMode.fromId(perfModeId)
        val dualVerifyPref = prefs.getBoolean(MainActivity.PREF_KEY_DUAL_VERIFY, false)
        val dualVerify = (dualVerifyPref || perfMode == EnginePerformanceMode.HIGH_ACCURACY) && perfMode != EnginePerformanceMode.EASY_RAM
        val translateToEnglish = prefs.getBoolean(MainActivity.PREF_KEY_TRANSLATE_ENGLISH, false)

        val hasCropSelection = cropScreenRect != null
        val isGhostDomMethod = method == SolverMethod.GHOST_DOM
        val isDirectVisionMethod = method == SolverMethod.DIRECT_VISION
        val autoCopyClipboard = prefs.getBoolean(MainActivity.PREF_KEY_AUTO_COPY_CLIPBOARD, false)

        if (preCapturedData == null && screenCaptureManager == null) {
            Toast.makeText(this, "Tap 'Start Solving' inside Prism Solver first.", Toast.LENGTH_SHORT).show()
        }

        val currentSolveId = PrismPerfLogger.nextSessionId()
        activeSolveId = currentSolveId
        val solveFlowStartMs = PrismPerfLogger.nowMs()
        // Never let a prior transient timeout silently lock out Cloudflare or Firebase on manual Solve/Retry
        PrismAiSolverEngine.resetTransientCooldownsForManualRetry()
        F1ProviderHealthManager.resetTransientCooldownsForManualRetry()
        currentSolveFlowJob?.cancel()
        activeSolvePollJob?.cancel()
        isScanning = true
        acquireSolveWakeLock()
        mainHandler.removeCallbacks(idleFadeRunnable)
        PrismPerfLogger.logStage(
            currentSolveId,
            "SOLVE_START",
            0L,
            status = "STARTED",
            detail = "method=${method.id} crop=$hasCropSelection forceFresh=$forceFreshResolve"
        )

        // Capture prior badge before clearing so auto-rescan / explicit re-solve knows what was flagged
        val priorShownBadge = previousWrongAnswer?.takeIf { isValidAnswerBadge(it) }
            ?: latestCachedAnswer.takeIf { isValidAnswerBadge(it) }

        // Clear previous question's answer immediately so stale answers can NEVER reappear
        latestCachedAnswer = ""
        latestAnswerModel = null
        mainHandler.post {
            llIslandExplanationDrawer?.visibility = View.GONE
            tvIslandExplanation?.text = ""
            tvIslandMeta?.text = ""
        }
        setAnswerUI("...", "#0369A1", loading = true, triggerFeedback = false, preserveIslandTitle = true)
        if (!isCamouflageEnabled && !isAdvEnabled(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE)) {
            tvIslandTitle?.text = when {
                forceFreshResolve -> "Re-checking..."
                preCapturedData != null || isGhostDomMethod -> "Reading text..."
                else -> "Capturing..."
            }
        }
        val btnWindowScan = geminiWindowView?.findViewById<TextView>(R.id.btn_window_scan)
        btnWindowScan?.text = when {
            forceFreshResolve -> "Re-Check..."
            method == SolverMethod.TURBO_HYBRID -> "All-in-One..."
            method == SolverMethod.CIRCLE_TO_SEARCH -> "Circle..."
            method == SolverMethod.DIRECT_VISION -> "Vision..."
            method == SolverMethod.GHOST_DOM -> "Reader..."
            else -> "Reading..."
        }

        currentSolveFlowJob = serviceScope.launch {
            var bitmapToRecycle: Bitmap? = null
            var safePromptInput = "Solve the multiple choice question, equation, or diagram shown on screen."
            var textBlocks = emptyList<Pair<String, Rect>>()
            try {
                // Non-delivering callback so racing engines (WebView vs Cloud vs API Keys) always return
                // their AnswerModel cleanly without double-delivering empty textBlocks.
                val geminiWebSolverCallback: suspend (String, String?, String) -> AnswerModel? = { webPrompt, base64Img, previewTxt ->
                    var resolvedModel: AnswerModel? = null
                    if (ensureGeminiWindowReady()) {
                        if (method != SolverMethod.TURBO_HYBRID) {
                            withContext(Dispatchers.Main) {
                                btnWindowScan?.text = "Gemini AI..."
                            }
                        }
                        solveViaGeminiWebSession(
                            promptText = webPrompt,
                            solveId = currentSolveId,
                            questionPreview = previewTxt,
                            isPrimaryMethod = false,
                            suppressFailureUi = true,
                            base64Jpeg = base64Img,
                            consensusMode = true,
                            onConsensusWebResolved = { resolvedModel = it }
                        )
                    }
                    resolvedModel
                }

                var hasAlreadyCapturedScreen = preCapturedData != null

                // ═══════════════════════════════════════════════════════════════════════
                // PRISM F1 MASTER ENGINE — Single Authoritative Intelligent MCQ Solver
                // All ordinary MCQ solving passes through ONE unified pipeline:
                // Auth Gate -> Single Capture/OCR -> Classify -> Account Cache ->
                // Adaptive Staggered Race -> Bounded Vision/Web Escalation -> Telemetry.
                // ═══════════════════════════════════════════════════════════════════════
                run {
                    val captureStartMs = PrismPerfLogger.nowMs()
                    val freshBitmap: Bitmap? = when {
                        preCapturedData?.third != null -> preCapturedData.third
                        cropScreenRect != null && screenCaptureManager != null -> {
                            val croppedData = captureAndOcrCropped(cropScreenRect)
                            textBlocks = croppedData?.second.orEmpty()
                            croppedData?.third
                        }
                        screenCaptureManager != null -> {
                            captureFrameOnly() ?: run {
                                delay(45)
                                captureFrameOnly()
                            }
                        }
                        else -> null
                    }
                    val captureDurationMs = (PrismPerfLogger.nowMs() - captureStartMs).coerceAtLeast(0L)
                    bitmapToRecycle = freshBitmap
                    hasAlreadyCapturedScreen = true

                    var effectiveForceFresh = forceFreshResolve
                    var effectivePrevWrong = priorShownBadge
                    val autoSameRescan = solveOrchestrator.detectSameQuestionRescan(freshBitmap, preCapturedData?.first)
                    if (autoSameRescan != null) {
                        effectiveForceFresh = true
                        effectivePrevWrong = effectivePrevWrong ?: autoSameRescan
                    }
                    if (effectiveForceFresh) {
                        solveOrchestrator.invalidateWrongAnswerEverywhere(
                            bitmap = freshBitmap,
                            rawText = preCapturedData?.first ?: lastSolvedQuestionText,
                            subjectMode = subjectMode
                        )
                    }

                    val preRaceText = preCapturedData?.first
                    val preRaceBlocks = preCapturedData?.second ?: textBlocks.takeIf { it.isNotEmpty() }
                    val disguiseActiveAtStart = isCamouflageEnabled || isAdvEnabled(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE)
                    withContext(Dispatchers.Main) {
                        btnWindowScan?.text = if (effectiveForceFresh) "Re-Check..." else "F1 Race..."
                        if (!disguiseActiveAtStart) {
                            tvIslandTitle?.text = when {
                                effectiveForceFresh -> "Re-checking..."
                                preRaceText.isNullOrBlank() -> "Reading text..."
                                else -> "Solving..."
                            }
                        }
                    }

                    val warmWebCallback = if (geminiWindowView != null && isGeminiReady && isGeminiWebEditorHydrated) {
                        geminiWebSolverCallback
                    } else {
                        null
                    }
                    val activeRaceMode = if (dualVerify || effectiveForceFresh || method == SolverMethod.TURBO_HYBRID) {
                        F1RaceMode.ACCURACY_CONSENSUS
                    } else {
                        F1RaceMode.FAST
                    }

                    val elapsedTickerJob = launch {
                        val tickerStartNs = System.nanoTime()
                        while (isActive && activeSolveId == currentSolveId && deliveredSolveId != currentSolveId) {
                            delay(500L)
                            val elapsedTenths = ((System.nanoTime() - tickerStartNs) / 100_000_000L).coerceAtLeast(1L)
                            val secText = "${elapsedTenths / 10}.${elapsedTenths % 10}s"
                            withContext(Dispatchers.Main) {
                                if (activeSolveId != currentSolveId || deliveredSolveId == currentSolveId) return@withContext
                                val isDisguised = isCamouflageEnabled || isAdvEnabled(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE)
                                if (!isDisguised) {
                                    tvIslandTitle?.text = if (effectiveForceFresh) {
                                        "Re-checking $secText"
                                    } else {
                                        "Solving $secText"
                                    }
                                }
                            }
                        }
                    }

                    val f1Result = try {
                        F1MasterEngine.solve(
                            context = this@OverlayService,
                            request = F1SolveRequest(
                                requestId = currentSolveId,
                                preExtractedText = preRaceText,
                                preExtractedBlocks = preRaceBlocks,
                                screenBitmap = freshBitmap,
                                cropRect = cropScreenRect,
                                captureDurationMs = captureDurationMs,
                                subjectMode = subjectMode,
                                solveMode = solveMode,
                                raceMode = activeRaceMode,
                                preferredMethod = method,
                                autoFailover = autoFailover,
                                requestedModel = selectedModel,
                                dualVerify = dualVerify,
                                translateToEnglish = translateToEnglish,
                                forceFreshResolve = effectiveForceFresh,
                                previousWrongAnswer = effectivePrevWrong,
                                allowGhostDomExtraction = true,
                                requireGoogleAuth = true,
                                geminiWebSolverCallback = warmWebCallback,
                                onProgressUpdate = { stageName, candidate ->
                                    if (candidate != null && activeSolveId == currentSolveId && deliveredSolveId != currentSolveId) {
                                        withContext(Dispatchers.Main) {
                                            if (activeSolveId != currentSolveId || deliveredSolveId == currentSolveId) return@withContext
                                            val isDisguised = isCamouflageEnabled || isAdvEnabled(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE)
                                            if (!isDisguised) {
                                                tvIslandTitle?.text = "F1 • $stageName"
                                            }
                                            btnWindowScan?.text = candidate.selectedOption
                                        }
                                    }
                                }
                            ),
                            ocrEngine = ocrEngine
                        )
                    } finally {
                        elapsedTickerJob.cancel()
                    }

                    if (f1Result.authRequired) {
                        pendingAuthSignInRequired = true
                        pendingWebsiteFallbackQuestion = null
                        withContext(Dispatchers.Main) {
                            setAnswerUI("LOGIN", "#D71921", loading = false, triggerFeedback = false)
                            tvIslandMeta?.text = "Google Sign-In Required"
                            tvIslandExplanation?.text = f1Result.authErrorMessage.ifBlank {
                                "Tap LOGIN or open Prism to sign in with your Google account."
                            }
                            btnWindowScan?.text = "Sign In"
                            isScanning = false
                        }
                        PrismPerfLogger.logStage(
                            currentSolveId,
                            "SOLVE_TOTAL",
                            PrismPerfLogger.nowMs() - solveFlowStartMs,
                            status = "AUTH_BLOCKED",
                            detail = f1Result.authErrorMessage
                        )
                        return@launch
                    }

                    pendingAuthSignInRequired = false
                    if (textBlocks.isEmpty()) {
                        textBlocks = f1Result.textBlocks
                    }
                    safePromptInput = f1Result.extractedQuestionPreview.ifBlank { safePromptInput }
                    if (autoCopyClipboard && safePromptInput.isNotBlank()) {
                        try {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Prism F1 Question", safePromptInput))
                        } catch (_: Exception) {}
                    }

                    val finalModel = f1Result.answerModel
                    if (finalModel != null && isValidAnswerBadge(finalModel.correctChoice)) {
                        pendingWebsiteFallbackQuestion = null
                        solveOrchestrator.recordDeliveredFingerprint(freshBitmap, safePromptInput, finalModel.correctChoice)
                        withContext(Dispatchers.Main) {
                            latestScreenTextBlocks = textBlocks
                            isScanning = false
                            deliverResolvedAnswer(
                                rawLetter = finalModel.correctChoice.trim(),
                                solveId = currentSolveId,
                                questionPreview = safePromptInput,
                                answerModel = finalModel,
                                wasFreshRescan = effectiveForceFresh
                            )
                        }
                        PrismPerfLogger.logStage(
                            currentSolveId,
                            "SOLVE_TOTAL",
                            f1Result.raceReport.totalMs,
                            status = if (f1Result.raceReport.cacheHit) "CACHE_HIT" else "SUCCESS",
                            detail = "engine=F1 winner=${f1Result.raceReport.winnerProviderName} badge=${finalModel.correctChoice}"
                        )
                        return@launch
                    }

                    // All automated servers failed or timed out -> clean non-hanging fallback state
                    if (activeSolveId == currentSolveId && deliveredSolveId != currentSolveId) {
                        if (f1Result.websiteFallbackReady && safePromptInput.isNotBlank()) {
                            pendingWebsiteFallbackQuestion = safePromptInput
                            try {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "Prism F1 Question",
                                        F1WebsiteFallbackAdapter.buildClipboardReadyPrompt(safePromptInput)
                                    )
                                )
                            } catch (_: Exception) {}
                        }
                        val (failBadge, failMeta) = when (f1Result.failureCategory) {
                            F1FailureCategory.UNREADABLE_SCREEN -> "TXT" to "Screen Unreadable"
                            F1FailureCategory.QUOTA_LIMITED -> "BUSY" to "Servers Rate-Limited"
                            F1FailureCategory.AUTH_OR_APP_CHECK -> "LOGIN" to "Sign-In / App Check"
                            F1FailureCategory.NETWORK_TIMEOUT -> "RETRY" to "Network Timeout"
                            else -> "RETRY" to "F1 Fallback Ready"
                        }
                        if (f1Result.failureCategory == F1FailureCategory.AUTH_OR_APP_CHECK && !f1Result.websiteFallbackReady) {
                            pendingAuthSignInRequired = true
                        }
                        withContext(Dispatchers.Main) {
                            setAnswerUI(failBadge, "#D71921", loading = false, triggerFeedback = false)
                            tvIslandMeta?.text = failMeta
                            tvIslandExplanation?.text = f1Result.errorMessage.ifBlank {
                                "No server replied in time. Tap RETRY to re-race or Open Web for browser fallback."
                            }
                            btnWindowScan?.text = if (f1Result.websiteFallbackReady) "Open Web" else "Retry"
                            isScanning = false
                        }
                        val r = f1Result.raceReport
                        val sanitizedDiag = "diag:cat=${f1Result.failureCategory.name},cap=${r.captureMs}ms,ocr=${r.ocrMs}ms,cache=${r.cacheLookupMs}ms,val=${r.validationMs}ms,tot=${r.totalMs}ms"
                        serviceScope.launch(Dispatchers.IO) {
                            runCatching {
                                FirebaseTelemetryRepository.recordSolveRequest(
                                    context = applicationContext,
                                    feature = TelemetryFeature.DYNAMIC_ISLAND,
                                    backend = TelemetryBackend.FAILED,
                                    modelUsed = "F1 • ${f1Result.failureCategory.name}",
                                    latencyMs = r.totalMs,
                                    status = TelemetryStatus.FAILED,
                                    questionPreview = sanitizedDiag,
                                    answerBadge = failBadge
                                )
                            }
                        }
                        PrismPerfLogger.logStage(
                            currentSolveId,
                            "SOLVE_TOTAL",
                            f1Result.raceReport.totalMs,
                            status = "FAILED",
                            detail = "engine=F1 cat=${f1Result.failureCategory.name} reason=${f1Result.errorMessage}"
                        )
                        return@launch
                    }
                }
            } catch (ce: CancellationException) {
                PrismPerfLogger.logStage(
                    currentSolveId,
                    "SOLVE_TOTAL",
                    PrismPerfLogger.nowMs() - solveFlowStartMs,
                    status = "CANCELLED",
                    detail = "method=${method.id}"
                )
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "SolveFlow error: ${e.message}", e)
                solveOrchestrator.recordFailureDiagnostic(
                    solveId = currentSolveId,
                    elapsedMs = PrismPerfLogger.nowMs() - solveFlowStartMs,
                    method = method,
                    failedStage = "UNHANDLED_SOLVE_EXCEPTION",
                    ocrStatus = "UNKNOWN",
                    throwable = e
                )
                withContext(Dispatchers.Main) {
                    isScanning = false
                    stopScanBreathingAnim()
                    btnWindowScan?.text = "Solve"
                }
            } finally {
                if (activeSolveId == currentSolveId) {
                    releaseHighPerformanceWakeLock()
                    if (isScanning && deliveredSolveId != currentSolveId) {
                        mainHandler.post {
                            if (activeSolveId == currentSolveId) {
                                isScanning = false
                                stopScanBreathingAnim()
                                btnWindowScan?.text = "Solve"
                            }
                        }
                    }
                }
                try {
                    bitmapToRecycle?.recycle()
                } catch (_: Exception) {}
            }
        }
    }

    private fun scaleOcrBlocksToScreen(
        blocks: List<Pair<String, Rect>>,
        bitmap: Bitmap?
    ): List<Pair<String, Rect>> {
        if (bitmap == null || blocks.isEmpty() || bitmap.width <= 0 || bitmap.height <= 0) return blocks
        val (realW, realH) = screenCaptureManager?.getRealScreenDimensions()
            ?: (resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels)
        if (realW <= bitmap.width + 4 || realH <= bitmap.height + 4) return blocks
        val sx = realW.toFloat() / bitmap.width.toFloat()
        val sy = realH.toFloat() / bitmap.height.toFloat()
        return blocks.map { (txt, r) ->
            txt to Rect(
                (r.left * sx).roundToInt(),
                (r.top * sy).roundToInt(),
                (r.right * sx).roundToInt(),
                (r.bottom * sy).roundToInt()
            )
        }
    }

    /**
     * Captures a clean screenshot frame without OCR when Multimodal Vision needs visual context
     * alongside Native Accessibility text.
     */
    private suspend fun captureFrameOnly(): Bitmap? {
        val scm = screenCaptureManager ?: return null
        val wasWindowOpen = isWindowVisible
        val sid = activeSolveId
        val beforeHideMs = System.currentTimeMillis()
        withContext(Dispatchers.Main) {
            islandBorderDrawable?.setHostVisible(false)
            bubbleView?.visibility = View.INVISIBLE
            geminiWindowView?.visibility = View.INVISIBLE
        }
        delay(20)

        val bitmap: Bitmap? = withContext(Dispatchers.IO) {
            scm.captureGuaranteedFreshFrame(minTimestampMs = beforeHideMs, sessionId = sid)
        }

        withContext(Dispatchers.Main) {
            bubbleView?.visibility = View.VISIBLE
            islandBorderDrawable?.setHostVisible(true)
            if (wasWindowOpen && geminiWindowView != null) {
                geminiWindowView?.visibility = View.VISIBLE
                setWindowFocusable(true)
                geminiWebView?.requestFocus()
            } else if (geminiWindowView != null) {
                parkGeminiWindowInRam()
            }
            bringFrontPillToTop()
        }
        return bitmap
    }

    private suspend fun captureAndOcrCropped(cropScreenRect: Rect): Triple<String, List<Pair<String, Rect>>, Bitmap?>? {
        val fullBitmap = captureFrameOnly() ?: return null
        val (realW, realH) = screenCaptureManager?.getRealScreenDimensions()
            ?: (resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels)
        val scaleX = fullBitmap.width.toFloat() / realW.coerceAtLeast(1).toFloat()
        val scaleY = fullBitmap.height.toFloat() / realH.coerceAtLeast(1).toFloat()

        val left = (cropScreenRect.left * scaleX).toInt().coerceIn(0, fullBitmap.width - 2)
        val top = (cropScreenRect.top * scaleY).toInt().coerceIn(0, fullBitmap.height - 2)
        val right = (cropScreenRect.right * scaleX).toInt().coerceIn(left + 1, fullBitmap.width)
        val bottom = (cropScreenRect.bottom * scaleY).toInt().coerceIn(top + 1, fullBitmap.height)

        val cropped = try {
            Bitmap.createBitmap(fullBitmap, left, top, (right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1))
        } catch (_: Exception) {
            fullBitmap
        }
        if (cropped !== fullBitmap && !fullBitmap.isRecycled) {
            fullBitmap.recycle()
        }

        val ocrRes = ocrEngine.extractTextWithDiagnostics(cropped, sessionId = activeSolveId)
        val invCropScaleX = cropScreenRect.width().toFloat() / cropped.width.coerceAtLeast(1).toFloat()
        val invCropScaleY = cropScreenRect.height().toFloat() / cropped.height.coerceAtLeast(1).toFloat()
        val offsetBlocks = ocrRes.blocks.map { (txt, r) ->
            txt to Rect(
                (r.left * invCropScaleX).roundToInt() + cropScreenRect.left,
                (r.top * invCropScaleY).roundToInt() + cropScreenRect.top,
                (r.right * invCropScaleX).roundToInt() + cropScreenRect.left,
                (r.bottom * invCropScaleY).roundToInt() + cropScreenRect.top
            )
        }
        return Triple(ocrRes.text, offsetBlocks, cropped)
    }

    private suspend fun captureAndOcr(): Triple<String, List<Pair<String, Rect>>, Bitmap?>? {
        val scm = screenCaptureManager ?: return null
        val wasWindowOpen = isWindowVisible
        val sid = activeSolveId
        val beforeHideMs = System.currentTimeMillis()
        withContext(Dispatchers.Main) {
            islandBorderDrawable?.setHostVisible(false)
            bubbleView?.visibility = View.INVISIBLE
            geminiWindowView?.visibility = View.INVISIBLE
        }
        delay(20)

        val bitmap: Bitmap? = withContext(Dispatchers.IO) {
            scm.captureGuaranteedFreshFrame(minTimestampMs = beforeHideMs, sessionId = sid)
        }

        withContext(Dispatchers.Main) {
            bubbleView?.visibility = View.VISIBLE
            islandBorderDrawable?.setHostVisible(true)
            if (wasWindowOpen && geminiWindowView != null) {
                geminiWindowView?.visibility = View.VISIBLE
                setWindowFocusable(true)
                geminiWebView?.requestFocus()
            } else if (geminiWindowView != null) {
                parkGeminiWindowInRam()
            }
            bringFrontPillToTop()
        }

        if (bitmap == null) return null

        val ocrRes = ocrEngine.extractTextWithDiagnostics(bitmap, sessionId = sid)
        val screenBlocks = scaleOcrBlocksToScreen(ocrRes.blocks, bitmap)
        return Triple(ocrRes.text, screenBlocks, bitmap)
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
        solveId: Long,
        questionPreview: String? = null,
        answerModel: AnswerModel? = null,
        displayBadgeOverride: String? = null,
        triggerFeedback: Boolean = true,
        wasFreshRescan: Boolean = false
    ) {
        val trimmed = rawLetter.trim()
        if (!isValidAnswerBadge(trimmed)) return
        val formattedBadge = if (trimmed.length == 1 && trimmed[0].uppercaseChar() in 'A'..'F') {
            trimmed.uppercase()
        } else {
            trimmed.take(40)
        }
        val uiBadge = displayBadgeOverride?.trim()?.takeIf { it.isNotBlank() }?.take(40) ?: formattedBadge

        if (solveId > 0L && deliveredSolveId == solveId) return
        if (solveId > 0L) {
            deliveredSolveId = solveId
        }
        if (!questionPreview.isNullOrBlank() && questionPreview.count { it.isLetterOrDigit() } >= 8) {
            lastSolvedQuestionSignature = computeQuestionSignature(questionPreview)
            lastSolvedQuestionText = questionPreview
        }

        activeSolvePollJob?.cancel()
        latestCachedAnswer = uiBadge
        latestAnswerModel = answerModel

        try {
            val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
            val newCount = prefs.getInt("PREF_SOLVED_COUNT", 0) + 1
            val editor = prefs.edit()
                .putString("PREF_LAST_SOLVED_ANSWER", uiBadge)
                .putInt("PREF_SOLVED_COUNT", newCount)

            if (!questionPreview.isNullOrBlank()) {
                val snippet = questionPreview.lines()
                    .map { it.trim() }
                    .firstOrNull { it.length >= 6 }
                    ?.replace("||", " ")
                    ?.take(72) ?: "Screen Question"
                val expSummary = listOfNotNull(
                    answerModel?.shortAnswer?.trim()?.takeIf { it.isNotBlank() },
                    answerModel?.explanation?.trim()?.takeIf { it.isNotBlank() }
                ).joinToString(": ").replace("\n", " ").replace("||", " ").take(120)
                val subjId = prefs.getString(MainActivity.PREF_KEY_SUBJECT_MODE, SubjectMode.GENERAL.id)
                    ?: SubjectMode.GENERAL.id
                val existing = prefs.getString("PREF_SOLVE_HISTORY", "") ?: ""
                val entry = "${System.currentTimeMillis()}||$uiBadge||$snippet||$expSummary||$subjId"
                val merged = (listOf(entry) + existing.split("\n").filter { it.isNotBlank() })
                    .take(25)
                    .joinToString("\n")
                editor.putString("PREF_SOLVE_HISTORY", merged)
            }
            editor.apply()
        } catch (_: Exception) {}

        // Persist to offline Room study history, Cloud Firestore deck, and Firebase Telemetry Dashboard
        overlaySessionRequestsCount++
        val solveLatencyMs = if (lastManualTapMs > 0L) {
            (System.currentTimeMillis() - lastManualTapMs).coerceIn(0L, 60_000L)
        } else {
            0L
        }
        val telemetryBackend = when {
            answerModel?.isCachedResult == true -> {
                val lbl = answerModel.engineLabel.lowercase()
                if (lbl.contains("shared") || lbl.contains("cloud")) TelemetryBackend.SHARED_CACHE
                else TelemetryBackend.LOCAL_CACHE
            }
            else -> TelemetryBackend.classifyFromSourceLabel(answerModel?.engineLabel)
        }
        val telemetryStatus = if (answerModel?.isCachedResult == true) {
            TelemetryStatus.CACHE_HIT
        } else {
            TelemetryStatus.SUCCESS
        }
        val telemetryModel = answerModel?.engineLabel?.takeIf { it.isNotBlank() } ?: solverMethod.shortName

        if (!questionPreview.isNullOrBlank()) {
            val subjId = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .getString(MainActivity.PREF_KEY_SUBJECT_MODE, SubjectMode.GENERAL.id)
                ?: SubjectMode.GENERAL.id
            serviceScope.launch(Dispatchers.IO) {
                solveOrchestrator.persistSolvedRecord(
                    questionPreview = questionPreview,
                    formattedBadge = formattedBadge,
                    answerModel = answerModel,
                    subjectId = subjId,
                    sourceMethodId = solverMethod.id
                )
                FirebaseTelemetryRepository.recordSolveRequest(
                    context = applicationContext,
                    feature = TelemetryFeature.DYNAMIC_ISLAND,
                    backend = telemetryBackend,
                    modelUsed = telemetryModel,
                    latencyMs = solveLatencyMs,
                    status = telemetryStatus,
                    questionPreview = questionPreview,
                    answerBadge = uiBadge
                )
            }
        } else {
            serviceScope.launch(Dispatchers.IO) {
                FirebaseTelemetryRepository.recordSolveRequest(
                    context = applicationContext,
                    feature = TelemetryFeature.DYNAMIC_ISLAND,
                    backend = telemetryBackend,
                    modelUsed = telemetryModel,
                    latencyMs = solveLatencyMs,
                    status = telemetryStatus,
                    questionPreview = "Dynamic Island Quick Solve",
                    answerBadge = uiBadge
                )
            }
        }

        mainHandler.post {
            val netSpeedDisguise = isAdvEnabled(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE)
            val hideExplanation = isCamouflageEnabled || netSpeedDisguise
            if (!hideExplanation) {
                val verifiedPrefix = when {
                    wasFreshRescan -> "Re-Verified • "
                    answerModel?.isCachedResult == true -> "Cached • "
                    answerModel?.verified == true -> "Verified • "
                    else -> ""
                }
                val confPct = answerModel?.confidence?.coerceIn(50, 100) ?: 94
                tvIslandMeta?.text = "${verifiedPrefix}${confPct}% Sure • tap card to hide"
                tvIslandSourceTag?.text = when {
                    wasFreshRescan -> "Re-Verified • Cache Updated"
                    answerModel?.isCachedResult == true -> "Offline Cache"
                    else -> answerModel?.engineLabel?.takeIf { it.isNotBlank() } ?: "AI Verified"
                }
                tvIslandSourceTag?.setTextColor(
                    Color.parseColor(
                        when {
                            wasFreshRescan -> "#34D399"
                            answerModel?.isCachedResult == true -> "#F59E0B"
                            else -> "#8E8E93"
                        }
                    )
                )
                val rawBody = buildString {
                    val shortTxt = answerModel?.shortAnswer?.trim().orEmpty()
                    val explTxt = answerModel?.explanation?.trim().orEmpty()
                    if (shortTxt.isNotBlank() && !shortTxt.equals(formattedBadge, ignoreCase = true)) {
                        append(shortTxt)
                        if (explTxt.isNotBlank()) append(": ")
                    }
                    append(explTxt)
                    val transTxt = answerModel?.translatedQuestion?.trim().orEmpty()
                    if (transTxt.isNotBlank()) {
                        append("\nEN: ")
                        append(transTxt)
                    }
                }.trim()
                val oneLineFallback = if (rawBody.isNotBlank()) {
                    rawBody
                } else if (formattedBadge.length == 1 && formattedBadge[0] in 'A'..'F') {
                    "Option $formattedBadge selected"
                } else {
                    "Answer: $formattedBadge"
                }
                tvIslandExplanation?.text = oneLineFallback
                // Keep the Dynamic Island sleek & compact after solving — do NOT auto-popup
                // the bulky extra Re-Solve / Explanation drawer unless the user taps Explain.
                llIslandExplanationDrawer?.visibility = View.GONE
            } else {
                tvIslandExplanation?.text = ""
                llIslandExplanationDrawer?.visibility = View.GONE
            }

            geminiWindowView?.findViewById<TextView>(R.id.btn_window_scan)?.text = "Solve"
            setAnswerUI(uiBadge, "#047857", loading = false, triggerFeedback = triggerFeedback)
            bringFrontPillToTop()
        }
    }

    private suspend fun evaluateJsJson(js: String): JSONObject? =
        withTimeoutOrNull(2_500L) {
            withContext(Dispatchers.Main) {
                val wv = geminiWebView ?: return@withContext null
                suspendCancellableCoroutine { cont ->
                    wv.evaluateJavascript(js) { raw ->
                        if (cont.isActive) {
                            cont.resume(parseJsEvalJson(raw))
                        }
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
                if (check?.optBoolean("needsLogin", false) == true) {
                    isGeminiWebEditorHydrated = false
                    return false
                }
                if (check?.optBoolean("hasEditor", false) == true && check.optInt("turnCount", 99) == 0) {
                    isGeminiReady = true
                    isGeminiWebEditorHydrated = true
                    return true
                }
            }
        }

        // Clean navigation fallback: wait until onPageFinished sets isGeminiReady = true so we NEVER race the old unloading DOM!
        withContext(Dispatchers.Main) {
            isGeminiReady = false
            isGeminiWebEditorHydrated = false
            geminiWebView?.loadUrl(GEMINI_WEB_URL)
        }
        delay(350)
        repeat(35) {
            delay(200)
            if (!isGeminiReady) return@repeat
            val check = evaluateJsJson(checkCleanStateJs)
            if (check?.optBoolean("needsLogin", false) == true) {
                isGeminiWebEditorHydrated = false
                return false
            }
            if (check?.optBoolean("hasEditor", false) == true) {
                isGeminiWebEditorHydrated = true
                return true
            }
        }
        val finalCheck = evaluateJsJson(checkCleanStateJs)
        val ready = finalCheck?.optBoolean("hasEditor", false) == true && finalCheck.optBoolean("needsLogin", false) != true
        isGeminiWebEditorHydrated = ready
        return ready
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
     * End-to-end Race-Free Gemini WebView Solver Pipeline:
     * 1. Keeps the warm, hydrated `<rich-textarea>` session alive in RAM across consecutive solves
     *    (0ms page reload overhead for turns 1..12) and stamps all prior `<model-response>` DOM nodes
     *    with `data-prism-consumed="1"` before injecting `[Q#<solveId>]` so a previous question's
     *    answer can NEVER bleed into the next solve.
     * 2. Temporarily enables keyboard focus on the parked 340dp x 440dp WebView while keeping
     *    `FLAG_NOT_TOUCHABLE` so Angular's `<rich-textarea>` `FormControl` registers the prompt
     *    and activates the Send button 100% reliably, then immediately releases focus.
     * 3. Extracts ONLY the winning option letter (`A`-`F`, `True`/`False`, or `1:B, 2:D`) as soon as
     *    the new turn outputs `FINAL_ANSWER: <option>`.
     */
    private suspend fun solveViaGeminiWebSession(
        promptText: String,
        solveId: Long,
        questionPreview: String,
        isPrimaryMethod: Boolean = true,
        suppressFailureUi: Boolean = false,
        base64Jpeg: String? = null,
        consensusMode: Boolean = false,
        onConsensusWebResolved: ((AnswerModel) -> Unit)? = null
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

            if (wasGenerating) {
                val stopGenerationJs = """
                    (function() {
                        try {
                            let stopBtns = Array.from(document.querySelectorAll('button[aria-label*="Stop" i], [data-test-id="stop-button"]'));
                            stopBtns.forEach(b => { try { b.click(); } catch(_) {} });
                            return JSON.stringify({ stopped: true });
                        } catch (e) { return JSON.stringify({ stopped: false }); }
                    })();
                """.trimIndent()
                evaluateJsJson(stopGenerationJs)
                delay(90)
                preState = evaluateJsJson(inspectStateJs)
            }

            // Classic 3-5s Warm Session Reuse (from nomaan5541/ocr-mcq-solver 1-2 months old commit):
            // NEVER reload https://gemini.google.com/app on every question when the editor is already warm!
            // Only reset if the input editor is missing or after 16+ turns to keep RAM light.
            if (!hasEditorInitially || preTurns >= 16) {
                btnWindowScan?.text = "Ready..."
                Log.d(TAG, "Resetting Gemini WebView to fresh chat (preTurns=$preTurns, hasEditor=$hasEditorInitially)")
                lastSyncedDomTurnSignature = ""
                forceResetGeminiWebChat()
                delay(80)
                preState = evaluateJsJson(inspectStateJs)
            }

            if (!isActive || activeSolveId != solveId) return@withContext false

            if (preState?.optBoolean("needsLogin", false) == true || preState?.optBoolean("hasEditor", false) != true) {
                isGeminiWebEditorHydrated = false
                if (isPrimaryMethod && !suppressFailureUi) {
                    isScanning = false
                    btnWindowScan?.text = "Solve"
                    if (preState?.optBoolean("needsLogin", false) == true && !isWindowVisible && geminiWindowView != null) {
                        showGeminiWindow(true)
                    }
                    Toast.makeText(
                        this@OverlayService,
                        "Please sign in to Gemini in the floating window (or switch to a smart background solver).",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@withContext false
            }
            isGeminiWebEditorHydrated = true

            // Stamp all prior model turns with data-prism-old="1" in <1ms so only the new turn is read
            val baselineTurnsJs = """
                (function() {
                    let byTag = Array.from(document.querySelectorAll('model-response'));
                    let m = byTag.length > 0
                        ? byTag
                        : Array.from(document.querySelectorAll('[data-message-author-role="model"], [data-test-id="model-turn"]'))
                              .filter((el, _, arr) => !arr.some(other => other !== el && other.contains(el)));
                    m.forEach(el => { try { el.setAttribute('data-prism-old', '1'); } catch(_) {} });
                    let u = Array.from(document.querySelectorAll('user-query, [data-message-author-role="user"]'));
                    return JSON.stringify({ modelTurns: m.length, userTurns: u.length });
                })();
            """.trimIndent()
            val baselineObj = evaluateJsJson(baselineTurnsJs)
            val baselineModelTurns = baselineObj?.optInt("modelTurns", 0) ?: 0
            val baselineUserTurns = baselineObj?.optInt("userTurns", 0) ?: 0

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
                                    input.innerHTML = '';
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
                            try {
                                btn.removeAttribute('disabled');
                                btn.setAttribute('aria-disabled', 'false');
                                btn.click();
                            } catch(_) {
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
                        let input = document.querySelector('rich-textarea div[contenteditable="true"], div.ql-editor, div[contenteditable="true"], textarea');
                        if (!input) return JSON.stringify({ sent: false });
                        input.focus();
                        let form = input.closest('form');
                        if (form) {
                            try { form.requestSubmit(); return JSON.stringify({ sent: true, method: "requestSubmit" }); } catch(_) {}
                        }
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
                if (!isWindowVisible) {
                    setParkedWebViewKeyboardFocus(true)
                    delay(20)
                } else {
                    setWindowFocusable(true)
                    geminiWebView?.requestFocus()
                    delay(15)
                }

                val insertRes = evaluateJsJson(insertPromptJs)
                insertStatus = insertRes?.optString("status", "") ?: ""
                Log.d(TAG, "Gemini prompt insert status: $insertRes")

                if (insertStatus != "INPUT_NOT_FOUND" && insertStatus != "EMPTY_AFTER_INSERT") {
                    for (sendAttempt in 1..6) {
                        delay(30)
                        sendRes = evaluateJsJson(clickStrictSendOnceJs)
                        if (sendRes?.optBoolean("sent", false) == true) break
                    }
                    if (sendRes?.optBoolean("sent", false) != true) {
                        sendRes = evaluateJsJson(pressEnterFallbackJs)
                    }
                    delay(20)
                }
            } finally {
                if (!isWindowVisible) {
                    setParkedWebViewKeyboardFocus(false)
                    bringFrontPillToTop()
                }
            }

            if (insertStatus == "INPUT_NOT_FOUND" || insertStatus == "EMPTY_AFTER_INSERT") {
                return@withContext false
            }

            Log.d(TAG, "Gemini initial send result: $sendRes")
            btnWindowScan?.text = "Solving..."

            suspend fun stopActiveGeminiWebGeneration() {
                val stopJs = """
                    (function() {
                        try {
                            let btns = Array.from(document.querySelectorAll(
                                'button[aria-label*="Stop" i], [data-test-id="stop-button"]'
                            ));
                            let stopped = 0;
                            btns.forEach(btn => {
                                let st = window.getComputedStyle(btn);
                                if (btn.offsetParent !== null && st.visibility !== 'hidden' && st.display !== 'none') {
                                    try { btn.click(); stopped++; } catch(_) {}
                                }
                            });
                            return JSON.stringify({ stopped: stopped });
                        } catch (_) {
                            return JSON.stringify({ stopped: 0 });
                        }
                    })();
                """.trimIndent()
                runCatching { evaluateJsJson(stopJs) }
            }

            var lastCleanText = ""
            var stableTicks = 0
            val baseFastTicks = 95 // ~6.2s initial fast window
            val extendedThinkingTicks = 280 // up to ~18.2s while Gemini Web is actively thinking/streaming
            var maxAllowedTicks = baseFastTicks
            var pendingAnswerModel: AnswerModel? = null
            var pendingAnswerFirstTick = -1
            var pendingTurnCount = 0
            var pendingCleanText = ""
            var deliveredHere = false

            val inspectTurnJs = """
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
                            : Array.from(document.querySelectorAll('[data-message-author-role="model"], [data-test-id="model-turn"]'))
                                  .filter((el, _, arr) => !arr.some(other => other !== el && other.contains(el)));
                        if (turns.length === 0) {
                            turns = Array.from(document.querySelectorAll('message-content')).filter(el => !el.closest('user-query'));
                        }
                        let freshTurns = turns.filter(el => el.getAttribute('data-prism-old') !== '1');
                        let userTurns = document.querySelectorAll('user-query, [data-message-author-role="user"]').length;

                        let stopCandidates = Array.from(document.querySelectorAll(
                            'button[aria-label*="Stop" i], [data-test-id="stop-button"]'
                        ));
                        let stopVisible = stopCandidates.some(isTrulyVisible);

                        let editor = document.querySelector('rich-textarea div[contenteditable="true"], div.ql-editor, textarea');
                        let editorRemainingLen = editor ? (editor.innerText || editor.textContent || editor.value || '').trim().length : 0;

                        if (freshTurns.length === 0 && turns.length <= $baselineModelTurns) {
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

                        let latestTurn = freshTurns.length > 0 ? freshTurns[freshTurns.length - 1] : turns[turns.length - 1];
                        let rawTurnText = (latestTurn.innerText || latestTurn.textContent || '').trim();
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

            fun deliverParsedNow(modelToDeliver: AnswerModel, turnCount: Int, cleanText: String, tickNum: Int): Boolean {
                lastSyncedDomTurnSignature = "$turnCount:${cleanText.hashCode()}"
                Log.d(TAG, "Instant answer resolved from stream: ${modelToDeliver.correctChoice} on tick $tickNum")
                if (consensusMode) {
                    onConsensusWebResolved?.invoke(modelToDeliver.copy(engineLabel = "Gemini Web AI"))
                } else {
                    deliverResolvedAnswer(
                        rawLetter = modelToDeliver.correctChoice,
                        solveId = solveId,
                        questionPreview = questionPreview,
                        answerModel = modelToDeliver.copy(engineLabel = "Gemini AI")
                    )
                }
                deliveredHere = true
                return true
            }

            try {
                var tick = 0
                while (tick < maxAllowedTicks) {
                    tick++
                    delay(65)
                    if (!isActive || deliveredSolveId == solveId || activeSolveId != solveId) {
                        deliveredHere = (deliveredSolveId == solveId)
                        return@withContext deliveredHere
                    }

                    val pollJson = evaluateJsJson(inspectTurnJs) ?: continue
                    val hasNewTurn = pollJson.optBoolean("hasNewTurn", false)
                    val cleanText = pollJson.optString("cleanText", "").trim()
                    val isGenerating = pollJson.optBoolean("isGenerating", false)
                    val isThinking = pollJson.optBoolean("isThinking", false)
                    val turnCount = pollJson.optInt("turnCount", 0)
                    val userTurnCount = pollJson.optInt("userTurnCount", 0)
                    val editorLen = pollJson.optInt("editorLen", 0)

                    // Dynamically extend polling while Gemini Web is actively thinking or generating
                    if (isThinking || isGenerating || userTurnCount > baselineUserTurns) {
                        maxAllowedTicks = extendedThinkingTicks
                    }

                    if (!hasNewTurn && !isGenerating && userTurnCount <= baselineUserTurns && editorLen > 0) {
                        if (tick in listOf(2, 4, 7, 10, 14, 18)) {
                            try {
                                if (!isWindowVisible) setParkedWebViewKeyboardFocus(true)
                                delay(25)
                                val retrySend = evaluateJsJson(clickStrictSendOnceJs)
                                if (retrySend?.optBoolean("sent", false) != true) {
                                    evaluateJsJson(pressEnterFallbackJs)
                                }
                            } finally {
                                if (!isWindowVisible) {
                                    setParkedWebViewKeyboardFocus(false)
                                    bringFrontPillToTop()
                                }
                            }
                        } else if (tick >= 28) {
                            Log.w(TAG, "Prompt remained unsent in editor after 28 ticks — breaking to failover")
                            break
                        }
                    }

                    if (isThinking && cleanText.isEmpty()) {
                        btnWindowScan?.text = "Thinking..."
                        if (!isCamouflageEnabled && !isAdvEnabled(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE)) {
                            tvIslandTitle?.text = "Gemini Thinking..."
                        }
                        continue
                    }

                    if (hasNewTurn && cleanText.isNotEmpty()) {
                        if (cleanText == lastCleanText) {
                            stableTicks++
                        } else {
                            lastCleanText = cleanText
                            stableTicks = 0
                        }

                        val parsedModel = try {
                            geminiVisionClient.extractAnswerLetter(cleanText)
                        } catch (_: Exception) {
                            null
                        }

                        if (parsedModel != null && isValidAnswerBadge(parsedModel.correctChoice)) {
                            if (parsedModel.explanation.isNotBlank() || !isGenerating) {
                                return@withContext deliverParsedNow(parsedModel, turnCount, cleanText, tick)
                            }
                            if (pendingAnswerModel == null) {
                                pendingAnswerModel = parsedModel
                                pendingAnswerFirstTick = tick
                                pendingTurnCount = turnCount
                                pendingCleanText = cleanText
                            } else {
                                pendingAnswerModel = parsedModel
                                pendingTurnCount = turnCount
                                pendingCleanText = cleanText
                            }
                            // Wait up to 5 ticks (~325ms) for the short 1-line "| reason" to finish streaming
                            if (tick - pendingAnswerFirstTick >= 5) {
                                return@withContext deliverParsedNow(parsedModel, pendingTurnCount, pendingCleanText, tick)
                            }
                        }
                    }
                }

                val pending = pendingAnswerModel
                if (pending != null && isValidAnswerBadge(pending.correctChoice)) {
                    return@withContext deliverParsedNow(pending, pendingTurnCount, pendingCleanText, maxAllowedTicks)
                }

                return@withContext (!consensusMode && deliveredSolveId == solveId).also {
                    deliveredHere = it
                }
            } finally {
                // Crucial: If another engine won or Gemini Web timed out, immediately press Stop in background WebView
                // so Gemini Web does not keep generating an orphaned response behind "RETRY".
                if (!deliveredHere && !consensusMode) {
                    withContext(NonCancellable + Dispatchers.Main) {
                        stopActiveGeminiWebGeneration()
                    }
                }
            }
    }

    private fun updateIslandAnswerPillBackground(fillHex: String, strokeHex: String) {
        val pill = tvAnswerDisplay ?: return
        if (hudStyle != MainActivity.HUD_STYLE_ISLAND && hudStyle != MainActivity.HUD_STYLE_TOP_STRIP) return
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(14).toFloat()
            setColor(Color.parseColor(fillHex))
            setStroke(dpToPx(1), Color.parseColor(strokeHex))
        }
        pill.background = bg
    }

    private fun setAnswerUI(
        text: String,
        colorHex: String,
        loading: Boolean = false,
        triggerFeedback: Boolean = true,
        preserveIslandTitle: Boolean = false,
        keepScanningFlag: Boolean = false
    ) {
        mainHandler.post {
            if (isInlineToolsVisible) {
                setInlineIslandToolsVisible(false)
            }
            val normalized = text.trim()
            val netSpeedDisguise = isAdvEnabled(MainActivity.PREF_KEY_ADV_NET_SPEED_DISGUISE)
            val splitPill = isAdvEnabled(MainActivity.PREF_KEY_ADV_SPLIT_PILL)

            tvIslandTitle?.visibility = if (splitPill) View.GONE else View.VISIBLE

            if (netSpeedDisguise) {
                ivIslandIcon?.setImageResource(R.drawable.ic_prism_orb)
                llIslandExplanationDrawer?.visibility = View.GONE
                if (!preserveIslandTitle) {
                    tvIslandTitle?.text = "Net Speed"
                }
                if (loading) {
                    val idleSpeed = "0.48 MB/s"
                    tvAnswerDisplay?.text = idleSpeed
                    tvAnswerDisplay?.textSize = 11.5f
                    tvAnswerDisplay?.setTextColor(Color.parseColor("#D71921"))
                    updateIslandAnswerPillBackground("#2A1014", "#66D71921")
                    tvWindowAnswerBadge?.text = idleSpeed
                    tvWindowAnswerBadge?.setTextColor(Color.parseColor("#D71921"))
                } else {
                    val (speedText, isResolved) = formatNetworkSpeedDisguise(normalized)
                    tvAnswerDisplay?.text = speedText
                    tvAnswerDisplay?.textSize = 11.5f
                    val badgeColor = Color.parseColor(if (isResolved) "#34D399" else "#EEEEEE")
                    tvAnswerDisplay?.setTextColor(badgeColor)
                    if (isResolved) {
                        updateIslandAnswerPillBackground("#0E2920", "#5534D399")
                    } else {
                        updateIslandAnswerPillBackground("#16181F", "#2EFFFFFF")
                    }
                    tvWindowAnswerBadge?.text = speedText
                    tvWindowAnswerBadge?.setTextColor(badgeColor)
                }
            } else if (isCamouflageEnabled) {
                ivIslandIcon?.setImageResource(R.drawable.ic_battery_camouflage)
                llIslandExplanationDrawer?.visibility = View.GONE
                val batteryNow = getDeviceBatteryPercentage()
                if (!preserveIslandTitle) {
                    tvIslandTitle?.text = "Charging"
                }
                if (loading) {
                    val loadingCamo = "${batteryNow}%"
                    tvAnswerDisplay?.text = loadingCamo
                    tvAnswerDisplay?.textSize = 13f
                    tvAnswerDisplay?.setTextColor(Color.parseColor("#D71921"))
                    updateIslandAnswerPillBackground("#2A1014", "#66D71921")
                    tvWindowAnswerBadge?.text = loadingCamo
                    tvWindowAnswerBadge?.setTextColor(Color.parseColor("#D71921"))
                } else {
                    val (camoText, isResolved) = formatChargingCamouflage(normalized, batteryNow)
                    tvAnswerDisplay?.text = camoText
                    tvAnswerDisplay?.textSize = if (camoText.length > 4) 13f else 15f
                    val badgeColor = Color.parseColor(if (isResolved) "#34D399" else "#EEEEEE")
                    tvAnswerDisplay?.setTextColor(badgeColor)
                    if (isResolved) {
                        updateIslandAnswerPillBackground("#0E2920", "#5534D399")
                    } else {
                        updateIslandAnswerPillBackground("#16181F", "#2EFFFFFF")
                    }
                    tvWindowAnswerBadge?.text = camoText
                    tvWindowAnswerBadge?.setTextColor(badgeColor)
                }
            } else {
                ivIslandIcon?.setImageResource(R.drawable.ic_prism_orb)
                if (loading) {
                    if (!preserveIslandTitle) {
                        tvIslandTitle?.text = "Reading..."
                    }
                    tvAnswerDisplay?.text = normalized
                    tvAnswerDisplay?.textSize = if (normalized.length > 2) 12f else 15f
                    tvAnswerDisplay?.setTextColor(Color.parseColor("#D71921"))
                    updateIslandAnswerPillBackground("#2A1014", "#66D71921")
                    tvWindowAnswerBadge?.text = normalized
                    tvWindowAnswerBadge?.setTextColor(Color.parseColor("#D71921"))
                } else {
                    if (isValidAnswerBadge(normalized)) {
                        val displayVal = if (normalized.length == 1) normalized.uppercase() else normalized
                        if (!preserveIslandTitle) {
                            tvIslandTitle?.text = "Solved"
                        }
                        tvAnswerDisplay?.text = displayVal
                        tvAnswerDisplay?.textSize = if (displayVal.length > 2) 12.5f else 15f
                        tvAnswerDisplay?.setTextColor(Color.parseColor("#34D399"))
                        updateIslandAnswerPillBackground("#0E2920", "#5534D399")
                        tvWindowAnswerBadge?.text = displayVal
                        tvWindowAnswerBadge?.setTextColor(Color.parseColor("#34D399"))
                    } else {
                        val isErrorState = normalized.uppercase() in setOf("RETRY", "ERR", "CAP", "TXT", "KEY")
                        if (!preserveIslandTitle) {
                            tvIslandTitle?.text = when (normalized.uppercase()) {
                                "RETRY", "ERR", "CAP", "TXT" -> "Retry Scan"
                                "KEY" -> "Key Needed"
                                else -> "Ready"
                            }
                        }
                        tvAnswerDisplay?.text = normalized
                        tvAnswerDisplay?.textSize = if (normalized.length > 2) 11f else 15f
                        val safeHex = if (colorHex.equals("#0F172A", ignoreCase = true) || colorHex.equals("#020617", ignoreCase = true)) {
                            "#EEEEEE"
                        } else {
                            colorHex
                        }
                        tvAnswerDisplay?.setTextColor(Color.parseColor(safeHex))
                        if (isErrorState) {
                            updateIslandAnswerPillBackground("#2A1014", "#66F43F5E")
                        } else {
                            updateIslandAnswerPillBackground("#16181F", "#2EFFFFFF")
                        }
                        tvWindowAnswerBadge?.text = normalized
                        tvWindowAnswerBadge?.setTextColor(Color.parseColor(safeHex))
                    }
                }
            }

            if (loading) {
                startScanBreathingAnim()
            } else if (keepScanningFlag) {
                // Keep scanning active during intermediate All-in-One consensus ticks without thrashing animations
                if (triggerFeedback && isValidAnswerBadge(normalized)) {
                    animateAnswerPop(tvAnswerDisplay)
                    triggerHapticFeedback(normalized)
                }
            } else {
                stopScanBreathingAnim()
                isScanning = false
                if (triggerFeedback && isValidAnswerBadge(normalized)) {
                    animateAnswerPop(tvAnswerDisplay)
                    animateAnswerPop(tvWindowAnswerBadge)
                    triggerHapticFeedback(normalized)
                    if (isStealthHiddenAll) {
                        val wordHint = latestAnswerModel?.shortAnswer?.trim().orEmpty()
                        lastSilentNotificationAnswer = if (wordHint.isNotBlank() && !wordHint.equals(normalized, ignoreCase = true)) {
                            "Answer: $normalized · $wordHint"
                        } else {
                            "Answer: $normalized"
                        }
                        refreshOngoingNotification()
                    }
                }
                wakeAndScheduleIdleFade()
            }

            // Only push WindowManager.updateViewLayout when layout sizing could actually change
            val newLayoutKey = "${tvAnswerDisplay?.text?.length ?: 0}|${tvIslandTitle?.text?.length ?: 0}|${llIslandExplanationDrawer?.visibility}"
            if (newLayoutKey != lastBubbleLayoutSignature) {
                lastBubbleLayoutSignature = newLayoutKey
                try { bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) } } catch (_: Exception) {}
            }
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
                        val isIslandPill = (hudStyle == MainActivity.HUD_STYLE_ISLAND || hudStyle == MainActivity.HUD_STYLE_TOP_STRIP)
                        val safeMinY = if (isIslandPill) dpToPx(2) else getStatusBarHeight()
                        val screenHeight = resources.displayMetrics.heightPixels
                        val maxSafeY = (screenHeight - dpToPx(60)).coerceAtLeast(safeMinY + 10)
                        params.y = (startY + totalDy).coerceIn(safeMinY, maxSafeY)
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
                            Toast.makeText(this@OverlayService, "Floating Pill Closed", Toast.LENGTH_SHORT).show()
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

    // ─── iOS Dynamic Island Morphing & Answer Spring Pop Animations ──────────

    private var scanBreathingAnimator: ObjectAnimator? = null
    private var islandLiveDotAnimator: ObjectAnimator? = null

    private fun startScanBreathingAnim() {
        // Trigger zero-allocation 360° circular border glow roaming around the Dynamic Island capsule perimeter
        islandBorderDrawable?.setScanning(true)

        val target = ivIslandIcon ?: ivBubbleIcon ?: return
        if (scanBreathingAnimator?.isRunning == true) return

        // Subtle pulse on the Dynamic Island live indicator dot during OCR + AI processing
        viewIslandLiveDot?.let { dot ->
            islandLiveDotAnimator?.cancel()
            val dotScaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.35f, 0.92f, 1.0f)
            val dotScaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.35f, 0.92f, 1.0f)
            val dotAlpha = PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f, 0.35f, 1.0f)
            islandLiveDotAnimator = ObjectAnimator.ofPropertyValuesHolder(dot, dotScaleX, dotScaleY, dotAlpha).apply {
                duration = 680L
                repeatCount = 6
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }

        // Smooth iOS 18 Dynamic Island horizontal spring morph when Live Activity scanning begins
        bubbleView?.animate()
            ?.scaleX(1.045f)
            ?.scaleY(1.025f)
            ?.setDuration(240)
            ?.setInterpolator(OvershootInterpolator(1.6f))
            ?.start()

        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.18f, 0.96f, 1.0f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.18f, 0.96f, 1.0f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f, 0.65f, 1.0f)

        scanBreathingAnimator = ObjectAnimator.ofPropertyValuesHolder(target, scaleX, scaleY, alpha).apply {
            duration = 720
            repeatCount = 6
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopScanBreathingAnim() {
        // Stop 360° circular border glow and return to sleek idle rim
        islandBorderDrawable?.setScanning(false)

        scanBreathingAnimator?.cancel()
        scanBreathingAnimator = null
        islandLiveDotAnimator?.cancel()
        islandLiveDotAnimator = null

        viewIslandLiveDot?.let {
            it.scaleX = 1.0f
            it.scaleY = 1.0f
            it.alpha = 1.0f
        }
        (ivIslandIcon ?: ivBubbleIcon)?.let {
            it.scaleX = 1.0f
            it.scaleY = 1.0f
            it.alpha = 1.0f
        }
        val (baseScaleX, baseScaleY) = computeBasePillScale()
        bubbleView?.animate()
            ?.scaleX(baseScaleX)
            ?.scaleY(baseScaleY)
            ?.setDuration(280)
            ?.setInterpolator(OvershootInterpolator(1.8f))
            ?.start()
    }

    private fun computeBasePillScale(): Pair<Float, Float> {
        val userScale = (islandScalePct.coerceIn(75, 125) / 100f)
        val isTopStrip = (hudStyle == MainActivity.HUD_STYLE_TOP_STRIP)
        val isEdgeTab = (hudStyle == MainActivity.HUD_STYLE_EDGE_TAB)
        val styleX = if (isTopStrip || isEdgeTab) 0.86f else 1.0f
        val styleY = if (isTopStrip) 0.82f else if (isEdgeTab) 0.86f else 1.0f
        return (styleX * userScale) to (styleY * userScale)
    }

    private fun animateAnswerPop(view: View?) {
        if (view == null) return
        // iOS Dynamic Island elastic pop-out reveal
        bubbleView?.animate()
            ?.scaleX(1.08f)
            ?.scaleY(1.04f)
            ?.setDuration(140)
            ?.withEndAction {
                val (baseScaleX, baseScaleY) = computeBasePillScale()
                bubbleView?.animate()
                    ?.scaleX(baseScaleX)
                    ?.scaleY(baseScaleY)
                    ?.setDuration(380)
                    ?.setInterpolator(OvershootInterpolator(3.0f))
                    ?.start()
            }
            ?.start()

        view.scaleX = 0.30f
        view.scaleY = 0.30f
        view.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(460)
            .setInterpolator(OvershootInterpolator(3.1f))
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
                CHANNEL_ID, "Floating Solver Pill", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Prism Solver floating answer pill" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun refreshOngoingNotification() {
        if (!isForegroundStarted) return
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentTitle = when {
            isStealthHiddenAll && !lastSilentNotificationAnswer.isNullOrBlank() -> lastSilentNotificationAnswer!!
            isStealthHiddenAll -> "Prism Hidden (Stealth Ready)"
            else -> "Prism Solver Active"
        }
        val contentText = when {
            isStealthHiddenAll -> "Pill is hidden. Double-press Volume Down or tap Solve Now."
            isCamouflageEnabled -> "Charging disguise ready. Tap the pill to solve."
            else -> "Ready on screen. Tap pill or Hide All anytime."
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_prism_orb)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        val solvePi = PendingIntent.getService(
            this,
            101,
            Intent(this, OverlayService::class.java).apply { action = ACTION_NOTIF_SOLVE_NOW },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hideAllPi = PendingIntent.getService(
            this,
            104,
            Intent(this, OverlayService::class.java).apply { action = ACTION_NOTIF_TOGGLE_HIDE_ALL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this,
            103,
            Intent(this, OverlayService::class.java).apply { action = ACTION_NOTIF_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "Solve Now", solvePi)
        builder.addAction(0, if (isStealthHiddenAll) "Show Pill" else "Hide All", hideAllPi)
        builder.addAction(0, "Stop", stopPi)

        return builder.build()
    }
}
