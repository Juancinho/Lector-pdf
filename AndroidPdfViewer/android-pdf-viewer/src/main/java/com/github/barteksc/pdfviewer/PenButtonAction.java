package com.github.barteksc.pdfviewer;

/** What a pen barrel button does while it is held during a pen gesture. */
public enum PenButtonAction {
    /** The button is ignored; the selected tool (or reading) applies. */
    NONE,
    /** Erase MJ Study strokes and highlights. */
    ERASER,
    /** Sweep over text to create a highlight. */
    HIGHLIGHTER,
    /** Sweep over text to select it, then show the normal selection actions. */
    SELECT_TEXT;

    PenTool tool() {
        switch (this) {
            case ERASER:
                return PenTool.ERASER;
            case HIGHLIGHTER:
                return PenTool.HIGHLIGHTER;
            case SELECT_TEXT:
                return PenTool.SELECT;
            default:
                return null;
        }
    }
}
