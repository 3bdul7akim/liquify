package com.hakim.liquify.catalog.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hakim.liquify.GlassLook
import com.hakim.liquify.GlassMaterial
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.LocalCatalogColors

/**
 * The fill of navigation chrome — segmented tabs, the search field, the snackbar — for the look in
 * scope: a dark or light wash over refracting glass, denser over a plain blur, near-opaque where
 * nothing is sampled and the fill *is* the pane.
 */
@Composable
fun barTint(): Color {
    val colors = LocalCatalogColors.current
    return when (LiquifyTheme.look) {
        GlassLook.Liquid -> colors.tabContainer
        GlassLook.Frosted -> colors.frostedTint
        GlassLook.Solid -> colors.solidTint
    }
}

/**
 * The theme's material re-cut for chrome: saturated harder than the panes around it, so a bar keeps
 * its colour over a busy page. Its dome and colour fringe stay the material's, so the switches on the
 * Looks & motion screen reach every bar. The other looks take the material as it is — Liquify adapts
 * it on its own.
 */
@Composable
fun barMaterial(): GlassMaterial {
    val base = LiquifyTheme.material
    val liquid = LiquifyTheme.look == GlassLook.Liquid
    return remember(base, liquid) {
        if (liquid) base.copy(saturation = 1.5f) else base
    }
}

/**
 * The fill of the large panes laid over a page — panels and the open menu — for the look in scope.
 */
@Composable
fun sheetTint(): Color {
    val colors = LocalCatalogColors.current
    return when (LiquifyTheme.look) {
        GlassLook.Liquid -> colors.sheetTint
        GlassLook.Frosted -> colors.frostedTint
        GlassLook.Solid -> colors.solidTint
    }
}

/**
 * The theme's material for a large pane over a page — the open menu: blurred at least enough that
 * the text beneath does not fight the text on the pane. Its dome and colour fringe stay the
 * material's, as every pane's do.
 */
@Composable
fun sheetMaterial(): GlassMaterial {
    val base = LiquifyTheme.material
    val look = LiquifyTheme.look
    return remember(base, look) {
        when (look) {
            GlassLook.Liquid -> base.copy(
                blurRadius = maxOf(base.blurRadius, 10.dp),
                innerShadow = null,
            )
            GlassLook.Frosted -> base.copy(blurRadius = maxOf(base.blurRadius, 24.dp), innerShadow = null)
            GlassLook.Solid -> base
        }
    }
}

/**
 * This material with its two expensive passes taken out — no blur and no refraction — for glass
 * that is travelling too fast to be looked through. Blend towards it by the speed of the flight:
 * `pressedMaterial = glass.calmed(), pressProgress = { motion.intensity }`.
 */
fun GlassMaterial.calmed(): GlassMaterial =
    copy(blurRadius = 0.dp, refractionHeight = 0.dp, refractionAmount = 0.dp)
