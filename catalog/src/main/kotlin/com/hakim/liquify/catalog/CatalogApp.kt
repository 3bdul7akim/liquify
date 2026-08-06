package com.hakim.liquify.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.hakim.liquify.Backdrop
import com.hakim.liquify.ProvideBackdrop
import com.hakim.liquify.backdrops.layerBackdrop
import com.hakim.liquify.backdrops.rememberLayerBackdrop
import com.hakim.liquify.catalog.screens.ControlsScreen
import com.hakim.liquify.catalog.screens.MaterialsScreen
import com.hakim.liquify.catalog.screens.MenuScreen
import com.hakim.liquify.catalog.screens.MergeScreen
import com.hakim.liquify.catalog.screens.MorphScreen
import com.hakim.liquify.catalog.screens.PlaygroundScreen
import com.hakim.liquify.catalog.screens.ScrollScreen
import com.hakim.liquify.catalog.ui.BodyStyle
import com.hakim.liquify.catalog.ui.CapsuleShape
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.CatalogTheme
import com.hakim.liquify.catalog.ui.GlassSurface
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.MaterialSurface
import com.hakim.liquify.catalog.ui.TitleStyle
import com.hakim.liquify.catalog.ui.Wallpaper
import com.hakim.liquify.catalog.ui.components.LiquidButton
import com.hakim.liquify.catalog.ui.drawGlyph
import com.hakim.liquify.material.GlassMaterial
import com.hakim.liquify.material.ProvideGlassMaterial

enum class Destination(val title: String, val summary: String) {
    Merge(
        "Merge & Gooey",
        "Two panes of glass fusing into one body and pinching apart again"
    ),
    Morph(
        "Morphing",
        "One capsule splitting into separate controls, bridged while it travels"
    ),
    Menu(
        "Menu emergence",
        "A menu growing out of the button that opened it, still attached"
    ),
    Controls(
        "Interactive controls",
        "Buttons, toggles and sliders that flex and light up under the finger"
    ),
    Materials(
        "Materials",
        "Regular, Clear and Thick — pick one and the whole app changes"
    ),
    Playground(
        "Playground",
        "Every parameter of the effect stack on a slider"
    ),
    Scroll(
        "Scroll & bars",
        "Floating glass over content that moves underneath it"
    )
}

@Composable
fun CatalogApp() {
    CatalogTheme {
        var destination by remember { mutableStateOf<Destination?>(null) }
        // The one attribute the whole app hangs off: every pane of glass below resolves its recipe
        // from here, so changing it on the Materials screen re-renders all of them at once.
        var material by remember { mutableStateOf(GlassMaterial.Regular) }

        val backdrop = rememberLayerBackdrop()
        val colors = LocalCatalogColors.current

        // Android's overscroll stretch is itself a render effect on the scroll container, and a
        // glass element captures its backdrop separately from it. At the end of a list the content
        // therefore stretches while the glass on top keeps refracting an unstretched copy of the
        // very same rows — two distortions of one thing, disagreeing. Off for the whole app so the
        // demo shows this library's lensing rather than the platform's fighting it.
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            ProvideGlassMaterial(material) {
                Box(Modifier.fillMaxSize().background(colors.background)) {
                    Wallpaper(Modifier.fillMaxSize().layerBackdrop(backdrop))

                    // One backdrop for the whole app: every liquify() below inherits it, so no
                    // screen has to thread it through unless it wants to refract something else.
                    ProvideBackdrop(backdrop) {
                        when (destination) {
                            null -> HomeScreen(backdrop) { destination = it }

                            Destination.Merge -> MergeScreen(backdrop)
                            Destination.Morph -> MorphScreen(backdrop)
                            Destination.Menu -> MenuScreen(backdrop)
                            Destination.Controls -> ControlsScreen(backdrop)
                            Destination.Materials -> MaterialsScreen(
                                backdrop = backdrop,
                                selected = material,
                                onSelect = { material = it }
                            )
                            Destination.Playground -> PlaygroundScreen(backdrop)
                            Destination.Scroll -> ScrollScreen(backdrop)
                        }
                    }

                    if (destination != null) {
                        TopBar(
                            backdrop = backdrop,
                            title = destination?.title.orEmpty(),
                            onBack = { destination = null },
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    }
                }
            }
        }

        BackHandler(enabled = destination != null) { destination = null }
    }
}

@Composable
private fun TopBar(
    backdrop: Backdrop,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glyphColor = LocalCatalogColors.current.content
    Row(
        modifier = modifier
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LiquidButton(
            onClick = onBack,
            backdrop = backdrop,
            modifier = Modifier.size(44.dp),
            height = 44.dp,
            contentPadding = 0.dp
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawGlyph(Glyph.ChevronLeft, size.minDimension * 0.6f, glyphColor)
            }
        }
        GlassSurface(
            backdrop = backdrop,
            interactiveHighlight = true,
            dragging = true,
            shape = CapsuleShape
        ) {
            CatalogText(
                title,
                HeadingStyle,
                Modifier.padding(horizontal = 20.dp, vertical = 11.dp)
            )
        }
    }
}

@Composable
private fun HomeScreen(
    backdrop: Backdrop,
    onSelect: (Destination) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 30.dp, end = 20.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // A plain surface, not glass: the title would otherwise sit straight on the wallpaper
            // and its legibility would depend on whichever leaf happened to be behind it.
            MaterialSurface(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp)) { _ ->
                Column(Modifier.padding(20.dp)) {
                    CatalogText("Liquify", TitleStyle)
                    Spacer(Modifier.height(6.dp))
                    CatalogText(
                        "Liquid glass for Jetpack Compose — refraction, specular rims, " +
                            "pointer-driven illumination and fluid merging.",
                        BodyStyle
                    )
                }
            }
        }

        items(Destination.entries) { entry ->
            DestinationCard(backdrop, entry) { onSelect(entry) }
        }
    }
}

@Composable
private fun DestinationCard(
    backdrop: Backdrop,
    destination: Destination,
    onClick: () -> Unit
) {
    GlassSurface(
        backdrop = backdrop,
        interactiveHighlight = true,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(destination) { detectTapGestures { onClick() } }
    ) {
        Column(Modifier.padding(20.dp)) {
            CatalogText(destination.title, HeadingStyle)
            Spacer(Modifier.height(5.dp))
            CatalogText(destination.summary, BodyStyle)
        }
    }
}
