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

    /** Default mapping: primary (lower) button erases, secondary selects text. */
    public static PenTool resolveTool(PenTool selected, int toolType, int buttonState,
                                      boolean penAlwaysWrites, PenTool lastWritingTool) {
        return resolveTool(selected, toolType, buttonState, penAlwaysWrites, lastWritingTool,
                PenButtonAction.ERASER, PenButtonAction.SELECT_TEXT);
    }

    /**
     * Tool used for one pen gesture.
     *
     * @param selected        tool chosen in the toolbar (NONE while reading)
     * @param toolType        MotionEvent tool type of the pointer
     * @param buttonState     MotionEvent button state at ACTION_DOWN
     * @param penAlwaysWrites whether the pen draws with {@code lastWritingTool} while reading
     * @param lastWritingTool last writing tool used, for pen-always-writes
     * @param primaryAction   what the primary barrel button does while held
     * @param secondaryAction what the secondary barrel button does while held
     */
    public static PenTool resolveTool(PenTool selected, int toolType, int buttonState,
                                      boolean penAlwaysWrites, PenTool lastWritingTool,
                                      PenButtonAction primaryAction, PenButtonAction secondaryAction) {
        if (toolType == MotionEvent.TOOL_TYPE_ERASER) {
            return PenTool.ERASER;
        }
        if (toolType != MotionEvent.TOOL_TYPE_STYLUS) {
            return selected;
        }
        // A held button wins in every mode, so "hold to erase/select" behaves the
        // same while reading and while writing.
        if ((buttonState & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 && primaryAction != null
                && primaryAction.tool() != null) {
            return primaryAction.tool();
        }
        if ((buttonState & MotionEvent.BUTTON_STYLUS_SECONDARY) != 0 && secondaryAction != null
                && secondaryAction.tool() != null) {
            return secondaryAction.tool();
        }
        if (selected == PenTool.NONE && penAlwaysWrites && lastWritingTool != null
                && lastWritingTool != PenTool.NONE && lastWritingTool != PenTool.SELECT) {
            return lastWritingTool;
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
