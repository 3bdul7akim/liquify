package com.hakim.liquify

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * Whether glass in this subtree may read what is behind it.
 *
 * Set it to `false` and every [liquify] below turns into a plain filled shape: no layer is recorded,
 * no effect chain is built, no shader is compiled, and the backdrop is never sampled. The element
 * keeps its outline, its draw hooks, its layer transform and its gestures — only the *material*
 * goes. Decoration is left exactly where the call site put it, so a rim or a shadow that was asked
 * for is still drawn; pass `null` for those at the call site if a flat surface should be bare.
 *
 * ### Why this is a local and not a parameter
 *
 * An app that offers a low-power appearance does not want to make this decision thirteen times. The
 * alternative, and what motivated this, is every component writing
 *
 * ```
 * val skin = if (fancy) Modifier.liquify(shape, backdrop, effects, …) else Modifier.background(c, s)
 * ```
 *
 * which duplicates the shape at both branches, forgets one of them sooner or later, and has to be
 * repeated for each new component. Provided once at the root, this replaces all of them:
 *
 * ```
 * CompositionLocalProvider(LocalGlassEnabled provides (appearance != Appearance.Flat)) { App() }
 * ```
 *
 * `staticCompositionLocalOf`, because it changes when a person picks a different appearance and at
 * no other time: reads cost nothing, and the one change it does make has to reach every pane anyway.
 *
 * ### What the call site still owns
 *
 * The library cannot guess colours. Glass fills are usually translucent because the blur behind them
 * is doing half the work; with nothing behind them the same fill leaves the page showing through.
 * So a call site that supports both should still pick a denser colour when this is off — but that is
 * a colour, not a second modifier chain.
 */
public val LocalGlassEnabled: androidx.compose.runtime.ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { true }

/**
 * What [liquify] becomes when [LocalGlassEnabled] is `false`.
 *
 * The draw order mirrors the glass path exactly — behind, surface, content, front — so a call site
 * that draws into those hooks gets the same stacking either way. The only thing missing between
 * `onDrawBehind` and `onDrawSurface` is the sampled, filtered backdrop.
 *
 * The outline is built in [drawWithCache], so it is recomputed when the size changes and not once
 * per frame; `clipPath` keeps the surface inside it, which is what makes a rounded fill rounded
 * without a `clip` modifier of its own (a `clip` would also crop children, and glass deliberately
 * does not).
 */
internal fun Modifier.flatSurface(
    shapeProvider: com.hakim.liquify.internal.ShapeProvider,
    onDrawBehind: (DrawScope.() -> Unit)?,
    onDrawSurface: (DrawScope.() -> Unit)?,
    onDrawFront: (DrawScope.() -> Unit)?,
): Modifier = drawWithCache {
    val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    onDrawWithContent {
        onDrawBehind?.invoke(this)
        if (onDrawSurface != null) clipPath(path) { onDrawSurface() }
        drawContent()
        onDrawFront?.invoke(this)
    }
}
