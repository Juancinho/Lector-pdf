package com.github.barteksc.pdfviewer;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

import com.shockwave.pdfium.PdfDocument;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Draws MJ Study /Ink annotations as vector overlays. Outlines are built once
 * per annotation in PDF user space and drawn through the page's affine
 * transform, so zooming and scrolling never rebuild geometry.
 */
final class InkOverlayRenderer {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final float[] affine = new float[6];
    private final float[] matrixValues = new float[9];
    private final Map<PdfDocument.InkAnnotation, Path> pathCache = new WeakHashMap<>();

    InkOverlayRenderer() {
        paint.setStyle(Paint.Style.FILL);
    }

    void draw(Canvas canvas, PdfFile pdfFile, Set<Integer> pages, float zoom, boolean nightMode,
              PDFView view) {
        for (Integer page : pages) {
            if (page == null || page < 0 || page >= pdfFile.getPagesCount()) {
                continue;
            }
            List<PdfDocument.InkAnnotation> inks = pdfFile.peekInkAnnotations(page);
            if (inks == null) {
                view.requestPrewarm(page);
                continue;
            }
            if (inks.isEmpty() || !applyPageMatrix(pdfFile, page, zoom)) {
                continue;
            }
            canvas.save();
            canvas.concat(matrix);
            for (PdfDocument.InkAnnotation ink : inks) {
                Path path = pathCache.get(ink);
                if (path == null) {
                    path = buildPath(ink);
                    pathCache.put(ink, path);
                }
                paint.setColor(displayColor(ink.getColor(), nightMode));
                canvas.drawPath(path, paint);
            }
            canvas.restore();
        }
    }

    boolean applyPageMatrix(PdfFile pdfFile, int page, float zoom) {
        if (!pdfFile.pdfToDocumentAffine(page, zoom, affine)) {
            return false;
        }
        matrixValues[0] = affine[0];
        matrixValues[1] = affine[2];
        matrixValues[2] = affine[4];
        matrixValues[3] = affine[1];
        matrixValues[4] = affine[3];
        matrixValues[5] = affine[5];
        matrixValues[6] = 0f;
        matrixValues[7] = 0f;
        matrixValues[8] = 1f;
        matrix.setValues(matrixValues);
        return true;
    }

    Matrix currentMatrix() {
        return matrix;
    }

    /** Document pixels per PDF point for the last applied page matrix. */
    float currentScale() {
        return (float) Math.hypot(affine[0], affine[1]);
    }

    static Path buildPath(PdfDocument.InkAnnotation ink) {
        float[][] strokes = ink.getStrokes();
        float[][] pressures = InkGeometry.decodePressure(ink.getPressure(), strokes);
        Path path = new Path();
        path.setFillType(Path.FillType.WINDING);
        for (int s = 0; s < strokes.length; s++) {
            appendPolygon(path, InkGeometry.outline(strokes[s],
                    pressures == null ? null : pressures[s], ink.getStrokeWidth()));
        }
        return path;
    }

    static void appendPolygon(Path path, float[] polygon) {
        if (polygon.length < 6) {
            return;
        }
        path.moveTo(polygon[0], polygon[1]);
        for (int i = 2; i + 1 < polygon.length; i += 2) {
            path.lineTo(polygon[i], polygon[i + 1]);
        }
        path.close();
    }

    /** Night mode inverts page colours, so ink is inverted the same way to stay visible. */
    static int displayColor(int argb, boolean nightMode) {
        if (!nightMode) {
            return argb;
        }
        return (argb & 0xFF000000) | (~argb & 0x00FFFFFF);
    }
}
