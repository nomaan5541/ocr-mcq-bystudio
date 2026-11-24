package com.omnisolve.overlay.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.ui.physics.liquidPressScale
import com.omnisolve.overlay.ui.physics.liquidSheenSweep
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

/**
 * Glass Prism Design Tokens — pure ice-white & sapphire-blue optical liquid glass system (zero red mixing).
 */
object GlassPrismTokens {
    val BgPrimary = Color(0xFFECF4FF)
    val BgSecondary = Color(0xFFD5E6FF)
    val SurfaceBase = Color(0xFFF9FCFF)
    val SurfaceElevated = Color(0xFFFFFFFF)

    val GlassSubtleFill = Color(0xFFFFFFFF).copy(alpha = 0.62f)
    val GlassStandardFill = Color(0xFFFFFFFF).copy(alpha = 0.76f)
    val GlassElevatedFill = Color(0xFFFFFFFF).copy(alpha = 0.88f)
    val GlassBorderSubtle = Color(0xFFFFFFFF).copy(alpha = 0.72f)
    val GlassBorderStandard = Color(0xFF9AC5FF).copy(alpha = 0.45f)
    val GlassBorderOptical = Color(0xFF1B6EF3).copy(alpha = 0.35f)

    val AccentPrimary = Color(0xFF0A66FF)
    val AccentSecondary = Color(0xFF4EA2FF)
    val TextPrimary = Color(0xFF0B162C)
    val TextSecondary = Color(0xFF465C7D)
    val TextMuted = Color(0xFF778CA8)
    val Success = Color(0xFF18A866)
    val Warning = Color(0xFFE88B14)
    val Error = Color(0xFFC81E2E)
}

/**
 * Four-tier Liquid Glass Material Engine:
 * - [Subtle]: Background panels & secondary information containers.
 * - [Standard]: Navigation bars, toolbars, buttons & interactive tiles.
 * - [Elevated]: Hero surfaces, bottom sheets, dialogs & answer panels.
 * - [Optical]: Selective specular highlight & refraction edge for primary CTAs & active hubs.
 */
enum class GlassMaterialTier {
    Subtle,
    Standard,
    Elevated,
    Optical
}

enum class GlassButtonVariant {
    Primary,
    Secondary,
    Glass,
    Ghost,
    Danger
}

/**
 * GPU-cached multi-layer Liquid Glass surface modifier with top specular bevel,
 * directional inner luminance, and tier-appropriate depth shadow.
 */
fun Modifier.liquidGlassSurface(
    tier: GlassMaterialTier = GlassMaterialTier.Standard,
    cornerRadius: Dp = 20.dp,
    accentTint: Color? = null,
    highlighted: Boolean = false
): Modifier = composed {
    val palette = LocalWhiteGlassPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    val effectiveAccent = accentTint ?: palette.accentSky
    val isDark = palette.isDarkObsidian

    val shadowDp = when (tier) {
        GlassMaterialTier.Subtle -> 0.dp
        GlassMaterialTier.Standard -> if (isDark) 6.dp else 3.dp
        GlassMaterialTier.Elevated -> if (isDark) 14.dp else 6.dp
        GlassMaterialTier.Optical -> if (isDark) 18.dp else 8.dp
    }

    val topLayerColor = when {
        !isDark -> palette.glassStrong
        tier == GlassMaterialTier.Subtle -> Color(0xFF12151E)
        tier == GlassMaterialTier.Standard -> Color(0xFF161A25)
        tier == GlassMaterialTier.Elevated -> Color(0xFF1A1F2C)
        else -> Color(0xFF1F2433)
    }
    val bottomLayerColor = when {
        !isDark -> palette.glassMedium
        tier == GlassMaterialTier.Subtle -> Color(0xFF0E1017)
        tier == GlassMaterialTier.Standard -> Color(0xFF11141C)
        tier == GlassMaterialTier.Elevated -> Color(0xFF121620)
        else -> Color(0xFF141824)
    }

    val rimColor = when {
        highlighted -> effectiveAccent.copy(alpha = if (isDark) 0.70f else 0.55f)
        !isDark -> palette.cardHairlineBorder
        tier == GlassMaterialTier.Subtle -> GlassPrismTokens.GlassBorderSubtle
        tier == GlassMaterialTier.Standard -> GlassPrismTokens.GlassBorderStandard
        tier == GlassMaterialTier.Elevated -> Color.White.copy(alpha = 0.16f)
        else -> GlassPrismTokens.GlassBorderOptical
    }

    val specularAlpha = when {
        !isDark -> 0.55f
        tier == GlassMaterialTier.Subtle -> 0.08f
        tier == GlassMaterialTier.Standard -> 0.15f
        tier == GlassMaterialTier.Elevated -> 0.22f
        else -> 0.30f
    }

    this
        .then(
            if (shadowDp > 0.dp) {
                Modifier.shadow(
                    elevation = shadowDp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.85f else 0.14f),
                    spotColor = if (highlighted || tier == GlassMaterialTier.Optical) {
                        effectiveAccent.copy(alpha = if (isDark) 0.34f else 0.16f)
                    } else {
                        Color.Black.copy(alpha = if (isDark) 0.72f else 0.12f)
                    }
                )
            } else {
                Modifier
            }
        )
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(topLayerColor, bottomLayerColor)
            )
        )
        .drawWithCache {
            val radiusPx = cornerRadius.toPx()
            val strokePx = if (highlighted) 1.25.dp.toPx() else 0.95.dp.toPx()
            val topBevelBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = specularAlpha),
                    Color.White.copy(alpha = specularAlpha * 0.65f),
                    Color.Transparent
                )
            )
            val tintGlowBrush = if (highlighted || tier == GlassMaterialTier.Optical) {
                Brush.radialGradient(
                    colors = listOf(
                        effectiveAccent.copy(alpha = if (highlighted) 0.18f else 0.11f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.86f, size.height * 0.14f),
                    radius = (size.maxDimension * 0.68f).coerceAtLeast(1f)
                )
            } else {
                null
            }
            val rimGradient = Brush.verticalGradient(
                colors = listOf(
                    rimColor,
                    rimColor.copy(alpha = (rimColor.alpha * 0.42f).coerceAtLeast(0.05f))
                )
            )
            onDrawBehind {
                if (tintGlowBrush != null) {
                    drawRect(brush = tintGlowBrush)
                }
                // Crisp specular top glass refraction rim
                drawLine(
                    brush = topBevelBrush,
                    start = Offset(size.width * 0.10f, 0.9.dp.toPx()),
                    end = Offset(size.width * 0.90f, 0.9.dp.toPx()),
                    strokeWidth = 1.1.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawRoundRect(
                    brush = rimGradient,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx)
                )
            }
        }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tier: GlassMaterialTier = GlassMaterialTier.Standard,
    cornerRadius: Dp = 20.dp,
    accentTint: Color? = null,
    highlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val clickMod = if (onClick != null) {
        Modifier
            .liquidPressScale(interactionSource, pressedScale = 0.975f)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentTint ?: palette.accentSky),
                role = Role.Button,
                onClick = onClick
            )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .liquidGlassSurface(
                tier = tier,
                cornerRadius = cornerRadius,
                accentTint = accentTint,
                highlighted = highlighted
            )
            .then(clickMod)
            .padding(contentPadding)
            .then(if (!testTag.isNullOrBlank()) Modifier.testTag(testTag) else Modifier),
        content = content
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GlassButtonVariant = GlassButtonVariant.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accentOverride: Color? = null,
    testTag: String? = null
) {
    val palette = LocalWhiteGlassPalette.current
    val accent = accentOverride ?: GlassPrismTokens.AccentPrimary
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)

    val containerModifier = when (variant) {
        GlassButtonVariant.Primary -> Modifier
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = shape,
                ambientColor = accent.copy(alpha = 0.45f),
                spotColor = accent.copy(alpha = 0.62f)
            )
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = if (enabled) 1f else 0.45f),
                        GlassPrismTokens.AccentSecondary.copy(alpha = if (enabled) 0.95f else 0.40f)
                    )
                )
            )
            .border(0.9.dp, Color.White.copy(alpha = 0.24f), shape)
            .liquidSheenSweep(enabled = enabled, tint = Color.White, maxAlpha = 0.18f, durationMs = 2800)

        GlassButtonVariant.Secondary -> Modifier
            .liquidGlassSurface(
                tier = GlassMaterialTier.Standard,
                cornerRadius = 16.dp,
                accentTint = accent,
                highlighted = false
            )

        GlassButtonVariant.Glass -> Modifier
            .liquidGlassSurface(
                tier = GlassMaterialTier.Elevated,
                cornerRadius = 16.dp,
                accentTint = accent,
                highlighted = true
            )

        GlassButtonVariant.Ghost -> Modifier
            .clip(shape)
            .background(Color.Transparent)

        GlassButtonVariant.Danger -> Modifier
            .clip(shape)
            .background(GlassPrismTokens.Error.copy(alpha = 0.16f))
            .border(1.dp, GlassPrismTokens.Error.copy(alpha = 0.55f), shape)
    }

    val fgColor = when (variant) {
        GlassButtonVariant.Primary -> Color.White
        GlassButtonVariant.Danger -> GlassPrismTokens.Error
        GlassButtonVariant.Ghost -> palette.inkSecondary
        else -> palette.inkPrimary
    }

    Row(
        modifier = modifier
            .heightIn(min = 50.dp)
            .minimumInteractiveComponentSize()
            .liquidPressScale(interactionSource, pressedScale = 0.96f)
            .then(containerModifier)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = if (variant == GlassButtonVariant.Primary) Color.White else accent),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .then(if (!testTag.isNullOrBlank()) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = fgColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accentColor: Color = GlassPrismTokens.AccentPrimary,
    testTag: String? = null
) {
    val palette = LocalWhiteGlassPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val tint by animateColorAsState(
        targetValue = if (active) accentColor else palette.inkPrimary,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "glass_icon_tint"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .minimumInteractiveComponentSize()
            .liquidPressScale(interactionSource, pressedScale = 0.92f)
            .liquidGlassSurface(
                tier = if (active) GlassMaterialTier.Optical else GlassMaterialTier.Standard,
                cornerRadius = 15.dp,
                accentTint = accentColor,
                highlighted = active
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentColor),
                role = Role.Button,
                onClick = onClick
            )
            .then(if (!testTag.isNullOrBlank()) Modifier.testTag(testTag) else Modifier)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun GlassBadge(
    text: String,
    color: Color = GlassPrismTokens.AccentSecondary,
    dotColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(0.9.dp, color.copy(alpha = 0.42f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            maxLines = 1
        )
    }
}

@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = GlassPrismTokens.AccentPrimary,
    testTag: String? = null
) {
    val palette = LocalWhiteGlassPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .heightIn(min = 38.dp)
            .liquidPressScale(interactionSource, pressedScale = 0.95f)
            .clip(shape)
            .background(
                if (selected) accentColor.copy(alpha = 0.20f)
                else Color.White.copy(alpha = if (palette.isDarkObsidian) 0.055f else 0.08f)
            )
            .border(
                width = if (selected) 1.1.dp else 0.85.dp,
                color = if (selected) accentColor.copy(alpha = 0.78f) else palette.cardHairlineBorder,
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentColor),
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .then(if (!testTag.isNullOrBlank()) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) GlassPrismTokens.AccentSecondary else palette.inkSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            color = if (selected) palette.inkPrimary else palette.inkSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun GlassDivider(
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        palette.cardHairlineBorder.copy(alpha = 0.85f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
fun GlassProgressBar(
    progress: Float,
    accentColor: Color = GlassPrismTokens.AccentPrimary,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "glass_progress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.09f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(5.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(accentColor, GlassPrismTokens.AccentSecondary)
                    )
                )
        )
    }
}
