package com.hakim.liquify.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefractionBySizeTest {

    private val tolerance = 0.0001f

    @Test
    fun `a 32 dp circle gets 8 and 12`() {
        assertEquals(8f, refractionHeightForSize(32f, 32f), tolerance)
        assertEquals(12f, refractionAmountForSize(32f, 32f), tolerance)
    }

    @Test
    fun `a 48 dp tall capsule gets 12 and 24, however wide it is`() {
        for (width in listOf(96f, 200f, 360f)) {
            assertEquals(12f, refractionHeightForSize(width, 48f), tolerance)
            assertEquals(24f, refractionAmountForSize(width, 48f), tolerance)
        }
    }

    @Test
    fun `a square is treated as round at any size`() {
        assertEquals(12f, refractionHeightForSize(48f, 48f), tolerance)
        assertEquals(18f, refractionAmountForSize(48f, 48f), tolerance)
        assertEquals(25f, refractionHeightForSize(100f, 100f), tolerance)
        assertEquals(37.5f, refractionAmountForSize(100f, 100f), tolerance)
    }

    @Test
    fun `orientation does not matter, only the short side and the aspect`() {
        assertEquals(refractionHeightForSize(200f, 48f), refractionHeightForSize(48f, 200f), tolerance)
        assertEquals(refractionAmountForSize(200f, 48f), refractionAmountForSize(48f, 200f), tolerance)
    }

    @Test
    fun `both grow in proportion with the element`() {
        assertEquals(2f * refractionHeightForSize(40f, 100f), refractionHeightForSize(80f, 200f), tolerance)
        assertEquals(2f * refractionAmountForSize(40f, 100f), refractionAmountForSize(80f, 200f), tolerance)
    }

    @Test
    fun `the displacement rises steadily from round to elongated`() {
        var previous = refractionAmountForSize(48f, 48f)
        var width = 48f
        while (width <= 120f) {
            val amount = refractionAmountForSize(width, 48f)
            assertTrue("amount fell at width $width", amount >= previous - tolerance)
            previous = amount
            width += 2f
        }
        assertEquals(24f, previous, tolerance)
    }

    @Test
    fun `an unmeasured element gets no refraction`() {
        assertEquals(0f, refractionHeightForSize(0f, 48f), tolerance)
        assertEquals(0f, refractionAmountForSize(48f, 0f), tolerance)
    }
}
