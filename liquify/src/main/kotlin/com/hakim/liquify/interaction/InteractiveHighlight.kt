/*
 * The press/drag tracking and the gel-flex transform are derived from Kyant0's AndroidLiquidGlass
 * (Apache-2.0): https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 */

package com.hakim.liquify.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.hakim.liquify.RuntimeShader
import com.hakim.liquify.asComposeShader
import com.hakim.liquify.highlight.Highlight
import com.hakim.liquify.highlight.HighlightStyle
import com.hakim.liquify.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Language("AGSL")
private const val ILLUMINATION_SHADER = """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float intensity = smoothstep(radius, radius * 0.5, distance(coord, position));
    return color * intensity;
}"""

/**
 * Smallest swell an element is given, as a fraction of its short side.
 *
 * `dragScaleAmount` is an absolute distance, and an absolute distance vanishes on anything large:
 * the same 4.dp is a sixth of a slider thumb but under two per cent of a menu panel, which reads as
 * the panel simply not reacting. Taking whichever of the two is greater leaves every small control
 * exactly as it was and lets big surfaces swell in proportion to themselves.
 */
private const val MinimumRelativeStretch = 0.06f

/**
 * Tracks touch on a glass element and exposes everything needed to make it react like liquid.
 *
 * Two separate behaviours come out of the same gesture, and each can be used on its own:
 *
 * - **Illumination** — the element lights up from under the fingertip and the glow follows the
 *   finger as it drags. [modifier] draws it, [gestureModifier] feeds it.
 * - **Drag animation** — the element leans towards the finger, damped by `tanh` so it never runs
 *   away, and — separately — swells and stretches along the direction of travel.
 *   [applyDragTransform] does both inside a `layerBlock`, the second half only when [stretches] is
 *   set.
 *
 * The usual way to wire both up is to hand the object to [liquify][com.hakim.liquify.liquify] and
 * let it do the plumbing:
 *
 * ```
 * val interaction = rememberInteractiveHighlight()
 *
 * Modifier.liquify(
 *     shape = { Capsule() },
 *     effects = { blur(2.dp.toPx()); lens(12.dp.toPx(), 24.dp.toPx()) },
 *     backdrop = backdrop,
 *     interaction = interaction          // dragging and interactiveHighlight default to true
 * )
 * ```
 *
 * Turn either half off with `dragging = false` / `interactiveHighlight = false`, or drop the
 * `interaction` argument entirely for a completely static pane.
 *
 * @param position maps the raw pointer position to where the light should actually come from.
 *   The default is the pointer itself; a segmented control can pin it to the selected segment.
 * @param glowColor colour of the interior illumination.
 * @param glowIntensity peak opacity of the hotspot.
 * @param dragScaleAmount how far the element swells when held. A floor of six per cent of the short
 *   side applies as well, so a large surface still reacts visibly instead of growing by a couple of
 *   pixels; raise this to exaggerate small controls.
 */
@Stable
public class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    private val glowColor: Color = Color.White,
    private val glowIntensity: Float = 0.15f,
    private val dragScaleAmount: Dp = 4.dp,
    private val dragResponse: Float = 0.15f,
    /**
     * Whether to keep following a drag that another gesture has claimed.
     *
     * Set it when a sibling gesture on the same control owns the movement — a tab indicator being
     * dragged, say — and the illumination should still track the finger. Left `false`, a scroller
     * taking over correctly ends the press.
     */
    private val tracksConsumedDrags: Boolean = false
) {

    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero

    // Not snapshot state: written and read within the same draw, which a MutableState would turn
    // into an invalidation loop.
    private var elementSize: Size = Size.Zero

    private val shader: RuntimeShader? =
        if (isRuntimeShaderSupported()) RuntimeShader(ILLUMINATION_SHADER) else null

    /** `0f` at rest, `1f` while held. */
    public val pressProgress: Float get() = pressProgressAnimation.value

    /** How far the finger has travelled since it landed, spring-damped. */
    public val offset: Offset get() = positionAnimation.value - startPosition

    /** The lit point in element-local pixels. */
    public val pointer: Offset get() = positionAnimation.value

    /** The lit point relative to the element centre — what [HighlightStyle.Dynamic] expects. */
    public val pointerFromCenter: Offset
        get() = positionAnimation.value - Offset(elementSize.width / 2f, elementSize.height / 2f)

    /**
     * A rim that lights up on the side the finger is on, ready to pass as `highlight`.
     *
     * ```
     * highlight = { interaction.dynamicHighlight() }
     * ```
     */
    public fun dynamicHighlight(width: Dp = 1.dp, color: Color = Color.White.copy(alpha = 0.75f)): Highlight =
        Highlight(
            width = width,
            style = HighlightStyle.Dynamic(
                color = color,
                pointer = pointerFromCenter,
                focus = pressProgress
            )
        )

    /**
     * How far the element follows the finger, for an element of [size].
     *
     * `tanh` bounds the result to the element's own short side no matter how far the finger
     * travels, so the control leans towards the drag but never detaches from where it belongs.
     *
     * Exposed because a merged group member has to apply this during *layout* rather than as a
     * layer transform — see `liquify`.
     */
    public fun dragTranslation(size: Size): Offset {
        val maxOffset = size.minDimension
        if (maxOffset <= 0f) return Offset.Zero
        val offset = offset
        return Offset(
            maxOffset * tanh(dragResponse * offset.x / maxOffset),
            maxOffset * tanh(dragResponse * offset.y / maxOffset)
        )
    }

    /**
     * How far the element swells and stretches, for an element of [size].
     *
     * Returns the x and y scale factors. Both grow with the press; the axis pointing at the finger
     * grows a little more, which is what turns a uniform swell into a stretch.
     *
     * Exposed for the same reason as [dragTranslation]: a merged group member cannot express this
     * as a layer transform alone, because the group builds its surface from geometry and has to
     * apply the identical factors to stay locked to the element.
     */
    public fun dragScale(size: Size, density: Density): Offset {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return Offset(1f, 1f)

        val growth = maxOf(
            with(density) { dragScaleAmount.toPx() },
            size.minDimension * MinimumRelativeStretch
        )
        val base = lerp(1f, 1f + growth / height, pressProgressAnimation.value)
        val maxDragScale = growth / height

        val offset = offset
        val offsetAngle = atan2(offset.y, offset.x)
        return Offset(
            base + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                (width / height).fastCoerceAtMost(1f),
            base + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                (height / width).fastCoerceAtMost(1f)
        )
    }

    /**
     * Leans the element towards the finger, and — when [stretches] is set — swells and stretches it
     * along the direction of travel.
     *
     * Call this inside a `layerBlock` — not a standalone `graphicsLayer` — so the backdrop can
     * invert the transform and the refraction stays anchored in screen space.
     */
    public fun GraphicsLayerScope.applyDragTransform() {
        if (size.width <= 0f || size.height <= 0f) return

        val translation = dragTranslation(size)
        translationX = translation.x
        translationY = translation.y

        if (!stretches) return

        val scale = dragScale(size, this)
        scaleX = scale.x
        scaleY = scale.y
    }

    /** Draws the interior illumination. Place it after `liquify` so it lands on the glass. */
    public val modifier: Modifier = Modifier.drawWithContent {
        elementSize = size
        val progress = pressProgressAnimation.value

        if (progress > 0f) {
            val shader = shader
            // The SDK check is redundant with the null check — the shader is only built when
            // supported — but it is the form lint can actually verify.
            if (shader != null && isRuntimeShaderSupported()) {
                // A flat lift plus a hotspot: the whole pane brightens, but the light clearly has
                // a source under the finger.
                drawRect(glowColor.copy(alpha = 0.08f * progress), blendMode = BlendMode.Plus)

                val litPosition = position(size, positionAnimation.value)
                shader.apply {
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", glowColor.copy(alpha = glowIntensity * progress))
                    setFloatUniform("radius", size.minDimension * 1.5f)
                    setFloatUniform(
                        "position",
                        litPosition.x.fastCoerceIn(0f, size.width),
                        litPosition.y.fastCoerceIn(0f, size.height)
                    )
                }
                drawRect(ShaderBrush(shader.asComposeShader()), blendMode = BlendMode.Plus)
            } else {
                drawRect(glowColor.copy(alpha = 0.25f * progress), blendMode = BlendMode.Plus)
            }
        }

        drawContent()
    }

    /**
     * Whether movement is claimed from ancestors.
     *
     * Set when the element actually moves with the finger: otherwise a drag inside a scrolling
     * list would both flex the element *and* scroll the list, which reads as the control sliding
     * out from under your thumb. Only the movement is consumed — the down and the up are left
     * alone, and nothing at all is taken before the finger has travelled a touch slop, so a
     * `clickable` alongside still registers taps.
     */
    internal var consumesDrag: Boolean = false

    /**
     * Whether the element swells and stretches as well as leaning towards the finger.
     *
     * Following the finger and deforming are two separate readings of the same gesture — a control
     * that leans is being pushed, one that also stretches is being pulled apart — so they are two
     * switches rather than one. Set by `liquify` from its `stretching` parameter, and read by the
     * group as well, which has to apply the identical factors to a merged member's box or the glass
     * comes away from the content.
     *
     * Defaults to `true` so that driving [applyDragTransform] by hand behaves as it always has.
     */
    internal var stretches: Boolean = true

    /** Distance travelled since the finger landed, used only to decide when a press became a drag. */
    private var travelled: Offset = Offset.Zero

    /** `true` once [travelled] has passed the platform touch slop for this gesture. */
    private var crossedSlop: Boolean = false

    /** Feeds [modifier], [dragTranslation] and [applyDragTransform]. */
    public val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        val touchSlop = viewConfiguration.touchSlop
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                travelled = Offset.Zero
                crossedSlop = false
                animationScope.launch {
                    launch { positionAnimation.snapTo(startPosition) }
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                }
            },
            onDragEnd = { release() },
            onDragCancel = { release() },
            stopOnConsumed = !tracksConsumedDrags
        ) { change, dragAmount ->
            if (dragAmount != Offset.Zero) {
                travelled += dragAmount
                if (!crossedSlop && travelled.getDistance() > touchSlop) {
                    crossedSlop = true
                }
                // Until the slop is crossed this is still a tap as far as everyone else is
                // concerned, and consuming even one pixel of it cancels theirs: `clickable` and
                // `detectTapGestures` both bail out the moment a change is taken from them. That
                // is what made glass buttons swallow presses — the finger never holds perfectly
                // still, so the very first jitter stole the tap.
                //
                // The illumination and the flex do *not* wait for this. They react from the down
                // event, because reacting instantly is the whole point of the material; they just
                // do it without claiming the gesture.
                if (consumesDrag && crossedSlop) {
                    change.consume()
                }
            }
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }

    private fun release() {
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
        }
    }
}

/**
 * Remembers an [InteractiveHighlight] bound to the current composition's coroutine scope.
 *
 * **Every argument is read once, on the first composition.** Passing a value that changes later has
 * no effect: the object survives recomposition on purpose, because rebuilding it would drop the
 * press and position animations mid-gesture and detach the pointer input along with them. Treat
 * these as configuration, not as state. If one of them genuinely has to change at runtime, hold the
 * object yourself and rebuild it under a key you control.
 */
@Composable
public fun rememberInteractiveHighlight(
    position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    glowColor: Color = Color.White,
    glowIntensity: Float = 0.15f,
    dragScaleAmount: Dp = 4.dp,
    dragResponse: Float = 0.15f,
    tracksConsumedDrags: Boolean = false
): InteractiveHighlight {
    val animationScope = rememberCoroutineScope()
    return remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = position,
            glowColor = glowColor,
            glowIntensity = glowIntensity,
            dragScaleAmount = dragScaleAmount,
            dragResponse = dragResponse,
            tracksConsumedDrags = tracksConsumedDrags
        )
    }
}
