// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.gitlab.mudlej.MjPdfReader.study.ink.*
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic input verifies routing and coordinates; it does not certify Xiaomi palm rejection. */
@RunWith(AndroidJUnit4::class)
class InkInputTest {
    @Test fun fingersDoNotWriteAndPenUsesPdfCoordinatesAfterZoom() {
        val input=InstrumentationRegistry.getInstrumentation().context.assets.open("normal.pdf").use { it.readBytes() }
        val loaded=CountDownLatch(1)
        val edits=mutableListOf<AnnotationEdit>()
        val interactions=mutableListOf<Int>()
        lateinit var pdf:PDFView
        lateinit var controller:InkController
        val vm=ReaderViewModel(SavedStateHandle())
        ActivityScenario.launch(StudyPreviewActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val parent=CoordinatorLayout(activity)
                pdf=PDFView(activity,null)
                parent.addView(pdf,CoordinatorLayout.LayoutParams(-1,-1))
                activity.setContentView(parent)
                vm.studyToolbarExpanded=true
                vm.studyInkEnabled=true
                controller=InkController(activity,pdf,vm,activity.lifecycleScope,{true},{edits.add(it)},{})
                controller.attach()
                pdf.fromBytes(input).onLoad { loaded.countDown() }
                    .onDocumentInteraction { interactions.add(it.actionMasked) }.load()
            }
            assertTrue("PDF did not load",loaded.await(20,TimeUnit.SECONDS))
            scenario.onActivity {
                controller.onDocument("test-input")
                vm.studyInkEnabled=true
                pdf.zoomTo(1.75f)
                pdf.moveTo(-20f,-35f)
                val fingerDown=SystemClock.uptimeMillis()
                val initialOffset=pdf.currentYOffset
                val fingerX=pdf.width*.7f
                val fingerY=pdf.height*.7f
                event(fingerDown,fingerDown,MotionEvent.ACTION_DOWN,fingerX,fingerY,MotionEvent.TOOL_TYPE_FINGER).useEvent(pdf)
                event(fingerDown,fingerDown+16,MotionEvent.ACTION_MOVE,fingerX,fingerY-60f,MotionEvent.TOOL_TYPE_FINGER).useEvent(pdf)
                event(fingerDown,fingerDown+32,MotionEvent.ACTION_MOVE,fingerX,fingerY-160f,MotionEvent.TOOL_TYPE_FINGER).useEvent(pdf)
                event(fingerDown,fingerDown+40,MotionEvent.ACTION_UP,fingerX,fingerY-160f,MotionEvent.TOOL_TYPE_FINGER).useEvent(pdf)
                assertNotEquals("Finger drag must still navigate",initialOffset,pdf.currentYOffset)
                assertTrue(edits.isEmpty())
                val expected=pdf.studyViewToPdf(0,pdf.width*.45f,pdf.height*.5f)!!
                val start=pdf.studyPdfToView(0,expected.x,expected.y)!!
                val end=android.graphics.PointF(start.x+30f,start.y+20f)
                assertEquals(0,pdf.studyPageAt(start.x,start.y))
                val roundTrip=pdf.studyViewToPdf(0,start.x,start.y)!!
                assertEquals(expected.x,roundTrip.x,.1f);assertEquals(expected.y,roundTrip.y,.1f)
                val now=SystemClock.uptimeMillis()
                event(now,now,MotionEvent.ACTION_DOWN,start.x,start.y,MotionEvent.TOOL_TYPE_STYLUS,.2f).useEvent(pdf)
                assertFalse("Pen down must stop navigation inertia",pdf.isFlinging)
                event(now,now+10,MotionEvent.ACTION_MOVE,end.x,end.y,MotionEvent.TOOL_TYPE_STYLUS,.8f).useEvent(pdf)
                event(now,now+20,MotionEvent.ACTION_UP,end.x,end.y,MotionEvent.TOOL_TYPE_STYLUS,.8f).useEvent(pdf)
                val patch=edits.single() as AnnotationEdit.InkPatch
                val stroke=InkCodec.decode(patch.after.single())!!
                assertEquals(0,stroke.page);assertEquals(expected.x,stroke.points.first().x,.1f)
                assertEquals(expected.y,stroke.points.first().y,.1f)
                assertTrue(stroke.points.last().pressure>stroke.points.first().pressure)
                assertTrue(vm.studyInkHistory.canUndo)
                assertTrue(interactions.contains(MotionEvent.ACTION_DOWN))
                assertTrue(interactions.contains(MotionEvent.ACTION_UP))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                val context=InstrumentationRegistry.getInstrumentation().targetContext
                java.io.File(context.filesDir,"study-ink-toolbar.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
                bitmap.recycle()
            }
            scenario.onActivity { controller.destroy();pdf.recycle() }
        }
    }
    private fun event(downTime:Long,time:Long,action:Int,x:Float,y:Float,tool:Int,pressure:Float=1f):MotionEvent {
        val properties=MotionEvent.PointerProperties().apply { id=0;toolType=tool }
        val coordinates=MotionEvent.PointerCoords().apply { this.x=x;this.y=y;this.pressure=pressure;size=.05f }
        return MotionEvent.obtain(downTime,time,action,1,arrayOf(properties),arrayOf(coordinates),0,0,1f,1f,0,0,
            if(tool==MotionEvent.TOOL_TYPE_STYLUS)InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN,0)
    }
    private fun MotionEvent.useEvent(pdf:PDFView){try { pdf.dispatchTouchEvent(this) }finally { recycle() }}
}
