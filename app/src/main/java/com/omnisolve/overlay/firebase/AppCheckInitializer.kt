package com.omnisolve.overlay.firebase

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import com.omnisolve.overlay.BuildConfig
import com.omnisolve.overlay.util.PrismPerfLogger

private const val TAG = "AppCheckInit"

// Contract keys defined by the Firebase App Check SDK and runtime harness.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

private val initLock = Any()

@Volatile
private var providerInstalled = false

@Volatile
private var lastProviderStatus: String = "UNINITIALIZED"

fun getAppCheckDiagnosticStatus(): String = lastProviderStatus

/**
 * Configures Firebase App Check before any UI, ViewModel, or Firebase AI / Firestore calls.
 * Safe, thread-safe, and idempotent across `MainActivity.onCreate()` and `OverlayService.onCreate()`.
 */
@SuppressLint("ApplySharedPref", "DiscouragedApi")
fun configureAppCheck(context: Context, intent: Intent?) {
    val startMs = PrismPerfLogger.nowMs()
    synchronized(initLock) {
        try {
            val appContext = context.applicationContext ?: context
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext)
            }
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                lastProviderStatus = "FIREBASE_NOT_INITIALIZED"
                Log.w(TAG, "FirebaseApp could not be initialized yet.")
                PrismPerfLogger.logStage(
                    0L,
                    "APP_CHECK_INIT",
                    PrismPerfLogger.nowMs() - startMs,
                    status = "SKIPPED",
                    detail = lastProviderStatus
                )
                return
            }

            if (BuildConfig.DEBUG) {
                val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
                if (!debugToken.isNullOrEmpty()) {
                    val persistenceKey = FirebaseApp.getInstance().persistenceKey
                    val prefs = appContext.getSharedPreferences(
                        "$DEBUG_STORE_PREFIX$persistenceKey",
                        Context.MODE_PRIVATE
                    )
                    // Synchronous commit() is required so DebugAppCheckProviderFactory reads the token immediately.
                    prefs.edit()
                        .putString(DEBUG_SECRET_KEY, debugToken)
                        .commit()
                    Log.d(TAG, "App Check debug secret configured from launch extra.")
                }

                if (!providerInstalled) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    providerInstalled = true
                    lastProviderStatus = "DEBUG_PROVIDER_INSTALLED"
                    Log.d(TAG, "DebugAppCheckProviderFactory installed.")
                    PrismPerfLogger.logStage(
                        0L,
                        "APP_CHECK_INIT",
                        PrismPerfLogger.nowMs() - startMs,
                        status = "OK",
                        detail = if (debugToken.isNullOrEmpty()) "debug_provider_local" else "debug_provider_harness"
                    )
                }
            } else if (!providerInstalled) {
                val siteKey = try {
                    appContext.getString(com.omnisolve.overlay.R.string.recaptcha_site_key).trim()
                } catch (_: Exception) {
                    val resId = appContext.resources.getIdentifier(
                        RECAPTCHA_SITE_KEY_RES,
                        "string",
                        appContext.packageName
                    )
                    if (resId != 0) appContext.getString(resId).trim() else ""
                }
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    providerInstalled = true
                    lastProviderStatus = "RECAPTCHA_PROVIDER_INSTALLED"
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                    PrismPerfLogger.logStage(
                        0L,
                        "APP_CHECK_INIT",
                        PrismPerfLogger.nowMs() - startMs,
                        status = "OK",
                        detail = "recaptcha_provider"
                    )
                } else {
                    lastProviderStatus = "RECAPTCHA_SITE_KEY_BLANK"
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                    PrismPerfLogger.logStage(
                        0L,
                        "APP_CHECK_INIT",
                        PrismPerfLogger.nowMs() - startMs,
                        status = "FAILED",
                        detail = lastProviderStatus
                    )
                }
            }
        } catch (e: Exception) {
            lastProviderStatus = "INIT_ERROR:${e.javaClass.simpleName}"
            Log.e(TAG, "Failed to initialize Firebase App Check", e)
            PrismPerfLogger.logStage(
                0L,
                "APP_CHECK_INIT",
                PrismPerfLogger.nowMs() - startMs,
                status = "ERROR",
                detail = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }
}
