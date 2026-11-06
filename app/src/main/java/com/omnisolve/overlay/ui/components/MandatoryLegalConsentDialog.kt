package com.omnisolve.overlay.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.R
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

/**
 * Mandatory first-launch agreement modal.
 * Users must check the agreement box before the application allows access.
 */
@Composable
fun MandatoryLegalConsentDialog(
    onAcceptAndContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val context = LocalContext.current
    var isChecked by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { /* Non-dismissible: Must accept to proceed */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = modifier
                .padding(20.dp)
                .widthIn(max = 520.dp)
                .livingGlassSurface(
                    strength = GlassMaterialStrength.Card,
                    cornerRadius = 24.dp
                )
                .padding(22.dp)
                .testTag("dialog_mandatory_legal_consent")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with Shield Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(palette.accentSkyContainer)
                            .border(1.dp, palette.accentSky, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = palette.accentSky,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.legal_consent_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary
                        )
                        Text(
                            text = "Legal Terms & Privacy Disclosure",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = palette.cardHairlineBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Summary points
                LegalSummaryBullet(
                    title = "1. Personal Educational Use Only",
                    description = "prism-ocr-solver is designed strictly as an assistive learning companion and study revision aid. You agree to use it in compliance with your school or exam policies."
                )
                Spacer(modifier = Modifier.height(8.dp))
                LegalSummaryBullet(
                    title = "2. On-Device RAM Processing & Zero Log Policy",
                    description = "Screen captures are temporarily processed strictly in device memory (RAM) via ML Kit on-device OCR and immediately destroyed. We never record, store, or sell your screen captures."
                )
                Spacer(modifier = Modifier.height(8.dp))
                LegalSummaryBullet(
                    title = "3. Private Device-Only Storage for Keys",
                    description = "Your AI API keys are saved locally on your phone only and are never uploaded to our servers or shared with third parties."
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Public Policy Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (palette.isDarkObsidian) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.URL_PRIVACY_POLICY_WEB))
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("btn_consent_open_policy_link"),
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "virusboss.in/app/prism-ocr-solver/#privacy-policy",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accentSky
                        )
                    }
                    Text(
                        text = "Read Policy",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkSecondary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = palette.cardHairlineBorder)
                Spacer(modifier = Modifier.height(10.dp))

                // Checkbox Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Checkbox) { isChecked = !isChecked }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { isChecked = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = palette.accentEmerald,
                            uncheckedColor = palette.inkSecondary,
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.testTag("checkbox_accept_terms")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.legal_consent_checkbox),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkPrimary,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Accept & Continue Button (Disabled until checked)
                val btnBg = if (isChecked) {
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Color(0xFF0284C7), Color(0xFF1D4ED8))
                    )
                } else {
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Color(0xFF64748B), Color(0xFF475569))
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(btnBg)
                        .clickable(
                            enabled = isChecked,
                            onClick = onAcceptAndContinue
                        )
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                        .testTag("btn_accept_and_continue"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (isChecked) Color.White else Color(0xFFCBD5E1),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.legal_consent_btn_accept),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isChecked) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalSummaryBullet(
    title: String,
    description: String
) {
    val palette = LocalWhiteGlassPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSecondary
        )
    }
}
