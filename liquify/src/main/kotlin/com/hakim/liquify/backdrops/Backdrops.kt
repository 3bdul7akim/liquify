/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.hakim.liquify.Backdrop

/** A backdrop that draws nothing. Glass over it shows only its own tint, rim and shadow. */
@Stable
public fun emptyBackdrop(): Backdrop = EmptyBackdrop

/**
 * A backdrop drawn procedurally, in the *consuming element's* coordinate space.
 *
 * Cheaper than [rememberLayerBackdrop] when the thing behind the glass is a gradient or a pattern
 * rather than real composables, because nothing has to be captured into a layer first.
 */
@Composable
public fun rememberCanvasBackdrop(onDraw: DrawScope.() -> Unit): Backdrop =
    remember(onDraw) { CanvasBackdrop(onDraw) }

/** Wraps [backdrop] so [onDraw] can filter, transform or partially skip its drawing. */
@Composable
public fun rememberBackdrop(
    backdrop: Backdrop,
    onDraw: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit
): Backdrop = remember(backdrop, onDraw) { DecoratedBackdrop(backdrop, onDraw) }

/** Stacks two backdrops; [backdrop2] is drawn over [backdrop1]. */
@Composable
public fun rememberCombinedBackdrop(backdrop1: Backdrop, backdrop2: Backdrop): Backdrop =
    remember(backdrop1, backdrop2) { CombinedBackdrop(arrayOf(backdrop1, backdrop2)) }

/** Stacks any number of backdrops, in the order given. */
@Composable
public fun rememberCombinedBackdrop(vararg backdrops: Backdrop): Backdrop =
    remember(*backdrops) { CombinedBackdrop(arrayOf(*backdrops)) }

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
        backdrops.forEach { backdrop ->
            with(backdrop) { drawBackdrop(density, coordinates, layerBlock) }
        }
    }
}
