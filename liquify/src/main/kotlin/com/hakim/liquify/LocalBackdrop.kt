package com.hakim.liquify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * The backdrop every [liquify] call in this subtree uses when it is not given one explicitly.
 *
 * A screen normally has exactly one thing behind the glass, and repeating it at every call site is
 * noise. Set it once with [ProvideBackdrop] — or let a
 * [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup] set it for you — and the rest of the
 * tree just works.
 *
 * `null` means nothing has been provided; [liquify] then requires an explicit `backdrop` argument
 * and fails loudly rather than silently rendering an empty pane.
 */
public val LocalBackdrop: ProvidableCompositionLocal<Backdrop?> = compositionLocalOf { null }

/**
 * Makes [backdrop] the default for every [liquify] call inside [content].
 *
 * ```
 * val backdrop = rememberLayerBackdrop()
 *
 * Box {
 *     Image(wallpaper, null, Modifier.fillMaxSize().layerBackdrop(backdrop))
 *
 *     ProvideBackdrop(backdrop) {
 *         Row(Modifier.liquify(Capsule())) { … }   // no backdrop argument needed
 *     }
 * }
 * ```
 *
 * Passing `backdrop` explicitly to a single [liquify] still wins, so one element can refract
 * something different without disturbing the rest.
 */
@Composable
public fun ProvideBackdrop(backdrop: Backdrop, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBackdrop provides backdrop, content = content)
}
