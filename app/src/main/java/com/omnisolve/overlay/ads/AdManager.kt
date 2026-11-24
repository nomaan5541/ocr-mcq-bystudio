package com.omnisolve.overlay.ads

import android.app.Activity
import android.content.Context

/**
 * Lightweight no-op stub for the permanently ad-free Prism Solver architecture.
 * Advertising SDKs (Google Mobile Ads / AdMob) are completely removed from the app.
 */
object AdManager {

    fun initialize(context: Context, onComplete: (() -> Unit)? = null) {
        onComplete?.invoke()
    }

    fun preloadInterstitial(context: Context) {
        // Permanently ad-free: no-op
    }

    fun preloadAppOpen(context: Context) {
        // Permanently ad-free: no-op
    }

    fun showInterstitial(activity: Activity, respectCooldown: Boolean = true, onDismiss: () -> Unit) {
        onDismiss()
    }

    fun showAppOpenIfReady(activity: Activity) {
        // Permanently ad-free: no-op
    }
}
