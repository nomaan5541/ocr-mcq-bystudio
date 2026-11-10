package com.omnisolve.overlay.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.R

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

@Immutable
data class WhiteGlassPalette(
    val isDarkObsidian: Boolean = false,
    val canvasTop: Color = Color(0xFFF6FAFF),
    val canvasBottom: Color = Color(0xFFEAF2FB),
    val glassStrong: Color = Color(0xFFFFFFFF),
    val glassMedium: Color = Color(0xFFF8FAFC),
    val glassSubtle: Color = Color(0xFFF1F5F9),
    val glassTintedSky: Color = Color(0xFFF0F9FF),
    val glassTintedMint: Color = Color(0xFFECFDF5),
    val rimHighlight: Color = Color(0xFFE2E8F0),
    val rimEdgeCool: Color = Color(0xFFBAE6FD),
    val rimEdgeMint: Color = Color(0xFFA7F3D0),
    val inkPrimary: Color = Color(0xFF020617),
    val inkSecondary: Color = Color(0xFF334155),
    val inkMuted: Color = Color(0xFF64748B),
    val accentSky: Color = Color(0xFF0284C7),
    val accentSkyBright: Color = Color(0xFF38BDF8),
    val accentSkyContainer: Color = Color(0xFFE0F2FE),
    val accentMint: Color = Color(0xFF047857),
    val accentMintContainer: Color = Color(0xFFD1FAE5),
    val accentEmerald: Color = Color(0xFF047857),
    val accentEmeraldContainer: Color = Color(0xFFD1FAE5),
    val warningAmber: Color = Color(0xFFB45309),
    val warningContainer: Color = Color(0xFFFEF3C7),
    val accentAmber: Color = Color(0xFFB45309),
    val accentAmberContainer: Color = Color(0xFFFEF3C7),
    val dangerRose: Color = Color(0xFFBE123C),
    val dangerContainer: Color = Color(0xFFFFE4E6),
    val cardHairlineBorder: Color = Color(0xFFCBD5E1)
)

private fun buildDaylightPalette() = WhiteGlassPalette(
    isDarkObsidian = false,
    canvasTop = Color(0xFFF8F9FA),
    canvasBottom = Color(0xFFF1F3F5),
    glassStrong = Color(0xFFFFFFFF),
    glassMedium = Color(0xFFF8FAFC),
    glassSubtle = Color(0xFFF1F5F9),
    glassTintedSky = Color(0xFFFEF2F2),
    glassTintedMint = Color(0xFFF0FDF4),
    rimHighlight = Color(0xFFE2E8F0),
    rimEdgeCool = Color(0xFFD71921),
    rimEdgeMint = Color(0xFF16A34A),
    inkPrimary = Color(0xFF0A0A0C),
    inkSecondary = Color(0xFF4B5563),
    inkMuted = Color(0xFF6B7280),
    accentSky = Color(0xFFD71921),
    accentSkyBright = Color(0xFFDC2626),
    accentSkyContainer = Color(0xFFFEE2E2),
    accentMint = Color(0xFF16A34A),
    accentMintContainer = Color(0xFFDCFCE7),
    accentEmerald = Color(0xFF16A34A),
    accentEmeraldContainer = Color(0xFFDCFCE7),
    warningAmber = Color(0xFFD97706),
    warningContainer = Color(0xFFFEF3C7),
    accentAmber = Color(0xFFD97706),
    accentAmberContainer = Color(0xFFFEF3C7),
    dangerRose = Color(0xFFD71921),
    dangerContainer = Color(0xFFFEE2E2),
    cardHairlineBorder = Color(0xFFE5E7EB)
)

private fun buildDarkObsidianPalette() = WhiteGlassPalette(
    isDarkObsidian = true,
    canvasTop = Color(0xFF0A0A0C),
    canvasBottom = Color(0xFF0A0A0C),
    glassStrong = Color(0xFF121316),
    glassMedium = Color(0xFF17181C),
    glassSubtle = Color(0xFF1D1F25),
    glassTintedSky = Color(0xFF2A1315),
    glassTintedMint = Color(0xFF132219),
    rimHighlight = Color(0xFF2B2D34),
    rimEdgeCool = Color(0xFFD71921),
    rimEdgeMint = Color(0xFF4ADE80),
    inkPrimary = Color(0xFFEEEEEE),
    inkSecondary = Color(0xFF767676),
    inkMuted = Color(0xFF555555),
    accentSky = Color(0xFFD71921),
    accentSkyBright = Color(0xFFFF3B30),
    accentSkyContainer = Color(0xFF2A1315),
    accentMint = Color(0xFF4ADE80),
    accentMintContainer = Color(0xFF1A211E),
    accentEmerald = Color(0xFF4ADE80),
    accentEmeraldContainer = Color(0xFF1A211E),
    warningAmber = Color(0xFFFBBF24),
    warningContainer = Color(0xFF332005),
    accentAmber = Color(0xFFFBBF24),
    accentAmberContainer = Color(0xFF332005),
    dangerRose = Color(0xFFD71921),
    dangerContainer = Color(0xFF2A1315),
    cardHairlineBorder = Color(0xFF23252A)
)

val LocalWhiteGlassPalette = staticCompositionLocalOf { buildDaylightPalette() }

private val DaylightMaterialColorScheme = lightColorScheme(
    primary = Color(0xFFD71921),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEE2E2),
    onPrimaryContainer = Color(0xFF450A0A),
    secondary = Color(0xFF16A34A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Color(0xFF052E16),
    tertiary = Color(0xFFD71921),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF0A0A0C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0A0C),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFE5E7EB),
    outlineVariant = Color(0xFFD1D5DB),
    error = Color(0xFFD71921),
    onError = Color.White
)

private val DarkObsidianMaterialColorScheme = darkColorScheme(
    primary = Color(0xFFD71921),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A1315),
    onPrimaryContainer = Color(0xFFFFD8D8),
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF052E16),
    secondaryContainer = Color(0xFF1A211E),
    onSecondaryContainer = Color(0xFFDCFCE7),
    tertiary = Color(0xFFD71921),
    onTertiary = Color.White,
    background = Color(0xFF0A0A0C),
    onBackground = Color(0xFFEEEEEE),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFF17181C),
    onSurfaceVariant = Color(0xFF767676),
    outline = Color(0xFF23252A),
    outlineVariant = Color(0xFF1D1F25),
    error = Color(0xFFD71921),
    onError = Color.White
)

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

val WhiteGlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun WhiteGlassTheme(
    darkObsidian: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkObsidian) buildDarkObsidianPalette() else buildDaylightPalette()
    val scheme = if (darkObsidian) DarkObsidianMaterialColorScheme else DaylightMaterialColorScheme
    CompositionLocalProvider(LocalWhiteGlassPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = WhiteGlassTypography,
            shapes = WhiteGlassShapes,
            content = content
        )
    }
}
