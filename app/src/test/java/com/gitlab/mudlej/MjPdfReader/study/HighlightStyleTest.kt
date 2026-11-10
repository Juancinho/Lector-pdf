// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study

import com.gitlab.mudlej.MjPdfReader.study.annotation.HighlightStyle
import com.gitlab.mudlej.MjPdfReader.study.annotation.HighlightStyleStore
import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import org.junit.Assert.*
import org.junit.Test

class HighlightStyleTest {
    @Test fun strictHexParsingPreservesBlackAndExpandsShorthand() {
        assertEquals(0, HighlightStyle.parseHex("#000000"))
        assertEquals(0xAABBCC, HighlightStyle.parseHex(" abc "))
        assertEquals(0x12ABEF, HighlightStyle.parseHex("#12abEF"))
        listOf("", "#", "#00000000", "##123", "12G456", "-12345").forEach {
            assertNull(it, HighlightStyle.parseHex(it))
        }
    }
    @Test fun rgbInputRejectsIncompleteOrOutOfRangeChannels() {
        assertEquals(0x00FF07, HighlightStyle.parseRgb("0", "255", "7"))
        assertNull(HighlightStyle.parseRgb("256", "0", "0"))
        assertNull(HighlightStyle.parseRgb("-1", "0", "0"))
        assertNull(HighlightStyle.parseRgb("", "0", "0"))
    }
    @Test fun alphaRoundTripsEverySupportedOpacity() {
        (1..100).forEach { opacity ->
            val style = HighlightStyle(0x123456, opacity)
            assertEquals(style, HighlightStyle.fromArgb(style.argb))
            assertEquals("#123456", style.hex)
        }
    }
    @Test fun preferencesSurviveRecreationAndRetainMeanings() {
        val prefs = InMemorySharedPreferences()
        val style = HighlightStyle(0x193A7E, 73, "Definición · λ")
        HighlightStyleStore(prefs).apply { use(style); setFavorite(style, true) }
        HighlightStyleStore(prefs).apply {
            assertEquals(style, current)
            assertEquals(listOf(style), recent)
            assertEquals(listOf(style), favorites)
            setFavorite(style, false)
            assertTrue(favorites.isEmpty())
        }
    }
    @Test fun recentsAreBoundedDistinctAndMostRecentFirst() {
        val store = HighlightStyleStore(InMemorySharedPreferences())
        (0..12).forEach { store.use(HighlightStyle(it)) }
        store.use(HighlightStyle(8, meaning = "Updated label"))
        assertEquals(8, store.recent.size)
        assertEquals(8, store.recent.first().rgb)
        assertEquals("Updated label", store.recent.first().meaning)
        assertEquals(8, store.recent.map { it.argb }.distinct().size)
    }
    @Test fun damagedPreferencesFallBackWithoutLosingValidEntries() {
        val prefs = InMemorySharedPreferences(mapOf(
            HighlightStyleStore.CURRENT to "not-json",
            HighlightStyleStore.FAVORITES to """[{"rgb":1,"opacityPercent":0,"meaning":"bad"},{"rgb":2,"opacityPercent":50,"meaning":"valid"}]""",
            HighlightStyleStore.RECENT to 123,
        ))
        val store = HighlightStyleStore(prefs)
        assertEquals(HighlightStyle.DEFAULT, store.current)
        assertEquals(listOf(HighlightStyle(2, 50, "valid")), store.favorites)
        assertTrue(store.recent.isEmpty())
    }
}
