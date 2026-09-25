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
 * @param color the ink. [Color.Unspecified] — the default — is resolved against the theme when the
 *   shadow is drawn, so one [Shadow] reads correctly in both: see [defaultColor].
 */
@Immutable
public data class Shadow(
    val radius: Dp = 7.5f.dp,
    val offset: DpOffset = DpOffset(0f.dp, radius / 1.5f),
    val color: Color = Color.Unspecified,
    @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode
) {

    public companion object {

        @Stable
        public val Default: Shadow = Shadow()

        /**
         * The ink an unspecified [color] resolves to over a light UI: black, at the low opacity a
         * material this transparent can carry without looking printed on.
         */
        @Stable
        public val LightThemeColor: Color = Color.Black.copy(alpha = 0.12f)

        /**
         * And over a dark one.
         *
         * A black shadow on a near-black background is not a subtle shadow, it is no shadow at
         * all — the element loses the gap that made it read as floating. Inverted into a pale halo
         * it does the same job the other way round: the surface around the glass is lifted
         * *towards* the light rather than away from it, and the element separates again. Kept
         * fainter than a rim would be, so it reads as spill and not as a second border.
         */
        @Stable
        public val DarkThemeColor: Color = Color.White.copy(alpha = 0.02f)

        /** [LightThemeColor] or [DarkThemeColor], for a [color] that was left unspecified. */
        @Stable
        public fun defaultColor(darkTheme: Boolean): Color =
            if (darkTheme) DarkThemeColor else LightThemeColor
    }
}

@Stable
public fun lerp(start: Shadow, stop: Shadow, fraction: Float): Shadow = Shadow(
    radius = lerp(start.radius, stop.radius, fraction),
    offset = lerp(start.offset, stop.offset, fraction),
    // An unspecified colour has no channels to interpolate — it is a request to be resolved later,
    // not a value — so it is switched rather than mixed, the way the blend mode is.
    color = if (start.color.isSpecified && stop.color.isSpecified) {
        lerp(start.color, stop.color, fraction)
    } else {
        if (fraction < 0.5f) start.color else stop.color
    },
    alpha = lerp(start.alpha, stop.alpha, fraction),
    blendMode = if (fraction < 0.5f) start.blendMode else stop.blendMode
)
