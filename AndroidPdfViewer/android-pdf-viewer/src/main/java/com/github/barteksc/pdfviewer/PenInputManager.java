package com.github.barteksc.pdfviewer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewParent;

import com.shockwave.pdfium.PdfDocument;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Routes stylus input to study tools. Fingers keep scrolling and zooming; the
 * pen highlights text, writes pressure-sensitive ink or erases, depending on the
 * selected tool and the pen buttons. While a writing tool is active, finger
 * touches that start while the pen is down or hovering, or shortly after it
 * lifts, are ignored as a resting palm. All of this is a software policy over
 * the MotionEvents Android delivers; it is not verified on Xiaomi hardware.
 */
final class PenInputManager {

    private static final int MAX_STROKE_POINTS = 4000;
    private static final float MIN_SAMPLE_DISTANCE_PX = 0.75f;
    private static final float ERASER_RADIUS_DP = 14f;
    private static final float HOVER_DOT_MIN_DP = 3f;

    private final PDFView pdfView;
    private final float density;
    private final Paint livePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path livePath = new Path();
    private final InkOverlayRenderer liveTransform = new InkOverlayRenderer();
    private final Set<String> erasedGroups = new HashSet<>();

    private OnPenInputListener listener;
    private PenTool selectedTool = PenTool.NONE;
    private PenTool lastWritingTool = PenTool.PEN;
    private boolean stylusOnly = true;
    private boolean penAlwaysWrites;
    private int penColor = 0xFF1F2A44;
    private int markerColor = 0x66FFD54F;
    private int highlighterColor = 0x80FFD54F;
    private float penWidth = 1.6f;
    private float markerWidth = 9f;

    // Gesture state.
    private PenTool gestureTool;
    private int activePointerId = -1;
    private boolean gestureIsStylus;
    private boolean sweepStarted;
    private boolean ignoringPalmGesture;
    private boolean navigationCancelRequested;
    private int strokePage = -1;
    private float[] strokePoints = new float[256];
    private float[] strokePressures = new float[128];
    private int strokeCount;
    private float lastViewX;
    private float lastViewY;
    private float smoothedPressure;
    private float pressureScale = 1f;
    private boolean liveDirty;
    private long lastPenUpMs;

    // Hover state.
    private boolean penHovering;
    private long lastHoverMs;
    private boolean hoverVisible;
    private float hoverX;
    private float hoverY;

    PenInputManager(PDFView pdfView) {
        this.pdfView = pdfView;
        this.density = pdfView.getResources().getDisplayMetrics().density;
        livePaint.setStyle(Paint.Style.FILL);
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(1.5f * density);
    }

    void setListener(OnPenInputListener listener) {
        this.listener = listener;
    }

    void setSelectedTool(PenTool tool) {
        PenTool next = tool == null ? PenTool.NONE : tool;
        if (gestureTool != null && next != selectedTool) {
            cancelGesture();
        }
        selectedTool = next;
        if (next != PenTool.NONE && next != PenTool.ERASER) {
            lastWritingTool = next;
        }
        hoverVisible = false;
        pdfView.invalidate();
    }

    PenTool getSelectedTool() {
        return selectedTool;
    }

    void setStylusOnly(boolean stylusOnly) {
        this.stylusOnly = stylusOnly;
    }

    void setPenAlwaysWrites(boolean penAlwaysWrites) {
        this.penAlwaysWrites = penAlwaysWrites;
    }

    void setStyle(int penColor, float penWidth, int markerColor, float markerWidth, int highlighterColor) {
        this.penColor = penColor;
        this.penWidth = Math.max(0.2f, penWidth);
        this.markerColor = markerColor;
        this.markerWidth = Math.max(0.5f, markerWidth);
        this.highlighterColor = highlighterColor;
    }

    boolean isGestureActive() {
        return gestureTool != null;
    }

    /** True once after the pen took over a gesture that navigation had already started. */
    boolean consumeNavigationCancel() {
        boolean requested = navigationCancelRequested;
        navigationCancelRequested = false;
        return requested;
    }

    private boolean writingActive() {
        return selectedTool != PenTool.NONE || penAlwaysWrites;
    }

    boolean handleTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (gestureTool != null) {
            return continueGesture(event, action);
        }
        if (ignoringPalmGesture) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                ignoringPalmGesture = false;
                return true;
            }
            boolean penJoins = action == MotionEvent.ACTION_POINTER_DOWN
                    && PenInputPolicy.isPenTool(event.getToolType(event.getActionIndex()));
            if (!penJoins) {
                return true;
            }
            // The pen landed while the palm rests: write, navigation never started.
            PenTool tool = PenInputPolicy.resolveTool(selectedTool, event.getToolType(event.getActionIndex()),
                    event.getButtonState(), penAlwaysWrites, lastWritingTool);
            if (tool != PenTool.NONE) {
                startGesture(tool, event, event.getActionIndex(), true);
            }
            return true;
        }
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN) {
            return false;
        }
        int index = event.getActionIndex();
        int toolType = event.getToolType(index);
        if (PenInputPolicy.isPenTool(toolType)) {
            PenTool tool = PenInputPolicy.resolveTool(selectedTool, toolType, event.getButtonState(),
                    penAlwaysWrites, lastWritingTool);
            if (tool == PenTool.NONE) {
                return false;
            }
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                navigationCancelRequested = true;
            }
            startGesture(tool, event, index, true);
            return true;
        }
        if (action != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (!stylusOnly && selectedTool != PenTool.NONE) {
            startGesture(selectedTool, event, index, false);
            return true;
        }
        long now = SystemClock.uptimeMillis();
        boolean hoverFresh = penHovering && now - lastHoverMs < PenInputPolicy.HOVER_STALE_MS;
        if (PenInputPolicy.isLikelyPalm(writingActive(), hoverFresh, now, lastPenUpMs)) {
            ignoringPalmGesture = true;
            return true;
        }
        return false;
    }

    boolean handleHover(MotionEvent event) {
        if (!PenInputPolicy.isPenTool(event.getToolType(0))) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                penHovering = true;
                lastHoverMs = SystemClock.uptimeMillis();
                hoverX = event.getX();
                hoverY = event.getY();
                PenTool tool = PenInputPolicy.resolveTool(selectedTool, event.getToolType(0),
                        event.getButtonState(), penAlwaysWrites, lastWritingTool);
                boolean show = tool == PenTool.ERASER || tool.isInk();
                if (show || hoverVisible) {
                    hoverVisible = show;
                    pdfView.invalidate();
                }
                return show;
            case MotionEvent.ACTION_HOVER_EXIT:
                penHovering = false;
                lastPenUpMs = SystemClock.uptimeMillis();
                if (hoverVisible) {
                    hoverVisible = false;
                    pdfView.invalidate();
                }
                return false;
            default:
                return false;
        }
    }

    private void startGesture(PenTool tool, MotionEvent event, int index, boolean stylus) {
        gestureTool = tool;
        gestureIsStylus = stylus;
        activePointerId = event.getPointerId(index);
        erasedGroups.clear();
        hoverVisible = false;
        pressureScale = pressureScaleFor(event);
        ViewParent parent = pdfView.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
        // Deliver pen samples as soon as they arrive instead of once per frame.
        pdfView.requestUnbufferedDispatch(event);
        if (listener != null) {
            listener.onPenGestureStarted(tool);
        }
        float x = event.getX(index);
        float y = event.getY(index);
        switch (tool) {
            case HIGHLIGHTER:
                TextSelectionManager selection = pdfView.getTextSelectionManager();
                if (selection != null) {
                    selection.setSweepColor(highlighterColor);
                    sweepStarted = selection.beginSweep(x, y);
                }
                break;
            case ERASER:
                eraseAt(x, y);
                break;
            case PEN:
            case MARKER:
                beginStroke(x, y, pressureAt(event, index, -1));
                break;
            default:
                break;
        }
    }

    private boolean continueGesture(MotionEvent event, int action) {
        int index = event.findPointerIndex(activePointerId);
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (index >= 0) {
                    int history = event.getHistorySize();
                    for (int h = 0; h < history; h++) {
                        onSample(event.getHistoricalX(index, h), event.getHistoricalY(index, h),
                                pressureAt(event, index, h));
                    }
                    onSample(event.getX(index), event.getY(index), pressureAt(event, index, -1));
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == activePointerId) {
                    finishGesture();
                    // Remaining pointers (usually the palm) never started a
                    // navigation gesture; keep ignoring them until they lift.
                    ignoringPalmGesture = true;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (index >= 0) {
                    onSample(event.getX(index), event.getY(index), pressureAt(event, index, -1));
                }
                finishGesture();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelGesture();
                return true;
            default:
                // Other pointers (usually the palm) are ignored for the whole pen gesture.
                return true;
        }
    }

    private void onSample(float x, float y, float pressure) {
        if (gestureTool == null) {
            return;
        }
        switch (gestureTool) {
            case HIGHLIGHTER:
                TextSelectionManager selection = pdfView.getTextSelectionManager();
                if (selection == null) {
                    return;
                }
                if (!sweepStarted) {
                    selection.setSweepColor(highlighterColor);
                    sweepStarted = selection.beginSweep(x, y);
                } else {
                    selection.extendSweep(x, y);
                }
                break;
            case ERASER:
                eraseAt(x, y);
                break;
            case PEN:
            case MARKER:
                appendStrokePoint(x, y, pressure);
                break;
            default:
                break;
        }
    }

    private void finishGesture() {
        PenTool tool = gestureTool;
        gestureTool = null;
        activePointerId = -1;
        if (gestureIsStylus) {
            lastPenUpMs = SystemClock.uptimeMillis();
        }
        if (tool == PenTool.HIGHLIGHTER) {
            TextSelectionManager selection = pdfView.getTextSelectionManager();
            PDFView.HighlightRequest request = selection == null ? null : selection.finishSweep();
            sweepStarted = false;
            if (request != null && listener != null) {
                listener.onPenHighlight(request, highlighterColor);
            }
        } else if (tool != null && tool.isInk()) {
            commitStroke(tool);
        }
        erasedGroups.clear();
        pdfView.invalidate();
    }

    void cancelGesture() {
        if (gestureTool == PenTool.HIGHLIGHTER) {
            TextSelectionManager selection = pdfView.getTextSelectionManager();
            if (selection != null) {
                selection.cancelSweep();
            }
        }
        gestureTool = null;
        activePointerId = -1;
        sweepStarted = false;
        strokeCount = 0;
        strokePage = -1;
        erasedGroups.clear();
        pdfView.invalidate();
    }

    // ---- Ink ----------------------------------------------------------------

    private void beginStroke(float viewX, float viewY, float pressure) {
        strokeCount = 0;
        strokePage = pageAt(viewX, viewY);
        if (strokePage < 0) {
            return;
        }
        smoothedPressure = PenInputPolicy.smoothPressure(0f, pressure, true);
        addPoint(viewX, viewY, smoothedPressure);
    }

    private void appendStrokePoint(float viewX, float viewY, float pressure) {
        if (strokePage < 0) {
            beginStroke(viewX, viewY, pressure);
            return;
        }
        smoothedPressure = PenInputPolicy.smoothPressure(smoothedPressure, pressure, false);
        if (Math.hypot(viewX - lastViewX, viewY - lastViewY) < MIN_SAMPLE_DISTANCE_PX) {
            // Keep the latest pressure on a stationary pen without adding a point.
            if (strokeCount > 0) {
                strokePressures[strokeCount - 1] = Math.max(strokePressures[strokeCount - 1], smoothedPressure);
                liveDirty = true;
            }
            return;
        }
        if (strokeCount >= MAX_STROKE_POINTS) {
            // Very long strokes are split so annotation and appearance sizes stay bounded.
            PenTool tool = gestureTool;
            commitStroke(tool);
            beginStroke(viewX, viewY, pressure);
            return;
        }
        addPoint(viewX, viewY, smoothedPressure);
    }

    private void addPoint(float viewX, float viewY, float pressure) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return;
        }
        float docX = viewX - pdfView.getCurrentXOffset();
        float docY = viewY - pdfView.getCurrentYOffset();
        float[] user = pdfFile.documentToPdfValues(strokePage, pdfView.getZoom(), docX, docY);
        if (strokePoints.length < (strokeCount + 1) * 2) {
            strokePoints = Arrays.copyOf(strokePoints, strokePoints.length * 2);
            strokePressures = Arrays.copyOf(strokePressures, strokePressures.length * 2);
        }
        strokePoints[strokeCount * 2] = user[0];
        strokePoints[strokeCount * 2 + 1] = user[1];
        strokePressures[strokeCount] = pressure;
        strokeCount++;
        lastViewX = viewX;
        lastViewY = viewY;
        liveDirty = true;
        pdfView.invalidate();
    }

    private void commitStroke(PenTool tool) {
        int page = strokePage;
        int count = strokeCount;
        if (page >= 0 && count > 0 && listener != null) {
            float[] points = Arrays.copyOf(strokePoints, count * 2);
            float[] pressures = Arrays.copyOf(strokePressures, count);
            listener.onPenStroke(page, points, pressures, colorFor(tool), widthFor(tool));
        }
        strokeCount = 0;
        strokePage = -1;
        livePath.rewind();
        liveDirty = false;
    }

    private int colorFor(PenTool tool) {
        return tool == PenTool.MARKER ? markerColor : penColor;
    }

    private float widthFor(PenTool tool) {
        return tool == PenTool.MARKER ? markerWidth : penWidth;
    }

    private float pressureAt(MotionEvent event, int index, int history) {
        if (!gestureIsStylus && gestureTool != null) {
            return InkGeometry.DEFAULT_PRESSURE;
        }
        if (event.getToolType(index) != MotionEvent.TOOL_TYPE_STYLUS
                && event.getToolType(index) != MotionEvent.TOOL_TYPE_ERASER) {
            return InkGeometry.DEFAULT_PRESSURE;
        }
        float raw = history < 0 ? event.getPressure(index) : event.getHistoricalPressure(index, history);
        return InkGeometry.clamp01(raw * pressureScale);
    }

    private static float pressureScaleFor(MotionEvent event) {
        InputDevice device = event.getDevice();
        if (device == null) {
            return 1f;
        }
        InputDevice.MotionRange range = device.getMotionRange(MotionEvent.AXIS_PRESSURE, event.getSource());
        if (range == null || range.getMax() <= 0f) {
            return 1f;
        }
        return 1f / range.getMax();
    }

    // ---- Eraser -------------------------------------------------------------

    private void eraseAt(float viewX, float viewY) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null || listener == null) {
            return;
        }
        int page = pageAt(viewX, viewY);
        if (page < 0) {
            return;
        }
        float docX = viewX - pdfView.getCurrentXOffset();
        float docY = viewY - pdfView.getCurrentYOffset();
        float[] user = pdfFile.documentToPdfValues(page, pdfView.getZoom(), docX, docY);
        float scale = liveTransform.applyPageMatrix(pdfFile, page, pdfView.getZoom())
                ? liveTransform.currentScale() : 1f;
        float radius = ERASER_RADIUS_DP * density / Math.max(0.01f, scale);
        List<PdfDocument.InkAnnotation> inks = pdfFile.peekInkAnnotations(page);
        if (inks != null) {
            for (PdfDocument.InkAnnotation ink : inks) {
                if (erasedGroups.contains(ink.getGroupKey())) {
                    continue;
                }
                float reach = radius + ink.getStrokeWidth() * 0.5f;
                for (float[] stroke : ink.getStrokes()) {
                    if (InkGeometry.distanceToStroke(stroke, user[0], user[1]) <= reach) {
                        erasedGroups.add(ink.getGroupKey());
                        listener.onPenEraseInk(page, ink);
                        break;
                    }
                }
            }
        }
        PDFView.HighlightAnnotation highlight = pdfView.findHighlightAnnotationAt(viewX, viewY);
        if (highlight != null && !erasedGroups.contains(highlight.groupKey)) {
            erasedGroups.add(highlight.groupKey);
            PDFView.HighlightGroup group = pdfView.getHighlightGroup(highlight.pageIndex, highlight.groupKey);
            if (group != null) {
                listener.onPenEraseHighlight(group);
            }
        }
        hoverX = viewX;
        hoverY = viewY;
        pdfView.invalidate();
    }

    private int pageAt(float viewX, float viewY) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return -1;
        }
        float docX = viewX - pdfView.getCurrentXOffset();
        float docY = viewY - pdfView.getCurrentYOffset();
        int page = pdfFile.getPageAtOffset(pdfView.isSwipeVertical() ? docY : docX,
                pdfView.isSwipeVertical() ? docX : docY, pdfView.getZoom());
        if (page < 0 || page >= pdfFile.getPagesCount()) {
            return -1;
        }
        pdfFile.ensurePageGeometry(page);
        return page;
    }

    // ---- Drawing (canvas is translated to document coordinates) -------------

    void draw(Canvas canvas, boolean nightMode) {
        PdfFile pdfFile = pdfView.pdfFile;
        if (pdfFile == null) {
            return;
        }
        if (gestureTool != null && gestureTool.isInk() && strokePage >= 0 && strokeCount > 0) {
            if (liveTransform.applyPageMatrix(pdfFile, strokePage, pdfView.getZoom())) {
                if (liveDirty) {
                    livePath.rewind();
                    livePath.setFillType(Path.FillType.WINDING);
                    InkOverlayRenderer.appendPolygon(livePath, InkGeometry.outline(
                            Arrays.copyOf(strokePoints, strokeCount * 2),
                            Arrays.copyOf(strokePressures, strokeCount),
                            widthFor(gestureTool)));
                    liveDirty = false;
                }
                livePaint.setColor(InkOverlayRenderer.displayColor(colorFor(gestureTool), nightMode));
                canvas.save();
                canvas.concat(liveTransform.currentMatrix());
                canvas.drawPath(livePath, livePaint);
                canvas.restore();
            }
        }
        boolean erasing = gestureTool == PenTool.ERASER;
        if (hoverVisible || erasing) {
            float x = hoverX - pdfView.getCurrentXOffset();
            float y = hoverY - pdfView.getCurrentYOffset();
            PenTool tool = erasing ? PenTool.ERASER
                    : (selectedTool == PenTool.NONE ? lastWritingTool : selectedTool);
            if (tool == PenTool.ERASER) {
                cursorPaint.setColor(nightMode ? 0xCCFFFFFF : 0xCC202321);
                canvas.drawCircle(x, y, ERASER_RADIUS_DP * density, cursorPaint);
            } else if (tool.isInk()) {
                int page = pageAt(hoverX, hoverY);
                float scale = page >= 0 && liveTransform.applyPageMatrix(pdfFile, page, pdfView.getZoom())
                        ? liveTransform.currentScale() : 1f;
                float radius = Math.max(HOVER_DOT_MIN_DP * density * 0.5f, widthFor(tool) * scale * 0.5f);
                cursorPaint.setColor(InkOverlayRenderer.displayColor(colorFor(tool) | 0xFF000000, nightMode));
                canvas.drawCircle(x, y, radius, cursorPaint);
            }
        }
    }
}
