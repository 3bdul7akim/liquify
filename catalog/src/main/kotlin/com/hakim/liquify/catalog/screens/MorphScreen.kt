package com.hakim.liquify.catalog.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
import com.hakim.liquify.catalog.ui.MaterialSurface
import com.hakim.liquify.catalog.ui.catalogGlassEffects
import com.hakim.liquify.catalog.ui.drawGlyph
import com.hakim.liquify.group.LiquidGlassGroup
import com.hakim.liquify.liquify
import com.hakim.liquify.transition.motionBlur
import kotlin.math.roundToInt

private val SegmentWidths = listOf(64.dp, 64.dp, 124.dp)
private val SegmentGap = 12.dp
private val SegmentHeight = 62.dp

/**
 * One control splitting into three.
 *
 * The trick is that all three members start life *exactly on top of each other* at the collapsed
 * width. Identical overlapping capsules produce one capsule, so there is nothing to cross-fade —
 * the single "Select" pill genuinely is the three controls, and separating them is a continuous
 * motion of the same surface rather than one view being swapped for another.
 */
@Composable
fun MorphScreen(backdrop: Backdrop) {
    var expanded by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 150f),
        label = "morph"
    )

    val glyphColor = LocalCatalogColors.current.content
    val morphEffects = catalogGlassEffects()

    val density = LocalDensity.current
    val collapsedWidth = SegmentWidths.fold(SegmentGap * 2) { acc, dp -> acc + dp } * 0.78f

    val expandedTotal = SegmentWidths.fold(SegmentGap * 2) { acc, dp -> acc + dp }
    val expandedCenters = remember {
        val centers = mutableListOf<Dp>()
        var cursor = -expandedTotal / 2f
        SegmentWidths.forEach { width ->
            centers += cursor + width / 2f
            cursor += width + SegmentGap
        }
        centers
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
                CatalogText("Tap the control", HeadingStyle)
                Spacer(Modifier.height(6.dp))
                CatalogText(
                    "Three merged capsules start perfectly overlapped, so they read as one. " +
                        "Separating them stretches a single surface apart instead of swapping " +
                        "one view for another.",
                    BodyStyle
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        LiquidGlassGroup(
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            effects = morphEffects,
            merge = 0.5f,
            contentAlignment = Alignment.Center
        ) {
            SegmentWidths.forEachIndexed { index, expandedWidth ->
                val width = lerp(collapsedWidth, expandedWidth, progress)
                val centerPx = with(density) {
                    lerp(0.dp, expandedCenters[index], progress).toPx()
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(centerPx.roundToInt(), 0) }
                        .size(width, SegmentHeight)
                        .pointerInput(Unit) { detectTapGestures { expanded = !expanded } }
                        .liquify(CapsuleShape, dragging = true, interactiveHighlight = true)
                        .motionBlur({progress}, maxRadius = 10.dp, fullSpeed = 20f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.alpha(progress)) {
                        when (index) {
                            0 -> Canvas(Modifier.size(SegmentHeight)) {
                                drawGlyph(Glyph.Circle, size.minDimension * 0.4f, glyphColor)
                            }

                            1 -> Canvas(Modifier.size(SegmentHeight)) {
                                drawGlyph(Glyph.Triangle, size.minDimension * 0.4f, glyphColor)
                            }

                            else -> CatalogText("Done", LabelStyle)
                        }
                    }
                }
            }

            // The collapsed label lives above the merged surface rather than inside any one
            // member, because while collapsed no single member *is* the control.
            Box(Modifier.alpha(1f - progress).pointerInput(Unit) { detectTapGestures { expanded = !expanded } }) {
                CatalogText("Select", LabelStyle)
            }
        }

        Spacer(Modifier.height(20.dp))

        MaterialSurface(shape = CapsuleShape) { _ ->
            CatalogText(
                if (expanded) "Tap again to merge back" else "Tap to split",
                BodyStyle,
                Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
    }
}
