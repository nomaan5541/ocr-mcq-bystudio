package com.omnisolve.overlay.util

import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight performance benchmark tracker logging cold launch, overlay initialization,
 * capture-to-OCR latency, and cache hits via `PRISM_PERF`.
 */
object PerformanceBenchmarkTracker {

    private val appProcessStartMs = AtomicLong(0L)
    private val coldLaunchDurationMs = AtomicLong(-1L)
    private val lastOverlayOpenMs = AtomicLong(-1L)
    private val lastCaptureToOcrMs = AtomicLong(-1L)
    private val cacheHitCount = AtomicLong(0L)

    fun markAppProcessStart(nowMs: Long = PrismPerfLogger.nowMs()) {
        appProcessStartMs.compareAndSet(0L, nowMs)
    }

    fun markColdLaunchReady(nowMs: Long = PrismPerfLogger.nowMs()): Long {
        val start = appProcessStartMs.get()
        val elapsed = if (start > 0L) (nowMs - start).coerceAtLeast(0L) else 0L
        if (coldLaunchDurationMs.compareAndSet(-1L, elapsed)) {
            PrismPerfLogger.logStage(
                sessionId = 0L,
                stage = "BENCH_COLD_LAUNCH",
                durationMs = elapsed,
                status = "OK"
            )
        }
        return coldLaunchDurationMs.get()
    }

    fun recordOverlayOpenLatency(startMs: Long, endMs: Long = PrismPerfLogger.nowMs()): Long {
        val elapsed = (endMs - startMs).coerceAtLeast(0L)
        lastOverlayOpenMs.set(elapsed)
        PrismPerfLogger.logStage(
            sessionId = 0L,
            stage = "BENCH_OVERLAY_OPEN",
            durationMs = elapsed,
            status = "OK"
        )
        return elapsed
    }

    fun recordCaptureToOcrLatency(sessionId: Long, durationMs: Long, cacheHit: Boolean): Long {
        val clamped = durationMs.coerceAtLeast(0L)
        lastCaptureToOcrMs.set(clamped)
        if (cacheHit) {
            cacheHitCount.incrementAndGet()
        }
        PrismPerfLogger.logStage(
            sessionId = sessionId,
            stage = "BENCH_CAPTURE_TO_OCR",
            durationMs = clamped,
            status = if (cacheHit) "CACHE_HIT" else "LIVE_OCR"
        )
        return clamped
    }

    fun getColdLaunchMs(): Long = coldLaunchDurationMs.get()
    fun getLastOverlayOpenMs(): Long = lastOverlayOpenMs.get()
    fun getLastCaptureToOcrMs(): Long = lastCaptureToOcrMs.get()
    fun getCacheHitCount(): Long = cacheHitCount.get()
}
