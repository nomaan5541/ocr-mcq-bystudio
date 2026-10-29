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
import android.view.WindowManager
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

    @Volatile
    private var isReady = false

    @Volatile
    private var callbackRegistered = false

    private val frameLock = Any()
    private var lastValidFrame: Bitmap? = null

    @Volatile
    private var lastFrameTimestampMs: Long = 0L

    init {
        detectScreenDimensions()
    }

    private fun detectScreenDimensions() {
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

        // Cap capture resolution to 1280px max side (even-aligned) to minimize gralloc/rendernode
        // buffer pressure on cloud emulators while keeping text crisp for ML Kit OCR.
        val longest = max(rawW, rawH)
        val scale = if (longest > MAX_CAPTURE_DIMENSION) {
            MAX_CAPTURE_DIMENSION.toFloat() / longest.toFloat()
        } else {
            1.0f
        }

        screenWidth = ((rawW * scale).roundToInt() and 1.inv()).coerceAtLeast(360)
        screenHeight = ((rawH * scale).roundToInt() and 1.inv()).coerceAtLeast(360)
        screenDensity = (rawDensity * scale).roundToInt().coerceAtLeast(160)

        Log.d(TAG, "Capture target: ${screenWidth}x${screenHeight} @${screenDensity}dpi")
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
                    }
                }, Handler(Looper.getMainLooper()))
                callbackRegistered = true
            }

            val bgHandler = ensureCaptureThread()
            val reader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )

            // Continuously drain and cache the latest VirtualDisplay frame so static emulator screens
            // always have an up-to-date frame ready immediately without stalling ImageReader buffers.
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

    /**
     * Returns a clean copy of the latest screen frame.
     * Waits briefly for any post-overlay-hide dirty frame, then immediately returns the live cached frame.
     */
    fun captureCurrentFrame(): Bitmap? {
        val prevW = screenWidth
        val prevH = screenHeight
        detectScreenDimensions()
        val dimensionsChanged = (prevW != screenWidth || prevH != screenHeight)

        if (!isReady || imageReader == null || dimensionsChanged) {
            if (mediaProjection == null) return null
            if (dimensionsChanged) {
                synchronized(frameLock) {
                    try { lastValidFrame?.recycle() } catch (_: Exception) {}
                    lastValidFrame = null
                }
            }
            initVirtualDisplay()
        }

        val callStartMs = System.currentTimeMillis()
        val hasCachedInitially = synchronized(frameLock) {
            lastValidFrame != null && !lastValidFrame!!.isRecycled
        }
        val maxWaitLoops = if (hasCachedInitially) 5 else 20

        for (i in 1..maxWaitLoops) {
            val freshArrived = synchronized(frameLock) {
                lastFrameTimestampMs >= callStartMs && lastValidFrame != null && !lastValidFrame!!.isRecycled
            }
            if (freshArrived) break
            Thread.sleep(25)
        }

        synchronized(frameLock) {
            val cached = lastValidFrame
            if (cached != null && !cached.isRecycled) {
                return try {
                    cached.copy(Bitmap.Config.ARGB_8888, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy cached frame", e)
                    null
                }
            }
        }

        // Direct fallback pull if listener hasn't fired yet
        val reader = imageReader ?: return null
        val directImg = safeAcquireImage(reader) ?: return null
        val directBmp = try {
            imageToBitmap(directImg)
        } finally {
            safeClose(directImg)
        } ?: return null

        synchronized(frameLock) {
            val old = lastValidFrame
            lastValidFrame = directBmp.copy(Bitmap.Config.ARGB_8888, false)
            lastFrameTimestampMs = System.currentTimeMillis()
            if (old != null && !old.isRecycled) {
                try { old.recycle() } catch (_: Exception) {}
            }
        }
        return directBmp
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
        synchronized(frameLock) {
            try { lastValidFrame?.recycle() } catch (_: Exception) {}
            lastValidFrame = null
        }
        captureHandler = null
        captureThread = null
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        Log.d(TAG, "Capture stopped & resources released")
    }
}
