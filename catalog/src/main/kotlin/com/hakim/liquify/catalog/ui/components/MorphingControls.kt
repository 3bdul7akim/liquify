package com.hakim.liquify.catalog.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.hakim.liquify.LiquidGlassGroup
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The three levels the row reshapes itself between. */
enum class MorphLevel { List, Item, Session }

/** The controls of the row, in the order [MorphingControlsLayout] takes them. */
private enum class MorphControl { Back, Search, Play, Shuffle, Reset }

private const val MorphMerge = 0.4f
private val MorphGap = 10.dp
private val MorphHeight = 56.dp

/** How long an armed reset waits for its confirming second tap. */
private const val ResetConfirmMillis = 3000L

/**
 * The size a control parks at, as a share of its full size. A little smaller rather than nothing:
 * coming out it grows the rest of the way — swelling into place instead of popping out of a speck —
 * and parked that much smaller it keeps clear of the edge of the control covering it, which inside
 * the merge would otherwise swell where the two outlines meet.
 */
private const val ParkedScale = 0.75f

/**
 * Back, search, play, shuffle and reset as **one body of glass** that reshapes itself with [level].
 *
 * All five are members of one group, so sliding out from under a neighbour is a liquid parting
 * rather than one pane passing another, and their colours — play in the accent, an armed reset in
 * the danger colour — blend through the bridges. Outside the Liquid look the group does not merge,
 * and the same morph runs with separate panes stacked in the same order.
 *
 * - **List** — the search field spans the row, with back and play parked under its two ends.
 * - **Item** — the field narrows and uncovers them; only the field moves.
 * - **Session** — back widens over the field, which parks under it; shuffle and reset come out from
 *   under back, and reset settles exactly over play, which never moves.
 *
 * The morph is two springs read in the layout phase, so it costs a relayout per frame and never a
 * recomposition. Arriving overshoots a little, which reads as liquid settling.
 */
@Composable
fun MorphingControls(
    level: MorphLevel,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    shuffleOn: Boolean,
    onToggleShuffle: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toItem = rememberLevelProgress(level != MorphLevel.List)
    val toSession = rememberLevelProgress(level == MorphLevel.Session)
    // Stable readers for the layout: reading the springs only there keeps the morph from recomposing.
    val toItemValue = remember(toItem) { { toItem.value } }
    val toSessionValue = remember(toSession) { { toSession.value } }
    val colors = LocalCatalogColors.current

    LiquidGlassGroup(modifier = modifier, merge = MorphMerge) {
        MorphingControlsLayout(toItemValue, toSessionValue, Modifier.fillMaxWidth()) {
            // Always five children, in MorphControl order: a control that is parked and staying
            // parked is an empty slot, so it costs nothing and cannot swell the edge covering it.
            MorphSlot(MorphControl.Back, toItem, toSession) { visibility ->
                LiquidButton(
                    onClick = onBack,
                    modifier = Modifier.hiddenWhen(level == MorphLevel.List),
                    touchHighlight = false,
                    stretching = true,
                ) {
                    GlyphIcon(Glyph.ChevronLeft, Modifier.fadeWith(visibility))
                    // Three units wide, a lone arrow looks lost; the words say where it goes.
                    AnimatedVisibility(
                        visible = level == MorphLevel.Session,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        CatalogText("Back to item", LabelStyle)
                    }
                }
            }
            MorphSlot(MorphControl.Search, toItem, toSession) { visibility ->
                val hidden = level == MorphLevel.Session
                LiquidSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = if (level == MorphLevel.List) "Search the list" else "Search",
                    modifier = Modifier
                        .focusProperties { canFocus = !hidden }
                        .hiddenWhen(hidden),
                    showIcon = level == MorphLevel.List,
                    contentAlpha = visibility,
                )
            }
            MorphSlot(MorphControl.Play, toItem, toSession) { visibility ->
                LiquidButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .hiddenWhen(level != MorphLevel.Item)
                        .semantics { contentDescription = "Start session" },
                    tint = colors.accent,
                    touchHighlight = false,
                    stretching = true,
                ) {
                    GlyphIcon(Glyph.Play, Modifier.fadeWith(visibility), color = Color.White)
                }
            }
            MorphSlot(MorphControl.Shuffle, toItem, toSession) { visibility ->
                LiquidButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .hiddenWhen(level != MorphLevel.Session)
                        .semantics { contentDescription = if (shuffleOn) "Shuffle on" else "Shuffle off" },
                    tint = if (shuffleOn) colors.accent else Color.Unspecified,
                    touchHighlight = false,
                    stretching = true,
                ) {
                    GlyphIcon(
                        Glyph.Shuffle,
                        Modifier.fadeWith(visibility),
                        color = if (shuffleOn) Color.White else colors.content,
                    )
                }
            }
            MorphSlot(MorphControl.Reset, toItem, toSession) { visibility ->
                ResetButton(level, onReset, visibility)
            }
        }
    }
}

/**
 * Starts over. It throws away progress, so a first tap only arms it — it turns the danger colour —
 * and a second one within [ResetConfirmMillis] resets.
 */
@Composable
private fun ResetButton(level: MorphLevel, onReset: () -> Unit, visibility: () -> Float) {
    val colors = LocalCatalogColors.current
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(ResetConfirmMillis)
            armed = false
        }
    }
    LiquidButton(
        onClick = {
            if (armed) {
                armed = false
                onReset()
            } else {
                armed = true
            }
        },
        modifier = Modifier
            .hiddenWhen(level != MorphLevel.Session)
            .semantics { contentDescription = if (armed) "Tap again to reset" else "Reset" },
        tint = if (armed) colors.danger else Color.Unspecified,
        touchHighlight = false,
        stretching = true,
    ) {
        GlyphIcon(Glyph.Reset, Modifier.fadeWith(visibility), color = if (armed) Color.White else colors.content)
    }
}

/** One child of the row: the control while it is on show or on its way, an empty slot otherwise. */
@Composable
private fun MorphSlot(
    control: MorphControl,
    toItem: Animatable<Float, AnimationVector1D>,
    toSession: Animatable<Float, AnimationVector1D>,
    content: @Composable (visibility: () -> Float) -> Unit,
) {
    // Derived, so this flips twice per morph rather than recomposing on every frame of it.
    val shown by remember(control, toItem, toSession) {
        derivedStateOf {
            morphVisibility(control, toItem.value, toSession.value) > 0f ||
                morphVisibility(control, toItem.targetValue, toSession.targetValue) > 0f
        }
    }
    val visibility = remember(control, toItem, toSession) {
        { morphVisibility(control, toItem.value, toSession.value) }
    }
    if (shown) content(visibility) else Spacer(Modifier)
}

/**
 * Lays out exactly five children along [morphEdges], each measured at its slot's width and the row's
 * height, both scaled by [morphScale], and centred in its slot. A layout size rather than a layer
 * scale on purpose: the group reads its members' geometry from layout.
 */
@Composable
private fun MorphingControlsLayout(
    toItem: () -> Float,
    toSession: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val rowHeight = MorphHeight.roundToPx()
        val rowWidth = constraints.maxWidth
        val item = toItem()
        val session = toSession()
        val edges = morphEdges(rowWidth.toFloat(), MorphGap.toPx(), item, session)

        val placeables = measurables.mapIndexed { index, measurable ->
            val scale = morphScale(morphVisibility(MorphControl.entries[index], item, session))
            val slotLeft = edges[index * 2]
            val slotRight = edges[index * 2 + 1]
            val inset = (slotRight - slotLeft) * (1f - scale) / 2f
            // Rounded at both edges rather than by width, so neighbours never open a one-pixel seam.
            val left = (slotLeft + inset).roundToInt()
            val right = (slotRight - inset).roundToInt()
            val placeable = measurable.measure(
                Constraints.fixed((right - left).coerceAtLeast(0), (rowHeight * scale).roundToInt()),
            )
            left to placeable
        }
        layout(rowWidth, rowHeight) {
            placeables.forEachIndexed { index, (left, placeable) ->
                placeable.place(
                    x = left,
                    y = (rowHeight - placeable.height) / 2,
                    zIndex = morphZIndex(MorphControl.entries[index], session),
                )
            }
        }
    }
}

/**
 * Left and right edge of every control, as `[back.left, back.right, search.left, …]`. The row is
 * shared out in units of `u`, a fifth of it after two gaps: 1 : 3 : 1 on an item (back, search,
 * play) and 3 : 1 : 1 in a session (back, shuffle, reset). Each segment is a straight interpolation,
 * so a spring overshooting either end carries the motion a little further and back — the wobble.
 */
private fun morphEdges(width: Float, gap: Float, toItem: Float, toSession: Float): FloatArray {
    val u = ((width - 2f * gap) / 5f).coerceAtLeast(0f)
    val end = width - u
    val list = floatArrayOf(0f, u, 0f, width, end, width, 0f, u, 0f, u)
    val item = floatArrayOf(0f, u, u + gap, 4f * u + gap, end, width, 0f, u, 0f, u)
    val session = floatArrayOf(0f, 3f * u, u + gap, 2f * u + gap, end, width, 3f * u + gap, 4f * u + gap, end, width)
    return FloatArray(list.size) { i ->
        list[i] + toItem * (item[i] - list[i]) + toSession * (session[i] - item[i])
    }
}

/** How much of a control is on show, `0` parked to `1` in use. */
private fun morphVisibility(control: MorphControl, toItem: Float, toSession: Float): Float =
    when (control) {
        MorphControl.Back -> toItem + toSession
        MorphControl.Search -> 1f - toSession
        MorphControl.Play -> toItem - toSession
        MorphControl.Shuffle, MorphControl.Reset -> toSession
    }.coerceIn(0f, 1f)

private fun morphScale(visibility: Float): Float = lerp(ParkedScale, 1f, visibility.coerceIn(0f, 1f))

/**
 * Which control covers which: on the way into an item the field lies over the buttons it uncovers;
 * into a session back lies over the field, shuffle and reset come out from under back — reset
 * behind shuffle — and reset slides over play.
 */
private fun morphZIndex(control: MorphControl, toSession: Float): Float =
    if (toSession > 0f) {
        when (control) {
            MorphControl.Back -> 5f
            MorphControl.Shuffle -> 4f
            MorphControl.Reset -> 3f
            MorphControl.Play -> 2f
            MorphControl.Search -> 1f
        }
    } else {
        when (control) {
            MorphControl.Search -> 5f
            MorphControl.Back, MorphControl.Play -> 3f
            MorphControl.Shuffle, MorphControl.Reset -> 1f
        }
    }

/**
 * How far the row is towards a level. Arriving overshoots a little; leaving is critically damped and
 * must be — below zero the parked controls would be carried out past the one covering them.
 */
@Composable
private fun rememberLevelProgress(reached: Boolean): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(if (reached) 1f else 0f) }
    LaunchedEffect(reached) {
        progress.animateTo(
            targetValue = if (reached) 1f else 0f,
            animationSpec =
                if (reached) spring(dampingRatio = 0.72f, stiffness = 320f)
                else spring(dampingRatio = 1f, stiffness = 380f),
        )
    }
    return progress
}

/** Fades a control's content by how much of the control is on show, in the draw phase. */
private fun Modifier.fadeWith(visibility: () -> Float): Modifier = graphicsLayer { alpha = visibility() }

/** A parked control is not there for accessibility services either. */
private fun Modifier.hiddenWhen(hidden: Boolean): Modifier = if (hidden) clearAndSetSemantics { } else this
