package com.hakim.liquify.catalog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** A button a screen adds to the end of the top bar, in its own colour. */
@Immutable
class TopBarAction(val glyph: Glyph, val label: String, val tint: Color, val onClick: () -> Unit)

/**
 * What a screen hands up to the app shell: overlays to draw above everything, and an action for the
 * top bar.
 *
 * The shell draws the top bar *over* the screen, as a sibling of the screen's layer, so the bar can
 * refract the screen scrolling beneath it. A panel or a menu opened inside the screen would end up
 * under that bar — so screens hand them up here, and the shell draws them above the bar, refracting
 * the same screen layer the bar does.
 */
@Stable
class CatalogChrome {
    internal val overlays = mutableStateListOf<@Composable () -> Unit>()
    var action by mutableStateOf<TopBarAction?>(null)
        internal set
}

val LocalCatalogChrome = staticCompositionLocalOf<CatalogChrome> { error("No CatalogChrome provided") }

/** Draws [content] above everything — the top bar included — for as long as this is composed. */
@Composable
fun CatalogOverlay(content: @Composable () -> Unit) {
    val chrome = LocalCatalogChrome.current
    val current by rememberUpdatedState(content)
    DisposableEffect(chrome) {
        val slot: @Composable () -> Unit = { current() }
        chrome.overlays += slot
        onDispose { chrome.overlays -= slot }
    }
}

/** Puts a button at the end of the top bar for as long as this is composed. */
@Composable
fun CatalogTopBarAction(glyph: Glyph, label: String, tint: Color, onClick: () -> Unit) {
    val chrome = LocalCatalogChrome.current
    val currentOnClick by rememberUpdatedState(onClick)
    DisposableEffect(chrome, glyph, label, tint) {
        val action = TopBarAction(glyph, label, tint) { currentOnClick() }
        chrome.action = action
        onDispose { if (chrome.action === action) chrome.action = null }
    }
}
