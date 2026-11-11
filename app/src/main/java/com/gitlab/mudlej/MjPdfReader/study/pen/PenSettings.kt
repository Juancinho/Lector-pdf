// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.pen

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.barteksc.pdfviewer.PenTool
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Local pen tool preferences. Like highlight styles, these are personal presets:
 * every stroke or highlight written with them is stored inside the PDF itself.
 * Widths are maximum widths in PDF points, reached at full pen pressure.
 */
data class PenSettings(
    val penColor: Int = PEN_COLORS.first(),
    val penWidth: Float = DEFAULT_PEN_WIDTH,
    val markerColor: Int = MARKER_COLORS.first(),
    val markerWidth: Float = DEFAULT_MARKER_WIDTH,
    val stylusOnly: Boolean = true,
    val penAlwaysWrites: Boolean = false,
    val lastWritingTool: PenTool = PenTool.PEN,
) {
    fun normalized(): PenSettings = copy(
        penColor = penColor or OPAQUE,
        penWidth = penWidth.coerceIn(PEN_WIDTH_RANGE.start, PEN_WIDTH_RANGE.endInclusive),
        markerColor = withMarkerAlpha(markerColor),
        markerWidth = markerWidth.coerceIn(MARKER_WIDTH_RANGE.start, MARKER_WIDTH_RANGE.endInclusive),
        lastWritingTool = lastWritingTool.takeIf { it != PenTool.NONE && it != PenTool.ERASER } ?: PenTool.PEN,
    )

    companion object {
        private const val OPAQUE = 0xFF000000.toInt()
        const val MARKER_ALPHA = 0x66
        const val DEFAULT_PEN_WIDTH = 1.6f
        const val DEFAULT_MARKER_WIDTH = 10f
        val PEN_WIDTH_RANGE = 0.5f..6f
        val MARKER_WIDTH_RANGE = 4f..24f

        /** Graphite, navy, red, green, purple, orange, teal, brown. */
        val PEN_COLORS = listOf(
            0xFF202321, 0xFF1F3A93, 0xFFC62828, 0xFF2E7D32,
            0xFF6A1B9A, 0xFFE65100, 0xFF00796B, 0xFF5D4037,
        ).map { it.toInt() }

        /** Translucent marker colours: yellow, green, blue, pink, orange, violet. */
        val MARKER_COLORS = listOf(
            0xFFFFE066, 0xFF9BE7A0, 0xFF9AD0F5, 0xFFF7A8C8, 0xFFFFC078, 0xFFC9B6F2,
        ).map { withMarkerAlpha(it.toInt()) }

        fun withMarkerAlpha(color: Int): Int = (MARKER_ALPHA shl 24) or (color and 0xFFFFFF)
    }
}

class PenSettingsStore(private val preferences: SharedPreferences) {

    fun load(): PenSettings = decode(preferences.getString(KEY, null))

    fun save(settings: PenSettings) {
        preferences.edit { putString(KEY, encode(settings.normalized())) }
    }

    companion object {
        const val KEY = "study.pen.settings.v1"

        internal fun encode(settings: PenSettings): String {
            val json = JsonObject()
            json.addProperty("penColor", settings.penColor)
            json.addProperty("penWidth", settings.penWidth)
            json.addProperty("markerColor", settings.markerColor)
            json.addProperty("markerWidth", settings.markerWidth)
            json.addProperty("stylusOnly", settings.stylusOnly)
            json.addProperty("penAlwaysWrites", settings.penAlwaysWrites)
            json.addProperty("lastWritingTool", settings.lastWritingTool.name)
            return json.toString()
        }

        /** Malformed fields fall back individually to defaults. */
        internal fun decode(value: String?): PenSettings {
            val defaults = PenSettings()
            if (value.isNullOrBlank()) {
                return defaults
            }
            val json = runCatching { JsonParser.parseString(value).asJsonObject }.getOrNull() ?: return defaults
            fun int(key: String, fallback: Int) = runCatching { json[key].asInt }.getOrDefault(fallback)
            fun float(key: String, fallback: Float) =
                runCatching { json[key].asFloat.takeIf { it.isFinite() } ?: fallback }.getOrDefault(fallback)
            fun bool(key: String, fallback: Boolean) = runCatching { json[key].asBoolean }.getOrDefault(fallback)
            val tool = runCatching { PenTool.valueOf(json["lastWritingTool"].asString) }.getOrDefault(PenTool.PEN)
            return PenSettings(
                penColor = int("penColor", defaults.penColor),
                penWidth = float("penWidth", defaults.penWidth),
                markerColor = int("markerColor", defaults.markerColor),
                markerWidth = float("markerWidth", defaults.markerWidth),
                stylusOnly = bool("stylusOnly", defaults.stylusOnly),
                penAlwaysWrites = bool("penAlwaysWrites", defaults.penAlwaysWrites),
                lastWritingTool = tool,
            ).normalized()
        }
    }
}
