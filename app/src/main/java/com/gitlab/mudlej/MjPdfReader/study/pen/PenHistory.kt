// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.pen

import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit

/**
 * A reversible pen action. [forward] re-does the action and [backward] undoes it;
 * both are plain annotation edits, so undo and redo go through the same PDF
 * write and recovery-journal path as every other annotation change.
 */
data class PenOperation(
    val forward: List<AnnotationEdit>,
    val backward: List<AnnotationEdit>,
) {
    companion object {
        /** A newly created stroke or highlight. */
        fun created(edit: AnnotationEdit): PenOperation = PenOperation(listOf(edit), listOfNotNull(inverseOf(edit)))

        /** Something the eraser removed; [restore] re-creates it. */
        fun erased(deletion: AnnotationEdit, restore: List<AnnotationEdit>): PenOperation =
            PenOperation(listOf(deletion), restore)

        fun inverseOf(edit: AnnotationEdit): AnnotationEdit? = when (edit) {
            is AnnotationEdit.AddInk -> AnnotationEdit.DeleteInk(edit.page, edit.group)
            is AnnotationEdit.Add -> AnnotationEdit.Delete(edit.page, edit.group)
            else -> null
        }
    }
}

/** Bounded undo/redo stacks. A new action clears the redo stack. */
class PenHistory(private val limit: Int = DEFAULT_LIMIT) {
    private val undoStack = ArrayDeque<PenOperation>()
    private val redoStack = ArrayDeque<PenOperation>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(operation: PenOperation) {
        undoStack.addLast(operation)
        while (undoStack.size > limit) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    /** Returns the operation to undo and moves it to the redo stack. */
    fun undo(): PenOperation? {
        val operation = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(operation)
        return operation
    }

    fun redo(): PenOperation? {
        val operation = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(operation)
        return operation
    }

    /** Puts an operation back after its undo/redo could not be applied to the PDF. */
    fun revertUndo() {
        redoStack.removeLastOrNull()?.let(undoStack::addLast)
    }

    fun revertRedo() {
        undoStack.removeLastOrNull()?.let(redoStack::addLast)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        const val DEFAULT_LIMIT = 200
    }
}
