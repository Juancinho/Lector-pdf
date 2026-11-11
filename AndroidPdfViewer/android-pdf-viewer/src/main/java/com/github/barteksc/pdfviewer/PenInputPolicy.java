package com.github.barteksc.pdfviewer;

import android.view.MotionEvent;

/**
 * Pure routing decisions for pen input, kept free of View state so they can be
 * unit tested. Hardware behaviour (which buttons a Xiaomi pen reports, whether
 * HyperOS consumes them, pressure ranges, hover) must still be verified on the
 * device; these rules only describe what the app does with the events it gets.
 */
public final class PenInputPolicy {

    /** Finger touches within this window after the pen lifts are treated as palm. */
    public static final long PALM_GUARD_AFTER_PEN_MS = 400L;
    /**
     * Hover state older than this is ignored, so a missing HOVER_EXIT can never
     * leave finger navigation blocked.
     */
    public static final long HOVER_STALE_MS = 1500L;

    private PenInputPolicy() {
    }

    public static boolean isPenTool(int toolType) {
        return toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER;
    }

    /**
     * Tool used for one pen gesture.
     *
     * @param selected       tool chosen in the toolbar (NONE while reading)
     * @param toolType       MotionEvent tool type of the pointer
     * @param buttonState    MotionEvent button state at ACTION_DOWN
     * @param penAlwaysWrites whether the pen draws with {@code lastWritingTool} while reading
     * @param lastWritingTool last non-NONE tool used, for pen-always-writes
     */
    public static PenTool resolveTool(PenTool selected, int toolType, int buttonState,
                                      boolean penAlwaysWrites, PenTool lastWritingTool) {
        if (toolType == MotionEvent.TOOL_TYPE_ERASER) {
            return PenTool.ERASER;
        }
        boolean primary = (buttonState & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0;
        boolean secondary = (buttonState & MotionEvent.BUTTON_STYLUS_SECONDARY) != 0;
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS) {
            if (selected == PenTool.NONE) {
                // Reading: a pen button turns the pen into a quick highlighter.
                if (primary || secondary) {
                    return PenTool.HIGHLIGHTER;
                }
                if (penAlwaysWrites && lastWritingTool != null && lastWritingTool != PenTool.NONE) {
                    return lastWritingTool;
                }
                return PenTool.NONE;
            }
            if (primary) {
                return selected == PenTool.ERASER ? PenTool.PEN : PenTool.ERASER;
            }
            if (secondary) {
                return PenTool.HIGHLIGHTER;
            }
            return selected;
        }
        return selected;
    }

    /**
     * Whether a finger gesture starting now should be ignored as a resting palm.
     * Only applies while a writing tool is active; while reading, fingers always
     * navigate.
     */
    public static boolean isLikelyPalm(boolean writingActive, boolean penInContactOrHovering,
                                       long nowMs, long lastPenUpMs) {
        if (!writingActive) {
            return false;
        }
        if (penInContactOrHovering) {
            return true;
        }
        return lastPenUpMs > 0 && nowMs - lastPenUpMs >= 0 && nowMs - lastPenUpMs < PALM_GUARD_AFTER_PEN_MS;
    }

    /** Exponential smoothing of pressure; keeps the first sample as is. */
    public static float smoothPressure(float previous, float sample, boolean first) {
        float clamped = InkGeometry.clamp01(sample);
        if (first) {
            return clamped;
        }
        return previous + 0.35f * (clamped - previous);
    }
}
