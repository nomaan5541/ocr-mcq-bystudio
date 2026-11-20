package com.omnisolve.overlay.ui.physics

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.PrismCurves
import com.omnisolve.overlay.ui.theme.SurfaceEffectStyle
import com.omnisolve.overlay.ui.theme.ThemeAnimationIntensity
import kotlin.math.cos
import kotlin.math.sin

val LocalReducedMotion = compositionLocalOf { false }

data class SpringPhysicsConfig(
    val enabled: Boolean = true,
    val dampingRatio: Float = 0.78f,
    val stiffness: Float = 520f
)

val LocalSpringPhysics = compositionLocalOf { SpringPhysicsConfig() }

@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }
}

object MotionTokens {
    fun <T> tactilePress(
        reducedMotion: Boolean = false,
        config: SpringPhysicsConfig = SpringPhysicsConfig()
    ): SpringSpec<T> =
        if (!config.enabled || reducedMotion) spring(dampingRatio = 1f, stiffness = 2400f)
        else spring(dampingRatio = config.dampingRatio.coerceIn(0.45f, 1f), stiffness = config.stiffness.coerceIn(200f, 1800f))

    fun <T> liquidMorph(
        reducedMotion: Boolean = false,
        config: SpringPhysicsConfig = SpringPhysicsConfig()
    ): SpringSpec<T> =
        if (!config.enabled || reducedMotion) spring(dampingRatio = 1f, stiffness = 2400f)
        else spring(dampingRatio = (config.dampingRatio * 0.96f).coerceIn(0.58f, 0.9f), stiffness = (config.stiffness * 0.78f).coerceIn(260f, 950f))

    fun <T> gentleSettle(
        reducedMotion: Boolean = false,
        config: SpringPhysicsConfig = SpringPhysicsConfig()
    ): SpringSpec<T> = tactilePress(reducedMotion, config)

    fun <T> counterSettle(
        reducedMotion: Boolean = false,
        config: SpringPhysicsConfig = SpringPhysicsConfig()
    ): SpringSpec<T> = tactilePress(reducedMotion, config)
}

enum class GlassMaterialStrength {
    Card,
    Hero,
    FloatingDock,
    AccentMint,
    AccentSky
}

/**
 * Elastic spring compression feedback on tap when fluid animations are enabled.
 */
fun Modifier.liquidPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.965f
): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val springCfg = LocalSpringPhysics.current
    if (reducedMotion) return@composed this

    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = MotionTokens.tactilePress(reducedMotion = false, config = springCfg),
        label = "liquid_press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Subtle machined liquid glass diagonal specular finish cached per layout size.
 * Uses [drawWithCache] (zero per-frame allocations / zero idle GPU burn) for 60-120fps scrolling.
 */
fun Modifier.liquidSheenSweep(
    enabled: Boolean = true,
    tint: Color = Color.White,
    maxAlpha: Float = 0.22f,
    durationMs: Int = 2700
): Modifier = if (!enabled) {
    this
} else {
    this.drawWithCache {
        val brush = Brush.linearGradient(
            colors = listOf(
                tint.copy(alpha = maxAlpha * 0.45f),
                Color.Transparent,
                tint.copy(alpha = maxAlpha * 0.20f)
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width * 0.85f, size.height)
        )
        onDrawWithContent {
            drawContent()
            if (size.width > 0f && size.height > 0f) {
                drawRect(brush = brush)
            }
        }
    }
}

/**
 * Subtle curved top glass highlight rim that accentuates sleek superellipse-like card contours.
 */
fun Modifier.topGlassSpecularHighlight(
    cornerRadius: Dp,
    alpha: Float = 0.14f
): Modifier = this.drawWithContent {
    drawContent()
    if (size.width > 4f && size.height > 4f) {
        val radiusPx = cornerRadius.toPx().coerceAtMost(size.minDimension / 2f)
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = alpha),
                0.28f to Color.White.copy(alpha = alpha * 0.22f),
                0.55f to Color.Transparent
            ),
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = 1.1.dp.toPx())
        )
    }
}

/**
 * Dynamic /unslop card surface modifier reading active [LocalWhiteGlassPalette] & [LocalPrismThemeSpec].
 */
fun Modifier.livingGlassSurface(
    strength: GlassMaterialStrength = GlassMaterialStrength.Card,
    cornerRadius: Dp = 20.dp,
    interactive: Boolean = false,
    scrollParallaxFactor: Float = 0f
): Modifier = composed {
    val palette = LocalWhiteGlassPalette.current
    val spec = LocalPrismThemeSpec.current
    val resolvedRadius = if (cornerRadius == 20.dp) PrismCurves.cardCorner(spec) else cornerRadius
    val shape: Shape = RoundedCornerShape(resolvedRadius)
    val borderWidth = (spec.borderWidthTenths / 10f).coerceIn(0.6f, 2.5f).dp

    val depthMultiplier = when (spec.animationIntensity) {
        ThemeAnimationIntensity.EXTREME_3D -> 1.4f
        ThemeAnimationIntensity.INTERACTIVE, ThemeAnimationIntensity.AUTO -> 1.0f
        ThemeAnimationIntensity.SUBTLE -> 0.7f
    }

    val shadowElevation = when (strength) {
        GlassMaterialStrength.FloatingDock -> (12f * depthMultiplier).dp
        GlassMaterialStrength.Hero -> (9f * depthMultiplier).dp
        GlassMaterialStrength.Card -> (4f * depthMultiplier).dp
        GlassMaterialStrength.AccentMint, GlassMaterialStrength.AccentSky -> (6f * depthMultiplier).dp
    }

    val topFill: Color
    val bottomFill: Color
    val borderColor: Color
    when (strength) {
        GlassMaterialStrength.FloatingDock -> {
            topFill = palette.glassMedium
            bottomFill = palette.glassStrong
            borderColor = palette.rimHighlight
        }
        GlassMaterialStrength.Hero -> {
            topFill = palette.glassMedium
            bottomFill = palette.glassStrong
            borderColor = palette.accentSky.copy(alpha = 0.52f)
        }
        GlassMaterialStrength.AccentMint -> {
            topFill = palette.glassTintedMint
            bottomFill = palette.glassStrong
            borderColor = palette.accentMint.copy(alpha = 0.50f)
        }
        GlassMaterialStrength.AccentSky -> {
            topFill = palette.glassTintedSky
            bottomFill = palette.glassStrong
            borderColor = palette.accentSky.copy(alpha = 0.68f)
        }
        GlassMaterialStrength.Card -> {
            topFill = palette.glassStrong
            bottomFill = palette.glassStrong.copy(alpha = if (palette.isDarkObsidian) 0.96f else 1f)
            borderColor = palette.cardHairlineBorder
        }
    }

    val ambientShadow = if (palette.isDarkObsidian) Color(0x99000000) else Color(0x1E0F172A)
    val spotShadow = when {
        strength == GlassMaterialStrength.AccentSky || strength == GlassMaterialStrength.Hero ->
            palette.accentSky.copy(alpha = if (palette.isDarkObsidian) 0.30f else 0.16f)
        palette.isDarkObsidian -> Color(0xAA000000)
        else -> Color(0x220F172A)
    }

    this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = ambientShadow,
            spotColor = spotShadow
        )
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(colors = listOf(topFill, bottomFill)),
            shape = shape
        )
        .border(
            width = borderWidth,
            color = borderColor,
            shape = shape
        )
        .topGlassSpecularHighlight(
            cornerRadius = resolvedRadius,
            alpha = if (palette.isDarkObsidian) 0.12f else 0.32f
        )
}

/**
 * Machined specular rim accent along a card's rounded contour.
 * Cached via [drawWithCache] so scrolling and idle screens remain 100% lag-free.
 */
fun Modifier.kineticAccentRim(
    cornerRadius: Dp,
    accentColor: Color,
    enabled: Boolean = true,
    strokeWidth: Dp = 1.25.dp,
    durationMs: Int = 5200
): Modifier = if (!enabled) {
    this
} else {
    this.drawWithCache {
        val radiusPx = cornerRadius.toPx().coerceAtMost(size.minDimension / 2f)
        val stroke = Stroke(width = strokeWidth.toPx())
        val brush = Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.56f),
                Color.White.copy(alpha = 0.22f),
                Color.Transparent,
                accentColor.copy(alpha = 0.22f)
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )
        onDrawWithContent {
            drawContent()
            if (size.width > 8f && size.height > 8f) {
                drawRoundRect(
                    brush = brush,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = stroke
                )
            }
        }
    }
}

/**
 * Restrained /unslop atmospheric canvas rendered from [LocalWhiteGlassPalette] and [LocalPrismThemeSpec.surfaceEffect].
 * Reads scroll position only in the draw phase via [scrollOffsetProvider] and avoids continuous
 * 60fps infinite redraws so scrolling stays locked at native display refresh rate.
 */
@Composable
fun LiquidAtmosphericBackdrop(
    scrollOffsetPx: Float = 0f,
    scrollOffsetProvider: (() -> Float)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val spec = LocalPrismThemeSpec.current
    val phase = 0.35f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.canvasTop, palette.canvasBottom)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val accent = palette.accentSky
            val accentBright = palette.accentSkyBright
            val subtleInk = palette.inkMuted
            val glowAlpha = if (palette.isDarkObsidian) 0.20f else 0.10f
            val liveScroll = scrollOffsetProvider?.invoke() ?: scrollOffsetPx
            val parallaxShift = (liveScroll * 0.045f).coerceIn(-72f, 72f)

            // Base controlled primary optical focal glow (never rainbow slop)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = glowAlpha),
                        accentBright.copy(alpha = glowAlpha * 0.34f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * (0.10f + phase * 0.05f), size.height * 0.06f - parallaxShift),
                    radius = size.width * 0.72f
                ),
                center = Offset(size.width * (0.10f + phase * 0.05f), size.height * 0.06f - parallaxShift),
                radius = size.width * 0.72f
            )

            when (spec.surfaceEffect) {
                SurfaceEffectStyle.PRISM_HORIZON -> {
                    // Architectural horizon arc + precision watchmaker calibration marks
                    val arcCenter = Offset(size.width * 0.5f, size.height * 0.92f - parallaxShift * 0.6f)
                    val mainR = size.width * 0.92f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = glowAlpha * 0.9f),
                                accentBright.copy(alpha = glowAlpha * 0.25f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * (0.5f + (phase - 0.5f) * 0.18f), size.height * 0.04f - parallaxShift),
                            radius = size.width * 0.76f
                        ),
                        center = Offset(size.width * (0.5f + (phase - 0.5f) * 0.18f), size.height * 0.04f - parallaxShift),
                        radius = size.width * 0.76f
                    )
                    // Twin delicate curved horizon arcs
                    drawCircle(
                        color = accent.copy(alpha = 0.16f),
                        radius = mainR,
                        center = arcCenter,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = if (palette.isDarkObsidian) 0.05f else 0.14f),
                        radius = mainR * 0.88f,
                        center = arcCenter,
                        style = Stroke(width = 0.75.dp.toPx())
                    )
                    // Subtle zenith precision dot-rule
                    val step = 30.dp.toPx()
                    var x = step * 0.5f
                    while (x < size.width) {
                        val edgeFade = 1f - kotlin.math.abs((x / size.width) - 0.5f) * 1.7f
                        if (edgeFade > 0.05f) {
                            drawCircle(
                                color = accentBright.copy(alpha = 0.14f * edgeFade),
                                radius = 1.1.dp.toPx(),
                                center = Offset(x, size.height * 0.11f - parallaxShift * 0.5f)
                            )
                        }
                        x += step
                    }
                }
                SurfaceEffectStyle.AMBIENT_GLOW -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = glowAlpha * 0.65f), Color.Transparent),
                            center = Offset(size.width * 0.92f, size.height * 0.94f),
                            radius = size.width * 0.62f
                        ),
                        center = Offset(size.width * 0.92f, size.height * 0.94f),
                        radius = size.width * 0.62f
                    )
                }
                SurfaceEffectStyle.HUD_RINGS -> {
                    val hudCenter = Offset(size.width * 0.88f, size.height * 0.14f - parallaxShift)
                    val ringStroke = Stroke(width = 1.dp.toPx())
                    listOf(0.18f, 0.28f, 0.40f).forEachIndexed { idx, factor ->
                        val r = size.width * factor + phase * (idx + 1) * 4.dp.toPx()
                        drawCircle(
                            color = accent.copy(alpha = 0.15f - idx * 0.03f),
                            radius = r,
                            center = hudCenter,
                            style = ringStroke
                        )
                    }
                    // Precision watchmaker dial ticks around middle ring
                    val tickR = size.width * 0.28f + phase * 8.dp.toPx()
                    for (i in 0 until 24) {
                        val a = i * (2f * Math.PI.toFloat() / 24f) + phase * 0.35f
                        val c = cos(a)
                        val s = sin(a)
                        val inner = tickR - 4.dp.toPx()
                        drawLine(
                            color = accentBright.copy(alpha = if (i % 3 == 0) 0.22f else 0.09f),
                            start = Offset(hudCenter.x + c * inner, hudCenter.y + s * inner),
                            end = Offset(hudCenter.x + c * tickR, hudCenter.y + s * tickR),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
                SurfaceEffectStyle.CYBER_GRID, SurfaceEffectStyle.BRUTALIST_GRID -> {
                    val step = if (spec.surfaceEffect == SurfaceEffectStyle.BRUTALIST_GRID) 64.dp.toPx() else 40.dp.toPx()
                    val lineAlpha = if (spec.surfaceEffect == SurfaceEffectStyle.BRUTALIST_GRID) 0.12f else 0.07f
                    val gridColor = if (spec.surfaceEffect == SurfaceEffectStyle.BRUTALIST_GRID) subtleInk else accent
                    var x = 0f
                    while (x <= size.width) {
                        drawLine(
                            color = gridColor.copy(alpha = lineAlpha),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 0.75.dp.toPx()
                        )
                        x += step
                    }
                    var y = 0f
                    while (y <= size.height) {
                        drawLine(
                            color = gridColor.copy(alpha = lineAlpha),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 0.75.dp.toPx()
                        )
                        y += step
                    }
                }
                SurfaceEffectStyle.DOT_MATRIX -> {
                    val step = 28.dp.toPx()
                    val dotRadius = 1.1.dp.toPx()
                    var x = step * 0.5f
                    while (x < size.width) {
                        var y = step * 0.5f
                        while (y < size.height * 0.50f) {
                            val fade = 1f - (y / (size.height * 0.50f))
                            val shimmer = 0.7f + 0.3f * sin((x + y) * 0.015f + phase * 6.28f)
                            drawCircle(
                                color = accentBright.copy(alpha = 0.13f * fade * shimmer),
                                radius = dotRadius,
                                center = Offset(x, y - parallaxShift * 0.3f)
                            )
                            y += step
                        }
                        x += step
                    }
                }
                SurfaceEffectStyle.NEURAL_NODES -> {
                    val anchors = listOf(
                        Offset(size.width * 0.16f, size.height * 0.12f - parallaxShift * 0.4f),
                        Offset(size.width * 0.48f, size.height * (0.07f + phase * 0.025f) - parallaxShift * 0.5f),
                        Offset(size.width * 0.84f, size.height * 0.16f - parallaxShift * 0.3f),
                        Offset(size.width * 0.72f, size.height * (0.30f - phase * 0.015f) - parallaxShift * 0.4f),
                        Offset(size.width * 0.30f, size.height * 0.24f - parallaxShift * 0.35f)
                    )
                    for (i in 0 until anchors.lastIndex) {
                        drawLine(
                            color = accent.copy(alpha = 0.15f),
                            start = anchors[i],
                            end = anchors[i + 1],
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    drawLine(
                        color = accent.copy(alpha = 0.11f),
                        start = anchors[0],
                        end = anchors[4],
                        strokeWidth = 1.dp.toPx()
                    )
                    // Travelling synaptic pulse along first spine
                    val pulsePt = Offset(
                        anchors[0].x + (anchors[1].x - anchors[0].x) * phase,
                        anchors[0].y + (anchors[1].y - anchors[0].y) * phase
                    )
                    drawCircle(
                        color = accentBright.copy(alpha = 0.48f),
                        radius = 3.5.dp.toPx(),
                        center = pulsePt
                    )
                    anchors.forEach { pt ->
                        drawCircle(
                            color = accentBright.copy(alpha = 0.28f),
                            radius = 3.dp.toPx(),
                            center = pt
                        )
                    }
                }
                SurfaceEffectStyle.AURORA_VEIL -> {
                    val waveX = size.width * (0.25f + phase * 0.5f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentBright.copy(alpha = glowAlpha * 0.90f), Color.Transparent),
                            center = Offset(waveX, size.height * 0.18f - parallaxShift * 0.5f),
                            radius = size.width * 0.82f
                        ),
                        center = Offset(waveX, size.height * 0.18f - parallaxShift * 0.5f),
                        radius = size.width * 0.82f
                    )
                }
                SurfaceEffectStyle.CARBON_WEAVE -> {
                    val spacing = 22.dp.toPx()
                    var offset = -size.height
                    while (offset < size.width) {
                        drawLine(
                            color = subtleInk.copy(alpha = 0.06f),
                            start = Offset(offset, 0f),
                            end = Offset(offset + size.height * 0.4f, size.height * 0.4f),
                            strokeWidth = 1.dp.toPx()
                        )
                        offset += spacing
                    }
                }
                SurfaceEffectStyle.LIQUID_SPECULAR -> {
                    val shineCenter = Offset(size.width * (0.78f - phase * 0.14f), size.height * 0.15f - parallaxShift * 0.4f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = if (palette.isDarkObsidian) 0.09f else 0.35f), Color.Transparent),
                            center = shineCenter,
                            radius = size.width * 0.55f
                        ),
                        center = shineCenter,
                        radius = size.width * 0.55f
                    )
                }
                SurfaceEffectStyle.LAB_RETICLE -> {
                    val cx = size.width * 0.86f
                    val cy = size.height * 0.13f - parallaxShift
                    val radius = 56.dp.toPx()
                    drawCircle(
                        color = accent.copy(alpha = 0.18f),
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(1.dp.toPx())
                    )
                    drawCircle(
                        color = accent.copy(alpha = 0.09f),
                        radius = radius * 0.62f,
                        center = Offset(cx, cy),
                        style = Stroke(0.8.dp.toPx())
                    )
                    val angle = phase * 3.14159f
                    val dx = cos(angle) * radius
                    val dy = sin(angle) * radius
                    drawLine(
                        color = accentBright.copy(alpha = 0.24f),
                        start = Offset(cx - dx, cy - dy),
                        end = Offset(cx + dx, cy + dy),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                SurfaceEffectStyle.IOS_LIQUID_GLASS -> {
                    // Apple-inspired translucent pearlescent floating light orbs on clean white/platinum canvas
                    val driftX = size.width * (0.22f + phase * 0.16f)
                    val driftY = size.height * 0.12f - parallaxShift * 0.35f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.11f), Color.Transparent),
                            center = Offset(driftX, driftY),
                            radius = size.width * 0.62f
                        ),
                        center = Offset(driftX, driftY),
                        radius = size.width * 0.62f
                    )
                    val orb2X = size.width * (0.86f - phase * 0.12f)
                    val orb2Y = size.height * 0.44f - parallaxShift * 0.2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF5AC8FA).copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(orb2X, orb2Y),
                            radius = size.width * 0.56f
                        ),
                        center = Offset(orb2X, orb2Y),
                        radius = size.width * 0.56f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.72f),
                        start = Offset(0f, 0.5.dp.toPx()),
                        end = Offset(size.width, 0.5.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }
    }
}

/**
 * Crisp, GPU-accelerated staggered entrance choreography that never blocks taps
 * and immediately snaps to final state when [LocalReducedMotion] is enabled.
 */
@Composable
fun ScrollRevealSection(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    if (reducedMotion) {
        Box(modifier = modifier) { content() }
        return
    }

    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val staggerMs = (index.coerceIn(0, 6) * 32L)
        if (staggerMs > 0L) kotlinx.coroutines.delay(staggerMs)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 280,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            alpha = 0.15f + 0.85f * p
            translationY = (1f - p) * 14.dp.toPx()
        }
    ) {
        content()
    }
}
