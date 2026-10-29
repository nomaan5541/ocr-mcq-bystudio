package com.omnisolve.overlay.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.R
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.components.LivingOptionChoiceCard
import com.omnisolve.overlay.ui.components.LivingPrimaryButton
import com.omnisolve.overlay.ui.components.LivingSecondaryButton
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

@Composable
fun HomeScreen(
    state: MainUiState,
    listState: LazyListState,
    onToggleOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onSelectSolverMethod: (SolverMethod) -> Unit,
    onToggleChargingFormat: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSignInServer: () -> Unit,
    onOpenGeminiLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Instant Start / Stop Floating Overlay Card
        item(key = "overlay_launcher_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .livingGlassSurface(
                        strength = GlassMaterialStrength.Hero,
                        cornerRadius = 22.dp
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.isOverlayRunning) Color(0xFF10B981) else palette.accentSky
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isOverlayRunning) "Floating Overlay Active" else "Ready to Launch",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary
                        )
                    }

                    Text(
                        text = if (state.batteryCamouflage) "91–94%" else "ABCD",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (state.batteryCamouflage) Color(0xFF047857) else Color(0xFF0369A1),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (state.batteryCamouflage) Color(0xFFD1FAE5) else Color(0xFFE0F2FE)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap the floating pill over any quiz screen to solve immediately. Tap again anytime to restart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary
                )

                if (!state.hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFFEF3C7))
                            .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overlay Permission Needed",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "Allow Display over other apps once to launch.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        LivingSecondaryButton(
                            text = stringResource(R.string.btn_grant),
                            icon = Icons.Filled.Layers,
                            onClick = onRequestOverlayPermission,
                            testTag = "btn_perm_overlay"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                LivingPrimaryButton(
                    text = if (state.isOverlayRunning) {
                        stringResource(R.string.cta_stop_solver)
                    } else {
                        stringResource(R.string.cta_start_solver)
                    },
                    icon = if (state.isOverlayRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    isActiveState = state.isOverlayRunning,
                    onClick = onToggleOverlay,
                    testTag = "btn_toggle_overlay",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 2. The 3 Primary Answer Methods right on the Home Screen
        item(key = "home_answer_methods_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .livingGlassSurface(
                        strength = GlassMaterialStrength.Card,
                        cornerRadius = 22.dp
                    )
                    .padding(18.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_methods_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.home_methods_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Method 1: Server Sol
                val isServerSignedIn = !state.firebaseUserId.isNullOrBlank()
                LivingOptionChoiceCard(
                    title = SolverMethod.FIREBASE_SERVER.title,
                    subtitle = SolverMethod.FIREBASE_SERVER.subtitle,
                    selected = state.solverMethod == SolverMethod.FIREBASE_SERVER,
                    icon = Icons.Filled.CloudDone,
                    badgeText = if (isServerSignedIn) "Logged In" else "Login Needed",
                    onClick = { onSelectSolverMethod(SolverMethod.FIREBASE_SERVER) },
                    testTag = "option_method_firebase"
                )
                if (state.solverMethod == SolverMethod.FIREBASE_SERVER && !isServerSignedIn) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LivingSecondaryButton(
                        text = if (state.isSigningInGoogle) "Signing in..." else "Log in to Server with Google",
                        icon = Icons.Filled.CloudDone,
                        onClick = onSignInServer,
                        testTag = "btn_home_server_login",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Method 2: OCR Text Extractor (Previous Method)
                LivingOptionChoiceCard(
                    title = SolverMethod.BACKGROUND_GEMINI.title,
                    subtitle = SolverMethod.BACKGROUND_GEMINI.subtitle,
                    selected = state.solverMethod == SolverMethod.BACKGROUND_GEMINI,
                    icon = Icons.Filled.DocumentScanner,
                    badgeText = if (state.isGoogleWebLoggedIn) "Gemini Ready" else "Built-in",
                    onClick = { onSelectSolverMethod(SolverMethod.BACKGROUND_GEMINI) },
                    testTag = "option_method_background_gemini"
                )
                if (state.solverMethod == SolverMethod.BACKGROUND_GEMINI && !state.isGoogleWebLoggedIn) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LivingSecondaryButton(
                        text = "Connect Background Gemini Session",
                        icon = Icons.Filled.DocumentScanner,
                        onClick = onOpenGeminiLogin,
                        testTag = "btn_home_gemini_login",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Method 3: API Key (Local Storage)
                val keyBadge = if (state.configuredKeyCount > 0) {
                    "${state.configuredKeyCount} Local Key(s)"
                } else {
                    "Set in Settings"
                }
                LivingOptionChoiceCard(
                    title = SolverMethod.API_KEYS.title,
                    subtitle = SolverMethod.API_KEYS.subtitle,
                    selected = state.solverMethod == SolverMethod.API_KEYS,
                    icon = Icons.Filled.Key,
                    badgeText = keyBadge,
                    onClick = { onSelectSolverMethod(SolverMethod.API_KEYS) },
                    testTag = "option_method_api_keys"
                )
                if (state.solverMethod == SolverMethod.API_KEYS && state.configuredKeyCount == 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LivingSecondaryButton(
                        text = "Enter Local API Keys in Settings",
                        icon = Icons.Filled.Settings,
                        onClick = onOpenSettings,
                        testTag = "btn_home_enter_keys",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Active indicator if 4th Settings method ("Overlapping Google Completely") is active
                if (state.solverMethod == SolverMethod.OVERLAPPING_GOOGLE) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE0F2FE))
                            .border(1.4.dp, palette.accentSky, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInFull,
                            contentDescription = null,
                            tint = palette.accentSky,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overlapping Google Completely (Active)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0C4A6E)
                            )
                            Text(
                                text = "Enabled from Settings. Tap 1, 2, or 3 above anytime to switch back.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkSecondary
                            )
                        }
                    }
                }
            }
        }

        // 3. Selected Setup Summary & Quick Format Switch (ABCD <-> Charging 91, 92, 93, 94)
        item(key = "selected_summary_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .livingGlassSurface(
                        strength = GlassMaterialStrength.Card,
                        cornerRadius = 22.dp
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.home_selected_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.inkPrimary
                    )
                    LivingSecondaryButton(
                        text = stringResource(R.string.btn_open_settings),
                        icon = Icons.Filled.Settings,
                        onClick = onOpenSettings,
                        testTag = "btn_open_settings_from_home",
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                SummaryStatusRow(
                    label = "Selected Method",
                    value = state.solverMethod.shortName
                )
                Spacer(modifier = Modifier.height(8.dp))
                SummaryStatusRow(
                    label = "Overlay Shape",
                    value = if (state.hudStyle == MainActivity.HUD_STYLE_ISLAND) {
                        "Top Dynamic Pill (${listOf(100, 90, 50, 25)[state.islandOpacityIndex.coerceIn(0, 3)]}%)"
                    } else {
                        "Mini Bubble (${listOf(100, 90, 50, 25)[state.islandOpacityIndex.coerceIn(0, 3)]}%)"
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SummaryStatusRow(
                    label = "Local API Keys",
                    value = if (state.configuredKeyCount > 0) {
                        "${state.configuredKeyCount} saved on device"
                    } else {
                        "None saved"
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick 1-tap Answer Format Toggle (ABCD <-> Charging 91, 92, 93, 94)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (state.batteryCamouflage) Color(0xFFECFDF5) else Color(0xFFF1F5F9))
                        .border(
                            width = 1.dp,
                            color = if (state.batteryCamouflage) Color(0xFF10B981) else Color(0xFFCBD5E1),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable(
                            role = Role.Button,
                            onClick = { onToggleChargingFormat(!state.batteryCamouflage) }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("btn_quick_toggle_format"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BatteryChargingFull,
                            contentDescription = null,
                            tint = if (state.batteryCamouflage) Color(0xFF047857) else palette.accentSky,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (state.batteryCamouflage) {
                                    "Display: Charging 91, 92, 93, 94"
                                } else {
                                    "Display: Option ABCD"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary
                            )
                            Text(
                                text = if (state.batteryCamouflage) {
                                    "91% = A • 92% = B • 93% = C • 94% = D"
                                } else {
                                    "Tap to switch to Charging 91, 92, 93, 94"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = "Switch answer format",
                        tint = palette.inkSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatusRow(
    label: String,
    value: String
) {
    val palette = LocalWhiteGlassPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = palette.inkPrimary
        )
    }
}
