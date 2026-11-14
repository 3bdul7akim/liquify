/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify

import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.lerp
import com.hakim.liquify.internal.HIGHLIGHT_AMBIENT_SHADER
import com.hakim.liquify.internal.HIGHLIGHT_DEFAULT_SHADER
import com.hakim.liquify.internal.HIGHLIGHT_TWSE_SHADER
import com.hakim.liquify.internal.RuntimeShader
import com.hakim.liquify.internal.RuntimeShaderCache
import com.hakim.liquify.internal.resolveCornerRadii
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The specular edge of a pane of glass.
 *
 * Real glass catches light at its edges, and that catch is most of what separates a glass control
 * from a translucent rectangle. The width is sub-pixel by default: a rim should read as light, not as
 * a stroke.
 *
 * @param width stroke width.
 * @param blurRadius softness; half the width keeps it crisp but not aliased.
 * @param alpha overall opacity, applied as a layer property — fading a rim costs nothing.
 * @param style how the intensity varies around the border.
 */
@Immutable
public data class GlassRim(
    val width: Dp = 0.25f.dp,
    val blurRadius: Dp = width / 2f,
    @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f,
    val style: GlassRimStyle = GlassRimStyle.Default
) {

    public companion object {

        /** Directional rim lit from the top left — the neutral default. */
        @Stable
        public val Default: GlassRim = GlassRim()

        /** Bright on the lit side, dark on the opposite one — reads as a bevel. */
        @Stable
        public val Ambient: GlassRim = GlassRim(style = GlassRimStyle.Ambient)

        /** A uniform hairline, the cheapest rim — and what every style falls back to below API 33. */
        @Stable
        public val Plain: GlassRim = GlassRim(style = GlassRimStyle.Plain)

        /**
         * A crisp line in one colour — an outline rather than a reflection, in the theme's ink unless
         * given a colour. Unblurred: the line is meant to be sharp.
         */
        @Stable
        public val Outline: GlassRim =
            GlassRim(width = 1f.dp, blurRadius = 0f.dp, style = GlassRimStyle.Outline)

        /**
         * A drawn edge: a light band just inside the outline and a dark one just outside it. Unblurred
         * on purpose — a blur would soften the division between the two bands, the one part of this
         * rim that has to stay crisp.
         */
        @Stable
        public val TwSe: GlassRim =
            GlassRim(width = 1f.dp, blurRadius = 0f.dp, style = GlassRimStyle.TwSe)
    }
}

/**
 * How a [GlassRim]'s intensity varies around the border. Below API 33 every style is drawn as a flat
 * stroke in its [color].
 */
@Immutable
public sealed interface GlassRimStyle {

    public val color: Color

    public val blendMode: BlendMode

    /** A flat border of one colour, added to what is below. */
    @Immutable
    public data class Plain(
        override val color: Color = Color.White.copy(alpha = 0.38f),
        override val blendMode: BlendMode = BlendMode.Plus
    ) : GlassRimStyle

    /**
     * A plain line in one colour — no light, no gradient, no shader. Drawn source-over, so a dark line
     * darkens what it sits on.
     *
     * @param color the line. [Color.Unspecified] resolves against the theme: dark ink over a light UI,
     *   a light line over a dark one, both at half strength.
     */
    @Immutable
    public data class Outline(
        override val color: Color = Color.Unspecified,
        override val blendMode: BlendMode = DrawScope.DefaultBlendMode
    ) : GlassRimStyle

    /**
     * Brightest where the border faces [angle], fading towards the perpendicular — one light source
     * somewhere off screen.
     *
     * @param angle direction of the light in degrees; `45f` is above and to the left.
     * @param falloff how sharply the rim fades away from that direction.
     */
    @Immutable
    public data class Default(
        override val color: Color = Color.White.copy(alpha = 0.5f),
        override val blendMode: BlendMode = BlendMode.Plus,
        val angle: Float = 45f,
        @param:FloatRange(from = 0.0) val falloff: Float = 1f
    ) : GlassRimStyle

    /** White on the lit side and black on the other, so the pane gains a subtle bevel. */
    @Immutable
    public data class Ambient(
        @param:FloatRange(from = 0.0, to = 1.0) val intensity: Float = 0.5f,
        val angle: Float = 45f,
        @param:FloatRange(from = 0.0) val falloff: Float = 1f
    ) : GlassRimStyle {

        override val color: Color = Color.White.copy(alpha = intensity)

        override val blendMode: BlendMode = DrawScope.DefaultBlendMode
    }

    /**
     * A drawn edge rather than a reflection, divided twice over.
     *
     * **Across the border:** one band just inside the outline and one just outside it, each fading as
     * it leaves the border and neither crossing to the other side.
     *
     * **Around the border:** which tone each band carries alternates by quadrant — on the leading
     * diagonal the inner face is lit and the shadow falls outside, on the other diagonal that
     * reverses. It is the same light seen from the other side of a raised lip, which is why the
     * diagonals agree.
     *
     * The one rim that draws past the element, and the one that looks the same in a light UI and a
     * dark one: it is an edge with its own shadow, not a reflection of what is behind. Inside a merged
     * [LiquidGlassGroup] the two bands survive but the quadrant swap does not — a merged body has no
     * centre of its own to divide against.
     *
     * @param color the lit tone; unspecified is a fixed white.
     * @param inverseColor the shadow tone; unspecified is a fixed black.
     * @param angle direction of the light in degrees.
     * @param falloff how sharply the rim fades away from that direction.
     * @param innerWidth how far the inner band reaches inside the outline.
     * @param outerWidth how far the outer band reaches outside it.
     * @param ambient how much of the rim survives where the border faces away from the light.
     * @param seamBlend how far the swap between the two arrangements is spread from the middle of an
     *   edge. Unspecified scales it with the element, so the sweep covers most of every edge.
     */
    @Immutable
    public data class TwSe(
        override val color: Color = Color.Unspecified,
        val inverseColor: Color = Color.Unspecified,
        override val blendMode: BlendMode = DrawScope.DefaultBlendMode,
        val angle: Float = 45f,
        @param:FloatRange(from = 0.0) val falloff: Float = 1f,
        val innerWidth: Dp = 1f.dp,
        val outerWidth: Dp = 1f.dp,
        @param:FloatRange(from = 0.0, to = 1.0) val ambient: Float = 0.35f,
        val seamBlend: Dp = Dp.Unspecified
    ) : GlassRimStyle

    public companion object {

        @Stable
        public val Default: Default = Default()

        @Stable
        public val Ambient: Ambient = Ambient()

        @Stable
        public val Plain: Plain = Plain()

        @Stable
        public val Outline: Outline = Outline()

        @Stable
        public val TwSe: TwSe = TwSe()
    }
}

/**
 * This style with every colour it left open resolved against the theme. Only [GlassRimStyle.Outline]
 * actually depends on it; [GlassRimStyle.TwSe] fills in fixed tones.
 */
internal fun GlassRimStyle.resolvedFor(darkTheme: Boolean): GlassRimStyle = when (this) {
    is GlassRimStyle.Outline ->
        if (color.isSpecified) this
        else copy(color = if (darkTheme) OutlineDarkThemeColor else OutlineLightThemeColor)

    is GlassRimStyle.TwSe -> {
        val lit = color.takeOrElse { TwSeLitColor }
        val shade = inverseColor.takeOrElse { TwSeShadeColor }
        if (lit == color && shade == inverseColor) this else copy(color = lit, inverseColor = shade)
    }

    else -> this
}

/**
 * How far this rim reaches outside the outline. Zero clips the stroke to the outline, so a wide rim
 * grows inwards; a style that draws past the edge reports the reach its stroke needs.
 */
internal val GlassRimStyle.outsetWidth: Dp
    get() = if (this is GlassRimStyle.TwSe) maxOf(innerWidth, outerWidth) else 0.dp

/** The program the rim stroke is painted with, or `null` for a flat stroke in [GlassRimStyle.color]. */
internal fun GlassRimStyle.createShader(
    scope: DrawScope,
    shape: Shape,
    cache: RuntimeShaderCache
): RuntimeShader? {
    if (!isRuntimeShaderSupported()) return null
    if (this is GlassRimStyle.Plain || this is GlassRimStyle.Outline) return null
    val size = scope.size
    val radii = shape.resolveCornerRadii(size, scope.layoutDirection, scope) ?: return null
    return when (this) {
        is GlassRimStyle.Default ->
            cache.obtainRuntimeShader("liquify.highlight.default", HIGHLIGHT_DEFAULT_SHADER).apply {
                setFloatUniform("halfSize", size.width * 0.5f, size.height * 0.5f)
                setFloatUniform("halfMinSize", size.minDimension * 0.5f)
                setFloatUniform("cornerRadii", radii)
                setColorUniform("color", color.copy(alpha = 1f))
                angle.toRadians().let { setFloatUniform("lightNormal", cos(it), sin(it)) }
                setFloatUniform("falloff", falloff)
            }

        is GlassRimStyle.Ambient ->
            cache.obtainRuntimeShader("liquify.highlight.ambient", HIGHLIGHT_AMBIENT_SHADER).apply {
                setFloatUniform("halfSize", size.width * 0.5f, size.height * 0.5f)
                setFloatUniform("halfMinSize", size.minDimension * 0.5f)
                setFloatUniform("cornerRadii", radii)
                angle.toRadians().let { setFloatUniform("lightNormal", cos(it), sin(it)) }
                setFloatUniform("falloff", falloff)
            }

        is GlassRimStyle.TwSe -> {
            // The rim node resolves the style before it gets here; this covers anything else.
            val resolved = resolvedFor(darkTheme = true) as GlassRimStyle.TwSe
            // The crossfade runs *along* an edge, so a fixed distance would sweep a whole toggle and
            // be a hairline on a card. Unspecified, it is a share of each half-edge instead.
            val blendX: Float
            val blendY: Float
            if (seamBlend.isSpecified) {
                val absolute = with(scope) { seamBlend.toPx() }.fastCoerceAtLeast(MinSeamBlend)
                blendX = absolute
                blendY = absolute
            } else {
                blendX = (size.width * 0.5f * RelativeSeamBlend).fastCoerceAtLeast(MinSeamBlend)
                blendY = (size.height * 0.5f * RelativeSeamBlend).fastCoerceAtLeast(MinSeamBlend)
            }
            val inner = with(scope) { innerWidth.toPx() }.fastCoerceAtLeast(MinBandWidth)
            val outer = with(scope) { outerWidth.toPx() }.fastCoerceAtLeast(MinBandWidth)
            cache.obtainRuntimeShader("liquify.highlight.twSe", HIGHLIGHT_TWSE_SHADER).apply {
                setFloatUniform("halfSize", size.width * 0.5f, size.height * 0.5f)
                setFloatUniform("halfMinSize", size.minDimension * 0.5f)
                setFloatUniform("cornerRadii", radii)
                setColorUniform("color", resolved.color)
                setColorUniform("inverseColor", resolved.inverseColor)
                angle.toRadians().let { setFloatUniform("lightNormal", cos(it), sin(it)) }
                setFloatUniform("falloff", falloff)
                // The shader ramps across these, so neither may be zero wide.
                setFloatUniform("innerWidth", inner)
                setFloatUniform("outerWidth", outer)
                setFloatUniform("ambient", ambient)
                setFloatUniform("seamBlend", blendX, blendY)
            }
        }

        is GlassRimStyle.Plain, is GlassRimStyle.Outline -> null
    }
}

/** Direction of the light, for the merged surface's rim. */
internal fun GlassRimStyle.lightAngleRadians(): Float = when (this) {
    is GlassRimStyle.Default -> angle.toRadians()
    is GlassRimStyle.Ambient -> angle.toRadians()
    is GlassRimStyle.TwSe -> angle.toRadians()
    else -> 45f.toRadians()
}

/** A falloff of zero makes `pow(|cos|, 0)` one everywhere: the uniform hairline of a flat style. */
internal fun GlassRimStyle.lightFalloff(): Float = when (this) {
    is GlassRimStyle.Default -> falloff
    is GlassRimStyle.Ambient -> falloff
    is GlassRimStyle.TwSe -> falloff
    is GlassRimStyle.Plain, is GlassRimStyle.Outline -> 0f
}

/**
 * [a] blended towards [b] by [fraction]. A side that is `null` is the other one faded out, so a rim
 * that only exists while pressed fades in instead of popping. Two different styles cannot be mixed,
 * so the first fades out over the first half and the second in over the second.
 */
internal fun lerpRim(a: GlassRim?, b: GlassRim?, fraction: Float): GlassRim? {
    if (fraction <= 0f || a == b) return a
    if (fraction >= 1f) return b
    if (a == null) return b?.copy(alpha = b.alpha * fraction)
    if (b == null) return a.copy(alpha = a.alpha * (1f - fraction))
    if (a.style != b.style) {
        return if (fraction < 0.5f) a.copy(alpha = a.alpha * (1f - fraction * 2f))
        else b.copy(alpha = b.alpha * (fraction * 2f - 1f))
    }
    return GlassRim(
        width = lerpExact(a.width, b.width, fraction),
        blurRadius = lerpExact(a.blurRadius, b.blurRadius, fraction),
        alpha = lerp(a.alpha, b.alpha, fraction),
        style = a.style
    )
}

private fun Float.toRadians(): Float = (this * (PI / 180.0)).toFloat()

private val OutlineLightThemeColor: Color = Color.Black.copy(alpha = 0.5f)

private val OutlineDarkThemeColor: Color = Color.White.copy(alpha = 0.5f)

/** Floor on a band's width: `smoothstep` needs the two edges of its range to differ. */
private const val MinBandWidth = 0.5f

/**
 * How much of each half-edge an unspecified [GlassRimStyle.TwSe.seamBlend] sweeps across. At `0.85`
 * the crossfade runs from one corner almost to the next, and only the corners settle on a pure
 * arrangement — where a 45° rim is brightest and dimmest.
 */
private const val RelativeSeamBlend = 0.85f

private const val MinSeamBlend = 0.0001f

/**
 * The fixed tones of [GlassRimStyle.TwSe]. Deliberately not theme-aware: an edge does not change which
 * side faces the light because the page behind it got darker.
 */
private val TwSeLitColor: Color = Color.White.copy(alpha = 0.85f)

private val TwSeShadeColor: Color = Color.Black.copy(alpha = 0.30f)
