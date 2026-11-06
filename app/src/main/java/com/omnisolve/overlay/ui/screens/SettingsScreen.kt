package com.omnisolve.overlay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.HorizontalSplit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.R
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.PracticeSample
import com.omnisolve.overlay.ui.components.FlashcardDeckSection
import com.omnisolve.overlay.ui.components.LivingOptionChoiceCard
import com.omnisolve.overlay.ui.components.LivingPrimaryButton
import com.omnisolve.overlay.ui.components.LivingSecondaryButton
import com.omnisolve.overlay.ui.components.LivingSwitchRow
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
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
    onOpenGeminiWebLogin: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
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
    onToggleAutoClick: (Boolean) -> Unit,
    onToggleAutoNext: (Boolean) -> Unit,
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
    onFlashcardSearchChange: (String) -> Unit,
    onFlashcardSubjectFilterChange: (String) -> Unit,
    onToggleFlashcardBookmarkedOnly: (Boolean) -> Unit,
    onToggleBookmarkEntry: (Long) -> Unit,
    onShowBannerMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val dividerColor = palette.cardHairlineBorder

    var nvidiaInput by rememberSaveable(state.nvidiaApiKey) { mutableStateOf(state.nvidiaApiKey) }
    var openRouterInput by rememberSaveable(state.openRouterApiKey) { mutableStateOf(state.openRouterApiKey) }
    var geminiInput by rememberSaveable(state.geminiApiKey) { mutableStateOf(state.geminiApiKey) }
    var openAiInput by rememberSaveable(state.openAiApiKey) { mutableStateOf(state.openAiApiKey) }
    var showKeysPlaintext by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Answer Display Format: Option ABCD vs Charging 91, 92, 93, 94
        item(key = "answer_format_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .livingGlassSurface(
                        strength = GlassMaterialStrength.Card,
                        cornerRadius = 22.dp
                    )
                    .padding(18.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_format_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_format_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                LivingOptionChoiceCard(
                    title = stringResource(R.string.format_abcd_title),
                    subtitle = stringResource(R.string.format_abcd_desc),
                    selected = !state.batteryCamouflage,
                    icon = Icons.Filled.FontDownload,
                    badgeText = "ABCD",
                    onClick = { onToggleChargingCamo(false) },
                    testTag = "option_format_abcd"
                )

                Spacer(modifier = Modifier.height(10.dp))

                LivingOptionChoiceCard(
                    title = stringResource(R.string.format_charging_title),
                    subtitle = stringResource(R.string.format_charging_desc),
                    selected = state.batteryCamouflage,
                    icon = Icons.Filled.BatteryChargingFull,
                    badgeText = "91–94%",
                    onClick = { onToggleChargingCamo(true) },
                    testTag = "option_format_charging"
                )
            }
        }

        // 2. Local API Keys Vault (Stored Strictly Locally on Device)
        item(key = "local_api_keys_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .livingGlassSurface(
                        strength = GlassMaterialStrength.Card,
                        cornerRadius = 22.dp
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = palette.accentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Local Device Storage Only",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentEmerald
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

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_keys_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.settings_keys_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                LocalApiKeyField(
                    label = "NVIDIA Free API Key (build.nvidia.com)",
                    placeholder = "nvapi-...",
                    badge = "FREE FOR ALL",
                    value = nvidiaInput,
                    onValueChange = { nvidiaInput = it },
                    showPlaintext = showKeysPlaintext,
                    testTag = "input_nvidia_key"
                )

                Spacer(modifier = Modifier.height(10.dp))

                LocalApiKeyField(
                    label = "OpenRouter API Key (openrouter.ai)",
                    placeholder = "sk-or-v1-...",
                    badge = "FREE MODELS",
                    value = openRouterInput,
                    onValueChange = { openRouterInput = it },
                    showPlaintext = showKeysPlaintext,
                    testTag = "input_openrouter_key"
                )

                Spacer(modifier = Modifier.height(10.dp))

                LocalApiKeyField(
                    label = "Gemini API Key (aistudio.google.com)",
                    placeholder = "AIzaSy...",
                    badge = "FREE TIER",
                    value = geminiInput,
                    onValueChange = { geminiInput = it },
                    showPlaintext = showKeysPlaintext,
                    testTag = "input_gemini_key"
                )

                Spacer(modifier = Modifier.height(10.dp))

                LocalApiKeyField(
                    label = "OpenAI API Key (platform.openai.com)",
                    placeholder = "sk-proj-...",
                    badge = "OPTIONAL",
                    value = openAiInput,
                    onValueChange = { openAiInput = it },
                    showPlaintext = showKeysPlaintext,
                    testTag = "input_openai_key"
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LivingPrimaryButton(
                        text = "Save Keys (${state.configuredKeyCount} Active)",
                        icon = Icons.Filled.Key,
                        onClick = {
                            onSaveApiKeys(
                                nvidiaInput,
                                openRouterInput,
                                geminiInput,
                                openAiInput
                            )
                        },
                        testTag = "btn_save_api_keys",
                        modifier = Modifier.weight(1f)
                    )
                    LivingSecondaryButton(
                        text = if (state.isTestingApiKeys) "Testing..." else "Test Keys",
                        icon = Icons.Filled.Bolt,
                        onClick = {
                            onTestApiKeys(
                                nvidiaInput,
                                openRouterInput,
                                geminiInput,
                                openAiInput
                            )
                        },
                        testTag = "btn_test_api_keys"
                    )
                    if (state.configuredKeyCount > 0 ||
                        nvidiaInput.isNotBlank() ||
                        openRouterInput.isNotBlank() ||
                        geminiInput.isNotBlank() ||
                        openAiInput.isNotBlank()
                    ) {
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
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.accentSkyContainer)
                            .border(1.dp, palette.accentSky, RoundedCornerShape(12.dp))
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

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = dividerColor)
                Spacer(modifier = Modifier.height(6.dp))

                LivingSwitchRow(
                    title = "Instant Auto-Switch on Error or Quota",
                    subtitle = "Immediately shifts to the next working AI key (NVIDIA ↔ OpenRouter ↔ Gemini ↔ OpenAI) on any error or quota limit.",
                    checked = state.autoFailoverEnabled,
                    onCheckedChange = onToggleAutoFailover,
                    icon = Icons.Filled.AutoAwesome,
                    testTag = "switch_auto_failover"
                )
            }
        }

        // 2B. Dynamic Flow Bar Options (Individual ON/OFF Toggles for Floating Island Controls)
        item(key = "dynamic_flow_options_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .livingGlassSurface(
                        strength = GlassMaterialStrength.Card,
                        cornerRadius = 22.dp
                    )
                    .padding(18.dp)
                    .testTag("section_dynamic_flow_options")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ViewStream,
                            contentDescription = null,
                            tint = palette.accentSky,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dynamic Flow Bar Options",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary
                        )
                    }
                    Text(
                        text = "CUSTOMIZE BAR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentEmerald,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.accentEmeraldContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Play, Eye, Bot & Opacity% buttons are removed from the floating bar for a clean workflow. Toggle ON/OFF which smart helper tools appear when you tap the Dynamic Island orb:",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                LivingSwitchRow(
                    title = "2-View Split Capture (1st + 2nd)",
                    subtitle = "Tap '1st' on Question + Diagram, scroll down to Options, then tap '2nd' to stitch & solve the complete problem.",
                    checked = state.dfTwoPartCaptureEnabled,
                    onCheckedChange = onToggleDfTwoPartCapture,
                    icon = Icons.Filled.HorizontalSplit,
                    testTag = "switch_df_two_part"
                )

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                LivingSwitchRow(
                    title = "2-Second Scroll-Capture Timer (2s↓)",
                    subtitle = "Captures View 1 immediately, gives a 2-second countdown while you scroll to options, then auto-captures View 2 & solves.",
                    checked = state.dfScrollTimerEnabled,
                    onCheckedChange = onToggleDfScrollTimer,
                    icon = Icons.Filled.Timer,
                    testTag = "switch_df_scroll_timer"
                )

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                LivingSwitchRow(
                    title = "Match-the-Following & Math+ Mode (STD / MATCH / MATH+)",
                    subtitle = "Quick chip to switch between Standard MCQ, Column I/II Match-the-Following, and Complicated Math reasoning.",
                    checked = state.dfComplexModeEnabled,
                    onCheckedChange = onToggleDfComplexMode,
                    icon = Icons.Filled.Calculate,
                    testTag = "switch_df_complex_mode"
                )

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                LivingSwitchRow(
                    title = "Swipe-Down Looping 3-Method Switcher",
                    subtitle = "Swipe down on the Dynamic Island to scroll a continuous loop of 1) Server Sol ↻ 2) OCR Text ↻ 3) API Key and tap to lock it in.",
                    checked = state.dfSwipeMethodLoopEnabled,
                    onCheckedChange = onToggleDfSwipeMethodLoop,
                    icon = Icons.Filled.SwapVert,
                    testTag = "switch_df_swipe_method_loop"
                )

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                LivingSwitchRow(
                    title = "Diagram Vision Boost Chip (IMG)",
                    subtitle = "Forces full diagram/circuit/graph image attachment alongside OCR text when enabled.",
                    checked = state.dfDiagramVisionEnabled,
                    onCheckedChange = onToggleDfDiagramVision,
                    icon = Icons.Filled.Image,
                    testTag = "switch_df_diagram_vision"
                )

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                LivingSwitchRow(
                    title = "Quick Region Box Snip Chip (Box)",
                    subtitle = "Adds a 1-tap 'Box' button in the Dynamic Flow bar to drag-snip a single diagram or sub-question.",
                    checked = state.dfQuickCropEnabled,
                    onCheckedChange = onToggleDfQuickCrop,
                    icon = Icons.Filled.CropFree,
                    testTag = "switch_df_quick_crop"
                )
            }
        }

        // 3. Server Sol & Background Gemini Logins + Permissions
        item(key = "logins_and_permissions_section") {
            val isGoogleSignedIn = !state.firebaseUserId.isNullOrBlank()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .livingGlassSurface(
                        strength = GlassMaterialStrength.Card,
                        cornerRadius = 22.dp
                    )
                    .padding(18.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_logins_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Server Sol Login
                if (isGoogleSignedIn) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.accentEmeraldContainer)
                            .border(1.dp, palette.accentEmerald, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = palette.accentEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.firebaseUserName?.takeIf { it.isNotBlank() } ?: "Server Connected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary
                            )
                            Text(
                                text = state.firebaseUserEmail ?: "Ready for Method 1 (Server Sol)",
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
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isSigningInGoogle) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = palette.accentSky
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        LivingPrimaryButton(
                            text = if (state.isSigningInGoogle) "Signing in..." else "Log in on Server (Google)",
                            icon = Icons.Filled.AccountCircle,
                            onClick = { if (!state.isSigningInGoogle) onSignInGoogle() },
                            testTag = "btn_google_login",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Background Gemini Web Login
                LivingSecondaryButton(
                    text = if (state.isGoogleWebLoggedIn) {
                        "Background Gemini Connected (Tap to Refresh)"
                    } else {
                        "Connect Background Gemini Session"
                    },
                    icon = Icons.Filled.AutoAwesome,
                    selected = state.isGoogleWebLoggedIn,
                    onClick = onOpenGeminiWebLogin,
                    testTag = "btn_gemini_web_session",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = dividerColor)
                Spacer(modifier = Modifier.height(12.dp))

                // System Permissions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LivingSecondaryButton(
                        text = if (state.hasOverlayPermission) "Overlay: Allowed" else "Allow Overlay",
                        icon = Icons.Filled.Layers,
                        selected = state.hasOverlayPermission,
                        onClick = onRequestOverlayPermission,
                        testTag = "btn_settings_perm_overlay",
                        modifier = Modifier.weight(1f)
                    )
                    LivingSecondaryButton(
                        text = if (state.isAccessibilityEnabled) "Auto-Tap: Ready" else "Enable Auto-Tap",
                        icon = Icons.Filled.AccessibilityNew,
                        selected = state.isAccessibilityEnabled,
                        onClick = onOpenAccessibilitySettings,
                        testTag = "btn_perm_accessibility",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Floating Overlay Appearance, Opacity, Vibration & Auto-Tap
        item(key = "overlay_controls_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .livingGlassSurface(
                        strength = GlassMaterialStrength.Card,
                        cornerRadius = 22.dp
                    )
                    .padding(18.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_overlay_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                LivingOptionChoiceCard(
                    title = stringResource(R.string.style_island_title),
                    subtitle = stringResource(R.string.style_island_desc),
                    selected = state.hudStyle == MainActivity.HUD_STYLE_ISLAND,
                    icon = Icons.Filled.ViewStream,
                    onClick = { onSelectHudStyle(MainActivity.HUD_STYLE_ISLAND) },
                    testTag = "option_hud_island"
                )

                Spacer(modifier = Modifier.height(10.dp))

                LivingOptionChoiceCard(
                    title = stringResource(R.string.style_bubble_title),
                    subtitle = stringResource(R.string.style_bubble_desc),
                    selected = state.hudStyle == MainActivity.HUD_STYLE_BUBBLE,
                    icon = Icons.Filled.BubbleChart,
                    onClick = { onSelectHudStyle(MainActivity.HUD_STYLE_BUBBLE) },
                    testTag = "option_hud_bubble"
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stringResource(R.string.opacity_label)} (20%–100%, also on Home)",
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
                        activeTrackColor = palette.accentSky
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_settings_opacity_pct")
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = dividerColor)
                Spacer(modifier = Modifier.height(8.dp))

                LivingSwitchRow(
                    title = stringResource(R.string.haptic_title),
                    subtitle = stringResource(R.string.haptic_desc),
                    checked = state.hapticEnabled,
                    onCheckedChange = onToggleHaptics,
                    icon = Icons.Filled.Vibration,
                    testTag = "switch_haptics"
                )

                Spacer(modifier = Modifier.height(6.dp))
                LivingSecondaryButton(
                    text = stringResource(R.string.btn_test_vibration),
                    icon = Icons.Filled.Vibration,
                    onClick = onTestVibration,
                    testTag = "btn_test_vibration",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = dividerColor)
                Spacer(modifier = Modifier.height(8.dp))

                LivingSwitchRow(
                    title = stringResource(R.string.instant_detect_title),
                    subtitle = stringResource(R.string.instant_detect_desc),
                    checked = state.instantTriggerEnabled,
                    onCheckedChange = onToggleInstantDetect,
                    icon = Icons.Filled.AutoAwesome,
                    testTag = "switch_instant_detect"
                )

                LivingSwitchRow(
                    title = stringResource(R.string.autotap_answer_title),
                    subtitle = stringResource(R.string.autotap_answer_desc),
                    checked = state.autoClickEnabled,
                    onCheckedChange = onToggleAutoClick,
                    icon = Icons.Filled.TouchApp,
                    testTag = "switch_auto_tap"
                )

                LivingSwitchRow(
                    title = stringResource(R.string.autotap_next_title),
                    subtitle = stringResource(R.string.autotap_next_desc),
                    checked = state.afkAutoAdvanceEnabled,
                    onCheckedChange = onToggleAutoNext,
                    icon = Icons.Filled.SmartToy,
                    testTag = "switch_auto_next"
                )
            }
        }

        // 5. Quick Practice Test & Solved History
        item(key = "quick_test_and_history_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .livingGlassSurface(
                        strength = GlassMaterialStrength.Card,
                        cornerRadius = 22.dp
                    )
                    .padding(18.dp)
            ) {
                // Feature Guide & Operational Manual (Comprehensive explanation of every feature)
                Text(
                    text = stringResource(R.string.feature_guide_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                )
                Text(
                    text = stringResource(R.string.feature_guide_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                FeatureExplanationCard(
                    title = "1. Floating Dynamic Island & 1-Tap Solving",
                    description = "A compact, draggable pill docked at the top of your screen. Tap anywhere on the pill body to instantly scan and solve the visible question. The pill displays the solved option badge (e.g. Option B) with zero lag.",
                    palette = palette
                )
                Spacer(modifier = Modifier.height(8.dp))

                FeatureExplanationCard(
                    title = "2. 2-View Split Capture (1st + 2nd)",
                    description = "Solves long questions where diagrams or equations are at the top and multiple choice options are below the fold. Tap '1st' to capture the question/diagram, scroll down to options, and tap '2nd' — prism-ocr-solver stitches both images and analyzes the full context.",
                    palette = palette
                )
                Spacer(modifier = Modifier.height(8.dp))

                FeatureExplanationCard(
                    title = "3. 2-Second Scroll-Capture Timer (2s↓)",
                    description = "One-tap automated capture: instantly snapshots View 1, gives you a live 2-second countdown while you scroll down to options, and automatically snapshots View 2 without requiring a second tap.",
                    palette = palette
                )
                Spacer(modifier = Modifier.height(8.dp))

                FeatureExplanationCard(
                    title = "4. Match-the-Following & Math+ Modes",
                    description = "Switch between Standard MCQ, Column I & Column II matching (outputs structured pairings like A-2, B-4, C-1, D-3), and Complicated Math+ multi-step equation reasoning.",
                    palette = palette
                )
                Spacer(modifier = Modifier.height(8.dp))

                FeatureExplanationCard(
                    title = "5. Swipe-Down Looping Method Selector",
                    description = "Swipe down directly on the floating Dynamic Island to reveal an infinite loop of the 3 solving engines: 1) Server Sol, 2) OCR Text Extractor, and 3) Local API Keys. Tap any method to instantly switch on the fly.",
                    palette = palette
                )
                Spacer(modifier = Modifier.height(8.dp))

                FeatureExplanationCard(
                    title = "6. Local API Keys & Instant Auto-Failover",
                    description = "Supports NVIDIA NIM, OpenRouter, OpenAI, and Google Gemini API keys. Keys are saved strictly on your local device. If a provider reaches rate limits or errors, prism-ocr-solver automatically fails over to the next configured provider in milliseconds.",
                    palette = palette
                )
                Spacer(modifier = Modifier.height(8.dp))

                FeatureExplanationCard(
                    title = "7. Start-Time Customizable Opacity (20% – 100%)",
                    description = "Adjust overlay transparency from 20% to 100% directly from the Home screen or Settings with live numeric and slider inputs for distraction-free reading.",
                    palette = palette
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = dividerColor)
                Spacer(modifier = Modifier.height(12.dp))

                // Legal & Privacy Documentation Hub
                Text(
                    text = stringResource(R.string.privacy_legal_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                )
                Text(
                    text = stringResource(R.string.privacy_legal_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                val context = androidx.compose.ui.platform.LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (palette.isDarkObsidian) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                        .border(1.dp, palette.accentSky.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(MainActivity.URL_PRIVACY_POLICY_WEB)
                            )
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                onShowBannerMessage("Visit: virusboss.in/app/prism-ocr-solver/#privacy-policy")
                            }
                        }
                        .padding(14.dp)
                        .testTag("btn_open_public_privacy_policy"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Official Privacy Policy & Disclosures",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentSky
                        )
                        Text(
                            text = "Hosted at virusboss.in/app/prism-ocr-solver/#privacy-policy",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary
                        )
                    }
                    LivingSecondaryButton(
                        text = "Open Web",
                        icon = Icons.Filled.OpenInFull,
                        onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(MainActivity.URL_PRIVACY_POLICY_WEB)
                            )
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                onShowBannerMessage("Visit: virusboss.in/app/prism-ocr-solver/#privacy-policy")
                            }
                        },
                        testTag = "btn_open_web_policy"
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = dividerColor)
                Spacer(modifier = Modifier.height(12.dp))

                // Solved History Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.inkPrimary
                    )
                    if (state.historyEntries.isNotEmpty()) {
                        LivingSecondaryButton(
                            text = stringResource(R.string.btn_clear_history),
                            icon = Icons.Filled.DeleteOutline,
                            onClick = onClearHistory,
                            testTag = "btn_clear_history",
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                if (state.historyEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = dividerColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    state.historyEntries.take(4).forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(palette.accentSkyContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = entry.answerLetter,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accentSky
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = entry.questionPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkPrimary,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 6. ADVANCED OPTIONS (ALL OFF BY DEFAULT — INDIVIDUAL ON/OFF TOGGLES)
        item(key = "advanced_options_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .livingGlassSurface(
                        strength = GlassMaterialStrength.Card,
                        cornerRadius = 22.dp
                    )
                    .padding(18.dp)
                    .testTag("section_advanced_options")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = palette.accentSky,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Advanced Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary
                        )
                    }
                    Text(
                        text = "DEFAULT OFF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentSky,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.accentSkyContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Every advanced feature starts OFF by default. Turn ON only the tools you want.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // (1) Crop-to-Scan Region Selector (Liquid Glass Snipper)
                LivingSwitchRow(
                    title = "Crop-to-Scan Region Selector",
                    subtitle = "Drag a glowing Liquid Glass box over a specific diagram, circuit, graph, or single question.",
                    checked = state.advCropToScanEnabled,
                    onCheckedChange = onToggleAdvCropToScan,
                    icon = Icons.Filled.CropFree,
                    testTag = "switch_adv_crop_scan"
                )

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                // (2) Dynamic Island Swipe & Long-Press Gestures
                LivingSwitchRow(
                    title = "Dynamic Island Swipe & Long-Press Gestures",
                    subtitle = "Swipe left/right on the pill to browse solved history (#3: B ↔ #2: D) or long-press to switch Subject Mode & OCR.",
                    checked = state.advIslandGesturesEnabled,
                    onCheckedChange = onToggleAdvIslandGestures,
                    icon = Icons.Filled.Swipe,
                    testTag = "switch_adv_island_gestures"
                )

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                // (3) Split Pill Mode (Badge • Short Answer)
                LivingSwitchRow(
                    title = "Split Pill Mode (B • Mitochondria)",
                    subtitle = "Shows both the option letter and concise short answer text together on the floating pill.",
                    checked = state.advSplitPillEnabled,
                    onCheckedChange = onToggleAdvSplitPill,
                    icon = Icons.Filled.HorizontalSplit,
                    testTag = "switch_adv_split_pill"
                )

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                // (4) Ultra-Thin Top Strip & Edge-Docked Mini Tab
                LivingSwitchRow(
                    title = "Ultra-Thin Top Strip & Edge-Docked Mini Tab",
                    subtitle = "Switch to ultra-minimal overlay shapes with tiny screen footprint.",
                    checked = state.advMinimalShapesEnabled,
                    onCheckedChange = onToggleAdvMinimalShapes,
                    icon = Icons.Filled.SpaceDashboard,
                    testTag = "switch_adv_minimal_shapes"
                )
                if (state.advMinimalShapesEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LivingSecondaryButton(
                            text = "Ultra-Thin Top Strip",
                            selected = state.hudStyle == MainActivity.HUD_STYLE_TOP_STRIP,
                            onClick = { onSelectHudStyle(MainActivity.HUD_STYLE_TOP_STRIP) },
                            testTag = "btn_shape_top_strip",
                            modifier = Modifier.weight(1f)
                        )
                        LivingSecondaryButton(
                            text = "Edge-Docked Tab",
                            selected = state.hudStyle == MainActivity.HUD_STYLE_EDGE_TAB,
                            onClick = { onSelectHudStyle(MainActivity.HUD_STYLE_EDGE_TAB) },
                            testTag = "btn_shape_edge_tab",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                // (5) Network Speed Disguise (0.91–0.94 MB/s)
                LivingSwitchRow(
                    title = "Network Speed Disguise (0.91–0.94 MB/s)",
                    subtitle = "0.91 MB/s = A • 0.92 MB/s = B • 0.93 MB/s = C • 0.94 MB/s = D (idle 0.48 MB/s).",
                    checked = state.advNetSpeedDisguiseEnabled,
                    onCheckedChange = onToggleAdvNetSpeedDisguise,
                    icon = Icons.Filled.Speed,
                    testTag = "switch_adv_net_speed"
                )

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                // (6) Auto-Fade Idle Timer
                LivingSwitchRow(
                    title = "Auto-Fade Idle Timer",
                    subtitle = "Automatically dims the floating overlay when idle and restores brightness on touch or new answer.",
                    checked = state.advAutoFadeIdleEnabled,
                    onCheckedChange = onToggleAdvAutoFadeIdle,
                    icon = Icons.Filled.Timer,
                    testTag = "switch_adv_auto_fade"
                )
            }
        }

        // 6B. Advanced Settings — Study Deck, Obsidian Theme, Layout & System Controls (All OFF by default)
        item(key = "advanced_options_study_and_system_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .livingGlassSurface(
                        strength = GlassMaterialStrength.Card,
                        cornerRadius = 22.dp
                    )
                    .padding(18.dp)
                    .testTag("section_advanced_system_options")
            ) {
                Text(
                    text = "Advanced Study, Theme & System Controls",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // (7) Interactive Flashcard Deck & PDF / Markdown Export
                LivingSwitchRow(
                    title = "Interactive Flashcard Deck & Study Export",
                    subtitle = "Turn solved history into swipeable Liquid Glass flashcards with search, bookmarks, and 1-tap PDF/Markdown share.",
                    checked = state.advFlashcardsExportEnabled,
                    onCheckedChange = onToggleAdvFlashcardsExport,
                    icon = Icons.Filled.Quiz,
                    testTag = "switch_adv_flashcards"
                )
                if (state.advFlashcardsExportEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlashcardDeckSection(
                        entries = state.historyEntries,
                        searchQuery = state.flashcardSearchQuery,
                        subjectFilter = state.flashcardSubjectFilter,
                        bookmarkedOnly = state.flashcardBookmarkedOnly,
                        onSearchQueryChange = onFlashcardSearchChange,
                        onSubjectFilterChange = onFlashcardSubjectFilterChange,
                        onToggleBookmarkedOnly = onToggleFlashcardBookmarkedOnly,
                        onToggleBookmarkEntry = onToggleBookmarkEntry,
                        onExportMessage = onShowBannerMessage
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                // (8) Dark Obsidian Liquid Glass Theme
                LivingSwitchRow(
                    title = "Dark Obsidian Liquid Glass Theme",
                    subtitle = "Switch between Daylight White Glass and deep Dark Obsidian Liquid Glass.",
                    checked = state.advDarkObsidianEnabled,
                    onCheckedChange = onToggleAdvDarkObsidian,
                    icon = Icons.Filled.DarkMode,
                    testTag = "switch_adv_dark_obsidian"
                )

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                // (9) Custom Spring-Physics Sliders
                LivingSwitchRow(
                    title = "Custom Spring-Physics Sliders",
                    subtitle = "Customize Liquid Glass spring bounce (damping) and snap speed (stiffness).",
                    checked = state.advSpringPhysicsEnabled,
                    onCheckedChange = onToggleAdvSpringPhysics,
                    icon = Icons.Filled.Tune,
                    testTag = "switch_adv_spring_physics"
                )
                if (state.advSpringPhysicsEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (palette.isDarkObsidian) Color(0xFF141D2E) else Color(0xFFF8FAFC))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("panel_spring_sliders")
                    ) {
                        Text(
                            text = "Spring Damping: ${"%.2f".format(state.springDamping)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.inkPrimary
                        )
                        Slider(
                            value = state.springDamping,
                            onValueChange = { d -> onUpdateSpringPhysics(d, state.springStiffness) },
                            valueRange = 0.35f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.accentSky,
                                activeTrackColor = palette.accentSky
                            ),
                            modifier = Modifier.testTag("slider_spring_damping")
                        )
                        Text(
                            text = "Spring Stiffness: ${state.springStiffness.roundToInt()}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.inkPrimary
                        )
                        Slider(
                            value = state.springStiffness,
                            onValueChange = { s -> onUpdateSpringPhysics(state.springDamping, s) },
                            valueRange = 200f..1500f,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.accentSky,
                                activeTrackColor = palette.accentSky
                            ),
                            modifier = Modifier.testTag("slider_spring_stiffness")
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                // (10) Split-Screen Compact Side-Rail Layout
                LivingSwitchRow(
                    title = "Split-Screen Compact Side-Rail Layout",
                    subtitle = "Replaces bottom bar with a compact side rail optimized for Android split-screen next to a PDF or browser.",
                    checked = state.advSplitScreenRailEnabled,
                    onCheckedChange = onToggleAdvSplitScreenRail,
                    icon = Icons.Filled.ViewSidebar,
                    testTag = "switch_adv_split_rail"
                )

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                // (11) Android Quick Settings Tile & Notification Controls
                LivingSwitchRow(
                    title = "Quick Settings Tile & Notification Controls",
                    subtitle = "Adds Solve Now, Stealth, and Stop buttons to the notification plus pull-down Quick Settings Tile support.",
                    checked = state.advQuickTileNotifEnabled,
                    onCheckedChange = onToggleAdvQuickTileNotif,
                    icon = Icons.Filled.NotificationsActive,
                    testTag = "switch_adv_quick_tile_notif"
                )

                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))

                // (12) Dual-Check Server Verification (Explicitly OFF by default so Server Sol never repeats!)
                LivingSwitchRow(
                    title = "Server Dual-Check Re-Verification",
                    subtitle = "Keep OFF for instant single-pass Server Sol. Turn ON only if you want a second verification pass.",
                    checked = state.dualVerifyEnabled,
                    onCheckedChange = onToggleDualVerify,
                    icon = Icons.Filled.VerifiedUser,
                    testTag = "switch_adv_dual_verify"
                )
            }
        }

        // 7. AT LAST: Overlapping Google Completely
        item(key = "overlapping_google_completely_section") {
            val isOverlappingActive = state.solverMethod == SolverMethod.OVERLAPPING_GOOGLE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .livingGlassSurface(
                        strength = if (isOverlappingActive) GlassMaterialStrength.AccentSky else GlassMaterialStrength.Card,
                        cornerRadius = 22.dp
                    )
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = null,
                        tint = palette.accentSky,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Full-Screen Floating Window Option",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentSky
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LivingOptionChoiceCard(
                    title = stringResource(R.string.overlapping_google_title),
                    subtitle = stringResource(R.string.overlapping_google_desc),
                    selected = isOverlappingActive,
                    icon = Icons.Filled.OpenInFull,
                    badgeText = if (isOverlappingActive) "ACTIVE" else "FULL OVERLAP",
                    onClick = {
                        if (isOverlappingActive) {
                            onSelectSolverMethod(SolverMethod.BACKGROUND_GEMINI)
                        } else {
                            onSelectSolverMethod(SolverMethod.OVERLAPPING_GOOGLE)
                        }
                    },
                    testTag = "option_method_overlapping_google"
                )

                Spacer(modifier = Modifier.height(8.dp))

                LivingSwitchRow(
                    title = "Enable Overlapping Google Completely",
                    subtitle = "Toggle on to keep a full floating Google / Gemini window overlapping right over your screen.",
                    checked = isOverlappingActive,
                    onCheckedChange = { enabled ->
                        onSelectSolverMethod(
                            if (enabled) SolverMethod.OVERLAPPING_GOOGLE else SolverMethod.BACKGROUND_GEMINI
                        )
                    },
                    testTag = "switch_overlapping_google"
                )
            }
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
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.inkPrimary
            )
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.accentEmerald,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.accentEmeraldContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text(placeholder) },
            visualTransformation = if (showPlaintext) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (palette.isDarkObsidian) Color(0xFF141D2E) else Color.White,
                unfocusedContainerColor = if (palette.isDarkObsidian) Color(0xFF0F172A) else Color(0xFFF8FAFC),
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

@Composable
private fun FeatureExplanationCard(
    title: String,
    description: String,
    palette: com.omnisolve.overlay.ui.theme.WhiteGlassPalette
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (palette.isDarkObsidian) Color(0xFF0F172A) else Color(0xFFF8FAFC))
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSecondary
        )
    }
}

