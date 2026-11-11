// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ui

import android.content.Context
import android.view.View
import com.gitlab.mudlej.MjPdfReader.R
import kotlin.math.roundToInt

internal fun Context.glassDp(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()

internal fun View.useReaderGlass(elevationDp: Int = 6) {
    setBackgroundResource(R.drawable.reader_glass)
    elevation = context.glassDp(elevationDp).toFloat()
}
