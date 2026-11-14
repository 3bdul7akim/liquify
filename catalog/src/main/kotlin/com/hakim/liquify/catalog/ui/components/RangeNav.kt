package com.hakim.liquify.catalog.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hakim.liquify.LiquidGlassGroup
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.liquify
import com.kyant.shapes.Capsule

/** How strongly the arrows and the label fuse — bridged at rest across the small gap between them. */
private const val RangeNavMerge = 0.5f

/**
 * `‹ label ›` — two arrow buttons and a label between them, one merged body of glass.
 *
 * All three are members of the same group, made of the group's glass, and each carries its colour
 * as a tint the group blends through the bridges: the row reads as one liquid control with two
 * coloured bulbs, not three panes side by side. Drag an arrow and it stretches out of the body. The
 * label lights up under the finger but stays put — the arrows are what moves.
 */
@Composable
fun RangeNav(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLabelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalCatalogColors.current.accent

    LiquidGlassGroup(merge = RangeNavMerge) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidButton(
                onClick = onPrevious,
                modifier = Modifier.size(50.dp).semantics { contentDescription = "Previous" },
                shape = CircleShape,
                tint = accent,
                touchHighlight = false,
                stretching = true,
                height = 50.dp,
                contentPadding = 0.dp,
            ) {
                GlyphIcon(Glyph.ChevronLeft, color = Color.White, size = 20.dp)
            }
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .height(50.dp)
                    .liquify(Capsule(), interactiveHighlight = true)
                    .clickable(interactionSource = null, indication = null, onClick = onLabelClick),
                contentAlignment = Alignment.Center,
            ) {
                CatalogText(label, LabelStyle)
            }
            LiquidButton(
                onClick = onNext,
                modifier = Modifier.size(50.dp).semantics { contentDescription = "Next" },
                shape = CircleShape,
                tint = accent,
                touchHighlight = false,
                stretching = true,
                height = 50.dp,
                contentPadding = 0.dp,
            ) {
                GlyphIcon(Glyph.ChevronRight, color = Color.White, size = 20.dp)
            }
        }
    }
}
