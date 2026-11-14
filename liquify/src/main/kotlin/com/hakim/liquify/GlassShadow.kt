/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify

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
 * The drop shadow of a pane of glass. Glass floats above the content, and the shadow is what sells
 * the gap. It is punched out under the element, so it never darkens the backdrop seen through it.
 *
 * @param radius blur radius.
 * @param offset displacement; a small downward offset reads as light from above.
 * @param color the ink. [Color.Unspecified] resolves against the theme when it is drawn: black over a
 *   light UI, a pale halo over a dark one — black on near-black would be no shadow at all.
 * @param alpha overall opacity, applied as a layer property — fading a shadow costs nothing.
 */
@Immutable
public data class GlassShadow(
    val radius: Dp = 7.5f.dp,
    val offset: DpOffset = DpOffset(0f.dp, radius / 1.5f),
    val color: Color = Color.Unspecified,
    @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode
) {

    public companion object {

        @Stable
        public val Default: GlassShadow = GlassShadow()

        /** What an unspecified colour resolves to over a light UI. */
        @Stable
        public val LightThemeColor: Color = Color.Black.copy(alpha = 0.12f)

        /**
         * And over a dark one: the surface around the glass is lifted towards the light instead of
         * away from it, kept fainter than a rim so it reads as spill, not as a second border.
         */
        @Stable
        public val DarkThemeColor: Color = Color.White.copy(alpha = 0.02f)
    }
}

/**
 * A shadow cast inside the pane, clipped to its shape: the glass looks recessed into the surface
 * rather than resting on it.
 *
 * @param color the ink; [Color.Unspecified] resolves against the theme, as for [GlassShadow].
 */
@Immutable
public data class GlassInnerShadow(
    val radius: Dp = 18f.dp,
    val offset: DpOffset = DpOffset(0f.dp, radius),
    val color: Color = Color.Unspecified,
    @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode
) {

    public companion object {

        @Stable
        public val Default: GlassInnerShadow = GlassInnerShadow()

        @Stable
        public val LightThemeColor: Color = Color.Black.copy(alpha = 0.25f)

        /**
         * Over a dark UI the crescent reads as the inside wall of the recess catching the light, a
         * touch stronger than the drop shadow because it lies over refracted backdrop.
         */
        @Stable
        public val DarkThemeColor: Color = Color.White.copy(alpha = 0.1f)
    }
}

internal fun GlassShadow.resolvedColor(darkTheme: Boolean): Color =
    if (color.isSpecified) color
    else if (darkTheme) GlassShadow.DarkThemeColor else GlassShadow.LightThemeColor

internal fun GlassInnerShadow.resolvedColor(darkTheme: Boolean): Color =
    if (color.isSpecified) color
    else if (darkTheme) GlassInnerShadow.DarkThemeColor else GlassInnerShadow.LightThemeColor

/**
 * [a] blended towards [b] by [fraction]; a `null` side is the other one faded out. An unspecified
 * colour is a request rather than a value, so it is switched at the midpoint instead of mixed.
 */
internal fun lerpShadow(a: GlassShadow?, b: GlassShadow?, fraction: Float): GlassShadow? {
    if (fraction <= 0f || a == b) return a
    if (fraction >= 1f) return b
    if (a == null) return b?.copy(alpha = b.alpha * fraction)
    if (b == null) return a.copy(alpha = a.alpha * (1f - fraction))
    return GlassShadow(
        radius = lerpExact(a.radius, b.radius, fraction),
        offset = lerpExact(a.offset, b.offset, fraction),
        color = lerpColor(a.color, b.color, fraction),
        alpha = lerp(a.alpha, b.alpha, fraction),
        blendMode = if (fraction < 0.5f) a.blendMode else b.blendMode
    )
}

internal fun lerpInnerShadow(
    a: GlassInnerShadow?,
    b: GlassInnerShadow?,
    fraction: Float
): GlassInnerShadow? {
    if (fraction <= 0f || a == b) return a
    if (fraction >= 1f) return b
    // A crescent growing out of nothing grows its radius as well as its alpha: that is what reads as
    // the pane sinking in, rather than a fixed shadow fading up.
    if (a == null) return b?.grownBy(fraction)
    if (b == null) return a.grownBy(1f - fraction)
    return GlassInnerShadow(
        radius = lerpExact(a.radius, b.radius, fraction),
        offset = lerpExact(a.offset, b.offset, fraction),
        color = lerpColor(a.color, b.color, fraction),
        alpha = lerp(a.alpha, b.alpha, fraction),
        blendMode = if (fraction < 0.5f) a.blendMode else b.blendMode
    )
}

private fun GlassInnerShadow.grownBy(fraction: Float): GlassInnerShadow = copy(
    radius = radius * fraction,
    offset = DpOffset(offset.x * fraction, offset.y * fraction),
    alpha = alpha * fraction
)

/** Mixes two colours, or switches at the midpoint while either is unspecified. */
internal fun lerpColor(a: Color, b: Color, fraction: Float): Color =
    if (a == b) a
    else if (a.isSpecified && b.isSpecified) lerp(a, b, fraction)
    else if (fraction < 0.5f) a else b

/**
 * [lerp] that keeps an end that does not move exactly as it is. The decoration records its layers
 * keyed on these values' bits, and `(1 - f) * a + f * a` is not always `a` — so a blend that only
 * fades would otherwise re-record a shadow or a rim on every frame of it.
 */
internal fun lerpExact(a: Dp, b: Dp, fraction: Float): Dp = if (a == b) a else lerp(a, b, fraction)

internal fun lerpExact(a: DpOffset, b: DpOffset, fraction: Float): DpOffset =
    if (a == b) a else lerp(a, b, fraction)

/**
 * Blends two tints. A missing or fully transparent side is the other one faded out — not mixed with
 * black, which is what transparent is, and which would turn a white knob grey on its way to clear.
 */
internal fun lerpTint(a: Color, b: Color, fraction: Float): Color {
    val hasA = a.isSpecified && a.alpha > 0f
    val hasB = b.isSpecified && b.alpha > 0f
    return when {
        fraction <= 0f -> a
        fraction >= 1f -> b
        hasA && hasB -> lerp(a, b, fraction)
        hasA -> a.copy(alpha = a.alpha * (1f - fraction))
        hasB -> b.copy(alpha = b.alpha * fraction)
        else -> Color.Unspecified
    }
}
