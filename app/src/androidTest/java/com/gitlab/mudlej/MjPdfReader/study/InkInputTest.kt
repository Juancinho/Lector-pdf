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
        lateinit var pdf:PDFView
        lateinit var controller:InkController
        val vm=ReaderViewModel(SavedStateHandle())
        ActivityScenario.launch(StudyPreviewActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val parent=CoordinatorLayout(activity)
                pdf=PDFView(activity,null)
                parent.addView(pdf,CoordinatorLayout.LayoutParams(-1,-1))
                activity.setContentView(parent)
                controller=InkController(activity,pdf,vm,activity.lifecycleScope,{true},{edits.add(it)},{})
                controller.attach()
                pdf.fromBytes(input).onLoad { loaded.countDown() }.load()
            }
            assertTrue("PDF did not load",loaded.await(20,TimeUnit.SECONDS))
            scenario.onActivity {
                controller.onDocument("test-input")
                vm.studyInkEnabled=true
                pdf.zoomTo(1.75f)
                pdf.moveTo(-20f,-35f)
                val start=pdf.studyPdfToView(0,60f,80f)!!
                val end=pdf.studyPdfToView(0,90f,90f)!!
                assertEquals(0,pdf.studyPageAt(start.x,start.y))
                val roundTrip=pdf.studyViewToPdf(0,start.x,start.y)!!
                assertEquals(60f,roundTrip.x,.1f);assertEquals(80f,roundTrip.y,.1f)
                val now=SystemClock.uptimeMillis()
                // Finger input must retain the reader's original listener and produce no ink.
                event(now,MotionEvent.ACTION_DOWN,start.x,start.y,MotionEvent.TOOL_TYPE_FINGER).useEvent(pdf)
                event(now,MotionEvent.ACTION_UP,start.x,start.y,MotionEvent.TOOL_TYPE_FINGER).useEvent(pdf)
                assertTrue(edits.isEmpty())
                event(now+10,MotionEvent.ACTION_DOWN,start.x,start.y,MotionEvent.TOOL_TYPE_STYLUS,.2f).useEvent(pdf)
                event(now+20,MotionEvent.ACTION_MOVE,end.x,end.y,MotionEvent.TOOL_TYPE_STYLUS,.8f).useEvent(pdf)
                event(now+30,MotionEvent.ACTION_UP,end.x,end.y,MotionEvent.TOOL_TYPE_STYLUS,.8f).useEvent(pdf)
                val patch=edits.single() as AnnotationEdit.InkPatch
                val stroke=InkCodec.decode(patch.after.single())!!
                assertEquals(0,stroke.page);assertEquals(60f,stroke.points.first().x,.1f)
                assertEquals(80f,stroke.points.first().y,.1f)
                assertTrue(stroke.points.last().pressure>stroke.points.first().pressure)
                assertTrue(vm.studyInkHistory.canUndo)
                controller.destroy();pdf.recycle()
            }
        }
    }
    private fun event(time:Long,action:Int,x:Float,y:Float,tool:Int,pressure:Float=1f):MotionEvent {
        val properties=MotionEvent.PointerProperties().apply { id=0;toolType=tool }
        val coordinates=MotionEvent.PointerCoords().apply { this.x=x;this.y=y;this.pressure=pressure;size=.05f }
        return MotionEvent.obtain(time,time,action,1,arrayOf(properties),arrayOf(coordinates),0,0,1f,1f,0,0,
            if(tool==MotionEvent.TOOL_TYPE_STYLUS)InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN,0)
    }
    private fun MotionEvent.useEvent(pdf:PDFView){try { pdf.dispatchTouchEvent(this) }finally { recycle() }}
}
