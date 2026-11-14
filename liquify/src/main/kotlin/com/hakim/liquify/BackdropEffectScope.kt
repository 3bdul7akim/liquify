/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify

import android.os.Build
import androidx.annotation.FloatRange
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.hakim.liquify.internal.ColorFilterCache
import com.hakim.liquify.internal.ColorFilterEffect
import com.hakim.liquify.internal.ColorMatrices
import com.hakim.liquify.internal.RuntimeShader
import com.hakim.liquify.internal.RuntimeShaderCache
import com.hakim.liquify.internal.RuntimeShaderCacheImpl
import com.hakim.liquify.internal.applyLens
import com.hakim.liquify.internal.applyMaterial
import com.hakim.liquify.internal.chain

/**
 * Where a hand-written glass recipe is written: the `effects = { … }` block of
 * [liquify][com.hakim.liquify.liquify] and [LiquidGlassGroup].
 *
 * You rarely need one — a [GlassMaterial] covers everyday glass. Reach for it for a recipe the
 * material cannot express:
 *
 * ```
 * Modifier.liquify(Capsule(), effects = {
 *     vibrancy()
 *     blur(4.dp)
 *     lens(refractionHeight = 24.dp, refractionAmount = 24.dp)
 * })
 * ```
 *
 * **Call order is GPU order.** `blur()` before `lens()` bends already-blurred light, which is what
 * reads as thick glass. The scope is a [Density], so dp values convert with `toPx()` and [size] is in
 * the same pixels.
 */
public sealed interface BackdropEffectScope : Density {

    /** Size of the element — or of the group's union — in pixels. */
    public val size: Size

    public val layoutDirection: LayoutDirection

    /** The element's outline; [RectangleShape][androidx.compose.ui.graphics.RectangleShape] for a group. */
    public val shape: Shape

    /**
     * Gaussian blur of the backdrop. Call it before [lens]. No-op below API 31.
     *
     * @param edgeTreatment how to sample beyond the recorded backdrop. [TileMode.Clamp] stretches the
     *   edge pixels; [TileMode.Decal] fades to transparent.
     */
    public fun blur(radius: Dp, edgeTreatment: TileMode = TileMode.Clamp)

    /**
     * Bends the backdrop inside a band along the border — the effect that makes glass read as glass.
     * The flat middle is left alone, so text behind it stays readable. Takes exactly these values,
     * whatever the element's size; a [GlassMaterial] sizes its refraction for you.
     *
     * Inside a merged [LiquidGlassGroup] the lens follows the merged outline of all members. No-op
     * below API 33, for shapes that are not rounded rectangles, and under [GlassLook.Frosted].
     *
     * @param refractionHeight thickness of the refracting band, measured inwards from the border.
     * @param refractionAmount how far the backdrop is displaced at the very edge.
     * @param depthEffect domes the pane into a lens instead of a flat sheet.
     * @param chromaticAberration a coloured fringe at the rim — seven samples per pixel instead of one.
     */
    public fun lens(
        refractionHeight: Dp,
        refractionAmount: Dp,
        depthEffect: Boolean = false,
        chromaticAberration: Boolean = false
    )

    /**
     * Saturates the backdrop so colours survive the blur. Costs no pass of its own: colour work is
     * folded into the refraction program that follows.
     */
    public fun vibrancy(@FloatRange(from = 0.0) amount: Float = 1.5f)

    /**
     * Blends the backdrop towards [color] by its alpha, *before* refraction, so the colour bends with
     * the rim. For the colour of a whole pane, prefer `liquify`'s `tint`.
     */
    public fun tint(color: Color)

    /** Applies [material]'s recipe — vibrancy, blur and its size-adapted lens — in the right order. */
    public fun material(material: GlassMaterial)

    /**
     * Joins the merged surface of the enclosing [LiquidGlassGroup], for an element with its own
     * effects; everything else in a group merges on its own. Does nothing outside a group.
     *
     * @param amount reach relative to the element's short side; `1f` starts bridging at about half of
     *   it.
     */
    public fun merge(@FloatRange(from = 0.0) amount: Float = 1f)

    /** Appends an arbitrary [RenderEffect] — the escape hatch for effects this library does not ship. */
    public fun effect(effect: RenderEffect)
}

/** The state both the element and the group scope build their chain in. */
internal abstract class BaseBackdropEffectScope : BackdropEffectScope, RuntimeShaderCache {

    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var size: Size = Size.Unspecified
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr

    /**
     * Extra pixels recorded around the element, so effects that read outside its bounds have real
     * content to read. A blur grows it; a lens spends it again.
     */
    var padding: Float = 0f

    /** The chain built so far. */
    var renderEffect: RenderEffect? = null

    /** Reach of the merge in pixels; above zero, the element is a member of its group's surface. */
    var mergeRadius: Float = 0f

    /** Whether the lens runs over the content as well; see `liquify`'s `refractsContent`. */
    var refractsContent: Boolean = false

    /** The lens over backdrop, surface and content together, while [refractsContent] is set. */
    var contentEffect: RenderEffect? = null

    /** `false` under [GlassLook.Frosted]: the lens and the merge stand down. */
    var refracts: Boolean = true

    /**
     * The colour the element lays over its glass, offered to the lens before the effects run — or
     * unspecified when the element draws it itself. See [fillFolded].
     */
    var fill: Color = Color.Unspecified

    /**
     * Whether the lens took [fill] in. A lens runs over every pixel of the pane anyway, so it lays the
     * colour down for a few multiplies; drawn on its own, the colour blended the whole pane a second
     * time on every frame.
     */
    var fillFolded: Boolean = false

    /**
     * Scratch for the four corner radii. The effects re-run on every frame something they read
     * animates, and they upload the values at once — one array per scope is enough.
     */
    val cornerRadiiScratch: FloatArray = FloatArray(4)

    /**
     * Fingerprint of what was last uploaded to the refraction program. A uniform write discards the
     * platform's built shader, so an effects block re-running because one animated value moved would
     * otherwise rebuild the program for nothing.
     */
    var lensUniforms: Long = Long.MIN_VALUE

    val colorFilters: ColorFilterCache = ColorFilterCache()

    /**
     * The colour work asked for so far — vibrancy and tint — as one matrix, not yet in the chain.
     *
     * A colour filter in front of a blur or a shader is a render pass of its own. Held here instead,
     * it rides along until the refraction or merge program takes it in for a few multiplies, and only
     * becomes a filter if nothing does, at the end of the chain. A blur in between does not stop it:
     * every matrix these effects build is linear on premultiplied colour, and a blur commutes with a
     * linear map — only the clamping moves.
     */
    val pendingColor: FloatArray = FloatArray(ColorMatrices.SIZE)

    var hasPendingColor: Boolean = false

    /**
     * A blur asked for in front of the lens and not yet in the chain, in pixels — or `0f`.
     *
     * The lens samples the backdrop anyway, and takes a blur this small in with nine taps there
     * ([com.hakim.liquify.internal.BLUR_TAPS_FUNCTION]) rather than as two more render passes per
     * pane. Only the first effect of a chain comes this way, and only up to [FoldedBlurMaxRadius]:
     * a wider blur would need a grid too coarse to pass for one. Anything that cannot take it in —
     * no lens after all, an effect of the caller's own — chains it as the blur it always was first;
     * see [flushBlur].
     */
    var foldedBlur: Float = 0f

    /** Where an effect builds its own matrix before handing it to [appendColor]. */
    val colorScratch: FloatArray = FloatArray(ColorMatrices.SIZE)

    /** [pendingColor] converted for a program's `colorMatrix` / `colorOffset` uniforms. */
    val colorUniformMatrix: FloatArray = FloatArray(9)
    val colorUniformOffset: FloatArray = FloatArray(3)

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader =
        runtimeShaderCache.obtainRuntimeShader(key, string)

    /** Adds [matrix] after whatever colour work is already pending. */
    fun appendColor(matrix: FloatArray) {
        if (hasPendingColor) {
            ColorMatrices.concat(first = pendingColor, second = matrix, out = pendingColor)
        } else {
            matrix.copyInto(pendingColor)
            hasPendingColor = true
        }
    }

    /** Chains a [foldedBlur] no lens took in as the blur effect it was asked for as. */
    fun flushBlur() {
        val radius = foldedBlur
        if (radius <= 0f) return
        foldedBlur = 0f
        renderEffect = BlurEffect(renderEffect, radius, radius, TileMode.Clamp)
    }

    /** Chains the pending colour work as a filter of its own, for anything that cannot take it in. */
    fun flushColor() {
        if (!hasPendingColor) return
        hasPendingColor = false
        if (!isRenderEffectSupported()) return
        renderEffect = ColorFilterEffect(renderEffect, colorFilters.matrix(pendingColor))
    }

    /** Returns `true` when anything the effects depend on changed and they must be re-applied. */
    fun update(density: Float, fontScale: Float, size: Size, layoutDirection: LayoutDirection): Boolean {
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

    fun apply(effects: BackdropEffectScope.() -> Unit) {
        padding = 0f
        renderEffect = null
        contentEffect = null
        mergeRadius = 0f
        hasPendingColor = false
        foldedBlur = 0f
        fillFolded = false
        effects()
    }

    open fun reset() {
        density = 1f
        fontScale = 1f
        size = Size.Unspecified
        layoutDirection = LayoutDirection.Ltr
        padding = 0f
        renderEffect = null
        refractsContent = false
        contentEffect = null
        mergeRadius = 0f
        hasPendingColor = false
        foldedBlur = 0f
        fill = Color.Unspecified
        fillFolded = false
        // The shaders the fingerprint describes go with the cache.
        lensUniforms = Long.MIN_VALUE
        colorFilters.clear()
        runtimeShaderCache.clear()
    }

    final override fun blur(radius: Dp, edgeTreatment: TileMode) {
        blurPx(radius.toPx(), edgeTreatment)
    }

    fun blurPx(radius: Float, edgeTreatment: TileMode = TileMode.Clamp) {
        if (!isRenderEffectSupported()) return
        if (radius <= 0f) return
        // Pending colour counts as an effect in front: the halo recorded for it is what the glass was
        // tuned against. The colour itself stays pending across the blur.
        if (edgeTreatment != TileMode.Clamp || renderEffect != null || hasPendingColor) {
            if (radius > padding) padding = radius
        }
        // First in the chain, small and refracted next: the lens takes it in. The halo above is the
        // same either way, so the glass records exactly what it did.
        if (renderEffect == null && foldedBlur == 0f && edgeTreatment == TileMode.Clamp &&
            refracts && !refractsContent && isRuntimeShaderSupported() &&
            radius <= FoldedBlurMaxRadius * density
        ) {
            foldedBlur = radius
            return
        }
        flushBlur()
        renderEffect = BlurEffect(renderEffect, radius, radius, edgeTreatment)
    }

    final override fun lens(
        refractionHeight: Dp,
        refractionAmount: Dp,
        depthEffect: Boolean,
        chromaticAberration: Boolean
    ) {
        lensPx(
            refractionHeight.toPx(),
            refractionAmount.toPx(),
            if (depthEffect) 1f else 0f,
            chromaticAberration
        )
    }

    /** [lens] in pixels, with the dome as a weight so a press can ease it in. */
    open fun lensPx(
        refractionHeight: Float,
        refractionAmount: Float,
        depthEffect: Float,
        chromaticAberration: Boolean
    ) {
        if (!refracts) return
        applyLens(refractionHeight, refractionAmount, depthEffect, chromaticAberration)
    }

    final override fun vibrancy(amount: Float) {
        if (amount == 1f) return
        if (!isRenderEffectSupported()) return
        ColorMatrices.controls(brightness = 0f, contrast = 1f, saturation = amount, out = colorScratch)
        appendColor(colorScratch)
    }

    final override fun tint(color: Color) {
        // Unspecified is not a colour, and its NaN alpha passes every comparison.
        if (!color.isSpecified || color.alpha <= 0f) return
        if (!isRenderEffectSupported()) return
        ColorMatrices.tint(color.red, color.green, color.blue, color.alpha, colorScratch)
        appendColor(colorScratch)
    }

    final override fun material(material: GlassMaterial) {
        applyMaterial(material, pressed = null, fraction = 0f)
    }

    final override fun merge(amount: Float) {
        if (!refracts || amount <= 0f) return
        val shortSide = size.minDimension
        if (shortSide <= 0f || shortSide.isNaN()) return
        mergeRadius = amount * shortSide * 0.5f
    }

    final override fun effect(effect: RenderEffect) {
        if (!isRenderEffectSupported()) return
        // An arbitrary effect has to see the blur and the colours asked for before it, so they go in
        // first.
        flushBlur()
        flushColor()
        renderEffect = renderEffect.chain(effect)
    }
}

/**
 * The widest blur the lens takes in, in dp: the light blur glass usually wears, and a third more. Past
 * it the nine taps would sit far enough apart to show as copies rather than a blur, so a wider one
 * stays an effect of its own.
 */
internal const val FoldedBlurMaxRadius: Float = 2f

/**
 * How far apart the lens sets its nine taps for a blur of [radius] pixels: the platform's sigma for
 * that radius, a fifth wider. The 1-2-1 grid has a smaller spread than the Gaussian it stands in for
 * at the same spacing, and the fifth makes up most of that without the taps standing apart.
 */
internal fun foldedBlurSpacing(radius: Float): Float =
    if (radius > 0f) (0.57735f * radius + 0.5f) * 1.2f else 0f

/** The effect scope of a single glass element. */
internal abstract class ElementEffectScope : BaseBackdropEffectScope()
