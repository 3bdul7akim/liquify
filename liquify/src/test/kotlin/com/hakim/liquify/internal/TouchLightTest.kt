package com.hakim.liquify.internal

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TouchLightTest {

    /**
     * Colours land in 8-bit sRGB — the tint's alpha going in and the light coming out — so a
     * component may be off by two steps.
     */
    private val step = 2f / 255f

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals("red", expected.red, actual.red, step)
        assertEquals("green", expected.green, actual.green, step)
        assertEquals("blue", expected.blue, actual.blue, step)
        assertEquals("alpha", expected.alpha, actual.alpha, step)
    }

    @Test
    fun clearGlassGlowsInTheLightItself() {
        assertColor(Color.White, touchLightColor(Color.White, Color.Unspecified))
        assertColor(Color.White, touchLightColor(Color.White, Color.Red.copy(alpha = 0f)))
    }

    @Test
    fun fullyTintedGlassGlowsInItsOwnColour() {
        assertColor(Color.Red, touchLightColor(Color.White, Color.Red))
    }

    @Test
    fun aPartTintGlowsAsFarAsItIsColoured() {
        // Half way from white to blue.
        assertColor(Color(0.5f, 0.5f, 1f), touchLightColor(Color.White, Color.Blue.copy(alpha = 0.5f)))
    }

    @Test
    fun theLightIsAlwaysOpaque() {
        // Its strength is applied by the program, so its own alpha must not dim it a second time.
        assertEquals(1f, touchLightColor(Color.White.copy(alpha = 0.3f), Color.Unspecified).alpha, 0f)
        assertEquals(1f, touchLightColor(Color.White, Color.Green.copy(alpha = 0.4f)).alpha, 0f)
    }

    @Test
    fun anUnspecifiedLightFallsBackToWhite() {
        assertColor(Color.White, touchLightColor(Color.Unspecified, Color.Unspecified))
    }
}
