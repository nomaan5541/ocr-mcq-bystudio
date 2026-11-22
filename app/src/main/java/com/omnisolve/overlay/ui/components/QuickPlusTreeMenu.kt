package com.omnisolve.overlay.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.api.PrismUniversalGeminiEngine
import com.omnisolve.overlay.api.UniversalAttachmentType
import com.omnisolve.overlay.api.UniversalChatMessage
import com.omnisolve.overlay.data.DailyUsageSnapshot
import com.omnisolve.overlay.data.UsageLimitFeature
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.liquidPressScale
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import kotlinx.coroutines.delay

/**
 * Studio-crafted Radial Bloom Action Deck rising organically from the center crimson `+` button.
 *
 * Replaces stiff binary-tree stick lines with true spring-physics radial emergence from the `+`
 * anchor, subtle concentric orbital guide arcs, and 5 everyday student actions:
 * - Form Link (Google Forms + Instant Class Cache)
 * - Camera Snap (zero-permission system camera, auto-shrunk under 2 MB)
 * - Photo (Gallery · Max 2 MB)
 * - PDF Paper (Document · Max 3 MB · 12 pages)
 * - Floating Pill quick start / stop strip (Free unlimited on-device OCR & Browser)
 */
@Composable
fun QuickPlusTreeOverlay(
    visible: Boolean,
    dailyUsage: DailyUsageSnapshot,
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickPdf: () -> Unit,
    onOpenFormLink: () -> Unit,
    onPickCamera: () -> Unit = {},
    onToggleFloatingPill: () -> Unit = {},
    isOverlayRunning: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current

    if (visible) {
        BackHandler(onBack = onDismiss)
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) fadeIn(tween(1)) else fadeIn(tween(190)),
        exit = if (reducedMotion) fadeOut(tween(1)) else fadeOut(tween(150)),
        modifier = modifier.fillMaxSize()
    ) {
        val ringProgress = remember { Animatable(0f) }
        val headerProgress = remember { Animatable(0f) }
        val tile0Progress = remember { Animatable(0f) } // Form Link (top-left)
        val tile1Progress = remember { Animatable(0f) } // Camera (top-right)
        val tile2Progress = remember { Animatable(0f) } // Photo (mid-left)
        val tile3Progress = remember { Animatable(0f) } // PDF (mid-right)
        val stripProgress = remember { Animatable(0f) } // Floating Pill strip (bottom-center)

        LaunchedEffect(visible) {
            if (visible) {
                if (reducedMotion) {
                    ringProgress.snapTo(1f)
                    headerProgress.snapTo(1f)
                    tile0Progress.snapTo(1f)
                    tile1Progress.snapTo(1f)
                    tile2Progress.snapTo(1f)
                    tile3Progress.snapTo(1f)
                    stripProgress.snapTo(1f)
                } else {
                    ringProgress.snapTo(0f)
                    headerProgress.snapTo(0f)
                    tile0Progress.snapTo(0f)
                    tile1Progress.snapTo(0f)
                    tile2Progress.snapTo(0f)
                    tile3Progress.snapTo(0f)
                    stripProgress.snapTo(0f)
                    ringProgress.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
                }
            }
        }

        val bloomSpring = remember {
            spring<Float>(dampingRatio = 0.74f, stiffness = 460f)
        }

        LaunchedEffect(visible) {
            if (visible && !reducedMotion) {
                delay(25)
                stripProgress.animateTo(1f, bloomSpring)
            }
        }
        LaunchedEffect(visible) {
            if (visible && !reducedMotion) {
                delay(60)
                tile2Progress.animateTo(1f, bloomSpring)
            }
        }
        LaunchedEffect(visible) {
            if (visible && !reducedMotion) {
                delay(95)
                tile3Progress.animateTo(1f, bloomSpring)
            }
        }
        LaunchedEffect(visible) {
            if (visible && !reducedMotion) {
                delay(125)
                tile0Progress.animateTo(1f, bloomSpring)
            }
        }
        LaunchedEffect(visible) {
            if (visible && !reducedMotion) {
                delay(160)
                tile1Progress.animateTo(1f, bloomSpring)
            }
        }
        LaunchedEffect(visible) {
            if (visible && !reducedMotion) {
                delay(175)
                headerProgress.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            }
        }

        val photoLeft = dailyUsage.remainingFor(UsageLimitFeature.ASK_AI_PHOTO)
        val photoMax = UsageLimitFeature.ASK_AI_PHOTO.dailyLimit
        val pdfLeft = dailyUsage.remainingFor(UsageLimitFeature.ASK_AI_PDF)
        val pdfMax = UsageLimitFeature.ASK_AI_PDF.dailyLimit
        val formsLeft = dailyUsage.remainingFor(UsageLimitFeature.GOOGLE_FORMS)
        val formsMax = UsageLimitFeature.GOOGLE_FORMS.dailyLimit

        val scrimInteraction = remember { MutableInteractionSource() }
        val accentCrimson = palette.accentSky
        val accentEmerald = palette.accentEmerald

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF0A0A0C).copy(alpha = 0.76f),
                        0.62f to Color(0xFF0A0A0C).copy(alpha = 0.88f),
                        1f to Color(0xFF14090B).copy(alpha = 0.95f)
                    )
                )
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss
                )
                .testTag("plus_tree_scrim"),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Subtle precision orbital horizon arcs radiating from the center `+` button
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .align(Alignment.BottomCenter)
            ) {
                val p = ringProgress.value.coerceIn(0f, 1f)
                if (p > 0.01f) {
                    val cx = size.width * 0.5f
                    val cy = size.height - 54.dp.toPx()

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentCrimson.copy(alpha = 0.16f * p),
                                accentCrimson.copy(alpha = 0.04f * p),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = (size.width * 0.52f).coerceAtLeast(160f) * p
                        ),
                        radius = (size.width * 0.52f).coerceAtLeast(160f) * p,
                        center = Offset(cx, cy)
                    )

                    val innerR = 112.dp.toPx() * (0.65f + 0.35f * p)
                    val outerR = 188.dp.toPx() * (0.65f + 0.35f * p)
                    drawCircle(
                        color = Color(0xFFEEEEEE).copy(alpha = 0.08f * p),
                        radius = innerR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = accentCrimson.copy(alpha = 0.14f * p),
                        radius = outerR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // Sculpted Radial Action Deck anchored cleanly above the bottom dock
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, bottom = 92.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quiet editorial header capsule
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            val p = headerProgress.value.coerceIn(0f, 1f)
                            alpha = p
                            translationY = (1f - p) * 18f
                        }
                        .clip(CircleShape)
                        .background(Color(0xFF141519))
                        .border(1.dp, palette.cardHairlineBorder, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentCrimson)
                    )
                    Text(
                        text = "QUICK SOLVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.1.sp,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = "·",
                        color = palette.inkSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Pick any option",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.inkSecondary,
                            fontSize = 10.5.sp
                        )
                    )
                }

                // Tier 1 Row: Google Forms (Hero) + Camera Snap (Auto <2 MB)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                val p = tile0Progress.value
                                alpha = p.coerceIn(0f, 1f)
                                val s = 0.62f + (0.38f * p)
                                scaleX = s
                                scaleY = s
                                translationX = (1f - p) * 68f
                                translationY = (1f - p) * 168f
                                rotationZ = (1f - p) * -4.5f
                            }
                    ) {
                        QuickBloomActionTile(
                            icon = Icons.Filled.Link,
                            title = "Form Link",
                            subtitle = "Google Forms",
                            badgeText = if (formsLeft > 0) "$formsLeft/$formsMax · Class Free" else "Class Cache Free",
                            accentTint = accentEmerald,
                            highlightBorder = true,
                            testTag = "btn_plus_tree_link",
                            onClick = {
                                onDismiss()
                                onOpenFormLink()
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                val p = tile1Progress.value
                                alpha = p.coerceIn(0f, 1f)
                                val s = 0.62f + (0.38f * p)
                                scaleX = s
                                scaleY = s
                                translationX = -(1f - p) * 68f
                                translationY = (1f - p) * 168f
                                rotationZ = (1f - p) * 4.5f
                            }
                    ) {
                        QuickBloomActionTile(
                            icon = Icons.Filled.CameraAlt,
                            title = "Camera",
                            subtitle = "Snap · Auto <2 MB",
                            badgeText = "$photoLeft/$photoMax left",
                            accentTint = accentCrimson,
                            highlightBorder = false,
                            testTag = "btn_plus_tree_camera",
                            onClick = {
                                onDismiss()
                                onPickCamera()
                            }
                        )
                    }
                }

                // Tier 2 Row: Gallery Photo (Max 2 MB) + PDF Paper (Max 3 MB)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                val p = tile2Progress.value
                                alpha = p.coerceIn(0f, 1f)
                                val s = 0.62f + (0.38f * p)
                                scaleX = s
                                scaleY = s
                                translationX = (1f - p) * 58f
                                translationY = (1f - p) * 96f
                                rotationZ = (1f - p) * -3.5f
                            }
                    ) {
                        QuickBloomActionTile(
                            icon = Icons.Filled.PhotoLibrary,
                            title = "Photo",
                            subtitle = "Gallery · Max 2 MB",
                            badgeText = "$photoLeft/$photoMax left",
                            accentTint = Color(0xFFEEEEEE),
                            highlightBorder = false,
                            testTag = "btn_plus_tree_photo",
                            onClick = {
                                onDismiss()
                                onPickPhoto()
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                val p = tile3Progress.value
                                alpha = p.coerceIn(0f, 1f)
                                val s = 0.62f + (0.38f * p)
                                scaleX = s
                                scaleY = s
                                translationX = -(1f - p) * 58f
                                translationY = (1f - p) * 96f
                                rotationZ = (1f - p) * 3.5f
                            }
                    ) {
                        QuickBloomActionTile(
                            icon = Icons.Filled.PictureAsPdf,
                            title = "PDF",
                            subtitle = "Max 3 MB · 12 pages",
                            badgeText = "$pdfLeft/$pdfMax left",
                            accentTint = Color(0xFFFF8A80),
                            highlightBorder = false,
                            testTag = "btn_plus_tree_pdf",
                            onClick = {
                                onDismiss()
                                onPickPdf()
                            }
                        )
                    }
                }

                // Tier 3 Full-Width Capsule: 1-Tap Floating Pill Toggle (Free On-Device OCR + Local Web)
                val pillInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val p = stripProgress.value
                            alpha = p.coerceIn(0f, 1f)
                            val s = 0.72f + (0.28f * p)
                            scaleX = s
                            scaleY = s
                            translationY = (1f - p) * 44f
                        }
                        .liquidPressScale(pillInteraction, pressedScale = 0.97f)
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isOverlayRunning) Color(0xFF10241A) else Color(0xFF141519)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isOverlayRunning) accentEmerald.copy(alpha = 0.6f) else palette.cardHairlineBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable(
                            interactionSource = pillInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                onDismiss()
                                onToggleFloatingPill()
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("btn_plus_tree_floating_pill"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isOverlayRunning) accentEmerald.copy(alpha = 0.16f)
                                    else accentCrimson.copy(alpha = 0.16f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = "Floating Pill",
                                tint = if (isOverlayRunning) accentEmerald else accentCrimson,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = if (isOverlayRunning) "Floating Pill is Live" else "Start Floating Pill",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = if (isOverlayRunning) "Tap to pause floating overlay"
                                else "On-device OCR & Browser · Free Unlimited",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 10.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isOverlayRunning) accentEmerald.copy(alpha = 0.18f)
                                else accentCrimson
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isOverlayRunning) "Stop" else "Start",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isOverlayRunning) accentEmerald else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickBloomActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String,
    accentTint: Color,
    highlightBorder: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp)
            .liquidPressScale(interaction, pressedScale = 0.95f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF131418))
            .border(
                width = 1.dp,
                color = if (highlightBorder) accentTint.copy(alpha = 0.45f) else palette.cardHairlineBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 13.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accentTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (highlightBorder) accentTint else palette.inkMuted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Clean, dark-only slide-up Quick Solve sheet displayed right over Home when a user picks a Photo
 * or PDF from the `+` tree (or shares a Photo/PDF from another app). Eliminates the need for a
 * separate cluttered Scan tab while keeping Photo & PDF solving fast and effortless.
 */
@Composable
fun QuickSolveResultSheet(
    visible: Boolean,
    state: MainUiState,
    onDismiss: () -> Unit,
    onUpdatePrompt: (String) -> Unit,
    onSendFollowUp: () -> Unit,
    onCancelSolve: () -> Unit,
    onStartFresh: () -> Unit,
    onShowBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    if (visible) {
        BackHandler(onBack = onDismiss)
    }

    LaunchedEffect(state.universalMessages.size, state.universalMessages.lastOrNull()?.text?.length) {
        if (visible && state.universalMessages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(state.universalMessages.lastIndex) }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) fadeIn(tween(1)) else slideInVertically(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 2 }
        ) + fadeIn(tween(200)),
        exit = if (reducedMotion) fadeOut(tween(1)) else slideOutVertically(
            animationSpec = tween(210, easing = FastOutSlowInEasing),
            targetOffsetY = { it / 2 }
        ) + fadeOut(tween(160)),
        modifier = modifier.fillMaxSize()
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0C).copy(alpha = 0.72f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            val sheetInteraction = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .heightIn(min = 320.dp, max = 640.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF101114))
                    .border(
                        width = 1.dp,
                        color = palette.cardHairlineBorder,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .clickable(
                        interactionSource = sheetInteraction,
                        indication = null,
                        onClick = {}
                    )
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("quick_solve_result_sheet")
            ) {
                // Drag indicator pill
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(palette.inkMuted.copy(alpha = 0.55f))
                )
                Spacer(Modifier.height(10.dp))

                // Sheet Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Quick Solve",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        )
                        val staged = state.universalStagedAttachment
                        val subtitleText = when {
                            state.isUniversalSolving -> "Reading and solving your file..."
                            staged != null -> {
                                val sizeStr = PrismUniversalGeminiEngine.formatByteSize(staged.sizeBytes)
                                listOf(staged.displayName, sizeStr).filter { it.isNotBlank() }.joinToString(" · ")
                            }
                            else -> "Photos up to 2 MB · PDFs up to 3 MB"
                        }
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.universalMessages.isNotEmpty() && !state.isUniversalSolving) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1A1B20))
                                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
                                    .clickable(role = Role.Button, onClick = onStartFresh)
                                    .padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.inkSecondary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A1B20))
                                .border(1.dp, palette.cardHairlineBorder, CircleShape)
                                .clickable(role = Role.Button, onClick = onDismiss)
                                .testTag("btn_quick_solve_close")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = palette.inkPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = palette.cardHairlineBorder.copy(alpha = 0.65f))
                Spacer(Modifier.height(10.dp))

                // Conversation / Answer Stream
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 410.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.universalMessages.isEmpty()) {
                        item(key = "quick_solve_empty") {
                            Text(
                                text = "Your answer will appear here right after you pick a Photo or PDF.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                ),
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    } else {
                        items(state.universalMessages, key = { it.id }) { msg ->
                            QuickSolveBubble(
                                message = msg,
                                onCopy = { text ->
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Prism Answer", text))
                                    onShowBanner("Answer copied")
                                },
                                onShare = { text ->
                                    runCatching {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share answer"))
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Follow-up input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF17181D))
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = state.universalPromptInput,
                        onValueChange = onUpdatePrompt,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = palette.inkPrimary,
                            fontSize = 13.5.sp
                        ),
                        cursorBrush = SolidColor(palette.accentSky),
                        decorationBox = { inner ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (state.universalPromptInput.isBlank()) {
                                    Text(
                                        text = "Ask a follow-up question...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = palette.inkMuted,
                                            fontSize = 13.5.sp
                                        )
                                    )
                                }
                                inner()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                    )

                    if (state.isUniversalSolving) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(palette.accentSkyContainer)
                                .clickable(role = Role.Button, onClick = onCancelSolve)
                                .testTag("btn_quick_solve_stop")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Stop",
                                tint = palette.accentSky,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        val canSend = state.universalPromptInput.isNotBlank() || state.universalStagedAttachment != null
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (canSend) palette.accentSky else Color(0xFF22242B))
                                .clickable(enabled = canSend, role = Role.Button, onClick = onSendFollowUp)
                                .testTag("btn_quick_solve_send")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (canSend) Color(0xFFEEEEEE) else palette.inkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSolveBubble(
    message: UniversalChatMessage,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    if (message.isUser) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF17181E))
                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!message.attachmentName.isNullOrBlank()) {
                val badgeLabel = if (message.attachmentType == UniversalAttachmentType.PDF) "PDF" else "PHOTO"
                Text(
                    text = "$badgeLabel · ${message.attachmentName}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.accentSky,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = palette.inkPrimary,
                    fontSize = 13.5.sp
                )
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (message.isError) Color(0xFF241214) else Color(0xFF13151B))
                .border(
                    1.dp,
                    if (message.isError) palette.accentSky.copy(alpha = 0.55f) else palette.cardHairlineBorder,
                    RoundedCornerShape(14.dp)
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (message.isStreaming && message.text.isBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = palette.accentSky
                    )
                    Text(
                        text = "Finding the best answer...",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.inkSecondary,
                            fontSize = 12.5.sp
                        )
                    )
                }
            } else {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = palette.inkPrimary,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    )
                )
            }

            if (!message.isStreaming && !message.isError && message.text.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1C1E26))
                            .clickable(role = Role.Button) { onCopy(message.text) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy answer",
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Copy",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1C1E26))
                            .clickable(role = Role.Button) { onShare(message.text) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share answer",
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Share",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
