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
 * The source of pixels a glass element refracts and blurs — the *thing behind the glass*.
 *
 * A backdrop is not a snapshot: it is re-drawn every frame into the element's own layer, which is
 * what lets glass track a scrolling list or a moving wallpaper. Obtain one with
 * [rememberLayerBackdrop][com.hakim.liquify.backdrops.rememberLayerBackdrop] (capture real
 * composables), [rememberCanvasBackdrop][com.hakim.liquify.backdrops.rememberCanvasBackdrop]
 * (draw something procedurally) or [emptyBackdrop][com.hakim.liquify.backdrops.emptyBackdrop].
 */
public interface Backdrop {

    /**
     * Whether [drawBackdrop] needs the element's [LayoutCoordinates] to position its content.
     *
     * Returning `false` lets the element skip global-position tracking entirely, which removes a
     * layout listener per glass element.
     */
    public val isCoordinatesDependent: Boolean

    /**
     * Draws the backdrop content into the current (already offset) draw scope.
     *
     * @param density the density of the consuming element, which may differ from the recording
     *   scope's density when the element sits inside a scaled layer.
     * @param coordinates the consuming element's coordinates, or `null` when
     *   [isCoordinatesDependent] is `false`.
     * @param layerBlock the consuming element's own graphics-layer transform, so the backdrop can
     *   invert it and stay anchored in screen space.
     */
    public fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)? = null
    )
}
