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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.omnisolve.overlay.data.GoogleAuthManager
import com.omnisolve.overlay.data.GoogleSignInOutcome
import com.omnisolve.overlay.firebase.configureAppCheck
import com.omnisolve.overlay.service.OverlayService
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.MainViewModel
import com.omnisolve.overlay.ui.components.AppTab
import com.omnisolve.overlay.ui.components.LiquidMorphBottomDock
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.LiquidAtmosphericBackdrop
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.screens.HomeScreen
import com.omnisolve.overlay.ui.screens.SettingsScreen
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
            WhiteGlassTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            putExtra(OverlayService.EXTRA_THEME_MODE, THEME_LIGHT)
            putExtra(OverlayService.EXTRA_ISLAND_OPACITY_INDEX, state.islandOpacityIndex)
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
            putExtra(OverlayService.EXTRA_THEME_MODE, THEME_LIGHT)
            putExtra(OverlayService.EXTRA_ISLAND_OPACITY_INDEX, state.islandOpacityIndex)
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
                    }
                )
            },
            bottomBar = {
                LiquidMorphBottomDock(
                    selectedTab = state.selectedTab,
                    onSelectTab = onSelectTab
                )
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
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
                        onToggleChargingFormat = { enabled ->
                            viewModel.setBatteryCamouflage(enabled, onSyncRunningService)
                        },
                        onOpenSettings = { onSelectTab(AppTab.Settings) },
                        onSignInServer = onSignInGoogle,
                        onOpenGeminiLogin = onOpenGoogleLogin,
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
                        onSelectOpacityIndex = { idx ->
                            viewModel.setIslandOpacityIndex(idx, onSyncRunningService)
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

@Composable
private fun WhiteGlassTopHeader(
    isOverlayRunning: Boolean,
    selectedTab: AppTab,
    onToggleSettings: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .livingGlassSurface(
                    strength = GlassMaterialStrength.FloatingDock,
                    cornerRadius = 20.dp
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("top_glass_header"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0F9FF))
                        .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_prism_orb),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.inkPrimary
                    )
                    Text(
                        text = stringResource(R.string.header_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSecondary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selectedTab == AppTab.Settings) Color(0xFFE0F2FE) else Color(0xFFF1F5F9)
                    )
                    .border(
                        width = 1.dp,
                        color = if (selectedTab == AppTab.Settings) palette.accentSky else Color(0xFFCBD5E1),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable(
                        role = Role.Button,
                        onClick = onToggleSettings
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("btn_header_account"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOverlayRunning) Color(0xFF10B981) else palette.accentSky)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedTab == AppTab.Settings) "Home" else "Settings",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                )
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Open Settings",
                    tint = palette.accentSky,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
