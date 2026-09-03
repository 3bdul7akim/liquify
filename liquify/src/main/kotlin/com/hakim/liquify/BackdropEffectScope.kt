/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.hakim.liquify.effects.ColorFilterCache
import com.hakim.liquify.effects.MAX_GRADIENT_BLUR_STEPS

/**
 * Receiver of the `effects = { … }` block of
 * [liquify][com.hakim.liquify.liquify] and
 * [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup].
 *
 * Effects are ordinary extension functions on this scope, so the order you call them in is the
 * order the GPU applies them — `blur()` then `lens()` blurs the backdrop and *then* refracts the
 * blurred result, which is what real glass does. They compose by chaining [renderEffect].
 *
 * The scope is a [Density], so `16.dp.toPx()` works directly inside the block.
 */
public sealed interface BackdropEffectScope : Density, RuntimeShaderCache {

    /** Size of the element (or of the group's union bounds) in pixels. */
    public val size: Size

    public val layoutDirection: LayoutDirection

    /**
     * The element's shape. Effects that need an analytic signed distance field — `lens()` above
     * all — read the corner radii from it.
     *
     * For a group this is [RectangleShape]; the group's real geometry is the merged distance field
     * of its members, not a single shape.
     */
    public val shape: Shape

    /**
     * Extra pixels recorded around the element so effects that read outside their own bounds have
     * real content to read. `blur()` grows this; `lens()` consumes it.
     */
    public var padding: Float

    /** The accumulated effect chain. Effects read, wrap and write this back. */
    public var renderEffect: RenderEffect?
}

/** Shared state for both the single-element and the group effect scope. */
internal abstract class BaseBackdropEffectScope : BackdropEffectScope {

    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var size: Size = Size.Unspecified
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override var padding: Float = 0f
    override var renderEffect: RenderEffect? = null

    /**
     * Smoothing radius in pixels requested by `merge()`. Zero means the element renders on its
     * own; anything above zero makes it a member of the enclosing group's merged surface.
     */
    var mergeRadius: Float = 0f

    /**
     * Colour this element contributes to the merged surface, requested by
     * [mergeTint][com.hakim.liquify.effects.mergeTint]. Unspecified means the member adds no
     * colour of its own and shows whatever the group's material makes of the backdrop.
     */
    var mergeTintColor: Color = Color.Unspecified

    /**
     * Scratch for the four corner radii, shared by every effect in one block.
     *
     * The effects run again whenever anything they read changes, which for a pressed control is
     * every frame — so resolving the radii into a fresh array there allocates one per frame per
     * element. The effects upload the values immediately and never hold on to the array, so one
     * per scope is enough.
     */
    val cornerRadiiScratch: FloatArray = FloatArray(4)

    /**
     * Fingerprint of the uniforms last uploaded to the refraction program, and to each stop of the
     * gradient blur.
     *
     * Writing a uniform is not free: it crosses into the platform shader and discards the native
     * instance it has already built, so the next draw recompiles it. Re-running an effects block
     * because one animated radius moved would otherwise re-upload the size, the offset and the
     * four corner radii along with it, none of which changed. The shaders live in this scope's own
     * cache, so what was last written to them is exactly what these record.
     */
    var lensUniforms: Long = Long.MIN_VALUE

    val gradientBlurUniforms: LongArray =
        LongArray(MAX_GRADIENT_BLUR_STEPS + 1) { Long.MIN_VALUE }

    /**
     * The colour-matrix filters this scope's effects block has built, kept while their inputs are
     * unchanged. See [ColorFilterCache] — an effects block driven by a press or a transition runs
     * every frame, and each of these is three allocations plus a platform filter.
     */
    val colorFilters: ColorFilterCache = ColorFilterCache()

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader =
        runtimeShaderCache.obtainRuntimeShader(key, string)

    /** Returns `true` when anything the effects depend on changed and they must be re-applied. */
    fun update(
        density: Float,
        fontScale: Float,
        size: Size,
        layoutDirection: LayoutDirection
    ): Boolean {
        val changed = density != this.density ||
            fontScale != this.fontScale ||
            size != this.size ||
            layoutDirection != this.layoutDirection
        if (changed) {
            this.density = density
            this.fontScale = fontScale
            this.size = size
            this.layoutDirection = layoutDirection
        }
        return changed
    }

    fun update(scope: DrawScope): Boolean =
        update(scope.density, scope.fontScale, scope.size, scope.layoutDirection)

    fun apply(effects: BackdropEffectScope.() -> Unit) {
        padding = 0f
        renderEffect = null
        mergeRadius = 0f
        mergeTintColor = Color.Unspecified
        effects()
    }

    open fun reset() {
        density = 1f
        fontScale = 1f
        size = Size.Unspecified
        layoutDirection = LayoutDirection.Ltr
        padding = 0f
        renderEffect = null
        mergeRadius = 0f
        mergeTintColor = Color.Unspecified
        // The shaders the fingerprints describe go with the cache, so what was uploaded to them is
        // no longer known.
        lensUniforms = Long.MIN_VALUE
        gradientBlurUniforms.fill(Long.MIN_VALUE)
        colorFilters.clear()
        runtimeShaderCache.clear()
    }
}

/** The effect scope of a single glass element. */
internal abstract class ElementEffectScope : BaseBackdropEffectScope()
