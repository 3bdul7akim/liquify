package com.hakim.liquify.catalog.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hakim.liquify.catalog.ui.BodyStyle
import com.hakim.liquify.catalog.ui.CapsuleShape
import com.hakim.liquify.catalog.ui.CatalogOverlay
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.DemoNote
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.MaterialSurface
import com.hakim.liquify.catalog.ui.ParameterRow
import com.hakim.liquify.catalog.ui.ScreenColumn
import com.hakim.liquify.catalog.ui.SettingRow
import com.hakim.liquify.catalog.ui.components.DropdownPlacement
import com.hakim.liquify.catalog.ui.components.GlassPanel
import com.hakim.liquify.catalog.ui.components.LiquidButton
import com.hakim.liquify.catalog.ui.components.LiquidDropdownOverlay
import com.hakim.liquify.catalog.ui.components.LiquidDropdownTrigger
import com.hakim.liquify.catalog.ui.components.LiquidSnackbarHost
import com.hakim.liquify.catalog.ui.components.LiquidToggle
import com.hakim.liquify.catalog.ui.components.LiquidTopTabs
import com.hakim.liquify.catalog.ui.components.liquidDropdownAnchor
import com.hakim.liquify.catalog.ui.components.rememberLiquidDropdownState
import com.hakim.liquify.catalog.ui.components.rememberLiquidSnackbarState

private val SortOptions = listOf("Name", "Date", "Size", "Kind")
private val Actions = listOf("Duplicate", "Move", "Share", "Delete")

/**
 * Glass laid over the page: a panel, two menus and a snackbar.
 *
 * The panel and the menus are handed up to the shell ([CatalogOverlay]), which draws them above
 * everything — the top bar included — refracting the whole screen beneath them. Their state stays
 * here, with the triggers.
 */
@Composable
fun PanelsScreen() {
    val colors = LocalCatalogColors.current
    var panelOpen by remember { mutableStateOf(false) }
    var sort by remember { mutableIntStateOf(0) }
    var notify by remember { mutableStateOf(true) }
    var volume by remember { mutableFloatStateOf(0.6f) }
    var size by remember { mutableIntStateOf(1) }
    val sortMenu = rememberLiquidDropdownState()
    val actionMenu = rememberLiquidDropdownState(placement = DropdownPlacement.BelowEnd, startSize = 52.dp, hidesAnchor = false)
    val snackbar = rememberLiquidSnackbarState()

    Box(Modifier.fillMaxSize()) {
        ScreenColumn {
            DemoNote(
                "A panel",
                "A plain pane over the page — its fill at nine tenths and the material's rim, nothing " +
                    "sampled — so it costs nothing to animate in. The toggle, the slider and the tabs " +
                    "in it are glass standing on the panel's colour. Back or a tap beside it closes it.",
            )
            LiquidButton(onClick = { panelOpen = true }, modifier = Modifier.fillMaxWidth(), height = 52.dp) {
                GlyphIcon(Glyph.Square, size = 20.dp)
                CatalogText("Open the panel", LabelStyle)
            }

            DemoNote(
                "A menu out of its button",
                "The trigger becomes a drop of glass that flies to where the menu will be — stretching " +
                    "along its travel, calmed and smeared by its own speed — and spreads into the menu " +
                    "before it has quite landed.",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MaterialSurface(Modifier.weight(1f), shape = CapsuleShape) {
                    CatalogText(
                        "Sort by ${SortOptions[sort]}",
                        LabelStyle,
                        Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                LiquidDropdownTrigger(sortMenu)
            }

            DemoNote(
                "A drop out of a button that stays",
                "The same flight from a button that keeps its place: a drop lifts out of it, fades in " +
                    "as it leaves and lands as an action menu.",
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LiquidButton(
                    onClick = { actionMenu.toggle() },
                    modifier = Modifier.size(52.dp).liquidDropdownAnchor(actionMenu),
                    shape = CircleShape,
                    tint = colors.accent,
                    stretching = true,
                    height = 52.dp,
                    contentPadding = 0.dp,
                ) {
                    GlyphIcon(Glyph.Plus, color = Color.White)
                }
            }

            DemoNote(
                "A snackbar",
                "A pill of floating chrome that grows out of its own corner and keeps its text through " +
                    "the exit, so it never blanks out mid-animation.",
            )
            LiquidButton(
                onClick = { snackbar.show("Saved — just now") },
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp,
            ) {
                CatalogText("Show a snackbar", LabelStyle)
            }
            // Room for the snackbar under the last button.
            Spacer(Modifier.height(60.dp))
        }

        LiquidSnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(20.dp),
        )
    }

    CatalogOverlay {
        LiquidDropdownOverlay(
            state = sortMenu,
            options = SortOptions,
            selectedIndex = sort,
            onSelect = { sort = it },
        )
        LiquidDropdownOverlay(
            state = actionMenu,
            options = Actions,
            selectedIndex = -1,
            onSelect = { snackbar.show("${Actions[it]} — done") },
        )
        if (panelOpen) {
            GlassPanel(onDismiss = { panelOpen = false }) { dismiss ->
                CatalogText("Settings", HeadingStyle)
                Spacer(Modifier.height(6.dp))
                CatalogText(
                    "Every control in here is glass over the panel's colour — handed nothing, it " +
                        "inherits a backdrop of that one colour, which Liquify draws without a layer.",
                    BodyStyle,
                )
                Spacer(Modifier.height(12.dp))
                SettingRow("Notifications") { LiquidToggle({ notify }, { notify = it }) }
                ParameterRow(
                    label = "Volume",
                    value = { volume },
                    valueLabel = "${(volume * 100).toInt()} %",
                    onValueChange = { volume = it },
                    defaultValue = 0.6f,
                )
                Spacer(Modifier.height(8.dp))
                LiquidTopTabs(
                    titles = listOf("Small", "Medium", "Large"),
                    selectedIndex = size,
                    onSelect = { size = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiquidButton(onClick = dismiss, modifier = Modifier.weight(1f)) {
                        CatalogText("Cancel", LabelStyle)
                    }
                    LiquidButton(
                        onClick = {
                            snackbar.show("Settings saved")
                            dismiss()
                        },
                        modifier = Modifier.weight(1f),
                        tint = colors.accent,
                    ) {
                        CatalogText("Save", LabelStyle.copy(color = Color.White))
                    }
                }
            }
        }
    }
}
