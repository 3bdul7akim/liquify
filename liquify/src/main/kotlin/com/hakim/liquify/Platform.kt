/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Whether `android.graphics.RenderEffect` is available on this device (Android 12, API 31).
 *
 * When this returns `false`, the blur and every colour effect are skipped and glass renders as a
 * plain translucent surface with its rim and shadows.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
public fun isRenderEffectSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Whether `android.graphics.RuntimeShader` (AGSL) is available on this device (Android 13, API 33).
 *
 * Refraction, merging, the shader rims and the touch glow's hotspots all need it. Everything degrades
 * on its own when it returns `false`.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
public fun isRuntimeShaderSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
