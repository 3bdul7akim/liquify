/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.internal

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativePaint

/**
 * Softens whatever the paint draws, including strokes and shader fills.
 *
 * A mask filter blurs the *coverage* of the geometry. The decoration only uses it below API 31,
 * which has no render effect to blur with; from there on it blurs a layer instead — see BlurLayer in
 * GlassDecoration for why.
 */
internal fun Paint.blur(radius: Float) {
    nativePaint.maskFilter =
        if (radius > 0f) BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL) else null
}

internal fun Paint.setRuntimeShader(runtimeShader: RuntimeShader?) {
    nativePaint.shader =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runtimeShader?.platformShader
        else null
}
