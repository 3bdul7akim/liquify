/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify

import android.os.Build
import androidx.annotation.RequiresApi
import org.intellij.lang.annotations.Language

/**
 * Compiles each distinct AGSL program at most once and hands back the same [RuntimeShader]
 * afterwards. Shader compilation is expensive; setting uniforms is not.
 */
public sealed interface RuntimeShaderCache {

    /**
     * Returns the cached shader for [key], compiling [string] on first use.
     *
     * [key] must uniquely identify the program text — two different programs sharing a key would
     * return the wrong shader.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = HashMap<String, RuntimeShader>(4)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader =
        runtimeShaders.getOrPut(key) { RuntimeShader(string) }

    /**
     * The same, except the program text is only built on a miss.
     *
     * The eager form takes the source as an argument, so a caller that *generates* its program —
     * the merged surface builds a different one per member count — pays for that generation on
     * every call, even when the answer was compiled long ago. In a draw pass that is once per
     * frame, for several kilobytes of text that go straight in the bin.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun obtainRuntimeShader(key: String, source: () -> String): RuntimeShader =
        runtimeShaders[key] ?: RuntimeShader(source()).also { runtimeShaders[key] = it }

    fun clear() {
        runtimeShaders.clear()
    }
}
