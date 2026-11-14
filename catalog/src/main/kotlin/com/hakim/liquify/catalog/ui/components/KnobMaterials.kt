package com.hakim.liquify.catalog.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hakim.liquify.GlassInnerShadow
import com.hakim.liquify.GlassMaterial
import com.hakim.liquify.GlassShadow
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.LocalCatalogColors

/** The two states of a control's knob: what it is at rest, and what it turns into while held. */
@Immutable
class KnobMaterials(val atRest: GlassMaterial, val pressed: GlassMaterial)

/**
 * A knob that is an opaque white pill at rest and a clear lens while held: the white clears, the
 * blur gives way to a refraction of [lensHeight] by [lensAmount], and the app material's rim, a third
 * finer, and a recessed inner shadow grow in with the press. Liquify blends between the two by the
 * press, so the control only says how far it is held.
 *
 * Both are cut from the material the app declares, so the knob moves with the app's motion preset,
 * wears the app's rim and is domed and fringed as the app's lens switches say. At rest the white
 * covers the glass completely, so Liquify does not even record the backdrop until the knob is touched.
 */
@Composable
fun knobMaterials(lensHeight: Dp, lensAmount: Dp): KnobMaterials {
    val base = LiquifyTheme.material
    val knob = LocalCatalogColors.current.knob
    return remember(base, knob, lensHeight, lensAmount) {
        val atRest = base.copy(
            blurRadius = 8.dp,
            refractionHeight = 0.dp,
            refractionAmount = 0.dp,
            refractionBySize = false,
            saturation = 1f,
            tint = knob,
            rim = null,
            // Left unspecified, so it resolves against the theme: dark ink over a light UI and a pale
            // halo over a dark one, where black would simply not be there.
            shadow = GlassShadow(radius = 4.dp),
            innerShadow = null,
        )
        KnobMaterials(
            atRest = atRest,
            pressed = atRest.copy(
                blurRadius = 0.dp,
                refractionHeight = lensHeight,
                refractionAmount = lensAmount,
                tint = Color.Transparent,
                rim = base.rim?.let { it.copy(width = it.width / 1.5f, blurRadius = it.blurRadius / 1.5f) },
                innerShadow = GlassInnerShadow(radius = 4.dp),
            ),
        )
    }
}
