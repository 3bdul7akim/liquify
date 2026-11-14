/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.internal

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * One element's shape, with its [Outline] shared by every node of that element and rebuilt only when
 * the size, the layout direction or the density changed.
 *
 * The rim, both shadows and the flat surface all ask for the same outline, and a continuous-curvature
 * squircle is twelve cubics — too expensive to rebuild for each of them, let alone on every frame a
 * pane is resized.
 */
@Immutable
internal class ShapeProvider(val innerShape: Shape) {

    private var cachedOutline: Outline? = null
    private var cachedSize: Size = Size.Unspecified
    private var cachedLayoutDirection: LayoutDirection? = null
    private var cachedDensity: Float = Float.NaN

    private var cachedClipOutline: Outline? = null
    private var cachedClipSize: Size = Size.Unspecified
    private var cachedClipLayoutDirection: LayoutDirection? = null
    private var cachedClipDensity: Float = Float.NaN
    private val clipRadii = FloatArray(4)

    /** [innerShape], with [Shape.createOutline] memoised. */
    val shape: Shape = object : Shape {

        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val outline = cachedOutline
            if (outline == null ||
                cachedSize != size ||
                cachedLayoutDirection != layoutDirection ||
                cachedDensity != density.density
            ) {
                cachedSize = size
                cachedLayoutDirection = layoutDirection
                cachedDensity = density.density
                return innerShape.createOutline(size, layoutDirection, density)
                    .also { cachedOutline = it }
            }
            return outline
        }
    }

    /**
     * What the element's layer clips to: a rounded rectangle with [innerShape]'s corner radii, which
     * the renderer clips to analytically, or the outline itself for a shape that is not a rounded
     * rectangle.
     *
     * Clipped to a curve, every draw inside the element — the glass, its tint, each icon and line of
     * text — cost a coverage mask rasterised on the render thread and uploaded again on every frame,
     * because the renderer cannot keep a clip mask from one frame to the next. A continuous-curvature
     * corner meets the circular one at its apex and strays from it by at most 1.6 % of the radius —
     * under a pixel up to a 60 px corner, and nothing at all at the ends of a capsule — always to the
     * inside, so the clip never cuts the curve. The rim, still drawn along the curve itself, lies over
     * the difference; it divides its bands at the circular corner as well.
     */
    val clipShape: Shape = object : Shape {

        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val outline = cachedClipOutline
            if (outline != null &&
                cachedClipSize == size &&
                cachedClipLayoutDirection == layoutDirection &&
                cachedClipDensity == density.density
            ) {
                return outline
            }
            cachedClipSize = size
            cachedClipLayoutDirection = layoutDirection
            cachedClipDensity = density.density
            val radii = clipRadii
            val clip =
                if (innerShape.resolveCornerRadii(size, layoutDirection, density, radii)) {
                    Outline.Rounded(
                        RoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = size.height,
                            topLeftCornerRadius = CornerRadius(radii[0]),
                            topRightCornerRadius = CornerRadius(radii[1]),
                            bottomRightCornerRadius = CornerRadius(radii[2]),
                            bottomLeftCornerRadius = CornerRadius(radii[3])
                        )
                    )
                } else {
                    shape.createOutline(size, layoutDirection, density)
                }
            cachedClipOutline = clip
            return clip
        }
    }
}
