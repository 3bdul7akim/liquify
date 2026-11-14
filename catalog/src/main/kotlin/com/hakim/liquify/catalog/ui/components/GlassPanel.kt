package com.hakim.liquify.catalog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.hakim.liquify.GlassLook
import com.hakim.liquify.GlassMotion
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.colorBackdrop
import com.hakim.liquify.liquify
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch

private val PanelShape = RoundedRectangle(40f.dp)

/**
 * The spring a panel arrives on, for the motion preset in scope — quieter than a control's: a panel
 * is a window, not something under the finger, so the presets differ by a few hundredths of damping.
 */
private fun panelEntrySpring(motion: GlassMotion): SpringSpec<Float> = when (motion) {
    GlassMotion.Performance -> PanelEntryCalm
    GlassMotion.Default -> PanelEntryDefault
    GlassMotion.Bouncy -> PanelEntryLively
}

private val PanelEntryCalm = spring<Float>(dampingRatio = 0.86f, stiffness = 680f)
private val PanelEntryDefault = spring<Float>(dampingRatio = 0.8f, stiffness = 620f)
private val PanelEntryLively = spring<Float>(dampingRatio = 0.74f, stiffness = 580f)

/** How opaque a panel's plain fill is: enough to read on, with the page still a shadow behind it. */
private const val PanelOpacity = 0.9f

/**
 * A panel over the page: a scrim, and a centred pane that zooms in on a small spring and out on a
 * quick fade, drawn in the window so it shares the app's look and motion.
 *
 * The pane is **plain**: the sheet's fill at nine tenths and the material's rim, under
 * [GlassLook.Solid], so nothing under it is recorded, blurred or bent. A sheet of thick glass this
 * size — a heavy blur and a lens across the whole page, with every control inside refracting the
 * page and the panel again — was the most expensive pane on screen on exactly the frames it
 * animated in. The controls inside are glass of the app's own look again, standing on the panel's
 * colour: glass over one colour, which Liquify lays down without a layer or a shader. Solid keeps its
 * fill as it is.
 *
 * Every animated read happens in the draw phase, so neither entry nor exit recomposes per frame.
 */
@Composable
fun GlassPanel(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val entrySpring = panelEntrySpring(LiquifyTheme.material.motion)
    LaunchedEffect(Unit) { progress.animateTo(1f, entrySpring) }
    var dismissing by remember { mutableStateOf(false) }
    val dismiss: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            scope.launch {
                progress.animateTo(0f, tween(durationMillis = 150, easing = FastOutSlowInEasing))
                onDismiss()
            }
        }
    }
    BackHandler(onBack = dismiss)

    val scrim = LocalCatalogColors.current.scrim
    val appLook = LiquifyTheme.look
    val sheet = sheetTint()
    val panelColor = if (appLook == GlassLook.Solid) sheet else sheet.copy(alpha = PanelOpacity)
    val controlsBackdrop = colorBackdrop(panelColor.copy(alpha = 1f))
    // Only its rim is read: under the Solid look below the panel samples nothing.
    val panelMaterial = sheetMaterial()

    Box(Modifier.fillMaxSize()) {
        // A *sibling* of the panel, not its parent: it only ever sees taps that miss the panel.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(scrim.copy(alpha = scrim.alpha * progress.value.fastCoerceIn(0f, 1f))) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismiss,
                ),
        )

        // Taken here, in the box's scope: the theme below opens a scope of its own.
        val centred = Modifier.align(Alignment.Center)
        LiquifyTheme(look = GlassLook.Solid) {
            Column(
                centred
                    .fillMaxWidth()
                    .padding(24.dp)
                    // Claims the panel's area for hit testing without consuming anything, so a tap on
                    // empty panel background does not fall through to the scrim.
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
                    // The entry is the pane's own layer transform, in every look.
                    .liquify(
                        shape = PanelShape,
                        tint = panelColor,
                        material = panelMaterial,
                        layerBlock = {
                            val p = progress.value
                            val scale = lerp(0.6f, 1f, p)
                            scaleX = scale
                            scaleY = scale
                            alpha = p.fastCoerceIn(0f, 1f)
                        },
                    )
                    // Scroll before the padding, so a pressed control that leans past its box has the
                    // padding as clip headroom.
                    .verticalScroll(rememberScrollState())
                    .animateContentSize()
                    .padding(24.dp)
            ) {
                LiquifyTheme(backdrop = controlsBackdrop, look = appLook) {
                    content(dismiss)
                }
            }
        }
    }
}
