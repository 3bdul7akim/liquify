package com.hakim.liquify.catalog.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.hakim.liquify.catalog.ui.BodyStyle
import com.hakim.liquify.catalog.ui.CapsuleShape
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.DemoCard
import com.hakim.liquify.catalog.ui.DemoNote
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.MaterialSurface
import com.hakim.liquify.catalog.ui.ScreenColumn
import com.hakim.liquify.catalog.ui.SettingRow
import com.hakim.liquify.catalog.ui.components.LiquidSearchField
import com.hakim.liquify.catalog.ui.components.LiquidSnackbarHost
import com.hakim.liquify.catalog.ui.components.LiquidToggle
import com.hakim.liquify.catalog.ui.components.LiquidTopTabs
import com.hakim.liquify.catalog.ui.components.LiveButton
import com.hakim.liquify.catalog.ui.components.MorphLevel
import com.hakim.liquify.catalog.ui.components.MorphingControls
import com.hakim.liquify.catalog.ui.components.RangeNav
import com.hakim.liquify.catalog.ui.components.rememberLiquidSnackbarState

/** The week the range picker starts on, and returns to when its label is tapped. */
private const val ThisWeek = 46

/**
 * The pieces a header is made of, each as one body of glass where it has several parts.
 *
 * - **Morphing controls** — back, search, play, shuffle and reset in one merged group that reshapes
 *   itself between three levels, every control parting from under its neighbour like liquid.
 * - **A range picker** — `‹ label ›`, two coloured bulbs fused to the label between them.
 * - **A search field** with a clear button nested in it.
 * - **A live button** whose shadow breathes through the pressed-material blend.
 */
@Composable
fun HeaderScreen() {
    var level by remember { mutableStateOf(MorphLevel.List) }
    var query by remember { mutableStateOf("") }
    var shuffleOn by remember { mutableStateOf(false) }
    var week by remember { mutableIntStateOf(ThisWeek) }
    var search by remember { mutableStateOf("") }
    var live by remember { mutableStateOf(true) }
    val snackbar = rememberLiquidSnackbarState()
    val focusManager = LocalFocusManager.current

    Box(Modifier.fillMaxSize()) {
        ScreenColumn {
            DemoNote(
                "Controls that reshape",
                "Five controls, one body of glass. Pick a level — or use the controls themselves: play " +
                    "starts a session, back goes up a level. Reset asks for a second tap.",
            )
            LiquidTopTabs(
                titles = MorphLevel.entries.map { it.name },
                selectedIndex = level.ordinal,
                onSelect = { level = changeLevel(MorphLevel.entries[it], focusManager) },
                modifier = Modifier.fillMaxWidth(),
            )
            MorphingControls(
                level = level,
                query = query,
                onQueryChange = { query = it },
                onBack = {
                    level = changeLevel(
                        if (level == MorphLevel.Session) MorphLevel.Item else MorphLevel.List,
                        focusManager,
                    )
                },
                onPlay = { level = changeLevel(MorphLevel.Session, focusManager) },
                shuffleOn = shuffleOn,
                onToggleShuffle = { shuffleOn = !shuffleOn },
                onReset = { snackbar.show("Progress reset") },
                modifier = Modifier.fillMaxWidth(),
            )

            DemoNote(
                "A range picker",
                "Two arrows and a label as one merged control, each bulb in its own colour. Drag an " +
                    "arrow and it stretches out of the body; tap the label to come back to this week.",
            )
            RangeNav(
                label = "Week $week",
                onPrevious = { week-- },
                onNext = { week++ },
                onLabelClick = { week = ThisWeek },
                modifier = Modifier.fillMaxWidth(),
            )

            DemoNote(
                "A search field",
                "One capsule of chrome glass that leans, stretches and lights up like a button. Type " +
                    "something: the clear button is a pane of glass nested in the field.",
            )
            LiquidSearchField(
                query = search,
                onQueryChange = { search = it },
                placeholder = "Search anything",
                modifier = Modifier.fillMaxWidth(),
            )

            DemoCard(
                "A live button",
                "Its shadow breathes in the accent while something is live — a pressedMaterial blended " +
                    "by a pulse instead of a finger, so a breath costs one redraw and no recomposition.",
            ) {
                SettingRow("Live") { LiquidToggle({ live }, { live = it }) }
            }
            // On the wallpaper, so the glow has something to fall on.
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveButton(live = live, onClick = { snackbar.show(if (live) "Jumping to now" else "Nothing on now") })
                Spacer(Modifier.width(14.dp))
                MaterialSurface(shape = CapsuleShape) {
                    CatalogText(
                        if (live) "Live now — breathing" else "Nothing on — standing still",
                        LabelStyle,
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            CatalogText(
                "In the Frosted and Solid looks the groups do not merge: the same morph runs with " +
                    "separate panes, stacked in the same order.",
                BodyStyle,
                Modifier.padding(horizontal = 4.dp),
            )
        }

        LiquidSnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(20.dp),
        )
    }
}

/** Moves to [level], and lets go of the keyboard: a level's search field has no business keeping it. */
private fun changeLevel(level: MorphLevel, focusManager: FocusManager): MorphLevel {
    focusManager.clearFocus()
    return level
}
