/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.effects

import androidx.annotation.FloatRange
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.util.fastCoerceAtLeast
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.BaseBackdropEffectScope
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

    val scope = this as BaseBackdropEffectScope

    // Refraction reads inwards from the border, so it re-uses the halo blur already reserved
    // rather than asking for more.
    if (padding > 0f) {
        padding = (padding - refractionHeight).fastCoerceAtLeast(0f)
    }

    val cornerRadii = scope.cornerRadiiScratch
    if (!shape.resolveCornerRadii(size, layoutDirection, this, cornerRadii)) return

    val shader =
        if (chromaticAberration) {
            obtainRuntimeShader("liquify.refractionDispersion", REFRACTION_DISPERSION_SHADER)
        } else {
            obtainRuntimeShader("liquify.refraction", REFRACTION_SHADER)
        }

    // Both of these were per-pixel divisions inside the band, by quantities that are the same for
    // every pixel of the draw. The shader takes the reciprocals instead.
    val refractionHeightInverse = 1f / refractionHeight
    val halfArea = size.width * size.height * 0.25f
    val dispersionScale = if (halfArea > 0f) 1f / halfArea else 0f

    // Half the element, the translation that centres a layer coordinate on it, and the bound on
    // the gradient radius. All three were recomputed per pixel from `size` and `offset` for values
    // that are the same across the whole draw.
    val halfWidth = size.width * 0.5f
    val halfHeight = size.height * 0.5f

    // Uploading a uniform discards the platform shader's built native instance, so an effects
    // block re-running because one animated value moved would pay to rebuild the program even
    // where nothing it reads has changed. The variant is part of the fingerprint because the two
    // programs are separate shader instances, each holding whatever was last written to it.
    val fingerprint = lensUniformFingerprint(
        chromaticAberration = chromaticAberration,
        size = size,
        padding = padding,
        cornerRadii = cornerRadii,
        refractionHeight = refractionHeight,
        refractionAmount = refractionAmount,
        depthEffect = depthEffect,
        dispersionScale = dispersionScale
    )
    if (fingerprint != scope.lensUniforms) {
        scope.lensUniforms = fingerprint
        shader.apply {
            setFloatUniform("halfSize", halfWidth, halfHeight)
            setFloatUniform("centerOffset", -padding - halfWidth, -padding - halfHeight)
            setFloatUniform("halfMinSize", minOf(halfWidth, halfHeight))
            setFloatUniform("cornerRadii", cornerRadii)
            setFloatUniform("refractionHeight", refractionHeight)
            setFloatUniform("refractionHeightInverse", refractionHeightInverse)
            // Negative displacement pulls the backdrop inwards, which is the direction that reads
            // as looking through the glass rather than as a bulge on top of it.
            setFloatUniform("refractionAmount", -refractionAmount)
            setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
            if (chromaticAberration) {
                setFloatUniform("dispersionScale", dispersionScale)
            }
        }
    }

    renderEffect = renderEffect.chain(RuntimeShaderEffect(shader, "content"))
}

/** Everything written to the refraction program, folded into one value. */
private fun lensUniformFingerprint(
    chromaticAberration: Boolean,
    size: Size,
    padding: Float,
    cornerRadii: FloatArray,
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean,
    dispersionScale: Float
): Long {
    var hash = if (chromaticAberration) 31L else 17L
    hash = hash * 31L + size.width.toRawBits()
    hash = hash * 31L + size.height.toRawBits()
    hash = hash * 31L + padding.toRawBits()
    for (corner in 0..3) hash = hash * 31L + cornerRadii[corner].toRawBits()
    hash = hash * 31L + refractionHeight.toRawBits()
    hash = hash * 31L + refractionAmount.toRawBits()
    hash = hash * 31L + if (depthEffect) 1L else 0L
    hash = hash * 31L + dispersionScale.toRawBits()
    return hash
}
