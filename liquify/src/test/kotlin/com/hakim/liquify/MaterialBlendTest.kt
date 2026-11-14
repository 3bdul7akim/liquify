package com.hakim.liquify

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hakim.liquify.internal.sameEffectsAs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialBlendTest {

    @Test
    fun restAndFullPressAreTheTwoValuesThemselves() {
        val rest = GlassRim.Default
        val pressed = GlassRim.Default.copy(width = 2.dp)
        assertSame(rest, lerpRim(rest, pressed, 0f))
        assertSame(pressed, lerpRim(rest, pressed, 1f))
    }

    @Test
    fun aRimThatOnlyExistsWhilePressedFadesIn() {
        val pressed = GlassRim.TwSe
        assertNull(lerpRim(null, pressed, 0f))
        assertEquals(0.25f, lerpRim(null, pressed, 0.25f)!!.alpha, 1e-6f)
        assertEquals(pressed.width, lerpRim(null, pressed, 0.25f)!!.width)
    }

    @Test
    fun differentStylesCrossThroughNothingRatherThanMixing() {
        val a = GlassRim.Default
        val b = GlassRim.Ambient
        assertEquals(a.style, lerpRim(a, b, 0.25f)!!.style)
        assertEquals(0.5f, lerpRim(a, b, 0.25f)!!.alpha, 1e-6f)
        assertEquals(b.style, lerpRim(a, b, 0.75f)!!.style)
        assertEquals(0.5f, lerpRim(a, b, 0.75f)!!.alpha, 1e-6f)
    }

    @Test
    fun anInnerShadowGrowsInRatherThanFadingAtFullSize() {
        val pressed = GlassInnerShadow(radius = 8.dp)
        val half = lerpInnerShadow(null, pressed, 0.5f)!!
        assertEquals(4.dp, half.radius)
        assertEquals(0.5f, half.alpha, 1e-6f)
        assertEquals(4.dp, half.offset.y)
    }

    @Test
    fun anUnspecifiedShadowColourIsSwitchedNotMixed() {
        val themed = GlassShadow(color = Color.Unspecified)
        val red = GlassShadow(color = Color.Red)
        assertEquals(Color.Unspecified, lerpShadow(themed, red, 0.4f)!!.color)
        assertEquals(Color.Red, lerpShadow(themed, red, 0.6f)!!.color)
    }

    @Test
    fun aShadowThatOnlyFadesKeepsEverythingElseExact() {
        // The shadow layer is recorded keyed on the radius, offset and colour: a pulse of its alpha
        // must not move them by a rounding error, or it re-records the layer on every frame.
        val rest = GlassShadow(radius = 10.dp, color = Color.Red, alpha = 0.26f)
        val pulsed = rest.copy(alpha = 0.85f)
        for (step in 1 until 100) {
            val blend = lerpShadow(rest, pulsed, step / 100f)!!
            assertEquals(rest.radius.value.toRawBits(), blend.radius.value.toRawBits())
            assertEquals(rest.offset, blend.offset)
            assertEquals(rest.color, blend.color)
        }
    }

    @Test
    fun onlyTheRecipeDecidesWhetherAPressRebuildsTheChain() {
        val rest = GlassMaterial.Regular
        assertTrue(rest.sameEffectsAs(rest.copy(tint = Color.Red, rim = null, shadow = null)))
        assertFalse(rest.sameEffectsAs(rest.copy(blurRadius = 0.dp)))
        assertFalse(rest.sameEffectsAs(rest.copy(refractionAmount = 12.dp)))
    }

    @Test
    fun aTintFadingToClearKeepsItsColour() {
        // Mixing towards transparent black would grey a white knob on its way out.
        val half = lerpTint(Color.White, Color.Transparent, 0.5f)
        assertEquals(1f, half.red, 1e-6f)
        assertEquals(1f, half.green, 1e-6f)
        // Color keeps alpha at eight bits: 0.5 is stored as 128 / 255.
        assertEquals(0.5f, half.alpha, 1f / 255f)
        assertEquals(Color.Unspecified, lerpTint(Color.Unspecified, Color.Transparent, 0.5f))
    }
}
