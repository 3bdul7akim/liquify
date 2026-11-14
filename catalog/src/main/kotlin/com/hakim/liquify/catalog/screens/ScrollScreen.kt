package com.hakim.liquify.catalog.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.BodyStyle
import com.hakim.liquify.catalog.ui.CatalogOverlay
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.SettingRow
import com.hakim.liquify.catalog.ui.components.DropdownPlacement
import com.hakim.liquify.catalog.ui.components.GlassPanel
import com.hakim.liquify.catalog.ui.components.LiquidBottomTab
import com.hakim.liquify.catalog.ui.components.LiquidBottomTabs
import com.hakim.liquify.catalog.ui.components.LiquidButton
import com.hakim.liquify.catalog.ui.components.LiquidDropdownOverlay
import com.hakim.liquify.catalog.ui.components.LiquidSearchField
import com.hakim.liquify.catalog.ui.components.LiquidSnackbarHost
import com.hakim.liquify.catalog.ui.components.LiquidToggle
import com.hakim.liquify.catalog.ui.components.LiquidTopTabs
import com.hakim.liquify.catalog.ui.components.PageArrivalThreshold
import com.hakim.liquify.catalog.ui.components.liquidDropdownAnchor
import com.hakim.liquify.catalog.ui.components.rememberLiquidDropdownState
import com.hakim.liquify.catalog.ui.components.rememberLiquidSnackbarState
import com.hakim.liquify.catalog.ui.components.topBarClearance
import com.hakim.liquify.layerBackdrop
import com.hakim.liquify.liquify
import com.hakim.liquify.progressiveBlur
import com.hakim.liquify.rememberCombinedBackdrop
import com.hakim.liquify.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import kotlin.math.abs

private val Pages = listOf("Inbox" to Glyph.List, "Starred" to Glyph.Star, "Shared" to Glyph.Share, "Liked" to Glyph.Heart)
private val Filters = listOf("All", "Recent", "Pinned")
private val FabActions = listOf("New row", "New page", "Share page")
private const val RowCount = 30

/** The header's blur at its strongest, at the top, and how strongly the page is tinted there. */
private val HeaderBlur = 20.dp
private const val HeaderTintStrength = 0.8f

/**
 * A small app built from the catalog's pieces, over content that actually moves.
 *
 * - Four **pages** in a pager, recorded into one layer that everything floating above refracts.
 * - A **header** dissolving into the page: Liquify's `progressiveBlur` — strongest at the top,
 *   fading out downwards — with a search field and segmented tabs on it.
 * - **Bottom tabs** that follow the swipe: the indicator rides the pager's position and takes its
 *   pressed pose while the pages move.
 * - A **FAB** a drop of glass flies out of and spreads into a menu, a **panel** behind every row and
 *   a **snackbar** for what they do.
 *
 * The rows sample only the wallpaper they inherit: they are inside the pages' layer, and glass must
 * never sample the layer it is part of.
 */
@Composable
fun ScrollScreen() {
    val pagerState = rememberPagerState { Pages.size }
    val scope = rememberCoroutineScope()
    val pagesBackdrop = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(LiquifyTheme.backdrop, pagesBackdrop)
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var openRow by remember { mutableStateOf<Int?>(null) }
    var pinned by remember { mutableStateOf(setOf(5, 10, 15)) }
    val snackbar = rememberLiquidSnackbarState()
    val fabMenu = rememberLiquidDropdownState(
        placement = DropdownPlacement.AboveEnd,
        startSize = 56.dp,
        hidesAnchor = false,
    )
    val colors = LocalCatalogColors.current

    // Search field, gap, tabs and the room the blur dissolves in.
    val headerHeight = topBarClearance() + 52.dp + 10.dp + 40.dp + 20.dp
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val rows = (1..RowCount).filter { row ->
        (query.isBlank() || "Row $row".contains(query.trim(), ignoreCase = true)) &&
            when (filter) {
                1 -> row <= 8
                2 -> row in pinned
                else -> true
            }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pagerState, Modifier.fillMaxSize().layerBackdrop(pagesBackdrop)) { page ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = headerHeight + 8.dp,
                    end = 20.dp,
                    bottom = bottomInset + 170.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rows, key = { it }) { row ->
                    DemoRow(page, row, pinned = row in pinned) { openRow = row }
                }
            }
        }

        // Siblings of the pages' layer, so they refract the pages moving beneath them.
        LiquifyTheme(backdrop = combined) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .progressiveBlur(radius = HeaderBlur, tint = colors.background.copy(alpha = HeaderTintStrength))
                    .padding(top = topBarClearance(), bottom = 20.dp)
                    .padding(horizontal = 20.dp)
            ) {
                LiquidSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search ${Pages[pagerState.targetPage].first}",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                LiquidTopTabs(Filters, filter, { filter = it }, Modifier.fillMaxWidth())
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
            ) {
                Box(Modifier.fillMaxWidth()) {
                    LiquidSnackbarHost(snackbar, Modifier.align(Alignment.BottomStart).padding(end = 72.dp))
                    LiquidButton(
                        onClick = { fabMenu.toggle() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(56.dp)
                            .liquidDropdownAnchor(fabMenu),
                        shape = CircleShape,
                        tint = colors.accent,
                        stretching = true,
                        height = 56.dp,
                        contentPadding = 0.dp,
                    ) {
                        GlyphIcon(Glyph.Plus, color = Color.White)
                    }
                }
                Spacer(Modifier.height(12.dp))
                LiquidBottomTabs(
                    selectedTabIndex = { pagerState.targetPage },
                    onTabSelected = { index ->
                        // The bar echoes every change back; only a real jump scrolls.
                        if (pagerState.targetPage != index) scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    tabsCount = Pages.size,
                    pagePosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                    pageMoving = {
                        pagerState.isScrollInProgress && abs(
                            pagerState.currentPage + pagerState.currentPageOffsetFraction - pagerState.targetPage
                        ) > PageArrivalThreshold
                    },
                ) {
                    Pages.forEachIndexed { index, (label, glyph) ->
                        LiquidBottomTab(onClick = { scope.launch { pagerState.animateScrollToPage(index) } }) {
                            GlyphIcon(glyph, size = 24.dp)
                            CatalogText(label, LabelStyle.copy(fontSize = 11.sp))
                        }
                    }
                }
            }
        }
    }

    // Above the top bar as well, refracting the whole screen.
    CatalogOverlay {
        LiquidDropdownOverlay(
            state = fabMenu,
            options = FabActions,
            selectedIndex = -1,
            onSelect = { snackbar.show("${FabActions[it]} — done") },
        )
        openRow?.let { row ->
            GlassPanel(onDismiss = { openRow = null }) { dismiss ->
                CatalogText("Row $row", HeadingStyle)
                Spacer(Modifier.height(6.dp))
                CatalogText(
                    "A plain panel, cheap to open: the controls in it are glass standing on its colour.",
                    BodyStyle
                )
                Spacer(Modifier.height(12.dp))
                SettingRow("Pinned") {
                    LiquidToggle(
                        selected = { row in pinned },
                        onSelect = { on -> pinned = if (on) pinned + row else pinned - row },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiquidButton(onClick = dismiss, modifier = Modifier.weight(1f)) {
                        CatalogText("Close", LabelStyle)
                    }
                    LiquidButton(
                        onClick = {
                            snackbar.show("Row $row shared")
                            dismiss()
                        },
                        modifier = Modifier.weight(1f),
                        tint = colors.accent,
                    ) {
                        CatalogText("Share", LabelStyle.copy(color = Color.White))
                    }
                }
            }
        }
    }
}

/** A neutral row: a glyph, a title and a line — glass that lights up under the finger. */
@Composable
private fun DemoRow(page: Int, row: Int, pinned: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            // Lights up but stays put: inside a list, a row that followed the finger would claim
            // the scroll.
            .liquify(RoundedRectangle(24.dp), interactiveHighlight = true)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.entries[(row + page) % Glyph.entries.size], size = 30.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            CatalogText("Row $row", HeadingStyle)
            Spacer(Modifier.height(2.dp))
            CatalogText("${Pages[page].first} · scroll me under the glass", BodyStyle)
        }
        if (pinned) GlyphIcon(Glyph.Star, color = LocalCatalogColors.current.accent, size = 18.dp)
    }
}
