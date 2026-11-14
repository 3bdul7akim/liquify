package com.hakim.liquify.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hakim.liquify.GlassLook
import com.hakim.liquify.GlassMaterial
import com.hakim.liquify.GlassMotion
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.screens.BackdropsScreen
import com.hakim.liquify.catalog.screens.ControlsScreen
import com.hakim.liquify.catalog.screens.HeaderScreen
import com.hakim.liquify.catalog.screens.LooksScreen
import com.hakim.liquify.catalog.screens.MaterialsScreen
import com.hakim.liquify.catalog.screens.MenuScreen
import com.hakim.liquify.catalog.screens.MergeScreen
import com.hakim.liquify.catalog.screens.MorphScreen
import com.hakim.liquify.catalog.screens.PanelsScreen
import com.hakim.liquify.catalog.screens.PlaygroundScreen
import com.hakim.liquify.catalog.screens.RimsScreen
import com.hakim.liquify.catalog.screens.ScrollScreen
import com.hakim.liquify.catalog.ui.BodyStyle
import com.hakim.liquify.catalog.ui.CatalogChrome
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.CatalogTheme
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogChrome
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.MaterialSurface
import com.hakim.liquify.catalog.ui.ThemeMode
import com.hakim.liquify.catalog.ui.TitleStyle
import com.hakim.liquify.catalog.ui.Wallpaper
import com.hakim.liquify.catalog.ui.components.GlassTopBar
import com.hakim.liquify.catalog.ui.isDark
import com.hakim.liquify.layerBackdrop
import com.hakim.liquify.liquify
import com.hakim.liquify.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle

enum class Destination(val section: String, val title: String, val summary: String, val glyph: Glyph) {
    Merge(
        "Effects", "Merge & gooey",
        "Panes of glass fusing into one body and pinching apart, their colours mixing in the bridge",
        Glyph.Circle
    ),
    Morph(
        "Effects", "Morphing",
        "One capsule splitting into separate controls, bridged while it travels",
        Glyph.Shuffle
    ),
    Menu(
        "Effects", "Menu emergence",
        "A menu growing out of the button that opened it, still attached",
        Glyph.List
    ),
    Controls(
        "Components", "Interactive controls",
        "Buttons, toggles, sliders and segmented tabs that flex and light up under the finger",
        Glyph.Play
    ),
    Header(
        "Components", "Header & navigation",
        "Controls that reshape into each other, a merged range picker, a search field, a live button",
        Glyph.ChevronLeft
    ),
    Panels(
        "Components", "Panels & menus",
        "A plain panel with glass controls, a drop that flies out into a menu, a snackbar",
        Glyph.Square
    ),
    Scroll(
        "Components", "Scroll & bars",
        "A small app: pages under a progressive blur, tabs that follow the swipe, a menu from the FAB",
        Glyph.Search
    ),
    Materials(
        "Glass", "Materials",
        "Regular, Clear, Thick and TwSe — pick one and the whole app changes",
        Glyph.Hexagon
    ),
    Rims(
        "Glass", "Rims & shadows",
        "Five rim styles with their light, width and blur; drop shadows and inner shadows",
        Glyph.Star
    ),
    Backdrops(
        "Glass", "Backdrops & content",
        "Layer, canvas, colour and combined backdrops, refracted content, effects of your own",
        Glyph.Triangle
    ),
    Looks(
        "Glass", "Looks & motion",
        "Liquid, Frosted or Solid, calm or bouncy, light or dark — for the whole app at once",
        Glyph.Heart
    ),
    Playground(
        "Glass", "Playground",
        "Every parameter of the glass on a slider",
        Glyph.Reset
    ),
}

/**
 * The catalog's shell.
 *
 * Two layers make up the screen. The **wallpaper's** is what every pane inside a screen refracts —
 * glass must never sample a layer it is part of. The **screen's** holds the wallpaper and the screen
 * together, and the chrome above it refracts that: the top bar, and the panels and menus a screen
 * hands up through [CatalogChrome], so they lie over the bar as well.
 *
 * The look, the motion, the material — its dome and colour fringe too — and light or dark are
 * declared here once, in one [LiquifyTheme]; the Looks & motion and Materials screens change them for
 * the whole app.
 *
 * @param onDarkThemeChange told whenever the catalog turns light or dark, for the system bars.
 */
@Composable
fun CatalogApp(onDarkThemeChange: (Boolean) -> Unit = {}) {
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    val dark = themeMode.isDark()
    val currentOnDarkThemeChange by rememberUpdatedState(onDarkThemeChange)
    LaunchedEffect(dark) { currentOnDarkThemeChange(dark) }

    CatalogTheme(dark = dark) {
        var destination by remember { mutableStateOf<Destination?>(null) }
        var material by remember { mutableStateOf(GlassMaterial.Regular) }
        var look by remember { mutableStateOf(GlassLook.Liquid) }
        var motion by remember { mutableStateOf(GlassMotion.Default) }
        // The two lens switches, for every pane at once — the material's own defaults to start from.
        var depthEffect by remember { mutableStateOf(true) }
        var chromaticAberration by remember { mutableStateOf(false) }

        val colors = LocalCatalogColors.current
        // Each look gets the fill it needs: a wash over refracting glass, denser over a plain blur,
        // near-opaque where nothing is sampled and the tint *is* the pane.
        val tint = when (look) {
            GlassLook.Liquid -> colors.glassTint
            GlassLook.Frosted -> colors.frostedTint
            GlassLook.Solid -> colors.solidTint
        }
        val appMaterial = remember(material, tint, motion, depthEffect, chromaticAberration) {
            material.copy(
                tint = tint,
                motion = motion,
                depthEffect = depthEffect,
                chromaticAberration = chromaticAberration,
            )
        }

        val wallpaperBackdrop = rememberLayerBackdrop()
        // Full screen, so recording once and drawing the recording halves its cost.
        val screenBackdrop = rememberLayerBackdrop(drawContentFromLayer = true)
        val chrome = remember { CatalogChrome() }

        // Added first, so every panel and menu opened later takes the back gesture before it.
        BackHandler(enabled = destination != null) { destination = null }

        // Android's overscroll stretch is a render effect on the scroll container, and glass samples
        // its backdrop apart from it: at the end of a list the content would stretch while the glass
        // refracted an unstretched copy. Off for the whole app.
        CompositionLocalProvider(LocalOverscrollFactory provides null, LocalCatalogChrome provides chrome) {
            LiquifyTheme(backdrop = wallpaperBackdrop, material = appMaterial, look = look, darkTheme = dark) {
                // No background of its own: the wallpaper is an opaque photograph cropped to the
                // screen, and a colour under it was a full-screen fill on every frame for nothing.
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(screenBackdrop)) {
                        Wallpaper(Modifier.fillMaxSize().layerBackdrop(wallpaperBackdrop))

                        when (destination) {
                            null -> HomeScreen { destination = it }
                            Destination.Merge -> MergeScreen()
                            Destination.Morph -> MorphScreen()
                            Destination.Menu -> MenuScreen()
                            Destination.Controls -> ControlsScreen()
                            Destination.Header -> HeaderScreen()
                            Destination.Panels -> PanelsScreen()
                            Destination.Scroll -> ScrollScreen()
                            Destination.Materials -> MaterialsScreen(selected = material, onSelect = { material = it })
                            Destination.Rims -> RimsScreen()
                            Destination.Backdrops -> BackdropsScreen()
                            Destination.Looks -> LooksScreen(
                                look = look,
                                onLookChange = { look = it },
                                motion = motion,
                                onMotionChange = { motion = it },
                                depthEffect = depthEffect,
                                onDepthEffectChange = { depthEffect = it },
                                chromaticAberration = chromaticAberration,
                                onChromaticAberrationChange = { chromaticAberration = it },
                                themeMode = themeMode,
                                onThemeModeChange = { themeMode = it },
                            )
                            Destination.Playground -> PlaygroundScreen()
                        }
                    }

                    // Siblings of the screen's layer, so they refract the screen moving beneath them.
                    LiquifyTheme(backdrop = screenBackdrop) {
                        destination?.let { shown ->
                            GlassTopBar(
                                title = shown.title,
                                onBack = { destination = null },
                                action = chrome.action,
                            )
                        }
                        chrome.overlays.forEach { overlay -> key(overlay) { overlay() } }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onSelect: (Destination) -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = top + 20.dp, end = 20.dp, bottom = bottom + 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // A plain surface, not glass: the title would otherwise sit straight on the wallpaper and
            // its legibility would depend on whichever leaf happened to be behind it.
            MaterialSurface(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Column(Modifier.padding(20.dp)) {
                    CatalogText("Liquify 2", TitleStyle)
                    Spacer(Modifier.height(6.dp))
                    CatalogText(
                        "Liquid glass for Jetpack Compose — refraction, specular rims, merging bodies, " +
                            "pointer-driven light and three looks that trade effects for GPU time.",
                        BodyStyle
                    )
                }
            }
        }

        Destination.entries.forEachIndexed { index, entry ->
            if (index == 0 || Destination.entries[index - 1].section != entry.section) {
                item(key = "section-${entry.section}") {
                    CatalogText(entry.section, LabelStyle, Modifier.padding(start = 8.dp, top = 10.dp))
                }
            }
            item(key = entry.name) { DestinationCard(entry) { onSelect(entry) } }
        }
    }
}

@Composable
private fun DestinationCard(destination: Destination, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // Lights up under the finger but stays put: inside a list, a card that followed the
            // finger would claim the scroll.
            .liquify(RoundedRectangle(28.dp), interactiveHighlight = true)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(destination.glyph, size = 26.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            CatalogText(destination.title, HeadingStyle)
            Spacer(Modifier.height(4.dp))
            CatalogText(destination.summary, BodyStyle)
        }
        Spacer(Modifier.width(8.dp))
        GlyphIcon(Glyph.ChevronRight, color = LocalCatalogColors.current.secondaryContent, size = 18.dp)
    }
}
