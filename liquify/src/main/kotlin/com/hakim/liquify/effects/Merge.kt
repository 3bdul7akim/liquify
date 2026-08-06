package com.hakim.liquify.effects

import androidx.annotation.FloatRange
import androidx.compose.ui.unit.Dp
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.BaseBackdropEffectScope

/**
 * Makes this element part of the merged surface of the enclosing
 * [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup] — the gooey / metaball behaviour.
 *
 * Members of a group stop rendering their own glass. Instead the group evaluates every member's
 * signed distance field, blends them with a smooth minimum and renders **one** surface. Two
 * elements drifting towards each other therefore grow a liquid bridge and fuse into a single body
 * of glass, then pinch apart again as they separate — with refraction and rim light following the
 * combined outline the whole way.
 *
 * ```
 * LiquidGlassGroup(backdrop = backdrop) {
 *     Box(
 *         Modifier
 *             .offset { IntOffset(x.roundToInt(), 0) }
 *             .size(72.dp)
 *             .liquify(
 *                 shape = { Capsule() },
 *                 effects = {
 *                     blur(4f)
 *                     lens(24f, 32f)
 *                     merge(1f)
 *                 },
 *                 backdrop = backdrop
 *             )
 *     )
 *     // …a second element, same treatment
 * }
 * ```
 *
 * Outside a group this call does nothing, so the same component works standalone.
 *
 * @param amount blend strength relative to the element's own size. `0f` disables merging, `1f`
 *   (the default) starts bridging at roughly half the element's short side, larger values reach
 *   further and produce a fatter, more viscous bridge.
 */
public fun BackdropEffectScope.merge(
    @FloatRange(from = 0.0) amount: Float = 1f
) {
    if (amount <= 0f) return
    val shortSide = size.minDimension
    if (shortSide <= 0f || shortSide.isNaN()) return
    (this as BaseBackdropEffectScope).mergeRadius = amount * shortSide * 0.5f
}

/**
 * Merges with an explicit reach instead of one relative to the element size.
 *
 * Prefer this when several differently sized elements must bridge consistently — the blend radius
 * is then the same for all of them.
 *
 * @param radius distance over which neighbouring elements fuse.
 */
public fun BackdropEffectScope.merge(radius: Dp) {
    val px = radius.toPx()
    if (px <= 0f) return
    (this as BaseBackdropEffectScope).mergeRadius = px
}
