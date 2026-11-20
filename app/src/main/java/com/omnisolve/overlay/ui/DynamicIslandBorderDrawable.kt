package com.omnisolve.overlay.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * Pure-OLED iOS Dynamic Island capsule drawable with continuous squircle corner adaptation
 * and a finite 360-degree theme-tinted sweep border animation.
 */
class DynamicIslandBorderDrawable(
    private val density: Float
) : Drawable() {

    companion object {
        const val ACCENT_CRIMSON = "CRIMSON"
        const val ACCENT_EMERALD = "EMERALD"
        const val ACCENT_CYAN = "CYAN"
        const val ACCENT_VIOLET = "VIOLET"
        const val MAX_ORBIT_CYCLES = 4

        fun accentHex(theme: String): String = when (theme.uppercase()) {
            ACCENT_EMERALD -> "#10B981"
            ACCENT_CYAN -> "#06B6D4"
            ACCENT_VIOLET -> "#A855F7"
            else -> "#D71921"
        }
    }

    private val bodyRect = RectF()
    private val glassRect = RectF()
    private val borderRect = RectF()
    private val shaderMatrix = Matrix()

    private var accentColorInt: Int = Color.parseColor("#D71921")
    private var isLightSurface: Boolean = false
    private var customSurfaceInt: Int? = null
    private var hostVisible: Boolean = true

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val topSpecularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Subtle Apple titanium-glass hairline rim when resting
    private val idleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.95f * density
        color = Color.parseColor("#2BFFFFFF")
    }

    private val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f * density
        color = Color.parseColor("#14FFFFFF")
    }

    private val activeHaloBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.0f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val activeCoreBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.75f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var sweepShader: SweepGradient? = null
    private var centerX = 0f
    private var centerY = 0f
    private var rotationDegrees = 0f
    private var isScanningActive = false
    private var orbitAnimator: ValueAnimator? = null

    fun setAccentTheme(theme: String) {
        accentColorInt = Color.parseColor(accentHex(theme))
        rebuildSweepShader()
        invalidateSelf()
    }

    fun setCustomAccentHex(customHex: String?, fallbackTheme: String = ACCENT_CRIMSON) {
        val candidate = customHex?.trim().orEmpty()
        accentColorInt = if (candidate.isNotEmpty()) {
            try {
                Color.parseColor(if (candidate.startsWith("#")) candidate else "#$candidate")
            } catch (_: Exception) {
                Color.parseColor(accentHex(fallbackTheme))
            }
        } else {
            Color.parseColor(accentHex(fallbackTheme))
        }
        rebuildSweepShader()
        invalidateSelf()
    }

    fun getAccentColorInt(): Int = accentColorInt

    fun isLightSurfaceMode(): Boolean = isLightSurface

    /**
     * Adapts the floating Dynamic Island capsule between pure OLED Dark Obsidian and
     * frosted iOS Liquid Glass Light (or any custom theme surface hex).
     */
    fun setThemeSurfaceMode(isLight: Boolean, surfaceHex: String? = null) {
        isLightSurface = isLight
        customSurfaceInt = surfaceHex?.trim()?.takeIf { it.isNotEmpty() }?.let { hex ->
            try {
                Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
            } catch (_: Exception) {
                null
            }
        }
        if (isLightSurface) {
            idleBorderPaint.color = Color.parseColor("#38000000")
            innerRimPaint.color = Color.parseColor("#99FFFFFF")
        } else {
            idleBorderPaint.color = Color.parseColor("#2BFFFFFF")
            innerRimPaint.color = Color.parseColor("#14FFFFFF")
        }
        rebuildBodyShaders()
        invalidateSelf()
    }

    fun setHostVisible(visible: Boolean) {
        if (hostVisible == visible) return
        hostVisible = visible
        if (!visible) {
            orbitAnimator?.cancel()
            orbitAnimator = null
        } else if (isScanningActive) {
            startOrbitAnimation()
        }
    }

    fun setScanning(active: Boolean) {
        if (isScanningActive == active) return
        isScanningActive = active
        if (active) {
            if (hostVisible) startOrbitAnimation()
        } else {
            stopOrbitAnimation()
        }
        invalidateSelf()
    }

    fun isScanning(): Boolean = isScanningActive

    fun isAnimating(): Boolean = orbitAnimator?.isRunning == true

    private fun startOrbitAnimation() {
        if (!hostVisible) return
        if (orbitAnimator?.isRunning == true) return
        orbitAnimator?.cancel()
        var wasCancelled = false
        orbitAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1200L
            repeatCount = MAX_ORBIT_CYCLES - 1
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                rotationDegrees = anim.animatedValue as Float
                val shader = sweepShader
                if (shader != null) {
                    shaderMatrix.setRotate(rotationDegrees, centerX, centerY)
                    shader.setLocalMatrix(shaderMatrix)
                }
                invalidateSelf()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    wasCancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    orbitAnimator = null
                    rotationDegrees = 0f
                    if (!wasCancelled && isScanningActive) {
                        isScanningActive = false
                    }
                    invalidateSelf()
                }
            })
            start()
        }
    }

    fun stopOrbitAnimation() {
        isScanningActive = false
        orbitAnimator?.cancel()
        orbitAnimator = null
        rotationDegrees = 0f
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        val inset = 1.4f * density
        bodyRect.set(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset
        )
        borderRect.set(bodyRect)

        glassRect.set(
            bodyRect.left + 12f * density,
            bodyRect.top + 0.8f * density,
            bodyRect.right - 12f * density,
            bodyRect.top + min(h * 0.34f, 14f * density)
        )

        centerX = bounds.exactCenterX()
        centerY = bounds.exactCenterY()

        rebuildBodyShaders()
        rebuildSweepShader()
    }

    private fun rebuildBodyShaders() {
        if (bodyRect.isEmpty) return
        if (isLightSurface) {
            // Frosted Apple iOS Liquid Glass Light capsule
            bodyPaint.shader = LinearGradient(
                0f,
                bodyRect.top,
                0f,
                bodyRect.bottom,
                intArrayOf(
                    Color.parseColor("#FCFFFFFF"),
                    Color.parseColor("#F5F5F7FF"),
                    Color.parseColor("#F2EBEBF0")
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
            topSpecularPaint.shader = LinearGradient(
                0f,
                glassRect.top,
                0f,
                glassRect.bottom,
                Color.parseColor("#B0FFFFFF"),
                Color.parseColor("#00FFFFFF"),
                Shader.TileMode.CLAMP
            )
        } else {
            val topColor = customSurfaceInt?.let { base ->
                Color.rgb(
                    (Color.red(base) * 0.45f).toInt().coerceIn(5, 36),
                    (Color.green(base) * 0.45f).toInt().coerceIn(5, 36),
                    (Color.blue(base) * 0.45f).toInt().coerceIn(7, 44)
                )
            } ?: Color.parseColor("#FF050507")
            val midColor = customSurfaceInt?.let { base ->
                Color.rgb(
                    (Color.red(base) * 0.28f).toInt().coerceIn(0, 24),
                    (Color.green(base) * 0.28f).toInt().coerceIn(0, 24),
                    (Color.blue(base) * 0.28f).toInt().coerceIn(2, 30)
                )
            } ?: Color.parseColor("#FF000000")
            bodyPaint.shader = LinearGradient(
                0f,
                bodyRect.top,
                0f,
                bodyRect.bottom,
                intArrayOf(topColor, midColor, Color.parseColor("#FF030305")),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            topSpecularPaint.shader = LinearGradient(
                0f,
                glassRect.top,
                0f,
                glassRect.bottom,
                Color.parseColor("#18FFFFFF"),
                Color.parseColor("#00FFFFFF"),
                Shader.TileMode.CLAMP
            )
        }
    }

    private fun rebuildSweepShader() {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val r = Color.red(accentColorInt)
        val g = Color.green(accentColorInt)
        val b = Color.blue(accentColorInt)
        val softTail = Color.argb(24, r, g, b)
        val midTail = Color.argb(65, r, g, b)
        val glowBody = Color.argb(205, r, g, b)
        val fullAccent = Color.argb(255, r, g, b)
        val brightTip = Color.argb(255, min(255, r + 90), min(255, g + 90), min(255, b + 90))

        val colors = intArrayOf(
            softTail,
            midTail,
            glowBody,
            fullAccent,
            brightTip,
            Color.WHITE,
            fullAccent,
            softTail
        )
        val positions = floatArrayOf(0.00f, 0.36f, 0.64f, 0.78f, 0.86f, 0.91f, 0.96f, 1.00f)

        sweepShader = SweepGradient(centerX, centerY, colors, positions).also { shader ->
            shaderMatrix.setRotate(rotationDegrees, centerX, centerY)
            shader.setLocalMatrix(shaderMatrix)
            activeCoreBorderPaint.shader = shader
            activeHaloBorderPaint.shader = shader
            activeHaloBorderPaint.alpha = 88
        }
    }

    override fun draw(canvas: Canvas) {
        if (bodyRect.isEmpty) return
        val cornerRadius = min(bodyRect.height() / 2f, 34f * density)

        canvas.drawRoundRect(bodyRect, cornerRadius, cornerRadius, bodyPaint)

        val glassRadius = min(glassRect.height() / 2f, cornerRadius)
        canvas.drawRoundRect(glassRect, glassRadius, glassRadius, topSpecularPaint)

        canvas.drawRoundRect(bodyRect, cornerRadius, cornerRadius, innerRimPaint)

        if (isScanningActive && sweepShader != null) {
            canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, activeHaloBorderPaint)
            canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, activeCoreBorderPaint)
        } else {
            canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, idleBorderPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        bodyPaint.alpha = alpha
        topSpecularPaint.alpha = (alpha * 0.12f).toInt()
        idleBorderPaint.alpha = (alpha * 0.35f).toInt()
        innerRimPaint.alpha = (alpha * 0.16f).toInt()
        activeCoreBorderPaint.alpha = alpha
        activeHaloBorderPaint.alpha = (alpha * 0.35f).toInt()
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bodyPaint.colorFilter = colorFilter
        activeCoreBorderPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
