// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study

import android.graphics.RectF
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shockwave.pdfium.PdfiumCore
import com.gitlab.mudlej.MjPdfReader.study.annotation.HighlightStyle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native JNI round trip, not an in-memory fake. Run on an arm64 device with the study APK. */
@RunWith(AndroidJUnit4::class)
class HighlightPersistenceTest {
    @Test fun arbitraryColorAndOpacitySurviveCreateSaveReopenAndRecolor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val core = PdfiumCore(context)
        val input = instrumentation.context.assets.open("normal.pdf").use { it.readBytes() }
        val output = File.createTempFile("study-roundtrip", ".pdf", context.cacheDir)
        val first = HighlightStyle(0x193A7E, 37).argb
        val second = HighlightStyle(0xD1495B, 82).argb
        try {
            val doc = core.newDocument(input)
            try {
                core.openPage(doc, 0)
                assertTrue(core.createHighlightAnnotation(doc, 0, listOf(RectF(20f, 100f, 150f, 80f)), first,
                    "A study quote", "study-test", "D:20261111000000Z"))
                assertEquals(first, core.getHighlightAnnotations(doc, 0).single { it.groupKey == "study-test" }.color)
                ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE).use {
                    assertTrue(core.saveAsCopy(doc, it))
                }
            } finally { core.closeDocument(doc) }
            val reopened = core.newDocument(output.readBytes())
            try {
                core.openPage(reopened, 0)
                val annotation = core.getHighlightAnnotations(reopened, 0).single { it.groupKey == "study-test" }
                assertEquals(first, annotation.color)
                assertTrue(core.setHighlightAnnotationColor(reopened, 0, annotation.annotationIndex, "study-test", second))
                ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE).use {
                    assertTrue(core.saveAsCopy(reopened, it))
                }
            } finally { core.closeDocument(reopened) }
            val final = core.newDocument(output.readBytes())
            try {
                core.openPage(final, 0)
                assertEquals(second, core.getHighlightAnnotations(final, 0).single { it.groupKey == "study-test" }.color)
            } finally { core.closeDocument(final) }
            output.copyTo(File(context.filesDir, "study-roundtrip.pdf"), overwrite = true)
        } finally { output.delete() }
    }
}
