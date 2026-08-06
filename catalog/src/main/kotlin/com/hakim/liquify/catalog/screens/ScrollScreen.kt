package com.hakim.liquify.catalog.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hakim.liquify.Backdrop
import com.hakim.liquify.backdrops.layerBackdrop
import com.hakim.liquify.backdrops.rememberCombinedBackdrop
import com.hakim.liquify.backdrops.rememberLayerBackdrop
import com.hakim.liquify.catalog.ui.BodyStyle
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.components.LiquidBottomTab
import com.hakim.liquify.catalog.ui.components.LiquidBottomTabs
import com.hakim.liquify.catalog.ui.drawGlyph
import com.hakim.liquify.liquify
import com.kyant.shapes.RoundedRectangle

private val TabGlyphs = listOf(Glyph.Search, Glyph.Heart, Glyph.List, Glyph.Plus)

/**
 * Glass over content that actually moves.
 *
 * The list is captured into its own [rememberLayerBackdrop] and combined with the wallpaper, so
 * the floating bar refracts the rows sliding underneath it rather than a static image. This is the
 * case where a backdrop being re-recorded every frame earns its cost.
 */
@Composable
fun ScrollScreen(wallpaperBackdrop: Backdrop) {
    val contentBackdrop = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(wallpaperBackdrop, contentBackdrop)
    var selectedTab by remember { mutableIntStateOf(0) }
    val glyphColor = LocalCatalogColors.current.content

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop),
            contentPadding = PaddingValues(start = 20.dp, top = 110.dp, end = 20.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(24) { index ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .liquify(RoundedRectangle(25.dp), backdrop = wallpaperBackdrop, interactiveHighlight = true)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Canvas(Modifier.size(34.dp)) {
                        drawGlyph(
                            Glyph.entries[index % Glyph.entries.size],
                            size.minDimension,
                            glyphColor
                        )
                    }
                    Column {
                        CatalogText("Row ${index + 1}", HeadingStyle)
                        Spacer(Modifier.height(4.dp))
                        CatalogText("Scroll me under the glass", BodyStyle)
                    }
                }
            }
        }

        // Floating tab bar: the indicator is a lens, so the icons distort and change colour
        // *through* it, and it can be dragged along the bar as well as tapped.
        LiquidBottomTabs(
            selectedTabIndex = { selectedTab },
            onTabSelected = { selectedTab = it },
            backdrop = combined,
            tabsCount = TabGlyphs.size,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            TabGlyphs.forEachIndexed { index, glyph ->
                LiquidBottomTab(onClick = { selectedTab = index }) {
                    Canvas(Modifier.size(26.dp)) {
                        drawGlyph(glyph, size.minDimension, glyphColor)
                    }
                }
            }
        }
    }
}
