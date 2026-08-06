/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.layout
import com.hakim.liquify.backdrops.LayerBackdrop
import com.hakim.liquify.effects.merge
import com.hakim.liquify.group.LocalGroupDefaults
import com.hakim.liquify.highlight.Highlight
import com.hakim.liquify.highlight.HighlightElement
import com.hakim.liquify.interaction.InteractiveHighlight
import com.hakim.liquify.interaction.rememberInteractiveHighlight
import com.hakim.liquify.internal.GlassNodeState
import com.hakim.liquify.internal.ShapeProvider
import com.hakim.liquify.material.GlassMaterial
import com.hakim.liquify.material.LocalGlassMaterial
import com.hakim.liquify.material.material
import com.hakim.liquify.shadow.InnerShadow
import com.hakim.liquify.shadow.InnerShadowElement
import com.hakim.liquify.shadow.Shadow
import com.hakim.liquify.shadow.ShadowElement
import kotlin.math.roundToInt

private val DefaultHighlight: () -> Highlight? = { Highlight.Default }
private val DefaultShadow: () -> Shadow? = { Shadow.Default }
private val DefaultOnDrawBackdrop: DrawScope.(DrawScope.() -> Unit) -> Unit = { it() }

/**
 * Turns this element into a pane of liquid glass.
 *
 * This is the only entry point the library has. Three things are resolved from context rather than
 * repeated at every call site:
 *
 * - **the backdrop**, from the enclosing [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup]
 *   or [ProvideBackdrop];
 * - **the material**, from [LocalGlassMaterial] — or from the group, so every member of one merged
 *   surface is made of the same glass without being told twice;
 * - **merging**, again from the group: a member does not have to remember to call `merge()`, nor to
 *   switch its own rim and shadow off so the group can draw one for the combined outline.
 *
 * ```
 * ProvideBackdrop(backdrop) {
 *     Column(Modifier.liquify(RoundedRectangle(28.dp))) { … }
 * }
 *
 * LiquidGlassGroup(backdrop) {
 *     Box(Modifier.size(88.dp).liquify(Capsule()))
 *     Box(Modifier.size(88.dp).liquify(Capsule()))
 * }
 * ```
 *
 * The element's own children draw on top of the glass, unclipped by it, so this is a *background*
 * treatment — put it on the container and lay out icons and text inside as usual.
 *
 * Everything degrades on its own: below API 33 the refraction is skipped, below API 31 the blur and
 * colour effects are too, and the element still renders with its rim, shadow and shape.
 *
 * For a hand-written effect stack, animatable rim and shadow, or the `onDraw*` hooks, use the
 * `effects` overload — it is the same modifier with everything opened up.
 *
 * @param shape the element's outline. [com.kyant.shapes.RoundedRectangle] and
 *   [com.kyant.shapes.Capsule] give the continuous-curvature corners the material is designed
 *   around.
 * @param material the glass recipe. Ignored inside a group, which supplies its own so the merged
 *   surface stays homogeneous.
 * @param backdrop overrides the inherited backdrop for this element only.
 * @param tint colour blended into the backdrop; overrides [GlassMaterial.tint] when specified.
 * @param gradientBlur ramps the blur up from nothing at the border rather than applying it evenly,
 *   and moves it above the refraction so the rim keeps a crisp lens — a frosted droplet rather than
 *   an evenly clouded one. Turns [GlassMaterial.gradientBlur] on; leave it `false` to take whatever
 *   the material already says.
 * @param highlight rim along the border, `null` for none. Ignored inside a group.
 * @param shadow drop shadow, `null` for none. Ignored inside a group.
 * @param innerShadow shadow cast inside the element, for a recessed look.
 * @param interaction an [InteractiveHighlight] to drive the touch reactions. Only needed when
 *   several elements must share one gesture; otherwise set [dragging] / [interactiveHighlight] and
 *   one is created for you.
 * @param dragging makes the element lean towards the finger with damped spring physics.
 *   `liquify(Capsule(), dragging = true)` is the whole setup.
 * @param stretching makes the element swell under the press and stretch along the direction it is
 *   being pulled. Off by default, and it needs [dragging]: an element that stays put has nothing to
 *   stretch towards, so `stretching = true` on its own does nothing.
 * @param interactiveHighlight lights the element up under the fingertip.
 * @param layerBlock transform applied to the element, inverted for the backdrop so the refraction
 *   stays anchored in screen space.
 * @param onDrawSurface drawn over the glass but under your content.
 * @throws IllegalStateException when no backdrop is available, explicitly or from context.
 */
@Composable
public fun Modifier.liquify(
    shape: Shape,
    material: GlassMaterial = LocalGlassMaterial.current,
    backdrop: Backdrop? = null,
    tint: Color = Color.Unspecified,
    gradientBlur: Boolean = false,
    highlight: Highlight? = Highlight.Default,
    shadow: Shadow? = Shadow.Default,
    innerShadow: InnerShadow? = null,
    interaction: InteractiveHighlight? = null,
    dragging: Boolean = interaction != null,
    stretching: Boolean = false,
    interactiveHighlight: Boolean = interaction != null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null
): Modifier {
    // Typed explicitly: Shape is a single-abstract-method interface, so an untyped lambda would be
    // SAM-convertible to it and the overloads would be ambiguous.
    val shapeProvider: () -> Shape = remember(shape) { { shape } }
    return liquify(
        shape = shapeProvider,
        material = material,
        backdrop = backdrop,
        tint = tint,
        gradientBlur = gradientBlur,
        highlight = highlight,
        shadow = shadow,
        innerShadow = innerShadow,
        interaction = interaction,
        dragging = dragging,
        stretching = stretching,
        interactiveHighlight = interactiveHighlight,
        layerBlock = layerBlock,
        onDrawSurface = onDrawSurface
    )
}

/**
 * [liquify] with a shape that can change every frame.
 *
 * Use this when the outline is animated — a corner radius easing open, a capsule stretching into a
 * rectangle. The lambda is read during draw, so it costs a redraw rather than a recomposition.
 */
@Composable
public fun Modifier.liquify(
    shape: () -> Shape,
    material: GlassMaterial = LocalGlassMaterial.current,
    backdrop: Backdrop? = null,
    tint: Color = Color.Unspecified,
    gradientBlur: Boolean = false,
    highlight: Highlight? = Highlight.Default,
    shadow: Shadow? = Shadow.Default,
    innerShadow: InnerShadow? = null,
    interaction: InteractiveHighlight? = null,
    dragging: Boolean = interaction != null,
    stretching: Boolean = false,
    interactiveHighlight: Boolean = interaction != null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null
): Modifier {
    val group = LocalGroupDefaults.current

    // A member of a merged surface hands its rim and shadow to the group, which draws one of each
    // for the combined outline. Drawing them here as well would trace the very seam that merging
    // exists to dissolve.
    val isMember = group != null && group.mergeAmount > 0f

    val effects: BackdropEffectScope.() -> Unit = remember(group, material, tint, gradientBlur) {
        if (group != null) {
            val groupEffects = group.effects
            val mergeAmount = group.mergeAmount
            val block: BackdropEffectScope.() -> Unit = {
                groupEffects()
                if (mergeAmount > 0f) merge(mergeAmount)
            }
            block
        } else {
            var resolved = material
            if (tint.isSpecified) resolved = resolved.copy(tint = tint)
            if (gradientBlur) resolved = resolved.copy(gradientBlur = true)
            val block: BackdropEffectScope.() -> Unit = { material(resolved) }
            block
        }
    }

    // Hoisted out of the conditionals below so joining or leaving a group does not change how many
    // remember slots this call occupies.
    val highlightProvider: (() -> Highlight?)? = remember(highlight) { highlight?.let { { it } } }
    val shadowProvider: (() -> Shadow?)? = remember(shadow) { shadow?.let { { it } } }
    val innerShadowProvider: (() -> InnerShadow?)? =
        remember(innerShadow) { innerShadow?.let { { it } } }

    // Resolved here rather than in the effects overload, because a merged member needs the object
    // to drive its own layout-based drag.
    val touch: InteractiveHighlight? = interaction
        ?: if (dragging || interactiveHighlight) rememberInteractiveHighlight() else null

    // Stretching rides on dragging: an element that does not follow the finger has nothing to
    // stretch towards, so the two switches are an AND rather than two independent ones.
    val stretches = dragging && stretching

    val memberDrag = if (isMember && dragging && touch != null) {
        // The effects overload is told dragging = false below, so it will not claim the gesture.
        touch.consumesDrag = true
        // Translation goes through layout so the group hears about it; the swell is a plain layer
        // scale, because the group applies the very same factors to its distance field and the two
        // therefore agree without layout having to run at all.
        Modifier
            .memberDragOffset(touch)
            .graphicsLayer {
                if (stretches && size.width > 0f && size.height > 0f) {
                    val scale = touch.dragScale(size, this)
                    scaleX = scale.x
                    scaleY = scale.y
                }
            }
    } else {
        Modifier
    }

    val modifier = this
        .then(memberDrag)
        .liquify(
            shape = shape,
            effects = effects,
            backdrop = backdrop,
            highlight = if (isMember) null else highlightProvider,
            shadow = if (isMember) null else shadowProvider,
            innerShadow = innerShadowProvider,
            layerBlock = layerBlock,
            onDrawSurface = onDrawSurface,
            interaction = touch,
            // A member's drag is done above, in layout. Doing it here as well would move the
            // element twice.
            dragging = dragging && !isMember,
            stretching = stretches,
            // Drawn by the element itself even inside a group, so it stays clipped to that
            // element's own outline instead of running across the merged body onto its neighbours.
            interactiveHighlight = interactiveHighlight
        )

    // The nested call resolved the flag against the `dragging` *it* was handed, which is false for
    // a member — a member's drag runs through layout up above instead. The group reads this flag to
    // decide whether to swell the member's box, so it has to carry what this call site asked for,
    // not what the inner one was told. Identical value for a non-member.
    if (touch != null) {
        touch.stretches = stretches
    }

    return modifier
}

/**
 * [liquify] with the effect stack written out by hand — the full form of the modifier.
 *
 * It answers four questions in one place: *what is behind the glass, what shape is it, does it cast
 * a shadow, and what is it made of.*
 *
 * ```
 * Modifier.liquify(
 *     shape = { RoundedRectangle(28.dp) },
 *     effects = {
 *         vibrancy()
 *         blur(8.dp.toPx())
 *         lens(refractionHeight = 24.dp.toPx(), refractionAmount = 32.dp.toPx())
 *     },
 *     highlight = { Highlight.Default.copy(alpha = progress) },
 *     onDrawSurface = { drawRect(containerColor) }
 * )
 * ```
 *
 * Reach for this when the material overload is not enough: a custom effect order, effects that
 * animate per frame, a rim or shadow driven by a live value, or the draw hooks. The two overloads
 * interoperate freely and can sit side by side in the same tree.
 *
 * Unlike the material overload, this one does **not** inherit the group's material or join its
 * merged surface automatically — an explicit effect stack means you are in control. Call
 * [merge][com.hakim.liquify.effects.merge] inside [effects] to opt in. The backdrop is still
 * resolved from context when [backdrop] is `null`.
 *
 * @param shape the element's outline, as a lambda so an animating shape does not force
 *   recomposition.
 * @param effects the material itself — the ordered stack of `blur()`, `vibrancy()`, `lens()`,
 *   `tint()`, `merge()` and friends. Order is significant: it is the order the GPU applies them in.
 * @param backdrop what is behind the glass. `null` resolves it from the enclosing group or
 *   [ProvideBackdrop].
 * @param highlight specular rim along the border. Pass `null` for none.
 * @param shadow drop shadow that lifts the glass off the content. Pass `null` for none.
 * @param innerShadow shadow cast inside the element, for a recessed look.
 * @param layerBlock transform applied to the element. Declaring scale and rotation *here* rather
 *   than with a separate `graphicsLayer` lets the backdrop invert the transform and stay anchored
 *   in screen space, so a scaling element does not drag its refraction along.
 * @param exportedBackdrop records this element's result into a [LayerBackdrop], so another glass
 *   element can refract this one.
 * @param onDrawBehind drawn under the glass.
 * @param onDrawBackdrop wraps the backdrop draw, for filtering or transforming it per element.
 * @param onDrawSurface drawn over the glass but under the content — the place for a tint or a
 *   pressed-state wash that should not be refracted.
 * @param onDrawFront drawn over everything, including the content.
 * @param interaction an [InteractiveHighlight] to drive the touch reactions. Only needed when
 *   several elements must share one gesture — a tab bar lighting up from its indicator, say.
 * @param dragging whether the element leans towards the finger. Applied before [layerBlock], so
 *   your own transform wins on any property it sets.
 * @param stretching whether it also swells and stretches along the direction of travel. Off by
 *   default, and only meaningful together with [dragging].
 * @param interactiveHighlight whether the element lights up under the fingertip.
 * @throws IllegalStateException when no backdrop is available, explicitly or from context.
 */
@Composable
public fun Modifier.liquify(
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    backdrop: Backdrop? = null,
    highlight: (() -> Highlight?)? = DefaultHighlight,
    shadow: (() -> Shadow?)? = DefaultShadow,
    innerShadow: (() -> InnerShadow?)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
    interaction: InteractiveHighlight? = null,
    dragging: Boolean = interaction != null,
    stretching: Boolean = false,
    interactiveHighlight: Boolean = interaction != null
): Modifier {
    val group = LocalGroupDefaults.current
    val inherited = LocalBackdrop.current
    val resolvedBackdrop = backdrop ?: group?.backdrop ?: inherited ?: noBackdrop()

    // Creating the state here is what lets a call site opt in with a single boolean instead of
    // hoisting an object it has no other use for.
    val touch: InteractiveHighlight? = interaction
        ?: if (dragging || interactiveHighlight) rememberInteractiveHighlight() else null

    if (touch != null) {
        // Claim movement only when the element actually follows the finger, so a glow-only element
        // inside a list still lets the list scroll.
        touch.consumesDrag = dragging
        touch.stretches = dragging && stretching
    }

    val shapeProvider = ShapeProvider(shape)
    val nodeState = GlassNodeState()
    val effectiveLayerBlock: (GraphicsLayerScope.() -> Unit)? =
        if (touch != null && dragging) {
            {
                with(touch) { applyDragTransform() }
                layerBlock?.invoke(this)
            }
        } else {
            layerBlock
        }
    return this
        .then(if (effectiveLayerBlock != null) Modifier.graphicsLayer(effectiveLayerBlock) else Modifier)
        .then(
            if (innerShadow != null) {
                InnerShadowElement(shapeProvider, innerShadow, nodeState)
            } else {
                Modifier
            }
        )
        .then(if (shadow != null) ShadowElement(shapeProvider, shadow, nodeState) else Modifier)
        .then(
            if (highlight != null) HighlightElement(shapeProvider, highlight, nodeState) else Modifier
        )
        .then(
            LiquifyElement(
                backdrop = resolvedBackdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                nodeState = nodeState,
                layerBlock = effectiveLayerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront,
                interaction = touch
            )
        )
        // After the backdrop node, so the glow lands on the glass and inside its clip.
        .then(
            if (touch != null && interactiveHighlight) touch.modifier else Modifier
        )
        // Input is attached whenever there is an interaction at all: a merged member drives its
        // drag from layout and its glow from the group, so both flags are off here yet it still
        // needs the gesture.
        .then(touch?.gestureModifier ?: Modifier)
}

/**
 * Applies the drag as a *layout* offset instead of a layer transform.
 *
 * This is the whole reason merged members can be dragged at all. A graphics-layer transform does
 * not re-run placement, so `onGloballyPositioned` never fires and the group keeps rebuilding its
 * merged surface from stale bounds — the content slides while the glass stays put. Reading the
 * drag state inside the placement lambda invalidates *placement*, so the group is told about every
 * frame of the movement and the surface stays locked to the element.
 */
private fun Modifier.memberDragOffset(interaction: InteractiveHighlight): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            val translation = interaction.dragTranslation(
                Size(placeable.width.toFloat(), placeable.height.toFloat())
            )
            placeable.place(translation.x.roundToInt(), translation.y.roundToInt())
        }
    }

private fun noBackdrop(): Nothing =
    error(
        "liquify() has no backdrop. Pass one explicitly, or wrap the subtree in " +
            "ProvideBackdrop { } or LiquidGlassGroup { }."
    )
