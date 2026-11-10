// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.annotation

import android.content.SharedPreferences
import com.google.gson.JsonParser
import com.google.gson.Gson

/** Local preferences only. The document's annotations themselves always live inside the PDF. */
class HighlightStyleStore(private val preferences: SharedPreferences) {
    val current: HighlightStyle
        get() = read(CURRENT).firstOrNull() ?: HighlightStyle.DEFAULT
    val recent: List<HighlightStyle> get() = read(RECENT).take(LIMIT)
    val favorites: List<HighlightStyle> get() = read(FAVORITES).take(LIMIT)

    fun use(style: HighlightStyle) {
        // One atomic preference transaction; Cancel never calls this method.
        preferences.edit()
            .putString(CURRENT, encode(listOf(style)))
            .putString(RECENT, encode((listOf(style) + recent).distinctBy { it.argb }.take(LIMIT)))
            .apply()
    }

    fun setFavorite(style: HighlightStyle, favorite: Boolean) {
        val remaining = favorites.filter { it.argb != style.argb }
        preferences.edit().putString(FAVORITES,
            encode(if (favorite) (listOf(style) + remaining).take(LIMIT) else remaining)).apply()
    }

    private fun read(key: String): List<HighlightStyle> = runCatching {
        decode(preferences.getString(key, null))
    }.getOrDefault(emptyList())

    companion object {
        const val CURRENT = "study.highlight.current.v1"
        const val RECENT = "study.highlight.recent.v1"
        const val FAVORITES = "study.highlight.favorites.v1"
        const val LIMIT = 8
        internal fun encode(styles: List<HighlightStyle>): String = Gson().toJson(styles)
        internal fun decode(value: String?): List<HighlightStyle> = runCatching {
            if (value == null) return emptyList()
            JsonParser.parseString(value).asJsonArray.take(LIMIT).mapNotNull { entry ->
                runCatching {
                    val obj = entry.asJsonObject
                    HighlightStyle(obj["rgb"].asInt, obj["opacityPercent"].asInt, obj["meaning"].asString)
                }.getOrNull()
            }.distinctBy { it.argb }
        }.getOrDefault(emptyList())
    }
}
