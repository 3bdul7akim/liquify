/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import org.intellij.lang.annotations.Language

/**
 * A thin, allocation free wrapper around `android.graphics.RuntimeShader`.
 *
 * Instances must only be created when [isRuntimeShaderSupported] returns `true`; the constructor
 * touches an API 33 class. Use [RuntimeShaderCache.obtainRuntimeShader] instead of constructing
 * these directly so that a shader is compiled once and then only re-uniformed per frame.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class RuntimeShader(@Language("AGSL") shaderString: String) {

    internal val platformShader: android.graphics.RuntimeShader =
        android.graphics.RuntimeShader(shaderString)

    public fun setFloatUniform(name: String, value: Float) {
        platformShader.setFloatUniform(name, value)
    }

    public fun setFloatUniform(name: String, value1: Float, value2: Float) {
        platformShader.setFloatUniform(name, value1, value2)
    }

    public fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) {
        platformShader.setFloatUniform(name, value1, value2, value3)
    }

    public fun setFloatUniform(
        name: String,
        value1: Float,
        value2: Float,
        value3: Float,
        value4: Float
    ) {
        platformShader.setFloatUniform(name, value1, value2, value3, value4)
    }

    /** Sets a `floatN` or a `floatN[]` uniform; [values] must have exactly the declared length. */
    public fun setFloatUniform(name: String, values: FloatArray) {
        platformShader.setFloatUniform(name, values)
    }

    public fun setIntUniform(name: String, value: Int) {
        platformShader.setIntUniform(name, value)
    }

    public fun setIntUniform(name: String, values: IntArray) {
        platformShader.setIntUniform(name, values)
    }

    /** Sets a `layout(color) uniform half4`. The colour is converted to sRGB ARGB. */
    public fun setColorUniform(name: String, color: Color) {
        platformShader.setColorUniform(name, color.toArgb())
    }

    /** Binds another shader to a `uniform shader` input. */
    public fun setInputShader(name: String, shader: Shader) {
        platformShader.setInputShader(name, shader)
    }
}

/** Exposes this runtime shader as a Compose [Shader] so it can back a `ShaderBrush`. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public fun RuntimeShader.asComposeShader(): Shader = platformShader
