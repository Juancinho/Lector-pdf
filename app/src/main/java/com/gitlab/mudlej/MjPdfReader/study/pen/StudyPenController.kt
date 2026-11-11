// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.pen

import com.github.barteksc.pdfviewer.InkGeometry
import com.github.barteksc.pdfviewer.OnPenInputListener
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.PenTool
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.shockwave.pdfium.PdfDocument
import java.util.UUID

/**
 * Turns pen results from [PDFView] into standard PDF annotations and keeps the
 * undo/redo history. Every change is written through [applyEdit] (the document)
 * and [recordEdit] (dirty state + recovery journal), exactly like selection-card
 * highlights, so the existing staged save path persists pen work.
 */
class StudyPenController(
    private val pdfView: PDFView,
    private val applyEdit: (AnnotationEdit) -> Boolean,
    private val recordEdit: (AnnotationEdit) -> Unit,
    private val canEditDocument: () -> Boolean,
    private val currentHighlightColor: () -> Int,
    private val now: () -> String,
    private val onPenGesture: () -> Unit,
    private val onFailure: () -> Unit,
) : OnPenInputListener {

    private val history = PenHistory()
    private var stateListener: (() -> Unit)? = null

    var settings: PenSettings = PenSettings()
        private set

    val tool: PenTool get() = pdfView.penTool
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    fun attach(initial: PenSettings) {
        settings = initial.normalized()
        pdfView.setOnPenInputListener(this)
        pushSettingsToView()
    }

    fun setStateListener(listener: (() -> Unit)?) {
        stateListener = listener
    }

    fun selectTool(tool: PenTool) {
        pdfView.setPenTool(tool)
        if (tool.isInk || tool == PenTool.HIGHLIGHTER) {
            settings = settings.copy(lastWritingTool = tool)
        }
        pushSettingsToView()
        notifyState()
    }

    fun updateSettings(next: PenSettings) {
        settings = next.normalized()
        pushSettingsToView()
        notifyState()
    }

    /** Re-reads the highlight colour, e.g. after the user picked a new highlight style. */
    fun refreshHighlightColor() {
        pushSettingsToView()
    }

    /** A different document was opened: its pen history no longer applies. */
    fun onDocumentChanged() {
        history.clear()
        notifyState()
    }

    private fun pushSettingsToView() {
        pdfView.setPenStyle(
            settings.penColor,
            settings.penWidth,
            settings.markerColor,
            settings.markerWidth,
            currentHighlightColor(),
        )
        pdfView.setPenStylusOnly(settings.stylusOnly)
        pdfView.setPenAlwaysWrites(settings.penAlwaysWrites)
    }

    fun undo(): Boolean {
        if (!canEditDocument()) return false
        val operation = history.undo() ?: return false
        if (!applyAll(operation.backward)) {
            history.revertUndo()
            onFailure()
        }
        notifyState()
        return true
    }

    fun redo(): Boolean {
        if (!canEditDocument()) return false
        val operation = history.redo() ?: return false
        if (!applyAll(operation.forward)) {
            history.revertRedo()
            onFailure()
        }
        notifyState()
        return true
    }

    private fun applyAll(edits: List<AnnotationEdit>): Boolean {
        for (edit in edits) {
            if (!applyEdit(edit)) {
                return false
            }
            recordEdit(edit)
        }
        return true
    }

    private fun commit(operation: PenOperation) {
        if (applyAll(operation.forward)) {
            history.push(operation)
        } else {
            onFailure()
        }
        notifyState()
    }

    // ---- OnPenInputListener --------------------------------------------------

    override fun onPenGestureStarted(tool: PenTool) {
        // The highlight style may have changed from the selection card since the
        // last gesture; the viewer reads the colour right after this callback.
        pushSettingsToView()
        onPenGesture()
    }

    override fun onPenHighlight(request: PDFView.HighlightRequest, color: Int) {
        if (!canEditDocument() || request.pdfRects.isEmpty()) return
        val edit = AnnotationEdit.Add(
            request.pageIndex,
            newGroup(),
            request.pdfRects,
            color,
            request.selectedText,
            now(),
        )
        commit(PenOperation.created(edit))
    }

    override fun onPenStroke(pageIndex: Int, pdfPoints: FloatArray, pressures: FloatArray, color: Int, maxWidth: Float) {
        if (!canEditDocument() || pdfPoints.size < 2) return
        val edit = AnnotationEdit.AddInk(
            pageIndex,
            newGroup(),
            listOf(pdfPoints),
            listOf(pressures),
            color,
            maxWidth,
            now(),
        )
        commit(PenOperation.created(edit))
    }

    override fun onPenEraseInk(pageIndex: Int, ink: PdfDocument.InkAnnotation) {
        if (!canEditDocument() || ink.groupKey.isEmpty()) return
        val strokes = ink.strokes.toList()
        val pressures = InkGeometry.decodePressure(ink.pressure, ink.strokes)?.toList().orEmpty()
        val restore = AnnotationEdit.AddInk(
            pageIndex,
            ink.groupKey,
            strokes,
            pressures,
            ink.color,
            ink.strokeWidth,
            ink.creationDate.ifEmpty { null },
        )
        commit(PenOperation.erased(AnnotationEdit.DeleteInk(pageIndex, ink.groupKey), listOf(restore)))
    }

    override fun onPenEraseHighlight(group: PDFView.HighlightGroup) {
        if (!canEditDocument() || group.groupKey.isEmpty() || group.pdfRects.isEmpty()) return
        val restore = buildList {
            add(
                AnnotationEdit.Add(
                    group.pageIndex,
                    group.groupKey,
                    group.pdfRects,
                    group.color,
                    group.quote,
                    group.creationDate.ifEmpty { null },
                )
            )
            if (group.note.isNotBlank()) {
                add(AnnotationEdit.SetNote(group.pageIndex, group.groupKey, group.note, now()))
            }
        }
        commit(PenOperation.erased(AnnotationEdit.Delete(group.pageIndex, group.groupKey), restore))
    }

    private fun newGroup(): String = UUID.randomUUID().toString()

    private fun notifyState() {
        stateListener?.invoke()
    }
}
