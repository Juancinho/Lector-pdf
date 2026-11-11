// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.github.barteksc.pdfviewer.InkGeometry
import com.gitlab.mudlej.MjPdfReader.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Live preview of the active tool on a small paper strip: a pressure-tapered
 * stroke for pen and marker (built with the same outline as saved ink), or a
 * highlighted sample phrase for the highlighter.
 */
class StrokePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Mode { INK, HIGHLIGHT }

    private val density = resources.displayMetrics.density
    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.study_paper)
    }
    private val paperEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.75f * density
        color = ContextCompat.getColor(context, R.color.study_glass_divider)
    }
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF202321.toInt()
        textSize = 15f * resources.displayMetrics.scaledDensity
    }
    private val path = Path()
    private val paper = RectF()
    private val band = RectF()

    private var mode = Mode.INK
    private var color = 0xFF202321.toInt()
    private var widthPt = 1.6f

    fun show(mode: Mode, color: Int, widthPt: Float) {
        this.mode = mode
        this.color = color
        this.widthPt = widthPt
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = 12f * density
        paper.set(paperEdge.strokeWidth, paperEdge.strokeWidth, width - paperEdge.strokeWidth, height - paperEdge.strokeWidth)
        canvas.drawRoundRect(paper, radius, radius, paperPaint)
        canvas.drawRoundRect(paper, radius, radius, paperEdge)
        if (mode == Mode.HIGHLIGHT) drawHighlight(canvas) else drawInk(canvas)
    }

    private fun drawInk(canvas: Canvas) {
        val left = 20f * density
        val right = width - 20f * density
        val mid = height / 2f
        val amplitude = height * 0.18f
        val samples = 48
        val xy = FloatArray(samples * 2)
        val pressure = FloatArray(samples)
        for (i in 0 until samples) {
            val t = i / (samples - 1f)
            xy[2 * i] = left + (right - left) * t
            xy[2 * i + 1] = mid + amplitude * sin(t * 2 * PI).toFloat()
            // Light start, full pressure in the middle, lifted end.
            pressure[i] = (0.15f + 0.85f * sin(t * PI).toFloat()).coerceIn(0f, 1f)
        }
        // Preview scale: one PDF point ≈ 1.4dp, clamped to the strip height.
        val maxWidthPx = (widthPt * 2.2f * density).coerceAtMost(height * 0.55f)
        val polygon = InkGeometry.outline(xy, pressure, maxWidthPx)
        path.rewind()
        if (polygon.size >= 6) {
            path.moveTo(polygon[0], polygon[1])
            var i = 2
            while (i + 1 < polygon.size) {
                path.lineTo(polygon[i], polygon[i + 1])
                i += 2
            }
            path.close()
        }
        inkPaint.color = color
        canvas.drawPath(path, inkPaint)
    }

    private fun drawHighlight(canvas: Canvas) {
        val text = context.getString(R.string.study_highlight_sample)
        val textWidth = textPaint.measureText(text)
        val startX = (width - textWidth) / 2f
        val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        band.set(
            startX - 4f * density,
            baseline + textPaint.ascent() - 1f * density,
            startX + textWidth + 4f * density,
            baseline + textPaint.descent() + 1f * density,
        )
        inkPaint.color = color
        canvas.drawRoundRect(band, 3f * density, 3f * density, inkPaint)
        canvas.drawText(text, startX, baseline, textPaint)
    }
}
