package com.hakim.liquify.internal

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorMatricesTest {

    /** Applies a 4×5 matrix to an unpremultiplied colour, translation in 0..255, without clamping. */
    private fun apply(matrix: FloatArray, color: FloatArray): FloatArray =
        FloatArray(4) { row ->
            var sum = matrix[row * 5 + 4] / 255f
            for (k in 0..3) sum += matrix[row * 5 + k] * color[k]
            sum
        }

    private val samples = listOf(
        floatArrayOf(0.2f, 0.4f, 0.6f, 1f),
        floatArrayOf(0.9f, 0.1f, 0.3f, 0.5f),
        floatArrayOf(0.5f, 0.5f, 0.5f, 0.25f),
    )

    private val matrices = listOf(
        FloatArray(ColorMatrices.SIZE).also { ColorMatrices.controls(0.1f, 1.2f, 1.6f, it) },
        FloatArray(ColorMatrices.SIZE).also { ColorMatrices.tint(1f, 0f, 0f, 0.5f, it) },
    )

    @Test
    fun concatIsTheSameAsApplyingBothInTurn() {
        val vibrancy = FloatArray(ColorMatrices.SIZE).also { ColorMatrices.controls(0f, 1f, 1.25f, it) }
        val tint = FloatArray(ColorMatrices.SIZE).also { ColorMatrices.tint(0.2f, 0.4f, 1f, 0.3f, it) }

        val combined = vibrancy.copyOf()
        ColorMatrices.concat(first = combined, second = tint, out = combined)

        for (color in samples) {
            val expected = apply(tint, apply(vibrancy, color))
            assertArrayEquals(expected, apply(combined, color), 1e-5f)
        }
    }

    @Test
    fun neutralControlsAreTheIdentity() {
        val matrix = FloatArray(ColorMatrices.SIZE).also { ColorMatrices.controls(0f, 1f, 1f, it) }
        for (color in samples) assertArrayEquals(color, apply(matrix, color), 1e-6f)
    }

    @Test
    fun saturatingOneColourIsTheMatrixOnThatColour() {
        // What glass over a colour backdrop shows, against what the vibrancy matrix gives every
        // pixel of a recorded layer: the same colour, clamped the way the filter clamps. A Color
        // holds eight bits a channel, hence the tolerance of half a step.
        val colors = listOf(
            Color(0.2f, 0.4f, 0.6f, 1f),
            Color(0.9f, 0.1f, 0.3f, 0.5f),
            Color(0.5f, 0.5f, 0.5f, 0.25f),
            Color(0.98f, 0.01f, 0.99f, 1f),
        )
        for (saturation in floatArrayOf(0f, 0.5f, 1.25f, 1.5f, 3f)) {
            val matrix = FloatArray(ColorMatrices.SIZE).also { ColorMatrices.controls(0f, 1f, saturation, it) }
            for (color in colors) {
                val mapped = apply(matrix, floatArrayOf(color.red, color.green, color.blue, color.alpha))
                val expected = FloatArray(4) { mapped[it].coerceIn(0f, 1f) }
                val actual = ColorMatrices.saturate(color, saturation)
                assertArrayEquals(
                    expected,
                    floatArrayOf(actual.red, actual.green, actual.blue, actual.alpha),
                    0.5f / 255f + 1e-5f
                )
            }
        }
    }

    @Test
    fun aSaturationOfOneLeavesTheColourAsItIs() {
        val color = Color(0.3f, 0.6f, 0.9f, 0.4f)
        assertEquals(color, ColorMatrices.saturate(color, 1f))
    }

    @Test
    fun everyColourMatrixLeavesAlphaAlone() {
        // What lets the folded matrix move across a blur, and what the shader's premultiplied form
        // relies on: alpha maps to itself.
        for (matrix in matrices) {
            assertEquals(0f, matrix[15], 0f)
            assertEquals(0f, matrix[16], 0f)
            assertEquals(0f, matrix[17], 0f)
            assertEquals(1f, matrix[18], 0f)
            assertEquals(0f, matrix[19], 0f)
        }
    }

    @Test
    fun noColourChannelReadsAlpha() {
        // The other half of what the premultiplied form in COLOR_MATRIX relies on.
        for (matrix in matrices) {
            assertEquals(0f, matrix[3], 0f)
            assertEquals(0f, matrix[8], 0f)
            assertEquals(0f, matrix[13], 0f)
        }
    }

    @Test
    fun shaderUniformsAreColumnMajorWithTheOffsetInUnitRange() {
        val source = FloatArray(ColorMatrices.SIZE) { it.toFloat() }
        val matrix = FloatArray(9)
        val offset = FloatArray(3)
        ColorMatrices.toShaderUniforms(source, matrix, offset)

        // Column 1, row 2 of the linear part is entry (2, 1) of the row-major source.
        assertEquals(source[2 * 5 + 1], matrix[1 * 3 + 2], 0f)
        assertEquals(source[0 * 5 + 2], matrix[2 * 3 + 0], 0f)
        assertArrayEquals(floatArrayOf(4f / 255f, 9f / 255f, 14f / 255f), offset, 1e-7f)
    }

    @Test
    fun premultipliedFormIsTheFilter() {
        // What a ColorMatrixColorFilter does — unpremultiply, map, clamp, premultiply — against what
        // COLOR_MATRIX does with the uniforms: the same colours, in or out of range.
        val combined = FloatArray(ColorMatrices.SIZE).also { ColorMatrices.controls(0.2f, 1.4f, 1.8f, it) }
        val tint = FloatArray(ColorMatrices.SIZE).also { ColorMatrices.tint(0.9f, 0.2f, 0.1f, 0.4f, it) }
        ColorMatrices.concat(first = combined, second = tint, out = combined)

        val matrix = FloatArray(9)
        val offset = FloatArray(3)
        ColorMatrices.toShaderUniforms(combined, matrix, offset)

        val premultiplied = listOf(
            floatArrayOf(0.2f, 0.4f, 0.6f, 1f),
            floatArrayOf(0.45f, 0.05f, 0.15f, 0.5f),
            floatArrayOf(0.125f, 0.125f, 0.125f, 0.25f),
            floatArrayOf(0f, 0f, 0f, 0f),
            floatArrayOf(0.98f, 0.01f, 0.99f, 1f),
        )
        for (color in premultiplied) {
            val a = color[3]
            val straight = floatArrayOf(
                if (a > 0f) color[0] / a else 0f,
                if (a > 0f) color[1] / a else 0f,
                if (a > 0f) color[2] / a else 0f,
                a
            )
            val mapped = apply(combined, straight).map { it.coerceIn(0f, 1f) }
            val expected = floatArrayOf(mapped[0] * mapped[3], mapped[1] * mapped[3], mapped[2] * mapped[3], mapped[3])

            val actual = FloatArray(4)
            for (row in 0..2) {
                var sum = offset[row] * a
                for (column in 0..2) sum += matrix[column * 3 + row] * color[column]
                actual[row] = sum.coerceIn(0f, a)
            }
            actual[3] = a
            assertArrayEquals(expected, actual, 1e-5f)
        }
    }
}
