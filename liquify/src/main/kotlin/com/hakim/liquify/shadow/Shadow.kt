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
 * Drop shadow cast by a glass element.
 *
 * Glass floats above content rather than sitting in it, and the shadow is what sells the gap. It
 * is punched out under the element so it never darkens the backdrop seen *through* the glass.
 *
 * Deepening the shadow while an element grows is a cheap and effective way to suggest a thicker,
 * more substantial material during a morph.
 *
 * @param radius blur radius of the shadow.
 * @param offset displacement; a small downward offset reads as light from above.
 */
@Immutable
public data class Shadow(
    val radius: Dp = 24f.dp,
    val offset: DpOffset = DpOffset(0f.dp, radius / 6f),
    val color: Color = Color.Black.copy(alpha = 0.1f),
    @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode
) {

    public companion object {

        @Stable
        public val Default: Shadow = Shadow()
    }
}

@Stable
public fun lerp(start: Shadow, stop: Shadow, fraction: Float): Shadow = Shadow(
    radius = lerp(start.radius, stop.radius, fraction),
    offset = lerp(start.offset, stop.offset, fraction),
    color = lerp(start.color, stop.color, fraction),
    alpha = lerp(start.alpha, stop.alpha, fraction),
    blendMode = if (fraction < 0.5f) start.blendMode else stop.blendMode
)
