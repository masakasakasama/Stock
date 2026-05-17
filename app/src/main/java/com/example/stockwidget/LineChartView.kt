package com.example.stockwidget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/** Minimal line chart drawn with Canvas (no external dependency). */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: List<Double> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x33FFFFFF
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textSize = 28f
    }

    fun setValues(v: List<Double>) {
        values = v
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = values
        if (data.size < 2) return

        val padL = 16f
        val padR = 96f
        val padV = 24f
        val w = width - padL - padR
        val h = height - padV * 2
        if (w <= 0 || h <= 0) return

        val min = data.minOrNull() ?: return
        val max = data.maxOrNull() ?: return
        val span = (max - min).let { if (it == 0.0) 1.0 else it }

        fun x(i: Int) = padL + w * i / (data.size - 1)
        fun y(value: Double) = padV + (h - (h * ((value - min) / span))).toFloat()

        // Horizontal grid lines + price labels (max / mid / min).
        listOf(max, (max + min) / 2, min).forEach { level ->
            val yy = y(level)
            canvas.drawLine(padL, yy, padL + w, yy, gridPaint)
            canvas.drawText(formatLevel(level), padL + w + 8f, yy + 10f, textPaint)
        }

        val up = data.last() >= data.first()
        val color = if (up) StockQuote.COLOR_UP else StockQuote.COLOR_DOWN
        linePaint.color = color

        val line = Path()
        val area = Path()
        for (i in data.indices) {
            val px = x(i)
            val py = y(data[i])
            if (i == 0) {
                line.moveTo(px, py)
                area.moveTo(px, padV + h)
                area.lineTo(px, py)
            } else {
                line.lineTo(px, py)
                area.lineTo(px, py)
            }
        }
        area.lineTo(x(data.size - 1), padV + h)
        area.close()

        fillPaint.color = (color and 0x00FFFFFF) or 0x22000000
        canvas.drawPath(area, fillPaint)
        canvas.drawPath(line, linePaint)
    }

    private fun formatLevel(v: Double): String =
        if (v >= 1000) String.format("%,.0f", v) else String.format("%.2f", v)
}
