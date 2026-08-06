/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.highlight

import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The specular rim drawn along a glass element's border.
 *
 * Real glass catches light at its edges, and that catch is most of what separates a glass control
 * from a translucent rectangle. Width is deliberately sub-pixel by default: the rim should read as
 * a highlight, not as a stroke.
 *
 * @param width stroke width of the rim.
 * @param blurRadius softness of the rim; half the width keeps it crisp but not aliased.
 * @param alpha overall opacity multiplier, handy for animating the rim in and out.
 * @param style how the intensity varies around the border.
 */
@Immutable
public data class Highlight(
    val width: Dp = 0.5f.dp,
    val blurRadius: Dp = width / 2f,
    @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f,
    val style: HighlightStyle = HighlightStyle.Default
) {

    public companion object {

        /** Directional rim lit from the top-left, the neutral default. */
        @Stable
        public val Default: Highlight = Highlight()

        /** Bright on the lit side, dark on the opposite side — reads as a thicker bevel. */
        @Stable
        public val Ambient: Highlight = Highlight(style = HighlightStyle.Ambient)

        /** Uniform hairline border, the cheapest option and the fallback below API 33. */
        @Stable
        public val Plain: Highlight = Highlight(style = HighlightStyle.Plain)

        /**
         * Rim that follows a pointer. Feed it a live position, e.g. from
         * [InteractiveHighlight][com.hakim.liquify.interaction.InteractiveHighlight]:
         *
         * ```
         * highlight = {
         *     Highlight(
         *         style = HighlightStyle.Dynamic(
         *             pointer = interaction.pointerFromCenter,
         *             focus = interaction.pressProgress
         *         )
         *     )
         * }
         * ```
         */
        @Stable
        public val Dynamic: Highlight = Highlight(style = HighlightStyle.Dynamic())
    }
}
