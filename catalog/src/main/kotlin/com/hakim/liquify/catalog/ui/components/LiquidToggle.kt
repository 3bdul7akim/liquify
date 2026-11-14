package com.hakim.liquify.catalog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.hakim.liquify.GlassLook
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.CircularCapsule
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.layerBackdrop
import com.hakim.liquify.liquify
import com.hakim.liquify.rememberBackdrop
import com.hakim.liquify.rememberCombinedBackdrop
import com.hakim.liquify.rememberDampedDragAnimation
import com.hakim.liquify.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/**
 * A switch whose knob is an actual lens.
 *
 * Under the **Liquid** look the knob swells under the finger, turns from an opaque pill into a lens
 * you can read the track through, grows a rim and an inner shadow as it is pressed, and stretches
 * along its own velocity — two materials ([knobMaterials]) Liquify blends between by the press, so
 * nothing here animates by hand.
 *
 * Under **Frosted** and **Solid** the knob is flat white, and nothing about it reacts to being held:
 * a lens that refracts nothing would only be an empty outline. It still slides, and it still follows
 * the finger.
 */
@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val liquid = LiquifyTheme.look == GlassLook.Liquid
    val colors = LocalCatalogColors.current
    val accentColor = colors.toggleOn
    val trackColor = colors.track
    val knob = knobMaterials(lensHeight = 5.dp, lensAmount = 10.dp)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    // The drag animation keeps the callbacks it was built with, so it calls through these: a caller
    // handing in fresh lambdas over fresh state would otherwise be written past.
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    var didDrag by remember { mutableStateOf(false) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }

    val dampedDragAnimation = rememberDampedDragAnimation(
        initialValue = fraction,
        valueRange = 0f..1f,
        pressedScale = 1.5f,
        onDragStarted = {
            didDrag = false
            dragDistance = 0f
        },
        onDragStopped = {
            if (didDrag) {
                fraction = if (targetValue >= 0.5f) 1f else 0f
                currentOnSelect(fraction == 1f)
                didDrag = false
            } else {
                fraction = if (currentSelected()) 0f else 1f
                currentOnSelect(fraction == 1f)
            }
        },
        onDrag = { _, dragAmount ->
            // A finger never taps perfectly still: only sideways travel past the touch slop makes
            // this a drag, or a wobbly tap would settle the knob back where it started.
            if (!didDrag) {
                dragDistance += abs(dragAmount.x)
                didDrag = dragDistance > touchSlop
            }
            val delta = dragAmount.x / dragWidth
            fraction =
                if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                else (fraction - delta).fastCoerceIn(0f, 1f)
        }
    )
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }.collectLatest { dampedDragAnimation.updateValue(it) }
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentSelected() }.collectLatest { isSelected ->
            val target = if (isSelected) 1f else 0f
            if (target != fraction) {
                fraction = target
                dampedDragAnimation.animateToValue(target)
            }
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    // The knob refracts the track underneath it as well as the page behind, which is what makes the
    // accent colour visibly bend as it slides.
    val knobBackdrop = rememberCombinedBackdrop(
        LiquifyTheme.backdrop,
        rememberBackdrop(trackBackdrop) { drawTrack ->
            val progress = dampedDragAnimation.pressProgress
            scale(lerp(2f / 3f, 0.75f, progress), lerp(0f, 0.75f, progress)) { drawTrack() }
        }
    )

    val knobSkin: Modifier =
        if (liquid) {
            Modifier.liquify(
                shape = Capsule(),
                backdrop = knobBackdrop,
                material = knob.atRest,
                pressedMaterial = knob.pressed,
                pressProgress = { dampedDragAnimation.pressProgress },
                layerBlock = {
                    scaleX = dampedDragAnimation.scaleX
                    scaleY = dampedDragAnimation.scaleY
                    // Stretch along travel, pinch across it — a droplet being flicked rather than a
                    // box being moved.
                    val velocity = dampedDragAnimation.velocity / 50f
                    scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                    scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                }
            )
        } else {
            Modifier.background(colors.knob, CircularCapsule)
        }

    // Read in composition so the announced state follows the real one; it changes rarely enough.
    val isOn = selected()

    Box(
        modifier
            // One node for TalkBack: "Switch, on — double tap to toggle". Taps and drags on the knob
            // are the drag animation's, not a clickable's, so nothing is doubled.
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                toggleableState = ToggleableState(isOn)
                onClick {
                    onSelect(!isOn)
                    true
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                // Only the glass knob refracts the track, so only then is the track recorded.
                .then(if (liquid) Modifier.layerBackdrop(trackBackdrop) else Modifier)
                .clip(CircularCapsule)
                .drawBehind { drawRect(lerp(trackColor, accentColor, dampedDragAnimation.value)) }
                .size(64f.dp, 28f.dp)
        )

        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, dampedDragAnimation.value)
                        else lerp(-padding, -(padding + dragWidth), dampedDragAnimation.value)
                }
                .then(dampedDragAnimation.modifier)
                .then(knobSkin)
                .size(40f.dp, 24f.dp)
        )
    }
}
