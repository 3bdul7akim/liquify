package com.hakim.liquify.catalog.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hakim.liquify.Backdrop
import com.hakim.liquify.catalog.ui.CapsuleShape
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.MaterialSurface
import com.hakim.liquify.catalog.ui.ParameterRow
import com.hakim.liquify.catalog.ui.catalogGlassEffects
import com.hakim.liquify.catalog.ui.components.LiquidToggle
import com.hakim.liquify.catalog.ui.drawGlyph
import com.hakim.liquify.group.LiquidGlassGroup
import com.hakim.liquify.liquify
import kotlin.math.roundToInt

private val Glyphs = listOf(Glyph.Circle, Glyph.Triangle, Glyph.Square, Glyph.Hexagon)

/**
 * The merge demo: several panes of glass sliding through each other.
 *
 * Everything interesting happens in the `effects` block — each element calls `merge()` and the
 * enclosing [LiquidGlassGroup] takes over rendering. Pull the separation slider slowly and watch
 * the bridge form well before the shapes actually touch, then thin out and snap.
 */
@Composable
fun MergeScreen(backdrop: Backdrop) {
    var separation by remember { mutableFloatStateOf(0.62f) }
    var strength by remember { mutableFloatStateOf(1f) }
    var count by remember { mutableIntStateOf(2) }
    var animate by remember { mutableStateOf(true) }

    val glyphColor = LocalCatalogColors.current.content
    val mergeEffects = catalogGlassEffects()

    val transition = rememberInfiniteTransition(label = "merge")
    val automatic by transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "separation"
    )
    val spread = if (animate) automatic else separation

    val elementSize = 88.dp
    // Wide enough that the far end of the slider clears the blend reach and the bridge actually
    // snaps, rather than the members merely looking adjacent.
    val maxGap = 190.dp
    val gapPx = with(LocalDensity.current) { (maxGap * spread).toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(104.dp))

        LiquidGlassGroup(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth().height(200.dp),
            effects = mergeEffects,
            merge = strength,
            contentAlignment = Alignment.Center
        ) {
            repeat(count) { index ->
                // Symmetric around the centre, so the group grows and shrinks in place.
                val slot = index - (count - 1) / 2f

                // Backdrop, material and merge strength all come from the group; a member only
                // has to say what shape it is.
                Box(
                    modifier = Modifier
                        .offset { IntOffset((gapPx * slot).roundToInt(), 0) }
                        .size(elementSize)
                        .liquify(CapsuleShape, dragging = true, interactiveHighlight = true),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawGlyph(Glyphs[index % Glyphs.size], size.minDimension * 0.36f, glyphColor)
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        // Plain surface, not glass: the toggle and the sliders are panes of glass themselves, and
        // stacking them on another one shows one backdrop through the other.
        MaterialSurface(modifier = Modifier.fillMaxWidth()) { cardBackdrop ->
            Column(Modifier.padding(18.dp)) {
                CatalogText("Controls", HeadingStyle)
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CatalogText("Animate", LabelStyle, Modifier.weight(1f))
                    LiquidToggle({ animate }, { animate = it }, cardBackdrop)
                }

                ParameterRow(
                    backdrop = cardBackdrop,
                    label = "Separation",
                    value = { if (animate) automatic else separation },
                    valueLabel = "%.2f".format(spread),
                    onValueChange = { separation = it; animate = false }
                )

                ParameterRow(
                    backdrop = cardBackdrop,
                    label = "merge(amount)",
                    value = { strength / 2f },
                    valueLabel = "%.2f".format(strength),
                    onValueChange = { strength = it * 2f }
                )

                ParameterRow(
                    backdrop = cardBackdrop,
                    label = "Elements",
                    value = { (count - 2) / 2f },
                    valueLabel = count.toString(),
                    onValueChange = { count = 2 + (it * 2f).roundToInt() }
                )
            }
        }
    }
}
