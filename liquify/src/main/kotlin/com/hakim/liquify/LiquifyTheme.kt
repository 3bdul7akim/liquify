package com.hakim.liquify

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How much the glass in a subtree is allowed to do — the one switch an app turns to trade looks for
 * GPU time. Every `liquify` and every [LiquidGlassGroup] below adapts on its own; no call site
 * branches.
 */
public enum class GlassLook {

    /** Everything: refraction, merging, the touch reactions and press transitions. */
    Liquid,

    /**
     * Blurred glass without refraction: the material's blur, saturation, tint, rim and shadows, but
     * no lens, no merging, and nothing that reacts to the finger. Elements still move — that is
     * layout and layer transforms, not glass — and a pressed material still follows a `pressProgress`
     * that is passed in.
     */
    Frosted,

    /**
     * Nothing is sampled. A pane is its tint, drawn as a plain shape, with its rim and shadows around
     * it: no layer is recorded, no effect chain is built and no shader is compiled. Pick a dense
     * `tint` for it — with nothing behind the pane, a translucent one lets the page show through.
     */
    Solid,
}

internal val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

internal val LocalGlassMaterial = compositionLocalOf { GlassMaterial.Regular }

// Static: it changes when someone picks a different look and at no other time, and that one change
// has to reach every pane anyway.
internal val LocalGlassLook = staticCompositionLocalOf { GlassLook.Liquid }

internal val LocalGlassDarkTheme = compositionLocalOf<Boolean?> { null }

/**
 * Declares the glass of everything inside [content], once — the way `MaterialTheme` declares colours.
 *
 * ```
 * val backdrop = rememberLayerBackdrop()
 *
 * Box {
 *     Wallpaper(Modifier.fillMaxSize().layerBackdrop(backdrop))
 *
 *     LiquifyTheme(backdrop = backdrop, material = GlassMaterial.Regular) {
 *         Row(Modifier.liquify(Capsule())) { … }   // inherits all of it
 *     }
 * }
 * ```
 *
 * Every argument defaults to what is already in scope, so a nested call only overrides what it names:
 * `LiquifyTheme(look = GlassLook.Solid) { … }` turns the glass of one subtree flat and keeps the rest.
 * Elements resolve in the order element → [LiquidGlassGroup] → this.
 *
 * @param backdrop what glass refracts when it is not given a backdrop of its own.
 * @param material the glass recipe every element is made of unless it names one.
 * @param look how much the glass may do; see [GlassLook].
 * @param darkTheme what theme-dependent colours resolve against — the ink of unspecified shadows and
 *   outline rims. Follows the system by default; pass the app's own setting when it has one.
 */
@Composable
public fun LiquifyTheme(
    backdrop: Backdrop? = LiquifyTheme.backdrop,
    material: GlassMaterial = LiquifyTheme.material,
    look: GlassLook = LiquifyTheme.look,
    darkTheme: Boolean = LiquifyTheme.darkTheme,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalBackdrop provides backdrop,
        LocalGlassMaterial provides material,
        LocalGlassLook provides look,
        LocalGlassDarkTheme provides darkTheme,
        content = content,
    )
}

/** What [LiquifyTheme] declared for the current position. */
public object LiquifyTheme {

    /** The inherited backdrop, or `null` where none was declared. */
    public val backdrop: Backdrop?
        @Composable @ReadOnlyComposable get() = LocalBackdrop.current

    /** The inherited material — inside a [LiquidGlassGroup], the group's. */
    public val material: GlassMaterial
        @Composable @ReadOnlyComposable get() = LocalGlassMaterial.current

    public val look: GlassLook
        @Composable @ReadOnlyComposable get() = LocalGlassLook.current

    public val darkTheme: Boolean
        @Composable @ReadOnlyComposable get() = LocalGlassDarkTheme.current ?: isSystemInDarkTheme()
}
