package com.omnisolve.overlay.ads

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import kotlinx.coroutines.delay

enum class AdBannerStyle {
    COMPACT_HEADER,
    INLINE_CARD
}

/**
 * Lightweight process-scoped pool for in-app [AdView] instances so scrolling
 * inside [androidx.compose.foundation.lazy.LazyColumn] or switching bottom tabs
 * never destroys and recreates heavy Chromium WebView-backed AdViews on the UI thread.
 */
private object InAppAdBannerPool {
    data class PooledBanner(
        val adView: AdView,
        val widthDp: Int,
        val unitId: String,
        val loadedState: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(false)
    )

    private val pool = LinkedHashMap<String, PooledBanner>(6)

    fun getOrCreate(
        placementTag: String,
        context: Context,
        adUnitId: String,
        adWidthDp: Int
    ): PooledBanner? {
        val existing = pool[placementTag]
        if (existing != null && existing.unitId == adUnitId && kotlin.math.abs(existing.widthDp - adWidthDp) <= 24) {
            return existing
        }
        // Evict stale size/unit entry cleanly
        existing?.let { old ->
            runCatching {
                (old.adView.parent as? ViewGroup)?.removeView(old.adView)
                old.adView.destroy()
            }
            pool.remove(placementTag)
        }

        val appCtx = context.applicationContext
        val created = runCatching {
            val adaptiveSize = runCatching {
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(appCtx, adWidthDp)
            }.getOrDefault(AdSize.BANNER)

            val loadedState = mutableStateOf(false)
            val view = AdView(appCtx).apply {
                setAdSize(adaptiveSize)
                this.adUnitId = adUnitId
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        loadedState.value = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loadedState.value = false
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
            PooledBanner(
                adView = view,
                widthDp = adWidthDp,
                unitId = adUnitId,
                loadedState = loadedState
            )
        }.getOrNull() ?: return null

        pool[placementTag] = created
        return created
    }
}

/**
 * Policy-compliant, lag-free pooled adaptive in-app AdMob Banner View for Jetpack Compose.
 *
 * - Waits briefly via [LaunchedEffect] before creating/loading an uncached WebView AdView so
 *   fast LazyColumn scrolling and tab switches never drop frames.
 * - Reuses the pooled AdView across scroll recycles and tab switches.
 * - Placed exclusively inside in-app screens (Home, Status header, Settings, Forms).
 * - NEVER rendered inside the floating Dynamic Island overlay over other apps.
 */
@Composable
fun InAppAdMobBanner(
    placementTag: String = "default",
    style: AdBannerStyle = AdBannerStyle.INLINE_CARD,
    modifier: Modifier = Modifier,
    adUnitId: String = AdConfig.BANNER_AD_UNIT_ID
) {
    if (!AdConfig.ADS_ENABLED || adUnitId.isBlank()) return
    if (LocalInspectionMode.current) return

    val context = LocalContext.current
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cornerRadius = themeSpec.cornerRadiusDp.coerceIn(12, 20).dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ad_banner_slot_$placementTag")
    ) {
        val adWidthDp = maxWidth.value.toInt().coerceIn(300, 728)
        var pooledBanner by remember(placementTag, adUnitId) {
            mutableStateOf<InAppAdBannerPool.PooledBanner?>(null)
        }

        // Defer initial WebView/AdView creation past scroll settle so frames never stutter
        LaunchedEffect(placementTag, adUnitId, adWidthDp) {
            if (pooledBanner == null) {
                delay(280L)
            }
            pooledBanner = InAppAdBannerPool.getOrCreate(
                placementTag = placementTag,
                context = context,
                adUnitId = adUnitId,
                adWidthDp = adWidthDp
            )
        }

        val activeBanner = pooledBanner
        val isAdLoaded = activeBanner?.loadedState?.value == true

        DisposableEffect(activeBanner) {
            runCatching { activeBanner?.adView?.resume() }
            onDispose {
                runCatching {
                    activeBanner?.adView?.pause()
                    (activeBanner?.adView?.parent as? ViewGroup)?.removeView(activeBanner?.adView)
                }
            }
        }

        if (activeBanner != null && isAdLoaded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(palette.glassStrong.copy(alpha = 0.88f))
                    .border(
                        width = 1.dp,
                        color = palette.cardHairlineBorder,
                        shape = RoundedCornerShape(cornerRadius)
                    )
                    .padding(
                        horizontal = if (style == AdBannerStyle.COMPACT_HEADER) 8.dp else 10.dp,
                        vertical = if (style == AdBannerStyle.COMPACT_HEADER) 6.dp else 8.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SPONSORED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.inkMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = {
                            activeBanner.adView.apply {
                                (parent as? ViewGroup)?.removeView(this)
                            }
                        },
                        update = { view ->
                            // View already attached cleanly; no per-recomposition work
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
