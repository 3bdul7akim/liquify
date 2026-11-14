package com.hakim.liquify

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.hakim.liquify.isRenderEffectSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/**
 * Blurs and fades content in and out of existence instead of just cross-fading it.
 *
 * A plain alpha fade makes content look like it was switched off. Blurring it as it goes reads as
 * the content *dissolving into* the material — which is what the glass itself does, so the two
 * agree. Put it on the children of a
 * [liquify][com.hakim.liquify.liquify] element:
 *
 * ```
 * Column(Modifier.materialize { progress }) { … }
 * ```
 *
 * [progress] is a lambda so it is read while the layer updates — a changing value costs a redraw,
 * not a recomposition, and a settled value costs nothing at all.
 *
 * Below API 31 this degrades to the fade alone. As with [motionBlur], the blur rasterises the layer
 * at its bounds, so put this on content that stays inside them.
 *
 * @param progress `0f` fully dissolved, `1f` fully present.
 * @param blurRadius blur at `progress == 0f`, falling to zero as it reaches `1f`.
 * @param fade whether to fade alpha alongside the blur.
 */
public fun Modifier.materialize(
    progress: () -> Float,
    blurRadius: Dp = 12.dp,
    fade: Boolean = true
): Modifier {
    val blurs = BlurEffectCache()
    return graphicsLayer {
        val value = progress().fastCoerceIn(0f, 1f)
        if (fade) alpha = value

        if (isRenderEffectSupported()) {
            // Decal so the blur fades into nothing at the edges rather than smearing the border
            // pixels outwards, which would look like a halo rather than a dissolve.
            renderEffect = blurs.effectFor(blurRadius.toPx() * (1f - value))
        }
    }
}

/**
 * Hands back the same blur while its radius has not moved by a visible amount.
 *
 * A dissolve or a motion blur re-runs its layer block on every frame it is animating, and a fresh
 * [BlurEffect] there is a Compose object *and* a platform one, sixty or a hundred and twenty times
 * a second, for a difference no one can see. Half a pixel is the granularity worth keeping: below
 * that the effect is dropped entirely, which is the same threshold the uncached version used.
 */
private class BlurEffectCache {

    private var radius = Float.NaN
    private var effect: RenderEffect? = null

    fun effectFor(rawRadius: Float): RenderEffect? {
        val quantised = (rawRadius * 2f).fastRoundToInt() / 2f
        if (quantised != radius) {
            radius = quantised
            effect =
                if (quantised > 0.5f) BlurEffect(quantised, quantised, TileMode.Decal) else null
        }
        return effect
    }
}

/**
 * Blurs an element in proportion to how fast it is currently moving or changing.
 *
 * Real motion blur needs the frame's velocity, and here that comes from the animation itself: the
 * modifier samples [progress] each time it changes and blurs by the rate of change. A spring
 * therefore blurs hardest in the middle of its travel and sharpens as it settles — including
 * through an overshoot, which a blur keyed to progress *value* could never do.
 *
 * That sampling is also why it costs nothing at rest: a settled animation stops changing, the layer
 * stops updating, and the last sample has velocity near zero, so the blur is already gone.
 *
 * Put it on the thing that moves — for a merged surface that means the group, so the glass blurs
 * along with its contents:
 *
 * ```
 * LiquidGlassGroup(
 *     modifier = Modifier
 *         .height(470.dp)
 *         .motionBlur({ progress })
 *         .padding(bottom = 48.dp)      // room for what the group draws past its own box
 * ) { … }
 * ```
 *
 * **Leave slack for anything that draws outside its bounds.** A render effect rasterises the layer
 * at exactly the layer's bounds, so while the blur is live — and only then — whatever a child drew
 * beyond them is cut off. A group is exactly such a child: its merged surface reaches half a merge
 * radius past the union of its members for the bridge, and its shadow reaches further still. A
 * member sitting flush against the edge therefore appears to have its glass sliced flat for the
 * length of the transition, and to snap back when the animation settles and the effect is dropped.
 * Growing the layer and insetting the content by the same amount, as above, costs no layout and
 * fixes it.
 *
 * No-op below API 31.
 *
 * @param progress the animated value driving the transition; any unit, only its rate matters.
 * @param maxRadius blur applied at or above [fullSpeed].
 * @param fullSpeed rate of change, in units per second, that earns the full [maxRadius]. Raising
 *   it makes the blur subtler, lowering it makes it saturate sooner. The default is tuned so a
 *   typical spring only touches [maxRadius] at its fastest instant rather than for most of its
 *   travel.
 */
@Composable
public fun Modifier.motionBlur(
    progress: () -> Float,
    maxRadius: Dp = 5.dp,
    fullSpeed: Float = 50f
): Modifier = this then rememberMotionBlur(maxRadius, fullSpeed, progress).modifier

/** Applies a hoisted [MotionBlur]. Equivalent to `then(motion.modifier)`. */
public fun Modifier.motionBlur(motion: MotionBlur): Modifier = this then motion.modifier

/**
 * Remembers a [MotionBlur] bound to the current composition's coroutine scope.
 *
 * Hoist one when something other than the blur has to know that the element is moving — a glass
 * pane that drops its refraction while it flies, say. [Modifier.motionBlur] with a lambda builds
 * one internally and is the shorter form when nothing else needs it.
 *
 * **Every argument is read once, on the first composition**, including [progress]. The object
 * survives recomposition on purpose: rebuilding it would drop the decay mid-transition. [progress]
 * is a lambda, so it still reads whatever it closes over on every sample.
 */
@Composable
public fun rememberMotionBlur(
    maxRadius: Dp = 5.dp,
    fullSpeed: Float = 50f,
    progress: () -> Float
): MotionBlur {
    val animationScope = rememberCoroutineScope()
    return remember(animationScope) {
        MotionBlur(animationScope, maxRadius, fullSpeed, progress)
    }
}

/**
 * How fast something is moving, and the blur that follows from it.
 *
 * Real motion blur needs the frame's velocity, and here it comes from the animation itself: the
 * layer samples `progress` whenever it changes and blurs by the rate of change. A spring therefore
 * blurs hardest in the middle of its travel and sharpens as it settles — including through an
 * overshoot, which a blur keyed to the progress *value* could never do.
 *
 * [intensity] is the same measurement normalised to `0f..1f`, and it is the reason this is a class
 * rather than a modifier alone. Refraction is by far the most expensive thing a pane of glass does,
 * and it is invisible while that pane is travelling — so the pane can drop it while it flies, by
 * blending towards a calm material as fast as it moves:
 *
 * ```
 * val motion = rememberMotionBlur(maxRadius = 12.dp, fullSpeed = 15f) { progress.value }
 * val glass = LiquifyTheme.material
 *
 * Modifier
 *     .motionBlur(motion)
 *     .liquify(
 *         shape = RoundedRectangle(22.dp),
 *         pressedMaterial = glass.copy(blurRadius = 0.dp, refractionHeight = 0.dp),
 *         pressProgress = { motion.intensity }
 *     )
 * ```
 *
 * The blend is read in the draw phase, so it follows the value without a recomposition and settles
 * when it reaches zero.
 */
@Stable
public class MotionBlur internal constructor(
    private val animationScope: CoroutineScope,
    private val maxRadius: Dp,
    private val fullSpeed: Float,
    private val progress: () -> Float
) {

    /** Read from the layer block, so writing it is what schedules the next frame. */
    private var speed: Float by mutableFloatStateOf(0f)

    private var lastValue = Float.NaN
    private var lastTime = 0L
    private var decay: Job? = null

    /**
     * Speed at which the blur would fall below half a pixel and stop being visible. Resolved from
     * the layer block, which is the only place with a density to convert [maxRadius] with.
     */
    private var invisibleBelow = 0f

    private val blurs = BlurEffectCache()

    /** `0f` at rest, `1f` at or above `fullSpeed`. Snapshot state: read it and you observe it. */
    public val intensity: Float
        get() = if (fullSpeed > 0f) (speed / fullSpeed).fastCoerceIn(0f, 1f) else 0f

    public val modifier: Modifier = Modifier.graphicsLayer {
        if (!isRenderEffectSupported()) return@graphicsLayer

        val maxRadiusPx = maxRadius.toPx()
        invisibleBelow = if (maxRadiusPx > 0.5f) fullSpeed * (0.5f / maxRadiusPx) else fullSpeed

        sample(progress())

        // This block runs on every frame the speed changes, which during a transition is all of
        // them, so the effect is reused rather than rebuilt for each new radius.
        renderEffect = blurs.effectFor(maxRadiusPx * intensity)
    }

    private fun sample(value: Float) {
        val now = SystemClock.uptimeMillis()
        val previous = lastValue
        lastValue = value

        if (previous.isNaN()) {
            lastTime = now
            return
        }

        val elapsed = now - lastTime
        // Two updates inside one millisecond say nothing useful about speed; keep the last estimate
        // rather than dividing by a rounding error.
        if (elapsed > 0L) {
            lastTime = now
            val measured = abs(value - previous) / (elapsed / 1000f)
            // Only ever raised here. The decay loop owns the way down, and writing a lower value
            // from inside the block that reads it would invalidate the layer that just ran.
            if (measured > speed) {
                speed = measured
                startDecay()
            }
        }
    }

    /**
     * Winds the speed back down to zero once the animation stops feeding it.
     *
     * The layer block only re-runs when something it reads changes, so a settled animation would
     * otherwise leave the last speed — and its blur — frozen on screen. Each wind-down writes the
     * state the block reads, which is what schedules the next one.
     *
     * The step is a function of *time*, not of frames: a fixed factor per frame decayed twice as
     * fast at 120 Hz as at 60. And it stops as soon as the blur would be under half a pixel rather
     * than at some far smaller number — past that point every remaining turn of the loop
     * invalidated a layer to change nothing, which on a spring was the larger half of them.
     */
    private fun startDecay() {
        if (decay?.isActive == true) return
        decay = animationScope.launch {
            var last = SystemClock.uptimeMillis()
            while (speed > invisibleBelow) {
                withFrameNanos { }
                val now = SystemClock.uptimeMillis()
                val elapsed = (now - last).coerceAtLeast(1L) / 1000f
                last = now
                speed *= exp(-elapsed / DecayTimeConstant)
            }
            speed = 0f
        }
    }
}

/** Seconds for the decay to fall to 1/e. Matches the previous factor of 0.8 per frame at 60 Hz. */
private const val DecayTimeConstant = 0.075f
