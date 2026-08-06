/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.effects

import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.internal.ColorFilterEffect
import com.hakim.liquify.isRenderEffectSupported

/** Appends an arbitrary [ColorFilter] to the chain. No-op below API 31. */
public fun BackdropEffectScope.colorFilter(colorFilter: ColorFilter) {
    if (!isRenderEffectSupported()) return
    renderEffect = ColorFilterEffect(renderEffect, colorFilter)
}

/**
 * Boosts the saturation of the backdrop so colours bleeding through the glass stay lively instead
 * of washing out behind the blur. This is the counterpart of the "vibrancy" layer in Apple's
 * material stack.
 *
 * @param amount saturation multiplier; `1f` is a no-op, the default `1.5f` matches the reference
 *   material.
 */
public fun BackdropEffectScope.vibrancy(
    @FloatRange(from = 0.0) amount: Float = 1.5f
) {
    if (amount == 1f) return
    colorFilter(if (amount == 1.5f) DefaultVibrancyFilter else colorControlsFilter(saturation = amount))
}

/**
 * Brightness / contrast / saturation in one pass.
 *
 * @param brightness additive, `0f` neutral, roughly `-1f..1f`.
 * @param contrast multiplicative around mid grey, `1f` neutral.
 * @param saturation `0f` greyscale, `1f` neutral, above that oversaturated.
 */
public fun BackdropEffectScope.colorControls(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f
) {
    if (brightness == 0f && contrast == 1f && saturation == 1f) return
    colorFilter(colorControlsFilter(brightness, contrast, saturation))
}

/** Scales the alpha of the refracted backdrop, letting the real background show through. */
public fun BackdropEffectScope.opacity(
    @FloatRange(from = 0.0, to = 1.0) alpha: Float
) {
    if (alpha == 1f) return
    colorFilter(
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, alpha, 0f
                )
            )
        )
    )
}

/**
 * Blends the backdrop towards [color] by the colour's own alpha — a tinted pane of glass.
 *
 * Unlike painting a translucent rectangle on top, this happens inside the effect chain, so a
 * following `lens()` refracts the *tinted* content and the tint bends around the rim with it.
 */
public fun BackdropEffectScope.tint(color: Color) {
    val amount = color.alpha
    if (amount <= 0f) return

    val keep = 1f - amount
    colorFilter(
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    keep, 0f, 0f, 0f, color.red * amount * 255f,
                    0f, keep, 0f, 0f, color.green * amount * 255f,
                    0f, 0f, keep, 0f, color.blue * amount * 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    )
}

private val DefaultVibrancyFilter: ColorFilter = colorControlsFilter(saturation = 1.5f)

private fun colorControlsFilter(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f
): ColorFilter {
    // Luminance weights of the sRGB primaries, spread over the off-diagonal so that desaturating
    // preserves perceived brightness.
    val inverseSaturation = 1f - saturation
    val r = 0.213f * inverseSaturation
    val g = 0.715f * inverseSaturation
    val b = 0.072f * inverseSaturation

    val translate = (0.5f - contrast * 0.5f + brightness) * 255f

    val cr = contrast * r
    val cg = contrast * g
    val cb = contrast * b
    val cs = contrast * saturation

    return ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                cr + cs, cg, cb, 0f, translate,
                cr, cg + cs, cb, 0f, translate,
                cr, cg, cb + cs, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}
