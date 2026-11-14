package com.hakim.liquify.internal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.isSpecified
import org.intellij.lang.annotations.Language

/**
 * How far an element's touch light reaches, as a multiple of its short side. The hotspot is at full
 * strength within half of that.
 */
internal const val TouchLightReach: Float = 1.5f

/**
 * The colour of the touch light inside glass of [tint]: the light seen through the colour.
 *
 * Clear glass glows in [light] itself. Coloured glass glows in its own colour, as far as it is
 * coloured — a tint at full strength in the pure colour, one at half strength halfway between — so a
 * press makes the tint brighter instead of laying a white film over it. Mixed straight in sRGB, the
 * way the glass lays its tint over what it refracts. The light's own alpha plays no part: how
 * brightly it shines is the strength's business.
 */
internal fun touchLightColor(light: Color, tint: Color): Color {
    val base = if (light.isSpecified) light.inSrgb() else Color.White
    if (!tint.isSpecified || tint.alpha <= 0f) return base.copy(alpha = 1f)
    val colour = tint.inSrgb()
    val share = colour.alpha.coerceAtMost(1f)
    return Color(
        red = (base.red + (colour.red - base.red) * share).coerceIn(0f, 1f),
        green = (base.green + (colour.green - base.green) * share).coerceIn(0f, 1f),
        blue = (base.blue + (colour.blue - base.blue) * share).coerceIn(0f, 1f),
    )
}

/** This colour in sRGB, without a conversion — and the allocation it makes — when it already is. */
private fun Color.inSrgb(): Color =
    if (colorSpace == ColorSpaces.Srgb) this else convert(ColorSpaces.Srgb)

/**
 * How strongly the touch light falls on one pixel, shared by both programs that draw it — a pane's
 * own glow and a merged body's — so the two cannot drift apart.
 *
 * `light` is `xy` the lit point and `z` the reach, in the program's own pixels, and `w` the strength:
 * a gentle lift of the whole element at just over half the strength, plus a hotspot at the full
 * strength within half the reach of the finger, fading out at the reach. With the default strength of
 * 0.15 that is 0.08 everywhere and 0.23 under the finger. A strength of zero — every member nobody
 * holds — returns at once; it is a uniform, so that branch is coherent across the draw.
 *
 * The strength is all that scales the light. Its colour arrives opaque: a colour uniform reaches the
 * program unpremultiplied, so a strength carried in its alpha would never dim the colour itself.
 */
@Language("AGSL")
internal const val TOUCH_LIGHT_FUNCTION: String = """
half touchLight(float2 coord, float4 light) {
    if (light.w <= 0.0) {
        return half(0.0);
    }
    float spot = smoothstep(light.z, light.z * 0.5, distance(coord, light.xy));
    return half(light.w * (0.08 / 0.15 + spot));
}"""
