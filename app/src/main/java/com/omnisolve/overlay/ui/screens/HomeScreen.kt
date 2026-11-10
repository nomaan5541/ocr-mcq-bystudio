package com.omnisolve.overlay.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.R
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import kotlin.math.roundToInt

// 3D Isometric Cube Vector for "Select Answer Method"
val CubeVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "Cube3D",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        fill = SolidColor(Color.White),
        pathData = listOf(
            PathNode.MoveTo(12f, 2f),
            PathNode.LineTo(21f, 7f),
            PathNode.LineTo(12f, 12f),
            PathNode.LineTo(3f, 7f),
            PathNode.Close,
            PathNode.MoveTo(12f, 13.5f),
            PathNode.LineTo(21f, 8.5f),
            PathNode.LineTo(21f, 17f),
            PathNode.LineTo(12f, 22f),
            PathNode.Close,
            PathNode.MoveTo(3f, 8.5f),
            PathNode.LineTo(12f, 13.5f),
            PathNode.LineTo(12f, 22f),
            PathNode.LineTo(3f, 17f),
            PathNode.Close
        )
    ).build()
}

@Composable
fun HomeScreen(
    state: MainUiState,
    listState: LazyListState,
    onToggleOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onSelectSolverMethod: (SolverMethod) -> Unit,
    onSetCustomOpacityPct: (Int) -> Unit,
    onSelectQuestionSolveMode: (QuestionSolveMode) -> Unit,
    onToggleChargingFormat: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSignInServer: () -> Unit,
    onOpenGeminiLogin: () -> Unit,
    onToggleTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val focusManager = LocalFocusManager.current
    var opacityTextInput by remember(state.customOpacityPct) {
        mutableStateOf(state.customOpacityPct.toString())
    }

    val cardBg = if (palette.isDarkObsidian) Color(0xFF121316) else Color.White
    val cardBorder = if (palette.isDarkObsidian) Color(0xFF23252A) else Color(0xFFE5E7EB)
    val textPrimary = if (palette.isDarkObsidian) Color(0xFFEEEEEE) else Color(0xFF0A0A0C)
    val textSecondary = if (palette.isDarkObsidian) Color(0xFF767676) else Color(0xFF4B5563)
    val brandRed = Color(0xFFD71921)

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // ── HERO GRADIENT BANNER (OLED DARK WITH RED ACCENTS) ─────────
        item(key = "hero_banner") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(26.dp),
                        ambientColor = Color(0x33D71921),
                        spotColor = Color(0x40D71921)
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        if (palette.isDarkObsidian)
                            Brush.linearGradient(listOf(Color(0xFF18191E), Color(0xFF111215)))
                        else
                            Brush.linearGradient(listOf(Color(0xFF2B2D34), Color(0xFF141518)))
                    )
                    .border(1.dp, cardBorder, RoundedCornerShape(26.dp))
                    .padding(start = 20.dp, top = 20.dp, bottom = 20.dp, end = 10.dp)
            ) {
                // Background Radial Glow Accent (Matching HTML hero-glow)
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawCircle(
                        color = Color(0x38D71921),
                        radius = size.width * 0.42f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.35f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Text Content
                    Column(modifier = Modifier.weight(1.22f)) {
                        Text(
                            text = "Solve Anything\nOn Your Screen",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 21.sp,
                                color = Color(0xFFEEEEEE),
                                letterSpacing = (-0.4).sp
                            )
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "Instant Floating AI study helper that reads MCQs on screen & solves in 1s",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF9E9E9E),
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.2.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // CTA Button
                        Box(
                            modifier = Modifier
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(50.dp), ambientColor = Color(0x33000000))
                                .clip(RoundedCornerShape(50.dp))
                                .background(Color.White)
                                .clickable(role = Role.Button, onClick = onToggleOverlay)
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                                .testTag("btn_toggle_overlay")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (state.isOverlayRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = brandRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = if (state.isOverlayRunning)
                                        stringResource(R.string.cta_stop_solver)
                                    else
                                        stringResource(R.string.cta_start_solver),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color(0xFF0A0A0C),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Right 3D Visual Artwork
                    Box(
                        modifier = Modifier.weight(0.85f),
                        contentAlignment = Alignment.Center
                    ) {
                        HeroArtwork()
                    }
                }
            }
        }

        // ── SECTION HEADER: SELECT ANSWER METHOD ─────────────────────
        item(key = "methods_section_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 1.dp, start = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brandRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CubeVector,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Select Answer Method",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    )
                    Text(
                        text = "Choose how you want to get the answer",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary
                        )
                    )
                }
            }
        }

        // ── METHOD 1: SERVER SOL ─────────────────────────────────────
        item(key = "method_card_server") {
            val isServerSignedIn = !state.firebaseUserId.isNullOrBlank()
            MethodCard(
                number = "1.",
                title = "Server Sol",
                subtitle = "Log in on server — sends the extracted screen question to server and returns the answer.",
                icon = Icons.Filled.Cloud,
                iconTint = if (palette.isDarkObsidian) Color(0xFFEEEEEE) else brandRed,
                iconBg = if (palette.isDarkObsidian) Color(0xFF1E2025) else Color(0xFFFEE2E2),
                badgeText = if (isServerSignedIn) "⚡ Logged In" else "⚡ Login Needed",
                badgeBg = if (isServerSignedIn) Color(0xFF14291E) else Color(0xFF2A1315),
                badgeTextColor = if (isServerSignedIn) Color(0xFF4ADE80) else brandRed,
                isSelected = state.solverMethod == SolverMethod.FIREBASE_SERVER,
                onClick = { onSelectSolverMethod(SolverMethod.FIREBASE_SERVER) },
                testTag = "option_method_firebase"
            )
            if (state.solverMethod == SolverMethod.FIREBASE_SERVER && !isServerSignedIn) {
                Spacer(modifier = Modifier.height(6.dp))
                PrismActionRow(
                    label = if (state.isSigningInGoogle) "Signing in..." else "Log in to Server with Google",
                    icon = Icons.Filled.Cloud,
                    onClick = onSignInServer,
                    testTag = "btn_home_server_login"
                )
            }
        }

        // ── METHOD 2: OCR TEXT EXTRACTOR (RECOMMENDED) ───────────────
        item(key = "method_card_gemini") {
            MethodCard(
                number = "2.",
                title = "OCR Text Extractor",
                subtitle = "Takes screenshot → extracts text via OCR → sends to background Gemini Flash.",
                icon = Icons.Filled.DocumentScanner,
                iconTint = Color.White,
                iconBg = brandRed,
                badgeText = if (state.isGoogleWebLoggedIn) "Gemini Ready" else "Built-in",
                badgeBg = if (palette.isDarkObsidian) Color(0xFF1E293B) else Color(0xFFDBEAFE),
                badgeTextColor = if (palette.isDarkObsidian) Color(0xFF60A5FA) else Color(0xFF1D4ED8),
                isSelected = state.solverMethod == SolverMethod.BACKGROUND_GEMINI,
                isRecommended = true,
                onClick = { onSelectSolverMethod(SolverMethod.BACKGROUND_GEMINI) },
                testTag = "option_method_background_gemini"
            )
        }

        // ── METHOD 3: API KEY ────────────────────────────────────────
        item(key = "method_card_api_key") {
            val keyBadge = if (state.configuredKeyCount > 0) "${state.configuredKeyCount} Key(s) Ready" else "3 Key(s) Ready"
            MethodCard(
                number = "3.",
                title = "API Key",
                subtitle = "Uses locally stored keys from Settings (NVIDIA Free, OpenRouter, Gemini, OpenAI) with auto quota shift.",
                icon = Icons.Filled.Key,
                iconTint = Color(0xFFA855F7),
                iconBg = if (palette.isDarkObsidian) Color(0xFF261933) else Color(0xFFF3E8FF),
                badgeText = keyBadge,
                badgeBg = if (palette.isDarkObsidian) Color(0xFF2E1B42) else Color(0xFFEDE9FE),
                badgeTextColor = Color(0xFFC084FC),
                isSelected = state.solverMethod == SolverMethod.API_KEYS,
                onClick = { onSelectSolverMethod(SolverMethod.API_KEYS) },
                testTag = "option_method_api_keys"
            )
            if (state.solverMethod == SolverMethod.API_KEYS && state.configuredKeyCount == 0) {
                Spacer(modifier = Modifier.height(6.dp))
                PrismActionRow(
                    label = "Enter Local API Keys in Settings",
                    icon = Icons.Filled.Settings,
                    onClick = onOpenSettings,
                    testTag = "btn_home_enter_keys"
                )
            }
        }

        // ── ACTION STRIP BUTTON (BACKGROUND GEMINI SESSION) ───────────
        item(key = "action_gemini_session") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button, onClick = onOpenGeminiLogin)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("btn_home_gemini_login"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (palette.isDarkObsidian) Color(0xFF2A1315) else Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cast,
                        contentDescription = null,
                        tint = brandRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Connect Background Gemini Session",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // ── PERMISSION CARD ──────────────────────────────────────────
        item(key = "perm_launch_card") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (palette.isDarkObsidian) Color(0xFF2A1315) else Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = brandRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ready to Launch",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        ),
                        color = textPrimary
                    )
                    Text(
                        text = "Tap the overlay permission to allow display over other apps.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = textSecondary
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp), ambientColor = Color(0x33D71921))
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (state.hasOverlayPermission) Color(0xFF10B981) else brandRed)
                        .clickable(role = Role.Button, onClick = onRequestOverlayPermission)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (state.hasOverlayPermission) Icons.Filled.Check else Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (state.hasOverlayPermission) stringResource(R.string.btn_enabled) else stringResource(R.string.btn_grant),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }

        // ── QUICK SETTINGS SECTION ───────────────────────────────────
        item(key = "quick_settings_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("section_home_opacity")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Quick Settings",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    )
                }

                Spacer(Modifier.height(6.dp))

                // 3 Quick Cards Grid matching HTML
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Card 1: Overlay Visibility
                    QuickCard(
                        icon = Icons.Filled.Layers,
                        label = "Overlay Visibility",
                        value = "${state.customOpacityPct}%",
                        onClick = {
                            val nextPct = when (state.customOpacityPct) {
                                100 -> 75
                                75 -> 50
                                50 -> 30
                                else -> 100
                            }
                            onSetCustomOpacityPct(nextPct)
                        },
                        modifier = Modifier.weight(1f),
                        isDark = palette.isDarkObsidian
                    )
                    // Card 2: Question Mode
                    QuickCard(
                        icon = Icons.Filled.Quiz,
                        label = "Question Mode",
                        value = when (state.questionSolveMode) {
                            QuestionSolveMode.STANDARD -> "Standard"
                            QuestionSolveMode.MATCH_FOLLOWING -> "Match"
                            QuestionSolveMode.COMPLEX_MATH -> "Math+"
                        },
                        onClick = {
                            val nextMode = when (state.questionSolveMode) {
                                QuestionSolveMode.STANDARD -> QuestionSolveMode.MATCH_FOLLOWING
                                QuestionSolveMode.MATCH_FOLLOWING -> QuestionSolveMode.COMPLEX_MATH
                                QuestionSolveMode.COMPLEX_MATH -> QuestionSolveMode.STANDARD
                            }
                            onSelectQuestionSolveMode(nextMode)
                        },
                        modifier = Modifier.weight(1f),
                        isDark = palette.isDarkObsidian
                    )
                    // Card 3: Theme
                    QuickCard(
                        icon = Icons.Filled.DarkMode,
                        label = "Theme",
                        value = if (palette.isDarkObsidian) "Dark" else "Light",
                        onClick = onToggleTheme,
                        modifier = Modifier.weight(1f),
                        isDark = palette.isDarkObsidian
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Fine-tuning Slider & Mode Chips
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Opacity, null, tint = brandRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Visibility / Opacity", style = MaterialTheme.typography.labelMedium, color = textSecondary, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = opacityTextInput,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(3)
                                opacityTextInput = digits
                                val parsed = digits.toIntOrNull()
                                if (parsed != null && parsed in 20..100) onSetCustomOpacityPct(parsed)
                            },
                            singleLine = true,
                            suffix = { Text("%", style = MaterialTheme.typography.labelSmall, color = textSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val clamped = (opacityTextInput.toIntOrNull() ?: state.customOpacityPct).coerceIn(20, 100)
                                opacityTextInput = clamped.toString()
                                onSetCustomOpacityPct(clamped)
                                focusManager.clearFocus()
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = brandRed,
                                unfocusedBorderColor = cardBorder,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.width(76.dp).testTag("input_home_opacity_pct")
                        )
                    }
                    Slider(
                        value = state.customOpacityPct.toFloat(),
                        onValueChange = {
                            val rounded = it.roundToInt().coerceIn(20, 100)
                            opacityTextInput = rounded.toString()
                            onSetCustomOpacityPct(rounded)
                        },
                        valueRange = 20f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = brandRed,
                            activeTrackColor = brandRed,
                            inactiveTrackColor = if (palette.isDarkObsidian) Color(0xFF23252A) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("slider_home_opacity_pct")
                    )

                    Spacer(Modifier.height(8.dp))

                    Text("Question Solving Mode", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuestionSolveMode.entries.forEach { mode ->
                            val isSelected = state.questionSolveMode == mode
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(
                                        if (isSelected) brandRed
                                        else if (palette.isDarkObsidian) Color(0xFF17181C) else Color(0xFFF1F5F9)
                                    )
                                    .clickable(role = Role.Button) { onSelectQuestionSolveMode(mode) }
                                    .padding(vertical = 9.dp)
                                    .testTag("btn_home_mode_${mode.id.lowercase()}")
                            ) {
                                Text(
                                    text = when (mode) {
                                        QuestionSolveMode.STANDARD -> "Standard"
                                        QuestionSolveMode.MATCH_FOLLOWING -> "Match"
                                        QuestionSolveMode.COMPLEX_MATH -> "Math+"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (isSelected) Color.White else textSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── METHOD CARD (STANDALONE) ─────────────────────────────────────────

@Composable
private fun MethodCard(
    number: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    badgeText: String,
    badgeBg: Color,
    badgeTextColor: Color,
    isSelected: Boolean,
    isRecommended: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalWhiteGlassPalette.current
    val brandRed = Color(0xFFD71921)
    val borderColor = if (isSelected) brandRed else if (palette.isDarkObsidian) Color(0xFF23252A) else Color(0xFFE5E7EB)
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val bgColor = when {
        isSelected && palette.isDarkObsidian -> Color(0xFF141519)
        isSelected -> Color(0xFFFFF5F5)
        palette.isDarkObsidian -> Color(0xFF121316)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .then(
                    if (isSelected)
                        Modifier.shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), ambientColor = Color(0x33D71921), spotColor = Color(0x40D71921))
                    else
                        Modifier.shadow(elevation = 1.dp, shape = RoundedCornerShape(16.dp), ambientColor = Color(0x0A000000))
                )
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor)
                .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = if (isRecommended) 13.dp else 11.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconBg)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.wrapContentHeight()
                ) {
                    Text(
                        text = "$number $title",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (palette.isDarkObsidian) Color(0xFFEEEEEE) else Color(0xFF0A0A0C)
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(badgeBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = badgeTextColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            )
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (palette.isDarkObsidian) Color(0xFF767676) else Color(0xFF4B5563)
                )
            }

            Spacer(Modifier.width(8.dp))

            // Checkmark or chevron
            if (isSelected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(brandRed)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (palette.isDarkObsidian) Color(0xFF17181C) else Color(0xFFF1F5F9))
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = if (palette.isDarkObsidian) Color(0xFF767676) else Color(0xFF94A3B8),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // Top Recommended Badge (pinned at top-left)
        if (isRecommended) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFD71921), Color(0xFFA51017))
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = "★ Recommended",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

// ── ACTION ROW ───────────────────────────────────────────────────────

@Composable
private fun PrismActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalWhiteGlassPalette.current
    val brandRed = Color(0xFFD71921)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (palette.isDarkObsidian) Color(0xFF17181C) else Color(0xFFF1F5F9))
            .border(1.dp, if (palette.isDarkObsidian) Color(0xFF23252A) else Color(0xFFE5E7EB), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = brandRed, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (palette.isDarkObsidian) Color(0xFFEEEEEE) else Color(0xFF0A0A0C),
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Filled.ChevronRight, null, tint = if (palette.isDarkObsidian) Color(0xFF767676) else Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
    }
}

// ── QUICK SETTINGS CARD ──────────────────────────────────────────────

@Composable
private fun QuickCard(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    val brandRed = Color(0xFFD71921)
    Column(
        modifier = modifier
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp), ambientColor = Color(0x0A000000))
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Color(0xFF121316) else Color.White)
            .border(
                1.dp,
                if (isDark) Color(0xFF23252A) else Color(0xFFE5E7EB),
                RoundedCornerShape(16.dp)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = brandRed,
                modifier = Modifier.size(16.dp)
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = if (isDark) Color(0xFF767676) else Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            color = if (isDark) Color(0xFF767676) else Color(0xFF64748B)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (isDark) Color(0xFFEEEEEE) else Color(0xFF1E293B)
        )
    }
}

// ── 3D HERO ARTWORK (MATCHING OLED DARK WITH RED SCANNING LASER) ─────

@Composable
fun HeroArtwork(modifier: Modifier = Modifier) {
    val brandRed = Color(0xFFD71921)
    Box(
        modifier = modifier.size(width = 120.dp, height = 135.dp),
        contentAlignment = Alignment.Center
    ) {
        // Glowing Red Orbital Rings (Matching HTML scanner-ring)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Ring 1 (rotate -25 deg)
            drawContext.transform.rotate(-25f, pivot = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.5f))
            drawOval(
                color = brandRed.copy(alpha = 0.25f),
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.04f, h * 0.28f),
                size = androidx.compose.ui.geometry.Size(w * 0.92f, h * 0.44f),
                style = Stroke(width = 8f)
            )
            drawOval(
                color = brandRed.copy(alpha = 0.65f),
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.05f, h * 0.30f),
                size = androidx.compose.ui.geometry.Size(w * 0.90f, h * 0.40f),
                style = Stroke(width = 2.dp.toPx())
            )

            // Ring 2 (rotate 18 deg)
            drawContext.transform.rotate(43f, pivot = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.5f))
            drawOval(
                color = brandRed.copy(alpha = 0.35f),
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.02f, h * 0.26f),
                size = androidx.compose.ui.geometry.Size(w * 0.96f, h * 0.46f),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        // Stylized Floating OLED Smartphone Graphic (rotate 12 deg)
        Column(
            modifier = Modifier
                .size(width = 80.dp, height = 126.dp)
                .rotate(12f)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = Color(0x40D71921),
                    spotColor = Color(0x60D71921)
                )
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1D1F25), Color(0xFF0F1012))
                    )
                )
                .border(2.dp, brandRed.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Earpiece
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color(0xFF333640))
            )
            Spacer(Modifier.height(5.dp))

            // Scanned Document View (Dark OLED Glass)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0A0C))
                    .padding(6.dp)
            ) {
                // Simulated MCQ quiz text lines
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.5.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth(0.85f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(brandRed))
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF23252A)))
                    Box(modifier = Modifier.fillMaxWidth(0.75f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF23252A)))
                    // Highlighted Correct Option (green check)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF14291E)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF4ADE80)))
                        Spacer(Modifier.width(3.dp))
                        Box(modifier = Modifier.fillMaxWidth(0.6f).height(3.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFF4ADE80)))
                    }
                    Box(modifier = Modifier.fillMaxWidth(0.65f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF23252A)))
                }

                // 4 Scanner Corner Brackets & Red Laser Beam
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bw = size.width
                    val bh = size.height
                    val len = 8f
                    val sw = 2f

                    // Top-Left
                    drawLine(brandRed, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(len, 0f), strokeWidth = sw)
                    drawLine(brandRed, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, len), strokeWidth = sw)

                    // Top-Right
                    drawLine(brandRed, androidx.compose.ui.geometry.Offset(bw, 0f), androidx.compose.ui.geometry.Offset(bw - len, 0f), strokeWidth = sw)
                    drawLine(brandRed, androidx.compose.ui.geometry.Offset(bw, 0f), androidx.compose.ui.geometry.Offset(bw, len), strokeWidth = sw)

                    // Bottom-Left
                    drawLine(brandRed, androidx.compose.ui.geometry.Offset(0f, bh), androidx.compose.ui.geometry.Offset(len, bh), strokeWidth = sw)
                    drawLine(brandRed, androidx.compose.ui.geometry.Offset(0f, bh), androidx.compose.ui.geometry.Offset(0f, bh - len), strokeWidth = sw)

                    // Bottom-Right
                    drawLine(brandRed, androidx.compose.ui.geometry.Offset(bw, bh), androidx.compose.ui.geometry.Offset(bw - len, bh), strokeWidth = sw)
                    drawLine(brandRed, androidx.compose.ui.geometry.Offset(bw, bh), androidx.compose.ui.geometry.Offset(bw, bh - len), strokeWidth = sw)

                    // Scanning Horizontal Laser Beam across center
                    drawLine(
                        brandRed.copy(alpha = 0.85f),
                        androidx.compose.ui.geometry.Offset(0f, bh * 0.45f),
                        androidx.compose.ui.geometry.Offset(bw, bh * 0.45f),
                        strokeWidth = 2.5f
                    )
                }
            }
        }
    }
}
