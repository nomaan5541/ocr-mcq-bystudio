package com.omnisolve.overlay.ui.physics

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Accessibility-aware composition local indicating whether system animations are disabled
 * or reduced by the user.
 */
val LocalReducedMotion = compositionLocalOf { false }

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

/**
 * Shared physical spring parameters across the entire Living Interface System.
 * Bounded damping prevents chaotic oscillation while preserving responsive physical mass.
 */
object MotionTokens {
    fun <T> tactilePress(reducedMotion: Boolean = false): SpringSpec<T> =
        if (reducedMotion) spring(dampingRatio = 1f, stiffness = 2500f)
        else spring(dampingRatio = 0.68f, stiffness = 620f)

    fun <T> liquidMorph(reducedMotion: Boolean = false): SpringSpec<T> =
        if (reducedMotion) spring(dampingRatio = 1f, stiffness = 2500f)
        else spring(dampingRatio = 0.74f, stiffness = 360f)

    fun <T> gentleSettle(reducedMotion: Boolean = false): SpringSpec<T> =
        if (reducedMotion) spring(dampingRatio = 1f, stiffness = 2500f)
        else spring(dampingRatio = 0.82f, stiffness = 240f)

    fun <T> counterSettle(reducedMotion: Boolean = false): SpringSpec<T> =
        if (reducedMotion) spring(dampingRatio = 1f, stiffness = 2500f)
        else spring(dampingRatio = 0.86f, stiffness = 190f)
}

enum class GlassMaterialStrength {
    Card,
    Hero,
    FloatingDock,
    AccentMint,
    AccentSky
}

/**
 * Reusable Living Liquid Glass surface modifier.
 * Combines:
 * 1. Optical translucent white frost body with context-sensitive tint
 * 2. Fresnel-inspired gradient rim illumination (bright white top-left to cool optical edge)
 * 3. Top specular light refraction line
 * 4. Localized physical touch compression, subtle 3D tilt toward finger pressure, and
 *    an expanding caustic light ripple originating at the exact tap coordinate.
 */
fun Modifier.livingGlassSurface(
    strength: GlassMaterialStrength = GlassMaterialStrength.Card,
    cornerRadius: Dp = 24.dp,
    interactive: Boolean = false,
    scrollParallaxFactor: Float = 0f
): Modifier = composed {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()

    val shape: Shape = RoundedCornerShape(cornerRadius)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var isPressed by remember { mutableStateOf(false) }
    var touchOffset by remember { mutableStateOf(Offset.Zero) }

    val rippleProgress = remember { Animatable(0f) }
    val rippleAlpha = remember { Animatable(0f) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && interactive && !reducedMotion) 0.972f else 1f,
        animationSpec = MotionTokens.tactilePress(reducedMotion),
        label = "glassPressScale"
    )

    val normX = if (boxSize.width > 0) ((touchOffset.x / boxSize.width) - 0.5f) * 2f else 0f
    val normY = if (boxSize.height > 0) ((touchOffset.y / boxSize.height) - 0.5f) * 2f else 0f

    val tiltX by animateFloatAsState(
        targetValue = if (isPressed && interactive && !reducedMotion) (-normY * 2.2f).coerceIn(-2.5f, 2.5f) else 0f,
        animationSpec = MotionTokens.tactilePress(reducedMotion),
        label = "glassTiltX"
    )
    val tiltY by animateFloatAsState(
        targetValue = if (isPressed && interactive && !reducedMotion) (normX * 2.2f).coerceIn(-2.5f, 2.5f) else 0f,
        animationSpec = MotionTokens.tactilePress(reducedMotion),
        label = "glassTiltY"
    )

    val shadowElevation = when (strength) {
        GlassMaterialStrength.FloatingDock -> 16.dp
        GlassMaterialStrength.Hero -> 12.dp
        GlassMaterialStrength.Card -> 7.dp
        GlassMaterialStrength.AccentMint, GlassMaterialStrength.AccentSky -> 8.dp
    }

    val topFill: Color
    val bottomFill: Color
    val rimEnd: Color
    when (strength) {
        GlassMaterialStrength.FloatingDock -> {
            topFill = Color(0xF5FFFFFF)
            bottomFill = Color(0xE8F1F7FE)
            rimEnd = palette.rimEdgeCool
        }
        GlassMaterialStrength.Hero -> {
            topFill = Color(0xF2FFFFFF)
            bottomFill = Color(0xE0EBF6FF)
            rimEnd = Color(0x4D0284C7)
        }
        GlassMaterialStrength.AccentMint -> {
            topFill = Color(0xF5FFFFFF)
            bottomFill = palette.glassTintedMint
            rimEnd = palette.rimEdgeMint
        }
        GlassMaterialStrength.AccentSky -> {
            topFill = Color(0xF5FFFFFF)
            bottomFill = palette.glassTintedSky
            rimEnd = Color(0x520284C7)
        }
        GlassMaterialStrength.Card -> {
            topFill = palette.glassStrong
            bottomFill = palette.glassMedium
            rimEnd = palette.rimEdgeCool
        }
    }

    val pointerMod = if (interactive) {
        Modifier.pointerInput(reducedMotion) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                touchOffset = down.position
                isPressed = true
                if (!reducedMotion) {
                    scope.launch {
                        rippleProgress.snapTo(0.08f)
                        rippleAlpha.snapTo(0.32f)
                        rippleProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                        )
                    }
                }
                waitForUpOrCancellation()
                isPressed = false
                if (!reducedMotion) {
                    scope.launch {
                        rippleAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 280)
                        )
                    }
                }
            }
        }
    } else {
        Modifier
    }

    this
        .onSizeChanged { boxSize = it }
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
            rotationX = tiltX
            rotationY = tiltY
            cameraDistance = 16f * density
        }
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = Color(0x1F0284C7),
            spotColor = Color(0x240F172A)
        )
        .clip(shape)
        .then(pointerMod)
        .drawWithContent {
            val radiusPx = cornerRadius.toPx()
            val lightShiftY = scrollParallaxFactor * 36f

            // 1. Translucent White Liquid Glass body
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(topFill, bottomFill),
                    start = Offset(0f, lightShiftY),
                    end = Offset(size.width, size.height + lightShiftY)
                ),
                cornerRadius = CornerRadius(radiusPx, radiusPx)
            )

            // 2. Subtle internal optical caustic glow near top-left
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x66FFFFFF),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.22f, size.height * 0.08f),
                    radius = max(size.width, size.height) * 0.65f
                ),
                radius = max(size.width, size.height) * 0.65f,
                center = Offset(size.width * 0.22f, size.height * 0.08f)
            )

            // 3. Expanding touch caustic pressure ripple
            if (rippleAlpha.value > 0.005f) {
                val maxR = max(size.width, size.height) * 1.15f
                val currentR = maxR * rippleProgress.value
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF38BDF8).copy(alpha = rippleAlpha.value * 0.35f),
                            Color.White.copy(alpha = rippleAlpha.value * 0.7f),
                            Color.Transparent
                        ),
                        center = touchOffset,
                        radius = currentR.coerceAtLeast(8f)
                    ),
                    radius = currentR.coerceAtLeast(8f),
                    center = touchOffset
                )
            }

            // 4. Content layer
            drawContent()

            // 5. Top specular glass thickness sheen
            val inset = radiusPx * 0.6f
            if (size.width > inset * 2f) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xEBFFFFFF),
                            Color.Transparent
                        ),
                        startX = inset,
                        endX = size.width - inset
                    ),
                    start = Offset(inset, 1.2.dp.toPx()),
                    end = Offset(size.width - inset, 1.2.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // 6. Fresnel specular gradient rim
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.rimHighlight,
                        Color(0x99FFFFFF),
                        rimEnd
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                ),
                cornerRadius = CornerRadius(radiusPx, radiusPx),
                size = Size(size.width, size.height),
                style = Stroke(width = 1.25.dp.toPx())
            )
        }
}

/**
 * Multi-layer Daylight Liquid Glass atmospheric backdrop with gentle caustic orbs
 * and scroll-linked parallax depth. Automatically freezes orbital drift in reduced-motion mode.
 */
@Composable
fun LiquidAtmosphericBackdrop(
    scrollOffsetPx: Float,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current

    val phase = if (reducedMotion) {
        0.25f
    } else {
        val infinite = rememberInfiniteTransition(label = "liquidAtmosphere")
        val animatedPhase by infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 16000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbPhase"
        )
        animatedPhase
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Base pearl-to-ice-blue daylight gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.canvasTop,
                    Color(0xFFF0F6FE),
                    palette.canvasBottom
                )
            )
        )

        val parallaxSlow = -scrollOffsetPx * 0.12f
        val parallaxMed = -scrollOffsetPx * 0.22f

        // Orb 1: Soft Optical Sky caustic light (Top Right)
        val orb1Center = Offset(
            x = size.width * 0.84f + cos(phase) * 24f,
            y = (size.height * 0.14f + sin(phase) * 18f) + parallaxSlow
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x3038BDF8),
                    Color(0x120284C7),
                    Color.Transparent
                ),
                center = orb1Center,
                radius = size.width * 0.62f
            ),
            radius = size.width * 0.62f,
            center = orb1Center
        )

        // Orb 2: Soft Mint Crystal refraction (Middle Left)
        val orb2Center = Offset(
            x = size.width * 0.14f + sin(phase * 0.8f) * 20f,
            y = (size.height * 0.48f + cos(phase * 0.8f) * 22f) + parallaxMed
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x2410B981),
                    Color(0x0D059669),
                    Color.Transparent
                ),
                center = orb2Center,
                radius = size.width * 0.56f
            ),
            radius = size.width * 0.56f,
            center = orb2Center
        )

        // Orb 3: Pure Pearl specular glow (Bottom Right)
        val orb3Center = Offset(
            x = size.width * 0.78f,
            y = (size.height * 0.82f) + parallaxSlow * 0.6f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x290EA5E9),
                    Color.Transparent
                ),
                center = orb3Center,
                radius = size.width * 0.52f
            ),
            radius = size.width * 0.52f,
            center = orb3Center
        )
    }
}

/**
 * Staggered spring-settled entrance container for cards and sections.
 * Respects reduced-motion accessibility immediately.
 */
@Composable
fun ScrollRevealSection(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    var visible by remember { mutableStateOf(reducedMotion || index == 0) }

    LaunchedEffect(index, reducedMotion) {
        if (!visible) {
            delay((index.coerceIn(0, 6) * 42L))
            visible = true
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MotionTokens.gentleSettle(reducedMotion),
        label = "revealAlpha"
    )
    val translateY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = MotionTokens.gentleSettle(reducedMotion),
        label = "revealOffsetY"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.97f,
        animationSpec = MotionTokens.gentleSettle(reducedMotion),
        label = "revealScale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = translateY
            this.scaleX = scale
            this.scaleY = scale
        }
    ) {
        content()
    }
}
