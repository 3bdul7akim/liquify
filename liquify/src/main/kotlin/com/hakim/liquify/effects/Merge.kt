package com.hakim.liquify.effects

import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
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

/**
 * Colours this element's share of the enclosing group's merged surface.
 *
 * A merged group renders **one** body of glass, so a member cannot simply tint itself: its own
 * `tint()` would be drawn clipped to its own box and cut a seam straight across the bridge that
 * merging exists to create. This hands the colour to the group instead, which mixes the members'
 * tints with **the very same weights it blends their distance fields with**. Two differently
 * coloured panes therefore stay pure while they are apart, and flow into one another exactly as
 * far as their surfaces do — the neck of a bridge carries the mixture of both.
 *
 * ```
 * LiquidGlassGroup(backdrop) {
 *     Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
 *         Box(Modifier.size(72.dp).liquify(Capsule(), tint = Color.Red))    // ← calls this
 *         Box(Modifier.size(72.dp).liquify(Capsule(), tint = Color.Blue))   //    for you
 *     }
 * }
 * ```
 *
 * The material overload of `liquify` already does this for you: inside a group its `tint`
 * argument (or its material's tint) is routed here rather than into the effect stack. Call it
 * yourself only from a hand-written `effects` block.
 *
 * [color]'s **alpha is the strength** of the tint, exactly as in
 * [GlassMaterial.tint][com.hakim.liquify.material.GlassMaterial.tint] — the colour is blended into
 * the refracted backdrop, not painted over it, so the glass stays glass. Mixing happens in
 * premultiplied space, which is what lets an untinted neighbour dilute a colour towards clear
 * glass instead of dragging it towards black.
 *
 * Outside a group, below API 33, or with an unspecified or fully transparent [color], this does
 * nothing — so the same component still works standalone.
 */
public fun BackdropEffectScope.mergeTint(color: Color) {
    if (!color.isSpecified || color.alpha <= 0f) return
    (this as BaseBackdropEffectScope).mergeTintColor = color
}
