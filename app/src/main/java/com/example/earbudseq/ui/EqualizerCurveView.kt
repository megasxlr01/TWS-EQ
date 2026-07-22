package com.example.earbudseq.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.earbudseq.R
import kotlin.math.abs

/**
 * A draggable multi-band EQ curve: vertical gridlines per band, a filled gradient curve
 * connecting each band's gain, a dot per band, gain value labels above each point, and
 * frequency labels below. Number of bands is whatever the device's real Equalizer
 * effect reports (commonly 5-6) rather than a fixed 10, since that's a hardware/driver
 * property that varies — unlike the fixed-band graphic EQs common on iOS.
 */
class EqualizerCurveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Band(val freqLabel: String, var gainDb: Float, val minDb: Float, val maxDb: Float)

    var bands: List<Band> = emptyList()
        set(value) { field = value; invalidate() }

    /** Fired continuously while dragging a point: (bandIndex, newGainDb). */
    var onBandChanged: ((Int, Float) -> Unit)? = null

    private val accent = context.getColor(R.color.accent)
    private val gridColor = context.getColor(R.color.divider)
    private val textPrimary = context.getColor(R.color.text_primary)
    private val textSecondary = context.getColor(R.color.text_secondary)

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gridColor; strokeWidth = 2f }
    private val gainTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textPrimary; textSize = 30f; textAlign = Paint.Align.CENTER
    }
    private val freqTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textSecondary; textSize = 28f; textAlign = Paint.Align.CENTER
    }

    private val topLabelSpace = 60f
    private val bottomLabelSpace = 56f
    private val sidePadding = 24f

    private var draggingIndex: Int? = null

    private fun plotTop() = topLabelSpace
    private fun plotBottom() = height - bottomLabelSpace
    private fun plotHeight() = plotBottom() - plotTop()

    private fun xFor(index: Int): Float {
        if (bands.size <= 1) return width / 2f
        val usable = width - 2 * sidePadding
        return sidePadding + usable * index / (bands.size - 1)
    }

    private fun yFor(band: Band): Float {
        val span = band.maxDb - band.minDb
        if (span <= 0f) return plotTop() + plotHeight() / 2f
        val t = (band.gainDb - band.minDb) / span
        return plotBottom() - t * plotHeight()
    }

    private fun gainForY(band: Band, y: Float): Float {
        val span = band.maxDb - band.minDb
        val t = ((plotBottom() - y) / plotHeight()).coerceIn(0f, 1f)
        return band.minDb + t * span
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bands.isEmpty() || width == 0 || height == 0) return

        // Vertical gridlines per band
        for (i in bands.indices) {
            val x = xFor(i)
            canvas.drawLine(x, plotTop(), x, plotBottom(), gridPaint)
        }

        val points = bands.mapIndexed { i, b -> xFor(i) to yFor(b) }

        // Filled gradient under the curve
        fillPaint.shader = LinearGradient(
            0f, plotTop(), 0f, plotBottom(),
            Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent)),
            Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent)),
            Shader.TileMode.CLAMP
        )
        val fillPath = Path().apply {
            moveTo(points.first().first, plotBottom())
            points.forEach { (x, y) -> lineTo(x, y) }
            lineTo(points.last().first, plotBottom())
            close()
        }
        canvas.drawPath(fillPath, fillPaint)

        // Curve line
        val linePath = Path().apply {
            points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
        }
        canvas.drawPath(linePath, curvePaint)

        // Dots + labels
        bands.forEachIndexed { i, band ->
            val (x, y) = points[i]
            canvas.drawCircle(x, y, 9f, dotPaint)
            val sign = if (band.gainDb >= 0) "+" else ""
            canvas.drawText("$sign${"%.1f".format(band.gainDb)}", x, topLabelSpace - 20f, gainTextPaint)
            canvas.drawText(band.freqLabel, x, height - 14f, freqTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bands.isEmpty()) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggingIndex = bands.indices.minByOrNull { abs(xFor(it) - event.x) }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = draggingIndex ?: return false
                val newGain = gainForY(bands[idx], event.y)
                bands[idx].gainDb = newGain
                onBandChanged?.invoke(idx, newGain)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingIndex = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
