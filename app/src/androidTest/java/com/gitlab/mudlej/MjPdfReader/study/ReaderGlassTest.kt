// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderGlassTest {
    @Test
    fun opensPdfAndUsesTheGroupedMenuToEnterFullscreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val sample = File.createTempFile("study-reader-", ".pdf", context.cacheDir)
        try {
            instrumentation.context.assets.open("normal.pdf").use { input ->
                sample.outputStream().use { output -> input.copyTo(output) }
            }
            val intent = Intent(Intent.ACTION_VIEW)
                .setClass(context, MainActivity::class.java)
                .setDataAndType(Uri.fromFile(sample), "application/pdf")
            val device = UiDevice.getInstance(instrumentation)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                val deadline = SystemClock.uptimeMillis() + 15_000
                var loaded = false
                while (!loaded && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { loaded = it.findViewById<PDFView>(R.id.pdfView).pageCount > 0 }
                    if (!loaded) SystemClock.sleep(100)
                }
                assertTrue("The actual reader must load the PDF fixture", loaded)
                scenario.onActivity { activity ->
                    val toolbar = activity.findViewById<Toolbar>(R.id.readerToolbar)
                    assertTrue(toolbar.isShown)
                    assertNotNull(toolbar.background)
                }
                device.takeScreenshot(File(context.filesDir, "study-glass-reader.png"))

                val more = device.wait(Until.findObject(By.res(context.packageName, "readerActionsOption")), 5_000)
                assertNotNull("The visible toolbar must expose its menu action", more)
                more!!.click()
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.study_reader_menu))), 5_000))
                assertTrue(device.hasObject(By.text(context.getString(R.string.study_reader_reading))))
                device.takeScreenshot(File(context.filesDir, "study-glass-menu.png"))

                val fullscreen = device.wait(Until.findObject(By.text(context.getString(R.string.full_screen))), 5_000)
                assertNotNull("The reading group must include fullscreen", fullscreen)
                fullscreen!!.click()
                scenario.onActivity { activity ->
                    assertTrue(activity.findViewById<Toolbar>(R.id.readerToolbar).visibility == View.GONE)
                }
            }
        } finally {
            sample.delete()
        }
    }
}
