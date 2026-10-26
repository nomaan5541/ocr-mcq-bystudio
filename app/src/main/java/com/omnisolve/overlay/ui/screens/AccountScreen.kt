package com.omnisolve.overlay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnisolve.overlay.R
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.components.LivingOptionChoiceCard
import com.omnisolve.overlay.ui.components.LivingPrimaryButton
import com.omnisolve.overlay.ui.components.LivingSecondaryButton
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.ScrollRevealSection
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

@Composable
fun AccountScreen(
    state: MainUiState,
    listState: LazyListState,
    onOpenGoogleLogin: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenProjectRepo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    var apiKeyDraft by remember(state.customApiKey) { mutableStateOf(state.customApiKey) }

    val scrollFraction by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex * 240f + listState.firstVisibleItemScrollOffset) / 900f
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("account_screen_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Free Google Gemini Web Sign-In Card
        item(key = "google_web_card") {
            ScrollRevealSection(index = 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .livingGlassSurface(
                            strength = GlassMaterialStrength.AccentSky,
                            cornerRadius = 24.dp,
                            scrollParallaxFactor = scrollFraction
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.isGoogleWebLoggedIn) palette.accentMintContainer else Color.White
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.isGoogleWebLoggedIn) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.Login,
                                contentDescription = null,
                                tint = if (state.isGoogleWebLoggedIn) palette.accentMint else palette.accentSky,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ai_web_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.inkPrimary
                            )
                            Text(
                                text = if (state.isGoogleWebLoggedIn) {
                                    "Signed in and ready for free Gemini Web solving"
                                } else {
                                    "Not signed in yet — tap below to connect your Google account"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (state.isGoogleWebLoggedIn) palette.accentMint else palette.warningAmber
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.ai_web_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSecondary
                    )

                    LivingPrimaryButton(
                        text = if (state.isGoogleWebLoggedIn) "Manage Google Gemini Session" else "Sign In to Google Gemini",
                        icon = Icons.AutoMirrored.Filled.Login,
                        onClick = onOpenGoogleLogin,
                        testTag = "btn_google_login",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 2. Direct Gemini API Key & Model Selector Card
        item(key = "gemini_api_card") {
            ScrollRevealSection(index = 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .livingGlassSurface(
                            strength = GlassMaterialStrength.Card,
                            cornerRadius = 24.dp,
                            scrollParallaxFactor = scrollFraction
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.ai_api_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.ai_api_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary
                        )
                    }

                    if (state.hasSecretApiKey && state.customApiKey.isBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accentMintContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = palette.accentMint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Studio Secret GEMINI_API_KEY detected automatically",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF065F46)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = apiKeyDraft,
                            onValueChange = { apiKeyDraft = it },
                            placeholder = {
                                Text(
                                    text = "Optional custom Gemini API key...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.inkSecondary
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xF2FFFFFF),
                                focusedBorderColor = palette.accentSky,
                                unfocusedBorderColor = Color(0xFF94A3B8),
                                focusedTextColor = palette.inkPrimary,
                                unfocusedTextColor = palette.inkPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_gemini_api_key")
                        )

                        LivingSecondaryButton(
                            text = "Save",
                            icon = Icons.Filled.Save,
                            selected = state.customApiKey.isNotBlank(),
                            onClick = { onSaveApiKey(apiKeyDraft) },
                            testTag = "btn_save_api_key",
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.ai_model_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.inkPrimary
                    )

                    LivingOptionChoiceCard(
                        title = stringResource(R.string.model_flash_title),
                        subtitle = stringResource(R.string.model_flash_desc),
                        selected = state.geminiModel == "gemini-3.5-flash",
                        icon = Icons.Filled.Bolt,
                        onClick = { onSelectModel("gemini-3.5-flash") },
                        testTag = "option_model_flash"
                    )

                    LivingOptionChoiceCard(
                        title = stringResource(R.string.model_pro_title),
                        subtitle = stringResource(R.string.model_pro_desc),
                        selected = state.geminiModel == "gemini-3.1-pro-preview",
                        icon = Icons.Filled.Psychology,
                        onClick = { onSelectModel("gemini-3.1-pro-preview") },
                        testTag = "option_model_pro"
                    )

                    LivingOptionChoiceCard(
                        title = stringResource(R.string.model_lite_title),
                        subtitle = stringResource(R.string.model_lite_desc),
                        selected = state.geminiModel == "gemini-3.1-flash-lite-preview",
                        icon = Icons.Filled.Speed,
                        onClick = { onSelectModel("gemini-3.1-flash-lite-preview") },
                        testTag = "option_model_lite"
                    )
                }
            }
        }

        // 3. Recent Solved Questions History
        item(key = "history_card") {
            ScrollRevealSection(index = 2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .livingGlassSurface(
                            strength = GlassMaterialStrength.Card,
                            cornerRadius = 24.dp,
                            scrollParallaxFactor = scrollFraction
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.history_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.inkPrimary
                        )
                        if (state.historyEntries.isNotEmpty()) {
                            LivingSecondaryButton(
                                text = stringResource(R.string.btn_clear_history),
                                icon = Icons.Filled.DeleteSweep,
                                onClick = onClearHistory,
                                testTag = "btn_clear_history",
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (state.historyEntries.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x99F8FAFC))
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = palette.inkMuted,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.history_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkSecondary
                            )
                        }
                    } else {
                        state.historyEntries.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xD9FFFFFF))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(palette.accentMintContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = entry.answerLetter,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.accentMint
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.questionPreview,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.inkPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.sourceLabel} • ${entry.timestamp}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.inkMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Open-Source Project Link
        item(key = "about_footer") {
            ScrollRevealSection(index = 3) {
                LivingSecondaryButton(
                    text = "View Project Guide on GitHub",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = onOpenProjectRepo,
                    testTag = "btn_open_github",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
