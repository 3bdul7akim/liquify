package com.hakim.liquify

import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The recipe of a pane of glass: how it treats what is behind it, and what it wears around it.
 *
 * Declare one for the whole app with [LiquifyTheme] and elements only name their shape. Give an
 * element a second material as `pressedMaterial` and it blends between the two while it is held —
 * the whole recipe, rim and shadows included, without a recomposition.
 *
 * ### Refraction that fits every size
 *
 * [refractionHeight] and [refractionAmount] are what a large pane gets. A small element cannot carry
 * them — a 20 dp band would turn a 32 dp button into a lens with no flat middle — so with
 * [refractionBySize] on (the default) every element is refracted in proportion to its short side `s`
 * and eases into these values as it grows:
 *
 * | | small elements | large panes |
 * | --- | --- | --- |
 * | band | `s / 4` | [refractionHeight] |
 * | bend | `s × 3/8` round, `s / 2` elongated | [refractionAmount] |
 *
 * blended by a smooth minimum, so nothing jumps as an element resizes. With the [Regular] values a
 * 32 dp circle gets 8 / 12 dp, a 56 dp search field 13 / 26 dp and a 250 dp menu 20 / 35 dp. Inside a
 * [LiquidGlassGroup] every member is refracted by its own size, blended across the bridges. Turn
 * [refractionBySize] off for exactly these values on every element.
 *
 * @param blurRadius how far the backdrop is blurred.
 * @param refractionHeight thickness of the refracting band at the rim, at full size.
 * @param refractionAmount how far the rim bends the backdrop, at full size.
 * @param saturation vibrancy, so colours survive the blur; `1f` for none.
 * @param tint the colour of the glass, laid over what it refracts; its alpha is the strength.
 *   [Color.Unspecified] keeps the glass neutral, so an app can colour it per element instead.
 * @param depthEffect domes the pane into a lens instead of a flat sheet.
 * @param chromaticAberration a coloured fringe at the rim — seven backdrop samples per pixel instead
 *   of one, so keep it to small controls.
 * @param rim the specular edge, or `null` for none.
 * @param shadow the drop shadow, or `null` for glass that sits flat on the content. An unspecified
 *   colour resolves against the theme: dark ink over a light UI, a pale halo over a dark one.
 * @param innerShadow a shadow inside the pane for a recessed look, `null` by default.
 * @param refractionBySize refract every element in proportion to its size, eased into the two values
 *   above; see the class documentation.
 * @param refractionHeightScale multiplies the band, whatever it resolved to — the knob for a user
 *   setting that makes all glass thicker or thinner.
 * @param refractionAmountScale multiplies the bend, likewise.
 * @param motion how glass made of this material moves under the finger; see [GlassMotion].
 */
@Immutable
public data class GlassMaterial(
    val blurRadius: Dp = 8.dp,
    val refractionHeight: Dp = 20.dp,
    val refractionAmount: Dp = 35.dp,
    @param:FloatRange(from = 0.0) val saturation: Float = 1.15f,
    val tint: Color = Color.Unspecified,
    val depthEffect: Boolean = true,
    val chromaticAberration: Boolean = false,
    val rim: GlassRim? = GlassRim.Default,
    val shadow: GlassShadow? = GlassShadow.Default,
    val innerShadow: GlassInnerShadow? = null,
    val refractionBySize: Boolean = true,
    @param:FloatRange(from = 0.0) val refractionHeightScale: Float = 1f,
    @param:FloatRange(from = 0.0) val refractionAmountScale: Float = 1f,
    val motion: GlassMotion = GlassMotion.Default,
) {
    public companion object {

        /**
         * The everyday material: barely blurred, barely saturated and largely transparent, so the
         * content behind stays readable. Definition comes from the rim lens, not from hiding the
         * backdrop.
         */
        @Stable
        public val Regular: GlassMaterial = GlassMaterial()

        /**
         * No blur at all, domed so the whole pane acts as a lens — for glass over imagery that has to
         * stay fully visible. Dim busy content behind it yourself; this does nothing for legibility.
         */
        @Stable
        public val Clear: GlassMaterial = GlassMaterial(blurRadius = 0.dp, saturation = 1f)

        /** Heavily frosted, for large surfaces such as sheets and sidebars. */
        @Stable
        public val Thick: GlassMaterial = GlassMaterial(
            blurRadius = 22.dp,
            refractionHeight = 32.dp,
            refractionAmount = 40.dp,
            saturation = 1.6f,
            tint = Color.White.copy(alpha = 0.01f),
        )

        /**
         * Regular with a drawn edge — [GlassRim.TwSe]: a lit band inside the outline and a shadow band
         * outside it — and a flatter, gentler lens to go with it.
         */
        @Stable
        public val TwSe: GlassMaterial = GlassMaterial(
            blurRadius = 6.dp,
            refractionHeight = 24.dp,
            refractionAmount = 24.dp,
            rim = GlassRim.TwSe,
        )
    }
}
