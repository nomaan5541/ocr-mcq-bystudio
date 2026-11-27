package com.omnisolve.overlay.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Centralized shared OkHttp networking hub for Prism Solver.
 *
 * Consolidating all AI, Forms, Consensus, Search, and Cache engines onto a single
 * underlying [ConnectionPool] and [Dispatcher] eliminates redundant thread pools,
 * reduces RAM & socket overhead on mid-range devices, and preserves warm HTTP/2 + TLS
 * sessions across consecutive solves.
 */
object PrismNetworkClient {

    private const val TAG = "PrismNetworkClient"
    private const val PREWARM_MIN_INTERVAL_MS = 3 * 60_000L

    private val sharedDispatcher: Dispatcher by lazy {
        Dispatcher().apply {
            maxRequests = 48
            maxRequestsPerHost = 12
        }
    }

    private val sharedConnectionPool: ConnectionPool by lazy {
        ConnectionPool(16, 5, TimeUnit.MINUTES)
    }

    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(sharedDispatcher)
            .connectionPool(sharedConnectionPool)
            .connectTimeout(5_000, TimeUnit.MILLISECONDS)
            .readTimeout(15_000, TimeUnit.MILLISECONDS)
            .writeTimeout(5_000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val lastPrewarmAtMs = AtomicLong(0L)

    /**
     * Derives a lightweight engine-specific [OkHttpClient] that shares the central
     * [ConnectionPool] and [Dispatcher] while applying custom per-engine timeouts/cookies.
     */
    fun derive(
        connectTimeoutMs: Long,
        readTimeoutMs: Long,
        writeTimeoutMs: Long = connectTimeoutMs.coerceAtLeast(3_000L),
        callTimeoutMs: Long = 0L,
        followRedirects: Boolean = true,
        followSslRedirects: Boolean = true,
        cookieJar: CookieJar? = null
    ): OkHttpClient {
        val builder = shared.newBuilder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
            .followRedirects(followRedirects)
            .followSslRedirects(followSslRedirects)
        if (callTimeoutMs > 0L) {
            builder.callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
        }
        if (cookieJar != null) {
            builder.cookieJar(cookieJar)
        }
        return builder.build()
    }

    /**
     * Quietly warms DNS + TLS + HTTP/2 sockets for critical edge AI hosts off the UI thread
     * so the user's very first solve skips cold handshake latency.
     */
    suspend fun prewarmCriticalHosts() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val prev = lastPrewarmAtMs.get()
        if (now - prev < PREWARM_MIN_INTERVAL_MS) return@withContext
        if (!lastPrewarmAtMs.compareAndSet(prev, now)) return@withContext

        val warmClient = derive(
            connectTimeoutMs = 2_500L,
            readTimeoutMs = 2_500L,
            callTimeoutMs = 3_000L
        )
        runCatching {
            val req = Request.Builder()
                .url(PrismAiSolverEngine.CLOUDFLARE_WORKER_URL)
                .head()
                .build()
            warmClient.newCall(req).execute().close()
        }.onFailure { err ->
            Log.d(TAG, "Non-critical edge prewarm skipped: ${err.message}")
        }
    }
}
