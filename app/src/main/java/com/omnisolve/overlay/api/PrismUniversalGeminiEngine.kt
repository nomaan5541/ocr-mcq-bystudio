package com.omnisolve.overlay.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.omnisolve.overlay.data.FirebaseTelemetryRepository
import com.omnisolve.overlay.data.TelemetryBackend
import com.omnisolve.overlay.data.TelemetryFeature
import com.omnisolve.overlay.data.TelemetryStatus
import com.omnisolve.overlay.data.UsageLimitCheckResult
import com.omnisolve.overlay.data.UsageLimitFeature
import com.omnisolve.overlay.data.UserUsageLimitManager
import com.omnisolve.overlay.f1.F1AuthGate
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class UniversalSolveStyle(
    val id: String,
    val label: String,
    val shortHint: String
) {
    DEEP_SOLVE(
        id = "deep_solve",
        label = "Deep Solve",
        shortHint = "Final answer + full step-by-step explanation"
    ),
    QUICK_DIRECT(
        id = "quick_direct",
        label = "Quick",
        shortHint = "Direct final answer + concise key reason"
    ),
    HINGLISH_TUTOR(
        id = "hinglish_tutor",
        label = "Hinglish",
        shortHint = "Friendly Hinglish explanation for easy revision"
    );

    fun next(): UniversalSolveStyle = when (this) {
        DEEP_SOLVE -> QUICK_DIRECT
        QUICK_DIRECT -> HINGLISH_TUTOR
        HINGLISH_TUTOR -> DEEP_SOLVE
    }
}

enum class UniversalAttachmentType {
    PHOTO,
    PDF
}

data class UniversalAttachment(
    val uri: Uri,
    val type: UniversalAttachmentType,
    val displayName: String,
    val sizeBytes: Long = 0L,
    val pageCount: Int = 0
)

data class UniversalChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val attachmentName: String? = null,
    val attachmentType: UniversalAttachmentType? = null,
    val attachmentUriString: String? = null,
    val styleLabel: String = "",
    val latencyMs: Long = 0L,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val requiresSignIn: Boolean = false
)

data class UniversalTurnContext(
    val isUser: Boolean,
    val text: String
)

data class UniversalSolveOutcome(
    val fullText: String,
    val latencyMs: Long,
    val modelUsed: String
)

class UniversalAuthRequiredException(message: String) : IllegalStateException(message)

class UniversalDailyLimitExceededException(
    val feature: UsageLimitFeature,
    message: String
) : IllegalStateException(message)

class UniversalAttachmentTooLargeException(
    val attachmentType: UniversalAttachmentType,
    message: String
) : IllegalArgumentException(message)

/**
 * Prism Ask Universal Gemini Engine ("Give anything, solve it").
 *
 * Handles open-ended study questions, photos, and PDFs through Firebase AI Logic
 * with deterministic decoding (`temperature = 0.0f`), strict mobile-friendly file size caps
 * (Photos <= 2 MB, PDFs <= 3 MB & <= 12 pages), and live token streaming.
 */
object PrismUniversalGeminiEngine {

    private const val TAG = "PrismUniversalGemini"
    const val UNIVERSAL_MODEL = "gemini-3.8-flash"
    const val FEATURE_UNIVERSAL_ASK = "UNIVERSAL_ASK"
    const val MAX_PHOTO_UPLOAD_BYTES = 2L * 1024L * 1024L
    const val MAX_PDF_UPLOAD_BYTES = 3L * 1024L * 1024L
    const val MAX_PDF_PAGE_LIMIT = 12
    private const val MAX_INLINE_PDF_BYTES = MAX_PDF_UPLOAD_BYTES
    private const val MAX_IMAGE_DIM_PX = 1280
    private const val MAX_PDF_FALLBACK_PAGES = 6
    private const val STREAM_TIMEOUT_MS = 60_000L

    /**
     * Test-only deterministic stream hook for Robolectric regression tests.
     */
    @Volatile
    internal var streamOverrideForTest: (suspend (
        prompt: String,
        attachment: UniversalAttachment?,
        style: UniversalSolveStyle,
        onChunk: (String) -> Unit
    ) -> String)? = null

    fun inspectAttachment(
        context: Context,
        uri: Uri,
        type: UniversalAttachmentType
    ): UniversalAttachment {
        var displayName = if (type == UniversalAttachmentType.PHOTO) "Selected Photo" else "Selected PDF"
        var sizeBytes = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        val queried = cursor.getString(nameIdx)
                        if (!queried.isNullOrBlank()) displayName = queried
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                    }
                }
            }
        } catch (_: Exception) {
        }

        var pageCount = 0
        if (type == UniversalAttachmentType.PDF) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                    }
                }
            } catch (_: Exception) {
            }
        }

        return UniversalAttachment(
            uri = uri,
            type = type,
            displayName = displayName,
            sizeBytes = sizeBytes,
            pageCount = pageCount
        )
    }

    /**
     * Automatically shrinks a newly snapped camera photo (written into app cache) to comfortably
     * fit under [MAX_PHOTO_UPLOAD_BYTES] (2 MB) without sacrificing text legibility.
     */
    fun compressCameraCaptureUnderLimit(
        context: Context,
        file: java.io.File
    ): Long {
        if (!file.exists()) return 0L
        if (file.length() in 1L..MAX_PHOTO_UPLOAD_BYTES) return file.length()
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val maxSide = max(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxSide / (sample * 2) >= MAX_IMAGE_DIM_PX) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return file.length()
            try {
                var quality = 85
                var compressedBytes = java.io.ByteArrayOutputStream()
                while (quality >= 45) {
                    compressedBytes = java.io.ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, quality, compressedBytes)
                    if (compressedBytes.size().toLong() <= MAX_PHOTO_UPLOAD_BYTES - 128_000L) break
                    quality -= 12
                }
                file.writeBytes(compressedBytes.toByteArray())
                file.length()
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        } catch (_: Exception) {
            file.length()
        }
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        return when {
            bytes < 1024L -> "${bytes} B"
            bytes < 1024L * 1024L -> "${(bytes / 1024.0).roundToInt()} KB"
            else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Checks photo (<= 2 MB) and PDF (<= 3 MB, <= 12 pages) upload caps.
     * Returns a plain-English error message if over the limit, or `null` when allowed.
     */
    fun validateAttachmentLimits(attachment: UniversalAttachment?): String? {
        if (attachment == null) return null
        return when (attachment.type) {
            UniversalAttachmentType.PHOTO -> {
                if (attachment.sizeBytes > MAX_PHOTO_UPLOAD_BYTES) {
                    "This photo is ${formatByteSize(attachment.sizeBytes)}. Please pick a photo under 2 MB."
                } else {
                    null
                }
            }
            UniversalAttachmentType.PDF -> {
                when {
                    attachment.sizeBytes > MAX_PDF_UPLOAD_BYTES ->
                        "This PDF is ${formatByteSize(attachment.sizeBytes)}. Please choose a PDF under 3 MB."
                    attachment.pageCount > MAX_PDF_PAGE_LIMIT ->
                        "This PDF has ${attachment.pageCount} pages. Please choose a PDF with $MAX_PDF_PAGE_LIMIT pages or fewer."
                    else -> null
                }
            }
        }
    }

    suspend fun solveStream(
        context: Context,
        userPrompt: String,
        attachment: UniversalAttachment?,
        style: UniversalSolveStyle,
        recentHistory: List<UniversalTurnContext>,
        onPartialUpdate: (String) -> Unit
    ): UniversalSolveOutcome = withContext(Dispatchers.IO) {
        val auth = F1AuthGate.verifyGoogleAccount(context)
        if (!auth.isAuthenticated || auth.uid.isNullOrBlank()) {
            throw UniversalAuthRequiredException(
                auth.errorMessage.ifBlank { "Please sign in with Google to solve photos and PDFs." }
            )
        }

        // Enforce file size & page limits BEFORE checking/burning daily quota or reading bytes into memory
        if (attachment != null) {
            val sizeError = validateAttachmentLimits(attachment)
            if (sizeError != null) {
                throw UniversalAttachmentTooLargeException(attachment.type, sizeError)
            }
        }

        val limitFeature = when (attachment?.type) {
            UniversalAttachmentType.PHOTO -> UsageLimitFeature.ASK_AI_PHOTO
            UniversalAttachmentType.PDF -> UsageLimitFeature.ASK_AI_PDF
            null -> UsageLimitFeature.ASK_AI_TEXT
        }
        val quotaCheck = UserUsageLimitManager.checkQuota(
            context = context,
            uid = auth.uid,
            feature = limitFeature
        )
        if (quotaCheck is UsageLimitCheckResult.LimitReached) {
            throw UniversalDailyLimitExceededException(limitFeature, quotaCheck.userMessage)
        }

        val startMonoMs = System.nanoTime() / 1_000_000L
        val cleanPrompt = userPrompt.trim()
        val effectivePrompt = buildUserInstruction(cleanPrompt, attachment)

        val testHook = streamOverrideForTest
        if (testHook != null) {
            val resultText = testHook(effectivePrompt, attachment, style, onPartialUpdate)
            if (resultText.isNotBlank()) {
                runCatching {
                    UserUsageLimitManager.recordUsage(context, auth.uid, limitFeature)
                }
            }
            val elapsed = ((System.nanoTime() / 1_000_000L) - startMonoMs).coerceAtLeast(1L)
            return@withContext UniversalSolveOutcome(
                fullText = resultText,
                latencyMs = elapsed,
                modelUsed = UNIVERSAL_MODEL
            )
        }

        val model = Firebase.ai.generativeModel(
            modelName = UNIVERSAL_MODEL,
            generationConfig = generationConfig {
                temperature = 0.0f
                topP = 0.1f
                maxOutputTokens = when (style) {
                    UniversalSolveStyle.QUICK_DIRECT -> 640
                    UniversalSolveStyle.DEEP_SOLVE,
                    UniversalSolveStyle.HINGLISH_TUTOR -> 2048
                }
            },
            systemInstruction = content {
                text(buildSystemPrompt(style, attachment?.type))
            }
        )

        val historyContents = buildHistoryContents(recentHistory)
        val decodedBitmaps = mutableListOf<Bitmap>()

        try {
            val accumulated = StringBuilder()

            val streamed = withTimeout(STREAM_TIMEOUT_MS) {
                when (attachment?.type) {
                    UniversalAttachmentType.PHOTO -> {
                        val photoBmp = decodeScaledBitmap(context, attachment.uri, MAX_IMAGE_DIM_PX)
                            ?: throw IllegalStateException("Could not open the selected photo. Please pick another image.")
                        decodedBitmaps.add(photoBmp)
                        val turnContent = content("user") {
                            image(photoBmp)
                            text(effectivePrompt)
                        }
                        streamContents(model, historyContents + turnContent, accumulated, onPartialUpdate)
                    }

                    UniversalAttachmentType.PDF -> {
                        val pdfBytes = readSmallPdfBytes(context, attachment.uri)
                        if (pdfBytes != null && pdfBytes.isNotEmpty()) {
                            try {
                                val turnContent = content("user") {
                                    inlineData(pdfBytes, "application/pdf")
                                    text(effectivePrompt)
                                }
                                streamContents(model, historyContents + turnContent, accumulated, onPartialUpdate)
                            } catch (inlineErr: Exception) {
                                if (inlineErr is CancellationException) throw inlineErr
                                Log.w(TAG, "Inline PDF upload failed, falling back to rendered page images: ${inlineErr.message}")
                                accumulated.clear()
                                val pageBitmaps = renderPdfPagesToBitmaps(context, attachment.uri, MAX_PDF_FALLBACK_PAGES)
                                decodedBitmaps.addAll(pageBitmaps)
                                if (pageBitmaps.isEmpty()) {
                                    throw IllegalStateException("Could not read pages from the selected PDF.")
                                }
                                val turnContent = content("user") {
                                    pageBitmaps.forEach { image(it) }
                                    text(effectivePrompt)
                                }
                                streamContents(model, historyContents + turnContent, accumulated, onPartialUpdate)
                            }
                        } else {
                            val pageBitmaps = renderPdfPagesToBitmaps(context, attachment.uri, MAX_PDF_FALLBACK_PAGES)
                            decodedBitmaps.addAll(pageBitmaps)
                            if (pageBitmaps.isEmpty()) {
                                throw IllegalStateException("Could not open the selected PDF file.")
                            }
                            val turnContent = content("user") {
                                pageBitmaps.forEach { image(it) }
                                text(effectivePrompt)
                            }
                            streamContents(model, historyContents + turnContent, accumulated, onPartialUpdate)
                        }
                    }

                    null -> {
                        val turnContent = content("user") {
                            text(effectivePrompt)
                        }
                        streamContents(model, historyContents + turnContent, accumulated, onPartialUpdate)
                    }
                }
            }

            if (streamed.isBlank()) {
                throw IllegalStateException("Could not generate a response. Please try again.")
            }

            runCatching {
                UserUsageLimitManager.recordUsage(
                    context = context.applicationContext,
                    uid = auth.uid,
                    feature = limitFeature
                )
            }

            val elapsed = ((System.nanoTime() / 1_000_000L) - startMonoMs).coerceAtLeast(1L)
            val telemetryFeature = when (attachment?.type) {
                UniversalAttachmentType.PHOTO -> TelemetryFeature.PHOTO_SOLVER
                UniversalAttachmentType.PDF -> TelemetryFeature.PDF_SOLVER
                null -> FEATURE_UNIVERSAL_ASK
            }
            runCatching {
                FirebaseTelemetryRepository.recordSolveRequest(
                    context = context.applicationContext,
                    feature = telemetryFeature,
                    backend = TelemetryBackend.FIREBASE_AI,
                    modelUsed = UNIVERSAL_MODEL,
                    latencyMs = elapsed,
                    status = TelemetryStatus.SUCCESS,
                    questionPreview = cleanPrompt.ifBlank { attachment?.displayName ?: "Prism Ask" }.take(96),
                    answerBadge = "ASK"
                )
            }

            UniversalSolveOutcome(
                fullText = streamed.trim(),
                latencyMs = elapsed,
                modelUsed = UNIVERSAL_MODEL
            )
        } catch (ce: CancellationException) {
            if (ce is TimeoutCancellationException) {
                throw IllegalStateException("Request timed out after 60 seconds. Please try again with a shorter prompt or fewer pages.")
            }
            throw ce
        } finally {
            decodedBitmaps.forEach { bmp ->
                try {
                    if (!bmp.isRecycled) bmp.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun streamContents(
        model: com.google.firebase.ai.GenerativeModel,
        contents: List<Content>,
        accumulated: StringBuilder,
        onPartialUpdate: (String) -> Unit
    ): String {
        model.generateContentStream(contents).collect { chunk ->
            val chunkText = chunk.text.orEmpty()
            if (chunkText.isNotEmpty()) {
                accumulated.append(chunkText)
                onPartialUpdate(accumulated.toString())
            }
        }
        return accumulated.toString()
    }

    private fun buildHistoryContents(recentHistory: List<UniversalTurnContext>): List<Content> {
        return recentHistory
            .filter { it.text.isNotBlank() }
            .takeLast(8)
            .map { turn ->
                content(if (turn.isUser) "user" else "model") {
                    text(turn.text.take(2400))
                }
            }
    }

    internal fun buildUserInstruction(
        cleanPrompt: String,
        attachment: UniversalAttachment?
    ): String {
        if (cleanPrompt.isNotBlank()) return cleanPrompt
        return when (attachment?.type) {
            UniversalAttachmentType.PHOTO ->
                "Inspect this image carefully and solve whatever is shown. If it contains ONE question or problem, provide ONE single verified final answer followed by a clear explanation. If it contains multiple distinct numbered questions, solve each one in order. Double-check all math, negation words (NOT/EXCEPT), diagrams, and option labels before writing the final answer."
            UniversalAttachmentType.PDF ->
                "Read this PDF carefully. If it contains questions, problems, or a worksheet, solve each one clearly in order with verified answers and concise explanations. If it contains study notes or an article, provide a crisp structured summary of the key concepts and formulas."
            null -> ""
        }
    }

    internal fun buildSystemPrompt(
        style: UniversalSolveStyle,
        attachmentType: UniversalAttachmentType?
    ): String {
        val styleGuidance = when (style) {
            UniversalSolveStyle.DEEP_SOLVE ->
                "Lead with a clear **Final Answer** line, then explain the exact reasoning step by step in clean, readable sections. Keep formatting crisp with short bullets or numbered steps."
            UniversalSolveStyle.QUICK_DIRECT ->
                "Lead immediately with the exact **Final Answer**, followed by a brief 2 to 3 line explanation of why it is correct. Stay concise and direct."
            UniversalSolveStyle.HINGLISH_TUTOR ->
                "Lead with the clear **Final Answer**, then explain the concept and steps in warm, clear Hinglish (simple English + Roman Hindi) so an Indian student can understand it effortlessly."
        }

        val mediaGuidance = when (attachmentType) {
            UniversalAttachmentType.PHOTO ->
                "CRITICAL VISUAL ACCURACY RULE: Read the attached image pixels directly (every symbol, fraction, exponent, negative sign, diagram label, and option letter). Verify your calculation or logic internally BEFORE writing the answer. Never split a single question into multiple conflicting answers and never guess blindly."
            UniversalAttachmentType.PDF ->
                "CRITICAL PDF ACCURACY RULE: Read all visible pages carefully, preserve question numbering if it is an assignment or paper, and double-check every answer before outputting it."
            null ->
                "You are a universal AI problem solver and study partner, similar to Google Gemini. You can solve anything the user gives you: math, science, coding, debugging, essays, grammar, translations, summaries, concept explanations, or MCQs."
        }

        return buildString {
            appendLine("You are Prism Ask, an accurate, versatile problem-solving assistant.")
            appendLine(mediaGuidance)
            appendLine(styleGuidance)
            append("Never use em dashes. Use plain punctuation (periods, commas, colons). Never hallucinate content that is not in the prompt, image, or PDF.")
        }
    }

    private fun decodeScaledBitmap(
        context: Context,
        uri: Uri,
        maxDimensionPx: Int
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val rawW = bounds.outWidth
        val rawH = bounds.outHeight
        if (rawW <= 0 || rawH <= 0) {
            return context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
        var sampleSize = 1
        while ((rawW / sampleSize) > maxDimensionPx * 2 || (rawH / sampleSize) > maxDimensionPx * 2) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val largest = max(decoded.width, decoded.height)
        if (largest <= maxDimensionPx) return decoded
        val scale = maxDimensionPx.toFloat() / largest.toFloat()
        val targetW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (decoded.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        if (scaled !== decoded) {
            try { decoded.recycle() } catch (_: Exception) {}
        }
        return scaled
    }

    private fun readSmallPdfBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_INLINE_PDF_BYTES) return null
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read inline PDF bytes: ${e.message}")
            null
        }
    }

    private fun renderPdfPagesToBitmaps(
        context: Context,
        uri: Uri,
        maxPages: Int
    ): List<Bitmap> {
        val result = mutableListOf<Bitmap>()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = renderer.pageCount.coerceAtMost(maxPages)
                    for (index in 0 until count) {
                        renderer.openPage(index).use { page ->
                            val scale = (1300f / page.width.coerceAtLeast(1)).coerceIn(1.1f, 2.0f)
                            val w = (page.width * scale).roundToInt().coerceIn(540, 1440)
                            val h = (page.height * scale).roundToInt().coerceIn(720, 2000)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            result.add(bmp)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PdfRenderer fallback failed: ${e.message}")
            result.forEach { try { it.recycle() } catch (_: Exception) {} }
            result.clear()
        }
        return result
    }
}
