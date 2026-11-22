package com.omnisolve.overlay.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.R
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.LocalSpringPhysics
import com.omnisolve.overlay.ui.physics.MotionTokens
import com.omnisolve.overlay.ui.physics.liquidPressScale
import com.omnisolve.overlay.ui.physics.liquidSheenSweep
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.PrismCurves
import com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype
import com.omnisolve.overlay.ui.theme.contrastOnColor

val BarChartVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "BarChart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        fill = SolidColor(Color.Black),
        pathData = listOf(
            PathNode.MoveTo(4f, 10f),
            PathNode.HorizontalTo(8f),
            PathNode.VerticalTo(20f),
            PathNode.HorizontalTo(4f),
            PathNode.Close,
            PathNode.MoveTo(10f, 4f),
            PathNode.HorizontalTo(14f),
            PathNode.VerticalTo(20f),
            PathNode.HorizontalTo(10f),
            PathNode.Close,
            PathNode.MoveTo(16f, 13f),
            PathNode.HorizontalTo(20f),
            PathNode.VerticalTo(20f),
            PathNode.HorizontalTo(16f),
            PathNode.Close
        )
    ).build()
}

enum class AppTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    Home(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    Stats(R.string.nav_stats, Icons.Filled.Add, Icons.Filled.Add, "nav_tab_stats"),
    Settings(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_tab_settings")
}

/**
 * Prism Crimson Floating Navigation Pill Dock:
 * Left = Home, Center = Animated `+` Tree Launcher (rotates 45deg to X when open), Right = Settings.
 */
@Composable
fun LiquidMorphBottomDock(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    isPlusMenuOpen: Boolean = false,
    onTogglePlusMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val reducedMotion = LocalReducedMotion.current
    val springCfg = LocalSpringPhysics.current
    val archetype = themeSpec.layoutArchetype
    val tabs = AppTab.entries
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    val isFlushAtelier = archetype == ThemeLayoutArchetype.ATELIER_BENTO
    val isTelemetryHud = archetype == ThemeLayoutArchetype.TELEMETRY_HUD
    val isEditorial = archetype == ThemeLayoutArchetype.EDITORIAL_MONOGRAPH
    val isKineticDeck = archetype == ThemeLayoutArchetype.KINETIC_DECK

    val dockCorner = when (archetype) {
        ThemeLayoutArchetype.ATELIER_BENTO -> 0.dp
        ThemeLayoutArchetype.TELEMETRY_HUD -> 10.dp
        ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> 999.dp
        ThemeLayoutArchetype.KINETIC_DECK -> 18.dp
        ThemeLayoutArchetype.IOS_LIQUID_LIGHT -> 999.dp
        ThemeLayoutArchetype.CLASSIC_PRISM -> PrismCurves.dockCorner(themeSpec)
    }
    val pillCorner = when (archetype) {
        ThemeLayoutArchetype.ATELIER_BENTO -> 10.dp
        ThemeLayoutArchetype.TELEMETRY_HUD -> 7.dp
        ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> 999.dp
        ThemeLayoutArchetype.KINETIC_DECK -> 13.dp
        ThemeLayoutArchetype.IOS_LIQUID_LIGHT -> 999.dp
        ThemeLayoutArchetype.CLASSIC_PRISM -> PrismCurves.dockPillCorner(themeSpec)
    }
    val outerHorizontalPadding = when (archetype) {
        ThemeLayoutArchetype.ATELIER_BENTO -> 0.dp
        ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> 22.dp
        else -> 16.dp
    }
    val outerVerticalPadding = if (isFlushAtelier) 0.dp else 10.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isFlushAtelier) {
                    Modifier.background(palette.glassStrong)
                } else {
                    Modifier
                }
            )
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = if (isFlushAtelier) 900.dp else 600.dp)
                .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .then(
                        if (isFlushAtelier) {
                            Modifier
                                .background(palette.glassStrong)
                                .border(
                                    width = 1.dp,
                                    color = palette.cardHairlineBorder,
                                    shape = RoundedCornerShape(0.dp)
                                )
                        } else {
                            Modifier
                                .shadow(
                                    elevation = if (palette.isDarkObsidian) 10.dp else 5.dp,
                                    shape = RoundedCornerShape(dockCorner),
                                    ambientColor = Color.Black.copy(alpha = if (palette.isDarkObsidian) 0.9f else 0.16f),
                                    spotColor = palette.accentSky.copy(alpha = if (palette.isDarkObsidian) 0.28f else 0.14f)
                                )
                                .clip(RoundedCornerShape(dockCorner))
                                .background(if (isEditorial) palette.glassStrong.copy(alpha = 0.95f) else palette.glassStrong)
                                .border(
                                    width = if (isTelemetryHud) 1.2.dp else (themeSpec.borderWidthTenths / 10f).dp,
                                    color = if (isTelemetryHud) palette.accentSky.copy(alpha = 0.45f) else palette.cardHairlineBorder,
                                    shape = RoundedCornerShape(dockCorner)
                                )
                        }
                    )
                    .padding(horizontal = if (isFlushAtelier) 10.dp else 6.dp, vertical = 6.dp)
                    .testTag("bottom_navigation_dock")
            ) {
                val tabWidth = maxWidth / tabs.size
                val isCompactTab = tabWidth < 104.dp || isEditorial || isFlushAtelier
                val density = LocalDensity.current
                val tabWidthPx = with(density) { tabWidth.toPx() }
                val indicatorOffsetPxState = animateFloatAsState(
                    targetValue = tabWidthPx * selectedIndex,
                    animationSpec = MotionTokens.dockSpringFor(archetype, reducedMotion, springCfg),
                    label = "dock_liquid_pill_offset_px"
                )
                val pillShape = RoundedCornerShape(pillCorner)

                Box(modifier = Modifier.fillMaxWidth()) {
                    // Architectural sliding active indicator running 100% on GPU graphicsLayer
                    Box(modifier = Modifier.matchParentSize()) {
                        when (archetype) {
                            ThemeLayoutArchetype.ATELIER_BENTO -> {
                                // Razor-thin sliding architectural top accent rail
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { translationX = indicatorOffsetPxState.value }
                                        .width(tabWidth)
                                        .fillMaxHeight()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .width((tabWidth * 0.48f).coerceIn(24.dp, 68.dp))
                                            .height(2.5.dp)
                                            .clip(CircleShape)
                                            .background(palette.accentSky)
                                    )
                                }
                            }

                            ThemeLayoutArchetype.TELEMETRY_HUD -> {
                                // Beveled HUD highlight block with top avionics notch
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { translationX = indicatorOffsetPxState.value }
                                        .width(tabWidth)
                                        .fillMaxHeight()
                                        .padding(horizontal = 2.dp)
                                        .clip(pillShape)
                                        .background(palette.glassTintedSky)
                                        .border(1.dp, palette.accentSky, pillShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 3.dp)
                                            .width(18.dp)
                                            .height(2.dp)
                                            .background(palette.accentSkyBright)
                                    )
                                }
                            }

                            ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> {
                                // Whisper-quiet editorial bottom dot indicator
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { translationX = indicatorOffsetPxState.value }
                                        .width(tabWidth)
                                        .fillMaxHeight()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 2.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(palette.accentSky)
                                    )
                                }
                            }

                            ThemeLayoutArchetype.KINETIC_DECK -> {
                                // Elevated recessed titanium hardware keycap indicator
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { translationX = indicatorOffsetPxState.value }
                                        .width(tabWidth)
                                        .fillMaxHeight()
                                        .padding(horizontal = 2.dp)
                                        .clip(pillShape)
                                        .background(palette.canvasTop)
                                        .border(1.3.dp, palette.accentSky, pillShape)
                                )
                            }

                            ThemeLayoutArchetype.IOS_LIQUID_LIGHT -> {
                                // Apple iOS 26 Liquid Glass frosted sliding capsule indicator
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { translationX = indicatorOffsetPxState.value }
                                        .width(tabWidth)
                                        .fillMaxHeight()
                                        .padding(horizontal = 3.dp, vertical = 2.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    palette.accentSky.copy(alpha = 0.16f),
                                                    palette.accentSky.copy(alpha = 0.09f)
                                                )
                                            )
                                        )
                                        .border(
                                            width = 0.9.dp,
                                            color = palette.accentSky.copy(alpha = 0.32f),
                                            shape = CircleShape
                                        )
                                        .liquidSheenSweep(
                                            enabled = true,
                                            tint = Color.White,
                                            maxAlpha = 0.35f,
                                            durationMs = 2600
                                        )
                                )
                            }

                            ThemeLayoutArchetype.CLASSIC_PRISM -> {
                                // Original Gliding Crimson Liquid Glass Pill
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { translationX = indicatorOffsetPxState.value }
                                        .width(tabWidth)
                                        .fillMaxHeight()
                                        .padding(horizontal = 2.dp)
                                        .clip(pillShape)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    palette.accentSkyBright,
                                                    palette.accentSky
                                                )
                                            )
                                        )
                                        .liquidSheenSweep(
                                            enabled = true,
                                            tint = Color.White,
                                            maxAlpha = 0.20f,
                                            durationMs = 2800
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 4.dp)
                                            .width(24.dp)
                                            .height(2.5.dp)
                                            .clip(CircleShape)
                                            .background(palette.onAccentInk.copy(alpha = 0.65f))
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isCenterPlus = tab == AppTab.Stats
                            val isSelected = if (isCenterPlus) isPlusMenuOpen else (tab == selectedTab)
                            val interactionSource = remember(tab) { MutableInteractionSource() }

                            if (isCenterPlus) {
                                val plusRotation by animateFloatAsState(
                                    targetValue = if (isPlusMenuOpen && !reducedMotion) 45f else if (isPlusMenuOpen) 45f else 0f,
                                    animationSpec = MotionTokens.tactilePress(reducedMotion, springCfg),
                                    label = "dock_plus_rotation"
                                )
                                val plusBg by animateColorAsState(
                                    targetValue = if (isPlusMenuOpen) palette.inkPrimary else palette.accentSky,
                                    animationSpec = spring(stiffness = 620f),
                                    label = "dock_plus_bg"
                                )
                                val plusTint by animateColorAsState(
                                    targetValue = if (isPlusMenuOpen) Color(0xFF0A0A0C) else palette.onAccentInk,
                                    animationSpec = spring(stiffness = 620f),
                                    label = "dock_plus_tint"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .minimumInteractiveComponentSize()
                                        .liquidPressScale(interactionSource, pressedScale = 0.90f)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            role = Role.Button,
                                            onClick = {
                                                if (onTogglePlusMenu != null) {
                                                    onTogglePlusMenu()
                                                } else {
                                                    onSelectTab(tab)
                                                }
                                            }
                                        )
                                        .testTag(tab.testTag),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(plusBg)
                                            .border(
                                                width = 1.dp,
                                                color = if (isPlusMenuOpen) palette.cardHairlineBorder else palette.accentSkyBright.copy(alpha = 0.55f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = if (isPlusMenuOpen) "Close quick options" else "Open quick solve options",
                                            tint = plusTint,
                                            modifier = Modifier
                                                .size(23.dp)
                                                .graphicsLayer { rotationZ = plusRotation }
                                        )
                                    }
                                }
                            } else {
                                val selectedFgColor = when (archetype) {
                                    ThemeLayoutArchetype.CLASSIC_PRISM -> palette.onAccentInk
                                    ThemeLayoutArchetype.IOS_LIQUID_LIGHT -> palette.accentSky
                                    ThemeLayoutArchetype.ATELIER_BENTO,
                                    ThemeLayoutArchetype.EDITORIAL_MONOGRAPH,
                                    ThemeLayoutArchetype.TELEMETRY_HUD,
                                    ThemeLayoutArchetype.KINETIC_DECK -> palette.accentSkyBright
                                }
                                val tabTextColor by animateColorAsState(
                                    targetValue = if (isSelected) selectedFgColor else palette.inkSecondary,
                                    animationSpec = spring(stiffness = 600f),
                                    label = "navText_${tab.name}"
                                )
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected && !reducedMotion) 1.12f else 1.0f,
                                    animationSpec = MotionTokens.tactilePress(reducedMotion, springCfg),
                                    label = "navIconScale_${tab.name}"
                                )
                                val rawLabel = stringResource(tab.labelRes)
                                val displayLabel = if (isTelemetryHud) rawLabel.uppercase() else rawLabel
                                val labelFont = when (archetype) {
                                    ThemeLayoutArchetype.TELEMETRY_HUD -> FontFamily.Monospace
                                    ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> FontFamily.Serif
                                    else -> FontFamily.Default
                                }

                                if (isCompactTab) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .minimumInteractiveComponentSize()
                                            .liquidPressScale(interactionSource, pressedScale = 0.94f)
                                            .clip(pillShape)
                                            .selectable(
                                                selected = isSelected,
                                                interactionSource = interactionSource,
                                                indication = ripple(color = palette.accentSky),
                                                onClick = { onSelectTab(tab) },
                                                role = Role.Tab
                                            )
                                            .padding(vertical = 6.dp, horizontal = 4.dp)
                                            .testTag(tab.testTag),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = rawLabel,
                                            tint = tabTextColor,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .scale(iconScale)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = displayLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = labelFont),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = if (isTelemetryHud) 10.sp else 11.sp,
                                            color = tabTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .minimumInteractiveComponentSize()
                                            .liquidPressScale(interactionSource, pressedScale = 0.94f)
                                            .clip(pillShape)
                                            .selectable(
                                                selected = isSelected,
                                                interactionSource = interactionSource,
                                                indication = ripple(color = palette.accentSky),
                                                onClick = { onSelectTab(tab) },
                                                role = Role.Tab
                                            )
                                            .padding(horizontal = 6.dp, vertical = 10.dp)
                                            .testTag(tab.testTag),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = rawLabel,
                                            tint = tabTextColor,
                                            modifier = Modifier
                                                .size(19.dp)
                                                .scale(iconScale)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = displayLabel,
                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = labelFont),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = if (isTelemetryHud) 12.sp else 13.5.sp,
                                            color = tabTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact Side-Rail navigation optimized for Android Split-Screen / Tablet multi-window mode.
 */
@Composable
fun CompactSplitScreenSideRail(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val tabs = AppTab.entries

    Column(
        modifier = modifier
            .width(74.dp)
            .fillMaxHeight()
            .padding(start = 8.dp, top = 8.dp, bottom = 16.dp)
            .livingGlassSurface(
                strength = GlassMaterialStrength.FloatingDock,
                cornerRadius = 24.dp
            )
            .padding(vertical = 12.dp, horizontal = 6.dp)
            .testTag("side_navigation_rail"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top)
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selectedTab
            val selectedBg = palette.accentSky
            val selectedFg = palette.onAccentInk
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) selectedBg else Color.Transparent)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelectTab(tab) },
                        role = Role.Tab
                    )
                    .padding(vertical = 10.dp, horizontal = 4.dp)
                    .testTag(tab.testTag),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                    tint = if (isSelected) selectedFg else palette.inkMuted,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(tab.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) selectedFg else palette.inkPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun LivingPrimaryButton(
    text: String,
    icon: ImageVector? = null,
    isActiveState: Boolean = false,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val bgColor = if (isActiveState) palette.accentSkyBright else palette.accentSky
    val fgColor = contrastOnColor(bgColor)
    val btnRadius = PrismCurves.pillOrButtonCorner(themeSpec)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .liquidPressScale(interactionSource, pressedScale = 0.955f)
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(btnRadius),
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = bgColor.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(btnRadius))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        bgColor,
                        palette.accentSkyBright.copy(alpha = 0.94f)
                    )
                )
            )
            .liquidSheenSweep(enabled = true, tint = fgColor, maxAlpha = 0.22f, durationMs = 2600)
            .clickable(
                indication = ripple(color = fgColor),
                interactionSource = interactionSource,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = fgColor
            )
        }
    }
}

@Composable
fun LivingSecondaryButton(
    text: String,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val bgColor = if (selected) palette.glassTintedSky else palette.glassMedium
    val borderColor = if (selected) palette.accentSky else palette.cardHairlineBorder
    val textColor = palette.inkPrimary
    val btnRadius = PrismCurves.rowCorner(themeSpec)
    val borderW = (themeSpec.borderWidthTenths / 10f).dp
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .liquidPressScale(interactionSource, pressedScale = 0.96f)
            .clip(RoundedCornerShape(btnRadius))
            .background(bgColor)
            .border(borderW, borderColor, RoundedCornerShape(btnRadius))
            .clickable(
                indication = ripple(color = palette.accentSky),
                interactionSource = interactionSource,
                role = Role.Button,
                onClick = onClick
            )
            .padding(contentPadding)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) palette.accentSky else textColor,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Clean unslop settings switch row powered by active [LocalWhiteGlassPalette].
 */
@Composable
fun LivingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val rowRadius = PrismCurves.rowCorner(themeSpec)
    val iconRadius = PrismCurves.iconWellCorner(themeSpec)
    val borderW = (themeSpec.borderWidthTenths / 10f).dp
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .liquidPressScale(interactionSource, pressedScale = 0.985f)
            .clip(RoundedCornerShape(rowRadius))
            .background(palette.glassStrong)
            .border(
                width = borderW,
                color = if (checked) palette.accentSky.copy(alpha = 0.45f) else palette.cardHairlineBorder,
                shape = RoundedCornerShape(rowRadius)
            )
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = ripple(color = palette.accentSky),
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 12.dp, horizontal = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(iconRadius))
                    .background(if (checked) palette.glassTintedSky else palette.glassMedium)
                    .border(
                        width = 1.dp,
                        color = if (checked) palette.accentSky.copy(alpha = 0.5f) else palette.cardHairlineBorder,
                        shape = RoundedCornerShape(iconRadius)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) palette.accentSky else palette.inkSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.inkPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onAccentInk,
                checkedTrackColor = palette.accentSky,
                uncheckedThumbColor = palette.inkSecondary,
                uncheckedTrackColor = palette.glassSubtle,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun LivingOptionChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    badgeText: String? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardRadius = PrismCurves.rowCorner(themeSpec)
    val iconRadius = PrismCurves.iconWellCorner(themeSpec)
    val borderColor = if (selected) palette.accentSky else palette.cardHairlineBorder
    val bgColor = if (selected) palette.glassTintedSky else palette.glassStrong
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .liquidPressScale(interactionSource, pressedScale = 0.98f)
            .clip(RoundedCornerShape(cardRadius))
            .background(bgColor)
            .border(
                width = if (selected) 1.5.dp else (themeSpec.borderWidthTenths / 10f).dp,
                color = borderColor,
                shape = RoundedCornerShape(cardRadius)
            )
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(color = palette.accentSky),
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(iconRadius))
                .background(if (selected) palette.glassTintedSky else palette.glassMedium)
                .border(
                    1.dp,
                    if (selected) palette.accentSky.copy(alpha = 0.55f) else palette.cardHairlineBorder,
                    RoundedCornerShape(iconRadius)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) palette.accentSky else palette.inkPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!badgeText.isNullOrBlank()) {
                    val badgeBg = if (selected) palette.accentSky else palette.glassSubtle
                    val badgeFg = if (selected) palette.onAccentInk else palette.inkSecondary
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 6.dp else 1.8.dp,
                    color = if (selected) palette.accentSky else palette.inkMuted,
                    shape = CircleShape
                )
        )
    }
}
