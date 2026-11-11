// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withScale
import kotlin.math.ceil

/**
 * Real backdrop blur for an in-window glass surface (Android 12+).
 *
 * Views cannot sample what is drawn behind them, so before each frame this view
 * redraws only the part of [source] it covers into a small bitmap (1/[DOWNSCALE]
 * resolution, clipped to its own bounds) and shows it through a GPU blur
 * ([RenderEffect]). It invalidates only when the sampled pixels change, so an
 * idle page costs nothing, and it pauses while [isPaused] (e.g. during a pen
 * stroke) to keep writing latency untouched. Any failure disables it for good
 * and the surface falls back to its frosted fill.
 */
@RequiresApi(Build.VERSION_CODES.S)
class GlassBackdropView(context: Context) : View(context) {

    var source: View? = null
    var isPaused: () -> Boolean = { false }
    var onFailed: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val selfLocation = IntArray(2)
    private val sourceLocation = IntArray(2)
    private var snapshot: Bitmap? = null
    private var snapshotCanvas: Canvas? = null
    private var pixels = IntArray(0)
    private var previous = IntArray(0)
    private var capturing = false
    private var failed = false

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        capture()
        true
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val radius = BLUR_RADIUS_DP * density
        setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
    }

    val isActive: Boolean get() = !failed

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(preDraw)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDraw)
        snapshot?.recycle()
        snapshot = null
        snapshotCanvas = null
        super.onDetachedFromWindow()
    }

    private fun capture() {
        val src = source ?: return
        if (failed || capturing || !isShown || width == 0 || height == 0 || isPaused()) return
        val w = ceil(width / DOWNSCALE).toInt().coerceAtLeast(1)
        val h = ceil(height / DOWNSCALE).toInt().coerceAtLeast(1)
        var bitmap = snapshot
        if (bitmap == null || bitmap.width != w || bitmap.height != h) {
            bitmap?.recycle()
            bitmap = createBitmap(w, h)
            snapshot = bitmap
            snapshotCanvas = Canvas(bitmap)
            pixels = IntArray(w * h)
            previous = IntArray(w * h)
        }
        val canvas = snapshotCanvas ?: return
        getLocationInWindow(selfLocation)
        src.getLocationInWindow(sourceLocation)
        capturing = true
        try {
            bitmap.eraseColor(Color.TRANSPARENT)
            canvas.withScale(1f / DOWNSCALE, 1f / DOWNSCALE) {
            canvas.translate(
                (sourceLocation[0] - selfLocation[0]).toFloat(),
                (sourceLocation[1] - selfLocation[1]).toFloat(),
            )
            canvas.clipRect(
                (selfLocation[0] - sourceLocation[0]).toFloat(),
                (selfLocation[1] - sourceLocation[1]).toFloat(),
                (selfLocation[0] - sourceLocation[0] + width).toFloat(),
                (selfLocation[1] - sourceLocation[1] + height).toFloat(),
            )
            src.draw(canvas)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "backdrop capture failed; falling back to frosted fill", t)
            failed = true
            onFailed?.invoke()
            return
        } finally {
            capturing = false
        }
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        if (!pixels.contentEquals(previous)) {
            System.arraycopy(pixels, 0, previous, 0, pixels.size)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = snapshot ?: return
        if (failed) return
        canvas.withScale(DOWNSCALE, DOWNSCALE) {
            drawBitmap(bitmap, 0f, 0f, paint)
        }
    }

    companion object {
        private const val TAG = "GlassBackdrop"
        private const val DOWNSCALE = 6f
        private const val BLUR_RADIUS_DP = 18f
    }
}
