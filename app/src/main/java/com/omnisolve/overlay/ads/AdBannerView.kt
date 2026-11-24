package com.omnisolve.overlay.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class AdBannerStyle {
    COMPACT_HEADER,
    INLINE_CARD
}

/**
 * Permanently ad-free no-op banner composable.
 * Retained as a lightweight stub to keep call-site compatibility with zero ad SDK overhead.
 */
@Composable
fun InAppAdMobBanner(
    placementTag: String = "default",
    style: AdBannerStyle = AdBannerStyle.INLINE_CARD,
    modifier: Modifier = Modifier,
    adUnitId: String = AdConfig.BANNER_AD_UNIT_ID
) {
    // Permanently ad-free: renders nothing
}
