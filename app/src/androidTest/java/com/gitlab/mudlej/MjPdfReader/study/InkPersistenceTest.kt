// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.barteksc.pdfviewer.InkGeometry
import com.shockwave.pdfium.PdfiumCore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Native JNI round trip for pressure-sensitive /Ink: create → save → reopen →
 * read → erase → save → reopen. Run on an arm64 device with the study APK.
 */
@RunWith(AndroidJUnit4::class)
class InkPersistenceTest {
    @Test fun pressureInkSurvivesSaveReopenAndErase() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val core = PdfiumCore(context)
        val input = instrumentation.context.assets.open("normal.pdf").use { it.readBytes() }
        val output = File.createTempFile("study-ink", ".pdf", context.cacheDir)
        val strokes = arrayOf(floatArrayOf(40f, 400f, 60f, 410f, 90f, 405f, 120f, 420f))
        val pressures = arrayOf(floatArrayOf(0.2f, 0.6f, 1f, 0.4f))
        val color = 0x661F3A93
        val width = 6f
        try {
            val doc = core.newDocument(input)
            try {
                core.openPage(doc, 0)
                assertTrue(core.createInkAnnotation(doc, 0, strokes, color, width,
                    InkGeometry.appearanceStream(strokes, pressures, color, width),
                    InkGeometry.encodePressure(pressures), "ink-test", "D:20261111000000Z"))
                saveTo(core, doc, output)
            } finally { core.closeDocument(doc) }

            val reopened = core.newDocument(output.readBytes())
            try {
                core.openPage(reopened, 0)
                val ink = core.getInkAnnotations(reopened, 0).single { it.groupKey == "ink-test" }
                assertEquals(color, ink.color)
                assertEquals(width, ink.strokeWidth, 0.01f)
                assertArrayEquals(strokes[0], ink.strokes[0], 0.01f)
                val decoded = InkGeometry.decodePressure(ink.pressure, ink.strokes)
                assertArrayEquals(pressures[0], decoded!![0], 0.01f)
                assertTrue(core.removeInkAnnotation(reopened, 0, "ink-test"))
                saveTo(core, reopened, output)
            } finally { core.closeDocument(reopened) }

            val final = core.newDocument(output.readBytes())
            try {
                core.openPage(final, 0)
                assertTrue(core.getInkAnnotations(final, 0).none { it.groupKey == "ink-test" })
            } finally { core.closeDocument(final) }
        } finally { output.delete() }
    }

    private fun saveTo(core: PdfiumCore, doc: com.shockwave.pdfium.PdfDocument, output: File) {
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE).use {
            assertTrue(core.saveAsCopy(doc, it))
        }
    }
}
