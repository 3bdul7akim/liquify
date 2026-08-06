/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.shadow

import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp

/**
 * Shadow cast *inside* an element, clipped to its shape.
 *
 * Reads as depth: the glass looks recessed into the surface rather than resting on top of it.
 * Pairs well with a pressed state, where the element sinks slightly.
 */
@Immutable
public data class InnerShadow(
    val radius: Dp = 24f.dp,
    val offset: DpOffset = DpOffset(0f.dp, radius),
    val color: Color = Color.Black.copy(alpha = 0.15f),
    @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode
) {

    public companion object {

        @Stable
        public val Default: InnerShadow = InnerShadow()
    }
}

@Stable
public fun lerp(start: InnerShadow, stop: InnerShadow, fraction: Float): InnerShadow = InnerShadow(
    radius = lerp(start.radius, stop.radius, fraction),
    offset = lerp(start.offset, stop.offset, fraction),
    color = lerp(start.color, stop.color, fraction),
    alpha = lerp(start.alpha, stop.alpha, fraction),
    blendMode = if (fraction < 0.5f) start.blendMode else stop.blendMode
)
