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
 * Resolves the four corner radii of [Shape] in pixels, ordered top-left, top-right, bottom-right,
 * bottom-left — the order every AGSL program in this library expects.
 *
 * Returns `null` for shapes whose geometry cannot be expressed as a rounded rectangle. The signed
 * distance fields used for refraction, the rim highlight and the merge pass are all analytic
 * rounded-rectangle fields, so those effects are skipped rather than rendered wrongly.
 */
internal fun Shape.resolveCornerRadii(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density
): FloatArray? {
    val maxRadius = size.minDimension / 2f
    return when {
        this === RectangleShape -> FloatArray(4)

        this is RoundedRectangularShape -> {
            val corners = corners(size, layoutDirection, density)
            floatArrayOf(
                corners.topLeft.fastCoerceAtMost(maxRadius),
                corners.topRight.fastCoerceAtMost(maxRadius),
                corners.bottomRight.fastCoerceAtMost(maxRadius),
                corners.bottomLeft.fastCoerceAtMost(maxRadius)
            )
        }

        this is AbsoluteRoundedCornerShape -> floatArrayOf(
            topStart.toPx(size, density).fastCoerceAtMost(maxRadius),
            topEnd.toPx(size, density).fastCoerceAtMost(maxRadius),
            bottomEnd.toPx(size, density).fastCoerceAtMost(maxRadius),
            bottomStart.toPx(size, density).fastCoerceAtMost(maxRadius)
        )

        this is CornerBasedShape -> {
            val isLtr = layoutDirection == LayoutDirection.Ltr
            val topLeft = if (isLtr) topStart else topEnd
            val topRight = if (isLtr) topEnd else topStart
            val bottomRight = if (isLtr) bottomEnd else bottomStart
            val bottomLeft = if (isLtr) bottomStart else bottomEnd
            floatArrayOf(
                topLeft.toPx(size, density).fastCoerceAtMost(maxRadius),
                topRight.toPx(size, density).fastCoerceAtMost(maxRadius),
                bottomRight.toPx(size, density).fastCoerceAtMost(maxRadius),
                bottomLeft.toPx(size, density).fastCoerceAtMost(maxRadius)
            )
        }

        else -> null
    }
}
