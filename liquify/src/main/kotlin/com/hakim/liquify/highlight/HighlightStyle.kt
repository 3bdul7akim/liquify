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
import com.hakim.liquify.RuntimeShader
import com.hakim.liquify.RuntimeShaderCache
import com.hakim.liquify.internal.HIGHLIGHT_AMBIENT_SHADER
import com.hakim.liquify.internal.HIGHLIGHT_DEFAULT_SHADER
import com.hakim.liquify.internal.HIGHLIGHT_DYNAMIC_SHADER
import com.hakim.liquify.internal.resolveCornerRadii
import com.hakim.liquify.isRuntimeShaderSupported
import kotlin.math.PI

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
                    setFloatUniform("angle", angle.toRadians())
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
        @param:FloatRange(from = 0.0, to = 1.0) val intensity: Float = 0.38f,
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
                    setFloatUniform("angle", angle.toRadians())
                    setFloatUniform("falloff", falloff)
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
    }
}

private fun Float.toRadians(): Float = (this * (PI / 180.0)).toFloat()
