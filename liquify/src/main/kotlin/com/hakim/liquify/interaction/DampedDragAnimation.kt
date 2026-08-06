/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 */

package com.hakim.liquify.interaction

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A draggable value with the gel physics liquid glass controls are built on.
 *
 * A slider thumb or a toggle knob is not a rectangle that jumps to a number: it is a blob that
 * swells when you grab it, lags slightly behind the value, and stretches along the direction it is
 * travelling. This holds the five animations that produce that — [value], [pressProgress],
 * [scaleX], [scaleY] and [velocity] — and leaves the drawing entirely to you.
 *
 * ```
 * val drag = rememberDampedDragAnimation(
 *     initialValue = 0f,
 *     valueRange = 0f..1f,
 *     onDrag = { _, dragAmount -> onValueChange(drag.targetValue + dragAmount.x / width) }
 * )
 *
 * Box(
 *     Modifier
 *         .then(drag.modifier)
 *         .liquify(
 *             shape = { Capsule() },
 *             // Blur while resting, refract while held: the thumb turns into a lens under
 *             // the finger and you can read the value straight through it.
 *             effects = {
 *                 blur(8.dp.toPx() * (1f - drag.pressProgress))
 *                 lens(10.dp.toPx() * drag.pressProgress, 14.dp.toPx() * drag.pressProgress)
 *             },
 *             backdrop = backdrop,
 *             layerBlock = { scaleX = drag.scaleX; scaleY = drag.scaleY }
 *         )
 * )
 * ```
 *
 * @param visibilityThreshold smallest change in [value] worth animating, in value units.
 * @param initialScale scale at rest.
 * @param pressedScale scale while held — above `1f` makes the control swell into the finger.
 */
@Stable
public class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    public val initialValue: Float,
    public val valueRange: ClosedRange<Float>,
    public val visibilityThreshold: Float,
    public val initialScale: Float,
    public val pressedScale: Float,
    /** Axis this control owns; movement across it is handed to the enclosing scroller. */
    public val orientation: Orientation? = Orientation.Horizontal,
    private val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit = {},
    private val onDragStopped: DampedDragAnimation.() -> Unit = {},
    private val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit
) {

    private val valueAnimationSpec = spring(1f, 970f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 270f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    // X and Y are damped differently on purpose: matched springs read as a rigid box scaling,
    // mismatched ones as something with surface tension.
    private val scaleXAnimationSpec = spring(0.6f, 230f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

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

    /** Normalised rate of change, for stretching the control along its direction of travel. */
    public val velocity: Float get() = velocityAnimation.value

    /**
     * Gesture input.
     *
     * Once the gesture is recognised as belonging to this control, its movement is consumed so an
     * enclosing scroller cannot act on it as well — dragging a slider must not also scroll the page
     * out from under it. Movement along the *other* axis is deliberately left alone and handed to
     * the scroller, so a vertical swipe that happens to start on a horizontal slider still scrolls
     * the list. Set [orientation] to `null` to claim any direction.
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
                    if (claim == Claim.Yielded) {
                        // Let go visually straight away, so the control does not sit there looking
                        // grabbed while the list scrolls past.
                        release()
                    }
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
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    public fun release() {
        animationScope.launch {
            awaitFrame()
            // Hold the swollen state until the value has almost caught up. Shrinking first would
            // show the thumb settling *after* it had already gone back to rest.
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    /** Animates towards [value] while tracking velocity — use this while dragging. */
    public fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() }
        }
    }

    /** Presses, animates to [value] and releases — use this for taps and programmatic jumps. */
    public fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val targetValue = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val targetVelocity =
            velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

/**
 * Remembers a [DampedDragAnimation] bound to the current composition's coroutine scope.
 *
 * **Every argument is read once, on the first composition,** including the callbacks. Passing a
 * value that changes later has no effect, and that is deliberate: this object *is* the control's
 * value, so rebuilding it would snap the control back to [initialValue] in the middle of a drag.
 */
@Composable
public fun rememberDampedDragAnimation(
    initialValue: Float,
    valueRange: ClosedRange<Float>,
    visibilityThreshold: Float = 0.001f,
    initialScale: Float = 1f,
    pressedScale: Float = 1.5f,
    orientation: Orientation? = Orientation.Horizontal,
    onDragStarted: DampedDragAnimation.(position: Offset) -> Unit = {},
    onDragStopped: DampedDragAnimation.() -> Unit = {},
    onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit
): DampedDragAnimation {
    val animationScope = rememberCoroutineScope()
    return remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = initialValue,
            valueRange = valueRange,
            visibilityThreshold = visibilityThreshold,
            initialScale = initialScale,
            pressedScale = pressedScale,
            orientation = orientation,
            onDragStarted = onDragStarted,
            onDragStopped = onDragStopped,
            onDrag = onDrag
        )
    }
}

private suspend fun awaitFrame() {
    withFrameNanos { }
}
