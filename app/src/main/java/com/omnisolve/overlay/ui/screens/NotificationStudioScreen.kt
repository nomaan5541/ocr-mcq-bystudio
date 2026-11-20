package com.omnisolve.overlay.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.service.CustomPinnedNotifConfig
import com.omnisolve.overlay.service.PrismEngagementNotificationManager
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.components.LivingPrimaryButton
import com.omnisolve.overlay.ui.components.LivingSecondaryButton
import com.omnisolve.overlay.ui.components.LivingSwitchRow
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

private data class NotifPresetMeta(
    val id: String,
    val label: String
)

private val NOTIF_PRESETS = listOf(
    NotifPresetMeta(CustomPinnedNotifConfig.TEMPLATE_FORMS_KEY, "Forms Key"),
    NotifPresetMeta(CustomPinnedNotifConfig.TEMPLATE_STUDY_NOTE, "Study Note"),
    NotifPresetMeta(CustomPinnedNotifConfig.TEMPLATE_QUICK_HUD, "Quick Remote"),
    NotifPresetMeta(CustomPinnedNotifConfig.TEMPLATE_BATTERY_STEALTH, "Battery Stealth")
)

@Composable
fun NotificationStudioScreen(
    state: MainUiState,
    onUpdateConfig: ((CustomPinnedNotifConfig) -> CustomPinnedNotifConfig) -> Unit,
    onApplyPreset: (String) -> Unit,
    onImportLatestSolved: () -> Unit,
    onPinNotification: () -> Boolean,
    onUnpinNotification: () -> Unit,
    onToggleWeeklyEngagement: (Boolean) -> Unit,
    onSendTestEngagementNotification: () -> Boolean,
    onSendWelcomeNotification: () -> Boolean,
    onPermissionUpdated: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = themeSpec.cornerRadiusDp.coerceIn(8, 28).dp
    val context = LocalContext.current
    val cfg = state.customPinConfig

    var pendingActionAfterPerm by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onPermissionUpdated()
        if (granted) {
            pendingActionAfterPerm?.invoke()
        }
        pendingActionAfterPerm = null
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        if (PrismEngagementNotificationManager.hasNotificationPermission(context)) {
            action()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingActionAfterPerm = action
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("notification_studio_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. LIVE ANDROID NOTIFICATION SHADE PREVIEW ──────────────────────
        item(key = "notif_live_preview") {
            LiveNotificationShadePreviewCard(
                config = cfg,
                isGranted = state.hasNotificationPermission,
                onRequestPermission = {
                    runWithNotificationPermission { onPermissionUpdated() }
                }
            )
        }

        // ── 2. PRESETS & CUSTOM MESSAGE EDITOR ──────────────────────────────
        item(key = "notif_editor_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Customize Pinned Bar",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary
                            )
                        )
                        Text(
                            text = "Design your sticky status-bar card with live action buttons",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.glassTintedSky)
                            .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button, onClick = onImportLatestSolved)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("btn_import_latest_to_notif"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Import latest solved answers",
                                tint = palette.accentSky,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Use Latest",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Quick Preset Selector Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NOTIF_PRESETS.forEach { preset ->
                        val active = cfg.templateId == preset.id
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) palette.accentSky else palette.glassMedium)
                                .border(
                                    width = 1.dp,
                                    color = if (active) palette.accentSky else palette.cardHairlineBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable(role = Role.Tab) { onApplyPreset(preset.id) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("chip_notif_preset_${preset.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (active) palette.onAccentInk else palette.inkSecondary,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = cfg.title,
                    onValueChange = { txt -> onUpdateConfig { it.copy(title = txt.take(56)) } },
                    label = { Text("Notification Title", color = palette.inkSecondary) },
                    placeholder = { Text("Prism Solver • Study Pin", color = palette.inkMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.glassMedium,
                        unfocusedContainerColor = palette.canvasBottom,
                        focusedBorderColor = palette.accentSky,
                        unfocusedBorderColor = palette.cardHairlineBorder,
                        focusedTextColor = palette.inkPrimary,
                        unfocusedTextColor = palette.inkPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_notif_title")
                )

                OutlinedTextField(
                    value = cfg.body,
                    onValueChange = { txt -> onUpdateConfig { it.copy(body = txt.take(280)) } },
                    label = { Text("Pinned Text / Answer Key", color = palette.inkSecondary) },
                    placeholder = { Text("(1-A|2-B|3-D|4-A)", color = palette.inkMuted) },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.glassMedium,
                        unfocusedContainerColor = palette.canvasBottom,
                        focusedBorderColor = palette.accentSky,
                        unfocusedBorderColor = palette.cardHairlineBorder,
                        focusedTextColor = palette.inkPrimary,
                        unfocusedTextColor = palette.inkPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_notif_body")
                )

                HorizontalDivider(color = palette.cardHairlineBorder)

                Text(
                    text = "Action Buttons & Disguise",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = palette.inkPrimary
                    )
                )

                LivingSwitchRow(
                    title = "Show 'Copy' Action Button",
                    subtitle = "1-tap copies pinned text straight to clipboard from the shade.",
                    checked = cfg.showCopy,
                    onCheckedChange = { enabled -> onUpdateConfig { it.copy(showCopy = enabled) } },
                    icon = Icons.Filled.ContentCopy,
                    testTag = "switch_notif_copy"
                )

                LivingSwitchRow(
                    title = "Show 'Solve Now' Action Button",
                    subtitle = "Triggers an immediate screen capture from the notification shade.",
                    checked = cfg.showSolveNow,
                    onCheckedChange = { enabled -> onUpdateConfig { it.copy(showSolveNow = enabled) } },
                    icon = Icons.Filled.Bolt,
                    testTag = "switch_notif_solve"
                )

                LivingSwitchRow(
                    title = "Show 'Stealth' Toggle Button",
                    subtitle = "Switch Dynamic Island visibility or disguise right from the notification.",
                    checked = cfg.showStealth,
                    onCheckedChange = { enabled -> onUpdateConfig { it.copy(showStealth = enabled) } },
                    icon = Icons.Filled.VisibilityOff,
                    testTag = "switch_notif_stealth"
                )

                LivingSwitchRow(
                    title = "Stealth Lockscreen Disguise",
                    subtitle = "Masks notification title as 'System Power • Balanced (92%)' on lockscreen.",
                    checked = cfg.stealthDisguise,
                    onCheckedChange = { enabled -> onUpdateConfig { it.copy(stealthDisguise = enabled) } },
                    icon = Icons.Filled.Shield,
                    testTag = "switch_notif_disguise"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LivingPrimaryButton(
                        text = if (cfg.isPinned) "Update Pinned Bar" else "Pin to Notification Bar",
                        icon = Icons.Filled.PushPin,
                        onClick = {
                            runWithNotificationPermission { onPinNotification() }
                        },
                        testTag = "btn_pin_custom_notif",
                        modifier = Modifier.weight(1f)
                    )
                    if (cfg.isPinned) {
                        LivingSecondaryButton(
                            text = "Unpin",
                            onClick = onUnpinNotification,
                            testTag = "btn_unpin_custom_notif"
                        )
                    }
                }
            }
        }

        // ── 3. SMART REMINDERS & WELCOME CHECK-IN VERIFICATION ──────────────
        item(key = "notif_reminders_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.glassTintedSky)
                            .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = palette.accentSky,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Smart Reminders & Welcome Alert",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary
                            )
                        )
                        Text(
                            text = "Weekly check-ins when away & instant 1-notification delivery test",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                LivingSwitchRow(
                    title = "Smart Weekly Check-Ins (2+/Week When Away)",
                    subtitle = "Sends randomized friendly reminders at varied times if you haven't opened the app.",
                    checked = state.autoWeeklyEngagementEnabled,
                    onCheckedChange = onToggleWeeklyEngagement,
                    icon = Icons.Filled.AutoAwesome,
                    testTag = "switch_weekly_reengagement"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LivingSecondaryButton(
                        text = "Send 1 Test Alert",
                        icon = Icons.Filled.NotificationsActive,
                        onClick = {
                            runWithNotificationPermission { onSendTestEngagementNotification() }
                        },
                        testTag = "btn_test_engagement_notif",
                        modifier = Modifier.weight(1f)
                    )
                    LivingSecondaryButton(
                        text = "Welcome Alert",
                        icon = Icons.Filled.AutoAwesome,
                        onClick = {
                            runWithNotificationPermission { onSendWelcomeNotification() }
                        },
                        testTag = "btn_test_welcome_notif",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveNotificationShadePreviewCard(
    config: CustomPinnedNotifConfig,
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = themeSpec.cornerRadiusDp.coerceIn(8, 28).dp
    val shadeTextWhite = Color(0xFFEEEEEE)
    val shadeTextSecondary = Color(0xFF9E9EA6)

    val displayedTitle = if (config.stealthDisguise) {
        "System Power • Balanced (92%)"
    } else {
        config.title.ifBlank { "Prism Solver • Study Pin" }
    }
    val displayedBody = config.body.ifBlank { "(1-A|2-B|3-D|4-A)" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(
                width = 1.2.dp,
                color = if (config.isPinned) palette.accentEmerald.copy(alpha = 0.6f) else palette.accentSky.copy(alpha = 0.45f),
                shape = RoundedCornerShape(cardCorner)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Shade Preview",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isGranted) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.accentSky)
                            .clickable(role = Role.Button, onClick = onRequestPermission)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("btn_grant_notif_permission")
                    ) {
                        Text(
                            text = "Allow Alerts",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.onAccentInk,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (config.isPinned) palette.accentEmeraldContainer else palette.glassTintedSky)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (config.isPinned) "PINNED LIVE" else "DRAFT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (config.isPinned) palette.accentEmerald else palette.inkPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }

        // Mock Android Notification Bar Surface (realistic dark shade capsule)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0A0B0E))
                .border(1.dp, Color(0xFF2A2D35), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (config.stealthDisguise) Color(0xFF374151) else palette.accentSky),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (config.stealthDisguise) Icons.Filled.Shield else Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = if (config.stealthDisguise) shadeTextWhite else palette.onAccentInk,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Text(
                    text = if (config.stealthDisguise) "Android System • now" else "Solve Anything • Pinned",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = shadeTextSecondary,
                        fontSize = 11.sp
                    )
                )
            }

            Text(
                text = displayedTitle,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = shadeTextWhite
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = displayedBody,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = shadeTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val shadeActionColor = if (palette.isDarkObsidian) palette.accentSkyBright else palette.accentSky
                if (config.showCopy) PreviewActionLabel("Copy", shadeActionColor)
                if (config.showSolveNow) PreviewActionLabel("Solve Now", shadeActionColor)
                if (config.showStealth) PreviewActionLabel("Stealth", shadeActionColor)
                PreviewActionLabel("Unpin", shadeActionColor)
            }
        }
    }
}

@Composable
private fun PreviewActionLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    )
}
