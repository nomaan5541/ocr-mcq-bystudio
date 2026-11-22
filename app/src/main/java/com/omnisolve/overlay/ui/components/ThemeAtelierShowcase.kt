package com.omnisolve.overlay.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.LocalSpringPhysics
import com.omnisolve.overlay.ui.physics.MotionTokens
import com.omnisolve.overlay.ui.physics.kineticAccentRim
import com.omnisolve.overlay.ui.physics.liquidPressScale
import com.omnisolve.overlay.ui.physics.liquidSheenSweep
import com.omnisolve.overlay.ui.physics.topGlassSpecularHighlight
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.PrismCurves
import com.omnisolve.overlay.ui.theme.RemoteThemeSpec
import com.omnisolve.overlay.ui.theme.SurfaceEffectStyle
import com.omnisolve.overlay.ui.theme.ThemeAnimationIntensity
import com.omnisolve.overlay.ui.theme.contrastOnColor
import com.omnisolve.overlay.ui.theme.parseHexColorOr
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private enum class StageDemoPhase {
    IDLE,
    SCANNING,
    SOLVED
}

/**
 * Interactive Live Theme & Motion Playground displayed at the top of Theme Studio.
 * Users can tap the miniature Floating Dynamic Island to experience real-time spring
 * expansion, optical laser sweep, and verified MCQ answer reveal in the active theme.
 */
@Composable
fun PrismLiveThemeShowcaseStage(
    activeSpec: RemoteThemeSpec,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current
    val springCfg = LocalSpringPhysics.current
    val haptics = LocalHapticFeedback.current

    var phase by remember { mutableStateOf(StageDemoPhase.IDLE) }
    var demoTriggerTick by remember { mutableIntStateOf(0) }

    // Trigger a short live preview cycle whenever the user taps the stage or picks a new theme
    LaunchedEffect(activeSpec.themeId, demoTriggerTick) {
        if (demoTriggerTick == 0 && phase == StageDemoPhase.IDLE) {
            return@LaunchedEffect
        }
        phase = StageDemoPhase.SCANNING
        delay(if (reducedMotion) 260L else 760L)
        phase = StageDemoPhase.SOLVED
        delay(2200L)
        phase = StageDemoPhase.IDLE
    }

    val stageAnimEnabled = !reducedMotion && activeSpec.animationIntensity != ThemeAnimationIntensity.SUBTLE
    val ambientPhase = if (stageAnimEnabled) {
        val inf = rememberInfiniteTransition(label = "stage_ambient")
        val p by inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (activeSpec.animationIntensity == ThemeAnimationIntensity.EXTREME_3D) 4200 else 7600,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "stage_ambient_phase"
        )
        p
    } else 0.42f

    val scanSweep = if (phase == StageDemoPhase.SCANNING && !reducedMotion) {
        val inf = rememberInfiniteTransition(label = "stage_scan_beam")
        val y by inf.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 560, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "stage_scan_y"
        )
        y
    } else 0.5f

    val stageCorner = PrismCurves.heroCorner(activeSpec)
    val innerCorner = PrismCurves.cardCorner(activeSpec)
    val pillInteraction = remember { MutableInteractionSource() }

    val islandWidth by animateDpAsState(
        targetValue = when (phase) {
            StageDemoPhase.IDLE -> 158.dp
            StageDemoPhase.SCANNING -> 196.dp
            StageDemoPhase.SOLVED -> 212.dp
        },
        animationSpec = MotionTokens.liquidMorph(reducedMotion, springCfg),
        label = "stage_island_width"
    )

    val islandBorderColor by animateColorAsState(
        targetValue = when (phase) {
            StageDemoPhase.IDLE -> palette.cardHairlineBorder
            StageDemoPhase.SCANNING -> palette.accentSky
            StageDemoPhase.SOLVED -> palette.accentMint
        },
        animationSpec = tween(260),
        label = "stage_island_border"
    )

    val confidenceProgress by animateFloatAsState(
        targetValue = when (phase) {
            StageDemoPhase.IDLE -> 0.24f
            StageDemoPhase.SCANNING -> 0.68f
            StageDemoPhase.SOLVED -> 0.98f
        },
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f),
        label = "stage_confidence_bar"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (palette.isDarkObsidian) 12.dp else 5.dp,
                shape = RoundedCornerShape(stageCorner),
                ambientColor = Color.Black.copy(alpha = 0.75f),
                spotColor = palette.accentSky.copy(alpha = 0.30f)
            )
            .clip(RoundedCornerShape(stageCorner))
            .background(palette.canvasTop)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(stageCorner))
            .kineticAccentRim(
                cornerRadius = stageCorner,
                accentColor = if (phase == StageDemoPhase.SOLVED) palette.accentMint else palette.accentSky,
                enabled = stageAnimEnabled,
                strokeWidth = 1.3.dp,
                durationMs = if (activeSpec.animationIntensity == ThemeAnimationIntensity.EXTREME_3D) 3200 else 5600
            )
            .topGlassSpecularHighlight(stageCorner, alpha = if (palette.isDarkObsidian) 0.16f else 0.30f)
            .testTag("theme_live_preview_stage")
    ) {
        // Live miniature architectural canvas viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(RoundedCornerShape(topStart = stageCorner, topEnd = stageCorner))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawMiniSurfaceEffect(
                    effect = activeSpec.surfaceEffect,
                    accent = palette.accentSky,
                    accentBright = palette.accentSkyBright,
                    muted = palette.inkMuted,
                    isDark = palette.isDarkObsidian,
                    phase = ambientPhase
                )

                // Optical scan laser during active demo pulse
                if (phase == StageDemoPhase.SCANNING) {
                    val beamY = size.height * scanSweep
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                palette.accentSky.copy(alpha = 0.75f),
                                Color.White.copy(alpha = 0.95f),
                                palette.accentSky.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(16.dp.toPx(), beamY),
                        end = Offset(size.width - 16.dp.toPx(), beamY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive Live Floating Dynamic Island Capsule
                Box(
                    modifier = Modifier
                        .width(islandWidth)
                        .minimumInteractiveComponentSize()
                        .liquidPressScale(pillInteraction, pressedScale = 0.94f)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            spotColor = islandBorderColor.copy(alpha = 0.48f)
                        )
                        .clip(CircleShape)
                        .background(
                            if (palette.isDarkObsidian) Color(0xFF060709) else palette.glassStrong
                        )
                        .border(1.25.dp, islandBorderColor, CircleShape)
                        .liquidSheenSweep(
                            enabled = phase != StageDemoPhase.IDLE,
                            tint = if (phase == StageDemoPhase.SOLVED) palette.accentMint else palette.accentSkyBright,
                            maxAlpha = 0.28f,
                            durationMs = 1500
                        )
                        .clickable(
                            interactionSource = pillInteraction,
                            indication = ripple(color = palette.accentSky),
                            role = Role.Button,
                            onClickLabel = "Play live theme motion preview"
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            demoTriggerTick += 1
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("btn_stage_play_motion"),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = phase,
                        transitionSpec = {
                            if (reducedMotion) {
                                fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                            } else {
                                (fadeIn(tween(180)) + scaleIn(initialScale = 0.92f)) togetherWith
                                    (fadeOut(tween(140)) + scaleOut(targetScale = 0.95f))
                            }
                        },
                        label = "stage_island_content"
                    ) { activePhase ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (activePhase) {
                                StageDemoPhase.IDLE -> {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(palette.accentSky)
                                    )
                                    Text(
                                        text = "Tap Pill • Preview",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = palette.inkPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                StageDemoPhase.SCANNING -> {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = palette.accentSkyBright,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Scanning Screen…",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = palette.accentSkyBright,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                StageDemoPhase.SOLVED -> {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(palette.accentMint),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Verified answer",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = "Option B • 99% Match",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = palette.accentMint,
                                            fontWeight = FontWeight.ExtraBold,
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

                // Miniature Live MCQ Specimen Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(innerCorner))
                        .background(palette.glassStrong.copy(alpha = 0.94f))
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(innerCorner))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Q. Speed of light in vacuum?",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 6.dp)
                        )
                        Text(
                            text = "${activeSpec.cornerRadiusDp}dp",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkMuted,
                                fontSize = 10.sp
                            ),
                            maxLines = 1
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StageMiniOptionPill(
                            letter = "A",
                            text = "1.5×10⁸ m/s",
                            highlighted = false,
                            modifier = Modifier.weight(1f)
                        )
                        StageMiniOptionPill(
                            letter = "B",
                            text = "3.0×10⁸ m/s",
                            highlighted = phase == StageDemoPhase.SOLVED,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Animated optical confidence meter
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(palette.glassSubtle)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(confidenceProgress)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(
                                    if (phase == StageDemoPhase.SOLVED) palette.accentMint else palette.accentSky
                                )
                        )
                    }
                }
            }
        }

        // Bottom Specimen Footer Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.glassStrong)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(palette.accentSky)
                )
                Text(
                    text = if (activeSpec.surfaceEffect.label.contains(activeSpec.layoutArchetype.shortBadge, ignoreCase = true)) {
                        activeSpec.surfaceEffect.label
                    } else {
                        "${activeSpec.layoutArchetype.shortBadge} • ${activeSpec.surfaceEffect.label}"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.inkSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(palette.glassMedium)
                    .border(1.dp, palette.cardHairlineBorder, CircleShape)
                    .clickable(role = Role.Button) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        demoTriggerTick += 1
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Replay motion preview",
                    tint = palette.accentSky,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Feel",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StageMiniOptionPill(
    letter: String,
    text: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val bg by animateColorAsState(
        targetValue = if (highlighted) palette.accentMintContainer else palette.glassMedium,
        animationSpec = tween(240),
        label = "stage_opt_bg_$letter"
    )
    val border by animateColorAsState(
        targetValue = if (highlighted) palette.accentMint else palette.cardHairlineBorder,
        animationSpec = tween(240),
        label = "stage_opt_border_$letter"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (highlighted) palette.accentMint else palette.glassSubtle),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (highlighted) Color.White else palette.inkPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp
                )
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (highlighted) palette.inkPrimary else palette.inkSecondary,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.5.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Art-directed Signature Atelier preview card featuring a custom miniature architectural
 * canvas preview, mini Dynamic Island capsule, palette swatches, and tactile spring selection.
 */
@Composable
fun SignatureAtelierCard(
    spec: RemoteThemeSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val cardCanvas = remember(spec.canvasTopHex) { parseHexColorOr(spec.canvasTopHex, Color(0xFF0A0A0C)) }
    val cardSurface = remember(spec.surfacePrimaryHex) { parseHexColorOr(spec.surfacePrimaryHex, Color(0xFF141519)) }
    val cardAccent = remember(spec.accentPrimaryHex) { parseHexColorOr(spec.accentPrimaryHex, Color(0xFFD71921)) }
    val cardAccentBright = remember(spec.accentBrightHex) { parseHexColorOr(spec.accentBrightHex, Color(0xFFFF4650)) }
    val cardInk = remember(spec.inkPrimaryHex) { parseHexColorOr(spec.inkPrimaryHex, Color(0xFFEEEEEE)) }
    val cardMuted = remember(spec.inkMutedHex) { parseHexColorOr(spec.inkMutedHex, Color(0xFF767676)) }
    val cardCorner = 20.dp

    val borderColor by animateColorAsState(
        targetValue = if (selected) cardAccent else palette.cardHairlineBorder,
        animationSpec = tween(260),
        label = "atelier_border_${spec.themeId}"
    )

    Column(
        modifier = modifier
            .width(188.dp)
            .minimumInteractiveComponentSize()
            .liquidPressScale(interactionSource, pressedScale = 0.955f)
            .shadow(
                elevation = if (selected) 10.dp else 4.dp,
                shape = RoundedCornerShape(cardCorner),
                ambientColor = Color.Black.copy(alpha = 0.65f),
                spotColor = if (selected) cardAccent.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(
                width = if (selected) 1.6.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(cardCorner)
            )
            .kineticAccentRim(
                cornerRadius = cardCorner,
                accentColor = cardAccent,
                enabled = selected && !reducedMotion,
                strokeWidth = 1.4.dp,
                durationMs = 4600
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = cardAccent),
                role = Role.RadioButton,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .testTag("card_atelier_${spec.themeId}")
    ) {
        // Live miniature architectural art header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(cardCanvas)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawMiniSurfaceEffect(
                    effect = spec.surfaceEffect,
                    accent = cardAccent,
                    accentBright = cardAccentBright,
                    muted = cardMuted,
                    isDark = spec.isDark,
                    phase = 0.45f
                )
            }

            // Mini Floating Dynamic Island inside preview
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF060709).copy(alpha = 0.92f))
                    .border(1.dp, cardAccent.copy(alpha = 0.85f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(cardAccentBright)
                )
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(3.5.dp)
                        .clip(CircleShape)
                        .background(cardInk.copy(alpha = 0.85f))
                )
            }

            // Miniature elevated glass slab at bottom of preview
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape((spec.cornerRadiusDp * 0.55f).toInt().coerceIn(4, 14).dp))
                    .background(cardSurface.copy(alpha = 0.94f))
                    .border(
                        0.8.dp,
                        cardAccent.copy(alpha = 0.35f),
                        RoundedCornerShape((spec.cornerRadiusDp * 0.55f).toInt().coerceIn(4, 14).dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(cardInk.copy(alpha = 0.85f))
                    )
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(cardMuted.copy(alpha = 0.65f))
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(cardAccent)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "B",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = contrastOnColor(cardAccent),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp
                        )
                    )
                }
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(cardCanvas.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected theme",
                        tint = cardAccentBright,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Crisp Editorial Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = spec.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = spec.tagline,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.5.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(cardCanvas, cardSurface, cardAccent, cardInk).forEach { sw ->
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(sw)
                                .border(0.75.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(cardAccent.copy(alpha = 0.16f))
                        .border(0.75.dp, cardAccent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = spec.layoutArchetype.shortBadge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = cardAccentBright,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawMiniSurfaceEffect(
    effect: SurfaceEffectStyle,
    accent: Color,
    accentBright: Color,
    muted: Color,
    isDark: Boolean,
    phase: Float
) {
    val glowAlpha = if (isDark) 0.30f else 0.14f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = glowAlpha),
                accentBright.copy(alpha = glowAlpha * 0.32f),
                Color.Transparent
            ),
            center = Offset(size.width * (0.20f + phase * 0.25f), size.height * 0.16f),
            radius = size.width * 0.65f
        ),
        center = Offset(size.width * (0.20f + phase * 0.25f), size.height * 0.16f),
        radius = size.width * 0.65f
    )

    when (effect) {
        SurfaceEffectStyle.PRISM_HORIZON -> {
            val arcCenter = Offset(size.width * 0.5f, size.height * 1.48f)
            drawCircle(
                color = accent.copy(alpha = 0.34f),
                radius = size.width * 0.68f,
                center = arcCenter,
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = if (isDark) 0.12f else 0.22f),
                radius = size.width * 0.58f,
                center = arcCenter,
                style = Stroke(width = 0.8.dp.toPx())
            )
        }
        SurfaceEffectStyle.HUD_RINGS -> {
            val c = Offset(size.width * 0.84f, size.height * 0.24f)
            listOf(0.18f, 0.30f, 0.44f).forEachIndexed { idx, f ->
                drawCircle(
                    color = accent.copy(alpha = 0.28f - idx * 0.06f),
                    radius = size.width * f,
                    center = c,
                    style = Stroke(1.dp.toPx())
                )
            }
        }
        SurfaceEffectStyle.DOT_MATRIX -> {
            val step = 14.dp.toPx()
            var x = step * 0.6f
            while (x < size.width) {
                var y = step * 0.6f
                while (y < size.height * 0.72f) {
                    drawCircle(
                        color = accentBright.copy(alpha = 0.22f),
                        radius = 1.dp.toPx(),
                        center = Offset(x, y)
                    )
                    y += step
                }
                x += step
            }
        }
        SurfaceEffectStyle.NEURAL_NODES -> {
            val pts = listOf(
                Offset(size.width * 0.16f, size.height * 0.30f),
                Offset(size.width * 0.46f, size.height * (0.20f + phase * 0.08f)),
                Offset(size.width * 0.82f, size.height * 0.34f),
                Offset(size.width * 0.66f, size.height * 0.68f)
            )
            for (i in 0 until pts.lastIndex) {
                drawLine(accent.copy(alpha = 0.30f), pts[i], pts[i + 1], strokeWidth = 1.dp.toPx())
            }
            pts.forEach { pt ->
                drawCircle(accentBright.copy(alpha = 0.55f), radius = 2.4.dp.toPx(), center = pt)
            }
        }
        SurfaceEffectStyle.LAB_RETICLE -> {
            val c = Offset(size.width * 0.82f, size.height * 0.28f)
            val r = 24.dp.toPx()
            drawCircle(accent.copy(alpha = 0.34f), radius = r, center = c, style = Stroke(1.dp.toPx()))
            val a = phase * 3.14159f
            drawLine(
                accentBright.copy(alpha = 0.45f),
                Offset(c.x - cos(a) * r, c.y - sin(a) * r),
                Offset(c.x + cos(a) * r, c.y + sin(a) * r),
                strokeWidth = 1.dp.toPx()
            )
        }
        SurfaceEffectStyle.AURORA_VEIL -> {
            val wx = size.width * (0.30f + phase * 0.42f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accentBright.copy(alpha = glowAlpha * 0.95f), Color.Transparent),
                    center = Offset(wx, size.height * 0.42f),
                    radius = size.width * 0.62f
                ),
                center = Offset(wx, size.height * 0.42f),
                radius = size.width * 0.62f
            )
        }
        SurfaceEffectStyle.CYBER_GRID, SurfaceEffectStyle.BRUTALIST_GRID -> {
            val step = 18.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(muted.copy(alpha = 0.18f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.7.dp.toPx())
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(muted.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.7.dp.toPx())
                y += step
            }
        }
        SurfaceEffectStyle.IOS_LIQUID_GLASS -> {
            val orbCenter = Offset(size.width * (0.76f - phase * 0.14f), size.height * 0.24f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.24f), Color.White.copy(alpha = 0.20f), Color.Transparent),
                    center = orbCenter,
                    radius = size.width * 0.56f
                ),
                center = orbCenter,
                radius = size.width * 0.56f
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = size.width * 0.34f,
                center = Offset(size.width * 0.24f, size.height * 0.82f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        else -> {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accentBright.copy(alpha = glowAlpha * 0.6f), Color.Transparent),
                    center = Offset(size.width * 0.86f, size.height * 0.82f),
                    radius = size.width * 0.5f
                ),
                center = Offset(size.width * 0.86f, size.height * 0.82f),
                radius = size.width * 0.5f
            )
        }
    }
}
