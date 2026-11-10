package com.omnisolve.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.omnisolve.overlay.ads.AdManager
import com.omnisolve.overlay.ads.InAppAdMobBanner
import com.omnisolve.overlay.data.GoogleAuthManager
import com.omnisolve.overlay.data.GoogleSignInOutcome
import com.omnisolve.overlay.firebase.configureAppCheck
import com.omnisolve.overlay.service.OverlayService
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.MainViewModel
import com.omnisolve.overlay.ui.components.AppTab
import com.omnisolve.overlay.ui.components.CompactSplitScreenSideRail
import com.omnisolve.overlay.ui.components.LiquidMorphBottomDock
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.LiquidAtmosphericBackdrop
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.LocalSpringPhysics
import com.omnisolve.overlay.ui.physics.SpringPhysicsConfig
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.screens.HomeScreen
import com.omnisolve.overlay.ui.screens.SettingsScreen
import com.omnisolve.overlay.ui.screens.StatsScreen
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.WhiteGlassTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREF_KEY_DISPLAY_MODE = "PREF_DISPLAY_MODE"
        const val PREF_KEY_AUTO_CLICK = "PREF_AUTO_CLICK"
        const val PREF_KEY_AFK_MODE = "PREF_AFK_MODE"
        const val PREF_KEY_SCAN_INTERVAL_SEC = "PREF_SCAN_INTERVAL_SEC"
        const val PREF_KEY_NEXT_DELAY_MS = "PREF_NEXT_DELAY_MS"
        const val PREF_KEY_INSTANT_TRIGGER = "PREF_INSTANT_TRIGGER"
        const val PREF_KEY_HUD_STYLE = "PREF_HUD_STYLE"
        const val PREF_KEY_CAMOUFLAGE = "PREF_CAMOUFLAGE"
        const val PREF_KEY_HAPTIC = "PREF_HAPTIC"
        const val PREF_KEY_ANTI_CHEAT = "PREF_ANTI_CHEAT"
        const val PREF_KEY_EXTRACTION_ENGINE = "PREF_EXTRACTION_ENGINE"
        const val ENGINE_NATIVE = "NATIVE"
        const val ENGINE_OCR = "OCR"
        const val MODE_STEALTH = "STEALTH"
        const val MODE_WINDOW = "WINDOW"
        const val HUD_STYLE_ISLAND = "DYNAMIC_ISLAND"
        const val HUD_STYLE_BUBBLE = "BUBBLE"
        const val HUD_STYLE_TOP_STRIP = "TOP_STRIP"
        const val HUD_STYLE_EDGE_TAB = "EDGE_TAB"
        const val PREF_KEY_THEME_MODE = "PREF_THEME_MODE"
        const val THEME_DARK = "DARK"
        const val THEME_LIGHT = "LIGHT"
        const val PREF_KEY_AUTO_COPY_CLIPBOARD = "PREF_AUTO_COPY_CLIPBOARD"
        const val PREF_KEY_NATIVE_FALLBACK_OCR = "PREF_KEY_NATIVE_FALLBACK_OCR"
        const val PREF_KEY_COMPANION_SYNC_ENABLED = "PREF_COMPANION_SYNC_ENABLED"
        const val PREF_KEY_COMPANION_HOST = "PREF_COMPANION_HOST"
        const val PREF_KEY_COMPANION_PORT = "PREF_COMPANION_PORT"
        const val PREF_KEY_GEMINI_API_KEY = "PREF_KEY_GEMINI_API_KEY"
        const val PREF_KEY_NVIDIA_API_KEY = "PREF_KEY_NVIDIA_API_KEY"
        const val PREF_KEY_OPENROUTER_API_KEY = "PREF_KEY_OPENROUTER_API_KEY"
        const val PREF_KEY_OPENAI_API_KEY = "PREF_KEY_OPENAI_API_KEY"
        const val PREF_KEY_SOLVER_METHOD = "PREF_SOLVER_METHOD"
        const val PREF_KEY_AUTO_FAILOVER = "PREF_AUTO_FAILOVER"
        const val PREF_KEY_GEMINI_MODEL = "PREF_GEMINI_MODEL"
        const val PREF_KEY_SUBJECT_MODE = "PREF_SUBJECT_MODE"
        const val PREF_KEY_DUAL_VERIFY = "PREF_DUAL_VERIFY"
        const val PREF_KEY_TRANSLATE_ENGLISH = "PREF_TRANSLATE_ENGLISH"

        // Advanced Options (All OFF by default)
        const val PREF_KEY_ADV_CROP_TO_SCAN = "PREF_ADV_CROP_TO_SCAN"
        const val PREF_KEY_ADV_ISLAND_GESTURES = "PREF_ADV_ISLAND_GESTURES"
        const val PREF_KEY_ADV_SPLIT_PILL = "PREF_ADV_SPLIT_PILL"
        const val PREF_KEY_ADV_MINIMAL_SHAPES = "PREF_ADV_MINIMAL_SHAPES"
        const val PREF_KEY_ADV_NET_SPEED_DISGUISE = "PREF_ADV_NET_SPEED_DISGUISE"
        const val PREF_KEY_ADV_AUTO_FADE_IDLE = "PREF_ADV_AUTO_FADE_IDLE"
        const val PREF_KEY_ADV_FLASHCARDS_EXPORT = "PREF_ADV_FLASHCARDS_EXPORT"
        const val PREF_KEY_ADV_DARK_OBSIDIAN = "PREF_ADV_DARK_OBSIDIAN"
        const val PREF_KEY_ADV_SPRING_PHYSICS = "PREF_ADV_SPRING_PHYSICS"
        const val PREF_KEY_SPRING_DAMPING = "PREF_SPRING_DAMPING"
        const val PREF_KEY_SPRING_STIFFNESS = "PREF_SPRING_STIFFNESS"
        const val PREF_KEY_ADV_SPLIT_SCREEN_RAIL = "PREF_ADV_SPLIT_SCREEN_RAIL"
        const val PREF_KEY_ADV_QUICK_TILE_NOTIF = "PREF_ADV_QUICK_TILE_NOTIF"

        // Start-Time Custom Opacity (20%..100%), Question Solve Mode & Dynamic Flow Bar Toggles
        const val PREF_KEY_CUSTOM_OPACITY_PCT = "PREF_CUSTOM_OPACITY_PCT"
        const val PREF_KEY_QUESTION_SOLVE_MODE = "PREF_QUESTION_SOLVE_MODE"
        const val PREF_KEY_DF_TWO_PART_CAPTURE = "PREF_DF_TWO_PART_CAPTURE"
        const val PREF_KEY_DF_SCROLL_TIMER = "PREF_DF_SCROLL_TIMER"
        const val PREF_KEY_DF_COMPLEX_MODE = "PREF_DF_COMPLEX_MODE"
        const val PREF_KEY_DF_DIAGRAM_VISION = "PREF_DF_DIAGRAM_VISION"
        const val PREF_KEY_DF_QUICK_CROP = "PREF_DF_QUICK_CROP"
        const val PREF_KEY_DF_SWIPE_METHOD_LOOP = "PREF_DF_SWIPE_METHOD_LOOP"

        // Legal & Privacy Consent Gate (First Launch Mandatory Checkbox)
        const val PREF_KEY_USER_CONSENT_ACCEPTED = "PREF_USER_CONSENT_ACCEPTED"
        const val URL_PRIVACY_POLICY_WEB = "https://virusboss.in/app/prism-ocr-solver/#privacy-policy"
        const val PREF_KEY_ENGINE_PERFORMANCE_MODE = "PREF_KEY_ENGINE_PERFORMANCE_MODE"
    }

    private val viewModel: MainViewModel by viewModels()

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.refreshSystemStatuses()
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startOverlayForegroundService(result.resultCode, result.data!!)
        } else {
            viewModel.showBannerMessage("Screen capture permission was declined")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureAppCheck(this, intent)

        // Initialize Google Mobile Ads SDK for in-app banners/interstitials
        AdManager.initialize(this) {
            AdManager.preloadInterstitial(this)
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )

        handleIncomingDeepLink(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.advDarkObsidianEnabled) {
                if (uiState.advDarkObsidianEnabled) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT
                        )
                    )
                }
            }

            val springCfg = SpringPhysicsConfig(
                dampingRatio = if (uiState.advSpringPhysicsEnabled) uiState.springDamping else 0.72f,
                stiffness = if (uiState.advSpringPhysicsEnabled) uiState.springStiffness else 650f
            )

            val currentDensity = LocalDensity.current
            // Dynamic auto-adjustment: safely clamps font scaling to [0.85f, 1.12f]
            // so developer options DPI changes, display size adjustments, or system font scale
            // NEVER cause text collision, line overlapping, or broken layouts.
            val adaptiveDensity = remember(currentDensity.density, currentDensity.fontScale) {
                Density(
                    density = currentDensity.density,
                    fontScale = currentDensity.fontScale.coerceIn(0.85f, 1.12f)
                )
            }

            WhiteGlassTheme(darkObsidian = uiState.advDarkObsidianEnabled) {
                CompositionLocalProvider(
                    LocalDensity provides adaptiveDensity,
                    LocalReducedMotion provides !uiState.advSpringPhysicsEnabled,
                    LocalSpringPhysics provides springCfg
                ) {
                    LaunchedEffect(Unit) {
                        if (FirebaseApp.getApps(this@MainActivity).isNotEmpty()) {
                            GoogleAuthManager.trySilentRestore(this@MainActivity)
                            viewModel.refreshSystemStatuses()
                        }
                    }

                    BackHandler(enabled = uiState.selectedTab != AppTab.Home) {
                        viewModel.selectTab(AppTab.Home)
                    }

                    MainInstantApp(
                        state = uiState,
                        viewModel = viewModel,
                        onSelectTab = { tab ->
                            triggerHapticTick()
                            viewModel.selectTab(tab)
                        },
                        onToggleOverlay = {
                            triggerHapticTick()
                            if (!uiState.isOverlayRunning) {
                                checkOverlayPermissionAndStart()
                            } else {
                                stopOverlayForegroundService()
                            }
                        },
                        onRequestOverlayPermission = ::openOverlayPermissionScreen,
                        onOpenAccessibilitySettings = ::openAccessibilitySettingsScreen,
                        onSignInGoogle = ::performInteractiveGoogleSignIn,
                        onSignOutGoogle = ::performGoogleSignOut,
                        onOpenGoogleLogin = {
                            loginLauncher.launch(Intent(this, LoginActivity::class.java))
                        },
                        onTestVibration = ::testPocketHapticPulse,
                        onSyncRunningService = ::notifyServiceConfigChanged
                    )

                    // Mandatory First-Launch Legal Agreement Gate
                    if (!uiState.userConsentAccepted) {
                        com.omnisolve.overlay.ui.components.MandatoryLegalConsentDialog(
                            onAcceptAndContinue = {
                                triggerHapticTick()
                                viewModel.acceptUserConsent()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun performInteractiveGoogleSignIn() {
        viewModel.setGoogleSignInLoading(true)
        lifecycleScope.launch {
            try {
                when (val outcome = GoogleAuthManager.signInInteractive(this@MainActivity)) {
                    is GoogleSignInOutcome.Success -> {
                        viewModel.refreshSystemStatuses()
                        viewModel.showBannerMessage("Signed in as ${outcome.user.email ?: outcome.user.displayName ?: "Google user"}")
                    }
                    is GoogleSignInOutcome.Cancelled -> Unit
                    is GoogleSignInOutcome.Error -> {
                        viewModel.showBannerMessage(outcome.userMessage)
                    }
                }
            } finally {
                viewModel.setGoogleSignInLoading(false)
            }
        }
    }

    private fun performGoogleSignOut() {
        lifecycleScope.launch {
            GoogleAuthManager.signOut(this@MainActivity)
            viewModel.refreshSystemStatuses()
            viewModel.showBannerMessage("Signed out of Server")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemStatuses()
    }

    private fun openOverlayPermissionScreen() {
        if (Settings.canDrawOverlays(this)) {
            viewModel.showBannerMessage("Overlay permission is already allowed")
            return
        }
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(
                this,
                "Allow 'Display over other apps' for Prism AI, then return here",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            viewModel.showBannerMessage("Could not open overlay permission settings")
        }
    }

    private fun openAccessibilitySettingsScreen() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "Find 'Prism AI' in Installed Services and turn it on",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            viewModel.showBannerMessage("Could not open Accessibility settings")
        }
    }

    private fun checkOverlayPermissionAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlayPermissionScreen()
            return
        }
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startOverlayForegroundService(resultCode: Int, resultData: Intent) {
        val state = viewModel.uiState.value
        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, resultData)
            putExtra(OverlayService.EXTRA_DISPLAY_MODE, state.displayMode)
            putExtra(OverlayService.EXTRA_AUTO_CLICK, state.autoClickEnabled)
            putExtra(OverlayService.EXTRA_AFK_MODE, state.afkAutoAdvanceEnabled)
            putExtra(OverlayService.EXTRA_SCAN_INTERVAL_SEC, state.scanIntervalSec)
            putExtra(OverlayService.EXTRA_NEXT_DELAY_MS, state.nextDelayMs)
            putExtra(OverlayService.EXTRA_INSTANT_TRIGGER, state.instantTriggerEnabled)
            putExtra(OverlayService.EXTRA_EXTRACTION_ENGINE, state.extractionEngine)
            putExtra(OverlayService.EXTRA_HUD_STYLE, state.hudStyle)
            putExtra(OverlayService.EXTRA_CAMOUFLAGE, state.batteryCamouflage)
            putExtra(OverlayService.EXTRA_HAPTIC, state.hapticEnabled)
            putExtra(OverlayService.EXTRA_ANTI_CHEAT, state.naturalTimingEnabled)
            putExtra(OverlayService.EXTRA_THEME_MODE, if (state.advDarkObsidianEnabled) THEME_DARK else THEME_LIGHT)
            putExtra(OverlayService.EXTRA_ISLAND_OPACITY_INDEX, state.islandOpacityIndex)
            putExtra(OverlayService.EXTRA_CUSTOM_OPACITY_PCT, state.customOpacityPct)
            putExtra(OverlayService.EXTRA_COMPANION_HOST, state.companionHost)
            putExtra(OverlayService.EXTRA_COMPANION_PORT, state.companionPort)
            putExtra(OverlayService.EXTRA_COMPANION_SYNC_ENABLED, state.companionSyncEnabled)
            putExtra(OverlayService.EXTRA_SOLVER_METHOD, state.solverMethod.id)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        viewModel.setOverlayServiceState(running = true, captureAuthorized = true)
        viewModel.showBannerMessage("Floating overlay started")
    }

    private fun stopOverlayForegroundService() {
        stopService(Intent(this, OverlayService::class.java))
        viewModel.setOverlayServiceState(running = false)
        viewModel.showBannerMessage("Floating overlay stopped")
    }

    private fun notifyServiceConfigChanged() {
        val state = viewModel.uiState.value
        if (!state.isOverlayRunning || !OverlayService.isServiceRunning()) return

        val updateIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_CONFIG
            putExtra(OverlayService.EXTRA_DISPLAY_MODE, state.displayMode)
            putExtra(OverlayService.EXTRA_AUTO_CLICK, state.autoClickEnabled)
            putExtra(OverlayService.EXTRA_AFK_MODE, state.afkAutoAdvanceEnabled)
            putExtra(OverlayService.EXTRA_SCAN_INTERVAL_SEC, state.scanIntervalSec)
            putExtra(OverlayService.EXTRA_NEXT_DELAY_MS, state.nextDelayMs)
            putExtra(OverlayService.EXTRA_INSTANT_TRIGGER, state.instantTriggerEnabled)
            putExtra(OverlayService.EXTRA_EXTRACTION_ENGINE, state.extractionEngine)
            putExtra(OverlayService.EXTRA_HUD_STYLE, state.hudStyle)
            putExtra(OverlayService.EXTRA_CAMOUFLAGE, state.batteryCamouflage)
            putExtra(OverlayService.EXTRA_HAPTIC, state.hapticEnabled)
            putExtra(OverlayService.EXTRA_ANTI_CHEAT, state.naturalTimingEnabled)
            putExtra(OverlayService.EXTRA_THEME_MODE, if (state.advDarkObsidianEnabled) THEME_DARK else THEME_LIGHT)
            putExtra(OverlayService.EXTRA_ISLAND_OPACITY_INDEX, state.islandOpacityIndex)
            putExtra(OverlayService.EXTRA_CUSTOM_OPACITY_PCT, state.customOpacityPct)
            putExtra(OverlayService.EXTRA_COMPANION_HOST, state.companionHost)
            putExtra(OverlayService.EXTRA_COMPANION_PORT, state.companionPort)
            putExtra(OverlayService.EXTRA_COMPANION_SYNC_ENABLED, state.companionSyncEnabled)
            putExtra(OverlayService.EXTRA_SOLVER_METHOD, state.solverMethod.id)
        }
        try {
            startService(updateIntent)
        } catch (_: Exception) {
        }
    }

    private fun handleIncomingDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "omnisolve" && data.host == "sync") {
            val host = data.getQueryParameter("host")
            val port = data.getQueryParameter("port")?.toIntOrNull() ?: 59124
            if (!host.isNullOrBlank()) {
                viewModel.applyDeepLinkSync(host, port, ::notifyServiceConfigChanged)
            }
        }
    }

    private fun testPocketHapticPulse() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null) {
                viewModel.showBannerMessage("Vibration hardware not available on this device")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val composition = VibrationEffect.startComposition()
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 0)
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f, 120)
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 240)
                vibrator.vibrate(composition.compose())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 120, 100), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 100, 120, 100), -1)
            }
            viewModel.showBannerMessage("Played 2 buzzes (Option B)")
        } catch (_: Exception) {
        }
    }

    private fun triggerHapticTick() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun MainInstantApp(
    state: MainUiState,
    viewModel: MainViewModel,
    onSelectTab: (AppTab) -> Unit,
    onToggleOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSignInGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onOpenGoogleLogin: () -> Unit,
    onTestVibration: () -> Unit,
    onSyncRunningService: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val homeListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LiquidAtmosphericBackdrop()

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                WhiteGlassTopHeader(
                    isOverlayRunning = state.isOverlayRunning,
                    selectedTab = state.selectedTab,
                    onToggleSettings = {
                        onSelectTab(
                            if (state.selectedTab == AppTab.Settings) AppTab.Home else AppTab.Settings
                        )
                    },
                    onShowNotifications = {
                        viewModel.showBannerMessage("Prism OCR Solver v3.5.2 is active & ready")
                    }
                )
            },
            bottomBar = {
                if (!state.advSplitScreenRailEnabled) {
                    LiquidMorphBottomDock(
                        selectedTab = state.selectedTab,
                        onSelectTab = onSelectTab
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                if (state.advSplitScreenRailEnabled) {
                    CompactSplitScreenSideRail(
                        selectedTab = state.selectedTab,
                        onSelectTab = onSelectTab
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentMod = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)

                    // Zero-delay direct screen swap for instant Frame-1 load & tab switching
                    when (state.selectedTab) {
                        AppTab.Home -> HomeScreen(
                            state = state,
                            listState = homeListState,
                            onToggleOverlay = onToggleOverlay,
                            onRequestOverlayPermission = onRequestOverlayPermission,
                            onSelectSolverMethod = { method ->
                                viewModel.selectSolverMethod(method, onSyncRunningService)
                            },
                            onSetCustomOpacityPct = { pct ->
                                viewModel.setCustomOpacityPct(pct, onSyncRunningService)
                            },
                            onSelectQuestionSolveMode = { mode ->
                                viewModel.setQuestionSolveMode(mode, onSyncRunningService)
                            },
                            onToggleChargingFormat = { enabled ->
                                viewModel.setBatteryCamouflage(enabled, onSyncRunningService)
                            },
                            onOpenSettings = { onSelectTab(AppTab.Settings) },
                            onSignInServer = onSignInGoogle,
                            onOpenGeminiLogin = onOpenGoogleLogin,
                            onToggleTheme = {
                                viewModel.setAdvDarkObsidian(!state.advDarkObsidianEnabled, onSyncRunningService)
                            },
                            modifier = contentMod
                        )

                        AppTab.Stats -> StatsScreen(
                            state = state,
                            onOpenSettings = { onSelectTab(AppTab.Settings) },
                            onSelectEngineMode = { mode ->
                                viewModel.setEnginePerformanceMode(mode, onSyncRunningService)
                            },
                            modifier = contentMod
                        )

                        AppTab.Settings -> SettingsScreen(
                            state = state,
                            sample = viewModel.samples[state.currentSampleIndex],
                            listState = settingsListState,
                            onSaveApiKeys = { nvidia, openRouter, gemini, openAi ->
                                viewModel.saveMultiProviderApiKeys(
                                    nvidiaKey = nvidia,
                                    openRouterKey = openRouter,
                                    geminiKey = gemini,
                                    openAiKey = openAi,
                                    onSyncService = onSyncRunningService
                                )
                            },
                            onTestApiKeys = { nvidia, openRouter, gemini, openAi ->
                                viewModel.testSavedApiKeys(
                                    nvidiaKey = nvidia,
                                    openRouterKey = openRouter,
                                    geminiKey = gemini,
                                    openAiKey = openAi,
                                    onSyncService = onSyncRunningService
                                )
                            },
                            onClearApiKeys = {
                                viewModel.clearLocalApiKeys(onSyncRunningService)
                            },
                            onToggleAutoFailover = { enabled ->
                                viewModel.setAutoFailoverEnabled(enabled, onSyncRunningService)
                            },
                            onToggleChargingCamo = { enabled ->
                                viewModel.setBatteryCamouflage(enabled, onSyncRunningService)
                            },
                            onSignInGoogle = onSignInGoogle,
                            onSignOutGoogle = onSignOutGoogle,
                            onOpenGeminiWebLogin = onOpenGoogleLogin,
                            onRequestOverlayPermission = onRequestOverlayPermission,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                            onSelectHudStyle = { style ->
                                viewModel.setHudStyle(style, onSyncRunningService)
                            },
                            onSetCustomOpacityPct = { pct ->
                                viewModel.setCustomOpacityPct(pct, onSyncRunningService)
                            },
                            onToggleDfTwoPartCapture = { enabled ->
                                viewModel.setDfTwoPartCapture(enabled, onSyncRunningService)
                            },
                            onToggleDfScrollTimer = { enabled ->
                                viewModel.setDfScrollTimer(enabled, onSyncRunningService)
                            },
                            onToggleDfComplexMode = { enabled ->
                                viewModel.setDfComplexMode(enabled, onSyncRunningService)
                            },
                            onToggleDfDiagramVision = { enabled ->
                                viewModel.setDfDiagramVision(enabled, onSyncRunningService)
                            },
                            onToggleDfQuickCrop = { enabled ->
                                viewModel.setDfQuickCrop(enabled, onSyncRunningService)
                            },
                            onToggleDfSwipeMethodLoop = { enabled ->
                                viewModel.setDfSwipeMethodLoop(enabled, onSyncRunningService)
                            },
                            onToggleHaptics = { enabled ->
                                viewModel.setHapticEnabled(enabled, onSyncRunningService)
                            },
                            onTestVibration = onTestVibration,
                            onToggleInstantDetect = { enabled ->
                                viewModel.setInstantTrigger(enabled, onSyncRunningService)
                            },
                            onToggleAutoClick = { enabled ->
                                viewModel.setAutoClick(
                                    enabled = enabled,
                                    onRequireAccessibility = {
                                        viewModel.showBannerMessage("Enable Auto-Tap helper first")
                                        onOpenAccessibilitySettings()
                                    },
                                    onSyncService = onSyncRunningService
                                )
                            },
                            onToggleAutoNext = { enabled ->
                                viewModel.setAfkAutoAdvance(
                                    enabled = enabled,
                                    onRequireAccessibility = {
                                        viewModel.showBannerMessage("Enable Auto-Tap helper first")
                                        onOpenAccessibilitySettings()
                                    },
                                    onSyncService = onSyncRunningService
                                )
                            },
                            onSolvePractice = viewModel::solvePracticeQuestion,
                            onNextPractice = viewModel::nextSampleQuestion,
                            onClearHistory = viewModel::clearHistory,
                            onSelectSolverMethod = { method ->
                                viewModel.selectSolverMethod(method, onSyncRunningService)
                            },
                            onToggleAdvCropToScan = { enabled ->
                                viewModel.setAdvCropToScan(enabled, onSyncRunningService)
                            },
                            onToggleAdvIslandGestures = { enabled ->
                                viewModel.setAdvIslandGestures(enabled, onSyncRunningService)
                            },
                            onToggleAdvSplitPill = { enabled ->
                                viewModel.setAdvSplitPill(enabled, onSyncRunningService)
                            },
                            onToggleAdvMinimalShapes = { enabled ->
                                viewModel.setAdvMinimalShapes(enabled, onSyncRunningService)
                            },
                            onToggleAdvNetSpeedDisguise = { enabled ->
                                viewModel.setAdvNetSpeedDisguise(enabled, onSyncRunningService)
                            },
                            onToggleAdvAutoFadeIdle = { enabled ->
                                viewModel.setAdvAutoFadeIdle(enabled, onSyncRunningService)
                            },
                            onToggleAdvFlashcardsExport = viewModel::setAdvFlashcardsExport,
                            onToggleAdvDarkObsidian = { enabled ->
                                viewModel.setAdvDarkObsidian(enabled, onSyncRunningService)
                            },
                            onToggleAdvSpringPhysics = viewModel::setAdvSpringPhysics,
                            onUpdateSpringPhysics = viewModel::updateSpringPhysics,
                            onToggleAdvSplitScreenRail = viewModel::setAdvSplitScreenRail,
                            onToggleAdvQuickTileNotif = { enabled ->
                                viewModel.setAdvQuickTileNotif(enabled, onSyncRunningService)
                            },
                            onToggleDualVerify = { enabled ->
                                viewModel.setDualVerifyEnabled(enabled, onSyncRunningService)
                            },
                            onFlashcardSearchChange = viewModel::updateFlashcardSearch,
                            onFlashcardSubjectFilterChange = viewModel::updateFlashcardSubjectFilter,
                            onToggleFlashcardBookmarkedOnly = viewModel::setFlashcardBookmarkedOnly,
                            onToggleBookmarkEntry = viewModel::toggleBookmarkHistoryEntry,
                            onShowBannerMessage = viewModel::showBannerMessage,
                            modifier = contentMod
                        )
                    }

                    // Instant status notification capsule
                    if (state.statusBannerMessage != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 24.dp, vertical = 6.dp)
                                .livingGlassSurface(
                                    strength = GlassMaterialStrength.FloatingDock,
                                    cornerRadius = 999.dp
                                )
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = state.statusBannerMessage,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.inkPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WhiteGlassTopHeader(
    isOverlayRunning: Boolean,
    selectedTab: AppTab,
    onToggleSettings: () -> Unit,
    onShowNotifications: () -> Unit = {}
) {
    val palette = LocalWhiteGlassPalette.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .testTag("top_glass_header"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Logo & Title matching HTML
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Diamond Prism Logo
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1D1F25), Color(0xFF15161A), Color(0xFF0D0E11))
                            )
                        )
                        .border(1.2.dp, Color(0xFF2B2D34), RoundedCornerShape(16.dp))
                        .padding(2.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Diamond Shape with Crimson / Scarlet gradient
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(45f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFD71921), Color(0xFF7A0A0F))
                                )
                            )
                            .border(1.5.dp, Color(0xFFFF4D4D), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.85f))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(13.dp))

                Column {
                    Text(
                        text = "Prism OCR Solver",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.inkPrimary,
                            letterSpacing = (-0.4).sp
                        )
                    )
                    Text(
                        text = "Instant Floating Solver",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.inkSecondary
                        )
                    )
                }
            }

            // Right: Action Buttons (Notification & Settings) matching HTML
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Notification Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (palette.isDarkObsidian) Color(0xFF16171B) else Color.White.copy(alpha = 0.9f))
                        .border(
                            1.dp,
                            if (palette.isDarkObsidian) Color(0xFF23252A) else Color(0x99CBD5E1),
                            CircleShape
                        )
                        .clickable(role = Role.Button, onClick = onShowNotifications),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = "Notifications",
                        tint = palette.inkPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Settings Button (preserving testTag("btn_header_account"))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (selectedTab == AppTab.Settings) Color(0xFF2A1315)
                            else if (palette.isDarkObsidian) Color(0xFF16171B)
                            else Color.White.copy(alpha = 0.9f)
                        )
                        .border(
                            1.dp,
                            if (selectedTab == AppTab.Settings) Color(0xFFD71921)
                            else if (palette.isDarkObsidian) Color(0xFF23252A)
                            else Color(0x99CBD5E1),
                            CircleShape
                        )
                        .clickable(role = Role.Button, onClick = onToggleSettings)
                        .testTag("btn_header_account"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = if (selectedTab == AppTab.Settings) Color(0xFFD71921)
                        else palette.inkPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
