package com.omnisolve.overlay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.LaptopChromebook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.R
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.components.LivingOptionChoiceCard
import com.omnisolve.overlay.ui.components.LivingSecondaryButton
import com.omnisolve.overlay.ui.components.LivingSwitchRow
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.ScrollRevealSection
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ControlsScreen(
    state: MainUiState,
    listState: LazyListState,
    onSelectHudStyle: (String) -> Unit,
    onSelectOpacityIndex: (Int) -> Unit,
    onSelectDisplayMode: (String) -> Unit,
    onToggleAutoClick: (Boolean) -> Unit,
    onToggleAutoNext: (Boolean) -> Unit,
    onToggleNaturalTiming: (Boolean) -> Unit,
    onChangeNextDelayMs: (Int) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onTestVibration: () -> Unit,
    onToggleBatteryCamo: (Boolean) -> Unit,
    onUpdateCompanionSync: (Boolean, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current

    var hostInput by remember(state.companionHost) { mutableStateOf(state.companionHost) }
    var portInput by remember(state.companionPort) { mutableStateOf(state.companionPort.toString()) }

    val scrollFraction by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex * 240f + listState.firstVisibleItemScrollOffset) / 900f
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("controls_screen_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Floating Bar Appearance & Brightness
        item(key = "floating_appearance") {
            ScrollRevealSection(index = 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .livingGlassSurface(
                            strength = GlassMaterialStrength.Card,
                            cornerRadius = 24.dp,
                            scrollParallaxFactor = scrollFraction
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.controls_style_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.controls_style_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary
                        )
                    }

                    LivingOptionChoiceCard(
                        title = stringResource(R.string.style_island_title),
                        subtitle = stringResource(R.string.style_island_desc),
                        selected = state.hudStyle == MainActivity.HUD_STYLE_ISLAND,
                        icon = Icons.Filled.SmartButton,
                        onClick = { onSelectHudStyle(MainActivity.HUD_STYLE_ISLAND) },
                        testTag = "option_hud_island"
                    )

                    LivingOptionChoiceCard(
                        title = stringResource(R.string.style_bubble_title),
                        subtitle = stringResource(R.string.style_bubble_desc),
                        selected = state.hudStyle == MainActivity.HUD_STYLE_BUBBLE,
                        icon = Icons.Filled.BubbleChart,
                        onClick = { onSelectHudStyle(MainActivity.HUD_STYLE_BUBBLE) },
                        testTag = "option_hud_bubble"
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.opacity_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.inkPrimary
                    )

                    val opacityLabels = listOf("100%", "90%", "50%", "25%")
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
                                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.window_mode_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.inkPrimary
                    )

                    LivingOptionChoiceCard(
                        title = stringResource(R.string.window_compact_title),
                        subtitle = stringResource(R.string.window_compact_desc),
                        selected = state.displayMode == MainActivity.MODE_STEALTH,
                        icon = Icons.Filled.ViewCompact,
                        onClick = { onSelectDisplayMode(MainActivity.MODE_STEALTH) },
                        testTag = "option_mode_compact"
                    )

                    LivingOptionChoiceCard(
                        title = stringResource(R.string.window_full_title),
                        subtitle = stringResource(R.string.window_full_desc),
                        selected = state.displayMode == MainActivity.MODE_WINDOW,
                        icon = Icons.Filled.ChatBubbleOutline,
                        onClick = { onSelectDisplayMode(MainActivity.MODE_WINDOW) },
                        testTag = "option_mode_window"
                    )
                }
            }
        }

        // 2. Hands-Free Auto-Tap Helper
        item(key = "hands_free_autotap") {
            ScrollRevealSection(index = 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .livingGlassSurface(
                            strength = GlassMaterialStrength.AccentMint,
                            cornerRadius = 24.dp,
                            scrollParallaxFactor = scrollFraction
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.autotap_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.autotap_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary
                        )
                    }

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
                        icon = Icons.Filled.FastForward,
                        testTag = "switch_auto_next"
                    )

                    LivingSwitchRow(
                        title = stringResource(R.string.autotap_natural_title),
                        subtitle = stringResource(R.string.autotap_natural_desc),
                        checked = state.naturalTimingEnabled,
                        onCheckedChange = onToggleNaturalTiming,
                        icon = Icons.Filled.Schedule,
                        testTag = "switch_natural_timing"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xB8FFFFFF))
                            .padding(14.dp)
                    ) {
                        val secFormatted = String.format(Locale.US, "%.1fs", state.nextDelayMs / 1000f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Wait Before Next Question",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.inkPrimary
                            )
                            Text(
                                text = secFormatted,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.accentMint
                            )
                        }
                        Slider(
                            value = ((state.nextDelayMs - 500) / 500f),
                            onValueChange = { step ->
                                val ms = 500 + step.roundToInt() * 500
                                onChangeNextDelayMs(ms)
                            },
                            valueRange = 0f..6f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.accentMint,
                                activeTrackColor = palette.accentMint,
                                inactiveTrackColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("slider_next_delay")
                        )
                    }
                }
            }
        }

        // 3. Quiet Vibration & Disguise
        item(key = "quiet_alerts") {
            ScrollRevealSection(index = 2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .livingGlassSurface(
                            strength = GlassMaterialStrength.Card,
                            cornerRadius = 24.dp,
                            scrollParallaxFactor = scrollFraction
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.quiet_alerts_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.quiet_alerts_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary
                        )
                    }

                    LivingSwitchRow(
                        title = stringResource(R.string.haptic_title),
                        subtitle = stringResource(R.string.haptic_desc),
                        checked = state.hapticEnabled,
                        onCheckedChange = onToggleHaptics,
                        icon = Icons.Filled.Vibration,
                        testTag = "switch_haptics"
                    )

                    LivingSecondaryButton(
                        text = stringResource(R.string.btn_test_vibration),
                        icon = Icons.Filled.Vibration,
                        onClick = onTestVibration,
                        testTag = "btn_test_vibration",
                        modifier = Modifier.fillMaxWidth()
                    )

                    LivingSwitchRow(
                        title = stringResource(R.string.camo_title),
                        subtitle = stringResource(R.string.camo_desc),
                        checked = state.batteryCamouflage,
                        onCheckedChange = onToggleBatteryCamo,
                        icon = Icons.Filled.BatteryChargingFull,
                        testTag = "switch_battery_camo"
                    )
                }
            }
        }

        // 4. Computer Wi-Fi Sync
        item(key = "computer_wifi_sync") {
            ScrollRevealSection(index = 3) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .livingGlassSurface(
                            strength = GlassMaterialStrength.Card,
                            cornerRadius = 24.dp,
                            scrollParallaxFactor = scrollFraction
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.companion_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.inkPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.companion_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.companionSyncEnabled) "Connected" else "Off",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.companionSyncEnabled) palette.accentMint else palette.inkMuted
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = {
                                hostInput = it
                                if (state.companionSyncEnabled) {
                                    onUpdateCompanionSync(true, it, portInput)
                                }
                            },
                            label = { Text("Computer IP", color = palette.inkSecondary) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xF0FFFFFF),
                                focusedBorderColor = palette.accentSky,
                                unfocusedBorderColor = Color(0xFF94A3B8),
                                focusedTextColor = palette.inkPrimary,
                                unfocusedTextColor = palette.inkPrimary,
                                focusedLabelColor = palette.accentSky,
                                unfocusedLabelColor = palette.inkSecondary
                            ),
                            modifier = Modifier
                                .weight(1.8f)
                                .testTag("input_companion_host")
                        )

                        OutlinedTextField(
                            value = portInput,
                            onValueChange = {
                                portInput = it
                                if (state.companionSyncEnabled) {
                                    onUpdateCompanionSync(true, hostInput, it)
                                }
                            },
                            label = { Text("Port", color = palette.inkSecondary) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xF0FFFFFF),
                                focusedBorderColor = palette.accentSky,
                                unfocusedBorderColor = Color(0xFF94A3B8),
                                focusedTextColor = palette.inkPrimary,
                                unfocusedTextColor = palette.inkPrimary,
                                focusedLabelColor = palette.accentSky,
                                unfocusedLabelColor = palette.inkSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_companion_port")
                        )
                    }

                    LivingSwitchRow(
                        title = stringResource(R.string.companion_toggle_title),
                        subtitle = "Listens on Wi-Fi ($hostInput:$portInput) or pair automatically with QR link.",
                        checked = state.companionSyncEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateCompanionSync(enabled, hostInput, portInput)
                        },
                        icon = Icons.Filled.LaptopChromebook,
                        testTag = "switch_companion_sync"
                    )
                }
            }
        }
    }
}
