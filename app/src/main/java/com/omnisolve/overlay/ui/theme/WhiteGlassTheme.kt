package com.omnisolve.overlay.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
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
    val canvasTop: Color = Color(0xFFF6FAFF),
    val canvasBottom: Color = Color(0xFFEAF2FB),
    val glassStrong: Color = Color(0xF7FFFFFF),
    val glassMedium: Color = Color(0xEDFFFFFF),
    val glassSubtle: Color = Color(0xDBFFFFFF),
    val glassTintedSky: Color = Color(0xF0F0F9FF),
    val glassTintedMint: Color = Color(0xF0ECFDF5),
    val rimHighlight: Color = Color(0xFAFFFFFF),
    val rimEdgeCool: Color = Color(0x420369A1),
    val rimEdgeMint: Color = Color(0x48047857),
    val inkPrimary: Color = Color(0xFF020617),
    val inkSecondary: Color = Color(0xFF0F172A),
    val inkMuted: Color = Color(0xFF1E293B),
    val accentSky: Color = Color(0xFF0369A1),
    val accentSkyBright: Color = Color(0xFF0284C7),
    val accentSkyContainer: Color = Color(0xFFE0F2FE),
    val accentMint: Color = Color(0xFF047857),
    val accentMintContainer: Color = Color(0xFFD1FAE5),
    val warningAmber: Color = Color(0xFFB45309),
    val warningContainer: Color = Color(0xFFFEF3C7),
    val dangerRose: Color = Color(0xFFBE123C),
    val dangerContainer: Color = Color(0xFFFFE4E6)
)

val LocalWhiteGlassPalette = staticCompositionLocalOf { WhiteGlassPalette() }

private val DaylightMaterialColorScheme = lightColorScheme(
    primary = Color(0xFF0369A1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF020617),
    secondary = Color(0xFF047857),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF022C22),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    background = Color(0xFFF4F8FD),
    onBackground = Color(0xFF020617),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF020617),
    surfaceVariant = Color(0xFFEFF5FC),
    onSurfaceVariant = Color(0xFF0F172A),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFBE123C),
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
    content: @Composable () -> Unit
) {
    val palette = WhiteGlassPalette()
    CompositionLocalProvider(LocalWhiteGlassPalette provides palette) {
        MaterialTheme(
            colorScheme = DaylightMaterialColorScheme,
            typography = WhiteGlassTypography,
            shapes = WhiteGlassShapes,
            content = content
        )
    }
}
