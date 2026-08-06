package com.hakim.liquify.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.hakim.liquify.BackdropEffectScope

/**
 * The material every [liquify][com.hakim.liquify.liquify] call in this subtree is made of unless it
 * names one itself.
 *
 * An app normally wants one glass recipe throughout and the ability to change it in one place —
 * a settings toggle between a clear and a frosted look, say. Provide it once with
 * [ProvideGlassMaterial] and every pane below follows, live, without any of them being rewritten.
 *
 * Defaults to [GlassMaterial.Regular].
 */
public val LocalGlassMaterial: ProvidableCompositionLocal<GlassMaterial> =
    compositionLocalOf { GlassMaterial.Regular }

/**
 * Makes [material] the glass every [liquify][com.hakim.liquify.liquify] call inside [content] uses
 * by default.
 *
 * ```
 * var material by remember { mutableStateOf(GlassMaterial.Regular) }
 *
 * ProvideGlassMaterial(material) {
 *     App(onMaterialChange = { material = it })   // every pane re-renders in the new glass
 * }
 * ```
 *
 * Passing `material` to a single `liquify` still wins, so one element can stay a different glass
 * without disturbing the rest.
 */
@Composable
public fun ProvideGlassMaterial(material: GlassMaterial, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassMaterial provides material, content = content)
}

/**
 * The effect stack of [material], as a stable lambda ready to hand to
 * [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup] or to `liquify`'s `effects`
 * parameter.
 *
 * Remembering it matters: a freshly allocated lambda on every recomposition would make the group
 * rebuild its inherited defaults each frame, so effect blocks are normally hoisted to a top-level
 * `val`. That is not possible once the material comes from a composition local — this does the same
 * job while still tracking the current one.
 *
 * ```
 * LiquidGlassGroup(backdrop, effects = rememberGlassEffects(GlassMaterial.Clear)) { … }
 * ```
 */
@Composable
public fun rememberGlassEffects(
    material: GlassMaterial = LocalGlassMaterial.current
): BackdropEffectScope.() -> Unit =
    remember(material) {
        val resolved = material
        { material(resolved) }
    }
