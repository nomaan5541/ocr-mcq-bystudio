package com.omnisolve.overlay.ui.physics

import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype
import kotlin.math.cos
import kotlin.math.sin

val LocalReducedMotion = compositionLocalOf { false }

data class SpringPhysicsConfig(
    val enabled: Boolean = true,
    val dampingRatio: Float = 0.80f,
    val stiffness: Float = 560f
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
        else spring(
            dampingRatio = config.dampingRatio.coerceIn(0.55f, 1f),
            stiffness = config.stiffness.coerceIn(260f, 1800f)
        )

    fun <T> liquidMorph(
        reducedMotion: Boolean = false,
        config: SpringPhysicsConfig = SpringPhysicsConfig()
    ): SpringSpec<T> =
        if (!config.enabled || reducedMotion) spring(dampingRatio = 1f, stiffness = 2400f)
        else spring(
            dampingRatio = (config.dampingRatio * 0.98f).coerceIn(0.68f, 0.92f),
            stiffness = (config.stiffness * 0.84f).coerceIn(320f, 960f)
        )

    fun <T> gentleSettle(
        reducedMotion: Boolean = false,
        config: SpringPhysicsConfig = SpringPhysicsConfig()
    ): SpringSpec<T> = tactilePress(reducedMotion, config)

    fun <T> counterSettle(
        reducedMotion: Boolean = false,
        config: SpringPhysicsConfig = SpringPhysicsConfig()
    ): SpringSpec<T> = tactilePress(reducedMotion, config)

    /**
     * Distinct per-theme dock indicator spring choreography so every theme archetype
     * feels physically different while staying locked at 60/120fps.
     */
    fun <T> dockSpringFor(
        archetype: ThemeLayoutArchetype,
        reducedMotion: Boolean = false,
        config: SpringPhysicsConfig = SpringPhysicsConfig()
    ): SpringSpec<T> {
        if (!config.enabled || reducedMotion) return spring(dampingRatio = 1f, stiffness = 2400f)
        return when (archetype) {
            ThemeLayoutArchetype.TELEMETRY_HUD -> spring(dampingRatio = 0.95f, stiffness = 980f)
            ThemeLayoutArchetype.ATELIER_BENTO -> spring(dampingRatio = 0.88f, stiffness = 680f)
            ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> spring(dampingRatio = 0.92f, stiffness = 460f)
            ThemeLayoutArchetype.KINETIC_DECK -> spring(dampingRatio = 0.76f, stiffness = 740f)
            ThemeLayoutArchetype.IOS_LIQUID_LIGHT -> spring(dampingRatio = 0.82f, stiffness = 500f)
            ThemeLayoutArchetype.GLASS_PRISM -> spring(dampingRatio = 0.78f, stiffness = 540f)
            ThemeLayoutArchetype.CRIMSON_STUDIO -> spring(dampingRatio = 0.76f, stiffness = 560f)
            ThemeLayoutArchetype.CLASSIC_PRISM -> spring(dampingRatio = 0.84f, stiffness = 580f)
        }
    }

    fun cardPressScaleFor(archetype: ThemeLayoutArchetype): Float = when (archetype) {
        ThemeLayoutArchetype.TELEMETRY_HUD -> 0.985f
        ThemeLayoutArchetype.ATELIER_BENTO -> 0.975f
        ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> 0.982f
        ThemeLayoutArchetype.KINETIC_DECK -> 0.955f
        ThemeLayoutArchetype.IOS_LIQUID_LIGHT -> 0.968f
        ThemeLayoutArchetype.GLASS_PRISM -> 0.962f
        ThemeLayoutArchetype.CRIMSON_STUDIO -> 0.966f
        ThemeLayoutArchetype.CLASSIC_PRISM -> 0.965f
    }

    /**
     * Zero-jank GPU-composited directional screen transition between Home <-> Settings.
     * Forward (Home -> Settings): enters smoothly from right while Home shifts gently left.
     * Back (Settings -> Home): outgoing screen slides cleanly out to the right while Home glides back in from left.
     */
    fun tabTransitionFor(
        archetype: ThemeLayoutArchetype,
        movingForward: Boolean,
        reducedMotion: Boolean
    ): ContentTransform {
        if (reducedMotion) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        val dir = if (movingForward) 1 else -1
        return when (archetype) {
            ThemeLayoutArchetype.TELEMETRY_HUD -> {
                (slideInHorizontally(
                    animationSpec = tween(180, easing = LinearOutSlowInEasing),
                    initialOffsetX = { w -> dir * (w / 5) }
                ) + fadeIn(tween(150))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(160, easing = FastOutSlowInEasing),
                        targetOffsetX = { w -> -dir * (w / 6) }
                    ) + fadeOut(tween(120)))
            }
            ThemeLayoutArchetype.ATELIER_BENTO -> {
                (slideInHorizontally(
                    animationSpec = tween(230, easing = FastOutSlowInEasing),
                    initialOffsetX = { w -> dir * (w / 4) }
                ) + fadeIn(tween(190))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        targetOffsetX = { w -> -dir * (w / 6) }
                    ) + fadeOut(tween(150)))
            }
            ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> {
                (fadeIn(tween(230, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                        initialOffsetX = { w -> dir * (w / 5) }
                    )) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        targetOffsetX = { w -> -dir * (w / 6) }
                    ) + fadeOut(tween(160)))
            }
            ThemeLayoutArchetype.KINETIC_DECK -> {
                (slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.88f, stiffness = 540f),
                    initialOffsetX = { w -> dir * (w / 3) }
                ) + fadeIn(tween(175))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(185, easing = FastOutSlowInEasing),
                        targetOffsetX = { w -> -dir * (w / 5) }
                    ) + fadeOut(tween(145)))
            }
            ThemeLayoutArchetype.IOS_LIQUID_LIGHT,
            ThemeLayoutArchetype.GLASS_PRISM,
            ThemeLayoutArchetype.CRIMSON_STUDIO,
            ThemeLayoutArchetype.CLASSIC_PRISM -> {
                if (movingForward) {
                    // Forward push: new screen glides in from right, previous shifts gently left
                    (slideInHorizontally(
                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                        initialOffsetX = { w -> (w * 0.28f).toInt() }
                    ) + fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            targetOffsetX = { w -> -(w * 0.14f).toInt() }
                        ) + fadeOut(tween(170)))
                } else {
                    // Reverse Back glide: outgoing screen exits smoothly to right, Home returns from left
                    (slideInHorizontally(
                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                        initialOffsetX = { w -> -(w * 0.18f).toInt() }
                    ) + fadeIn(tween(210, easing = FastOutSlowInEasing))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(230, easing = FastOutSlowInEasing),
                            targetOffsetX = { w -> (w * 0.30f).toInt() }
                        ) + fadeOut(tween(175)))
                }
            }
        }
    }

    /**
     * Context-aware forward push / reverse Back-navigation animation for sub-screens
     * (Google Forms, Dynamic Island, Themes, Privacy, Export, Notifications).
     */
    fun subScreenTransition(
        goingBack: Boolean,
        reducedMotion: Boolean
    ): ContentTransform {
        if (reducedMotion) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        return if (!goingBack) {
            // Entering a sub-screen: crisp slide-in from right + fade
            (slideInHorizontally(
                animationSpec = tween(245, easing = FastOutSlowInEasing),
                initialOffsetX = { w -> (w * 0.28f).toInt() }
            ) + fadeIn(tween(195, easing = FastOutSlowInEasing))) togetherWith
                (slideOutHorizontally(
                    animationSpec = tween(210, easing = FastOutSlowInEasing),
                    targetOffsetX = { w -> -(w * 0.12f).toInt() }
                ) + fadeOut(tween(160)))
        } else {
            // Pressing Back / returning to Home: outgoing sub-screen slides away right, Home glides back in from left
            (slideInHorizontally(
                animationSpec = tween(245, easing = FastOutSlowInEasing),
                initialOffsetX = { w -> -(w * 0.16f).toInt() }
            ) + fadeIn(tween(205, easing = FastOutSlowInEasing))) togetherWith
                (slideOutHorizontally(
                    animationSpec = tween(230, easing = FastOutSlowInEasing),
                    targetOffsetX = { w -> (w * 0.32f).toInt() }
                ) + fadeOut(tween(175)))
        }
    }
}

enum class GlassMaterialStrength {
    Card,
    Hero,
    FloatingDock,
    AccentMint,
    AccentSky
}

/**
 * Elastic spring compression feedback on tap calibrated per active theme archetype.
 * Reads animation state strictly in GPU graphicsLayer (zero recomposition/relayout).
 */
fun Modifier.liquidPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = -1f
): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val springCfg = LocalSpringPhysics.current
    val archetype = LocalPrismThemeSpec.current.layoutArchetype
    if (reducedMotion) return@composed this

    val resolvedPressed = if (pressedScale > 0f) pressedScale else MotionTokens.cardPressScaleFor(archetype)
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) resolvedPressed else 1f,
        animationSpec = MotionTokens.tactilePress(reducedMotion = false, config = springCfg),
        label = "liquid_press_scale"
    )
    this.graphicsLayer {
        val s = scaleState.value
        scaleX = s
        scaleY = s
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
 * Subtle curved top glass highlight rim cached via [drawWithCache] so LazyColumn scroll
 * performs zero per-frame Brush or Stroke allocations.
 */
fun Modifier.topGlassSpecularHighlight(
    cornerRadius: Dp,
    alpha: Float = 0.14f
): Modifier = this.drawWithCache {
    val radiusPx = cornerRadius.toPx().coerceAtMost(size.minDimension / 2f)
    val corner = CornerRadius(radiusPx, radiusPx)
    val stroke = Stroke(width = 1.1.dp.toPx())
    val brush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = alpha),
        0.28f to Color.White.copy(alpha = alpha * 0.22f),
        0.55f to Color.Transparent
    )
    onDrawWithContent {
        drawContent()
        if (size.width > 4f && size.height > 4f) {
            drawRoundRect(
                brush = brush,
                cornerRadius = corner,
                style = stroke
            )
        }
    }
}

/**
 * High-FPS /unslop card surface modifier reading active [LocalWhiteGlassPalette] & [LocalPrismThemeSpec].
 * Caches gradient brushes via [drawWithCache] and reserves expensive hardware elevation shadows
 * strictly for Hero & FloatingDock containers so LazyColumn scrolling stays silky smooth.
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
    val borderWidth = (spec.borderWidthTenths / 10f).coerceIn(0.6f, 2.2f).dp

    val depthMultiplier = when (spec.animationIntensity) {
        ThemeAnimationIntensity.EXTREME_3D -> 1.25f
        ThemeAnimationIntensity.INTERACTIVE, ThemeAnimationIntensity.AUTO -> 1.0f
        ThemeAnimationIntensity.SUBTLE -> 0.7f
    }

    val shadowElevation = when (strength) {
        GlassMaterialStrength.FloatingDock -> (10f * depthMultiplier).dp
        GlassMaterialStrength.Hero -> (7f * depthMultiplier).dp
        // Ordinary scrolling cards rely on crisp borders + cached specular rim instead of
        // per-item hardware shadow render passes, eliminating LazyColumn scroll jank.
        GlassMaterialStrength.Card,
        GlassMaterialStrength.AccentMint,
        GlassMaterialStrength.AccentSky -> 0.dp
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

    val baseMod = if (shadowElevation > 0.dp) {
        val ambientShadow = if (palette.isDarkObsidian) Color(0x88000000) else Color(0x1A0F172A)
        val spotShadow = when {
            strength == GlassMaterialStrength.Hero ->
                palette.accentSky.copy(alpha = if (palette.isDarkObsidian) 0.28f else 0.14f)
            palette.isDarkObsidian -> Color(0x99000000)
            else -> Color(0x200F172A)
        }
        this.shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = ambientShadow,
            spotColor = spotShadow
        )
    } else {
        this
    }

    baseMod
        .clip(shape)
        .drawWithCache {
            val fillBrush = Brush.verticalGradient(colors = listOf(topFill, bottomFill))
            onDrawBehind {
                drawRect(brush = fillBrush)
            }
        }
        .border(
            width = borderWidth,
            color = borderColor,
            shape = shape
        )
        .topGlassSpecularHighlight(
            cornerRadius = resolvedRadius,
            alpha = if (palette.isDarkObsidian) 0.11f else 0.28f
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
 * High-FPS /unslop atmospheric canvas rendered from [LocalWhiteGlassPalette] and [LocalPrismThemeSpec.surfaceEffect].
 *
 * Performance architecture:
 * - Static brushes, arcs, grids, and dot matrices are pre-computed ONCE per layout size inside [drawWithCache].
 * - Scroll parallax runs 100% on the GPU render thread via [graphicsLayer] (`translationY`) with zero
 *   per-frame allocations or Canvas re-loops during LazyColumn scroll!
 */
@Composable
fun LiquidAtmosphericBackdrop(
    scrollOffsetPx: Float = 0f,
    scrollOffsetProvider: (() -> Float)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val spec = LocalPrismThemeSpec.current
    val reducedMotion = LocalReducedMotion.current
    val phase = 0.35f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                remember(palette.canvasTop, palette.canvasBottom) {
                    Brush.verticalGradient(colors = listOf(palette.canvasTop, palette.canvasBottom))
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!reducedMotion) {
                        val liveScroll = scrollOffsetProvider?.invoke() ?: scrollOffsetPx
                        translationY = -(liveScroll * 0.04f).coerceIn(-64f, 64f)
                    }
                }
                .drawWithCache {
                    val accent = palette.accentSky
                    val accentBright = palette.accentSkyBright
                    val subtleInk = palette.inkMuted
                    val glowAlpha = if (palette.isDarkObsidian) 0.20f else 0.10f
                    val baseCenter = Offset(size.width * (0.10f + phase * 0.05f), size.height * 0.06f)
                    val baseRadius = (size.width * 0.72f).coerceAtLeast(1f)
                    val baseBrush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = glowAlpha),
                            accentBright.copy(alpha = glowAlpha * 0.34f),
                            Color.Transparent
                        ),
                        center = baseCenter,
                        radius = baseRadius
                    )

                    val horizonCenter = Offset(size.width * (0.5f + (phase - 0.5f) * 0.18f), size.height * 0.04f)
                    val horizonRadius = (size.width * 0.76f).coerceAtLeast(1f)
                    val horizonBrush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = glowAlpha * 0.9f),
                            accentBright.copy(alpha = glowAlpha * 0.25f),
                            Color.Transparent
                        ),
                        center = horizonCenter,
                        radius = horizonRadius
                    )
                    val ambientCenter = Offset(size.width * 0.92f, size.height * 0.94f)
                    val ambientRadius = (size.width * 0.62f).coerceAtLeast(1f)
                    val ambientBrush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = glowAlpha * 0.65f), Color.Transparent),
                        center = ambientCenter,
                        radius = ambientRadius
                    )
                    val auroraCenter = Offset(size.width * (0.25f + phase * 0.5f), size.height * 0.18f)
                    val auroraRadius = (size.width * 0.82f).coerceAtLeast(1f)
                    val auroraBrush = Brush.radialGradient(
                        colors = listOf(accentBright.copy(alpha = glowAlpha * 0.90f), Color.Transparent),
                        center = auroraCenter,
                        radius = auroraRadius
                    )
                    val shineCenter = Offset(size.width * (0.78f - phase * 0.14f), size.height * 0.15f)
                    val shineRadius = (size.width * 0.55f).coerceAtLeast(1f)
                    val shineBrush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = if (palette.isDarkObsidian) 0.09f else 0.35f), Color.Transparent),
                        center = shineCenter,
                        radius = shineRadius
                    )
                    val iosOrb1Center = Offset(size.width * (0.22f + phase * 0.16f), size.height * 0.12f)
                    val iosOrb1Radius = (size.width * 0.62f).coerceAtLeast(1f)
                    val iosOrb1Brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.11f), Color.Transparent),
                        center = iosOrb1Center,
                        radius = iosOrb1Radius
                    )
                    val iosOrb2Center = Offset(size.width * (0.86f - phase * 0.12f), size.height * 0.44f)
                    val iosOrb2Radius = (size.width * 0.56f).coerceAtLeast(1f)
                    val iosOrb2Brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF5AC8FA).copy(alpha = 0.10f), Color.Transparent),
                        center = iosOrb2Center,
                        radius = iosOrb2Radius
                    )
                    val stroke1 = Stroke(width = 1.dp.toPx())
                    val strokeThin = Stroke(width = 0.75.dp.toPx())

                    onDrawBehind {
                        drawCircle(brush = baseBrush, center = baseCenter, radius = baseRadius)

                        when (spec.surfaceEffect) {
                            SurfaceEffectStyle.PRISM_HORIZON -> {
                                val arcCenter = Offset(size.width * 0.5f, size.height * 0.92f)
                                val mainR = size.width * 0.92f
                                drawCircle(brush = horizonBrush, center = horizonCenter, radius = horizonRadius)
                                drawCircle(color = accent.copy(alpha = 0.16f), radius = mainR, center = arcCenter, style = stroke1)
                                drawCircle(
                                    color = Color.White.copy(alpha = if (palette.isDarkObsidian) 0.05f else 0.14f),
                                    radius = mainR * 0.88f,
                                    center = arcCenter,
                                    style = strokeThin
                                )
                                val step = 30.dp.toPx()
                                var x = step * 0.5f
                                while (x < size.width) {
                                    val edgeFade = 1f - kotlin.math.abs((x / size.width) - 0.5f) * 1.7f
                                    if (edgeFade > 0.05f) {
                                        drawCircle(
                                            color = accentBright.copy(alpha = 0.14f * edgeFade),
                                            radius = 1.1.dp.toPx(),
                                            center = Offset(x, size.height * 0.11f)
                                        )
                                    }
                                    x += step
                                }
                            }
                            SurfaceEffectStyle.AMBIENT_GLOW -> {
                                drawCircle(brush = ambientBrush, center = ambientCenter, radius = ambientRadius)
                                val shift = (phase - 0.5f) * size.width * 0.08f
                                val ribbon1 = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(-size.width * 0.1f, size.height * 0.14f + shift * 0.4f)
                                    cubicTo(
                                        size.width * 0.25f, size.height * 0.02f - shift,
                                        size.width * 0.65f, size.height * 0.26f + shift,
                                        size.width * 1.1f, size.height * 0.12f
                                    )
                                }
                                drawPath(
                                    path = ribbon1,
                                    brush = Brush.horizontalGradient(
                                        listOf(Color.Transparent, accent.copy(alpha = 0.24f), accentBright.copy(alpha = 0.14f), Color.Transparent)
                                    ),
                                    style = Stroke(width = 34.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )
                                drawPath(
                                    path = ribbon1,
                                    color = accentBright.copy(alpha = 0.22f),
                                    style = Stroke(width = 1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )
                                val ribbon2 = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(-size.width * 0.08f, size.height * 0.62f)
                                    cubicTo(
                                        size.width * 0.32f, size.height * 0.52f + shift,
                                        size.width * 0.68f, size.height * 0.74f - shift,
                                        size.width * 1.12f, size.height * 0.56f
                                    )
                                }
                                drawPath(
                                    path = ribbon2,
                                    brush = Brush.horizontalGradient(
                                        listOf(Color.Transparent, accent.copy(alpha = 0.16f), Color.Transparent)
                                    ),
                                    style = Stroke(width = 46.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )
                            }
                            SurfaceEffectStyle.HUD_RINGS -> {
                                val hudCenter = Offset(size.width * 0.88f, size.height * 0.14f)
                                listOf(0.18f, 0.28f, 0.40f).forEachIndexed { idx, factor ->
                                    val r = size.width * factor + phase * (idx + 1) * 4.dp.toPx()
                                    drawCircle(
                                        color = accent.copy(alpha = 0.15f - idx * 0.03f),
                                        radius = r,
                                        center = hudCenter,
                                        style = stroke1
                                    )
                                }
                                val tickR = size.width * 0.28f + phase * 8.dp.toPx()
                                val inner = tickR - 4.dp.toPx()
                                for (i in 0 until 24) {
                                    val a = i * (2f * Math.PI.toFloat() / 24f) + phase * 0.35f
                                    val c = cos(a)
                                    val s = sin(a)
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
                                    drawLine(color = gridColor.copy(alpha = lineAlpha), start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 0.75.dp.toPx())
                                    x += step
                                }
                                var y = 0f
                                while (y <= size.height) {
                                    drawLine(color = gridColor.copy(alpha = lineAlpha), start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 0.75.dp.toPx())
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
                                        drawCircle(color = accentBright.copy(alpha = 0.13f * fade * shimmer), radius = dotRadius, center = Offset(x, y))
                                        y += step
                                    }
                                    x += step
                                }
                            }
                            SurfaceEffectStyle.NEURAL_NODES -> {
                                val anchors = listOf(
                                    Offset(size.width * 0.16f, size.height * 0.12f),
                                    Offset(size.width * 0.48f, size.height * (0.07f + phase * 0.025f)),
                                    Offset(size.width * 0.84f, size.height * 0.16f),
                                    Offset(size.width * 0.72f, size.height * (0.30f - phase * 0.015f)),
                                    Offset(size.width * 0.30f, size.height * 0.24f)
                                )
                                for (i in 0 until anchors.lastIndex) {
                                    drawLine(color = accent.copy(alpha = 0.15f), start = anchors[i], end = anchors[i + 1], strokeWidth = 1.dp.toPx())
                                }
                                drawLine(color = accent.copy(alpha = 0.11f), start = anchors[0], end = anchors[4], strokeWidth = 1.dp.toPx())
                                val pulsePt = Offset(
                                    anchors[0].x + (anchors[1].x - anchors[0].x) * phase,
                                    anchors[0].y + (anchors[1].y - anchors[0].y) * phase
                                )
                                drawCircle(color = accentBright.copy(alpha = 0.48f), radius = 3.5.dp.toPx(), center = pulsePt)
                                anchors.forEach { pt ->
                                    drawCircle(color = accentBright.copy(alpha = 0.28f), radius = 3.dp.toPx(), center = pt)
                                }
                            }
                            SurfaceEffectStyle.AURORA_VEIL -> {
                                drawCircle(brush = auroraBrush, center = auroraCenter, radius = auroraRadius)
                                val wave = (phase - 0.5f) * size.width * 0.10f
                                val studioRibbon = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(-size.width * 0.1f, size.height * 0.22f)
                                    cubicTo(
                                        size.width * 0.30f, size.height * 0.08f + wave,
                                        size.width * 0.65f, size.height * 0.34f - wave,
                                        size.width * 1.12f, size.height * 0.18f
                                    )
                                }
                                drawPath(
                                    path = studioRibbon,
                                    brush = Brush.horizontalGradient(
                                        listOf(Color.Transparent, accent.copy(alpha = 0.30f), Color(0xFF4F2880).copy(alpha = 0.18f), Color.Transparent)
                                    ),
                                    style = Stroke(width = 48.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )
                                drawPath(
                                    path = studioRibbon,
                                    color = accentBright.copy(alpha = 0.26f),
                                    style = Stroke(width = 1.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
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
                            SurfaceEffectStyle.LIQUID_SPECULAR,
                            SurfaceEffectStyle.IOS_LIQUID_GLASS -> {
                                // Animated curly white & royal-blue liquid silk ribbons + floating glossy droplets
                                drawCircle(brush = iosOrb1Brush, center = iosOrb1Center, radius = iosOrb1Radius)
                                drawCircle(brush = iosOrb2Brush, center = iosOrb2Center, radius = iosOrb2Radius)
                                drawCircle(brush = shineBrush, center = shineCenter, radius = shineRadius)

                                val drift = (phase - 0.5f) * size.width * 0.09f
                                val curlTop = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(size.width * 0.56f, -size.height * 0.04f)
                                    cubicTo(
                                        size.width * 0.38f + drift, size.height * 0.14f,
                                        size.width * 1.06f, size.height * 0.20f - drift * 0.6f,
                                        size.width * 0.96f, size.height * 0.36f
                                    )
                                }
                                drawPath(
                                    path = curlTop,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.64f),
                                            Color(0xFF8EC5FF).copy(alpha = 0.34f),
                                            Color(0xFF1B6EF3).copy(alpha = 0.18f)
                                        )
                                    ),
                                    style = Stroke(width = 62.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )
                                drawPath(
                                    path = curlTop,
                                    color = Color.White.copy(alpha = 0.80f),
                                    style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )

                                val curlMid = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(-size.width * 0.12f, size.height * 0.34f + drift * 0.5f)
                                    cubicTo(
                                        size.width * 0.24f, size.height * 0.44f - drift,
                                        size.width * 0.68f, size.height * 0.28f + drift,
                                        size.width * 1.14f, size.height * 0.48f
                                    )
                                }
                                drawPath(
                                    path = curlMid,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF5FA8FF).copy(alpha = 0.26f),
                                            Color.White.copy(alpha = 0.58f),
                                            Color(0xFF9ACBFF).copy(alpha = 0.32f),
                                            Color(0xFF1B6EF3).copy(alpha = 0.18f)
                                        )
                                    ),
                                    style = Stroke(width = 56.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )
                                drawPath(
                                    path = curlMid,
                                    color = Color.White.copy(alpha = 0.72f),
                                    style = Stroke(width = 1.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )

                                val curlBottom = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(-size.width * 0.08f, size.height * 0.80f - drift * 0.5f)
                                    cubicTo(
                                        size.width * 0.30f, size.height * 0.68f + drift,
                                        size.width * 0.72f, size.height * 0.90f - drift,
                                        size.width * 1.12f, size.height * 0.76f
                                    )
                                }
                                drawPath(
                                    path = curlBottom,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF1B6EF3).copy(alpha = 0.22f),
                                            Color(0xFF8BC4FF).copy(alpha = 0.32f),
                                            Color.White.copy(alpha = 0.56f)
                                        )
                                    ),
                                    style = Stroke(width = 70.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )
                                drawPath(
                                    path = curlBottom,
                                    color = Color.White.copy(alpha = 0.68f),
                                    style = Stroke(width = 1.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )

                                // Floating glossy liquid glass droplets
                                val drop1 = Offset(size.width * 0.64f, size.height * 0.10f + drift * 0.35f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        listOf(Color.White.copy(alpha = 0.92f), Color(0xFF7CB8FF).copy(alpha = 0.50f), Color(0xFF1B6EF3).copy(alpha = 0.14f)),
                                        center = drop1 - Offset(2.dp.toPx(), 2.dp.toPx()),
                                        radius = 10.dp.toPx()
                                    ),
                                    radius = 6.dp.toPx(),
                                    center = drop1
                                )
                                val drop2 = Offset(size.width * 0.93f, size.height * 0.25f - drift * 0.45f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        listOf(Color.White.copy(alpha = 0.95f), Color(0xFF5FA8FF).copy(alpha = 0.55f), Color(0xFF1B6EF3).copy(alpha = 0.18f)),
                                        center = drop2 - Offset(2.dp.toPx(), 2.dp.toPx()),
                                        radius = 12.dp.toPx()
                                    ),
                                    radius = 7.5.dp.toPx(),
                                    center = drop2
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.78f),
                                    start = Offset(0f, 0.5.dp.toPx()),
                                    end = Offset(size.width, 0.5.dp.toPx()),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            SurfaceEffectStyle.LAB_RETICLE -> {
                                val cx = size.width * 0.86f
                                val cy = size.height * 0.13f
                                val radius = 56.dp.toPx()
                                drawCircle(color = accent.copy(alpha = 0.18f), radius = radius, center = Offset(cx, cy), style = stroke1)
                                drawCircle(color = accent.copy(alpha = 0.09f), radius = radius * 0.62f, center = Offset(cx, cy), style = strokeThin)
                                val angle = phase * 3.14159f
                                val dx = cos(angle) * radius
                                val dy = sin(angle) * radius
                                drawLine(color = accentBright.copy(alpha = 0.24f), start = Offset(cx - dx, cy - dy), end = Offset(cx + dx, cy + dy), strokeWidth = 1.dp.toPx())
                            }
                        }
                    }
                }
        )
    }
}

/**
 * Crisp, GPU-accelerated theme-aware staggered entrance choreography that never blocks taps
 * and immediately snaps to final state when [LocalReducedMotion] is enabled.
 */
@Composable
fun ScrollRevealSection(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val archetype = LocalPrismThemeSpec.current.layoutArchetype
    if (reducedMotion) {
        Box(modifier = modifier) { content() }
        return
    }

    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val staggerStep = when (archetype) {
            ThemeLayoutArchetype.TELEMETRY_HUD -> 16L
            ThemeLayoutArchetype.KINETIC_DECK -> 22L
            ThemeLayoutArchetype.ATELIER_BENTO -> 26L
            else -> 24L
        }
        val durationMs = when (archetype) {
            ThemeLayoutArchetype.TELEMETRY_HUD -> 140
            ThemeLayoutArchetype.KINETIC_DECK -> 185
            ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> 240
            ThemeLayoutArchetype.IOS_LIQUID_LIGHT -> 230
            ThemeLayoutArchetype.GLASS_PRISM -> 215
            ThemeLayoutArchetype.CRIMSON_STUDIO -> 210
            ThemeLayoutArchetype.ATELIER_BENTO -> 195
            ThemeLayoutArchetype.CLASSIC_PRISM -> 205
        }
        val staggerMs = (index.coerceIn(0, 5) * staggerStep)
        if (staggerMs > 0L) kotlinx.coroutines.delay(staggerMs)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            alpha = 0.25f + 0.75f * p
            when (archetype) {
                ThemeLayoutArchetype.TELEMETRY_HUD -> {
                    translationX = (1f - p) * 10.dp.toPx()
                }
                ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> {
                    translationY = (1f - p) * 6.dp.toPx()
                }
                ThemeLayoutArchetype.KINETIC_DECK -> {
                    val s = 0.97f + 0.03f * p
                    scaleX = s
                    scaleY = s
                    translationY = (1f - p) * 10.dp.toPx()
                }
                else -> {
                    translationY = (1f - p) * 11.dp.toPx()
                }
            }
        }
    ) {
        content()
    }
}
