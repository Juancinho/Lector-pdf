// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.pen

import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PenHistoryTest {

    private fun ink(group: String) = AnnotationEdit.AddInk(
        3, group, listOf(floatArrayOf(1f, 2f, 3f, 4f)), listOf(floatArrayOf(0.2f, 0.9f)), 0xFF000000.toInt(), 1.5f,
    )

    @Test
    fun createdStrokeUndoesToADeletionOfTheSameGroup() {
        val operation = PenOperation.created(ink("g1"))
        assertEquals(listOf(AnnotationEdit.DeleteInk(3, "g1")), operation.backward)
        val highlight = AnnotationEdit.Add(2, "h1", emptyList(), 0, "quote")
        assertEquals(listOf(AnnotationEdit.Delete(2, "h1")), PenOperation.created(highlight).backward)
    }

    @Test
    fun undoRedoMoveOperationsBetweenStacks() {
        val history = PenHistory()
        val first = PenOperation.created(ink("a"))
        val second = PenOperation.erased(AnnotationEdit.DeleteInk(3, "b"), listOf(ink("b")))
        history.push(first)
        history.push(second)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(second, history.undo())
        assertEquals(first, history.undo())
        assertNull(history.undo())
        assertEquals(first, history.redo())
        history.push(PenOperation.created(ink("c")))
        assertFalse("a new action clears redo", history.canRedo)
    }

    @Test
    fun failedUndoAndRedoCanBeReverted() {
        val history = PenHistory()
        val operation = PenOperation.created(ink("a"))
        history.push(operation)
        history.undo()
        history.revertUndo()
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
        history.undo()
        history.redo()
        history.revertRedo()
        assertTrue(history.canRedo)
        assertFalse(history.canUndo)
    }

    @Test
    fun historyIsBounded() {
        val history = PenHistory(limit = 3)
        repeat(5) { history.push(PenOperation.created(ink("g$it"))) }
        var count = 0
        while (history.undo() != null) count++
        assertEquals(3, count)
    }
}
