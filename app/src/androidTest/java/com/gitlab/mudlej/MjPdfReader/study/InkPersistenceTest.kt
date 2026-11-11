// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gitlab.mudlej.MjPdfReader.study.ink.*
import com.shockwave.pdfium.PdfiumCore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class InkPersistenceTest {
    @Test fun pressureInkSurvivesSaveReopenAndIdempotentRemoval() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val core=PdfiumCore(context)
        val input=instrumentation.context.assets.open("normal.pdf").use { it.readBytes() }
        val output=File.createTempFile("study-ink-roundtrip",".pdf",context.cacheDir)
        val stroke=InkStroke("study-pressure-test",0,listOf(InkPoint(20f,30f,.1f),InkPoint(60f,50f,.5f),InkPoint(100f,30f,1f)),0xCC193A7E.toInt(),8f)
        try {
            val doc=core.newDocument(input)
            try {
                core.openPage(doc,0)
                repeat(2) { assertTrue(core.addStudyInk(doc,0,stroke.nativePoints(),stroke.color,stroke.width,stroke.id,InkCodec.encode(stroke),true)) }
                assertEquals(listOf(stroke),core.getStudyInk(doc,0).map { InkCodec.decode(it) })
                ParcelFileDescriptor.open(output,ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE).use { assertTrue(core.saveAsCopy(doc,it)) }
            } finally { core.closeDocument(doc) }
            val reopened=core.newDocument(output.readBytes())
            try {
                core.openPage(reopened,0)
                assertEquals(stroke,InkCodec.decode(core.getStudyInk(reopened,0).single()))
                // The stored appearance must actually change rendering, not only metadata.
                val w=core.getPageWidthPoint(reopened,0);val h=core.getPageHeightPoint(reopened,0)
                val withInk=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                val withoutInk=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                try {
                    withInk.eraseColor(Color.WHITE);withoutInk.eraseColor(Color.WHITE)
                    core.renderPageBitmap(reopened,withInk,0,0,0,w,h,true)
                    repeat(2){assertTrue(core.removeStudyInk(reopened,0,stroke.id))}
                    assertTrue(core.getStudyInk(reopened,0).isEmpty())
                    core.renderPageBitmap(reopened,withoutInk,0,0,0,w,h,true)
                    assertFalse("Ink annotation must have a visible PDF appearance",withInk.sameAs(withoutInk))
                } finally { withInk.recycle();withoutInk.recycle() }
                ParcelFileDescriptor.open(output,ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE).use { assertTrue(core.saveAsCopy(reopened,it)) }
            } finally { core.closeDocument(reopened) }
            val final=core.newDocument(output.readBytes())
            try { core.openPage(final,0);assertTrue(core.getStudyInk(final,0).isEmpty()) } finally { core.closeDocument(final) }
        } finally { output.delete() }
    }
}
