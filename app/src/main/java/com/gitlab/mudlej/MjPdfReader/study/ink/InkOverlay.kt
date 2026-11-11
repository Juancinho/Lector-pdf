// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ink

import android.content.Context
import android.graphics.*
import android.view.View
import com.github.barteksc.pdfviewer.PDFView
import kotlin.math.*

/** Draw-only surface: all touch routing remains on PDFView, so fingers still pan/pinch. */
class InkOverlay(context: Context, private val pdf: PDFView) : View(context) {
    var page = -1
    var points: List<InkPoint> = emptyList()
    var ghosts: List<InkStroke> = emptyList()
    var selected: List<InkStroke> = emptyList()
    var color = 0xFF263E4D.toInt()
    var width = 2f
    var pressure = true
    var lasso = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val matrix = Matrix()
    private val values = FloatArray(9)

    fun clearPreview() { points = emptyList(); ghosts = emptyList(); invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (page < 0 || (points.isEmpty() && ghosts.isEmpty() && selected.isEmpty())) return
        val origin = pdf.studyPdfToView(page, 0f, 0f) ?: return
        val x = pdf.studyPdfToView(page, 100f, 0f) ?: return
        val y = pdf.studyPdfToView(page, 0f, 100f) ?: return
        values[0] = (x.x-origin.x)/100; values[1] = (y.x-origin.x)/100; values[2] = origin.x
        values[3] = (x.y-origin.y)/100; values[4] = (y.y-origin.y)/100; values[5] = origin.y
        values[6] = 0f; values[7] = 0f; values[8] = 1f
        matrix.setValues(values)
        val scale = hypot(values[0], values[3]).coerceAtLeast(.001f)
        canvas.save(); canvas.concat(matrix)
        if (points.isNotEmpty()) drawStroke(canvas, points, color, width, pressure, lasso, scale)
        ghosts.forEach { drawStroke(canvas, it.points, it.color, it.width, it.pressureEnabled, false, scale) }
        if (selected.isNotEmpty()) {
            val p = selected.flatMap { it.points }
            val left=p.minOf { it.x }; val right=p.maxOf { it.x }; val bottom=p.minOf { it.y }; val top=p.maxOf { it.y }
            paint.style=Paint.Style.STROKE; paint.color=0xFF5271C4.toInt(); paint.strokeWidth=1.5f/scale
            canvas.drawRect(left, bottom, right, top, paint)
            paint.style=Paint.Style.FILL; canvas.drawCircle(right, top, 7f/scale, paint)
        }
        canvas.restore()
    }
    private fun drawStroke(canvas: Canvas, pts: List<InkPoint>, color: Int, width: Float, pressure: Boolean, lasso: Boolean, scale: Float) {
        paint.color=color; paint.style=Paint.Style.STROKE; paint.strokeWidth=if(lasso)1.5f/scale else width
        if (lasso || !pressure) {
            path.reset(); path.moveTo(pts[0].x,pts[0].y)
            for (i in 1 until pts.size) path.lineTo(pts[i].x,pts[i].y)
            if (pts.size==1) canvas.drawPoint(pts[0].x,pts[0].y,paint) else canvas.drawPath(path,paint)
        } else {
            if(pts.size==1) canvas.drawPoint(pts[0].x,pts[0].y,paint)
            for(i in 1 until pts.size) {
                val a=pts[i-1]; val b=pts[i]
                paint.strokeWidth=width*(.25f+.75f*(a.pressure+b.pressure)/2)
                canvas.drawLine(a.x,a.y,b.x,b.y,paint)
            }
        }
    }
}
