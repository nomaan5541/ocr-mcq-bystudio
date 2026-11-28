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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Non-intrusive Google AdMob manager supporting Banner, Interstitial, App Open, and Rewarded Ads.
 *
 * Ads are preloaded asynchronously and support on-demand fetching for Rewarded Ads so tapping
 * "Watch Ad" always attempts a fresh load even if the initial preload failed or had no fill yet.
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
    private var rewardedAd: RewardedAd? = null

    @Volatile
    private var isRewardedLoading = false

    @Volatile
    private var lastInterstitialShownAtMs = 0L

    @Volatile
    private var lastAppOpenShownAtMs = 0L

    private val _isRewardedAdReady = MutableStateFlow(false)
    val isRewardedAdReady: StateFlow<Boolean> = _isRewardedAdReady.asStateFlow()

    private val _isRewardedAdLoading = MutableStateFlow(false)
    val isRewardedAdLoading: StateFlow<Boolean> = _isRewardedAdLoading.asStateFlow()

    private val _lastRewardedError = MutableStateFlow<String?>(null)
    val lastRewardedError: StateFlow<String?> = _lastRewardedError.asStateFlow()

    private val _lastBannerStatus = MutableStateFlow<String?>(null)
    val lastBannerStatus: StateFlow<String?> = _lastBannerStatus.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    internal fun updateBannerStatus(status: String?) {
        _lastBannerStatus.value = status
    }

    fun initialize(context: Context, onComplete: (() -> Unit)? = null) {
        if (!AdConfig.ADS_ENABLED) {
            onComplete?.invoke()
            return
        }
        if (isInitialized) {
            preloadInterstitial(context)
            preloadAppOpen(context)
            preloadRewarded(context)
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
                        preloadRewarded(appContext)
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
                            Log.d(TAG, "Interstitial load skipped (${error.code}): ${error.message}")
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
                            Log.d(TAG, "AppOpen load skipped (${error.code}): ${error.message}")
                        }
                    }
                )
            } catch (t: Throwable) {
                isAppOpenLoading = false
                Log.w(TAG, "AppOpen load error: ${t.message}")
            }
        }
    }

    fun preloadRewarded(context: Context) {
        if (!AdConfig.REWARDED_ENABLED || isRewardedLoading || rewardedAd != null) return
        isRewardedLoading = true
        _isRewardedAdLoading.value = true
        val appContext = context.applicationContext
        mainHandler.post {
            try {
                RewardedAd.load(
                    appContext,
                    AdConfig.REWARDED_AD_UNIT_ID,
                    AdRequest.Builder().build(),
                    object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedAd) {
                            rewardedAd = ad
                            isRewardedLoading = false
                            _isRewardedAdLoading.value = false
                            _isRewardedAdReady.value = true
                            _lastRewardedError.value = null
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            rewardedAd = null
                            isRewardedLoading = false
                            _isRewardedAdLoading.value = false
                            _isRewardedAdReady.value = false
                            _lastRewardedError.value = formatAdError(error)
                            Log.w(TAG, "Rewarded load failed (${error.code}): ${error.message}")
                        }
                    }
                )
            } catch (t: Throwable) {
                isRewardedLoading = false
                _isRewardedAdLoading.value = false
                _isRewardedAdReady.value = false
                _lastRewardedError.value = t.message ?: "Rewarded load error"
                Log.w(TAG, "Rewarded load error: ${t.message}")
            }
        }
    }

    private fun formatAdError(error: LoadAdError): String {
        return when (error.code) {
            AdRequest.ERROR_CODE_NO_FILL ->
                "AdMob has no ad fill yet (Code 3). New ad units or pending Play Store apps take a little time to fill — tap Retry soon."
            AdRequest.ERROR_CODE_NETWORK_ERROR ->
                "Network error loading ad (Code 2). Check internet connection."
            AdRequest.ERROR_CODE_INTERNAL_ERROR ->
                "AdMob temporary error (Code 0). Tap Retry."
            else ->
                "${error.message} (Code ${error.code})"
        }
    }

    /**
     * Shows an interstitial only after a user function is completed and cooldown has elapsed.
     */
    fun showInterstitial(activity: Activity, respectCooldown: Boolean = true, onDismiss: () -> Unit = {}) {
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

    /**
     * Shows a Rewarded Video Ad immediately if cached, or fetches one on-demand and opens it
     * as soon as it finishes loading.
     */
    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: (amount: Int, type: String) -> Unit,
        onClosedWithoutReward: () -> Unit = {},
        onUnavailable: (reason: String) -> Unit = {}
    ) {
        if (!AdConfig.REWARDED_ENABLED || activity.isFinishing || activity.isDestroyed) {
            onUnavailable("Ads disabled or screen unavailable.")
            return
        }
        val cached = rewardedAd
        if (cached != null) {
            presentRewardedAd(activity, cached, onRewardEarned, onClosedWithoutReward, onUnavailable)
            return
        }

        // Load on-demand and auto-present once ready
        _isRewardedAdLoading.value = true
        isRewardedLoading = true
        _lastRewardedError.value = null

        val startLoad = {
            mainHandler.post {
                try {
                    RewardedAd.load(
                        activity.applicationContext,
                        AdConfig.REWARDED_AD_UNIT_ID,
                        AdRequest.Builder().build(),
                        object : RewardedAdLoadCallback() {
                            override fun onAdLoaded(ad: RewardedAd) {
                                isRewardedLoading = false
                                _isRewardedAdLoading.value = false
                                _isRewardedAdReady.value = true
                                _lastRewardedError.value = null
                                if (!activity.isFinishing && !activity.isDestroyed) {
                                    presentRewardedAd(activity, ad, onRewardEarned, onClosedWithoutReward, onUnavailable)
                                } else {
                                    rewardedAd = ad
                                }
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                rewardedAd = null
                                isRewardedLoading = false
                                _isRewardedAdLoading.value = false
                                _isRewardedAdReady.value = false
                                val reason = formatAdError(error)
                                _lastRewardedError.value = reason
                                onUnavailable(reason)
                            }
                        }
                    )
                } catch (t: Throwable) {
                    isRewardedLoading = false
                    _isRewardedAdLoading.value = false
                    _isRewardedAdReady.value = false
                    val reason = t.message ?: "Failed to load rewarded ad."
                    _lastRewardedError.value = reason
                    onUnavailable(reason)
                }
            }
        }

        if (!isInitialized) {
            initialize(activity) { startLoad() }
        } else {
            startLoad()
        }
    }

    private fun presentRewardedAd(
        activity: Activity,
        ad: RewardedAd,
        onRewardEarned: (amount: Int, type: String) -> Unit,
        onClosedWithoutReward: () -> Unit,
        onUnavailable: (reason: String) -> Unit
    ) {
        rewardedAd = null
        _isRewardedAdReady.value = false
        var rewardGranted = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preloadRewarded(activity)
                if (!rewardGranted) {
                    onClosedWithoutReward()
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preloadRewarded(activity)
                onUnavailable("Ad failed to display: ${error.message} (Code ${error.code})")
            }
        }
        try {
            ad.show(activity) { rewardItem ->
                rewardGranted = true
                onRewardEarned(rewardItem.amount, rewardItem.type)
            }
        } catch (t: Throwable) {
            preloadRewarded(activity)
            onUnavailable(t.message ?: "Unable to show ad.")
        }
    }

    fun showAppOpenIfReady(activity: Activity) {
        if (!isInitialized || !AdConfig.APP_OPEN_ENABLED || activity.isFinishing || activity.isDestroyed) return
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
