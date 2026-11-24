package com.omnisolve.overlay.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.liquidPressScale
import com.omnisolve.overlay.ui.physics.liquidSheenSweep

/**
 * Reusable Design Tokens for the Prism Solver Dark Studio System.
 */
object PrismTokens {
    val Background = Color(0xFF07080B)
    val SecondaryBackground = Color(0xFF0D0F14)
    val Surface = Color(0xFF14161D)
    val ElevatedSurface = Color(0xFF1B1E27)
    val PrimaryRed = Color(0xFFE3182D)
    val AccentRed = Color(0xFFFF334A)
    val DeepRed = Color(0xFF9E0B1A)
    val SoftRedGlow = Color(0x3BE3182D)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA1A5B1)
    val TextMuted = Color(0xFF727784)
    val Border = Color(0xFF292D38)
    val SpecularRim = Color(0x28FFFFFF)
    val Success = Color(0xFF32D583)
    val SuccessBg = Color(0xFF0D281D)
    val Warning = Color(0xFFF5B942)
    val IndigoAccent = Color(0xFF6366F1)
    val IndigoBright = Color(0xFF818CF8)
}

/**
 * Precision dark glass surface with restrained lighting, metallic top rim,
 * and optional soft crimson edge sheen.
 */
@Composable
fun PrismGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    elevated: Boolean = false,
    accentRim: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val topSurface = if (elevated) PrismTokens.ElevatedSurface else PrismTokens.Surface
    val bottomSurface = if (elevated) Color(0xFF11141C) else PrismTokens.SecondaryBackground

    val rimBrush = if (accentRim != null) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f),
                PrismTokens.Border.copy(alpha = 0.85f),
                accentRim.copy(alpha = 0.58f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.14f),
                PrismTokens.Border.copy(alpha = 0.72f)
            )
        )
    }

    val surfaceModifier = modifier
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .then(if (onClick != null) Modifier.liquidPressScale(interactionSource, pressedScale = 0.978f) else Modifier)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(topSurface.copy(alpha = 0.96f), bottomSurface.copy(alpha = 0.97f))
            )
        )
        .drawBehind {
            // Subtle inner corner atmospheric reflection (never flat or neon-heavy)
            if (accentRim != null) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accentRim.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.92f),
                        radius = size.maxDimension * 0.78f
                    )
                )
            }
            // Crisp specular hairline along top edge
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = if (elevated) 0.16f else 0.11f),
                        Color.Transparent
                    )
                ),
                start = Offset(size.width * 0.12f, 1.dp.toPx()),
                end = Offset(size.width * 0.88f, 1.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }
        .border(width = 1.dp, brush = rimBrush, shape = shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = accentRim ?: PrismTokens.PrimaryRed),
                    role = Role.Button,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
        .padding(contentPadding)

    Box(modifier = surfaceModifier) {
        content()
    }
}

/**
 * Compact live status pill (e.g., "Ready to Solve" or "Floating Helper Active").
 */
@Composable
fun ScanStatusIndicator(
    isOverlayRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val pulseAlpha = if (reducedMotion) {
        1f
    } else {
        val inf = rememberInfiniteTransition(label = "status_dot_pulse")
        val a by inf.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_alpha"
        )
        a
    }

    val dotColor = if (isOverlayRunning) PrismTokens.Success else PrismTokens.Success
    val label = if (isOverlayRunning) "Helper Active" else "Ready to Solve"

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF0E1D16).copy(alpha = 0.92f))
            .border(1.dp, PrismTokens.Success.copy(alpha = 0.34f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = pulseAlpha))
        )
        Text(
            text = label,
            color = PrismTokens.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Native Jetpack Compose animated smartphone product illustration showing:
 * 1. Realistic MCQ displayed on a dark smartphone screen
 * 2. Red viewfinder scan brackets identifying the question
 * 3. Smooth sweeping scan beam
 * 4. Highlighted correct answer + explanation preview
 */
@Composable
fun ScreenQuestionIllustration(
    showExplanationMode: Boolean = false,
    questionText: String = "HTTP OK code?",
    options: List<Triple<String, String, Boolean>> = listOf(
        Triple("A", "200 OK", true),
        Triple("B", "404", false),
        Triple("C", "500", false)
    ),
    explanationText: String = "200 OK confirms success.",
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val inf = rememberInfiniteTransition(label = "hero_phone_anim")

    val scanBeamProgress by inf.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_beam"
    )
    val floatOffsetDp by inf.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phone_float"
    )
    val pulseDot by inf.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_dot"
    )

    val effectiveFloat = if (reducedMotion) 0f else floatOffsetDp
    val effectiveScan = if (reducedMotion) 0.48f else scanBeamProgress

    Box(
        modifier = modifier
            .width(134.dp)
            .height(248.dp)
            .graphicsLayer {
                translationY = effectiveFloat.dp.toPx()
                rotationZ = 4.2f
            }
            .drawBehind {
                // Restrained crimson-graphite halo behind the tall smartphone
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PrismTokens.PrimaryRed.copy(alpha = 0.28f),
                            Color(0xFF1A1020).copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.52f, size.height * 0.54f),
                        radius = size.maxDimension * 0.65f
                    )
                )
                // Subtle realistic titanium hardware side buttons (power right, volume left)
                val btnColor = Color(0xFF4A5064)
                drawLine(btnColor, Offset(size.width - 1.dp.toPx(), size.height * 0.28f), Offset(size.width - 1.dp.toPx(), size.height * 0.40f), 2.5.dp.toPx(), StrokeCap.Round)
                drawLine(btnColor, Offset(1.dp.toPx(), size.height * 0.22f), Offset(1.dp.toPx(), size.height * 0.30f), 2.2.dp.toPx(), StrokeCap.Round)
                drawLine(btnColor, Offset(1.dp.toPx(), size.height * 0.33f), Offset(1.dp.toPx(), size.height * 0.41f), 2.2.dp.toPx(), StrokeCap.Round)
            }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        val phoneShape = RoundedCornerShape(24.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 20.dp,
                    shape = phoneShape,
                    ambientColor = Color.Black,
                    spotColor = PrismTokens.PrimaryRed.copy(alpha = 0.42f)
                )
                .clip(phoneShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1B202C), Color(0xFF0C0E14), Color(0xFF08090E))
                    )
                )
                .border(
                    width = 1.6.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.42f),
                            Color(0xFF394154),
                            PrismTokens.PrimaryRed.copy(alpha = 0.58f)
                        )
                    ),
                    shape = phoneShape
                )
                .padding(horizontal = 7.dp, vertical = 9.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Status Bar + Dynamic Island Scanning Pill
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "9:41",
                            color = PrismTokens.TextMuted,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(3.5.dp).clip(CircleShape).background(PrismTokens.TextMuted.copy(alpha = 0.7f)))
                            Box(Modifier.width(8.dp).height(4.dp).clip(RoundedCornerShape(1.5.dp)).background(PrismTokens.TextMuted.copy(alpha = 0.7f)))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF050609))
                            .border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                            .padding(horizontal = 9.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(PrismTokens.AccentRed.copy(alpha = if (reducedMotion) 1f else pulseDot))
                        )
                        Text(
                            text = if (showExplanationMode) "Solved" else "Scanning...",
                            color = PrismTokens.TextPrimary,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                // Tall Vertical Scanned MCQ Frame with Red Corner Brackets & Moving Scan Beam
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF10131C).copy(alpha = 0.94f))
                        .drawBehind {
                            val bLen = 11.dp.toPx()
                            val strokeW = 2.dp.toPx()
                            val pad = 3.dp.toPx()
                            val bracketColor = PrismTokens.AccentRed
                            // Top-left bracket
                            drawLine(bracketColor, Offset(pad, pad), Offset(pad + bLen, pad), strokeW, StrokeCap.Round)
                            drawLine(bracketColor, Offset(pad, pad), Offset(pad, pad + bLen), strokeW, StrokeCap.Round)
                            // Top-right bracket
                            drawLine(bracketColor, Offset(size.width - pad, pad), Offset(size.width - pad - bLen, pad), strokeW, StrokeCap.Round)
                            drawLine(bracketColor, Offset(size.width - pad, pad), Offset(size.width - pad, pad + bLen), strokeW, StrokeCap.Round)
                            // Bottom-left bracket
                            drawLine(bracketColor, Offset(pad, size.height - pad), Offset(pad + bLen, size.height - pad), strokeW, StrokeCap.Round)
                            drawLine(bracketColor, Offset(pad, size.height - pad), Offset(pad, size.height - pad - bLen), strokeW, StrokeCap.Round)
                            // Bottom-right bracket
                            drawLine(bracketColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - bLen, size.height - pad), strokeW, StrokeCap.Round)
                            drawLine(bracketColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - bLen), strokeW, StrokeCap.Round)

                            // Animated Crimson Scan Beam
                            val beamY = size.height * effectiveScan
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        PrismTokens.PrimaryRed.copy(alpha = 0.24f),
                                        Color.Transparent
                                    ),
                                    startY = (beamY - 12.dp.toPx()).coerceAtLeast(0f),
                                    endY = (beamY + 12.dp.toPx()).coerceAtMost(size.height)
                                ),
                                topLeft = Offset(0f, (beamY - 12.dp.toPx()).coerceAtLeast(0f)),
                                size = Size(size.width, 24.dp.toPx())
                            )
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        PrismTokens.AccentRed.copy(alpha = 0.92f),
                                        Color.Transparent
                                    )
                                ),
                                start = Offset(pad, beamY),
                                end = Offset(size.width - pad, beamY),
                                strokeWidth = 1.4.dp.toPx()
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                    ) {
                        Text(
                            text = questionText,
                            color = PrismTokens.TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            lineHeight = 13.sp,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (showExplanationMode) {
                            // Studio Explanation Preview Mode (Reference Image 2)
                            val correctOpt = options.firstOrNull { it.third } ?: options.first()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFF192420))
                                    .border(1.dp, PrismTokens.Success.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
                                    .padding(horizontal = 7.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(PrismTokens.Success),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF07130D),
                                        modifier = Modifier.size(9.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Correct Answer",
                                        color = PrismTokens.Success,
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${correctOpt.first}. ${correctOpt.second}",
                                        color = PrismTokens.TextPrimary,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFF1B141A))
                                    .border(0.9.dp, PrismTokens.PrimaryRed.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                                    .padding(horizontal = 7.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lightbulb,
                                    contentDescription = null,
                                    tint = PrismTokens.Warning,
                                    modifier = Modifier.size(11.dp).padding(top = 1.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Explanation",
                                        color = PrismTokens.TextPrimary,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = explanationText,
                                        color = PrismTokens.TextSecondary,
                                        fontSize = 8.sp,
                                        lineHeight = 10.5.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            // Live Option Highlighting Mode (Reference Image 1)
                            options.take(3).forEach { (letter, label, isCorrect) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isCorrect) Color(0xFF321118) else Color(0xFF171A23)
                                        )
                                        .border(
                                            width = if (isCorrect) 1.dp else 0.7.dp,
                                            color = if (isCorrect) PrismTokens.AccentRed.copy(alpha = 0.85f) else PrismTokens.Border,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 7.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isCorrect) PrismTokens.PrimaryRed.copy(alpha = 0.3f)
                                                    else Color(0xFF252936)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = letter,
                                                color = if (isCorrect) PrismTokens.TextPrimary else PrismTokens.TextSecondary,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = label,
                                            color = if (isCorrect) PrismTokens.TextPrimary else PrismTokens.TextSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isCorrect) {
                                        Box(
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clip(CircleShape)
                                                .background(PrismTokens.PrimaryRed),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(8.5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom smartphone gesture bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.28f))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact dark glass tile used for the 3-step explanation ("Scan Screen", "Get Answer", "Understand")
 * or quick-mode selection ("MCQ", "Math", "Room", "Stealth").
 */
@Composable
fun ScanFeatureStepTile(
    title: String,
    icon: ImageVector,
    selected: Boolean = false,
    accentColor: Color = PrismTokens.AccentRed,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val borderColor by animateColorAsState(
        targetValue = if (selected) PrismTokens.AccentRed.copy(alpha = 0.85f) else PrismTokens.Border,
        animationSpec = tween(220),
        label = "step_tile_border"
    )
    val bgTop by animateColorAsState(
        targetValue = if (selected) Color(0xFF331118) else PrismTokens.ElevatedSurface,
        animationSpec = tween(220),
        label = "step_tile_bg"
    )
    val shape = RoundedCornerShape(13.dp)

    Column(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .minimumInteractiveComponentSize()
            .then(if (onClick != null) Modifier.liquidPressScale(interactionSource, pressedScale = 0.94f) else Modifier)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(bgTop, if (selected) Color(0xFF1D0B10) else PrismTokens.Surface)
                )
            )
            .border(width = if (selected) 1.2.dp else 0.9.dp, color = borderColor, shape = shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = PrismTokens.PrimaryRed),
                        role = Role.Button,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 6.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) PrismTokens.AccentRed else accentColor,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = title,
            color = if (selected) PrismTokens.TextPrimary else PrismTokens.TextSecondary,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Primary Home CTA: Controlled crimson gradient capsule with soft specular top light,
 * crisp scan/play icon, subtitle caption, and right chevron badge.
 */
@Composable
fun FloatingHelperActionButton(
    isOverlayRunning: Boolean,
    caption: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "btn_start_floating_helper"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(28.dp)
    val gradientColors = if (isOverlayRunning) {
        listOf(Color(0xFF1F2430), Color(0xFF151822), Color(0xFF101219))
    } else {
        listOf(Color(0xFFFF364B), PrismTokens.PrimaryRed, Color(0xFFA80D1D))
    }
    val borderBrush = if (isOverlayRunning) {
        Brush.horizontalGradient(listOf(PrismTokens.Success.copy(alpha = 0.65f), PrismTokens.Border))
    } else {
        Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.42f), Color(0xFFFF6B78).copy(alpha = 0.25f))
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .liquidPressScale(interactionSource, pressedScale = 0.97f)
            .shadow(
                elevation = if (isOverlayRunning) 8.dp else 18.dp,
                shape = shape,
                ambientColor = PrismTokens.PrimaryRed.copy(alpha = 0.45f),
                spotColor = if (isOverlayRunning) PrismTokens.Success.copy(alpha = 0.4f) else PrismTokens.PrimaryRed.copy(alpha = 0.72f)
            )
            .clip(shape)
            .background(Brush.horizontalGradient(gradientColors))
            .border(width = 1.2.dp, brush = borderBrush, shape = shape)
            .liquidSheenSweep(
                enabled = !isOverlayRunning,
                tint = Color.White,
                maxAlpha = 0.16f,
                durationMs = 3400
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = if (caption != null) 14.dp else 16.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when {
                        isOverlayRunning -> Icons.Filled.Stop
                        caption != null -> Icons.Filled.CenterFocusStrong
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = null,
                    tint = if (isOverlayRunning) PrismTokens.Success else Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = if (isOverlayRunning) "Stop Floating Helper" else "Start Floating Helper",
                        color = Color.White,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!caption.isNullOrBlank()) {
                        Text(
                            text = if (isOverlayRunning) "Floating pill is active on your screen" else caption,
                            color = Color.White.copy(alpha = 0.84f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.24f))
                    .border(0.9.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Compact Google Sign-In workspace panel (when signed out) or personalized
 * study workspace status bar (when signed in).
 */
@Composable
fun GoogleSignInWorkspacePanel(
    userEmail: String?,
    solvedCount: Int,
    onSignInClick: () -> Unit,
    onOpenHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSignedIn = !userEmail.isNullOrBlank()
    PrismGlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        elevated = false,
        accentRim = if (isSignedIn) PrismTokens.Success else PrismTokens.PrimaryRed,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = if (isSignedIn) onOpenHistoryClick else onSignInClick,
        testTag = if (isSignedIn) "card_signed_in_workspace" else "card_home_ocr_limited_signin"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left circular emblem: Multi-color Google G vector OR green verified badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10131A))
                    .border(
                        1.dp,
                        if (isSignedIn) PrismTokens.Success.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.14f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSignedIn) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = "Signed in",
                        tint = PrismTokens.Success,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    GoogleGMark(modifier = Modifier.size(20.dp))
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (isSignedIn) "Study Workspace Synced" else "Unlock your study workspace",
                    color = PrismTokens.TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isSignedIn) {
                        "$userEmail · $solvedCount solved"
                    } else {
                        "Access rooms, sync and additional features."
                    },
                    color = PrismTokens.TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            if (isSignedIn) {
                                listOf(Color(0xFF1E2430), Color(0xFF171B24))
                            } else {
                                listOf(PrismTokens.AccentRed, PrismTokens.PrimaryRed)
                            }
                        )
                    )
                    .border(
                        width = 0.9.dp,
                        color = if (isSignedIn) PrismTokens.Border else Color.White.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
                    .padding(horizontal = 13.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isSignedIn) "History" else "Sign In",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * Crisp multi-color Google "G" emblem drawn natively on Canvas.
 */
@Composable
private fun GoogleGMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeW = size.minDimension * 0.20f
        val inset = strokeW / 2f
        val arcSize = Size(size.width - strokeW, size.height - strokeW)
        val topLeft = Offset(inset, inset)

        // Red top-left arc
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = -145f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
        )
        // Yellow left arc
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 145f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
        )
        // Green bottom arc
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 42f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
        )
        // Blue right arc
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -8f,
            sweepAngle = 54f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
        )
        // Horizontal blue bar
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.50f, size.height * 0.50f),
            end = Offset(size.width - inset * 0.6f, size.height * 0.50f),
            strokeWidth = strokeW,
            cap = StrokeCap.Butt
        )
    }
}

/**
 * Distinct Studio Feature Tile for Quiz Browser or Live Rooms.
 */
@Composable
fun PrismFeatureTile(
    title: String,
    description: String,
    badgeText: String,
    badgeIcon: ImageVector,
    actionLabel: String,
    accentRim: Color,
    artwork: @Composable () -> Unit,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    PrismGlassSurface(
        modifier = modifier,
        cornerRadius = 22.dp,
        elevated = false,
        accentRim = accentRim,
        contentPadding = PaddingValues(14.dp),
        onClick = onClick,
        testTag = testTag
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                artwork()

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF1D202B).copy(alpha = 0.92f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        .padding(horizontal = 7.dp, vertical = 3.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = PrismTokens.TextSecondary,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = badgeText,
                        color = PrismTokens.TextPrimary,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        maxLines = 1
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    color = PrismTokens.TextPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    color = PrismTokens.TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF1D202C))
                    .border(0.9.dp, accentRim.copy(alpha = 0.42f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = actionLabel,
                    color = PrismTokens.TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = PrismTokens.TextPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Custom layered frosted document emblem for the Quiz Browser tile.
 */
@Composable
fun QuizBrowserArtwork(
    studioCrimsonAccent: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(44.dp)) {
        val w = size.width
        val h = size.height
        val backColor = if (studioCrimsonAccent) Color(0xFF8C1423) else Color(0xFF4338CA)
        val frontTop = if (studioCrimsonAccent) Color(0xFFE3182D) else Color(0xFF6366F1)
        val frontBottom = if (studioCrimsonAccent) Color(0xFF5D0C17) else Color(0xFF312E81)

        // Offset depth sheet
        drawRoundRect(
            color = backColor.copy(alpha = 0.65f),
            topLeft = Offset(w * 0.08f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.76f),
            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
        )
        // Front frosted glass document card
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(frontTop, frontBottom),
                start = Offset(w * 0.2f, 0f),
                end = Offset(w, h)
            ),
            topLeft = Offset(w * 0.18f, h * 0.06f),
            size = Size(w * 0.74f, h * 0.80f),
            cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx())
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.34f),
            topLeft = Offset(w * 0.18f, h * 0.06f),
            size = Size(w * 0.74f, h * 0.80f),
            cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
        // Three crisp question lines
        val lineX1 = w * 0.32f
        drawLine(Color.White.copy(alpha = 0.92f), Offset(lineX1, h * 0.30f), Offset(w * 0.74f, h * 0.30f), 2.4.dp.toPx(), StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.80f), Offset(lineX1, h * 0.47f), Offset(w * 0.78f, h * 0.47f), 2.4.dp.toPx(), StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.65f), Offset(lineX1, h * 0.64f), Offset(w * 0.62f, h * 0.64f), 2.4.dp.toPx(), StrokeCap.Round)
    }
}

/**
 * Custom layered frosted live-room emblem for the Live Rooms tile.
 */
@Composable
fun LiveRoomsArtwork(
    studioIndigoAccent: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(44.dp)) {
        val w = size.width
        val h = size.height
        val bodyTop = if (studioIndigoAccent) Color(0xFF4F46E5) else Color(0xFF3E4455)
        val bodyBottom = if (studioIndigoAccent) Color(0xFF211C4A) else Color(0xFF1D202A)

        // Frosted camera / room glass block
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(bodyTop, bodyBottom),
                start = Offset(0f, 0f),
                end = Offset(w * 0.8f, h)
            ),
            topLeft = Offset(w * 0.06f, h * 0.16f),
            size = Size(w * 0.68f, h * 0.64f),
            cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx())
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.32f),
            topLeft = Offset(w * 0.06f, h * 0.16f),
            size = Size(w * 0.68f, h * 0.64f),
            cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
        // Play / broadcast chevron inside
        val playPath = Path().apply {
            moveTo(w * 0.32f, h * 0.34f)
            lineTo(w * 0.56f, h * 0.48f)
            lineTo(w * 0.32f, h * 0.62f)
            close()
        }
        drawPath(playPath, color = Color.White.copy(alpha = 0.90f))

        // Crimson LIVE indicator pill at bottom-right
        drawRoundRect(
            color = PrismTokens.PrimaryRed,
            topLeft = Offset(w * 0.50f, h * 0.58f),
            size = Size(w * 0.44f, h * 0.28f),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 2.6.dp.toPx(),
            center = Offset(w * 0.72f, h * 0.72f)
        )
    }
}

/**
 * Compact, truthful 4-benefit horizontal glass bar (no fake marketing claims).
 */
@Composable
fun PrismBenefitStrip(
    title: String? = null,
    modifier: Modifier = Modifier
) {
    val benefits = remember {
        listOf(
            Triple(Icons.Filled.CenterFocusStrong, "Screen\nScan", PrismTokens.AccentRed),
            Triple(Icons.Filled.Bolt, "Instant\nAI", PrismTokens.Warning),
            Triple(Icons.Filled.AutoAwesome, "Clear\nExplanations", PrismTokens.IndigoBright),
            Triple(Icons.Filled.Layers, "Easy\nAccess", PrismTokens.Success)
        )
    }

    PrismGlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        elevated = false,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
        testTag = "prism_benefit_strip"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!title.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    RedPrismLogoMark(modifier = Modifier.size(14.dp), animated = false)
                    Text(
                        text = title,
                        color = PrismTokens.TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                benefits.forEachIndexed { idx, (icon, label, tint) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label.replace("\n", " "),
                            tint = tint,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = label,
                            color = PrismTokens.TextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                    if (idx < benefits.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(30.dp)
                                .background(PrismTokens.Border.copy(alpha = 0.8f))
                        )
                    }
                }
            }
        }
    }
}
