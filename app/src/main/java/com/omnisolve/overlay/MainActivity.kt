package com.omnisolve.overlay

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.omnisolve.overlay.ads.AdManager
import com.omnisolve.overlay.data.AbuseEnforcementState
import com.omnisolve.overlay.data.GoogleAuthManager
import com.omnisolve.overlay.data.GoogleSignInOutcome
import com.omnisolve.overlay.firebase.configureAppCheck
import com.omnisolve.overlay.service.OverlayService
import com.omnisolve.overlay.service.PrismEngagementNotificationManager
import com.omnisolve.overlay.ui.HomeSubScreen
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.MainViewModel
import com.omnisolve.overlay.ui.components.AppTab
import com.omnisolve.overlay.ui.components.CompactSplitScreenSideRail
import com.omnisolve.overlay.ui.components.LiquidMorphBottomDock
import com.omnisolve.overlay.ui.components.NewUserFeatureGuideOverlay
import com.omnisolve.overlay.ui.components.PrismAppEntryAnimationOverlay
import com.omnisolve.overlay.ui.components.QuickPlusTreeOverlay
import com.omnisolve.overlay.ui.components.QuickSolveResultSheet
import com.omnisolve.overlay.ui.components.RedPrismLogoMark
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.LiquidAtmosphericBackdrop
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.LocalSpringPhysics
import com.omnisolve.overlay.ui.physics.SpringPhysicsConfig
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.physics.rememberSystemReducedMotion
import com.omnisolve.overlay.ui.screens.HomeScreen
import com.omnisolve.overlay.ui.screens.SettingsScreen
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.PrismCurves
import com.omnisolve.overlay.ui.theme.ThemeAnimationIntensity
import com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype
import com.omnisolve.overlay.ui.theme.WhiteGlassTheme
import com.omnisolve.overlay.util.AppShareHelper
import com.omnisolve.overlay.util.InAppReviewHelper
import com.omnisolve.overlay.util.InAppUpdateController
import com.omnisolve.overlay.util.PerformanceBenchmarkTracker
import com.omnisolve.overlay.util.PlayUpdateUiState
import com.omnisolve.overlay.util.PrismCrashlytics
import com.omnisolve.overlay.util.ShareIntentRouter
import com.omnisolve.overlay.util.SharedPayload
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREF_KEY_DISPLAY_MODE = "PREF_DISPLAY_MODE"
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
        const val PREF_KEY_GOOGLE_FORMS_MODEL = "PREF_GOOGLE_FORMS_MODEL"
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
        const val PREF_KEY_FLUID_ANIMATIONS_ENABLED = "PREF_FLUID_ANIMATIONS_ENABLED"
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

        // Dynamic Island Studio Extended Customization
        const val PREF_KEY_ISLAND_ACCENT_THEME = "PREF_ISLAND_ACCENT_THEME"
        const val PREF_KEY_ISLAND_CUSTOM_HEX = "PREF_ISLAND_CUSTOM_HEX"
        const val PREF_KEY_ISLAND_LIGHT_MODE = "PREF_ISLAND_LIGHT_MODE"
        const val PREF_KEY_ISLAND_SURFACE_HEX = "PREF_ISLAND_SURFACE_HEX"
        const val PREF_KEY_ISLAND_SIZE_PRESET = "PREF_ISLAND_SIZE_PRESET"
        const val PREF_KEY_ISLAND_EDGE_SNAP = "PREF_ISLAND_EDGE_SNAP"
        const val PREF_KEY_ISLAND_IDLE_FADE_SEC = "PREF_ISLAND_IDLE_FADE_SEC"

        const val ISLAND_SIZE_COMPACT = "COMPACT"
        const val ISLAND_SIZE_STANDARD = "STANDARD"
        const val ISLAND_SIZE_LARGE = "LARGE"
        const val ISLAND_SIZE_XL = "XL"
        const val PREF_KEY_ISLAND_SCALE_PCT = "PREF_ISLAND_SCALE_PCT"
        const val PREF_KEY_ISLAND_ANSWER_SCALE = "PREF_ISLAND_ANSWER_SCALE"
        const val ANSWER_SCALE_NORMAL = "NORMAL"
        const val ANSWER_SCALE_LARGE = "LARGE"
        const val ANSWER_SCALE_XL_BOLD = "XL_BOLD"
        const val PREF_KEY_ISLAND_WIDTH_DP = "PREF_ISLAND_WIDTH_DP"
        const val PREF_KEY_ISLAND_HEIGHT_DP = "PREF_ISLAND_HEIGHT_DP"
        const val PREF_KEY_ISLAND_CORNER_RADIUS_DP = "PREF_ISLAND_CORNER_RADIUS_DP"
        const val PREF_KEY_ISLAND_ANIM_SPEED = "PREF_ISLAND_ANIM_SPEED"
        const val PREF_KEY_ISLAND_CUTOUT_ALIGN = "PREF_ISLAND_CUTOUT_ALIGN"
        const val PREF_KEY_ISLAND_CUTOUT_OFFSET_X_DP = "PREF_ISLAND_CUTOUT_OFFSET_X_DP"
        const val PREF_KEY_ISLAND_CUTOUT_OFFSET_Y_DP = "PREF_ISLAND_CUTOUT_OFFSET_Y_DP"
        const val EXTRA_REQUEST_GOOGLE_SIGN_IN = "EXTRA_REQUEST_GOOGLE_SIGN_IN"
        const val EXTRA_LAUNCH_OVERLAY_FROM_TILE = "EXTRA_LAUNCH_OVERLAY_FROM_TILE"
    }

    private val viewModel: MainViewModel by viewModels()
    private lateinit var inAppUpdateController: InAppUpdateController
    private var islandEntrancePulseTick by mutableIntStateOf(0)
    private var activePrePermissionPrompt by mutableStateOf<PrePermissionPrompt?>(null)
    private var showStartConsentDialog by mutableStateOf(false)
    private var skipEntryAnimationForShortcut by mutableStateOf(false)

    private val inAppUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.showBannerMessage("Update paused. Check Settings anytime.")
        }
    }

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.refreshSystemStatuses()
    }

    private var hasAskedNotificationPermissionThisOpen = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshSystemStatuses()
        if (granted) {
            viewModel.showBannerMessage("Notifications enabled for pinned answers & updates")
        }
    }

    private fun requestNotificationPermissionOnOpenIfNeeded() {
        if (hasAskedNotificationPermissionThisOpen) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            hasAskedNotificationPermissionThisOpen = true
            runCatching {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startOverlayForegroundService(result.resultCode, result.data!!)
            islandEntrancePulseTick += 1
        } else {
            viewModel.showBannerMessage("Screen capture permission was declined")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        PerformanceBenchmarkTracker.markAppProcessStart()
        super.onCreate(savedInstanceState)

        configureAppCheck(this, intent)
        PrismCrashlytics.initialize(this)
        inAppUpdateController = InAppUpdateController(applicationContext)
        inAppUpdateController.checkForUpdates(manualCheck = false)

        // Initialize Google Mobile Ads SDK asynchronously after first frame to prevent launch jank
        lifecycleScope.launch {
            delay(900L)
            AdManager.initialize(applicationContext)
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        handleIncomingDeepLink(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val playUpdateState by inAppUpdateController.updateState.collectAsStateWithLifecycle()
            var showEntryAnimation by rememberSaveable { mutableStateOf(!skipEntryAnimationForShortcut) }
            if (skipEntryAnimationForShortcut && showEntryAnimation) {
                showEntryAnimation = false
            }

            LaunchedEffect(uiState.effectiveThemeSpec.isDark) {
                val barStyle = if (uiState.effectiveThemeSpec.isDark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            // Ask for Notification permission cleanly once the app opens (Android 13+) if not yet allowed
            LaunchedEffect(showEntryAnimation) {
                if (!showEntryAnimation) {
                    delay(350L)
                    requestNotificationPermissionOnOpenIfNeeded()
                }
            }

            val springCfg = SpringPhysicsConfig(
                dampingRatio = if (uiState.advSpringPhysicsEnabled) uiState.springDamping else 0.72f,
                stiffness = if (uiState.advSpringPhysicsEnabled) uiState.springStiffness else 650f
            )

            val currentDensity = LocalDensity.current
            // Resilient layouts adapt cleanly across accessibility Font Size & Display Size scales
            val adaptiveDensity = remember(currentDensity.density, currentDensity.fontScale) {
                Density(
                    density = currentDensity.density,
                    fontScale = currentDensity.fontScale.coerceIn(0.85f, 1.30f)
                )
            }

            val systemReducedMotion = rememberSystemReducedMotion()

            WhiteGlassTheme(themeSpec = uiState.effectiveThemeSpec) {
                CompositionLocalProvider(
                    LocalDensity provides adaptiveDensity,
                    LocalReducedMotion provides (!uiState.fluidAnimationsEnabled || systemReducedMotion || uiState.effectiveThemeSpec.animationIntensity == ThemeAnimationIntensity.SUBTLE),
                    LocalSpringPhysics provides springCfg
                ) {
                    LaunchedEffect(Unit) {
                        PerformanceBenchmarkTracker.markColdLaunchReady()
                        if (FirebaseApp.getApps(this@MainActivity).isNotEmpty()) {
                            GoogleAuthManager.trySilentRestore(this@MainActivity)
                            viewModel.refreshSystemStatuses()
                        }
                    }

                    BackHandler(
                        enabled = uiState.selectedTab != AppTab.Home || uiState.homeSubScreen != HomeSubScreen.MAIN
                    ) {
                        if (uiState.selectedTab == AppTab.Home && uiState.homeSubScreen != HomeSubScreen.MAIN) {
                            viewModel.openHomeSubScreen(HomeSubScreen.MAIN)
                        } else {
                            viewModel.selectTab(AppTab.Home)
                        }
                    }

                    LaunchedEffect(uiState.solvedCount) {
                        if (uiState.solvedCount >= 5) {
                            InAppReviewHelper.maybeRequestReview(this@MainActivity, uiState.solvedCount)
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        MainInstantApp(
                            state = uiState,
                            viewModel = viewModel,
                            playUpdateState = playUpdateState,
                            onStartPlayUpdate = {
                                inAppUpdateController.startUpdateFlow(
                                    launcher = inAppUpdateLauncher,
                                    onFallbackOpenPlayStore = {
                                        AppShareHelper.openPlayStoreListing(this@MainActivity)
                                    }
                                )
                            },
                            onInstallReadyUpdate = {
                                inAppUpdateController.completeFlexibleUpdate()
                            },
                            onDismissPlayUpdateBanner = {
                                inAppUpdateController.dismissBanner()
                            },
                            onCheckForPlayUpdates = {
                                inAppUpdateController.checkForUpdates(
                                    manualCheck = true,
                                    onManualFeedback = { msg -> viewModel.showBannerMessage(msg) }
                                )
                            },
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
                            onRequestOverlayPermission = {
                                if (Settings.canDrawOverlays(this@MainActivity)) {
                                    openOverlayPermissionScreen()
                                } else {
                                    activePrePermissionPrompt = PrePermissionPrompt.OVERLAY_SETTING
                                }
                            },
                            onSignInGoogle = ::performInteractiveGoogleSignIn,
                            onSignOutGoogle = ::performGoogleSignOut,
                            onDeleteGoogleAccount = {
                                viewModel.deleteGoogleAccount(
                                    onDeleted = ::notifyServiceConfigChanged,
                                    onRequireReauth = ::performInteractiveGoogleSignIn
                                )
                            },
                            onOpenGoogleLogin = {
                                loginLauncher.launch(Intent(this@MainActivity, LoginActivity::class.java))
                            },
                            onTestVibration = ::testPocketHapticPulse,
                            onSyncRunningService = ::notifyServiceConfigChanged
                        )

                        // Show Legal Consent ONLY when tapping Start Solving for the first time
                        if (showStartConsentDialog && !uiState.userConsentAccepted) {
                            com.omnisolve.overlay.ui.components.MandatoryLegalConsentDialog(
                                onAcceptAndContinue = {
                                    triggerHapticTick()
                                    showStartConsentDialog = false
                                    viewModel.acceptUserConsent()
                                    checkOverlayPermissionAndStart()
                                }
                            )
                        }

                        // Fair-Use Warning (Strike 1) or Device Restriction (Strike 2) Dialog
                        uiState.pendingAbuseDialog?.let { enforcement ->
                            FairUseEnforcementDialog(
                                enforcement = enforcement,
                                onDismiss = {
                                    triggerHapticTick()
                                    viewModel.dismissAbuseDialog()
                                }
                            )
                        }

                        // Interactive Feature Tour (replayable from Settings)
                        NewUserFeatureGuideOverlay(
                            visible = uiState.showOnboardingGuide && !showEntryAnimation,
                            onFinishGuide = { _ ->
                                triggerHapticTick()
                                viewModel.completeOnboardingGuide(sendWelcomeNotification = true)
                            }
                        )

                        // Red Prism App Entry Reveal Animation
                        PrismAppEntryAnimationOverlay(
                            visible = showEntryAnimation,
                            onAnimationFinished = { showEntryAnimation = false }
                        )

                        // Calm 1-line pre-permission explainer before Android system dialogs
                        activePrePermissionPrompt?.let { promptStep ->
                            PrePermissionExplainerDialog(
                                step = promptStep,
                                onConfirm = {
                                    triggerHapticTick()
                                    activePrePermissionPrompt = null
                                    when (promptStep) {
                                        PrePermissionPrompt.OVERLAY_SETTING -> openOverlayPermissionScreen()
                                        PrePermissionPrompt.SCREEN_CAPTURE -> launchScreenCaptureIntent()
                                    }
                                },
                                onDismiss = {
                                    activePrePermissionPrompt = null
                                }
                            )
                        }

                        // Overlapping Dynamic Island Entrance Feedback immediately after Display Share approval
                        OverlappingDynamicIslandSpawnPulseOverlay(
                            triggerTick = islandEntrancePulseTick,
                            modifier = Modifier.align(Alignment.TopCenter)
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
                        notifyServiceConfigChanged()
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
            notifyServiceConfigChanged()
            viewModel.showBannerMessage("Signed out • Free Screen Reader active")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingDeepLink(intent)
    }

    private var foregroundResumeAtMs: Long = 0L

    override fun onResume() {
        super.onResume()
        foregroundResumeAtMs = System.currentTimeMillis()
        PrismEngagementNotificationManager.recordAppOpened(this)
        viewModel.refreshSystemStatuses()
        if (::inAppUpdateController.isInitialized) {
            inAppUpdateController.onResumeCheck(inAppUpdateLauncher)
        }
    }

    override fun onPause() {
        if (foregroundResumeAtMs > 0L) {
            val elapsedSec = ((System.currentTimeMillis() - foregroundResumeAtMs) / 1000L).coerceAtLeast(0L)
            foregroundResumeAtMs = 0L
            if (elapsedSec >= 2L) {
                viewModel.recordAppForegroundSession(elapsedSec)
            }
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (::inAppUpdateController.isInitialized) {
            inAppUpdateController.unregisterListener()
        }
        super.onDestroy()
    }

    private fun openOverlayPermissionScreen() {
        if (Settings.canDrawOverlays(this)) {
            viewModel.showBannerMessage("Floating pill permission is already allowed")
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
                "Turn on 'Display over other apps' so the pill can float above your quiz, then come back.",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            viewModel.showBannerMessage("Could not open overlay permission settings")
        }
    }

    private fun checkOverlayPermissionAndStart() {
        if (!viewModel.uiState.value.userConsentAccepted) {
            showStartConsentDialog = true
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            activePrePermissionPrompt = PrePermissionPrompt.OVERLAY_SETTING
            return
        }
        activePrePermissionPrompt = PrePermissionPrompt.SCREEN_CAPTURE
    }

    private fun launchScreenCaptureIntent() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startOverlayForegroundService(resultCode: Int, resultData: Intent) {
        val state = viewModel.uiState.value
        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, resultData)
            putExtra(OverlayService.EXTRA_DISPLAY_MODE, state.displayMode)
            putExtra(OverlayService.EXTRA_SCAN_INTERVAL_SEC, state.scanIntervalSec)
            putExtra(OverlayService.EXTRA_NEXT_DELAY_MS, state.nextDelayMs)
            putExtra(OverlayService.EXTRA_INSTANT_TRIGGER, state.instantTriggerEnabled)
            putExtra(OverlayService.EXTRA_EXTRACTION_ENGINE, state.extractionEngine)
            putExtra(OverlayService.EXTRA_HUD_STYLE, state.hudStyle)
            putExtra(OverlayService.EXTRA_CAMOUFLAGE, state.batteryCamouflage)
            putExtra(OverlayService.EXTRA_HAPTIC, state.hapticEnabled)
            putExtra(OverlayService.EXTRA_THEME_MODE, if (state.effectiveThemeSpec.isDark) THEME_DARK else THEME_LIGHT)
            putExtra(OverlayService.EXTRA_ISLAND_OPACITY_INDEX, state.islandOpacityIndex)
            putExtra(OverlayService.EXTRA_CUSTOM_OPACITY_PCT, state.customOpacityPct)
            putExtra(OverlayService.EXTRA_COMPANION_HOST, state.companionHost)
            putExtra(OverlayService.EXTRA_COMPANION_PORT, state.companionPort)
            putExtra(OverlayService.EXTRA_COMPANION_SYNC_ENABLED, state.companionSyncEnabled)
            putExtra(OverlayService.EXTRA_SOLVER_METHOD, state.solverMethod.id)
            putExtra(OverlayService.EXTRA_ISLAND_ACCENT_THEME, state.islandAccentTheme)
            putExtra(
                OverlayService.EXTRA_ISLAND_CUSTOM_HEX,
                if (state.effectiveThemeSpec.isNormalDefault) "" else state.effectiveThemeSpec.islandAccentHex
            )
            putExtra(OverlayService.EXTRA_ISLAND_SIZE_PRESET, state.islandSizePreset)
            putExtra(OverlayService.EXTRA_ISLAND_SCALE_PCT, state.islandScalePct)
            putExtra(OverlayService.EXTRA_ISLAND_ANSWER_SCALE, state.islandAnswerScale)
            putExtra(OverlayService.EXTRA_ISLAND_EDGE_SNAP, state.islandEdgeSnap)
            putExtra(OverlayService.EXTRA_ISLAND_IDLE_FADE_SEC, state.islandIdleFadeSec)
            putExtra(OverlayService.EXTRA_SPRING_DAMPING, state.springDamping)
            putExtra(OverlayService.EXTRA_SPRING_STIFFNESS, state.springStiffness)
            putExtra(OverlayService.EXTRA_FLUID_ANIMATIONS, state.fluidAnimationsEnabled)
            putExtra(OverlayService.EXTRA_ISLAND_WIDTH_DP, state.islandWidthDp)
            putExtra(OverlayService.EXTRA_ISLAND_HEIGHT_DP, state.islandHeightDp)
            putExtra(OverlayService.EXTRA_ISLAND_CORNER_RADIUS_DP, state.islandCornerRadiusDp)
            putExtra(OverlayService.EXTRA_ISLAND_ANIM_SPEED, state.islandAnimSpeed)
            putExtra(OverlayService.EXTRA_ISLAND_CUTOUT_ALIGN, state.islandCutoutAlign)
            putExtra(OverlayService.EXTRA_ISLAND_CUTOUT_OFFSET_X_DP, state.islandCutoutOffsetXDp)
            putExtra(OverlayService.EXTRA_ISLAND_CUTOUT_OFFSET_Y_DP, state.islandCutoutOffsetYDp)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        viewModel.setOverlayServiceState(running = true, captureAuthorized = true)
        viewModel.showBannerMessage("Floating pill started")
    }

    private fun stopOverlayForegroundService() {
        stopService(Intent(this, OverlayService::class.java))
        viewModel.setOverlayServiceState(running = false)
        viewModel.showBannerMessage("Floating pill stopped")
    }

    private fun notifyServiceConfigChanged() {
        val state = viewModel.uiState.value
        if (!state.isOverlayRunning || !OverlayService.isServiceRunning()) return

        val updateIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_CONFIG
            putExtra(OverlayService.EXTRA_DISPLAY_MODE, state.displayMode)
            putExtra(OverlayService.EXTRA_SCAN_INTERVAL_SEC, state.scanIntervalSec)
            putExtra(OverlayService.EXTRA_NEXT_DELAY_MS, state.nextDelayMs)
            putExtra(OverlayService.EXTRA_INSTANT_TRIGGER, state.instantTriggerEnabled)
            putExtra(OverlayService.EXTRA_EXTRACTION_ENGINE, state.extractionEngine)
            putExtra(OverlayService.EXTRA_HUD_STYLE, state.hudStyle)
            putExtra(OverlayService.EXTRA_CAMOUFLAGE, state.batteryCamouflage)
            putExtra(OverlayService.EXTRA_HAPTIC, state.hapticEnabled)
            putExtra(OverlayService.EXTRA_THEME_MODE, if (state.effectiveThemeSpec.isDark) THEME_DARK else THEME_LIGHT)
            putExtra(OverlayService.EXTRA_ISLAND_OPACITY_INDEX, state.islandOpacityIndex)
            putExtra(OverlayService.EXTRA_CUSTOM_OPACITY_PCT, state.customOpacityPct)
            putExtra(OverlayService.EXTRA_COMPANION_HOST, state.companionHost)
            putExtra(OverlayService.EXTRA_COMPANION_PORT, state.companionPort)
            putExtra(OverlayService.EXTRA_COMPANION_SYNC_ENABLED, state.companionSyncEnabled)
            putExtra(OverlayService.EXTRA_SOLVER_METHOD, state.solverMethod.id)
            putExtra(OverlayService.EXTRA_ISLAND_ACCENT_THEME, state.islandAccentTheme)
            putExtra(
                OverlayService.EXTRA_ISLAND_CUSTOM_HEX,
                if (state.effectiveThemeSpec.isNormalDefault) "" else state.effectiveThemeSpec.islandAccentHex
            )
            putExtra(OverlayService.EXTRA_ISLAND_SIZE_PRESET, state.islandSizePreset)
            putExtra(OverlayService.EXTRA_ISLAND_SCALE_PCT, state.islandScalePct)
            putExtra(OverlayService.EXTRA_ISLAND_ANSWER_SCALE, state.islandAnswerScale)
            putExtra(OverlayService.EXTRA_ISLAND_EDGE_SNAP, state.islandEdgeSnap)
            putExtra(OverlayService.EXTRA_ISLAND_IDLE_FADE_SEC, state.islandIdleFadeSec)
            putExtra(OverlayService.EXTRA_SPRING_DAMPING, state.springDamping)
            putExtra(OverlayService.EXTRA_SPRING_STIFFNESS, state.springStiffness)
            putExtra(OverlayService.EXTRA_FLUID_ANIMATIONS, state.fluidAnimationsEnabled)
            putExtra(OverlayService.EXTRA_ISLAND_WIDTH_DP, state.islandWidthDp)
            putExtra(OverlayService.EXTRA_ISLAND_HEIGHT_DP, state.islandHeightDp)
            putExtra(OverlayService.EXTRA_ISLAND_CORNER_RADIUS_DP, state.islandCornerRadiusDp)
            putExtra(OverlayService.EXTRA_ISLAND_ANIM_SPEED, state.islandAnimSpeed)
            putExtra(OverlayService.EXTRA_ISLAND_CUTOUT_ALIGN, state.islandCutoutAlign)
            putExtra(OverlayService.EXTRA_ISLAND_CUTOUT_OFFSET_X_DP, state.islandCutoutOffsetXDp)
            putExtra(OverlayService.EXTRA_ISLAND_CUTOUT_OFFSET_Y_DP, state.islandCutoutOffsetYDp)
        }
        try {
            startService(updateIntent)
        } catch (_: Exception) {
        }
    }

    private fun handleIncomingDeepLink(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(EXTRA_REQUEST_GOOGLE_SIGN_IN, false)) {
            intent.removeExtra(EXTRA_REQUEST_GOOGLE_SIGN_IN)
            skipEntryAnimationForShortcut = true
            viewModel.requestGoogleSignInGate()
            performInteractiveGoogleSignIn()
        }

        if (intent.getBooleanExtra(EXTRA_LAUNCH_OVERLAY_FROM_TILE, false)) {
            intent.removeExtra(EXTRA_LAUNCH_OVERLAY_FROM_TILE)
            skipEntryAnimationForShortcut = true
            if (OverlayService.isServiceRunning()) {
                OverlayService.triggerSolveFromHardware()
            } else {
                checkOverlayPermissionAndStart()
            }
            return
        }

        // Priority 1: Android Share-Sheet Direct Answer Key (WhatsApp / Telegram / Chrome / Gallery)
        val sharedPayload = ShareIntentRouter.parseAndConsume(intent)
        if (sharedPayload != null) {
            skipEntryAnimationForShortcut = true
            val onSignInRequired = {
                viewModel.requestGoogleSignInGate()
                performInteractiveGoogleSignIn()
            }
            when (sharedPayload) {
                is SharedPayload.SharedPhoto -> {
                    viewModel.selectTab(AppTab.Home)
                    viewModel.openHomeSubScreen(HomeSubScreen.MAIN)
                    if (sharedPayload.caption.isNotBlank()) {
                        viewModel.updateUniversalPrompt(sharedPayload.caption)
                    }
                    viewModel.pickUniversalPhoto(
                        uri = sharedPayload.uri,
                        autoSolveNow = true,
                        onRequireGoogleSignIn = onSignInRequired
                    )
                    viewModel.showBannerMessage("Shared photo received · Checking answer...")
                }

                is SharedPayload.SharedPdf -> {
                    viewModel.selectTab(AppTab.Home)
                    viewModel.openHomeSubScreen(HomeSubScreen.MAIN)
                    if (sharedPayload.caption.isNotBlank()) {
                        viewModel.updateUniversalPrompt(sharedPayload.caption)
                    }
                    viewModel.pickUniversalPdf(
                        uri = sharedPayload.uri,
                        autoSolveNow = true,
                        onRequireGoogleSignIn = onSignInRequired
                    )
                    viewModel.showBannerMessage("Shared PDF received · Reading pages...")
                }

                is SharedPayload.SharedGoogleFormUrl -> {
                    viewModel.selectTab(AppTab.Home)
                    viewModel.openHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE)
                    viewModel.updateGoogleFormUrl(sharedPayload.url)
                    viewModel.solveGoogleFormFromUrl(onRequireGoogleSignIn = onSignInRequired)
                    viewModel.showBannerMessage("Google Form link received · Extracting questions...")
                }

                is SharedPayload.SharedQuestionText -> {
                    viewModel.selectTab(AppTab.Home)
                    viewModel.openHomeSubScreen(HomeSubScreen.MAIN)
                    viewModel.openQuickSolveSheet()
                    viewModel.updateUniversalPrompt(sharedPayload.text)
                    viewModel.sendUniversalMessage(
                        promptOverride = sharedPayload.text,
                        onRequireGoogleSignIn = onSignInRequired
                    )
                    viewModel.showBannerMessage("Shared question received · Solving...")
                }
            }
            return
        }

        val data = intent.data ?: return
        if (data.scheme == "omnisolve" && data.host == "sync") {
            val host = data.getQueryParameter("host")
            val port = data.getQueryParameter("port")?.toIntOrNull() ?: 59124
            if (!host.isNullOrBlank()) {
                viewModel.applyDeepLinkSync(host, port, ::notifyServiceConfigChanged)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            if (!cm.hasPrimaryClip()) return
            val copied = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            viewModel.inspectClipboardForFormLink(copied)
        } catch (_: Exception) {
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
    onSignInGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onDeleteGoogleAccount: () -> Unit,
    onOpenGoogleLogin: () -> Unit,
    onTestVibration: () -> Unit,
    onSyncRunningService: () -> Unit,
    playUpdateState: PlayUpdateUiState = PlayUpdateUiState.Idle,
    onStartPlayUpdate: () -> Unit = {},
    onInstallReadyUpdate: () -> Unit = {},
    onDismissPlayUpdateBanner: () -> Unit = {},
    onCheckForPlayUpdates: () -> Unit = {}
) {
    val palette = LocalWhiteGlassPalette.current
    val homeListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    var isPlusTreeOpen by rememberSaveable { mutableStateOf(false) }

    val onRequireSignInForQuickSolve: () -> Unit = {
        viewModel.requestGoogleSignInGate()
        onSignInGoogle()
    }

    val plusPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.pickUniversalPhoto(
                uri = uri,
                autoSolveNow = true,
                onRequireGoogleSignIn = onRequireSignInForQuickSolve
            )
        }
    }

    val plusContext = androidx.compose.ui.platform.LocalContext.current
    var pendingCameraCaptureFile by remember { mutableStateOf<java.io.File?>(null) }
    var pendingCameraCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val plusCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val file = pendingCameraCaptureFile
        val uri = pendingCameraCaptureUri
        if (saved && file != null && uri != null) {
            viewModel.pickCapturedCameraPhoto(
                file = file,
                uri = uri,
                onRequireGoogleSignIn = onRequireSignInForQuickSolve
            )
        }
    }

    val plusPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.pickUniversalPdf(
                uri = uri,
                autoSolveNow = true,
                onRequireGoogleSignIn = onRequireSignInForQuickSolve
            )
        }
    }

    // Close the + tree automatically whenever user switches screens
    LaunchedEffect(state.selectedTab, state.homeSubScreen) {
        isPlusTreeOpen = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LiquidAtmosphericBackdrop(
            scrollOffsetProvider = {
                val activeList = when (state.selectedTab) {
                    AppTab.Settings -> settingsListState
                    else -> homeListState
                }
                (activeList.firstVisibleItemIndex * 220f + activeList.firstVisibleItemScrollOffset).coerceIn(0f, 1600f)
            }
        )

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                WhiteGlassTopHeader(
                    isOverlayRunning = state.isOverlayRunning,
                    isNotifPinned = state.customPinConfig.isPinned || state.isGoogleFormPinned,
                    selectedTab = state.selectedTab,
                    homeSubScreen = state.homeSubScreen,
                    onBackFromSubScreen = {
                        viewModel.openHomeSubScreen(HomeSubScreen.MAIN)
                    },
                    onToggleSettings = {
                        isPlusTreeOpen = false
                        onSelectTab(
                            if (state.selectedTab == AppTab.Settings) AppTab.Home else AppTab.Settings
                        )
                    },
                    onOpenThemes = {
                        isPlusTreeOpen = false
                        onSelectTab(AppTab.Home)
                        viewModel.openHomeSubScreen(HomeSubScreen.THEME_STUDIO)
                    },
                    onShowNotifications = {
                        isPlusTreeOpen = false
                        onSelectTab(AppTab.Home)
                        viewModel.openHomeSubScreen(HomeSubScreen.NOTIFICATION_STUDIO)
                    }
                )
            },
            bottomBar = {
                // Keep Home | + | Settings visible on BOTH Home AND Settings so returning Home is always 1 tap away
                if (!state.advSplitScreenRailEnabled &&
                    state.homeSubScreen == HomeSubScreen.MAIN
                ) {
                    LiquidMorphBottomDock(
                        selectedTab = if (state.selectedTab == AppTab.Stats) AppTab.Home else state.selectedTab,
                        onSelectTab = { targetTab ->
                            if (targetTab == AppTab.Stats) {
                                isPlusTreeOpen = !isPlusTreeOpen
                            } else {
                                isPlusTreeOpen = false
                                onSelectTab(targetTab)
                            }
                        },
                        isPlusMenuOpen = isPlusTreeOpen,
                        onTogglePlusMenu = { isPlusTreeOpen = !isPlusTreeOpen }
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
                        selectedTab = if (state.selectedTab == AppTab.Stats) AppTab.Home else state.selectedTab,
                        onSelectTab = { targetTab ->
                            if (targetTab == AppTab.Stats) {
                                isPlusTreeOpen = !isPlusTreeOpen
                            } else {
                                isPlusTreeOpen = false
                                onSelectTab(targetTab)
                            }
                        }
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
                        .widthIn(max = 600.dp)
                    val reducedMotion = LocalReducedMotion.current
                    val activeArchetype = LocalPrismThemeSpec.current.layoutArchetype

                    // Zero-jank GPU-composited directional screen shift between Home <-> Settings
                    val effectiveTab = if (state.selectedTab == AppTab.Stats) AppTab.Home else state.selectedTab
                    AnimatedContent(
                        targetState = effectiveTab,
                        transitionSpec = {
                            val movingForward = targetState.ordinal >= initialState.ordinal
                            com.omnisolve.overlay.ui.physics.MotionTokens.tabTransitionFor(
                                archetype = activeArchetype,
                                movingForward = movingForward,
                                reducedMotion = reducedMotion
                            )
                        },
                        label = "theme_aware_tab_screen_shift"
                    ) { activeTab ->
                    when (activeTab) {
                        AppTab.Home, AppTab.Stats -> HomeScreen(
                            state = state,
                            listState = homeListState,
                            onToggleOverlay = onToggleOverlay,
                            onRequestOverlayPermission = onRequestOverlayPermission,
                            onSelectSolverMethod = { method ->
                                viewModel.selectSolverMethod(
                                    method = method,
                                    onSyncService = onSyncRunningService,
                                    onRequireGoogleSignIn = {
                                        viewModel.requestGoogleSignInGate()
                                        onSignInGoogle()
                                    }
                                )
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
                            onSelectHudStyle = { style ->
                                viewModel.setHudStyle(style, onSyncRunningService)
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
                            onToggleAdvSplitPill = { enabled ->
                                viewModel.setAdvSplitPill(enabled, onSyncRunningService)
                            },
                            onToggleAdvIslandGestures = { enabled ->
                                viewModel.setAdvIslandGestures(enabled, onSyncRunningService)
                            },
                            onSelectIslandAccentTheme = { theme ->
                                viewModel.setIslandAccentTheme(theme, onSyncRunningService)
                            },
                            onSelectIslandSizePreset = { preset ->
                                viewModel.setIslandSizePreset(preset, onSyncRunningService)
                            },
                            onSetIslandScalePct = { pct ->
                                viewModel.setIslandScalePct(pct, onSyncRunningService)
                            },
                            onSelectIslandAnswerScale = { scale ->
                                viewModel.setIslandAnswerScale(scale, onSyncRunningService)
                            },
                            onToggleIslandEdgeSnap = { enabled ->
                                viewModel.setIslandEdgeSnap(enabled, onSyncRunningService)
                            },
                            onSelectIslandIdleFadeSec = { sec ->
                                viewModel.setIslandIdleFadeSec(sec, onSyncRunningService)
                            },
                            onUpdateIslandSpringPhysics = { damping, stiffness ->
                                viewModel.setAdvSpringPhysics(true)
                                viewModel.updateSpringPhysics(damping, stiffness)
                                onSyncRunningService()
                            },
                            onSetIslandWidthDp = { widthDp ->
                                viewModel.setIslandWidthDp(widthDp, onSyncRunningService)
                            },
                            onSetIslandHeightDp = { heightDp ->
                                viewModel.setIslandHeightDp(heightDp, onSyncRunningService)
                            },
                            onSetIslandCornerRadiusDp = { cornerDp ->
                                viewModel.setIslandCornerRadiusDp(cornerDp, onSyncRunningService)
                            },
                            onSetIslandAnimSpeed = { speed ->
                                viewModel.setIslandAnimSpeed(speed, onSyncRunningService)
                            },
                            onToggleIslandCutoutAlign = { aligned ->
                                viewModel.setIslandCutoutAlign(aligned, onSyncRunningService)
                            },
                            onSetIslandCutoutOffsets = { xDp, yDp ->
                                viewModel.setIslandCutoutOffsets(xDp, yDp, onSyncRunningService)
                            },
                            onResetIslandGeometry = {
                                viewModel.resetIslandGeometryDefaults(onSyncRunningService)
                            },
                            onToggleFluidAnimations = { enabled ->
                                viewModel.setFluidAnimationsEnabled(enabled, onSyncRunningService)
                            },
                            onOpenSettings = { onSelectTab(AppTab.Settings) },
                            onSignInServer = onSignInGoogle,
                            onOpenGeminiLogin = onOpenGoogleLogin,
                            onToggleTheme = {
                                viewModel.setAdvDarkObsidian(!state.advDarkObsidianEnabled, onSyncRunningService)
                            },
                            onOpenHomeSubScreen = viewModel::openHomeSubScreen,
                            onToggleBookmarkEntry = viewModel::toggleBookmarkHistoryEntry,
                            onDeleteHistoryEntry = viewModel::deleteHistoryEntry,
                            onClearOfflineRoomCache = viewModel::clearOfflineRoomCache,
                            onAddSampleExport = viewModel::appendSampleQuestionToExport,
                            onClearExportHistory = viewModel::clearHistory,
                            onShowBannerMessage = viewModel::showBannerMessage,
                            onUpdateGoogleFormUrl = viewModel::updateGoogleFormUrl,
                            onUpdateGoogleFormPastedText = viewModel::updateGoogleFormPastedText,
                            onSelectSubjectMode = viewModel::selectSubjectMode,
                            onSelectGoogleFormsModel = viewModel::selectGoogleFormsModel,
                            onToggleGoogleFormAppendMode = viewModel::toggleGoogleFormAppendMode,
                            onSolveGoogleFormUrl = {
                                viewModel.solveGoogleFormFromUrl {
                                    viewModel.requestGoogleSignInGate()
                                    onSignInGoogle()
                                }
                            },
                            onSolveGoogleFormPastedText = {
                                viewModel.solveGoogleFormFromPastedText {
                                    viewModel.requestGoogleSignInGate()
                                    onSignInGoogle()
                                }
                            },
                            onSolveGoogleFormImageUri = { uri ->
                                viewModel.openHomeSubScreen(HomeSubScreen.MAIN)
                                viewModel.pickUniversalPhoto(uri, autoSolveNow = true) {
                                    viewModel.requestGoogleSignInGate()
                                    onSignInGoogle()
                                }
                            },
                            onSolveGoogleFormPdfUri = { uri ->
                                viewModel.openHomeSubScreen(HomeSubScreen.MAIN)
                                viewModel.pickUniversalPdf(uri, autoSolveNow = true) {
                                    viewModel.requestGoogleSignInGate()
                                    onSignInGoogle()
                                }
                            },
                            onOpenAskAiStudio = {
                                viewModel.openHomeSubScreen(HomeSubScreen.MAIN)
                                isPlusTreeOpen = true
                            },
                            onReSolveGoogleFormQuestion = { qNum ->
                                viewModel.reSolveSingleGoogleFormQuestion(qNum) {
                                    viewModel.requestGoogleSignInGate()
                                    onSignInGoogle()
                                }
                            },
                            onPinFormsKeyToDynamicIsland = viewModel::pinFormsKeyToDynamicIsland,
                            onUnpinFormsKeyFromDynamicIsland = viewModel::unpinFormsKeyFromDynamicIsland,
                            onClearGoogleFormResult = viewModel::clearGoogleFormResult,
                            onRefreshGoogleFormPinStatus = viewModel::refreshGoogleFormPinStatus,
                            onSaveGoogleFormsToExport = viewModel::saveGoogleFormItemsToExportHistory,
                            onCancelGoogleFormSolve = viewModel::cancelGoogleFormSolve,
                            onUpdateCustomNotifConfig = viewModel::updateCustomNotifConfig,
                            onApplyCustomNotifPreset = viewModel::applyCustomNotifPreset,
                            onImportLatestSolvedIntoNotif = viewModel::importLatestSolvedIntoCustomNotif,
                            onPinCustomBarNotification = viewModel::pinCustomBarNotification,
                            onUnpinCustomBarNotification = viewModel::unpinCustomBarNotification,
                            onToggleAutoWeeklyEngagement = viewModel::setAutoWeeklyEngagement,
                            onSendRandomTestNotification = viewModel::sendOneRandomTestEngagementNotification,
                            onSendWelcomeNotification = viewModel::sendWelcomeNotificationManual,
                            onRefreshNotificationPermission = viewModel::refreshSystemStatuses,
                            onSelectLocalThemePreset = { spec ->
                                viewModel.selectLocalThemePreset(spec, onSyncRunningService)
                            },
                            onRevertToNormalDefaultTheme = {
                                viewModel.revertToNormalDefaultTheme(onSyncRunningService)
                            },
                            onSelectThemeAnimOverride = { intensity ->
                                viewModel.setThemeAnimationOverride(intensity, onSyncRunningService)
                            },
                            onSelectThemeAppearanceOverride = { mode ->
                                viewModel.setThemeColorAppearanceOverride(mode, onSyncRunningService)
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
                            onDeleteGoogleAccount = onDeleteGoogleAccount,
                            onOpenGeminiWebLogin = onOpenGoogleLogin,
                            onRequestOverlayPermission = onRequestOverlayPermission,
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
                            onSolvePractice = viewModel::solvePracticeQuestion,
                            onNextPractice = viewModel::nextSampleQuestion,
                            onClearHistory = viewModel::clearHistory,
                            onSelectSolverMethod = { method ->
                                viewModel.selectSolverMethod(
                                    method = method,
                                    onSyncService = onSyncRunningService,
                                    onRequireGoogleSignIn = {
                                        viewModel.requestGoogleSignInGate()
                                        onSignInGoogle()
                                    }
                                )
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
                            onToggleFluidAnimations = { enabled ->
                                viewModel.setFluidAnimationsEnabled(enabled, onSyncRunningService)
                            },
                            onFlashcardSearchChange = viewModel::updateFlashcardSearch,
                            onFlashcardSubjectFilterChange = viewModel::updateFlashcardSubjectFilter,
                            onToggleFlashcardBookmarkedOnly = viewModel::setFlashcardBookmarkedOnly,
                            onToggleBookmarkEntry = viewModel::toggleBookmarkHistoryEntry,
                            onShowBannerMessage = viewModel::showBannerMessage,
                            onOpenNotificationStudio = {
                                viewModel.openHomeSubScreen(HomeSubScreen.NOTIFICATION_STUDIO)
                            },
                            onReplayOnboardingGuide = viewModel::replayOnboardingGuide,
                            onOpenThemeStudio = {
                                viewModel.selectTab(AppTab.Home)
                                viewModel.openHomeSubScreen(HomeSubScreen.THEME_STUDIO)
                            },
                            onSelectLocalThemePreset = { spec ->
                                viewModel.selectLocalThemePreset(spec, onSyncRunningService)
                            },
                            onRevertToNormalDefaultTheme = {
                                viewModel.revertToNormalDefaultTheme(onSyncRunningService)
                            },
                            onClearOfflineRoomCache = viewModel::clearOfflineRoomCache,
                            onOpenHistoryVault = {
                                viewModel.selectTab(AppTab.Home)
                                viewModel.openHomeSubScreen(HomeSubScreen.EXPORT_QA)
                            },
                            playUpdateState = playUpdateState,
                            onCheckForPlayUpdates = {
                                if (playUpdateState is PlayUpdateUiState.Available) {
                                    onStartPlayUpdate()
                                } else {
                                    onCheckForPlayUpdates()
                                }
                            },
                            onInstallReadyUpdate = onInstallReadyUpdate,
                            modifier = contentMod
                        )
                    }
                    }

                    // Smart Clipboard Form-Link Suggestion Pill (1-tap solve copied WhatsApp/Chrome quiz link)
                    val copiedFormLink = state.copiedFormLinkSuggestion
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !copiedFormLink.isNullOrBlank() && state.homeSubScreen != HomeSubScreen.GOOGLE_FORMS_SOLVE && !isPlusTreeOpen,
                        enter = if (reducedMotion) EnterTransition.None else slideInVertically { -it } + fadeIn(tween(220)),
                        exit = if (reducedMotion) ExitTransition.None else slideOutVertically { -it } + fadeOut(tween(170)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .widthIn(max = 560.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF141519))
                                .border(1.dp, palette.accentSky.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("banner_copied_form_link"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = null,
                                tint = palette.accentSky,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Quiz link copied",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = "Tap Solve to get answers right away",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = palette.inkSecondary,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.accentSky)
                                    .clickable(role = Role.Button) {
                                        val link = copiedFormLink.orEmpty()
                                        viewModel.dismissCopiedFormLinkSuggestion()
                                        if (link.isNotBlank()) {
                                            onSelectTab(AppTab.Home)
                                            viewModel.openHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE)
                                            viewModel.updateGoogleFormUrl(link)
                                            viewModel.solveGoogleFormFromUrl(onRequireGoogleSignIn = onRequireSignInForQuickSolve)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("btn_solve_copied_form_link")
                            ) {
                                Text(
                                    text = "Solve",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.onAccentInk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button) {
                                        viewModel.dismissCopiedFormLinkSuggestion()
                                    }
                                    .testTag("btn_dismiss_copied_form_link")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss copied link",
                                    tint = palette.inkSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Non-blocking Play Store In-App Update Banner
                    val showPlayUpdateBanner = playUpdateState is PlayUpdateUiState.Available ||
                        playUpdateState is PlayUpdateUiState.Downloading ||
                        playUpdateState is PlayUpdateUiState.ReadyToInstall
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showPlayUpdateBanner,
                        enter = if (reducedMotion) EnterTransition.None else slideInVertically { it } + fadeIn(tween(220)),
                        exit = if (reducedMotion) ExitTransition.None else slideOutVertically { it } + fadeOut(tween(180)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = if (state.homeSubScreen == HomeSubScreen.MAIN) 96.dp else 18.dp
                            )
                            .widthIn(max = 560.dp)
                    ) {
                        PlayStoreUpdateFloatingBanner(
                            updateState = playUpdateState,
                            onStartUpdate = onStartPlayUpdate,
                            onInstallReady = onInstallReadyUpdate,
                            onDismiss = onDismissPlayUpdateBanner
                        )
                    }

                    // Instant status notification capsule
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.statusBannerMessage != null,
                        enter = if (reducedMotion) EnterTransition.None else slideInVertically { -it } + fadeIn(tween(200)),
                        exit = if (reducedMotion) ExitTransition.None else slideOutVertically { -it } + fadeOut(tween(180)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .livingGlassSurface(
                                    strength = GlassMaterialStrength.FloatingDock,
                                    cornerRadius = 999.dp
                                )
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = state.statusBannerMessage.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.inkPrimary
                            )
                        }
                    }

                    // Radial Bloom Action Deck rising organically from the center `+` dock button
                    QuickPlusTreeOverlay(
                        visible = isPlusTreeOpen,
                        dailyUsage = state.dailyUsage,
                        onDismiss = { isPlusTreeOpen = false },
                        onPickPhoto = {
                            plusPhotoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onPickPdf = {
                            plusPdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        onOpenFormLink = {
                            onSelectTab(AppTab.Home)
                            viewModel.openHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE)
                        },
                        onPickCamera = {
                            runCatching {
                                val dir = java.io.File(plusContext.cacheDir, "camera_captures").apply { mkdirs() }
                                val outFile = java.io.File(dir, "snap_${System.currentTimeMillis()}.jpg")
                                val outUri = FileProvider.getUriForFile(
                                    plusContext,
                                    "${plusContext.packageName}.fileprovider",
                                    outFile
                                )
                                pendingCameraCaptureFile = outFile
                                pendingCameraCaptureUri = outUri
                                plusCameraLauncher.launch(outUri)
                            }.onFailure {
                                viewModel.showBannerMessage("Camera app could not be opened on this device")
                            }
                        },
                        onToggleFloatingPill = onToggleOverlay,
                        isOverlayRunning = state.isOverlayRunning
                    )
                }
            }
        }

        // Slide-up Quick Solve Sheet for Photo & PDF answers (no separate Scan tab needed)
        QuickSolveResultSheet(
            visible = state.isQuickSolveSheetOpen,
            state = state,
            onDismiss = viewModel::dismissQuickSolveSheet,
            onUpdatePrompt = viewModel::updateUniversalPrompt,
            onSendFollowUp = {
                viewModel.sendUniversalMessage(onRequireGoogleSignIn = onRequireSignInForQuickSolve)
            },
            onCancelSolve = viewModel::cancelUniversalSolve,
            onStartFresh = viewModel::startNewUniversalChat,
            onShowBanner = viewModel::showBannerMessage
        )
    }
}

@Composable
private fun PlayStoreUpdateFloatingBanner(
    updateState: PlayUpdateUiState,
    onStartUpdate: () -> Unit,
    onInstallReady: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = PrismCurves.cardCorner(themeSpec)
    val buttonCorner = PrismCurves.pillOrButtonCorner(themeSpec)

    val title = when (updateState) {
        is PlayUpdateUiState.Available -> "Prism Solver update ready"
        is PlayUpdateUiState.Downloading -> "Downloading update (${updateState.progressPct}%)"
        PlayUpdateUiState.ReadyToInstall -> "Update downloaded"
        else -> ""
    }
    val subtitle = when (updateState) {
        is PlayUpdateUiState.Available -> "New Play Store release with faster math & bug fixes"
        is PlayUpdateUiState.Downloading -> "Keep using Prism Solver while it finishes"
        PlayUpdateUiState.ReadyToInstall -> "Restart now to apply the newest version"
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(cardCorner), ambientColor = Color.Black.copy(alpha = 0.45f))
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(cardCorner))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("banner_play_in_app_update"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(palette.accentSky.copy(alpha = 0.16f))
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = "App update available",
                tint = palette.accentSky,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        when (updateState) {
            is PlayUpdateUiState.Available -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(buttonCorner))
                        .background(palette.accentSky)
                        .clickable(role = Role.Button, onClick = onStartUpdate)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("btn_banner_play_update_now")
                ) {
                    Text(
                        text = "Update",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = palette.onAccentInk,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                if (!updateState.isImmediate) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onDismiss)
                            .testTag("btn_banner_play_update_later")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss update banner",
                            tint = palette.inkSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            PlayUpdateUiState.ReadyToInstall -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(buttonCorner))
                        .background(palette.accentMint)
                        .clickable(role = Role.Button, onClick = onInstallReady)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("btn_banner_play_restart_install")
                ) {
                    Text(
                        text = "Restart",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun GoogleSignInMandatoryGateDialog(
    isSigningIn: Boolean,
    onContinueWithGoogle: () -> Unit,
    onContinueOcrOnly: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = PrismCurves.heroCorner(themeSpec)
    val buttonCorner = PrismCurves.pillOrButtonCorner(themeSpec)
    val stripCorner = PrismCurves.innerStripCorner(themeSpec)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvasTop.copy(alpha = 0.90f))
            .clickable(enabled = false, onClick = {})
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("google_signin_mandatory_gate"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(cardCorner))
                .background(palette.glassStrong)
                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accentSky.copy(alpha = 0.16f))
                        .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    RedPrismLogoMark(
                        modifier = Modifier.size(28.dp),
                        animated = true
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sign in with Google",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = "Save your settings and unlock Best Accuracy",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.inkSecondary,
                            fontSize = 12.5.sp
                        )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(stripCorner))
                    .background(palette.canvasBottom)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(stripCorner))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Good to know",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                )
                Text(
                    text = "• Signing in unlocks Best Accuracy, Photo & Diagrams, and Form Auto-Fill.\n• If a school Google Form is private, the form page may ask for your student login separately.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = palette.inkSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(buttonCorner))
                    .background(palette.accentSky)
                    .clickable(enabled = !isSigningIn, role = Role.Button, onClick = onContinueWithGoogle)
                    .padding(vertical = 14.dp, horizontal = 16.dp)
                    .testTag("btn_gate_google_signin")
            ) {
                Text(
                    text = if (isSigningIn) "Connecting Google Account..." else "Continue with Google",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = palette.onAccentInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    )
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(buttonCorner))
                    .background(palette.glassSubtle)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(buttonCorner))
                    .clickable(enabled = !isSigningIn, role = Role.Button, onClick = onContinueOcrOnly)
                    .padding(vertical = 13.dp, horizontal = 16.dp)
                    .testTag("btn_gate_ocr_only")
            ) {
                Text(
                    text = "Skip for Now (Free Screen Reader)",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                )
            }
        }
    }
}

private enum class PrePermissionPrompt {
    OVERLAY_SETTING,
    SCREEN_CAPTURE
}

@Composable
private fun PrePermissionExplainerDialog(
    step: PrePermissionPrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = PrismCurves.heroCorner(themeSpec)
    val buttonCorner = PrismCurves.pillOrButtonCorner(themeSpec)
    val isOverlay = step == PrePermissionPrompt.OVERLAY_SETTING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvasTop.copy(alpha = 0.88f))
            .clickable(enabled = true, onClick = onDismiss)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("dialog_pre_permission_explainer"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(cardCorner))
                .background(palette.glassStrong)
                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.accentSky.copy(alpha = 0.16f))
                        .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = palette.accentSky,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isOverlay) "Step 1 of 2: Show Floating Pill" else "Step 2 of 2: Screen Reading",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = if (isOverlay) {
                            "Needed so the answer pill can stay on top of your quiz app."
                        } else {
                            "Android shows a 'Start recording or casting' box next. Don't worry."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.inkSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Text(
                text = if (isOverlay) {
                    "Find Prism Solver in the list, turn on 'Allow display over other apps', then come right back."
                } else {
                    "We only read the question on your screen when you tap the top pill. Nothing is recorded, stored, or shared."
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = palette.inkPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(buttonCorner))
                        .background(palette.glassSubtle)
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(buttonCorner))
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(vertical = 12.dp)
                        .testTag("btn_pre_permission_cancel")
                ) {
                    Text(
                        text = "Not Now",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1.4f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(buttonCorner))
                        .background(palette.accentSky)
                        .clickable(role = Role.Button, onClick = onConfirm)
                        .padding(vertical = 12.dp)
                        .testTag("btn_pre_permission_continue")
                ) {
                    Text(
                        text = if (isOverlay) "Open Settings" else "Continue & Allow",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = palette.onAccentInk,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlappingDynamicIslandSpawnPulseOverlay(
    triggerTick: Int,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val palette = LocalWhiteGlassPalette.current
    var visible by remember { mutableStateOf(false) }
    val scaleAnim = remember { Animatable(0.32f) }
    val sweepAnim = remember { Animatable(0f) }

    LaunchedEffect(triggerTick, reducedMotion) {
        if (triggerTick <= 0 || reducedMotion) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        scaleAnim.snapTo(0.32f)
        sweepAnim.snapTo(0f)
        launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.56f, stiffness = 420f)
            )
        }
        launch {
            sweepAnim.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 1050, easing = FastOutSlowInEasing)
            )
        }
        delay(1250L)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)) + slideInVertically { -it / 2 },
        exit = fadeOut(tween(280)) + scaleOut(targetScale = 0.72f),
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        val accent = palette.accentSky
        val accentBright = palette.accentSkyBright
        Box(
            modifier = Modifier
                .scale(scaleAnim.value)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(999.dp),
                    ambientColor = accent,
                    spotColor = accentBright
                )
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF040507))
                .testTag("overlapping_island_spawn_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val corner = size.height / 2f
                rotate(degrees = sweepAnim.value) {
                    drawRoundRect(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.25f),
                                accentBright,
                                Color.White,
                                accent,
                                accent.copy(alpha = 0.25f)
                            )
                        ),
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(palette.accentMint)
                )
                Text(
                    text = "Floating Pill Ready",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun WhiteGlassTopHeader(
    isOverlayRunning: Boolean,
    isNotifPinned: Boolean = false,
    selectedTab: AppTab,
    homeSubScreen: HomeSubScreen = HomeSubScreen.MAIN,
    onBackFromSubScreen: () -> Unit = {},
    onToggleSettings: () -> Unit,
    onOpenThemes: () -> Unit = {},
    onShowNotifications: () -> Unit = {}
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val archetype = themeSpec.layoutArchetype
    val headerBtnCorner = PrismCurves.iconWellCorner(themeSpec)
    val actionShape = when (archetype) {
        ThemeLayoutArchetype.ATELIER_BENTO -> RoundedCornerShape(12.dp)
        ThemeLayoutArchetype.TELEMETRY_HUD -> RoundedCornerShape(8.dp)
        ThemeLayoutArchetype.KINETIC_DECK -> RoundedCornerShape(14.dp)
        ThemeLayoutArchetype.IOS_LIQUID_LIGHT,
        ThemeLayoutArchetype.EDITORIAL_MONOGRAPH,
        ThemeLayoutArchetype.CLASSIC_PRISM -> CircleShape
    }
    val showBottomHairline = archetype == ThemeLayoutArchetype.ATELIER_BENTO ||
        archetype == ThemeLayoutArchetype.TELEMETRY_HUD

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val isKineticBar = archetype == ThemeLayoutArchetype.KINETIC_DECK &&
                selectedTab != AppTab.Settings &&
                !(selectedTab == AppTab.Home && homeSubScreen != HomeSubScreen.MAIN)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .then(
                        if (isKineticBar) {
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(palette.glassStrong)
                                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(18.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        } else {
                            Modifier
                        }
                    )
                    .testTag("top_glass_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (selectedTab == AppTab.Settings || (selectedTab == AppTab.Home && homeSubScreen != HomeSubScreen.MAIN)) {
                    val isSubScreen = selectedTab == AppTab.Home && homeSubScreen != HomeSubScreen.MAIN
                    val headerTitle = if (isSubScreen) homeSubScreen.title else "Settings"
                    val headerSubtitle = if (isSubScreen) homeSubScreen.subtitle else "Manage your app preferences"
                    val backAction = if (isSubScreen) onBackFromSubScreen else onToggleSettings
                    val titleFont = when (archetype) {
                        ThemeLayoutArchetype.TELEMETRY_HUD -> FontFamily.Monospace
                        ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> FontFamily.Serif
                        else -> FontFamily.Default
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(actionShape)
                                .background(palette.glassMedium)
                                .border(1.dp, palette.cardHairlineBorder, actionShape)
                                .clickable(role = Role.Button, onClick = backAction)
                                .testTag("btn_header_back"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = palette.inkPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (archetype == ThemeLayoutArchetype.TELEMETRY_HUD) headerTitle.uppercase() else headerTitle,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = if (archetype == ThemeLayoutArchetype.TELEMETRY_HUD) 18.sp else 21.sp,
                                    fontFamily = titleFont,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.inkPrimary,
                                    letterSpacing = if (archetype == ThemeLayoutArchetype.TELEMETRY_HUD) 0.5.sp else (-0.4).sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = headerSubtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = palette.inkSecondary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right Action Button: Settings gear on sub-screens; Theme Studio palette on Settings (never a broken Moon icon)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(actionShape)
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, actionShape)
                            .clickable(
                                role = Role.Button,
                                onClick = if (isSubScreen) onToggleSettings else onOpenThemes
                            )
                            .testTag("btn_header_account"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSubScreen) Icons.Filled.Settings else Icons.Filled.Palette,
                            contentDescription = if (isSubScreen) "Settings" else "Themes",
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (archetype == ThemeLayoutArchetype.EDITORIAL_MONOGRAPH) {
                    // EDITORIAL MONOGRAPH: Symmetrical centered serif masthead with flanked ghost actions
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(palette.glassMedium.copy(alpha = 0.7f))
                            .border(
                                1.dp,
                                if (isNotifPinned) palette.accentMint.copy(alpha = 0.6f) else palette.cardHairlineBorder,
                                CircleShape
                            )
                            .clickable(role = Role.Button, onClick = onShowNotifications)
                            .testTag("btn_header_notifications"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isOverlayRunning || isNotifPinned) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                            contentDescription = "Notifications",
                            tint = if (isNotifPinned || isOverlayRunning) palette.accentMint else palette.inkPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Prism Solver",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 21.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary,
                                letterSpacing = 0.2.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isOverlayRunning) "LIVE OVERLAY" else themeSpec.name.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                letterSpacing = 1.6.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOverlayRunning) palette.accentMint else palette.inkSecondary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(palette.glassMedium.copy(alpha = 0.7f))
                            .border(1.dp, palette.cardHairlineBorder, CircleShape)
                            .clickable(role = Role.Button, onClick = onToggleSettings)
                            .testTag("btn_header_account"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                } else {
                    // Classic Prism, Atelier Bento, Telemetry HUD, and Kinetic Deck Headers
                    val isHud = archetype == ThemeLayoutArchetype.TELEMETRY_HUD
                    val isAtelier = archetype == ThemeLayoutArchetype.ATELIER_BENTO
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isKineticBar) 42.dp else 46.dp)
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(if (isHud) 8.dp else headerBtnCorner),
                                    ambientColor = Color.Black,
                                    spotColor = palette.accentSky.copy(alpha = 0.33f)
                                )
                                .clip(RoundedCornerShape(if (isHud) 8.dp else headerBtnCorner))
                                .background(palette.canvasTop)
                                .border(
                                    width = 1.dp,
                                    color = palette.accentSky.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(if (isHud) 8.dp else headerBtnCorner)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            RedPrismLogoMark(
                                modifier = Modifier.size(if (isKineticBar) 26.dp else 30.dp),
                                animated = true
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = when {
                                        isHud -> "PRISM // HUD"
                                        isAtelier -> "Prism Atelier"
                                        else -> "Prism Solver"
                                    },
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = if (isHud) 17.5.sp else 19.5.sp,
                                        fontFamily = if (isHud) FontFamily.Monospace else FontFamily.Default,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.inkPrimary,
                                        letterSpacing = if (isHud) 0.6.sp else (-0.3).sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isOverlayRunning) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(if (isHud) 4.dp else 50.dp))
                                            .background(palette.accentMintContainer)
                                            .border(1.dp, palette.accentMint.copy(alpha = 0.6f), RoundedCornerShape(if (isHud) 4.dp else 50.dp))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isHud) "[LIVE]" else "LIVE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.5.sp,
                                                fontFamily = if (isHud) FontFamily.Monospace else FontFamily.Default,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = palette.accentMint
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            Text(
                                text = when {
                                    isHud -> "${themeSpec.name.uppercase()} • READY"
                                    isAtelier -> if (themeSpec.name.contains(themeSpec.layoutArchetype.shortBadge, ignoreCase = true)) {
                                        themeSpec.name
                                    } else {
                                        "${themeSpec.layoutArchetype.shortBadge} • ${themeSpec.name}"
                                    }
                                    else -> "Screen & quiz study helper"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = if (isHud) 10.5.sp else 12.sp,
                                    fontFamily = if (isHud) FontFamily.Monospace else FontFamily.Default,
                                    fontWeight = FontWeight.Normal,
                                    color = palette.inkSecondary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right: Status Button + Settings Gear Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(actionShape)
                                .background(if (isKineticBar) palette.canvasTop else palette.glassStrong)
                                .border(
                                    1.dp,
                                    if (isNotifPinned) palette.accentMint.copy(alpha = 0.6f) else palette.cardHairlineBorder,
                                    actionShape
                                )
                                .clickable(role = Role.Button, onClick = onShowNotifications)
                                .testTag("btn_header_notifications"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isOverlayRunning || isNotifPinned) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                                contentDescription = "Notifications",
                                tint = if (isNotifPinned || isOverlayRunning) palette.accentMint else palette.inkPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Settings Button (preserving testTag("btn_header_account"))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(actionShape)
                                .background(if (isKineticBar) palette.canvasTop else palette.glassStrong)
                                .border(1.dp, palette.cardHairlineBorder, actionShape)
                                .clickable(role = Role.Button, onClick = onToggleSettings)
                                .testTag("btn_header_account"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = palette.inkPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showBottomHairline) {
            HorizontalDivider(
                color = if (archetype == ThemeLayoutArchetype.TELEMETRY_HUD) {
                    palette.accentSky.copy(alpha = 0.35f)
                } else {
                    palette.cardHairlineBorder
                }
            )
        }
    }
}

@Composable
private fun FairUseEnforcementDialog(
    enforcement: AbuseEnforcementState,
    onDismiss: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val isBanned = enforcement is AbuseEnforcementState.Banned
    val title = if (isBanned) {
        "Device Restricted"
    } else {
        "Fair-Use Warning (1 of 2)"
    }
    val reasonText = when (enforcement) {
        is AbuseEnforcementState.Warning -> enforcement.reason
        is AbuseEnforcementState.Banned -> enforcement.reason
        AbuseEnforcementState.Clear -> ""
    }
    val body = if (isBanned) {
        "Cloud solving has been disabled on this device due to repeated attempts to bypass daily limits ($reasonText). Saved study history and personal API keys in Settings still work."
    } else {
        "$reasonText. Daily limits are shared per device, so switching or recreating accounts will not add extra credits. Repeating this once more will restrict cloud solving on this device."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = false) {}
            .padding(24.dp)
            .testTag(if (isBanned) "dialog_device_banned" else "dialog_fair_use_warning"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(palette.canvasBottom)
                .border(
                    width = 1.4.dp,
                    color = palette.accentSky.copy(alpha = if (isBanned) 0.9f else 0.65f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(palette.accentSkyContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isBanned) Icons.Filled.Shield else Icons.Filled.Warning,
                        contentDescription = title,
                        tint = palette.accentSky,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Text(
                        text = if (isBanned) "Fair-use limit enforced" else "Final warning before device ban",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.accentSky,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = palette.inkSecondary,
                    lineHeight = 20.sp
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.accentSky)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .testTag("btn_dismiss_fair_use_dialog"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isBanned) "Understood" else "I Understand",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = palette.onAccentInk,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
