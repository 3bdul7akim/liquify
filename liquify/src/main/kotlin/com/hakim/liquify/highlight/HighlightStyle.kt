/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.highlight

import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.util.fastCoerceAtLeast
import com.hakim.liquify.RuntimeShader
import com.hakim.liquify.RuntimeShaderCache
import com.hakim.liquify.internal.HIGHLIGHT_AMBIENT_SHADER
import com.hakim.liquify.internal.HIGHLIGHT_DEFAULT_SHADER
import com.hakim.liquify.internal.HIGHLIGHT_DYNAMIC_SHADER
import com.hakim.liquify.internal.HIGHLIGHT_TWSE_SHADER
import com.hakim.liquify.internal.resolveCornerRadii
import com.hakim.liquify.isRuntimeShaderSupported
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Controls how a [Highlight]'s intensity varies around the border.
 *
 * Implementations return an AGSL program that is used as the rim stroke's shader. Returning `null`
 * paints a flat stroke in [color] instead, which is what every style falls back to below API 33.
 */
@Immutable
public interface HighlightStyle {

    public val color: Color

    public val blendMode: BlendMode

    /**
     * This style with every colour it left open resolved against the surrounding theme.
     *
     * Returns the style itself by default, which is what all but one of the built-in styles want: a
     * specular rim is white over anything, because it is a reflection rather than a pigment.
     * Override it for a style that has to know what it sits on — see [TwSe], which flips.
     *
     * Called by the rim node before it records, and by the group before it uploads the merged
     * silhouette's rim, so an implementation may allocate; it is not on the per-frame path.
     */
    public fun resolvedFor(darkTheme: Boolean): HighlightStyle = this

    /**
     * How far this rim is allowed to reach *outside* the element's outline.
     *
     * `0.dp` — every style but [TwSe] — means the rim is a reflection on the glass itself, and the
     * node clips the stroke to the outline: a wide rim then grows inwards and nothing ever spills
     * past the edge. A style that deliberately draws beyond the edge declares its reach here, and
     * the stroke is centred on the outline and recorded with that much slack around the element
     * instead.
     *
     * It is the reach the *stroke* needs, not just the outward band: a style whose inner band is
     * the wider of the two must report that, or the stroke will clip it.
     */
    public val outsetWidth: Dp get() = 0.dp

    public fun DrawScope.createShader(
        shape: Shape,
        runtimeShaderCache: RuntimeShaderCache
    ): RuntimeShader?

    /** Flat border of a single colour. */
    @Immutable
    public data class Plain(
        override val color: Color = Color.White.copy(alpha = 0.38f),
        override val blendMode: BlendMode = BlendMode.Plus
    ) : HighlightStyle {

        override fun DrawScope.createShader(
            shape: Shape,
            runtimeShaderCache: RuntimeShaderCache
        ): RuntimeShader? = null
    }

    /**
     * Brightest where the border normal points at [angle], fading towards the perpendicular —
     * a single light source somewhere off-screen.
     *
     * @param angle direction of the light in degrees; `45f` puts it above and to the left.
     * @param falloff how sharply the rim fades away from that direction. Higher is tighter.
     */
    @Immutable
    public data class Default(
        override val color: Color = Color.White.copy(alpha = 0.5f),
        override val blendMode: BlendMode = BlendMode.Plus,
        val angle: Float = 45f,
        @param:FloatRange(from = 0.0) val falloff: Float = 1f
    ) : HighlightStyle {

        override fun DrawScope.createShader(
            shape: Shape,
            runtimeShaderCache: RuntimeShaderCache
        ): RuntimeShader? {
            if (!isRuntimeShaderSupported()) return null
            val cornerRadii = shape.resolveCornerRadii(size, layoutDirection, this) ?: return null
            return runtimeShaderCache
                .obtainRuntimeShader("liquify.highlight.default", HIGHLIGHT_DEFAULT_SHADER)
                .apply {
                    setFloatUniform("halfSize", size.width * 0.5f, size.height * 0.5f)
                    setFloatUniform("halfMinSize", size.minDimension * 0.5f)
                    setFloatUniform("cornerRadii", cornerRadii)
                    setColorUniform("color", color.copy(alpha = 1f))
                    angle.toRadians().let { setFloatUniform("lightNormal", cos(it), sin(it)) }
                    setFloatUniform("falloff", falloff)
                }
        }
    }

    /**
     * White on the lit side and black on the opposite side, so the element gains a subtle bevel
     * and sits more convincingly on top of the content behind it.
     */
    @Immutable
    public data class Ambient(
        @param:FloatRange(from = 0.0, to = 1.0) val intensity: Float = 0.5f,
        val angle: Float = 45f,
        @param:FloatRange(from = 0.0) val falloff: Float = 1f
    ) : HighlightStyle {

        override val color: Color = Color.White.copy(alpha = intensity)

        override val blendMode: BlendMode = DrawScope.DefaultBlendMode

        override fun DrawScope.createShader(
            shape: Shape,
            runtimeShaderCache: RuntimeShaderCache
        ): RuntimeShader? {
            if (!isRuntimeShaderSupported()) return null
            val cornerRadii = shape.resolveCornerRadii(size, layoutDirection, this) ?: return null
            return runtimeShaderCache
                .obtainRuntimeShader("liquify.highlight.ambient", HIGHLIGHT_AMBIENT_SHADER)
                .apply {
                    setFloatUniform("halfSize", size.width * 0.5f, size.height * 0.5f)
                    setFloatUniform("halfMinSize", size.minDimension * 0.5f)
                    setFloatUniform("cornerRadii", cornerRadii)
                    angle.toRadians().let { setFloatUniform("lightNormal", cos(it), sin(it)) }
                    setFloatUniform("falloff", falloff)
                }
        }
    }

    /**
     * A drawn edge rather than a reflection, divided twice over.
     *
     * **Across the border.** One band just *inside* the outline and one just *outside* it, each
     * fading to nothing as it leaves the border, and neither crossing to the other side. Letting
     * them blend would collapse the pair into a single soft line straddling the edge, which is the
     * thing this style exists not to be.
     *
     * **Around the border.** Which of the two tones each band carries alternates by quadrant: on
     * the leading diagonal the *inner* face is lit and the shadow falls outside, and on the other
     * diagonal that reverses — the *outer* edge catches the light and the inner face lies in shade.
     * That is not decoration but the same light seen from the other side of the lip, which is why
     * the diagonals agree: top-left with bottom-right, top-right with bottom-left, so one light
     * appears to cross a raised edge rather than four unrelated arcs meeting at the corners.
     *
     * It is the only rim in the library that draws **past** the element. The highlight node reads
     * [outsetWidth] and, seeing it non-zero, centres the stroke on the outline instead of clipping
     * it away, and records the layer with that much slack around the element.
     *
     * Drawn source-over rather than additively, because half of it *darkens*: `BlendMode.Plus`
     * cannot subtract light, and the outer band would simply not appear.
     *
     * It is also the one rim that looks **the same in a light UI and a dark one**. The others are
     * reflections and have to know what they are reflecting off; this is a lit edge with its own
     * shadow under it, and an edge does not swap which side faces the light because the page behind
     * it got darker.
     *
     * Inside a [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup] this degrades to a
     * plain directional rim in the lit tone. The merged surface's rim is rendered inside the merge
     * program, which has one silhouette and no outside to draw on.
     *
     * @param color the lit tone. [Color.Unspecified] — the default — is a fixed white; its alpha is
     *   the strength of whichever band is carrying it.
     * @param inverseColor the shadow tone. Unspecified is a fixed black.
     * @param angle direction of the light in degrees, as in [Default]. It also decides which
     *   diagonal is the lit one, so the brightest quadrants and the lit-inside quadrants agree.
     * @param falloff how sharply the rim fades away from that direction. Higher is tighter.
     * @param innerWidth how far the inner band reaches inside the outline before it is fully
     *   transparent.
     * @param outerWidth how far the outer band reaches outside it.
     * @param ambient how much of the rim survives where the border faces away from the light. `0f`
     *   lets it vanish on two sides, which reads as a broken outline rather than as an edge.
     * @param seamBlend how far the swap from one arrangement to the other is spread, measured out
     *   from the midpoint of an edge in each direction. [Dp.Unspecified] — the default — scales it
     *   with the element so the sweep covers most of every edge whatever the size, which is what
     *   makes it read as one gradient rather than as four flat quadrants meeting at a seam. An
     *   explicit distance is absolute and applies to both axes; `0.dp` gives a hard switch, which
     *   aliases.
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
    ) : HighlightStyle {

        /**
         * The wider of the two bands, because the stroke is centred on the outline and has to
         * contain both — the outer one decides how far it spills, the inner one how far it must
         * reach back before the stroke's own edge cuts the gradient short.
         */
        override val outsetWidth: Dp get() = maxOf(innerWidth, outerWidth)

        /**
         * Fills in whichever tone was left open. [darkTheme] is ignored on purpose: this rim looks
         * the same in both themes — see [TwSeLitColor].
         */
        override fun resolvedFor(darkTheme: Boolean): TwSe {
            val lit = color.takeOrElse { TwSeLitColor }
            val shade = inverseColor.takeOrElse { TwSeShadeColor }
            return if (lit == color && shade == inverseColor) {
                this
            } else {
                copy(color = lit, inverseColor = shade)
            }
        }

        override fun DrawScope.createShader(
            shape: Shape,
            runtimeShaderCache: RuntimeShaderCache
        ): RuntimeShader? {
            if (!isRuntimeShaderSupported()) return null
            val cornerRadii = shape.resolveCornerRadii(size, layoutDirection, this) ?: return null
            // Unspecified is not a colour, and the shader has no way to ask what theme it is in.
            // The rim node resolves the style before it gets here; this covers a caller that
            // reached the program directly, and costs nothing once the colours are settled.
            val resolved = resolvedFor(darkTheme = true)

            // A fixed distance cannot describe the quadrant crossfade: it runs *along* an edge, so
            // the same 4.dp that sweeps a whole toggle is a hairline on a card. Left unspecified it
            // is taken as a share of each half-edge instead, and the sweep looks the same at any
            // size. The shader interpolates across ±this, so neither axis may be zero wide.
            val blendX: Float
            val blendY: Float
            if (seamBlend.isSpecified) {
                val absolute = seamBlend.toPx().fastCoerceAtLeast(MinSeamBlend)
                blendX = absolute
                blendY = absolute
            } else {
                blendX = (size.width * 0.5f * RelativeSeamBlend).fastCoerceAtLeast(MinSeamBlend)
                blendY = (size.height * 0.5f * RelativeSeamBlend).fastCoerceAtLeast(MinSeamBlend)
            }

            return runtimeShaderCache
                .obtainRuntimeShader("liquify.highlight.twSe", HIGHLIGHT_TWSE_SHADER)
                .apply {
                    setFloatUniform("halfSize", size.width * 0.5f, size.height * 0.5f)
                    setFloatUniform("halfMinSize", size.minDimension * 0.5f)
                    setFloatUniform("cornerRadii", cornerRadii)
                    setColorUniform("color", resolved.color)
                    setColorUniform("inverseColor", resolved.inverseColor)
                    angle.toRadians().let { setFloatUniform("lightNormal", cos(it), sin(it)) }
                    setFloatUniform("falloff", falloff)
                    // The shader ramps across these, so neither may be zero wide.
                    setFloatUniform(
                        "innerWidth",
                        innerWidth.toPx().fastCoerceAtLeast(MinBandWidth)
                    )
                    setFloatUniform(
                        "outerWidth",
                        outerWidth.toPx().fastCoerceAtLeast(MinBandWidth)
                    )
                    setFloatUniform("ambient", ambient)
                    setFloatUniform("seamBlend", blendX, blendY)
                }
        }
    }

    /**
     * Rim that lights up on the side facing [pointer] — the border half of "glass energises with
     * light when you touch it".
     *
     * With [focus] at `0f` this degrades to a plain directional rim aimed at the pointer; at `1f`
     * the highlight also fades with distance, concentrating into a hotspot right under the finger.
     * Animate [focus] with the press progress and the rim ignites where the touch lands.
     *
     * @param pointer position relative to the element's **centre**, in pixels.
     * @param focus how strongly the rim concentrates around the pointer.
     * @param falloff angular tightness of the lit arc.
     */
    @Immutable
    public data class Dynamic(
        override val color: Color = Color.White.copy(alpha = 0.75f),
        override val blendMode: BlendMode = BlendMode.Plus,
        val pointer: Offset = Offset.Zero,
        @param:FloatRange(from = 0.0, to = 1.0) val focus: Float = 1f,
        @param:FloatRange(from = 0.0) val falloff: Float = 1.5f
    ) : HighlightStyle {

        override fun DrawScope.createShader(
            shape: Shape,
            runtimeShaderCache: RuntimeShaderCache
        ): RuntimeShader? {
            if (!isRuntimeShaderSupported()) return null
            val cornerRadii = shape.resolveCornerRadii(size, layoutDirection, this) ?: return null
            return runtimeShaderCache
                .obtainRuntimeShader("liquify.highlight.dynamic", HIGHLIGHT_DYNAMIC_SHADER)
                .apply {
                    setFloatUniform("halfSize", size.width * 0.5f, size.height * 0.5f)
                    setFloatUniform("halfMinSize", size.minDimension * 0.5f)
                    // max(halfSize.x, halfSize.y) * 2 — the reach of the proximity falloff.
                    setFloatUniform("pointerReach", size.maxDimension)
                    setFloatUniform("cornerRadii", cornerRadii)
                    setColorUniform("color", color.copy(alpha = 1f))
                    setFloatUniform("falloff", falloff)
                    setFloatUniform("pointer", pointer.x, pointer.y)
                    setFloatUniform("pointerFocus", focus)
                }
        }
    }

    public companion object {

        @Stable
        public val Default: Default = Default()

        @Stable
        public val Ambient: Ambient = Ambient()

        @Stable
        public val Plain: Plain = Plain()

        @Stable
        public val TwSe: TwSe = TwSe()
    }
}

private fun Float.toRadians(): Float = (this * (PI / 180.0)).toFloat()

/** Floor on a band's width. `smoothstep` needs the two edges of its range to differ. */
private const val MinBandWidth = 0.5f

/**
 * How much of each half-edge an unspecified [HighlightStyle.TwSe.seamBlend] sweeps across.
 *
 * At `0.85` the crossfade runs from one corner almost to the next, and only the corners themselves
 * settle on a pure arrangement — which is where they belong, because a 45° rim is at its brightest
 * on the leading diagonal and dimmest on the other one. Lower it and the quadrants flatten out with
 * a seam between them; raise it past 1 and the pure corners never appear at all.
 */
private const val RelativeSeamBlend = 0.85f

/** Floor on the crossfade width. `smoothstep` needs the two edges of its range to differ. */
private const val MinSeamBlend = 0.0001f

/**
 * The tones [HighlightStyle.TwSe] resolves unspecified colours to.
 *
 * Fixed, and deliberately not theme-aware — the one rim in the library that is the same in a light
 * UI and a dark one. Every other rim is a *reflection*, and a reflection has to know what it is
 * reflecting off; this one is a lit edge with its own shadow under it, and an edge does not change
 * which side of itself faces the light because the page behind it got darker. Inverting them would
 * turn the lip inside out.
 *
 * Not companions on the style itself: a nested class carrying one would shadow the
 * `HighlightStyle.TwSe` preset, which is the name call sites actually reach for.
 */
private val TwSeLitColor: Color = Color.White.copy(alpha = 0.85f)

private val TwSeShadeColor: Color = Color.Black.copy(alpha = 0.30f)
