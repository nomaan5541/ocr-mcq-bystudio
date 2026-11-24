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
    PREMIUM_LUXURY("PREMIUM_LUXURY", "Studio"),
    EXPERIMENTAL_CRAZY("EXPERIMENTAL_CRAZY", "Experimental"),
    NATURE_ATMOSPHERIC("NATURE_ATMOSPHERIC", "Atmospheric"),
    MODERN_LANGUAGE("MODERN_LANGUAGE", "Modern"),
    GAMING_INTERACTIVE("GAMING_INTERACTIVE", "HUD"),
    UNUSUAL_CONCEPTS("UNUSUAL_CONCEPTS", "Concepts");

    companion object {
        fun fromId(id: String?): ThemeCategory =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class SurfaceEffectStyle(val id: String, val label: String) {
    AMBIENT_GLOW("AMBIENT_GLOW", "Ambient Prism"),
    HUD_RINGS("HUD_RINGS", "Optical Rings"),
    CYBER_GRID("CYBER_GRID", "Precision Grid"),
    NEURAL_NODES("NEURAL_NODES", "Neural Graph"),
    AURORA_VEIL("AURORA_VEIL", "Crimson Veil"),
    DOT_MATRIX("DOT_MATRIX", "Dot Matrix"),
    BRUTALIST_GRID("BRUTALIST_GRID", "Editorial Lines"),
    CARBON_WEAVE("CARBON_WEAVE", "Graphite Weave"),
    LIQUID_SPECULAR("LIQUID_SPECULAR", "Liquid Glass"),
    LAB_RETICLE("LAB_RETICLE", "Scan Reticle"),
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
    EXTREME_3D("EXTREME_3D", "Lively", 1.45f);

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
 * Architectural layout blueprints for the 4 curated Prism Solver themes.
 */
enum class ThemeLayoutArchetype(
    val id: String,
    val label: String,
    val shortBadge: String,
    val layoutTagline: String
) {
    CLASSIC_PRISM(
        id = "CLASSIC_PRISM",
        label = "Prism Dark",
        shortBadge = "Default",
        layoutTagline = "Obsidian dark studio with live scan preview & quick modes"
    ),
    CRIMSON_STUDIO(
        id = "CRIMSON_STUDIO",
        label = "Crimson Studio",
        shortBadge = "Studio",
        layoutTagline = "Cinematic graphite & crimson glass with 3-step visual workflow"
    ),
    GLASS_PRISM(
        id = "GLASS_PRISM",
        label = "Glass Prism",
        shortBadge = "Liquid Glass",
        layoutTagline = "4-tier frosted dark glass layers with specular rim highlights"
    ),
    IOS_LIQUID_LIGHT(
        id = "IOS_LIQUID_LIGHT",
        label = "Prism Light",
        shortBadge = "Light",
        layoutTagline = "Bright daylight glass cards with a crisp light floating pill"
    ),
    // Legacy compatibility enum values retained so saved prefs/tests never crash
    ATELIER_BENTO(
        id = "ATELIER_BENTO",
        label = "Bento Grid",
        shortBadge = "Bento",
        layoutTagline = "Compact grid layout"
    ),
    TELEMETRY_HUD(
        id = "TELEMETRY_HUD",
        label = "Status Deck",
        shortBadge = "HUD",
        layoutTagline = "Structured status strip"
    ),
    EDITORIAL_MONOGRAPH(
        id = "EDITORIAL_MONOGRAPH",
        label = "Editorial Studio",
        shortBadge = "Editorial",
        layoutTagline = "Clean focused layout"
    ),
    KINETIC_DECK(
        id = "KINETIC_DECK",
        label = "Quick Deck",
        shortBadge = "Kinetic",
        layoutTagline = "Split quick-action tiles"
    );

    companion object {
        fun fromId(id: String?): ThemeLayoutArchetype =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: CLASSIC_PRISM
    }
}

@Immutable
data class RemoteThemeSpec(
    val themeId: String = DEFAULT_THEME_ID,
    val themeNumber: Int = 1,
    val name: String = "Prism Default",
    val tagline: String = "Obsidian graphite surfaces with controlled crimson lighting",
    val category: ThemeCategory = ThemeCategory.DEFAULT,
    val isDark: Boolean = true,
    val isPublic: Boolean = true,
    val canvasTopHex: String = "#07080B",
    val canvasBottomHex: String = "#0D0F14",
    val surfacePrimaryHex: String = "#14161D",
    val surfaceElevatedHex: String = "#1B1E27",
    val surfaceSubtleHex: String = "#222632",
    val borderHex: String = "#292D38",
    val accentPrimaryHex: String = "#E3182D",
    val accentBrightHex: String = "#FF334A",
    val accentContainerHex: String = "#2B1016",
    val inkPrimaryHex: String = "#F5F5F7",
    val inkSecondaryHex: String = "#A1A5B1",
    val inkMutedHex: String = "#727784",
    val successHex: String = "#32D583",
    val warningHex: String = "#F5B942",
    val errorHex: String = "#FF4D5E",
    val cornerRadiusDp: Int = 20,
    val borderWidthTenths: Int = 10,
    val surfaceEffect: SurfaceEffectStyle = SurfaceEffectStyle.AMBIENT_GLOW,
    val animationIntensity: ThemeAnimationIntensity = ThemeAnimationIntensity.INTERACTIVE,
    val colorAppearance: ThemeColorAppearance = ThemeColorAppearance.DARK_SIGNATURE_RED,
    val useMonospaceAccents: Boolean = false,
    val islandAccentHex: String = "#E3182D",
    val version: Int = 4,
    val isTopRecommendedForMcq: Boolean = true,
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
            ThemeColorAppearance.DARK_SIGNATURE_RED -> {
                if (!isDark) {
                    // Never bleed crimson onto Prism Light; preserve clean daylight frosted white & blue
                    this
                } else {
                    copy(
                        accentPrimaryHex = "#E3182D",
                        accentBrightHex = "#FF334A",
                        accentContainerHex = "#2B1016",
                        islandAccentHex = "#E3182D",
                        errorHex = "#FF4D5E",
                        isDark = true
                    )
                }
            }
            ThemeColorAppearance.DARK_ONLY -> {
                if (!isDark) {
                    copy(
                        isDark = true,
                        canvasTopHex = "#07080B",
                        canvasBottomHex = "#0D0F14",
                        surfacePrimaryHex = "#14161D",
                        surfaceElevatedHex = "#1B1E27",
                        surfaceSubtleHex = "#222632",
                        borderHex = "#292D38",
                        inkPrimaryHex = "#F5F5F7",
                        inkSecondaryHex = "#A1A5B1",
                        inkMutedHex = "#727784",
                        errorHex = "#FF4D5E"
                    )
                } else {
                    this
                }
            }
            ThemeColorAppearance.ADAPTIVE_LIGHT_DARK -> {
                if (!systemIsDark && isDark) {
                    copy(
                        isDark = false,
                        canvasTopHex = "#F5F6FA",
                        canvasBottomHex = "#ECEEF5",
                        surfacePrimaryHex = "#FFFFFF",
                        surfaceElevatedHex = "#F7F8FC",
                        surfaceSubtleHex = "#E9ECF4",
                        borderHex = "#D4D9E6",
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
        val canvasTop = parseHexColorOr(canvasTopHex, Color(0xFF07080B))
        val canvasBottom = parseHexColorOr(canvasBottomHex, Color(0xFF0D0F14))
        val surfacePrimary = parseHexColorOr(surfacePrimaryHex, Color(0xFF14161D))
        val surfaceElevated = parseHexColorOr(surfaceElevatedHex, Color(0xFF1B1E27))
        val surfaceSubtle = parseHexColorOr(surfaceSubtleHex, Color(0xFF222632))
        val border = parseHexColorOr(borderHex, Color(0xFF292D38))
        val accent = parseHexColorOr(accentPrimaryHex, Color(0xFFE3182D))
        val accentBright = parseHexColorOr(accentBrightHex, Color(0xFFFF334A))
        val accentContainer = parseHexColorOr(accentContainerHex, Color(0xFF2B1016))
        val inkPrimary = parseHexColorOr(inkPrimaryHex, Color(0xFFF5F5F7))
        val inkSecondary = parseHexColorOr(inkSecondaryHex, Color(0xFFA1A5B1))
        val inkMuted = parseHexColorOr(inkMutedHex, Color(0xFF727784))
        val success = parseHexColorOr(successHex, Color(0xFF32D583))
        val warning = parseHexColorOr(warningHex, Color(0xFFF5B942))
        val resolvedErrorHex = if (isDark) errorHex else if (errorHex.equals("#F87171", ignoreCase = true)) "#B91C1C" else errorHex
        val danger = parseHexColorOr(resolvedErrorHex, if (isDark) Color(0xFFFF4D5E) else Color(0xFFB91C1C))

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
                    themeNumber = obj.optInt("themeNumber", 1),
                    name = obj.optString("name", "Prism Default"),
                    tagline = obj.optString("tagline", ""),
                    category = ThemeCategory.fromId(obj.optString("category")),
                    isDark = obj.optBoolean("isDark", true),
                    isPublic = obj.optBoolean("isPublic", true),
                    canvasTopHex = normalizeHex(obj.optString("canvasTopHex"), "#07080B"),
                    canvasBottomHex = normalizeHex(obj.optString("canvasBottomHex"), "#0D0F14"),
                    surfacePrimaryHex = normalizeHex(obj.optString("surfacePrimaryHex"), "#14161D"),
                    surfaceElevatedHex = normalizeHex(obj.optString("surfaceElevatedHex"), "#1B1E27"),
                    surfaceSubtleHex = normalizeHex(obj.optString("surfaceSubtleHex"), "#222632"),
                    borderHex = normalizeHex(obj.optString("borderHex"), "#292D38"),
                    accentPrimaryHex = normalizeHex(obj.optString("accentPrimaryHex"), "#E3182D"),
                    accentBrightHex = normalizeHex(obj.optString("accentBrightHex"), "#FF334A"),
                    accentContainerHex = normalizeHex(obj.optString("accentContainerHex"), "#2B1016"),
                    inkPrimaryHex = normalizeHex(obj.optString("inkPrimaryHex"), "#F5F5F7"),
                    inkSecondaryHex = normalizeHex(obj.optString("inkSecondaryHex"), "#A1A5B1"),
                    inkMutedHex = normalizeHex(obj.optString("inkMutedHex"), "#727784"),
                    successHex = normalizeHex(obj.optString("successHex"), "#32D583"),
                    warningHex = normalizeHex(obj.optString("warningHex"), "#F5B942"),
                    errorHex = normalizeHex(
                        obj.optString("errorHex"),
                        if (obj.optBoolean("isDark", true)) "#FF4D5E" else "#B91C1C"
                    ),
                    cornerRadiusDp = obj.optInt("cornerRadiusDp", 20).coerceIn(0, 36),
                    borderWidthTenths = obj.optInt("borderWidthTenths", 10).coerceIn(5, 35),
                    surfaceEffect = SurfaceEffectStyle.fromId(obj.optString("surfaceEffect")),
                    animationIntensity = ThemeAnimationIntensity.fromId(obj.optString("animationIntensity")),
                    colorAppearance = ThemeColorAppearance.fromId(obj.optString("colorAppearance")),
                    useMonospaceAccents = obj.optBoolean("useMonospaceAccents", false),
                    islandAccentHex = normalizeHex(obj.optString("islandAccentHex"), "#E3182D"),
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

/**
 * 1. DEFAULT THEME: Prism Default (Obsidian Dark + Crimson Accent)
 * Studio-crafted dark-only design with live scan phone preview, quick mode chips,
 * crimson gradient helper CTA, Google workspace panel, and dual feature tiles.
 */
val NORMAL_DEFAULT_THEME: RemoteThemeSpec = RemoteThemeSpec(
    themeId = RemoteThemeSpec.DEFAULT_THEME_ID,
    themeNumber = 1,
    name = "Prism Default",
    tagline = "Obsidian graphite dark UI with controlled crimson lighting",
    category = ThemeCategory.DEFAULT,
    isDark = true,
    isPublic = true,
    canvasTopHex = "#07080B",
    canvasBottomHex = "#0D0F14",
    surfacePrimaryHex = "#14161D",
    surfaceElevatedHex = "#1B1E27",
    surfaceSubtleHex = "#222632",
    borderHex = "#292D38",
    accentPrimaryHex = "#E3182D",
    accentBrightHex = "#FF334A",
    accentContainerHex = "#2B1016",
    inkPrimaryHex = "#F5F5F7",
    inkSecondaryHex = "#A1A5B1",
    inkMutedHex = "#727784",
    successHex = "#32D583",
    warningHex = "#F5B942",
    errorHex = "#FF4D5E",
    cornerRadiusDp = 22,
    borderWidthTenths = 10,
    surfaceEffect = SurfaceEffectStyle.AMBIENT_GLOW,
    animationIntensity = ThemeAnimationIntensity.INTERACTIVE,
    colorAppearance = ThemeColorAppearance.DARK_SIGNATURE_RED,
    useMonospaceAccents = false,
    islandAccentHex = "#E3182D",
    version = 4,
    isTopRecommendedForMcq = true,
    layoutArchetype = ThemeLayoutArchetype.CLASSIC_PRISM
)

/**
 * 2. LIGHT THEME: Prism Light
 * Pure white & royal-blue frosted daylight glass with animated curly silk ribbons and matching blue-white floating pill.
 */
val IOS_LIQUID_LIGHT_THEME: RemoteThemeSpec = RemoteThemeSpec(
    themeId = "ios_liquid_light",
    themeNumber = 2,
    name = "Prism Light",
    tagline = "Pure white & royal-blue liquid glass with swirling silk waves",
    category = ThemeCategory.MODERN_LANGUAGE,
    isDark = false,
    isPublic = true,
    canvasTopHex = "#E5EFFD",
    canvasBottomHex = "#CEE1FA",
    surfacePrimaryHex = "#FFFFFF",
    surfaceElevatedHex = "#F7FAFF",
    surfaceSubtleHex = "#E4EEFC",
    borderHex = "#9AB9EA",
    accentPrimaryHex = "#1B6EF3",
    accentBrightHex = "#4B9BFF",
    accentContainerHex = "#E4F0FF",
    inkPrimaryHex = "#0E172A",
    inkSecondaryHex = "#475A77",
    inkMutedHex = "#7A8CA8",
    successHex = "#18A866",
    warningHex = "#E88B14",
    errorHex = "#C81E2E",
    cornerRadiusDp = 24,
    borderWidthTenths = 12,
    surfaceEffect = SurfaceEffectStyle.IOS_LIQUID_GLASS,
    animationIntensity = ThemeAnimationIntensity.INTERACTIVE,
    colorAppearance = ThemeColorAppearance.AUTO,
    useMonospaceAccents = false,
    islandAccentHex = "#1B6EF3",
    version = 4,
    isTopRecommendedForMcq = true,
    layoutArchetype = ThemeLayoutArchetype.IOS_LIQUID_LIGHT
)

/**
 * 3. LIQUID GLASS THEME: Glass Prism
 * 100% ice-white & sapphire-blue optical liquid glass with animated curly glass ribbons and zero red mixing.
 */
val GLASS_PRISM_THEME: RemoteThemeSpec = RemoteThemeSpec(
    themeId = "glass_prism",
    themeNumber = 3,
    name = "Glass Prism",
    tagline = "Pure white & sapphire-blue liquid glass layers with animated waves",
    category = ThemeCategory.MODERN_LANGUAGE,
    isDark = false,
    isPublic = true,
    canvasTopHex = "#ECF4FF",
    canvasBottomHex = "#D2E5FF",
    surfacePrimaryHex = "#F9FCFF",
    surfaceElevatedHex = "#FFFFFF",
    surfaceSubtleHex = "#E1EEFF",
    borderHex = "#BCD6FA",
    accentPrimaryHex = "#0A66FF",
    accentBrightHex = "#4EA2FF",
    accentContainerHex = "#DDEEFF",
    inkPrimaryHex = "#0B162C",
    inkSecondaryHex = "#465C7D",
    inkMutedHex = "#778CA8",
    successHex = "#18A866",
    warningHex = "#E88B14",
    errorHex = "#C81E2E",
    cornerRadiusDp = 26,
    borderWidthTenths = 9,
    surfaceEffect = SurfaceEffectStyle.LIQUID_SPECULAR,
    animationIntensity = ThemeAnimationIntensity.INTERACTIVE,
    colorAppearance = ThemeColorAppearance.AUTO,
    useMonospaceAccents = false,
    islandAccentHex = "#0A66FF",
    version = 4,
    isTopRecommendedForMcq = true,
    layoutArchetype = ThemeLayoutArchetype.GLASS_PRISM
)

/**
 * 4. ADDED NEW FLAGSHIP THEME: Crimson Studio
 * Cinematic obsidian-crimson glass edition featuring the 3-step visual workflow
 * ("Just Point • Scan • Understand"), interactive phone explanation preview,
 * and rich obsidian-indigo dual feature cards.
 */
val CRIMSON_STUDIO_THEME: RemoteThemeSpec = RemoteThemeSpec(
    themeId = "crimson_studio",
    themeNumber = 4,
    name = "Crimson Studio",
    tagline = "Cinematic obsidian glass, 3-step scan showcase & studio crimson glow",
    category = ThemeCategory.PREMIUM_LUXURY,
    isDark = true,
    isPublic = true,
    canvasTopHex = "#06070A",
    canvasBottomHex = "#0B0D13",
    surfacePrimaryHex = "#13151D",
    surfaceElevatedHex = "#1A1D28",
    surfaceSubtleHex = "#232736",
    borderHex = "#2D3140",
    accentPrimaryHex = "#E3182D",
    accentBrightHex = "#FF334A",
    accentContainerHex = "#300E16",
    inkPrimaryHex = "#F5F5F7",
    inkSecondaryHex = "#A4A8B5",
    inkMutedHex = "#737887",
    successHex = "#32D583",
    warningHex = "#F5B942",
    errorHex = "#FF4D5E",
    cornerRadiusDp = 24,
    borderWidthTenths = 10,
    surfaceEffect = SurfaceEffectStyle.AURORA_VEIL,
    animationIntensity = ThemeAnimationIntensity.INTERACTIVE,
    colorAppearance = ThemeColorAppearance.DARK_ONLY,
    useMonospaceAccents = false,
    islandAccentHex = "#E3182D",
    version = 1,
    isTopRecommendedForMcq = true,
    layoutArchetype = ThemeLayoutArchetype.CRIMSON_STUDIO
)

/**
 * Strictly 2 curated core themes (zero clutter, zero theme bleed):
 * 1. Prism Default (Obsidian Dark + Refined Crimson)
 * 2. Prism Light (Daylight Frosted White + Royal Blue)
 */
val CURATED_PRISM_THEMES: List<RemoteThemeSpec> = listOf(
    NORMAL_DEFAULT_THEME,
    IOS_LIQUID_LIGHT_THEME
)

// Backwards-compatible aliases pointing cleanly to the curated 2-theme set
val ALL_50_UNSLOP_THEMES: List<RemoteThemeSpec> = CURATED_PRISM_THEMES
val ALL_THEME_OPTIONS: List<RemoteThemeSpec> = CURATED_PRISM_THEMES
val FEATURED_THEME_IDS: List<String> = CURATED_PRISM_THEMES.map { it.themeId }
val FEATURED_THEMES: List<RemoteThemeSpec> = CURATED_PRISM_THEMES
val TOP_5_MCQ_THEMES: List<RemoteThemeSpec> = CURATED_PRISM_THEMES
val SIGNATURE_ATELIER_THEMES: List<RemoteThemeSpec> = CURATED_PRISM_THEMES
val FLAGSHIP_LIGHT_THEMES: List<RemoteThemeSpec> = listOf(IOS_LIQUID_LIGHT_THEME)

fun findThemePresetById(id: String?): RemoteThemeSpec {
    val normalized = id?.trim().orEmpty()
    if (normalized.equals(IOS_LIQUID_LIGHT_THEME.themeId, ignoreCase = true) ||
        normalized.equals(GLASS_PRISM_THEME.themeId, ignoreCase = true)
    ) {
        return IOS_LIQUID_LIGHT_THEME
    }
    return CURATED_PRISM_THEMES.firstOrNull {
        it.themeId.equals(normalized, ignoreCase = true)
    } ?: NORMAL_DEFAULT_THEME
}
