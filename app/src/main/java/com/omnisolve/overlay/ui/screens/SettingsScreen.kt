package com.omnisolve.overlay.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.HorizontalSplit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.BuildConfig
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.R
import com.omnisolve.overlay.ads.AdBannerStyle
import com.omnisolve.overlay.ads.InAppAdMobBanner
import com.omnisolve.overlay.data.DailyUsageSnapshot
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.service.OverlayService
import com.omnisolve.overlay.service.PrismQuickSettingsTileService
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.PracticeSample
import com.omnisolve.overlay.util.BatterySurvivalHelper
import com.omnisolve.overlay.ui.components.LivingOptionChoiceCard
import com.omnisolve.overlay.ui.components.LivingPrimaryButton
import com.omnisolve.overlay.ui.components.LivingSecondaryButton
import com.omnisolve.overlay.ui.components.LivingSwitchRow
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.NORMAL_DEFAULT_THEME
import com.omnisolve.overlay.ui.theme.RemoteThemeSpec
import com.omnisolve.overlay.ui.theme.SIGNATURE_ATELIER_THEMES
import com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype
import com.omnisolve.overlay.ui.theme.parseHexColorOr
import com.omnisolve.overlay.util.AppShareHelper
import com.omnisolve.overlay.util.PlayUpdateUiState
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: MainUiState,
    sample: PracticeSample,
    listState: LazyListState,
    onSaveApiKeys: (nvidia: String, openRouter: String, gemini: String, openAi: String) -> Unit,
    onTestApiKeys: (nvidia: String, openRouter: String, gemini: String, openAi: String) -> Unit,
    onClearApiKeys: () -> Unit,
    onToggleAutoFailover: (Boolean) -> Unit,
    onToggleChargingCamo: (Boolean) -> Unit,
    onSignInGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onDeleteGoogleAccount: () -> Unit = {},
    onOpenGeminiWebLogin: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onSelectHudStyle: (String) -> Unit,
    onSetCustomOpacityPct: (Int) -> Unit,
    onToggleDfTwoPartCapture: (Boolean) -> Unit,
    onToggleDfScrollTimer: (Boolean) -> Unit,
    onToggleDfComplexMode: (Boolean) -> Unit,
    onToggleDfDiagramVision: (Boolean) -> Unit,
    onToggleDfQuickCrop: (Boolean) -> Unit,
    onToggleDfSwipeMethodLoop: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onTestVibration: () -> Unit,
    onToggleInstantDetect: (Boolean) -> Unit,
    onSolvePractice: () -> Unit,
    onNextPractice: () -> Unit,
    onClearHistory: () -> Unit,
    onSelectSolverMethod: (SolverMethod) -> Unit,
    onToggleAdvCropToScan: (Boolean) -> Unit,
    onToggleAdvIslandGestures: (Boolean) -> Unit,
    onToggleAdvSplitPill: (Boolean) -> Unit,
    onToggleAdvMinimalShapes: (Boolean) -> Unit,
    onToggleAdvNetSpeedDisguise: (Boolean) -> Unit,
    onToggleAdvAutoFadeIdle: (Boolean) -> Unit,
    onToggleAdvFlashcardsExport: (Boolean) -> Unit,
    onToggleAdvDarkObsidian: (Boolean) -> Unit,
    onToggleAdvSpringPhysics: (Boolean) -> Unit,
    onUpdateSpringPhysics: (damping: Float, stiffness: Float) -> Unit,
    onToggleAdvSplitScreenRail: (Boolean) -> Unit,
    onToggleAdvQuickTileNotif: (Boolean) -> Unit,
    onToggleDualVerify: (Boolean) -> Unit,
    onToggleFluidAnimations: (Boolean) -> Unit = {},
    onFlashcardSearchChange: (String) -> Unit,
    onFlashcardSubjectFilterChange: (String) -> Unit,
    onToggleFlashcardBookmarkedOnly: (Boolean) -> Unit,
    onToggleBookmarkEntry: (Long) -> Unit,
    onShowBannerMessage: (String) -> Unit,
    onOpenNotificationStudio: () -> Unit = {},
    onReplayOnboardingGuide: () -> Unit = {},
    onOpenThemeStudio: () -> Unit = {},
    onSelectLocalThemePreset: (RemoteThemeSpec) -> Unit = {},
    onRevertToNormalDefaultTheme: () -> Unit = {},
    onClearOfflineRoomCache: () -> Unit = {},
    onOpenHistoryVault: () -> Unit = {},
    playUpdateState: PlayUpdateUiState = PlayUpdateUiState.Idle,
    onCheckForPlayUpdates: () -> Unit = {},
    onInstallReadyUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val context = LocalContext.current
    val hasOverlayPermission = try {
        Settings.canDrawOverlays(context)
    } catch (_: Exception) {
        state.isOverlayRunning
    }
    val isGoogleSignedIn = !state.firebaseUserId.isNullOrBlank()
    val isOverlappingActive = state.solverMethod == SolverMethod.OVERLAPPING_GOOGLE

    var expandedAccount by rememberSaveable { mutableStateOf(true) }
    var expandedPermissions by rememberSaveable { mutableStateOf(true) }
    var expandedAnswerLook by rememberSaveable { mutableStateOf(false) }
    var expandedHelpPrivacy by rememberSaveable { mutableStateOf(false) }
    var expandedAdvanced by rememberSaveable { mutableStateOf(false) }

    var nvidiaInput by rememberSaveable(state.nvidiaApiKey) { mutableStateOf(state.nvidiaApiKey) }
    var openRouterInput by rememberSaveable(state.openRouterApiKey) { mutableStateOf(state.openRouterApiKey) }
    var geminiInput by rememberSaveable(state.geminiApiKey) { mutableStateOf(state.geminiApiKey) }
    var openAiInput by rememberSaveable(state.openAiApiKey) { mutableStateOf(state.openAiApiKey) }
    var showKeysPlaintext by rememberSaveable { mutableStateOf(false) }
    var showPrivacyPolicyModal by rememberSaveable { mutableStateOf(false) }
    var showDeleteAccountDialog by rememberSaveable { mutableStateOf(false) }

    if (showPrivacyPolicyModal) {
        PrivacyPolicyModalDialog(
            onDismiss = { showPrivacyPolicyModal = false },
            onClearSolvedHistory = onClearHistory,
            onClearLocalApiKeys = onClearApiKeys
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isDeletingAccount) showDeleteAccountDialog = false
            },
            containerColor = palette.glassStrong,
            titleContentColor = palette.inkPrimary,
            textContentColor = palette.inkSecondary,
            title = {
                Text(
                    text = "Delete Account & Saved History?",
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                )
            },
            text = {
                Text(
                    text = "This permanently removes your saved cloud history and disconnects your Google account from Prism Solver. You can sign in again anytime.",
                    color = palette.inkSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteGoogleAccount()
                    },
                    enabled = !state.isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentSky,
                        contentColor = palette.onAccentInk
                    ),
                    modifier = Modifier.testTag("btn_confirm_delete_account")
                ) {
                    Text(
                        text = if (state.isDeletingAccount) "Deleting..." else "Delete Permanently",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteAccountDialog = false },
                    enabled = !state.isDeletingAccount,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.inkPrimary),
                    modifier = Modifier.testTag("btn_cancel_delete_account")
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("dialog_delete_google_account")
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("settings_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 0. SIMPLE QUICK STATUS CARD ────────────────────────────────────
        item(key = "settings_overview_card") {
            SystemReadinessHeroCard(
                hasOverlayPermission = hasOverlayPermission,
                isGoogleSignedIn = isGoogleSignedIn,
                activeMethod = state.solverMethod.title,
                onFixNext = {
                    when {
                        !hasOverlayPermission -> onRequestOverlayPermission()
                        !isGoogleSignedIn -> onSignInGoogle()
                        else -> onShowBannerMessage("Everything is ready to solve")
                    }
                }
            )
        }

        // ── 0B. LIVE THEME & COMPLETE LAYOUT REARRANGEMENT SWITCHER ────────
        item(key = "settings_theme_studio_control") {
            SettingsThemeControlCard(
                activeSpec = state.effectiveThemeSpec,
                preferLocalDefault = state.preferLocalDefaultTheme,
                onOpenThemeStudio = onOpenThemeStudio,
                onSelectLocalThemePreset = onSelectLocalThemePreset,
                onRevertToNormalDefault = onRevertToNormalDefaultTheme
            )
        }

        // ── 0C. SPONSORED BANNER CARD (IN-APP SETTINGS SLOT) ───────────────
        item(key = "settings_inline_ad_slot") {
            InAppAdMobBanner(
                placementTag = "settings_inline_card",
                style = AdBannerStyle.INLINE_CARD
            )
        }

        // ── 1. ACCOUNT & FORM BROWSER LOGIN ────────────────────────────────
        item(key = "local_api_keys_section") {
            SettingsAccordionCard(
                icon = Icons.Filled.AccountCircle,
                title = "Account",
                subtitle = if (isGoogleSignedIn) {
                    state.firebaseUserEmail ?: "Signed in"
                } else {
                    "App account & school form login"
                },
                statusBadge = if (isGoogleSignedIn) "Signed In" else "Sign In Needed",
                isExpanded = expandedAccount,
                onToggleExpand = { expandedAccount = !expandedAccount }
            ) {
                Text(
                    text = "App account saves your history and unlocks Google Forms solving. Private school or college forms may also ask you to sign in inside the form browser.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = palette.inkSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                )

                if (isGoogleSignedIn) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.accentEmeraldContainer)
                            .border(1.dp, palette.accentEmerald.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            tint = palette.accentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.firebaseUserName?.takeIf { it.isNotBlank() } ?: "Google Account Connected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary
                            )
                            Text(
                                text = state.firebaseUserEmail ?: "Cloud account active",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkSecondary
                            )
                        }
                        LivingSecondaryButton(
                            text = "Sign Out",
                            onClick = onSignOutGoogle,
                            testTag = "btn_google_logout",
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    DailyAllowanceCompactCard(snapshot = state.dailyUsage)

                    LivingSecondaryButton(
                        text = if (state.isDeletingAccount) "Deleting Account & History..." else "Delete Account & Cloud Data",
                        icon = Icons.Filled.DeleteOutline,
                        onClick = {
                            if (!state.isDeletingAccount) showDeleteAccountDialog = true
                        },
                        testTag = "btn_delete_google_account",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (state.isSigningInGoogle) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = palette.accentSky
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        LivingPrimaryButton(
                            text = if (state.isSigningInGoogle) "Signing in..." else "Sign in with Google",
                            icon = Icons.Filled.AccountCircle,
                            onClick = { if (!state.isSigningInGoogle) onSignInGoogle() },
                            testTag = "btn_google_login",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                LivingSecondaryButton(
                    text = if (state.isGoogleWebLoggedIn) {
                        "Form Browser Account: Signed In"
                    } else {
                        "Optional: Sign In Inside Form Browser"
                    },
                    icon = Icons.Filled.AutoAwesome,
                    selected = state.isGoogleWebLoggedIn,
                    onClick = onOpenGeminiWebLogin,
                    testTag = "btn_gemini_web_session",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── 2. PERMISSIONS & NOTIFICATIONS ─────────────────────────────────
        item(key = "settings_permissions_section") {
            val readyCount = listOf(hasOverlayPermission, state.hasNotificationPermission).count { it }
            SettingsAccordionCard(
                icon = Icons.Filled.Layers,
                title = "Permissions & Alerts",
                subtitle = "Allow floating helper & answer bar",
                statusBadge = "$readyCount/2 Ready",
                isExpanded = expandedPermissions,
                onToggleExpand = { expandedPermissions = !expandedPermissions }
            ) {
                SettingsStatusActionRow(
                    title = "Display Over Other Apps",
                    subtitle = if (hasOverlayPermission) {
                        "Allowed • We only read your screen when you tap the pill"
                    } else {
                        "Needed to show the floating pill above your quiz app"
                    },
                    statusText = if (hasOverlayPermission) "Allowed" else "Allow",
                    isReady = hasOverlayPermission,
                    icon = Icons.Filled.Layers,
                    testTag = "btn_settings_perm_overlay",
                    onClick = onRequestOverlayPermission
                )

                SettingsStatusActionRow(
                    title = "Pinned Answer Notification",
                    subtitle = if (state.customPinConfig.isPinned) {
                        "Pinned live • Tap to edit or unpin bar"
                    } else {
                        "Keep form answers handy in your top notification shade"
                    },
                    statusText = if (state.customPinConfig.isPinned) "Pinned" else "Open",
                    isReady = state.customPinConfig.isPinned,
                    icon = Icons.Filled.NotificationsActive,
                    testTag = "btn_settings_open_notification_studio",
                    onClick = onOpenNotificationStudio
                )

                SettingsStatusActionRow(
                    title = "Quick Settings Tile",
                    subtitle = "Swipe down from any app to start or trigger Prism Pill in 1 tap",
                    statusText = "Add Tile",
                    isReady = false,
                    icon = Icons.Filled.Bolt,
                    testTag = "btn_settings_add_qs_tile",
                    onClick = {
                        PrismQuickSettingsTileService.requestAddTileToShade(context) { message ->
                            onShowBannerMessage(message)
                        }
                    }
                )

                LivingSwitchRow(
                    title = "Quick Notification Controls",
                    subtitle = "Shows Solve & Stop buttons in your notification bar while active.",
                    checked = state.advQuickTileNotifEnabled,
                    onCheckedChange = onToggleAdvQuickTileNotif,
                    icon = Icons.Filled.NotificationsActive,
                    testTag = "switch_adv_quick_tile_notif"
                )
            }
        }

        // ── 3. ANSWER LOOK & VIBRATION ──────────────────────────────────────
        item(key = "answer_format_section") {
            SettingsAccordionCard(
                icon = Icons.Filled.ViewStream,
                title = "Answer Look & Sound",
                subtitle = "A/B/C/D vs Charging %, transparency & vibration",
                statusBadge = if (state.batteryCamouflage) "Charging %" else "A / B / C / D",
                isExpanded = expandedAnswerLook,
                onToggleExpand = { expandedAnswerLook = !expandedAnswerLook }
            ) {
                LivingOptionChoiceCard(
                    title = stringResource(R.string.format_abcd_title),
                    subtitle = stringResource(R.string.format_abcd_desc),
                    selected = !state.batteryCamouflage,
                    icon = Icons.Filled.FontDownload,
                    badgeText = "A B C D",
                    onClick = { onToggleChargingCamo(false) },
                    testTag = "option_format_abcd"
                )

                LivingOptionChoiceCard(
                    title = stringResource(R.string.format_charging_title),
                    subtitle = stringResource(R.string.format_charging_desc),
                    selected = state.batteryCamouflage,
                    icon = Icons.Filled.BatteryChargingFull,
                    badgeText = "91–94%",
                    onClick = { onToggleChargingCamo(true) },
                    testTag = "option_format_charging"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pill Transparency",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.inkPrimary
                    )
                    Text(
                        text = "${state.customOpacityPct}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentSky
                    )
                }
                Slider(
                    value = state.customOpacityPct.toFloat(),
                    onValueChange = { onSetCustomOpacityPct(it.roundToInt()) },
                    valueRange = 20f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accentSky,
                        activeTrackColor = palette.accentSky,
                        inactiveTrackColor = palette.cardHairlineBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_settings_opacity_pct")
                )

                LivingOptionChoiceCard(
                    title = stringResource(R.string.style_island_title),
                    subtitle = stringResource(R.string.style_island_desc),
                    selected = state.hudStyle == MainActivity.HUD_STYLE_ISLAND,
                    icon = Icons.Filled.ViewStream,
                    badgeText = "Normal Pill",
                    onClick = { onSelectHudStyle(MainActivity.HUD_STYLE_ISLAND) },
                    testTag = "option_hud_island"
                )

                LivingOptionChoiceCard(
                    title = stringResource(R.string.style_bubble_title),
                    subtitle = stringResource(R.string.style_bubble_desc),
                    selected = state.hudStyle == MainActivity.HUD_STYLE_BUBBLE,
                    icon = Icons.Filled.BubbleChart,
                    badgeText = "Round Bubble",
                    onClick = { onSelectHudStyle(MainActivity.HUD_STYLE_BUBBLE) },
                    testTag = "option_hud_bubble"
                )

                HorizontalDivider(color = palette.cardHairlineBorder)

                LivingSwitchRow(
                    title = stringResource(R.string.haptic_title),
                    subtitle = stringResource(R.string.haptic_desc),
                    checked = state.hapticEnabled,
                    onCheckedChange = onToggleHaptics,
                    testTag = "switch_haptics",
                    icon = Icons.Filled.Vibration
                )
                LivingSecondaryButton(
                    text = stringResource(R.string.btn_test_vibration),
                    icon = Icons.Filled.Vibration,
                    onClick = onTestVibration,
                    testTag = "btn_test_vibration",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── 4. UPDATES, OFFLINE CACHE, HELP & PRIVACY ──────────────────────
        item(key = "settings_updates_and_share_card") {
            val updateStatusText = when (playUpdateState) {
                PlayUpdateUiState.Checking -> "Checking..."
                is PlayUpdateUiState.Available -> "Update Ready"
                is PlayUpdateUiState.Downloading -> "${playUpdateState.progressPct}%"
                PlayUpdateUiState.ReadyToInstall -> "Restart Now"
                PlayUpdateUiState.UpToDate -> "Up to Date"
                is PlayUpdateUiState.Unavailable -> "Play Store"
                PlayUpdateUiState.Idle -> "Check"
            }
            val updateSubtitle = when (playUpdateState) {
                PlayUpdateUiState.Checking -> "Checking Google Play Store for a newer release..."
                is PlayUpdateUiState.Available -> "A newer Prism Solver release is available on Google Play"
                is PlayUpdateUiState.Downloading -> "Downloading update in background (${playUpdateState.progressPct}%)"
                PlayUpdateUiState.ReadyToInstall -> "Update downloaded. Tap to restart and install."
                PlayUpdateUiState.UpToDate -> "v${BuildConfig.VERSION_NAME} • You have the latest version"
                is PlayUpdateUiState.Unavailable -> "v${BuildConfig.VERSION_NAME} • Tap to check on Google Play"
                PlayUpdateUiState.Idle -> "v${BuildConfig.VERSION_NAME} • Check Google Play for improvements"
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
                    .testTag("card_settings_updates_and_share"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsStatusActionRow(
                    title = "Play Store App Updates",
                    subtitle = updateSubtitle,
                    statusText = updateStatusText,
                    isReady = playUpdateState is PlayUpdateUiState.UpToDate || playUpdateState is PlayUpdateUiState.ReadyToInstall,
                    icon = Icons.Filled.SystemUpdate,
                    testTag = "btn_check_play_updates",
                    onClick = {
                        if (playUpdateState is PlayUpdateUiState.ReadyToInstall) {
                            onInstallReadyUpdate()
                        } else {
                            onCheckForPlayUpdates()
                        }
                    }
                )

                SettingsStatusActionRow(
                    title = "Offline Room Cache (${maxOf(state.offlineCacheCount, state.historyEntries.size)} Saved)",
                    subtitle = "${state.offlineCacheSavedCalls} AI calls saved • View history offline anytime",
                    statusText = "Open",
                    isReady = true,
                    icon = Icons.Filled.OfflineBolt,
                    testTag = "btn_settings_open_offline_cache",
                    onClick = onOpenHistoryVault
                )

                LivingSecondaryButton(
                    text = "Share Prism Solver with Friends",
                    icon = Icons.Filled.Share,
                    onClick = { AppShareHelper.shareAppWithFriends(context) },
                    testTag = "btn_share_with_friends",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── 4B. KEEP DYNAMIC ISLAND ALIVE (BATTERY SAVER & OEM GUARD) ───────
        item(key = "settings_keep_island_alive_card") {
            val batteryStatus = remember(state.isOverlayRunning) { BatterySurvivalHelper.inspect(context) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.glassStrong)
                    .border(
                        width = 1.dp,
                        color = if (batteryStatus.isProtected) {
                            palette.accentEmerald.copy(alpha = 0.45f)
                        } else {
                            palette.warningAmber.copy(alpha = 0.55f)
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(16.dp)
                    .testTag("card_settings_battery_keep_alive"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (batteryStatus.isProtected) palette.accentEmeraldContainer
                                    else palette.warningContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (batteryStatus.isProtected) Icons.Filled.Shield else Icons.Filled.BatteryChargingFull,
                                contentDescription = null,
                                tint = if (batteryStatus.isProtected) palette.accentEmerald else palette.warningAmber,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Keep Dynamic Island Alive",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "${batteryStatus.oemDisplayName} • ${batteryStatus.shortBadge}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (batteryStatus.isProtected) palette.accentEmerald else palette.warningAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Text(
                    text = batteryStatus.oemStepsHint,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = palette.inkSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LivingSecondaryButton(
                        text = if (batteryStatus.isProtected) "Battery Settings" else "Disable Battery Opt.",
                        icon = Icons.Filled.BatteryChargingFull,
                        onClick = {
                            BatterySurvivalHelper.openUnrestrictedBatterySettings(context)
                            onShowBannerMessage("Set Battery → Unrestricted so Dynamic Island stays instant.")
                        },
                        testTag = "btn_disable_battery_optimization",
                        modifier = Modifier.weight(1f)
                    )
                    if (state.isOverlayRunning) {
                        LivingSecondaryButton(
                            text = "Stop Island",
                            icon = Icons.Filled.Stop,
                            onClick = {
                                try {
                                    context.stopService(Intent(context, OverlayService::class.java))
                                    onShowBannerMessage("Dynamic Island stopped.")
                                } catch (_: Exception) {
                                }
                            },
                            testTag = "btn_settings_stop_island",
                            modifier = Modifier.weight(0.85f)
                        )
                    }
                }
            }
        }

        item(key = "settings_help_privacy_section") {
            SettingsAccordionCard(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = "Help, History & Privacy",
                subtitle = "5-second tour, saved offline cache & privacy policy",
                statusBadge = "${state.historyEntries.size} Saved",
                isExpanded = expandedHelpPrivacy,
                onToggleExpand = { expandedHelpPrivacy = !expandedHelpPrivacy },
                modifier = Modifier.testTag("section_advanced_system_options")
            ) {
                SettingsStatusActionRow(
                    title = "Replay Quick Interactive Tour",
                    subtitle = "Try the 5-second live MCQ & Math demo again",
                    statusText = "Replay",
                    isReady = true,
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    testTag = "btn_replay_feature_guide",
                    onClick = onReplayOnboardingGuide
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LivingSecondaryButton(
                        text = "Clear History (${state.historyEntries.size})",
                        icon = Icons.Filled.DeleteOutline,
                        onClick = {
                            onClearHistory()
                            onClearOfflineRoomCache()
                            onShowBannerMessage("Cleared local history & offline cache")
                        },
                        testTag = "btn_clear_history",
                        modifier = Modifier.weight(1f)
                    )
                    LivingSecondaryButton(
                        text = "Privacy Policy",
                        icon = Icons.Filled.VerifiedUser,
                        onClick = { showPrivacyPolicyModal = true },
                        testTag = "btn_open_public_privacy_policy",
                        modifier = Modifier.weight(1f)
                    )
                }

                LivingSecondaryButton(
                    text = "Open Web Privacy Policy",
                    icon = Icons.Filled.OpenInFull,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.URL_PRIVACY_POLICY_WEB))
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            onShowBannerMessage("Could not open browser. Check your browser app or tap Privacy Policy above.")
                        }
                    },
                    testTag = "btn_open_web_policy",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── 5. ADVANCED & BRING YOUR OWN KEY (COLLAPSED BY DEFAULT) ────────
        item(key = "advanced_byok_and_power_controls") {
            SettingsAccordionCard(
                icon = Icons.Filled.Tune,
                title = "Advanced & Bring Your Own Key",
                subtitle = "Optional personal API keys, extra gestures, flashcards & physics",
                statusBadge = if (state.configuredKeyCount > 0) "${state.configuredKeyCount} Key(s)" else "Optional",
                isExpanded = expandedAdvanced,
                onToggleExpand = { expandedAdvanced = !expandedAdvanced },
                modifier = Modifier.testTag("btn_toggle_advanced_settings")
            ) {
                // Personal Keys Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Personal API Keys (${state.configuredKeyCount} Saved)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Optional. Leave empty to use built-in solving.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = palette.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    LivingSecondaryButton(
                        text = if (showKeysPlaintext) "Hide" else "Show",
                        icon = if (showKeysPlaintext) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        onClick = { showKeysPlaintext = !showKeysPlaintext },
                        testTag = "btn_toggle_show_keys",
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                LocalApiKeyField("NVIDIA NIM Key", "nvapi-...", "OPTIONAL", nvidiaInput, { nvidiaInput = it }, showKeysPlaintext, "input_nvidia_key")
                LocalApiKeyField("OpenRouter Key", "sk-or-v1-...", "OPTIONAL", openRouterInput, { openRouterInput = it }, showKeysPlaintext, "input_openrouter_key")
                LocalApiKeyField("Google Gemini Key", "AIzaSy...", "OPTIONAL", geminiInput, { geminiInput = it }, showKeysPlaintext, "input_gemini_key")
                LocalApiKeyField("OpenAI Key", "sk-proj-...", "OPTIONAL", openAiInput, { openAiInput = it }, showKeysPlaintext, "input_openai_key")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LivingPrimaryButton(
                        text = "Save Keys (${state.configuredKeyCount})",
                        icon = Icons.Filled.Key,
                        onClick = { onSaveApiKeys(nvidiaInput, openRouterInput, geminiInput, openAiInput) },
                        testTag = "btn_save_api_keys",
                        modifier = Modifier.weight(1f)
                    )
                    LivingSecondaryButton(
                        text = if (state.isTestingApiKeys) "Testing..." else "Test",
                        icon = Icons.Filled.Bolt,
                        onClick = { onTestApiKeys(nvidiaInput, openRouterInput, geminiInput, openAiInput) },
                        testTag = "btn_test_api_keys"
                    )
                    if (state.configuredKeyCount > 0 || nvidiaInput.isNotBlank() || openRouterInput.isNotBlank() || geminiInput.isNotBlank() || openAiInput.isNotBlank()) {
                        LivingSecondaryButton(
                            text = "Clear",
                            icon = Icons.Filled.DeleteOutline,
                            onClick = {
                                nvidiaInput = ""
                                openRouterInput = ""
                                geminiInput = ""
                                openAiInput = ""
                                onClearApiKeys()
                            },
                            testTag = "btn_clear_api_keys"
                        )
                    }
                }

                if (!state.apiKeysTestSummary.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.glassTintedSky)
                            .border(1.dp, palette.accentSky.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("txt_api_keys_test_summary")
                    ) {
                        Text(
                            text = state.apiKeysTestSummary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.inkPrimary
                        )
                    }
                }

                LivingSwitchRow(
                    title = "Automatic Key Backup Rotation",
                    subtitle = "Switches to your next saved key if one runs out of free quota.",
                    checked = state.autoFailoverEnabled,
                    onCheckedChange = onToggleAutoFailover,
                    icon = Icons.Filled.AutoAwesome,
                    testTag = "switch_auto_failover"
                )

                LivingSwitchRow(
                    title = "Double-Check Answers",
                    subtitle = "Runs a second check for tough questions.",
                    checked = state.dualVerifyEnabled,
                    onCheckedChange = onToggleDualVerify,
                    icon = Icons.Filled.VerifiedUser,
                    testTag = "switch_adv_dual_verify"
                )

                HorizontalDivider(color = palette.cardHairlineBorder)

                // Extra Pill Shapes & Disguises
                LivingSwitchRow(
                    title = "Extra Pill Shapes (Small Strip & Edge Tab)",
                    subtitle = "Unlock slim top strip and side tab shapes.",
                    checked = state.advMinimalShapesEnabled,
                    onCheckedChange = onToggleAdvMinimalShapes,
                    icon = Icons.Filled.SpaceDashboard,
                    testTag = "switch_adv_minimal_shapes"
                )
                if (state.advMinimalShapesEnabled) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LivingSecondaryButton(
                            text = "Top Strip",
                            selected = state.hudStyle == MainActivity.HUD_STYLE_TOP_STRIP,
                            onClick = { onSelectHudStyle(MainActivity.HUD_STYLE_TOP_STRIP) },
                            testTag = "btn_shape_top_strip",
                            modifier = Modifier.weight(1f)
                        )
                        LivingSecondaryButton(
                            text = "Edge Tab",
                            selected = state.hudStyle == MainActivity.HUD_STYLE_EDGE_TAB,
                            onClick = { onSelectHudStyle(MainActivity.HUD_STYLE_EDGE_TAB) },
                            testTag = "btn_shape_edge_tab",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                LivingSwitchRow(
                    title = "Show Answer + Key Word",
                    subtitle = "Shows the letter alongside a short clue on the pill.",
                    checked = state.advSplitPillEnabled,
                    onCheckedChange = onToggleAdvSplitPill,
                    icon = Icons.Filled.HorizontalSplit,
                    testTag = "switch_adv_split_pill"
                )
                LivingSwitchRow(
                    title = "Speed Meter Disguise (0.91–0.94 MB/s)",
                    subtitle = "Shows answers as a tiny network speed badge.",
                    checked = state.advNetSpeedDisguiseEnabled,
                    onCheckedChange = onToggleAdvNetSpeedDisguise,
                    icon = Icons.Filled.Speed,
                    testTag = "switch_adv_net_speed"
                )
                LivingSwitchRow(
                    title = "Dim Pill When Idle",
                    subtitle = "Fades the floating pill gently when not in use.",
                    checked = state.advAutoFadeIdleEnabled,
                    onCheckedChange = onToggleAdvAutoFadeIdle,
                    icon = Icons.Filled.Timer,
                    testTag = "switch_adv_auto_fade"
                )

                HorizontalDivider(color = palette.cardHairlineBorder)

                // Pill Chip & Gesture Controls
                LivingSwitchRow("Two-Part Capture", "Capture top of question first, scroll, then tap Part 2.", state.dfTwoPartCaptureEnabled, onToggleDfTwoPartCapture, "switch_df_two_part", Icons.Filled.HorizontalSplit)
                LivingSwitchRow("2-Second Scroll Timer", "Waits 2 seconds so you can scroll to choices.", state.dfScrollTimerEnabled, onToggleDfScrollTimer, "switch_df_scroll_timer", Icons.Filled.Timer)
                LivingSwitchRow("Question Type Chip", "Switch Quick MCQ / Match / Math inside the pill.", state.dfComplexModeEnabled, onToggleDfComplexMode, "switch_df_complex_mode", Icons.Filled.Calculate)
                LivingSwitchRow("Diagram Vision Boost", "Helps with charts, figures, and geometry images.", state.dfDiagramVisionEnabled, onToggleDfDiagramVision, "switch_df_diagram_vision", Icons.Filled.Image)
                LivingSwitchRow("Quick Crop Box", "Draw a box around one question on screen.", state.dfQuickCropEnabled, onToggleDfQuickCrop, "switch_df_quick_crop", Icons.Filled.CropFree)
                LivingSwitchRow("Crop-to-Scan Selector", "Manually frame any graph or formula.", state.advCropToScanEnabled, onToggleAdvCropToScan, "switch_adv_crop_scan", Icons.Filled.CropFree)
                LivingSwitchRow("Swipe to Switch Style", "Swipe down on the pill to change solving styles.", state.dfSwipeMethodLoopEnabled, onToggleDfSwipeMethodLoop, "switch_df_swipe_method_loop", Icons.Filled.SwapVert)
                LivingSwitchRow("Swipe Past Answers", "Swipe left or right on the pill for recent answers.", state.advIslandGesturesEnabled, onToggleAdvIslandGestures, "switch_adv_island_gestures", Icons.Filled.Swipe)

                HorizontalDivider(color = palette.cardHairlineBorder)

                // Triggers, Window & Display
                LivingSwitchRow(stringResource(R.string.instant_detect_title), stringResource(R.string.instant_detect_desc), state.instantTriggerEnabled, onToggleInstantDetect, "switch_instant_detect", Icons.Filled.AutoAwesome)
                LivingOptionChoiceCard(
                    title = stringResource(R.string.overlapping_google_title),
                    subtitle = stringResource(R.string.overlapping_google_desc),
                    selected = isOverlappingActive,
                    icon = Icons.Filled.OpenInFull,
                    badgeText = if (isOverlappingActive) "ACTIVE" else "WINDOW",
                    onClick = {
                        onSelectSolverMethod(
                            if (isOverlappingActive) SolverMethod.BACKGROUND_GEMINI else SolverMethod.OVERLAPPING_GOOGLE
                        )
                    },
                    testTag = "option_method_overlapping_google"
                )
                LivingSwitchRow(
                    title = "Enable Floating AI Browser",
                    subtitle = "Opens answers in a draggable floating web card.",
                    checked = isOverlappingActive,
                    onCheckedChange = { enabled ->
                        onSelectSolverMethod(
                            if (enabled) SolverMethod.OVERLAPPING_GOOGLE else SolverMethod.BACKGROUND_GEMINI
                        )
                    },
                    icon = Icons.Filled.OpenInFull,
                    testTag = "switch_overlapping_google"
                )

                LivingSwitchRow("Deep Dark Background", "Uses pure dark surface for maximum contrast.", state.advDarkObsidianEnabled, onToggleAdvDarkObsidian, "switch_adv_dark_obsidian", Icons.Filled.DarkMode)
                LivingSwitchRow("Tablet Side Navigation Rail", "Shows side navigation bar on wide screens.", state.advSplitScreenRailEnabled, onToggleAdvSplitScreenRail, "switch_adv_split_rail", Icons.AutoMirrored.Filled.ViewSidebar)
                LivingSwitchRow(
                    title = "Smooth Motion Effects",
                    subtitle = "Turn off for simpler transitions and extra battery savings.",
                    checked = state.fluidAnimationsEnabled,
                    onCheckedChange = onToggleFluidAnimations,
                    testTag = "switch_fluid_animations",
                    icon = Icons.Filled.AutoAwesome
                )
                LivingSwitchRow("Custom Pill Bounce Speed", "Fine-tune how fast the pill opens and closes.", state.advSpringPhysicsEnabled, onToggleAdvSpringPhysics, "switch_adv_spring_physics", Icons.Filled.Tune)
                if (state.advSpringPhysicsEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("panel_spring_sliders")
                    ) {
                        Text("Bounce Smoothness: ${"%.2f".format(state.springDamping)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = palette.inkPrimary)
                        Slider(
                            value = state.springDamping,
                            onValueChange = { d -> onUpdateSpringPhysics(d, state.springStiffness) },
                            valueRange = 0.35f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.accentSky,
                                activeTrackColor = palette.accentSky,
                                inactiveTrackColor = palette.cardHairlineBorder
                            ),
                            modifier = Modifier.testTag("slider_spring_damping")
                        )
                        Text("Snap Speed: ${state.springStiffness.roundToInt()}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = palette.inkPrimary)
                        Slider(
                            value = state.springStiffness,
                            onValueChange = { s -> onUpdateSpringPhysics(state.springDamping, s) },
                            valueRange = 200f..1500f,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.accentSky,
                                activeTrackColor = palette.accentSky,
                                inactiveTrackColor = palette.cardHairlineBorder
                            ),
                            modifier = Modifier.testTag("slider_spring_stiffness")
                        )
                    }
                }
            }
        }

        // ── 6. CLEAN BUILD SPEC FOOTER ──────────────────────────────────────
        item(key = "settings_build_footer") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(14.dp))
                    .padding(vertical = 10.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Prism Solver • ${themeSpec.name} • Private On-Device Reading",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = palette.inkSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp
                    )
                )
            }
        }
    }
}

// ── APPEARANCE & THEME STUDIO CARD ──────────────────────────────────────────

@Composable
private fun SettingsThemeControlCard(
    activeSpec: RemoteThemeSpec,
    preferLocalDefault: Boolean,
    onOpenThemeStudio: () -> Unit,
    onSelectLocalThemePreset: (RemoteThemeSpec) -> Unit,
    onRevertToNormalDefault: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val haptics = LocalHapticFeedback.current
    val archetype = themeSpec.layoutArchetype
    val cardCorner = when (archetype) {
        ThemeLayoutArchetype.TELEMETRY_HUD -> 10.dp
        ThemeLayoutArchetype.ATELIER_BENTO -> 16.dp
        ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> 18.dp
        ThemeLayoutArchetype.KINETIC_DECK -> 18.dp
        ThemeLayoutArchetype.IOS_LIQUID_LIGHT -> 24.dp
        ThemeLayoutArchetype.CLASSIC_PRISM -> themeSpec.cornerRadiusDp.coerceIn(8, 28).dp
    }
    val accentColor = parseHexColorOr(activeSpec.accentPrimaryHex, palette.accentSky)
    val swatchColors = listOf(
        parseHexColorOr(activeSpec.canvasTopHex, palette.canvasTop),
        parseHexColorOr(activeSpec.surfacePrimaryHex, palette.glassStrong),
        accentColor,
        parseHexColorOr(activeSpec.inkPrimaryHex, palette.inkPrimary)
    )
    val quickThemes = remember {
        listOf(NORMAL_DEFAULT_THEME, com.omnisolve.overlay.ui.theme.IOS_LIQUID_LIGHT_THEME) + SIGNATURE_ATELIER_THEMES
    }
    val useMono = archetype == ThemeLayoutArchetype.TELEMETRY_HUD
    val useSerif = archetype == ThemeLayoutArchetype.EDITORIAL_MONOGRAPH

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(1.dp, palette.accentSky.copy(alpha = 0.48f), RoundedCornerShape(cardCorner))
            .padding(16.dp)
            .testTag("card_settings_theme_control"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(if (useMono) 8.dp else 12.dp))
                        .background(palette.glassTintedSky)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(if (useMono) 8.dp else 12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = null,
                        tint = palette.accentSky,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (useMono) "THEMES & LAYOUTS" else "Themes & App Layout",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.5.sp,
                            fontFamily = when {
                                useMono -> FontFamily.Monospace
                                useSerif -> FontFamily.Serif
                                else -> FontFamily.Default
                            },
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (activeSpec.name.contains(activeSpec.layoutArchetype.shortBadge, ignoreCase = true)) {
                            activeSpec.name
                        } else {
                            "${activeSpec.name} • ${activeSpec.layoutArchetype.shortBadge}"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            color = palette.inkSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                swatchColors.forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(1.dp, palette.cardHairlineBorder, CircleShape)
                    )
                }
            }
        }

        // Active Layout Blueprint Summary Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (useMono) 7.dp else 12.dp))
                .background(palette.glassMedium)
                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(if (useMono) 7.dp else 12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = activeSpec.layoutArchetype.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.accentSky,
                        fontFamily = if (useMono) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = activeSpec.layoutArchetype.layoutTagline,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = palette.inkSecondary,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (preferLocalDefault && activeSpec.isNormalDefault) "Default" else activeSpec.layoutArchetype.shortBadge,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }

        // Instant One-Tap Theme + Full Layout Rearrangement Strip directly inside Settings
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_quick_theme_strip")
        ) {
            items(items = quickThemes, key = { it.themeId }) { spec ->
                val isSelected = activeSpec.themeId == spec.themeId
                val chipAccent = remember(spec.accentPrimaryHex) {
                    parseHexColorOr(spec.accentPrimaryHex, palette.accentSky)
                }
                val chipCanvas = remember(spec.canvasTopHex) {
                    parseHexColorOr(spec.canvasTopHex, palette.canvasTop)
                }
                val chipCorner = if (useMono) 8.dp else 12.dp
                Column(
                    modifier = Modifier
                        .width(136.dp)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(chipCorner))
                        .background(if (isSelected) palette.glassTintedSky else palette.canvasTop)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) chipAccent else palette.cardHairlineBorder,
                            shape = RoundedCornerShape(chipCorner)
                        )
                        .clickable(role = Role.RadioButton) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (spec.isNormalDefault) {
                                onRevertToNormalDefault()
                            } else {
                                onSelectLocalThemePreset(spec)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                        .testTag("btn_settings_quick_theme_${spec.themeId}"),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(chipCanvas)
                                    .border(0.75.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(chipAccent)
                            )
                        }
                        Text(
                            text = spec.layoutArchetype.shortBadge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSelected) palette.accentSkyBright else palette.inkMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = spec.name,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = palette.inkPrimary,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 360.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LivingPrimaryButton(
                        text = "Open Themes",
                        icon = Icons.Filled.Palette,
                        onClick = onOpenThemeStudio,
                        testTag = "btn_settings_open_theme_studio",
                        modifier = Modifier.fillMaxWidth()
                    )

                    LivingSecondaryButton(
                        text = if (activeSpec.isNormalDefault) "Default Active" else "Reset Default",
                        icon = Icons.Filled.RestartAlt,
                        selected = activeSpec.isNormalDefault,
                        onClick = onRevertToNormalDefault,
                        testTag = "btn_settings_revert_default_theme",
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LivingPrimaryButton(
                        text = "Open Themes",
                        icon = Icons.Filled.Palette,
                        onClick = onOpenThemeStudio,
                        testTag = "btn_settings_open_theme_studio",
                        modifier = Modifier.weight(1f)
                    )

                    LivingSecondaryButton(
                        text = if (activeSpec.isNormalDefault) "Default Active" else "Reset Default",
                        icon = Icons.Filled.RestartAlt,
                        selected = activeSpec.isNormalDefault,
                        onClick = onRevertToNormalDefault,
                        testTag = "btn_settings_revert_default_theme",
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp)
                    )
                }
            }
        }
    }
}

// ── CLEAN COLLAPSIBLE & READINESS BUILDING BLOCKS ───────────────────────────

@Composable
private fun SystemReadinessHeroCard(
    hasOverlayPermission: Boolean,
    isGoogleSignedIn: Boolean,
    activeMethod: String,
    onFixNext: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val heroCorner = themeSpec.cornerRadiusDp.coerceIn(8, 28).dp
    val readyScore = listOf(hasOverlayPermission, isGoogleSignedIn).count { it }
    val allCoreReady = hasOverlayPermission && isGoogleSignedIn

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(heroCorner))
            .background(
                Brush.linearGradient(listOf(palette.glassTintedSky, palette.glassStrong))
            )
            .border(
                width = 1.dp,
                color = if (allCoreReady) palette.accentEmerald.copy(alpha = 0.45f) else palette.accentSky.copy(alpha = 0.45f),
                shape = RoundedCornerShape(heroCorner)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (allCoreReady) "Everything Ready" else "Setup Check ($readyScore/2)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.inkPrimary
                    )
                )
                Text(
                    text = "Solving Style: $activeMethod",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = palette.inkSecondary,
                        fontSize = 12.sp
                    )
                )
            }

            if (!hasOverlayPermission || !isGoogleSignedIn) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.accentSky)
                        .clickable(role = Role.Button, onClick = onFixNext)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (!hasOverlayPermission) "Allow Pill" else "Sign In",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.onAccentInk,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.glassMedium)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((readyScore / 2f).coerceAtLeast(0.18f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            if (allCoreReady) listOf(palette.accentEmerald, palette.accentMint)
                            else listOf(palette.accentSky, palette.accentSkyBright)
                        )
                    )
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadinessChip(
                "Floating Pill",
                if (hasOverlayPermission) "Allowed" else "Needed",
                hasOverlayPermission,
                Modifier.weight(1f)
            )
            ReadinessChip(
                "Account",
                if (isGoogleSignedIn) "Signed In" else "Sign In Needed",
                isGoogleSignedIn,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DailyAllowanceCompactCard(
    snapshot: DailyUsageSnapshot
) {
    val palette = LocalWhiteGlassPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.canvasBottom)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("card_daily_usage_limits"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Daily Allowance",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
            )
            Text(
                text = "Resets 00:00 UTC",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = palette.inkMuted,
                    fontSize = 10.5.sp
                )
            )
        }
        snapshot.items().forEach { item ->
            val ratio = (item.used.toFloat() / item.limit.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.feature.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.inkSecondary,
                            fontSize = 11.5.sp
                        )
                    )
                    Text(
                        text = "${item.used} / ${item.limit}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (item.isExhausted) palette.accentSky else palette.inkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(palette.cardHairlineBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(if (item.isExhausted) palette.accentSky else palette.accentEmerald)
                    )
                }
            }
        }
        Text(
            text = "Cached answers & your own API keys do not use daily allowance.",
            style = MaterialTheme.typography.labelSmall.copy(
                color = palette.inkMuted,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        )
    }
}

@Composable
private fun ReadinessChip(
    label: String,
    value: String,
    isReady: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(palette.canvasBottom)
            .border(
                width = 1.dp,
                color = if (isReady) palette.accentEmerald.copy(alpha = 0.4f) else palette.cardHairlineBorder,
                shape = RoundedCornerShape(11.dp)
            )
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isReady) palette.accentEmerald else palette.accentSky)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(color = palette.inkSecondary, fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (isReady) palette.accentEmerald else palette.inkPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsAccordionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    statusBadge: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val archetype = themeSpec.layoutArchetype
    val isEditorial = archetype == ThemeLayoutArchetype.EDITORIAL_MONOGRAPH
    val isHud = archetype == ThemeLayoutArchetype.TELEMETRY_HUD
    val corner = when (archetype) {
        ThemeLayoutArchetype.TELEMETRY_HUD -> 8.dp
        ThemeLayoutArchetype.ATELIER_BENTO -> 14.dp
        ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> 0.dp
        ThemeLayoutArchetype.KINETIC_DECK -> 18.dp
        ThemeLayoutArchetype.IOS_LIQUID_LIGHT -> 22.dp
        ThemeLayoutArchetype.CLASSIC_PRISM -> themeSpec.cornerRadiusDp.coerceIn(8, 26).dp
    }
    val titleFont = when (archetype) {
        ThemeLayoutArchetype.TELEMETRY_HUD -> FontFamily.Monospace
        ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> FontFamily.Serif
        else -> FontFamily.Default
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isEditorial) {
                    Modifier.background(Color.Transparent)
                } else {
                    Modifier
                        .clip(RoundedCornerShape(corner))
                        .background(palette.glassStrong)
                        .border(
                            width = if (isHud && isExpanded) 1.25.dp else 1.dp,
                            color = if (isExpanded) palette.accentSky.copy(alpha = 0.45f) else palette.cardHairlineBorder,
                            shape = RoundedCornerShape(corner)
                        )
                }
            )
    ) {
        if (isEditorial) {
            HorizontalDivider(color = palette.cardHairlineBorder)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button, onClick = onToggleExpand)
                .padding(horizontal = if (isEditorial) 4.dp else 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isEditorial) 34.dp else 40.dp)
                        .clip(RoundedCornerShape(if (isHud) 6.dp else if (isEditorial) 999.dp else 12.dp))
                        .background(if (isEditorial) Color.Transparent else palette.glassTintedSky)
                        .border(
                            1.dp,
                            palette.accentSky.copy(alpha = if (isEditorial) 0.35f else 0.55f),
                            RoundedCornerShape(if (isHud) 6.dp else if (isEditorial) 999.dp else 12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = palette.accentSky, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHud) title.uppercase() else title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = if (isHud) 14.sp else 15.5.sp,
                            fontFamily = titleFont,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            color = palette.inkSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(if (isHud) 4.dp else 7.dp))
                        .background(palette.glassMedium)
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(if (isHud) 4.dp else 7.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isHud) statusBadge.uppercase() else statusBadge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.inkPrimary,
                            fontFamily = if (isHud) FontFamily.Monospace else FontFamily.Default,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = palette.inkSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (isEditorial) 4.dp else 16.dp,
                        end = if (isEditorial) 4.dp else 16.dp,
                        bottom = 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isEditorial) {
                    HorizontalDivider(color = palette.cardHairlineBorder)
                }
                content()
            }
        }
    }
}

@Composable
private fun SettingsStatusActionRow(
    title: String,
    subtitle: String,
    statusText: String,
    isReady: Boolean,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.glassMedium)
            .border(
                width = 1.dp,
                color = if (isReady) palette.accentEmerald.copy(alpha = 0.4f) else palette.accentSky.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isReady) palette.accentEmeraldContainer else palette.glassTintedSky),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (isReady) palette.accentEmerald else palette.accentSky, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.inkPrimary
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = palette.inkSecondary
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isReady) palette.accentEmeraldContainer else palette.accentSky)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isReady) palette.accentEmerald else palette.onAccentInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = palette.inkSecondary, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun LocalApiKeyField(
    label: String,
    placeholder: String,
    badge: String,
    value: String,
    onValueChange: (String) -> Unit,
    showPlaintext: Boolean,
    testTag: String
) {
    val palette = LocalWhiteGlassPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = palette.inkPrimary)
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.accentSky,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.glassTintedSky)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text(placeholder, color = palette.inkMuted) },
            visualTransformation = if (showPlaintext) VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = palette.glassMedium,
                unfocusedContainerColor = palette.canvasBottom,
                focusedBorderColor = palette.accentSky,
                unfocusedBorderColor = palette.cardHairlineBorder,
                focusedTextColor = palette.inkPrimary,
                unfocusedTextColor = palette.inkPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}
