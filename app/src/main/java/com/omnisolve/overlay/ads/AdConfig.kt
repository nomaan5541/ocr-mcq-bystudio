package com.omnisolve.overlay.ads

import com.omnisolve.overlay.BuildConfig

/**
 * Google AdMob Central Configuration.
 *
 * - AdMob App ID (AndroidManifest.xml): ca-app-pub-3665831670411159~8704985821
 * - Banner Ad Unit (Prism_Banner): ca-app-pub-3665831670411159/4574169125
 * - Interstitial Ad Unit (Prism_Interstitial): ca-app-pub-3665831670411159/7008760774
 * - App Open Ad Unit (Prism_App_Open): ca-app-pub-3665831670411159/6406125542
 * - app-ads.txt: google.com, pub-3665831670411159, DIRECT, f08c47fec0942fa0
 */
object AdConfig {
    const val ADS_ENABLED: Boolean = true

    private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_APP_OPEN_ID = "ca-app-pub-3940256099942544/9257395921"

    private const val PROD_BANNER_ID = "ca-app-pub-3665831670411159/4574169125"
    private const val PROD_INTERSTITIAL_ID = "ca-app-pub-3665831670411159/7008760774"
    private const val PROD_APP_OPEN_ID = "ca-app-pub-3665831670411159/6406125542"

    val BANNER_AD_UNIT_ID: String
        get() = if (BuildConfig.DEBUG) TEST_BANNER_ID else PROD_BANNER_ID

    val INTERSTITIAL_ENABLED: Boolean
        get() = ADS_ENABLED && (BuildConfig.DEBUG || PROD_INTERSTITIAL_ID.isNotBlank())

    val APP_OPEN_ENABLED: Boolean
        get() = ADS_ENABLED && (BuildConfig.DEBUG || PROD_APP_OPEN_ID.isNotBlank())

    val INTERSTITIAL_AD_UNIT_ID: String
        get() = if (BuildConfig.DEBUG) TEST_INTERSTITIAL_ID else PROD_INTERSTITIAL_ID

    val APP_OPEN_AD_UNIT_ID: String
        get() = if (BuildConfig.DEBUG) TEST_APP_OPEN_ID else PROD_APP_OPEN_ID
}
