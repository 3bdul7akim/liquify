package com.hakim.liquify.internal

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * A value pulled towards a target by a damped spring, stepped by hand.
 *
 * [DampedDragAnimation] re-aims its velocity at a new target on every frame of a drag. As an
 * `Animatable` that was a fresh `animateTo` per frame, each one launched as a coroutine of its own
 * and cancelling the one before it — a job, a continuation and a mutator handover, sixty to a
 * hundred and twenty times a second, for a value that was only ever being nudged. Stepped here, one
 * loop follows the moving target for as long as it moves, and the spring keeps its momentum across
 * every change of target exactly as the interrupted animations did.
 */
internal class SpringFollower {

    var value: Float = 0f
        private set

    /** How fast [value] is changing, in its own units per second. */
    var rate: Float = 0f
        private set

    /**
     * Advances the spring by [seconds] towards [target].
     *
     * Integrated in sub-steps of at most a 240th of a second, so a long frame cannot make a stiff
     * spring explode, and returns `true` once it has come to rest on the target — within
     * [threshold] of it and moving slower than ten thresholds a second — at which point it is
     * snapped there exactly.
     */
    fun advance(
        target: Float,
        dampingRatio: Float,
        stiffness: Float,
        seconds: Float,
        threshold: Float
    ): Boolean {
        val steps = ceil(seconds / MaxStepSeconds).toInt().coerceAtLeast(1)
        val step = seconds / steps
        // Unit mass: the critical damping coefficient is 2·√k.
        val damping = 2f * dampingRatio * sqrt(stiffness)
        var current = value
        var currentRate = rate
        repeat(steps) {
            // Semi-implicit Euler: the rate first, then the position from the new rate. Explicit
            // Euler gains energy on every step and would ring a lightly damped spring up forever.
            currentRate += (-stiffness * (current - target) - damping * currentRate) * step
            current += currentRate * step
        }
        if (abs(current - target) < threshold && abs(currentRate) < threshold * RestRate) {
            value = target
            rate = 0f
            return true
        }
        value = current
        rate = currentRate
        return false
    }
}

private const val MaxStepSeconds = 1f / 240f

/** Below this many thresholds per second the spring counts as no longer moving. */
private const val RestRate = 10f
