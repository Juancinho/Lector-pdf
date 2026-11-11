// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.github.barteksc.pdfviewer.PenButtonAction
import com.github.barteksc.pdfviewer.PenTool
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.StudyPenOptionsBinding
import com.gitlab.mudlej.MjPdfReader.databinding.StudyPenToolbarBinding
import com.gitlab.mudlej.MjPdfReader.study.annotation.HighlightStyle
import com.gitlab.mudlej.MjPdfReader.study.pen.PenSettings
import com.gitlab.mudlej.MjPdfReader.study.pen.StudyPenController
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.Locale
import kotlin.math.abs

/**
 * Floating liquid-glass pen toolbar.
 *
 * - Tools are drawn as real instruments whose tip/band takes the active colour;
 *   the selected one rises out of the bar.
 * - Drag it by the grip (or drag the collapsed bubble); it snaps to edges and
 *   remembers its position as fractions of the free space, so rotation keeps it
 *   in a sensible place. Double-tap the grip to switch horizontal/vertical.
 * - Collapse turns it into a bubble showing the active tool; the tool stays active.
 * - Options open in a popover anchored next to the bar, wherever it is.
 */
class StudyPenToolbar(
    private val binding: StudyPenToolbarBinding,
    private val controller: StudyPenController,
    private val highlightColors: () -> List<Int>,
    private val currentHighlight: () -> HighlightStyle,
    private val useHighlightColor: (Int) -> Unit,
    private val openHighlightEditor: () -> Unit,
    private val saveSettings: (PenSettings) -> Unit,
    private val onChromeChanged: (Boolean) -> Unit,
) {
    private val root: FrameLayout = binding.root
    private val context = root.context
    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private class ToolCell(val cell: FrameLayout, val art: ImageView, val body: Int, val accent: Int)

    private val tools: Map<PenTool, ToolCell> = linkedMapOf(
        PenTool.HIGHLIGHTER to ToolCell(binding.studyToolHighlighter, binding.studyToolHighlighterArt,
            R.drawable.tool_highlighter_body, R.drawable.tool_highlighter_accent),
        PenTool.PEN to ToolCell(binding.studyToolPen, binding.studyToolPenArt,
            R.drawable.tool_pen_body, R.drawable.tool_pen_accent),
        PenTool.MARKER to ToolCell(binding.studyToolMarker, binding.studyToolMarkerArt,
            R.drawable.tool_marker_body, R.drawable.tool_marker_accent),
        PenTool.ERASER to ToolCell(binding.studyToolEraser, binding.studyToolEraserArt,
            R.drawable.tool_eraser_body, R.drawable.tool_eraser_accent),
    )
    private val iconButtons = listOf(
        binding.studyToolRead, binding.studyUndoButton, binding.studyRedoButton, binding.studyCollapseButton,
    )
    private val dividers = listOf(binding.studyDivider1, binding.studyDivider2, binding.studyDivider3, binding.studyDivider4)

    private var options: StudyPenOptionsBinding? = null
    private var backdrop: View? = null
    private var popover: PopupWindow? = null
    private var appliedVertical: Boolean? = null
    private var lastGripTapMs = 0L

    val isShowing: Boolean get() = root.isVisible

    init {
        tools.forEach { (tool, cell) ->
            TooltipCompat.setTooltipText(cell.cell, cell.cell.contentDescription)
            cell.cell.setOnClickListener { onToolClicked(tool) }
        }
        iconButtons.forEach { TooltipCompat.setTooltipText(it, it.contentDescription) }
        TooltipCompat.setTooltipText(binding.studyColorButton, binding.studyColorButton.contentDescription)
        TooltipCompat.setTooltipText(binding.studyDoneButton, binding.studyDoneButton.contentDescription)
        binding.studyToolRead.setOnClickListener { onToolClicked(PenTool.NONE) }
        binding.studyColorButton.setOnClickListener { toggleOptions() }
        binding.studyUndoButton.setOnClickListener { controller.undo() }
        binding.studyRedoButton.setOnClickListener { controller.redo() }
        binding.studyCollapseButton.setOnClickListener { setCollapsed(true) }
        binding.studyDoneButton.setOnClickListener { hide() }
        installDrag(binding.studyGrip, onTap = ::onGripTapped)
        installDrag(binding.studyToolbarBubble, onTap = { setCollapsed(false) })
        binding.studyToolbarBubble.setOnClickListener { setCollapsed(false) }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyPosition() }
        (root.parent as? View)?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyPosition() }
        controller.setStateListener { refresh() }
        // Clip children to the capsule/circle so instruments vanish into the bar's rim.
        binding.studyToolbarPill.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, minOf(view.width, view.height) / 2f)
                // A lighter shadow reads as glass rather than an opaque slab.
                outline.alpha = 0.55f
            }
        }
        binding.studyToolbarPill.clipToOutline = true
        binding.studyToolbarBubble.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        binding.studyToolbarBubble.clipToOutline = true
        applyOrientation(controller.settings.toolbarVertical)
        refresh()
    }

    /**
     * Gives the expanded bar a real blur of [source] behind it (Android 12+). Without
     * it the capsule keeps a denser frosted fill so text underneath never competes.
     */
    fun attachBackdrop(source: View, paused: () -> Boolean) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || backdrop != null) return
        val pill = binding.studyToolbarPill
        val view = GlassBackdropView(context).apply {
            this.source = source
            isPaused = paused
            elevation = pill.elevation
            outlineProvider = pill.outlineProvider
            clipToOutline = true
            onFailed = {
                pill.setBackgroundResource(R.drawable.bg_glass_capsule)
                visibility = View.GONE
            }
        }
        root.addView(view, 0, FrameLayout.LayoutParams(0, 0))
        pill.setBackgroundResource(R.drawable.bg_glass_capsule_clear)
        // Keep the backdrop exactly under the capsule as it resizes, rotates or collapses.
        pill.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val params = view.layoutParams as FrameLayout.LayoutParams
            val marginLeft = left - root.paddingLeft
            val marginTop = top - root.paddingTop
            if (params.width != right - left || params.height != bottom - top ||
                params.leftMargin != marginLeft || params.topMargin != marginTop
            ) {
                params.width = right - left
                params.height = bottom - top
                params.leftMargin = marginLeft
                params.topMargin = marginTop
                view.post { view.layoutParams = params }
            }
        }
        backdrop = view
        syncBackdropVisibility()
    }

    private fun syncBackdropVisibility() {
        backdrop?.visibility = if (binding.studyToolbarPill.isVisible) View.VISIBLE else View.GONE
    }

    // ---- Visibility -----------------------------------------------------------

    fun show(tool: PenTool = controller.settings.lastWritingTool) {
        val settings = controller.settings
        applyOrientation(settings.toolbarVertical)
        applyCollapsed(settings.toolbarCollapsed, animate = false)
        if (!isShowing) {
            root.alpha = 0f
            root.scaleX = 0.94f
            root.scaleY = 0.94f
            root.visibility = View.VISIBLE
            root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ANIMATION_MS)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        controller.selectTool(tool)
        refresh()
        root.post { applyPosition(); onChromeChanged(true) }
    }

    fun hide() {
        if (!isShowing) return
        dismissOptions()
        controller.selectTool(PenTool.NONE)
        saveSettings(controller.settings)
        root.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(ANIMATION_MS).withEndAction {
            root.visibility = View.GONE
            root.alpha = 1f
            root.scaleX = 1f
            root.scaleY = 1f
            onChromeChanged(false)
        }.start()
    }

    fun toggle() {
        if (isShowing) hide() else show()
    }

    /** Highlight styles can change from the selection card or the style editor. */
    fun onHighlightStyleChanged() {
        controller.refreshHighlightColor()
        refresh()
        options?.let { bindOptions(it) }
    }

    /** Screen-space bounds of the visible toolbar surface, or null when hidden. */
    fun visibleBounds(): Rect? {
        if (!isShowing) return null
        val surface = if (binding.studyToolbarBubble.isVisible) binding.studyToolbarBubble else binding.studyToolbarPill
        val left = (root.x + surface.left).toInt()
        val top = (root.y + surface.top).toInt()
        return Rect(left, top, left + surface.width, top + surface.height)
    }

    // ---- Tools ----------------------------------------------------------------

    private fun onToolClicked(tool: PenTool) {
        if (tool == controller.tool && tool != PenTool.NONE) {
            toggleOptions()
            return
        }
        controller.selectTool(tool)
        saveSettings(controller.settings)
        options?.takeIf { popover?.isShowing == true }?.let { bindOptions(it) }
        refresh()
    }

    private fun refresh() {
        val active = controller.tool
        tools.forEach { (tool, cell) ->
            cell.art.setImageDrawable(toolArt(tool, cell))
            val selected = tool == active
            cell.cell.isSelected = selected
            ViewCompat.setStateDescription(cell.cell, if (selected) context.getString(R.string.study_selected) else null)
            raise(cell.art, selected, animate = isShowing)
        }
        binding.studyToolRead.isSelected = active == PenTool.NONE
        ViewCompat.setStateDescription(
            binding.studyToolRead,
            if (active == PenTool.NONE) context.getString(R.string.study_selected) else null,
        )
        val color = toolColor(active)
        val showColor = active == PenTool.PEN || active == PenTool.MARKER || active == PenTool.HIGHLIGHTER
        binding.studyColorDot.background = colorWell(if (showColor) color else neutralWell())
        binding.studyColorButton.alpha = if (showColor) 1f else 0.55f
        setEnabled(binding.studyUndoButton, controller.canUndo)
        setEnabled(binding.studyRedoButton, controller.canRedo)
        val bubbleTool = if (active == PenTool.NONE || active == PenTool.SELECT) controller.settings.lastWritingTool else active
        tools[bubbleTool]?.let { binding.studyToolbarBubble.findViewById<ImageView>(R.id.studyBubbleArt).setImageDrawable(toolArt(bubbleTool, it)) }
        // A thin ring in the active colour makes the collapsed bubble readable at a glance.
        val ring = (2 * density).toInt()
        val inset = (5 * density).toInt()
        binding.studyToolbarBubble.foreground = android.graphics.drawable.InsetDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(ring, displayAccent(toolColor(bubbleTool)))
            },
            inset,
        )
    }

    private fun toolArt(tool: PenTool, cell: ToolCell): Drawable {
        val body = AppCompatResources.getDrawable(context, cell.body)!!.mutate()
        val accent = AppCompatResources.getDrawable(context, cell.accent)!!.mutate()
        DrawableCompat.setTint(accent, displayAccent(toolColor(tool)))
        return LayerDrawable(arrayOf(body, accent))
    }

    private fun toolColor(tool: PenTool): Int = when (tool) {
        PenTool.PEN -> controller.settings.penColor
        PenTool.MARKER -> controller.settings.markerColor
        PenTool.HIGHLIGHTER -> currentHighlight().argb
        PenTool.ERASER -> ContextCompat.getColor(context, R.color.study_eraser_rubber)
        else -> neutralWell()
    }

    /**
     * Very dark inks (graphite, navy) would vanish on the dark tool bodies; in dark
     * mode only the artwork is lifted towards white. Strokes keep the real colour.
     */
    private fun displayAccent(color: Int): Int {
        val opaqueColor = opaque(color)
        if (!isNight() || ColorUtils.calculateLuminance(opaqueColor) > 0.12) return opaqueColor
        return ColorUtils.blendARGB(opaqueColor, Color.WHITE, 0.45f)
    }

    private fun isNight(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun neutralWell(): Int = ContextCompat.getColor(context, R.color.study_glass_divider)

    private fun colorWell(color: Int): Drawable {
        val drawable = AppCompatResources.getDrawable(context, R.drawable.bg_study_color_dot)!!.mutate() as LayerDrawable
        (drawable.findDrawableByLayerId(R.id.swatch_fill) as? GradientDrawable)?.setColor(opaque(color))
        return drawable
    }

    /** In a vertical bar the tips point towards the page: right on the left side, left on the right side. */
    private fun tipsPointRight(): Boolean = appliedVertical == true && controller.settings.toolbarX < 0.5f

    /** Selected tools rise out of the bar, like instruments lifted from a tray. */
    private fun raise(art: View, selected: Boolean, animate: Boolean) {
        val vertical = appliedVertical == true
        val offset = (if (selected) RAISED_DP else RESTING_DP) * density
        art.rotation = when {
            !vertical -> 0f
            tipsPointRight() -> 90f
            else -> -90f
        }
        val targetX = when {
            !vertical -> 0f
            tipsPointRight() -> -offset
            else -> offset
        }
        val targetY = if (vertical) 0f else offset
        if (animate) {
            art.animate().translationX(targetX).translationY(targetY).setDuration(RAISE_MS)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
        } else {
            art.translationX = targetX
            art.translationY = targetY
        }
    }

    private fun setEnabled(button: View, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else DISABLED_ALPHA
    }

    // ---- Orientation and collapse ----------------------------------------------

    private fun onGripTapped() {
        val now = SystemClock.uptimeMillis()
        if (now - lastGripTapMs < DOUBLE_TAP_MS) {
            lastGripTapMs = 0L
            setVertical(!controller.settings.toolbarVertical)
        } else {
            lastGripTapMs = now
        }
    }

    private fun setVertical(vertical: Boolean) {
        if (vertical == controller.settings.toolbarVertical) return
        dismissOptions()
        persist(controller.settings.copy(toolbarVertical = vertical))
        applyOrientation(vertical)
        refresh()
        root.post { applyPosition(); onChromeChanged(true) }
    }

    private fun applyOrientation(vertical: Boolean) {
        if (appliedVertical == vertical) return
        appliedVertical = vertical
        binding.studyToolbarPill.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        fun size(view: View, along: Int, across: Int) {
            view.layoutParams = view.layoutParams.apply {
                width = ((if (vertical) across else along) * density).toInt()
                height = ((if (vertical) along else across) * density).toInt()
            }
        }
        size(binding.studyGrip, 26, 64)
        binding.studyGrip.rotation = if (vertical) 90f else 0f
        iconButtons.forEach { size(it, 44, 48) }
        size(binding.studyColorButton, 44, 48)
        tools.values.forEach { cell -> size(cell.cell, 46, 64) }
        val pad = (6 * density).toInt()
        if (vertical) {
            binding.studyToolbarPill.setPaddingRelative(0, pad, 0, pad)
        } else {
            binding.studyToolbarPill.setPaddingRelative(pad, 0, pad, 0)
        }
        dividers.forEach { divider ->
            val params = divider.layoutParams as LinearLayout.LayoutParams
            val length = (28 * density).toInt()
            val gap = (5 * density).toInt()
            params.width = if (vertical) length else 1.coerceAtLeast((1 * density).toInt())
            params.height = if (vertical) 1.coerceAtLeast((1 * density).toInt()) else length
            params.setMargins(if (vertical) 0 else gap, if (vertical) gap else 0, if (vertical) 0 else gap, if (vertical) gap else 0)
            divider.layoutParams = params
        }
        tools.values.forEach { raise(it.art, it.cell.isSelected, animate = false) }
    }

    private fun setCollapsed(collapsed: Boolean) {
        if (collapsed == controller.settings.toolbarCollapsed && collapsed == binding.studyToolbarBubble.isVisible) return
        dismissOptions()
        persist(controller.settings.copy(toolbarCollapsed = collapsed))
        applyCollapsed(collapsed, animate = true)
        root.post { applyPosition(); onChromeChanged(true) }
    }

    private fun applyCollapsed(collapsed: Boolean, animate: Boolean) {
        val show = if (collapsed) binding.studyToolbarBubble else binding.studyToolbarPill
        val hide = if (collapsed) binding.studyToolbarPill else binding.studyToolbarBubble
        if (show.isVisible && !hide.isVisible) return
        hide.animate().cancel()
        hide.visibility = View.GONE
        show.visibility = View.VISIBLE
        syncBackdropVisibility()
        if (animate) {
            show.alpha = 0f
            show.scaleX = 0.8f
            show.scaleY = 0.8f
            show.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ANIMATION_MS)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        refresh()
    }

    // ---- Position and dragging ---------------------------------------------------

    private fun applyPosition() {
        val parent = root.parent as? View ?: return
        if (root.width == 0 || parent.width == 0) return
        val settings = controller.settings
        root.translationX = settings.toolbarX * freeWidth(parent)
        root.translationY = settings.toolbarY * freeHeight(parent)
    }

    private fun freeWidth(parent: View) = (parent.width - root.width).coerceAtLeast(0).toFloat()
    private fun freeHeight(parent: View) = (parent.height - root.height).coerceAtLeast(0).toFloat()

    @SuppressLint("ClickableViewAccessibility")
    private fun installDrag(handle: View, onTap: () -> Unit) {
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        handle.setOnTouchListener { view, event ->
            val parent = root.parent as? View ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = root.translationX
                    startY = root.translationY
                    dragging = false
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    view.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        dismissOptions()
                        root.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                    }
                    if (dragging) {
                        root.translationX = (startX + dx).coerceIn(0f, freeWidth(parent))
                        root.translationY = (startY + dy).coerceIn(0f, freeHeight(parent))
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    if (dragging) {
                        settleAfterDrag(parent)
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        onTap()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** Snaps to nearby edges with a short ease and stores the new position. */
    private fun settleAfterDrag(parent: View) {
        val snap = SNAP_DP * density
        val maxX = freeWidth(parent)
        val maxY = freeHeight(parent)
        var x = root.translationX
        var y = root.translationY
        if (x < snap) x = 0f else if (maxX - x < snap) x = maxX
        if (y < snap) y = 0f else if (maxY - y < snap) y = maxY
        root.animate().translationX(x).translationY(y).scaleX(1f).scaleY(1f).setDuration(ANIMATION_MS)
            .setInterpolator(DecelerateInterpolator()).withEndAction { onChromeChanged(true) }.start()
        persist(
            controller.settings.copy(
                toolbarX = if (maxX > 0f) x / maxX else 0.5f,
                toolbarY = if (maxY > 0f) y / maxY else 1f,
            )
        )
        // Moving across the middle flips which way vertical instruments point.
        tools.values.forEach { raise(it.art, it.cell.isSelected, animate = true) }
    }

    private fun persist(settings: PenSettings) {
        controller.updateSettings(settings)
        saveSettings(controller.settings)
    }

    // ---- Options popover -------------------------------------------------------

    private fun toggleOptions() {
        if (popover?.isShowing == true) {
            dismissOptions()
        } else {
            showOptions()
        }
    }

    private fun dismissOptions() {
        popover?.dismiss()
    }

    private fun showOptions() {
        val binding = options ?: createOptions().also { options = it }
        bindOptions(binding)
        val window = root.rootView
        val margin = (12 * density).toInt()
        val width = minOf((360 * density).toInt(), window.width - 2 * margin)
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val maxHeight = window.height - 2 * margin - (48 * density).toInt()
        val height = minOf(binding.root.measuredHeight, maxHeight)
        val popup = popover ?: PopupWindow(binding.root, width, height, true).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isOutsideTouchable = true
            elevation = 18 * density
            animationStyle = android.R.style.Animation_Dialog
            popover = this
        }
        popup.width = width
        popup.height = height

        val anchor = if (this.binding.studyToolbarBubble.isVisible) this.binding.studyToolbarBubble else this.binding.studyToolbarPill
        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val gap = (10 * density).toInt()
        val (x, y) = if (appliedVertical == true) {
            val right = location[0] + anchor.width + gap
            val px = if (right + width <= window.width - margin) right else location[0] - gap - width
            px to (location[1] + anchor.height / 2 - height / 2)
        } else {
            val above = location[1] - gap - height
            val py = if (above >= margin) above else location[1] + anchor.height + gap
            (location[0] + anchor.width / 2 - width / 2) to py
        }
        val clampedX = x.coerceIn(margin, (window.width - width - margin).coerceAtLeast(margin))
        val clampedY = y.coerceIn(margin, (window.height - height - margin).coerceAtLeast(margin))
        if (popup.isShowing) {
            popup.update(clampedX, clampedY, width, height)
        } else {
            popup.showAtLocation(root, Gravity.NO_GRAVITY, clampedX, clampedY)
        }
    }

    private fun createOptions(): StudyPenOptionsBinding {
        val options = StudyPenOptionsBinding.inflate(LayoutInflater.from(context))
        ViewCompat.setAccessibilityHeading(options.studyOptionsTitle, true)
        options.studyMoreColorsButton.setOnClickListener {
            dismissOptions()
            openHighlightEditor()
        }
        options.studyWidthSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val settings = controller.settings
            persist(if (controller.tool == PenTool.MARKER) settings.copy(markerWidth = value) else settings.copy(penWidth = value))
            updateWidth(options, value)
            refresh()
        }
        buildButtonChips(options.studyPrimaryChips) { action -> persist(controller.settings.copy(primaryButton = action)) }
        buildButtonChips(options.studySecondaryChips) { action -> persist(controller.settings.copy(secondaryButton = action)) }
        listOf(options.studyOrientationHorizontal, options.studyOrientationVertical).forEach { button ->
            button.backgroundTintList = ContextCompat.getColorStateList(context, R.color.study_segment_background)
            button.setTextColor(ContextCompat.getColorStateList(context, R.color.study_segment_content))
            button.strokeColor = ContextCompat.getColorStateList(context, R.color.study_glass_divider)
        }
        options.studyOrientationGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) setVertical(checkedId == R.id.studyOrientationVertical)
        }
        options.studyStylusOnlySwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != controller.settings.stylusOnly) persist(controller.settings.copy(stylusOnly = checked))
        }
        options.studyPenAlwaysSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != controller.settings.penAlwaysWrites) persist(controller.settings.copy(penAlwaysWrites = checked))
        }
        return options
    }

    private fun buildButtonChips(group: ChipGroup, onPicked: (PenButtonAction) -> Unit) {
        val labels = linkedMapOf(
            PenButtonAction.ERASER to R.string.study_button_erase,
            PenButtonAction.SELECT_TEXT to R.string.study_button_select,
            PenButtonAction.HIGHLIGHTER to R.string.study_button_highlight,
            PenButtonAction.NONE to R.string.study_button_none,
        )
        labels.forEach { (action, label) ->
            val chip = Chip(group.context, null, com.google.android.material.R.attr.chipStyle).apply {
                setChipDrawable(
                    com.google.android.material.chip.ChipDrawable.createFromAttributes(
                        context, null, 0, R.style.StudyChip,
                    )
                )
                id = View.generateViewId()
                tag = action
                setText(label)
                isCheckable = true
                setEnsureMinTouchTargetSize(true)
                setTextColor(ContextCompat.getColorStateList(context, R.color.study_segment_content))
                chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.study_segment_background)
            }
            group.addView(chip)
        }
        group.setOnCheckedStateChangeListener { chipGroup, checkedIds ->
            val checked = checkedIds.firstOrNull()?.let { chipGroup.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            onPicked(checked.tag as PenButtonAction)
        }
    }

    private fun checkChip(group: ChipGroup, action: PenButtonAction) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as Chip
            if (chip.tag == action && !chip.isChecked) chip.isChecked = true
        }
    }

    private fun bindOptions(options: StudyPenOptionsBinding) {
        val tool = controller.tool
        val settings = controller.settings
        options.studyOptionsTitle.setText(
            when (tool) {
                PenTool.HIGHLIGHTER -> R.string.study_options_highlighter
                PenTool.PEN -> R.string.study_options_pen
                PenTool.MARKER -> R.string.study_options_marker
                PenTool.ERASER -> R.string.study_options_eraser
                else -> R.string.study_pen_toolbar
            }
        )
        options.studyToolHint.setText(
            when (tool) {
                PenTool.HIGHLIGHTER -> R.string.study_hint_highlighter
                PenTool.PEN -> R.string.study_hint_pen
                PenTool.MARKER -> R.string.study_hint_marker
                PenTool.ERASER -> R.string.study_hint_eraser
                else -> R.string.study_hint_read
            }
        )
        val colors = when (tool) {
            PenTool.PEN -> PenSettings.PEN_COLORS
            PenTool.MARKER -> PenSettings.MARKER_COLORS
            PenTool.HIGHLIGHTER -> highlightPalette()
            else -> emptyList()
        }
        options.studyColorGroup.isVisible = colors.isNotEmpty()
        buildSwatches(options, colors, toolColor(tool))

        val ink = tool == PenTool.PEN || tool == PenTool.MARKER
        options.studyWidthGroup.isVisible = ink
        if (ink) {
            val slider = options.studyWidthSlider
            val range = if (tool == PenTool.MARKER) PenSettings.MARKER_WIDTH_RANGE else PenSettings.PEN_WIDTH_RANGE
            val value = if (tool == PenTool.MARKER) settings.markerWidth else settings.penWidth
            slider.valueFrom = range.start
            slider.valueTo = range.endInclusive
            slider.stepSize = if (tool == PenTool.MARKER) 0.5f else 0.1f
            slider.value = snap(value, slider.valueFrom, slider.stepSize).coerceIn(range.start, range.endInclusive)
            updateWidth(options, slider.value)
        }
        options.studyStrokePreview.isVisible = ink || tool == PenTool.HIGHLIGHTER
        if (tool == PenTool.HIGHLIGHTER) {
            options.studyStrokePreview.show(StrokePreviewView.Mode.HIGHLIGHT, currentHighlight().argb, 0f)
        }
        options.studyMoreColorsButton.isVisible = tool == PenTool.HIGHLIGHTER
        checkChip(options.studyPrimaryChips, settings.primaryButton)
        checkChip(options.studySecondaryChips, settings.secondaryButton)
        options.studyOrientationGroup.check(
            if (settings.toolbarVertical) R.id.studyOrientationVertical else R.id.studyOrientationHorizontal
        )
        options.studyStylusOnlySwitch.isChecked = settings.stylusOnly
        options.studyPenAlwaysSwitch.isChecked = settings.penAlwaysWrites
    }

    private fun updateWidth(options: StudyPenOptionsBinding, value: Float) {
        options.studyWidthValue.text = String.format(Locale.getDefault(), "%.1f pt", value)
        options.studyStrokePreview.show(StrokePreviewView.Mode.INK, toolColor(controller.tool), value)
    }

    private fun snap(value: Float, from: Float, step: Float): Float = from + Math.round((value - from) / step) * step

    private fun highlightPalette(): List<Int> {
        val current = currentHighlight()
        return (listOf(current.argb) + highlightColors().map {
            HighlightStyle(it and 0xFFFFFF, current.opacityPercent).argb
        }).distinct().take(8)
    }

    private fun buildSwatches(options: StudyPenOptionsBinding, colors: List<Int>, selected: Int) {
        val row = options.studyColorRow
        row.removeAllViews()
        val toolName = options.studyOptionsTitle.text
        colors.forEach { color ->
            val isSelected = (color and 0xFFFFFF) == (selected and 0xFFFFFF)
            val target = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams((40 * density).toInt(), (48 * density).toInt())
                setBackgroundResource(R.drawable.bg_glass_tool_cell)
                isFocusable = true
                val hex = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)
                contentDescription = context.getString(R.string.study_pen_color_description, toolName, hex) +
                    if (isSelected) ", " + context.getString(R.string.study_selected) else ""
                setOnClickListener { onColorPicked(color) }
            }
            val ring = View(context).apply {
                val size = (34 * density).toInt()
                layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    if (isSelected) setStroke((2 * density).toInt(), ContextCompat.getColor(context, R.color.study_ink))
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val dot = View(context).apply {
                val size = (26 * density).toInt()
                layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
                background = colorWell(color)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            target.addView(ring)
            target.addView(dot)
            row.addView(target)
        }
    }

    private fun onColorPicked(color: Int) {
        val settings = controller.settings
        when (controller.tool) {
            PenTool.PEN -> persist(settings.copy(penColor = color))
            PenTool.MARKER -> persist(settings.copy(markerColor = color))
            PenTool.HIGHLIGHTER -> {
                useHighlightColor(color)
                controller.refreshHighlightColor()
            }
            else -> Unit
        }
        options?.let { bindOptions(it) }
        refresh()
    }

    companion object {
        private const val ANIMATION_MS = 180L
        private const val RAISE_MS = 200L
        private const val DOUBLE_TAP_MS = 320L
        private const val DISABLED_ALPHA = 0.32f
        private const val RESTING_DP = 14f
        private const val RAISED_DP = 2.5f
        private const val SNAP_DP = 28f

        private fun opaque(color: Int) = color or 0xFF000000.toInt()
    }
}
