package com.example.stockwidget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Minimal line chart with a tap/drag crosshair, drawn with Canvas. */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: List<Double> = emptyList()
    private var times: List<Long> = emptyList()
    private var selected = -1

    private var chartLeft = 0f
    private var chartRight = 0f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x33FFFFFF
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textSize = 28f
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xCCFFFFFF.toInt()
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xEE222B36.toInt()
    }
    private val bubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 34f
        isFakeBoldText = true
    }

    fun setData(v: List<Double>, t: List<Long>) {
        values = v
        times = t
        selected = -1
        invalidate()
    }

    private fun dateLabel(epochSec: Long): String {
        if (epochSec <= 0L) return ""
        val spanDays = if (times.size >= 2)
            (times.last() - times.first()) / 86400.0 else 0.0
        val pattern = when {
            spanDays <= 10 -> "M/d HH:mm"
            spanDays <= 400 -> "M/d"
            else -> "yyyy/M"
        }
        return SimpleDateFormat(pattern, Locale.getDefault())
            .format(Date(epochSec * 1000))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (values.size < 2) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val span = (chartRight - chartLeft).coerceAtLeast(1f)
                val frac = ((event.x - chartLeft) / span).coerceIn(0f, 1f)
                selected = (frac * (values.size - 1)).roundToInt()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = values
        if (data.size < 2) return

        val padL = 16f
        val padR = 96f
        val padTop = 24f
        val padBottom = 52f
        val w = width - padL - padR
        val h = height - padTop - padBottom
        if (w <= 0 || h <= 0) return
        chartLeft = padL
        chartRight = padL + w

        val min = data.minOrNull() ?: return
        val max = data.maxOrNull() ?: return
        val sp = (max - min).let { if (it == 0.0) 1.0 else it }

        fun x(i: Int) = padL + w * i / (data.size - 1)
        fun y(value: Double) = padTop + (h - (h * ((value - min) / sp))).toFloat()

        listOf(max, (max + min) / 2, min).forEach { level ->
            val yy = y(level)
            canvas.drawLine(padL, yy, padL + w, yy, gridPaint)
            canvas.drawText(fmt(level), padL + w + 8f, yy + 10f, textPaint)
        }

        val up = data.last() >= data.first()
        val color = if (up) StockQuote.COLOR_UP else StockQuote.COLOR_DOWN
        linePaint.color = color
        dotPaint.color = color

        val bottomY = padTop + h
        val line = Path()
        val area = Path()
        for (i in data.indices) {
            val px = x(i)
            val py = y(data[i])
            if (i == 0) {
                line.moveTo(px, py); area.moveTo(px, bottomY); area.lineTo(px, py)
            } else {
                line.lineTo(px, py); area.lineTo(px, py)
            }
        }
        area.lineTo(x(data.size - 1), bottomY)
        area.close()
        fillPaint.color = (color and 0x00FFFFFF) or 0x22000000
        canvas.drawPath(area, fillPaint)
        canvas.drawPath(line, linePaint)

        // Start / end date labels along the bottom.
        if (times.size == data.size && times.isNotEmpty()) {
            val startL = dateLabel(times.first())
            val endL = dateLabel(times.last())
            canvas.drawText(startL, padL, bottomY + 38f, textPaint)
            val ew = textPaint.measureText(endL)
            canvas.drawText(endL, padL + w - ew, bottomY + 38f, textPaint)
        }

        if (selected in data.indices) {
            val sx = x(selected)
            val sy = y(data[selected])
            canvas.drawLine(sx, padTop, sx, bottomY, crosshairPaint)
            canvas.drawCircle(sx, sy, 9f, dotPaint)

            val priceLabel = fmt(data[selected])
            val pw = bubbleText.measureText(priceLabel)
            val pbw = pw + 28f
            var pbx = (sx - pbw / 2).coerceIn(padL, padL + w - pbw)
            canvas.drawRoundRect(
                RectF(pbx, padTop, pbx + pbw, padTop + 56f), 12f, 12f, bubblePaint
            )
            canvas.drawText(priceLabel, pbx + 14f, padTop + 38f, bubbleText)

            val dateL = if (selected < times.size) dateLabel(times[selected]) else ""
            if (dateL.isNotEmpty()) {
                val dw = bubbleText.measureText(dateL)
                val dbw = dw + 28f
                val dbx = (sx - dbw / 2).coerceIn(padL, padL + w - dbw)
                canvas.drawRoundRect(
                    RectF(dbx, bottomY + 4f, dbx + dbw, bottomY + 52f),
                    12f, 12f, bubblePaint
                )
                canvas.drawText(dateL, dbx + 14f, bottomY + 38f, bubbleText)
            }
        }
    }

    private fun fmt(v: Double): String =
        if (v >= 1000) String.format("%,.2f", v) else String.format("%.4f", v)
}
