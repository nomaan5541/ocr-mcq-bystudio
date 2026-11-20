package com.omnisolve.overlay.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.R
import kotlin.math.roundToInt

val PlusJakartaFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val InterFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

/**
 * Dynamic Design System Palette powered by active [RemoteThemeSpec].
 * Defaults to Red Prism Obsidian (#0A0A0C / #121316 / #D71921 / #EEEEEE).
 */
@Immutable
data class WhiteGlassPalette(
    val isDarkObsidian: Boolean = true,
    val canvasTop: Color = Color(0xFF0A0A0C),
    val canvasBottom: Color = Color(0xFF0A0A0C),
    val glassStrong: Color = Color(0xFF121316),
    val glassMedium: Color = Color(0xFF17181C),
    val glassSubtle: Color = Color(0xFF1D1F24),
    val glassTintedSky: Color = Color(0xFF221114),
    val glassTintedMint: Color = Color(0xFF0D281E),
    val rimHighlight: Color = Color(0xFF23252A),
    val rimEdgeCool: Color = Color(0xFFD71921),
    val rimEdgeMint: Color = Color(0xFF10B981),
    val inkPrimary: Color = Color(0xFFEEEEEE),
    val inkSecondary: Color = Color(0xFF9E9EA6),
    val inkMuted: Color = Color(0xFF767676),
    val accentSky: Color = Color(0xFFD71921),
    val accentSkyBright: Color = Color(0xFFFF3B43),
    val accentSkyContainer: Color = Color(0xFF2D1014),
    val onAccentInk: Color = Color(0xFFF8FAFC),
    val accentMint: Color = Color(0xFF10B981),
    val accentMintContainer: Color = Color(0xFF064E3B),
    val accentEmerald: Color = Color(0xFF10B981),
    val accentEmeraldContainer: Color = Color(0xFF064E3B),
    val warningAmber: Color = Color(0xFFFBBF24),
    val warningContainer: Color = Color(0xFF2E2208),
    val accentAmber: Color = Color(0xFFFBBF24),
    val accentAmberContainer: Color = Color(0xFF2E2208),
    val dangerRose: Color = Color(0xFFD71921),
    val dangerContainer: Color = Color(0xFF360E12),
    val cardHairlineBorder: Color = Color(0xFF23252A)
)

fun contrastOnColor(background: Color): Color {
    val luma = 0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue
    return if (luma > 0.62f) Color(0xFF0A0C10) else Color(0xFFF8FAFC)
}

val LocalWhiteGlassPalette = staticCompositionLocalOf { NORMAL_DEFAULT_THEME.toPalette() }

val LocalPrismThemeSpec = staticCompositionLocalOf { NORMAL_DEFAULT_THEME }

val WhiteGlassTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.5.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )
)

private fun buildTypographyForTheme(useMonospaceAccents: Boolean): Typography {
    if (!useMonospaceAccents) return WhiteGlassTypography
    return WhiteGlassTypography.copy(
        labelMedium = WhiteGlassTypography.labelMedium.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.4.sp
        ),
        labelSmall = WhiteGlassTypography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
    )
}

private fun buildShapesForTheme(cornerRadiusDp: Int): Shapes {
    val raw = cornerRadiusDp.coerceIn(0, 36)
    val boosted = if (raw >= 10) (raw + 4).coerceAtMost(32) else raw
    return Shapes(
        extraSmall = RoundedCornerShape((boosted * 0.52f).roundToInt().coerceAtLeast(3).dp),
        small = RoundedCornerShape((boosted * 0.72f).roundToInt().coerceAtLeast(4).dp),
        medium = RoundedCornerShape(boosted.dp),
        large = RoundedCornerShape((boosted + 4).coerceAtMost(36).dp),
        extraLarge = RoundedCornerShape((boosted + 10).coerceAtMost(42).dp)
    )
}

/**
 * Harmonized concentric curve ratios that give rounded themes ultra-smooth sculptural curves
 * while keeping intentional angular/brutalist presets crisp.
 */
object PrismCurves {
    fun heroCorner(spec: RemoteThemeSpec): androidx.compose.ui.unit.Dp {
        val base = spec.cornerRadiusDp.coerceIn(0, 36)
        return if (base <= 6) base.coerceAtLeast(2).dp else (base + 10).coerceIn(22, 34).dp
    }

    fun cardCorner(spec: RemoteThemeSpec): androidx.compose.ui.unit.Dp {
        val base = spec.cornerRadiusDp.coerceIn(0, 36)
        return if (base <= 6) base.coerceAtLeast(2).dp else (base + 6).coerceIn(18, 30).dp
    }

    fun rowCorner(spec: RemoteThemeSpec): androidx.compose.ui.unit.Dp {
        val base = spec.cornerRadiusDp.coerceIn(0, 36)
        return if (base <= 6) base.coerceAtLeast(2).dp else (base + 1).coerceIn(15, 24).dp
    }

    fun innerStripCorner(spec: RemoteThemeSpec): androidx.compose.ui.unit.Dp {
        val base = spec.cornerRadiusDp.coerceIn(0, 36)
        return if (base <= 6) base.coerceAtLeast(2).dp else (base * 0.80f).roundToInt().coerceIn(12, 20).dp
    }

    fun iconWellCorner(spec: RemoteThemeSpec): androidx.compose.ui.unit.Dp {
        val base = spec.cornerRadiusDp.coerceIn(0, 36)
        return if (base <= 6) base.coerceAtLeast(2).dp else (base * 0.78f).roundToInt().coerceIn(12, 18).dp
    }

    fun pillOrButtonCorner(spec: RemoteThemeSpec): androidx.compose.ui.unit.Dp {
        val base = spec.cornerRadiusDp.coerceIn(0, 36)
        return if (base <= 6) base.coerceAtLeast(2).dp else 999.dp
    }

    fun dockCorner(spec: RemoteThemeSpec): androidx.compose.ui.unit.Dp {
        val base = spec.cornerRadiusDp.coerceIn(0, 36)
        return if (base <= 6) (base + 4).dp else 32.dp
    }

    fun dockPillCorner(spec: RemoteThemeSpec): androidx.compose.ui.unit.Dp {
        val base = spec.cornerRadiusDp.coerceIn(0, 36)
        return if (base <= 6) (base + 2).dp else 26.dp
    }
}

val WhiteGlassShapes = buildShapesForTheme(20)

@Composable
fun WhiteGlassTheme(
    themeSpec: RemoteThemeSpec = NORMAL_DEFAULT_THEME,
    darkObsidian: Boolean = themeSpec.isDark,
    content: @Composable () -> Unit
) {
    val targetPalette = remember(themeSpec) { themeSpec.toPalette() }
    val colorAnimSpec = remember {
        androidx.compose.animation.core.tween<Color>(
            durationMillis = 360,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        )
    }

    val canvasTop by androidx.compose.animation.animateColorAsState(targetPalette.canvasTop, colorAnimSpec, label = "theme_canvas_top")
    val canvasBottom by androidx.compose.animation.animateColorAsState(targetPalette.canvasBottom, colorAnimSpec, label = "theme_canvas_bottom")
    val glassStrong by androidx.compose.animation.animateColorAsState(targetPalette.glassStrong, colorAnimSpec, label = "theme_glass_strong")
    val glassMedium by androidx.compose.animation.animateColorAsState(targetPalette.glassMedium, colorAnimSpec, label = "theme_glass_medium")
    val glassSubtle by androidx.compose.animation.animateColorAsState(targetPalette.glassSubtle, colorAnimSpec, label = "theme_glass_subtle")
    val glassTintedSky by androidx.compose.animation.animateColorAsState(targetPalette.glassTintedSky, colorAnimSpec, label = "theme_glass_tinted_sky")
    val glassTintedMint by androidx.compose.animation.animateColorAsState(targetPalette.glassTintedMint, colorAnimSpec, label = "theme_glass_tinted_mint")
    val rimHighlight by androidx.compose.animation.animateColorAsState(targetPalette.rimHighlight, colorAnimSpec, label = "theme_rim_highlight")
    val rimEdgeCool by androidx.compose.animation.animateColorAsState(targetPalette.rimEdgeCool, colorAnimSpec, label = "theme_rim_edge_cool")
    val inkPrimary by androidx.compose.animation.animateColorAsState(targetPalette.inkPrimary, colorAnimSpec, label = "theme_ink_primary")
    val inkSecondary by androidx.compose.animation.animateColorAsState(targetPalette.inkSecondary, colorAnimSpec, label = "theme_ink_secondary")
    val inkMuted by androidx.compose.animation.animateColorAsState(targetPalette.inkMuted, colorAnimSpec, label = "theme_ink_muted")
    val accentSky by androidx.compose.animation.animateColorAsState(targetPalette.accentSky, colorAnimSpec, label = "theme_accent_sky")
    val accentSkyBright by androidx.compose.animation.animateColorAsState(targetPalette.accentSkyBright, colorAnimSpec, label = "theme_accent_bright")
    val accentSkyContainer by androidx.compose.animation.animateColorAsState(targetPalette.accentSkyContainer, colorAnimSpec, label = "theme_accent_container")
    val onAccentInk by androidx.compose.animation.animateColorAsState(targetPalette.onAccentInk, colorAnimSpec, label = "theme_on_accent")
    val cardHairlineBorder by androidx.compose.animation.animateColorAsState(targetPalette.cardHairlineBorder, colorAnimSpec, label = "theme_border")

    val animatedPalette = targetPalette.copy(
        canvasTop = canvasTop,
        canvasBottom = canvasBottom,
        glassStrong = glassStrong,
        glassMedium = glassMedium,
        glassSubtle = glassSubtle,
        glassTintedSky = glassTintedSky,
        glassTintedMint = glassTintedMint,
        rimHighlight = rimHighlight,
        rimEdgeCool = rimEdgeCool,
        inkPrimary = inkPrimary,
        inkSecondary = inkSecondary,
        inkMuted = inkMuted,
        accentSky = accentSky,
        accentSkyBright = accentSkyBright,
        accentSkyContainer = accentSkyContainer,
        onAccentInk = onAccentInk,
        cardHairlineBorder = cardHairlineBorder
    )

    val baseMaterialScheme = remember(themeSpec) { themeSpec.toMaterialColorScheme() }
    val materialScheme = baseMaterialScheme.copy(
        primary = accentSky,
        onPrimary = onAccentInk,
        primaryContainer = accentSkyContainer,
        onPrimaryContainer = inkPrimary,
        secondary = inkPrimary,
        onSecondary = canvasTop,
        secondaryContainer = glassMedium,
        onSecondaryContainer = inkPrimary,
        tertiary = inkMuted,
        onTertiary = inkPrimary,
        background = canvasTop,
        onBackground = inkPrimary,
        surface = glassStrong,
        onSurface = inkPrimary,
        surfaceVariant = glassMedium,
        onSurfaceVariant = inkSecondary,
        outline = cardHairlineBorder,
        outlineVariant = glassSubtle
    )

    val typography = remember(themeSpec.useMonospaceAccents) {
        buildTypographyForTheme(themeSpec.useMonospaceAccents)
    }
    val shapes = remember(themeSpec.cornerRadiusDp) {
        buildShapesForTheme(themeSpec.cornerRadiusDp)
    }

    CompositionLocalProvider(
        LocalWhiteGlassPalette provides animatedPalette,
        LocalPrismThemeSpec provides themeSpec
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
