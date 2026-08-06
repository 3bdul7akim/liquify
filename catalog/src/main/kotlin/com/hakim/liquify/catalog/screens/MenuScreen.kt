package com.hakim.liquify.catalog.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.hakim.liquify.Backdrop
import com.hakim.liquify.catalog.ui.BodyStyle
import com.hakim.liquify.catalog.ui.CapsuleShape
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.GlassSurface
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.catalogGlassEffects
import com.hakim.liquify.catalog.ui.drawGlyph
import com.hakim.liquify.effects.blur
import com.hakim.liquify.group.LiquidGlassGroup
import com.hakim.liquify.liquify
import com.hakim.liquify.transition.materialize
import com.hakim.liquify.transition.motionBlur
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

private val MenuItems = listOf(
    Glyph.Circle to "One",
    Glyph.Triangle to "Two",
    Glyph.Square to "Three",
    Glyph.Hexagon to "Four"
)

/**
 * A menu that grows out of the button that opened it.
 *
 * While the panel is still close to the button the two are one piece of glass, so the menu reads
 * as having been *pulled out of* the control rather than as having appeared over it. Past a
 * certain distance the bridge thins and releases on its own — no explicit hand-off needed, it
 * falls out of the distance-field blend.
 */
@Composable
fun MenuScreen(backdrop: Backdrop) {
    var open by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "menu"
    )

    val colors = LocalCatalogColors.current
    val menuEffects = catalogGlassEffects()
    // The rows sit *inside* the panel's glass, so they need their own wash to separate from it —
    // dark over a dark pane, light over a light one.
    val rowScrim = if (colors.isLight) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.5f)

    val density = LocalDensity.current
    val buttonSize = 60.dp
    val panelWidth = 220.dp
    val panelHeight = 230.dp

    // The panel starts the size of the button, centred on it, and rises as it grows.
    val currentPanelWidth = lerp(buttonSize, panelWidth, progress)
    val currentPanelHeight = lerp(buttonSize, panelHeight, progress)
    val panelRise = with(density) {
        lerp(0.dp, (panelHeight / 2f), progress).toPx()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(84.dp))

        GlassSurface(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                CatalogText("Tap the share button", HeadingStyle)
                Spacer(Modifier.height(6.dp))
                CatalogText(
                    "The panel and the button are two members of one merged surface. They stay " +
                        "joined by a liquid neck until the panel has travelled far enough for it " +
                        "to break.",
                    BodyStyle
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        LiquidGlassGroup(
            backdrop = backdrop,
            // Motion blur on the group, not the panel: the glass is drawn by the group, so this
            // is what makes the whole merged surface smear as it travels rather than just its text.
            //
            // The padding sits *inside* the blur, and it is not decoration. A render effect
            // rasterises its layer at exactly the layer's bounds, so while the blur is live
            // anything drawn outside them is cut — and the group deliberately draws outside its
            // own box, by half a merge radius of bridge plus the merged shadow. With the share
            // button flush against the bottom edge that showed up as its glass being sliced off
            // flat for the length of the transition. The layer is grown by the same amount it is
            // inset, so the group keeps its old size and the content does not move.
            modifier = Modifier
                .fillMaxWidth()
                .height(470.dp)
                .motionBlur({progress})
                .padding(bottom = 48.dp),
            effects = menuEffects,
            merge = 0.6f,
            contentAlignment = Alignment.BottomCenter
        ) {
            // Panel — grows upward out of the button.
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, -panelRise.roundToInt()) }
                    .size(currentPanelWidth, currentPanelHeight)
                    // Animated corner radius, so the shape is a lambda here.
                    .liquify(
                        { RoundedRectangle(lerp(30.dp, 26.dp, progress)) },
                        dragging = true,
                        stretching = true,
                        interactiveHighlight = false
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        // Dissolves into the material instead of merely fading out.
                        .materialize({ progress * progress })
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MenuItems.forEach { (glyph, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .liquify(
                                    shape = { RoundedCornerShape(15.dp) },
                                    effects = {
                                        blur(20.dp.toPx())
                                    },
                                    backdrop = backdrop,
                                    highlight = null,
                                    shadow = null,
                                    innerShadow = null,
                                    onDrawBackdrop = { drawRect(rowScrim) },
                                    dragging = false,
                                    interactiveHighlight = true
                                )
                                .pointerInput(Unit) { detectTapGestures { open = false } }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Canvas(Modifier.size(26.dp)) {
                                drawGlyph(glyph, size.minDimension, colors.content)
                            }
                            CatalogText(label, LabelStyle)
                        }
                    }
                }
            }

            // Button — stays put.
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .liquify(CapsuleShape, dragging = true, interactiveHighlight = true)
                    .pointerInput(Unit) { detectTapGestures { open = !open } },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawGlyph(Glyph.Share, size.minDimension * 0.46f, colors.content)
                }
            }
        }
    }
}
