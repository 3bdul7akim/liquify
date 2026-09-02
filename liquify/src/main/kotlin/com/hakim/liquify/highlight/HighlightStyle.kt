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
                    setFloatUniform("size", size.width, size.height)
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
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("cornerRadii", cornerRadii)
                    angle.toRadians().let { setFloatUniform("lightNormal", cos(it), sin(it)) }
                    setFloatUniform("falloff", falloff)
                }
        }
    }

    /**
     * Directional rim, like [Default], whose four quadrants alternate between a tone and its
     * opposite — and whose tone follows the theme.
     *
     * Two things separate it from [Default]:
     *
     * - **It flips with the surface.** Left unspecified, [color] is white over a dark UI and
     *   **black over a light one**. A white specular rim is a reflection and works over anything;
     *   this style is closer to a drawn bevel, and a bevel lit in white on a light background has
     *   nothing to stand against.
     * - **The corners alternate.** [color] runs along the top-left and bottom-right quadrants and
     *   [inverseColor] — its opposite, derived automatically — along the other two. So a rim that
     *   is white at the top-left is black at the top-right, white again at the bottom-right and
     *   black at the bottom-left, which reads as one light crossing a raised edge.
     *
     * Drawn source-over rather than additively, because half of it *darkens*: `BlendMode.Plus`
     * cannot subtract light, and the dark quadrants would simply not appear.
     *
     * Inside a [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup] this degrades to a
     * plain directional rim in the resolved tone. A merged surface has one silhouette and no
     * quadrants of its own to alternate across, and giving it some would put a seam through exactly
     * the bridges that merging exists to dissolve.
     *
     * @param color the tone of the leading diagonal. [Color.Unspecified] — the default — resolves
     *   against the theme; its alpha is the strength of the rim.
     * @param inverseColor the tone of the other two quadrants. [Color.Unspecified] derives it by
     *   inverting [color], which is what turns white into black.
     * @param angle direction of the light in degrees, as in [Default].
     * @param falloff how sharply the rim fades away from that direction. Higher is tighter.
     * @param seamBlend how far the change from one tone to the other is spread, measured out from
     *   the midpoint of an edge in each direction. [Dp.Unspecified] — the default — scales it with
     *   the element so the sweep covers most of every edge whatever the size, which is what makes
     *   it read as one gradient rather than as four flat quadrants meeting at a seam. An explicit
     *   distance is absolute and applies to both axes; `0.dp` gives a hard switch, which aliases.
     */
    @Immutable
    public data class TwSe(
        override val color: Color = Color.Unspecified,
        val inverseColor: Color = Color.Unspecified,
        override val blendMode: BlendMode = DrawScope.DefaultBlendMode,
        val angle: Float = 45f,
        @param:FloatRange(from = 0.0) val falloff: Float = 1f,
        val seamBlend: Dp = Dp.Unspecified
    ) : HighlightStyle {

        override fun resolvedFor(darkTheme: Boolean): TwSe {
            val tone = color.takeOrElse { twSeDefaultColor(darkTheme) }
            val opposite = inverseColor.takeOrElse { tone.inverted() }
            return if (tone == color && opposite == inverseColor) {
                this
            } else {
                copy(color = tone, inverseColor = opposite)
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

            // A fixed distance cannot describe this: the crossfade runs *along* an edge, so the
            // same 4.dp that sweeps a whole toggle is a hairline on a card. Left unspecified it is
            // taken as a share of each half-edge instead, and the sweep looks the same at any size.
            // The shader interpolates across ±this, so neither axis may be zero wide.
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
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("cornerRadii", cornerRadii)
                    setColorUniform("color", resolved.color)
                    setColorUniform("inverseColor", resolved.inverseColor)
                    angle.toRadians().let { setFloatUniform("lightNormal", cos(it), sin(it)) }
                    setFloatUniform("falloff", falloff)
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
                    setFloatUniform("size", size.width, size.height)
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

/**
 * How much of each half-edge an unspecified [HighlightStyle.TwSe.seamBlend] sweeps across.
 *
 * At `0.85` the crossfade runs from one corner almost to the next, and only the corners themselves
 * settle on a pure tone — which is where they belong, because a 45° rim is at its brightest on the
 * leading diagonal and fades to nothing on the other one. Lower it and the tones flatten into
 * quadrants with a seam between them; raise it past 1 and the pure tones never appear at all.
 */
private const val RelativeSeamBlend = 0.85f

/** Floor on the crossfade width. `smoothstep` needs the two edges of its range to differ. */
private const val MinSeamBlend = 0.0001f

/**
 * The tone [HighlightStyle.TwSe] resolves an unspecified colour to.
 *
 * White over a dark UI — the same catch of light every other rim uses — and black over a light one,
 * which is the inversion that keeps a *drawn* bevel visible where a white one would disappear.
 *
 * Not a companion on the style itself: a nested class carrying one would shadow the
 * `HighlightStyle.TwSe` preset, which is the name call sites actually reach for.
 */
private fun twSeDefaultColor(darkTheme: Boolean): Color =
    if (darkTheme) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)

/**
 * The colour opposite this one, keeping its alpha and its colour space.
 *
 * Channel-wise rather than a hue rotation, because what this has to guarantee is that white becomes
 * black: the point of the inversion is contrast against the same background, not a different hue.
 */
private fun Color.inverted(): Color =
    Color(1f - red, 1f - green, 1f - blue, alpha, colorSpace)
