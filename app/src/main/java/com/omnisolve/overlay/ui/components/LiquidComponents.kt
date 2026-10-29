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
import com.omnisolve.overlay.R
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

enum class AppTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    Home(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    Settings(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_tab_settings")
}

/**
 * Clean, instant 2-tab bottom navigation bar (Home & Settings) with zero transition lag.
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
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .livingGlassSurface(
                    strength = GlassMaterialStrength.FloatingDock,
                    cornerRadius = 32.dp
                )
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .testTag("bottom_navigation_dock"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                val pillShape = RoundedCornerShape(26.dp)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .minimumInteractiveComponentSize()
                        .clip(pillShape)
                        .background(
                            if (isSelected) Color(0xFFE0F2FE) else Color.Transparent
                        )
                        .border(
                            width = if (isSelected) 1.2.dp else 0.dp,
                            color = if (isSelected) Color(0xFF38BDF8) else Color.Transparent,
                            shape = pillShape
                        )
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
                        tint = if (isSelected) palette.accentSky else palette.inkSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFF0C4A6E) else palette.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
    val bgColor = if (selected) Color(0xFFE0F2FE) else Color(0xFFF8FAFC)
    val borderColor = if (selected) palette.accentSky else Color(0xFFCBD5E1)
    val textColor = if (selected) Color(0xFF0C4A6E) else palette.inkPrimary

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) }
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
                    .background(if (checked) palette.accentSkyContainer else Color(0xFFE2E8F0)),
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
                uncheckedTrackColor = Color(0xFF94A3B8),
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
    val borderColor = if (selected) palette.accentSky else Color(0xFFCBD5E1)
    val bgColor = if (selected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)

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
                .background(if (selected) palette.accentSky else Color(0xFFE2E8F0)),
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
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color(0xFF0369A1) else Color(0xFF475569),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Color(0xFFBAE6FD) else Color(0xFFE2E8F0))
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
