package com.omnisolve.overlay.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

enum class AdBannerStyle {
    COMPACT_HEADER,
    INLINE_CARD
}

/**
 * Renders an inline Google AdMob banner inside main app screens.
 * Never overlays or blocks floating solver controls.
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
    val adView = remember(adUnitId, placementTag) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = adUnitId
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
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(factory = { adView })
    }
}
