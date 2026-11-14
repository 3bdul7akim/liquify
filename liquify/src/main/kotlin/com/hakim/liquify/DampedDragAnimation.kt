/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 */

package com.hakim.liquify

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import com.hakim.liquify.internal.SpringFollower
import com.hakim.liquify.internal.inspectDragGestures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A draggable value with the gel physics liquid glass controls are built on.
 *
 * A slider thumb or a toggle knob is not a rectangle that jumps to a number: it is a blob that swells
 * when you grab it, lags slightly behind the value, and stretches along the direction it travels.
 * This holds the five animations that produce that — [value], [pressProgress], [scaleX], [scaleY] and
 * [velocity] — and leaves the drawing to you. [pressProgress] is exactly what `liquify`'s
 * `pressProgress` wants, so a thumb that turns into a lens while it is held is two materials:
 *
 * ```
 * val drag = rememberDampedDragAnimation(
 *     initialValue = 0f,
 *     valueRange = 0f..1f,
 *     onDrag = { _, amount -> onValueChange(targetValue + amount.x / width) }
 * )
 *
 * Box(
 *     Modifier
 *         .then(drag.modifier)
 *         .liquify(
 *             shape = Capsule(),
 *             material = ThumbAtRest,
 *             pressedMaterial = ThumbPressed,
 *             pressProgress = { drag.pressProgress },
 *             layerBlock = { scaleX = drag.scaleX; scaleY = drag.scaleY }
 *         )
 * )
 * ```
 *
 * @param visibilityThreshold smallest change in [value] worth animating, in value units.
 * @param initialScale scale at rest.
 * @param pressedScale scale while held — above `1f` makes the control swell into the finger.
 * @param orientation the axis this control owns; movement across it is handed to the enclosing
 *   scroller. `null` claims any direction.
 * @param motion how bouncy the swell and the stretch are; see [GlassMotion].
 */
@Stable
public class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    public val initialValue: Float,
    public val valueRange: ClosedRange<Float>,
    public val visibilityThreshold: Float,
    public val initialScale: Float,
    public val pressedScale: Float,
    public val orientation: Orientation? = Orientation.Horizontal,
    motion: GlassMotion = GlassMotion.Default,
    private val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit = {},
    private val onDragStopped: DampedDragAnimation.() -> Unit = {},
    private val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit
) {

    /**
     * The motion preset, read when a press or a release starts and on every frame the velocity is
     * followed. The [value] itself is never made bouncy: call sites map it to positions and indices
     * that must not overshoot their range.
     */
    public var motion: GlassMotion = motion

    private val valueAnimationSpec = spring(1f, 970f, visibilityThreshold)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    /**
     * The velocity, followed by a spring stepped once per frame rather than an `Animatable`, which
     * would be re-aimed — a fresh coroutine — on every frame of a drag. See [SpringFollower].
     */
    private val velocitySpring = SpringFollower()
    private var velocityState by mutableFloatStateOf(0f)
    private var velocityTarget = 0f
    private var velocityLoop: Job? = null
    private val velocityThreshold = visibilityThreshold * 10f

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    public val value: Float get() = valueAnimation.value

    /** [value] mapped to `0f..1f` across [valueRange]. */
    public val progress: Float
        get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)

    /** Where the value is heading — read this in callbacks, not [value], which lags behind. */
    public val targetValue: Float get() = valueAnimation.targetValue

    public val pressProgress: Float get() = pressProgressAnimation.value
    public val scaleX: Float get() = scaleXAnimation.value
    public val scaleY: Float get() = scaleYAnimation.value

    /**
     * Normalised rate of change, for stretching the control along its direction of travel. Scaled a
     * little by the [motion] preset, so a control that stretches by it follows without knowing.
     */
    public val velocity: Float get() = velocityState

    /**
     * Gesture input. Once the gesture is recognised as this control's, its movement is consumed so
     * an enclosing scroller cannot act on it too; movement along the *other* axis is handed to the
     * scroller, so a vertical swipe that starts on a horizontal slider still scrolls the page.
     */
    public val modifier: Modifier = Modifier.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        inspectDragGestures(
            onDragStart = { down ->
                claim = Claim.Undecided
                accumulated = Offset.Zero
                onDragStarted(down.position)
                press()
            },
            onDragEnd = { finishGesture() },
            onDragCancel = { finishGesture() }
        ) { change, dragAmount ->
            if (dragAmount != Offset.Zero) {
                accumulated += dragAmount
                if (claim == Claim.Undecided && accumulated.getDistance() > slop) {
                    claim = when (orientation) {
                        Orientation.Horizontal ->
                            if (abs(accumulated.x) >= abs(accumulated.y)) Claim.Mine else Claim.Yielded

                        Orientation.Vertical ->
                            if (abs(accumulated.y) >= abs(accumulated.x)) Claim.Mine else Claim.Yielded

                        null -> Claim.Mine
                    }
                    // Let go visually at once, so the control does not look grabbed while the list
                    // scrolls past.
                    if (claim == Claim.Yielded) release()
                }
                if (claim == Claim.Mine) change.consume()
            }
            if (claim != Claim.Yielded) {
                onDrag(size, dragAmount)
            }
        }
    }

    private enum class Claim { Undecided, Mine, Yielded }

    private var claim = Claim.Undecided
    private var accumulated = Offset.Zero

    private fun finishGesture() {
        // A yielded gesture never belonged to this control, so it must not commit a value — that is
        // what would otherwise flip a toggle at the end of a scroll that merely started on it.
        if (claim != Claim.Yielded) {
            onDragStopped()
            release()
        }
    }

    public fun press() {
        velocityTracker.resetTracking()
        val spec = motion.spec
        val swollen = initialScale + (pressedScale - initialScale) * spec.swell
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, spec.dragPressSpec) }
            launch { scaleXAnimation.animateTo(swollen, spec.scaleXSpec) }
            launch { scaleYAnimation.animateTo(swollen, spec.scaleYSpec) }
        }
    }

    public fun release() {
        animationScope.launch {
            withFrameNanos { }
            // Hold the swollen state until the value has almost caught up; shrinking first would show
            // the thumb settling after it had already gone back to rest.
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            val spec = motion.spec
            launch { pressProgressAnimation.animateTo(0f, spec.dragPressSpec) }
            launch { scaleXAnimation.animateTo(initialScale, spec.scaleXSpec) }
            launch { scaleYAnimation.animateTo(initialScale, spec.scaleYSpec) }
        }
    }

    /** Animates towards [value] while tracking velocity — use this while dragging. */
    public fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() }
            // Arrived without being redirected: the stretch has nothing left to follow.
            followVelocity(0f)
        }
    }

    /** Presses, animates to [value] and releases — use this for taps and programmatic jumps. */
    public fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val targetValue = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                if (velocity != 0f) followVelocity(0f)
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val targetVelocity =
            velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        followVelocity(targetVelocity * motion.spec.velocityScale)
    }

    /**
     * Points the velocity spring at [target], starting the loop that steps it if it is at rest. The
     * loop ends by itself once the spring settles, so an untouched control costs no frames.
     */
    private fun followVelocity(target: Float) {
        velocityTarget = target
        if (velocityLoop?.isActive == true) return
        velocityLoop = animationScope.launch {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                // Capped, so a frame that arrives late after a pause cannot fling the spring.
                val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(MaxFrameSeconds)
                last = now
                val spec = motion.spec
                val settled = velocitySpring.advance(
                    target = velocityTarget,
                    dampingRatio = spec.velocityDamping,
                    stiffness = spec.velocityStiffness,
                    seconds = seconds,
                    threshold = velocityThreshold
                )
                velocityState = velocitySpring.value
                if (settled) break
            }
        }
    }
}

/**
 * Remembers a [DampedDragAnimation] bound to the current composition's coroutine scope.
 *
 * Every argument is read once, on the first composition, including the callbacks — this object *is*
 * the control's value, so rebuilding it would snap the control back mid-drag. The one exception is
 * [motion], which follows the material in scope.
 */
@Composable
public fun rememberDampedDragAnimation(
    initialValue: Float,
    valueRange: ClosedRange<Float>,
    visibilityThreshold: Float = 0.001f,
    initialScale: Float = 1f,
    pressedScale: Float = 1.5f,
    orientation: Orientation? = Orientation.Horizontal,
    motion: GlassMotion = LiquifyTheme.material.motion,
    onDragStarted: DampedDragAnimation.(position: Offset) -> Unit = {},
    onDragStopped: DampedDragAnimation.() -> Unit = {},
    onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit
): DampedDragAnimation {
    val animationScope = rememberCoroutineScope()
    val animation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = initialValue,
            valueRange = valueRange,
            visibilityThreshold = visibilityThreshold,
            initialScale = initialScale,
            pressedScale = pressedScale,
            orientation = orientation,
            motion = motion,
            onDragStarted = onDragStarted,
            onDragStopped = onDragStopped,
            onDrag = onDrag
        )
    }
    animation.motion = motion
    return animation
}

/** The longest step one frame may advance the velocity spring by. */
private const val MaxFrameSeconds = 1f / 30f
