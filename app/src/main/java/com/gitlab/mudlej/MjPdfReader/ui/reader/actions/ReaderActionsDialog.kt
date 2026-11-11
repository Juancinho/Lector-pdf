// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.ui.reader.actions

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.TextViewCompat
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.study.ui.glassDp
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import java.util.function.Consumer

data class ReaderAction(
    @StringRes val titleRes: Int,
    val iconRes: Int,
    val visible: Boolean = true,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

data class ReaderMenuSection(@StringRes val titleRes: Int, val actions: List<ReaderAction>)

data class ReaderMenuContent(val sections: List<ReaderMenuSection>)

fun showReaderActionsDialog(activity: MainActivity, content: ReaderMenuContent): AppCompatDialog {
    val dialog = AppCompatDialog(activity, R.style.StudyReaderMenuWindow)
    dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE)

    val panel = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.glassDp(16), activity.glassDp(12), activity.glassDp(16), activity.glassDp(12))
    }
    panel.addView(TextView(activity).apply {
        setText(R.string.study_reader_menu)
        setTextColor(activity.getColor(R.color.reader_glass_ink))
        textSize = 24f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(activity.glassDp(8), activity.glassDp(4), activity.glassDp(8), activity.glassDp(12))
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    })
    val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    content.sections.forEach { section ->
        val actions = section.actions.filter { it.visible }
        if (actions.isEmpty()) return@forEach
        column.addView(TextView(activity).apply {
            setText(section.titleRes)
            setTextColor(activity.getColor(R.color.reader_glass_muted))
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(activity.glassDp(8), activity.glassDp(12), activity.glassDp(8), activity.glassDp(4))
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
        val columns = if (activity.resources.configuration.screenWidthDp >= 400 &&
            activity.resources.configuration.fontScale <= 1.3f) 2 else 1
        actions.chunked(columns).forEach { rowActions ->
            val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            rowActions.forEach { action ->
                val params = LinearLayout.LayoutParams(0, -2, 1f)
                row.addView(Button(activity).apply {
                    isAllCaps = false
                    text = activity.getString(action.titleRes)
                    textSize = 14f
                    setTextColor(activity.getColor(R.color.reader_glass_ink))
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    minimumHeight = activity.glassDp(48)
                    minHeight = minimumHeight
                    setPadding(activity.glassDp(8), activity.glassDp(6), activity.glassDp(8), activity.glassDp(6))
                    compoundDrawablePadding = activity.glassDp(10)
                    val icon = AppCompatResources.getDrawable(activity, action.iconRes)?.mutate()
                    icon?.setBounds(0, 0, activity.glassDp(21), activity.glassDp(21))
                    setCompoundDrawablesRelative(icon, null, null, null)
                    TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(currentTextColor))
                    setBackgroundResource(R.drawable.reader_glass_button)
                    isEnabled = action.enabled
                    alpha = if (action.enabled) 1f else .45f
                    setOnClickListener { dialog.dismiss(); action.onClick() }
                }, params)
            }
            if (rowActions.size < columns) row.addView(Space(activity), LinearLayout.LayoutParams(0, 0, 1f))
            column.addView(row)
        }
    }
    panel.addView(ScrollView(activity).apply {
        isFillViewport = false
        addView(column)
    }, LinearLayout.LayoutParams(-1, 0, 1f))
    dialog.setContentView(panel)
    dialog.setCanceledOnTouchOutside(true)
    dialog.show()
    val window = requireNotNull(dialog.window)
    val configuration = activity.resources.configuration
    val width = minOf(540, configuration.screenWidthDp - 24).coerceAtLeast(1)
    val height = minOf(660, configuration.screenHeightDp - 84).coerceAtLeast(1)
    window.setLayout(activity.glassDp(width), activity.glassDp(height))
    window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
    window.attributes = window.attributes.apply { y = activity.glassDp(60) }
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    window.setDimAmount(.22f)
    val fallback = GradientDrawable().apply {
        setColor(activity.getColor(R.color.reader_glass_solid))
        cornerRadius = activity.glassDp(28).toFloat()
        setStroke(activity.glassDp(1), activity.getColor(R.color.reader_glass_rim))
    }
    window.setBackgroundDrawable(fallback)
    if (Build.VERSION.SDK_INT >= 31) {
        val manager = activity.windowManager
        val glass = AppCompatResources.getDrawable(activity, R.drawable.reader_glass)
        val listener = Consumer<Boolean> { enabled ->
            window.setBackgroundDrawable(if (enabled) glass else fallback)
            window.setBackgroundBlurRadius(if (enabled) activity.glassDp(28) else 0)
            window.setDimAmount(if (enabled) .16f else .26f)
        }
        manager.addCrossWindowBlurEnabledListener(activity.mainExecutor, listener)
        dialog.setOnDismissListener { manager.removeCrossWindowBlurEnabledListener(listener) }
    }
    return dialog
}
