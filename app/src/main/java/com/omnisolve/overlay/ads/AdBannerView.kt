package com.omnisolve.overlay.ads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

enum class AdBannerStyle {
    COMPACT_HEADER,
    INLINE_CARD
}

/**
 * Renders an inline Anchored Adaptive Google AdMob banner inside main app screens.
 * Collapses cleanly when no ad is filled so there are never blank gaps, and never blocks floating controls.
 */
@Composable
fun InAppAdMobBanner(
    placementTag: String = "default",
    style: AdBannerStyle = AdBannerStyle.INLINE_CARD,
    modifier: Modifier = Modifier,
    adUnitId: String = AdConfig.BANNER_AD_UNIT_ID
) {
    if (!AdConfig.ADS_ENABLED || adUnitId.isBlank()) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val adWidthDp = (configuration.screenWidthDp - 32).coerceAtLeast(300)
    var isAdLoaded by remember(adUnitId, placementTag) { mutableStateOf(false) }

    val adView = remember(adUnitId, placementTag, adWidthDp) {
        val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
        AdView(context).apply {
            setAdSize(adaptiveSize)
            this.adUnitId = adUnitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    isAdLoaded = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isAdLoaded = false
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose {
            try {
                adView.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    val verticalPadding = if (style == AdBannerStyle.COMPACT_HEADER) 4.dp else 8.dp
    AnimatedVisibility(
        visible = isAdLoaded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = verticalPadding),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(factory = { adView })
        }
    }
}
