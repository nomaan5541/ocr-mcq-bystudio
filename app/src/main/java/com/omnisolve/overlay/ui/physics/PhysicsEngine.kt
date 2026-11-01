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

data class SpringPhysicsConfig(
    val enabled: Boolean = false,
    val dampingRatio: Float = 0.72f,
    val stiffness: Float = 650f
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
        reducedMotion: Boolean = true,
        config: SpringPhysicsConfig = SpringPhysicsConfig()
    ): SpringSpec<T> =
        if (!config.enabled || reducedMotion) spring(dampingRatio = 1f, stiffness = 3000f)
        else spring(dampingRatio = config.dampingRatio.coerceIn(0.4f, 1f), stiffness = config.stiffness.coerceIn(180f, 2000f))

    fun <T> liquidMorph(
        reducedMotion: Boolean = true,
        config: SpringPhysicsConfig = SpringPhysicsConfig()
    ): SpringSpec<T> = tactilePress(reducedMotion, config)

    fun <T> gentleSettle(
        reducedMotion: Boolean = true,
        config: SpringPhysicsConfig = SpringPhysicsConfig()
    ): SpringSpec<T> = tactilePress(reducedMotion, config)

    fun <T> counterSettle(
        reducedMotion: Boolean = true,
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
 * Lightweight, zero-lag surface modifier supporting both Daylight White Glass and Dark Obsidian Liquid Glass.
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
            topFill = palette.glassStrong
            bottomFill = palette.glassMedium
            borderColor = palette.rimHighlight
        }
        GlassMaterialStrength.Hero -> {
            topFill = palette.glassStrong
            bottomFill = palette.glassTintedSky
            borderColor = palette.rimEdgeCool
        }
        GlassMaterialStrength.AccentMint -> {
            topFill = palette.glassStrong
            bottomFill = palette.glassTintedMint
            borderColor = palette.rimEdgeMint
        }
        GlassMaterialStrength.AccentSky -> {
            topFill = palette.glassStrong
            bottomFill = palette.glassTintedSky
            borderColor = palette.rimEdgeCool
        }
        GlassMaterialStrength.Card -> {
            topFill = palette.glassStrong
            bottomFill = palette.glassMedium
            borderColor = palette.rimHighlight
        }
    }

    this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = if (palette.isDarkObsidian) Color(0x66000000) else Color(0x140F172A),
            spotColor = if (palette.isDarkObsidian) Color(0x80000000) else Color(0x1A0F172A)
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
 * Static, zero-CPU backdrop supporting both Daylight White Glass and Dark Obsidian Liquid Glass.
 */
@Composable
fun LiquidAtmosphericBackdrop(
    scrollOffsetPx: Float = 0f,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val midColor = if (palette.isDarkObsidian) Color(0xFF0B1120) else Color(0xFFF1F5F9)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.canvasTop,
                        midColor,
                        palette.canvasBottom
                    )
                )
            )
    )
}

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
