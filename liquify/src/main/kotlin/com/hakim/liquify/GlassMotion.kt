package com.hakim.liquify

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset

/**
 * How glass moves under the finger: how far it leans, how much it swells and stretches, and how
 * much it wobbles on the way back.
 *
 * One setting for every moving part the library owns — the lean and stretch of `liquify`, the press
 * swell, merged members, and the springs of [DampedDragAnimation] behind sliders, toggles and tab
 * indicators. It is carried by the material, so an app states it once:
 *
 * ```
 * LiquifyTheme(material = GlassMaterial.Regular.copy(motion = GlassMotion.Bouncy)) { App() }
 * ```
 *
 * The presets are moods of one material, not three materials: sizes differ by a few per cent — enough
 * to notice side by side, never enough to make one look like another control. A resting pane looks
 * the same under all three.
 */
public enum class GlassMotion {

    /**
     * Calm: springs that barely overshoot and settle quickly, a slightly shorter lean and a slightly
     * smaller swell. Also the cheapest — every frame a spring is still moving is a frame the glass is
     * rendered again.
     */
    Performance,

    /** The library's own motion. */
    Default,

    /**
     * Livelier: springs that overshoot a little further and wobble once more on the way back, a lean
     * that follows the finger a little further, and a stretch that pinches the other axis in a touch.
     */
    Bouncy,
}

/**
 * The numbers behind one [GlassMotion] preset.
 *
 * The springs are built once per preset rather than per press: a [SpringSpec] is immutable, and a
 * press is exactly when nothing should be allocated.
 */
internal class MotionSpec(
    // GlassInteraction: the press swell and the snap back of the lean after release.
    val pressSpec: SpringSpec<Float>,
    val releaseSpec: SpringSpec<Offset>,
    /** Multiplies the interaction's `dragResponse`: how eagerly it leans. */
    val response: Float,
    /** How far the lean may go, as a multiple of the element's short side. */
    val reach: Float,
    /** Multiplies the swell and the stretch along the direction of travel. */
    val stretch: Float,
    /**
     * How much of the stretch along one axis is taken back from the other. A pane that grows in one
     * direction without narrowing in the other reads as a rigid box; one that pinches in reads as
     * something with volume being pulled.
     */
    val squash: Float,
    // DampedDragAnimation.
    val dragPressSpec: SpringSpec<Float>,
    val scaleXSpec: SpringSpec<Float>,
    val scaleYSpec: SpringSpec<Float>,
    val velocityDamping: Float,
    val velocityStiffness: Float,
    /** Multiplies the reported velocity, which is what call sites stretch a control along. */
    val velocityScale: Float,
    /** Multiplies how far a control swells from its resting scale to its pressed one. */
    val swell: Float,
)

private val PerformanceSpec = MotionSpec(
    pressSpec = spring(0.8f, 380f, 0.002f),
    releaseSpec = spring(0.8f, 400f, Offset.VisibilityThreshold),
    response = 0.9f,
    reach = 0.9f,
    stretch = 0.9f,
    squash = 0f,
    dragPressSpec = spring(1f, 1200f, 0.002f),
    scaleXSpec = spring(0.8f, 300f, 0.002f),
    scaleYSpec = spring(0.85f, 320f, 0.002f),
    velocityDamping = 0.75f,
    velocityStiffness = 340f,
    velocityScale = 0.9f,
    swell = 0.95f,
)

private val DefaultSpec = MotionSpec(
    pressSpec = spring(0.5f, 300f, 0.001f),
    releaseSpec = spring(0.5f, 300f, Offset.VisibilityThreshold),
    response = 1f,
    reach = 1f,
    stretch = 1f,
    squash = 0f,
    dragPressSpec = spring(1f, 1000f, 0.001f),
    scaleXSpec = spring(0.6f, 230f, 0.001f),
    scaleYSpec = spring(0.7f, 250f, 0.001f),
    velocityDamping = 0.5f,
    velocityStiffness = 270f,
    velocityScale = 1f,
    swell = 1f,
)

private val BouncySpec = MotionSpec(
    pressSpec = spring(0.4f, 280f, 0.001f),
    releaseSpec = spring(0.38f, 250f, Offset.VisibilityThreshold),
    response = 1.15f,
    reach = 1.1f,
    stretch = 1.1f,
    squash = 0.15f,
    // Not bouncy on purpose: a control's press progress drives blur radii and lens strengths, and
    // an overshoot would hand those values they were never written for.
    dragPressSpec = spring(1f, 1000f, 0.001f),
    scaleXSpec = spring(0.45f, 210f, 0.001f),
    scaleYSpec = spring(0.55f, 230f, 0.001f),
    velocityDamping = 0.38f,
    velocityStiffness = 240f,
    velocityScale = 1.15f,
    swell = 1.05f,
)

internal val GlassMotion.spec: MotionSpec
    get() = when (this) {
        GlassMotion.Performance -> PerformanceSpec
        GlassMotion.Default -> DefaultSpec
        GlassMotion.Bouncy -> BouncySpec
    }
