package com.omnisolve.overlay.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Non-intrusive Google AdMob manager.
 *
 * Ads are preloaded asynchronously and displayed ONLY after completed actions
 * (never during active OCR/AI solving or floating overlay interactions).
 */
object AdManager {
    private const val TAG = "PrismAdManager"
    private const val INTERSTITIAL_COOLDOWN_MS = 45_000L
    private const val APP_OPEN_COOLDOWN_MS = 120_000L

    @Volatile
    private var isInitialized = false

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var isInterstitialLoading = false

    @Volatile
    private var appOpenAd: AppOpenAd? = null

    @Volatile
    private var isAppOpenLoading = false

    @Volatile
    private var lastInterstitialShownAtMs = 0L

    @Volatile
    private var lastAppOpenShownAtMs = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun initialize(context: Context, onComplete: (() -> Unit)? = null) {
        if (!AdConfig.ADS_ENABLED) {
            onComplete?.invoke()
            return
        }
        if (isInitialized) {
            onComplete?.invoke()
            return
        }
        val appContext = context.applicationContext
        Thread {
            try {
                MobileAds.initialize(appContext) {
                    isInitialized = true
                    mainHandler.post {
                        preloadInterstitial(appContext)
                        preloadAppOpen(appContext)
                        onComplete?.invoke()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "AdMob initialization failed gracefully: ${t.message}")
                mainHandler.post { onComplete?.invoke() }
            }
        }.start()
    }

    fun preloadInterstitial(context: Context) {
        if (!AdConfig.INTERSTITIAL_ENABLED || isInterstitialLoading || interstitialAd != null) return
        isInterstitialLoading = true
        val appContext = context.applicationContext
        mainHandler.post {
            try {
                InterstitialAd.load(
                    appContext,
                    AdConfig.INTERSTITIAL_AD_UNIT_ID,
                    AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            interstitialAd = ad
                            isInterstitialLoading = false
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            interstitialAd = null
                            isInterstitialLoading = false
                            Log.d(TAG, "Interstitial load skipped: ${error.message}")
                        }
                    }
                )
            } catch (t: Throwable) {
                isInterstitialLoading = false
                Log.w(TAG, "Interstitial load error: ${t.message}")
            }
        }
    }

    fun preloadAppOpen(context: Context) {
        if (!AdConfig.APP_OPEN_ENABLED || isAppOpenLoading || appOpenAd != null) return
        isAppOpenLoading = true
        val appContext = context.applicationContext
        mainHandler.post {
            try {
                AppOpenAd.load(
                    appContext,
                    AdConfig.APP_OPEN_AD_UNIT_ID,
                    AdRequest.Builder().build(),
                    object : AppOpenAd.AppOpenAdLoadCallback() {
                        override fun onAdLoaded(ad: AppOpenAd) {
                            appOpenAd = ad
                            isAppOpenLoading = false
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            appOpenAd = null
                            isAppOpenLoading = false
                            Log.d(TAG, "AppOpen load skipped: ${error.message}")
                        }
                    }
                )
            } catch (t: Throwable) {
                isAppOpenLoading = false
                Log.w(TAG, "AppOpen load error: ${t.message}")
            }
        }
    }

    /**
     * Shows an interstitial only after a user function is completed and cooldown has elapsed.
     * Immediately invokes [onDismiss] if no ad is ready or cooldown is active.
     */
    fun showInterstitial(activity: Activity, respectCooldown: Boolean = true, onDismiss: () -> Unit) {
        if (!AdConfig.INTERSTITIAL_ENABLED || activity.isFinishing || activity.isDestroyed) {
            onDismiss()
            return
        }
        val now = System.currentTimeMillis()
        if (respectCooldown && (now - lastInterstitialShownAtMs) < INTERSTITIAL_COOLDOWN_MS) {
            onDismiss()
            return
        }
        val ad = interstitialAd
        if (ad == null) {
            preloadInterstitial(activity)
            onDismiss()
            return
        }
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                lastInterstitialShownAtMs = System.currentTimeMillis()
                preloadInterstitial(activity)
                onDismiss()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preloadInterstitial(activity)
                onDismiss()
            }
        }
        try {
            ad.show(activity)
        } catch (_: Throwable) {
            onDismiss()
        }
    }

    fun showAppOpenIfReady(activity: Activity) {
        if (!AdConfig.APP_OPEN_ENABLED || activity.isFinishing || activity.isDestroyed) return
        val now = System.currentTimeMillis()
        if ((now - lastAppOpenShownAtMs) < APP_OPEN_COOLDOWN_MS) return
        val ad = appOpenAd ?: run {
            preloadAppOpen(activity)
            return
        }
        appOpenAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                lastAppOpenShownAtMs = System.currentTimeMillis()
                preloadAppOpen(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preloadAppOpen(activity)
            }
        }
        try {
            ad.show(activity)
        } catch (_: Throwable) {
            // Non-blocking
        }
    }
}
