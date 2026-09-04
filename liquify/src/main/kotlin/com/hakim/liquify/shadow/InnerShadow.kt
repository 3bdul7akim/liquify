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
import androidx.compose.ui.graphics.isSpecified
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
 *
 * @param color the ink. [Color.Unspecified] — the default — is resolved against the theme when the
 *   shadow is drawn: see [defaultColor].
 */
@Immutable
public data class InnerShadow(
    val radius: Dp = 18f.dp,
    val offset: DpOffset = DpOffset(0f.dp, radius),
    val color: Color = Color.Unspecified,
    @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode
) {

    public companion object {

        @Stable
        public val Default: InnerShadow = InnerShadow()

        /** The ink an unspecified [color] resolves to over a light UI. */
        @Stable
        public val LightThemeColor: Color = Color.Black.copy(alpha = 0.25f)

        /**
         * And over a dark one.
         *
         * The crescent sits along the inner edge the offset points away from, so as a light it
         * reads as exactly the inverse: the inside wall of the recess catching the light instead
         * of falling into shade. Carried a touch stronger than the drop shadow, because it lies
         * over refracted backdrop rather than over the page.
         */
        @Stable
        public val DarkThemeColor: Color = Color.White.copy(alpha = 0.325f)

        /** [LightThemeColor] or [DarkThemeColor], for a [color] that was left unspecified. */
        @Stable
        public fun defaultColor(darkTheme: Boolean): Color =
            if (darkTheme) DarkThemeColor else LightThemeColor
    }
}

@Stable
public fun lerp(start: InnerShadow, stop: InnerShadow, fraction: Float): InnerShadow = InnerShadow(
    radius = lerp(start.radius, stop.radius, fraction),
    offset = lerp(start.offset, stop.offset, fraction),
    // Switched rather than mixed while either side is unspecified: see the drop shadow.
    color = if (start.color.isSpecified && stop.color.isSpecified) {
        lerp(start.color, stop.color, fraction)
    } else {
        if (fraction < 0.5f) start.color else stop.color
    },
    alpha = lerp(start.alpha, stop.alpha, fraction),
    blendMode = if (fraction < 0.5f) start.blendMode else stop.blendMode
)
