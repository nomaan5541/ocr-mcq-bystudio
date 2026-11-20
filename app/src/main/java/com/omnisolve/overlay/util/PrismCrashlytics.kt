package com.omnisolve.overlay.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.omnisolve.overlay.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Privacy-safe Firebase Crashlytics telemetry & background main-thread ANR stall watchdog.
 *
 * Diagnostic Hygiene:
 * - Only attaches non-PII diagnostic fields (`app_version`, `solver_method`, `failed_stage`, `ocr_status`).
 * - Never records screenshots, question text, answer strings, user emails, tokens, or API keys.
 * - Safe no-op when `FirebaseApp` is not initialized (e.g. unit tests / Robolectric).
 */
object PrismCrashlytics {

    private const val TAG = "PrismCrashlytics"
    const val KEY_APP_VERSION = "app_version"
    const val KEY_SOLVER_METHOD = "solver_method"
    const val KEY_FAILED_STAGE = "failed_stage"
    const val KEY_OCR_STATUS = "ocr_status"

    private val ALLOWED_KEYS = setOf(
        KEY_APP_VERSION,
        KEY_SOLVER_METHOD,
        KEY_FAILED_STAGE,
        KEY_OCR_STATUS
    )

    private val anrMonitorStarted = AtomicBoolean(false)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun initialize(context: Context) {
        val appCtx = context.applicationContext
        val crashlytics = getCrashlyticsOrNull(appCtx)
        if (crashlytics != null) {
            try {
                crashlytics.isCrashlyticsCollectionEnabled = true
                crashlytics.setCustomKey(KEY_APP_VERSION, safeVersionString())
            } catch (_: Throwable) {
            }
        }
        startAnrWatchdogIfNeeded()
    }

    /**
     * Pure helper returning a strictly redacted diagnostic map for solver failures.
     */
    fun buildSafeFailureContext(
        solverMethod: String,
        failedStage: String,
        ocrStatus: String = "UNKNOWN",
        appVersion: String = safeVersionString()
    ): Map<String, String> {
        val rawMap = mapOf(
            KEY_APP_VERSION to appVersion,
            KEY_SOLVER_METHOD to solverMethod,
            KEY_FAILED_STAGE to failedStage,
            KEY_OCR_STATUS to ocrStatus
        )
        return rawMap.filterKeys { it in ALLOWED_KEYS }
            .mapValues { (_, v) -> sanitizeDiagnosticValue(v) }
    }

    fun sanitizeDiagnosticValue(raw: String): String {
        val scrubbed = PrismPerfLogger.sanitizeDetail(raw)
        if (scrubbed.isBlank()) return "NONE"
        // Guard against accidental question/answer payload leaks inside stage strings
        return scrubbed
            .replace(Regex("""(?i)(question|prompt|answer|explanation|option[A-F]?)\s*[:=]\s*[^,;]+"""), "[REDACTED_CONTENT]")
            .take(64)
    }

    fun sanitizeThrowableSummary(throwable: Throwable): String {
        val type = throwable.javaClass.simpleName.ifBlank { "Exception" }
        val msg = sanitizeDiagnosticValue(throwable.message.orEmpty())
        return "$type: $msg"
    }

    fun recordNonFatalSolverFailure(
        context: Context? = null,
        solverMethod: String,
        failedStage: String,
        ocrStatus: String = "NONE",
        throwable: Throwable? = null
    ) {
        val safeContext = buildSafeFailureContext(
            solverMethod = solverMethod,
            failedStage = failedStage,
            ocrStatus = ocrStatus
        )
        val summary = throwable?.let { sanitizeThrowableSummary(it) }
            ?: "SolverFailure(method=${safeContext[KEY_SOLVER_METHOD]}, stage=${safeContext[KEY_FAILED_STAGE]})"

        PrismPerfLogger.logStage(
            sessionId = 0L,
            stage = "CRASHLYTICS_NONFATAL",
            durationMs = 0L,
            status = safeContext[KEY_FAILED_STAGE] ?: "FAILED",
            detail = "method=${safeContext[KEY_SOLVER_METHOD]} ocr=${safeContext[KEY_OCR_STATUS]} $summary"
        )

        val crashlytics = getCrashlyticsOrNull(context) ?: return
        try {
            safeContext.forEach { (k, v) -> crashlytics.setCustomKey(k, v) }
            crashlytics.log("stage=${safeContext[KEY_FAILED_STAGE]} method=${safeContext[KEY_SOLVER_METHOD]}")
            val recorded = throwable ?: IllegalStateException(summary)
            crashlytics.recordException(recorded)
        } catch (_: Throwable) {
        }
    }

    fun recordAnrStall(
        context: Context? = null,
        stallMs: Long,
        activeMethod: String = "UNKNOWN"
    ) {
        val safeMethod = sanitizeDiagnosticValue(activeMethod)
        PrismPerfLogger.logStage(
            sessionId = 0L,
            stage = "ANR_WATCHDOG",
            durationMs = stallMs,
            status = "MAIN_THREAD_STALL",
            detail = "method=$safeMethod"
        )
        val crashlytics = getCrashlyticsOrNull(context) ?: return
        try {
            crashlytics.setCustomKey(KEY_APP_VERSION, safeVersionString())
            crashlytics.setCustomKey(KEY_SOLVER_METHOD, safeMethod)
            crashlytics.setCustomKey(KEY_FAILED_STAGE, "MAIN_THREAD_STALL_${stallMs}MS")
            crashlytics.recordException(
                IllegalStateException("MainThreadStall(${stallMs}ms) during $safeMethod")
            )
        } catch (_: Throwable) {
        }
    }

    private fun startAnrWatchdogIfNeeded() {
        if (!anrMonitorStarted.compareAndSet(false, true)) return
        val thread = Thread({
            val tickAck = AtomicLong(0L)
            var lastReportedTick = -1L
            var seq = 0L
            while (!Thread.currentThread().isInterrupted) {
                val expected = ++seq
                val postedAt = SystemClock.elapsedRealtime()
                try {
                    mainHandler.post { tickAck.set(expected) }
                    Thread.sleep(5_000L)
                    if (tickAck.get() != expected && lastReportedTick != expected) {
                        val stallMs = (SystemClock.elapsedRealtime() - postedAt).coerceAtLeast(5_000L)
                        lastReportedTick = expected
                        recordAnrStall(context = null, stallMs = stallMs)
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (_: Throwable) {
                }
            }
        }, "PrismAnrWatchdog").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY + 1
        }
        try {
            thread.start()
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to start ANR watchdog: ${t.message}")
        }
    }

    private fun safeVersionString(): String {
        return try {
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        } catch (_: Throwable) {
            "unknown"
        }
    }

    private fun getCrashlyticsOrNull(context: Context?): FirebaseCrashlytics? {
        return try {
            if (context != null) {
                if (FirebaseApp.getApps(context.applicationContext).isEmpty()) return null
            } else {
                FirebaseApp.getInstance()
            }
            FirebaseCrashlytics.getInstance()
        } catch (_: Throwable) {
            null
        }
    }
}
