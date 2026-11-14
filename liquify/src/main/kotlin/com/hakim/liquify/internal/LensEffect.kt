/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.internal

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.util.fastCoerceAtLeast
import com.hakim.liquify.BaseBackdropEffectScope
import com.hakim.liquify.foldedBlurSpacing
import com.hakim.liquify.isRuntimeShaderSupported

/**
 * The edge refraction of a single element: one runtime-shader pass that bends the backdrop inside a
 * band along the border, takes in the colour work pending in front of it and lays the element's own
 * colour over the result.
 *
 * Over the content as well ([BaseBackdropEffectScope.refractsContent]) the lens reads a layer of the
 * element's own size with the backdrop already in it, so the colour work is flushed onto the backdrop
 * first — text is bent, never recoloured.
 *
 * @param depthEffect how far the surface normal is domed, `0f` flat to `1f` fully.
 */
internal fun BaseBackdropEffectScope.applyLens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Float,
    chromaticAberration: Boolean
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    val onContent = refractsContent
    if (onContent) flushColor()

    // Refraction reads inwards from the border, so it re-uses the halo the blur reserved rather than
    // asking for more. Over the content it reads another layer, and the halo stays the blur's.
    if (!onContent && padding > 0f) {
        padding = (padding - refractionHeight).fastCoerceAtLeast(0f)
    }
    val layerPadding = if (onContent) 0f else padding

    val cornerRadii = cornerRadiiScratch
    if (!shape.resolveCornerRadii(size, layoutDirection, this, cornerRadii)) return

    // A small blur in front is taken in here, nine taps per sample — by the fringe program too, see
    // DISPERSION_FUNCTION; see foldedBlur. Taken only now that the lens is sure to run: returning
    // above, it is still pending, and the caller chains it as the blur it was.
    val blurSpacing = foldedBlurSpacing(foldedBlur)
    foldedBlur = 0f

    val shader =
        if (chromaticAberration) {
            obtainRuntimeShader("liquify.refractionDispersion", REFRACTION_DISPERSION_SHADER)
        } else {
            obtainRuntimeShader("liquify.refraction", REFRACTION_SHADER)
        }

    // Values that are the same for every pixel of the draw, folded on the CPU instead of being
    // recomputed — or divided by — per pixel.
    val refractionHeightInverse = 1f / refractionHeight
    val halfArea = size.width * size.height * 0.25f
    val dispersionScale = if (halfArea > 0f) 1f / halfArea else 0f
    val halfWidth = size.width * 0.5f
    val halfHeight = size.height * 0.5f
    // How deep inside its box a pixel must be to be certainly past the band, whatever corner it is
    // nearest — what lets the program's interior test skip the radius lookup.
    val interiorDepth = refractionHeight +
        maxOf(maxOf(cornerRadii[0], cornerRadii[1]), maxOf(cornerRadii[2], cornerRadii[3]))

    val colorFiltered = hasPendingColor
    hasPendingColor = false

    // The element's colour, laid over the glass here rather than drawn over the pane on its own; see
    // BaseBackdropEffectScope.fillFolded. Over the content it would colour the text.
    val fill = if (onContent) Color.Unspecified else fill
    val foldsFill = fill.isSpecified && fill.alpha > 0f
    fillFolded = foldsFill

    val fingerprint = lensUniformFingerprint(
        chromaticAberration = chromaticAberration,
        size = size,
        padding = layerPadding,
        cornerRadii = cornerRadii,
        refractionHeight = refractionHeight,
        refractionAmount = refractionAmount,
        depthEffect = depthEffect,
        dispersionScale = dispersionScale,
        blurSpacing = blurSpacing,
        color = if (colorFiltered) ColorMatrices.fingerprint(pendingColor) else 0L,
        fill = if (foldsFill) fill.value.toLong() else 0L
    )
    if (fingerprint != lensUniforms) {
        lensUniforms = fingerprint
        shader.apply {
            setFloatUniform("halfSize", halfWidth, halfHeight)
            setFloatUniform("centerOffset", -layerPadding - halfWidth, -layerPadding - halfHeight)
            setFloatUniform("halfMinSize", minOf(halfWidth, halfHeight))
            setFloatUniform("cornerRadii", cornerRadii)
            setFloatUniform("interiorDepth", interiorDepth)
            setFloatUniform("refractionHeight", refractionHeight)
            setFloatUniform("refractionHeightInverse", refractionHeightInverse)
            // Negative pulls the backdrop inwards, which reads as looking through the glass rather
            // than as a bulge on top of it.
            setFloatUniform("refractionAmount", -refractionAmount)
            setFloatUniform("depthEffect", depthEffect)
            if (chromaticAberration) setFloatUniform("dispersionScale", dispersionScale)
            setFloatUniform("blurSpacing", blurSpacing)
            setColorMatrixUniforms(this@applyLens, colorFiltered)
            setColorUniform("fill", if (foldsFill) fill else Color.Transparent)
        }
    }

    val effect = RuntimeShaderEffect(shader, "content")
    if (onContent) {
        contentEffect = contentEffect.chain(effect)
    } else {
        renderEffect = renderEffect.chain(effect)
    }
}

/**
 * Writes the scope's pending colour work to a program that includes `COLOR_MATRIX`, or switches it
 * off there when [filtered] is `false`.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun RuntimeShader.setColorMatrixUniforms(scope: BaseBackdropEffectScope, filtered: Boolean) {
    if (filtered) {
        val matrix = scope.colorUniformMatrix
        val offset = scope.colorUniformOffset
        ColorMatrices.toShaderUniforms(scope.pendingColor, matrix, offset)
        setFloatUniform("colorMatrix", matrix)
        setFloatUniform("colorOffset", offset[0], offset[1], offset[2])
    }
    setFloatUniform("colorFiltered", if (filtered) 1f else 0f)
}

/** Everything written to the refraction program, folded into one value. */
private fun lensUniformFingerprint(
    chromaticAberration: Boolean,
    size: Size,
    padding: Float,
    cornerRadii: FloatArray,
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Float,
    dispersionScale: Float,
    blurSpacing: Float,
    color: Long,
    fill: Long
): Long {
    var hash = if (chromaticAberration) 31L else 17L
    hash = hash * 31L + size.width.toRawBits()
    hash = hash * 31L + size.height.toRawBits()
    hash = hash * 31L + padding.toRawBits()
    for (corner in 0..3) hash = hash * 31L + cornerRadii[corner].toRawBits()
    hash = hash * 31L + refractionHeight.toRawBits()
    hash = hash * 31L + refractionAmount.toRawBits()
    hash = hash * 31L + depthEffect.toRawBits()
    hash = hash * 31L + dispersionScale.toRawBits()
    hash = hash * 31L + blurSpacing.toRawBits()
    hash = hash * 31L + color
    hash = hash * 31L + fill
    return hash
}
