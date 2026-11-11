package com.github.barteksc.pdfviewer;

/** Active pen tool of the study toolbar. NONE keeps the normal reading gestures. */
public enum PenTool {
    NONE,
    /** Sweep over text to create a standard /Highlight annotation. */
    HIGHLIGHTER,
    /** Opaque pressure-sensitive /Ink. */
    PEN,
    /** Wide translucent pressure-sensitive /Ink. */
    MARKER,
    /** Removes MJ Study ink strokes and highlights touched by the pen. */
    ERASER;

    public boolean isInk() {
        return this == PEN || this == MARKER;
    }
}
