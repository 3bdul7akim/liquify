package com.hakim.liquify.catalog.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.hakim.liquify.GlassInnerShadow
import com.hakim.liquify.GlassShadow
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.CatalogTopBarAction
import com.hakim.liquify.catalog.ui.DemoCard
import com.hakim.liquify.catalog.ui.Divider
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.ParameterRow
import com.hakim.liquify.catalog.ui.RimKind
import com.hakim.liquify.catalog.ui.ScreenColumn
import com.hakim.liquify.catalog.ui.SettingRow
import com.hakim.liquify.catalog.ui.components.LiquidToggle
import com.hakim.liquify.catalog.ui.components.LiquidTopTabs
import com.hakim.liquify.catalog.ui.rimOf
import com.hakim.liquify.liquify
import com.kyant.shapes.RoundedRectangle

/** The rim each segment of the picker wears, wide enough to read on a 40 dp pill. */
private val SegmentRimWidth = 1.dp

/** The edge of the preview, at values that show it clearly rather than at the subtle defaults. */
@Immutable
private data class EdgeRecipe(
    val rim: RimKind = RimKind.Default,
    val rimWidth: Float = 1.5f,
    val rimBlur: Float = 0.75f,
    val rimAngle: Float = 45f,
    val rimAlpha: Float = 1f,
    val shadow: Boolean = true,
    val shadowRadius: Float = 12f,
    val shadowOffset: Float = 8f,
    val shadowAlpha: Float = 1f,
    val glow: Boolean = false,
    val innerShadow: Boolean = false,
    val innerRadius: Float = 18f,
    val innerOffset: Float = 10f,
    val innerAlpha: Float = 1f,
)

/**
 * Everything around the glass: the specular rim, the drop shadow and the inner shadow.
 *
 * The rim is most of what separates a glass control from a translucent rectangle. Its style says how
 * its light varies around the border, the angle where the light comes from; Outline and TwSe are
 * crisp by design and ignore the blur. The shadow lifts a pane off the page — in the theme's ink, or
 * as a glow in the accent — and the inner shadow sinks it in. Under the Solid look all three remain:
 * they are drawn around the pane, not sampled.
 */
@Composable
fun RimsScreen() {
    var edge by remember { mutableStateOf(EdgeRecipe()) }
    val colors = LocalCatalogColors.current

    CatalogTopBarAction(Glyph.Reset, "Reset the edge", colors.danger) { edge = EdgeRecipe() }

    val base = LiquifyTheme.material
    val material = remember(edge, base, colors.accent) {
        base.copy(
            rim = rimOf(edge.rim, edge.rimWidth.dp, edge.rimAngle, edge.rimAlpha, edge.rimBlur.dp),
            shadow = if (!edge.shadow) null else GlassShadow(
                radius = edge.shadowRadius.dp,
                offset = DpOffset(0.dp, edge.shadowOffset.dp),
                // Unspecified resolves against the theme: dark ink over a light UI, a pale halo over
                // a dark one.
                color = if (edge.glow) colors.accent else Color.Unspecified,
                alpha = edge.shadowAlpha,
            ),
            innerShadow = if (!edge.innerShadow) null else GlassInnerShadow(
                radius = edge.innerRadius.dp,
                offset = DpOffset(0.dp, edge.innerOffset.dp),
                alpha = edge.innerAlpha,
            ),
        )
    }
    val segmentRims = remember { RimKind.entries.map { rimOf(it, SegmentRimWidth) } }

    ScreenColumn {
        // On the wallpaper, so the rim and the shadows have something to sit on.
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .liquify(
                    RoundedRectangle(36.dp),
                    material = material,
                    dragging = true,
                    stretching = true,
                    interactiveHighlight = true,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlyphIcon(Glyph.Star, size = 34.dp)
                Spacer(Modifier.height(10.dp))
                CatalogText("${edge.rim.label} rim", HeadingStyle)
            }
        }

        // The five styles side by side, each at the same width.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RimKind.entries.forEach { kind ->
                val tileMaterial = remember(base, kind) { base.copy(rim = rimOf(kind, 1.5.dp, blur = 0.75.dp)) }
                Box(
                    Modifier
                        .weight(1f)
                        .height(64.dp)
                        .liquify(RoundedRectangle(20.dp), material = tileMaterial, interactiveHighlight = true),
                    contentAlignment = Alignment.Center,
                ) {
                    CatalogText(kind.label, LabelStyle.copy(fontSize = LabelStyle.fontSize * 0.8f))
                }
            }
        }

        DemoCard(
            "Rim",
            "Each segment wears its rim, so every choice shows itself before it is made. The light " +
                "angle turns the lit side around the pane.",
        ) {
            LiquidTopTabs(
                titles = RimKind.entries.map { it.label },
                selectedIndex = edge.rim.ordinal,
                onSelect = { edge = edge.copy(rim = RimKind.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
                segmentRims = segmentRims,
            )
            Spacer(Modifier.height(8.dp))
            ParameterRow(
                "Light angle", { edge.rimAngle }, "${edge.rimAngle.toInt()}°", 0f..360f,
                { edge = edge.copy(rimAngle = it) }, defaultValue = 45f,
            )
            ParameterRow(
                "Width", { edge.rimWidth }, "%.2f dp".format(edge.rimWidth), 0.25f..4f,
                { edge = edge.copy(rimWidth = it) }, defaultValue = 1.5f,
            )
            ParameterRow(
                "Blur", { edge.rimBlur }, "%.2f dp".format(edge.rimBlur), 0f..4f,
                { edge = edge.copy(rimBlur = it) }, defaultValue = 0.75f,
            )
            ParameterRow(
                "Opacity", { edge.rimAlpha }, "%.2f".format(edge.rimAlpha), 0f..1f,
                { edge = edge.copy(rimAlpha = it) }, defaultValue = 1f,
            )
        }

        DemoCard(
            "Drop shadow",
            "Lifts the pane off the page. Left without a colour it takes the theme's ink; as a glow it " +
                "takes the accent.",
        ) {
            SettingRow("Shadow") { LiquidToggle({ edge.shadow }, { edge = edge.copy(shadow = it) }) }
            SettingRow("Glow in the accent") { LiquidToggle({ edge.glow }, { edge = edge.copy(glow = it) }) }
            Divider()
            ParameterRow(
                "Radius", { edge.shadowRadius }, "${edge.shadowRadius.toInt()} dp", 0f..40f,
                { edge = edge.copy(shadowRadius = it) }, defaultValue = 12f,
            )
            ParameterRow(
                "Offset", { edge.shadowOffset }, "${edge.shadowOffset.toInt()} dp", -24f..24f,
                { edge = edge.copy(shadowOffset = it) }, defaultValue = 8f,
            )
            ParameterRow(
                "Opacity", { edge.shadowAlpha }, "%.2f".format(edge.shadowAlpha), 0f..1f,
                { edge = edge.copy(shadowAlpha = it) }, defaultValue = 1f,
            )
        }

        DemoCard(
            "Inner shadow",
            "A shadow inside the pane, for a recessed look — what the knobs of the toggle and the " +
                "slider grow while they are held.",
        ) {
            SettingRow("Inner shadow") {
                LiquidToggle({ edge.innerShadow }, { edge = edge.copy(innerShadow = it) })
            }
            Divider()
            ParameterRow(
                "Radius", { edge.innerRadius }, "${edge.innerRadius.toInt()} dp", 0f..40f,
                { edge = edge.copy(innerRadius = it) }, defaultValue = 18f,
            )
            ParameterRow(
                "Offset", { edge.innerOffset }, "${edge.innerOffset.toInt()} dp", -24f..24f,
                { edge = edge.copy(innerOffset = it) }, defaultValue = 10f,
            )
            ParameterRow(
                "Opacity", { edge.innerAlpha }, "%.2f".format(edge.innerAlpha), 0f..1f,
                { edge = edge.copy(innerAlpha = it) }, defaultValue = 1f,
            )
        }
    }
}
