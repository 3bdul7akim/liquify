package com.hakim.liquify.internal

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hakim.liquify.GlassMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefractionTest {

    // A Regular pane at full size: a 20 dp band and a 35 dp bend.
    private val band = 20f
    private val bend = 35f

    @Test
    fun `small elements are refracted in proportion to their size`() {
        // A quarter of the short side, and three eighths for something round — within the few per
        // cent the rounded corner of the cap takes.
        assertEquals(8f, refractionHeightForSize(32f, 32f, band), 0.1f)
        assertEquals(12f, refractionAmountForSize(32f, 32f, bend), 0.1f)
    }

    @Test
    fun `large panes ease into the material's own values`() {
        assertEquals(band, refractionHeightForSize(400f, 800f, band), 0.05f)
        assertEquals(bend, refractionAmountForSize(400f, 800f, bend), 0.1f)
        assertTrue(refractionHeightForSize(10_000f, 10_000f, band) <= band)
    }

    @Test
    fun `nothing jumps while an element grows`() {
        var previousBand = 0f
        var previousBend = 0f
        var side = 1f
        while (side <= 600f) {
            val nextBand = refractionHeightForSize(side, side, band)
            val nextBend = refractionAmountForSize(side, side, bend)
            assertTrue("band fell at $side", nextBand >= previousBand)
            assertTrue("bend fell at $side", nextBend >= previousBend)
            // Never more than a quarter of the step: no kink, no jump.
            assertTrue("band jumped at $side", nextBand - previousBand <= 0.25f + 1e-4f)
            previousBand = nextBand
            previousBend = nextBend
            side += 1f
        }
    }

    @Test
    fun `never more than either rule on its own`() {
        for (side in listOf(16f, 48f, 80f, 120f, 300f)) {
            assertTrue(refractionHeightForSize(side, side, band) <= minOf(side / 4f, band) + 1e-4f)
        }
    }

    @Test
    fun `elongated elements carry more bend than round ones of the same height`() {
        val round = refractionAmountForSize(48f, 48f, bend)
        val capsule = refractionAmountForSize(200f, 48f, bend)
        assertTrue(capsule > round)
        assertEquals(refractionAmountForSize(200f, 48f, bend), refractionAmountForSize(48f, 200f, bend), 0f)
    }

    @Test
    fun `the rule is the same at every density`() {
        // Pixels in, pixels out: doubling the element and the full-size value doubles the result.
        assertEquals(
            2f * refractionHeightForSize(40f, 100f, band),
            refractionHeightForSize(80f, 200f, band * 2f),
            1e-3f
        )
        assertEquals(
            2f * refractionAmountForSize(40f, 100f, bend),
            refractionAmountForSize(80f, 200f, bend * 2f),
            1e-3f
        )
    }

    @Test
    fun `an unmeasured element and a zero material get no refraction`() {
        assertEquals(0f, refractionHeightForSize(0f, 48f, band), 0f)
        assertEquals(0f, refractionAmountForSize(48f, 0f, bend), 0f)
        assertEquals(0f, refractionHeightForSize(48f, 48f, 0f), 0f)
        assertEquals(0f, softMin(0f, 10f), 0f)
    }

    @Test
    fun `a material scales its result and can opt out of the size rule`() {
        val density = Density(2f)
        val regular = GlassMaterial.Regular
        val thicker = regular.copy(refractionHeightScale = 1.5f)
        assertEquals(
            regular.refractionHeightPx(64f, 64f, density) * 1.5f,
            thicker.refractionHeightPx(64f, 64f, density),
            1e-4f
        )
        val fixed = regular.copy(refractionBySize = false, refractionHeight = 12.dp)
        assertEquals(24f, fixed.refractionHeightPx(64f, 64f, density), 0f)
    }
}
