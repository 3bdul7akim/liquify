package com.hakim.liquify.catalog.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hakim.liquify.LiquidGlassGroup
import com.hakim.liquify.catalog.ui.BodyStyle
import com.hakim.liquify.catalog.ui.CapsuleShape
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.DemoCard
import com.hakim.liquify.catalog.ui.Divider
import com.hakim.liquify.catalog.ui.GlassSurface
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.MaterialSurface
import com.hakim.liquify.catalog.ui.ParameterRow
import com.hakim.liquify.catalog.ui.ScreenColumn
import com.hakim.liquify.catalog.ui.SettingRow
import com.hakim.liquify.catalog.ui.components.LiquidButton
import com.hakim.liquify.catalog.ui.components.LiquidToggle
import com.hakim.liquify.catalog.ui.components.LiquidTopTabs
import com.kyant.shapes.RoundedRectangle

private val RowGlyphs = listOf(Glyph.Heart, Glyph.Share, Glyph.Star, Glyph.Search)

/**
 * Controls that flex and light up under the finger. Press and hold, then drag around inside a
 * button — the hotspot follows with a little lag, in the colour of the glass it lights.
 *
 * Tapping still works while all that is going on: nothing is consumed until the finger has passed
 * the platform touch slop, so a press that never really moves reaches `clickable` intact.
 */
@Composable
fun ControlsScreen() {
    var toggleA by remember { mutableStateOf(true) }
    var toggleB by remember { mutableStateOf(false) }
    var slider by remember { mutableFloatStateOf(0.4f) }
    var taps by remember { mutableIntStateOf(0) }
    var segment by remember { mutableIntStateOf(1) }

    val colors = LocalCatalogColors.current

    ScreenColumn {
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                CatalogText("Press and drag", HeadingStyle)
                Spacer(Modifier.height(6.dp))
                CatalogText(
                    "Hold a control and move your finger without lifting it. The glass leans and " +
                        "stretches after it, the light trails the touch on a spring, and it lights the " +
                        "controls next to it.",
                    BodyStyle
                )
            }
        }

        // The group is a Box, so the members need a Row to be placed apart — stacked children would
        // sit on top of each other and read as a single button.
        LiquidGlassGroup(modifier = Modifier.fillMaxWidth(), merge = 0.5f) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                for (glyph in RowGlyphs) {
                    LiquidButton(
                        onClick = { taps++ },
                        modifier = Modifier.size(64.dp),
                        contentPadding = 0.dp,
                        height = 64.dp,
                        stretching = true
                    ) {
                        GlyphIcon(glyph, size = 26.dp)
                    }
                }
            }
        }

        DemoCard(
            "Buttons",
            "Plain glass, coloured glass — the tint plus a hue shift of what shows through — and a " +
                "disabled one that keeps still. Under the Solid look a coloured button is the colour.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiquidButton(onClick = { taps++ }, modifier = Modifier.weight(1f), height = 52.dp) {
                    CatalogText("Continue", LabelStyle)
                }
                LiquidButton(
                    onClick = { taps++ },
                    modifier = Modifier.weight(1f),
                    tint = colors.accent,
                    height = 52.dp
                ) {
                    CatalogText("Accent", LabelStyle.copy(color = Color.White))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiquidButton(
                    onClick = { taps = 0 },
                    modifier = Modifier.weight(1f),
                    tint = colors.danger,
                    shape = RoundedRectangle(18.dp),
                    height = 52.dp
                ) {
                    CatalogText("Reset", LabelStyle.copy(color = Color.White))
                }
                LiquidButton(onClick = {}, modifier = Modifier.weight(1f), enabled = false, height = 52.dp) {
                    CatalogText("Disabled", LabelStyle)
                }
            }
            Spacer(Modifier.height(12.dp))
            MaterialSurface(shape = CapsuleShape) {
                CatalogText("Taps: $taps", BodyStyle, Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
            }
        }

        DemoCard(
            "Toggles and a slider",
            "The knob is an opaque pill at rest and a lens while held — the track bends through it. " +
                "Drag or tap; double-tap the slider's thumb to send it back to its default.",
        ) {
            SettingRow("Refraction") { LiquidToggle({ toggleA }, { toggleA = it }) }
            Divider()
            SettingRow("Reduce transparency") { LiquidToggle({ toggleB }, { toggleB = it }) }
            Divider()
            ParameterRow(
                label = "Intensity",
                value = { slider },
                valueLabel = "${(slider * 100).toInt()} %",
                onValueChange = { slider = it },
                defaultValue = 0.4f,
            )
        }

        DemoCard(
            "Segmented tabs",
            "For switching views within a screen: no drag, a sliding accent pill. The bar is chrome " +
                "glass, saturated harder than the panes around it.",
        ) {
            LiquidTopTabs(
                titles = listOf("Day", "Week", "Month"),
                selectedIndex = segment,
                onSelect = { segment = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
