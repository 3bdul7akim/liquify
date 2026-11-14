/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.internal

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import com.hakim.liquify.isRuntimeShaderSupported
import org.intellij.lang.annotations.Language

/**
 * A thin wrapper around `android.graphics.RuntimeShader` that remembers the program it was compiled
 * from.
 *
 * The source is what lets [RuntimeShaderPool] prove that a recycled instance really is the program a
 * caller asked for: cache keys are only a promise, and a pool shared by every element in the app is
 * exactly where two callers who picked the same key for different programs would meet.
 *
 * Only constructed where [isRuntimeShaderSupported] holds; the constructor touches an API 33 class.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class RuntimeShader(@Language("AGSL") val source: String) {

    val platformShader: android.graphics.RuntimeShader = android.graphics.RuntimeShader(source)

    fun setFloatUniform(name: String, value: Float) {
        platformShader.setFloatUniform(name, value)
    }

    fun setFloatUniform(name: String, value1: Float, value2: Float) {
        platformShader.setFloatUniform(name, value1, value2)
    }

    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) {
        platformShader.setFloatUniform(name, value1, value2, value3)
    }

    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
        platformShader.setFloatUniform(name, value1, value2, value3, value4)
    }

    /** Sets a `floatN` or a `floatN[]` uniform; [values] must have exactly the declared length. */
    fun setFloatUniform(name: String, values: FloatArray) {
        platformShader.setFloatUniform(name, values)
    }

    /** Sets a `layout(color) uniform half4`. The colour is converted to sRGB ARGB. */
    fun setColorUniform(name: String, color: Color) {
        platformShader.setColorUniform(name, color.toArgb())
    }

    /** This program as a Compose [Shader], so it can back a `ShaderBrush`. */
    fun asComposeShader(): Shader = platformShader
}

/** Compiles each distinct AGSL program at most once per owner and hands back the same instance. */
internal interface RuntimeShaderCache {

    /**
     * The cached shader for [key], compiled from [string] on first use. [key] must uniquely identify
     * the program text.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = HashMap<String, RuntimeShader>(4)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader =
        runtimeShaders[key]
            ?: (RuntimeShaderPool.acquire(key, string) ?: RuntimeShader(string))
                .also { runtimeShaders[key] = it }

    /**
     * The same, except the program text is only built on a miss.
     *
     * The merged surface generates a different program per member count, and the eager form would
     * pay for generating several kilobytes of text on every call — once per frame — only to throw it
     * away. Only for the library's own generated programs, whose keys encode everything the text
     * depends on: a pooled instance is taken on the key alone.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun obtainRuntimeShader(key: String, source: () -> String): RuntimeShader =
        runtimeShaders[key]
            ?: (RuntimeShaderPool.acquire(key, expectedSource = null) ?: RuntimeShader(source()))
                .also { runtimeShaders[key] = it }

    /** Hands every shader back to [RuntimeShaderPool], so the next element skips the compile. */
    fun clear() {
        if (runtimeShaders.isEmpty()) return
        if (isRuntimeShaderSupported()) {
            for ((key, shader) in runtimeShaders) RuntimeShaderPool.release(key, shader)
        }
        runtimeShaders.clear()
    }
}

/**
 * Compiled programs no element is using at the moment, kept for the next one that asks.
 *
 * Every element owns its shader instances, because each carries its own uniforms — but the compile is
 * the same for all of them, and it is paid on the main thread in the very frame the element appears.
 * An editor panel attaching a dozen controls at once would parse the refraction and rim programs a
 * dozen times. A detaching element hands its instances back here instead, and an attaching one takes
 * an idle instance before it compiles.
 *
 * Reuse is safe because nothing keeps pointing at a shader it no longer owns: render effects and
 * recorded paints snapshot the uniforms when they are built, and every cache that takes a recycled
 * instance starts with its uniform fingerprints cleared.
 *
 * Main thread only, like every draw and attach path that reaches it.
 */
internal object RuntimeShaderPool {

    /** Idle instances kept per program — a burst larger than this is not coming back. */
    private const val MaxIdlePerProgram = 8

    private val idle = HashMap<String, ArrayList<RuntimeShader>>(16)

    /**
     * An idle instance of the program registered under [key], or `null` to compile one. With
     * [expectedSource] set, an instance compiled from different text is left where it is.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun acquire(key: String, expectedSource: String?): RuntimeShader? {
        val instances = idle[key] ?: return null
        for (index in instances.lastIndex downTo 0) {
            val candidate = instances[index]
            if (expectedSource == null ||
                candidate.source === expectedSource ||
                candidate.source == expectedSource
            ) {
                return instances.removeAt(index)
            }
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun release(key: String, shader: RuntimeShader) {
        val instances = idle.getOrPut(key) { ArrayList(2) }
        if (instances.size < MaxIdlePerProgram) instances += shader
    }
}
