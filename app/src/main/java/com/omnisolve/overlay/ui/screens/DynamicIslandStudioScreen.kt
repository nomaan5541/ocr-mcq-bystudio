package com.omnisolve.overlay.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.contrastOnColor
import kotlin.math.roundToInt

private val PillOledBlack = Color(0xFF000000)
private val PillWhite = Color(0xFFEEEEEE)
private val PillMuted = Color(0xFF9E9EA6)
private val PillEmerald = Color(0xFF34D399)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DynamicIslandStudioScreen(
    state: MainUiState,
    onBackToHome: () -> Unit,
    onSetCustomOpacityPct: (Int) -> Unit,
    onSelectQuestionSolveMode: (QuestionSolveMode) -> Unit,
    onSelectHudStyle: (String) -> Unit,
    onToggleDfTwoPartCapture: (Boolean) -> Unit,
    onToggleDfScrollTimer: (Boolean) -> Unit,
    onToggleDfComplexMode: (Boolean) -> Unit,
    onToggleDfDiagramVision: (Boolean) -> Unit,
    onToggleDfQuickCrop: (Boolean) -> Unit,
    onToggleDfSwipeMethodLoop: (Boolean) -> Unit,
    onToggleAdvSplitPill: (Boolean) -> Unit,
    onToggleAdvIslandGestures: (Boolean) -> Unit,
    onToggleChargingFormat: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = themeSpec.cornerRadiusDp.coerceIn(14, 24).dp
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var opacityTextInput by remember(state.customOpacityPct) {
        mutableStateOf(state.customOpacityPct.toString())
    }
    var showAdvancedControls by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("dynamic_island_studio_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. LIVE FLOATING PILL PREVIEW & SIZE/STYLE PRESETS ──
        item(key = "island_live_prototype_card") {
            IosDynamicIslandStudioCard(
                state = state,
                onSelectHudStyle = onSelectHudStyle,
                onToggleChargingFormat = onToggleChargingFormat
            )
        }

        // ── 2. PILL TRANSPARENCY & QUESTION TYPE ──
        item(key = "island_opacity_and_solve_mode_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                    .padding(16.dp)
                    .testTag("section_home_opacity"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(palette.accentSkyContainer)
                            .border(1.dp, palette.accentSky.copy(alpha = 0.45f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Opacity,
                            contentDescription = null,
                            tint = palette.accentSky,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pill Transparency",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary
                            )
                        )
                        Text(
                            text = "Pick Subtle, Balanced, or Dark, or slide to adjust",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = palette.inkSecondary
                        )
                    }
                    OutlinedTextField(
                        value = opacityTextInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(3)
                            opacityTextInput = digits
                            val parsed = digits.toIntOrNull()
                            if (parsed != null && parsed in 20..100) onSetCustomOpacityPct(parsed)
                        },
                        singleLine = true,
                        suffix = {
                            Text(
                                "%",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.inkMuted
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            val clamped = (opacityTextInput.toIntOrNull() ?: state.customOpacityPct)
                                .coerceIn(20, 100)
                            opacityTextInput = clamped.toString()
                            onSetCustomOpacityPct(clamped)
                            focusManager.clearFocus()
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.accentSky,
                            unfocusedBorderColor = palette.cardHairlineBorder,
                            focusedTextColor = palette.inkPrimary,
                            unfocusedTextColor = palette.inkPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(82.dp)
                            .testTag("input_home_opacity_pct")
                    )
                }

                // Simple 1-Word Look Presets: Subtle / Balanced / Dark
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val lookPresets = listOf(
                        40 to "Subtle",
                        75 to "Balanced",
                        100 to "Dark"
                    )
                    lookPresets.forEach { (presetPct, label) ->
                        val active = when (presetPct) {
                            40 -> state.customOpacityPct <= 55
                            75 -> state.customOpacityPct in 56..89
                            else -> state.customOpacityPct >= 90
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (active) palette.accentSkyContainer else palette.glassMedium
                                )
                                .border(
                                    1.dp,
                                    if (active) palette.accentSky else palette.cardHairlineBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(role = Role.Button) {
                                    opacityTextInput = presetPct.toString()
                                    onSetCustomOpacityPct(presetPct)
                                }
                                .padding(vertical = 8.dp)
                                .testTag("btn_opacity_preset_${label.lowercase()}")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (active) palette.accentSky else palette.inkSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                Slider(
                    value = state.customOpacityPct.toFloat(),
                    onValueChange = {
                        val rounded = it.roundToInt().coerceIn(20, 100)
                        opacityTextInput = rounded.toString()
                        onSetCustomOpacityPct(rounded)
                    },
                    valueRange = 20f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accentSky,
                        activeTrackColor = palette.accentSky,
                        inactiveTrackColor = palette.glassSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_home_opacity_pct")
                )
            }
        }

        // ── 2B. QUESTION TYPE CARD ──
        item(key = "island_question_type_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = palette.accentSky,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Question Type",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuestionSolveMode.entries.forEach { mode ->
                        val isSelected = state.questionSolveMode == mode
                        val selectedInk = contrastOnColor(palette.accentSky)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier.background(palette.accentSky)
                                    } else {
                                        Modifier
                                            .background(palette.glassMedium)
                                            .border(
                                                1.dp,
                                                palette.cardHairlineBorder,
                                                RoundedCornerShape(12.dp)
                                            )
                                    }
                                )
                                .clickable(role = Role.Button) { onSelectQuestionSolveMode(mode) }
                                .padding(horizontal = 6.dp, vertical = 10.dp)
                                .testTag("btn_home_mode_${mode.id.lowercase()}")
                        ) {
                            Text(
                                text = when (mode) {
                                    QuestionSolveMode.STANDARD -> "Quick MCQ"
                                    QuestionSolveMode.MATCH_FOLLOWING -> "Match List"
                                    QuestionSolveMode.COMPLEX_MATH -> "Math & Steps"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (isSelected) selectedInk else palette.inkSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // ── 3. COLLAPSIBLE MORE PILL CONTROLS (ADVANCED) ──
        item(key = "island_more_controls_accordion") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) {
                            showAdvancedControls = !showAdvancedControls
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .testTag("btn_toggle_island_advanced"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "More Pill Controls (Advanced)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                        Text(
                            text = "Two-part capture, scroll timer, crop box, and gestures",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                    Icon(
                        imageVector = if (showAdvancedControls) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = palette.accentSky
                    )
                }

                AnimatedVisibility(visible = showAdvancedControls) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IslandFeatureToggleChip(
                            label = "Two-Part Capture (Top + Bottom)",
                            enabled = state.dfTwoPartCaptureEnabled,
                            onClick = { onToggleDfTwoPartCapture(!state.dfTwoPartCaptureEnabled) }
                        )
                        IslandFeatureToggleChip(
                            label = "2-Second Scroll Timer",
                            enabled = state.dfScrollTimerEnabled,
                            onClick = { onToggleDfScrollTimer(!state.dfScrollTimerEnabled) }
                        )
                        IslandFeatureToggleChip(
                            label = "Quick Mode Switch Chip",
                            enabled = state.dfComplexModeEnabled,
                            onClick = { onToggleDfComplexMode(!state.dfComplexModeEnabled) }
                        )
                        IslandFeatureToggleChip(
                            label = "Swipe to Switch Style",
                            enabled = state.dfSwipeMethodLoopEnabled,
                            onClick = { onToggleDfSwipeMethodLoop(!state.dfSwipeMethodLoopEnabled) }
                        )
                        IslandFeatureToggleChip(
                            label = "Diagram & Chart Boost",
                            enabled = state.dfDiagramVisionEnabled,
                            onClick = { onToggleDfDiagramVision(!state.dfDiagramVisionEnabled) }
                        )
                        IslandFeatureToggleChip(
                            label = "Crop Question Area",
                            enabled = state.dfQuickCropEnabled,
                            onClick = { onToggleDfQuickCrop(!state.dfQuickCropEnabled) }
                        )
                        IslandFeatureToggleChip(
                            label = "Show Answer + Word",
                            enabled = state.advSplitPillEnabled,
                            onClick = { onToggleAdvSplitPill(!state.advSplitPillEnabled) }
                        )
                        IslandFeatureToggleChip(
                            label = "Swipe Through Past Answers",
                            enabled = state.advIslandGesturesEnabled,
                            onClick = { onToggleAdvIslandGestures(!state.advIslandGesturesEnabled) }
                        )
                    }
                }
            }
        }

        // ── 4. DONE & BACK TO HOME BUTTON ──
        item(key = "studio_back_button") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.glassMedium)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onBackToHome)
                    .padding(horizontal = 16.dp, vertical = 13.dp)
                    .testTag("btn_island_studio_done"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = palette.inkPrimary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Done & Back to Home",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                )
            }
        }
    }
}

@Composable
fun IosDynamicIslandStudioCard(
    state: MainUiState,
    onSelectHudStyle: (String) -> Unit,
    onToggleChargingFormat: (Boolean) -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = themeSpec.cornerRadiusDp.coerceIn(14, 24).dp

    // 0 = Ready, 1 = Reading, 2 = Solved, 3 = Expanded
    var previewMorphState by remember { mutableIntStateOf(2) }

    val islandWidth by animateDpAsState(
        targetValue = when (previewMorphState) {
            0 -> 178.dp
            1 -> 244.dp
            2 -> 238.dp
            else -> 322.dp
        },
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandWidthMorph"
    )

    val islandHeight by animateDpAsState(
        targetValue = if (previewMorphState == 3) 104.dp else 44.dp,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandHeightMorph"
    )

    val cornerDp by animateDpAsState(
        targetValue = if (previewMorphState == 3) 26.dp else 32.dp,
        animationSpec = tween(220),
        label = "islandCornerMorph"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "islandWave")
    val wavePulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wavePulse"
    )

    val previewAlpha = (state.customOpacityPct / 100f).coerceIn(0.2f, 1f)
    val accentColor = palette.accentSky
    val selectedInk = contrastOnColor(accentColor)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Floating Pill Look",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap the black pill below to preview • ${state.customOpacityPct}% see-through",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            color = palette.inkSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.accentSkyContainer)
                    .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
            }
        }

        // Authentic iPhone-style OLED Black Pill Preview Stage
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B0C10), Color(0xFF14161D))
                    )
                )
                .border(1.dp, Color(0xFF272A34), RoundedCornerShape(20.dp))
                .padding(top = 8.dp, bottom = 18.dp, start = 10.dp, end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF22252F))
            )
            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .width(islandWidth)
                    .height(islandHeight)
                    .alpha(previewAlpha)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(cornerDp),
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(RoundedCornerShape(cornerDp))
                    .background(PillOledBlack)
                    .border(
                        width = if (previewMorphState == 1) 1.8.dp else 1.dp,
                        brush = if (previewMorphState == 1) {
                            Brush.sweepGradient(
                                listOf(
                                    accentColor.copy(alpha = 0.15f),
                                    accentColor,
                                    Color.White,
                                    accentColor,
                                    accentColor.copy(alpha = 0.15f)
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF2C2F3A),
                                    Color(0xFF3E4250),
                                    Color(0xFF2C2F3A)
                                )
                            )
                        },
                        shape = RoundedCornerShape(cornerDp)
                    )
                    .clickable(role = Role.Button) {
                        previewMorphState = (previewMorphState + 1) % 4
                    }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF08090D))
                                .border(1.2.dp, Color(0xFF262A36), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10192D))
                                    .border(
                                        1.dp,
                                        if (previewMorphState == 1) accentColor else Color(0xFF2E4068),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3B82F6).copy(alpha = 0.85f))
                                )
                            }
                        }
                        Spacer(Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    when (previewMorphState) {
                                        1 -> accentColor.copy(alpha = wavePulse)
                                        2 -> PillEmerald
                                        else -> accentColor
                                    }
                                )
                        )
                    }

                    AnimatedContent(
                        targetState = previewMorphState,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                        label = "islandCenterText",
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    ) { targetState ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (targetState == 1) {
                                val bars = listOf(0.45f, 0.95f, 0.65f, 0.85f)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    bars.forEachIndexed { idx, factor ->
                                        val h = (6 + 10 * ((wavePulse * factor + idx * 0.1f) % 1f)).dp
                                        Box(
                                            modifier = Modifier
                                                .width(2.5.dp)
                                                .height(h)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(accentColor)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = when (targetState) {
                                    0 -> if (state.batteryCamouflage) "Charging" else "Ready"
                                    1 -> "Reading..."
                                    2 -> if (state.advSplitPillEnabled) "B • Mitochondria" else "Answer"
                                    else -> state.solverMethod.shortName
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = PillWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    when (previewMorphState) {
                                        1 -> Color(0xFF2A1014)
                                        2, 3 -> Color(0xFF0E2920)
                                        else -> Color(0xFF16181F)
                                    }
                                )
                                .border(
                                    1.2.dp,
                                    when (previewMorphState) {
                                        1 -> accentColor
                                        2, 3 -> Color(0xFF10B981)
                                        else -> Color(0xFF2B2E38)
                                    },
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    state.batteryCamouflage -> "92%"
                                    previewMorphState == 0 -> "•"
                                    previewMorphState == 1 -> "READ"
                                    else -> "B"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = when (previewMorphState) {
                                        1 -> Color(0xFFFF8A90)
                                        2, 3 -> PillEmerald
                                        else -> PillWhite
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1
                            )
                        }
                        if (previewMorphState == 3) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1C1E26)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "×",
                                    color = PillMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (previewMorphState == 3) {
                    Spacer(Modifier.height(7.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.dfTwoPartCaptureEnabled) {
                            MiniIslandChip("Part 1", PillWhite, accentColor)
                            MiniIslandChip("Part 2", PillEmerald, accentColor)
                        }
                        if (state.dfScrollTimerEnabled) {
                            MiniIslandChip("2s↓", PillWhite, accentColor)
                        }
                        if (state.dfComplexModeEnabled) {
                            MiniIslandChip(state.questionSolveMode.chipLabel, Color(0xFFFF8A90), accentColor)
                        }
                        MiniIslandChip("↻ ${state.solverMethod.shortName}", PillEmerald, accentColor)
                    }
                }
            }
        }

        // State Switcher Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val stateLabels = listOf("Ready", "Reading", "Answer", "Expanded")
            stateLabels.forEachIndexed { idx, label ->
                val active = previewMorphState == idx
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (active) accentColor else palette.glassMedium)
                        .border(
                            1.dp,
                            if (active) accentColor else palette.cardHairlineBorder,
                            RoundedCornerShape(50.dp)
                        )
                        .clickable(role = Role.Tab) { previewMorphState = idx }
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (active) selectedInk else palette.inkSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Simple 1-word Size Presets
        Text(
            text = "Pill Size",
            style = MaterialTheme.typography.labelMedium.copy(
                color = palette.inkPrimary,
                fontWeight = FontWeight.Bold
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val styles = listOf(
                MainActivity.HUD_STYLE_TOP_STRIP to "Small",
                MainActivity.HUD_STYLE_ISLAND to "Normal",
                MainActivity.HUD_STYLE_BUBBLE to "Large"
            )
            styles.forEach { (styleId, label) ->
                val isSelected = state.hudStyle == styleId
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) palette.accentSkyContainer else palette.glassMedium)
                        .border(
                            1.2.dp,
                            if (isSelected) accentColor else palette.cardHairlineBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(role = Role.RadioButton) { onSelectHudStyle(styleId) }
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .testTag("btn_pill_size_${label.lowercase()}")
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isSelected) accentColor else palette.inkSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Answer Disguise Quick Toggle (A/B/C/D vs Charging 91-94%)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (state.batteryCamouflage) palette.glassTintedMint else palette.glassMedium)
                .border(
                    1.dp,
                    if (state.batteryCamouflage) palette.accentMint else palette.cardHairlineBorder,
                    RoundedCornerShape(12.dp)
                )
                .clickable(role = Role.Switch) { onToggleChargingFormat(!state.batteryCamouflage) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.BatteryChargingFull,
                    contentDescription = null,
                    tint = if (state.batteryCamouflage) palette.accentMint else palette.inkSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "Disguise Answer as Charging %",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Shows 91%–94% instead of A, B, C, D",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.inkSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }
            Text(
                text = if (state.batteryCamouflage) "ON" else "OFF",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = if (state.batteryCamouflage) palette.accentMint else palette.inkMuted,
                    fontWeight = FontWeight.ExtraBold
                )
            )
        }
    }
}

@Composable
private fun MiniIslandChip(text: String, textColor: Color, borderTint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF16171C))
            .border(1.dp, borderTint.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun IslandFeatureToggleChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(if (enabled) palette.accentSkyContainer else palette.glassMedium)
            .border(
                width = 1.dp,
                color = if (enabled) palette.accentSky else palette.cardHairlineBorder,
                shape = RoundedCornerShape(50.dp)
            )
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (enabled) palette.accentSky else palette.inkMuted)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (enabled) palette.accentSky else palette.inkSecondary,
                fontWeight = if (enabled) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.5.sp
            )
        )
    }
}
