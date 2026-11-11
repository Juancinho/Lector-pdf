// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ui

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.github.barteksc.pdfviewer.PenTool
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.StudyPenToolbarBinding
import com.gitlab.mudlej.MjPdfReader.study.annotation.HighlightStyle
import com.gitlab.mudlej.MjPdfReader.study.pen.PenSettings
import com.gitlab.mudlej.MjPdfReader.study.pen.StudyPenController
import java.util.Locale

/**
 * Floating liquid-glass toolbar for the pen. One row of tools; colour, width and
 * pen behaviour live in a panel anchored above the toolbar and only open on
 * demand (re-tap the active tool, the colour dot or the tune button).
 */
class StudyPenToolbar(
    private val binding: StudyPenToolbarBinding,
    private val controller: StudyPenController,
    private val highlightColors: () -> List<Int>,
    private val currentHighlight: () -> HighlightStyle,
    private val useHighlightColor: (Int) -> Unit,
    private val openHighlightEditor: () -> Unit,
    private val saveSettings: (PenSettings) -> Unit,
    private val onVisibilityChanged: (Boolean) -> Unit,
) {
    private val root: View = binding.root
    private val context = root.context
    private val density = context.resources.displayMetrics.density
    private val toolButtons: Map<PenTool, ImageButton> = mapOf(
        PenTool.NONE to binding.studyToolRead,
        PenTool.HIGHLIGHTER to binding.studyToolHighlighter,
        PenTool.PEN to binding.studyToolPen,
        PenTool.MARKER to binding.studyToolMarker,
        PenTool.ERASER to binding.studyToolEraser,
    )

    val isShowing: Boolean get() = root.isVisible

    init {
        toolButtons.forEach { (tool, button) ->
            TooltipCompat.setTooltipText(button, button.contentDescription)
            button.setOnClickListener { onToolClicked(tool) }
        }
        listOf(
            binding.studyOptionsButton, binding.studyUndoButton,
            binding.studyRedoButton, binding.studyDoneButton,
        ).forEach { TooltipCompat.setTooltipText(it, it.contentDescription) }
        TooltipCompat.setTooltipText(binding.studyColorButton, binding.studyColorButton.contentDescription)
        binding.studyColorButton.setOnClickListener { toggleOptions() }
        binding.studyOptionsButton.setOnClickListener { toggleOptions() }
        binding.studyUndoButton.setOnClickListener { controller.undo() }
        binding.studyRedoButton.setOnClickListener { controller.redo() }
        binding.studyDoneButton.setOnClickListener { hide() }
        binding.studyMoreColorsButton.setOnClickListener { openHighlightEditor() }
        ViewCompat.setAccessibilityHeading(binding.studyOptionsTitle, true)
        binding.studyWidthSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) onWidthChanged(value)
        }
        binding.studyStylusOnlySwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != controller.settings.stylusOnly) {
                applySettings(controller.settings.copy(stylusOnly = checked))
            }
        }
        binding.studyPenAlwaysSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != controller.settings.penAlwaysWrites) {
                applySettings(controller.settings.copy(penAlwaysWrites = checked))
            }
        }
        controller.setStateListener { refresh() }
        refresh()
    }

    fun show(tool: PenTool = controller.settings.lastWritingTool) {
        if (!isShowing) {
            root.alpha = 0f
            root.translationY = 24 * density
            root.visibility = View.VISIBLE
            root.animate().alpha(1f).translationY(0f).setDuration(ANIMATION_MS).start()
            onVisibilityChanged(true)
        }
        controller.selectTool(tool)
        refresh()
    }

    fun hide() {
        if (!isShowing) return
        controller.selectTool(PenTool.NONE)
        saveSettings(controller.settings)
        binding.studyOptionsPanel.visibility = View.GONE
        root.animate().alpha(0f).translationY(24 * density).setDuration(ANIMATION_MS).withEndAction {
            root.visibility = View.GONE
            root.translationY = 0f
            root.alpha = 1f
        }.start()
        onVisibilityChanged(false)
    }

    fun toggle() {
        if (isShowing) hide() else show()
    }

    /** Highlight styles can change from the selection card or the style editor. */
    fun onHighlightStyleChanged() {
        controller.refreshHighlightColor()
        refresh()
    }

    private fun onToolClicked(tool: PenTool) {
        if (tool == controller.tool && tool != PenTool.NONE) {
            toggleOptions()
            return
        }
        controller.selectTool(tool)
        saveSettings(controller.settings)
        if (binding.studyOptionsPanel.isVisible) {
            bindOptions()
        }
        refresh()
    }

    private fun toggleOptions() {
        val panel = binding.studyOptionsPanel
        if (panel.isVisible) {
            panel.visibility = View.GONE
            return
        }
        val maxWidth = context.resources.displayMetrics.widthPixels - (32 * density).toInt()
        panel.layoutParams = panel.layoutParams.apply { width = minOf((360 * density).toInt(), maxWidth) }
        bindOptions()
        panel.alpha = 0f
        panel.visibility = View.VISIBLE
        panel.animate().alpha(1f).setDuration(ANIMATION_MS).start()
    }

    private fun applySettings(settings: PenSettings) {
        controller.updateSettings(settings)
        saveSettings(controller.settings)
        refresh()
    }

    private fun onWidthChanged(value: Float) {
        val settings = controller.settings
        val next = when (controller.tool) {
            PenTool.MARKER -> settings.copy(markerWidth = value)
            else -> settings.copy(penWidth = value)
        }
        applySettings(next)
        updateWidthLabel(value)
    }

    private fun bindOptions() {
        val tool = controller.tool
        val settings = controller.settings
        binding.studyOptionsTitle.setText(
            when (tool) {
                PenTool.HIGHLIGHTER -> R.string.study_options_highlighter
                PenTool.PEN -> R.string.study_options_pen
                PenTool.MARKER -> R.string.study_options_marker
                PenTool.ERASER -> R.string.study_options_eraser
                PenTool.NONE -> R.string.study_options_read
            }
        )
        val colors = when (tool) {
            PenTool.PEN -> PenSettings.PEN_COLORS
            PenTool.MARKER -> PenSettings.MARKER_COLORS
            PenTool.HIGHLIGHTER -> highlightPalette()
            else -> emptyList()
        }
        binding.studyColorScroll.visibility = if (colors.isEmpty()) View.GONE else View.VISIBLE
        buildSwatches(colors, toolColor(tool))

        val slider = binding.studyWidthSlider
        val showWidth = tool == PenTool.PEN || tool == PenTool.MARKER
        slider.visibility = if (showWidth) View.VISIBLE else View.GONE
        binding.studyWidthLabel.visibility = slider.visibility
        if (showWidth) {
            val range = if (tool == PenTool.MARKER) PenSettings.MARKER_WIDTH_RANGE else PenSettings.PEN_WIDTH_RANGE
            val value = if (tool == PenTool.MARKER) settings.markerWidth else settings.penWidth
            slider.valueFrom = range.start
            slider.valueTo = range.endInclusive
            slider.stepSize = if (tool == PenTool.MARKER) 0.5f else 0.1f
            slider.value = snap(value, slider.valueFrom, slider.stepSize).coerceIn(range.start, range.endInclusive)
            updateWidthLabel(slider.value)
        }
        binding.studyMoreColorsButton.visibility = if (tool == PenTool.HIGHLIGHTER) View.VISIBLE else View.GONE
        binding.studyToolHint.visibility = if (tool == PenTool.ERASER || tool == PenTool.HIGHLIGHTER) View.VISIBLE else View.GONE
        binding.studyToolHint.setText(
            if (tool == PenTool.ERASER) R.string.study_eraser_hint else R.string.study_tool_highlighter
        )
        binding.studyStylusOnlySwitch.isChecked = settings.stylusOnly
        binding.studyPenAlwaysSwitch.isChecked = settings.penAlwaysWrites
    }

    private fun snap(value: Float, from: Float, step: Float): Float {
        val steps = Math.round((value - from) / step)
        return from + steps * step
    }

    private fun updateWidthLabel(value: Float) {
        binding.studyWidthLabel.text = context.getString(
            R.string.study_width,
            String.format(Locale.getDefault(), "%.1f", value),
        )
    }

    private fun highlightPalette(): List<Int> {
        val current = currentHighlight()
        return (listOf(current.argb) + highlightColors().map {
            HighlightStyle(it and 0xFFFFFF, current.opacityPercent).argb
        }).distinct().take(8)
    }

    private fun buildSwatches(colors: List<Int>, selected: Int) {
        val row = binding.studyColorRow
        row.removeAllViews()
        val toolName = binding.studyOptionsTitle.text
        colors.forEach { color ->
            val selectedSwatch = (color and 0xFFFFFF) == (selected and 0xFFFFFF)
            val target = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams((48 * density).toInt(), (48 * density).toInt())
                setBackgroundResource(R.drawable.bg_glass_tool)
                isSelected = selectedSwatch
                isFocusable = true
                val hex = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)
                contentDescription = context.getString(R.string.study_pen_color_description, toolName, hex) +
                    if (selectedSwatch) ", " + context.getString(R.string.study_selected) else ""
                setOnClickListener { onColorPicked(color) }
            }
            val dot = View(context).apply {
                layoutParams = FrameLayout.LayoutParams((30 * density).toInt(), (30 * density).toInt(), Gravity.CENTER)
                setBackgroundResource(R.drawable.bg_study_swatch)
                backgroundTintList = ColorStateList.valueOf(color or OPAQUE)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            target.addView(dot)
            row.addView(target)
        }
    }

    private fun onColorPicked(color: Int) {
        val settings = controller.settings
        when (controller.tool) {
            PenTool.PEN -> applySettings(settings.copy(penColor = color))
            PenTool.MARKER -> applySettings(settings.copy(markerColor = color))
            PenTool.HIGHLIGHTER -> {
                useHighlightColor(color)
                controller.refreshHighlightColor()
            }
            else -> Unit
        }
        bindOptions()
        refresh()
    }

    private fun toolColor(tool: PenTool): Int = when (tool) {
        PenTool.PEN -> controller.settings.penColor
        PenTool.MARKER -> controller.settings.markerColor
        PenTool.HIGHLIGHTER -> currentHighlight().argb
        else -> 0
    }

    private fun refresh() {
        val active = controller.tool
        toolButtons.forEach { (tool, button) ->
            val selected = tool == active
            button.isSelected = selected
            ViewCompat.setStateDescription(
                button,
                if (selected) context.getString(R.string.study_selected) else null,
            )
        }
        val color = toolColor(active)
        val showColor = active == PenTool.PEN || active == PenTool.MARKER || active == PenTool.HIGHLIGHTER
        binding.studyColorButton.visibility = if (showColor) View.VISIBLE else View.GONE
        if (showColor) {
            binding.studyColorDot.backgroundTintList = ColorStateList.valueOf(color or OPAQUE)
        }
        setEnabled(binding.studyUndoButton, controller.canUndo)
        setEnabled(binding.studyRedoButton, controller.canRedo)
        binding.studyDoneButton.imageTintList =
            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.study_accent))
    }

    private fun setEnabled(button: ImageButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else DISABLED_ALPHA
    }

    companion object {
        private const val ANIMATION_MS = 160L
        private const val DISABLED_ALPHA = 0.38f
        private const val OPAQUE = 0xFF000000.toInt()
    }
}
