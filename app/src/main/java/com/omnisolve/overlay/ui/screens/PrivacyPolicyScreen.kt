package com.omnisolve.overlay.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

@Composable
fun PrivacyPolicyModalDialog(
    onDismiss: () -> Unit,
    onClearSolvedHistory: (() -> Unit)? = null,
    onClearLocalApiKeys: (() -> Unit)? = null
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val modalCorner = themeSpec.cornerRadiusDp.coerceIn(14, 28).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.canvasTop.copy(alpha = 0.90f))
                .padding(horizontal = 14.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .clip(RoundedCornerShape(modalCorner))
                    .background(palette.canvasBottom)
                    .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(modalCorner))
                    .testTag("dialog_privacy_policy_screen")
            ) {
                // Modal Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.glassStrong)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accentSkyContainer)
                                .border(1.dp, palette.accentSky, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = palette.accentSky,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Privacy Policy & Data Security",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.5.sp
                                )
                            )
                            Text(
                                text = "Updated v3.5.2 • Private In-Memory Reading",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, CircleShape)
                            .clickable(role = Role.Button, onClick = onDismiss)
                            .testTag("btn_close_privacy_policy_dialog"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close Privacy Policy",
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = palette.cardHairlineBorder)

                PrivacyPolicyScreen(
                    onBack = onDismiss,
                    onClearSolvedHistory = onClearSolvedHistory,
                    onClearLocalApiKeys = onClearLocalApiKeys,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    onClearSolvedHistory: (() -> Unit)? = null,
    onClearLocalApiKeys: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = themeSpec.cornerRadiusDp.coerceIn(12, 24).dp
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .testTag("privacy_policy_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1. VERIFIED PRIVACY SPECS BANNER ──
        item(key = "privacy_verified_banner") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.accentSky.copy(alpha = 0.5f), RoundedCornerShape(cardCorner))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(palette.accentEmerald)
                        )
                        Text(
                            text = "ZERO-LOG VOLATILE RAM ARCHITECTURE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.accentSky,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.accentSkyContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "v3.5.2 Policy",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Text(
                    text = "Prism Solver reads screen frames only in temporary device memory (RAM) when you tap the floating pill. No screenshots, screen recordings, or personal credentials are ever written to disk or uploaded to third-party tracking servers.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = palette.inkPrimary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrivacySpecPill(label = "RAM-Only Capture", value = "0 Disk Writes", modifier = Modifier.weight(1f))
                    PrivacySpecPill(label = "On-Device Reader", value = "Google ML Kit", modifier = Modifier.weight(1f))
                    PrivacySpecPill(label = "Ad / Analytics SDKs", value = "0 Trackers", modifier = Modifier.weight(1f))
                }
            }
        }

        // ── SECTION 01: TEMPORARY MEMORY CAPTURE & ON-DEVICE TEXT READER ──
        item(key = "privacy_sec_01") {
            PrivacyPolicySectionCard(
                stepNumber = "01",
                title = "Temporary Memory Reading (No Saved Screenshots)",
                icon = Icons.Filled.Memory,
                bullets = listOf(
                    "Single-Frame Memory Buffer: Only when you tap the floating pill to solve a question does Android capture a single frame into temporary memory.",
                    "On-Device Text Reading: Text reading runs locally on your phone using Google ML Kit without sending full screenshots to any external text-reading server.",
                    "Immediate Frame Disposal: Once the question and choices are read (within 150–300ms), the temporary image buffer is immediately cleared."
                )
            )
        }

        // ── SECTION 02: SOLVING STYLES & DATA TRANSMISSION ──
        item(key = "privacy_sec_02") {
            PrivacyPolicySectionCard(
                stepNumber = "02",
                title = "Solving Styles & Data Transmission",
                icon = Icons.Filled.Security,
                bullets = listOf(
                    "1. Fast Answer (Default): Sends only the question text over encrypted HTTPS to return the answer letter (A/B/C/D) in a few seconds.",
                    "2. Best Accuracy: Compares answers across multiple helpers and shows when they agree on the floating pill.",
                    "Optional Diagrams & Personal Keys: Cropped question images are sent only when you choose Photo & Diagrams or Diagram Boost. Optional personal keys connect directly from your device."
                )
            )
        }

        // ── SECTION 03: FLOATING OVERLAY & SCREEN CAPTURE DISCLOSURE ──
        item(key = "privacy_sec_03") {
            PrivacyPolicySectionCard(
                stepNumber = "03",
                title = "Floating Overlay & Screen Capture Disclosure",
                icon = Icons.Filled.VerifiedUser,
                bullets = listOf(
                    "No Accessibility Service Required: This app does not request or use Android Accessibility permissions and never auto-clicks or inspects other apps in the background.",
                    "On-Demand Screen Reading Only: Screen frames are captured strictly when you tap the Dynamic Island to read an on-screen question, processed in memory, and never saved to disk.",
                    "Instant Revocation: You can stop the floating Dynamic Island at any time from the Home screen, the pill close button, or the notification shade."
                )
            )
        }

        // ── SECTION 04: LOCAL KEY VAULT & EXPORTED STUDY DATA ──
        item(key = "privacy_sec_04") {
            PrivacyPolicySectionCard(
                stepNumber = "04",
                title = "Encrypted Local Key Vault & Exported Data",
                icon = Icons.Filled.Lock,
                bullets = listOf(
                    "Encrypted Device-Only Key Storage: Your personal API keys are encrypted with Android Keystore AES-256-GCM in a dedicated private file excluded from Android cloud backups.",
                    "User-Controlled Study Exports: Solved questions and answers are kept locally so you can review or export them as .TXT, .CSV, .JSON, or .MD files in Export Questions & Answers.",
                    "Educational Compliance: Designed strictly as a study and revision assistant. Users are responsible for complying with applicable academic and platform guidelines."
                )
            )
        }

        // ── SECTION 05: INTERACTIVE PRIVACY & DATA CONTROLS ──
        item(key = "privacy_sec_05_controls") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Data & Policy Actions",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    )
                )

                if (onClearSolvedHistory != null || onClearLocalApiKeys != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onClearSolvedHistory != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.glassMedium)
                                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button, onClick = onClearSolvedHistory)
                                    .padding(vertical = 11.dp, horizontal = 10.dp)
                                    .testTag("btn_privacy_clear_history")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    tint = palette.accentSky,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Clear Solved Q&A",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }

                        if (onClearLocalApiKeys != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.glassMedium)
                                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button, onClick = onClearLocalApiKeys)
                                    .padding(vertical = 11.dp, horizontal = 10.dp)
                                    .testTag("btn_privacy_clear_keys")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = palette.accentSky,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Wipe Local Keys",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // Open Official Public Web Policy Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.accentSkyContainer)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.URL_PRIVACY_POLICY_WEB))
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("btn_privacy_open_external_browser"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.OpenInBrowser,
                            contentDescription = null,
                            tint = palette.accentSky,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Open Public Web Privacy Policy",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            )
                            Text(
                                text = "Official Prism Solver Privacy Policy (virusboss.in)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                    Text(
                        text = "Browser",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.accentSky,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Done / Back Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.accentSky)
                        .clickable(role = Role.Button, onClick = onBack)
                        .padding(vertical = 12.dp)
                        .testTag("btn_privacy_policy_done"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = palette.onAccentInk,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Done Reading Privacy Policy",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = palette.onAccentInk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacySpecPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.glassMedium)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = palette.inkMuted,
                fontSize = 9.5.sp
            ),
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                color = palette.inkPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun PrivacyPolicySectionCard(
    stepNumber: String,
    title: String,
    icon: ImageVector,
    bullets: List<String>
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = themeSpec.cornerRadiusDp.coerceIn(12, 24).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.accentSkyContainer)
                    .border(1.dp, palette.accentSky.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.accentSky,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                )
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.accentSky,
                modifier = Modifier.size(18.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )
            )
        }

        HorizontalDivider(color = palette.cardHairlineBorder)

        bullets.forEach { bullet ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = palette.accentSky,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = bullet,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = palette.inkSecondary,
                        fontSize = 12.2.sp,
                        lineHeight = 17.5.sp
                    )
                )
            }
        }
    }
}
