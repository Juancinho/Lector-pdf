package com.github.barteksc.pdfviewer;

import com.shockwave.pdfium.PdfDocument;

/** Pen tool results, delivered on the UI thread. The app owns persistence and undo. */
public interface OnPenInputListener {

    void onPenGestureStarted(PenTool tool);

    /** A pen sweep over text ended; {@code color} is the highlighter colour. */
    void onPenHighlight(PDFView.HighlightRequest request, int color);

    /** A finished ink stroke in PDF user space with per-point pressure in [0, 1]. */
    void onPenStroke(int pageIndex, float[] pdfPoints, float[] pressures, int color, float maxWidth);

    void onPenEraseInk(int pageIndex, PdfDocument.InkAnnotation ink);

    void onPenEraseHighlight(PDFView.HighlightGroup group);
}
