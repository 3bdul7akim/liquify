/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.internal

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.shapes.RoundedRectangularShape

/**
 * Writes the four corner radii of [Shape] in pixels into [out], ordered top-left, top-right,
 * bottom-right, bottom-left — the order every AGSL program in this library expects.
 *
 * Returns `false`, leaving [out] untouched, for shapes whose geometry cannot be expressed as a
 * rounded rectangle. The signed distance fields used for refraction, the rim highlight and the
 * merge pass are all analytic rounded-rectangle fields, so those effects are skipped rather than
 * rendered wrongly.
 *
 * [out] is a caller-owned scratch array rather than a fresh one, because the callers are on the
 * per-frame path: an effect block that reads an animated value re-runs on every frame it changes,
 * and a dragged group member resolves its radii on every layout pass. Four floats allocated there
 * is four floats allocated sixty times a second, per element.
 */
internal fun Shape.resolveCornerRadii(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
    out: FloatArray
): Boolean {
    val maxRadius = size.minDimension / 2f
    when {
        this === RectangleShape -> {
            out[0] = 0f
            out[1] = 0f
            out[2] = 0f
            out[3] = 0f
        }

        this is RoundedRectangularShape -> {
            val corners = corners(size, layoutDirection, density)
            out[0] = corners.topLeft.fastCoerceAtMost(maxRadius)
            out[1] = corners.topRight.fastCoerceAtMost(maxRadius)
            out[2] = corners.bottomRight.fastCoerceAtMost(maxRadius)
            out[3] = corners.bottomLeft.fastCoerceAtMost(maxRadius)
        }

        this is AbsoluteRoundedCornerShape -> {
            out[0] = topStart.toPx(size, density).fastCoerceAtMost(maxRadius)
            out[1] = topEnd.toPx(size, density).fastCoerceAtMost(maxRadius)
            out[2] = bottomEnd.toPx(size, density).fastCoerceAtMost(maxRadius)
            out[3] = bottomStart.toPx(size, density).fastCoerceAtMost(maxRadius)
        }

        this is CornerBasedShape -> {
            val isLtr = layoutDirection == LayoutDirection.Ltr
            val topLeft = if (isLtr) topStart else topEnd
            val topRight = if (isLtr) topEnd else topStart
            val bottomRight = if (isLtr) bottomEnd else bottomStart
            val bottomLeft = if (isLtr) bottomStart else bottomEnd
            out[0] = topLeft.toPx(size, density).fastCoerceAtMost(maxRadius)
            out[1] = topRight.toPx(size, density).fastCoerceAtMost(maxRadius)
            out[2] = bottomRight.toPx(size, density).fastCoerceAtMost(maxRadius)
            out[3] = bottomLeft.toPx(size, density).fastCoerceAtMost(maxRadius)
        }

        else -> return false
    }
    return true
}

/**
 * [resolveCornerRadii] into a fresh array, for the call sites that are not on the per-frame path —
 * the rim styles, which only resolve their geometry when the recorded stroke actually changes.
 */
internal fun Shape.resolveCornerRadii(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density
): FloatArray? {
    val radii = FloatArray(4)
    return if (resolveCornerRadii(size, layoutDirection, density, radii)) radii else null
}
