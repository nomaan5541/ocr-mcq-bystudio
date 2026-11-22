package com.omnisolve.overlay.f1

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Records real, measured timings for every stage and provider inside the Prism F1 Master Engine.
 * Exposes live reactive StateFlows for the in-app F1 Diagnostics dashboard.
 * Never fabricates synthetic latencies.
 */
object F1PerformanceMonitor {

    private const val MAX_HISTORY = 25

    private val _recentRaces = MutableStateFlow<List<F1RaceReport>>(emptyList())
    val recentRaces: StateFlow<List<F1RaceReport>> = _recentRaces.asStateFlow()

    private val _latestRace = MutableStateFlow<F1RaceReport?>(null)
    val latestRace: StateFlow<F1RaceReport?> = _latestRace.asStateFlow()

    fun recordRace(report: F1RaceReport) {
        _latestRace.value = report
        val updated = (listOf(report) + _recentRaces.value).take(MAX_HISTORY)
        _recentRaces.value = updated
    }

    fun formatReportSummary(report: F1RaceReport): String = buildString {
        appendLine("F1 RACE RESULTS (#${report.requestId})")
        if (report.captureMs > 0L) {
            appendLine("Capture: ${report.captureMs} ms")
        }
        if (report.ocrMs > 0L) {
            appendLine("OCR: ${report.ocrMs} ms · SUCCESS")
        }
        if (report.cacheHit) {
            appendLine("Cache Lookup: ${report.cacheLookupMs} ms · HIT")
        } else if (report.cacheLookupMs > 0L) {
            appendLine("Cache Lookup: ${report.cacheLookupMs} ms · MISS")
        }
        if (report.validationMs > 0L || report.providerMetrics.isNotEmpty()) {
            appendLine("Validation: ${report.validationMs} ms")
        }
        report.providerMetrics.forEach { metric ->
            val detailSuffix = if (metric.detail.isNotBlank()) " (${metric.detail})" else ""
            appendLine("${metric.providerName}: ${metric.latencyMs} ms · ${metric.status.name}$detailSuffix")
        }
        if (report.winnerProviderId != null) {
            appendLine("WINNER: ${report.winnerProviderName}")
        } else if (report.failureCategory != F1FailureCategory.NONE) {
            appendLine("STATUS: ${report.failureCategory.name}")
        } else {
            appendLine("WINNER: ${report.winnerProviderName}")
        }
        append("TOTAL TIME: ${report.totalMs} ms")
    }

    fun clearForTest() {
        _latestRace.value = null
        _recentRaces.value = emptyList()
    }
}
