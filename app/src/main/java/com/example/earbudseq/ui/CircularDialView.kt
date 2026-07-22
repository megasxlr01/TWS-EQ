package com.example.earbudseq.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.earbudseq.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A speedometer-style arc gauge: a 270-degree ring, gray track behind, accent-colored
 * arc showing the current percentage, a dot at the arc's end, percentage text in the
 * center. Drag anywhere on the ring (or across the view) to adjust the value.
 */
class CircularDialView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 0-100 */
    var value: Int = 0
        set(v) { field = v.coerceIn(0, 100); invalidate() }

    var onValueChanged: ((Int) -> Unit)? = null

    private val accent = context.getColor(R.color.accent)
    private val track = context.getColor(R.color.divider)
    private val textPrimary = context.getColor(R.color.text_primary)

    // 270-degree sweep, opening centered at the bottom.
    private val startAngle = 135f
    private val sweepAngle = 270f

    private val strokeWidth = 16f
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = track; style = Paint.Style.STROKE; this.strokeWidth = this@CircularDialView.strokeWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent; style = Paint.Style.STROKE; this.strokeWidth = this@CircularDialView.strokeWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textPrimary; textAlign = Paint.Align.CENTER; textSize = 40f
    }

    private val rect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = strokeWidth
        rect.set(pad, pad, w - pad, h - pad)
        textPaint.textSize = h * 0.22f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(rect, startAngle, sweepAngle, false, trackPaint)
        val valueSweep = sweepAngle * (value / 100f)
        canvas.drawArc(rect, startAngle, valueSweep, false, arcPaint)

        val endAngleRad = Math.toRadians((startAngle + valueSweep).toDouble())
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.width() / 2f
        val dotX = cx + r * cos(endAngleRad).toFloat()
        val dotY = cy + r * sin(endAngleRad).toFloat()
        canvas.drawCircle(dotX, dotY, strokeWidth * 0.7f, dotPaint)

        canvas.drawText("$value%", cx, cy + textPaint.textSize / 3f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val cx = rect.centerX()
                val cy = rect.centerY()
                var angleDeg = Math.toDegrees(
                    atan2((event.y - cy).toDouble(), (event.x - cx).toDouble())
                ).toFloat()
                angleDeg = (angleDeg - startAngle + 360f) % 360f
                if (angleDeg > sweepAngle) {
                    // Snap to whichever end of the arc is closer when dragging past it.
                    angleDeg = if (angleDeg - sweepAngle < (360f - angleDeg)) sweepAngle else 0f
                }
                val newValue = ((angleDeg / sweepAngle) * 100f).toInt().coerceIn(0, 100)
                if (newValue != value) {
                    value = newValue
                    onValueChanged?.invoke(newValue)
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
