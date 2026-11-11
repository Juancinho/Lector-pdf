package com.github.barteksc.pdfviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure geometry for pressure-sensitive ink. Points are flat x,y pairs in PDF user
 * space. A stroke keeps its centreline (written to the standard /InkList) and a
 * per-point pressure in [0, 1]; the visible shape is a filled outline whose
 * half-width follows the pressure. The same outline is used for the on-screen
 * overlay and for the annotation appearance stream, so what other readers show
 * matches what was drawn here.
 */
public final class InkGeometry {

    /** Pressure used when the input device reports none (mouse, finger, old data). */
    public static final float DEFAULT_PRESSURE = 0.55f;
    /** Fraction of the maximum width reached at zero pressure. */
    static final float MIN_WIDTH_FRACTION = 0.28f;
    static final int CAP_SEGMENTS = 6;
    private static final float EPSILON = 1e-4f;

    private InkGeometry() {
    }

    /** Width at a given pressure for a pen whose maximum width is {@code maxWidth}. */
    public static float widthForPressure(float pressure, float maxWidth) {
        float p = clamp01(pressure);
        return maxWidth * (MIN_WIDTH_FRACTION + (1f - MIN_WIDTH_FRACTION) * p);
    }

    /** Nominal /Border width for readers that ignore the appearance stream. */
    public static float nominalWidth(float maxWidth) {
        return widthForPressure(DEFAULT_PRESSURE, maxWidth);
    }

    public static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return DEFAULT_PRESSURE;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * Builds one closed outline polygon (flat x,y pairs) around a stroke. Returns
     * an empty array for invalid input. {@code pressures} may be null or shorter
     * than the stroke, in which case missing values use {@link #DEFAULT_PRESSURE}.
     */
    public static float[] outline(float[] xy, float[] pressures, float maxWidth) {
        if (xy == null || xy.length < 2 || maxWidth <= 0f) {
            return new float[0];
        }
        int n = xy.length / 2;
        float[] radius = new float[n];
        for (int i = 0; i < n; i++) {
            float p = pressures != null && i < pressures.length ? pressures[i] : DEFAULT_PRESSURE;
            radius[i] = widthForPressure(p, maxWidth) * 0.5f;
        }
        if (n == 1 || isSinglePoint(xy)) {
            return circle(xy[0], xy[1], maxRadius(radius));
        }

        float[] tx = new float[n];
        float[] ty = new float[n];
        float lastX = 1f;
        float lastY = 0f;
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1);
            int b = Math.min(n - 1, i + 1);
            float dx = xy[2 * b] - xy[2 * a];
            float dy = xy[2 * b + 1] - xy[2 * a + 1];
            float length = (float) Math.hypot(dx, dy);
            if (length > EPSILON) {
                lastX = dx / length;
                lastY = dy / length;
            }
            tx[i] = lastX;
            ty[i] = lastY;
        }
        // Back-fill leading points that had no direction yet.
        for (int i = n - 2; i >= 0; i--) {
            float dx = xy[2 * Math.min(n - 1, i + 1)] - xy[2 * Math.max(0, i - 1)];
            float dy = xy[2 * Math.min(n - 1, i + 1) + 1] - xy[2 * Math.max(0, i - 1) + 1];
            if (Math.hypot(dx, dy) <= EPSILON) {
                tx[i] = tx[i + 1];
                ty[i] = ty[i + 1];
            }
        }

        List<Float> out = new ArrayList<>(n * 4 + CAP_SEGMENTS * 4 + 8);
        for (int i = 0; i < n; i++) {
            float nx = -ty[i];
            float ny = tx[i];
            out.add(xy[2 * i] + nx * radius[i]);
            out.add(xy[2 * i + 1] + ny * radius[i]);
        }
        appendCap(out, xy[2 * (n - 1)], xy[2 * (n - 1) + 1], -ty[n - 1], tx[n - 1], tx[n - 1], ty[n - 1],
                radius[n - 1]);
        for (int i = n - 1; i >= 0; i--) {
            float nx = -ty[i];
            float ny = tx[i];
            out.add(xy[2 * i] - nx * radius[i]);
            out.add(xy[2 * i + 1] - ny * radius[i]);
        }
        appendCap(out, xy[0], xy[1], ty[0], -tx[0], -tx[0], -ty[0], radius[0]);
        float[] result = new float[out.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = out.get(i);
        }
        return result;
    }

    /**
     * Semicircle from {@code start} direction through {@code through} direction to
     * minus {@code start}, excluding both end points (they are the side points).
     */
    private static void appendCap(List<Float> out, float cx, float cy, float sx, float sy,
                                  float txDir, float tyDir, float r) {
        for (int k = 1; k < CAP_SEGMENTS; k++) {
            double theta = Math.PI * k / CAP_SEGMENTS;
            float c = (float) Math.cos(theta);
            float s = (float) Math.sin(theta);
            out.add(cx + r * (sx * c + txDir * s));
            out.add(cy + r * (sy * c + tyDir * s));
        }
    }

    private static boolean isSinglePoint(float[] xy) {
        for (int i = 2; i + 1 < xy.length; i += 2) {
            if (Math.abs(xy[i] - xy[0]) > EPSILON || Math.abs(xy[i + 1] - xy[1]) > EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static float maxRadius(float[] radius) {
        float max = 0f;
        for (float r : radius) {
            max = Math.max(max, r);
        }
        return max;
    }

    static float[] circle(float cx, float cy, float r) {
        int segments = CAP_SEGMENTS * 2;
        float[] out = new float[segments * 2];
        for (int k = 0; k < segments; k++) {
            double theta = 2 * Math.PI * k / segments;
            out[2 * k] = cx + r * (float) Math.cos(theta);
            out[2 * k + 1] = cy + r * (float) Math.sin(theta);
        }
        return out;
    }

    /**
     * PDF content stream for an /AP normal appearance: every stroke outline is one
     * subpath of a single non-zero fill, so translucent ink never double-darkens
     * where the outline overlaps itself. {@code /GS} is the ExtGState PDFium adds
     * to the appearance resources when the annotation opacity (/CA) is below 1.
     */
    public static String appearanceStream(float[][] strokes, float[][] pressures, int argb, float maxWidth) {
        StringBuilder out = new StringBuilder();
        out.append("q\n");
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha < 255) {
            out.append("/GS gs\n");
        }
        out.append(number(((argb >> 16) & 0xFF) / 255f)).append(' ')
                .append(number(((argb >> 8) & 0xFF) / 255f)).append(' ')
                .append(number((argb & 0xFF) / 255f)).append(" rg\n");
        boolean any = false;
        for (int s = 0; s < strokes.length; s++) {
            float[] pressure = pressures != null && s < pressures.length ? pressures[s] : null;
            float[] polygon = outline(strokes[s], pressure, maxWidth);
            if (polygon.length < 6) {
                continue;
            }
            any = true;
            out.append(number(polygon[0])).append(' ').append(number(polygon[1])).append(" m\n");
            for (int i = 2; i + 1 < polygon.length; i += 2) {
                out.append(number(polygon[i])).append(' ').append(number(polygon[i + 1])).append(" l\n");
            }
            out.append("h\n");
        }
        if (any) {
            out.append("f\n");
        }
        out.append("Q\n");
        return out.toString();
    }

    /** Encodes per-point pressures as "p p p;p p" with two decimals. */
    public static String encodePressure(float[][] pressures) {
        if (pressures == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int s = 0; s < pressures.length; s++) {
            if (s > 0) {
                out.append(';');
            }
            float[] stroke = pressures[s];
            if (stroke == null) {
                continue;
            }
            for (int i = 0; i < stroke.length; i++) {
                if (i > 0) {
                    out.append(' ');
                }
                out.append(String.format(Locale.ROOT, "%.2f", clamp01(stroke[i])));
            }
        }
        return out.toString();
    }

    /**
     * Decodes {@link #encodePressure} output. Returns null when the sidecar is
     * missing or does not match the stroke point counts, so callers fall back to
     * uniform width instead of trusting stale data.
     */
    public static float[][] decodePressure(String encoded, float[][] strokes) {
        if (encoded == null || encoded.isEmpty() || strokes == null) {
            return null;
        }
        String[] parts = encoded.split(";", -1);
        if (parts.length != strokes.length) {
            return null;
        }
        float[][] result = new float[strokes.length][];
        for (int s = 0; s < strokes.length; s++) {
            int expected = strokes[s] == null ? 0 : strokes[s].length / 2;
            String part = parts[s].trim();
            String[] values = part.isEmpty() ? new String[0] : part.split(" ");
            if (values.length != expected) {
                return null;
            }
            float[] stroke = new float[expected];
            for (int i = 0; i < expected; i++) {
                try {
                    stroke[i] = clamp01(Float.parseFloat(values[i]));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            result[s] = stroke;
        }
        return result;
    }

    /** Minimum distance from (px, py) to the stroke centreline, or +infinity. */
    public static float distanceToStroke(float[] xy, float px, float py) {
        if (xy == null || xy.length < 2) {
            return Float.POSITIVE_INFINITY;
        }
        if (xy.length < 4) {
            return (float) Math.hypot(px - xy[0], py - xy[1]);
        }
        float best = Float.POSITIVE_INFINITY;
        for (int i = 0; i + 3 < xy.length; i += 2) {
            best = Math.min(best, distanceToSegment(px, py, xy[i], xy[i + 1], xy[i + 2], xy[i + 3]));
        }
        return best;
    }

    static float distanceToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float lengthSquared = dx * dx + dy * dy;
        float t = lengthSquared <= EPSILON ? 0f : ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
        t = Math.max(0f, Math.min(1f, t));
        return (float) Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    private static String number(float value) {
        String formatted = String.format(Locale.ROOT, "%.3f", value);
        // Trim trailing zeros to keep appearance streams compact.
        int end = formatted.length();
        while (end > 0 && formatted.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && formatted.charAt(end - 1) == '.') {
            end--;
        }
        String trimmed = formatted.substring(0, end);
        return trimmed.isEmpty() || "-".equals(trimmed) || "-0".equals(trimmed) ? "0" : trimmed;
    }
}
