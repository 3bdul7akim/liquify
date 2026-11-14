package com.hakim.liquify.internal

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.isSpecified

/**
 * What `liquify` draws under [GlassLook.Solid][com.hakim.liquify.GlassLook.Solid]: its tint as a
 * plain shape, then the surface hook clipped to it, then the content — the glass path's stacking
 * with the sampled backdrop taken out.
 *
 * The outline comes from the shape cache and is rebuilt only when the size changes. The tint is drawn
 * as the outline itself, so a pane without a surface hook needs no clip at all.
 */
internal fun Modifier.flatSurface(
    shapeProvider: ShapeProvider,
    tint: Color,
    onDrawSurface: (DrawScope.() -> Unit)?,
): Modifier = drawWithCache {
    val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
    val path = if (onDrawSurface != null) Path().apply { addOutline(outline) } else null
    onDrawWithContent {
        if (tint.isSpecified && tint.alpha > 0f) drawOutline(outline, tint)
        if (path != null && onDrawSurface != null) clipPath(path) { onDrawSurface() }
        drawContent()
    }
}
