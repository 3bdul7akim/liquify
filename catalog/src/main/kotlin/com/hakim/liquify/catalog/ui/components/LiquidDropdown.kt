package com.hakim.liquify.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.hakim.liquify.GlassLook
import com.hakim.liquify.GlassMaterial
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.MotionBlur
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.Glyph
import com.hakim.liquify.catalog.ui.GlyphIcon
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.liquify
import com.hakim.liquify.materialize
import com.hakim.liquify.motionBlur
import com.hakim.liquify.rememberMotionBlur
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2

/** Size of the collapsed trigger — and therefore of the drop that leaves it. */
val DropdownTriggerSize: Dp = 44.dp

/** How small the drop pulls itself in to while it travels. */
private val DotSize = 30.dp
private val PanelWidth = 250.dp
private val PanelRowHeight = 50.dp

/** Air between an anchor and a menu that opens above it. */
private val AboveGap = 12.dp

/**
 * One shape for the whole animation. A 22 dp corner is clamped to half the short side, so this *is*
 * a circle on the trigger and on the drop, and the menu's corner once the box has grown: the shape
 * morphs purely because the element resizes, and nothing is allocated per frame.
 */
private val PanelShape: Shape = RoundedRectangle(22f.dp)

/** Peak speed of the flight spring, in progress units per second — what the stretch is scaled by. */
private const val FlightPeakSpeed = 15f

/** How far the drop elongates along its direction of travel at full speed. */
private const val MaxStretch = 0.75f

/**
 * How far into the flight the pane starts growing. Short of `1` on purpose: a drop of liquid does
 * not land and *then* spread, it is already spreading as it decelerates.
 */
private const val ExpandStartsAt = 0.7f

/**
 * How much of the growth the stretch is released across — gone before the box is recognisably a
 * box, since a rounded square turned 135° is a diamond.
 */
private const val StretchReleaseWindow = 0.2f

/**
 * Room around the drop inside the motion-blur layer: a render effect rasterises its layer at the
 * layer's bounds, and the rim reaches past the outline. Only paid while something moves.
 */
private val MotionSlack = 40.dp

/** Where the open menu sits relative to the element that opened it. */
enum class DropdownPlacement {
    /** Below, flush with the anchor's end edge. */
    BelowEnd,

    /** Above, flush with the anchor's end edge, a small gap over it — for anchors near the bottom. */
    AboveEnd,
}

/**
 * Shared state between an anchor (a trigger, a FAB) and [LiquidDropdownOverlay]. The split is what
 * lets the menu refract the real page: an overlay *sibling* of the page's content layer can sample
 * it, where glass inside that layer could not sample the layer it is part of.
 */
@Stable
class LiquidDropdownState internal constructor(
    internal val placement: DropdownPlacement,
    internal val startSize: Dp,
    internal val hidesAnchor: Boolean,
) {
    var expanded by mutableStateOf(false)
        private set

    /** True while the overlay owns the drop; a hiding anchor turns invisible for that time. */
    internal var active by mutableStateOf(false)

    internal val iconAlpha = Animatable(1f)

    /** `0` = the drop sitting on the anchor, `1` = arrived at the centre of the box to come. */
    internal val dropProgress = Animatable(0f)

    /** `0` = still the travelling drop, `1` = grown to the full menu. */
    internal val expandProgress = Animatable(0f)

    /** Live anchor bounds in root coordinates (a plain field — written on every layout). */
    internal var anchorBoundsLive = Rect.Zero

    /** Anchor snapshot taken at open time; every position below is derived from it. */
    internal var anchor by mutableStateOf(Rect.Zero)

    /** Overlay origin in root coordinates; snapshot state, because the draw phase reads it. */
    internal var overlayOrigin by mutableStateOf(Offset.Zero)

    fun open() {
        anchor = anchorBoundsLive
        expanded = true
    }

    fun close() {
        expanded = false
    }

    fun toggle() = if (expanded) close() else open()
}

/**
 * @param placement where the menu opens relative to its anchor.
 * @param startSize the drop's size as it leaves the anchor.
 * @param hidesAnchor whether the anchor *becomes* the drop (the trigger, the same glass circle) or
 *   stays where it is while a drop lifts out of it (a FAB).
 */
@Composable
fun rememberLiquidDropdownState(
    placement: DropdownPlacement = DropdownPlacement.BelowEnd,
    startSize: Dp = DropdownTriggerSize,
    hidesAnchor: Boolean = true,
): LiquidDropdownState = remember { LiquidDropdownState(placement, startSize, hidesAnchor) }

/** Marks this element as the one the dropdown opens from. */
fun Modifier.liquidDropdownAnchor(state: LiquidDropdownState): Modifier =
    onGloballyPositioned { state.anchorBoundsLive = it.boundsInRoot() }

/**
 * The collapsed control: a bare glass circle with an arrow in it. While the menu is open it is
 * invisible — the overlay's drop took its place — and it reappears once the drop has flown back.
 */
@Composable
fun LiquidDropdownTrigger(state: LiquidDropdownState, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(DropdownTriggerSize)
            .liquidDropdownAnchor(state)
            .graphicsLayer { alpha = if (state.active) 0f else 1f }
            .liquify(
                shape = CircleShape,
                // The drop's own fill, so nothing changes colour at the hand-over.
                tint = sheetTint(),
                dragging = true,
                stretching = true,
                interactiveHighlight = true,
            )
            .semantics { contentDescription = if (state.expanded) "Collapse" else "Expand" }
            .clickable(interactionSource = null, indication = null, role = Role.Button) { state.toggle() },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            Glyph.ChevronDown,
            Modifier.graphicsLayer { alpha = state.iconAlpha.value },
            size = 24.dp,
            scale = 0.8f,
        )
    }
}

/**
 * The floating half: a scrim, and the one piece of glass that is the drop *and* the menu.
 *
 * 1. a hiding anchor's arrow fades out, so what leaves is a bare drop of glass,
 * 2. the drop flies to the centre of the box about to appear — stretching along its travel, pulling
 *    itself in, its blur and lens calmed by its speed and smeared by a motion blur,
 * 3. **before** it has arrived it is already spreading into the menu, the stretch releasing as the
 *    box grows, which is what a drop of liquid does when it lands,
 * 4. the rows condense out of the material ([materialize]).
 *
 * Closing reverses it exactly. One element travels, deforms and grows, so the refraction and the
 * rim are continuous the whole way. Compose it as a **sibling** of the page's content layer and
 * above everything else, so its scrim blocks the whole page while the menu is open.
 *
 * @param selectedIndex the option to mark as current, or `-1` for an action menu.
 */
@Composable
fun LiquidDropdownOverlay(
    state: LiquidDropdownState,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.expanded) {
        if (state.expanded) {
            // Awaited: the arrow has to be gone before the shape is handed over, or it would blink.
            if (state.hidesAnchor) state.iconAlpha.animateTo(0f, tween(80))
            state.active = true
            // Launched, so the growth can start *on top of* the flight. Slightly underdamped: the
            // overshoot puts reverse velocity into the last frames, and the stretch follows velocity.
            launch { state.dropProgress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 1100f)) }
            snapshotFlow { state.dropProgress.value }.first { it >= ExpandStartsAt }
            state.expandProgress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 800f))
        } else if (state.active) {
            val collapse = launch {
                state.expandProgress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 1100f))
            }
            snapshotFlow { state.expandProgress.value }.first { it <= 1f - ExpandStartsAt }
            state.dropProgress.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 1100f))
            // Joined before the hand-back, so the anchor never reappears under a half-open pane.
            collapse.join()
            state.active = false
            if (state.hidesAnchor) state.iconAlpha.animateTo(1f, tween(80))
        }
    }

    if (!state.active) return

    BackHandler(enabled = state.expanded) { state.close() }

    val accent = LocalCatalogColors.current.accent
    val dropColor = sheetTint()
    // Rows have a fixed height, so the open menu's size is arithmetic rather than a measurement.
    val panelHeight = PanelRowHeight * options.size

    // One object, used twice: the blur of the flight and the calming of the glass during it.
    val motion = rememberMotionBlur(maxRadius = 10.dp, fullSpeed = 75f) {
        state.dropProgress.value + state.expandProgress.value
    }
    val material = sheetMaterial()
    val calmMaterial = remember(material) { material.calmed() }

    val density = LocalDensity.current
    val travelAngle = remember(state.anchor, panelHeight, density) {
        with(density) {
            val (start, end) = flightEnds(state, PanelWidth.toPx(), panelHeight.toPx(), AboveGap.toPx())
            Math.toDegrees(atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())).toFloat()
        }
    }

    // Where the drop is, draw-phase only. The *outer* layer carries the travel, so the sampling
    // region moves with the element and the drop refracts whatever is underneath it right now.
    val flightTransform: GraphicsLayerScope.() -> Unit = remember(state, panelHeight) {
        {
            val (start, end) = flightEnds(state, PanelWidth.toPx(), panelHeight.toPx(), AboveGap.toPx())
            val origin = state.overlayOrigin
            val d = state.dropProgress.value
            translationX = lerp(start.x, end.x, d) - origin.x - size.width * 0.5f
            translationY = lerp(start.y, end.y, d) - origin.y - size.height * 0.5f
            // A drop lifting out of an anchor that stays put fades in as it leaves.
            if (!state.hidesAnchor) alpha = (d * 4f).fastCoerceIn(0f, 1f)
        }
    }

    // The stretch, in `layerBlock`: liquify inverts it before sampling, so the glass deforms while
    // the backdrop behind it stays put — a drop pulled out of shape, not a magnified picture.
    val stretchTransform: GraphicsLayerScope.() -> Unit = remember(state, travelAngle) {
        {
            val release = (1f - state.expandProgress.value / StretchReleaseWindow).fastCoerceIn(0f, 1f)
            if (release > 0f) {
                val speed = (state.dropProgress.velocity / FlightPeakSpeed).fastCoerceIn(-1f, 1f)
                val stretch = abs(speed) * MaxStretch * release
                if (stretch > 0f) {
                    rotationZ = travelAngle
                    scaleX = 1f + stretch
                    // Reciprocal, so it reads as the same volume of liquid.
                    scaleY = 1f / (1f + stretch)
                }
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { state.overlayOrigin = it.positionInRoot() },
    ) {
        // Blocks the page while open; a tap outside dismisses.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    state.close()
                },
        )

        EmergingDrop(
            state = state,
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            material = material,
            calmMaterial = calmMaterial,
            motion = motion,
            flightTransform = flightTransform,
            stretchTransform = stretchTransform,
            dropColor = dropColor,
            accent = accent,
            panelHeight = panelHeight,
        )
    }
}

/** Start (the anchor's centre) and end (the open menu's centre) of the flight, in root pixels. */
private fun flightEnds(
    state: LiquidDropdownState,
    panelWidthPx: Float,
    panelHeightPx: Float,
    aboveGapPx: Float,
): Pair<Offset, Offset> {
    val anchor = state.anchor
    val endX = anchor.right - panelWidthPx * 0.5f
    val endY = when (state.placement) {
        DropdownPlacement.BelowEnd -> anchor.top + panelHeightPx * 0.5f
        DropdownPlacement.AboveEnd -> anchor.top - aboveGapPx - panelHeightPx * 0.5f
    }
    return anchor.center to Offset(endX, endY)
}

/**
 * The drop itself: one element that shrinks as it travels and then grows into the menu. Its size is
 * a *layout* input — the band and the rim follow the laid-out geometry, where a layer scale would
 * magnify the picture — resolved in a layout modifier, so the flight recomposes nothing.
 */
@Composable
private fun EmergingDrop(
    state: LiquidDropdownState,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    material: GlassMaterial,
    calmMaterial: GlassMaterial,
    motion: MotionBlur,
    flightTransform: GraphicsLayerScope.() -> Unit,
    stretchTransform: GraphicsLayerScope.() -> Unit,
    dropColor: Color,
    accent: Color,
    panelHeight: Dp,
) {
    // The choreography survives the Solid look; only the motion blur and the pane it smears go.
    val samples = LiquifyTheme.look != GlassLook.Solid
    val slack = if (samples) MotionSlack else 0.dp
    val sizing = Modifier.layout { measurable, _ ->
        // The flight's overshoot is a position overshoot, so the shrink is clamped; the growth may
        // pass 1 so the spring's overshoot reaches the glass as a real swell.
        val flight = state.dropProgress.value.fastCoerceIn(0f, 1f)
        val expand = state.expandProgress.value.fastCoerceIn(0f, 1.1f)
        val travelling = lerp(state.startSize.toPx(), DotSize.toPx(), flight)
        val slackPx = slack.toPx() * 2f
        val width = (lerp(travelling, PanelWidth.toPx(), expand) + slackPx).fastRoundToInt()
        val height = (lerp(travelling, panelHeight.toPx(), expand) + slackPx).fastRoundToInt()
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    // *Before* liquify: a liquify node draws its glass and then its content, so a blur placed after
    // it would wrap the rows only. From out here the layer holds the finished pane.
    val flightBlur = if (samples) Modifier.motionBlur(motion).padding(MotionSlack) else Modifier

    Box(
        Modifier
            .graphicsLayer(flightTransform)
            .then(sizing)
            .then(flightBlur)
            .liquify(
                shape = PanelShape,
                tint = dropColor,
                material = material,
                pressedMaterial = calmMaterial,
                pressProgress = { motion.intensity },
                layerBlock = stretchTransform,
            ),
        // Centred, because the box grows about its centre — the rows stay put.
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                // Laid out once at the menu's final size; the box clips the overflow.
                .requiredWidth(PanelWidth)
                // Condenses out of the material, and late: nothing shows until a third open.
                .materialize({ ((state.expandProgress.value - 0.35f) / 0.65f).fastCoerceIn(0f, 1f) }),
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(PanelRowHeight)
                        .clickable(interactionSource = null, indication = null, role = Role.Button) {
                            onSelect(index)
                            state.close()
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CatalogText(
                        label,
                        if (isSelected) LabelStyle.copy(color = accent) else LabelStyle,
                        Modifier.weight(1f),
                    )
                    if (isSelected) GlyphIcon(Glyph.Check, color = accent, size = 18.dp)
                }
            }
        }
    }
}
