// SPDX-License-Identifier: GPL-3.0-or-later
package com.github.barteksc.pdfviewer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InkGeometryTest {

    private static final float EPS = 1e-3f;

    @Test
    public void widthGrowsWithPressureAndNeverReachesZero() {
        assertEquals(10f * InkGeometry.MIN_WIDTH_FRACTION, InkGeometry.widthForPressure(0f, 10f), EPS);
        assertEquals(10f, InkGeometry.widthForPressure(1f, 10f), EPS);
        assertTrue(InkGeometry.widthForPressure(0.3f, 10f) < InkGeometry.widthForPressure(0.8f, 10f));
        assertEquals(10f, InkGeometry.widthForPressure(5f, 10f), EPS);
        assertEquals(InkGeometry.widthForPressure(InkGeometry.DEFAULT_PRESSURE, 4f),
                InkGeometry.widthForPressure(Float.NaN, 4f), EPS);
    }

    @Test
    public void horizontalStrokeOutlineFollowsPressureOnBothSides() {
        float[] xy = {0, 0, 10, 0, 20, 0};
        float[] pressures = {0f, 1f, 0f};
        float[] polygon = InkGeometry.outline(xy, pressures, 4f);
        // First point is the left side of the first sample; with direction +x the
        // normal is +y, so the side point sits at half of the zero-pressure width.
        assertEquals(0f, polygon[0], EPS);
        assertEquals(4f * InkGeometry.MIN_WIDTH_FRACTION / 2f, polygon[1], EPS);
        // Middle sample at full pressure is two points wide on each side.
        assertEquals(10f, polygon[2], EPS);
        assertEquals(2f, polygon[3], EPS);
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        for (int i = 0; i + 1 < polygon.length; i += 2) {
            minY = Math.min(minY, polygon[i + 1]);
            maxY = Math.max(maxY, polygon[i + 1]);
            maxX = Math.max(maxX, polygon[i]);
        }
        assertEquals(-2f, minY, EPS);
        assertEquals(2f, maxY, EPS);
        // The round end cap extends past the last centreline point.
        assertTrue(maxX > 20f);
        assertEquals(0, polygon.length % 2);
    }

    @Test
    public void singlePointBecomesADot() {
        float[] polygon = InkGeometry.outline(new float[] {5, 5}, new float[] {1f}, 2f);
        assertEquals(InkGeometry.CAP_SEGMENTS * 4, polygon.length);
        for (int i = 0; i + 1 < polygon.length; i += 2) {
            assertEquals(1f, Math.hypot(polygon[i] - 5, polygon[i + 1] - 5), EPS);
        }
    }

    @Test
    public void invalidInputProducesNoOutline() {
        assertEquals(0, InkGeometry.outline(null, null, 2f).length);
        assertEquals(0, InkGeometry.outline(new float[] {1}, null, 2f).length);
        assertEquals(0, InkGeometry.outline(new float[] {1, 2, 3, 4}, null, 0f).length);
    }

    @Test
    public void pressureRoundTripsAndRejectsMismatchedData() {
        float[][] strokes = {{0, 0, 1, 1, 2, 2}, {5, 5}};
        float[][] pressures = {{0.1f, 0.555f, 1f}, {0.42f}};
        String encoded = InkGeometry.encodePressure(pressures);
        assertEquals("0.10 0.56 1.00;0.42", encoded);
        float[][] decoded = InkGeometry.decodePressure(encoded, strokes);
        assertEquals(0.56f, decoded[0][1], EPS);
        assertEquals(0.42f, decoded[1][0], EPS);
        assertNull(InkGeometry.decodePressure("0.1 0.2;0.3", strokes));
        assertNull(InkGeometry.decodePressure("0.1 0.2 0.3", strokes));
        assertNull(InkGeometry.decodePressure("0.1 x 0.3;0.3", strokes));
        assertNull(InkGeometry.decodePressure("", strokes));
    }

    @Test
    public void appearanceStreamFillsOneOutlinePerStrokeAndUsesOpacityState() {
        float[][] strokes = {{0, 0, 10, 0}, {0, 10, 10, 10}};
        String opaque = InkGeometry.appearanceStream(strokes, null, 0xFFFF0000, 2f);
        assertTrue(opaque.startsWith("q\n"));
        assertFalse(opaque.contains("/GS gs"));
        assertTrue(opaque.contains("1 0 0 rg"));
        assertEquals(2, count(opaque, " m\n"));
        assertEquals(1, count(opaque, "\nf\n"));
        assertTrue(opaque.endsWith("Q\n"));

        String translucent = InkGeometry.appearanceStream(strokes, null, 0x6600FF00, 2f);
        assertTrue(translucent.contains("/GS gs"));
        assertTrue(translucent.contains("0 1 0 rg"));
    }

    @Test
    public void appearanceNumbersAreLocaleIndependent() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            String stream = InkGeometry.appearanceStream(new float[][] {{0.5f, 0.25f, 3.75f, 1.5f}}, null,
                    0xFF000000, 1f);
            assertFalse(stream.contains(","));
            assertEquals("0.50", InkGeometry.encodePressure(new float[][] {{0.5f}}));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    @Test
    public void distanceToStrokeMeasuresToTheNearestSegment() {
        float[] stroke = {0, 0, 10, 0, 10, 10};
        assertEquals(3f, InkGeometry.distanceToStroke(stroke, 5, 3), EPS);
        assertEquals(2f, InkGeometry.distanceToStroke(stroke, 12, 5), EPS);
        assertEquals(5f, InkGeometry.distanceToStroke(new float[] {0, 0}, 3, 4), EPS);
        assertEquals(Float.POSITIVE_INFINITY, InkGeometry.distanceToStroke(null, 0, 0), 0f);
    }

    private static int count(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + 1);
        }
        return count;
    }
}
