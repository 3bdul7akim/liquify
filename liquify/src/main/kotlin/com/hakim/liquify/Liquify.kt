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
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.layout
import com.hakim.liquify.internal.GlassDecorationElement
import com.hakim.liquify.internal.GlassNodeState
import com.hakim.liquify.internal.GlowElement
import com.hakim.liquify.internal.LiquifyElement
import com.hakim.liquify.internal.LocalGroupDefaults
import com.hakim.liquify.internal.ShapeProvider
import com.hakim.liquify.internal.flatSurface
import kotlin.math.roundToInt

/**
 * Turns this element into a pane of liquid glass.
 *
 * An element says only what it is — its shape, maybe a colour, maybe how it reacts to the finger.
 * Everything else it inherits, element → [LiquidGlassGroup] → [LiquifyTheme]: the backdrop, the
 * material with its rim and shadows, the theme, the look. And it adapts on its own:
 *
 * - **in a [LiquidGlassGroup]** it merges with its neighbours into one body of glass;
 * - **under [GlassLook.Frosted]** it is blurred glass without a lens, and **under
 *   [GlassLook.Solid]** a plain shape in its [tint], with nothing sampled;
 * - **at every size** its refraction is in proportion to it — see [GlassMaterial];
 * - **on every API level** it does what the device can: no lens below 33, no blur below 31.
 *
 * ```
 * LiquifyTheme(backdrop = backdrop) {
 *     Row(Modifier.liquify(Capsule())) { … }
 *     Box(Modifier.liquify(CircleShape, tint = accent, dragging = true, interactiveHighlight = true))
 * }
 * ```
 *
 * The element's children draw on top of the glass, clipped to its outline: it is a background, so
 * put it on the container and keep anything that must overhang the pane outside the element. The clip
 * is the outline's rounded rectangle, which costs nothing to clip to — a continuous corner strays from
 * the circular one by under 2 % of its radius, and the rim is drawn along the true curve over it.
 *
 * @param shape the outline. [RoundedRectangle][com.kyant.shapes.RoundedRectangle] and
 *   [Capsule][com.kyant.shapes.Capsule] give the continuous corners the refraction is designed
 *   around; shapes that are not rounded rectangles get no lens rather than a wrong one.
 * @param tint the colour of the glass, laid over what it refracts; its alpha is the strength. In a
 *   group it is this member's colour, blended through the bridges; under [GlassLook.Solid] it is the
 *   pane itself. Unspecified takes the material's.
 * @param material the glass recipe, inherited. Inside a group a member is made of the group's glass
 *   whatever it names here; this applies whenever the element renders a pane of its own.
 * @param pressedMaterial the glass while pressed. With it, the element blends its whole recipe — blur,
 *   lens, tint, rim, shadows — towards this one as [pressProgress] goes from `0f` to `1f`, in the
 *   draw phase, without recomposing. Under [GlassLook.Frosted] the blend follows a [pressProgress]
 *   passed in — the element's own touch is off there — and under [GlassLook.Solid] the pane keeps its
 *   resting material.
 * @param pressProgress how far the element is pressed. Defaults to its own touch when [dragging] or
 *   [interactiveHighlight] is on; pass one to drive it from elsewhere — a
 *   [DampedDragAnimation.pressProgress], or a [MotionBlur.intensity] to calm the glass while it flies.
 * @param backdrop what this element refracts instead of the inherited backdrop.
 * @param interaction a hoisted [GlassInteraction], for several elements that share one gesture.
 * @param dragging whether the element leans towards the finger. It then claims a drag once it moves
 *   past the touch slop, so leave it off for elements inside something that scrolls.
 * @param stretching whether it also swells and stretches along its travel; needs [dragging].
 * @param interactiveHighlight whether it lights up under the fingertip, in the colour of its glass: a
 *   tinted element glows in its own [tint], clear glass in white. Only the element itself is lit; in
 *   a group the light flows on through the bridges, mixed like the members' colours.
 * @param layerBlock a transform for the element, inverted for the backdrop so the refraction stays
 *   anchored in screen space while the element scales or turns.
 * @param onDrawSurface drawn over the glass and under the content, for what a tint cannot express.
 * @param exportedBackdrop records this element's glass into a [LayerBackdrop], so another element can
 *   refract this one.
 * @param refractsContent bends the content along with the backdrop, as if it lay behind the glass.
 *   Colour and blur stay with the backdrop, so text is bent but never blurred. Only under
 *   [GlassLook.Liquid], the one look with a lens.
 * @param effects a hand-written recipe instead of [material]'s, for glass a material cannot express —
 *   see [BackdropEffectScope]. The rim and the shadows still come from [material]. An element with
 *   its own effects is a pane of its own inside a group unless it calls `merge()`.
 * @param exactClip clips the glass and the content to the true outline instead of its rounded
 *   rectangle — for content that fills the corners and has to follow the continuous curve to the last
 *   pixel. It costs a coverage mask for every draw inside the element on every frame.
 * @throws IllegalStateException when glass that samples has no backdrop, given or inherited.
 */
@Composable
public fun Modifier.liquify(
    shape: Shape,
    tint: Color = Color.Unspecified,
    material: GlassMaterial = LiquifyTheme.material,
    pressedMaterial: GlassMaterial? = null,
    pressProgress: (() -> Float)? = null,
    backdrop: Backdrop? = null,
    interaction: GlassInteraction? = null,
    dragging: Boolean = interaction != null,
    stretching: Boolean = false,
    interactiveHighlight: Boolean = interaction != null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    refractsContent: Boolean = false,
    effects: (BackdropEffectScope.() -> Unit)? = null,
    exactClip: Boolean = false,
): Modifier {
    val look = LocalGlassLook.current
    val liquid = look == GlassLook.Liquid
    val group = if (liquid) LocalGroupDefaults.current else null
    val darkTheme = LiquifyTheme.darkTheme

    // Only liquid glass reacts to the finger; the other looks keep their elements still.
    val touch: GlassInteraction? =
        if (liquid && (dragging || interactiveHighlight)) interaction ?: rememberGlassInteraction()
        else null
    if (touch != null) {
        touch.motion = material.motion
        // Claimed only when the element follows the finger, so a glow-only one inside a list still
        // lets the list scroll.
        touch.consumesDrag = dragging
        touch.stretches = dragging && stretching
    }

    // A member's drag goes through layout rather than a layer transform: placement is what the group
    // hears about, so the merged surface stays locked to the element instead of sliding off it.
    val memberTouch = if (dragging && group != null && effects == null) touch else null

    // A press blends towards the pressed material — driven by the caller, or by the element's touch,
    // which only liquid glass has. A solid pane keeps its resting material.
    val ownProgress: (() -> Float)? = if (touch != null) remember(touch) { { touch.pressProgress } } else null
    val progress: (() -> Float)? =
        if (pressedMaterial != null && look != GlassLook.Solid) pressProgress ?: ownProgress else null
    val pressed = if (progress != null) pressedMaterial else null

    val shapeProvider = remember(shape) { ShapeProvider(shape) }
    val nodeState = remember { GlassNodeState() }

    val dragLayerBlock: (GraphicsLayerScope.() -> Unit)? =
        if (touch != null && dragging && memberTouch == null) {
            remember(touch, layerBlock) {
                val block: GraphicsLayerScope.() -> Unit = {
                    with(touch) { applyDragTransform() }
                    layerBlock?.invoke(this)
                }
                block
            }
        } else {
            null
        }
    val transform = dragLayerBlock ?: layerBlock

    val memberDragModifier =
        if (memberTouch != null) remember(memberTouch) { Modifier.memberDrag(memberTouch) } else Modifier

    val decorated = material.isDecorated || pressed?.isDecorated == true
    val glows = touch != null && interactiveHighlight

    val surface: Modifier =
        if (look == GlassLook.Solid) {
            Modifier.flatSurface(shapeProvider, tint.takeOrElse { material.tint }, onDrawSurface)
        } else {
            LiquifyElement(
                backdrop = backdrop ?: group?.backdrop ?: LocalBackdrop.current ?: noBackdrop(),
                shapeProvider = shapeProvider,
                nodeState = nodeState,
                material = material,
                pressedMaterial = pressed,
                pressProgress = progress,
                tint = tint,
                effects = effects,
                group = group,
                refracts = liquid,
                layerBlock = transform,
                exportedBackdrop = exportedBackdrop,
                onDrawSurface = onDrawSurface,
                refractsContent = refractsContent && liquid,
                interaction = touch,
                glows = glows,
                exactClip = exactClip
            )
        }

    return this
        .then(memberDragModifier)
        .then(if (transform != null) Modifier.graphicsLayer(transform) else Modifier)
        .then(
            if (decorated) {
                GlassDecorationElement(shapeProvider, nodeState, material, pressed, progress, darkTheme)
            } else {
                Modifier
            }
        )
        .then(surface)
        // After the glass, so the light lands on it and inside its clip. It takes the element's own
        // colour — not the material's, which is the body's — as a merged member's light does.
        .then(if (glows) GlowElement(touch, nodeState, tint) else Modifier)
        .then(touch?.gestureModifier ?: Modifier)
}

private val GlassMaterial.isDecorated: Boolean
    get() = rim != null || shadow != null || innerShadow != null

/**
 * The drag of a merged member: the lean as a layout offset, so placement — which the group watches —
 * follows the finger, and the swell as a layer scale, which the group applies to its field as well.
 */
private fun Modifier.memberDrag(interaction: GlassInteraction): Modifier =
    this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                val translation = interaction.dragTranslation(
                    Size(placeable.width.toFloat(), placeable.height.toFloat())
                )
                placeable.place(translation.x.roundToInt(), translation.y.roundToInt())
            }
        }
        .graphicsLayer {
            if (interaction.stretches && size.width > 0f && size.height > 0f) {
                val scale = interaction.dragScale(size, this)
                scaleX = scale.x
                scaleY = scale.y
            }
        }

private fun noBackdrop(): Nothing =
    error(
        "liquify() has no backdrop. Pass one, or declare it once with " +
            "LiquifyTheme(backdrop = …)."
    )
