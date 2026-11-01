package com.omnisolve.overlay.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive Liquid Glass Crop-to-Scan Region Selector ("Snipper"):
 * Allows the user to drag a glowing optical selection box over a specific diagram, circuit,
 * graph, or single question when a PDF or webpage shows multiple questions at once.
 */
class CropRegionOverlayView(
    context: Context,
    private val onRegionSelected: (Rect) -> Unit,
    private val onCancelled: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(135, 9, 13, 22)
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 56, 189, 248)
        style = Paint.Style.STROKE
        strokeWidth = 7f * density
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(56, 189, 248)
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * density
    }

    private val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.1f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 15, 23, 42)
        style = Paint.Style.FILL
    }

    private val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 56, 189, 248)
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
    }

    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var startX = 0f
    private var startY = 0f
    private var curX = 0f
    private var curY = 0f
    private var isDraggingBox = false

    private val cancelChipRect = RectF()
    private val selectionRectF = RectF()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Translucent obsidian scrim
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        // 2. Top instruction + Cancel pill
        val pillWidth = min(width * 0.88f, 340f * density)
        val pillHeight = 40f * density
        val pillLeft = (width - pillWidth) / 2f
        val pillTop = 44f * density
        cancelChipRect.set(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight)
        canvas.drawRoundRect(cancelChipRect, 20f * density, 20f * density, pillBgPaint)
        canvas.drawRoundRect(cancelChipRect, 20f * density, 20f * density, pillBorderPaint)
        canvas.drawText(
            "Drag box over question/diagram  •  Tap here to Cancel",
            cancelChipRect.centerX(),
            cancelChipRect.centerY() + 4.5f * density,
            pillTextPaint
        )

        // 3. Clear & highlight active Liquid Glass crop box
        if (isDraggingBox) {
            val l = min(startX, curX)
            val t = min(startY, curY)
            val r = max(startX, curX)
            val b = max(startY, curY)
            selectionRectF.set(l, t, r, b)

            val corner = 12f * density
            canvas.drawRoundRect(selectionRectF, corner, corner, clearPaint)
            canvas.drawRoundRect(selectionRectF, corner, corner, glowPaint)
            canvas.drawRoundRect(selectionRectF, corner, corner, borderPaint)
            canvas.drawRoundRect(selectionRectF, corner, corner, innerRimPaint)

            // Corner optical handles
            val handleRadius = 5.5f * density
            canvas.drawCircle(l, t, handleRadius, handlePaint)
            canvas.drawCircle(r, t, handleRadius, handlePaint)
            canvas.drawCircle(l, b, handleRadius, handlePaint)
            canvas.drawCircle(r, b, handleRadius, handlePaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val offsetX = loc[0]
        val offsetY = loc[1]

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (cancelChipRect.contains(event.x, event.y)) {
                    onCancelled()
                    return true
                }
                startX = event.x
                startY = event.y
                curX = event.x
                curY = event.y
                isDraggingBox = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingBox) {
                    curX = event.x.coerceIn(0f, width.toFloat())
                    curY = event.y.coerceIn(0f, height.toFloat())
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDraggingBox) {
                    isDraggingBox = false
                    val minSizePx = (36f * density).toInt()
                    val left = min(startX, curX).toInt() + offsetX
                    val top = min(startY, curY).toInt() + offsetY
                    val right = max(startX, curX).toInt() + offsetX
                    val bottom = max(startY, curY).toInt() + offsetY
                    if (abs(right - left) >= minSizePx && abs(bottom - top) >= minSizePx) {
                        onRegionSelected(Rect(left, top, right, bottom))
                    } else {
                        onCancelled()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDraggingBox = false
                onCancelled()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
