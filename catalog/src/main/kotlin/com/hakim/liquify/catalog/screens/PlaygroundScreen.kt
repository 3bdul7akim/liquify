package com.hakim.liquify.catalog.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hakim.liquify.Backdrop
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.Divider
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.MaterialSurface
import com.hakim.liquify.catalog.ui.ParameterRow
import com.hakim.liquify.catalog.ui.components.LiquidToggle
import com.hakim.liquify.catalog.ui.drawGlyph
import com.hakim.liquify.effects.blur
import com.hakim.liquify.effects.gradientBlur
import com.hakim.liquify.effects.lens
import com.hakim.liquify.effects.opacity
import com.hakim.liquify.effects.tint
import com.hakim.liquify.effects.vibrancy
import com.hakim.liquify.highlight.Highlight
import com.hakim.liquify.highlight.HighlightStyle
import com.hakim.liquify.liquify
import com.hakim.liquify.shadow.Shadow
import com.kyant.shapes.RoundedRectangle

/** Every knob of the effect stack, live, on one card. */
@Composable
fun PlaygroundScreen(backdrop: Backdrop) {
    var blurRadius by remember { mutableFloatStateOf(0.25f) }
    var refractionHeight by remember { mutableFloatStateOf(0.4f) }
    var refractionAmount by remember { mutableFloatStateOf(0.45f) }
    var cornerRadius by remember { mutableFloatStateOf(0.45f) }
    var saturation by remember { mutableFloatStateOf(0.25f) }
    var tintAlpha by remember { mutableFloatStateOf(0.06f) }
    var surfaceOpacity by remember { mutableFloatStateOf(1f) }
    var depth by remember { mutableStateOf(true) }
    var stretching by remember { mutableStateOf(false) }
    var dispersion by remember { mutableStateOf(false) }
    var ambientRim by remember { mutableStateOf(false) }
    var gradient by remember { mutableStateOf(false) }

    val blurDp = blurRadius * 40f
    val heightDp = refractionHeight * 60f
    val amountDp = refractionAmount * 80f
    val cornerDp = cornerRadius * 80f
    val saturationValue = 1f + saturation * 2f

    val glyphColor = LocalCatalogColors.current.content

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 110.dp, end = 20.dp, bottom = 30.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .liquify(
                    shape = { RoundedRectangle(cornerDp.dp) },
                    effects = {
                        vibrancy(saturationValue)
                        tint(Color.White.copy(alpha = tintAlpha))
                        if (surfaceOpacity < 1f) opacity(surfaceOpacity)
                        // Same radius either way. The uniform blur goes under the lens so the
                        // refraction bends softened light; the gradient blur goes over it, or the
                        // lens would sample the soft middle back out over the rim.
                        if (!gradient) blur(blurDp.dp.toPx())
                        lens(
                            refractionHeight = heightDp.dp.toPx(),
                            refractionAmount = amountDp.dp.toPx(),
                            depthEffect = depth,
                            chromaticAberration = dispersion
                        )
                        if (gradient) gradientBlur(radius = blurDp.dp.toPx())
                    },
                    backdrop = backdrop,
                    highlight = {
                        if (ambientRim) {
                            Highlight(width = 1.dp, style = HighlightStyle.Ambient)
                        } else {
                            Highlight.Default
                        }
                    },
                    shadow = { Shadow.Default },
                    // Two booleans and the card flexes towards the finger and lights up — no
                    // interaction object to hoist.
                    dragging = true,
                    interactiveHighlight = true,
                    stretching = stretching
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf(Glyph.Circle, Glyph.Triangle, Glyph.Square, Glyph.Hexagon).forEach { glyph ->
                    Canvas(Modifier.size(34.dp)) {
                        drawGlyph(glyph, size.minDimension, glyphColor)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Plain surface, not glass: eleven glass controls stacked on a glass card would be eleven
        // backdrops showing through a twelfth.
        MaterialSurface(modifier = Modifier.fillMaxWidth()) { cardBackdrop ->
            Column(Modifier.padding(18.dp)) {
                CatalogText("Effect stack", HeadingStyle)
                Spacer(Modifier.height(8.dp))

                ParameterRow(
                    cardBackdrop,
                    "blur(radius)",
                    { blurRadius },
                    "${blurDp.toInt()} dp",
                    onValueChange = { blurRadius = it }
                )
                ParameterRow(
                    cardBackdrop,
                    "lens(refractionHeight)",
                    { refractionHeight },
                    "${heightDp.toInt()} dp",
                    onValueChange = { refractionHeight = it }
                )
                ParameterRow(
                    cardBackdrop,
                    "lens(refractionAmount)",
                    { refractionAmount },
                    "${amountDp.toInt()} dp",
                    onValueChange = { refractionAmount = it }
                )
                ParameterRow(
                    cardBackdrop,
                    "vibrancy(amount)",
                    { saturation },
                    "%.2f".format(saturationValue),
                    onValueChange = { saturation = it }
                )
                ParameterRow(
                    cardBackdrop,
                    "tint(alpha)",
                    { tintAlpha },
                    "%.2f".format(tintAlpha),
                    onValueChange = { tintAlpha = it }
                )
                ParameterRow(
                    cardBackdrop,
                    "opacity(alpha)",
                    { surfaceOpacity },
                    "%.2f".format(surfaceOpacity),
                    onValueChange = { surfaceOpacity = it }
                )
                ParameterRow(
                    cardBackdrop,
                    "Corner radius",
                    { cornerRadius },
                    "${cornerDp.toInt()} dp",
                    onValueChange = { cornerRadius = it }
                )

                Spacer(Modifier.height(10.dp))
                Divider()
                Spacer(Modifier.height(4.dp))

                ToggleRow(cardBackdrop, "gradientBlur", gradient) { gradient = !gradient }
                ToggleRow(cardBackdrop, "depthEffect", depth) { depth = !depth }
                ToggleRow(cardBackdrop, "chromaticAberration", dispersion) { dispersion = !dispersion }
                ToggleRow(cardBackdrop, "Ambient rim", ambientRim) { ambientRim = !ambientRim }
                ToggleRow(cardBackdrop, "Stretching", stretching) { stretching = !stretching }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    backdrop: Backdrop,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CatalogText(label, LabelStyle, Modifier.weight(1f))
        LiquidToggle(selected = { checked }, onSelect = onCheckedChange, backdrop = backdrop)
    }
}
