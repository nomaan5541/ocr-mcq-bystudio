package com.omnisolve.overlay.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Groups
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    Stats(R.string.nav_stats, Icons.Filled.Groups, Icons.Outlined.Groups, "nav_tab_stats"),
    Settings(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_tab_settings")
}

/**
 * Prism Floating Sculpted Glass Navigation Dock:
 * [Home] [Rooms]  (Elevated Center Red Prism Orb -> Floating Helper)  [Browser] [Settings]
 * Respects edge-to-edge navigation bar safe areas and 48dp minimum touch targets.
 */
@Composable
fun LiquidMorphBottomDock(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    onOpenBrowser: () -> Unit = {},
    isOverlayRunning: Boolean = false,
    onToggleOverlay: () -> Unit = {},
    isPlusMenuOpen: Boolean = false,
    onTogglePlusMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val reducedMotion = LocalReducedMotion.current
    val springCfg = LocalSpringPhysics.current
    val archetype = themeSpec.layoutArchetype

    val isTelemetryHud = archetype == ThemeLayoutArchetype.TELEMETRY_HUD
    val isCrimsonStudio = archetype == ThemeLayoutArchetype.CRIMSON_STUDIO
    val isLight = !palette.isDarkObsidian ||
        archetype == ThemeLayoutArchetype.IOS_LIQUID_LIGHT ||
        archetype == ThemeLayoutArchetype.GLASS_PRISM
    val centerActionClick = onTogglePlusMenu ?: onToggleOverlay

    // 5-slot geometry: [0: Home] [1: Rooms] [2: Center Prism Orb -> Quick Actions] [3: Browser] [4: Settings]
    val selectedVisualSlot = when (selectedTab) {
        AppTab.Home -> 0
        AppTab.Stats -> 1
        AppTab.Settings -> 4
    }

    val dockCorner = when (archetype) {
        ThemeLayoutArchetype.ATELIER_BENTO -> 20.dp
        ThemeLayoutArchetype.TELEMETRY_HUD -> 14.dp
        else -> 24.dp
    }
    val pillCorner = if (isLight) 999.dp else 20.dp

    val lightBlueAccent = Color(0xFF1B6EF3)
    val lightBlueSoft = Color(0xFF4F9CFF)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .height(70.dp)
                .testTag("bottom_navigation_dock"),
            contentAlignment = Alignment.BottomCenter
        ) {
            val totalWidth = maxWidth
            val density = LocalDensity.current
            val pillShape = RoundedCornerShape(pillCorner)
            val barHeight = 56.dp
            val crestRise = 14.dp

            // Compact sculpted floating glass dock with smooth concave notch cradling the center Prism orb
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val topY = crestRise.toPx()
                val cr = dockCorner.toPx().coerceAtMost((h - topY) * 0.49f)
                val cx = w * 0.5f
                val cradleHalf = 31.dp.toPx()
                val dipDepth = 12.dp.toPx()

                val dockPath = Path().apply {
                    moveTo(cr, topY)
                    lineTo(cx - cradleHalf - 13.dp.toPx(), topY)
                    cubicTo(
                        cx - cradleHalf + 2.dp.toPx(), topY,
                        cx - cradleHalf * 0.66f, topY + dipDepth,
                        cx, topY + dipDepth
                    )
                    cubicTo(
                        cx + cradleHalf * 0.66f, topY + dipDepth,
                        cx + cradleHalf - 2.dp.toPx(), topY,
                        cx + cradleHalf + 13.dp.toPx(), topY
                    )
                    lineTo(w - cr, topY)
                    quadraticTo(w, topY, w, topY + cr)
                    lineTo(w, h - cr)
                    quadraticTo(w, h, w - cr, h)
                    lineTo(cr, h)
                    quadraticTo(0f, h, 0f, h - cr)
                    lineTo(0f, topY + cr)
                    quadraticTo(0f, topY, cr, topY)
                    close()
                }

                // Grounded soft shadow
                drawPath(
                    path = dockPath,
                    color = if (isLight) Color(0xFF1A438A).copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.55f)
                )
                // Main theme-pure glass fill (pure white-blue liquid glass vs deep obsidian crimson)
                drawPath(
                    path = dockPath,
                    brush = Brush.verticalGradient(
                        colors = if (isLight) {
                            listOf(
                                Color(0xFFFAFDFE).copy(alpha = 0.94f),
                                Color(0xFFE7F0FF).copy(alpha = 0.92f)
                            )
                        } else {
                            listOf(
                                Color(0xFF151720).copy(alpha = 0.96f),
                                Color(0xFF0A0B10).copy(alpha = 0.98f)
                            )
                        },
                        startY = topY,
                        endY = h
                    )
                )
                // Specular glass rim tracing the silhouette
                drawPath(
                    path = dockPath,
                    brush = Brush.verticalGradient(
                        colors = if (isLight) {
                            listOf(
                                Color.White.copy(alpha = 0.95f),
                                lightBlueAccent.copy(alpha = 0.24f)
                            )
                        } else {
                            listOf(
                                Color.White.copy(alpha = if (isCrimsonStudio) 0.22f else 0.17f),
                                PrismTokens.PrimaryRed.copy(alpha = 0.28f)
                            )
                        },
                        startY = topY,
                        endY = h
                    ),
                    style = Stroke(width = 1.05.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Sliding Active Indicator inside the compact 56dp bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(barHeight)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                val indicatorW = (totalWidth - 8.dp) / 5f
                val indicatorPx = with(density) { indicatorW.toPx() }
                val activeOffset by animateFloatAsState(
                    targetValue = indicatorPx * selectedVisualSlot,
                    animationSpec = MotionTokens.dockSpringFor(archetype, reducedMotion, springCfg),
                    label = "dock_active_pill_x"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer { translationX = activeOffset }
                        .width(indicatorW)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(pillShape)
                            .background(
                                Brush.radialGradient(
                                    colors = if (isLight) {
                                        listOf(
                                            lightBlueSoft.copy(alpha = 0.26f),
                                            lightBlueAccent.copy(alpha = 0.08f)
                                        )
                                    } else {
                                        listOf(
                                            PrismTokens.PrimaryRed.copy(alpha = 0.50f),
                                            PrismTokens.DeepRed.copy(alpha = 0.20f),
                                            Color.Transparent
                                        )
                                    }
                                )
                            )
                            .border(
                                width = 0.85.dp,
                                color = if (isLight) lightBlueAccent.copy(alpha = 0.34f) else PrismTokens.AccentRed.copy(alpha = 0.38f),
                                shape = pillShape
                            )
                    )
                    // Crisp theme-matched active bottom underline bar
                    Box(
                        modifier = Modifier
                            .padding(bottom = 1.dp)
                            .width(20.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(if (isLight) lightBlueAccent else PrismTokens.AccentRed)
                    )
                }
            }

            // 5-Slot Interactive Row: [Home] [Rooms] [Center Prism Orb -> Quick Actions] [Browser] [Settings]
            val selectedFgColor = if (isLight) lightBlueAccent else PrismTokens.TextPrimary
            val inactiveFgColor = if (isLight) Color(0xFF475569) else PrismTokens.TextSecondary
            val labelFont = if (isTelemetryHud) FontFamily.Monospace else FontFamily.Default

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Slot 0: Home
                DockNavSlot(
                    tab = AppTab.Home,
                    isSelected = selectedTab == AppTab.Home,
                    selectedFgColor = selectedFgColor,
                    inactiveFgColor = inactiveFgColor,
                    labelFont = labelFont,
                    isTelemetryHud = isTelemetryHud,
                    pillShape = pillShape,
                    onClick = { onSelectTab(AppTab.Home) },
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                )

                // Slot 1: Rooms
                DockNavSlot(
                    tab = AppTab.Stats,
                    isSelected = selectedTab == AppTab.Stats,
                    selectedFgColor = selectedFgColor,
                    inactiveFgColor = inactiveFgColor,
                    labelFont = labelFont,
                    isTelemetryHud = isTelemetryHud,
                    pillShape = pillShape,
                    onClick = { onSelectTab(AppTab.Stats) },
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                )

                // Slot 2: Elevated Center Prism Quick-Action Orb (Replaces Floating + FAB)
                val orbInteraction = remember { MutableInteractionSource() }
                val orbScale by animateFloatAsState(
                    targetValue = if (isPlusMenuOpen && !reducedMotion) 1.07f else 1f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 460f),
                    label = "center_prism_orb_scale"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(bottom = 10.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .minimumInteractiveComponentSize()
                            .scale(orbScale)
                            .liquidPressScale(orbInteraction, pressedScale = 0.90f)
                            .shadow(
                                elevation = if (isLight) 10.dp else 14.dp,
                                shape = CircleShape,
                                ambientColor = if (isLight) lightBlueAccent.copy(alpha = 0.42f) else PrismTokens.PrimaryRed.copy(alpha = 0.55f),
                                spotColor = if (isLight) lightBlueAccent.copy(alpha = 0.55f) else PrismTokens.PrimaryRed.copy(alpha = 0.80f)
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = if (isLight) {
                                        if (isPlusMenuOpen) {
                                            listOf(lightBlueSoft, lightBlueAccent)
                                        } else {
                                            listOf(Color.White, Color(0xFFE6F0FF), Color(0xFFD2E5FF))
                                        }
                                    } else {
                                        if (isPlusMenuOpen) {
                                            listOf(Color(0xFF282B36), Color(0xFF14161D))
                                        } else {
                                            listOf(Color(0xFF240B10), Color(0xFF0E0A0E), Color(0xFF07080B))
                                        }
                                    }
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.verticalGradient(
                                    colors = if (isLight) {
                                        listOf(Color.White, lightBlueAccent.copy(alpha = 0.65f))
                                    } else {
                                        listOf(
                                            if (isPlusMenuOpen) Color.White.copy(alpha = 0.55f) else PrismTokens.AccentRed,
                                            PrismTokens.PrimaryRed.copy(alpha = 0.55f)
                                        )
                                    }
                                ),
                                shape = CircleShape
                            )
                            .clickable(
                                interactionSource = orbInteraction,
                                indication = ripple(bounded = false, color = if (isLight) lightBlueAccent else PrismTokens.AccentRed),
                                role = Role.Button,
                                onClick = centerActionClick
                            )
                            .testTag("nav_center_prism_action")
                    ) {
                        if (isPlusMenuOpen) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close quick actions",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            RedPrismLogoMark(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape),
                                animated = !reducedMotion
                            )
                        }
                    }
                }

                // Slot 3: Browser
                val browserInteraction = remember { MutableInteractionSource() }
                val browserLabel = stringResource(R.string.nav_browser)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                        .minimumInteractiveComponentSize()
                        .liquidPressScale(browserInteraction, pressedScale = 0.94f)
                        .clip(pillShape)
                        .clickable(
                            interactionSource = browserInteraction,
                            indication = ripple(color = if (isLight) lightBlueAccent else palette.accentSky),
                            role = Role.Button,
                            onClick = onOpenBrowser
                        )
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .testTag("nav_tab_browser"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = browserLabel,
                        tint = inactiveFgColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isTelemetryHud) browserLabel.uppercase() else browserLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = labelFont),
                        fontWeight = FontWeight.Medium,
                        fontSize = if (isTelemetryHud) 9.sp else 10.sp,
                        color = inactiveFgColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Slot 4: Settings
                DockNavSlot(
                    tab = AppTab.Settings,
                    isSelected = selectedTab == AppTab.Settings,
                    selectedFgColor = selectedFgColor,
                    inactiveFgColor = inactiveFgColor,
                    labelFont = labelFont,
                    isTelemetryHud = isTelemetryHud,
                    pillShape = pillShape,
                    onClick = { onSelectTab(AppTab.Settings) },
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                )
            }
        }
    }
}

@Composable
private fun DockNavSlot(
    tab: AppTab,
    isSelected: Boolean,
    selectedFgColor: Color,
    inactiveFgColor: Color,
    labelFont: FontFamily,
    isTelemetryHud: Boolean,
    pillShape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current
    val springCfg = LocalSpringPhysics.current
    val interactionSource = remember(tab) { MutableInteractionSource() }
    val tabTextColor by animateColorAsState(
        targetValue = if (isSelected) selectedFgColor else inactiveFgColor,
        animationSpec = spring(stiffness = 600f),
        label = "navText_${tab.name}"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected && !reducedMotion) 1.10f else 1.0f,
        animationSpec = MotionTokens.tactilePress(reducedMotion, springCfg),
        label = "navIconScale_${tab.name}"
    )
    val rawLabel = stringResource(tab.labelRes)
    val displayLabel = if (isTelemetryHud) rawLabel.uppercase() else rawLabel

    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .liquidPressScale(interactionSource, pressedScale = 0.94f)
            .clip(pillShape)
            .selectable(
                selected = isSelected,
                interactionSource = interactionSource,
                indication = ripple(color = palette.accentSky),
                onClick = onClick,
                role = Role.Tab
            )
            .padding(vertical = 4.dp, horizontal = 2.dp)
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
            fontSize = if (isTelemetryHud) 9.sp else 10.sp,
            color = tabTextColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Compact Side-Rail navigation optimized for Android Split-Screen / Tablet multi-window mode.
 */
@Composable
fun CompactSplitScreenSideRail(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    onOpenBrowser: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current

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
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Top)
    ) {
        listOf(AppTab.Home, AppTab.Stats).forEach { tab ->
            SideRailItem(
                label = stringResource(tab.labelRes),
                icon = if (tab == selectedTab) tab.selectedIcon else tab.unselectedIcon,
                selected = tab == selectedTab,
                testTag = tab.testTag,
                onClick = { onSelectTab(tab) }
            )
        }
        SideRailItem(
            label = stringResource(R.string.nav_browser),
            icon = Icons.Filled.Language,
            selected = false,
            highlighted = true,
            testTag = "nav_tab_browser_rail",
            onClick = onOpenBrowser
        )
        SideRailItem(
            label = stringResource(AppTab.Settings.labelRes),
            icon = if (selectedTab == AppTab.Settings) AppTab.Settings.selectedIcon else AppTab.Settings.unselectedIcon,
            selected = selectedTab == AppTab.Settings,
            testTag = AppTab.Settings.testTag,
            onClick = { onSelectTab(AppTab.Settings) }
        )
    }
}

@Composable
private fun SideRailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    highlighted: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val bg = when {
        selected -> palette.accentSky
        highlighted -> palette.glassTintedSky
        else -> Color.Transparent
    }
    val fg = when {
        selected -> palette.onAccentInk
        highlighted -> palette.accentSkyBright
        else -> palette.inkPrimary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab
            )
            .padding(vertical = 9.dp, horizontal = 3.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected || highlighted) fg else palette.inkMuted,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = fg,
            maxLines = 1,
            softWrap = false
        )
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
