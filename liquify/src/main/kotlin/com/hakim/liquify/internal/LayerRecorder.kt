/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.internal

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize

/**
 * Records [block] into [layer] while keeping the recording density identical to the outer draw
 * scope. Without this, a layer recorded inside a scaled graphics layer would resolve `Dp` values
 * against the wrong density and the glass would drift by a fraction of a pixel per frame.
 */
internal fun DrawScope.recordLayer(
    layer: GraphicsLayer,
    density: Density,
    size: IntSize = this.size.toIntSize(),
    block: DrawScope.() -> Unit
) {
    layer.record(size) {
        val previousDensity = drawContext.density
        drawContext.density = density
        try {
            block()
        } finally {
            drawContext.density = previousDensity
        }
    }
}
