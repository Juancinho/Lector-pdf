// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ink

import kotlin.math.*

enum class InkTool { PEN, PENCIL, HIGHLIGHTER, LINE, ARROW, RECTANGLE, ELLIPSE, ERASER, LASSO }

data class InkPoint(val x: Float, val y: Float, val pressure: Float = 1f) {
    init { require(x.isFinite() && y.isFinite() && abs(x) <= 1_000_000 && abs(y) <= 1_000_000); require(pressure.isFinite() && pressure in 0f..1f) }
}

/** Immutable PDF user-space geometry. Standard /InkList and appearance are the authoritative export. */
data class InkStroke(
    val id: String,
    val page: Int,
    val points: List<InkPoint>,
    val color: Int,
    val width: Float,
    val tool: InkTool = InkTool.PEN,
    val pressureEnabled: Boolean = true,
) {
    init {
        require(id.isNotBlank() && id.length <= 100 && page >= 0)
        require(points.isNotEmpty() && points.size <= MAX_POINTS)
        require(width.isFinite() && width in 0.1f..256f)
        require(tool != InkTool.ERASER && tool != InkTool.LASSO)
    }
    fun nativePoints(): FloatArray = FloatArray(points.size * 3) { index ->
        val p = points[index / 3]
        when (index % 3) { 0 -> p.x; 1 -> p.y; else -> if (pressureEnabled) p.pressure else 1f }
    }
    fun transformed(dx: Float = 0f, dy: Float = 0f, scale: Float = 1f, originX: Float = 0f, originY: Float = 0f) = copy(
        points = points.map { it.copy(x = originX + (it.x - originX) * scale + dx, y = originY + (it.y - originY) * scale + dy) },
        width = (width * scale).coerceIn(0.1f, 256f),
    )
    companion object {
        const val MAX_POINTS = 4096
        fun pressure(raw: Float, min: Float = 0f, max: Float = 1f): Float {
            if (!raw.isFinite()) return 0.5f
            return (if (min.isFinite() && max.isFinite() && max > min) (raw - min) / (max - min) else raw).coerceIn(0.05f, 1f)
        }
        fun shape(tool: InkTool, start: InkPoint, end: InkPoint): List<InkPoint> = when (tool) {
            InkTool.LINE -> listOf(start, end)
            InkTool.ARROW -> {
                val angle = atan2(end.y - start.y, end.x - start.x)
                val size = (hypot(end.x - start.x, end.y - start.y) * 0.18f).coerceIn(3f, 24f)
                val left = InkPoint(end.x - cos(angle - 0.55f) * size, end.y - sin(angle - 0.55f) * size)
                val right = InkPoint(end.x - cos(angle + 0.55f) * size, end.y - sin(angle + 0.55f) * size)
                listOf(start, end, left, end, right)
            }
            InkTool.RECTANGLE -> listOf(start, InkPoint(end.x, start.y), end, InkPoint(start.x, end.y), start)
            InkTool.ELLIPSE -> {
                val cx = (start.x + end.x) / 2; val cy = (start.y + end.y) / 2
                val rx = abs(end.x - start.x) / 2; val ry = abs(end.y - start.y) / 2
                (0..64).map { val a = 2 * PI * it / 64; InkPoint(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat()) }
            }
            else -> listOf(start, end)
        }
    }
}

object InkGeometry {
    fun distance(point: InkPoint, a: InkPoint, b: InkPoint): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val length = dx * dx + dy * dy
        if (length < 1e-12f) return hypot(point.x - a.x, point.y - a.y)
        val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) / length).coerceIn(0f, 1f)
        return hypot(point.x - a.x - dx * t, point.y - a.y - dy * t)
    }
    fun hits(stroke: InkStroke, point: InkPoint, radius: Float): Boolean {
        val reach = radius + stroke.width / 2
        val points = stroke.points
        if (points.size == 1) return distance(point, points[0], points[0]) <= reach
        // Erasing runs for every pen sample: avoid allocating a Pair/list per segment.
        for (index in 1 until points.size) {
            if (distance(point, points[index - 1], points[index]) <= reach) return true
        }
        return false
    }

    fun inside(point: InkPoint, polygon: List<InkPoint>): Boolean {
        if (polygon.size < 3) return false
        var result = false; var previous = polygon.last()
        polygon.forEach { current ->
            if ((current.y > point.y) != (previous.y > point.y) &&
                point.x < (previous.x - current.x) * (point.y - current.y) / (previous.y - current.y) + current.x) result = !result
            previous = current
        }
        return result
    }
    fun selected(stroke: InkStroke, polygon: List<InkPoint>): Boolean = stroke.points.any { inside(it, polygon) }
}

/** Bounded command history, committed only after the native PDF mutation succeeds. */
data class InkChange(val before: List<InkStroke>, val after: List<InkStroke>) {
    init { require(before.isNotEmpty() || after.isNotEmpty()); require((before + after).map { it.page }.distinct().size == 1) }
    fun inverse() = InkChange(after, before)
}
class InkHistory(private val maxCommands: Int = 100, private val maxPoints: Int = 200_000) {
    private val undo = ArrayDeque<InkChange>()
    private val redo = ArrayDeque<InkChange>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    fun record(change: InkChange) {
        undo.addLast(change); redo.clear()
        while (undo.size > maxCommands || undo.sumOf { c -> (c.before + c.after).sumOf { it.points.size } } > maxPoints) undo.removeFirst()
    }
    fun peekUndo() = undo.lastOrNull()?.inverse()
    fun peekRedo() = redo.lastOrNull()
    fun didUndo() { undo.removeLastOrNull()?.let(redo::addLast) }
    fun didRedo() { redo.removeLastOrNull()?.let(undo::addLast) }
    fun clear() { undo.clear(); redo.clear() }
}
