/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density

/**
 * What is behind the glass — the pixels a pane refracts and blurs.
 *
 * A backdrop is not a snapshot: it is drawn again every frame into the pane's own layer, which is
 * what lets glass track a scrolling list or a moving wallpaper. Obtain one with
 * [rememberLayerBackdrop] (real composables), [rememberCanvasBackdrop] (something drawn) or
 * [emptyBackdrop], and declare it once with [LiquifyTheme].
 */
public interface Backdrop {

    /**
     * Whether [drawBackdrop] needs the consuming element's [LayoutCoordinates]. `false` lets the
     * element skip position tracking for it.
     */
    public val isCoordinatesDependent: Boolean

    /**
     * Draws the backdrop as the consuming element sees it, into the current draw scope.
     *
     * @param density the consuming element's density.
     * @param coordinates the consuming element's coordinates, or `null` when
     *   [isCoordinatesDependent] is `false`.
     * @param layerBlock the consuming element's own layer transform, so the backdrop can invert it and
     *   stay anchored in screen space.
     */
    public fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)? = null
    )
}
