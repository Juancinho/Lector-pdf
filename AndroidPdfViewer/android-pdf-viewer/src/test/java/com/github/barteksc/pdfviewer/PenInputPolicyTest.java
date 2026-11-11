// SPDX-License-Identifier: GPL-3.0-or-later
package com.github.barteksc.pdfviewer;

import android.view.MotionEvent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PenInputPolicyTest {

    private static final int STYLUS = MotionEvent.TOOL_TYPE_STYLUS;
    private static final int ERASER = MotionEvent.TOOL_TYPE_ERASER;
    private static final int FINGER = MotionEvent.TOOL_TYPE_FINGER;
    private static final int PRIMARY = MotionEvent.BUTTON_STYLUS_PRIMARY;
    private static final int SECONDARY = MotionEvent.BUTTON_STYLUS_SECONDARY;

    private static final PenButtonAction ERASE = PenButtonAction.ERASER;
    private static final PenButtonAction SELECT = PenButtonAction.SELECT_TEXT;
    private static final PenButtonAction HIGHLIGHT = PenButtonAction.HIGHLIGHTER;
    private static final PenButtonAction NONE = PenButtonAction.NONE;

    private static PenTool resolve(PenTool selected, int toolType, int buttons, boolean always,
                                   PenTool last, PenButtonAction primary, PenButtonAction secondary) {
        return PenInputPolicy.resolveTool(selected, toolType, buttons, always, last, primary, secondary);
    }

    @Test
    public void readingStylusNavigatesUnlessAButtonIsHeldOrPenAlwaysWrites() {
        assertEquals(PenTool.NONE, resolve(PenTool.NONE, STYLUS, 0, false, PenTool.PEN, ERASE, SELECT));
        assertEquals(PenTool.ERASER, resolve(PenTool.NONE, STYLUS, PRIMARY, false, PenTool.PEN, ERASE, SELECT));
        assertEquals(PenTool.SELECT, resolve(PenTool.NONE, STYLUS, SECONDARY, false, PenTool.PEN, ERASE, SELECT));
        assertEquals(PenTool.MARKER, resolve(PenTool.NONE, STYLUS, 0, true, PenTool.MARKER, ERASE, SELECT));
        assertEquals(PenTool.NONE, resolve(PenTool.NONE, STYLUS, 0, true, PenTool.NONE, ERASE, SELECT));
        assertEquals(PenTool.NONE, resolve(PenTool.NONE, STYLUS, 0, true, PenTool.SELECT, ERASE, SELECT));
    }

    @Test
    public void buttonsFollowTheConfiguredActionsInEveryMode() {
        assertEquals(PenTool.PEN, resolve(PenTool.PEN, STYLUS, 0, false, PenTool.PEN, ERASE, SELECT));
        assertEquals(PenTool.ERASER, resolve(PenTool.PEN, STYLUS, PRIMARY, false, PenTool.PEN, ERASE, SELECT));
        assertEquals(PenTool.SELECT, resolve(PenTool.MARKER, STYLUS, SECONDARY, false, PenTool.PEN, ERASE, SELECT));
        assertEquals(PenTool.HIGHLIGHTER, resolve(PenTool.PEN, STYLUS, PRIMARY, false, PenTool.PEN, HIGHLIGHT, SELECT));
        assertEquals(PenTool.ERASER, resolve(PenTool.ERASER, STYLUS, PRIMARY, false, PenTool.PEN, ERASE, SELECT));
        // A button set to "nothing" leaves the selected tool (or reading) in place.
        assertEquals(PenTool.PEN, resolve(PenTool.PEN, STYLUS, PRIMARY, false, PenTool.PEN, NONE, SELECT));
        assertEquals(PenTool.NONE, resolve(PenTool.NONE, STYLUS, PRIMARY, false, PenTool.PEN, NONE, NONE));
        // Primary wins when both are reported.
        assertEquals(PenTool.ERASER,
                resolve(PenTool.PEN, STYLUS, PRIMARY | SECONDARY, false, PenTool.PEN, ERASE, SELECT));
        // The legacy overload uses erase/select defaults.
        assertEquals(PenTool.ERASER, PenInputPolicy.resolveTool(PenTool.PEN, STYLUS, PRIMARY, false, PenTool.PEN));
        assertEquals(PenTool.SELECT, PenInputPolicy.resolveTool(PenTool.PEN, STYLUS, SECONDARY, false, PenTool.PEN));
    }

    @Test
    public void eraserEndAlwaysErasesAndFingersKeepTheSelectedTool() {
        assertEquals(PenTool.ERASER, PenInputPolicy.resolveTool(PenTool.NONE, ERASER, 0, false, PenTool.PEN));
        assertEquals(PenTool.ERASER, PenInputPolicy.resolveTool(PenTool.PEN, ERASER, 0, false, PenTool.PEN));
        assertEquals(PenTool.MARKER, PenInputPolicy.resolveTool(PenTool.MARKER, FINGER, PRIMARY, false, PenTool.PEN));
        assertTrue(PenInputPolicy.isPenTool(STYLUS));
        assertTrue(PenInputPolicy.isPenTool(ERASER));
        assertFalse(PenInputPolicy.isPenTool(FINGER));
    }

    @Test
    public void palmGuardOnlyAppliesWhileWriting() {
        assertFalse(PenInputPolicy.isLikelyPalm(false, true, 1000, 900));
        assertTrue(PenInputPolicy.isLikelyPalm(true, true, 1000, 0));
        assertTrue(PenInputPolicy.isLikelyPalm(true, false, 1000, 1000 - PenInputPolicy.PALM_GUARD_AFTER_PEN_MS + 1));
        assertFalse(PenInputPolicy.isLikelyPalm(true, false, 1000, 1000 - PenInputPolicy.PALM_GUARD_AFTER_PEN_MS));
        assertFalse(PenInputPolicy.isLikelyPalm(true, false, 1000, 0));
    }

    @Test
    public void pressureSmoothingStartsAtTheSampleAndFollowsGradually() {
        assertEquals(0.8f, PenInputPolicy.smoothPressure(0f, 0.8f, true), 1e-4f);
        float next = PenInputPolicy.smoothPressure(0.2f, 1f, false);
        assertTrue(next > 0.2f && next < 1f);
        assertEquals(1f, PenInputPolicy.smoothPressure(0.2f, 4f, true), 1e-4f);
    }
}
