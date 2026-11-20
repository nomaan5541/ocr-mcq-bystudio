package com.omnisolve.overlay.cache

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.LruCache
import com.omnisolve.overlay.model.AnswerModel
import java.security.MessageDigest
import kotlin.math.max

/**
 * Strictly in-memory, bounded, expiring solve cache for repeated scans of the same question.
 *
 * Security & Privacy Rules Enforced:
 * 1. Never writes screenshots, crops, OCR text, or AI request payloads to disk.
 * 2. Same-Capture Cache (max 4 entries, 20s TTL) is keyed by perceptual bitmap dHash + dimensions +
 *    crop box + solver config, and is immediately invalidated on MediaProjection stop.
 * 3. AI Answer Cache (max 24 entries, 180s TTL) is keyed strictly by canonical hash of
 *    `promptVersion + modelId + subjectMode + solveMode + normalizedQuestion + normalizedOptions`
 *    and marks hits with `isCachedResult = true`.
 */
class SolveResultCache(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val maxSameCaptureEntries: Int = DEFAULT_SAME_CAPTURE_MAX,
    private val sameCaptureTtlMs: Long = DEFAULT_SAME_CAPTURE_TTL_MS,
    private val maxAnswerEntries: Int = DEFAULT_ANSWER_MAX,
    private val answerTtlMs: Long = DEFAULT_ANSWER_TTL_MS
) {

    companion object {
        const val PROMPT_VERSION = "v2"
        const val DEFAULT_SAME_CAPTURE_MAX = 4
        const val DEFAULT_SAME_CAPTURE_TTL_MS = 20_000L
        const val DEFAULT_ANSWER_MAX = 24
        const val DEFAULT_ANSWER_TTL_MS = 180_000L

        private val whitespaceRegex = Regex("""\s+""")
        private val optionSplitRegex = Regex("""(?m)^\s*(?:[(]?[A-Fa-f][.)\]:]|[(]?[1-6][.)\]])\s+""")

        /**
         * Computes a lightweight 64-bit difference hash (dHash) over a 9x8 luminance grid.
         * Zero disk allocation and deterministic across identical frames.
         */
        fun computePerceptualHash(bitmap: Bitmap): Long {
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return 0L
            val w = bitmap.width
            val h = bitmap.height
            var hash = 0L
            for (row in 0 until 8) {
                val y = (((row + 0.5f) / 8f) * h).toInt().coerceIn(0, h - 1)
                var prevLuma = 0
                for (col in 0 until 9) {
                    val x = (((col + 0.5f) / 9f) * w).toInt().coerceIn(0, w - 1)
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel ushr 16) and 0xFF
                    val g = (pixel ushr 8) and 0xFF
                    val b = pixel and 0xFF
                    val luma = (r * 30 + g * 59 + b * 11) / 100
                    if (col > 0) {
                        val bitIndex = row * 8 + (col - 1)
                        if (luma > prevLuma) {
                            hash = hash or (1L shl bitIndex)
                        }
                    }
                    prevLuma = luma
                }
            }
            return hash
        }

        fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

        fun normalizeForKey(raw: String): String {
            return raw.trim()
                .lowercase()
                .replace(whitespaceRegex, " ")
        }

        /**
         * Splits OCR / captured text into canonical `(normalizedQuestion, normalizedOptions)` pair.
         */
        fun splitNormalizedQuestionAndOptions(rawText: String): Pair<String, String> {
            val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return "" to ""
            val firstOptionIdx = lines.indexOfFirst { optionSplitRegex.containsMatchIn(it) }
            val questionPart = if (firstOptionIdx > 0) {
                lines.subList(0, firstOptionIdx).joinToString(" ")
            } else {
                lines.joinToString(" ")
            }
            val optionsPart = if (firstOptionIdx >= 0) {
                lines.subList(firstOptionIdx, lines.size).joinToString(" | ")
            } else {
                ""
            }
            return normalizeForKey(questionPart) to normalizeForKey(optionsPart)
        }

        fun buildSameCaptureKey(
            perceptualHash: Long,
            bitmapWidth: Int,
            bitmapHeight: Int,
            cropRect: Rect?,
            promptVersion: String = PROMPT_VERSION,
            modelId: String,
            subjectMode: String,
            solveMode: String
        ): String {
            val cropPart = if (cropRect == null) {
                "full"
            } else {
                "${cropRect.left},${cropRect.top},${cropRect.right},${cropRect.bottom}"
            }
            return sha256Hex(
                listOf(
                    perceptualHash.toString(16),
                    "${bitmapWidth}x${bitmapHeight}",
                    cropPart,
                    promptVersion.trim(),
                    normalizeForKey(modelId),
                    normalizeForKey(subjectMode),
                    normalizeForKey(solveMode)
                ).joinToString("|")
            )
        }

        fun buildAnswerCacheKey(
            promptVersion: String = PROMPT_VERSION,
            modelId: String,
            subjectMode: String,
            solveMode: String,
            normalizedQuestion: String,
            normalizedOptions: String
        ): String {
            return sha256Hex(
                listOf(
                    promptVersion.trim(),
                    normalizeForKey(modelId),
                    normalizeForKey(subjectMode),
                    normalizeForKey(solveMode),
                    normalizeForKey(normalizedQuestion),
                    normalizeForKey(normalizedOptions)
                ).joinToString("|")
            )
        }

        private fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) {
                val i = b.toInt() and 0xFF
                if (i < 16) sb.append('0')
                sb.append(i.toString(16))
            }
            return sb.toString()
        }
    }

    private data class CachedEntry(
        val key: String,
        val bitmapHash: Long,
        val bitmapWidth: Int,
        val bitmapHeight: Int,
        val cropSignature: String,
        val configSignature: String,
        val ocrText: String,
        val normalizedQuestion: String,
        val answer: AnswerModel,
        val createdAtMs: Long
    )

    private val lock = Any()
    private val sameCaptureLru = LruCache<String, CachedEntry>(max(1, maxSameCaptureEntries))
    private val aiAnswerLru = LruCache<String, CachedEntry>(max(1, maxAnswerEntries))

    fun getSameCapture(
        bitmap: Bitmap,
        cropRect: Rect?,
        modelId: String,
        subjectMode: String,
        solveMode: String,
        promptVersion: String = PROMPT_VERSION
    ): Pair<AnswerModel, String>? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val pHash = computePerceptualHash(bitmap)
        val exactKey = buildSameCaptureKey(
            perceptualHash = pHash,
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            cropRect = cropRect,
            promptVersion = promptVersion,
            modelId = modelId,
            subjectMode = subjectMode,
            solveMode = solveMode
        )
        val now = clockMs()
        val cropSig = cropRect?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: "full"
        val cfgSig = "${promptVersion.trim()}|${normalizeForKey(modelId)}|${normalizeForKey(subjectMode)}|${normalizeForKey(solveMode)}"

        synchronized(lock) {
            val exact = sameCaptureLru.get(exactKey)
            if (exact != null) {
                if (now - exact.createdAtMs <= sameCaptureTtlMs) {
                    return exact.answer.copy(isCachedResult = true) to exact.ocrText
                } else {
                    sameCaptureLru.remove(exactKey)
                }
            }

            // Near-identical frame tolerance (<= 2 bits dHash diff, same dims, crop & config)
            val snapshot = sameCaptureLru.snapshot()
            for ((k, entry) in snapshot) {
                if (now - entry.createdAtMs > sameCaptureTtlMs) {
                    sameCaptureLru.remove(k)
                    continue
                }
                if (entry.bitmapWidth == bitmap.width &&
                    entry.bitmapHeight == bitmap.height &&
                    entry.cropSignature == cropSig &&
                    entry.configSignature == cfgSig &&
                    hammingDistance(pHash, entry.bitmapHash) <= 2
                ) {
                    return entry.answer.copy(isCachedResult = true) to entry.ocrText
                }
            }
            return null
        }
    }

    fun putSameCapture(
        bitmap: Bitmap,
        cropRect: Rect?,
        modelId: String,
        subjectMode: String,
        solveMode: String,
        ocrText: String,
        answer: AnswerModel,
        promptVersion: String = PROMPT_VERSION
    ) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return
        val pHash = computePerceptualHash(bitmap)
        val key = buildSameCaptureKey(
            perceptualHash = pHash,
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            cropRect = cropRect,
            promptVersion = promptVersion,
            modelId = modelId,
            subjectMode = subjectMode,
            solveMode = solveMode
        )
        val cropSig = cropRect?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: "full"
        val cfgSig = "${promptVersion.trim()}|${normalizeForKey(modelId)}|${normalizeForKey(subjectMode)}|${normalizeForKey(solveMode)}"
        val (normQ, _) = splitNormalizedQuestionAndOptions(ocrText)
        val entry = CachedEntry(
            key = key,
            bitmapHash = pHash,
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            cropSignature = cropSig,
            configSignature = cfgSig,
            ocrText = ocrText,
            normalizedQuestion = normQ,
            answer = answer.copy(isCachedResult = false),
            createdAtMs = clockMs()
        )
        synchronized(lock) {
            sameCaptureLru.put(key, entry)
        }
    }

    fun getAiAnswer(
        modelId: String,
        subjectMode: String,
        solveMode: String,
        normalizedQuestion: String,
        normalizedOptions: String,
        promptVersion: String = PROMPT_VERSION
    ): AnswerModel? {
        if (normalizedQuestion.isBlank() && normalizedOptions.isBlank()) return null
        val key = buildAnswerCacheKey(
            promptVersion = promptVersion,
            modelId = modelId,
            subjectMode = subjectMode,
            solveMode = solveMode,
            normalizedQuestion = normalizedQuestion,
            normalizedOptions = normalizedOptions
        )
        val now = clockMs()
        synchronized(lock) {
            val entry = aiAnswerLru.get(key) ?: return null
            if (now - entry.createdAtMs > answerTtlMs) {
                aiAnswerLru.remove(key)
                return null
            }
            return entry.answer.copy(isCachedResult = true)
        }
    }

    fun putAiAnswer(
        modelId: String,
        subjectMode: String,
        solveMode: String,
        normalizedQuestion: String,
        normalizedOptions: String,
        answer: AnswerModel,
        promptVersion: String = PROMPT_VERSION
    ) {
        if (normalizedQuestion.isBlank() && normalizedOptions.isBlank()) return
        val key = buildAnswerCacheKey(
            promptVersion = promptVersion,
            modelId = modelId,
            subjectMode = subjectMode,
            solveMode = solveMode,
            normalizedQuestion = normalizedQuestion,
            normalizedOptions = normalizedOptions
        )
        val entry = CachedEntry(
            key = key,
            bitmapHash = 0L,
            bitmapWidth = 0,
            bitmapHeight = 0,
            cropSignature = "",
            configSignature = "",
            ocrText = "",
            normalizedQuestion = normalizeForKey(normalizedQuestion),
            answer = answer.copy(isCachedResult = false),
            createdAtMs = clockMs()
        )
        synchronized(lock) {
            aiAnswerLru.put(key, entry)
        }
    }

    /**
     * Targeted anti-poison eviction: removes any cached entry matching EITHER the visual frame
     * (dHash Hamming distance <= 3) OR the normalized question text across all solver modes/models.
     */
    fun evictForQuestion(bitmap: Bitmap?, rawText: String?) {
        val targetHash = if (bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
            computePerceptualHash(bitmap)
        } else {
            0L
        }
        val targetNormQ = rawText?.takeIf { it.isNotBlank() }?.let {
            splitNormalizedQuestionAndOptions(it).first
        }.orEmpty()

        synchronized(lock) {
            val sameSnapshot = sameCaptureLru.snapshot()
            for ((k, entry) in sameSnapshot) {
                val frameMatches = targetHash != 0L && entry.bitmapHash != 0L &&
                    hammingDistance(targetHash, entry.bitmapHash) <= 3
                val textMatches = targetNormQ.length >= 6 &&
                    entry.normalizedQuestion.isNotBlank() &&
                    entry.normalizedQuestion == targetNormQ
                if (frameMatches || textMatches) {
                    sameCaptureLru.remove(k)
                }
            }

            if (targetNormQ.length >= 6) {
                val aiSnapshot = aiAnswerLru.snapshot()
                for ((k, entry) in aiSnapshot) {
                    if (entry.normalizedQuestion == targetNormQ) {
                        aiAnswerLru.remove(k)
                    }
                }
            }
        }
    }

    fun invalidateSameCaptureCache() {
        synchronized(lock) {
            sameCaptureLru.evictAll()
        }
    }

    fun clearAll() {
        synchronized(lock) {
            sameCaptureLru.evictAll()
            aiAnswerLru.evictAll()
        }
    }

    fun sameCaptureCount(): Int = synchronized(lock) { sameCaptureLru.size() }
    fun aiAnswerCount(): Int = synchronized(lock) { aiAnswerLru.size() }
}
