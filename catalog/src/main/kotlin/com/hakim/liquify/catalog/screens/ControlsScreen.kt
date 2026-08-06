package com.hakim.liquify.catalog.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.hakim.liquify.Backdrop
import com.hakim.liquify.catalog.ui.BodyStyle
import com.hakim.liquify.catalog.ui.CapsuleShape
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.Divider
import com.hakim.liquify.catalog.ui.GlassSurface
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.MaterialSurface
import com.hakim.liquify.catalog.ui.catalogGlassEffects
import com.hakim.liquify.catalog.ui.components.LiquidButton
import com.hakim.liquify.catalog.ui.components.LiquidSlider
import com.hakim.liquify.catalog.ui.components.LiquidToggle
import com.hakim.liquify.catalog.ui.drawGlyph
import com.hakim.liquify.group.LiquidGlassGroup
import com.kyant.shapes.RoundedRectangle

/**
 * Every control here uses the same two pieces: a pointer-driven rim
 * ([com.hakim.liquify.highlight.HighlightStyle.Dynamic]) and a pointer-driven interior glow
 * ([com.hakim.liquify.interaction.InteractiveHighlight]). Press and hold, then drag around inside
 * a button — the hotspot follows with a little lag, and the rim brightens on the side you are on.
 *
 * Tapping still works while all that is going on: nothing is consumed until the finger has passed
 * the platform touch slop, so a press that never really moves reaches `clickable` intact.
 */
@Composable
fun ControlsScreen(backdrop: Backdrop) {
    var toggleA by remember { mutableStateOf(true) }
    var toggleB by remember { mutableStateOf(false) }
    var slider by remember { mutableFloatStateOf(0.4f) }
    var taps by remember { mutableIntStateOf(0) }

    val glyphColor = LocalCatalogColors.current.content
    val groupEffects = catalogGlassEffects()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(84.dp))

        GlassSurface(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                CatalogText("Press and drag", HeadingStyle)
                Spacer(Modifier.height(6.dp))
                CatalogText(
                    "Hold a control and move your finger without lifting it. The illumination " +
                        "trails the touch on a spring, and the rim lights up on the side the " +
                        "light is coming from.",
                    BodyStyle
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // The group is a Box, so the members need a Row to be placed apart — stacked children
        // would sit on top of each other and read as a single button.
        LiquidGlassGroup(
            modifier = Modifier.fillMaxWidth(),
            backdrop = backdrop,
            effects = groupEffects,
            merge = 0.5f
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LiquidButton(
                onClick = { taps++ },
                backdrop = backdrop,
                modifier = Modifier.size(64.dp),
                contentPadding = 0.dp,
                height = 64.dp,
                stretching = true
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawGlyph(Glyph.Heart, size.minDimension * 0.42f, glyphColor)
                }
            }
            LiquidButton(
                onClick = { taps++ },
                backdrop = backdrop,
                modifier = Modifier.size(64.dp),
                contentPadding = 0.dp,
                height = 64.dp,
                stretching = true
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawGlyph(Glyph.Share, size.minDimension * 0.42f, glyphColor)
                }
            }
            LiquidButton(
                onClick = { taps++ },
                backdrop = backdrop,
                modifier = Modifier.size(64.dp),
                contentPadding = 0.dp,
                height = 64.dp,
                stretching = true
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawGlyph(Glyph.Circle, size.minDimension * 0.42f, glyphColor)
                }
            }
            LiquidButton(
                onClick = { taps++ },
                backdrop = backdrop,
                modifier = Modifier.size(64.dp),
                contentPadding = 0.dp,
                height = 64.dp,
                stretching = true
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawGlyph(Glyph.Search, size.minDimension * 0.42f, glyphColor)
                }
            }
          }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LiquidButton(
                onClick = { taps++ },
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
                height = 56.dp
            ) {
                CatalogText("Continue", LabelStyle)
            }
            LiquidButton(
                onClick = { taps = 0 },
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
                tint = Color(0xFFEF4444),
                shape = RoundedRectangle(20.dp),
                height = 56.dp
            ) {
                CatalogText("Reset", LabelStyle)
            }
        }

        Spacer(Modifier.height(10.dp))
        MaterialSurface(shape = CapsuleShape) { _ ->
            CatalogText(
                "Taps: $taps",
                BodyStyle,
                Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
        Spacer(Modifier.height(20.dp))

        // Plain surface, not glass: every control in here is already a pane of glass.
        MaterialSurface(modifier = Modifier.fillMaxWidth()) { cardBackdrop ->
            Column(Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CatalogText("Refraction", LabelStyle, Modifier.weight(1f))
                    LiquidToggle({ toggleA }, { toggleA = it }, cardBackdrop)
                }
                Divider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CatalogText("Reduce transparency", LabelStyle, Modifier.weight(1f))
                    LiquidToggle({ toggleB }, { toggleB = it }, cardBackdrop)
                }
                Divider()
                Spacer(Modifier.height(14.dp))
                CatalogText("Intensity", LabelStyle, Modifier.padding(bottom = 8.dp))
                LiquidSlider(
                    value = { slider },
                    onValueChange = { slider = it },
                    backdrop = cardBackdrop,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
