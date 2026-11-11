// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ink

import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import org.junit.Assert.*
import org.junit.Test

class InkEngineTest {
    private fun stroke(id:String="a",page:Int=0)=InkStroke(id,page,listOf(InkPoint(10f,20f,.2f),InkPoint(30f,40f,1f)),0x804968B2.toInt(),2f)
    @Test fun codecRetainsPdfGeometryPressureAndAlpha() {
        val original=stroke()
        assertEquals(original,InkCodec.decode(InkCodec.encode(original)))
        assertArrayEquals(floatArrayOf(10f,20f,.2f,30f,40f,1f),original.nativePoints(),0f)
    }
    @Test fun malformedAndUnboundedRecordsAreRejected() {
        val valid=InkCodec.encode(stroke())
        assertNull(InkCodec.decode("{}"));assertNull(InkCodec.decode("{"));assertNull(InkCodec.decode("x".repeat(600_001)))
        assertNull(InkCodec.decode(valid.replace("\"width\":2.0","\"width\":-1")))
        assertNull(InkCodec.decode(valid.replace("\"pressure\":0.2","\"pressure\":2")))
        assertNull(InkCodec.decode(valid.replace("\"tool\":\"PEN\"","\"tool\":\"ERASER\"")))
    }
    @Test fun pressureUsesDeviceRangeAndHasSafeFallback() {
        assertEquals(.5f,InkStroke.pressure(512f,0f,1024f),.00001f)
        assertEquals(.05f,InkStroke.pressure(-1f),0f)
        assertEquals(1f,InkStroke.pressure(5f),0f)
        assertEquals(.5f,InkStroke.pressure(Float.NaN),0f)
    }
    @Test fun transformUsesPdfCoordinatesAndScalesNib() {
        val moved=stroke().transformed(5f,-10f,2f,10f,20f)
        assertEquals(InkPoint(15f,10f,.2f),moved.points.first())
        assertEquals(InkPoint(55f,50f,1f),moved.points.last())
        assertEquals(4f,moved.width,0f);assertEquals(stroke().id,moved.id)
    }
    @Test fun eraserHitsSegmentsAndIncludesNibWidth() {
        val line=stroke().copy(points=listOf(InkPoint(0f,0f),InkPoint(100f,0f)),width=4f)
        assertTrue(InkGeometry.hits(line,InkPoint(50f,3f),1f))
        assertFalse(InkGeometry.hits(line,InkPoint(50f,4f),1f))
        assertTrue(InkGeometry.hits(line.copy(points=listOf(InkPoint(5f,5f))),InkPoint(5f,5f),0f))
    }
    @Test fun lassoUsesClosedPolygonInPdfCoordinates() {
        val polygon=listOf(InkPoint(0f,0f),InkPoint(50f,0f),InkPoint(50f,50f),InkPoint(0f,50f))
        assertTrue(InkGeometry.selected(stroke(),polygon))
        assertFalse(InkGeometry.inside(InkPoint(60f,20f),polygon))
        assertFalse(InkGeometry.inside(InkPoint(10f,10f),polygon.take(2)))
    }
    @Test fun shapesRemainVectorAndClosedWhereExpected() {
        val start=InkPoint(10f,20f);val end=InkPoint(110f,220f)
        assertEquals(2,InkStroke.shape(InkTool.LINE,start,end).size)
        assertEquals(5,InkStroke.shape(InkTool.ARROW,start,end).size)
        val rectangle=InkStroke.shape(InkTool.RECTANGLE,start,end)
        assertEquals(rectangle.first(),rectangle.last());assertEquals(5,rectangle.size)
        val ellipse=InkStroke.shape(InkTool.ELLIPSE,start,end)
        assertEquals(65,ellipse.size);assertEquals(ellipse.first().x,ellipse.last().x,.0001f)
        assertTrue(ellipse.all { it.x in 10f..110f&&it.y in 20f..220f })
    }
    @Test fun undoRedoAndNewBranchRespectCommitOrder() {
        val history=InkHistory()
        val add=InkChange(emptyList(),listOf(stroke()))
        val move=InkChange(listOf(stroke()),listOf(stroke().transformed(10f,0f)))
        history.record(add);history.record(move)
        assertEquals(move.inverse(),history.peekUndo());history.didUndo()
        assertEquals(move,history.peekRedo());assertEquals(add.inverse(),history.peekUndo())
        history.didRedo();assertEquals(move.inverse(),history.peekUndo())
        history.didUndo();history.record(InkChange(emptyList(),listOf(stroke("b"))))
        assertFalse(history.canRedo)
    }
    @Test fun historyBoundsBothCommandAndPointMemory() {
        val history=InkHistory(2,4)
        repeat(3){history.record(InkChange(emptyList(),listOf(stroke(it.toString()))))}
        history.didUndo();history.didUndo();assertFalse(history.canUndo)
        val bounded=InkHistory(100,1);bounded.record(InkChange(emptyList(),listOf(stroke())))
        assertFalse(bounded.canUndo)
    }
    @Test(expected=IllegalArgumentException::class) fun patchCannotCrossPages(){InkChange(listOf(stroke()),listOf(stroke(page=1)))}
    @Test fun journalRoundTripPreservesTransformAndInverse() {
        val change=InkChange(listOf(stroke()),listOf(stroke().transformed(10f,0f)))
        val edit=AnnotationEdit.InkPatch(0,change.before.map(InkCodec::encode),change.after.map(InkCodec::encode))
        assertEquals(edit,AnnotationEdit.fromJsonLine(edit.toJsonLine()))
        assertEquals(change.inverse(),InkChange(edit.before.map { InkCodec.decode(it)!! },edit.after.map { InkCodec.decode(it)!! }).inverse())
    }
    @Test fun failedTransformRestoresRemovedStrokesAndRemovesPartialAdds() {
        val original=stroke();val other=stroke("b");val changed=original.transformed(10f,0f)
        val state=linkedMapOf(original.id to original,other.id to other)
        var failed=false
        val writer=object:InkWriter {
            override fun remove(stroke:InkStroke)=true.also { state.remove(stroke.id) }
            override fun add(stroke:InkStroke):Boolean {
                if(stroke.id=="b"&&!failed){failed=true;return false}
                state[stroke.id]=stroke;return true
            }
        }
        assertFalse(InkMutator.apply(writer,InkChange(listOf(original,other),listOf(changed,other.transformed(10f,0f)))))
        assertEquals(mapOf(original.id to original,other.id to other),state)
    }
    @Test fun successfulTransformAndItsUndoRestoreExactMetadata() {
        val original=stroke();val moved=original.transformed(8f,6f)
        val state=mutableMapOf(original.id to original)
        val writer=object:InkWriter {
            override fun add(stroke:InkStroke)=true.also { state[stroke.id]=stroke }
            override fun remove(stroke:InkStroke)=true.also { state.remove(stroke.id) }
        }
        val change=InkChange(listOf(original),listOf(moved))
        assertTrue(InkMutator.apply(writer,change));assertEquals(moved,state[original.id])
        assertTrue(InkMutator.apply(writer,change.inverse()));assertEquals(original,state[original.id])
    }
}
