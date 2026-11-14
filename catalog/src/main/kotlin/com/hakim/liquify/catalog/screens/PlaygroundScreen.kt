package com.hakim.liquify.catalog.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hakim.liquify.GlassInnerShadow
import com.hakim.liquify.GlassShadow
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.BodyStyle
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.CatalogTopBarAction
import com.hakim.liquify.catalog.ui.DemoCard
import com.hakim.liquify.catalog.ui.Divider
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.HeadingStyle
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

/** Everything the playground's card is made of, at the values of the Regular material. */
@Immutable
private data class Recipe(
    val blur: Float = 8f,
    val height: Float = 20f,
    val amount: Float = 35f,
    val saturation: Float = 1.15f,
    val tint: Float = 0.06f,
    val corner: Float = 36f,
    val depth: Boolean = true,
    val dispersion: Boolean = false,
    val bySize: Boolean = true,
    val rim: RimKind? = RimKind.Default,
    val shadow: Boolean = true,
    val innerShadow: Boolean = false,
    val pressed: Boolean = true,
    val refractsContent: Boolean = false,
    val stretching: Boolean = true,
)

/**
 * Every parameter of a [GlassMaterial][com.hakim.liquify.GlassMaterial] on one card, live.
 *
 * The card is built from the sliders below as an ordinary material, so what it shows is exactly what
 * an app gets from `GlassMaterial(…)`. With **Pressed state** on it also has a `pressedMaterial` — a
 * clear, deeper lens with a recessed shadow — and blends into it while held, in the draw phase. The
 * reset in the top bar puts everything back.
 */
@Composable
fun PlaygroundScreen() {
    var recipe by remember { mutableStateOf(Recipe()) }
    val colors = LocalCatalogColors.current

    CatalogTopBarAction(Glyph.Reset, "Reset the recipe", colors.danger) { recipe = Recipe() }

    // The app's motion stays: the card moves like every other pane.
    val base = LiquifyTheme.material
    val material = remember(recipe, base) {
        base.copy(
            blurRadius = recipe.blur.dp,
            refractionHeight = recipe.height.dp,
            refractionAmount = recipe.amount.dp,
            saturation = recipe.saturation,
            tint = Color.White.copy(alpha = recipe.tint),
            depthEffect = recipe.depth,
            chromaticAberration = recipe.dispersion,
            refractionBySize = recipe.bySize,
            rim = recipe.rim?.let { rimOf(it, width = if (it == RimKind.Outline || it == RimKind.TwSe) 1.dp else 0.5.dp) },
            shadow = if (recipe.shadow) GlassShadow.Default else null,
            innerShadow = if (recipe.innerShadow) GlassInnerShadow.Default else null,
        )
    }
    val pressedMaterial = remember(material, recipe.pressed) {
        if (!recipe.pressed) null
        else material.copy(
            blurRadius = 0.dp,
            refractionHeight = material.refractionHeight * 1.5f,
            refractionAmount = material.refractionAmount * 1.5f,
            tint = colors.accent.copy(alpha = 0.12f),
            innerShadow = GlassInnerShadow(radius = 10.dp),
        )
    }

    ScreenColumn {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .liquify(
                    shape = RoundedRectangle(recipe.corner.dp),
                    material = material,
                    pressedMaterial = pressedMaterial,
                    // Two booleans and the card leans towards the finger and lights up — no
                    // interaction object to hoist. The press it measures drives the pressed blend.
                    dragging = true,
                    stretching = recipe.stretching,
                    interactiveHighlight = true,
                    refractsContent = recipe.refractsContent,
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    listOf(Glyph.Circle, Glyph.Triangle, Glyph.Square, Glyph.Hexagon).forEach { glyph ->
                        GlyphIcon(glyph, size = 34.dp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                CatalogText("Hold and drag me", HeadingStyle)
            }
        }

        DemoCard(
            "Lens",
            "The band along the rim that bends the backdrop, and how far it bends it. By size, an " +
                "element eases into these values as it grows; off, every element gets them as they are.",
        ) {
            ParameterRow(
                "refractionHeight", { recipe.height }, "${recipe.height.toInt()} dp", 0f..60f,
                { recipe = recipe.copy(height = it) }, defaultValue = 20f,
            )
            ParameterRow(
                "refractionAmount", { recipe.amount }, "${recipe.amount.toInt()} dp", 0f..80f,
                { recipe = recipe.copy(amount = it) }, defaultValue = 35f,
            )
            SettingRow("refractionBySize") {
                LiquidToggle({ recipe.bySize }, { recipe = recipe.copy(bySize = it) })
            }
            SettingRow("depthEffect") { LiquidToggle({ recipe.depth }, { recipe = recipe.copy(depth = it) }) }
            SettingRow("chromaticAberration") {
                LiquidToggle({ recipe.dispersion }, { recipe = recipe.copy(dispersion = it) })
            }
            SettingRow("refractsContent") {
                LiquidToggle({ recipe.refractsContent }, { recipe = recipe.copy(refractsContent = it) })
            }
        }

        DemoCard(
            "Surface",
            "Blur under the lens, vibrancy so colours survive it, a tint over it, and the shape.",
        ) {
            ParameterRow(
                "blurRadius", { recipe.blur }, "${recipe.blur.toInt()} dp", 0f..40f,
                { recipe = recipe.copy(blur = it) }, defaultValue = 8f,
            )
            ParameterRow(
                "saturation", { recipe.saturation }, "%.2f".format(recipe.saturation), 1f..3f,
                { recipe = recipe.copy(saturation = it) }, defaultValue = 1.15f,
            )
            ParameterRow(
                "tint (alpha)", { recipe.tint }, "%.2f".format(recipe.tint), 0f..0.6f,
                { recipe = recipe.copy(tint = it) }, defaultValue = 0.06f,
            )
            ParameterRow(
                "Corner radius", { recipe.corner }, "${recipe.corner.toInt()} dp", 0f..100f,
                { recipe = recipe.copy(corner = it) }, defaultValue = 36f,
            )
        }

        DemoCard(
            "Edge and depth",
            "The rim, the drop shadow and a recessed inner shadow — the Rims & shadows screen has " +
                "every knob of them.",
        ) {
            SettingRow("Rim") {
                LiquidToggle({ recipe.rim != null }, { recipe = recipe.copy(rim = if (it) RimKind.Default else null) })
            }
            recipe.rim?.let { rim ->
                LiquidTopTabs(
                    titles = RimKind.entries.map { it.label },
                    selectedIndex = rim.ordinal,
                    onSelect = { recipe = recipe.copy(rim = RimKind.entries[it]) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
            Divider()
            SettingRow("shadow") { LiquidToggle({ recipe.shadow }, { recipe = recipe.copy(shadow = it) }) }
            SettingRow("innerShadow") {
                LiquidToggle({ recipe.innerShadow }, { recipe = recipe.copy(innerShadow = it) })
            }
        }

        DemoCard(
            "Under the finger",
            "pressedMaterial blends the whole recipe towards a second one while the card is held — " +
                "here a clear, deeper lens. Stretching lets it swell and pull along its travel.",
        ) {
            SettingRow("Pressed state") { LiquidToggle({ recipe.pressed }, { recipe = recipe.copy(pressed = it) }) }
            SettingRow("stretching") {
                LiquidToggle({ recipe.stretching }, { recipe = recipe.copy(stretching = it) })
            }
            Spacer(Modifier.height(6.dp))
            CatalogText(
                "Frosted keeps the blur, tint, rim and shadows and drops the lens and the touch; Solid " +
                    "keeps only the tint, rim and shadows. Switch looks on the Looks & motion screen.",
                BodyStyle
            )
        }
    }
}
