package com.hakim.liquify.catalog.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hakim.liquify.GlassLook
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.liquify
import com.kyant.shapes.Capsule

/** How much of a [LiquidButton]'s colour the glass takes: enough to read as coloured, still glass. */
private const val TintStrength = 0.75f

/**
 * A glass button that flexes towards the finger and lights up under it — and the catalog's only
 * button: every other control is built from this one.
 *
 * Everything about its glass is inherited, and it adapts to where it is:
 *
 * - **Inside a [LiquidGlassGroup][com.hakim.liquify.LiquidGlassGroup]** it is a member of the merged
 *   body, made of the group's glass, and its [tint] becomes its colour there, blending through the
 *   bridges into its neighbours'. A group lays its children out as a `Box`, so wrap them in a `Row`.
 * - **Given a [tint]** it is coloured glass: three quarters of the colour over the glass, and what
 *   shows through shifted towards the same hue, so it reads as coloured glass rather than paint.
 * - **Under [GlassLook.Solid]** it is a flat pane in the full colour, with nothing behind it to tint.
 *
 * @param dragging whether the glass leans towards the finger. Leave it off for buttons inside
 *   something that scrolls: a button that follows the finger claims the drag.
 * @param touchHighlight whether it lights up under the finger, in its own colour — merged, the light
 *   flows through the bridges with it.
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = Capsule(),
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    dragging: Boolean = true,
    stretching: Boolean = false,
    touchHighlight: Boolean = true,
    height: Dp = 48.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit
) {
    val solid = LiquifyTheme.look == GlassLook.Solid
    val glassTint = when {
        !tint.isSpecified -> Color.Unspecified
        solid -> tint
        else -> tint.copy(alpha = tint.alpha * TintStrength)
    }

    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .liquify(
                shape = shape,
                tint = glassTint,
                dragging = dragging && enabled,
                stretching = stretching,
                interactiveHighlight = touchHighlight && enabled,
                // Shifts what shows through towards the tint's hue. A merged member has no surface
                // of its own and skips this; a solid pane has nothing behind it to shift.
                onDrawSurface = if (tint.isSpecified && !solid) {
                    { drawRect(tint, blendMode = BlendMode.Hue) }
                } else {
                    null
                },
            )
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .height(height)
            .padding(horizontal = contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
