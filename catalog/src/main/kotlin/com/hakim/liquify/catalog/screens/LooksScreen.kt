package com.hakim.liquify.catalog.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hakim.liquify.GlassLook
import com.hakim.liquify.GlassMotion
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.DemoCard
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.ScreenColumn
import com.hakim.liquify.catalog.ui.SettingRow
import com.hakim.liquify.catalog.ui.ThemeMode
import com.hakim.liquify.catalog.ui.components.LiquidToggle
import com.hakim.liquify.catalog.ui.components.LiquidTopTabs
import com.hakim.liquify.liquify
import com.kyant.shapes.RoundedRectangle

private val Motions = listOf(GlassMotion.Performance, GlassMotion.Default, GlassMotion.Bouncy)

/**
 * The switches the whole app hangs off — declared once in the shell's [LiquifyTheme], so every pane
 * on every screen follows them on the next frame — and the same pane in all three looks at once,
 * through a nested `LiquifyTheme(look = …)`.
 */
@Composable
fun LooksScreen(
    look: GlassLook,
    onLookChange: (GlassLook) -> Unit,
    motion: GlassMotion,
    onMotionChange: (GlassMotion) -> Unit,
    depthEffect: Boolean,
    onDepthEffectChange: (Boolean) -> Unit,
    chromaticAberration: Boolean,
    onChromaticAberrationChange: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val colors = LocalCatalogColors.current

    ScreenColumn {
        // On the wallpaper rather than on a card, so there is something to refract. Each tile
        // overrides only the look — and the fill that look needs; everything else is inherited.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassLook.entries.forEach { tileLook ->
                val tint = when (tileLook) {
                    GlassLook.Liquid -> colors.glassTint
                    GlassLook.Frosted -> colors.frostedTint
                    GlassLook.Solid -> colors.solidTint
                }
                LiquifyTheme(look = tileLook, material = LiquifyTheme.material.copy(tint = tint)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(110.dp)
                            .liquify(RoundedRectangle(26.dp), dragging = true, stretching = true, interactiveHighlight = true),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlyphIcon(Glyph.Hexagon, size = 28.dp)
                            Spacer(Modifier.height(8.dp))
                            CatalogText(tileLook.name, LabelStyle)
                        }
                    }
                }
            }
        }

        DemoCard(
            "Look",
            "Liquid is everything: refraction, merging, touch. Frosted keeps the blur, tint, rim and " +
                "shadows and drops the lens. Solid samples nothing — a pane is its fill.",
        ) {
            LiquidTopTabs(
                titles = GlassLook.entries.map { it.name },
                selectedIndex = look.ordinal,
                onSelect = { onLookChange(GlassLook.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoCard(
            "Lens",
            "Two switches on the app's material, for every pane at once — bars, panels, knobs and merged " +
                "bodies alike. Only the bottom tabs keep their own dome. Liquid only, like the lens itself.",
        ) {
            SettingRow("Depth effect") { LiquidToggle({ depthEffect }, onDepthEffectChange) }
            SettingRow("Chromatic aberration") {
                LiquidToggle({ chromaticAberration }, onChromaticAberrationChange)
            }
        }

        DemoCard(
            "Motion",
            "How glass moves under the finger — the swell, the stretch, the springs. Calm for older " +
                "phones, bouncy for show. Press any control to feel it.",
        ) {
            LiquidTopTabs(
                titles = listOf("Calm", "Default", "Bouncy"),
                selectedIndex = Motions.indexOf(motion).coerceAtLeast(0),
                onSelect = { onMotionChange(Motions[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoCard(
            "Theme",
            "Light or dark for the catalog, apart from the phone. The glass resolves its theme-dependent " +
                "colours — unspecified shadows, outline rims — against it.",
        ) {
            LiquidTopTabs(
                titles = ThemeMode.entries.map { it.name },
                selectedIndex = themeMode.ordinal,
                onSelect = { onThemeModeChange(ThemeMode.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
