package com.hakim.liquify.catalog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hakim.liquify.LiquidGlassGroup
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.TopBarAction
import com.hakim.liquify.liquify
import com.kyant.shapes.Capsule

private val BarControlSize = 48.dp
private val BarGap = 15.dp
private val BarPadding = 8.dp

/** The bar's height below the status bar. */
val TopBarHeight: Dp = BarControlSize + BarPadding * 2

/**
 * How strongly the bar's members fuse. Two 48 dp members bridge once `merge` exceeds four times their
 * gap over 48 dp — about `1.25` at 15 dp apart — so at `0.5` they stand apart at rest and fuse once
 * one is dragged within about 6 dp of its neighbour, the bridge stretching out until it snaps.
 */
private const val TopBarMerge = 0.5f

/** How far a screen's content has to start below the top of the window to clear the bar. */
@Composable
fun topBarClearance(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight

/**
 * The header of every screen: back, title and an optional action as **one body of glass**.
 *
 * The three are members of a [LiquidGlassGroup], each in its own colour — the back button always in
 * the accent, the title clear, the action in whatever the screen gives it — and the colours blend
 * through the bridges between them. Every member leans towards the finger and stretches; drag one and
 * it pulls its colour along out of the body, the bridge thinning until it snaps.
 *
 * Draw it as a *sibling* above the screen's layer, inside a theme whose backdrop is that layer: the
 * whole bar then refracts the screen scrolling beneath it.
 */
@Composable
fun GlassTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: TopBarAction? = null,
) {
    val colors = LocalCatalogColors.current

    // The group spans the whole width, so a member dragged off its slot stays inside the body.
    LiquidGlassGroup(
        modifier
            .fillMaxWidth()
            .statusBarsPadding(), merge = TopBarMerge
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = BarPadding),
            horizontalArrangement = Arrangement.spacedBy(BarGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                LiquidButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(BarControlSize)
                        .semantics { contentDescription = "Back" },
                    shape = CircleShape,
                    tint = colors.accent,
                    stretching = true,
                    height = BarControlSize,
                    contentPadding = 0.dp,
                ) {
                    GlyphIcon(Glyph.ChevronLeft, color = Color.White)
                }
            }

            Box(
                Modifier
                    .weight(1f, fill = false)
                    .height(BarControlSize)
                    .liquify(
                        Capsule(),
                        dragging = true,
                        stretching = true,
                        interactiveHighlight = true
                    )
                    .padding(horizontal = 20.dp)
                    .semantics { heading() },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    title,
                    style = HeadingStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (action != null) {
                LiquidButton(
                    onClick = action.onClick,
                    modifier = Modifier
                        .size(BarControlSize)
                        .semantics { contentDescription = action.label },
                    shape = CircleShape,
                    tint = action.tint,
                    stretching = true,
                    height = BarControlSize,
                    contentPadding = 0.dp,
                ) {
                    GlyphIcon(action.glyph, color = Color.White, scale = 0.9f)
                }
            }
        }
    }
}
