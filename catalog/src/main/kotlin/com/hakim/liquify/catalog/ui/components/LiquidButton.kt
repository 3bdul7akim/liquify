package com.hakim.liquify.catalog.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hakim.liquify.Backdrop
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.interaction.rememberInteractiveHighlight
import com.hakim.liquify.liquify
import com.hakim.liquify.material.LocalGlassMaterial
import com.kyant.shapes.Capsule

/**
 * A glass button that flexes towards the finger and lights up under it.
 *
 * It uses the short form of `liquify`, which is what lets it **merge with its neighbours inside a
 * [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup]** — there it drops its own material
 * and rim in favour of the group's, and fuses. Standalone it is an ordinary pane of glass made of
 * whatever [LocalGlassMaterial] currently holds.
 *
 * Note the group lays its children out as a `Box`, so wrap them in a `Row` or `Column` to place
 * them apart:
 *
 * ```
 * LiquidGlassGroup(backdrop) {
 *     Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
 *         LiquidButton(onClick = { … }, backdrop = backdrop, …) { … }
 *         LiquidButton(onClick = { … }, backdrop = backdrop, …) { … }
 *     }
 * }
 * ```
 *
 * Setting [isInteractive] to `false` drops the touch reactions and falls back to the platform
 * ripple, which is the right choice for a button inside an already-animating container.
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    stretching: Boolean = false,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    shape: Shape = Capsule(),
    height: Dp = 48f.dp,
    contentPadding: Dp = 16f.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interactiveHighlight = rememberInteractiveHighlight()
    val glassTint = LocalCatalogColors.current.glassTint

    Row(
        modifier
            .liquify(
                shape = shape,
                backdrop = backdrop,
                tint = glassTint,
                stretching = stretching,
                onDrawSurface = {
                    if (tint.isSpecified) {
                        // Hue first, then a partial fill: tinting the backdrop rather than
                        // covering it keeps the glass reading as coloured glass, not as paint.
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                },
                interaction = if (isInteractive) interactiveHighlight else null
            )
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .height(height)
            .padding(horizontal = contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
