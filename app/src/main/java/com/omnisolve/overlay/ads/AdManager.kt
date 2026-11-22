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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AdManager {
    private const val TAG = "AdManager"
    private const val INTERSTITIAL_COOLDOWN_MS = 180_000L // Max 1 interstitial every 3 minutes
    private const val APP_OPEN_COOLDOWN_MS = 240_000L     // Max 1 app open every 4 minutes
    private const val APP_OPEN_EXPIRY_MS = 4L * 3_600_000L // AdMob App Open ads expire after 4 hours

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isInitializing = false

    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var lastInterstitialShownMs: Long = 0L

    private var appOpenAd: AppOpenAd? = null
    private var appOpenLoadTimeMs: Long = 0L
    private var isAppOpenLoading = false
    private var lastAppOpenShownMs: Long = 0L
    private var isShowingFullScreenAd = false

    /**
     * Initializes Google Mobile Ads SDK off the main thread once per process
     * so cold startup and first-frame rendering stay silky smooth with zero jank.
     */
    fun initialize(context: Context, onComplete: (() -> Unit)? = null) {
        if (!AdConfig.ADS_ENABLED) return
        if (isInitialized) {
            onComplete?.invoke()
            return
        }
        if (isInitializing) return
        isInitializing = true

        val appCtx = context.applicationContext
        initScope.launch {
            try {
                MobileAds.initialize(appCtx) { status ->
                    isInitialized = true
                    isInitializing = false
                    Log.d(TAG, "AdMob MobileAds initialized off-main-thread: $status")
                    mainHandler.post {
                        if (AdConfig.INTERSTITIAL_ENABLED) {
                            preloadInterstitial(appCtx)
                        }
                        if (AdConfig.APP_OPEN_ENABLED) {
                            preloadAppOpen(appCtx)
                        }
                        onComplete?.invoke()
                    }
                }
            } catch (e: Exception) {
                isInitializing = false
                Log.e(TAG, "Failed to initialize MobileAds SDK", e)
            }
        }
    }

    /**
     * Preloads an interstitial ad only when enabled with a valid unit ID.
     * Never shown while the floating overlay is over other apps.
     */
    fun preloadInterstitial(context: Context) {
        if (!AdConfig.INTERSTITIAL_ENABLED || isInterstitialLoading || interstitialAd != null) return
        val unitId = AdConfig.INTERSTITIAL_AD_UNIT_ID
        if (unitId.isBlank()) return

        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()

        runCatching {
            InterstitialAd.load(
                context,
                unitId,
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
        }.onFailure {
            isInterstitialLoading = false
        }
    }

    /**
     * Preloads an App Open ad only when enabled with a valid unit ID.
     */
    fun preloadAppOpen(context: Context) {
        if (!AdConfig.APP_OPEN_ENABLED || isAppOpenLoading || appOpenAd != null) return
        val unitId = AdConfig.APP_OPEN_AD_UNIT_ID
        if (unitId.isBlank()) return

        isAppOpenLoading = true
        val adRequest = AdRequest.Builder().build()

        runCatching {
            AppOpenAd.load(
                context,
                unitId,
                adRequest,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAd = ad
                        appOpenLoadTimeMs = System.currentTimeMillis()
                        isAppOpenLoading = false
                        Log.d(TAG, "AdMob AppOpen loaded successfully")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        appOpenAd = null
                        appOpenLoadTimeMs = 0L
                        isAppOpenLoading = false
                        Log.w(TAG, "AdMob AppOpen failed to load: ${loadAdError.message}")
                    }
                }
            )
        }.onFailure {
            isAppOpenLoading = false
        }
    }

    /**
     * Displays the preloaded interstitial ad if available and outside cooldown.
     * Invokes onDismiss callback after the ad finishes or if skipped.
     */
    fun showInterstitial(activity: Activity, respectCooldown: Boolean = true, onDismiss: () -> Unit) {
        if (!AdConfig.INTERSTITIAL_ENABLED || isShowingFullScreenAd || activity.isFinishing || activity.isDestroyed) {
            onDismiss()
            return
        }
        val now = System.currentTimeMillis()
        if (respectCooldown && (now - lastInterstitialShownMs) < INTERSTITIAL_COOLDOWN_MS) {
            onDismiss()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            isShowingFullScreenAd = true
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    isShowingFullScreenAd = false
                    lastInterstitialShownMs = System.currentTimeMillis()
                    interstitialAd = null
                    preloadInterstitial(activity.applicationContext)
                    onDismiss()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    isShowingFullScreenAd = false
                    interstitialAd = null
                    preloadInterstitial(activity.applicationContext)
                    onDismiss()
                }
            }
            ad.show(activity)
        } else {
            preloadInterstitial(activity.applicationContext)
            onDismiss()
        }
    }

    /**
     * Displays an App Open ad on foreground return if ready and cooldown has elapsed.
     */
    fun showAppOpenIfReady(activity: Activity) {
        if (!AdConfig.APP_OPEN_ENABLED || isShowingFullScreenAd || activity.isFinishing || activity.isDestroyed) return
        val now = System.currentTimeMillis()
        if ((now - lastAppOpenShownMs) < APP_OPEN_COOLDOWN_MS) return

        val ad = appOpenAd
        val isFresh = ad != null && (now - appOpenLoadTimeMs) < APP_OPEN_EXPIRY_MS
        if (ad != null && isFresh) {
            isShowingFullScreenAd = true
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    isShowingFullScreenAd = false
                    lastAppOpenShownMs = System.currentTimeMillis()
                    appOpenAd = null
                    appOpenLoadTimeMs = 0L
                    preloadAppOpen(activity.applicationContext)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    isShowingFullScreenAd = false
                    appOpenAd = null
                    appOpenLoadTimeMs = 0L
                    preloadAppOpen(activity.applicationContext)
                }
            }
            ad.show(activity)
        } else {
            if (ad != null && !isFresh) {
                appOpenAd = null
                appOpenLoadTimeMs = 0L
            }
            preloadAppOpen(activity.applicationContext)
        }
    }
}
