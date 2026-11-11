// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.pen

import com.github.barteksc.pdfviewer.PenTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PenSettingsStoreTest {

    @Test
    fun settingsRoundTrip() {
        val settings = PenSettings(
            penColor = PenSettings.PEN_COLORS[2],
            penWidth = 2.4f,
            markerColor = PenSettings.MARKER_COLORS[1],
            markerWidth = 12f,
            stylusOnly = false,
            penAlwaysWrites = true,
            lastWritingTool = PenTool.MARKER,
        )
        assertEquals(settings, PenSettingsStore.decode(PenSettingsStore.encode(settings)))
    }

    @Test
    fun missingOrMalformedValuesFallBackIndividually() {
        assertEquals(PenSettings(), PenSettingsStore.decode(null))
        assertEquals(PenSettings(), PenSettingsStore.decode("not json"))
        val decoded = PenSettingsStore.decode(
            """{"penWidth":"wide","markerWidth":99,"stylusOnly":false,"lastWritingTool":"LASER"}"""
        )
        assertEquals(PenSettings.DEFAULT_PEN_WIDTH, decoded.penWidth)
        assertEquals(PenSettings.MARKER_WIDTH_RANGE.endInclusive, decoded.markerWidth)
        assertFalse(decoded.stylusOnly)
        assertEquals(PenTool.PEN, decoded.lastWritingTool)
    }

    @Test
    fun normalizationKeepsPenOpaqueAndMarkerTranslucent() {
        val normalized = PenSettings(
            penColor = 0x10203040,
            markerColor = 0xFFFFE066.toInt(),
            penWidth = 0.01f,
            lastWritingTool = PenTool.ERASER,
        ).normalized()
        assertEquals(0xFF203040.toInt(), normalized.penColor)
        assertEquals(PenSettings.MARKER_ALPHA, normalized.markerColor ushr 24)
        assertEquals(PenSettings.PEN_WIDTH_RANGE.start, normalized.penWidth)
        assertEquals(PenTool.PEN, normalized.lastWritingTool)
        assertTrue(PenSettings.MARKER_COLORS.all { it ushr 24 == PenSettings.MARKER_ALPHA })
        assertTrue(PenSettings.PEN_COLORS.all { it ushr 24 == 0xFF })
    }
}
