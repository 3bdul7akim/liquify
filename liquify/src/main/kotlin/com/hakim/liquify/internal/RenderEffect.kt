/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.internal

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.hakim.liquify.RuntimeShader

/**
 * Chains [other] *after* the receiver, so the receiver is evaluated first and its result becomes
 * the input of [other].
 */
@RequiresApi(Build.VERSION_CODES.S)
internal fun RenderEffect?.chain(other: RenderEffect): RenderEffect =
    if (this != null) {
        android.graphics.RenderEffect.createChainEffect(
            other.asAndroidRenderEffect(),
            asAndroidRenderEffect()
        ).asComposeRenderEffect()
    } else {
        other
    }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun RuntimeShaderEffect(
    runtimeShader: RuntimeShader,
    uniformShaderName: String
): RenderEffect =
    android.graphics.RenderEffect.createRuntimeShaderEffect(
        runtimeShader.platformShader,
        uniformShaderName
    ).asComposeRenderEffect()

/**
 * Composites two effects that each read the *same* input, rather than feeding one into the other.
 *
 * This is the only way to build an effect whose strength varies across the element: nothing can
 * recover detail once it has been blurred, so a partial blur has to be a blurred copy and a sharp
 * copy blended together. Both branches re-evaluate whatever came before them, which is why the
 * cheap colour work belongs in front of it and the expensive work does not.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal fun BlendEffect(
    destination: RenderEffect,
    source: RenderEffect,
    blendMode: android.graphics.BlendMode
): RenderEffect =
    android.graphics.RenderEffect.createBlendModeEffect(
        destination.asAndroidRenderEffect(),
        source.asAndroidRenderEffect(),
        blendMode
    ).asComposeRenderEffect()

@RequiresApi(Build.VERSION_CODES.S)
internal fun ColorFilterEffect(
    renderEffect: RenderEffect?,
    colorFilter: ColorFilter
): RenderEffect =
    if (renderEffect != null) {
        android.graphics.RenderEffect.createColorFilterEffect(
            colorFilter.asAndroidColorFilter(),
            renderEffect.asAndroidRenderEffect()
        ).asComposeRenderEffect()
    } else {
        android.graphics.RenderEffect.createColorFilterEffect(
            colorFilter.asAndroidColorFilter()
        ).asComposeRenderEffect()
    }
