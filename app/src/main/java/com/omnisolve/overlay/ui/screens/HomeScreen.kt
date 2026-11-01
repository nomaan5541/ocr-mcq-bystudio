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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.R
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.components.LivingOptionChoiceCard
import com.omnisolve.overlay.ui.components.LivingPrimaryButton
import com.omnisolve.overlay.ui.components.LivingSecondaryButton
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    state: MainUiState,
    listState: LazyListState,
    onToggleOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onSelectSolverMethod: (SolverMethod) -> Unit,
    onSetCustomOpacityPct: (Int) -> Unit,
    onSelectQuestionSolveMode: (QuestionSolveMode) -> Unit,
    onToggleChargingFormat: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSignInServer: () -> Unit,
    onOpenGeminiLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val focusManager = LocalFocusManager.current
    var opacityTextInput by remember(state.customOpacityPct) {
        mutableStateOf(state.customOpacityPct.toString())
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Instant Start / Stop Floating Overlay + Start-Time 20%–100% Opacity Input & Slider
        item(key = "overlay_launcher_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .livingGlassSurface(
                        strength = GlassMaterialStrength.Hero,
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
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.isOverlayRunning) Color(0xFF10B981) else palette.accentSky
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isOverlayRunning) "Floating Overlay Active" else "Ready to Launch",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary
                        )
                    }

                    val badgeLabel = when {
                        state.advNetSpeedDisguiseEnabled -> "MB/s"
                        state.batteryCamouflage -> "91–94%"
                        else -> "ABCD"
                    }
                    val badgeActive = state.batteryCamouflage || state.advNetSpeedDisguiseEnabled
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (badgeActive) palette.accentEmerald else palette.accentSky,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (badgeActive) palette.accentEmeraldContainer else palette.accentSkyContainer
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap the Dynamic Island to solve immediately, swipe down on it to loop between methods, or tap the Orb for 2-view & scroll capture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary
                )

                if (!state.hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.accentAmberContainer)
                            .border(1.dp, palette.accentAmber, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overlay Permission Needed",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentAmber
                            )
                            Text(
                                text = "Allow Display over other apps once to launch.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        LivingSecondaryButton(
                            text = stringResource(R.string.btn_grant),
                            icon = Icons.Filled.Layers,
                            onClick = onRequestOverlayPermission,
                            testTag = "btn_perm_overlay"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Start-Time Customizable Visibility / Opacity (20% to 100% Slider + Direct Numeric Input)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (palette.isDarkObsidian) Color(0xFF141D2E) else Color(0xFFF8FAFC))
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("section_home_opacity")
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
                            Icon(
                                imageVector = Icons.Filled.Opacity,
                                contentDescription = null,
                                tint = palette.accentSky,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Overlay Visibility (${state.customOpacityPct}%)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.inkPrimary
                                )
                                Text(
                                    text = "Slide or type exact % from 20% to 100%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.inkSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        OutlinedTextField(
                            value = opacityTextInput,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(3)
                                opacityTextInput = digits
                                val parsed = digits.toIntOrNull()
                                if (parsed != null && parsed in 20..100) {
                                    onSetCustomOpacityPct(parsed)
                                }
                            },
                            singleLine = true,
                            suffix = {
                                Text(
                                    text = "%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.inkSecondary
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val clamped = (opacityTextInput.toIntOrNull() ?: state.customOpacityPct).coerceIn(20, 100)
                                    opacityTextInput = clamped.toString()
                                    onSetCustomOpacityPct(clamped)
                                    focusManager.clearFocus()
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = palette.accentSky,
                                unfocusedBorderColor = palette.cardHairlineBorder,
                                focusedTextColor = palette.inkPrimary,
                                unfocusedTextColor = palette.inkPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .width(88.dp)
                                .testTag("input_home_opacity_pct")
                        )
                    }

                    Slider(
                        value = state.customOpacityPct.toFloat(),
                        onValueChange = { newVal ->
                            val rounded = newVal.roundToInt().coerceIn(20, 100)
                            opacityTextInput = rounded.toString()
                            onSetCustomOpacityPct(rounded)
                        },
                        valueRange = 20f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.accentSky,
                            activeTrackColor = palette.accentSky
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("slider_home_opacity_pct")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Question Mode Selector (Standard / Match the Following / Complicated Math+)
                Text(
                    text = "Question Solving Mode",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuestionSolveMode.entries.forEach { mode ->
                        LivingSecondaryButton(
                            text = when (mode) {
                                QuestionSolveMode.STANDARD -> "Standard"
                                QuestionSolveMode.MATCH_FOLLOWING -> "Match Pairs"
                                QuestionSolveMode.COMPLEX_MATH -> "Math+ Deep"
                            },
                            selected = state.questionSolveMode == mode,
                            onClick = { onSelectQuestionSolveMode(mode) },
                            testTag = "btn_home_mode_${mode.id.lowercase()}",
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LivingPrimaryButton(
                    text = if (state.isOverlayRunning) {
                        stringResource(R.string.cta_stop_solver)
                    } else {
                        stringResource(R.string.cta_start_solver)
                    },
                    icon = if (state.isOverlayRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    isActiveState = state.isOverlayRunning,
                    onClick = onToggleOverlay,
                    testTag = "btn_toggle_overlay",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 2. The 3 Primary Answer Methods right on the Home Screen
        item(key = "home_answer_methods_card") {
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
                    text = stringResource(R.string.home_methods_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Pick here or swipe DOWN on the Dynamic Island anytime to scroll-loop between 1) Server Sol, 2) OCR Text, and 3) API Key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Method 1: Server Sol
                val isServerSignedIn = !state.firebaseUserId.isNullOrBlank()
                LivingOptionChoiceCard(
                    title = SolverMethod.FIREBASE_SERVER.title,
                    subtitle = SolverMethod.FIREBASE_SERVER.subtitle,
                    selected = state.solverMethod == SolverMethod.FIREBASE_SERVER,
                    icon = Icons.Filled.CloudDone,
                    badgeText = if (isServerSignedIn) "Logged In" else "Login Needed",
                    onClick = { onSelectSolverMethod(SolverMethod.FIREBASE_SERVER) },
                    testTag = "option_method_firebase"
                )
                if (state.solverMethod == SolverMethod.FIREBASE_SERVER && !isServerSignedIn) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LivingSecondaryButton(
                        text = if (state.isSigningInGoogle) "Signing in..." else "Log in to Server with Google",
                        icon = Icons.Filled.CloudDone,
                        onClick = onSignInServer,
                        testTag = "btn_home_server_login",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Method 2: OCR Text Extractor (Previous Method)
                LivingOptionChoiceCard(
                    title = SolverMethod.BACKGROUND_GEMINI.title,
                    subtitle = SolverMethod.BACKGROUND_GEMINI.subtitle,
                    selected = state.solverMethod == SolverMethod.BACKGROUND_GEMINI,
                    icon = Icons.Filled.DocumentScanner,
                    badgeText = if (state.isGoogleWebLoggedIn) "Gemini Ready" else "Built-in",
                    onClick = { onSelectSolverMethod(SolverMethod.BACKGROUND_GEMINI) },
                    testTag = "option_method_background_gemini"
                )
                if (state.solverMethod == SolverMethod.BACKGROUND_GEMINI && !state.isGoogleWebLoggedIn) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LivingSecondaryButton(
                        text = "Connect Background Gemini Session",
                        icon = Icons.Filled.DocumentScanner,
                        onClick = onOpenGeminiLogin,
                        testTag = "btn_home_gemini_login",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Method 3: API Key (Local Storage)
                val keyBadge = if (state.configuredKeyCount > 0) {
                    "${state.configuredKeyCount} Key(s) Ready"
                } else {
                    "Set in Settings"
                }
                LivingOptionChoiceCard(
                    title = SolverMethod.API_KEYS.title,
                    subtitle = SolverMethod.API_KEYS.subtitle,
                    selected = state.solverMethod == SolverMethod.API_KEYS,
                    icon = Icons.Filled.Key,
                    badgeText = keyBadge,
                    onClick = { onSelectSolverMethod(SolverMethod.API_KEYS) },
                    testTag = "option_method_api_keys"
                )
                if (state.solverMethod == SolverMethod.API_KEYS && state.configuredKeyCount == 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LivingSecondaryButton(
                        text = "Enter Local API Keys in Settings",
                        icon = Icons.Filled.Settings,
                        onClick = onOpenSettings,
                        testTag = "btn_home_enter_keys",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Active indicator if 4th Settings method ("Overlapping Google Completely") is active
                if (state.solverMethod == SolverMethod.OVERLAPPING_GOOGLE) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.accentSkyContainer)
                            .border(1.4.dp, palette.accentSky, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInFull,
                            contentDescription = null,
                            tint = palette.accentSky,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overlapping Google Completely (Active)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary
                            )
                            Text(
                                text = "Enabled from Settings. Tap 1, 2, or 3 above anytime to switch back.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkSecondary
                            )
                        }
                    }
                }
            }
        }

        // 3. Selected Setup Summary & Quick Format Switch (ABCD <-> Charging 91, 92, 93, 94)
        item(key = "selected_summary_card") {
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
                        text = stringResource(R.string.home_selected_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.inkPrimary
                    )
                    LivingSecondaryButton(
                        text = stringResource(R.string.btn_open_settings),
                        icon = Icons.Filled.Settings,
                        onClick = onOpenSettings,
                        testTag = "btn_open_settings_from_home",
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                SummaryStatusRow(
                    label = "Selected Method",
                    value = state.solverMethod.shortName
                )
                Spacer(modifier = Modifier.height(8.dp))
                val pctVal = state.customOpacityPct
                val shapeName = when (state.hudStyle) {
                    MainActivity.HUD_STYLE_TOP_STRIP -> "Ultra-Thin Top Strip ($pctVal%)"
                    MainActivity.HUD_STYLE_EDGE_TAB -> "Edge-Docked Mini Tab ($pctVal%)"
                    MainActivity.HUD_STYLE_BUBBLE -> "Mini Bubble ($pctVal%)"
                    else -> "Top Dynamic Pill ($pctVal%)"
                }
                SummaryStatusRow(
                    label = "Overlay Shape",
                    value = shapeName
                )
                Spacer(modifier = Modifier.height(8.dp))
                SummaryStatusRow(
                    label = "Question Mode",
                    value = state.questionSolveMode.fullLabel
                )
                Spacer(modifier = Modifier.height(8.dp))
                SummaryStatusRow(
                    label = "API Keys",
                    value = if (state.configuredKeyCount > 0) {
                        "${state.configuredKeyCount} active (Instant Failover)"
                    } else {
                        "None saved"
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = palette.cardHairlineBorder)
                Spacer(modifier = Modifier.height(12.dp))

                val toggleBg = when {
                    state.batteryCamouflage && palette.isDarkObsidian -> Color(0xFF0A2E26)
                    state.batteryCamouflage -> Color(0xFFECFDF5)
                    palette.isDarkObsidian -> Color(0xFF141D2E)
                    else -> Color(0xFFF1F5F9)
                }
                val toggleBorder = when {
                    state.batteryCamouflage -> palette.accentEmerald
                    palette.isDarkObsidian -> Color(0xFF334155)
                    else -> Color(0xFFCBD5E1)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(toggleBg)
                        .border(
                            width = 1.dp,
                            color = toggleBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable(
                            role = Role.Button,
                            onClick = { onToggleChargingFormat(!state.batteryCamouflage) }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("btn_quick_toggle_format"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BatteryChargingFull,
                            contentDescription = null,
                            tint = if (state.batteryCamouflage) palette.accentEmerald else palette.accentSky,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (state.batteryCamouflage) {
                                    "Display: Charging 91, 92, 93, 94"
                                } else {
                                    "Display: Option ABCD"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary
                            )
                            Text(
                                text = if (state.batteryCamouflage) {
                                    "91% = A • 92% = B • 93% = C • 94% = D"
                                } else {
                                    "Tap to switch to Charging 91, 92, 93, 94"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = "Switch answer format",
                        tint = palette.inkSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatusRow(
    label: String,
    value: String
) {
    val palette = LocalWhiteGlassPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = palette.inkPrimary
        )
    }
}
