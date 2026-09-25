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
        runtimeShaders[key]
            ?: (RuntimeShaderPool.acquire(key, string) ?: RuntimeShader(string))
                .also { runtimeShaders[key] = it }

    /**
     * The same, except the program text is only built on a miss.
     *
     * The eager form takes the source as an argument, so a caller that *generates* its program —
     * the merged surface builds a different one per member count — pays for that generation on
     * every call, even when the answer was compiled long ago. In a draw pass that is once per
     * frame, for several kilobytes of text that go straight in the bin.
     *
     * Only for the library's own generated programs, whose keys encode everything the text depends
     * on: a recycled instance is taken from the pool on the key alone, without the text to check it
     * against — producing that text is exactly the cost this overload exists to avoid.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun obtainRuntimeShader(key: String, source: () -> String): RuntimeShader =
        runtimeShaders[key]
            ?: (RuntimeShaderPool.acquire(key, expectedSource = null) ?: RuntimeShader(source()))
                .also { runtimeShaders[key] = it }

    /**
     * Drops every shader from this cache, handing each one to [RuntimeShaderPool] so the next
     * element that needs the same program does not have to compile it again.
     */
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
 * Every glass element owns its shader instances, because each one carries its own uniforms. But the
 * *compile* is the same for all of them, and it is paid on the main thread in the very frame the
 * element appears: opening an editor panel attached a dozen elements at once — the panel, its
 * buttons, a toggle, a slider — and every one of them parsed the refraction program and the rim
 * program from scratch, which is exactly the frame a person is watching the panel arrive in. A
 * header swapped on a tab change did the same for its mask, on every change.
 *
 * So a detaching element hands its instances back here instead of dropping them, and an attaching
 * one takes an idle instance before it compiles. Reuse is safe because nothing keeps pointing at a
 * shader it no longer owns: a render effect and a recorded paint both capture an immutable snapshot
 * of the uniforms when they are built, and every cache that takes a recycled instance starts with
 * its uniform fingerprints cleared, so all of them are written again before the first draw.
 *
 * Main thread only, like every draw and attach path that reaches it.
 */
internal object RuntimeShaderPool {

    /**
     * Idle instances kept per program. A screen rarely drops more than a handful of elements with
     * the same program at once, and anything beyond this would only be memory held for a burst that
     * is not coming back.
     */
    private const val MaxIdlePerProgram = 8

    private val idle = HashMap<String, ArrayList<RuntimeShader>>(16)

    /**
     * An idle instance of the program registered under [key], or `null` to compile one.
     *
     * With [expectedSource] set, an instance compiled from different text is left where it is: a
     * caller-chosen key is only a promise, and this is the one place a broken promise would reach
     * across elements.
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
