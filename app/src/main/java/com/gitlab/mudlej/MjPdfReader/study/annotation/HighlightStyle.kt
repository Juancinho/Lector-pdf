// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.annotation

import java.util.Locale

/** PDF color is RGB + constant opacity (/C and /CA). Never overload a color channel. */
data class HighlightStyle(val rgb: Int, val opacityPercent: Int = 40, val meaning: String = "") {
    init {
        require(rgb in 0..0xFFFFFF)
        require(opacityPercent in 1..100)
        require(meaning.length <= 40)
    }

    val hex: String get() = String.format(Locale.ROOT, "#%06X", rgb)
    val argb: Int get() = (((opacityPercent * 255 + 50) / 100) shl 24) or rgb

    companion object {
        val DEFAULT = HighlightStyle(0xFFF176)
        fun fromArgb(argb: Int, meaning: String = ""): HighlightStyle = HighlightStyle(
            argb and 0xFFFFFF,
            (((argb ushr 24) * 100 + 127) / 255).coerceIn(1, 100),
            meaning,
        )
        /** Strict #RGB or #RRGGBB, optional hash; alpha has a separate, explicit control. */
        fun parseHex(text: String): Int? {
            val value = text.trim().removePrefix("#")
            if (!value.matches(Regex("[0-9a-fA-F]{3}|[0-9a-fA-F]{6}"))) return null
            return (if (value.length == 3) value.map { "$it$it" }.joinToString("") else value).toInt(16)
        }
        fun parseRgb(red: String, green: String, blue: String): Int? {
            val channels = listOf(red, green, blue).map { it.toIntOrNull() ?: return null }
            if (channels.any { it !in 0..255 }) return null
            return (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
        }
    }
}
