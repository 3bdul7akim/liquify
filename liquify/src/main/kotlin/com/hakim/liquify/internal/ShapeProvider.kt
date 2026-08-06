/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.internal

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Wraps a `() -> Shape` lambda so the resulting [Outline] is only rebuilt when the shape, size,
 * layout direction or density actually changed. Continuous-curvature squircle outlines are made of
 * twelve cubics and are far too expensive to rebuild on every draw.
 */
@Immutable
internal class ShapeProvider(private val shapeBlock: () -> Shape) {

    private var cachedShape: Shape? = null
    private var cachedOutline: Outline? = null
    private var cachedSize: Size = Size.Unspecified
    private var cachedLayoutDirection: LayoutDirection? = null
    private var cachedDensity: Float = Float.NaN

    /** The caller supplied shape, without any outline caching. */
    val innerShape: Shape get() = shapeBlock()

    /** A [Shape] that memoises [Shape.createOutline]. */
    val shape: Shape = object : Shape {

        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val shape = shapeBlock()
            if (cachedShape != shape) {
                cachedShape = shape
                cachedOutline = null
            }
            val outline = cachedOutline
            if (outline == null ||
                cachedSize != size ||
                cachedLayoutDirection != layoutDirection ||
                cachedDensity != density.density
            ) {
                cachedSize = size
                cachedLayoutDirection = layoutDirection
                cachedDensity = density.density
                return shape.createOutline(size, layoutDirection, density)
                    .also { cachedOutline = it }
            }
            return outline
        }
    }
}
