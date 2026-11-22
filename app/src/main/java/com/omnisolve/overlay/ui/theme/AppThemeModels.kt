package com.omnisolve.overlay.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

enum class ThemeCategory(val id: String, val label: String) {
    DEFAULT("DEFAULT", "Default"),
    FUTURISTIC_SCIFI("FUTURISTIC_SCIFI", "Sci-Fi"),
    PREMIUM_LUXURY("PREMIUM_LUXURY", "Luxury"),
    EXPERIMENTAL_CRAZY("EXPERIMENTAL_CRAZY", "Experimental"),
    NATURE_ATMOSPHERIC("NATURE_ATMOSPHERIC", "Atmospheric"),
    MODERN_LANGUAGE("MODERN_LANGUAGE", "Modern"),
    GAMING_INTERACTIVE("GAMING_INTERACTIVE", "Gaming HUD"),
    UNUSUAL_CONCEPTS("UNUSUAL_CONCEPTS", "Concepts");

    companion object {
        fun fromId(id: String?): ThemeCategory =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class SurfaceEffectStyle(val id: String, val label: String) {
    AMBIENT_GLOW("AMBIENT_GLOW", "Ambient Prism"),
    HUD_RINGS("HUD_RINGS", "JARVIS Rings"),
    CYBER_GRID("CYBER_GRID", "Precision Grid"),
    NEURAL_NODES("NEURAL_NODES", "Neural Graph"),
    AURORA_VEIL("AURORA_VEIL", "Aurora Horizon"),
    DOT_MATRIX("DOT_MATRIX", "Dot Matrix"),
    BRUTALIST_GRID("BRUTALIST_GRID", "Brutalist Lines"),
    CARBON_WEAVE("CARBON_WEAVE", "Carbon Texture"),
    LIQUID_SPECULAR("LIQUID_SPECULAR", "Liquid Glass"),
    LAB_RETICLE("LAB_RETICLE", "Lab Telemetry"),
    PRISM_HORIZON("PRISM_HORIZON", "Prism Horizon"),
    IOS_LIQUID_GLASS("IOS_LIQUID_GLASS", "Frosted Light");

    companion object {
        fun fromId(id: String?): SurfaceEffectStyle =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: AMBIENT_GLOW
    }
}

enum class ThemeAnimationIntensity(val id: String, val label: String, val motionFactor: Float) {
    AUTO("AUTO", "Theme Default", 1f),
    SUBTLE("SUBTLE", "Subtle", 0.35f),
    INTERACTIVE("INTERACTIVE", "Interactive", 1.0f),
    EXTREME_3D("EXTREME_3D", "Extreme 3D", 1.65f);

    companion object {
        fun fromId(id: String?): ThemeAnimationIntensity =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: AUTO
    }
}

enum class ThemeColorAppearance(val id: String, val label: String) {
    AUTO("AUTO", "Theme Default"),
    DARK_ONLY("DARK_ONLY", "Dark Only"),
    DARK_SIGNATURE_RED("DARK_SIGNATURE_RED", "Dark + Signature Red"),
    ADAPTIVE_LIGHT_DARK("ADAPTIVE_LIGHT_DARK", "Adaptive Light/Dark");

    companion object {
        fun fromId(id: String?): ThemeColorAppearance =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: AUTO
    }
}

/**
 * Architectural layout blueprints that completely rearrange the app's header,
 * Home screen composition, tool navigation, and bottom dock per theme.
 */
enum class ThemeLayoutArchetype(
    val id: String,
    val label: String,
    val shortBadge: String,
    val layoutTagline: String
) {
    CLASSIC_PRISM(
        id = "CLASSIC_PRISM",
        label = "Classic Prism",
        shortBadge = "Classic",
        layoutTagline = "Main scanner card with a vertical list of tools"
    ),
    ATELIER_BENTO(
        id = "ATELIER_BENTO",
        label = "Bento Grid",
        shortBadge = "Bento Grid",
        layoutTagline = "Compact grid layout with quick launch tiles"
    ),
    TELEMETRY_HUD(
        id = "TELEMETRY_HUD",
        label = "Status Deck",
        shortBadge = "HUD Deck",
        layoutTagline = "Structured status strip with numbered tool rows"
    ),
    EDITORIAL_MONOGRAPH(
        id = "EDITORIAL_MONOGRAPH",
        label = "Editorial Studio",
        shortBadge = "Editorial",
        layoutTagline = "Centered header with a clean, focused layout"
    ),
    KINETIC_DECK(
        id = "KINETIC_DECK",
        label = "Quick Deck",
        shortBadge = "Kinetic",
        layoutTagline = "Split quick-action tiles with a central launch card"
    ),
    IOS_LIQUID_LIGHT(
        id = "IOS_LIQUID_LIGHT",
        label = "Prism Light",
        shortBadge = "Light",
        layoutTagline = "Bright white cards, clean layout, and a light floating pill"
    );

    companion object {
        fun fromId(id: String?): ThemeLayoutArchetype =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: CLASSIC_PRISM
    }
}

@Immutable
data class RemoteThemeSpec(
    val themeId: String = DEFAULT_THEME_ID,
    val themeNumber: Int = 0,
    val name: String = "Red Prism Obsidian",
    val tagline: String = "Dark background with Prism red accents",
    val category: ThemeCategory = ThemeCategory.DEFAULT,
    val isDark: Boolean = true,
    val isPublic: Boolean = true,
    val canvasTopHex: String = "#0A0A0C",
    val canvasBottomHex: String = "#0A0A0C",
    val surfacePrimaryHex: String = "#121316",
    val surfaceElevatedHex: String = "#17181C",
    val surfaceSubtleHex: String = "#1D1F24",
    val borderHex: String = "#23252A",
    val accentPrimaryHex: String = "#D71921",
    val accentBrightHex: String = "#FF3B43",
    val accentContainerHex: String = "#2C1014",
    val inkPrimaryHex: String = "#EEEEEE",
    val inkSecondaryHex: String = "#9E9EA6",
    val inkMutedHex: String = "#767676",
    val successHex: String = "#10B981",
    val warningHex: String = "#FBBF24",
    val errorHex: String = "#F87171",
    val cornerRadiusDp: Int = 20,
    val borderWidthTenths: Int = 10,
    val surfaceEffect: SurfaceEffectStyle = SurfaceEffectStyle.AMBIENT_GLOW,
    val animationIntensity: ThemeAnimationIntensity = ThemeAnimationIntensity.INTERACTIVE,
    val colorAppearance: ThemeColorAppearance = ThemeColorAppearance.DARK_SIGNATURE_RED,
    val useMonospaceAccents: Boolean = false,
    val islandAccentHex: String = "#D71921",
    val version: Int = 1,
    val isTopRecommendedForMcq: Boolean = false,
    val layoutArchetype: ThemeLayoutArchetype = ThemeLayoutArchetype.CLASSIC_PRISM
) {
    val isNormalDefault: Boolean
        get() = themeId == DEFAULT_THEME_ID

    fun parseColor(hex: String, fallback: Color): Color = parseHexColorOr(hex, fallback)

    fun resolveWithOverrides(
        systemIsDark: Boolean,
        animationOverride: ThemeAnimationIntensity,
        appearanceOverride: ThemeColorAppearance
    ): RemoteThemeSpec {
        val effectiveAnim = if (animationOverride == ThemeAnimationIntensity.AUTO) {
            animationIntensity
        } else {
            animationOverride
        }
        val effectiveAppearance = if (appearanceOverride == ThemeColorAppearance.AUTO) {
            colorAppearance
        } else {
            appearanceOverride
        }

        val withAppearance = when (effectiveAppearance) {
            ThemeColorAppearance.DARK_SIGNATURE_RED -> copy(
                accentPrimaryHex = "#D71921",
                accentBrightHex = "#FF4650",
                accentContainerHex = "#2C1014",
                islandAccentHex = "#D71921",
                errorHex = "#F87171",
                isDark = true
            )
            ThemeColorAppearance.ADAPTIVE_LIGHT_DARK -> {
                if (!systemIsDark && isDark) {
                    // Clean, unslop daylight paper variant preserving the theme's signature accent hue
                    copy(
                        isDark = false,
                        canvasTopHex = "#F6F7F9",
                        canvasBottomHex = "#EEF1F5",
                        surfacePrimaryHex = "#FFFFFF",
                        surfaceElevatedHex = "#F1F3F7",
                        surfaceSubtleHex = "#E6E9EF",
                        borderHex = "#D5DAE3",
                        inkPrimaryHex = "#101216",
                        inkSecondaryHex = "#4B5262",
                        inkMutedHex = "#7A8294",
                        errorHex = "#B91C1C",
                        accentContainerHex = accentPrimaryHex
                    )
                } else {
                    this
                }
            }
            else -> this
        }

        return withAppearance.copy(
            animationIntensity = effectiveAnim,
            colorAppearance = effectiveAppearance
        )
    }

    fun toPalette(): WhiteGlassPalette {
        val canvasTop = parseHexColorOr(canvasTopHex, Color(0xFF0A0A0C))
        val canvasBottom = parseHexColorOr(canvasBottomHex, Color(0xFF0A0A0C))
        val surfacePrimary = parseHexColorOr(surfacePrimaryHex, Color(0xFF121316))
        val surfaceElevated = parseHexColorOr(surfaceElevatedHex, Color(0xFF17181C))
        val surfaceSubtle = parseHexColorOr(surfaceSubtleHex, Color(0xFF1E2026))
        val border = parseHexColorOr(borderHex, Color(0xFF23252A))
        val accent = parseHexColorOr(accentPrimaryHex, Color(0xFFD71921))
        val accentBright = parseHexColorOr(accentBrightHex, Color(0xFFFF3B43))
        val accentContainer = parseHexColorOr(accentContainerHex, Color(0xFF2C1014))
        val inkPrimary = parseHexColorOr(inkPrimaryHex, Color(0xFFEEEEEE))
        val inkSecondary = parseHexColorOr(inkSecondaryHex, Color(0xFF9E9EA6))
        val inkMuted = parseHexColorOr(inkMutedHex, Color(0xFF767676))
        val success = parseHexColorOr(successHex, Color(0xFF10B981))
        val warning = parseHexColorOr(warningHex, Color(0xFFFBBF24))
        val resolvedErrorHex = if (isDark) errorHex else if (errorHex.equals("#F87171", ignoreCase = true)) "#B91C1C" else errorHex
        val danger = parseHexColorOr(resolvedErrorHex, if (isDark) Color(0xFFF87171) else Color(0xFFB91C1C))

        val tintedAccent = if (isDark) {
            blendColors(surfacePrimary, accent, 0.14f)
        } else {
            blendColors(surfacePrimary, accent, 0.08f)
        }
        val tintedMint = if (isDark) {
            blendColors(surfacePrimary, success, 0.14f)
        } else {
            blendColors(surfacePrimary, success, 0.09f)
        }
        val successContainer = if (isDark) {
            blendColors(surfacePrimary, success, 0.22f)
        } else {
            blendColors(Color.White, success, 0.16f)
        }
        val warnContainer = if (isDark) {
            blendColors(surfacePrimary, warning, 0.18f)
        } else {
            blendColors(Color.White, warning, 0.16f)
        }
        val errContainer = if (isDark) {
            blendColors(surfacePrimary, danger, 0.20f)
        } else {
            blendColors(Color.White, danger, 0.14f)
        }

        val resolvedAccentContainer = if (!isDark && !isColorLight(accentContainer)) {
            blendColors(Color.White, accent, 0.14f)
        } else {
            accentContainer
        }
        val onAccent = if (isColorLight(accent)) Color(0xFF0A0C10) else Color(0xFFF8FAFC)

        return WhiteGlassPalette(
            isDarkObsidian = isDark,
            canvasTop = canvasTop,
            canvasBottom = canvasBottom,
            glassStrong = surfacePrimary,
            glassMedium = surfaceElevated,
            glassSubtle = surfaceSubtle,
            glassTintedSky = tintedAccent,
            glassTintedMint = tintedMint,
            rimHighlight = border,
            rimEdgeCool = accent,
            rimEdgeMint = success,
            inkPrimary = inkPrimary,
            inkSecondary = inkSecondary,
            inkMuted = inkMuted,
            accentSky = accent,
            accentSkyBright = accentBright,
            accentSkyContainer = resolvedAccentContainer,
            onAccentInk = onAccent,
            accentMint = success,
            accentMintContainer = successContainer,
            accentEmerald = success,
            accentEmeraldContainer = successContainer,
            warningAmber = warning,
            warningContainer = warnContainer,
            accentAmber = warning,
            accentAmberContainer = warnContainer,
            dangerRose = danger,
            dangerContainer = errContainer,
            cardHairlineBorder = border
        )
    }

    fun toMaterialColorScheme(): ColorScheme {
        val p = toPalette()
        val onAccent = p.onAccentInk
        return if (isDark) {
            darkColorScheme(
                primary = p.accentSky,
                onPrimary = onAccent,
                primaryContainer = p.accentSkyContainer,
                onPrimaryContainer = p.inkPrimary,
                secondary = p.inkPrimary,
                onSecondary = p.canvasTop,
                secondaryContainer = p.glassMedium,
                onSecondaryContainer = p.inkPrimary,
                tertiary = p.inkMuted,
                onTertiary = p.inkPrimary,
                background = p.canvasTop,
                onBackground = p.inkPrimary,
                surface = p.glassStrong,
                onSurface = p.inkPrimary,
                surfaceVariant = p.glassMedium,
                onSurfaceVariant = p.inkSecondary,
                outline = p.cardHairlineBorder,
                outlineVariant = p.glassSubtle,
                error = p.dangerRose,
                onError = Color.White
            )
        } else {
            lightColorScheme(
                primary = p.accentSky,
                onPrimary = onAccent,
                primaryContainer = p.accentSkyContainer,
                onPrimaryContainer = p.inkPrimary,
                secondary = p.inkPrimary,
                onSecondary = p.canvasTop,
                secondaryContainer = p.glassMedium,
                onSecondaryContainer = p.inkPrimary,
                tertiary = p.inkMuted,
                onTertiary = p.inkPrimary,
                background = p.canvasTop,
                onBackground = p.inkPrimary,
                surface = p.glassStrong,
                onSurface = p.inkPrimary,
                surfaceVariant = p.glassMedium,
                onSurfaceVariant = p.inkSecondary,
                outline = p.cardHairlineBorder,
                outlineVariant = p.glassSubtle,
                error = p.dangerRose,
                onError = Color.White
            )
        }
    }

    fun toJsonString(): String = JSONObject().apply {
        put("themeId", themeId)
        put("themeNumber", themeNumber)
        put("name", name)
        put("tagline", tagline)
        put("category", category.id)
        put("isDark", isDark)
        put("isPublic", isPublic)
        put("canvasTopHex", canvasTopHex)
        put("canvasBottomHex", canvasBottomHex)
        put("surfacePrimaryHex", surfacePrimaryHex)
        put("surfaceElevatedHex", surfaceElevatedHex)
        put("surfaceSubtleHex", surfaceSubtleHex)
        put("borderHex", borderHex)
        put("accentPrimaryHex", accentPrimaryHex)
        put("accentBrightHex", accentBrightHex)
        put("accentContainerHex", accentContainerHex)
        put("inkPrimaryHex", inkPrimaryHex)
        put("inkSecondaryHex", inkSecondaryHex)
        put("inkMutedHex", inkMutedHex)
        put("successHex", successHex)
        put("warningHex", warningHex)
        put("errorHex", errorHex)
        put("cornerRadiusDp", cornerRadiusDp)
        put("borderWidthTenths", borderWidthTenths)
        put("surfaceEffect", surfaceEffect.id)
        put("animationIntensity", animationIntensity.id)
        put("colorAppearance", colorAppearance.id)
        put("useMonospaceAccents", useMonospaceAccents)
        put("islandAccentHex", islandAccentHex)
        put("version", version)
        put("layoutArchetype", layoutArchetype.id)
    }.toString()

    companion object {
        const val DEFAULT_THEME_ID = "default_prism_crimson"

        fun fromJsonString(raw: String?): RemoteThemeSpec? {
            if (raw.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(raw)
                RemoteThemeSpec(
                    themeId = obj.optString("themeId", DEFAULT_THEME_ID).ifBlank { DEFAULT_THEME_ID },
                    themeNumber = obj.optInt("themeNumber", 0),
                    name = obj.optString("name", "Red Prism Obsidian"),
                    tagline = obj.optString("tagline", ""),
                    category = ThemeCategory.fromId(obj.optString("category")),
                    isDark = obj.optBoolean("isDark", true),
                    isPublic = obj.optBoolean("isPublic", true),
                    canvasTopHex = normalizeHex(obj.optString("canvasTopHex"), "#0A0A0C"),
                    canvasBottomHex = normalizeHex(obj.optString("canvasBottomHex"), "#0A0A0C"),
                    surfacePrimaryHex = normalizeHex(obj.optString("surfacePrimaryHex"), "#121316"),
                    surfaceElevatedHex = normalizeHex(obj.optString("surfaceElevatedHex"), "#17181C"),
                    surfaceSubtleHex = normalizeHex(obj.optString("surfaceSubtleHex"), "#1D1F24"),
                    borderHex = normalizeHex(obj.optString("borderHex"), "#23252A"),
                    accentPrimaryHex = normalizeHex(obj.optString("accentPrimaryHex"), "#D71921"),
                    accentBrightHex = normalizeHex(obj.optString("accentBrightHex"), "#FF3B43"),
                    accentContainerHex = normalizeHex(obj.optString("accentContainerHex"), "#2C1014"),
                    inkPrimaryHex = normalizeHex(obj.optString("inkPrimaryHex"), "#EEEEEE"),
                    inkSecondaryHex = normalizeHex(obj.optString("inkSecondaryHex"), "#9E9EA6"),
                    inkMutedHex = normalizeHex(obj.optString("inkMutedHex"), "#767676"),
                    successHex = normalizeHex(obj.optString("successHex"), "#10B981"),
                    warningHex = normalizeHex(obj.optString("warningHex"), "#FBBF24"),
                    errorHex = normalizeHex(
                        obj.optString("errorHex"),
                        if (obj.optBoolean("isDark", true)) "#F87171" else "#B91C1C"
                    ),
                    cornerRadiusDp = obj.optInt("cornerRadiusDp", 20).coerceIn(0, 36),
                    borderWidthTenths = obj.optInt("borderWidthTenths", 10).coerceIn(5, 35),
                    surfaceEffect = SurfaceEffectStyle.fromId(obj.optString("surfaceEffect")),
                    animationIntensity = ThemeAnimationIntensity.fromId(obj.optString("animationIntensity")),
                    colorAppearance = ThemeColorAppearance.fromId(obj.optString("colorAppearance")),
                    useMonospaceAccents = obj.optBoolean("useMonospaceAccents", false),
                    islandAccentHex = normalizeHex(obj.optString("islandAccentHex"), "#D71921"),
                    version = obj.optInt("version", 1).coerceAtLeast(1),
                    layoutArchetype = ThemeLayoutArchetype.fromId(obj.optString("layoutArchetype"))
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

fun normalizeHex(raw: String?, fallback: String): String {
    val cleaned = raw?.trim().orEmpty()
    return if (Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$").matches(cleaned)) {
        cleaned.uppercase()
    } else {
        fallback
    }
}

fun parseHexColorOr(hex: String, fallback: Color): Color {
    val clean = hex.trim().removePrefix("#")
    return try {
        when (clean.length) {
            6 -> Color(0xFF000000L or clean.toLong(16))
            8 -> Color(clean.toLong(16))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

private fun blendColors(base: Color, overlay: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = base.red * (1f - t) + overlay.red * t,
        green = base.green * (1f - t) + overlay.green * t,
        blue = base.blue * (1f - t) + overlay.blue * t,
        alpha = 1f
    )
}

private fun isColorLight(color: Color): Boolean {
    val luma = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return luma > 0.62f
}

val NORMAL_DEFAULT_THEME = RemoteThemeSpec()

/**
 * Helper builder for unslop-calibrated dark & light themes with strict contrast,
 * quiet chrome surfaces, and a focused single primary accent.
 */
private fun unslopTheme(
    number: Int,
    id: String,
    name: String,
    tagline: String,
    category: ThemeCategory,
    canvas: String,
    surface: String,
    elevated: String,
    subtle: String,
    border: String,
    accent: String,
    accentBright: String,
    accentContainer: String,
    isDark: Boolean = true,
    inkPrimary: String = if (isDark) "#EFF2F7" else "#101216",
    inkSecondary: String = if (isDark) "#9AA3B2" else "#4C5566",
    inkMuted: String = if (isDark) "#687082" else "#7E8699",
    success: String = if (isDark) "#10B981" else "#059669",
    warning: String = if (isDark) "#FBBF24" else "#D97706",
    error: String = if (isDark) "#F87171" else "#B91C1C",
    radiusDp: Int = 18,
    borderWidthTenths: Int = 10,
    effect: SurfaceEffectStyle = SurfaceEffectStyle.AMBIENT_GLOW,
    animation: ThemeAnimationIntensity = ThemeAnimationIntensity.INTERACTIVE,
    appearance: ThemeColorAppearance = if (isDark) ThemeColorAppearance.DARK_ONLY else ThemeColorAppearance.ADAPTIVE_LIGHT_DARK,
    mono: Boolean = false,
    topMcq: Boolean = false,
    layout: ThemeLayoutArchetype? = null
): RemoteThemeSpec {
    val resolvedLayout = layout ?: when (category) {
        ThemeCategory.DEFAULT -> ThemeLayoutArchetype.CLASSIC_PRISM
        ThemeCategory.FUTURISTIC_SCIFI, ThemeCategory.GAMING_INTERACTIVE -> ThemeLayoutArchetype.TELEMETRY_HUD
        ThemeCategory.PREMIUM_LUXURY, ThemeCategory.MODERN_LANGUAGE -> ThemeLayoutArchetype.ATELIER_BENTO
        ThemeCategory.NATURE_ATMOSPHERIC -> ThemeLayoutArchetype.EDITORIAL_MONOGRAPH
        ThemeCategory.EXPERIMENTAL_CRAZY, ThemeCategory.UNUSUAL_CONCEPTS -> ThemeLayoutArchetype.KINETIC_DECK
    }
    return RemoteThemeSpec(
        themeId = id,
        themeNumber = number,
        name = name,
        tagline = tagline,
        category = category,
        isDark = isDark,
        isPublic = true,
        canvasTopHex = canvas,
        canvasBottomHex = canvas,
        surfacePrimaryHex = surface,
        surfaceElevatedHex = elevated,
        surfaceSubtleHex = subtle,
        borderHex = border,
        accentPrimaryHex = accent,
        accentBrightHex = accentBright,
        accentContainerHex = accentContainer,
        inkPrimaryHex = inkPrimary,
        inkSecondaryHex = inkSecondary,
        inkMutedHex = inkMuted,
        successHex = success,
        warningHex = warning,
        errorHex = error,
        cornerRadiusDp = radiusDp,
        borderWidthTenths = borderWidthTenths,
        surfaceEffect = effect,
        animationIntensity = animation,
        colorAppearance = appearance,
        useMonospaceAccents = mono,
        islandAccentHex = accent,
        version = 1,
        isTopRecommendedForMcq = topMcq,
        layoutArchetype = resolvedLayout
    )
}

/**
 * Complete 50-theme /unslop catalog covering all 7 requested categories + Top 5 MCQ picks.
 */
val ALL_50_UNSLOP_THEMES: List<RemoteThemeSpec> = listOf(
    // ── 1. Futuristic & Sci-Fi Themes (1..6) ────────────────────────────────
    unslopTheme(
        number = 1,
        id = "cyberpunk_interface",
        name = "Cyberpunk Interface",
        tagline = "Voltage cyan & obsidian panels with crisp technical lines",
        category = ThemeCategory.FUTURISTIC_SCIFI,
        canvas = "#07090F",
        surface = "#0D111C",
        elevated = "#13192A",
        subtle = "#1A2238",
        border = "#23304F",
        accent = "#00E5FF",
        accentBright = "#67F2FF",
        accentContainer = "#092D3B",
        radiusDp = 12,
        borderWidthTenths = 12,
        effect = SurfaceEffectStyle.CYBER_GRID,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true
    ),
    unslopTheme(
        number = 2,
        id = "jarvis_ai",
        name = "JARVIS AI",
        tagline = "Holographic telemetry rings & surgical ice-blue precision HUD",
        category = ThemeCategory.FUTURISTIC_SCIFI,
        canvas = "#060B12",
        surface = "#0B1320",
        elevated = "#101B2D",
        subtle = "#16243B",
        border = "#1F3654",
        accent = "#38BDF8",
        accentBright = "#7DD3FC",
        accentContainer = "#0D2B45",
        radiusDp = 16,
        borderWidthTenths = 11,
        effect = SurfaceEffectStyle.HUD_RINGS,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true,
        topMcq = true
    ),
    unslopTheme(
        number = 3,
        id = "spaceship_cockpit",
        name = "Spaceship Cockpit",
        tagline = "Flight deck gauges, amber warning trims & matte slate bays",
        category = ThemeCategory.FUTURISTIC_SCIFI,
        canvas = "#0A0C0E",
        surface = "#111519",
        elevated = "#171C22",
        subtle = "#1F262E",
        border = "#2B343F",
        accent = "#F59E0B",
        accentBright = "#FBBF24",
        accentContainer = "#332206",
        radiusDp = 10,
        borderWidthTenths = 12,
        effect = SurfaceEffectStyle.LAB_RETICLE,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true
    ),
    unslopTheme(
        number = 4,
        id = "alien_technology",
        name = "Alien Technology",
        tagline = "Bioluminescent phosphor teal & asymmetric obsidian plating",
        category = ThemeCategory.FUTURISTIC_SCIFI,
        canvas = "#050B0A",
        surface = "#0B1513",
        elevated = "#11201D",
        subtle = "#182B27",
        border = "#213E38",
        accent = "#2DD4BF",
        accentBright = "#5EEAD4",
        accentContainer = "#0B332E",
        radiusDp = 24,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.AURORA_VEIL,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 5,
        id = "tactical_command",
        name = "Tactical Command",
        tagline = "Matte olive dark panels with crisp amber accents",
        category = ThemeCategory.FUTURISTIC_SCIFI,
        canvas = "#080A08",
        surface = "#101410",
        elevated = "#161C16",
        subtle = "#1E261E",
        border = "#2B362B",
        accent = "#84CC16",
        accentBright = "#A3E635",
        accentContainer = "#20300A",
        radiusDp = 8,
        borderWidthTenths = 12,
        effect = SurfaceEffectStyle.CYBER_GRID,
        animation = ThemeAnimationIntensity.SUBTLE,
        mono = true
    ),
    unslopTheme(
        number = 6,
        id = "neural_network",
        name = "Neural Network",
        tagline = "Connected synaptic indigo nodes over calm obsidian depth",
        category = ThemeCategory.FUTURISTIC_SCIFI,
        canvas = "#080810",
        surface = "#10101D",
        elevated = "#161728",
        subtle = "#1E2036",
        border = "#2A2D4A",
        accent = "#818CF8",
        accentBright = "#A5B4FC",
        accentContainer = "#1F224F",
        radiusDp = 18,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.NEURAL_NODES,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),

    // ── 2. Premium Luxury Themes (7..12) ────────────────────────────────────
    unslopTheme(
        number = 7,
        id = "black_titanium",
        name = "Black Titanium",
        tagline = "Brushed PVD titanium surfaces with restrained ice-silver focus",
        category = ThemeCategory.PREMIUM_LUXURY,
        canvas = "#09090B",
        surface = "#111215",
        elevated = "#17191D",
        subtle = "#1F2227",
        border = "#2C3038",
        accent = "#E4E4E7",
        accentBright = "#FFFFFF",
        accentContainer = "#26282E",
        radiusDp = 16,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.CARBON_WEAVE,
        animation = ThemeAnimationIntensity.SUBTLE,
        topMcq = true
    ),
    unslopTheme(
        number = 8,
        id = "obsidian_glass",
        name = "Obsidian Glass",
        tagline = "Ultra-quiet smoked crystal cards & razor-sharp specular edges",
        category = ThemeCategory.PREMIUM_LUXURY,
        canvas = "#070709",
        surface = "#101114",
        elevated = "#16181D",
        subtle = "#1D2026",
        border = "#282B33",
        accent = "#F43F5E",
        accentBright = "#FB7185",
        accentContainer = "#331019",
        radiusDp = 22,
        borderWidthTenths = 9,
        effect = SurfaceEffectStyle.LIQUID_SPECULAR,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        appearance = ThemeColorAppearance.DARK_SIGNATURE_RED,
        topMcq = true
    ),
    unslopTheme(
        number = 9,
        id = "royal_gold",
        name = "Royal Gold",
        tagline = "Champagne bullion highlights set against deep velvet noir",
        category = ThemeCategory.PREMIUM_LUXURY,
        canvas = "#090806",
        surface = "#13110D",
        elevated = "#1B1812",
        subtle = "#242018",
        border = "#352F23",
        accent = "#E5B95C",
        accentBright = "#F5D07A",
        accentContainer = "#332710",
        radiusDp = 18,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.AMBIENT_GLOW,
        animation = ThemeAnimationIntensity.SUBTLE
    ),
    unslopTheme(
        number = 10,
        id = "carbon_fiber",
        name = "Carbon Fiber",
        tagline = "Motorsport twill texture paired with pit-lane scarlet accent",
        category = ThemeCategory.PREMIUM_LUXURY,
        canvas = "#08090B",
        surface = "#111216",
        elevated = "#17191F",
        subtle = "#1F222A",
        border = "#2A2E38",
        accent = "#EF4444",
        accentBright = "#F87171",
        accentContainer = "#341012",
        radiusDp = 14,
        borderWidthTenths = 11,
        effect = SurfaceEffectStyle.CARBON_WEAVE,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        appearance = ThemeColorAppearance.DARK_SIGNATURE_RED
    ),
    unslopTheme(
        number = 11,
        id = "editorial_luxury",
        name = "Editorial Luxury",
        tagline = "Warm alabaster serif-inspired poise & oxblood ink restraint",
        category = ThemeCategory.PREMIUM_LUXURY,
        canvas = "#0C0A0A",
        surface = "#151212",
        elevated = "#1D1919",
        subtle = "#262121",
        border = "#362E2E",
        accent = "#D4A373",
        accentBright = "#E6BE94",
        accentContainer = "#332419",
        radiusDp = 14,
        borderWidthTenths = 8,
        effect = SurfaceEffectStyle.AMBIENT_GLOW,
        animation = ThemeAnimationIntensity.SUBTLE
    ),
    unslopTheme(
        number = 12,
        id = "chrome_minimalism",
        name = "Chrome Minimalism",
        tagline = "Precision anodized aluminum curves with neutral slate clarity",
        category = ThemeCategory.PREMIUM_LUXURY,
        canvas = "#0B0C0E",
        surface = "#131518",
        elevated = "#1A1D21",
        subtle = "#22262C",
        border = "#313740",
        accent = "#CBD5E1",
        accentBright = "#F1F5F9",
        accentContainer = "#252B33",
        radiusDp = 20,
        borderWidthTenths = 9,
        effect = SurfaceEffectStyle.LIQUID_SPECULAR,
        animation = ThemeAnimationIntensity.SUBTLE
    ),

    // ── 3. Experimental & Crazy Themes (13..20) ─────────────────────────────
    unslopTheme(
        number = 13,
        id = "liquid_glass",
        name = "Liquid Glass",
        tagline = "Refractive frosted planes with buoyant depth & azure light",
        category = ThemeCategory.EXPERIMENTAL_CRAZY,
        canvas = "#080C12",
        surface = "#101722",
        elevated = "#16202F",
        subtle = "#1D2A3D",
        border = "#2B3D57",
        accent = "#60A5FA",
        accentBright = "#93C5FD",
        accentContainer = "#142A4A",
        radiusDp = 26,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.LIQUID_SPECULAR,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 14,
        id = "morphing_fluid",
        name = "Morphing Fluid",
        tagline = "Soft organic capsules with violet-plum depth & silky springs",
        category = ThemeCategory.EXPERIMENTAL_CRAZY,
        canvas = "#09070E",
        surface = "#120F1C",
        elevated = "#1A1628",
        subtle = "#231E36",
        border = "#322B4D",
        accent = "#C084FC",
        accentBright = "#D8B4FE",
        accentContainer = "#2D1B4E",
        radiusDp = 28,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.AURORA_VEIL,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 15,
        id = "digital_brutalism",
        name = "Digital Brutalism",
        tagline = "Sharp square cards, bold borders, and bright lime accents",
        category = ThemeCategory.EXPERIMENTAL_CRAZY,
        canvas = "#080808",
        surface = "#111111",
        elevated = "#191919",
        subtle = "#222222",
        border = "#3A3A3A",
        accent = "#D9F99D",
        accentBright = "#ECFCCB",
        accentContainer = "#263015",
        radiusDp = 4,
        borderWidthTenths = 18,
        effect = SurfaceEffectStyle.BRUTALIST_GRID,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true
    ),
    unslopTheme(
        number = 16,
        id = "surreal_3d",
        name = "Surreal 3D",
        tagline = "Sculptural shadows, high-elevation slabs & warm tangerine spark",
        category = ThemeCategory.EXPERIMENTAL_CRAZY,
        canvas = "#0B090C",
        surface = "#151218",
        elevated = "#1E1922",
        subtle = "#27212C",
        border = "#382F3F",
        accent = "#FB923C",
        accentBright = "#FDBA74",
        accentContainer = "#3B1E0D",
        radiusDp = 24,
        borderWidthTenths = 11,
        effect = SurfaceEffectStyle.LIQUID_SPECULAR,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 17,
        id = "kinetic_typography",
        name = "Kinetic Typography",
        tagline = "Swiss editorial contrast with bold tabular ink & vermilion punch",
        category = ThemeCategory.EXPERIMENTAL_CRAZY,
        canvas = "#0A0A0B",
        surface = "#131316",
        elevated = "#1B1B20",
        subtle = "#24242B",
        border = "#34343E",
        accent = "#FF4D4D",
        accentBright = "#FF7878",
        accentContainer = "#381214",
        radiusDp = 8,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.BRUTALIST_GRID,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true
    ),
    unslopTheme(
        number = 18,
        id = "generative_particles",
        name = "Generative Particles",
        tagline = "Fine dot-field cosmos & periwinkle signal telemetry",
        category = ThemeCategory.EXPERIMENTAL_CRAZY,
        canvas = "#060810",
        surface = "#0D111F",
        elevated = "#13192D",
        subtle = "#1A223B",
        border = "#263154",
        accent = "#7C8CFF",
        accentBright = "#A3AEFF",
        accentContainer = "#1B224D",
        radiusDp = 20,
        borderWidthTenths = 9,
        effect = SurfaceEffectStyle.DOT_MATRIX,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 19,
        id = "holographic",
        name = "Holographic",
        tagline = "Iridescent aqua-violet sheen tempered over deep gunmetal",
        category = ThemeCategory.EXPERIMENTAL_CRAZY,
        canvas = "#07090E",
        surface = "#0E131D",
        elevated = "#151C2B",
        subtle = "#1D2639",
        border = "#2B3852",
        accent = "#22D3EE",
        accentBright = "#A78BFA",
        accentContainer = "#102B3D",
        radiusDp = 22,
        borderWidthTenths = 11,
        effect = SurfaceEffectStyle.AURORA_VEIL,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 20,
        id = "floating_dimension",
        name = "Floating Dimension",
        tagline = "Layered Z-depth floating cards with crisp rose-coral edge",
        category = ThemeCategory.EXPERIMENTAL_CRAZY,
        canvas = "#090A0E",
        surface = "#12141B",
        elevated = "#191C26",
        subtle = "#212533",
        border = "#2E3447",
        accent = "#F472B6",
        accentBright = "#F9A8D4",
        accentContainer = "#3B172C",
        radiusDp = 24,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.LIQUID_SPECULAR,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),

    // ── 4. Nature & Atmospheric Themes (21..26) ─────────────────────────────
    unslopTheme(
        number = 21,
        id = "northern_lights",
        name = "Northern Lights",
        tagline = "Arctic night sky with calm aurora emerald horizon veil",
        category = ThemeCategory.NATURE_ATMOSPHERIC,
        canvas = "#060B0E",
        surface = "#0B1419",
        elevated = "#111E25",
        subtle = "#182932",
        border = "#223A46",
        accent = "#34D399",
        accentBright = "#6EE7B7",
        accentContainer = "#0B3328",
        radiusDp = 20,
        borderWidthTenths = 9,
        effect = SurfaceEffectStyle.AURORA_VEIL,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 22,
        id = "deep_ocean",
        name = "Deep Ocean",
        tagline = "Abyssal trench navy with pelagic turquoise instruments",
        category = ThemeCategory.NATURE_ATMOSPHERIC,
        canvas = "#040911",
        surface = "#091220",
        elevated = "#0F1B2E",
        subtle = "#15253D",
        border = "#1F3554",
        accent = "#0EA5E9",
        accentBright = "#38BDF8",
        accentContainer = "#0A2A42",
        radiusDp = 20,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.AURORA_VEIL,
        animation = ThemeAnimationIntensity.SUBTLE
    ),
    unslopTheme(
        number = 23,
        id = "galaxy_nebula",
        name = "Galaxy Nebula",
        tagline = "Starfield obsidian & muted magenta-violet cosmic dust",
        category = ThemeCategory.NATURE_ATMOSPHERIC,
        canvas = "#08060F",
        surface = "#110D1E",
        elevated = "#18132B",
        subtle = "#211A3A",
        border = "#302752",
        accent = "#A855F7",
        accentBright = "#C084FC",
        accentContainer = "#2A1545",
        radiusDp = 22,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.DOT_MATRIX,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 24,
        id = "solar_eclipse",
        name = "Solar Eclipse",
        tagline = "Umbra pitch black ringed by a crisp corona gold halo",
        category = ThemeCategory.NATURE_ATMOSPHERIC,
        canvas = "#050506",
        surface = "#0E0E11",
        elevated = "#16161A",
        subtle = "#1E1E24",
        border = "#2D2D36",
        accent = "#FBBF24",
        accentBright = "#FDE68A",
        accentContainer = "#332608",
        radiusDp = 20,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.HUD_RINGS,
        animation = ThemeAnimationIntensity.SUBTLE
    ),
    unslopTheme(
        number = 25,
        id = "midnight_forest",
        name = "Midnight Forest",
        tagline = "Pine shadow slate paired with soothing sage-moss focus",
        category = ThemeCategory.NATURE_ATMOSPHERIC,
        canvas = "#070B09",
        surface = "#0E1511",
        elevated = "#141E19",
        subtle = "#1B2822",
        border = "#273830",
        accent = "#4ADE80",
        accentBright = "#86EFAC",
        accentContainer = "#10331D",
        radiusDp = 18,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.AMBIENT_GLOW,
        animation = ThemeAnimationIntensity.SUBTLE
    ),
    unslopTheme(
        number = 26,
        id = "volcanic_ember",
        name = "Volcanic Ember",
        tagline = "Basalt stone surfaces lit by controlled molten magma glow",
        category = ThemeCategory.NATURE_ATMOSPHERIC,
        canvas = "#0A0706",
        surface = "#140E0C",
        elevated = "#1D1512",
        subtle = "#261C18",
        border = "#382923",
        accent = "#F97316",
        accentBright = "#FB923C",
        accentContainer = "#381908",
        radiusDp = 16,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.AMBIENT_GLOW,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),

    // ── 5. Modern Design Languages (27..34) ─────────────────────────────────
    unslopTheme(
        number = 27,
        id = "glassmorphism_2",
        name = "Glassmorphism 2.0",
        tagline = "Precision translucent layers & daylight-friendly studio glass",
        category = ThemeCategory.MODERN_LANGUAGE,
        canvas = "#F2F4F8",
        surface = "#FFFFFF",
        elevated = "#F7F9FC",
        subtle = "#E9EEF5",
        border = "#D2DAE6",
        accent = "#2563EB",
        accentBright = "#3B82F6",
        accentContainer = "#DBEAFE",
        isDark = false,
        radiusDp = 22,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.LIQUID_SPECULAR,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 28,
        id = "neumorphism_2",
        name = "Neumorphism 2.0",
        tagline = "Tactile soft clay surfaces with crisp modern ink contrast",
        category = ThemeCategory.MODERN_LANGUAGE,
        canvas = "#E7EBF0",
        surface = "#EFF2F6",
        elevated = "#F6F8FB",
        subtle = "#DCE2EA",
        border = "#C9D2DE",
        accent = "#0F766E",
        accentBright = "#14B8A6",
        accentContainer = "#CCFBF1",
        isDark = false,
        radiusDp = 24,
        borderWidthTenths = 12,
        effect = SurfaceEffectStyle.AMBIENT_GLOW,
        animation = ThemeAnimationIntensity.SUBTLE
    ),
    unslopTheme(
        number = 29,
        id = "micro_bento_grid",
        name = "Micro-Bento Grid",
        tagline = "Modular 4dp dashboard tiles, whisper borders & cobalt highlight",
        category = ThemeCategory.MODERN_LANGUAGE,
        canvas = "#090A0D",
        surface = "#111318",
        elevated = "#171A21",
        subtle = "#1E222B",
        border = "#2A2F3B",
        accent = "#3B82F6",
        accentBright = "#60A5FA",
        accentContainer = "#13264A",
        radiusDp = 14,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.DOT_MATRIX,
        animation = ThemeAnimationIntensity.SUBTLE
    ),
    unslopTheme(
        number = 30,
        id = "monochrome_ultra",
        name = "Monochrome Ultra",
        tagline = "Clean grayscale contrast for distraction-free study",
        category = ThemeCategory.MODERN_LANGUAGE,
        canvas = "#080808",
        surface = "#111111",
        elevated = "#181818",
        subtle = "#212121",
        border = "#2E2E2E",
        accent = "#F5F5F5",
        accentBright = "#FFFFFF",
        accentContainer = "#262626",
        radiusDp = 16,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.DOT_MATRIX,
        animation = ThemeAnimationIntensity.SUBTLE
    ),
    unslopTheme(
        number = 31,
        id = "paper_ink_minimal",
        name = "Paper & Ink Minimal",
        tagline = "Warm archival ivory paper with charcoal ink & crimson seal",
        category = ThemeCategory.MODERN_LANGUAGE,
        canvas = "#F7F4EE",
        surface = "#FFFDF9",
        elevated = "#F0ECE3",
        subtle = "#E5E0D5",
        border = "#D5CEC0",
        accent = "#D71921",
        accentBright = "#EF4444",
        accentContainer = "#FDE8E8",
        isDark = false,
        inkPrimary = "#161513",
        inkSecondary = "#4E4B45",
        inkMuted = "#807A70",
        radiusDp = 12,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.DOT_MATRIX,
        animation = ThemeAnimationIntensity.SUBTLE
    ),
    unslopTheme(
        number = 32,
        id = "spatial_ui",
        name = "Spatial UI",
        tagline = "Depth-layered frosted planes calibrated for ambient focus",
        category = ThemeCategory.MODERN_LANGUAGE,
        canvas = "#0B0D12",
        surface = "#141821",
        elevated = "#1B202C",
        subtle = "#232938",
        border = "#323B4F",
        accent = "#93C5FD",
        accentBright = "#BFDBFE",
        accentContainer = "#192945",
        radiusDp = 26,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.LIQUID_SPECULAR,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 33,
        id = "liquid_metal",
        name = "Liquid Metal",
        tagline = "Molten mercury sheen, sharp specular rims & cool platinum ink",
        category = ThemeCategory.MODERN_LANGUAGE,
        canvas = "#07080A",
        surface = "#111317",
        elevated = "#181B21",
        subtle = "#20242C",
        border = "#303642",
        accent = "#D8DEE9",
        accentBright = "#FFFFFF",
        accentContainer = "#252A33",
        radiusDp = 20,
        borderWidthTenths = 12,
        effect = SurfaceEffectStyle.LIQUID_SPECULAR,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 34,
        id = "quantum_interface",
        name = "Quantum Interface",
        tagline = "Wavefunction slate paired with crisp entangled indigo telemetry",
        category = ThemeCategory.MODERN_LANGUAGE,
        canvas = "#070910",
        surface = "#0E121E",
        elevated = "#141A2B",
        subtle = "#1B2338",
        border = "#273352",
        accent = "#6366F1",
        accentBright = "#818CF8",
        accentContainer = "#1C1F4A",
        radiusDp = 18,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.HUD_RINGS,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true
    ),

    // ── 6. Gaming & Interactive Themes (35..40) ─────────────────────────────
    unslopTheme(
        number = 35,
        id = "mech_hud",
        name = "Mech HUD",
        tagline = "Armored chamfered frames & high-alert crimson-orange visor",
        category = ThemeCategory.GAMING_INTERACTIVE,
        canvas = "#08090B",
        surface = "#111317",
        elevated = "#181B20",
        subtle = "#20242B",
        border = "#2E343E",
        accent = "#FF5722",
        accentBright = "#FF8A65",
        accentContainer = "#3B160B",
        radiusDp = 8,
        borderWidthTenths = 14,
        effect = SurfaceEffectStyle.CYBER_GRID,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true
    ),
    unslopTheme(
        number = 36,
        id = "sci_fi_radar",
        name = "Sci-Fi Radar",
        tagline = "Sonar sweep concentric circles & crisp phosphor radar green",
        category = ThemeCategory.GAMING_INTERACTIVE,
        canvas = "#050B08",
        surface = "#0A140F",
        elevated = "#101E17",
        subtle = "#162920",
        border = "#203B2E",
        accent = "#22C55E",
        accentBright = "#4ADE80",
        accentContainer = "#0D331B",
        radiusDp = 16,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.HUD_RINGS,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true
    ),
    unslopTheme(
        number = 37,
        id = "esports_arena",
        name = "Esports Arena",
        tagline = "Tournament broadcast obsidian with sharp electric crimson",
        category = ThemeCategory.GAMING_INTERACTIVE,
        canvas = "#08080B",
        surface = "#111116",
        elevated = "#181820",
        subtle = "#20202B",
        border = "#2D2D3D",
        accent = "#F43F5E",
        accentBright = "#FB7185",
        accentContainer = "#38101C",
        radiusDp = 12,
        borderWidthTenths = 12,
        effect = SurfaceEffectStyle.BRUTALIST_GRID,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        appearance = ThemeColorAppearance.DARK_SIGNATURE_RED
    ),
    unslopTheme(
        number = 38,
        id = "energy_core",
        name = "Energy Core",
        tagline = "Reactor plasma gold-amber centered inside dark containment",
        category = ThemeCategory.GAMING_INTERACTIVE,
        canvas = "#090806",
        surface = "#13100B",
        elevated = "#1C170F",
        subtle = "#251E14",
        border = "#382E1E",
        accent = "#F59E0B",
        accentBright = "#FCD34D",
        accentContainer = "#382506",
        radiusDp = 18,
        borderWidthTenths = 11,
        effect = SurfaceEffectStyle.HUD_RINGS,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 39,
        id = "virtual_reality_grid",
        name = "Virtual Reality Grid",
        tagline = "Spatial wireframe horizon with neon sky telemetry",
        category = ThemeCategory.GAMING_INTERACTIVE,
        canvas = "#06080F",
        surface = "#0D111F",
        elevated = "#13192D",
        subtle = "#1A223D",
        border = "#263259",
        accent = "#38BDF8",
        accentBright = "#7DD3FC",
        accentContainer = "#0E2945",
        radiusDp = 14,
        borderWidthTenths = 11,
        effect = SurfaceEffectStyle.CYBER_GRID,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true
    ),
    unslopTheme(
        number = 40,
        id = "retro_vaporwave",
        name = "Retro Vaporwave",
        tagline = "Dusk synthwave violet tempered with clean readable contrast",
        category = ThemeCategory.GAMING_INTERACTIVE,
        canvas = "#0D0814",
        surface = "#160F22",
        elevated = "#1F1530",
        subtle = "#291C3E",
        border = "#3B2859",
        accent = "#EC4899",
        accentBright = "#F472B6",
        accentContainer = "#3D132B",
        radiusDp = 16,
        borderWidthTenths = 11,
        effect = SurfaceEffectStyle.CYBER_GRID,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),

    // ── 7. Unusual & High-Craft Concepts (41..50) ───────────────────────────
    unslopTheme(
        number = 41,
        id = "zero_gravity_ui",
        name = "Zero-Gravity UI",
        tagline = "Weightless floating cards over calm orbital night",
        category = ThemeCategory.UNUSUAL_CONCEPTS,
        canvas = "#07090D",
        surface = "#10141C",
        elevated = "#171C28",
        subtle = "#1F2636",
        border = "#2D374D",
        accent = "#7DD3FC",
        accentBright = "#BAE6FD",
        accentContainer = "#122B3E",
        radiusDp = 26,
        borderWidthTenths = 9,
        effect = SurfaceEffectStyle.DOT_MATRIX,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 42,
        id = "time_travel_console",
        name = "Time Travel Console",
        tagline = "Chronometer brass numerals & deep obsidian instrument deck",
        category = ThemeCategory.UNUSUAL_CONCEPTS,
        canvas = "#080807",
        surface = "#12110E",
        elevated = "#1A1813",
        subtle = "#232019",
        border = "#342F25",
        accent = "#D97706",
        accentBright = "#FBBF24",
        accentContainer = "#331E06",
        radiusDp = 12,
        borderWidthTenths = 11,
        effect = SurfaceEffectStyle.HUD_RINGS,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true
    ),
    unslopTheme(
        number = 43,
        id = "digital_laboratory",
        name = "Digital Laboratory",
        tagline = "Scientific telemetry grid, sterile precision & cobalt-mint indicators",
        category = ThemeCategory.UNUSUAL_CONCEPTS,
        canvas = "#070A0F",
        surface = "#0E131B",
        elevated = "#141B26",
        subtle = "#1B2433",
        border = "#273347",
        accent = "#14B8A6",
        accentBright = "#2DD4BF",
        accentContainer = "#0B2E2B",
        radiusDp = 14,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.LAB_RETICLE,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true,
        topMcq = true
    ),
    unslopTheme(
        number = 44,
        id = "holographic_terminal",
        name = "Holographic Terminal",
        tagline = "Monospace CLI clarity with razor emerald command prompt focus",
        category = ThemeCategory.UNUSUAL_CONCEPTS,
        canvas = "#050908",
        surface = "#0A1210",
        elevated = "#101B18",
        subtle = "#162521",
        border = "#203630",
        accent = "#10B981",
        accentBright = "#34D399",
        accentContainer = "#082E22",
        radiusDp = 8,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.CYBER_GRID,
        animation = ThemeAnimationIntensity.SUBTLE,
        mono = true
    ),
    unslopTheme(
        number = 45,
        id = "biometric_scanner",
        name = "Biometric Scanner",
        tagline = "Optic iris ring geometry & laser crimson verification pulse",
        category = ThemeCategory.UNUSUAL_CONCEPTS,
        canvas = "#080608",
        surface = "#110D11",
        elevated = "#191319",
        subtle = "#221A22",
        border = "#332733",
        accent = "#E11D48",
        accentBright = "#FB7185",
        accentContainer = "#360D19",
        radiusDp = 20,
        borderWidthTenths = 11,
        effect = SurfaceEffectStyle.HUD_RINGS,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        appearance = ThemeColorAppearance.DARK_SIGNATURE_RED
    ),
    unslopTheme(
        number = 46,
        id = "invisible_ui",
        name = "Invisible UI",
        tagline = "Deep black borderless surfaces that keep answers front and center",
        category = ThemeCategory.UNUSUAL_CONCEPTS,
        canvas = "#000000",
        surface = "#070709",
        elevated = "#0D0D10",
        subtle = "#141418",
        border = "#1B1B21",
        accent = "#A1A1AA",
        accentBright = "#E4E4E7",
        accentContainer = "#1C1C21",
        radiusDp = 18,
        borderWidthTenths = 6,
        effect = SurfaceEffectStyle.AMBIENT_GLOW,
        animation = ThemeAnimationIntensity.SUBTLE
    ),
    unslopTheme(
        number = 47,
        id = "ai_neural_matrix",
        name = "AI Neural Matrix",
        tagline = "Synaptic node graph paired with high-clarity azure reasoning trace",
        category = ThemeCategory.UNUSUAL_CONCEPTS,
        canvas = "#060911",
        surface = "#0C111F",
        elevated = "#12192D",
        subtle = "#19223B",
        border = "#243154",
        accent = "#38BDF8",
        accentBright = "#7DD3FC",
        accentContainer = "#0E2B45",
        radiusDp = 16,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.NEURAL_NODES,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true
    ),
    unslopTheme(
        number = 48,
        id = "robotic_telemetry",
        name = "Robotic Telemetry",
        tagline = "Industrial servo slate, amber status ticks & high-legibility mono",
        category = ThemeCategory.UNUSUAL_CONCEPTS,
        canvas = "#090A0B",
        surface = "#121417",
        elevated = "#191C20",
        subtle = "#21252B",
        border = "#2F353D",
        accent = "#F97316",
        accentBright = "#FB923C",
        accentContainer = "#381B08",
        radiusDp = 10,
        borderWidthTenths = 12,
        effect = SurfaceEffectStyle.LAB_RETICLE,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true
    ),
    unslopTheme(
        number = 49,
        id = "soundwave_interface",
        name = "Soundwave Interface",
        tagline = "Studio mastering console dark & acoustic violet waveform focus",
        category = ThemeCategory.UNUSUAL_CONCEPTS,
        canvas = "#08070D",
        surface = "#100E1B",
        elevated = "#171426",
        subtle = "#1F1B33",
        border = "#2D274A",
        accent = "#8B5CF6",
        accentBright = "#A78BFA",
        accentContainer = "#25194A",
        radiusDp = 20,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.AURORA_VEIL,
        animation = ThemeAnimationIntensity.INTERACTIVE
    ),
    unslopTheme(
        number = 50,
        id = "adaptive_chameleon_ui",
        name = "Adaptive Chameleon UI",
        tagline = "Auto-adapts between crisp daylight alabaster & OLED crimson night",
        category = ThemeCategory.UNUSUAL_CONCEPTS,
        canvas = "#0A0A0D",
        surface = "#131418",
        elevated = "#1A1C22",
        subtle = "#22252D",
        border = "#2E323D",
        accent = "#D71921",
        accentBright = "#FF4650",
        accentContainer = "#2E1014",
        radiusDp = 20,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.AMBIENT_GLOW,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        appearance = ThemeColorAppearance.ADAPTIVE_LIGHT_DARK
    )
)

val ALL_THEME_OPTIONS: List<RemoteThemeSpec> = listOf(NORMAL_DEFAULT_THEME) + ALL_50_UNSLOP_THEMES

val FEATURED_THEME_IDS: List<String> = listOf(
    "jarvis_ai",
    "black_titanium",
    "obsidian_glass",
    "digital_laboratory"
)

val FEATURED_THEMES: List<RemoteThemeSpec> = FEATURED_THEME_IDS.mapNotNull { id ->
    ALL_50_UNSLOP_THEMES.firstOrNull { it.themeId == id }
}

val TOP_5_MCQ_THEMES: List<RemoteThemeSpec> = FEATURED_THEMES

/**
 * Curated Signature Atelier Editions crafted for unmistakable visual polish,
 * architectural depth, and buttery motion choreography without altering the
 * 50-theme + Default catalog invariants.
 */
val SIGNATURE_ATELIER_THEMES: List<RemoteThemeSpec> = listOf(
    unslopTheme(
        number = 101,
        id = "atelier_prism_noir",
        name = "Prism Noir Atelier",
        tagline = "Deep black background with signature red highlights",
        category = ThemeCategory.PREMIUM_LUXURY,
        canvas = "#0A0A0C",
        surface = "#111216",
        elevated = "#181A20",
        subtle = "#20232B",
        border = "#2A2D36",
        accent = "#D71921",
        accentBright = "#FF4650",
        accentContainer = "#2D1014",
        inkPrimary = "#EEEEEE",
        inkSecondary = "#A1A3AD",
        inkMuted = "#767676",
        radiusDp = 22,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.PRISM_HORIZON,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        appearance = ThemeColorAppearance.DARK_SIGNATURE_RED,
        layout = ThemeLayoutArchetype.ATELIER_BENTO
    ),
    unslopTheme(
        number = 102,
        id = "atelier_kyoto_amber",
        name = "Kyoto Smoked Amber",
        tagline = "Warm dark background with soft golden amber accents",
        category = ThemeCategory.PREMIUM_LUXURY,
        canvas = "#0A0807",
        surface = "#14110F",
        elevated = "#1C1814",
        subtle = "#26201B",
        border = "#352C24",
        accent = "#E6A954",
        accentBright = "#F5CA82",
        accentContainer = "#33240F",
        inkPrimary = "#F3EFEA",
        inkSecondary = "#AEA59A",
        inkMuted = "#7A7168",
        radiusDp = 22,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.AURORA_VEIL,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        layout = ThemeLayoutArchetype.EDITORIAL_MONOGRAPH
    ),
    unslopTheme(
        number = 103,
        id = "atelier_swiss_glacier",
        name = "Swiss Glacier OLED",
        tagline = "Deep navy dark theme with clear ice-blue accents",
        category = ThemeCategory.FUTURISTIC_SCIFI,
        canvas = "#05080E",
        surface = "#0C121D",
        elevated = "#131C2B",
        subtle = "#1B273A",
        border = "#263752",
        accent = "#38BDF8",
        accentBright = "#7DD3FC",
        accentContainer = "#0C2A42",
        inkPrimary = "#F0F5FA",
        inkSecondary = "#99ABBF",
        inkMuted = "#64768C",
        radiusDp = 20,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.HUD_RINGS,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true,
        layout = ThemeLayoutArchetype.TELEMETRY_HUD
    ),
    unslopTheme(
        number = 104,
        id = "atelier_matcha_zen",
        name = "Matcha Zen Slate",
        tagline = "Soft dark green surfaces with calm jade highlights",
        category = ThemeCategory.NATURE_ATMOSPHERIC,
        canvas = "#060A08",
        surface = "#0D1410",
        elevated = "#141E18",
        subtle = "#1C2921",
        border = "#273A2F",
        accent = "#34D399",
        accentBright = "#6EE7B7",
        accentContainer = "#0B3326",
        inkPrimary = "#ECF5F0",
        inkSecondary = "#97ADA0",
        inkMuted = "#62786B",
        radiusDp = 24,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.DOT_MATRIX,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        layout = ThemeLayoutArchetype.EDITORIAL_MONOGRAPH
    ),
    unslopTheme(
        number = 105,
        id = "atelier_velvet_indigo",
        name = "Velvet Horizon",
        tagline = "Deep plum background with soft violet highlights",
        category = ThemeCategory.EXPERIMENTAL_CRAZY,
        canvas = "#08070F",
        surface = "#110F1E",
        elevated = "#19162B",
        subtle = "#221F3B",
        border = "#312D52",
        accent = "#A78BFA",
        accentBright = "#C4B5FD",
        accentContainer = "#281D4D",
        inkPrimary = "#F3F1FC",
        inkSecondary = "#A59EC0",
        inkMuted = "#70698B",
        radiusDp = 24,
        borderWidthTenths = 10,
        effect = SurfaceEffectStyle.NEURAL_NODES,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        layout = ThemeLayoutArchetype.KINETIC_DECK
    ),
    unslopTheme(
        number = 106,
        id = "atelier_ceramic_crimson",
        name = "Ceramic Studio",
        tagline = "Matte charcoal cards with crisp red highlights",
        category = ThemeCategory.MODERN_LANGUAGE,
        canvas = "#0C0C0E",
        surface = "#151619",
        elevated = "#1D1F24",
        subtle = "#262930",
        border = "#353942",
        accent = "#E11D2E",
        accentBright = "#FF4D5E",
        accentContainer = "#361016",
        inkPrimary = "#F2F2F4",
        inkSecondary = "#A6A8B0",
        inkMuted = "#767880",
        radiusDp = 14,
        borderWidthTenths = 11,
        effect = SurfaceEffectStyle.LAB_RETICLE,
        animation = ThemeAnimationIntensity.INTERACTIVE,
        mono = true,
        layout = ThemeLayoutArchetype.TELEMETRY_HUD
    )
)

/**
 * Flagship opt-in iOS Liquid Glass Light (White Edition) theme — completely rearranged
 * Apple-style Control Center widgets, collapsing large title, frosted white cards,
 * kinetic spring motion, and matching light Dynamic Island. Users explicitly choose it
 * from Theme Studio while default installations keep the signature dark obsidian look.
 */
val IOS_LIQUID_LIGHT_THEME: RemoteThemeSpec = RemoteThemeSpec(
    themeId = "ios_liquid_light",
    themeNumber = 201,
    name = "Prism Light",
    tagline = "Bright white cards, clean layout, and a light floating pill",
    category = ThemeCategory.MODERN_LANGUAGE,
    isDark = false,
    isPublic = true,
    canvasTopHex = "#F2F2F7",
    canvasBottomHex = "#EAEAF2",
    surfacePrimaryHex = "#FFFFFF",
    surfaceElevatedHex = "#F9F9FC",
    surfaceSubtleHex = "#EBEBF0",
    borderHex = "#D8D8DF",
    accentPrimaryHex = "#007AFF",
    accentBrightHex = "#3898FF",
    accentContainerHex = "#E5F1FF",
    inkPrimaryHex = "#1C1C1E",
    inkSecondaryHex = "#636366",
    inkMutedHex = "#8E8E93",
    successHex = "#24A148",
    warningHex = "#FF9500",
    errorHex = "#D71921",
    cornerRadiusDp = 24,
    borderWidthTenths = 8,
    surfaceEffect = SurfaceEffectStyle.IOS_LIQUID_GLASS,
    animationIntensity = ThemeAnimationIntensity.INTERACTIVE,
    colorAppearance = ThemeColorAppearance.AUTO,
    useMonospaceAccents = false,
    islandAccentHex = "#007AFF",
    version = 2,
    isTopRecommendedForMcq = false,
    layoutArchetype = ThemeLayoutArchetype.IOS_LIQUID_LIGHT
)

val FLAGSHIP_LIGHT_THEMES: List<RemoteThemeSpec> = listOf(IOS_LIQUID_LIGHT_THEME)

fun findThemePresetById(id: String?): RemoteThemeSpec =
    (FLAGSHIP_LIGHT_THEMES + SIGNATURE_ATELIER_THEMES + ALL_THEME_OPTIONS).firstOrNull {
        it.themeId.equals(id?.trim(), ignoreCase = true)
    } ?: NORMAL_DEFAULT_THEME
