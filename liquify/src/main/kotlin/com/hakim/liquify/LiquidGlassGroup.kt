package com.hakim.liquify

import android.os.Build
import android.os.SystemClock
import androidx.annotation.FloatRange
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.hakim.liquify.internal.ColorMatrices
import com.hakim.liquify.internal.GlassMember
import com.hakim.liquify.internal.GroupDefaults
import com.hakim.liquify.internal.LiquidGlassGroupState
import com.hakim.liquify.internal.LocalGroupDefaults
import com.hakim.liquify.internal.LocalLiquidGlassGroup
import com.hakim.liquify.internal.MAX_MERGED_ELEMENTS
import com.hakim.liquify.internal.RuntimeShader
import com.hakim.liquify.internal.RuntimeShaderCacheImpl
import com.hakim.liquify.internal.RuntimeShaderEffect
import com.hakim.liquify.internal.TouchLightReach
import com.hakim.liquify.internal.UniformBlend
import com.hakim.liquify.internal.UniformFlow
import com.hakim.liquify.internal.UniformGlow
import com.hakim.liquify.internal.UniformLight
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
import com.hakim.liquify.internal.setColorMatrixUniforms
import com.hakim.liquify.internal.setRuntimeShader
import com.hakim.liquify.internal.touchLightColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val UnitScale = Offset(1f, 1f)

/**
 * A container whose glass children fuse into one body of liquid glass.
 *
 * Every `liquify` inside it becomes a member: it stops rendering its own pane and contributes its
 * shape to one shared surface, blended with a smooth minimum. Two members drifting together grow a
 * bridge and become one shape; pulling apart pinches the bridge until it snaps. Refraction — its dome
 * and its chromatic aberration too — rim and shadow follow the combined outline, so there is never a
 * seam.
 *
 * ```
 * LiquidGlassGroup {
 *     Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
 *         Box(Modifier.size(56.dp).liquify(Capsule(), tint = Color.Red.copy(alpha = 0.6f)))
 *         Box(Modifier.size(56.dp).liquify(Capsule()))
 *     }
 * }
 * ```
 *
 * Members name nothing but their shape and colour: backdrop, material, rim and shadow are the
 * group's, and a member's `tint` becomes its colour in the body, blended through the bridges. Its
 * touch light goes the same way: a held member lights up in its own colour, and the light flows on
 * through a bridge into the colour beyond — never across a gap, and never cut off at its outline. An
 * element renders its own pane instead when it has its own `effects`, when it sits inside another
 * glass element — a clear button inside a search field — or when [merge] is `0f`. Merging is a
 * shader, so under [GlassLook.Frosted] and [GlassLook.Solid] every member is a pane of its own.
 *
 * The group lays its children out as a `Box`, so they stack: wrap them in a `Row` or a `Column` to
 * place them apart. Up to twelve members merge; cost is linear in their number, so keep groups tight.
 *
 * @param material the glass of the whole body, inherited by every child.
 * @param merge how strongly members fuse, relative to their own size. Two members bridge once the
 *   reach exceeds twice the gap between them: two 48 dp buttons 8 dp apart need about `0.7`, and look
 *   properly gooey around `1.2`.
 * @param backdrop what the body refracts; inherited from [LiquifyTheme] when `null`.
 * @param effects a hand-written recipe for the whole body, instead of [material]'s.
 * @throws IllegalStateException when the group merges and has no backdrop, given or inherited.
 */
@Composable
public fun LiquidGlassGroup(
    modifier: Modifier = Modifier,
    material: GlassMaterial = LiquifyTheme.material,
    @FloatRange(from = 0.0) merge: Float = 1f,
    backdrop: Backdrop? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    effects: (BackdropEffectScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val resolvedBackdrop = backdrop ?: LocalBackdrop.current
    val merges = merge > 0f && LocalGlassLook.current == GlassLook.Liquid && isRuntimeShaderSupported()
    // Only a merged body samples. Members of a group that does not merge are panes of their own, and
    // under GlassLook.Solid nothing samples at all, so a backdrop is only required here.
    val bodyBackdrop = if (merges) resolvedBackdrop ?: noGroupBackdrop() else null
    val groupEffects = effects ?: remember(material) { materialEffects(material) }
    val state = remember { LiquidGlassGroupState() }
    val defaults = remember(bodyBackdrop, groupEffects, merge) {
        bodyBackdrop?.let { GroupDefaults(it, groupEffects, merge) }
    }
    val darkTheme = LiquifyTheme.darkTheme

    CompositionLocalProvider(
        LocalLiquidGlassGroup provides if (defaults != null) state else null,
        LocalGroupDefaults provides defaults,
        LocalBackdrop provides resolvedBackdrop,
        // Members of one body are the same glass, so the group states it once for its subtree — for
        // children that do not merge as well.
        LocalGlassMaterial provides material,
    ) {
        Box(
            modifier = if (bodyBackdrop != null) {
                modifier.then(
                    LiquidGlassGroupElement(
                        state = state,
                        backdrop = bodyBackdrop,
                        effects = groupEffects,
                        rim = material.rim,
                        shadow = material.shadow,
                        darkTheme = darkTheme
                    )
                )
            } else {
                modifier
            },
            contentAlignment = contentAlignment,
            content = content
        )
    }
}

private fun noGroupBackdrop(): Nothing =
    error("LiquidGlassGroup has no backdrop. Pass one, or declare it with LiquifyTheme(backdrop = …).")

/**
 * The body's recipe: the material's tint folded in front of its stack. A member's own colour is laid
 * over that by the merge program.
 */
private fun materialEffects(material: GlassMaterial): BackdropEffectScope.() -> Unit = {
    tint(material.tint)
    material(material)
}

private class LiquidGlassGroupElement(
    val state: LiquidGlassGroupState,
    val backdrop: Backdrop,
    val effects: BackdropEffectScope.() -> Unit,
    val rim: GlassRim?,
    val shadow: GlassShadow?,
    val darkTheme: Boolean
) : ModifierNodeElement<LiquidGlassGroupNode>() {

    override fun create(): LiquidGlassGroupNode =
        LiquidGlassGroupNode(state, backdrop, effects, rim, shadow, darkTheme)

    override fun update(node: LiquidGlassGroupNode) {
        if (node.state !== state) {
            node.state.onInvalidate = null
            node.state = state
            state.onInvalidate = { node.invalidateDraw() }
        }
        node.backdrop = backdrop
        node.effects = effects
        node.rim = rim
        if (node.shadow != shadow || node.darkTheme != darkTheme) {
            // The recorded shadow is painted in the resolved ink, so it has to be laid down again.
            node.invalidateShadow()
        }
        node.shadow = shadow
        node.darkTheme = darkTheme
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "liquidGlassGroup"
        properties["backdrop"] = backdrop
        properties["rim"] = rim
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiquidGlassGroupElement) return false
        return state === other.state &&
            backdrop == other.backdrop &&
            effects === other.effects &&
            rim == other.rim &&
            shadow == other.shadow &&
            darkTheme == other.darkTheme
    }

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + backdrop.hashCode()
        result = 31 * result + effects.hashCode()
        result = 31 * result + (rim?.hashCode() ?: 0)
        result = 31 * result + (shadow?.hashCode() ?: 0)
        result = 31 * result + darkTheme.hashCode()
        return result
    }
}

private class LiquidGlassGroupNode(
    var state: LiquidGlassGroupState,
    var backdrop: Backdrop,
    var effects: BackdropEffectScope.() -> Unit,
    var rim: GlassRim?,
    var shadow: GlassShadow?,
    var darkTheme: Boolean
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

    /**
     * The merged program in use and the variant it was resolved for. It only changes when a member
     * joins or leaves, or when the first coloured member appears.
     */
    private var glassShader: RuntimeShader? = null
    private var glassMemberCount = -1
    private var glassTinted = false

    /**
     * Everything last uploaded to that program, folded into one value. A page scrolling behind a
     * settled group changes none of it, so that frame writes no uniforms at all.
     */
    private var glassUniforms = Long.MIN_VALUE

    /**
     * The finished chain, kept between frames. A runtime-shader effect copies the uniforms when it is
     * built, so this is reused only while the fingerprint above says they are unchanged.
     */
    private var composedRenderEffect: RenderEffect? = null

    private var glassLayer: GraphicsLayer? = null
    private var shadowBlurLayer: GraphicsLayer? = null
    private var shadowLayer: GraphicsLayer? = null

    private val silhouettePaint = Paint()
    private val silhouetteMaskPaint = Paint().apply { blendMode = BlendMode.DstOut }

    private var appliedShadowRadius = Float.NaN

    /** Where the glass layer is being recorded, for [recordGlassBlock]. */
    private var recordLeft = 0f
    private var recordTop = 0f

    private val recordGlassBlock: DrawScope.() -> Unit = {
        val scope = this
        val canvas = drawContext.canvas
        val left = recordLeft
        val top = recordTop
        canvas.translate(-left, -top)
        with(backdrop) { scope.drawBackdrop(scope, groupCoordinates, null) }
        canvas.translate(left, top)
    }

    /** Forces the recorded shadow to be laid down again on the next draw. */
    fun invalidateShadow() {
        shadowFingerprint = Long.MIN_VALUE
    }

    override fun ContentDrawScope.draw() {
        val glassLayer = glassLayer
        if (glassLayer == null || !isRuntimeShaderSupported()) {
            return drawContent()
        }

        // Zero-sized members would still contribute a blob to the field, so they are dropped. Indexed
        // rather than a for-in, which would allocate an iterator on every frame.
        active.clear()
        val members = state.members
        for (i in 0 until members.size) {
            val member = members[i]
            if (member.halfWidth > 0f && member.halfHeight > 0f) {
                active += member
                if (active.size == MAX_MERGED_ELEMENTS) break
            }
        }
        val count = active.size
        if (count == 0) return drawContent()

        val effectsBlock = effects
        val density = requireDensity()
        updateDrawnGeometry(count, density)
        updateTouchLights(count)

        // Union of the members as drawn, widened by the reach of the blend so the bridge and the
        // outward bulge of a smooth minimum are inside the recorded area.
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        var maxMergeRadius = 0f
        for (i in 0 until count) {
            val member = active[i]
            left = minOf(left, member.drawnCenterX - member.drawnHalfWidth)
            top = minOf(top, member.drawnCenterY - member.drawnHalfHeight)
            right = maxOf(right, member.drawnCenterX + member.drawnHalfWidth)
            bottom = maxOf(bottom, member.drawnCenterY + member.drawnHalfHeight)
            maxMergeRadius = maxOf(maxMergeRadius, member.mergeRadius)
        }

        val bridgeMargin = maxMergeRadius * 0.5f
        left -= bridgeMargin
        top -= bridgeMargin
        right += bridgeMargin
        bottom += bridgeMargin

        val contentWidth = right - left
        val contentHeight = bottom - top

        // The chain depends on the union size, the density and the block — never on where members sit
        // inside it, which reaches the merge program through uniforms. A group merely redrawn because
        // the page scrolled keeps its chain. A block that reads animated state marks it dirty through
        // the observation below.
        val scopeChanged = effectScope.update(
            density.density,
            density.fontScale,
            Size(contentWidth, contentHeight),
            layoutDirection
        )
        var chainChanged = false
        if (scopeChanged || effectsDirty || effectsBlock !== appliedEffects) {
            observeReads {
                effectScope.resetLens()
                effectScope.apply(effectsBlock)
            }
            appliedEffects = effectsBlock
            effectsDirty = false
            chainChanged = true
        }

        val padding = effectScope.padding
        val layerLeft = floor(left - padding)
        val layerTop = floor(top - padding)
        val layerWidth = ceil(contentWidth + padding * 2f).toInt() + 1
        val layerHeight = ceil(contentHeight + padding * 2f).toInt() + 1
        if (layerWidth <= 0 || layerHeight <= 0) return drawContent()

        // A group whose members declare no colour compiles the plain program; the tinted one is cached
        // under its own key, so the first colour costs one compile and nothing afterwards.
        var tinted = false
        for (i in 0 until count) {
            val memberTint = active[i].tint
            if (memberTint.isSpecified && memberTint.alpha > 0f) {
                tinted = true
                break
            }
        }

        val rim = rim

        // Colour carried from a dragged member into the ones it is pushed into; stepped once per frame.
        val flowing = updateColourFlow(count, tinted)
        keepFlowing(flowing)

        var shader = glassShader
        if (shader == null || glassMemberCount != count || glassTinted != tinted) {
            shader = shaderCache.obtainRuntimeShader(mergeGlassShaderKey(count, tinted)) {
                mergeGlassShader(count, tinted)
            }
            glassShader = shader
            glassMemberCount = count
            glassTinted = tinted
            glassUniforms = Long.MIN_VALUE
            chainChanged = true
        }

        val fingerprint = glassUniformFingerprint(count, tinted, layerLeft, layerTop, rim, density)
        if (fingerprint != glassUniforms) {
            glassUniforms = fingerprint
            // How far this rim reaches past the silhouette, so the reject at the top of the program
            // keeps the pixels its outer band is drawn on.
            val rimOutset = with(density) { rim?.style?.outsetWidth?.toPx() ?: 0f }
            val widestBand = shader.writeMemberUniforms(active, count, tinted, rimOutset)
            shader.writeLightUniforms(active, count)
            shader.writeSurfaceUniforms(layerLeft, layerTop, widestBand, rim, density, tinted)
            // The effect below copies the uniforms as they are now.
            chainChanged = true
        }

        drawMergedShadow(count, layerLeft, layerTop, layerWidth, layerHeight, density)

        recordLeft = layerLeft
        recordTop = layerTop
        recordLayer(glassLayer, density, IntSize(layerWidth, layerHeight), recordGlassBlock)

        var renderEffect = composedRenderEffect
        if (chainChanged || renderEffect == null) {
            renderEffect = effectScope.renderEffect.chain(RuntimeShaderEffect(shader, "content"))
            composedRenderEffect = renderEffect
        }
        glassLayer.renderEffect = renderEffect
        glassLayer.topLeft = IntOffset(layerLeft.roundToInt(), layerTop.roundToInt())
        drawLayer(glassLayer)

        drawContent()
    }

    /**
     * Everything written to the merged glass program, folded into one value. A stretching member
     * deforms through a layer scale layout never reports, so its press and travel stand in for it.
     */
    private fun glassUniformFingerprint(
        count: Int,
        tinted: Boolean,
        layerLeft: Float,
        layerTop: Float,
        rim: GlassRim?,
        density: Density
    ): Long {
        var hash = count * 31L + if (tinted) 7L else 3L
        hash = hash * 31L + layerLeft.toRawBits()
        hash = hash * 31L + layerTop.toRawBits()
        // The colour work of the effects in front, which this program applies itself.
        hash = hash * 31L +
            if (effectScope.hasPendingColor) {
                ColorMatrices.fingerprint(effectScope.pendingColor)
            } else {
                0L
            }
        hash = hash * 31L + effectScope.lensFingerprint()
        hash = hash * 31L + effectScope.foldedBlur.toRawBits()
        hash = hash * 31L + density.density.toRawBits()
        hash = hash * 31L + (rim?.hashCode() ?: 0)
        // A rim that resolves its colour against the theme hashes the same in both.
        hash = hash * 31L + if (darkTheme) 1L else 0L
        for (i in 0 until count) {
            val member = active[i]
            hash = hash * 31L + member.centerX.toRawBits()
            hash = hash * 31L + member.centerY.toRawBits()
            hash = hash * 31L + member.halfWidth.toRawBits()
            hash = hash * 31L + member.halfHeight.toRawBits()
            val radii = member.cornerRadii
            for (corner in 0..3) hash = hash * 31L + radii[corner].toRawBits()
            hash = hash * 31L + member.mergeRadius.toRawBits()
            hash = hash * 31L + member.tint.hashCode()
            hash = hash * 31L + member.flowAmount.toRawBits()
            hash = hash * 31L + member.lightStrength.toRawBits()
            if (member.lightStrength > 0f) {
                hash = hash * 31L + member.lightX.toRawBits()
                hash = hash * 31L + member.lightY.toRawBits()
                hash = hash * 31L + member.lightReach.toRawBits()
                hash = hash * 31L + member.lightColor.hashCode()
            }
            val interaction = member.interaction
            if (interaction != null && interaction.stretches) {
                hash = hash * 31L + interaction.pressProgress.toRawBits()
                val offset = interaction.offset
                hash = hash * 31L + offset.x.toRawBits()
                hash = hash * 31L + offset.y.toRawBits()
            }
        }
        hash = hash * 31L + flowSourceIndex
        return hash
    }

    /**
     * Uploads the surface's own parameters: where the layer sits, the widest band of any member —
     * which bounds where the program takes a gradient — and the rim.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun RuntimeShader.writeSurfaceUniforms(
        layerLeft: Float,
        layerTop: Float,
        widestBand: Float,
        rim: GlassRim?,
        density: Density,
        tinted: Boolean
    ) {
        setFloatUniform("offset", layerLeft, layerTop)
        setFloatUniform("aaInverse", 1f)
        // The effects in front leave their colour work pending; this program takes it in.
        setColorMatrixUniforms(effectScope, effectScope.hasPendingColor)
        // Only the tinted program declares them.
        if (tinted) writeFlowUniforms()

        setFloatUniform("refractionHeight", widestBand)
        setFloatUniform("depthEffect", effectScope.depthEffect)
        setFloatUniform("dispersion", if (effectScope.chromaticAberration) 1f else 0f)
        setFloatUniform("blurSpacing", foldedBlurSpacing(effectScope.foldedBlur))

        if (rim != null && rim.width.value > 0f) {
            val style = rim.style.resolvedFor(darkTheme)
            setColorUniform("highlightColor", style.color.copy(alpha = 1f))
            setFloatUniform("highlightIntensity", style.color.alpha * rim.alpha)
            // The vector rather than its angle: a cos() and a sin() per pixel saved.
            val angle = style.lightAngleRadians()
            setFloatUniform("highlightNormal", cos(angle), sin(angle))
            setFloatUniform("highlightFalloff", style.lightFalloff())

            with(density) {
                // A two-band rim carries the same edge onto the merged silhouette as onto a pane: a lit
                // line inside it and a shadow line outside. Every other rim keeps the doubled stroke
                // width the single-element path clips in half.
                when (style) {
                    is GlassRimStyle.TwSe -> {
                        setColorUniform("highlightShadeColor", style.inverseColor.copy(alpha = 1f))
                        setFloatUniform("highlightShadeIntensity", style.inverseColor.alpha * rim.alpha)
                        setFloatUniform("highlightWidth", style.innerWidth.toPx())
                        setFloatUniform("highlightOuterWidth", style.outerWidth.toPx())
                        setFloatUniform("highlightAmbient", style.ambient)
                        setFloatUniform("highlightSolid", 0f)
                    }

                    is GlassRimStyle.Outline -> {
                        // A line rather than light, laid over the glass at its own width.
                        setColorUniform("highlightShadeColor", Color.Black)
                        setFloatUniform("highlightShadeIntensity", 0f)
                        setFloatUniform("highlightWidth", rim.width.toPx())
                        setFloatUniform("highlightOuterWidth", 0f)
                        setFloatUniform("highlightAmbient", 0f)
                        setFloatUniform("highlightSolid", 1f)
                    }

                    else -> {
                        setColorUniform("highlightShadeColor", Color.Black)
                        setFloatUniform("highlightShadeIntensity", 0f)
                        setFloatUniform("highlightWidth", rim.width.toPx() * 2f + rim.blurRadius.toPx())
                        setFloatUniform("highlightOuterWidth", 0f)
                        setFloatUniform("highlightAmbient", 0f)
                        setFloatUniform("highlightSolid", 0f)
                    }
                }
            }
        } else {
            setColorUniform("highlightColor", Color.White)
            setFloatUniform("highlightIntensity", 0f)
            setFloatUniform("highlightWidth", 0f)
            setFloatUniform("highlightNormal", 1f, 0f)
            setFloatUniform("highlightFalloff", 1f)
            setColorUniform("highlightShadeColor", Color.Black)
            setFloatUniform("highlightShadeIntensity", 0f)
            setFloatUniform("highlightOuterWidth", 0f)
            setFloatUniform("highlightAmbient", 0f)
            setFloatUniform("highlightSolid", 0f)
        }
    }

    /**
     * Uploads each member's geometry, colour and refraction to its own uniforms, and the bounding box
     * they add up to — one call per member rather than array uploads, because SkSL only allows
     * constant indices into uniforms.
     *
     * Each member is refracted by what the recorded lens gives its *laid-out* size, as a single pane
     * measures its layout size: a press should not turn up the lens.
     *
     * @return the widest refracting band of any member, in pixels.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun RuntimeShader.writeMemberUniforms(
        members: List<GlassMember>,
        count: Int,
        tinted: Boolean,
        rimOutset: Float
    ): Float {
        var boundsLeft = Float.POSITIVE_INFINITY
        var boundsTop = Float.POSITIVE_INFINITY
        var boundsRight = Float.NEGATIVE_INFINITY
        var boundsBottom = Float.NEGATIVE_INFINITY
        var maxMergeRadius = 0f
        var widestBand = 0f

        for (i in 0 until count) {
            val member = members[i]
            // The reach this member is folded in with: the widest of its own and every earlier one's.
            if (i > 0) {
                val reach = maxOf(maxMergeRadius, member.mergeRadius)
                setFloatUniform(UniformBlend[i], reach, if (reach > 0f) 0.5f / reach else 0f)
            }
            val centerX = member.drawnCenterX
            val centerY = member.drawnCenterY
            val halfWidth = member.drawnHalfWidth
            val halfHeight = member.drawnHalfHeight
            val radiusScale = member.drawnRadiusScale

            setFloatUniform(UniformRect[i], centerX, centerY, halfWidth, halfHeight)
            boundsLeft = minOf(boundsLeft, centerX - halfWidth)
            boundsTop = minOf(boundsTop, centerY - halfHeight)
            boundsRight = maxOf(boundsRight, centerX + halfWidth)
            boundsBottom = maxOf(boundsBottom, centerY + halfHeight)
            maxMergeRadius = maxOf(maxMergeRadius, member.mergeRadius)

            val radii = member.cornerRadii
            setFloatUniform(
                UniformRadii[i],
                radii[0] * radiusScale,
                radii[1] * radiusScale,
                radii[2] * radiusScale,
                radii[3] * radiusScale
            )
            // param.y carries the tint strength and param.zw the refraction.
            val memberTint = member.tint
            val tintAmount = if (memberTint.isSpecified) memberTint.alpha else 0f
            val memberWidth = member.halfWidth * 2f
            val memberHeight = member.halfHeight * 2f
            val band = effectScope.memberRefractionHeight(memberWidth, memberHeight)
            // Negative, so the backdrop is pulled inwards like the single-element lens.
            val displacement = -effectScope.memberRefractionAmount(memberWidth, memberHeight)
            widestBand = maxOf(widestBand, band)
            setFloatUniform(UniformParam[i], member.mergeRadius, tintAmount, band, displacement)
            if (tinted) {
                // Opaque: the strength travels in param.y, and a layout(color) uniform would otherwise
                // have its alpha applied twice.
                setColorUniform(
                    UniformTint[i],
                    if (memberTint.isSpecified) memberTint.copy(alpha = 1f) else Color.Transparent
                )
                // The program carries one source's colour at a time; a member still holding another's
                // lets it go at once rather than turning into the wrong colour.
                val source = flowSourceMember()
                val flow = if (source != null && member.flowSource === source) member.flowAmount else 0f
                setFloatUniform(UniformFlow[i], flow, 1f / maxOf(memberWidth, memberHeight))
            }
        }

        // How far outside the members anything this program draws can reach. The blend saturates, so
        // the field never goes deeper than one blend radius below the nearest member's own distance,
        // however many members there are; a rim's outer band and two pixels of coverage ramp go on top.
        val margin = maxMergeRadius + rimOutset + 2f
        setFloatUniform(
            "bounds",
            boundsLeft - margin,
            boundsTop - margin,
            boundsRight + margin,
            boundsBottom + margin
        )
        return widestBand
    }

    /**
     * Uploads each member's touch light to the glass program — the one program that declares them.
     * A member nobody holds gets no strength, which is all the program checks.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun RuntimeShader.writeLightUniforms(members: List<GlassMember>, count: Int) {
        for (i in 0 until count) {
            val member = members[i]
            val strength = member.lightStrength
            if (strength > 0f) {
                setFloatUniform(UniformLight[i], member.lightX, member.lightY, member.lightReach, strength)
                // Opaque: the strength is what dims it; see TOUCH_LIGHT_FUNCTION.
                setColorUniform(UniformGlow[i], member.lightColor.copy(alpha = 1f))
            } else {
                setFloatUniform(UniformLight[i], 0f, 0f, 0f, 0f)
            }
        }
    }

    /**
     * Casts one shadow for the whole silhouette, then punches the silhouette out of it so it never
     * darkens the backdrop seen through the glass. Two layers, because the blur is a layer-wide
     * render effect: the cut-out has to happen after it.
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
        val shadow = shadow ?: return
        val blurLayer = shadowBlurLayer ?: return
        val compositeLayer = shadowLayer ?: return

        val shadowColor = shadow.resolvedColor(darkTheme)
        val radius = shadow.radius.toPx()
        val offsetX = shadow.offset.x.toPx()
        val offsetY = shadow.offset.y.toPx()
        val margin = ceil(radius * 2f).toInt()

        val shadowWidth = layerWidth + margin * 2
        val shadowHeight = layerHeight + margin * 2
        val shadowLeft = layerLeft - margin
        val shadowTop = layerTop - margin

        // The shadow depends only on the silhouette and itself, not on the backdrop — a group redrawn
        // because the page scrolled does not need two full-size layers recorded again. Alpha and blend
        // mode are layer properties and stay out of it.
        val fingerprint = shadowFingerprint(
            count, radius, offsetX, offsetY, shadowColor, shadowLeft, shadowTop,
            shadowWidth, shadowHeight
        )
        if (fingerprint != shadowFingerprint) {
            shadowFingerprint = fingerprint

            // Sampled in the shadow layer's own space, so it needs its own program instance.
            val silhouette = shaderCache.obtainRuntimeShader(mergeSilhouetteShaderKey(count)) {
                mergeSilhouetteShader(count)
            }.apply {
                writeMemberUniforms(active, count, tinted = false, rimOutset = 0f)
                setFloatUniform("aaInverse", 1f)
                setColorUniform("color", shadowColor.copy(alpha = 1f))
                setFloatUniform("colorAlpha", shadowColor.alpha)
                // The shadow falls where the shape would be if it were further from the surface.
                setFloatUniform("offset", shadowLeft - offsetX, shadowTop - offsetY)
            }
            val cutout = shaderCache.obtainRuntimeShader(mergeSilhouetteMaskShaderKey(count)) {
                mergeSilhouetteShader(count)
            }.apply {
                writeMemberUniforms(active, count, tinted = false, rimOutset = 0f)
                setFloatUniform("aaInverse", 1f)
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
     * Everything the recorded shadow is made of. A member being stretched deforms through a layer
     * scale layout never reports, so that case gives up on the comparison for as long as it lasts.
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

        // Members only ask for a redraw when they move *relative to the group*, so the group's own
        // position is the signal that a page scrolled past it.
        val position = coordinates.positionInRoot()
        if (position != lastPosition) {
            lastPosition = position
            invalidateDraw()
        }
    }

    /** State the effects block reads changed; the next draw rebuilds the chain. */
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

    /**
     * Works out where each member is drawn this frame: its laid-out box swollen by the stretch a
     * dragged member applies as a layer scale. The recorded layer, the field and the colour flow are
     * all built from this one box, so a member stretched far is never drawn past what was recorded.
     */
    private fun updateDrawnGeometry(count: Int, density: Density) {
        for (i in 0 until count) {
            val member = active[i]
            val interaction = member.interaction
            val scale = if (interaction != null && interaction.stretches) {
                interaction.dragScale(Size(member.halfWidth * 2f, member.halfHeight * 2f), density)
            } else {
                UnitScale
            }
            // A layer scale grows the element about its centre, dragging its reported top left up and
            // to the left; adding that back keeps the box growing symmetrically.
            member.drawnCenterX = member.centerX + member.halfWidth * (scale.x - 1f)
            member.drawnCenterY = member.centerY + member.halfHeight * (scale.y - 1f)
            member.drawnHalfWidth = member.halfWidth * scale.x
            member.drawnHalfHeight = member.halfHeight * scale.y
            member.drawnRadiusScale = minOf(scale.x, scale.y)
        }
    }

    /**
     * Works out each member's touch light for this frame, from the box it is drawn in: where the
     * finger is, how far the light reaches, how strong it is and its colour. A merged member's own
     * light stands down, so this is the only place it is drawn — and reading the press and the finger
     * here is what redraws the group while either moves.
     */
    private fun updateTouchLights(count: Int) {
        for (i in 0 until count) {
            val member = active[i]
            val interaction = member.interaction
            val glow = if (member.glows && interaction != null) interaction.glowAmount else 0f
            if (glow <= 0f || interaction == null) {
                member.lightStrength = 0f
                continue
            }
            val width = member.halfWidth * 2f
            val height = member.halfHeight * 2f
            // The finger in the member's laid-out box, carried into the box it is drawn in: a
            // stretched member swells about its centre, and the light swells with it.
            val point = interaction.litPoint(Size(width, height))
            member.lightX = member.drawnCenterX +
                (point.x - member.halfWidth) * member.drawnHalfWidth / member.halfWidth
            member.lightY = member.drawnCenterY +
                (point.y - member.halfHeight) * member.drawnHalfHeight / member.halfHeight
            member.lightReach = minOf(width, height) * TouchLightReach * member.drawnRadiusScale
            member.lightStrength = interaction.glowIntensity * glow
            member.lightColor = touchLightColor(interaction.glowColor, member.tint)
        }
    }

    /** Uptime of the last flow step, or `-1` before the first. */
    private var flowClock = -1L

    /** Index into `active` of the member whose colour is flowing, or `-1`. */
    private var flowSourceIndex = -1

    private var flowAnimating = false
    private var flowLoop: Job? = null

    private fun flowSourceMember(): GlassMember? = active.getOrNull(flowSourceIndex)

    /**
     * Moves colour between members the way liquid would.
     *
     * The member being dragged carries its colour. Pushed into a neighbour until the two fuse, the
     * colour enters it from the side they touch — half of it within about a third of a second, a soft
     * front, then creeping on while they stay fused. Pulled apart or let go, the neighbour's own colour
     * returns over about a second and a half: these are controls, and a delete button has to end up
     * red again. Only a drag towards the neighbour counts, so members laid out to fuse at rest do not
     * swap colours on every tap.
     *
     * @return whether anything is still changing, so the group keeps drawing frames.
     */
    private fun updateColourFlow(count: Int, tinted: Boolean): Boolean {
        val now = SystemClock.uptimeMillis()
        val seconds = if (flowClock < 0L) 0f else ((now - flowClock) / 1000f).coerceIn(0f, MaxFlowStep)
        flowClock = now

        if (!tinted) {
            for (i in 0 until count) {
                active[i].flowAmount = 0f
                active[i].flowSource = null
            }
            flowSourceIndex = -1
            return false
        }

        // The member held hardest is the one that carries.
        var carrier: GlassMember? = null
        var carrierPress = 0f
        for (i in 0 until count) {
            val press = active[i].interaction?.pressProgress ?: 0f
            if (press > carrierPress) {
                carrier = active[i]
                carrierPress = press
            }
        }
        var drag = Offset.Zero
        var moving = 0f
        val carrierInteraction = carrier?.interaction
        if (carrier != null && carrierInteraction != null) {
            val width = carrier.halfWidth * 2f
            val height = carrier.halfHeight * 2f
            drag = carrierInteraction.dragTranslation(Size(width, height))
            val shortSide = minOf(width, height)
            if (shortSide > 0f) {
                moving = smoothStep(MovingFrom, MovingFull, drag.getDistance() / shortSide) *
                    carrierPress.coerceIn(0f, 1f)
            }
        }

        var animating = false
        for (i in 0 until count) {
            val member = active[i]
            val contact =
                if (carrier != null && member !== carrier && moving > 0f) {
                    pushedInto(carrier, member, drag) * moving
                } else {
                    0f
                }
            if (contact > MinContact) {
                if (member.flowSource !== carrier) {
                    member.flowAmount = 0f
                    member.flowSource = carrier
                }
                // Quickly towards half, then on at a crawl: whichever of the two is further.
                val target = FastFlowShare * contact
                val approached = member.flowAmount +
                    (target - member.flowAmount) * (1f - exp(-seconds / FastFlowSeconds))
                val crept = member.flowAmount + SlowFlowRate * contact * seconds
                member.flowAmount = min(1f, max(approached, crept))
                if (member.flowAmount < 1f) animating = true
            } else if (member.flowAmount > 0f) {
                member.flowAmount *= exp(-seconds / ReturnFlowSeconds)
                if (member.flowAmount < FlowGone) {
                    member.flowAmount = 0f
                    member.flowSource = null
                } else {
                    animating = true
                }
            }
        }

        // The source the program draws: the first one a member still holds colour from.
        flowSourceIndex = -1
        for (i in 0 until count) {
            val source = active[i].flowSource ?: continue
            if (active[i].flowAmount <= 0f) continue
            val index = active.indexOfFirst { it === source }
            if (index in 0 until count) {
                flowSourceIndex = index
                break
            }
        }
        return animating
    }

    /**
     * How far [carrier] is pushed into [member]: `1` once they touch, falling to `0` where the gap is
     * too wide for their reaches to bridge — and scaled by how directly the drag points at the member.
     */
    private fun pushedInto(carrier: GlassMember, member: GlassMember, drag: Offset): Float {
        val reach = maxOf(carrier.mergeRadius, member.mergeRadius)
        if (reach <= 0f) return 0f
        // The gap as drawn, stretch included: the gap the bridge actually spans.
        val dx = maxOf(
            abs(carrier.drawnCenterX - member.drawnCenterX) - (carrier.drawnHalfWidth + member.drawnHalfWidth),
            0f
        )
        val dy = maxOf(
            abs(carrier.drawnCenterY - member.drawnCenterY) - (carrier.drawnHalfHeight + member.drawnHalfHeight),
            0f
        )
        // Two members bridge once the gap is under half the reach.
        val touching = (1f - hypot(dx, dy) / (reach * 0.5f)).coerceIn(0f, 1f)
        if (touching <= 0f) return 0f

        val towardX = member.drawnCenterX - carrier.drawnCenterX
        val towardY = member.drawnCenterY - carrier.drawnCenterY
        val towardLength = hypot(towardX, towardY)
        val dragLength = drag.getDistance()
        if (towardLength <= 0f || dragLength <= 0f) return 0f
        val toward = ((drag.x * towardX + drag.y * towardY) / (towardLength * dragLength)).coerceAtLeast(0f)
        return touching * toward
    }

    /** Keeps the group drawing while colour is still moving with the finger holding still. */
    private fun keepFlowing(flowing: Boolean) {
        flowAnimating = flowing
        if (!flowing || flowLoop?.isActive == true) return
        flowLoop = coroutineScope.launch {
            while (flowAnimating) {
                withFrameNanos { }
                invalidateDraw()
            }
        }
    }

    /** The member whose colour is flowing: its box as drawn, its corners and its colour. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun RuntimeShader.writeFlowUniforms() {
        val source = flowSourceMember()
        if (source == null) {
            setFloatUniform("flowActive", 0f)
            return
        }
        setFloatUniform("flowActive", 1f)
        setFloatUniform(
            "flowRect",
            source.drawnCenterX,
            source.drawnCenterY,
            source.drawnHalfWidth,
            source.drawnHalfHeight
        )
        val radii = source.cornerRadii
        val radiusScale = source.drawnRadiusScale
        setFloatUniform(
            "flowRadii",
            radii[0] * radiusScale,
            radii[1] * radiusScale,
            radii[2] * radiusScale,
            radii[3] * radiusScale
        )
        val tint = source.tint
        val specified = tint.isSpecified
        setColorUniform("flowColor", if (specified) tint.copy(alpha = 1f) else Color.Transparent)
        setFloatUniform("flowStrength", if (specified) tint.alpha else 0f)
    }

    override fun onDetach() {
        flowLoop?.cancel()
        flowLoop = null
        flowAnimating = false
        flowClock = -1L
        flowSourceIndex = -1
        for (member in state.members) {
            member.flowAmount = 0f
            member.flowSource = null
        }
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
        // The programs these describe went with the cache.
        glassShader = null
        glassMemberCount = -1
        glassUniforms = Long.MIN_VALUE
        composedRenderEffect = null
    }
}

/** The longest step one frame may advance the colour flow by, so a stall cannot pour it all in. */
private const val MaxFlowStep = 0.1f

/** How much of a neighbour the carried colour takes over in its first, quick phase. */
private const val FastFlowShare = 0.5f

/** Time constant of that quick phase: most of the way to half within a third of a second. */
private const val FastFlowSeconds = 0.35f

/** How fast the rest follows while the two stay fused, per second of full contact. */
private const val SlowFlowRate = 0.06f

/** Time constant of the way back after the two part: all but gone in about a second and a half. */
private const val ReturnFlowSeconds = 0.5f

/** Below this a flow has run out, and stops costing frames. */
private const val FlowGone = 0.004f

/** Contact too faint to count. */
private const val MinContact = 0.01f

/** Drag, as a share of the carrier's short side, from which it starts to count — and fully. */
private const val MovingFrom = 0.02f
private const val MovingFull = 0.15f

/** Hermite ease of [x] between [from] and [to]. */
private fun smoothStep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
