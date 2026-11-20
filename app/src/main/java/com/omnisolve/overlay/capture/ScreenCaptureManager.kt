package com.omnisolve.overlay.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import com.omnisolve.overlay.util.PrismPerfLogger
import kotlin.math.max
import kotlin.math.roundToInt

class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureManager"
        private const val MAX_CAPTURE_DIMENSION = 1280
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = 320
    private var rawScreenWidth = 1080
    private var rawScreenHeight = 2400
    private var cachedRotation = -1
    private var dimensionsDetected = false

    @Volatile
    private var isReady = false

    @Volatile
    private var callbackRegistered = false

    private val frameLock = Any()
    private var lastValidFrame: Bitmap? = null

    @Volatile
    private var lastFrameTimestampMs: Long = 0L

    @Volatile
    var onProjectionStoppedListener: (() -> Unit)? = null

    init {
        detectScreenDimensionsIfNeeded(force = true)
    }

    private fun currentDisplayRotation(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.rotation ?: Surface.ROTATION_0
            } else {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }
        } catch (_: Exception) {
            Surface.ROTATION_0
        }
    }

    private fun detectScreenDimensionsIfNeeded(force: Boolean = false): Boolean {
        val rot = currentDisplayRotation()
        if (!force && dimensionsDetected && rot == cachedRotation) {
            return false
        }
        val prevW = screenWidth
        val prevH = screenHeight
        var rawW = 1080
        var rawH = 2400
        var rawDensity = 320
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                rawW = bounds.width()
                rawH = bounds.height()
                rawDensity = context.resources.configuration.densityDpi
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(metrics)
                rawW = metrics.widthPixels
                rawH = metrics.heightPixels
                rawDensity = metrics.densityDpi
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not detect screen dimensions, using defaults", e)
        }

        if (rawW <= 0) rawW = 1080
        if (rawH <= 0) rawH = 2400
        if (rawDensity <= 0) rawDensity = 320

        rawScreenWidth = rawW
        rawScreenHeight = rawH
        cachedRotation = rot
        dimensionsDetected = true

        val longest = max(rawW, rawH)
        val scale = if (longest > MAX_CAPTURE_DIMENSION) {
            MAX_CAPTURE_DIMENSION.toFloat() / longest.toFloat()
        } else {
            1.0f
        }

        screenWidth = ((rawW * scale).roundToInt() and 1.inv()).coerceAtLeast(360)
        screenHeight = ((rawH * scale).roundToInt() and 1.inv()).coerceAtLeast(360)
        screenDensity = (rawDensity * scale).roundToInt().coerceAtLeast(160)

        Log.d(TAG, "Capture target: ${screenWidth}x${screenHeight} (display ${rawScreenWidth}x${rawScreenHeight}) @${screenDensity}dpi")
        return (prevW != screenWidth || prevH != screenHeight)
    }

    fun getRealScreenDimensions(): Pair<Int, Int> {
        return Pair(rawScreenWidth.coerceAtLeast(screenWidth), rawScreenHeight.coerceAtLeast(screenHeight))
    }

    private fun ensureCaptureThread(): Handler {
        val existingHandler = captureHandler
        val existingThread = captureThread
        if (existingHandler != null && existingThread != null && existingThread.isAlive) {
            return existingHandler
        }
        val thread = HandlerThread("PrismScreenCapture").apply { start() }
        val handler = Handler(thread.looper)
        captureThread = thread
        captureHandler = handler
        return handler
    }

    fun setMediaProjection(projection: MediaProjection) {
        mediaProjection = projection
        callbackRegistered = false
        initVirtualDisplay()
    }

    @SuppressLint("WrongConstant")
    @Synchronized
    private fun initVirtualDisplay() {
        val proj = mediaProjection ?: run {
            Log.e(TAG, "initVirtualDisplay: mediaProjection is null")
            return
        }

        try {
            try { virtualDisplay?.release() } catch (_: Exception) {}
            try { imageReader?.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
            try { imageReader?.close() } catch (_: Exception) {}
            virtualDisplay = null
            imageReader = null

            if (!callbackRegistered) {
                proj.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.d(TAG, "MediaProjection stopped externally")
                        isReady = false
                        clearFrameCache()
                        try {
                            onProjectionStoppedListener?.invoke()
                        } catch (_: Exception) {}
                    }
                }, Handler(Looper.getMainLooper()))
                callbackRegistered = true
            }

            val bgHandler = ensureCaptureThread()
            val reader = createAndAttachImageReader(bgHandler)
            imageReader = reader
            virtualDisplay = proj.createVirtualDisplay(
                "OmniSolveCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                bgHandler
            )

            isReady = true
            Log.d(TAG, "VirtualDisplay created: ${screenWidth}x${screenHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualDisplay", e)
            isReady = false
        }
    }

    @SuppressLint("WrongConstant")
    private fun createAndAttachImageReader(bgHandler: Handler): ImageReader {
        val reader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            3
        )
        reader.setOnImageAvailableListener({ r ->
            val img = safeAcquireImage(r) ?: return@setOnImageAvailableListener
            val bmp = try {
                imageToBitmap(img)
            } finally {
                safeClose(img)
            }
            if (bmp != null) {
                synchronized(frameLock) {
                    val old = lastValidFrame
                    lastValidFrame = bmp
                    lastFrameTimestampMs = System.currentTimeMillis()
                    if (old != null && old !== bmp && !old.isRecycled) {
                        try { old.recycle() } catch (_: Exception) {}
                    }
                }
            }
        }, bgHandler)
        return reader
    }

    @Synchronized
    private fun refreshVirtualDisplaySurface() {
        val vd = virtualDisplay
        if (vd == null || !isReady) {
            initVirtualDisplay()
            return
        }
        try {
            val bgHandler = ensureCaptureThread()
            val oldReader = imageReader
            val newReader = createAndAttachImageReader(bgHandler)
            imageReader = newReader
            vd.surface = newReader.surface
            if (oldReader != null) {
                bgHandler.postDelayed({
                    try { oldReader.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
                    try { oldReader.close() } catch (_: Exception) {}
                }, 120L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Surface refresh fallback to initVirtualDisplay: ${e.message}")
            initVirtualDisplay()
        }
    }

    fun isCaptureReady(): Boolean = isReady && imageReader != null && virtualDisplay != null && mediaProjection != null

    /**
     * Clears any cached screen frame and resets its timestamp so subsequent OCR passes
     * cannot accidentally solve a stale previous-screen screenshot.
     */
    fun clearFrameCache() {
        synchronized(frameLock) {
            val old = lastValidFrame
            lastValidFrame = null
            lastFrameTimestampMs = 0L
            if (old != null && !old.isRecycled) {
                try {
                    old.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    @VisibleForTesting
    internal fun injectTestFrameForTesting(bitmap: Bitmap, timestampMs: Long) {
        synchronized(frameLock) {
            val old = lastValidFrame
            lastValidFrame = bitmap
            lastFrameTimestampMs = timestampMs
            if (old != null && old !== bitmap && !old.isRecycled) {
                try {
                    old.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    @VisibleForTesting
    internal fun simulateProjectionStopForTesting() {
        isReady = false
        clearFrameCache()
    }

    @VisibleForTesting
    internal fun hasCachedFrameForTesting(): Boolean = synchronized(frameLock) {
        lastValidFrame != null && !lastValidFrame!!.isRecycled
    }

    @VisibleForTesting
    internal fun getLastFrameTimestampMsForTesting(): Long = synchronized(frameLock) {
        lastFrameTimestampMs
    }

    /**
     * Captures a guaranteed fresh frame of the CURRENT screen quickly without redundant
     * WindowManager queries or disruptive ImageReader swaps mid-capture.
     */
    fun captureGuaranteedFreshFrame(minTimestampMs: Long = 0L, sessionId: Long = 0L): Bitmap? {
        val capStart = PrismPerfLogger.nowMs()
        if (!isReady || imageReader == null || virtualDisplay == null) {
            if (mediaProjection == null) {
                PrismPerfLogger.logStage(sessionId, "SCREEN_CAPTURE", 0L, status = "NO_PROJECTION")
                return null
            }
            initVirtualDisplay()
        } else if (detectScreenDimensionsIfNeeded(force = false)) {
            try {
                virtualDisplay?.resize(screenWidth, screenHeight, screenDensity)
            } catch (_: Exception) {}
        }

        drainPendingImageFromReader()

        if (minTimestampMs > 0L) {
            var refreshedSurface = false
            for (attempt in 1..10) {
                val freshArrived = synchronized(frameLock) {
                    lastValidFrame != null &&
                        !lastValidFrame!!.isRecycled &&
                        lastFrameTimestampMs >= minTimestampMs
                }
                if (freshArrived) break
                // On static quiz screens (PDF, Chrome, Google Forms), SurfaceFlinger may not push a new
                // frame after hiding the Dynamic Island unless we nudge the VirtualDisplay surface once.
                if (attempt == 4 && !refreshedSurface) {
                    refreshedSurface = true
                    refreshVirtualDisplaySurface()
                }
                try { Thread.sleep(15) } catch (_: InterruptedException) { break }
                drainPendingImageFromReader()
            }
        }

        val cachedBmp = synchronized(frameLock) {
            val cached = lastValidFrame
            // Always prefer a frame newer than minTimestampMs, but if the screen is 100% static
            // and MediaProjection is still active, return the latest valid frame instead of null
            // so OCR + AI never fail with RETRY on static quiz pages.
            if (cached != null && !cached.isRecycled && isReady) {
                try {
                    cached.copy(Bitmap.Config.ARGB_8888, false)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }

        val result = cachedBmp ?: captureCurrentFrame(requireFresh = false)
        val elapsed = PrismPerfLogger.nowMs() - capStart
        PrismPerfLogger.logStage(
            sessionId,
            "SCREEN_CAPTURE",
            elapsed,
            status = if (result != null) "OK" else "EMPTY",
            detail = if (result != null) "${result.width}x${result.height}" else "stale_or_null_frame"
        )
        return result
    }

    private fun drainPendingImageFromReader() {
        val reader = imageReader ?: return
        try {
            val pendingImg = safeAcquireImage(reader) ?: return
            val bmp = try {
                imageToBitmap(pendingImg)
            } finally {
                safeClose(pendingImg)
            }
            if (bmp != null) {
                synchronized(frameLock) {
                    val old = lastValidFrame
                    lastValidFrame = bmp
                    lastFrameTimestampMs = System.currentTimeMillis()
                    if (old != null && old !== bmp && !old.isRecycled) {
                        try { old.recycle() } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun captureCurrentFrame(requireFresh: Boolean = true): Bitmap? {
        if (!isReady || imageReader == null || virtualDisplay == null) {
            if (mediaProjection == null) return null
            initVirtualDisplay()
        } else if (detectScreenDimensionsIfNeeded(force = false)) {
            try {
                virtualDisplay?.resize(screenWidth, screenHeight, screenDensity)
            } catch (_: Exception) {}
        }

        drainPendingImageFromReader()

        val callStartMs = System.currentTimeMillis()
        for (i in 1..4) {
            val freshArrived = synchronized(frameLock) {
                lastValidFrame != null &&
                    !lastValidFrame!!.isRecycled &&
                    (!requireFresh || lastFrameTimestampMs >= callStartMs - 60L)
            }
            if (freshArrived) break
            try { Thread.sleep(12) } catch (_: InterruptedException) { break }
        }

        synchronized(frameLock) {
            val cached = lastValidFrame
            val isFresh = !requireFresh || lastFrameTimestampMs >= callStartMs - 60L
            if (cached != null && !cached.isRecycled && isFresh) {
                return try {
                    cached.copy(Bitmap.Config.ARGB_8888, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy captured frame", e)
                    null
                }
            }
        }

        return null
    }

    private fun safeAcquireImage(reader: ImageReader): Image? {
        return try {
            reader.acquireLatestImage()
        } catch (_: Exception) {
            try {
                reader.acquireNextImage()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null

        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        if (pixelStride <= 0 || rowStride <= 0) return null

        val imgW = image.width.takeIf { it > 0 } ?: screenWidth
        val imgH = image.height.takeIf { it > 0 } ?: screenHeight
        val rowPadding = rowStride - pixelStride * imgW
        val bitmapWidth = imgW + (rowPadding / pixelStride).coerceAtLeast(0)
        if (bitmapWidth <= 0 || imgH <= 0) return null

        return try {
            buffer.rewind()
            val rawBitmap = Bitmap.createBitmap(bitmapWidth, imgH, Bitmap.Config.ARGB_8888)
            rawBitmap.copyPixelsFromBuffer(buffer)
            if (bitmapWidth != imgW) {
                val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, imgW, imgH)
                rawBitmap.recycle()
                cropped
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Bitmap from image buffer", e)
            null
        }
    }

    private fun safeClose(image: Image?) {
        try { image?.close() } catch (_: Exception) {}
    }

    @Synchronized
    fun stopCapture() {
        isReady = false
        callbackRegistered = false
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        try { captureThread?.quitSafely() } catch (_: Exception) {}
        clearFrameCache()
        captureHandler = null
        captureThread = null
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        Log.d(TAG, "Capture stopped & resources released")
    }
}
