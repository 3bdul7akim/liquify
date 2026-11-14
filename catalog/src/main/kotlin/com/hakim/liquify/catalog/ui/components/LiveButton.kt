package com.hakim.liquify.catalog.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.hakim.liquify.GlassLook
import com.hakim.liquify.GlassShadow
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.liquify
import com.kyant.shapes.RoundedRectangle

private val LiveButtonSize = 48.dp
private val LiveShape = RoundedRectangle(24f.dp)

/** The glow's alpha at rest, and at the top of a breath. */
private const val LiveGlowAlpha = 0.26f
private const val LiveGlowBreathAlpha = 0.85f

/** One half of a breath. */
private const val BreathMillis = 900

/**
 * A "live" mark — a dot inside a ring — whose shadow breathes in the accent while [live].
 *
 * The breath is the glass's **press blend**, driven by a pulse rather than a finger: the two
 * materials differ only in the shadow's alpha, so Liquify blends it in the draw — one redraw per
 * frame, no recomposition, and neither the shadow's recording nor the glass's effects rebuilt. It
 * shows that a `pressedMaterial` is a general-purpose blend, not just a touch reaction.
 *
 * Only while [live]: a breath that never stops keeps the app rendering at the full refresh rate.
 */
@Composable
fun LiveButton(live: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val liquid = LiquifyTheme.look == GlassLook.Liquid
    val accent = LocalCatalogColors.current.accent

    // A plain Animatable rather than an infinite transition: cancelling this effect actually stops
    // the frame clock.
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(liquid, live) {
        // Only liquid glass casts the shadow the pulse breathes through.
        if (!liquid) return@LaunchedEffect
        if (!live) {
            if (pulse.value != 0f) pulse.animateTo(0f, tween(BreathMillis, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        // With system animations off every animateTo returns at once, which would make the loop
        // below a busy spin. Hold a steady glow instead.
        if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) {
            pulse.snapTo(0.6f)
            return@LaunchedEffect
        }
        while (true) {
            pulse.animateTo(1f, tween(BreathMillis, easing = FastOutSlowInEasing))
            pulse.animateTo(0f, tween(BreathMillis, easing = FastOutSlowInEasing))
        }
    }

    // Only the alpha breathes: the radius is part of the shadow's recording, the alpha is not.
    val base = LiquifyTheme.material
    val atRest = remember(base, accent) {
        base.copy(shadow = GlassShadow(radius = 10.dp, offset = DpOffset.Zero, color = accent, alpha = LiveGlowAlpha))
    }
    val breathing = remember(atRest) { atRest.copy(shadow = atRest.shadow?.copy(alpha = LiveGlowBreathAlpha)) }

    Box(
        modifier
            .size(LiveButtonSize)
            .liquify(
                shape = LiveShape,
                material = atRest,
                pressedMaterial = breathing,
                pressProgress = { pulse.value },
            )
            .semantics { contentDescription = if (live) "Live now" else "Jump to now" }
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(LiveButtonSize)) {
            val ringRadius = size.minDimension * 0.29f
            drawCircle(color = accent, radius = ringRadius, style = Stroke(width = 1.5f.dp.toPx()))
            drawCircle(color = accent, radius = ringRadius * 0.42f)
        }
    }
}
