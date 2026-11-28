package com.omnisolve.overlay.ads

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.data.DailyUsageSnapshot
import com.omnisolve.overlay.data.UsageLimitFeature
import com.omnisolve.overlay.data.UserUsageLimitManager
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

/**
 * High-visibility Rewards & Bonus Credits card displayed on Home and Settings.
 * Lets any user watch a Rewarded Video Ad to unlock +5 Cloud Solves, +3 Follow-Ups,
 * +2 Photo Keys, +1 PDF Key, and +2 Google Forms for the current UTC day.
 */
@Composable
fun RewardBonusCreditsCard(
    snapshot: DailyUsageSnapshot,
    onWatchRewardedAd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val haptics = LocalHapticFeedback.current
    val isRewardReady by AdManager.isRewardedAdReady.collectAsState()
    val isRewardLoading by AdManager.isRewardedAdLoading.collectAsState()
    val lastError by AdManager.lastRewardedError.collectAsState()

    val cloudLeft = snapshot.remainingFor(UsageLimitFeature.OVERLAY_MCQ)
    val cloudTotal = snapshot.effectiveLimitFor(UsageLimitFeature.OVERLAY_MCQ)
    val photoLeft = snapshot.remainingFor(UsageLimitFeature.ASK_AI_PHOTO)
    val pdfLeft = snapshot.remainingFor(UsageLimitFeature.ASK_AI_PDF)
    val formsLeft = snapshot.remainingFor(UsageLimitFeature.GOOGLE_FORMS)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.glassMedium,
                        palette.accentEmerald.copy(alpha = 0.08f)
                    )
                )
            )
            .border(1.dp, palette.accentEmerald.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(14.dp)
            .testTag("card_rewards_bonus_hub"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.accentEmerald.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CardGiftcard,
                        contentDescription = null,
                        tint = palette.accentEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Rewards & Daily Credits",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    )
                    Text(
                        text = "Watch a short ad to boost today's AI limits",
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
                    .background(palette.accentEmerald.copy(alpha = 0.16f))
                    .border(1.dp, palette.accentEmerald.copy(alpha = 0.4f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${snapshot.rewardClaimsToday}/${UserUsageLimitManager.MAX_DAILY_REWARD_CLAIMS} Boosts",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.accentEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
            }
        }

        // Compact Credit Pills Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CreditStatChip(label = "Solves", value = "$cloudLeft/$cloudTotal", modifier = Modifier.weight(1f))
            CreditStatChip(label = "Photo", value = "$photoLeft left", modifier = Modifier.weight(1f))
            CreditStatChip(label = "PDF", value = "$pdfLeft left", modifier = Modifier.weight(1f))
            CreditStatChip(label = "Forms", value = "$formsLeft left", modifier = Modifier.weight(1f))
        }

        // Watch Ad CTA Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (snapshot.canClaimReward) {
                        Brush.horizontalGradient(listOf(palette.accentEmerald, palette.accentSky))
                    } else {
                        Brush.horizontalGradient(listOf(palette.cardHairlineBorder, palette.cardHairlineBorder))
                    }
                )
                .clickable(enabled = snapshot.canClaimReward && !isRewardLoading, role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onWatchRewardedAd()
                }
                .padding(horizontal = 14.dp, vertical = 11.dp)
                .testTag("btn_rewards_watch_ad"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isRewardLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Loading Reward Video…",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayCircleFilled,
                    contentDescription = null,
                    tint = if (snapshot.canClaimReward) Color.White else palette.inkMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = when {
                        !snapshot.canClaimReward -> "Daily Reward Limit Reached (5/5)"
                        isRewardReady -> "Watch Ad • Earn +5 Solves & Bonus Keys"
                        else -> "Watch Ad • +5 Solves, +2 Photo, +1 PDF"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = if (snapshot.canClaimReward) Color.White else palette.inkMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!lastError.isNullOrBlank()) {
            Text(
                text = lastError!!,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = palette.inkMuted,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            )
        }
    }
}

@Composable
private fun CreditStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.canvasBottom.copy(alpha = 0.7f))
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                color = palette.inkPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            maxLines = 1
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = palette.inkMuted,
                fontSize = 9.5.sp
            ),
            maxLines = 1
        )
    }
}
