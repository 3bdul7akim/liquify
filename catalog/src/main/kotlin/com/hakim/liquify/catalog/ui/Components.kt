package com.hakim.liquify.catalog.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hakim.liquify.Backdrop
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.ProvideBackdrop
import com.hakim.liquify.backdrops.rememberCanvasBackdrop
import com.hakim.liquify.catalog.ui.components.LiquidSlider
import com.hakim.liquify.liquify
import com.hakim.liquify.material.GlassMaterial
import com.hakim.liquify.material.LocalGlassMaterial
import com.hakim.liquify.material.rememberGlassEffects
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

val CapsuleShape: Shape = Capsule()
val CardShape: Shape = RoundedRectangle(28.dp)

// Composable properties rather than plain vals: the colour comes from the theme, so it has to be
// read inside composition to follow a light/dark switch without the app being restarted.
val TitleStyle: TextStyle
    @Composable get() = TextStyle(
        color = LocalCatalogColors.current.content,
        fontSize = 30.sp,
        fontWeight = FontWeight.SemiBold
    )

val HeadingStyle: TextStyle
    @Composable get() = TextStyle(
        color = LocalCatalogColors.current.content,
        fontSize = 19.sp,
        fontWeight = FontWeight.Medium
    )

val BodyStyle: TextStyle
    @Composable get() = TextStyle(
        color = LocalCatalogColors.current.secondaryContent,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

val LabelStyle: TextStyle
    @Composable get() = TextStyle(
        color = LocalCatalogColors.current.content,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium
    )

@Composable
fun CatalogText(text: String, style: TextStyle = BodyStyle, @SuppressLint("ModifierParameter") modifier: Modifier = Modifier) {
    BasicText(text = text, style = style, modifier = modifier)
}

/**
 * The catalog's default glass treatment.
 *
 * The material is *not* hard-coded: it comes from
 * [LocalGlassMaterial][com.hakim.liquify.material.LocalGlassMaterial], so picking a different one
 * on the Materials screen re-renders every surface in the app at once.
 */
@Composable
fun GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    dragging: Boolean = false,
    interactiveHighlight: Boolean = false,
    tintColor: Color = LocalCatalogColors.current.glassTint,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier.liquify(
            shape = shape,
            backdrop = backdrop,
            tint = tintColor,
            dragging = dragging,
            interactiveHighlight = interactiveHighlight
        ),
        content = content
    )
}

/**
 * A plain, opaque surface — deliberately *not* glass.
 *
 * Two jobs, both of which glass does badly:
 *
 * - **It carries glass controls.** A toggle or a slider is already a pane of glass; sitting it on
 *   another one means one backdrop showing through the other, and the two refractions fight. A flat
 *   surface gives them something definite to sit on.
 * - **It backs bare text.** Anything drawn straight onto the wallpaper is at the mercy of whatever
 *   the photograph happens to be doing behind it, which in light mode means dark text on a dark
 *   leaf. This guarantees the contrast instead of hoping for it.
 *
 * The [Backdrop] handed to [content] is the surface's own colour, so a glass control inside
 * refracts *this* card rather than the wallpaper it hides. It is also provided as
 * [LocalBackdrop][com.hakim.liquify.LocalBackdrop], so a bare `liquify()` inside picks it up on its
 * own.
 */
@Composable
fun MaterialSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    content: @Composable BoxScope.(backdrop: Backdrop) -> Unit
) {
    val surfaceColor = LocalCatalogColors.current.materialSurface
    // Remembered on the colour: rememberCanvasBackdrop keys on the lambda, so a fresh one per
    // recomposition would rebuild the backdrop every frame.
    val onDraw: DrawScope.() -> Unit = remember(surfaceColor) { { drawRect(surfaceColor) } }
    val surfaceBackdrop = rememberCanvasBackdrop(onDraw)

    Box(modifier.background(surfaceColor, shape)) {
        ProvideBackdrop(surfaceBackdrop) {
            content(surfaceBackdrop)
        }
    }
}

/**
 * The app's material with the theme's glass tint folded in, as an effects block.
 *
 * This is what a [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup] needs: a group takes
 * an effect stack rather than a material, and it has to be a stable instance or the group rebuilds
 * its inherited defaults every recomposition.
 */
@Composable
fun catalogGlassEffects(
    material: GlassMaterial = LocalGlassMaterial.current
): BackdropEffectScope.() -> Unit =
    rememberGlassEffects(material.copy(tint = LocalCatalogColors.current.glassTint))

/**
 * A labelled slider row, used throughout the playground.
 *
 * [value] is a lambda, not a `Float`, on purpose: the slider observes it with `snapshotFlow`, so a
 * captured value would be read once and then never again and the thumb would sit still while the
 * number above it changed.
 */
@Composable
fun ParameterRow(
    backdrop: Backdrop,
    label: String,
    value: () -> Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CatalogText(label, LabelStyle, Modifier.weight(1f))
            CatalogText(valueLabel, BodyStyle.copy(textAlign = TextAlign.End))
        }
        LiquidSlider(
            value = value,
            onValueChange = onValueChange,
            backdrop = backdrop,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().height(32.dp)
        )
    }
}

/** Thin separator that stays visible over any backdrop. */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    val color = LocalCatalogColors.current.divider
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind { drawRect(color) }
    )
}
