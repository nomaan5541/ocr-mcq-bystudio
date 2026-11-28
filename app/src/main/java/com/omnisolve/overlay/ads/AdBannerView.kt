package com.omnisolve.overlay.ads

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
 * Always keeps [AdView] mounted in the Android view hierarchy so AdMob SDK visibility & fill checks succeed.
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

    val adView = remember(adUnitId, placementTag, adWidthDp) {
        val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
        AdView(context).apply {
            setAdSize(adaptiveSize)
            this.adUnitId = adUnitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d("PrismAdBanner", "Banner loaded ($placementTag)")
                    AdManager.updateBannerStatus(null)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("PrismAdBanner", "Banner load failed ($placementTag): code=${error.code} ${error.message}")
                    AdManager.updateBannerStatus("Banner: ${error.message} (code ${error.code})")
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { adView },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
