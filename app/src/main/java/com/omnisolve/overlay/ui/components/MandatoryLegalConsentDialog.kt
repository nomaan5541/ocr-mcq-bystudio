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
    var isChecked by remember { mutableStateOf(false) }
    var showFullPrivacyPolicy by remember { mutableStateOf(false) }

    if (showFullPrivacyPolicy) {
        com.omnisolve.overlay.ui.screens.PrivacyPolicyModalDialog(
            onDismiss = { showFullPrivacyPolicy = false }
        )
    }

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
                            text = "Privacy & Study Promise",
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
                    title = "1. On-Demand Reading Only",
                    description = "We only capture a temporary screen frame when you tap the floating pill, or process a photo or PDF you explicitly pick. Screenshots are never saved to your gallery."
                )
                Spacer(modifier = Modifier.height(8.dp))
                LegalSummaryBullet(
                    title = "2. Solving, Shared Form Answers & Ads",
                    description = "Questions (and photos or PDFs you pick) are sent securely to generate answers. History syncs to your signed-in Google account, solved Google Form links may be cached so classmates get instant answers, and Google AdMob ads are shown."
                )
                Spacer(modifier = Modifier.height(8.dp))
                LegalSummaryBullet(
                    title = "3. Made for Personal Learning",
                    description = "Use Prism Solver as a study and revision helper in line with your school or exam rules. You can clear history or delete your account anytime in Settings."
                )

                Spacer(modifier = Modifier.height(14.dp))

                // In-App Updated Privacy Policy Viewer Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.glassMedium)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .clickable {
                            showFullPrivacyPolicy = true
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
                            text = "Privacy Policy & Terms",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.inkPrimary
                        )
                    }
                    Text(
                        text = "Read Policy",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentSky
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
                            checkedColor = palette.accentSky,
                            uncheckedColor = palette.inkSecondary,
                            checkmarkColor = palette.onAccentInk
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
                val btnBg = if (isChecked) palette.accentSky else palette.glassSubtle
                val btnInk = if (isChecked) palette.onAccentInk else palette.inkMuted
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
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
                            tint = btnInk,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.legal_consent_btn_accept),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = btnInk
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
