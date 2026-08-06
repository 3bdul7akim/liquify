package com.hakim.liquify.catalog.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.hakim.liquify.Backdrop
import com.hakim.liquify.backdrops.layerBackdrop
import com.hakim.liquify.backdrops.rememberBackdrop
import com.hakim.liquify.backdrops.rememberCombinedBackdrop
import com.hakim.liquify.backdrops.rememberLayerBackdrop
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.effects.blur
import com.hakim.liquify.effects.lens
import com.hakim.liquify.highlight.Highlight
import com.hakim.liquify.interaction.rememberDampedDragAnimation
import com.hakim.liquify.liquify
import com.hakim.liquify.shadow.InnerShadow
import com.hakim.liquify.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest

/**
 * A toggle whose knob is an actual lens.
 *
 * At rest the knob blurs its backdrop, so it reads as a frosted pill. While held it swaps blur for
 * refraction and swells — you can see the track colour bend through it. The knob is also draggable,
 * not just tappable, and it follows the finger with the same damped physics as the slider.
 */
@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val colors = LocalCatalogColors.current
    val accentColor = colors.toggleOn
    val trackColor = colors.track

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }

    val dampedDragAnimation = rememberDampedDragAnimation(
        initialValue = fraction,
        valueRange = 0f..1f,
        pressedScale = 1.5f,
        onDragStopped = {
            if (didDrag) {
                fraction = if (targetValue >= 0.5f) 1f else 0f
                onSelect(fraction == 1f)
                didDrag = false
            } else {
                fraction = if (selected()) 0f else 1f
                onSelect(fraction == 1f)
            }
        },
        onDrag = { _, dragAmount ->
            if (!didDrag) didDrag = dragAmount.x != 0f
            val delta = dragAmount.x / dragWidth
            fraction =
                if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                else (fraction - delta).fastCoerceIn(0f, 1f)
        }
    )
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }.collectLatest { dampedDragAnimation.updateValue(it) }
    }
    LaunchedEffect(selected) {
        snapshotFlow { selected() }.collectLatest { isSelected ->
            val target = if (isSelected) 1f else 0f
            if (target != fraction) {
                fraction = target
                dampedDragAnimation.animateToValue(target)
            }
        }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
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
                .semantics { role = Role.Switch }
                .then(dampedDragAnimation.modifier)
                .liquify(
                    shape = { Capsule() },
                    // The knob refracts the track underneath it as well as the page behind, which
                    // is what makes the accent colour visibly bend as it slides.
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            scale(lerp(2f / 3f, 0.75f, progress), lerp(0f, 0.75f, progress)) {
                                drawBackdrop()
                            }
                        }
                    ),
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            5f.dp.toPx() * progress,
                            10f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = { Shadow(radius = 4f.dp, color = colors.controlShadow) },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 4f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        // Stretch along travel, pinch across it — the knob behaves like a droplet
                        // being flicked rather than a box being moved.
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        drawRect(colors.knob.copy(alpha = 1f - dampedDragAnimation.pressProgress))
                    }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}
