/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.effects

import androidx.compose.ui.graphics.RenderEffect
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.RuntimeShader
import com.hakim.liquify.internal.RuntimeShaderEffect
import com.hakim.liquify.internal.chain
import com.hakim.liquify.isRenderEffectSupported
import com.hakim.liquify.isRuntimeShaderSupported
import org.intellij.lang.annotations.Language

/** Appends a raw [RenderEffect] to the chain. No-op below API 31. */
public fun BackdropEffectScope.effect(effect: RenderEffect) {
    if (!isRenderEffectSupported()) return
    renderEffect = renderEffect.chain(effect)
}

/**
 * Appends a custom AGSL program to the chain — the escape hatch for effects this library does not
 * ship.
 *
 * The program is compiled once per [key] and then only re-uniformed, so [key] must be unique per
 * program text. Declare `uniform shader <uniformShaderName>;` in the program to sample whatever
 * the previous effects produced.
 *
 * ```
 * effects = {
 *     blur(12f)
 *     runtimeShaderEffect(
 *         key = "frost",
 *         shaderString = """
 *             uniform shader content;
 *             uniform float2 size;
 *             half4 main(float2 coord) {
 *                 float2 jitter = float2(sin(coord.y * 0.3), cos(coord.x * 0.3)) * 2.0;
 *                 return content.eval(coord + jitter);
 *             }
 *         """,
 *         uniformShaderName = "content"
 *     ) {
 *         setFloatUniform("size", size.width, size.height)
 *     }
 * }
 * ```
 *
 * No-op below API 33.
 */
public fun BackdropEffectScope.runtimeShaderEffect(
    key: String,
    @Language("AGSL") shaderString: String,
    uniformShaderName: String = "content",
    block: RuntimeShader.() -> Unit
) {
    if (!isRuntimeShaderSupported()) return

    val shader = obtainRuntimeShader(key, shaderString).apply(block)
    renderEffect = renderEffect.chain(RuntimeShaderEffect(shader, uniformShaderName))
}
