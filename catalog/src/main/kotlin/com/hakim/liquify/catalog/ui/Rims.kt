package com.hakim.liquify.catalog.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hakim.liquify.GlassRim
import com.hakim.liquify.GlassRimStyle

/** The five rim styles Liquify ships, as the catalog offers them. */
enum class RimKind(val label: String) {
    Default("Default"),
    Ambient("Ambient"),
    Plain("Plain"),
    Outline("Outline"),
    TwSe("TwSe"),
}

/**
 * The rim of [kind] at [width], lit from [angle] degrees where the style has a light direction.
 * Outline and TwSe stay unblurred, as their presets are: their lines are meant to be crisp.
 */
fun rimOf(kind: RimKind, width: Dp, angle: Float = 45f, alpha: Float = 1f, blur: Dp = width / 2f): GlassRim =
    when (kind) {
        RimKind.Default -> GlassRim(width, blur, alpha, GlassRimStyle.Default(angle = angle))
        RimKind.Ambient -> GlassRim(width, blur, alpha, GlassRimStyle.Ambient(angle = angle))
        RimKind.Plain -> GlassRim(width, blur, alpha, GlassRimStyle.Plain())
        RimKind.Outline -> GlassRim(width, 0.dp, alpha, GlassRimStyle.Outline())
        RimKind.TwSe -> GlassRim(width, 0.dp, alpha, GlassRimStyle.TwSe(angle = angle))
    }
