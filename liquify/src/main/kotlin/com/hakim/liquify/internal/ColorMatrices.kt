package com.hakim.liquify.internal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.util.fastCoerceIn

/**
 * 4×5 colour matrices in the layout of `android.graphics.ColorMatrix`: row-major, one row per output
 * channel — `r g b a translate` — with the translation in `0..255`.
 *
 * The colour effects build these instead of a filter each, so that consecutive ones can be folded
 * into a single matrix and that matrix folded into the next shader — see
 * `BaseBackdropEffectScope.appendColor`.
 *
 * Everything writes into caller-owned arrays: the effects block re-runs on every frame something it
 * reads is animating, and none of this may allocate there.
 */
internal object ColorMatrices {

    const val SIZE: Int = 20

    // Luminance weights of the sRGB primaries.
    private const val LumaRed = 0.213f
    private const val LumaGreen = 0.715f
    private const val LumaBlue = 0.072f

    /** Scratch for [concat]. Main thread only, like every effects block that reaches it. */
    private val concatScratch = FloatArray(SIZE)

    /**
     * Brightness, contrast and saturation in one matrix — the one `colorControls()` and
     * `vibrancy()` have always applied.
     */
    fun controls(brightness: Float, contrast: Float, saturation: Float, out: FloatArray) {
        // The luminance weights, spread over the off-diagonal so that desaturating preserves
        // perceived brightness.
        val inverseSaturation = 1f - saturation
        val r = LumaRed * inverseSaturation
        val g = LumaGreen * inverseSaturation
        val b = LumaBlue * inverseSaturation

        val translate = (0.5f - contrast * 0.5f + brightness) * 255f

        val cr = contrast * r
        val cg = contrast * g
        val cb = contrast * b
        val cs = contrast * saturation

        // Written entry by entry: a vararg helper would allocate the very array this avoids.
        out.fill(0f)
        out[0] = cr + cs
        out[1] = cg
        out[2] = cb
        out[4] = translate
        out[5] = cr
        out[6] = cg + cs
        out[7] = cb
        out[9] = translate
        out[10] = cr
        out[11] = cg
        out[12] = cb + cs
        out[14] = translate
        out[18] = 1f
    }

    /**
     * [color] as [controls] leaves it at this [saturation], brightness and contrast untouched — the
     * same matrix, worked out once on one colour instead of on every pixel of a layer. For glass
     * whose whole backdrop is that colour. Clamped as the filter and the program clamp; alpha is
     * left alone.
     */
    fun saturate(color: Color, saturation: Float): Color {
        if (saturation == 1f) return color
        val source = color.convert(ColorSpaces.Srgb)
        val grey = (LumaRed * source.red + LumaGreen * source.green + LumaBlue * source.blue) *
            (1f - saturation)
        return Color(
            red = (grey + saturation * source.red).fastCoerceIn(0f, 1f),
            green = (grey + saturation * source.green).fastCoerceIn(0f, 1f),
            blue = (grey + saturation * source.blue).fastCoerceIn(0f, 1f),
            alpha = source.alpha
        )
    }

    /** Blends towards [red], [green], [blue] (`0..1`) by [amount] — a tinted pane of glass. */
    fun tint(red: Float, green: Float, blue: Float, amount: Float, out: FloatArray) {
        val keep = 1f - amount
        out.fill(0f)
        out[0] = keep
        out[4] = red * amount * 255f
        out[6] = keep
        out[9] = green * amount * 255f
        out[12] = keep
        out[14] = blue * amount * 255f
        out[18] = 1f
    }

    /**
     * Writes into [out] the matrix that applies [first] and then [second]. [out] may be either of
     * the two.
     *
     * Exact as long as [first] does not push a channel out of `0..1`: a filter clamps its result,
     * a product of matrices only clamps once at the end.
     */
    fun concat(first: FloatArray, second: FloatArray, out: FloatArray) {
        val result = concatScratch
        for (row in 0..3) {
            val r = row * 5
            for (column in 0..4) {
                var sum = 0f
                for (k in 0..3) sum += second[r + k] * first[k * 5 + column]
                // The translation column carries the second matrix's own translation on top.
                result[r + column] = if (column == 4) sum + second[r + 4] else sum
            }
        }
        result.copyInto(out)
    }

    /**
     * The matrix as `COLOR_MATRIX` wants it: the colour rows' linear part as a column-major `half3x3`
     * in [matrix] and their translation in `0..1` in [offset].
     *
     * That leaves out the alpha row and the alpha column of the colour rows, and the program works on
     * premultiplied colour on the strength of exactly that: every matrix [controls] and [tint] build —
     * and so every product [concat] makes of them — leaves alpha to alpha and keeps colour from
     * reading it.
     */
    fun toShaderUniforms(colorMatrix: FloatArray, matrix: FloatArray, offset: FloatArray) {
        for (row in 0..2) {
            for (column in 0..2) matrix[column * 3 + row] = colorMatrix[row * 5 + column]
            offset[row] = colorMatrix[row * 5 + 4] / 255f
        }
    }

    /** Folds every entry into one value, for the uniform fingerprints. */
    fun fingerprint(colorMatrix: FloatArray): Long {
        var hash = 23L
        for (value in colorMatrix) hash = hash * 31L + value.toRawBits()
        return hash
    }
}

/**
 * The colour-matrix filter one effect scope last chained, kept until its matrix changes.
 *
 * Each filter is three allocations plus a platform filter built on first use, and an effects block
 * driven by a press re-runs on every frame — so a fresh one there would allocate all of that per frame
 * per element to describe a colour transform that had not moved. One slot: the colour effects fold
 * into a single matrix, so a chain holds at most one filter built from them.
 */
internal class ColorFilterCache {

    private val matrix = FloatArray(ColorMatrices.SIZE)
    private var filter: ColorFilter? = null

    fun matrix(values: FloatArray): ColorFilter {
        val cached = filter
        if (cached != null && values.contentEquals(matrix)) return cached
        values.copyInto(matrix)
        // A copy: the scope keeps editing the array it handed in, and the filter must not see that.
        return ColorMatrixColorFilter(ColorMatrix(values.copyOf())).also { filter = it }
    }

    fun clear() {
        filter = null
    }
}
