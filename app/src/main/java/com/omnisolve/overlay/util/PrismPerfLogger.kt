package com.omnisolve.overlay.util

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * Structured, monotonic performance and lifecycle logger tagged with `PRISM_PERF`.
 *
 * Designed for physical-device Logcat inspection (`adb logcat -s PRISM_PERF`) without ever
 * leaking API keys, auth/App Check tokens, PII, or full screen/question bodies.
 */
object PrismPerfLogger {

    const val TAG = "PRISM_PERF"

    private val sessionSeq = AtomicLong(1000L)

    fun nextSessionId(): Long = sessionSeq.incrementAndGet()

    fun nowMs(): Long = SystemClock.elapsedRealtime()

    fun logStage(
        sessionId: Long,
        stage: String,
        durationMs: Long = 0L,
        status: String = "OK",
        detail: String = ""
    ) {
        val safeStage = stage.trim().uppercase().take(40)
        val safeStatus = status.trim().uppercase().take(32)
        val safeDetail = sanitizeDetail(detail)
        val msg = buildString {
            append("session=")
            append(sessionId)
            append(" stage=")
            append(safeStage)
            append(" ms=")
            append(durationMs.coerceAtLeast(0L))
            append(" status=")
            append(safeStatus)
            if (safeDetail.isNotEmpty()) {
                append(" detail=\"")
                append(safeDetail)
                append('"')
            }
        }
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {
            // Safe fallback when invoked in pure JVM context
        }
    }

    internal fun sanitizeDetail(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(Regex("""AIza[0-9A-Za-z\-_]{12,}"""), "[REDACTED_KEY]")
            .replace(Regex("""sk-[0-9A-Za-z\-_]{12,}"""), "[REDACTED_KEY]")
            .replace(Regex("""nvapi-[0-9A-Za-z\-_]{12,}"""), "[REDACTED_KEY]")
            .replace(Regex("""eyJ[0-9A-Za-z\-_]+\.[0-9A-Za-z\-_]+(?:\.[0-9A-Za-z\-_]*)?"""), "[REDACTED_JWT]")
            .replace(Regex("""(?i)bearer\s+[A-Za-z0-9\-._~+/]+=*"""), "Bearer [REDACTED]")
            .replace(Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}"""), "[REDACTED_EMAIL]")
            .replace('"', '\'')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .take(160)
    }
}
