package com.hakim.liquify

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GlassMotionTest {

    @Test
    fun defaultIsExactlyTheMotionFromBeforeThePresets() {
        val motion = GlassMotion.Default.spec
        assertEquals(spring(0.5f, 300f, 0.001f), motion.pressSpec)
        assertEquals(spring(0.5f, 300f, Offset.VisibilityThreshold), motion.releaseSpec)
        assertEquals(spring(1f, 1000f, 0.001f), motion.dragPressSpec)
        assertEquals(spring(0.6f, 230f, 0.001f), motion.scaleXSpec)
        assertEquals(spring(0.7f, 250f, 0.001f), motion.scaleYSpec)
        assertEquals(0.5f, motion.velocityDamping, 0f)
        assertEquals(270f, motion.velocityStiffness, 0f)
        val factors = listOf(motion.response, motion.reach, motion.stretch, motion.velocityScale, motion.swell)
        for (factor in factors) {
            assertEquals(1f, factor, 0f)
        }
        assertEquals(0f, motion.squash, 0f)
    }

    @Test
    fun presetsAreOrderedFromCalmToBouncy() {
        val performance = GlassMotion.Performance.spec
        val default = GlassMotion.Default.spec
        val bouncy = GlassMotion.Bouncy.spec

        assertTrue(performance.stretch < default.stretch && default.stretch < bouncy.stretch)
        assertTrue(performance.reach < default.reach && default.reach < bouncy.reach)
        assertTrue(performance.swell < default.swell && default.swell < bouncy.swell)
        assertTrue(performance.velocityScale < default.velocityScale)
        assertTrue(default.velocityScale < bouncy.velocityScale)
        // Higher damping is calmer.
        assertTrue(performance.pressSpec.dampingRatio > default.pressSpec.dampingRatio)
        assertTrue(bouncy.pressSpec.dampingRatio < default.pressSpec.dampingRatio)
        assertTrue(performance.releaseSpec.dampingRatio > default.releaseSpec.dampingRatio)
        assertTrue(bouncy.releaseSpec.dampingRatio < default.releaseSpec.dampingRatio)
        assertTrue(bouncy.velocityDamping < default.velocityDamping)
    }

    @Test
    fun sizesDifferOnlyALittleFromTheDefault() {
        // The presets are moods of one material: anything that changes how large a pane gets may
        // move by a few per cent, never so far that one preset looks like a different control.
        for (preset in GlassMotion.entries) {
            val motion = preset.spec
            for (factor in listOf(motion.stretch, motion.swell, motion.reach)) {
                assertTrue("$preset: $factor", abs(factor - 1f) <= 0.1f + 1e-5f)
            }
            assertTrue("$preset squash ${motion.squash}", motion.squash <= 0.2f)
        }
    }

    @Test
    fun noPresetMakesAControlsPressProgressOvershoot() {
        // Call sites feed it into blur radii and lens strengths; see DampedDragAnimation.motion.
        for (preset in GlassMotion.entries) {
            assertEquals(1f, preset.spec.dragPressSpec.dampingRatio, 0f)
        }
    }
}
