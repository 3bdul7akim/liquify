/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.effects

import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.GroupEffectScope
import com.hakim.liquify.internal.BlendEffect
import com.hakim.liquify.internal.GRADIENT_BLUR_SHADER
import com.hakim.liquify.internal.RuntimeShaderEffect
import com.hakim.liquify.internal.chain
import com.hakim.liquify.internal.resolveCornerRadii
import com.hakim.liquify.isRenderEffectSupported
import com.hakim.liquify.isRuntimeShaderSupported

/**
 * Gaussian blur of the backdrop, in pixels.
 *
 * Call this *before* `lens()` so the refraction bends already-blurred content — that ordering is
 * what makes the rim read as thick glass rather than as a blurred sticker.
 *
 * Blurring samples outside the element, so this grows [BackdropEffectScope.padding] whenever the
 * result could show the layer's edge. `lens()` then spends that padding again. No-op below API 31.
 *
 * @param radius blur sigma in pixels; use `16.dp.toPx()` inside the effects block.
 * @param edgeTreatment how to sample beyond the recorded backdrop. [TileMode.Clamp] stretches the
 *   edge pixels and needs no padding; [TileMode.Decal] fades to transparent and does.
 */
public fun BackdropEffectScope.blur(
    @FloatRange(from = 0.0) radius: Float,
    edgeTreatment: TileMode = TileMode.Clamp
) {
    if (!isRenderEffectSupported()) return
    if (radius <= 0f) return

    if (edgeTreatment != TileMode.Clamp || renderEffect != null) {
        if (radius > padding) {
            padding = radius
        }
    }

    renderEffect = BlurEffect(renderEffect, radius, radius, edgeTreatment)
}

/** Width of the ramp from sharp to blurred. Fixed, and independent of the blur radius. */
private val DefaultFadeWidth: Dp = 15.dp

/**
 * Intermediate blurs the ramp is built from by default, and the ceiling on them.
 *
 * Two, and the reason is measured rather than chosen. Scrolling seven of these at once on a
 * Galaxy S21 FE, with a warm-up pass before each reading:
 *
 * | steps | janky frames | median frame |
 * | --- | --- | --- |
 * | 1 | 7.9 % | 17 ms |
 * | 2 | 9.3 % | 16 ms |
 * | 3 | 43 % | 32 ms |
 * | 4 | 57 % | 44 ms |
 *
 * The second stop is free and the third is not — the cost is a cliff, not a slope, which suggests
 * a composite budget somewhere in HWUI rather than the arithmetic. So the default takes the step
 * that costs nothing, and anything above it is an explicit decision made against a real budget.
 */
private const val DefaultSteps = 2
private const val MaxSteps = 6

/**
 * Blur that only exists in the middle of the element, ramping up from nothing at the border.
 *
 * A frosted droplet is not uniformly cloudy: its rim stays clear and works as a lens, and only the
 * middle is frosted. [blur] cannot express that, because a Gaussian blur is one strength
 * everywhere. This runs the backdrop through two branches — one blurred, one untouched — masks each
 * with the complement of the other and adds them back together, so the strength of the blur follows
 * the element's own outline.
 *
 * **Call this *after* `lens()`,** which is the opposite of where [blur] goes:
 *
 * ```
 * effects = {
 *     vibrancy()
 *     lens(refractionHeight = 22.dp.toPx(), refractionAmount = 30.dp.toPx())
 *     gradientBlur(radius = 20.dp.toPx())
 * }
 * ```
 *
 * A uniform blur belongs *under* the refraction, because thick glass bends light that has already
 * been scattered. A gradient blur is the other way round, and refraction is the reason: `lens()`
 * samples *inwards* by up to `refractionAmount`, so a rim pixel takes its colour from deep inside
 * the element. Blur that interior first and the rim fetches the soft version — the crisp edge the
 * effect exists for is dragged away by the very lens it is meant to show off, and widening
 * `refractionHeight` visibly eats into the blur. Applied afterwards, the lens bends a sharp
 * backdrop and this only touches what is left in the middle.
 *
 * Use it *instead of* [blur], not alongside it. Unlike [blur] it needs no recorded halo of its own:
 * the blurred branch is masked away before it reaches the border, so there is nothing out there for
 * it to sample.
 *
 * Falls back to a plain [blur] below API 33, for shapes that are not rounded rectangles, and inside
 * a [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup], whose merged field is not a
 * rounded rectangle either and has no single outline to measure against.
 *
 * @param radius blur sigma in pixels at full strength, the same value [blur] takes.
 * @param fadeWidth how far in from the border the ramp from sharp to fully blurred takes, in
 *   pixels. A fixed distance that nothing else touches: raising [radius] makes the middle cloudier
 *   without moving the band, and the clear rim stays exactly where it was put. Smoothness is
 *   [steps]' job, not this one's.
 * @param clearWidth an optional dead band at the very border that stays completely sharp before the
 *   ramp starts. Zero by default, so the fade begins at the outline itself.
 * @param steps how many intermediate blurs the ramp is built from. One is a plain cross-fade
 *   between sharp and fully blurred, which never looks gradual whatever the easing; each step above
 *   that adds a copy at an intermediate radius so the kernel genuinely grows across the band.
 *   **Measure before raising it.** Going past two was a cliff rather than a slope on the device
 *   this was profiled on — a third stop took a scrolling screenful of these from 9 % janky frames
 *   to 43 %, and a fourth to 57 %. Worth it for one large surface, ruinous for a list of them.
 * @param edgeTreatment how the blurred branches sample beyond the recorded backdrop.
 */
public fun BackdropEffectScope.gradientBlur(
    @FloatRange(from = 0.0) radius: Float,
    @FloatRange(from = 0.0) fadeWidth: Float = DefaultFadeWidth.toPx(),
    @FloatRange(from = 0.0) clearWidth: Float = 0f,
    @IntRange(from = 1, to = 6) steps: Int = DefaultSteps,
    edgeTreatment: TileMode = TileMode.Clamp
) {
    if (!isRenderEffectSupported()) return
    if (radius <= 0f) return

    // Split guards rather than one condition: each is the form lint can follow to prove the
    // API-33 calls below are reachable only where they exist.
    if (!isRuntimeShaderSupported()) return blur(radius, edgeTreatment)
    if (this is GroupEffectScope) return blur(radius, edgeTreatment)

    val cornerRadii =
        shape.resolveCornerRadii(size, layoutDirection, this) ?: return blur(radius, edgeTreatment)

    val stopCount = steps.coerceIn(1, MaxSteps)

    // Snapshot: every branch has to hang off the state of the chain as it was, not off what this
    // function is building. Passing the identical instance to each of them also gives Skia one
    // shared node to evaluate rather than a copy per branch.
    val base = renderEffect

    // Deliberately does not grow `padding`. The blurred branches are masked away before they reach
    // the border, so none of them samples outside the recorded area — and because this runs after
    // `lens()` has already spent whatever halo was reserved, the padding read here is the one the
    // layer is really recorded with, which is what puts the mask exactly under the outline.
    var combined: RenderEffect? = null
    for (stop in 0..stopCount) {
        val stopRadius = radius * stop / stopCount
        val blurredBase =
            if (stopRadius > 0f) {
                BlurEffect(base, stopRadius, stopRadius, edgeTreatment)
            } else {
                base
            }

        val weights = obtainRuntimeShader("liquify.gradientBlur.$stop", GRADIENT_BLUR_SHADER).apply {
            setFloatUniform("size", size.width, size.height)
            setFloatUniform("offset", -padding, -padding)
            setFloatUniform("cornerRadii", cornerRadii)
            setFloatUniform("clearWidth", clearWidth)
            setFloatUniform("fadeWidth", fadeWidth)
            setFloatUniform("stopIndex", stop.toFloat())
            setFloatUniform("stopCount", stopCount.toFloat())
        }

        val branch = blurredBase.chain(RuntimeShaderEffect(weights, "content"))
        // Plus, not SrcOver: the weights are a partition of unity, so adding the branches
        // reproduces the original wherever they overlap. SrcOver would stack them as layers
        // instead and go wrong the moment the backdrop is not fully opaque.
        combined =
            if (combined == null) branch
            else BlendEffect(combined, branch, android.graphics.BlendMode.PLUS)
    }

    renderEffect = combined
}
