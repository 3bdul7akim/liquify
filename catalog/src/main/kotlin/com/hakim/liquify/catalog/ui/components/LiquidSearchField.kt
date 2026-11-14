package com.hakim.liquify.catalog.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.liquify
import com.kyant.shapes.Capsule

/**
 * A search field: one capsule of glass, a search glyph, and a clear button once there is something
 * to clear — a pane of glass *on* a pane of glass, the one nesting that reads well, because the
 * button is small and refracts the same page the field does.
 *
 * The field leans towards the finger, stretches and lights up like a button. It sits in a header
 * above the page, so it refracts whatever scrolls beneath it.
 *
 * @param showIcon the search glyph; leave it out where the field is squeezed between neighbours and
 *   needs every bit of its width for the query.
 * @param contentAlpha fades the field's content — not its glass — in the draw phase, for a field
 *   that slides under a neighbour.
 */
@Composable
fun LiquidSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    contentAlpha: () -> Float = { 1f },
) {
    val colors = LocalCatalogColors.current
    val textStyle = TextStyle(color = colors.content, fontSize = 16.sp)

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .height(52.dp)
            .liquify(
                shape = Capsule(),
                tint = barTint(),
                material = barMaterial(),
                dragging = true,
                stretching = true,
                interactiveHighlight = true,
            )
            // Inside the glass, so only what is drawn on it fades.
            .graphicsLayer { alpha = contentAlpha() },
        textStyle = textStyle,
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { innerTextField ->
            Row(
                Modifier.padding(start = 16.dp, end = if (query.isEmpty()) 16.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showIcon) {
                    GlyphIcon(Glyph.Search, color = colors.secondaryContent, size = 20.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        BasicText(
                            placeholder,
                            style = textStyle.copy(color = colors.secondaryContent),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    LiquidButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier
                            .size(40.dp)
                            .semantics { contentDescription = "Clear search" },
                        shape = CircleShape,
                        dragging = false,
                        height = 40.dp,
                        contentPadding = 0.dp,
                    ) {
                        GlyphIcon(Glyph.Close, size = 18.dp)
                    }
                }
            }
        },
    )
}
