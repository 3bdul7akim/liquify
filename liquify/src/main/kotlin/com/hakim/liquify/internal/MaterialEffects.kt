package com.hakim.liquify.internal

import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.hakim.liquify.BaseBackdropEffectScope
import com.hakim.liquify.GlassMaterial
import com.hakim.liquify.GroupEffectScope
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Applies [rest]'s recipe — or the blend of it towards [pressed] by [fraction] — in GPU order:
 * vibrancy, blur, then the lens.
 *
 * The blend is worked out on the numbers, not on a blended copy of the material: this runs on every
 * frame of a press, and allocating a material per frame per pane to describe two floats moving would
 * be the one allocation on that path. The tint is not part of it; the element lays it over the glass
 * itself.
 */
internal fun BaseBackdropEffectScope.applyMaterial(
    rest: GlassMaterial,
    pressed: GlassMaterial?,
    fraction: Float
) {
    val f = if (pressed == null) 0f else fraction.fastCoerceIn(0f, 1f)
    val target = pressed ?: rest

    val saturation = lerp(rest.saturation, target.saturation, f)
    if (saturation != 1f) vibrancy(saturation)

    // A blur goes *under* the refraction: thick glass bends light that has already been scattered.
    val blurRadius = lerp(rest.blurRadius.toPx(), target.blurRadius.toPx(), f)
    if (blurRadius > 0f) blurPx(blurRadius)

    if (this is GroupEffectScope) {
        // The group has no size of its own to measure, only the union of its members, which says
        // nothing about any one of them. It refracts each member by the member's own size.
        recordMaterial(rest)
        return
    }

    val width = size.width
    val height = size.height
    // NaN-safe: an element that has not been measured yet has an unspecified size.
    if (!(width > 0f && height > 0f)) return

    val band = lerp(
        rest.refractionHeightPx(width, height, this),
        target.refractionHeightPx(width, height, this),
        f
    )
    val bend = lerp(
        rest.refractionAmountPx(width, height, this),
        target.refractionAmountPx(width, height, this),
        f
    )
    // The dome is a weight in the program, so a press can ease it in. Dispersion is a different
    // program altogether and can only switch.
    val depth = lerp(if (rest.depthEffect) 1f else 0f, if (target.depthEffect) 1f else 0f, f)
    val dispersion = if (f < 0.5f) rest.chromaticAberration else target.chromaticAberration
    lensPx(band, bend, depth, dispersion)
}

/**
 * Whether [other] runs the same chain as this material: everything [applyMaterial] reads. The tint
 * and the decoration are not part of it — the element lays the one, its decoration draws the other.
 */
internal fun GlassMaterial.sameEffectsAs(other: GlassMaterial): Boolean =
    blurRadius == other.blurRadius &&
        saturation == other.saturation &&
        refractionHeight == other.refractionHeight &&
        refractionAmount == other.refractionAmount &&
        refractionBySize == other.refractionBySize &&
        refractionHeightScale == other.refractionHeightScale &&
        refractionAmountScale == other.refractionAmountScale &&
        depthEffect == other.depthEffect &&
        chromaticAberration == other.chromaticAberration

/**
 * The refracting band this material gives an element of [width] × [height] pixels — see
 * [GlassMaterial] for the rule — with [density] the one the size is measured in.
 */
internal fun GlassMaterial.refractionHeightPx(width: Float, height: Float, density: Density): Float {
    val full = with(density) { refractionHeight.toPx() }
    val band = if (refractionBySize) refractionHeightForSize(width, height, full) else full
    return band * refractionHeightScale
}

/** The displacement this material gives an element of [width] × [height] pixels. */
internal fun GlassMaterial.refractionAmountPx(width: Float, height: Float, density: Density): Float {
    val full = with(density) { refractionAmount.toPx() }
    val bend = if (refractionBySize) refractionAmountForSize(width, height, full) else full
    return bend * refractionAmountScale
}

/** The band's share of the short side while an element is small. */
private const val HeightPerShortSide = 0.25f

/** How far a small round element bends, as a share of its short side. */
private const val RoundAmountPerShortSide = 0.375f

/** How far a small elongated element bends, as a share of its short side. */
private const val ElongatedAmountPerShortSide = 0.5f

/** The aspect ratio — long side over short — from which an element counts as fully elongated. */
private const val ElongatedFromAspect = 2f

/**
 * The refracting band of an element of [width] × [height], easing into [fullSize] as it grows.
 *
 * Small, it is a quarter of the short side: the band reaches half way to the middle, whatever the
 * size, so a small control keeps a flat centre. The short side, because it is the one the band can
 * run out of — a capsule's rim wraps its ends at the radius of its height. Large, it is [fullSize]:
 * real glass does not get thicker because the pane got wider, and a band that kept growing would turn
 * a sheet into a fisheye.
 *
 * Unit-agnostic: pixels in, pixels out.
 */
internal fun refractionHeightForSize(width: Float, height: Float, fullSize: Float): Float {
    val short = min(width, height)
    if (short <= 0f) return 0f
    return softMin(short * HeightPerShortSide, fullSize)
}

/**
 * How far an element of [width] × [height] bends its backdrop at the rim, easing into [fullSize].
 *
 * Three eighths of the short side for something round, half of it for something elongated, with a
 * smooth blend in between: a circle's rim closes in on a small middle from every side, so the same
 * pull drags the whole picture inwards and reads as a fisheye, where a capsule has its long axis to
 * spread it along.
 */
internal fun refractionAmountForSize(width: Float, height: Float, fullSize: Float): Float {
    val short = min(width, height)
    if (short <= 0f) return 0f
    val aspect = max(width, height) / short
    val elongation = smoothStep((aspect - 1f) / (ElongatedFromAspect - 1f))
    val bySize = short *
        (RoundAmountPerShortSide + (ElongatedAmountPerShortSide - RoundAmountPerShortSide) * elongation)
    return softMin(bySize, fullSize)
}

/**
 * The smaller of [value] and [cap], with the corner between them rounded: `(v⁻⁴ + c⁻⁴)^(-1/4)`.
 *
 * Within a few per cent of [value] while it is well below the cap, and of [cap] well above it, so an
 * element resizing across the corner never sees its refraction kink. The fourth power keeps the
 * corner tight — at the crossover the result is 16 % under both, a square law would take 29 %. It is
 * homogeneous, too: scaling both inputs scales the result, so the rule is the same at every density.
 */
internal fun softMin(value: Float, cap: Float): Float {
    if (value <= 0f || cap <= 0f) return 0f
    val v2 = value * value
    val c2 = cap * cap
    return value * cap / sqrt(sqrt(v2 * v2 + c2 * c2))
}

/** Hermite ease on 0..1, so the blend from round to elongated has no corner at either end. */
private fun smoothStep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
