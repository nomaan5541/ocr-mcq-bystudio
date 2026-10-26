package com.omnisolve.overlay.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnisolve.overlay.R
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.MotionTokens
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import kotlin.math.abs

enum class AppTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    Home(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    Practice(R.string.nav_practice, Icons.Filled.Quiz, Icons.Outlined.Quiz, "nav_tab_practice"),
    Controls(R.string.nav_controls, Icons.Filled.Tune, Icons.Outlined.Tune, "nav_tab_controls"),
    Account(R.string.nav_account, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_tab_account")
}

/**
 * Floating White Liquid Glass bottom navigation dock with a continuous gliding,
 * velocity-stretching liquid glass active pill indicator.
 */
@Composable
fun LiquidMorphBottomDock(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current
    val tabs = AppTab.entries

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .livingGlassSurface(
                    strength = GlassMaterialStrength.FloatingDock,
                    cornerRadius = 34.dp,
                    interactive = false
                )
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .testTag("bottom_navigation_dock")
        ) {
            val slotWidth = maxWidth / tabs.size
            val targetOffset = slotWidth * selectedTab.ordinal

            val animatedOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = MotionTokens.liquidMorph(reducedMotion),
                label = "pillOffset"
            )

            // Liquid droplet stretch proportional to in-flight distance
            val travelDeltaDp = abs(targetOffset.value - animatedOffset.value)
            val stretchScaleX = if (reducedMotion) 1f else (1f + (travelDeltaDp / 260f).coerceAtMost(0.22f))
            val stretchScaleY = if (reducedMotion) 1f else (1f - (travelDeltaDp / 520f).coerceAtMost(0.10f))

            // Gliding Liquid Glass Indicator Pill
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(slotWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = stretchScaleX
                        scaleY = stretchScaleY
                    }
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color(0x330284C7),
                        spotColor = Color(0x290284C7)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                palette.accentSkyContainer
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White,
                                Color(0x550284C7)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected && !reducedMotion) 1.12f else 1f,
                        animationSpec = MotionTokens.tactilePress(reducedMotion),
                        label = "tabIconScale"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) palette.accentSky else palette.inkMuted,
                        animationSpec = MotionTokens.gentleSettle(reducedMotion),
                        label = "tabColor"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(28.dp))
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelectTab(tab) },
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            )
                            .testTag(tab.testTag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = stringResource(tab.labelRes),
                            tint = contentColor,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) palette.accentSky else palette.inkSecondary,
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
 * Physics-driven primary action button with optical sky/mint liquid gradient,
 * tactile press compression, and specular glass rim.
 */
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
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.96f else 1f,
        animationSpec = MotionTokens.tactilePress(reducedMotion),
        label = "primaryBtnScale"
    )

    val gradientColors = if (isActiveState) {
        listOf(Color(0xFFE11D48), Color(0xFFBE123C))
    } else {
        listOf(palette.accentSkyBright, palette.accentSky)
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isPressed) 4.dp else 10.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = if (isActiveState) Color(0x40E11D48) else Color(0x400284C7),
                spotColor = if (isActiveState) Color(0x40E11D48) else Color(0x400284C7)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(gradientColors))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x80FFFFFF), Color(0x1AFFFFFF))
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = Color.White),
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
                color = Color.White
            )
        }
    }
}

/**
 * Secondary frosted white glass action button with crisp slate/sky label.
 */
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
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.95f else 1f,
        animationSpec = MotionTokens.tactilePress(reducedMotion),
        label = "secBtnScale"
    )

    val bgBrush = if (selected) {
        Brush.verticalGradient(listOf(palette.accentSkyContainer, Color(0xFFBAE6FD)))
    } else {
        Brush.verticalGradient(listOf(Color(0xF5FFFFFF), Color(0xE6F1F5F9)))
    }

    val borderColor = if (selected) palette.accentSky else Color(0xFF94A3B8)
    val textColor = if (selected) Color(0xFF0369A1) else palette.inkPrimary

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(bgBrush)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = palette.accentSky),
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

/**
 * Tactile toggle row with clear plain-English title, helper subtitle, and Material 3 Switch.
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (checked) palette.accentSkyContainer else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) palette.accentSky else palette.inkSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
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

/**
 * Selectable option card for radio choices with smooth active glass tint.
 */
@Composable
fun LivingOptionChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current
    val borderColor by animateColorAsState(
        targetValue = if (selected) palette.accentSky else Color(0xFFE2E8F0),
        animationSpec = MotionTokens.gentleSettle(reducedMotion),
        label = "choiceBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFEFF8FF) else Color(0xB8FFFFFF),
        animationSpec = MotionTokens.gentleSettle(reducedMotion),
        label = "choiceBg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(width = if (selected) 1.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
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
                .background(if (selected) palette.accentSky else Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else palette.inkSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.inkPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
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
                    color = if (selected) palette.accentSky else Color(0xFF475569),
                    shape = CircleShape
                )
        )
    }
}

/**
 * Animated numeric or text metric tile for the Home dashboard.
 */
@Composable
fun AnimatedMetricTile(
    label: String,
    numericValue: Int? = null,
    textValue: String? = null,
    suffix: String = "",
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current

    val displayString = if (numericValue != null) {
        val animatedInt by animateIntAsState(
            targetValue = numericValue,
            animationSpec = MotionTokens.counterSettle(reducedMotion),
            label = "metricCounter"
        )
        "$animatedInt$suffix"
    } else {
        textValue ?: ""
    }

    Column(
        modifier = modifier
            .livingGlassSurface(
                strength = GlassMaterialStrength.Card,
                cornerRadius = 18.dp,
                interactive = true
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = displayString,
            style = MaterialTheme.typography.titleLarge,
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = palette.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
