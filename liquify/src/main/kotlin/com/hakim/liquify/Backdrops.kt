/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density

/** A backdrop that draws nothing: glass over it shows only its own tint, rim and shadow. */
@Stable
public fun emptyBackdrop(): Backdrop = EmptyBackdrop

/**
 * A backdrop of one [color] — the card or the sheet a control sits on, and the right one for glass
 * that sits on a plain surface *inside* the layer it would otherwise sample.
 *
 * Glass over a single colour has nothing to blur and nothing to bend, so it is laid down as that
 * colour with the material's vibrancy worked into it: no layer is recorded and no effect runs, while
 * the tint, the rim, the shadows and the touch stay exactly as they are. An element with its own
 * `effects`, one that refracts its content and the members of a merging group still render it the
 * long way.
 */
@Stable
public fun colorBackdrop(color: Color): Backdrop = ColorBackdrop(color)

/**
 * A backdrop drawn by [onDraw], in the consuming element's own coordinates. Cheaper than a layer
 * backdrop when what is behind the glass is drawn rather than composed — a gradient, a pattern. For
 * a single colour, [colorBackdrop] takes the layer out as well.
 */
@Composable
public fun rememberCanvasBackdrop(onDraw: DrawScope.() -> Unit): Backdrop =
    remember(onDraw) { CanvasBackdrop(onDraw) }

/**
 * [backdrop], with [onDraw] wrapped around its drawing to filter, transform or partially skip it —
 * the track of a slider seen magnified through its thumb, say.
 */
@Composable
public fun rememberBackdrop(
    backdrop: Backdrop,
    onDraw: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit
): Backdrop = remember(backdrop, onDraw) { DecoratedBackdrop(backdrop, onDraw) }

/**
 * Stacks [backdrops], each drawn over the one before it. `null`s are skipped, so the inherited
 * backdrop can be passed as it is: `rememberCombinedBackdrop(LiquifyTheme.backdrop, trackBackdrop)`.
 */
@Composable
public fun rememberCombinedBackdrop(vararg backdrops: Backdrop?): Backdrop =
    remember(*backdrops) {
        val present = backdrops.filterNotNull()
        when (present.size) {
            0 -> EmptyBackdrop
            1 -> present[0]
            else -> CombinedBackdrop(present.toTypedArray())
        }
    }

@Immutable
private object EmptyBackdrop : Backdrop {

    override val isCoordinatesDependent: Boolean = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
    }
}

/**
 * One colour. A single element recognises it and lays the colour down itself, without a layer or a
 * chain; a group or a combined backdrop simply draws it.
 */
@Immutable
internal class ColorBackdrop(val color: Color) : Backdrop {

    override val isCoordinatesDependent: Boolean = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        drawRect(color)
    }

    // By colour: an element handed an equal backdrop on recomposition keeps what it has built.
    override fun equals(other: Any?): Boolean = other is ColorBackdrop && other.color == color

    override fun hashCode(): Int = color.hashCode()
}

@Immutable
private class CanvasBackdrop(val onDraw: DrawScope.() -> Unit) : Backdrop {

    override val isCoordinatesDependent: Boolean = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        onDraw()
    }
}

@Immutable
private class DecoratedBackdrop(
    val backdrop: Backdrop,
    val onDraw: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit
) : Backdrop {

    override val isCoordinatesDependent: Boolean = backdrop.isCoordinatesDependent

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        onDraw { with(backdrop) { drawBackdrop(density, coordinates, layerBlock) } }
    }
}

@Immutable
private class CombinedBackdrop(val backdrops: Array<Backdrop>) : Backdrop {

    override val isCoordinatesDependent: Boolean = backdrops.any { it.isCoordinatesDependent }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        for (backdrop in backdrops) {
            with(backdrop) { drawBackdrop(density, coordinates, layerBlock) }
        }
    }
}
