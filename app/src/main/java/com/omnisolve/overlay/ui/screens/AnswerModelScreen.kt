package com.omnisolve.overlay.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.data.UsageLimitFeature
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

@Composable
fun AnswerModelScreen(
    state: MainUiState,
    onBackToHome: () -> Unit,
    onSelectSolverMethod: (SolverMethod) -> Unit,
    onOpenSettings: () -> Unit,
    onSignInServer: () -> Unit,
    onOpenGeminiLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = themeSpec.cornerRadiusDp.coerceIn(14, 24).dp
    val listState = rememberLazyListState()
    val isServerSignedIn = !state.firebaseUserId.isNullOrBlank() || state.firebaseUserEmail != null
    val serverQuotaLimit = UsageLimitFeature.OVERLAY_MCQ.dailyLimit
    val serverQuotaRemaining = state.dailyUsage.remainingFor(UsageLimitFeature.OVERLAY_MCQ)
    val serverQuotaPill = if (isServerSignedIn) {
        "$serverQuotaRemaining/$serverQuotaLimit left"
    } else {
        "$serverQuotaLimit/day limit"
    }
    var showMoreOptions by rememberSaveable {
        mutableStateOf(state.solverMethod in SolverMethod.advancedModes)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("answer_model_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1. ACTIVE STYLE SUMMARY ──
        item(key = "model_rating_overview") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.accentSky.copy(alpha = 0.45f), RoundedCornerShape(cardCorner))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accentSkyContainer)
                                .border(1.dp, palette.accentSky.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CenterFocusStrong,
                                contentDescription = null,
                                tint = palette.accentSky,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Active: ${state.solverMethod.shortName}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.inkPrimary
                                )
                            )
                            Text(
                                text = if (state.solverMethod.usesCloudServerQuota) {
                                    "Cloud server mode ($serverQuotaRemaining/$serverQuotaLimit daily solves left) • Auto-switches to OCR + Phone Gemini (Unlimited) after $serverQuotaLimit solves."
                                } else {
                                    "Unlimited mode (zero Prism server quota used) • On-device OCR & Offline Room Cache active."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = palette.inkSecondary
                                )
                            )
                        }
                    }

                    RatingScoreBadge(
                        score = state.solverMethod.ratingScore,
                        isHighRated = state.solverMethod.ratingStars >= 4
                    )
                }

                if (state.solverMethod != SolverMethod.BACKGROUND_GEMINI) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.canvasBottom)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Want quick replies anytime?",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.accentSky)
                                .clickable(role = Role.Button) {
                                    onSelectSolverMethod(SolverMethod.BACKGROUND_GEMINI)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("btn_reset_ocr_default")
                        ) {
                            Text(
                                text = "Use Fast Default",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.onAccentInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        if (!isServerSignedIn) {
            item(key = "ocr_only_limited_signin_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cardCorner))
                        .background(palette.glassStrong)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.65f), RoundedCornerShape(cardCorner))
                        .clickable(role = Role.Button, onClick = onSignInServer)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("card_models_ocr_limited_signin"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    ) {
                        Text(
                            text = "Sign in to start solving",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Sign in with Google for cloud server modes ($serverQuotaLimit/day) & sync, or choose OCR + Phone Gemini (Unlimited • No Server) below.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.accentSky)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign In",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.onAccentInk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // ── 2. EVERYDAY TOP 3 PICKS ──
        item(key = "everyday_picks_header") {
            Text(
                text = "Simple Picks",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = palette.inkSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // 1. Smart Auto (Fast + Auto-Math Vision + Offline Cache) — Cloud Server Limit
        item(key = "method_ocr_default") {
            RatedSolverMethodCard(
                number = "1.",
                title = "Smart Auto (Fast + Math)",
                subtitle = "Checks offline cache first, solves MCQs on Prism cloud servers ($serverQuotaLimit/day limit), and upgrades to Vision for ½, Σ, ∫.",
                icon = Icons.Filled.CenterFocusStrong,
                topRecommendedTag = "RECOMMENDED • AUTO-MATH & CACHE",
                inlineSpecText = serverQuotaPill,
                ratingScore = SolverMethod.BACKGROUND_GEMINI.ratingScore,
                ratingStars = SolverMethod.BACKGROUND_GEMINI.ratingStars,
                ratingLabel = "${SolverMethod.BACKGROUND_GEMINI.ratingLabel} • Daily Limit",
                isHighRated = true,
                isSelected = state.solverMethod == SolverMethod.BACKGROUND_GEMINI,
                isLocked = false,
                onClick = { onSelectSolverMethod(SolverMethod.BACKGROUND_GEMINI) },
                testTag = "option_method_background_gemini"
            )
        }

        // 2. OCR + Phone Gemini (Zero Server • Unlimited)
        item(key = "method_phone_gemini_ocr") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RatedSolverMethodCard(
                    number = "2.",
                    title = "OCR + Phone Gemini (No Server)",
                    subtitle = "On-device ML Kit OCR + your phone's Gemini web session. Uses zero cloud servers — 100% unlimited with no daily limit.",
                    icon = Icons.Filled.CastConnected,
                    topRecommendedTag = "UNLIMITED • NO SERVER LIMIT",
                    inlineSpecText = "Unlimited",
                    ratingScore = SolverMethod.OVERLAPPING_GOOGLE.ratingScore,
                    ratingStars = SolverMethod.OVERLAPPING_GOOGLE.ratingStars,
                    ratingLabel = SolverMethod.OVERLAPPING_GOOGLE.ratingLabel,
                    isHighRated = true,
                    isSelected = state.solverMethod == SolverMethod.OVERLAPPING_GOOGLE,
                    isLocked = false,
                    onClick = { onSelectSolverMethod(SolverMethod.OVERLAPPING_GOOGLE) },
                    testTag = "option_method_overlapping_google"
                )
                if (state.solverMethod == SolverMethod.OVERLAPPING_GOOGLE) {
                    ModelHelperActionRow(
                        label = "Connect Phone Gemini Session (gemini.google.com)",
                        icon = Icons.Filled.CastConnected,
                        onClick = onOpenGeminiLogin,
                        testTag = "btn_home_connect_gemini_session"
                    )
                }
            }
        }

        // 3. Double-Check Accuracy — Cloud Server Limit
        item(key = "method_all_in_one") {
            RatedSolverMethodCard(
                number = "3.",
                title = "Double-Check Accuracy",
                subtitle = "Cross-checks multiple cloud AI engines together and verifies tough MCQs and Match tables before replying ($serverQuotaLimit/day limit).",
                icon = Icons.Filled.Bolt,
                topRecommendedTag = "MOST ACCURATE • DOUBLE-CHECKED",
                inlineSpecText = serverQuotaPill,
                ratingScore = SolverMethod.TURBO_HYBRID.ratingScore,
                ratingStars = SolverMethod.TURBO_HYBRID.ratingStars,
                ratingLabel = "${SolverMethod.TURBO_HYBRID.ratingLabel} • Daily Limit",
                isHighRated = true,
                isSelected = state.solverMethod == SolverMethod.TURBO_HYBRID,
                isLocked = false,
                onClick = { onSelectSolverMethod(SolverMethod.TURBO_HYBRID) },
                testTag = "option_method_turbo_hybrid"
            )
        }

        // 4. Photo & Math Vision — Cloud Server Limit
        item(key = "method_direct_vision") {
            RatedSolverMethodCard(
                number = "4.",
                title = "Photo & Math Vision",
                subtitle = "Reads the screen image directly on cloud vision servers for equations, geometry figures, and charts ($serverQuotaLimit/day limit).",
                icon = Icons.Filled.RemoveRedEye,
                inlineSpecText = serverQuotaPill,
                ratingScore = SolverMethod.DIRECT_VISION.ratingScore,
                ratingStars = SolverMethod.DIRECT_VISION.ratingStars,
                ratingLabel = "${SolverMethod.DIRECT_VISION.ratingLabel} • Daily Limit",
                isHighRated = true,
                isSelected = state.solverMethod == SolverMethod.DIRECT_VISION,
                isLocked = false,
                onClick = { onSelectSolverMethod(SolverMethod.DIRECT_VISION) },
                testTag = "option_method_direct_vision"
            )
        }

        // ── 3. COLLAPSIBLE MORE OPTIONS (ADVANCED) ──
        item(key = "more_options_toggle") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { showMoreOptions = !showMoreOptions }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("btn_toggle_more_solver_options"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = palette.accentSky,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "More Options (Advanced)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                        Text(
                            text = "Cloud Solver, Web Lookup & Custom Keys",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = if (showMoreOptions) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (showMoreOptions) "Hide more options" else "Show more options",
                    tint = palette.inkPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (showMoreOptions) {
            item(key = "method_server_sol") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RatedSolverMethodCard(
                        number = "5.",
                        title = "Cloud Solver",
                        subtitle = "Fast cloud answers with automatic backup ($serverQuotaLimit/day server limit).",
                        icon = Icons.Filled.Cloud,
                        inlineSpecText = serverQuotaPill,
                        ratingScore = SolverMethod.FIREBASE_SERVER.ratingScore,
                        ratingStars = SolverMethod.FIREBASE_SERVER.ratingStars,
                        ratingLabel = "${SolverMethod.FIREBASE_SERVER.ratingLabel} • Daily Limit",
                        isHighRated = SolverMethod.FIREBASE_SERVER.ratingStars >= 4,
                        isSelected = state.solverMethod == SolverMethod.FIREBASE_SERVER,
                        isLocked = false,
                        onClick = { onSelectSolverMethod(SolverMethod.FIREBASE_SERVER) },
                        testTag = "option_method_firebase"
                    )

                    GoogleServerLoginPillRow(
                        isSignedIn = isServerSignedIn,
                        userEmail = state.firebaseUserEmail,
                        onClick = onSignInServer,
                        testTag = "btn_home_google_signin"
                    )
                }
            }

            item(key = "method_circle_to_search") {
                RatedSolverMethodCard(
                    number = "6.",
                    title = "Web Facts Lookup",
                    subtitle = "Checks live web facts alongside cloud AI. Helpful for general knowledge and trivia ($serverQuotaLimit/day limit).",
                    icon = Icons.Filled.Radar,
                    inlineSpecText = serverQuotaPill,
                    ratingScore = SolverMethod.CIRCLE_TO_SEARCH.ratingScore,
                    ratingStars = SolverMethod.CIRCLE_TO_SEARCH.ratingStars,
                    ratingLabel = "${SolverMethod.CIRCLE_TO_SEARCH.ratingLabel} • Daily Limit",
                    isHighRated = SolverMethod.CIRCLE_TO_SEARCH.ratingStars >= 4,
                    isSelected = state.solverMethod == SolverMethod.CIRCLE_TO_SEARCH,
                    isLocked = false,
                    onClick = { onSelectSolverMethod(SolverMethod.CIRCLE_TO_SEARCH) },
                    testTag = "option_method_circle_to_search"
                )
            }

            item(key = "method_ghost_dom") {
                RatedSolverMethodCard(
                    number = "7.",
                    title = "Direct App Text Reader",
                    subtitle = "Reads visible question text directly from supported quiz apps and solves on cloud servers ($serverQuotaLimit/day limit).",
                    icon = Icons.Filled.AccountTree,
                    inlineSpecText = serverQuotaPill,
                    ratingScore = SolverMethod.GHOST_DOM.ratingScore,
                    ratingStars = SolverMethod.GHOST_DOM.ratingStars,
                    ratingLabel = "${SolverMethod.GHOST_DOM.ratingLabel} • Daily Limit",
                    isHighRated = SolverMethod.GHOST_DOM.ratingStars >= 4,
                    isSelected = state.solverMethod == SolverMethod.GHOST_DOM,
                    isLocked = false,
                    onClick = { onSelectSolverMethod(SolverMethod.GHOST_DOM) },
                    testTag = "option_method_ghost_dom"
                )
            }

            item(key = "method_api_keys") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RatedSolverMethodCard(
                        number = "8.",
                        title = "Your Own API Keys",
                        subtitle = "Uses personal developer keys you added in Settings (no Prism server quota used).",
                        icon = Icons.Filled.Key,
                        inlineSpecText = "${state.configuredKeyCount} Keys • Unlimited",
                        ratingScore = SolverMethod.API_KEYS.ratingScore,
                        ratingStars = SolverMethod.API_KEYS.ratingStars,
                        ratingLabel = SolverMethod.API_KEYS.ratingLabel,
                        isHighRated = SolverMethod.API_KEYS.ratingStars >= 4,
                        isSelected = state.solverMethod == SolverMethod.API_KEYS,
                        isLocked = false,
                        onClick = { onSelectSolverMethod(SolverMethod.API_KEYS) },
                        testTag = "btn_home_api_keys_card"
                    )
                    Box(
                        modifier = Modifier
                            .size(1.dp)
                            .clickable { onSelectSolverMethod(SolverMethod.API_KEYS) }
                            .testTag("option_method_api_keys")
                    )
                    if (state.solverMethod == SolverMethod.API_KEYS && state.configuredKeyCount == 0) {
                        ModelHelperActionRow(
                            label = "Add Your Own Keys in Settings",
                            icon = Icons.Filled.Key,
                            onClick = onOpenSettings,
                            testTag = "btn_home_open_api_keys"
                        )
                    }
                }
            }
        }

        // Back CTA
        item(key = "method_footer_actions") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.glassMedium)
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onBackToHome)
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                        .testTag("btn_answer_models_done"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = palette.inkPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Back to Home Screen",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = palette.inkPrimary,
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
fun RatingScoreBadge(
    score: String,
    isHighRated: Boolean
) {
    val palette = LocalWhiteGlassPalette.current
    val bg = if (isHighRated) palette.accentEmeraldContainer else palette.warningContainer
    val border = if (isHighRated) palette.accentEmerald.copy(alpha = 0.65f) else palette.warningAmber.copy(alpha = 0.55f)
    val textCol = if (isHighRated) palette.accentEmerald else palette.warningAmber

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = if (isHighRated) palette.accentAmber else palette.warningAmber,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = "$score ★",
            style = MaterialTheme.typography.labelSmall.copy(
                color = textCol,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun RatedSolverMethodCard(
    number: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    inlineSpecText: String? = null,
    topRecommendedTag: String? = null,
    ratingScore: String,
    ratingStars: Int,
    ratingLabel: String,
    isHighRated: Boolean,
    isSelected: Boolean,
    isLocked: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = themeSpec.cornerRadiusDp.coerceIn(12, 24).dp

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> palette.accentSky
            isHighRated -> palette.accentEmerald.copy(alpha = 0.45f)
            else -> palette.cardHairlineBorder
        },
        animationSpec = spring(stiffness = 600f),
        label = "ratedMethodBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(top = if (topRecommendedTag != null) 10.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(RoundedCornerShape(cardCorner))
                .background(if (isSelected) palette.glassTintedSky else palette.glassStrong)
                .border(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(cardCorner)
                )
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .testTag(testTag),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Icon Container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (isSelected) palette.accentSkyContainer else palette.glassSubtle)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) palette.accentSky.copy(alpha = 0.65f) else palette.cardHairlineBorder,
                            shape = RoundedCornerShape(13.dp)
                        )
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) palette.accentSky else palette.inkPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Title + Spec Pill + Description
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$number $title",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        val badgeLabel = if (isLocked) "SIGN IN" else inlineSpecText
                        if (badgeLabel != null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            isLocked -> palette.warningContainer
                                            isSelected -> palette.accentSky
                                            else -> palette.glassSubtle
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = when {
                                            isLocked -> palette.warningAmber
                                            isSelected -> palette.onAccentInk
                                            else -> palette.inkPrimary
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.2.sp,
                            lineHeight = 16.5.sp,
                            color = palette.inkSecondary
                        )
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Selection Indicator
                if (isSelected) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(palette.accentSky)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = palette.onAccentInk,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(palette.glassSubtle)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = palette.inkSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Bottom Rating Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isHighRated) palette.accentEmeraldContainer else palette.warningContainer)
                    .border(
                        width = 1.dp,
                        color = if (isHighRated) palette.accentEmerald.copy(alpha = 0.4f) else palette.warningAmber.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    for (starIdx in 1..5) {
                        val filled = starIdx <= ratingStars
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = when {
                                !filled -> palette.rimHighlight
                                isHighRated -> palette.accentAmber
                                else -> palette.warningAmber
                            },
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "$ratingScore / 5.0",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isHighRated) palette.accentEmerald else palette.warningAmber,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }

                Text(
                    text = ratingLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isHighRated) palette.accentEmerald else palette.inkSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        // Top Floating Recommended Tag for OCR Default
        if (topRecommendedTag != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .offset(x = 16.dp, y = (-10).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.accentSky)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = palette.onAccentInk,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = topRecommendedTag,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.onAccentInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun GoogleServerLoginPillRow(
    isSignedIn: Boolean,
    userEmail: String?,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalWhiteGlassPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.glassMedium)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val stroke = 3.8.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                drawArc(
                    color = Color(0xFFEA4335),
                    startAngle = -145f,
                    sweepAngle = 105f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                drawArc(
                    color = Color(0xFFFBBC05),
                    startAngle = 140f,
                    sweepAngle = 75f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                drawArc(
                    color = Color(0xFF34A853),
                    startAngle = 45f,
                    sweepAngle = 95f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                drawArc(
                    color = Color(0xFF4285F4),
                    startAngle = -15f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                drawLine(
                    color = Color(0xFF4285F4),
                    start = Offset(size.width * 0.52f, size.height * 0.5f),
                    end = Offset(size.width * 0.92f, size.height * 0.5f),
                    strokeWidth = stroke * 0.85f
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = if (isSignedIn) {
                    "Google Account Connected (${userEmail ?: "Signed in"})"
                } else {
                    "Sign in with Google"
                },
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.inkPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = palette.inkSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ModelHelperActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalWhiteGlassPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.glassMedium)
            .border(1.dp, palette.accentSky.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.accentSky,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.inkPrimary
                )
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = palette.inkSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
