package com.hakim.liquify.catalog.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.hakim.liquify.Backdrop
import com.hakim.liquify.catalog.ui.BodyStyle
import com.hakim.liquify.catalog.ui.CardShape
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.MaterialSurface
import com.hakim.liquify.catalog.ui.drawGlyph
import com.hakim.liquify.liquify
import com.hakim.liquify.material.GlassMaterial

private val Materials = listOf(
    Triple(
        "Regular",
        GlassMaterial.Regular,
        "Barely blurred and mostly transparent — definition comes from the rim, " +
            "not from hiding what is behind it."
    ),
    Triple(
        "Clear",
        GlassMaterial.Clear,
        "No blur at all, domed so the whole pane is a lens. Dim the content behind it yourself."
    ),
    Triple(
        "Thick",
        GlassMaterial.Thick,
        "Heavily frosted and opaque, for large surfaces such as sheets and sidebars."
    )
)

/**
 * The three presets over identical backdrop content — and the app's material picker.
 *
 * Tapping a card publishes it through
 * [ProvideGlassMaterial][com.hakim.liquify.material.ProvideGlassMaterial] at the root, so every
 * other pane of glass in the app — this screen's top bar included — switches to it on the next
 * frame. Each card keeps rendering its *own* preset regardless, which is what makes the comparison
 * a comparison.
 */
@Composable
fun MaterialsScreen(
    backdrop: Backdrop,
    selected: GlassMaterial,
    onSelect: (GlassMaterial) -> Unit
) {
    val colors = LocalCatalogColors.current

    // LazyColumn, not a scrolling Column: each card is an offscreen layer plus a render-effect
    // chain, and an eager Column would compose and draw all of them whether or not they are on
    // screen.
    LazyColumn(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 84.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(Materials) { (name, preset, description) ->
            val isSelected = preset == selected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .pointerInput(preset) { detectTapGestures { onSelect(preset) } }
                    .liquify(
                        shape = CardShape,
                        material = preset,
                        backdrop = backdrop,
                        tint = colors.glassTint,
                        dragging = true,
                        interactiveHighlight = true,
                        stretching = true
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        CatalogText(name, HeadingStyle)
                        Spacer(Modifier.height(6.dp))
                        CatalogText(description, BodyStyle)
                    }
                    Canvas(Modifier.size(26.dp)) {
                        if (isSelected) {
                            drawCircle(colors.accent, radius = size.minDimension / 2f)
                            drawGlyph(Glyph.Check, size.minDimension * 0.52f, Color.White)
                        } else {
                            drawCircle(
                                colors.secondaryContent,
                                radius = size.minDimension / 2f - 1.dp.toPx(),
                                style = Stroke(1.5.dp.toPx())
                            )
                        }
                    }
                }
            }
        }

        item {
            MaterialSurface(Modifier.fillMaxWidth()) { _ ->
                CatalogText(
                    "Tap a card to make it the app's material. It is published once at the root " +
                        "with ProvideGlassMaterial, and every liquify() below reads it from " +
                        "LocalGlassMaterial — no screen has to be told twice.",
                    BodyStyle,
                    Modifier.padding(18.dp)
                )
            }
        }
    }
}
