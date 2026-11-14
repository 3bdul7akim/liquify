package com.hakim.liquify.catalog.screens

import android.graphics.ColorMatrixColorFilter
import android.os.Build
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hakim.liquify.Backdrop
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.BodyStyle
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.DemoCard
import com.hakim.liquify.catalog.ui.DemoNote
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.ParameterRow
import com.hakim.liquify.catalog.ui.ScreenColumn
import com.hakim.liquify.catalog.ui.SettingRow
import com.hakim.liquify.catalog.ui.components.LiquidToggle
import com.hakim.liquify.catalog.ui.components.LiquidTopTabs
import com.hakim.liquify.colorBackdrop
import com.hakim.liquify.emptyBackdrop
import com.hakim.liquify.liquify
import com.hakim.liquify.progressiveBlur
import com.hakim.liquify.rememberBackdrop
import com.hakim.liquify.rememberCanvasBackdrop
import com.hakim.liquify.rememberCombinedBackdrop
import com.hakim.liquify.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

private val TileShape = RoundedRectangle(26.dp)
private val TileHeight = 104.dp
private val LensSize = 92.dp

private val Filters = listOf("None", "Mono", "Sepia", "Invert")

/** The colour matrices of [Filters], row by row: red, green, blue, alpha — each times r, g, b, a, offset. */
private val FilterMatrices: List<FloatArray?> = listOf(
    null,
    floatArrayOf(
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ),
    floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ),
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/**
 * What glass refracts, and what else it can do with it.
 *
 * - **Backdrops** — the wallpaper's layer, a drawn canvas, one colour, two combined, one decorated
 *   (here: magnified) and none at all.
 * - **Glass on glass** — a pane that exports its glass, and a lens that refracts it.
 * - **Refracted content** — the content bent along with the backdrop, as if it lay behind the glass.
 * - **Effects of your own** — a hand-written recipe with an arbitrary render effect in the chain.
 * - **Progressive blur** — a blur that is strongest at the top and fades out downwards.
 */
@Composable
fun BackdropsScreen() {
    ScreenColumn {
        DemoNote(
            "Backdrops",
            "The same tile over six backdrops. Glass refracts whatever it is given — or, given nothing, " +
                "inherits the one LiquifyTheme declares.",
        )
        BackdropTiles()

        DemoNote(
            "Glass on glass",
            "The coloured pane records its own glass into a backdrop (exportedBackdrop). The lens " +
                "refracts that and the wallpaper — drag it across the pane.",
        )
        GlassOnGlass()

        RefractedContent()

        CustomEffects()

        ProgressiveBlurDemo()
    }
}

@Composable
private fun BackdropTiles() {
    val colors = LocalCatalogColors.current
    val wallpaper = LiquifyTheme.backdrop
    val stripes = remember(colors.accent) {
        Brush.linearGradient(
            0f to colors.accent.copy(alpha = 0.85f),
            0.5f to colors.accent.copy(alpha = 0.85f),
            0.5f to Color.Transparent,
            1f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(36f, 36f),
            tileMode = TileMode.Repeated,
        )
    }
    val canvas = rememberCanvasBackdrop { drawRect(stripes) }
    val combined = rememberCombinedBackdrop(wallpaper, canvas)
    // A decorated backdrop: the wallpaper drawn through a transform, here magnified about the centre.
    val magnified = wallpaper?.let { rememberBackdrop(it) { draw -> scale(1.8f) { draw() } } }
    val solid = remember(colors.accent) { colorBackdrop(colors.accent) }

    val tiles: List<Pair<String, Backdrop?>> = listOf(
        "Layer" to wallpaper,
        "Canvas" to canvas,
        "Colour" to solid,
        "Combined" to combined,
        "Decorated" to magnified,
        "Empty" to emptyBackdrop(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, backdrop) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(TileHeight)
                            .liquify(TileShape, backdrop = backdrop, interactiveHighlight = true),
                        contentAlignment = Alignment.Center,
                    ) {
                        CatalogText(label, LabelStyle)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassOnGlass() {
    val colors = LocalCatalogColors.current
    val paneBackdrop = rememberLayerBackdrop()
    val lensBackdrop = rememberCombinedBackdrop(LiquifyTheme.backdrop, paneBackdrop)
    val base = LiquifyTheme.material
    // A clear, domed lens: no blur and no wash, so all it does is bend what is under it.
    val lens = remember(base) {
        base.copy(blurRadius = 0.dp, refractionHeight = 22.dp, refractionAmount = 34.dp, depthEffect = true, tint = Color.Transparent)
    }
    var lensOffset by remember { mutableStateOf(Offset.Zero) }
    val lensPx = with(LocalDensity.current) { LensSize.toPx() }

    BoxWithConstraints(Modifier.fillMaxWidth().height(200.dp)) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // The pane: coloured glass that records itself for the lens.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(120.dp)
                .liquify(
                    RoundedRectangle(30.dp),
                    tint = colors.accent.copy(alpha = 0.55f),
                    exportedBackdrop = paneBackdrop,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Hexagon, color = Color.White, size = 28.dp)
                CatalogText("A pane of glass", HeadingStyle.copy(color = Color.White))
            }
        }

        // The lens: a sibling of the pane, never inside it — glass must not sample a layer it is in.
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (width / 2f - lensPx / 2f + lensOffset.x).roundToInt(),
                        (height / 2f - lensPx / 2f + lensOffset.y).roundToInt(),
                    )
                }
                .size(LensSize)
                .pointerInput(width, height) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        lensOffset = Offset(
                            (lensOffset.x + drag.x).coerceIn(-width / 2f + lensPx / 2f, width / 2f - lensPx / 2f),
                            (lensOffset.y + drag.y).coerceIn(-height / 2f + lensPx / 2f, height / 2f - lensPx / 2f),
                        )
                    }
                }
                .liquify(CircleShape, material = lens, backdrop = lensBackdrop, interactiveHighlight = true)
        )
    }
}

@Composable
private fun RefractedContent() {
    var refracts by remember { mutableStateOf(true) }

    DemoCard(
        "Refracted content",
        "refractsContent bends the content along with the backdrop, as if it lay behind the glass. " +
            "Colour and blur stay with the backdrop, so text is bent but never blurred. Liquid only.",
    ) {
        SettingRow("refractsContent") { LiquidToggle({ refracts }, { refracts = it }) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .liquify(
                RoundedRectangle(40.dp),
                dragging = true,
                stretching = true,
                interactiveHighlight = true,
                refractsContent = refracts,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(Glyph.Circle, Glyph.Triangle, Glyph.Square, Glyph.Hexagon, Glyph.Star).forEach {
                    GlyphIcon(it, size = 30.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            CatalogText("Text near the rim bends with the glass", BodyStyle)
        }
    }
}

@Composable
private fun CustomEffects() {
    var filter by remember { mutableIntStateOf(1) }
    // Built once per choice; the recipe below only appends it.
    val filterEffect = remember(filter) { FilterMatrices[filter]?.let(::colorFilterEffect) }

    DemoCard(
        "Effects of your own",
        "A hand-written recipe instead of a material — effects = { … }, where call order is GPU order " +
            "— with an arbitrary RenderEffect in the chain: here a colour matrix, before the lens, so " +
            "the filtered colours bend with the rim. Android 12 and newer.",
    ) {
        LiquidTopTabs(Filters, filter, { filter = it }, Modifier.fillMaxWidth())
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .liquify(
                RoundedRectangle(40.dp),
                dragging = true,
                interactiveHighlight = true,
                effects = {
                    vibrancy()
                    blur(2.dp)
                    filterEffect?.let { effect(it) }
                    lens(refractionHeight = 24.dp, refractionAmount = 36.dp, depthEffect = true)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CatalogText(Filters[filter], HeadingStyle)
    }
}

@Composable
private fun ProgressiveBlurDemo() {
    val colors = LocalCatalogColors.current
    var radius by remember { mutableFloatStateOf(20f) }
    var tint by remember { mutableFloatStateOf(0.6f) }

    DemoCard(
        "Progressive blur",
        "Modifier.progressiveBlur — the backdrop blurred and tinted at the top, dissolving towards the " +
            "bottom edge. No outline, no rim: a header surface, not a pane. Under Solid it is the fade " +
            "alone.",
    ) {
        ParameterRow(
            "radius", { radius }, "${radius.toInt()} dp", 0f..40f,
            { radius = it }, defaultValue = 20f,
        )
        ParameterRow(
            "tint (alpha)", { tint }, "%.2f".format(tint), 0f..1f,
            { tint = it }, defaultValue = 0.6f,
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(30.dp))
            .progressiveBlur(radius = radius.dp, tint = colors.background.copy(alpha = tint)),
        contentAlignment = Alignment.TopCenter,
    ) {
        CatalogText("Strongest up here", HeadingStyle, Modifier.padding(top = 24.dp))
    }
}

/** A colour-matrix render effect, or `null` below Android 12, where render effects do not exist. */
private fun colorFilterEffect(matrix: FloatArray): RenderEffect? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        android.graphics.RenderEffect
            .createColorFilterEffect(ColorMatrixColorFilter(matrix))
            .asComposeRenderEffect()
    } else {
        null
    }
