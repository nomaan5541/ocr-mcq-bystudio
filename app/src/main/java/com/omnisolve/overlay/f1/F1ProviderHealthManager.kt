package com.omnisolve.overlay.f1

import android.content.Context
import android.content.SharedPreferences
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

data class F1ProviderHealthSnapshot(
    val providerId: F1ProviderId,
    val averageLatencyMs: Long,
    val recentLatencyMs: Long,
    val successRatePct: Int,
    val totalRequests: Int,
    val failureCount: Int,
    val timeoutCount: Int,
    val quotaFailureCount: Int,
    val lastSuccessMs: Long,
    val cooldownUntilMs: Long,
    val isAvailable: Boolean
)

/**
 * Adaptive rolling-window provider intelligence for the Prism F1 Master Engine.
 *
 * Tracks per-provider latency, rolling success rate, timeouts, quota failures,
 * and failure-specific cooldowns. Never permanently disables a provider on a single
 * failure and persists aggregate metrics locally (zero PII).
 */
object F1ProviderHealthManager {

    private const val PREFS_NAME = "prism_f1_provider_health_v1"
    private const val ROLLING_WINDOW_SIZE = 20

    private const val COOLDOWN_AUTH_MS = 120_000L
    private const val COOLDOWN_QUOTA_MS = 90_000L
    private const val COOLDOWN_MODEL_UNAVAILABLE_MS = 60_000L
    private const val COOLDOWN_SERVER_MS = 25_000L
    private const val COOLDOWN_TRANSIENT_MS = 10_000L

    private data class RollingOutcome(
        val success: Boolean,
        val latencyMs: Long
    )

    private class ProviderState(
        val recentWindow: ArrayDeque<RollingOutcome> = ArrayDeque(),
        var totalRequests: Int = 0,
        var successCount: Int = 0,
        var failureCount: Int = 0,
        var consecutiveFailures: Int = 0,
        var timeoutCount: Int = 0,
        var quotaFailureCount: Int = 0,
        var lastSuccessMs: Long = 0L,
        var recentLatencyMs: Long = 0L,
        var persistedAvgLatencyMs: Long = 0L,
        var cooldownUntilMs: Long = 0L,
        var lastFailureKind: F1FailureKind? = null
    )

    private val states = ConcurrentHashMap<F1ProviderId, ProviderState>()

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    internal var clockMsProvider: () -> Long = { System.currentTimeMillis() }

    fun initialize(context: Context) {
        if (prefs != null) return
        val appPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = appPrefs
        loadPersistedStats(appPrefs)
    }

    private fun stateFor(id: F1ProviderId): ProviderState =
        states.computeIfAbsent(id) { ProviderState() }

    fun recordSuccess(providerId: F1ProviderId, latencyMs: Long) {
        val now = clockMsProvider()
        val safeLatency = latencyMs.coerceIn(1L, 60_000L)
        val st = stateFor(providerId)
        synchronized(st) {
            st.totalRequests++
            st.successCount++
            st.consecutiveFailures = 0
            st.lastSuccessMs = now
            st.recentLatencyMs = safeLatency
            st.cooldownUntilMs = 0L
            st.lastFailureKind = null
            st.recentWindow.addLast(RollingOutcome(success = true, latencyMs = safeLatency))
            while (st.recentWindow.size > ROLLING_WINDOW_SIZE) {
                st.recentWindow.removeFirst()
            }
            st.persistedAvgLatencyMs = computeAvgLatencyLocked(st)
        }
        persistProvider(providerId)
    }

    fun recordFailure(providerId: F1ProviderId, kind: F1FailureKind, latencyMs: Long = 0L) {
        val now = clockMsProvider()
        val st = stateFor(providerId)
        synchronized(st) {
            st.totalRequests++
            st.failureCount++
            st.consecutiveFailures++
            if (kind == F1FailureKind.TIMEOUT) st.timeoutCount++
            if (kind == F1FailureKind.QUOTA_ERROR) st.quotaFailureCount++
            st.recentWindow.addLast(RollingOutcome(success = false, latencyMs = latencyMs.coerceAtLeast(0L)))
            while (st.recentWindow.size > ROLLING_WINDOW_SIZE) {
                st.recentWindow.removeFirst()
            }
            // Separate cooldown policies — never permanently disable on a single transient failure
            val cooldownMs = when (kind) {
                F1FailureKind.AUTH_ERROR -> COOLDOWN_AUTH_MS
                F1FailureKind.QUOTA_ERROR -> COOLDOWN_QUOTA_MS
                F1FailureKind.MODEL_UNAVAILABLE -> COOLDOWN_MODEL_UNAVAILABLE_MS
                F1FailureKind.SERVER_ERROR -> if (st.consecutiveFailures >= 2) COOLDOWN_SERVER_MS else 0L
                F1FailureKind.TRANSIENT_NETWORK,
                F1FailureKind.TIMEOUT -> if (st.consecutiveFailures >= 2) COOLDOWN_TRANSIENT_MS else 0L
            }
            if (cooldownMs > 0L) {
                st.cooldownUntilMs = maxOf(st.cooldownUntilMs, now + cooldownMs)
                st.lastFailureKind = kind
            } else if (st.cooldownUntilMs <= now) {
                st.lastFailureKind = kind
            }
        }
        persistProvider(providerId)
    }

    fun isProviderAvailable(providerId: F1ProviderId): Boolean {
        val now = clockMsProvider()
        val st = stateFor(providerId)
        synchronized(st) {
            if (st.cooldownUntilMs > 0L && now >= st.cooldownUntilMs) {
                // Cooldown expired — allow recovery probe
                st.cooldownUntilMs = 0L
                st.consecutiveFailures = 0
                st.lastFailureKind = null
            }
            return st.cooldownUntilMs <= now
        }
    }

    fun getSnapshot(providerId: F1ProviderId): F1ProviderHealthSnapshot {
        val now = clockMsProvider()
        val st = stateFor(providerId)
        synchronized(st) {
            val available = st.cooldownUntilMs <= now
            return F1ProviderHealthSnapshot(
                providerId = providerId,
                averageLatencyMs = computeAvgLatencyLocked(st),
                recentLatencyMs = st.recentLatencyMs,
                successRatePct = computeSuccessRateLocked(st),
                totalRequests = st.totalRequests,
                failureCount = st.failureCount,
                timeoutCount = st.timeoutCount,
                quotaFailureCount = st.quotaFailureCount,
                lastSuccessMs = st.lastSuccessMs,
                cooldownUntilMs = if (available) 0L else st.cooldownUntilMs,
                isAvailable = available
            )
        }
    }

    fun getAllSnapshots(): List<F1ProviderHealthSnapshot> {
        val tracked = listOf(
            F1ProviderId.FIREBASE_GEMINI,
            F1ProviderId.CLOUDFLARE_WORKERS,
            F1ProviderId.ZERO_AUTH_ONLINE_AI,
            F1ProviderId.DIRECT_VISION,
            F1ProviderId.DUCKDUCKGO_SEARCH,
            F1ProviderId.PERSONAL_API_KEYS,
            F1ProviderId.GEMINI_WEBVIEW
        )
        return tracked.map { getSnapshot(it) }
    }

    /**
     * Ranks candidate providers using rolling latency, success rate, cooldown availability,
     * and question-category affinity so the healthiest & fastest provider launches at T = 0ms.
     */
    fun rankEligibleProviders(
        candidates: List<F1ProviderId>,
        category: F1QuestionCategory
    ): List<F1ProviderId> {
        val eligible = candidates.filter { isProviderAvailable(it) }
        if (eligible.size <= 1) return eligible

        return eligible.sortedBy { id ->
            val snap = getSnapshot(id)
            val baseLatency = when {
                snap.averageLatencyMs > 0L -> snap.averageLatencyMs.toDouble()
                id == F1ProviderId.FIREBASE_GEMINI -> 1_100.0
                id == F1ProviderId.PERSONAL_API_KEYS -> 1_850.0
                id == F1ProviderId.DIRECT_VISION -> 2_200.0
                id == F1ProviderId.CLOUDFLARE_WORKERS -> 3_600.0
                id == F1ProviderId.ZERO_AUTH_ONLINE_AI -> 4_800.0
                else -> 5_500.0
            }
            val reliabilityPenalty = ((100 - snap.successRatePct).coerceAtLeast(0)) * 35.0
            val categoryBonus = when {
                (category == F1QuestionCategory.DIAGRAM_BASED || category == F1QuestionCategory.IMAGE_BASED) &&
                    (id == F1ProviderId.DIRECT_VISION || id == F1ProviderId.FIREBASE_GEMINI) -> -850.0
                (category == F1QuestionCategory.MATHEMATICS || category == F1QuestionCategory.COMPLEX_REASONING) &&
                    id == F1ProviderId.FIREBASE_GEMINI -> -600.0
                (category == F1QuestionCategory.SIMPLE_FACTUAL_MCQ || category == F1QuestionCategory.TRUE_FALSE) &&
                    id == F1ProviderId.FIREBASE_GEMINI -> -250.0
                else -> 0.0
            }
            baseLatency + reliabilityPenalty + categoryBonus
        }
    }

    /**
     * Computes adaptive staggered launch delay for backup competitors based on the primary
     * provider's rolling health. Gives Firebase AI Logic clean headroom before launching
     * emergency backups, while waking backups immediately if primary fails early.
     */
    fun computeAdaptiveHedgeDelayMs(
        primaryProviderId: F1ProviderId,
        config: F1TimeoutConfig
    ): Long {
        val snap = getSnapshot(primaryProviderId)
        if (!snap.isAvailable) return 0L
        return when {
            snap.totalRequests >= 2 && snap.successRatePct < 65 -> config.minHedgeDelayMs
            snap.averageLatencyMs >= 4_500L -> config.minHedgeDelayMs
            snap.averageLatencyMs in 1L..2_400L && snap.successRatePct >= 90 -> config.maxHedgeDelayMs
            else -> ((config.minHedgeDelayMs + config.maxHedgeDelayMs) / 2L)
        }.coerceIn(config.minHedgeDelayMs, config.maxHedgeDelayMs)
    }

    /**
     * Clears transient cooldowns (network/timeout/server 5xx) when the user explicitly triggers
     * a manual retry, while preserving hard quota, auth, and unavailable-model cooldowns.
     */
    fun resetTransientCooldownsForManualRetry() {
        for (st in states.values) {
            synchronized(st) {
                when (st.lastFailureKind) {
                    F1FailureKind.TRANSIENT_NETWORK,
                    F1FailureKind.TIMEOUT,
                    F1FailureKind.SERVER_ERROR -> {
                        st.cooldownUntilMs = 0L
                        st.consecutiveFailures = 0
                        st.lastFailureKind = null
                    }
                    else -> Unit
                }
            }
        }
    }

    fun resetCooldownsAndStats() {
        states.clear()
        prefs?.edit()?.clear()?.apply()
    }

    fun resetForTest() {
        states.clear()
        prefs?.edit()?.clear()?.apply()
        clockMsProvider = { System.currentTimeMillis() }
    }

    private fun computeAvgLatencyLocked(st: ProviderState): Long {
        val latencies = st.recentWindow.filter { it.success && it.latencyMs > 0L }.map { it.latencyMs }
        return if (latencies.isNotEmpty()) {
            latencies.sum() / latencies.size
        } else {
            st.persistedAvgLatencyMs
        }
    }

    private fun computeSuccessRateLocked(st: ProviderState): Int {
        if (st.recentWindow.isEmpty()) {
            return if (st.totalRequests > 0) ((st.successCount * 100) / st.totalRequests).coerceIn(0, 100) else 100
        }
        val wins = st.recentWindow.count { it.success }
        return ((wins * 100) / st.recentWindow.size).coerceIn(0, 100)
    }

    private fun persistProvider(id: F1ProviderId) {
        val sp = prefs ?: return
        val snap = getSnapshot(id)
        val key = id.name.lowercase()
        sp.edit()
            .putLong("${key}_avg_ms", snap.averageLatencyMs)
            .putLong("${key}_recent_ms", snap.recentLatencyMs)
            .putInt("${key}_total", snap.totalRequests)
            .putInt("${key}_fail", snap.failureCount)
            .putInt("${key}_timeout", snap.timeoutCount)
            .putInt("${key}_quota", snap.quotaFailureCount)
            .putLong("${key}_last_ok", snap.lastSuccessMs)
            .apply()
    }

    private fun loadPersistedStats(sp: SharedPreferences) {
        for (id in F1ProviderId.entries) {
            val key = id.name.lowercase()
            val total = sp.getInt("${key}_total", 0)
            if (total <= 0) continue
            val st = stateFor(id)
            synchronized(st) {
                st.totalRequests = total
                st.failureCount = sp.getInt("${key}_fail", 0).coerceAtMost(total)
                st.successCount = (total - st.failureCount).coerceAtLeast(0)
                st.timeoutCount = sp.getInt("${key}_timeout", 0)
                st.quotaFailureCount = sp.getInt("${key}_quota", 0)
                st.lastSuccessMs = sp.getLong("${key}_last_ok", 0L)
                st.recentLatencyMs = sp.getLong("${key}_recent_ms", 0L)
                st.persistedAvgLatencyMs = sp.getLong("${key}_avg_ms", 0L)
            }
        }
    }
}
