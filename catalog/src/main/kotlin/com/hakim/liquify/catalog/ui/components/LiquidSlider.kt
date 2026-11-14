package com.hakim.liquify.catalog.ui.components

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.hakim.liquify.DampedDragAnimation
import com.hakim.liquify.GlassLook
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.CircularCapsule
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.layerBackdrop
import com.hakim.liquify.liquify
import com.hakim.liquify.rememberBackdrop
import com.hakim.liquify.rememberCombinedBackdrop
import com.hakim.liquify.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

private val ThumbWidth = 40f.dp
private val ThumbHeight = 24f.dp

/** The glass track: a hairline the thumb rides *on top of*, overhanging it at both ends. */
private val GlassTrackHeight = 6f.dp

/** The flat track: the thumb's own height plus 4 dp, so the thumb sits *inside* it. */
private val FlatTrackHeight = ThumbHeight + 4f.dp

/** What is left over on each side once the thumb is inside the flat track. */
private val FlatThumbInset = (FlatTrackHeight - ThumbHeight) / 2f

/** Half the width of the flat track's accent fade, as a share of the track's length. */
private const val FadeHalfWidth = 0.02f

/** Steps the fade is quantised to — see [AccentFade]. */
private const val FadeSteps = 512f

/**
 * A slider in two skins, chosen by the look in scope.
 *
 * **Liquid** is a hairline track with a thumb riding on top of it: the thumb swells under the finger,
 * turns from an opaque pill into a lens that magnifies the filled track — you read the value through
 * it at the moment you set it — grows a rim and an inner shadow, and stretches along its velocity.
 *
 * **Frosted** and **Solid** invert that: the track grows to the thumb's height plus 4 dp and the
 * thumb sits *inside* it, flat white, and the accent fades out over a short band instead of stopping
 * at a cut. Both skins share the motion: the thumb follows the finger and springs to a tapped spot.
 *
 * @param defaultValue the value a double tap on the thumb returns to, or `null` for none.
 */
@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    visibilityThreshold: Float = (valueRange.endInclusive - valueRange.start) / 1000f,
    defaultValue: Float? = null,
) {
    val liquid = LiquifyTheme.look == GlassLook.Liquid
    val colors = LocalCatalogColors.current
    val accentColor = colors.accent
    val trackColor = colors.track
    val thumb = knobMaterials(lensHeight = 10.dp, lensAmount = 14.dp)

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        val trackWidth = constraints.maxWidth

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        // The gesture callbacks below are built once, so they read these through updated state.
        val currentOnValueChange by rememberUpdatedState(onValueChange)
        val currentDefaultValue by rememberUpdatedState(defaultValue)
        val viewConfiguration = LocalViewConfiguration.current
        val taps = remember { TapCounter() }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = { taps.onDown() },
                onDragStopped = {
                    if (didDrag) currentOnValueChange(targetValue)
                    // Double tap on the thumb: back to the default. Recognised from the gesture the
                    // thumb already runs, so a drag followed by a quick touch never counts as one.
                    val reset = currentDefaultValue
                    val isDoubleTap = taps.onUp(
                        slop = viewConfiguration.touchSlop,
                        doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis,
                    )
                    if (reset != null && isDoubleTap) {
                        val target = reset.coerceIn(valueRange)
                        animateToValue(target)
                        currentOnValueChange(target)
                    }
                },
                onDrag = { _, dragAmount ->
                    taps.onMove(dragAmount)
                    if (!didDrag) didDrag = dragAmount.x != 0f
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    currentOnValueChange(
                        if (isLtr) (targetValue + delta).coerceIn(valueRange)
                        else (targetValue - delta).coerceIn(valueRange)
                    )
                }
            )
        }
        // The motion preset follows the theme, also while this control stays on screen.
        dampedDragAnimation.motion = LiquifyTheme.material.motion
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }.collectLatest {
                if (dampedDragAnimation.targetValue != it) dampedDragAnimation.updateValue(it)
            }
        }

        // Hoisted so both skins seek identically.
        val tapToSeek = Modifier.pointerInput(animationScope) {
            detectTapGestures { position ->
                val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                val target =
                    (if (isLtr) valueRange.start + delta
                    else valueRange.endInclusive - delta).coerceIn(valueRange)
                dampedDragAnimation.animateToValue(target)
                currentOnValueChange(target)
            }
        }

        // Only the glass thumb samples the track, so only then is it recorded into a layer.
        val trackHeight = if (liquid) GlassTrackHeight else FlatTrackHeight
        Box(if (liquid) Modifier.layerBackdrop(trackBackdrop) else Modifier) {
            Box(
                Modifier
                    .clip(CircularCapsule)
                    .background(trackColor)
                    .then(tapToSeek)
                    .height(trackHeight)
                    .fillMaxWidth()
            )

            if (liquid) {
                // A sibling laid out to the progress width: its end is a layout edge, so a hard one.
                Box(
                    Modifier
                        .clip(CircularCapsule)
                        .background(accentColor)
                        .height(trackHeight)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val width = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                            layout(width, placeable.height) { placeable.place(0, 0) }
                        }
                )
            } else {
                val accentFade = remember(accentColor, isLtr) { AccentFade(accentColor, isLtr) }
                val fadeFill: DrawScope.() -> Unit = remember(accentFade, dampedDragAnimation) {
                    { drawRect(accentFade.forProgress(dampedDragAnimation.progress)) }
                }
                Box(
                    Modifier
                        .clip(CircularCapsule)
                        .drawBehind(fadeFill)
                        .height(trackHeight)
                        .fillMaxWidth()
                )
            }
        }

        // The glass thumb overhangs the track by a quarter of itself at each end; the flat one is
        // held inside the track, with the same 2 dp of air at the sides as above and below.
        val thumbTranslation: GraphicsLayerScope.() -> Unit =
            if (liquid) {
                {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                            if (isLtr) 1f else -1f
                }
            } else {
                {
                    val inset = FlatThumbInset.toPx()
                    val travel = (trackWidth - size.width - inset * 2f).coerceAtLeast(0f)
                    translationX = (inset + travel * dampedDragAnimation.progress) * if (isLtr) 1f else -1f
                }
            }
        // The thumb refracts the track as well as the page behind, magnified while it is held.
        val thumbBackdrop = rememberCombinedBackdrop(
            LiquifyTheme.backdrop,
            rememberBackdrop(trackBackdrop) { drawTrack ->
                val progress = dampedDragAnimation.pressProgress
                scale(lerp(2f / 3f, 1f, progress), lerp(0f, 1f, progress)) { drawTrack() }
            }
        )

        val thumbSkin: Modifier =
            if (liquid) {
                Modifier.liquify(
                    shape = Capsule(),
                    backdrop = thumbBackdrop,
                    material = thumb.atRest,
                    pressedMaterial = thumb.pressed,
                    pressProgress = { dampedDragAnimation.pressProgress },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    }
                )
            } else {
                Modifier.background(colors.knob, CircularCapsule)
            }

        Box(
            Modifier
                .graphicsLayer(thumbTranslation)
                .then(dampedDragAnimation.modifier)
                .then(thumbSkin)
                .size(ThumbWidth, ThumbHeight)
        )
    }
}

/**
 * The flat track's accent, fading out at its leading edge instead of stopping at one.
 *
 * It fades to the accent at zero alpha rather than to [Color.Transparent], which is transparent
 * *black* and would drag the colour through grey on the way. The brush is quantised to [FadeSteps]
 * so one native gradient serves many frames of a drag while the edge still slides continuously.
 */
private class AccentFade(accent: Color, private val isLtr: Boolean) {

    private val opaque = accent
    private val clear = accent.copy(alpha = 0f)

    private var step = -1
    private var brush: Brush = build(0f)

    fun forProgress(progress: Float): Brush {
        val quantised = (progress.fastCoerceIn(0f, 1f) * FadeSteps).fastRoundToInt()
        if (quantised != step) {
            step = quantised
            brush = build(quantised / FadeSteps)
        }
        return brush
    }

    private fun build(progress: Float): Brush {
        val boundary = if (isLtr) progress else 1f - progress
        val from = (boundary - FadeHalfWidth).fastCoerceIn(0f, 1f)
        val to = (boundary + FadeHalfWidth).fastCoerceIn(0f, 1f)
        val leading = if (isLtr) opaque else clear
        val trailing = if (isLtr) clear else opaque
        return Brush.horizontalGradient(0f to leading, from to leading, to to trailing, 1f to trailing)
    }
}

/**
 * Tells taps from drags on the thumb and pairs taps into double taps. Travel is summed from the drag
 * deltas, so it measures the finger rather than the thumb, which moves with it.
 */
private class TapCounter {

    private var travel = 0f
    private var lastTapUptime = 0L

    fun onDown() {
        travel = 0f
    }

    fun onMove(dragAmount: Offset) {
        travel += abs(dragAmount.x) + abs(dragAmount.y)
    }

    /** Records the end of a press; `true` when it completed a double tap. */
    fun onUp(slop: Float, doubleTapTimeoutMillis: Long): Boolean {
        val now = SystemClock.uptimeMillis()
        if (travel > slop) {
            lastTapUptime = 0L
            return false
        }
        val isSecondTap = lastTapUptime != 0L && now - lastTapUptime <= doubleTapTimeoutMillis
        lastTapUptime = if (isSecondTap) 0L else now
        return isSecondTap
    }
}
