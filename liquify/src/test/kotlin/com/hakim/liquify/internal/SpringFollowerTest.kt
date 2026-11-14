package com.hakim.liquify.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringFollowerTest {

    /** Runs [frames] frames of [frameSeconds] and returns the largest value seen and whether it settled. */
    private fun SpringFollower.run(
        target: Float,
        dampingRatio: Float,
        stiffness: Float,
        frames: Int,
        frameSeconds: Float = 1f / 60f
    ): Pair<Float, Boolean> {
        var peak = value
        var settled = false
        repeat(frames) {
            if (!settled) {
                settled = advance(target, dampingRatio, stiffness, frameSeconds, threshold = 0.001f)
                peak = maxOf(peak, value)
            }
        }
        return peak to settled
    }

    @Test
    fun criticallyDampedSettlesOnTheTargetWithoutOvershoot() {
        val spring = SpringFollower()
        val (peak, settled) = spring.run(target = 1f, dampingRatio = 1f, stiffness = 600f, frames = 120)
        assertTrue(settled)
        assertEquals(1f, spring.value, 0f)
        assertEquals(0f, spring.rate, 0f)
        assertTrue("peak $peak", peak <= 1f)
    }

    @Test
    fun underdampedOvershootsThenSettles() {
        val spring = SpringFollower()
        val (peak, settled) = spring.run(target = 1f, dampingRatio = 0.25f, stiffness = 200f, frames = 600)
        assertTrue("peak $peak", peak > 1.3f)
        assertTrue(settled)
        assertEquals(1f, spring.value, 0f)
    }

    @Test
    fun theResultDoesNotDependOnTheFrameRate() {
        val at60 = SpringFollower().apply { run(1f, 0.5f, 270f, frames = 6) }
        val at120 = SpringFollower().apply { run(1f, 0.5f, 270f, frames = 12, frameSeconds = 1f / 120f) }
        assertEquals(at60.value, at120.value, 0.002f)
    }

    @Test
    fun aLongFrameCannotMakeAStiffSpringExplode() {
        val spring = SpringFollower()
        spring.advance(target = 1f, dampingRatio = 1f, stiffness = 1500f, seconds = 0.5f, threshold = 0.001f)
        assertTrue("value ${spring.value}", spring.value in 0f..1.0001f)
    }
}
