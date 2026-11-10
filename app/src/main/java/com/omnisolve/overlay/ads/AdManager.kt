package com.omnisolve.overlay.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"

    @Volatile
    private var isInitialized = false

    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false

    /**
     * Initializes Google Mobile Ads SDK once per process.
     * Strictly called from MainActivity (in-app UI only).
     */
    fun initialize(context: Context, onComplete: (() -> Unit)? = null) {
        if (isInitialized) {
            onComplete?.invoke()
            return
        }

        try {
            MobileAds.initialize(context) { status ->
                isInitialized = true
                Log.d(TAG, "AdMob MobileAds initialized successfully: $status")
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MobileAds SDK", e)
        }
    }

    /**
     * Preloads an interstitial ad for in-app transitions.
     * Never shown while the floating overlay is over other apps.
     */
    fun preloadInterstitial(context: Context) {
        if (!AdConfig.ADS_ENABLED || isInterstitialLoading || interstitialAd != null) return

        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            AdConfig.INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    Log.d(TAG, "AdMob Interstitial loaded successfully")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    Log.w(TAG, "AdMob Interstitial failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Displays the preloaded interstitial ad if available.
     * Invokes onDismiss callback after the ad finishes or if no ad is ready.
     */
    fun showInterstitial(activity: Activity, onDismiss: () -> Unit) {
        if (!AdConfig.ADS_ENABLED) {
            onDismiss()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    preloadInterstitial(activity)
                    onDismiss()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    preloadInterstitial(activity)
                    onDismiss()
                }
            }
            ad.show(activity)
        } else {
            preloadInterstitial(activity)
            onDismiss()
        }
    }
}
