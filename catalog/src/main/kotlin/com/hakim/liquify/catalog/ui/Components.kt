package com.hakim.liquify.catalog.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.components.LiquidSlider
import com.hakim.liquify.catalog.ui.components.topBarClearance
import com.hakim.liquify.colorBackdrop
import com.hakim.liquify.liquify
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

val CapsuleShape: Shape = Capsule()
val CardShape: Shape = RoundedRectangle(28.dp)

/**
 * [CapsuleShape] as the circular capsule, for what a component clips to or draws itself on every
 * frame — a track, a knob, a tab's touch area. The renderer handles it analytically, where the
 * continuous capsule is a path: clipped to, it costs a coverage mask for every draw inside it on every
 * frame; drawn small, it is rasterised into a path atlas on every frame. At the ends of a capsule the
 * two curves differ by 0.03 % of the radius. Glass keeps [CapsuleShape], whose rim follows the curve.
 */
val CircularCapsule: Shape = CircleShape

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
 * The catalog's default pane of glass.
 *
 * It names nothing but its shape: backdrop, material and tint all come from the [LiquifyTheme] at
 * the root, so picking a different material on the Materials screen re-renders every surface at once.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    dragging: Boolean = false,
    interactiveHighlight: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier.liquify(
            shape = shape,
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
 * - **It carries glass controls.** A toggle or a slider is already a pane of glass; on another one,
 *   one backdrop shows through the other and the two refractions fight.
 * - **It backs bare text**, whose contrast would otherwise depend on the photograph behind it.
 *
 * It declares its own colour as the backdrop for everything inside, so a glass control in it
 * refracts *this* card rather than the wallpaper it hides — and does so without being told.
 */
@Composable
fun MaterialSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    content: @Composable BoxScope.() -> Unit
) {
    val surfaceColor = LocalCatalogColors.current.materialSurface
    val surfaceBackdrop = colorBackdrop(surfaceColor)

    Box(modifier.background(surfaceColor, shape)) {
        LiquifyTheme(backdrop = surfaceBackdrop) {
            content()
        }
    }
}

/**
 * A labelled slider row, used throughout the playground.
 *
 * [value] is a lambda, not a `Float`: the slider observes it with `snapshotFlow`, so a captured value
 * would be read once and the thumb would sit still while the number above it changed.
 *
 * @param defaultValue what a double tap on the thumb returns to, or `null` for none.
 */
@Composable
fun ParameterRow(
    label: String,
    value: () -> Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    defaultValue: Float? = null,
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
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().height(32.dp),
            defaultValue = defaultValue,
        )
    }
}

/** Room for a screen's content: under the top bar, above the navigation bar. */
@Composable
fun screenPadding(horizontal: Dp = 20.dp): PaddingValues = PaddingValues(
    start = horizontal,
    top = topBarClearance() + 12.dp,
    end = horizontal,
    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 30.dp,
)

/** A screen's scrolling column of demos, cleared of the top bar and the navigation bar. */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(screenPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/**
 * A titled demo card: a plain surface with a heading and a line of explanation, then the demo. The
 * surface is not glass on purpose — see [MaterialSurface].
 */
@Composable
fun DemoCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    MaterialSurface(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            CatalogText(title, HeadingStyle)
            Spacer(Modifier.height(4.dp))
            CatalogText(description, BodyStyle)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/**
 * A heading and a line of explanation on a plain surface, for a demo that sits on the wallpaper
 * below it — glass that has to refract something rather than a card's flat colour.
 */
@Composable
fun DemoNote(title: String, description: String, modifier: Modifier = Modifier) {
    MaterialSurface(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            CatalogText(title, HeadingStyle)
            Spacer(Modifier.height(4.dp))
            CatalogText(description, BodyStyle)
        }
    }
}

/** A label on the left and a control on the right — a toggle, mostly. */
@Composable
fun SettingRow(label: String, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatalogText(label, LabelStyle, Modifier.weight(1f))
        control()
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
