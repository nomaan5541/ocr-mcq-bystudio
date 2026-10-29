package com.omnisolve.overlay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.omnisolve.overlay.ui.components.LivingOptionChoiceCard
import com.omnisolve.overlay.ui.components.LivingPrimaryButton
import com.omnisolve.overlay.ui.components.LivingSecondaryButton
import com.omnisolve.overlay.ui.components.LivingSwitchRow
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

@Composable
fun SettingsScreen(
    state: MainUiState,
    sample: PracticeSample,
    listState: LazyListState,
    onSaveApiKeys: (nvidia: String, openRouter: String, gemini: String, openAi: String) -> Unit,
    onClearApiKeys: () -> Unit,
    onToggleAutoFailover: (Boolean) -> Unit,
    onToggleChargingCamo: (Boolean) -> Unit,
    onSignInGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onOpenGeminiWebLogin: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSelectHudStyle: (String) -> Unit,
    onSelectOpacityIndex: (Int) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onTestVibration: () -> Unit,
    onToggleInstantDetect: (Boolean) -> Unit,
    onToggleAutoClick: (Boolean) -> Unit,
    onToggleAutoNext: (Boolean) -> Unit,
    onSolvePractice: () -> Unit,
    onNextPractice: () -> Unit,
    onClearHistory: () -> Unit,
    onSelectSolverMethod: (SolverMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current

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
                            tint = Color(0xFF047857),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Local Device Storage Only",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857)
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LivingPrimaryButton(
                        text = "Save Keys Locally (${state.configuredKeyCount} Active)",
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

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(6.dp))

                LivingSwitchRow(
                    title = "Auto-Shift AI When Quota Reached",
                    subtitle = "Automatically switches to the next working AI key if one reaches its rate or quota limit.",
                    checked = state.autoFailoverEnabled,
                    onCheckedChange = onToggleAutoFailover,
                    icon = Icons.Filled.AutoAwesome,
                    testTag = "switch_auto_failover"
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
                            .background(Color(0xFFECFDF5))
                            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF047857),
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
                HorizontalDivider(color = Color(0xFFE2E8F0))
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
            val opacityLabels = listOf("100%", "90%", "50%", "25%")
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
                Text(
                    text = stringResource(R.string.opacity_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.inkPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    opacityLabels.forEachIndexed { idx, label ->
                        LivingSecondaryButton(
                            text = label,
                            selected = state.islandOpacityIndex == idx,
                            onClick = { onSelectOpacityIndex(idx) },
                            testTag = "btn_opacity_$idx",
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFE2E8F0))
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
                HorizontalDivider(color = Color(0xFFE2E8F0))
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.titleMedium,
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

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = sample.question,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = sample.options.joinToString("   "),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkSecondary
                )

                if (state.practiceSolvedLetter != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val previewBadge = if (state.batteryCamouflage) {
                        val ch = state.practiceSolvedLetter.trim().uppercase().firstOrNull()
                        if (ch != null && ch in 'A'..'F') "Charging ${91 + (ch - 'A')}% (${state.practiceSolvedLetter})"
                        else state.practiceSolvedLetter
                    } else {
                        "Option ${state.practiceSolvedLetter}"
                    }
                    Text(
                        text = "Result: $previewBadge — ${state.practiceStatusNote.orEmpty()}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF047857)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LivingPrimaryButton(
                        text = if (state.isPracticeScanning) "Solving..." else stringResource(R.string.btn_solve_sample),
                        icon = Icons.Filled.PlayArrow,
                        onClick = onSolvePractice,
                        testTag = "btn_solve_practice",
                        modifier = Modifier.weight(1f)
                    )
                    LivingSecondaryButton(
                        text = stringResource(R.string.btn_next_sample),
                        icon = Icons.Filled.SkipNext,
                        onClick = onNextPractice,
                        testTag = "btn_next_practice"
                    )
                }

                if (state.historyEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0))
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
                                    .background(Color(0xFFE0F2FE)),
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

        // 6. AT LAST: Overlapping Google Completely
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
                color = Color(0xFF047857),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFD1FAE5))
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
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color(0xFFF8FAFC),
                focusedBorderColor = palette.accentSky,
                unfocusedBorderColor = Color(0xFFCBD5E1),
                focusedTextColor = palette.inkPrimary,
                unfocusedTextColor = palette.inkPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}
