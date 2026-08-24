package com.hakim.liquify.group

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.hakim.liquify.Backdrop
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.GroupEffectScope
import com.hakim.liquify.LocalBackdrop
import com.hakim.liquify.RuntimeShader
import com.hakim.liquify.RuntimeShaderCacheImpl
import com.hakim.liquify.highlight.Highlight
import com.hakim.liquify.highlight.HighlightStyle
import com.hakim.liquify.internal.MAX_MERGED_ELEMENTS
import com.hakim.liquify.internal.RuntimeShaderEffect
import com.hakim.liquify.internal.UniformParam
import com.hakim.liquify.internal.UniformRadii
import com.hakim.liquify.internal.UniformRect
import com.hakim.liquify.internal.UniformTint
import com.hakim.liquify.internal.chain
import com.hakim.liquify.internal.mergeGlassShader
import com.hakim.liquify.internal.mergeGlassShaderKey
import com.hakim.liquify.internal.mergeSilhouetteMaskShaderKey
import com.hakim.liquify.internal.mergeSilhouetteShader
import com.hakim.liquify.internal.mergeSilhouetteShaderKey
import com.hakim.liquify.internal.recordLayer
import com.hakim.liquify.internal.setRuntimeShader
import com.hakim.liquify.isRenderEffectSupported
import com.hakim.liquify.isRuntimeShaderSupported
import com.hakim.liquify.material.rememberGlassEffects
import com.hakim.liquify.shadow.Shadow
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val UnitScale = Offset(1f, 1f)

private val DefaultHighlight: () -> Highlight? = { Highlight.Default }
private val DefaultShadow: () -> Shadow? = { Shadow.Default }

/**
 * A container whose glass children fuse into a single body of liquid glass.
 *
 * Children opt in by calling [merge][com.hakim.liquify.effects.merge] in their effects block. Each
 * member then stops rendering its own glass and instead contributes its signed distance field to
 * one shared surface, blended with a smooth minimum. Two members drifting together grow a bridge
 * between them and become one shape; pulling apart pinches the bridge until it snaps. Refraction,
 * rim light and shadow all follow the combined outline, so there is never a visible seam where two
 * members overlap.
 *
 * Members declare nothing but their shape — the backdrop, the material and the merge strength all
 * come from the group, because every member of one surface is by definition made of the same glass:
 *
 * ```
 * ProvideBackdrop(backdrop) {
 *     LiquidGlassGroup {
 *         Box(Modifier.size(88.dp).liquify(Capsule()))
 *         Box(Modifier.size(88.dp).liquify(Capsule()))
 *     }
 * }
 * ```
 *
 * The group lays its children out as a **`Box`**, so they stack on top of one another. Wrap them in
 * a `Row` or a `Column` to place them apart — which is usually what you want, since two members
 * only bridge once there is a gap between them to bridge.
 *
 * Set [merge] to `0f` to lay elements out together without fusing them, and use
 * the `effects` overload of [liquify][com.hakim.liquify.liquify] for a child that should stay a
 * separate pane of glass inside the group — an explicit effect stack never joins the merge unless
 * it calls [merge][com.hakim.liquify.effects.merge] itself.
 *
 * @param backdrop what the merged surface refracts. `null` resolves it from
 *   [ProvideBackdrop][com.hakim.liquify.ProvideBackdrop], the same way
 *   [liquify][com.hakim.liquify.liquify] does, so a screen that has already declared its backdrop
 *   does not have to repeat it here.
 * @param effects the material of the merged surface, inherited by every member. Defaults to the
 *   material from [LocalGlassMaterial][com.hakim.liquify.material.LocalGlassMaterial], so a group
 *   follows an app-wide glass change like everything else.
 * @param merge how strongly members fuse, relative to their own size. `1f` starts bridging at
 *   roughly half an element's short side; `0f` disables merging entirely.
 * @param highlight rim of the merged silhouette, rendered inside the merge program rather than as
 *   a stroke, since the combined outline has no [androidx.compose.ui.graphics.Outline].
 * @param shadow shadow cast by the combined silhouette.
 */
@Composable
public fun LiquidGlassGroup(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    effects: BackdropEffectScope.() -> Unit = rememberGlassEffects(),
    merge: Float = 1f,
    highlight: (() -> Highlight?)? = DefaultHighlight,
    shadow: (() -> Shadow?)? = DefaultShadow,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val resolvedBackdrop = backdrop ?: LocalBackdrop.current ?: error(
        "LiquidGlassGroup has no backdrop. Pass one explicitly, or wrap the subtree in " +
            "ProvideBackdrop { }."
    )

    val state = rememberLiquidGlassGroupState()
    val defaults = remember(resolvedBackdrop, effects, merge) {
        GroupDefaults(backdrop = resolvedBackdrop, effects = effects, mergeAmount = merge)
    }

    CompositionLocalProvider(
        LocalLiquidGlassGroup provides state,
        LocalGroupDefaults provides defaults,
        LocalBackdrop provides resolvedBackdrop
    ) {
        Box(
            modifier = modifier.liquidGlassGroup(
                state,
                resolvedBackdrop,
                effects,
                highlight,
                shadow
            ),
            contentAlignment = contentAlignment,
            content = content
        )
    }
}

/**
 * The drawing half of [LiquidGlassGroup], for layouts that cannot be a `Box`.
 *
 * You must still provide [LocalLiquidGlassGroup] with the same [state] around the children,
 * otherwise their `merge()` calls have nothing to register with.
 */
public fun Modifier.liquidGlassGroup(
    state: LiquidGlassGroupState,
    backdrop: Backdrop,
    effects: (BackdropEffectScope.() -> Unit)? = null,
    highlight: (() -> Highlight?)? = DefaultHighlight,
    shadow: (() -> Shadow?)? = DefaultShadow
): Modifier = this then LiquidGlassGroupElement(state, backdrop, effects, highlight, shadow)

private class LiquidGlassGroupElement(
    val state: LiquidGlassGroupState,
    val backdrop: Backdrop,
    val effects: (BackdropEffectScope.() -> Unit)?,
    val highlight: (() -> Highlight?)?,
    val shadow: (() -> Shadow?)?
) : ModifierNodeElement<LiquidGlassGroupNode>() {

    override fun create(): LiquidGlassGroupNode =
        LiquidGlassGroupNode(state, backdrop, effects, highlight, shadow)

    override fun update(node: LiquidGlassGroupNode) {
        if (node.state !== state) {
            node.state.onInvalidate = null
            node.state = state
            state.onInvalidate = { node.invalidateDraw() }
        }
        node.backdrop = backdrop
        node.effects = effects
        node.highlight = highlight
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "liquidGlassGroup"
        properties["state"] = state
        properties["backdrop"] = backdrop
        properties["effects"] = effects
        properties["highlight"] = highlight
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiquidGlassGroupElement) return false
        return state === other.state &&
            backdrop == other.backdrop &&
            effects == other.effects &&
            highlight == other.highlight &&
            shadow == other.shadow
    }

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + backdrop.hashCode()
        result = 31 * result + (effects?.hashCode() ?: 0)
        result = 31 * result + (highlight?.hashCode() ?: 0)
        result = 31 * result + (shadow?.hashCode() ?: 0)
        return result
    }
}

private class LiquidGlassGroupNode(
    var state: LiquidGlassGroupState,
    var backdrop: Backdrop,
    var effects: (BackdropEffectScope.() -> Unit)?,
    var highlight: (() -> Highlight?)?,
    var shadow: (() -> Shadow?)?
) : DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private val effectScope = GroupEffectScope()
    private val shaderCache = RuntimeShaderCacheImpl()
    private val active = ArrayList<GlassMember>(MAX_MERGED_ELEMENTS)

    /** The effects block the current chain was built from, and whether it has to be rebuilt. */
    private var appliedEffects: (BackdropEffectScope.() -> Unit)? = null
    private var effectsDirty = true

    /** Everything the recorded shadow depends on, so an unchanged one can simply be redrawn. */
    private var shadowFingerprint = Long.MIN_VALUE

    private var glassLayer: GraphicsLayer? = null
    private var shadowBlurLayer: GraphicsLayer? = null
    private var shadowLayer: GraphicsLayer? = null

    private val silhouettePaint = Paint()
    private val silhouetteMaskPaint = Paint().apply { blendMode = BlendMode.DstOut }

    private var appliedShadowRadius = Float.NaN

    override fun ContentDrawScope.draw() {
        val glassLayer = glassLayer
        if (glassLayer == null || !isRuntimeShaderSupported()) {
            return drawContent()
        }

        // Zero-sized members would still contribute a blob to the field, so they are dropped
        // rather than clamped.
        active.clear()
        for (member in state.members) {
            if (member.halfWidth > 0f && member.halfHeight > 0f) {
                active += member
                if (active.size == MAX_MERGED_ELEMENTS) break
            }
        }
        val count = active.size
        if (count == 0) return drawContent()

        val effectsBlock = effects ?: active[0].effects
        if (effectsBlock == null) return drawContent()

        // Union of the members, widened by the reach of the blend so the bridge and the outward
        // bulge a smooth minimum produces are inside the recorded area.
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        var maxMergeRadius = 0f
        for (i in 0 until count) {
            val member = active[i]
            left = minOf(left, member.centerX - member.halfWidth)
            top = minOf(top, member.centerY - member.halfHeight)
            right = maxOf(right, member.centerX + member.halfWidth)
            bottom = maxOf(bottom, member.centerY + member.halfHeight)
            maxMergeRadius = maxOf(maxMergeRadius, member.mergeRadius)
        }

        val bridgeMargin = maxMergeRadius * 0.5f
        left -= bridgeMargin
        top -= bridgeMargin
        right += bridgeMargin
        bottom += bridgeMargin

        val contentWidth = right - left
        val contentHeight = bottom - top

        val density = requireDensity()
        // The chain depends on the union size, the density and the block itself — never on where
        // the members sit inside it, since their geometry reaches the merge program through its
        // own uniforms further down. So a group that is merely being redrawn, because the page
        // scrolled behind it, keeps the effects it already has instead of allocating a blur, a
        // colour filter and a chain every frame.
        //
        // An effects block is free to read animated state, though, and that is invisible to the
        // check above — so the apply runs inside observeReads and a changed read marks it dirty.
        val scopeChanged = effectScope.update(
            density.density,
            density.fontScale,
            Size(contentWidth, contentHeight),
            layoutDirection
        )
        if (scopeChanged || effectsDirty || effectsBlock !== appliedEffects) {
            observeReads {
                effectScope.resetLens()
                effectScope.apply(effectsBlock)
            }
            appliedEffects = effectsBlock
            effectsDirty = false
        }

        val padding = effectScope.padding
        val layerLeft = floor(left - padding)
        val layerTop = floor(top - padding)
        val layerWidth = ceil(contentWidth + padding * 2f).toInt() + 1
        val layerHeight = ceil(contentHeight + padding * 2f).toInt() + 1
        if (layerWidth <= 0 || layerHeight <= 0) return drawContent()

        // A group whose members declare no colour compiles — and pays for — the plain program.
        // The two variants are cached under different keys, so switching between them costs one
        // shader compile the first time a colour appears and nothing afterwards.
        var tinted = false
        for (i in 0 until count) {
            val memberTint = active[i].tint
            if (memberTint.isSpecified && memberTint.alpha > 0f) {
                tinted = true
                break
            }
        }

        val highlight = highlight?.invoke()
        val glassShader = shaderCache.obtainRuntimeShader(mergeGlassShaderKey(count, tinted)) {
            mergeGlassShader(count, tinted)
        }
        glassShader.apply {
            writeMemberUniforms(active, count, density, tinted)
            setFloatUniform("offset", layerLeft, layerTop)
            setFloatUniform("aaWidth", 1f)
            setFloatUniform("refractionHeight", effectScope.refractionHeight)
            // Negative so the backdrop is pulled inwards, matching the single-element lens.
            setFloatUniform("refractionAmount", -effectScope.refractionAmount)
            setFloatUniform("depthEffect", effectScope.depthEffect)
            if (highlight != null && highlight.width.value > 0f) {
                val style = highlight.style
                setColorUniform("highlightColor", style.color.copy(alpha = 1f))
                setFloatUniform("highlightIntensity", style.color.alpha * highlight.alpha)
                setFloatUniform(
                    "highlightWidth",
                    highlight.width.toPx() * 2f + highlight.blurRadius.toPx()
                )
                setFloatUniform("highlightAngle", style.lightAngleRadians())
                setFloatUniform("highlightFalloff", style.lightFalloff())
            } else {
                setColorUniform("highlightColor", Color.White)
                setFloatUniform("highlightIntensity", 0f)
                setFloatUniform("highlightWidth", 0f)
                setFloatUniform("highlightAngle", 0f)
                setFloatUniform("highlightFalloff", 1f)
            }

        }

        drawMergedShadow(count, layerLeft, layerTop, layerWidth, layerHeight, density)

        recordLayer(glassLayer, density, IntSize(layerWidth, layerHeight)) {
            val canvas = drawContext.canvas
            canvas.translate(-layerLeft, -layerTop)
            with(backdrop) {
                drawBackdrop(density, this@LiquidGlassGroupNode.groupCoordinates, null)
            }
            canvas.translate(layerLeft, layerTop)
        }
        glassLayer.renderEffect =
            effectScope.renderEffect.chain(RuntimeShaderEffect(glassShader, "content"))
        glassLayer.topLeft = IntOffset(layerLeft.roundToInt(), layerTop.roundToInt())
        drawLayer(glassLayer)

        drawContent()
    }

    /**
     * Uploads each member's geometry to its own `float4` uniforms.
     *
     * One call per member per frame rather than three array uploads, because the programs declare
     * individual uniforms — see [mergeGlassShader] for why arrays are not an option.
     *
     * Only reachable past the [isRuntimeShaderSupported] guard at the top of [draw].
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun RuntimeShader.writeMemberUniforms(
        members: List<GlassMember>,
        count: Int,
        density: Density,
        tinted: Boolean
    ) {
        for (i in 0 until count) {
            val member = members[i]
            // A stretching member swells via a layer scale, which layout never sees. Applying the
            // very same factors to its box here is what keeps the glass welded to the content
            // instead of the content growing out of its own surface. A member that only leans
            // towards the finger has no scale to match, and its movement already came through
            // layout.
            val interaction = member.interaction
            val scale = if (interaction != null && interaction.stretches) {
                interaction.dragScale(
                    Size(member.halfWidth * 2f, member.halfHeight * 2f),
                    density
                )
            } else {
                UnitScale
            }
            val halfWidth = member.halfWidth * scale.x
            val halfHeight = member.halfHeight * scale.y
            val radiusScale = minOf(scale.x, scale.y)

            // The layer scale grows the element about its centre, which drags its *reported* top
            // left corner up and to the left; the registered centre inherited that shift. Adding it
            // back keeps the box growing symmetrically instead of leaning towards the origin.
            val centerX = member.centerX + member.halfWidth * (scale.x - 1f)
            val centerY = member.centerY + member.halfHeight * (scale.y - 1f)

            setFloatUniform(UniformRect[i], centerX, centerY, halfWidth, halfHeight)
            val radii = member.cornerRadii
            setFloatUniform(
                UniformRadii[i],
                radii[0] * radiusScale,
                radii[1] * radiusScale,
                radii[2] * radiusScale,
                radii[3] * radiusScale
            )
            // param.y carries the tint strength; the silhouette program has no colour uniforms at
            // all, so it is only ever written — never read — there.
            val memberTint = member.tint
            val tintAmount = if (memberTint.isSpecified) memberTint.alpha else 0f
            setFloatUniform(UniformParam[i], member.mergeRadius, tintAmount, 0f, 0f)
            if (tinted) {
                // Opaque: the strength travels in param.y, and a layout(color) uniform would
                // otherwise have its alpha applied twice.
                setColorUniform(
                    UniformTint[i],
                    if (memberTint.isSpecified) memberTint.copy(alpha = 1f) else Color.Transparent
                )
            }
        }
    }

    /**
     * Casts one shadow for the whole silhouette, then punches the silhouette back out of it so the
     * shadow never darkens the backdrop seen through the glass.
     *
     * Two layers are needed because the blur is a layer-wide render effect: the cut-out has to
     * happen after it, not with it.
     *
     * Only reachable past the [isRuntimeShaderSupported] guard at the top of [draw].
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun ContentDrawScope.drawMergedShadow(
        count: Int,
        layerLeft: Float,
        layerTop: Float,
        layerWidth: Int,
        layerHeight: Int,
        density: Density
    ) {
        if (!isRenderEffectSupported()) return
        val shadow = shadow?.invoke() ?: return
        val blurLayer = shadowBlurLayer ?: return
        val compositeLayer = shadowLayer ?: return

        val radius = shadow.radius.toPx()
        val offsetX = shadow.offset.x.toPx()
        val offsetY = shadow.offset.y.toPx()
        val margin = ceil(radius * 2f).toInt()

        val shadowWidth = layerWidth + margin * 2
        val shadowHeight = layerHeight + margin * 2
        val shadowLeft = layerLeft - margin
        val shadowTop = layerTop - margin

        // Nothing about this shadow depends on the backdrop, only on the silhouette and the shadow
        // itself — so redrawing the group because the page scrolled behind it does not need two
        // full-size layers recorded again. Alpha and blend mode stay out of the fingerprint: they
        // are layer properties, applied below without touching the recording.
        val fingerprint = shadowFingerprint(
            count, radius, offsetX, offsetY, shadow.color, shadowLeft, shadowTop,
            shadowWidth, shadowHeight
        )
        if (fingerprint != shadowFingerprint) {
            shadowFingerprint = fingerprint

            // The silhouette is sampled in the shadow layer's own space, so it needs its own
            // program instance: sharing one with the glass pass would mean sharing uniforms too.
            val silhouette = shaderCache.obtainRuntimeShader(mergeSilhouetteShaderKey(count)) {
                mergeSilhouetteShader(count)
            }.apply {
                writeMemberUniforms(active, count, density, tinted = false)
                setFloatUniform("aaWidth", 1f)
                setColorUniform("color", shadow.color.copy(alpha = 1f))
                setFloatUniform("colorAlpha", shadow.color.alpha)
                // Displaced by the shadow offset: the shadow falls where the shape would be if it
                // were that much further from the surface.
                setFloatUniform("offset", shadowLeft - offsetX, shadowTop - offsetY)
            }
            val cutout = shaderCache.obtainRuntimeShader(mergeSilhouetteMaskShaderKey(count)) {
                mergeSilhouetteShader(count)
            }.apply {
                writeMemberUniforms(active, count, density, tinted = false)
                setFloatUniform("aaWidth", 1f)
                setColorUniform("color", Color.White)
                setFloatUniform("colorAlpha", 1f)
                setFloatUniform("offset", shadowLeft, shadowTop)
            }

            silhouettePaint.setRuntimeShader(silhouette)
            silhouetteMaskPaint.setRuntimeShader(cutout)

            if (appliedShadowRadius != radius) {
                blurLayer.renderEffect =
                    if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
                appliedShadowRadius = radius
            }
            blurLayer.topLeft = IntOffset.Zero
            recordShadow(blurLayer, compositeLayer, shadowWidth, shadowHeight)
        }

        compositeLayer.alpha = shadow.alpha
        compositeLayer.blendMode = shadow.blendMode
        compositeLayer.topLeft = IntOffset(shadowLeft.roundToInt(), shadowTop.roundToInt())
        drawLayer(compositeLayer)
    }

    /**
     * Everything the recorded shadow is made of, folded into one value.
     *
     * A member that is being *stretched* deforms through a layer scale that layout never reports,
     * so its geometry here would look unchanged while the silhouette moves — that case gives up on
     * the comparison and re-records, which costs nothing in practice because it only lasts as long
     * as a finger is down.
     */
    private fun shadowFingerprint(
        count: Int,
        radius: Float,
        offsetX: Float,
        offsetY: Float,
        color: Color,
        shadowLeft: Float,
        shadowTop: Float,
        shadowWidth: Int,
        shadowHeight: Int
    ): Long {
        var hash = count * 31L + 17L
        hash = hash * 31L + radius.toRawBits()
        hash = hash * 31L + offsetX.toRawBits()
        hash = hash * 31L + offsetY.toRawBits()
        hash = hash * 31L + color.hashCode()
        hash = hash * 31L + shadowLeft.toRawBits()
        hash = hash * 31L + shadowTop.toRawBits()
        hash = hash * 31L + shadowWidth
        hash = hash * 31L + shadowHeight
        for (i in 0 until count) {
            val member = active[i]
            val interaction = member.interaction
            if (interaction != null && interaction.stretches) return Long.MIN_VALUE
            hash = hash * 31L + member.centerX.toRawBits()
            hash = hash * 31L + member.centerY.toRawBits()
            hash = hash * 31L + member.halfWidth.toRawBits()
            hash = hash * 31L + member.halfHeight.toRawBits()
            hash = hash * 31L + member.mergeRadius.toRawBits()
            val radii = member.cornerRadii
            for (corner in 0..3) hash = hash * 31L + radii[corner].toRawBits()
        }
        return hash
    }

    private fun ContentDrawScope.recordShadow(
        blurLayer: GraphicsLayer,
        compositeLayer: GraphicsLayer,
        shadowWidth: Int,
        shadowHeight: Int
    ) {
        blurLayer.record(IntSize(shadowWidth, shadowHeight)) {
            drawContext.canvas.drawRect(
                0f, 0f, shadowWidth.toFloat(), shadowHeight.toFloat(), silhouettePaint
            )
        }

        compositeLayer.record(IntSize(shadowWidth, shadowHeight)) {
            drawLayer(blurLayer)
            drawContext.canvas.drawRect(
                0f, 0f, shadowWidth.toFloat(), shadowHeight.toFloat(), silhouetteMaskPaint
            )
        }
    }

    private var groupCoordinates: LayoutCoordinates? = null

    private var lastPosition: Offset = Offset.Unspecified

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        groupCoordinates = coordinates
        state.coordinates = coordinates

        // Members only ask for a redraw when they move *relative to the group*, so scrolling the
        // page past a settled group changed nothing and its glass kept showing the backdrop from
        // wherever it used to be. The group's own position is the missing signal.
        val position = coordinates.positionInRoot()
        if (position != lastPosition) {
            lastPosition = position
            invalidateDraw()
        }
    }

    /**
     * State the effects block reads has changed, so the cached chain no longer describes it.
     *
     * The block runs during draw — the union size it needs only exists then — so this cannot
     * rebuild here; it marks the chain stale and lets the next draw do the work.
     */
    override fun onObservedReadsChanged() {
        effectsDirty = true
        invalidateDraw()
    }

    override fun onAttach() {
        val context = requireGraphicsContext()
        glassLayer = context.createGraphicsLayer()
        shadowBlurLayer = context.createGraphicsLayer()
        shadowLayer = context.createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
        state.onInvalidate = { invalidateDraw() }
    }

    override fun onDetach() {
        val context = requireGraphicsContext()
        glassLayer?.let { context.releaseGraphicsLayer(it) }
        shadowBlurLayer?.let { context.releaseGraphicsLayer(it) }
        shadowLayer?.let { context.releaseGraphicsLayer(it) }
        glassLayer = null
        shadowBlurLayer = null
        shadowLayer = null
        groupCoordinates = null
        state.coordinates = null
        state.onInvalidate = null
        shaderCache.clear()
        effectScope.reset()
        appliedShadowRadius = Float.NaN
        appliedEffects = null
        effectsDirty = true
        shadowFingerprint = Long.MIN_VALUE
    }
}

/** Direction of the rim light, mapped from whichever [HighlightStyle] the group was given. */
private fun HighlightStyle.lightAngleRadians(): Float = when (this) {
    is HighlightStyle.Default -> angle.degreesToRadians()
    is HighlightStyle.Ambient -> angle.degreesToRadians()
    else -> 45f.degreesToRadians()
}

/**
 * A falloff of zero makes `pow(|cos|, 0)` equal one everywhere, which is exactly the uniform
 * hairline [HighlightStyle.Plain] describes.
 */
private fun HighlightStyle.lightFalloff(): Float = when (this) {
    is HighlightStyle.Default -> falloff
    is HighlightStyle.Ambient -> falloff
    is HighlightStyle.Plain -> 0f
    is HighlightStyle.Dynamic -> falloff
    else -> 1f
}

private fun Float.degreesToRadians(): Float = (this * (PI / 180.0)).toFloat()
