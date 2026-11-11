// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ui

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.study.annotation.HighlightStyle
import com.gitlab.mudlej.MjPdfReader.study.annotation.HighlightStyleStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** A native, scrollable editor. Fragment state preserves an unfinished draft on rotation. */
class HighlightStyleDialog : DialogFragment() {
    private lateinit var hex: TextInputEditText
    private lateinit var channels: List<TextInputEditText>
    private lateinit var meaning: TextInputEditText
    private lateinit var opacity: Slider
    private lateinit var favorite: MaterialCheckBox

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext(), R.style.StudyColorDialog)
        val context = builder.context
        val store = HighlightStyleStore(PreferenceManager.getDefaultSharedPreferences(context))
        val draft = savedInstanceState ?: arguments
        var style = draft?.getInt("argb")?.let { HighlightStyle.fromArgb(it, draft.getString("meaning").orEmpty()) }
            ?: store.current
        var syncing = false
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        fun label(text: String) = TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(context.getColor(R.color.study_muted))
            setPadding(0, dp(12), 0, dp(8))
        }.also(body::addView)
        label(getString(R.string.study_highlight_subtitle))
        val preview = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                // A paper sample, deliberately white even in dark UI; same PDF multiply blend.
                val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
                canvas.drawColor(Color.WHITE)
                paint.color = Color.rgb(32, 35, 33)
                paint.textSize = 16 * resources.displayMetrics.scaledDensity
                val baseline = height / 2f + paint.textSize / 3f
                val text = getString(R.string.study_preview)
                canvas.drawText(text, dp(12).toFloat(), baseline, paint)
                paint.color = style.argb
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                canvas.drawRect(dp(10).toFloat(), baseline - paint.textSize,
                    (dp(14) + paint.measureText(text)).coerceAtMost(width - dp(8).toFloat()), baseline + dp(4), paint)
                paint.xfermode = null
                canvas.restoreToCount(layer)
            }
        }.apply {
            minimumHeight = dp(72)
            contentDescription = getString(R.string.study_preview)
        }
        body.addView(preview, LinearLayout.LayoutParams(-1, dp(72)))
        fun field(parent: LinearLayout, title: String, numeric: Boolean = false): TextInputEditText {
            val layout = TextInputLayout(context).apply {
                hint = title
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            }
            val edit = TextInputEditText(context).apply {
                isSingleLine = true
                inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
                minimumHeight = dp(56)
            }
            layout.addView(edit)
            parent.addView(layout, LinearLayout.LayoutParams(if (numeric) 0 else -1, -2,
                if (numeric) 1f else 0f).apply { topMargin = dp(12); if (numeric) marginEnd = dp(4) })
            return edit
        }
        hex = field(body, getString(R.string.study_hex)).apply { id = R.id.study_hex_input; filters = arrayOf(InputFilter.LengthFilter(7)) }
        val rgbRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(rgbRow)
        channels = listOf(R.string.study_red, R.string.study_green, R.string.study_blue).map {
            field(rgbRow, getString(it), true).apply { filters = arrayOf(InputFilter.LengthFilter(3)) }
        }
        channels.zip(listOf(R.id.study_red_input, R.id.study_green_input, R.id.study_blue_input)).forEach { (edit, inputId) -> edit.id = inputId }
        val opacityLabel = label(getString(R.string.study_opacity, style.opacityPercent))
        opacity = Slider(context).apply {
            id = R.id.study_opacity_slider
            valueFrom = 1f; valueTo = 100f; stepSize = 1f; value = style.opacityPercent.toFloat()
            contentDescription = getString(R.string.study_opacity, style.opacityPercent)
        }
        body.addView(opacity)
        meaning = field(body, getString(R.string.study_meaning)).apply {
            id = R.id.study_meaning_input
            filters = arrayOf(InputFilter.LengthFilter(40))
            hint = getString(R.string.study_meaning_hint)
            setText(style.meaning)
        }
        favorite = MaterialCheckBox(context).apply {
            text = getString(R.string.study_favorite)
            isChecked = draft?.getBoolean("favorite") ?: store.favorites.any { it.argb == style.argb }
            minHeight = dp(48)
        }
        body.addView(favorite)
        fun valid(): Boolean = HighlightStyle.parseHex(hex.text.toString()) == style.rgb &&
            HighlightStyle.parseRgb(channels[0].text.toString(), channels[1].text.toString(), channels[2].text.toString()) == style.rgb
        fun refresh() {
            preview.invalidate()
            (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = valid()
        }
        fun select(next: HighlightStyle) {
            syncing = true
            style = next
            hex.setText(style.hex)
            channels.forEachIndexed { index, edit -> edit.setText(((style.rgb shr (16 - index * 8)) and 255).toString()) }
            opacity.value = style.opacityPercent.toFloat()
            meaning.setText(style.meaning)
            favorite.isChecked = store.favorites.any { it.argb == style.argb }
            hex.error = null
            channels.forEach { it.error = null }
            syncing = false
            refresh()
        }
        select(style)
        hex.doAfterTextChanged {
            if (!syncing) {
                val rgb = HighlightStyle.parseHex(it.toString())
                hex.error = if (rgb == null) getString(R.string.study_invalid_hex) else null
                if (rgb != null) {
                    syncing = true
                    style = style.copy(rgb = rgb)
                    channels.forEachIndexed { index, edit -> edit.setText(((rgb shr (16 - index * 8)) and 255).toString()); edit.error = null }
                    syncing = false
                }
                refresh()
            }
        }
        channels.forEach { edit -> edit.doAfterTextChanged {
            if (!syncing) {
                val rgb = HighlightStyle.parseRgb(channels[0].text.toString(), channels[1].text.toString(), channels[2].text.toString())
                edit.error = if (rgb == null) getString(R.string.study_invalid_rgb) else null
                if (rgb != null) {
                    syncing = true
                    style = style.copy(rgb = rgb)
                    hex.setText(style.hex); hex.error = null
                    channels.forEach { it.error = null }
                    syncing = false
                }
                refresh()
            }
        } }
        opacity.addOnChangeListener { _, value, _ ->
            style = style.copy(opacityPercent = value.toInt())
            opacityLabel.text = getString(R.string.study_opacity, value.toInt())
            opacity.contentDescription = opacityLabel.text
            refresh()
        }
        fun swatches(title: Int, styles: List<HighlightStyle>) {
            if (styles.isEmpty()) return
            label(getString(title))
            // Wrapping rows preserve 48dp targets on narrow screens and large font settings.
            styles.chunked(4).forEach { rowStyles ->
                val row = LinearLayout(context).apply { gravity = Gravity.START }
                body.addView(row)
                rowStyles.forEach { item ->
                    row.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = if (item.meaning.isBlank()) item.hex else item.meaning
                        textSize = 11f
                        isAllCaps = false
                        contentDescription = getString(R.string.study_swatch_description, item.hex, item.opacityPercent) + " " + item.meaning
                        setTextColor(context.getColor(R.color.study_ink))
                        strokeColor = android.content.res.ColorStateList.valueOf(item.rgb or 0xFF000000.toInt())
                        strokeWidth = dp(3)
                        minWidth = dp(48); minHeight = dp(48)
                        setPadding(dp(6), 0, dp(6), 0)
                        setOnClickListener { select(item) }
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
                }
            }
        }
        swatches(R.string.study_favorites, store.favorites)
        swatches(R.string.study_recent, store.recent)
        val scroll = ScrollView(context).apply { addView(body); isFillViewport = false }
        val result = builder.setTitle(R.string.study_highlight_title).setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.study_use_highlight, null).create()
        result.setOnShowListener {
            refresh()
            result.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (valid()) {
                    val chosen = style.copy(meaning = meaning.text.toString().trim())
                    store.use(chosen)
                    store.setFavorite(chosen, favorite.isChecked)
                    parentFragmentManager.setFragmentResult(RESULT, bundleOf("argb" to chosen.argb, "meaning" to chosen.meaning))
                    dismiss()
                }
            }
        }
        // Restore raw partially entered fields, including invalid input.
        savedInstanceState?.let { state ->
            syncing = true
            hex.setText(state.getString("hex"))
            channels.forEachIndexed { i, edit -> edit.setText(state.getString("channel$i")) }
            favorite.isChecked = state.getBoolean("favorite")
            syncing = false
            if (HighlightStyle.parseHex(hex.text.toString()) == null) hex.error = getString(R.string.study_invalid_hex)
            if (HighlightStyle.parseRgb(channels[0].text.toString(), channels[1].text.toString(), channels[2].text.toString()) == null) {
                channels.first().error = getString(R.string.study_invalid_rgb)
            }
        }
        return result
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("hex", hex.text.toString())
        channels.forEachIndexed { index, edit -> outState.putString("channel$index", edit.text.toString()) }
        val rgb = HighlightStyle.parseHex(hex.text.toString()) ?: HighlightStyle.DEFAULT.rgb
        outState.putInt("argb", HighlightStyle(rgb, opacity.value.toInt()).argb)
        outState.putString("meaning", meaning.text.toString())
        outState.putBoolean("favorite", favorite.isChecked)
    }

    companion object {
        const val RESULT = "study.highlight.style"
        fun newInstance(argb: Int? = null) = HighlightStyleDialog().apply {
            if (argb != null) arguments = bundleOf("argb" to argb)
        }
    }
}
