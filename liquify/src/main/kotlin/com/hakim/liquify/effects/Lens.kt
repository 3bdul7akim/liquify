/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.effects

import androidx.annotation.FloatRange
import androidx.compose.ui.util.fastCoerceAtLeast
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.GroupEffectScope
import com.hakim.liquify.internal.REFRACTION_DISPERSION_SHADER
import com.hakim.liquify.internal.REFRACTION_SHADER
import com.hakim.liquify.internal.RuntimeShaderEffect
import com.hakim.liquify.internal.chain
import com.hakim.liquify.internal.resolveCornerRadii
import com.hakim.liquify.isRuntimeShaderSupported

/**
 * Bends the backdrop near the border, the way a thick pane of glass does.
 *
 * This is the effect that makes the material read as glass rather than as frosted plastic: light
 * is gathered *at the rim* instead of being smeared evenly. Inside the flat centre nothing is
 * refracted at all, so text behind the middle of the element stays readable.
 *
 * Inside a [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup] this call refracts through
 * the *merged* distance field of every member instead of this element's own rectangle, which is
 * what carries the refraction continuously across a gooey bridge.
 *
 * No-op below API 33, and for shapes that are not rounded rectangles.
 *
 * @param refractionHeight thickness of the refracting band in pixels, measured inwards from the
 *   border. Roughly the perceived thickness of the glass.
 * @param refractionAmount how far the backdrop is displaced at the very edge, in pixels. Larger
 *   values bend more sharply.
 * @param depthEffect mixes a radial component into the surface normal, turning a flat pane into a
 *   slightly domed lens.
 * @param chromaticAberration splits the refraction per colour channel, adding the coloured fringe
 *   real glass shows at its rim. Costs seven backdrop samples per pixel instead of one.
 */
public fun BackdropEffectScope.lens(
    @FloatRange(from = 0.0) refractionHeight: Float,
    @FloatRange(from = 0.0) refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (this is GroupEffectScope) {
        recordLens(refractionHeight, refractionAmount, depthEffect)
        return
    }

    // Refraction reads inwards from the border, so it re-uses the halo blur already reserved
    // rather than asking for more.
    if (padding > 0f) {
        padding = (padding - refractionHeight).fastCoerceAtLeast(0f)
    }

    val cornerRadii = shape.resolveCornerRadii(size, layoutDirection, this) ?: return

    val shader =
        if (chromaticAberration) {
            obtainRuntimeShader("liquify.refractionDispersion", REFRACTION_DISPERSION_SHADER)
        } else {
            obtainRuntimeShader("liquify.refraction", REFRACTION_SHADER)
        }

    shader.apply {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("cornerRadii", cornerRadii)
        setFloatUniform("refractionHeight", refractionHeight)
        // Negative displacement pulls the backdrop inwards, which is the direction that reads as
        // looking through the glass rather than as a bulge on top of it.
        setFloatUniform("refractionAmount", -refractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (chromaticAberration) {
            setFloatUniform("chromaticAberration", 1f)
        }
    }

    renderEffect = renderEffect.chain(RuntimeShaderEffect(shader, "content"))
}
