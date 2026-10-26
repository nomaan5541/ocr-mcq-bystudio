package com.omnisolve.overlay.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnisolve.overlay.R
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.PracticeSample
import com.omnisolve.overlay.ui.components.LivingPrimaryButton
import com.omnisolve.overlay.ui.components.LivingSecondaryButton
import com.omnisolve.overlay.ui.components.LivingSwitchRow
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.ScrollRevealSection
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import kotlin.math.roundToInt

@Composable
fun PracticeScreen(
    state: MainUiState,
    sample: PracticeSample,
    listState: LazyListState,
    onSolveQuestion: () -> Unit,
    onNextSample: () -> Unit,
    onCustomQuestionChange: (String) -> Unit,
    onToggleInstantDetect: (Boolean) -> Unit,
    onScanIntervalChange: (Int) -> Unit,
    onToggleClipboardCopy: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current

    val scrollFraction by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex * 240f + listState.firstVisibleItemScrollOffset) / 900f
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("practice_screen_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Interactive Sample & Custom Question Solver Card
        item(key = "practice_card") {
            ScrollRevealSection(index = 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .livingGlassSurface(
                            strength = GlassMaterialStrength.Hero,
                            cornerRadius = 26.dp,
                            scrollParallaxFactor = scrollFraction
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.practice_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = palette.inkPrimary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.practice_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sample Question Preview Box with Optical Light Scan Sweep
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xF5FFFFFF))
                            .border(1.dp, Color(0xFFD6E4F0), RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (state.customPracticeInput.isNotBlank()) "Custom Question" else sample.category,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.accentSky
                                )
                                LivingSecondaryButton(
                                    text = stringResource(R.string.btn_next_sample),
                                    icon = Icons.Filled.Refresh,
                                    onClick = onNextSample,
                                    testTag = "btn_next_sample",
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            if (state.customPracticeInput.isBlank()) {
                                Text(
                                    text = sample.question,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = palette.inkPrimary
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                sample.options.forEach { optionText ->
                                    val optionLetter = optionText.take(1).uppercase()
                                    val isWinner = state.practiceSolvedLetter == optionLetter
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isWinner) palette.accentMintContainer else Color(0xFFF8FAFC)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isWinner) palette.accentMint else Color(0xFFE2E8F0),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = optionText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isWinner) Color(0xFF065F46) else palette.inkPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isWinner) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Correct option",
                                                tint = palette.accentMint,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Solving your custom question below:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.inkSecondary
                                )
                            }
                        }

                        // Optical scanning beam while solving
                        if (state.isPracticeScanning && !reducedMotion) {
                            val infinite = rememberInfiniteTransition(label = "scanBeam")
                            val sweepY by infinite.animateFloat(
                                initialValue = 0f,
                                targetValue = 180f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 700, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "beamOffsetY"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .offset(y = sweepY.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                palette.accentSkyBright,
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.customPracticeInput,
                        onValueChange = onCustomQuestionChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.practice_custom_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkSecondary
                            )
                        },
                        minLines = 2,
                        maxLines = 5,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color(0xF2FFFFFF),
                            focusedBorderColor = palette.accentSky,
                            unfocusedBorderColor = Color(0xFF94A3B8),
                            focusedTextColor = palette.inkPrimary,
                            unfocusedTextColor = palette.inkPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_custom_practice_question")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LivingPrimaryButton(
                        text = if (state.isPracticeScanning) "Reading & Solving..." else stringResource(R.string.btn_solve_sample),
                        icon = Icons.Filled.AutoAwesome,
                        onClick = onSolveQuestion,
                        testTag = "btn_solve_practice",
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.practiceStatusNote != null && state.practiceSolvedLetter == null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = state.practiceStatusNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.warningAmber
                        )
                    }

                    // Solved Answer Reveal Card
                    AnimatedVisibility(
                        visible = state.practiceSolvedLetter != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .fillMaxWidth()
                                .livingGlassSurface(
                                    strength = GlassMaterialStrength.AccentMint,
                                    cornerRadius = 18.dp
                                )
                                .padding(16.dp)
                                .testTag("card_practice_result")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(palette.accentMint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = state.practiceSolvedLetter ?: "?",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Best Answer: Option ${state.practiceSolvedLetter ?: ""}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF065F46)
                                    )
                                    if (state.practiceStatusNote != null) {
                                        Text(
                                            text = state.practiceStatusNote,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.accentMint
                                        )
                                    }
                                }
                            }
                            if (!state.practiceExplanation.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = state.practiceExplanation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.inkPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Screen Check Timing & Clipboard Settings
        item(key = "timing_settings") {
            ScrollRevealSection(index = 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .livingGlassSurface(
                            strength = GlassMaterialStrength.Card,
                            cornerRadius = 24.dp,
                            scrollParallaxFactor = scrollFraction
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.scan_timing_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.scan_timing_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary
                        )
                    }

                    LivingSwitchRow(
                        title = stringResource(R.string.instant_detect_title),
                        subtitle = stringResource(R.string.instant_detect_desc),
                        checked = state.instantTriggerEnabled,
                        onCheckedChange = onToggleInstantDetect,
                        icon = Icons.Filled.Speed,
                        testTag = "switch_instant_detect"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x99F8FAFC))
                            .padding(14.dp)
                    ) {
                        val speedLabel = when {
                            state.scanIntervalSec <= 1 -> "Fastest (1s)"
                            state.scanIntervalSec in 2..3 -> "Recommended (${state.scanIntervalSec}s)"
                            state.scanIntervalSec in 4..6 -> "Relaxed (${state.scanIntervalSec}s)"
                            else -> "Slow & Gentle (${state.scanIntervalSec}s)"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Check Screen Every ${state.scanIntervalSec}s",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.inkPrimary
                            )
                            Text(
                                text = speedLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.accentSky
                            )
                        }
                        Slider(
                            value = state.scanIntervalSec.toFloat(),
                            onValueChange = { onScanIntervalChange(it.roundToInt()) },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.accentSky,
                                activeTrackColor = palette.accentSky,
                                inactiveTrackColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("slider_scan_interval")
                        )
                    }

                    LivingSwitchRow(
                        title = stringResource(R.string.clipboard_copy_title),
                        subtitle = stringResource(R.string.clipboard_copy_desc),
                        checked = state.autoCopyClipboard,
                        onCheckedChange = onToggleClipboardCopy,
                        icon = Icons.Filled.ContentCopy,
                        testTag = "switch_auto_copy_clipboard"
                    )
                }
            }
        }
    }
}
