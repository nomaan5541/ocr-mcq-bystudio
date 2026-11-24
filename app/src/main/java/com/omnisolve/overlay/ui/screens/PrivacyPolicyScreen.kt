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
                                text = "Updated v4.0 • Transparent Data & AI Policy",
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
        // ── 1. TRANSPARENT PRIVACY OVERVIEW BANNER ──
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
                            text = "ON-DEMAND PRIVACY & DATA SAFETY",
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
                            text = "v4.0 Policy",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Text(
                    text = "Prism Solver reads your screen only when you tap Solve, and only processes photos or PDFs that you explicitly select. Screenshots are processed in temporary memory and are never saved to your photo gallery.",
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
                    PrivacySpecPill(label = "Screen Capture", value = "On Tap Only", modifier = Modifier.weight(1f))
                    PrivacySpecPill(label = "On-Device OCR", value = "Google ML Kit", modifier = Modifier.weight(1f))
                    PrivacySpecPill(label = "Advertising", value = "100% Ad-Free", modifier = Modifier.weight(1f))
                }
            }
        }

        // ── SCREEN READING, PHOTOS & PDFS ──
        item(key = "privacy_sec_01") {
            PrivacyPolicySectionCard(
                title = "Screen Reading, Photos & PDFs",
                icon = Icons.Filled.Memory,
                bullets = listOf(
                    "Screen reading runs only when you tap Solve. One temporary screenshot is checked in memory and is never saved to your gallery.",
                    "Text on your screen is read on your phone first so only the question text is sent whenever possible.",
                    "Photos (up to 2 MB) and PDFs (up to 3 MB) that you pick yourself are sent securely to generate your answer, then cleared from temporary memory."
                )
            )
        }

        // ── ACCOUNT, SHARED CLASS ANSWERS & FAIR USE ──
        item(key = "privacy_sec_02") {
            PrivacyPolicySectionCard(
                title = "Google Sign-In, Shared Form Answers & Fair Use",
                icon = Icons.Filled.Security,
                bullets = listOf(
                    "Google Sign-In keeps your saved history synced and protects daily usage limits for every student.",
                    "Class-Wide Shared Form Answers: When a Google Form link is solved, its hashed link and verified answer text may be saved on our server so classmates opening the same quiz link get instant answers without repeating AI calls.",
                    "Fair-Use Protection: Daily limits apply per account and per device. Repeatedly switching or deleting accounts on the same phone to bypass daily limits triggers a warning and then a device restriction."
                )
            )
        }

        // ── AD-FREE GUARANTEE & FLOATING HELPER ──
        item(key = "privacy_sec_03") {
            PrivacyPolicySectionCard(
                title = "Ad-Free Privacy & Floating Helper",
                icon = Icons.Filled.VerifiedUser,
                bullets = listOf(
                    "Prism Solver is 100% ad-free with zero advertising SDKs, zero device advertising ID collection, and zero third-party ad tracking.",
                    "Prism Solver never uses hidden Accessibility auto-clicking or background screen recording. All network requests use TLS 1.3 / HTTPS.",
                    "You can turn off the floating helper anytime from Home, the pill close button, Quick Settings, or your notification shade."
                )
            )
        }

        // ── YOUR CONTROLS & ACCOUNT DELETION ──
        item(key = "privacy_sec_04") {
            PrivacyPolicySectionCard(
                title = "Your Data Controls & Deletion",
                icon = Icons.Filled.Lock,
                bullets = listOf(
                    "Automatic 7-Day Cleanup: Saved MCQ answers, quiz history, and local answer caches are automatically deleted after 7 days.",
                    "Any optional personal keys you enter are encrypted on your phone only and never backed up to the cloud.",
                    "You can also clear your saved history anytime, remove saved keys, or permanently delete your account from Settings.",
                    "Built as a personal study and revision helper. Always follow your school, college, and exam rules."
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
    title: String,
    icon: ImageVector,
    bullets: List<String>
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = themeSpec.cornerRadiusDp.coerceIn(12, 20).dp

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
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.accentSkyContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.accentSky,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )
            )
        }

        HorizontalDivider(color = palette.cardHairlineBorder.copy(alpha = 0.7f))

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
