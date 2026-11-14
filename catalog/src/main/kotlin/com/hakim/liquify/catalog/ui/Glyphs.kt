package com.hakim.liquify.catalog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The glyph set from the reference material, drawn as thin outlines rather than pulled in from an
 * icon dependency — it keeps the catalog to `foundation` only and matches the hairline weight the
 * material is designed for.
 */
enum class Glyph {
    Circle,
    Triangle,
    Square,
    Hexagon,
    Heart,
    Share,
    Plus,
    Search,
    List,
    ChevronLeft,
    ChevronRight,
    ChevronDown,
    Check,
    Close,
    Play,
    Shuffle,
    Reset,
    Star,
}

/**
 * [glyph] as a composable of [size], drawn at [scale] of it — the form every control in the catalog
 * uses, so none of them spells out a `Canvas` of its own.
 */
@Composable
fun GlyphIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    color: Color = LocalCatalogColors.current.content,
    size: Dp = 22.dp,
    scale: Float = 1f,
) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, this.size.minDimension * scale, color) }
}

/**
 * Draws [glyph] centred in the current draw scope, scaled to [size] pixels.
 *
 * Corners are rounded via a join, so the triangle and hexagon get the same soft vertices the
 * reference icons have.
 */
fun DrawScope.drawGlyph(
    glyph: Glyph,
    size: Float,
    color: Color = Color.Black,
    strokeWidth: Float = size * 0.085f
) {
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val left = (this.size.width - size) / 2f
    val top = (this.size.height - size) / 2f

    translate(left, top) {
        when (glyph) {
            Glyph.Circle -> drawCircle(
                color = color,
                radius = size / 2f - strokeWidth / 2f,
                center = Offset(size / 2f, size / 2f),
                style = stroke
            )

            Glyph.Triangle -> drawPath(roundedPolygonPath(3, size, -90f), color, style = stroke)

            Glyph.Square -> drawPath(roundedPolygonPath(4, size, -45f), color, style = stroke)

            Glyph.Hexagon -> drawPath(roundedPolygonPath(6, size, -90f), color, style = stroke)

            Glyph.Heart -> drawPath(heartPath(size), color, style = stroke)

            Glyph.Share -> drawSharePath(size, color, stroke, strokeWidth)

            Glyph.Plus -> {
                val inset = size * 0.2f
                drawLine(color, Offset(inset, size / 2f), Offset(size - inset, size / 2f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size / 2f, inset), Offset(size / 2f, size - inset), strokeWidth, StrokeCap.Round)
            }

            Glyph.Search -> {
                val radius = size * 0.3f
                val center = Offset(size * 0.44f, size * 0.44f)
                drawCircle(color, radius, center, style = stroke)
                val start = center + Offset(radius * 0.72f, radius * 0.72f)
                drawLine(color, start, Offset(size * 0.86f, size * 0.86f), strokeWidth, StrokeCap.Round)
            }

            Glyph.List -> {
                val dotX = size * 0.22f
                val lineStart = size * 0.4f
                val lineEnd = size * 0.86f
                for (row in 0..2) {
                    val y = size * (0.27f + row * 0.23f)
                    drawCircle(color, strokeWidth * 0.8f, Offset(dotX, y))
                    drawLine(color, Offset(lineStart, y), Offset(lineEnd, y), strokeWidth, StrokeCap.Round)
                }
            }

            Glyph.ChevronLeft -> drawPath(polyline(size, 0.62f, 0.22f, 0.35f, 0.5f, 0.62f, 0.78f), color, style = stroke)

            Glyph.ChevronRight -> drawPath(polyline(size, 0.38f, 0.22f, 0.65f, 0.5f, 0.38f, 0.78f), color, style = stroke)

            Glyph.ChevronDown -> drawPath(polyline(size, 0.22f, 0.38f, 0.5f, 0.65f, 0.78f, 0.38f), color, style = stroke)

            Glyph.Check -> drawPath(polyline(size, 0.22f, 0.52f, 0.42f, 0.72f, 0.78f, 0.28f), color, style = stroke)

            Glyph.Close -> {
                val inset = size * 0.26f
                drawLine(color, Offset(inset, inset), Offset(size - inset, size - inset), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size - inset, inset), Offset(inset, size - inset), strokeWidth, StrokeCap.Round)
            }

            Glyph.Play -> {
                val path = Path().apply {
                    moveTo(size * 0.34f, size * 0.22f)
                    lineTo(size * 0.78f, size * 0.5f)
                    lineTo(size * 0.34f, size * 0.78f)
                    close()
                }
                drawPath(path, color, style = stroke)
            }

            Glyph.Shuffle -> {
                // Two crossing strokes with an arrow head on each, like the usual shuffle mark.
                drawPath(polyline(size, 0.16f, 0.3f, 0.4f, 0.3f, 0.66f, 0.7f, 0.84f, 0.7f), color, style = stroke)
                drawPath(polyline(size, 0.16f, 0.7f, 0.4f, 0.7f, 0.66f, 0.3f, 0.84f, 0.3f), color, style = stroke)
                drawPath(polyline(size, 0.74f, 0.2f, 0.84f, 0.3f, 0.74f, 0.4f), color, style = stroke)
                drawPath(polyline(size, 0.74f, 0.6f, 0.84f, 0.7f, 0.74f, 0.8f), color, style = stroke)
            }

            Glyph.Reset -> {
                // Three quarters of a circle, and the arrow head that closes it.
                drawArc(
                    color = color,
                    startAngle = -60f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = Offset(size * 0.2f, size * 0.2f),
                    size = Size(size * 0.6f, size * 0.6f),
                    style = stroke
                )
                drawPath(polyline(size, 0.62f, 0.14f, 0.68f, 0.26f, 0.55f, 0.32f), color, style = stroke)
            }

            Glyph.Star -> drawPath(starPath(size), color, style = stroke)
        }
    }
}

/** An open path through the given points, each as a share of [size]: x0, y0, x1, y1, … */
private fun polyline(size: Float, vararg points: Float): Path = Path().apply {
    moveTo(size * points[0], size * points[1])
    var i = 2
    while (i < points.size) {
        lineTo(size * points[i], size * points[i + 1])
        i += 2
    }
}

/** A regular [sides]-gon inscribed in [size], rotated so it sits the way the reference icons do. */
private fun roundedPolygonPath(sides: Int, size: Float, rotationDegrees: Float): Path {
    val radius = size / 2f * 0.92f
    val center = size / 2f
    val rotation = rotationDegrees * PI.toFloat() / 180f
    return Path().apply {
        for (i in 0 until sides) {
            val angle = rotation + i * 2f * PI.toFloat() / sides
            val x = center + radius * cos(angle)
            val y = center + radius * sin(angle)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/** A five-pointed star, its points on one circle and its inner corners on a smaller one. */
private fun starPath(size: Float): Path = Path().apply {
    val center = size / 2f
    val outer = size * 0.42f
    val inner = outer * 0.45f
    for (i in 0 until 10) {
        val angle = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
        val radius = if (i % 2 == 0) outer else inner
        val x = center + radius * cos(angle)
        val y = center + radius * sin(angle)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun heartPath(size: Float): Path = Path().apply {
    val w = size
    val h = size
    moveTo(w * 0.5f, h * 0.82f)
    cubicTo(w * 0.16f, h * 0.6f, w * 0.1f, h * 0.36f, w * 0.24f, h * 0.25f)
    cubicTo(w * 0.35f, h * 0.16f, w * 0.46f, h * 0.22f, w * 0.5f, h * 0.32f)
    cubicTo(w * 0.54f, h * 0.22f, w * 0.65f, h * 0.16f, w * 0.76f, h * 0.25f)
    cubicTo(w * 0.9f, h * 0.36f, w * 0.84f, h * 0.6f, w * 0.5f, h * 0.82f)
    close()
}

/** Box with a notch at the top, plus the arrow rising out of it. */
private fun DrawScope.drawSharePath(
    size: Float,
    color: Color,
    stroke: Stroke,
    strokeWidth: Float
) {
    val boxPath = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                rect = Rect(
                    offset = Offset(size * 0.2f, size * 0.42f),
                    size = Size(size * 0.6f, size * 0.42f)
                ),
                radiusX = size * 0.12f,
                radiusY = size * 0.12f
            )
        )
    }
    // Cut the notch instead of overdrawing it, so the arrow tail is not doubled up.
    val notch = Path().apply {
        addRect(
            Rect(
                offset = Offset(size * 0.38f, size * 0.36f),
                size = Size(size * 0.24f, size * 0.14f)
            )
        )
    }
    drawPath(Path().apply { op(boxPath, notch, PathOperation.Difference) }, color, style = stroke)

    drawLine(
        color,
        Offset(size * 0.5f, size * 0.18f),
        Offset(size * 0.5f, size * 0.56f),
        strokeWidth,
        StrokeCap.Round
    )
    val arrow = Path().apply {
        moveTo(size * 0.36f, size * 0.31f)
        lineTo(size * 0.5f, size * 0.17f)
        lineTo(size * 0.64f, size * 0.31f)
    }
    drawPath(arrow, color, style = stroke)
}
