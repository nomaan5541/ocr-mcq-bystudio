package com.omnisolve.overlay.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import kotlinx.coroutines.delay

private data class InteractiveDemoCase(
    val id: String,
    val tabLabel: String,
    val modeBadge: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val answerLetter: String,
    val explanation: String,
    val latencyLabel: String
)

private val DEMO_CASES = listOf(
    InteractiveDemoCase(
        id = "math_sigma",
        tabLabel = "Math ½·Σ",
        modeBadge = "Auto-Math Vision",
        question = "Evaluate  Σ (k = 1 to 3)  (1/2) · 2k :",
        options = listOf("A) 3", "B) 6", "C) 9", "D) 12"),
        correctIndex = 1,
        answerLetter = "B",
        explanation = "(1/2)·2k = k, so Σ(k=1..3) k = 1 + 2 + 3 = 6. Vision reads ½ & Σ directly from pixels.",
        latencyLabel = "Math Vision • Instant"
    ),
    InteractiveDemoCase(
        id = "mcq_fast",
        tabLabel = "MCQ",
        modeBadge = "Offline Room Cache",
        question = "Which organelle produces most ATP inside a cell?",
        options = listOf("A) Ribosome", "B) Mitochondria", "C) Golgi Body", "D) Lysosome"),
        correctIndex = 1,
        answerLetter = "B",
        explanation = "Mitochondria generate ATP via oxidative phosphorylation — cached offline for 0ms repeat hits.",
        latencyLabel = "Cached Offline • 0ms"
    ),
    InteractiveDemoCase(
        id = "match_cols",
        tabLabel = "Match",
        modeBadge = "Column Match Engine",
        question = "Match Column I → II:  1. H₂O  2. NaCl  3. CO₂  (i) Salt  (ii) Water  (iii) Dry Ice",
        options = listOf("A) 1-i, 2-ii, 3-iii", "B) 1-ii, 2-i, 3-iii", "C) 1-iii, 2-i, 3-ii", "D) 1-ii, 2-iii, 3-i"),
        correctIndex = 1,
        answerLetter = "B",
        explanation = "H₂O is Water (ii), NaCl is Salt (i), and solid CO₂ is Dry Ice (iii) → 1-ii, 2-i, 3-iii.",
        latencyLabel = "Match Vision • Verified"
    )
)

private enum class DemoRunState {
    IDLE,
    SCANNING,
    SOLVED
}

/**
 * 5-Second Interactive Live Demo sandbox so first-time users immediately experience how the
 * floating Prism Pill solves MCQs and Math symbols (`1/2`, `Σ`) without needing permissions first.
 */
@Composable
fun LiveInteractiveDemoCard(
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val reducedMotion = LocalReducedMotion.current
    val corner = themeSpec.cornerRadiusDp.coerceIn(12, 22).dp

    var selectedCaseIdx by remember { mutableIntStateOf(0) }
    var runState by remember { mutableStateOf(DemoRunState.IDLE) }

    val activeCase = DEMO_CASES[selectedCaseIdx]

    LaunchedEffect(runState, selectedCaseIdx) {
        if (runState == DemoRunState.SCANNING) {
            delay(if (reducedMotion) 180L else 520L)
            runState = DemoRunState.SOLVED
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(palette.glassStrong)
            .border(1.dp, palette.accentSky.copy(alpha = 0.45f), RoundedCornerShape(corner))
            .padding(if (compact) 12.dp else 14.dp)
            .testTag("card_interactive_live_demo"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header + Mode Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "TRY 5-SECOND LIVE DEMO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.accentSky,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.7.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = "Tap the mock pill to see how Prism solves",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = palette.inkSecondary,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Sample Switcher Chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DEMO_CASES.forEachIndexed { idx, demo ->
                    val selected = idx == selectedCaseIdx
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) palette.accentSky else palette.glassMedium)
                            .border(
                                1.dp,
                                if (selected) palette.accentSky else palette.cardHairlineBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(role = Role.Tab) {
                                selectedCaseIdx = idx
                                runState = DemoRunState.IDLE
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_demo_tab_${demo.id}")
                    ) {
                        Text(
                            text = demo.tabLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (selected) palette.onAccentInk else palette.inkSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Mock Quiz Question Surface
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.canvasBottom)
                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (selectedCaseIdx == 0) Icons.Filled.Calculate else Icons.Filled.Quiz,
                        contentDescription = null,
                        tint = palette.accentSky,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = activeCase.modeBadge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.inkSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        )
                    )
                }

                // Interactive Mock Floating Pill CTA
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .clip(CircleShape)
                        .background(
                            when (runState) {
                                DemoRunState.SOLVED -> palette.accentEmerald
                                else -> palette.accentSky
                            }
                        )
                        .clickable(role = Role.Button) {
                            runState = when (runState) {
                                DemoRunState.IDLE -> DemoRunState.SCANNING
                                DemoRunState.SCANNING -> DemoRunState.SOLVED
                                DemoRunState.SOLVED -> DemoRunState.IDLE
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("btn_live_demo_pill")
                ) {
                    when (runState) {
                        DemoRunState.IDLE -> {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Tap mock pill to solve",
                                tint = palette.onAccentInk,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Tap Pill to Solve",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.onAccentInk,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )
                        }
                        DemoRunState.SCANNING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = palette.onAccentInk
                            )
                            Text(
                                text = "Scanning...",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.onAccentInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )
                        }
                        DemoRunState.SOLVED -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Solved",
                                tint = palette.onAccentInk,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Ans: ${activeCase.answerLetter}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.onAccentInk,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Reset demo",
                                tint = palette.onAccentInk.copy(alpha = 0.85f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = activeCase.question,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (selectedCaseIdx == 0) FontFamily.Monospace else FontFamily.Default,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            )

            // 2x2 Options Grid
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                activeCase.options.chunked(2).forEachIndexed { rowIdx, pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pair.forEachIndexed { colIdx, opt ->
                            val globalIdx = rowIdx * 2 + colIdx
                            val isWinner = runState == DemoRunState.SOLVED && globalIdx == activeCase.correctIndex
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isWinner) palette.accentEmeraldContainer else palette.glassMedium
                                    )
                                    .border(
                                        1.dp,
                                        if (isWinner) palette.accentEmerald else palette.cardHairlineBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 9.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = opt,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isWinner) palette.accentEmerald else palette.inkPrimary,
                                        fontWeight = if (isWinner) FontWeight.ExtraBold else FontWeight.Medium,
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

            AnimatedVisibility(
                visible = runState == DemoRunState.SOLVED,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.accentEmeraldContainer)
                        .border(1.dp, palette.accentEmerald.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("box_live_demo_result"),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "${activeCase.latencyLabel} • Correct: ${activeCase.answerLetter}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.accentEmerald,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.5.sp
                        )
                    )
                    Text(
                        text = activeCase.explanation,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.inkPrimary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
        }
    }
}
