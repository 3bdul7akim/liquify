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
import androidx.compose.ui.graphics.isSpecified
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.BaseBackdropEffectScope
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
    colorFilter(
        (this as BaseBackdropEffectScope).colorFilters.controls(
            brightness = 0f,
            contrast = 1f,
            saturation = amount
        )
    )
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
    colorFilter(
        (this as BaseBackdropEffectScope).colorFilters.controls(brightness, contrast, saturation)
    )
}

/** Scales the alpha of the refracted backdrop, letting the real background show through. */
public fun BackdropEffectScope.opacity(
    @FloatRange(from = 0.0, to = 1.0) alpha: Float
) {
    if (alpha == 1f) return
    colorFilter((this as BaseBackdropEffectScope).colorFilters.opacity(alpha))
}

/**
 * Blends the backdrop towards [color] by the colour's own alpha — a tinted pane of glass.
 *
 * Unlike painting a translucent rectangle on top, this happens inside the effect chain, so a
 * following `lens()` refracts the *tinted* content and the tint bends around the rim with it.
 */
public fun BackdropEffectScope.tint(color: Color) {
    // Unspecified is not a colour, and its alpha is NaN — which no comparison rejects, so it has
    // to be excluded by name rather than by strength.
    if (!color.isSpecified || color.alpha <= 0f) return
    colorFilter((this as BaseBackdropEffectScope).colorFilters.tint(color))
}

/**
 * The colour-matrix filters one effect scope has built, kept until their inputs change.
 *
 * Each of these is a `ColorMatrixColorFilter` wrapping a `ColorMatrix` wrapping a twenty-element
 * `FloatArray` — three allocations, and a platform filter built from them on first use. An effects
 * block re-runs whenever anything it reads changes, which for glass driven by a press or a
 * transition is *every frame*, so a stack of `vibrancy()` + `tint()` was allocating six objects a
 * frame per element to describe a colour transform that had not moved.
 *
 * One slot per effect rather than a map: an effects block calls each of them at most once, and the
 * whole point is to avoid allocating on this path — a lookup key would be one more object.
 *
 * Only ever touched from the effect application, which runs on the main thread.
 */
internal class ColorFilterCache {

    private var controlsBrightness = Float.NaN
    private var controlsContrast = Float.NaN
    private var controlsSaturation = Float.NaN
    private var controlsFilter: ColorFilter? = null

    private var opacityAlpha = Float.NaN
    private var opacityFilter: ColorFilter? = null

    private var tintColor: Color = Color.Unspecified
    private var tintFilter: ColorFilter? = null

    fun controls(brightness: Float, contrast: Float, saturation: Float): ColorFilter {
        val cached = controlsFilter
        if (cached != null &&
            brightness == controlsBrightness &&
            contrast == controlsContrast &&
            saturation == controlsSaturation
        ) {
            return cached
        }
        val built = colorControlsFilter(brightness, contrast, saturation)
        controlsBrightness = brightness
        controlsContrast = contrast
        controlsSaturation = saturation
        controlsFilter = built
        return built
    }

    fun opacity(alpha: Float): ColorFilter {
        val cached = opacityFilter
        if (cached != null && alpha == opacityAlpha) return cached
        val built = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, alpha, 0f
                )
            )
        )
        opacityAlpha = alpha
        opacityFilter = built
        return built
    }

    fun tint(color: Color): ColorFilter {
        val cached = tintFilter
        if (cached != null && tintColor.isSpecified && color == tintColor) return cached
        val amount = color.alpha
        val keep = 1f - amount
        val built = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    keep, 0f, 0f, 0f, color.red * amount * 255f,
                    0f, keep, 0f, 0f, color.green * amount * 255f,
                    0f, 0f, keep, 0f, color.blue * amount * 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        tintColor = color
        tintFilter = built
        return built
    }

    fun clear() {
        controlsBrightness = Float.NaN
        controlsContrast = Float.NaN
        controlsSaturation = Float.NaN
        controlsFilter = null

        opacityAlpha = Float.NaN
        opacityFilter = null

        tintColor = Color.Unspecified
        tintFilter = null
    }
}

private fun colorControlsFilter(
    brightness: Float,
    contrast: Float,
    saturation: Float
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
