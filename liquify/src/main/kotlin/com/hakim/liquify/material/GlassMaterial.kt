package com.hakim.liquify.material

import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.effects.blur
import com.hakim.liquify.effects.gradientBlur
import com.hakim.liquify.effects.lens
import com.hakim.liquify.effects.merge
import com.hakim.liquify.effects.opacity
import com.hakim.liquify.effects.tint
import com.hakim.liquify.effects.vibrancy
import com.hakim.liquify.highlight.Highlight
import com.hakim.liquify.material.GlassMaterial.Companion.Clear
import com.hakim.liquify.material.GlassMaterial.Companion.Regular
import com.hakim.liquify.shadow.InnerShadow
import com.hakim.liquify.shadow.Shadow

/**
 * A ready-made recipe for the whole effect stack, so most call sites need one line instead of five.
 *
 * The two presets follow the distinction the material is designed around: [Regular] adapts and
 * stays legible over anything, [Clear] is far more transparent and expects the content behind it
 * to be dimmed or simple enough not to fight the controls on top.
 *
 * Everything is still a plain effect stack underneath — drop [material] into an effects block and
 * add or override individual effects around it.
 *
 * @param blurRadius how far the backdrop is blurred behind the glass.
 * @param refractionHeight thickness of the refracting band at the rim.
 * @param refractionAmount how sharply the rim bends the backdrop.
 * @param saturation vibrancy applied to the backdrop, `1f` for none.
 * @param tint colour blended into the backdrop; its alpha is the blend amount.
 *   [Color.Unspecified] — the default — leaves the backdrop's own colour alone, which is what lets
 *   an app tint its glass per theme instead of inheriting a fixed one from the material.
 * @param opacity opacity of the glass itself, below `1f` to let the raw background through.
 * @param depthEffect dome the pane instead of keeping it flat.
 * @param chromaticAberration split the refraction per colour channel for a fringed rim.
 * @param gradientBlur ramp the blur up from nothing at the border instead of applying it evenly, the
 *   way a frosted droplet is cloudy in the middle and clear at its edge. The ramp is a fixed short
 *   distance and is independent of [refractionHeight]; switching this on also moves the blur to
 *   *after* the refraction, so the rim keeps a crisp lens.
 * @param highlight the specular rim, or `null` for a pane with no catch of light along its border.
 *   Part of the material because a thicker glass carries a wider rim, and because an app that wants
 *   its glass unrimmed should be able to say so once rather than at every call site.
 * @param shadow the drop shadow, or `null` for glass that sits flat on the content. Its colour is
 *   [Color.Unspecified] by default, which resolves against the theme when it is drawn — see
 *   [Shadow.defaultColor].
 * @param innerShadow a shadow cast inside the element for a recessed look, `null` by default.
 */
@Immutable
public data class GlassMaterial(
    val blurRadius: Dp = 8.dp,
    val refractionHeight: Dp = 20.dp,
    val refractionAmount: Dp = 35.dp,
    @param:FloatRange(from = 0.0) val saturation: Float = 1.15f,
    val tint: Color = Color.Unspecified,
    @param:FloatRange(from = 0.0, to = 1.0) val opacity: Float = 1f,
    val depthEffect: Boolean = true,
    val chromaticAberration: Boolean = false,
    val gradientBlur: Boolean = false,
    val highlight: Highlight? = Highlight.Default,
    val shadow: Shadow? = Shadow.Default,
    val innerShadow: InnerShadow? = null
) {
    public companion object {

        /**
         * The everyday material: barely blurred, barely saturated and largely transparent, so the
         * content behind it stays readable and the pane reads as glass rather than as frosting.
         * Definition comes from the rim lens, not from hiding the backdrop.
         */
        @Stable
        public val Regular: GlassMaterial = GlassMaterial()

        /**
         * No blur at all and the most transparent of the three, domed so the whole pane acts as a
         * lens. For glass over imagery you want to stay fully visible — dim the content behind it
         * yourself, this material does nothing to keep text legible.
         */
        @Stable
        public val Clear: GlassMaterial = GlassMaterial(
            blurRadius = 0.dp,
            refractionHeight = 20.dp,
            refractionAmount = 35.dp,
            saturation = 1f,
            tint = Color.Unspecified,
            opacity = 1f,
            depthEffect = true,
            highlight = Highlight.Default
        )

        /** Heavily frosted and opaque, for large surfaces such as sheets and sidebars. */
        @Stable
        public val Thick: GlassMaterial = GlassMaterial(
            blurRadius = 22.dp,
            refractionHeight = 32.dp,
            refractionAmount = 40.dp,
            saturation = 1.6f,
            tint = Color.White.copy(0.01f),
            opacity = 1f,
            highlight = Highlight.TwSe
        )
    }
}

/**
 * Applies a whole [GlassMaterial] in effect order: vibrancy, tint, blur, then refraction.
 *
 * ```
 * effects = { material(GlassMaterial.Regular) }
 * ```
 *
 * [GlassMaterial.highlight], [GlassMaterial.shadow] and [GlassMaterial.innerShadow] are **not**
 * applied here, and cannot be: an effect block builds the chain the backdrop is run through, while
 * those three are drawn by nodes of their own around it. `liquify` reads them from the material for
 * you; a hand-written effects block passes them to the modifier itself.
 *
 * @param mergeAmount blend strength passed on to [merge][merge]. Leave
 *   it at `0f` unless the element sits in a
 *   [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup].
 */
public fun BackdropEffectScope.material(
    material: GlassMaterial,
    @FloatRange(from = 0.0) mergeAmount: Float = 0f
) {
    if (material.saturation != 1f) vibrancy(material.saturation)
    if (material.tint.isSpecified && material.tint.alpha > 0f) tint(material.tint)
    if (material.opacity < 1f) opacity(material.opacity)

    val hasBlur = material.blurRadius.value > 0f
    // A uniform blur goes *under* the refraction — thick glass bends light that was already
    // scattered. A gradient blur goes on top of it, because the lens samples inwards and would
    // otherwise drag the soft middle back out over the very rim the effect exists to keep crisp.
    if (hasBlur && !material.gradientBlur) blur(material.blurRadius.toPx())

    lens(
        refractionHeight = material.refractionHeight.toPx(),
        refractionAmount = material.refractionAmount.toPx(),
        depthEffect = material.depthEffect,
        chromaticAberration = material.chromaticAberration
    )

    if (hasBlur && material.gradientBlur) gradientBlur(material.blurRadius.toPx())

    if (mergeAmount > 0f) merge(mergeAmount)
}
