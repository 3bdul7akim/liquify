package com.hakim.liquify.transition

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import com.hakim.liquify.isRenderEffectSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

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
): Modifier = graphicsLayer {
    val value = progress().fastCoerceIn(0f, 1f)
    if (fade) alpha = value

    if (isRenderEffectSupported()) {
        val radius = blurRadius.toPx() * (1f - value)
        // Decal so the blur fades into nothing at the edges rather than smearing the border pixels
        // outwards, which would look like a halo rather than a dissolve.
        renderEffect =
            if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
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
 *     backdrop = backdrop,
 *     modifier = Modifier
 *         .height(470.dp)
 *         .motionBlur { progress }
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
): Modifier {
    val scope = rememberCoroutineScope()
    val tracker = remember(scope) { MotionBlurTracker(scope) }
    return this.graphicsLayer {
        if (!isRenderEffectSupported()) return@graphicsLayer

        val speed = tracker.sample(progress())
        val radius = maxRadius.toPx() * (speed / fullSpeed).fastCoerceIn(0f, 1f)
        renderEffect =
            if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
    }
}

/**
 * Turns a stream of animated values into a rate of change that reliably returns to zero.
 *
 * The subtlety is what happens when the animation *stops*: the layer block is only re-run when
 * something it reads changes, so a settled animation would leave the last non-zero speed — and its
 * blur — frozen on screen forever. So the speed is snapshot state with a decay loop behind it: any
 * movement starts the loop, the loop winds the speed down frame by frame, and because the layer
 * reads that state each wind-down re-runs it. The loop stops at zero and nothing invalidates again.
 */
private class MotionBlurTracker(private val scope: CoroutineScope) {

    /** Read from the layer block, so writing it is what schedules the next frame. */
    var speed: Float by mutableFloatStateOf(0f)
        private set

    private var lastValue = Float.NaN
    private var lastTime = 0L
    private var decay: Job? = null

    fun sample(value: Float): Float {
        val now = SystemClock.uptimeMillis()
        val previous = lastValue
        lastValue = value

        if (previous.isNaN()) {
            lastTime = now
            return speed
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
        return speed
    }

    private fun startDecay() {
        if (decay?.isActive == true) return
        decay = scope.launch {
            while (speed > 0.02f) {
                withFrameNanos { }
                speed *= 0.8f
            }
            speed = 0f
        }
    }
}
