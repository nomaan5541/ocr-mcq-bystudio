package com.omnisolve.overlay.ui.physics

import android.provider.Settings
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

val LocalReducedMotion = compositionLocalOf { true }

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
    fun <T> tactilePress(reducedMotion: Boolean = true): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = 3000f)

    fun <T> liquidMorph(reducedMotion: Boolean = true): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = 3000f)

    fun <T> gentleSettle(reducedMotion: Boolean = true): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = 3000f)

    fun <T> counterSettle(reducedMotion: Boolean = true): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = 3000f)
}

enum class GlassMaterialStrength {
    Card,
    Hero,
    FloatingDock,
    AccentMint,
    AccentSky
}

/**
 * Lightweight, zero-lag surface modifier: renders crisp elevated daylight cards immediately on
 * Frame 1 without per-card gesture coroutines, 3D perspective shaders, or continuous redraws.
 */
fun Modifier.livingGlassSurface(
    strength: GlassMaterialStrength = GlassMaterialStrength.Card,
    cornerRadius: Dp = 20.dp,
    interactive: Boolean = false,
    scrollParallaxFactor: Float = 0f
): Modifier = composed {
    val palette = LocalWhiteGlassPalette.current
    val shape: Shape = RoundedCornerShape(cornerRadius)

    val shadowElevation = when (strength) {
        GlassMaterialStrength.FloatingDock -> 6.dp
        GlassMaterialStrength.Hero -> 4.dp
        GlassMaterialStrength.Card -> 2.dp
        GlassMaterialStrength.AccentMint, GlassMaterialStrength.AccentSky -> 3.dp
    }

    val topFill: Color
    val bottomFill: Color
    val borderColor: Color
    when (strength) {
        GlassMaterialStrength.FloatingDock -> {
            topFill = Color(0xFFFFFFFF)
            bottomFill = Color(0xFFF8FAFC)
            borderColor = Color(0xFFCBD5E1)
        }
        GlassMaterialStrength.Hero -> {
            topFill = Color(0xFFFFFFFF)
            bottomFill = Color(0xFFF0F9FF)
            borderColor = Color(0xFFBAE6FD)
        }
        GlassMaterialStrength.AccentMint -> {
            topFill = Color(0xFFFFFFFF)
            bottomFill = palette.glassTintedMint
            borderColor = Color(0xFFA7F3D0)
        }
        GlassMaterialStrength.AccentSky -> {
            topFill = Color(0xFFFFFFFF)
            bottomFill = palette.glassTintedSky
            borderColor = Color(0xFFBAE6FD)
        }
        GlassMaterialStrength.Card -> {
            topFill = Color(0xFFFFFFFF)
            bottomFill = Color(0xFFF8FAFC)
            borderColor = Color(0xFFE2E8F0)
        }
    }

    this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = Color(0x140F172A),
            spotColor = Color(0x1A0F172A)
        )
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(colors = listOf(topFill, bottomFill)),
            shape = shape
        )
        .border(
            width = 1.dp,
            color = borderColor,
            shape = shape
        )
}

/**
 * Static, zero-CPU daylight backdrop — renders immediately on Frame 1 with zero animation overhead.
 */
@Composable
fun LiquidAtmosphericBackdrop(
    scrollOffsetPx: Float = 0f,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.canvasTop,
                        Color(0xFFF1F5F9),
                        palette.canvasBottom
                    )
                )
            )
    )
}

/**
 * Instant zero-delay section container (replaces staggered entrance animation so screens appear immediately).
 */
@Composable
fun ScrollRevealSection(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
    }
}
