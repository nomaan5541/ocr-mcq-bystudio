package com.omnisolve.overlay.ads

/**
 * Ad-Free Architecture Stub.
 *
 * Advertising is permanently disabled across Prism Solver. All ad flags evaluate
 * to false and no third-party ad SDKs or ad unit IDs are bundled in the APK/AAB.
 */
object AdConfig {
    /** Permanently false: Prism Solver ships 100% ad-free. */
    const val ADS_ENABLED: Boolean = false

    val BANNER_AD_UNIT_ID: String = ""

    val INTERSTITIAL_ENABLED: Boolean = false

    val APP_OPEN_ENABLED: Boolean = false

    val INTERSTITIAL_AD_UNIT_ID: String = ""

    val APP_OPEN_AD_UNIT_ID: String = ""
}
