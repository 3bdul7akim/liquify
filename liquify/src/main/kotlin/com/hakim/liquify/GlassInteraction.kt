/*
 * The press/drag tracking and the gel-flex transform are derived from Kyant0's AndroidLiquidGlass
 * (Apache-2.0): https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 */

package com.hakim.liquify

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.hakim.liquify.internal.GlowElement
import com.hakim.liquify.internal.RuntimeShader
import com.hakim.liquify.internal.RuntimeShaderPool
import com.hakim.liquify.internal.TOUCH_LIGHT_FUNCTION
import com.hakim.liquify.internal.TouchLightReach
import com.hakim.liquify.internal.inspectDragGestures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import kotlin.math.sqrt
import kotlin.math.tanh

// One element's light: `glowLight` is `xy` the lit point and `z` the reach in the element's own
// pixels, and `w` the strength. Premultiplied on the way out, so drawn with BlendMode.Plus it adds
// exactly the light and nothing more.
@Language("AGSL")
private const val ILLUMINATION_SHADER = """
layout(color) uniform half4 glowColor;
uniform float4 glowLight;
$TOUCH_LIGHT_FUNCTION

half4 main(float2 coord) {
    half strength = touchLight(coord, glowLight);
    return half4(glowColor.rgb * strength, strength);
}"""

/**
 * Smallest swell an element is given, as a fraction of its short side.
 *
 * `dragScaleAmount` is an absolute distance, and an absolute distance vanishes on anything large: the
 * same 4 dp is a sixth of a slider thumb but under two per cent of a menu panel. Taking whichever of
 * the two is greater lets big surfaces swell in proportion to themselves.
 */
private const val MinimumRelativeStretch = 0.06f

/** Pool key of the hotspot program — see [RuntimeShaderPool]. */
private const val IlluminationShaderKey = "liquify.illumination"

/**
 * The touch state of glass: where the finger is, how hard the element is pressed, and the light and
 * flex that follow from it.
 *
 * You rarely need one. `liquify(dragging = …, stretching = …, interactiveHighlight = …)` creates its
 * own. Hoist one only when **several elements must share one gesture** — a tab bar whose whole panel
 * glows from wherever its indicator sits:
 *
 * ```
 * val shared = rememberGlassInteraction(position = { size, _ -> indicatorCenter(size) })
 *
 * Row(Modifier.liquify(Capsule()).then(shared.modifier)) { … }      // glows
 * Box(Modifier.then(shared.gestureModifier).liquify(Capsule())) { … }  // feeds the gesture
 * ```
 *
 * @param position maps the raw pointer position to where the light should come from. The default is
 *   the pointer itself; a segmented control can pin it to the selected segment.
 * @param glowColor colour of the light inside clear glass. Coloured glass glows in its own colour
 *   instead, as far as it is coloured, so a press makes the tint brighter.
 * @param glowIntensity strength of the light: the hotspot's peak, over a lift of the whole element
 *   at about half of it.
 * @param dragScaleAmount how far the element swells when held. A floor of six per cent of the short
 *   side applies as well, so a large surface still reacts visibly.
 * @param dragResponse how eagerly the element leans towards the finger.
 * @param tracksConsumedDrags keep following a drag that another gesture has claimed — for a glow on a
 *   control whose own drag consumes the movement. Left `false`, a scroller taking over ends the press.
 * @param motion how far it leans, how much it stretches and how much it wobbles; see [GlassMotion].
 */
@Stable
public class GlassInteraction(
    private val animationScope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    internal val glowColor: Color = Color.White,
    internal val glowIntensity: Float = 0.15f,
    private val dragScaleAmount: Dp = 4.dp,
    private val dragResponse: Float = 0.15f,
    private val tracksConsumedDrags: Boolean = false,
    motion: GlassMotion = GlassMotion.Default
) : RememberObserver {

    /**
     * The motion preset. `liquify` hands it the material's on every composition, so an app-wide
     * change reaches an element that is already on screen; it lands with the next movement rather
     * than in the middle of one.
     */
    public var motion: GlassMotion = motion

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero

    /**
     * Where the finger is while one is down, written straight from the pointer events.
     *
     * Snapping the position animation on every event would launch a coroutine per pointer move. The
     * animation is only needed for the way back after release; until then this plain state is the
     * position, and [tracking] says which of the two is current.
     */
    private var livePosition by mutableStateOf(Offset.Zero)
    private var tracking by mutableStateOf(false)

    /** Counts presses, so a release that resumes after a new press has begun leaves it alone. */
    private var gesture = 0

    private val currentPosition: Offset
        get() = if (tracking) livePosition else positionAnimation.value

    /**
     * The hotspot program, taken on the first press rather than at construction — almost no button
     * is ever touched in a given session, and an eager compile would land in the frame it appears.
     * Taken from [RuntimeShaderPool] and handed back when this object leaves the composition.
     */
    private var shader: RuntimeShader? = null

    private fun obtainShader(): RuntimeShader? {
        if (!isRuntimeShaderSupported()) return null
        shader?.let { return it }
        val obtained = RuntimeShaderPool.acquire(IlluminationShaderKey, ILLUMINATION_SHADER)
            ?: RuntimeShader(ILLUMINATION_SHADER)
        // A recycled instance holds whatever its last owner wrote, and a fresh one holds nothing.
        uploadedGlowColor = Color.Unspecified
        uploadedGlowLight.fill(Float.NaN)
        glowBrush = null
        shader = obtained
        return obtained
    }

    private fun releaseShader() {
        val owned = shader ?: return
        shader = null
        glowBrush = null
        if (isRuntimeShaderSupported()) RuntimeShaderPool.release(IlluminationShaderKey, owned)
    }

    override fun onRemembered() {}

    override fun onForgotten() {
        releaseShader()
    }

    override fun onAbandoned() {
        releaseShader()
    }

    /** Built on first use and kept: the paint resolves the shader on every draw, so uniforms land. */
    private var glowBrush: ShaderBrush? = null

    /**
     * What was last written to the hotspot: its colour, and its point, reach and strength. A uniform
     * write discards the platform's built shader, so unchanged values are skipped.
     */
    private var uploadedGlowColor = Color.Unspecified
    private val uploadedGlowLight = FloatArray(4) { Float.NaN }

    /**
     * `0f` at rest, `1f` while held. A bouncy [motion] overshoots both ways — past `1f` as the press
     * lands and below `0f` as it lets go.
     */
    public val pressProgress: Float get() = pressProgressAnimation.value

    /** How far the finger has travelled since it landed, spring-damped. */
    public val offset: Offset get() = currentPosition - startPosition

    /** The lit point in element-local pixels. */
    public val pointer: Offset get() = currentPosition

    /**
     * Where the light sits on an element of [size], in its own pixels: wherever [position] puts it,
     * kept on the element. A merged group reads it for every member it lights.
     */
    internal fun litPoint(size: Size): Offset {
        val point = position(size, currentPosition)
        return Offset(
            point.x.fastCoerceIn(0f, size.width),
            point.y.fastCoerceIn(0f, size.height)
        )
    }

    /** How brightly the element is lit, `0f..1f`: the press, without a bouncy overshoot. */
    internal val glowAmount: Float get() = pressProgressAnimation.value.fastCoerceIn(0f, 1f)

    /**
     * How far the element follows the finger, for an element of [size]. `tanh` bounds it to the
     * element's short side however far the finger travels, so it leans but never detaches.
     */
    internal fun dragTranslation(size: Size): Offset {
        val spec = motion.spec
        val maxOffset = size.minDimension * spec.reach
        if (maxOffset <= 0f) return Offset.Zero
        val response = dragResponse * spec.response
        val offset = offset
        return Offset(
            maxOffset * tanh(response * offset.x / maxOffset),
            maxOffset * tanh(response * offset.y / maxOffset)
        )
    }

    /**
     * How far the element swells and stretches, as x and y scale factors. Both grow with the press;
     * the axis pointing at the finger grows a little more, which turns a swell into a stretch.
     */
    internal fun dragScale(size: Size, density: Density): Offset {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return Offset(1f, 1f)

        val spec = motion.spec
        val growth = maxOf(
            with(density) { dragScaleAmount.toPx() },
            size.minDimension * MinimumRelativeStretch
        ) * spec.stretch
        val base = lerp(1f, 1f + growth / height, pressProgressAnimation.value)
        val maxDragScale = growth / height

        val offset = offset
        // x² / r and y² / r: the cosine and sine factors without the round trip through an angle.
        val distance = sqrt(offset.x * offset.x + offset.y * offset.y)
        val alongX = if (distance > 0f) offset.x * offset.x / distance else 0f
        val alongY = if (distance > 0f) offset.y * offset.y / distance else 0f
        val stretchX = maxDragScale * (alongX / size.maxDimension) *
            (width / height).fastCoerceAtMost(1f)
        val stretchY = maxDragScale * (alongY / size.maxDimension) *
            (height / width).fastCoerceAtMost(1f)
        return Offset(
            base + stretchX - stretchY * spec.squash,
            base + stretchY - stretchX * spec.squash
        )
    }

    /** Leans the element towards the finger and, when [stretches], swells and stretches it. */
    internal fun GraphicsLayerScope.applyDragTransform() {
        if (size.width <= 0f || size.height <= 0f) return

        val translation = dragTranslation(size)
        translationX = translation.x
        translationY = translation.y

        if (!stretches) return

        val scale = dragScale(size, this)
        scaleX = scale.x
        scaleY = scale.y
    }

    /**
     * Draws the light. Place it after `liquify` so it lands on the glass, inside its clip.
     *
     * Every element carrying it lights itself, and only itself — one interaction on several elements
     * lights each of them from the same point. You rarely need it: `interactiveHighlight = true` on
     * `liquify` draws an element's own light, in the colour of its glass, and inside a merged
     * [LiquidGlassGroup] lets the group lay it into the body, flowing through the bridges like the
     * members' colours.
     */
    public val modifier: Modifier = GlowElement(this)

    /**
     * Draws the light of one element, in [color], over its glass and under its content: a gentle lift
     * of the whole pane plus a hotspot under the finger — the pane brightens, but the light has a
     * source.
     */
    internal fun DrawScope.drawGlow(color: Color) {
        if (pressProgressAnimation.value <= 0f) return
        val glow = glowAmount
        val shader = obtainShader()
        // Redundant with the null check, but the form lint can verify.
        if (shader != null && isRuntimeShaderSupported()) {
            val lit = litPoint(size)
            val reach = size.minDimension * TouchLightReach
            writeGlow(shader, lit.x, lit.y, reach, glowIntensity * glow, color)
            val brush = glowBrush
                ?: ShaderBrush(shader.asComposeShader()).also { glowBrush = it }
            drawRect(brush, blendMode = BlendMode.Plus)
        } else {
            // Without a program there is no hotspot, so a flat lift stands in for it.
            drawRect(color.copy(alpha = (0.25f * glow).fastCoerceIn(0f, 1f)), blendMode = BlendMode.Plus)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun writeGlow(
        shader: RuntimeShader,
        x: Float,
        y: Float,
        reach: Float,
        strength: Float,
        color: Color
    ) {
        if (color != uploadedGlowColor) {
            uploadedGlowColor = color
            // Opaque: the strength is what dims it; see TOUCH_LIGHT_FUNCTION.
            shader.setColorUniform("glowColor", color.copy(alpha = 1f))
        }
        val light = uploadedGlowLight
        if (x != light[0] || y != light[1] || reach != light[2] || strength != light[3]) {
            light[0] = x
            light[1] = y
            light[2] = reach
            light[3] = strength
            shader.setFloatUniform("glowLight", x, y, reach, strength)
        }
    }

    /**
     * Whether movement is claimed from ancestors — only while the element actually follows the
     * finger, so a glow-only element inside a list still lets the list scroll.
     */
    internal var consumesDrag: Boolean = false

    /** Whether the element also swells and stretches; read by merged groups as well. */
    internal var stretches: Boolean = true

    /** Distance travelled since the finger landed, to decide when a press became a drag. */
    private var travelled: Offset = Offset.Zero

    private var crossedSlop: Boolean = false

    /** The gesture input that feeds the glow and the flex. */
    public val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        val touchSlop = viewConfiguration.touchSlop
        inspectDragGestures(
            onDragStart = { down ->
                gesture++
                startPosition = down.position
                livePosition = down.position
                tracking = true
                travelled = Offset.Zero
                crossedSlop = false
                val pressSpec = motion.spec.pressSpec
                animationScope.launch {
                    // Stops a release that may still be springing back from the last press.
                    launch { positionAnimation.snapTo(startPosition) }
                    launch { pressProgressAnimation.animateTo(1f, pressSpec) }
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
                // Until the slop is crossed this is still a tap for everyone else, and consuming
                // even one pixel would cancel a `clickable` alongside. The glow and the flex react
                // from the down event regardless — they just do it without claiming the gesture.
                if (consumesDrag && crossedSlop) {
                    change.consume()
                }
            }
            livePosition = change.position
        }
    }

    private fun release() {
        val releasedGesture = gesture
        val spec = motion.spec
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, spec.pressSpec) }
            launch {
                // Hand the position from the finger to the spring without a jump.
                positionAnimation.snapTo(livePosition)
                if (gesture != releasedGesture) return@launch
                tracking = false
                positionAnimation.animateTo(startPosition, spec.releaseSpec)
            }
        }
    }
}

/**
 * Remembers a [GlassInteraction] bound to the current composition's coroutine scope.
 *
 * Every argument is read once, on the first composition, except [motion]: the object survives
 * recomposition on purpose, because rebuilding it would drop the press mid-gesture. [motion] follows
 * the material in scope and is handed over on every composition.
 */
@Composable
public fun rememberGlassInteraction(
    position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    glowColor: Color = Color.White,
    glowIntensity: Float = 0.15f,
    dragScaleAmount: Dp = 4.dp,
    dragResponse: Float = 0.15f,
    tracksConsumedDrags: Boolean = false,
    motion: GlassMotion = LiquifyTheme.material.motion
): GlassInteraction {
    val animationScope = rememberCoroutineScope()
    val interaction = remember(animationScope) {
        GlassInteraction(
            animationScope = animationScope,
            position = position,
            glowColor = glowColor,
            glowIntensity = glowIntensity,
            dragScaleAmount = dragScaleAmount,
            dragResponse = dragResponse,
            tracksConsumedDrags = tracksConsumedDrags,
            motion = motion
        )
    }
    interaction.motion = motion
    return interaction
}
