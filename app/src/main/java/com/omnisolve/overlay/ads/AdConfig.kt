package com.omnisolve.overlay.ads

/**
 * Google AdMob Central Configuration
 *
 * NOTE FOR PRODUCTION PLAY STORE RELEASE:
 * 1. Create your app in Google AdMob Console (https://admob.google.com/v2/apps/list).
 * 2. Put your real AdMob App ID in AndroidManifest.xml:
 *      <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" />
 * 3. Replace the test ad unit IDs below with your production Ad Unit IDs.
 *
 * Current defaults use official Google sample test ad units to prevent account suspension
 * during development and testing.
 */
object AdConfig {
    /** Global master switch to enable/disable ads in the app */
    const val ADS_ENABLED: Boolean = true

    /**
     * Google AdMob Banner Ad Unit ID (Prism_Main_Banner)
     */
    const val BANNER_AD_UNIT_ID: String = "ca-app-pub-6628813367564084/4121394511"

    /**
     * Google AdMob Interstitial Ad Unit ID
     * Test ID: "ca-app-pub-3940256099942544/1033173712"
     * Replace with: "ca-app-pub-XXXXXXXXXXXXXXXX/WWWWWWWWWW"
     */
    const val INTERSTITIAL_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/1033173712"

    /**
     * Google AdMob App Open Ad Unit ID (Optional)
     * Test ID: "ca-app-pub-3940256099942544/9257395921"
     */
    const val APP_OPEN_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/9257395921"
}
