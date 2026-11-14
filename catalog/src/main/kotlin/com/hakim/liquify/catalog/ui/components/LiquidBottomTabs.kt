package com.hakim.liquify.catalog.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.hakim.liquify.DampedDragAnimation
import com.hakim.liquify.GlassInnerShadow
import com.hakim.liquify.GlassInteraction
import com.hakim.liquify.GlassLook
import com.hakim.liquify.GlassMaterial
import com.hakim.liquify.GlassRim
import com.hakim.liquify.GlassShadow
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.CircularCapsule
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.layerBackdrop
import com.hakim.liquify.liquify
import com.hakim.liquify.rememberCombinedBackdrop
import com.hakim.liquify.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * How close, in pages, a pager has to be to its destination to count as arrived. A spring never
 * quite finishes, and held until it did, the indicator stayed swollen long after it had plainly
 * arrived; at a hundredth and a half of a page it is a pixel or two from its slot.
 */
const val PageArrivalThreshold = 0.015f

/** Content scale for the tabs when nothing is allowed to grow. */
private val FlatTabScale: () -> Float = { 1f }

internal val LocalLiquidBottomTabScale = staticCompositionLocalOf { FlatTabScale }

/**
 * A tab bar whose selection indicator is a lens you can drag along the bar — in all three looks.
 *
 * Three stacked layers: the bar, a hidden copy of the tab row recorded as a backdrop and tinted with
 * the accent, and the indicator, which refracts that copy — so the icons genuinely change colour and
 * distort *through* the lens instead of being swapped for coloured variants. The hidden row and the
 * indicator each blend between a resting and a pressed material while the indicator is held.
 *
 * - **Liquid** is the full recipe: refraction on all three, a rim that fades in under the finger, a
 *   glow that follows it, the whole bar swelling on press and leaning when dragged past either end.
 * - **Frosted** keeps the *motion* and drops the *material*: no lens and nothing that changes size,
 *   but the indicator still glides and follows the finger. Blur is the one effect left, split across
 *   two clearly different radii so the selected tab still reads softer than the bar.
 * - **Solid** samples nothing and restacks: an opaque indicator over a tab would hide it, so the bar
 *   splits into surface → indicator → icons.
 *
 * @param pagePosition the pager's live position in pages (`currentPage + currentPageOffsetFraction`).
 *   When given, the indicator glides with a swipe between pages instead of jumping once it commits,
 *   stretching along its velocity as under a finger. A drag on the indicator always wins over it.
 * @param pageMoving whether the pager is still on its way — see [PageArrivalThreshold]. While it is,
 *   the indicator takes the pressed pose it has under a finger, and settles once the pager arrives.
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    pagePosition: (() -> Float)? = null,
    pageMoving: (() -> Boolean)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val look = LiquifyTheme.look
    val liquid = look == GlassLook.Liquid
    val samples = look != GlassLook.Solid
    val accentColor = LocalCatalogColors.current.accent
    val glass = tabBarGlass()

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount }

        val offsetAnimation = remember { Animatable(0f) }
        // The rubber-band lean past either end, Liquid only. A constant elsewhere, so the drag never
        // invalidates anything through it.
        val panelOffset by remember(density, liquid) {
            derivedStateOf {
                if (!liquid) {
                    0f
                } else {
                    val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                    with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) { mutableIntStateOf(selectedTabIndex()) }
        // Whether a finger is on the indicator; while it is, the pager position is ignored.
        val indicatorHeld = remember { mutableStateOf(false) }
        // Set when a drag on the indicator ends over another tab: the indicator is there already, so
        // it sits out the pager's scroll to that tab and picks the pager up again once it arrived.
        val followPaused = remember { mutableStateOf(false) }
        val followsPager = pagePosition != null
        val currentPagePosition by rememberUpdatedState(pagePosition)
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.0001f,
                initialScale = 1f,
                pressedScale = 80f / 52f,
                onDragStarted = { indicatorHeld.value = true },
                onDragStopped = {
                    indicatorHeld.value = false
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    if (followsPager) {
                        // Settle into the tab the finger chose, from where the finger left it; the
                        // pages scroll there on their own, and the indicator waits for them.
                        updateValue(targetIndex.toFloat())
                        val position = currentPagePosition?.invoke()
                        followPaused.value =
                            position != null && abs(position - targetIndex) > PageArrivalThreshold
                    } else {
                        animateToValue(targetIndex.toFloat())
                    }
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 100f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                }
            )
        }
        // The motion preset follows the theme, also while this control stays on screen.
        val motion = LiquifyTheme.material.motion
        dampedDragAnimation.motion = motion
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }.collectLatest { index -> currentIndex = index }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    // Following the pager, the indicator is already where the pager is going; a
                    // press-and-release here would swell it at the end of every swipe.
                    if (!followsPager) dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }
        if (pagePosition != null) {
            LaunchedEffect(dampedDragAnimation, pagePosition) {
                // The same spring the drag uses, fed from the pager: a swipe between pages and a
                // drag on the bar feel like one and the same motion.
                snapshotFlow { pagePosition() }.collect { position ->
                    if (!indicatorHeld.value && !followPaused.value) dampedDragAnimation.updateValue(position)
                }
            }
        }
        if (pageMoving != null) {
            LaunchedEffect(dampedDragAnimation, pageMoving) {
                snapshotFlow { pageMoving() }.collect { moving ->
                    if (indicatorHeld.value) return@collect
                    if (moving) {
                        if (!followPaused.value) dampedDragAnimation.press()
                    } else {
                        if (followPaused.value) {
                            followPaused.value = false
                            currentPagePosition?.let { dampedDragAnimation.updateValue(it()) }
                        }
                        dampedDragAnimation.release()
                    }
                }
            }
        }

        // One glow for the whole bar, positioned at the indicator, so the panel lights up from
        // wherever it sits. Liquid only, like every touch reaction.
        val interaction = remember(animationScope) {
            GlassInteraction(
                animationScope = animationScope,
                // The indicator's own drag consumes the movement; the glow follows it all the same.
                tracksConsumedDrags = true,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }
        interaction.motion = motion
        val touchGlow = if (liquid) interaction.modifier else Modifier
        val touchGlowGesture = if (liquid) interaction.gestureModifier else Modifier

        val pressProgress = remember(dampedDragAnimation) { { dampedDragAnimation.pressProgress } }
        val panelSway = if (liquid) Modifier.graphicsLayer { translationX = panelOffset } else Modifier
        val panelSwell: (GraphicsLayerScope.() -> Unit)? =
            if (liquid) {
                {
                    val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, dampedDragAnimation.pressProgress)
                    scaleX = scale
                    scaleY = scale
                }
            } else {
                null
            }
        val panelSkin = Modifier.liquify(shape = Capsule(), material = glass.panel, layerBlock = panelSwell)

        // Sampling, the bar *is* the tab row and the translucent indicator lands on top of it. A
        // solid indicator would hide the tab it marks, so there the bar splits in two.
        if (samples) {
            Row(
                Modifier
                    .then(panelSway)
                    .then(panelSkin)
                    .then(touchGlow)
                    .height(68f.dp)
                    .fillMaxWidth()
                    .padding(4f.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        } else {
            Box(
                Modifier
                    .then(panelSkin)
                    .height(68f.dp)
                    .fillMaxWidth()
            )
        }

        // The tab under the lens grows with the press, Liquid only — elsewhere a constant the tabs can
        // read without ever invalidating.
        val glassTabScale: () -> Float = remember(dampedDragAnimation) {
            { lerp(1f, 1.3f, dampedDragAnimation.pressProgress) }
        }
        val tabScale: () -> Float = if (liquid) glassTabScale else FlatTabScale

        // This row exists only to be recorded into `tabsBackdrop`. Under Solid nothing samples that
        // layer, so it is not composed at all — a second layout and draw of every tab, saved.
        if (samples) CompositionLocalProvider(LocalLiquidBottomTabScale provides tabScale) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .padding(horizontal = 2f.dp)
                    .layerBackdrop(tabsBackdrop)
                    .then(panelSway)
                    .liquify(
                        shape = Capsule(),
                        material = glass.tabs,
                        pressedMaterial = glass.tabsPressed,
                        pressProgress = pressProgress
                    )
                    .then(touchGlow)
                    .height(61f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 2f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        // The press scale and the velocity stretch, Liquid only. The pill still *moves* in every look.
        val indicatorStretch: (GraphicsLayerScope.() -> Unit)? =
            if (liquid) {
                {
                    scaleX = dampedDragAnimation.scaleX
                    scaleY = dampedDragAnimation.scaleY
                    val velocity = dampedDragAnimation.velocity / 7.5f
                    scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                    scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                }
            } else {
                null
            }

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(touchGlowGesture)
                .then(dampedDragAnimation.modifier)
                .liquify(
                    shape = Capsule(),
                    backdrop = rememberCombinedBackdrop(LiquifyTheme.backdrop, tabsBackdrop),
                    material = glass.indicator,
                    pressedMaterial = glass.indicatorPressed,
                    pressProgress = pressProgress,
                    layerBlock = indicatorStretch
                )
                .height(61f.dp)
                .fillMaxWidth(1f / tabsCount)
        )

        // The other half of the split: the tabs, drawn last so they sit on the solid pill.
        if (!samples) {
            Row(
                Modifier
                    .height(68f.dp)
                    .fillMaxWidth()
                    .padding(4f.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(CircularCapsule)
            .clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scale = scale()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/** The bar's glass, the hidden tab row's and the indicator's — pressed too, where they react to it. */
@Immutable
private class TabBarGlass(
    val panel: GlassMaterial,
    val tabs: GlassMaterial,
    val tabsPressed: GlassMaterial?,
    val indicator: GlassMaterial,
    val indicatorPressed: GlassMaterial?,
)

/**
 * The bar's three layers for the look in scope, cut from the theme's material.
 *
 * Liquid: the bar and the tabs layer keep one fixed refraction — they are long and flat, and the
 * size rule would hand them the band of a 68 dp pane — and the indicator goes from a faint wash to a
 * lens with the material's rim, a shadow and a recessed inner shadow. They keep their own dome too —
 * a flat bar under a domed indicator — where every other pane takes the material's; the colour fringe
 * is the material's for them as well. Frosted: one blur for the bar,
 * a far softer one for the tabs layer, and a flat accent wash for the indicator. Solid: the fills.
 */
@Composable
private fun tabBarGlass(): TabBarGlass {
    val look = LiquifyTheme.look
    val base = LiquifyTheme.material
    val colors = LocalCatalogColors.current
    return remember(look, base, colors) {
        val rim = base.rim ?: GlassRim.Default
        when (look) {
            GlassLook.Liquid -> {
                val panel = base.copy(
                    blurRadius = 2.dp,
                    refractionBySize = false,
                    refractionHeight = 20.dp,
                    refractionAmount = 30.dp,
                    saturation = 1.5f,
                    depthEffect = false,
                    tint = colors.tabContainer,
                    shadow = GlassShadow.Default,
                    innerShadow = null,
                )
                val tabs = panel.copy(
                    // The tab seen through the indicator stays softer than the bar around it.
                    blurRadius = 8.dp,
                    refractionHeight = 0.dp,
                    refractionAmount = 0.dp,
                    rim = null,
                    shadow = null,
                )
                val indicator = base.copy(
                    blurRadius = 0.dp,
                    saturation = 1f,
                    refractionBySize = false,
                    refractionHeight = 0.dp,
                    refractionAmount = 0.dp,
                    depthEffect = true,
                    tint = colors.tabIndicator,
                    rim = null,
                    shadow = null,
                    innerShadow = null,
                )
                TabBarGlass(
                    panel = panel,
                    tabs = tabs,
                    tabsPressed = tabs.copy(refractionHeight = 14.dp, refractionAmount = 28.dp, rim = rim),
                    indicator = indicator,
                    indicatorPressed = indicator.copy(
                        refractionHeight = 12.dp,
                        refractionAmount = 30.dp,
                        tint = Color.Black.copy(alpha = 0.05f),
                        rim = rim,
                        shadow = GlassShadow.Default,
                        innerShadow = GlassInnerShadow(radius = 8.dp),
                    ),
                )
            }

            GlassLook.Frosted -> {
                val bar = base.copy(
                    blurRadius = 16.dp,
                    saturation = 1f,
                    tint = colors.frostedTint,
                    shadow = null,
                    innerShadow = null,
                )
                TabBarGlass(
                    panel = bar,
                    // Thinner than the bar: an opaque fill would hide the softness the heavier blur
                    // is there to show off.
                    tabs = bar.copy(
                        blurRadius = 44.dp,
                        rim = null,
                        tint = colors.frostedTint.copy(alpha = colors.frostedTint.alpha * 0.55f),
                    ),
                    tabsPressed = null,
                    // Translucent, so the softer layer underneath still shows through it.
                    indicator = bar.copy(blurRadius = 0.dp, tint = colors.accent.copy(alpha = 0.32f)),
                    indicatorPressed = null,
                )
            }

            GlassLook.Solid -> {
                val bar = base.copy(tint = colors.solidTint, shadow = null, innerShadow = null)
                TabBarGlass(
                    panel = bar,
                    tabs = bar,
                    tabsPressed = null,
                    indicator = bar.copy(tint = colors.accent.copy(alpha = 0.22f), rim = null),
                    indicatorPressed = null,
                )
            }
        }
    }
}
