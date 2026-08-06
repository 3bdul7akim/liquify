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
 * When this returns `false`, [blur][com.hakim.liquify.effects.blur] and every colour effect are
 * silently skipped and glass elements render as a plain translucent surface.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
public fun isRenderEffectSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Whether `android.graphics.RuntimeShader` (AGSL) is available on this device (Android 13, API 33).
 *
 * Refraction ([lens][com.hakim.liquify.effects.lens]), the merge/gooey pass and the shader based
 * highlight styles all require this. Everything degrades gracefully when it returns `false`.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
public fun isRuntimeShaderSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
