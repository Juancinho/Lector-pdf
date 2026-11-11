// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.gitlab.mudlej.MjPdfReader.R

/**
 * Liquid-glass helpers. Android Views cannot blur what is drawn behind a view in
 * the same window, so in-window chrome (the pen toolbar, selection card) uses a
 * translucent frosted fill with a specular edge. Separate windows (menus,
 * sheets) get a real backdrop blur where the system allows cross-window blur
 * (Android 12+, and only when the device/battery settings enable it).
 */
object GlassSurfaces {

    private const val BLUR_RADIUS_DP = 36
    private const val BLUR_BEHIND_DP = 12

    fun applyToDialogWindow(window: Window?, cornerRadiusDp: Float = 28f) {
        window ?: return
        val context = window.context
        val density = context.resources.displayMetrics.density
        val blurAvailable = isCrossWindowBlurEnabled(context)
        val fill = ContextCompat.getColor(
            context,
            if (blurAvailable) R.color.study_glass_fill_blurred else R.color.study_glass_fill_strong,
        )
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusDp * density
            setColor(fill)
            setStroke(density.toInt().coerceAtLeast(1), ContextCompat.getColor(context, R.color.study_glass_edge))
        }
        window.setBackgroundDrawable(background)
        if (blurAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius((BLUR_RADIUS_DP * density).toInt())
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = (BLUR_BEHIND_DP * density).toInt()
                dimAmount = 0.12f
            }
        }
    }

    fun isCrossWindowBlurEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false
        }
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return false
        return windowManager.isCrossWindowBlurEnabled
    }
}
