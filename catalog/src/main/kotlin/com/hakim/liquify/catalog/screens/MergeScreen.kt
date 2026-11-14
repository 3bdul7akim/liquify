package com.hakim.liquify.catalog.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hakim.liquify.LiquidGlassGroup
import com.hakim.liquify.catalog.ui.BodyStyle
import com.hakim.liquify.catalog.ui.CapsuleShape
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.HeadingStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.catalog.ui.MaterialSurface
import com.hakim.liquify.catalog.ui.ParameterRow
import com.hakim.liquify.catalog.ui.SettingRow
import com.hakim.liquify.catalog.ui.components.LiquidToggle
import com.hakim.liquify.catalog.ui.screenPadding
import com.hakim.liquify.liquify
import kotlin.math.roundToInt

private val Glyphs = listOf(Glyph.Circle, Glyph.Triangle, Glyph.Square, Glyph.Hexagon)

/** How much of its colour a member takes: enough to see two of them mix in a bridge. */
private const val MemberTintAlpha = 0.7f

/**
 * The merge demo: several panes of glass sliding through each other.
 *
 * The members only name their shape — and, with Colours on, a colour each. The enclosing
 * [LiquidGlassGroup] makes them one body and blends their colours through the bridges, so two
 * bubbles meeting mix into the colour in between. Pull the separation slider slowly and watch the
 * bridge form well before the shapes touch, then thin out and snap; drag a bubble out of the body
 * and it carries its colour with it. A held bubble lights up in its own colour, and the light flows
 * through a bridge just as the colour does.
 */
@Composable
fun MergeScreen() {
    var separation by remember { mutableFloatStateOf(0.62f) }
    var strength by remember { mutableFloatStateOf(1f) }
    var count by remember { mutableIntStateOf(2) }
    var animate by remember { mutableStateOf(true) }
    var coloured by remember { mutableStateOf(true) }

    val colors = LocalCatalogColors.current

    val transition = rememberInfiniteTransition(label = "merge")
    val automatic by transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "separation"
    )
    val spread = if (animate) automatic else separation

    val elementSize = 88.dp
    // Wide enough that the far end of the slider clears the blend reach and the bridge actually
    // snaps, rather than the members merely looking adjacent.
    val maxGap = 190.dp
    val gapPx = with(LocalDensity.current) { (maxGap * spread).toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(screenPadding())
    ) {
        Spacer(Modifier.height(30.dp))

        LiquidGlassGroup(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            merge = strength,
            contentAlignment = Alignment.Center
        ) {
            repeat(count) { index ->
                // Symmetric around the centre, so the group grows and shrinks in place.
                val slot = index - (count - 1) / 2f
                val tint =
                    if (coloured) colors.memberColors[index % colors.memberColors.size].copy(alpha = MemberTintAlpha)
                    else Color.Unspecified

                Box(
                    modifier = Modifier
                        .offset { IntOffset((gapPx * slot).roundToInt(), 0) }
                        .size(elementSize)
                        .liquify(CapsuleShape, tint = tint, dragging = true, interactiveHighlight = true),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(
                        Glyphs[index % Glyphs.size],
                        color = if (coloured) Color.White else colors.content,
                        size = 32.dp,
                    )
                }
            }
        }

        Spacer(Modifier.height(30.dp))

        // Plain surface, not glass: the toggle and the sliders are panes of glass themselves, and
        // stacking them on another one shows one backdrop through the other.
        MaterialSurface(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                CatalogText("Controls", HeadingStyle)
                Spacer(Modifier.height(4.dp))
                CatalogText(
                    "With Colours on, every member names its own tint and the bridges mix them. " +
                        "Hold a bubble and it lights up in its own colour, the light flowing through " +
                        "the bridge with it. Drag it away from the others: its colour flows along.",
                    BodyStyle
                )
                Spacer(Modifier.height(10.dp))

                SettingRow("Animate") { LiquidToggle({ animate }, { animate = it }) }
                SettingRow("Colours") { LiquidToggle({ coloured }, { coloured = it }) }

                ParameterRow(
                    label = "Separation",
                    value = { if (animate) automatic else separation },
                    valueLabel = "%.2f".format(spread),
                    onValueChange = { separation = it; animate = false }
                )

                ParameterRow(
                    label = "merge",
                    value = { strength / 2f },
                    valueLabel = "%.2f".format(strength),
                    onValueChange = { strength = it * 2f },
                    defaultValue = 0.5f,
                )

                ParameterRow(
                    label = "Elements",
                    value = { (count - 2) / 2f },
                    valueLabel = count.toString(),
                    onValueChange = { count = 2 + (it * 2f).roundToInt() }
                )
            }
        }
    }
}
