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
import com.hakim.liquify.RuntimeShader

/**
 * Softens whatever the paint draws, including strokes and shader fills.
 *
 * A mask filter blurs the *coverage* of the geometry, which is why it works for a rim stroke where
 * a layer-wide render effect would also blur what the shader put inside it.
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
