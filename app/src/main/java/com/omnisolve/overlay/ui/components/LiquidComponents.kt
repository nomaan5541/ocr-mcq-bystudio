package com.omnisolve.overlay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.R
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.PathNode
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

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
    Stats(R.string.nav_stats, BarChartVector, BarChartVector, "nav_tab_stats"),
    Settings(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_tab_settings")
}

/**
 * Modern floating pill dock with bottom-wave gradient matching the exact HTML specification.
 */
@Composable
fun LiquidMorphBottomDock(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val tabs = AppTab.entries

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Clean Floating Dock without background curly shade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (palette.isDarkObsidian) Color(0xFF121316).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.94f))
                    .border(
                        width = 1.dp,
                        color = if (palette.isDarkObsidian) Color(0xFF23252A) else Color(0x99CBD5E1),
                        shape = RoundedCornerShape(50.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 5.dp)
                    .testTag("bottom_navigation_dock"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                tabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val pillShape = RoundedCornerShape(50.dp)
                    val selectedBg = Color(0xFFD71921)
                    val selectedText = Color.White
                    val unselectedText = if (palette.isDarkObsidian) Color(0xFF767676) else Color(0xFF64748B)

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(pillShape)
                            .background(if (isSelected) selectedBg else Color.Transparent)
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelectTab(tab) },
                                role = Role.Tab
                            )
                            .testTag(tab.testTag),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = stringResource(tab.labelRes),
                            tint = if (isSelected) selectedText else unselectedText,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isSelected) selectedText else unselectedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact Side-Rail navigation optimized for Android Split-Screen / Tablet multi-window mode
 * alongside a PDF reader or browser.
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
            val selectedBg = if (palette.isDarkObsidian) Color(0xFF0C2D48) else Color(0xFFE0F2FE)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) selectedBg else Color.Transparent)
                    .border(
                        width = if (isSelected) 1.2.dp else 0.dp,
                        color = if (isSelected) palette.accentSky else Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    )
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
                    tint = if (isSelected) palette.accentSky else palette.inkSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(tab.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) palette.accentSky else palette.inkPrimary,
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
    val gradientColors = if (isActiveState) {
        listOf(Color(0xFFE11D48), Color(0xFFBE123C))
    } else {
        listOf(Color(0xFF0284C7), Color(0xFF1D4ED8))
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(gradientColors))
            .clickable(
                indication = ripple(color = Color.White),
                interactionSource = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
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
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
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
    val bgColor = when {
        selected && palette.isDarkObsidian -> Color(0xFF0C2D48)
        selected -> Color(0xFFE0F2FE)
        palette.isDarkObsidian -> Color(0xFF151F32)
        else -> Color(0xFFF8FAFC)
    }
    val borderColor = when {
        selected -> palette.accentSky
        palette.isDarkObsidian -> Color(0xFF334155)
        else -> Color(0xFFCBD5E1)
    }
    val textColor = when {
        selected && palette.isDarkObsidian -> Color(0xFFE0F2FE)
        selected -> Color(0xFF0C4A6E)
        else -> palette.inkPrimary
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(
                indication = ripple(color = palette.accentSky),
                interactionSource = null,
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
                    tint = textColor,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

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
    val idleCircleBg = if (palette.isDarkObsidian) Color(0xFF1E293B) else Color(0xFFE2E8F0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (checked) palette.accentSkyContainer else idleCircleBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) palette.accentSky else palette.inkPrimary,
                    modifier = Modifier.size(20.dp)
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
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.accentSky,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = if (palette.isDarkObsidian) Color(0xFF475569) else Color(0xFF94A3B8),
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
    val borderColor = when {
        selected -> palette.accentSky
        palette.isDarkObsidian -> Color(0xFF334155)
        else -> Color(0xFFCBD5E1)
    }
    val bgColor = when {
        selected && palette.isDarkObsidian -> Color(0xFF0E2A47)
        selected -> Color(0xFFEFF6FF)
        palette.isDarkObsidian -> Color(0xFF141D2E)
        else -> Color(0xFFF8FAFC)
    }
    val iconIdleBg = if (palette.isDarkObsidian) Color(0xFF1E293B) else Color(0xFFE2E8F0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(width = if (selected) 1.8.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .selectable(
                selected = selected,
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
                .clip(CircleShape)
                .background(if (selected) palette.accentSky else iconIdleBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else palette.inkPrimary,
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
                    color = palette.inkPrimary
                )
                if (!badgeText.isNullOrBlank()) {
                    val badgeBg = when {
                        selected && palette.isDarkObsidian -> Color(0xFF0369A1)
                        selected -> Color(0xFFBAE6FD)
                        palette.isDarkObsidian -> Color(0xFF1E293B)
                        else -> Color(0xFFE2E8F0)
                    }
                    val badgeFg = when {
                        selected && palette.isDarkObsidian -> Color(0xFFE0F2FE)
                        selected -> Color(0xFF0369A1)
                        palette.isDarkObsidian -> Color(0xFF94A3B8)
                        else -> Color(0xFF475569)
                    }
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
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
                    width = if (selected) 6.dp else 2.dp,
                    color = if (selected) palette.accentSky else Color(0xFF64748B),
                    shape = CircleShape
                )
        )
    }
}
