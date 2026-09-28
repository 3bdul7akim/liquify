package com.hakim.liquify.effects

import androidx.annotation.FloatRange
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.GroupEffectScope
import com.hakim.liquify.isRuntimeShaderSupported
import kotlin.math.max
import kotlin.math.min

/** The refracting band's thickness, as a share of the element's short side. */
private const val HeightPerShortSide = 0.25f

/** How far a round element bends its backdrop, as a share of its short side. */
private const val RoundAmountPerShortSide = 0.375f

/** How far an elongated element bends its backdrop, as a share of its short side. */
private const val ElongatedAmountPerShortSide = 0.5f

/** The aspect ratio (long side over short side) from which an element counts as fully elongated. */
private const val ElongatedFromAspect = 2f

/**
 * The refracting band's thickness for an element of [width] × [height], in the same unit as the two.
 *
 * A quarter of the short side: the band reaches half way from the rim to the middle, whatever the
 * size. A fixed band is the problem this solves — the same 24 dp that reads as glass on a 56 dp field
 * turns a 32 dp button into a lens with no flat middle left, and barely registers on a large panel.
 * Scaling with the element keeps the proportions, and so the look, identical at every size.
 *
 * The short side, because it is the one the band can run out of: a capsule's rim wraps its ends at
 * the radius of its height, and a wide one is no thicker there for being long.
 *
 * Unit-agnostic — pixels in, pixels out, or dp in, dp out — because the rule is a pure proportion.
 *
 * ```
 * refractionHeightForSize(32f, 32f)   // 8
 * refractionHeightForSize(200f, 48f)  // 12
 * ```
 */
public fun refractionHeightForSize(width: Float, height: Float): Float {
    val short = min(width, height)
    return if (short > 0f) short * HeightPerShortSide else 0f
}

/**
 * How far an element of [width] × [height] bends its backdrop at the rim, in the same unit.
 *
 * Three eighths of the short side for something round, half of it for something elongated, and a
 * smooth blend in between — measured by the aspect ratio, fully elongated from 2 : 1 on.
 *
 * The shape matters as well as the size. A circle's rim surrounds a small middle from every side,
 * so a strong displacement drags the whole picture inwards and the element reads as a fisheye; a
 * capsule has its long axis to spread the same pull along, and carries more before it does. That is
 * why a 32 dp circle wants 12 and a 48 dp tall capsule 24 — twice the bend for one and a half times
 * the size.
 *
 * ```
 * refractionAmountForSize(32f, 32f)   // 12  — round
 * refractionAmountForSize(48f, 48f)   // 18  — round, larger
 * refractionAmountForSize(200f, 48f)  // 24  — elongated
 * ```
 */
public fun refractionAmountForSize(width: Float, height: Float): Float {
    val short = min(width, height)
    if (short <= 0f) return 0f
    val aspect = max(width, height) / short
    val elongation = smoothStep((aspect - 1f) / (ElongatedFromAspect - 1f))
    return short * (RoundAmountPerShortSide +
        (ElongatedAmountPerShortSide - RoundAmountPerShortSide) * elongation)
}

/** Hermite ease on 0..1, so the blend from round to elongated has no corner at either end. */
private fun smoothStep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * [lens] with its two inputs taken from the element's own size — see [refractionHeightForSize] and
 * [refractionAmountForSize] — and scaled by [heightScale] and [amountScale].
 *
 * Use it where one recipe covers controls of very different sizes: a 32 dp icon button and a
 * 56 dp search field then refract in the same proportions, instead of the one fixed pair being
 * right for one of them and wrong for the other. The effect follows the element as it resizes, so a
 * pane that grows open keeps a band in proportion the whole way.
 *
 * Inside a [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup] every member is refracted
 * by its *own* size, blended across the bridges exactly like the members' colours — a button looks
 * the same merged as it does on its own.
 *
 * No-op below API 33, and for shapes that are not rounded rectangles.
 *
 * @param heightScale multiplies the band the size gives, `1f` for the rule as it is.
 * @param amountScale multiplies the displacement the size gives.
 */
public fun BackdropEffectScope.lensBySize(
    @FloatRange(from = 0.0) heightScale: Float = 1f,
    @FloatRange(from = 0.0) amountScale: Float = 1f,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false
) {
    if (!isRuntimeShaderSupported()) return
    if (heightScale <= 0f || amountScale <= 0f) return

    if (this is GroupEffectScope) {
        // The group has no size of its own to measure — only the union of its members, which says
        // nothing about any one of them. It resolves each member's refraction itself.
        recordLensBySize(heightScale, amountScale, depthEffect)
        return
    }

    // NaN-safe: an element that has not been measured yet has an unspecified size.
    val width = size.width
    val height = size.height
    if (!(width > 0f && height > 0f)) return

    lens(
        refractionHeight = refractionHeightForSize(width, height) * heightScale,
        refractionAmount = refractionAmountForSize(width, height) * amountScale,
        depthEffect = depthEffect,
        chromaticAberration = chromaticAberration
    )
}
