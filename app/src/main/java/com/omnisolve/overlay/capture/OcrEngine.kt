package com.omnisolve.overlay.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.omnisolve.overlay.util.PrismPerfLogger
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

data class OcrExtractionResult(
    val text: String,
    val blocks: List<Pair<String, Rect>>,
    val durationMs: Long,
    val passCount: Int,
    val timedOut: Boolean = false,
    val errorReason: String? = null
)

/**
 * High-Speed Asynchronous OCR Pipeline with Pre-Allocated Bitmap Buffer Pool.
 *
 * Physical-Device Reliability Improvements:
 * 1. Dedicated off-main-thread coroutine worker pool (`ocrWorkerDispatcher`).
 * 2. Pre-allocated reusable `ARGB_8888` bitmap & canvas ring buffer pool to avoid GC pauses.
 * 3. Tracked ML Kit warm-up (`warmupDeferred` + explicit `PRISM_PERF` timing/status — never silent).
 * 4. Hard per-pass timeout (`8000ms`) so a stalled ML Kit binder call can never hang the pipeline.
 * 5. Downscaled second high-contrast pass ONLY when Pass 1 < 6 chars and didn't time out.
 */
open class OcrEngine {

    companion object {
        private const val TAG = "OcrEngine"
        private const val MAX_OCR_SIDE = 1280
        private const val POOL_SIZE = 2
        private const val WARMUP_AWAIT_BUDGET_MS = 1200L
        internal const val OCR_RECOGNIZE_TIMEOUT_MS = 8000L
        internal const val TOTAL_OCR_BUDGET_MS = 4500L
        private const val MIN_PASS2_BUDGET_MS = 900L

        private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L
    }

    private val ocrExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "PrismOcrWorker").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY + 1
        }
    }
    private val ocrWorkerDispatcher = ocrExecutor.asCoroutineDispatcher()
    private val workerScope = CoroutineScope(SupervisorJob() + ocrWorkerDispatcher)

    private class PreAllocatedBitmapSlot(width: Int, height: Int) {
        var bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var canvas: Canvas = Canvas(bitmap)
        val inUse = AtomicBoolean(false)

        fun ensureCapacity(targetW: Int, targetH: Int): Bitmap {
            if (bitmap.isRecycled || bitmap.width != targetW || bitmap.height != targetH) {
                try {
                    if (!bitmap.isRecycled) {
                        bitmap.reconfigure(targetW, targetH, Bitmap.Config.ARGB_8888)
                        canvas = Canvas(bitmap)
                        return bitmap
                    }
                } catch (_: Exception) {
                    // Fallback to fresh allocation when size grows beyond allocation byte count
                }
                try { if (!bitmap.isRecycled) bitmap.recycle() } catch (_: Exception) {}
                bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                canvas = Canvas(bitmap)
            }
            return bitmap
        }
    }

    private val bufferPoolMutex = Mutex()
    private val preAllocatedPool = ArrayList<PreAllocatedBitmapSlot>(POOL_SIZE)
    private val warmupDeferred = CompletableDeferred<Boolean>()

    @Volatile
    var lastWarmupStatus: String = "PENDING"
        private set

    @Volatile
    var lastOcrError: String? = null
        private set

    private val contrastPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        val contrast = 1.42f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    init {
        workerScope.launch {
            val startMs = PrismPerfLogger.nowMs()
            var warmSlot: PreAllocatedBitmapSlot? = null
            try {
                bufferPoolMutex.withLock {
                    if (preAllocatedPool.isEmpty()) {
                        repeat(POOL_SIZE) {
                            preAllocatedPool.add(PreAllocatedBitmapSlot(720, 1280))
                        }
                    }
                }
                warmSlot = acquirePreAllocatedSlot(720, 1280)
                val warmImg = InputImage.fromBitmap(warmSlot.bitmap, 0)
                withTimeout(OCR_RECOGNIZE_TIMEOUT_MS) {
                    recognizer.process(warmImg).await()
                }
                val elapsed = PrismPerfLogger.nowMs() - startMs
                lastWarmupStatus = "READY:${elapsed}ms"
                PrismPerfLogger.logStage(0L, "MLKIT_INIT", elapsed, status = "OK", detail = "warmup_ready")
                warmupDeferred.complete(true)
            } catch (ce: CancellationException) {
                if (ce !is TimeoutCancellationException) {
                    lastWarmupStatus = "CANCELLED"
                    warmupDeferred.complete(false)
                    throw ce
                }
                val elapsed = PrismPerfLogger.nowMs() - startMs
                lastWarmupStatus = "TIMEOUT:${elapsed}ms"
                lastOcrError = lastWarmupStatus
                Log.w(TAG, "ML Kit warm-up timed out after ${elapsed}ms")
                PrismPerfLogger.logStage(0L, "MLKIT_INIT", elapsed, status = "TIMEOUT", detail = "warmup_timeout")
                warmupDeferred.complete(false)
            } catch (e: Exception) {
                val elapsed = PrismPerfLogger.nowMs() - startMs
                lastWarmupStatus = "ERROR:${e.javaClass.simpleName}"
                lastOcrError = "${e.javaClass.simpleName}: ${e.message}"
                Log.w(TAG, "ML Kit warm-up failed: ${e.javaClass.simpleName}: ${e.message}")
                PrismPerfLogger.logStage(
                    0L,
                    "MLKIT_INIT",
                    elapsed,
                    status = "FAILED",
                    detail = "${e.javaClass.simpleName}: ${e.message}"
                )
                warmupDeferred.complete(false)
            } finally {
                releaseSlot(warmSlot)
            }
        }
    }

    private suspend fun acquirePreAllocatedSlot(targetW: Int, targetH: Int): PreAllocatedBitmapSlot {
        bufferPoolMutex.withLock {
            for (slot in preAllocatedPool) {
                if (slot.inUse.compareAndSet(false, true)) {
                    slot.ensureCapacity(targetW, targetH)
                    return slot
                }
            }
            val newSlot = PreAllocatedBitmapSlot(targetW, targetH)
            newSlot.inUse.set(true)
            if (preAllocatedPool.size < POOL_SIZE + 2) {
                preAllocatedPool.add(newSlot)
            }
            return newSlot
        }
    }

    private fun releaseSlot(slot: PreAllocatedBitmapSlot?) {
        slot?.inUse?.set(false)
    }

    open suspend fun extractText(bitmap: Bitmap): String = withContext(ocrWorkerDispatcher) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return@withContext ""
        val result = extractTextWithDiagnostics(bitmap, sessionId = 0L)
        result.text
    }

    open suspend fun extractTextWithBounds(bitmap: Bitmap): Pair<String, List<Pair<String, Rect>>> =
        withContext(ocrWorkerDispatcher) {
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                return@withContext Pair("", emptyList())
            }
            val result = extractTextWithDiagnostics(bitmap, sessionId = 0L)
            Pair(result.text, result.blocks)
        }

    open suspend fun extractTextWithDiagnostics(
        bitmap: Bitmap,
        sessionId: Long = 0L
    ): OcrExtractionResult = extractTextWithDiagnostics(
        bitmap = bitmap,
        sessionId = sessionId,
        totalBudgetMs = TOTAL_OCR_BUDGET_MS
    )

    open suspend fun extractTextWithDiagnostics(
        bitmap: Bitmap,
        sessionId: Long,
        totalBudgetMs: Long
    ): OcrExtractionResult = withContext(ocrWorkerDispatcher) {
        val totalStart = PrismPerfLogger.nowMs()
        val effectiveBudgetMs = totalBudgetMs.coerceAtLeast(600L)
        val deadlineElapsedMs = monotonicNowMs() + effectiveBudgetMs
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            lastOcrError = "INVALID_BITMAP"
            return@withContext OcrExtractionResult("", emptyList(), 0L, 0, false, "INVALID_BITMAP")
        }

        // Wait briefly for in-flight model warm-up without starving Pass 1 OCR budget
        if (!warmupDeferred.isCompleted) {
            val warmupCap = minOf(
                WARMUP_AWAIT_BUDGET_MS,
                (deadlineElapsedMs - monotonicNowMs() - 1200L).coerceAtLeast(0L)
            )
            if (warmupCap > 0L) {
                withTimeoutOrNull(warmupCap) { warmupDeferred.await() }
            }
        }

        val srcW = bitmap.width
        val srcH = bitmap.height
        val longestSide = max(srcW, srcH)
        val scale = if (longestSide > MAX_OCR_SIDE) {
            MAX_OCR_SIDE.toFloat() / longestSide.toFloat()
        } else {
            1.0f
        }
        val targetW = (srcW * scale).roundToInt().coerceAtLeast(64)
        val targetH = (srcH * scale).roundToInt().coerceAtLeast(64)
        val invScale = 1.0f / scale

        var slot1: PreAllocatedBitmapSlot? = null
        var slot2: PreAllocatedBitmapSlot? = null
        try {
            val prepStart = PrismPerfLogger.nowMs()
            val workingBitmap = if (scale < 1.0f) {
                slot1 = acquirePreAllocatedSlot(targetW, targetH)
                val destRect = Rect(0, 0, targetW, targetH)
                slot1.canvas.drawBitmap(bitmap, null, destRect, scalePaint)
                slot1.bitmap
            } else {
                bitmap
            }
            val prepMs = PrismPerfLogger.nowMs() - prepStart
            if (prepMs >= 5L) {
                PrismPerfLogger.logStage(
                    sessionId,
                    "BITMAP_PREP",
                    prepMs,
                    status = "OK",
                    detail = "${srcW}x${srcH}->${targetW}x${targetH}"
                )
            }

            // Pass 1: Fast asynchronous ML Kit recognition bounded by shared monotonic OCR budget
            val pass1TimeoutMs = (deadlineElapsedMs - monotonicNowMs()).coerceAtLeast(500L)
            val pass1 = runMlKitRecognition(workingBitmap, invScale, pass1TimeoutMs)
            val pass1Assessment = OcrQualityValidator.assess(pass1.first, pass1.second)
            if (pass1Assessment.isUsableQuestion) {
                lastOcrError = null
                val elapsed = PrismPerfLogger.nowMs() - totalStart
                PrismPerfLogger.logStage(
                    sessionId,
                    "OCR",
                    elapsed,
                    status = "OK",
                    detail = "passes=1 chars=${pass1Assessment.meaningfulCharCount} opts=${pass1Assessment.optionMarkerCount} blocks=${pass1.second.size}"
                )
                return@withContext OcrExtractionResult(pass1.first, pass1.second, elapsed, passCount = 1)
            }

            coroutineContext.ensureActive()

            // Skip Pass 2 if remaining monotonic OCR budget is too tight to finish safely
            val remainingForPass2Ms = deadlineElapsedMs - monotonicNowMs()
            if (remainingForPass2Ms < MIN_PASS2_BUDGET_MS) {
                val elapsed = PrismPerfLogger.nowMs() - totalStart
                lastOcrError = null
                PrismPerfLogger.logStage(
                    sessionId,
                    "OCR",
                    elapsed,
                    status = "OK",
                    detail = "passes=1_budget_cap chars=${pass1Assessment.meaningfulCharCount} blocks=${pass1.second.size}"
                )
                return@withContext OcrExtractionResult(pass1.first, pass1.second, elapsed, passCount = 1)
            }

            // Pass 2: High-contrast pass from already-downscaled workingBitmap within remaining budget
            slot2 = acquirePreAllocatedSlot(targetW, targetH)
            val destRect = Rect(0, 0, targetW, targetH)
            slot2.canvas.drawBitmap(workingBitmap, null, destRect, contrastPaint)
            val pass2 = try {
                runMlKitRecognition(slot2.bitmap, invScale, remainingForPass2Ms)
            } catch (_: TimeoutCancellationException) {
                // Preserve Pass 1 partial output rather than discarding everything on Pass 2 timeout
                val elapsed = PrismPerfLogger.nowMs() - totalStart
                PrismPerfLogger.logStage(
                    sessionId,
                    "OCR",
                    elapsed,
                    status = "OK",
                    detail = "passes=2_timeout_fallback chars=${pass1Assessment.meaningfulCharCount} blocks=${pass1.second.size}"
                )
                return@withContext OcrExtractionResult(pass1.first, pass1.second, elapsed, passCount = 2)
            }
            val pass2Assessment = OcrQualityValidator.assess(pass2.first, pass2.second)
            val best = if (pass2Assessment.qualityScore > pass1Assessment.qualityScore) pass2 else pass1
            val bestChars = max(pass1Assessment.meaningfulCharCount, pass2Assessment.meaningfulCharCount)
            val elapsed = PrismPerfLogger.nowMs() - totalStart
            lastOcrError = null
            PrismPerfLogger.logStage(
                sessionId,
                "OCR",
                elapsed,
                status = "OK",
                detail = "passes=2 chars=$bestChars blocks=${best.second.size}"
            )
            return@withContext OcrExtractionResult(best.first, best.second, elapsed, passCount = 2)
        } catch (ce: CancellationException) {
            val elapsed = PrismPerfLogger.nowMs() - totalStart
            if (ce !is TimeoutCancellationException) {
                PrismPerfLogger.logStage(sessionId, "OCR", elapsed, status = "CANCELLED")
                throw ce
            }
            lastOcrError = "OCR_TIMEOUT_${effectiveBudgetMs}MS"
            Log.e(TAG, "OCR timed out after ${elapsed}ms")
            PrismPerfLogger.logStage(
                sessionId,
                "OCR",
                elapsed,
                status = "TIMEOUT",
                detail = "warmup=$lastWarmupStatus"
            )
            return@withContext OcrExtractionResult("", emptyList(), elapsed, passCount = 1, timedOut = true, errorReason = lastOcrError)
        } catch (e: Exception) {
            val elapsed = PrismPerfLogger.nowMs() - totalStart
            val err = "${e.javaClass.simpleName}: ${e.message}"
            lastOcrError = err
            Log.e(TAG, "Async OCR worker failed ($err)")
            PrismPerfLogger.logStage(
                sessionId,
                "OCR",
                elapsed,
                status = "FAILED",
                detail = err
            )
            return@withContext OcrExtractionResult("", emptyList(), elapsed, passCount = 1, timedOut = false, errorReason = err)
        } finally {
            releaseSlot(slot1)
            releaseSlot(slot2)
        }
    }

    private suspend fun runMlKitRecognition(
        workingBitmap: Bitmap,
        invScale: Float,
        timeoutMs: Long = TOTAL_OCR_BUDGET_MS
    ): Pair<String, List<Pair<String, Rect>>> {
        val inputImage = InputImage.fromBitmap(workingBitmap, 0)
        val result = withTimeout(timeoutMs.coerceAtLeast(500L)) {
            recognizer.process(inputImage).await()
        }
        val fullText = result.text.trim()
        val blocks = ArrayList<Pair<String, Rect>>(result.textBlocks.size * 3)
        val needsUnscale = invScale != 1.0f

        for (block in result.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.trim()
                val lineBounds = line.boundingBox
                if (lineBounds != null && lineText.isNotBlank()) {
                    val rect = if (needsUnscale) {
                        Rect(
                            (lineBounds.left * invScale).roundToInt(),
                            (lineBounds.top * invScale).roundToInt(),
                            (lineBounds.right * invScale).roundToInt(),
                            (lineBounds.bottom * invScale).roundToInt()
                        )
                    } else {
                        lineBounds
                    }
                    blocks.add(Pair(lineText, rect))
                }
            }
            val blockText = block.text.trim()
            val blockBounds = block.boundingBox
            if (blockBounds != null && blockText.isNotBlank()) {
                val rect = if (needsUnscale) {
                    Rect(
                        (blockBounds.left * invScale).roundToInt(),
                        (blockBounds.top * invScale).roundToInt(),
                        (blockBounds.right * invScale).roundToInt(),
                        (blockBounds.bottom * invScale).roundToInt()
                    )
                } else {
                    blockBounds
                }
                blocks.add(Pair(blockText, rect))
            }
        }
        return Pair(fullText, blocks)
    }

    fun close() {
        try { workerScope.cancel() } catch (_: Exception) {}
        try { recognizer.close() } catch (_: Exception) {}
        try { ocrExecutor.shutdownNow() } catch (_: Exception) {}
        try {
            for (slot in preAllocatedPool) {
                if (!slot.bitmap.isRecycled) slot.bitmap.recycle()
            }
            preAllocatedPool.clear()
        } catch (_: Exception) {}
    }
}
