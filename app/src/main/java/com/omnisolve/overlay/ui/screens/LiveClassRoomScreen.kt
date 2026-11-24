package com.omnisolve.overlay.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import com.omnisolve.overlay.util.RoomQrHelper
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.omnisolve.overlay.GoogleFormsWebActivity
import com.omnisolve.overlay.data.LiveClassRoom
import com.omnisolve.overlay.data.LiveClassRoomRepository
import com.omnisolve.overlay.data.LiveRoomAnswer
import com.omnisolve.overlay.data.LiveRoomMember
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.physics.LocalReducedMotion

// Discord-inspired deep mobile surfaces fused with Prism's dark brand palette
private val DiscordCanvas = Color(0xFF0A0A0C)
private val DiscordPanel = Color(0xFF111214)
private val DiscordCard = Color(0xFF1A1B1F)
private val DiscordElevated = Color(0xFF23252C)
private val DiscordHairline = Color(0xFF2B2D36)
private val DiscordInkPrimary = Color(0xFFEEEEEE)
private val DiscordInkMuted = Color(0xFF949BA4)
private val DiscordOnlineGreen = Color(0xFF23A55A)
private val PrismCrimson = Color(0xFFD71921)
private val PrismAmber = Color(0xFFF59E0B)

private enum class RoomChannelTab(val channelLabel: String, val tag: String) {
    LIVE_EXAM("live-exam", "tab_room_channel_exam"),
    MEMBERS("members", "tab_room_channel_members"),
    CONTROLS("controls", "tab_room_channel_controls")
}

/**
 * Prism Live Class Rooms — Mobile Discord-Style Class Sync (Max 40 Members).
 *
 * Key guarantees:
 * - Max 40 members hard-enforced.
 * - Host-exclusive control: ONLY the creator can approve/decline/remove members, set/change
 *   the locked quiz URL, and broadcast live answers simultaneously to all approved classmates.
 * - Strict privacy: member rows show ONLY circular profile photo + display name (+ HOST tag);
 *   profiles are strictly non-clickable/unviewable and zero email/phone info is ever shown.
 * - Shuffled-option-proof answers: short/1-word answers show the exact word directly; lengthy
 *   answers auto-locate & highlight the matching option inside each member's Quiz Browser.
 */
@Composable
fun LiveClassRoomScreen(
    state: MainUiState,
    onBackToHome: () -> Unit,
    onSignInGoogle: () -> Unit,
    onCreateRoom: (roomName: String, subject: SubjectMode, initialQuizUrl: String) -> Unit,
    onJoinRoom: (roomCode: String) -> Unit,
    onApproveMember: (targetUid: String) -> Unit = {},
    onRejectOrRemoveMember: (targetUid: String, wasApproved: Boolean) -> Unit = { _, _ -> },
    onUpdateLockedQuizUrl: (rawUrl: String) -> Unit = {},
    onLeaveRoom: () -> Unit,
    onEndRoom: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onVoteConfirm: (String) -> Unit,
    onVoteDispute: (String) -> Unit,
    onAiVerifyAnswer: (LiveRoomAnswer) -> Unit,
    onShowBannerMessage: (String) -> Unit,
    showBackButton: Boolean = true,
    applyStatusBarPadding: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current
    val isSignedIn = !state.firebaseUserId.isNullOrBlank()

    if (showBackButton) {
        BackHandler(onBack = onBackToHome)
    }

    var joinCodeInput by rememberSaveable { mutableStateOf("") }
    var createNameInput by rememberSaveable { mutableStateOf("") }
    var createUrlInput by rememberSaveable { mutableStateOf(state.googleFormUrlInput) }
    var createSubject by rememberSaveable { mutableStateOf(state.subjectMode.id) }
    var activeChannel by rememberSaveable { mutableStateOf(RoomChannelTab.LIVE_EXAM.name) }
    var expandedAnswerId by remember { mutableStateOf<String?>(null) }
    var showQrInviteDialog by rememberSaveable { mutableStateOf(false) }

    val activeRoom = state.activeLiveClassRoom
    val isHost = activeRoom != null && !state.firebaseUserId.isNullOrBlank() && activeRoom.hostUid == state.firebaseUserId
    val isApprovedMember = activeRoom != null && (
        isHost || state.myLiveRoomMemberStatus == LiveRoomMember.STATUS_APPROVED
    )

    var hostUrlEditor by rememberSaveable(activeRoom?.roomCode) {
        mutableStateOf(activeRoom?.quizUrl.orEmpty())
    }
    LaunchedEffect(activeRoom?.quizUrl) {
        val syncedUrl = activeRoom?.quizUrl.orEmpty()
        if (syncedUrl.isNotBlank() && hostUrlEditor.isBlank()) {
            hostUrlEditor = syncedUrl
        }
    }

    val approvedMembers = remember(state.liveClassRoomMembers, activeRoom) {
        val fromCollection = state.liveClassRoomMembers.filter {
            it.status == LiveRoomMember.STATUS_APPROVED
        }
        if (fromCollection.isNotEmpty()) {
            fromCollection.sortedWith(
                compareByDescending<LiveRoomMember> { it.role == LiveRoomMember.ROLE_HOST }
                    .thenBy { it.displayName.lowercase() }
            )
        } else if (activeRoom != null) {
            listOf(
                LiveRoomMember(
                    uid = activeRoom.hostUid,
                    roomCode = activeRoom.roomCode,
                    displayName = activeRoom.hostName.ifBlank { "Host" },
                    photoUrl = activeRoom.hostPhotoUrl,
                    role = LiveRoomMember.ROLE_HOST,
                    status = LiveRoomMember.STATUS_APPROVED
                )
            )
        } else {
            emptyList()
        }
    }
    val pendingMembers = remember(state.liveClassRoomMembers) {
        state.liveClassRoomMembers
            .filter { it.status == LiveRoomMember.STATUS_PENDING }
            .sortedBy { it.displayName.lowercase() }
    }
    val approvedCount = approvedMembers.size.coerceAtLeast(activeRoom?.memberCount ?: 0)
        .coerceAtMost(LiveClassRoomRepository.MAX_ROOM_MEMBERS)

    val pulseAlpha = if (reducedMotion) {
        1f
    } else {
        val infinite = rememberInfiniteTransition(label = "live_room_beacon")
        infinite.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(950, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "beacon_alpha"
        ).value
    }

    val selectedChannel = RoomChannelTab.entries.firstOrNull { it.name == activeChannel }
        ?: RoomChannelTab.LIVE_EXAM

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DiscordCanvas)
            .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("live_class_room_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── TOP DISCORD SERVER HEADER ──────────────────────────────────
            item(key = "live_room_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showBackButton) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(DiscordCard)
                                .border(1.dp, DiscordHairline, RoundedCornerShape(14.dp))
                                .clickable(role = Role.Button, onClick = onBackToHome)
                                .testTag("btn_live_room_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = DiscordInkPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Discord-style squircle server emblem
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (activeRoom != null) PrismCrimson else DiscordCard)
                                .border(
                                    1.dp,
                                    if (activeRoom != null) PrismCrimson else DiscordHairline,
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Groups,
                                contentDescription = null,
                                tint = DiscordInkPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .alpha(if (activeRoom != null) pulseAlpha else 0.8f)
                                    .clip(CircleShape)
                                    .background(if (activeRoom != null) DiscordOnlineGreen else PrismCrimson)
                            )
                            Text(
                                text = when {
                                    activeRoom != null && isHost -> "LIVE ROOM · HOST"
                                    activeRoom != null && isApprovedMember -> "LIVE ROOM · CONNECTED"
                                    activeRoom != null -> "WAITING FOR HOST"
                                    else -> "PRISM LIVE ROOMS"
                                },
                                color = if (activeRoom != null) DiscordOnlineGreen else PrismCrimson,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                        }
                        Text(
                            text = activeRoom?.roomName ?: "Class Sync Rooms",
                            color = DiscordInkPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (activeRoom != null) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(DiscordCard)
                                .border(1.dp, DiscordHairline, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "$approvedCount/${LiveClassRoomRepository.MAX_ROOM_MEMBERS}",
                                color = DiscordInkPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // ── ERROR NOTICE ───────────────────────────────────────────────
            val roomError = state.liveClassRoomError
            if (!roomError.isNullOrBlank()) {
                item(key = "live_room_error") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF231114))
                            .border(1.dp, PrismCrimson.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = PrismCrimson,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = roomError,
                            color = DiscordInkPrimary,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // ── STATE 1: GOOGLE SIGN-IN GATE ───────────────────────────────
            if (!isSignedIn) {
                item(key = "live_room_auth_gate") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DiscordPanel)
                            .border(1.dp, DiscordHairline, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = null,
                            tint = PrismCrimson,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = "Sign in to join or host a Live Room",
                            color = DiscordInkPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Up to 40 classmates can join one room. Only your name and circular profile photo are shown — no one can open your profile.",
                            color = DiscordInkMuted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PrismCrimson)
                                .clickable(role = Role.Button, onClick = onSignInGoogle)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("btn_live_room_sign_in")
                        ) {
                            Text(
                                text = "Sign in with Google",
                                color = DiscordInkPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else if (activeRoom == null) {
                // ── STATE 2: LOBBY (JOIN OR CREATE ROOM) ───────────────────
                item(key = "live_room_join_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DiscordPanel)
                            .border(1.dp, DiscordHairline, RoundedCornerShape(16.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Join a Live Room",
                                color = DiscordInkPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            DiscordSmallBadge(text = "MAX 40", tint = DiscordOnlineGreen)
                        }
                        Text(
                            text = "Enter the 6-character code from your host. The host approves each member before live answers appear.",
                            color = DiscordInkMuted,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = joinCodeInput,
                                onValueChange = { raw ->
                                    joinCodeInput = raw.uppercase()
                                        .filter { it.isLetterOrDigit() || it == '-' }
                                        .take(7)
                                },
                                placeholder = {
                                    Text(
                                        text = "K7M-29Q",
                                        color = DiscordInkMuted.copy(alpha = 0.6f),
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    color = DiscordInkPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                ),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (LiveClassRoomRepository.isValidRoomCode(joinCodeInput)) {
                                            onJoinRoom(joinCodeInput)
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrismCrimson,
                                    unfocusedBorderColor = DiscordHairline,
                                    focusedContainerColor = DiscordCanvas,
                                    unfocusedContainerColor = DiscordCanvas,
                                    cursorColor = PrismCrimson
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_live_room_code")
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DiscordCard)
                                    .border(1.dp, DiscordHairline, RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button) {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                        val normalized = LiveClassRoomRepository.normalizeRoomCode(clipText)
                                        if (normalized.isNotBlank()) {
                                            joinCodeInput = LiveClassRoomRepository.formatRoomCode(normalized)
                                        } else {
                                            onShowBannerMessage("No room code found in clipboard")
                                        }
                                    }
                                    .testTag("btn_live_room_paste_code")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentPaste,
                                    contentDescription = "Paste room code",
                                    tint = DiscordInkPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF231114))
                                    .border(1.dp, PrismCrimson.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                    .clickable(enabled = !state.isLiveClassRoomBusy, role = Role.Button) {
                                        RoomQrHelper.launchGoogleQrScanner(
                                            context = context,
                                            onCodeScanned = { scannedCode ->
                                                joinCodeInput = LiveClassRoomRepository.formatRoomCode(scannedCode)
                                                onJoinRoom(scannedCode)
                                            },
                                            onMessage = onShowBannerMessage
                                        )
                                    }
                                    .testTag("btn_live_room_scan_qr")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QrCodeScanner,
                                    contentDescription = "Scan room QR code",
                                    tint = PrismCrimson,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (state.isLiveClassRoomBusy) PrismCrimson.copy(alpha = 0.55f)
                                        else PrismCrimson
                                    )
                                    .clickable(enabled = !state.isLiveClassRoomBusy, role = Role.Button) {
                                        onJoinRoom(joinCodeInput)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                    .testTag("btn_live_room_join")
                            ) {
                                if (state.isLiveClassRoomBusy) {
                                    CircularProgressIndicator(
                                        color = DiscordInkPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Text(
                                        text = "Request to Join",
                                        color = DiscordInkPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DiscordCard)
                                    .border(1.dp, DiscordHairline, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !state.isLiveClassRoomBusy, role = Role.Button) {
                                        RoomQrHelper.launchGoogleQrScanner(
                                            context = context,
                                            onCodeScanned = { scannedCode ->
                                                joinCodeInput = LiveClassRoomRepository.formatRoomCode(scannedCode)
                                                onJoinRoom(scannedCode)
                                            },
                                            onMessage = onShowBannerMessage
                                        )
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                    .testTag("btn_live_room_scan_qr_banner")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QrCodeScanner,
                                    contentDescription = null,
                                    tint = PrismCrimson,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Scan QR",
                                    color = DiscordInkPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                item(key = "live_room_create_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DiscordPanel)
                            .border(1.dp, DiscordHairline, RoundedCornerShape(16.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Create a Live Room",
                                color = DiscordInkPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            DiscordSmallBadge(text = "HOST ONLY", tint = PrismCrimson)
                        }
                        Text(
                            text = "You control everything: approve up to 40 members, lock the exam link so nobody changes it, and broadcast every answer live.",
                            color = DiscordInkMuted,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                        OutlinedTextField(
                            value = createNameInput,
                            onValueChange = { createNameInput = it.take(60) },
                            placeholder = {
                                Text(
                                    text = "Room name (e.g. CSE-B Networks Quiz)",
                                    color = DiscordInkMuted.copy(alpha = 0.65f)
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = DiscordInkPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrismCrimson,
                                unfocusedBorderColor = DiscordHairline,
                                focusedContainerColor = DiscordCanvas,
                                unfocusedContainerColor = DiscordCanvas,
                                cursorColor = PrismCrimson
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_live_room_name")
                        )
                        OutlinedTextField(
                            value = createUrlInput,
                            onValueChange = { createUrlInput = it.take(600) },
                            placeholder = {
                                Text(
                                    text = "Exam or quiz link (optional — you can set it inside)",
                                    color = DiscordInkMuted.copy(alpha = 0.65f)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = DiscordInkMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = DiscordInkPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrismCrimson,
                                unfocusedBorderColor = DiscordHairline,
                                focusedContainerColor = DiscordCanvas,
                                unfocusedContainerColor = DiscordCanvas,
                                cursorColor = PrismCrimson
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_live_room_initial_url")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            SubjectMode.entries.forEach { subj ->
                                val selected = createSubject == subj.id
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (selected) PrismCrimson else DiscordCard)
                                        .border(
                                            1.dp,
                                            if (selected) PrismCrimson else DiscordHairline,
                                            CircleShape
                                        )
                                        .clickable(role = Role.Button) { createSubject = subj.id }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = subj.label,
                                        color = DiscordInkPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DiscordElevated)
                                .border(1.dp, PrismCrimson.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                                .clickable(enabled = !state.isLiveClassRoomBusy, role = Role.Button) {
                                    onCreateRoom(createNameInput, SubjectMode.fromId(createSubject), createUrlInput.trim())
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("btn_live_room_create")
                        ) {
                            Text(
                                text = "Create Room & Get 6-Digit Code",
                                color = DiscordInkPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Private invite code · Up to 40 members · Auto-expires in 24 hours.",
                            color = DiscordInkMuted,
                            fontSize = 11.5.sp
                        )
                    }
                }
            } else if (!isApprovedMember) {
                // ── STATE 3: WAITING FOR HOST APPROVAL GATE ────────────────
                item(key = "live_room_pending_gate") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(DiscordPanel)
                            .border(1.dp, PrismAmber.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                            .padding(22.dp)
                            .testTag("live_room_pending_gate"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(PrismAmber.copy(alpha = 0.14f))
                                .border(1.dp, PrismAmber.copy(alpha = 0.55f), CircleShape)
                        ) {
                            CircularProgressIndicator(
                                color = PrismAmber,
                                strokeWidth = 2.4.dp,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Text(
                            text = "Waiting for host approval",
                            color = DiscordInkPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${activeRoom.hostName.ifBlank { "The room host" }} needs to approve your request to join ${activeRoom.roomName}. You'll enter automatically the moment you're accepted.",
                            color = DiscordInkMuted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(DiscordCard)
                                .border(1.dp, DiscordHairline, RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "ROOM ${activeRoom.formattedCode()}",
                                color = DiscordInkPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.5.sp
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DiscordCard)
                                .border(1.dp, DiscordHairline, RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button, onClick = onLeaveRoom)
                                .testTag("btn_live_room_leave")
                        ) {
                            Text(
                                text = "Cancel Request",
                                color = DiscordInkPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                // ── STATE 4: ACTIVE DISCORD-STYLE ROOM ─────────────────────
                item(key = "active_room_banner") {
                    DiscordServerHeaderCard(
                        room = activeRoom,
                        isHost = isHost,
                        approvedCount = approvedCount,
                        pendingCount = if (isHost) pendingMembers.size else 0,
                        selectedChannel = selectedChannel,
                        onSelectChannel = { activeChannel = it.name },
                        onCopyCode = {
                            val formatted = activeRoom.formattedCode()
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Prism Room Code", formatted))
                            onShowBannerMessage("Copied room code $formatted")
                        },
                        onShowQr = { showQrInviteDialog = true },
                        onShareInvite = {
                            val formatted = activeRoom.formattedCode()
                            val shareText = buildString {
                                appendLine("Join our Prism Live Class Room: ${activeRoom.roomName}")
                                appendLine("Room Code: $formatted")
                                appendLine("Direct Link: prism://room/${activeRoom.roomCode}")
                                append("Open Prism -> Rooms -> enter $formatted and wait for host approval.")
                            }
                            runCatching {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Prism Live Room: $formatted")
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Room Code"))
                            }
                        }
                    )
                }

                // ── HOST PENDING REQUEST QUICK ALERT (Visible even on # live-exam) ──
                if (isHost && pendingMembers.isNotEmpty() && selectedChannel != RoomChannelTab.MEMBERS) {
                    item(key = "host_pending_alert") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF231114))
                                .border(1.dp, PrismCrimson.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) { activeChannel = RoomChannelTab.MEMBERS.name }
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                                .testTag("banner_pending_join_requests"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${pendingMembers.size} classmate${if (pendingMembers.size == 1) "" else "s"} waiting for approval",
                                    color = DiscordInkPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap to review in # members",
                                    color = DiscordInkMuted,
                                    fontSize = 11.5.sp
                                )
                            }
                            DiscordSmallBadge(text = "REVIEW", tint = PrismCrimson)
                        }
                    }
                }

                when (selectedChannel) {
                    // ── CHANNEL 1: # live-exam ─────────────────────────────
                    RoomChannelTab.LIVE_EXAM -> {
                        item(key = "live_room_locked_url_card") {
                            LockedExamBrowserCard(
                                room = activeRoom,
                                isHost = isHost,
                                isBusy = state.isLiveClassRoomBusy,
                                hostUrlDraft = hostUrlEditor,
                                onHostUrlDraftChange = { hostUrlEditor = it.take(600) },
                                onLockUrl = { onUpdateLockedQuizUrl(hostUrlEditor.trim()) },
                                onOpenLiveBrowser = {
                                    val targetUrl = activeRoom.quizUrl.ifBlank {
                                        if (isHost) hostUrlEditor.trim().ifBlank { state.googleFormUrlInput }
                                        else ""
                                    }
                                    context.startActivity(
                                        GoogleFormsWebActivity.createLiveRoomIntent(
                                            context = context,
                                            roomCode = activeRoom.roomCode,
                                            isHost = isHost,
                                            lockedQuizUrl = targetUrl
                                        )
                                    )
                                }
                            )
                        }

                        if (state.liveClassRoomAnswers.isNotEmpty()) {
                            item(key = "active_room_compact_key") {
                                val compactKey = remember(state.liveClassRoomAnswers) {
                                    state.liveClassRoomAnswers
                                        .sortedBy { it.questionNumber }
                                        .joinToString(" · ") { ans ->
                                            val smart = LiveClassRoomRepository.formatSmartAnswerDisplay(ans)
                                            if (smart.isShortDirectAnswer) {
                                                "Q${ans.questionNumber}: ${smart.directWordOrPhrase}"
                                            } else {
                                                "Q${ans.questionNumber}: ${ans.answerBadge}"
                                            }
                                        }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DiscordPanel)
                                        .border(1.dp, DiscordOnlineGreen.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                        .clickable(role = Role.Button) {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("Live Room Answer Key", compactKey))
                                            onShowBannerMessage("Copied live answers")
                                        }
                                        .padding(horizontal = 14.dp, vertical = 11.dp)
                                        .testTag("live_room_compact_key"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                        Text(
                                            text = "LIVE ANSWERS (${state.liveClassRoomAnswers.size} QS)",
                                            color = DiscordInkMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = compactKey,
                                            color = DiscordOnlineGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy live answers",
                                        tint = DiscordInkPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            items(
                                items = state.liveClassRoomAnswers,
                                key = { it.id.ifBlank { "ans_${it.questionNumber}_${it.questionText.hashCode()}" } }
                            ) { answer ->
                                DiscordLiveAnswerCard(
                                    answer = answer,
                                    isHost = isHost,
                                    expanded = expandedAnswerId == answer.id,
                                    hasVoted = answer.id in state.liveClassRoomVotedIds,
                                    isVerifyingWithAi = state.liveClassRoomVerifyingAnswerId == answer.id,
                                    onToggleExpand = {
                                        expandedAnswerId = if (expandedAnswerId == answer.id) null else answer.id
                                    },
                                    onVoteConfirm = { onVoteConfirm(answer.id) },
                                    onVoteDispute = { onVoteDispute(answer.id) },
                                    onAiVerify = { onAiVerifyAnswer(answer) }
                                )
                            }
                        } else {
                            item(key = "active_room_empty") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(DiscordPanel)
                                        .border(1.dp, DiscordHairline, RoundedCornerShape(16.dp))
                                        .padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Sync,
                                        contentDescription = null,
                                        tint = PrismCrimson,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = if (isHost) "Ready to broadcast live answers" else "Waiting for host answers",
                                        color = DiscordInkPrimary,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isHost) {
                                            "Open the Live Exam Browser above and solve any question. Every approved classmate sees the exact answer word (or highlighted option if options are shuffled) right at the top of their browser."
                                        } else {
                                            "Tap Open Live Exam Browser above. As soon as ${activeRoom.hostName.ifBlank { "the host" }} solves a question, the answer appears automatically at the top of your screen."
                                        },
                                        color = DiscordInkMuted,
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }

                    // ── CHANNEL 2: # members (Privacy-First, Max 40) ───────
                    RoomChannelTab.MEMBERS -> {
                        if (isHost && pendingMembers.isNotEmpty()) {
                            item(key = "pending_members_header") {
                                Text(
                                    text = "WAITING FOR YOUR APPROVAL (${pendingMembers.size})",
                                    color = PrismCrimson,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            items(
                                items = pendingMembers,
                                key = { "pending_${it.uid}" }
                            ) { member ->
                                PendingMemberApprovalRow(
                                    member = member,
                                    roomFull = approvedCount >= LiveClassRoomRepository.MAX_ROOM_MEMBERS,
                                    onApprove = { onApproveMember(member.uid) },
                                    onDecline = { onRejectOrRemoveMember(member.uid, false) }
                                )
                            }
                        }

                        item(key = "approved_members_header") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "MEMBERS — $approvedCount / ${LiveClassRoomRepository.MAX_ROOM_MEMBERS}",
                                    color = DiscordInkMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Privacy protected: only profile photo and display name are shown. Profiles cannot be opened.",
                                    color = DiscordInkMuted.copy(alpha = 0.8f),
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        items(
                            items = approvedMembers,
                            key = { "member_${it.uid}" }
                        ) { member ->
                            // Strictly non-clickable member row — zero profile inspection
                            PrivacySafeMemberRow(
                                member = member,
                                canHostRemove = isHost && member.role != LiveRoomMember.ROLE_HOST && member.uid != state.firebaseUserId,
                                onRemove = { onRejectOrRemoveMember(member.uid, true) }
                            )
                        }
                    }

                    // ── CHANNEL 3: # controls ──────────────────────────────
                    RoomChannelTab.CONTROLS -> {
                        item(key = "room_controls_panel") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(DiscordPanel)
                                    .border(1.dp, DiscordHairline, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Room Controls",
                                    color = DiscordInkPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // Capacity meter
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Room Capacity",
                                            color = DiscordInkPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "$approvedCount / ${LiveClassRoomRepository.MAX_ROOM_MEMBERS} members",
                                            color = DiscordInkMuted,
                                            fontSize = 12.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    val fillFrac = (approvedCount.toFloat() / LiveClassRoomRepository.MAX_ROOM_MEMBERS.toFloat()).coerceIn(0.03f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(CircleShape)
                                            .background(DiscordElevated)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fillFrac)
                                                .height(7.dp)
                                                .clip(CircleShape)
                                                .background(if (approvedCount >= 36) PrismCrimson else DiscordOnlineGreen)
                                        )
                                    }
                                }

                                if (isHost) {
                                    HorizontalDivider(color = DiscordHairline, thickness = 1.dp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                            Text(
                                                text = "Auto-broadcast my solved answers",
                                                color = DiscordInkPrimary,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Quiz Browser & Floating Pill solves appear live for all approved members",
                                                color = DiscordInkMuted,
                                                fontSize = 11.5.sp
                                            )
                                        }
                                        Switch(
                                            checked = state.liveClassRoomAutoSync,
                                            onCheckedChange = onToggleAutoSync,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = DiscordInkPrimary,
                                                checkedTrackColor = PrismCrimson,
                                                uncheckedThumbColor = DiscordInkMuted,
                                                uncheckedTrackColor = DiscordCanvas
                                            ),
                                            modifier = Modifier.testTag("switch_live_room_autosync")
                                        )
                                    }
                                }

                                HorizontalDivider(color = DiscordHairline, thickness = 1.dp)

                                Text(
                                    text = "Room code expires automatically in ${activeRoom.remainingHours()}h. Only the host can change the exam link or admit classmates.",
                                    color = DiscordInkMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isHost) Color(0xFF2B1216) else DiscordCard)
                                        .border(
                                            1.dp,
                                            if (isHost) PrismCrimson.copy(alpha = 0.7f) else DiscordHairline,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable(role = Role.Button, onClick = if (isHost) onEndRoom else onLeaveRoom)
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                        .testTag("btn_live_room_leave"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = if (isHost) "End room" else "Leave room",
                                        tint = if (isHost) PrismCrimson else DiscordInkPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isHost) "End Room for Everyone" else "Leave Room",
                                        color = if (isHost) PrismCrimson else DiscordInkPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showQrInviteDialog && activeRoom != null) {
            RoomQrInviteDialog(
                room = activeRoom,
                onCopyCode = {
                    val formatted = activeRoom.formattedCode()
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("Prism Room Code", formatted))
                    onShowBannerMessage("Copied room code $formatted")
                },
                onDismiss = { showQrInviteDialog = false }
            )
        }
    }
}

@Composable
private fun RoomQrInviteDialog(
    room: LiveClassRoom,
    onCopyCode: () -> Unit,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(room.roomCode) {
        RoomQrHelper.generateRoomQrBitmap(room.roomCode, sizePx = 640)
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(DiscordPanel)
                .border(1.dp, PrismCrimson.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                .padding(20.dp)
                .testTag("dialog_live_room_qr_invite"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "SCAN TO JOIN ROOM",
                        color = PrismCrimson,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = room.roomName.ifBlank { "Live Class Room" },
                        color = DiscordInkPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DiscordCard)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .testTag("btn_close_room_qr_dialog")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close QR dialog",
                        tint = DiscordInkPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Crisp high-contrast white QR framing card
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(228.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(12.dp)
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Room invite QR code for ${room.formattedCode()}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = room.formattedCode(),
                        color = Color(0xFF0A0A0C),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = room.formattedCode(),
                    color = DiscordInkPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    letterSpacing = 2.4.sp
                )
                Text(
                    text = "Classmates open Rooms → Scan QR · Host approval required (max 40)",
                    color = DiscordInkMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DiscordCard)
                        .border(1.dp, DiscordHairline, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onCopyCode)
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        tint = DiscordInkPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Copy Code",
                        color = DiscordInkPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrismCrimson)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "Done",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscordServerHeaderCard(
    room: LiveClassRoom,
    isHost: Boolean,
    approvedCount: Int,
    pendingCount: Int,
    selectedChannel: RoomChannelTab,
    onSelectChannel: (RoomChannelTab) -> Unit,
    onCopyCode: () -> Unit,
    onShowQr: () -> Unit,
    onShareInvite: () -> Unit
) {
    val headerShape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = headerShape,
                ambientColor = Color.Black.copy(alpha = 0.90f),
                spotColor = PrismCrimson.copy(alpha = 0.32f)
            )
            .clip(headerShape)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF191B23), Color(0xFF121319)))
            )
            .drawBehind {
                val w = size.width
                val h = size.height
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent)
                    ),
                    start = Offset(w * 0.12f, 1.dp.toPx()),
                    end = Offset(w * 0.88f, 1.dp.toPx()),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val curl = Path().apply {
                    moveTo(w * 0.62f, 0f)
                    cubicTo(w * 0.76f, h * 0.20f, w * 0.88f, h * 0.14f, w, h * 0.34f)
                }
                drawPath(
                    path = curl,
                    color = PrismCrimson.copy(alpha = 0.22f),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            .border(1.dp, Color(0xFF2A2D39), headerShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Code + QR/Copy/Share bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "INVITE CODE · HOSTED BY ${room.hostName.ifBlank { "HOST" }.uppercase()}",
                    color = DiscordInkMuted,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = room.formattedCode(),
                    color = DiscordInkPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    letterSpacing = 1.8.sp,
                    modifier = Modifier.testTag("text_active_room_code")
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DiscordCard)
                        .border(1.dp, PrismCrimson.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onShowQr)
                        .testTag("btn_live_room_show_qr")
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode2,
                        contentDescription = "Show room QR code",
                        tint = PrismCrimson,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DiscordCard)
                        .border(1.dp, DiscordHairline, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onCopyCode)
                        .testTag("btn_live_room_copy_code")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy room code",
                        tint = DiscordInkPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrismCrimson)
                        .clickable(role = Role.Button, onClick = onShareInvite)
                        .testTag("btn_live_room_share")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share room code",
                        tint = DiscordInkPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Discord Channel Selector Pills (# live-exam | # members | # controls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DiscordCanvas)
                .border(1.dp, DiscordHairline, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RoomChannelTab.entries.forEach { tab ->
                val isSelected = tab == selectedChannel
                val badgeCount = if (tab == RoomChannelTab.MEMBERS && isHost) pendingCount else 0
                val displayText = when (tab) {
                    RoomChannelTab.LIVE_EXAM -> "live-exam"
                    RoomChannelTab.MEMBERS -> "members ($approvedCount)"
                    RoomChannelTab.CONTROLS -> "controls"
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) DiscordElevated else Color.Transparent)
                        .clickable(role = Role.Tab) { onSelectChannel(tab) }
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .testTag(tab.tag),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (tab) {
                            RoomChannelTab.LIVE_EXAM -> Icons.Filled.Tag
                            RoomChannelTab.MEMBERS -> Icons.Filled.Groups
                            RoomChannelTab.CONTROLS -> Icons.Filled.Tune
                        },
                        contentDescription = null,
                        tint = if (isSelected) DiscordInkPrimary else DiscordInkMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = displayText,
                        color = if (isSelected) DiscordInkPrimary else DiscordInkMuted,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badgeCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(PrismCrimson)
                        ) {
                            Text(
                                text = badgeCount.coerceAtMost(9).toString(),
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedExamBrowserCard(
    room: LiveClassRoom,
    isHost: Boolean,
    isBusy: Boolean,
    hostUrlDraft: String,
    onHostUrlDraftChange: (String) -> Unit,
    onLockUrl: () -> Unit,
    onOpenLiveBrowser: () -> Unit
) {
    val cleanHostLabel = remember(room.quizUrl) {
        if (room.quizUrl.isBlank()) {
            "No exam link locked yet"
        } else {
            runCatching { android.net.Uri.parse(room.quizUrl).host.orEmpty().removePrefix("www.") }
                .getOrDefault("")
                .ifBlank { room.quizUrl.take(36) }
        }
    }

    val cardShape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = cardShape,
                ambientColor = Color.Black.copy(alpha = 0.90f),
                spotColor = PrismCrimson.copy(alpha = 0.35f)
            )
            .clip(cardShape)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1A1C24), Color(0xFF111218)))
            )
            .drawBehind {
                val w = size.width
                val h = size.height
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.14f), Color.Transparent)
                    ),
                    start = Offset(w * 0.12f, 1.dp.toPx()),
                    end = Offset(w * 0.88f, 1.dp.toPx()),
                    strokeWidth = 1.1.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val curl = Path().apply {
                    moveTo(w * 0.56f, h)
                    cubicTo(w * 0.68f, h * 0.76f, w * 0.84f, h * 0.86f, w, h * 0.60f)
                }
                drawPath(
                    path = curl,
                    color = PrismCrimson.copy(alpha = 0.24f),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            .border(1.dp, PrismCrimson.copy(alpha = 0.48f), cardShape)
            .padding(14.dp)
            .testTag("live_room_locked_url_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (room.quizUrl.isNotBlank()) DiscordOnlineGreen else DiscordInkMuted,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = if (isHost) "Locked Exam Link (Host Control)" else "Locked by ${room.hostName.ifBlank { "Host" }}",
                        color = DiscordInkPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = cleanHostLabel,
                        color = if (room.quizUrl.isNotBlank()) DiscordOnlineGreen else DiscordInkMuted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!isHost) {
                DiscordSmallBadge(text = "LOCKED", tint = DiscordOnlineGreen)
            }
        }

        if (isHost) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = hostUrlDraft,
                    onValueChange = onHostUrlDraftChange,
                    placeholder = {
                        Text(
                            text = "Paste exam/quiz URL to lock for all members",
                            color = DiscordInkMuted.copy(alpha = 0.65f),
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = DiscordInkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = DiscordInkPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrismCrimson,
                        unfocusedBorderColor = DiscordHairline,
                        focusedContainerColor = DiscordCanvas,
                        unfocusedContainerColor = DiscordCanvas,
                        cursorColor = PrismCrimson
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_live_room_locked_url")
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 50.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DiscordElevated)
                        .border(1.dp, PrismCrimson.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .clickable(enabled = !isBusy && hostUrlDraft.isNotBlank(), role = Role.Button, onClick = onLockUrl)
                        .padding(horizontal = 12.dp)
                        .testTag("btn_live_room_lock_url")
                ) {
                    Text(
                        text = "Lock",
                        color = DiscordInkPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text = if (isHost) {
                "Any website you open loads on every member's browser automatically. Your Name & Roll No stay private — only exam answers broadcast."
            } else {
                "Opens the host's exam in your own private browser. Enter your own Name & Roll No while host answers stream at the top."
            },
            color = DiscordInkMuted,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )

        // Launch Live Exam Browser (Shuffled-Option-Proof Top Answer Bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PrismCrimson)
                .clickable(role = Role.Button, onClick = onOpenLiveBrowser)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("btn_live_room_open_browser"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = DiscordInkPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isHost) "Open Live Exam Browser (Host)" else "Open Live Exam Browser",
                color = DiscordInkPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PendingMemberApprovalRow(
    member: LiveRoomMember,
    roomFull: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DiscordCard)
            .border(1.dp, PrismCrimson.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("member_pending_${member.uid}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MemberCircularAvatar(
            displayName = member.displayName,
            photoUrl = member.photoUrl,
            showOnlineDot = false,
            size = 40.dp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.displayName.ifBlank { "Classmate" },
                color = DiscordInkPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (roomFull) "Room is at 40/40 limit" else "Wants to join room",
                color = if (roomFull) PrismAmber else DiscordInkMuted,
                fontSize = 11.5.sp
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DiscordElevated)
                .border(1.dp, DiscordHairline, RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onDecline)
                .testTag("btn_decline_member_${member.uid}")
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Decline request",
                tint = DiscordInkMuted,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (roomFull) DiscordElevated else DiscordOnlineGreen)
                .clickable(enabled = !roomFull, role = Role.Button, onClick = onApprove)
                .testTag("btn_approve_member_${member.uid}")
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Approve member",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Strictly non-clickable member row: shows ONLY circular profile photo + display name (+ HOST tag).
 * Never opens any profile modal or reveals email/phone/UID.
 */
@Composable
private fun PrivacySafeMemberRow(
    member: LiveRoomMember,
    canHostRemove: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DiscordPanel)
            .border(1.dp, DiscordHairline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("member_row_${member.uid}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MemberCircularAvatar(
            displayName = member.displayName,
            photoUrl = member.photoUrl,
            showOnlineDot = true,
            size = 38.dp
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = member.displayName.ifBlank { "Classmate" },
                color = DiscordInkPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (member.role == LiveRoomMember.ROLE_HOST) {
                DiscordSmallBadge(text = "HOST", tint = PrismCrimson)
            }
        }
        if (canHostRemove) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DiscordCard)
                    .clickable(role = Role.Button, onClick = onRemove)
                    .testTag("btn_remove_member_${member.uid}")
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonRemove,
                    contentDescription = "Remove member",
                    tint = DiscordInkMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MemberCircularAvatar(
    displayName: String,
    photoUrl: String,
    showOnlineDot: Boolean,
    size: Dp
) {
    val initial = displayName.trim().firstOrNull()?.uppercase() ?: "P"
    Box(modifier = Modifier.size(size)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(DiscordElevated)
                .border(1.dp, DiscordHairline, CircleShape)
        ) {
            Text(
                text = initial,
                color = DiscordInkPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (photoUrl.isNotBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "$displayName photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }
        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(DiscordPanel)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(DiscordOnlineGreen)
                )
            }
        }
    }
}

@Composable
private fun DiscordLiveAnswerCard(
    answer: LiveRoomAnswer,
    isHost: Boolean,
    expanded: Boolean,
    hasVoted: Boolean,
    isVerifyingWithAi: Boolean,
    onToggleExpand: () -> Unit,
    onVoteConfirm: () -> Unit,
    onVoteDispute: () -> Unit,
    onAiVerify: () -> Unit
) {
    val smart = remember(answer.shortAnswer, answer.answerBadge, answer.questionNumber) {
        LiveClassRoomRepository.formatSmartAnswerDisplay(answer)
    }
    val isDisputed = answer.disputeCount > 0 && !answer.verifiedByAi
    val borderColor = when {
        answer.verifiedByAi -> DiscordOnlineGreen.copy(alpha = 0.65f)
        isDisputed -> PrismAmber.copy(alpha = 0.65f)
        else -> DiscordHairline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(DiscordPanel)
            .border(1.dp, borderColor, RoundedCornerShape(15.dp))
            .clickable(role = Role.Button, onClick = onToggleExpand)
            .padding(14.dp)
            .testTag("card_live_room_answer_${answer.questionNumber}"),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        // Shuffled-option-proof answer banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (smart.isShortDirectAnswer || answer.verifiedByAi) DiscordOnlineGreen.copy(alpha = 0.16f) else Color(0xFF231114))
                        .border(
                            1.dp,
                            if (smart.isShortDirectAnswer || answer.verifiedByAi) DiscordOnlineGreen.copy(alpha = 0.6f) else PrismCrimson.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (smart.isShortDirectAnswer) {
                            "Q${answer.questionNumber} · ${smart.directWordOrPhrase}"
                        } else {
                            "Q${answer.questionNumber} · Match in Browser"
                        },
                        color = if (smart.isShortDirectAnswer || answer.verifiedByAi) DiscordOnlineGreen else DiscordInkPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (answer.verifiedByAi) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF0E261C))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "AI Verified",
                        tint = DiscordOnlineGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Verified",
                        color = DiscordOnlineGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // For lengthy answers, display the matched answer text cleanly so students recognize it even if shuffled
        if (!smart.isShortDirectAnswer && smart.matchTargetText.isNotBlank()) {
            Text(
                text = smart.matchTargetText,
                color = DiscordInkPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
                maxLines = if (expanded) 8 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(DiscordCard)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }

        Text(
            text = answer.questionText,
            color = DiscordInkPrimary.copy(alpha = 0.85f),
            fontSize = 12.5.sp,
            lineHeight = 17.5.sp,
            maxLines = if (expanded) 12 else 2,
            overflow = TextOverflow.Ellipsis
        )

        AnimatedVisibility(
            visible = expanded && answer.explanation.isNotBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Text(
                text = answer.explanation,
                color = DiscordInkMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DiscordCanvas)
                    .padding(10.dp)
            )
        }

        Text(
            text = "Solved by ${answer.sharedByName.ifBlank { "Host" }} · ${answer.sourceLabel}",
            color = DiscordInkMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(DiscordCanvas)
                    .border(1.dp, DiscordOnlineGreen.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                    .clickable(enabled = !hasVoted, role = Role.Button, onClick = onVoteConfirm)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("btn_room_confirm_${answer.questionNumber}")
            ) {
                Icon(
                    imageVector = if (hasVoted) Icons.Filled.Check else Icons.Filled.ThumbUp,
                    contentDescription = "Confirm answer",
                    tint = DiscordOnlineGreen,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "${answer.confirmCount}",
                    color = DiscordInkPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(DiscordCanvas)
                    .border(1.dp, if (isDisputed) PrismAmber else DiscordHairline, RoundedCornerShape(9.dp))
                    .clickable(enabled = !hasVoted, role = Role.Button, onClick = onVoteDispute)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("btn_room_dispute_${answer.questionNumber}")
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = "Flag answer",
                    tint = if (isDisputed) PrismAmber else DiscordInkMuted,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "${answer.disputeCount}",
                    color = DiscordInkPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.weight(1f))

            if (isHost) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isDisputed) PrismCrimson else DiscordElevated)
                        .border(
                            1.dp,
                            if (isDisputed) PrismCrimson else DiscordHairline,
                            RoundedCornerShape(9.dp)
                        )
                        .clickable(enabled = !isVerifyingWithAi, role = Role.Button, onClick = onAiVerify)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("btn_room_ai_verify_${answer.questionNumber}")
                ) {
                    if (isVerifyingWithAi) {
                        CircularProgressIndicator(
                            color = DiscordInkPrimary,
                            strokeWidth = 1.8.dp,
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Verify with AI",
                            tint = if (isDisputed) DiscordInkPrimary else PrismCrimson,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "AI Verify",
                            color = DiscordInkPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscordSmallBadge(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = tint,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            maxLines = 1
        )
    }
}
