/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.hakim.liquify.Backdrop
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.BaseBackdropEffectScope
import com.hakim.liquify.ColorBackdrop
import com.hakim.liquify.ElementEffectScope
import com.hakim.liquify.GlassInteraction
import com.hakim.liquify.GlassMaterial
import com.hakim.liquify.LayerBackdrop
import com.hakim.liquify.isRenderEffectSupported
import com.hakim.liquify.isRuntimeShaderSupported
import com.hakim.liquify.lerpTint
import kotlin.math.ceil

/**
 * The glass of one element: it records the backdrop into an offscreen layer, runs the effect chain
 * over it, lays the tint on top and draws the element's content over that, all clipped to the shape —
 * to its rounded rectangle, unless [exactClip] asks for the curve itself; see
 * [ShapeProvider.clipShape].
 *
 * It is also what decides — during layout, before anything is drawn — whether the element renders
 * itself or hands its geometry to the enclosing group.
 */
internal class LiquifyElement(
    val backdrop: Backdrop,
    val shapeProvider: ShapeProvider,
    val nodeState: GlassNodeState,
    val material: GlassMaterial,
    val pressedMaterial: GlassMaterial?,
    val pressProgress: (() -> Float)?,
    val tint: Color,
    val effects: (BackdropEffectScope.() -> Unit)?,
    val group: GroupDefaults?,
    val refracts: Boolean,
    val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    val exportedBackdrop: LayerBackdrop?,
    val onDrawSurface: (DrawScope.() -> Unit)?,
    val refractsContent: Boolean,
    val interaction: GlassInteraction?,
    /** Whether the element lights up under the finger — the light its group lays into the body. */
    val glows: Boolean,
    /** Whether the layer clips to the true outline rather than to its rounded rectangle. */
    val exactClip: Boolean
) : ModifierNodeElement<LiquifyNode>() {

    override fun create(): LiquifyNode = LiquifyNode(
        backdrop = backdrop,
        shapeProvider = shapeProvider,
        nodeState = nodeState,
        material = material,
        pressedMaterial = pressedMaterial,
        pressProgress = pressProgress,
        tint = tint,
        effects = effects,
        group = group,
        refracts = refracts,
        layerBlock = layerBlock,
        exportedBackdrop = exportedBackdrop,
        onDrawSurface = onDrawSurface,
        refractsContent = refractsContent,
        interaction = interaction,
        glows = glows,
        exactClip = exactClip
    )

    override fun update(node: LiquifyNode) {
        // Only these describe the effect chain. Rebuilding it for any other change would re-run the
        // effects and re-upload their uniforms on every recomposition of the screen around a pane
        // with, say, an inline surface hook.
        val rebuildEffects = node.material != material ||
            node.pressedMaterial != pressedMaterial ||
            node.pressProgress !== pressProgress ||
            node.effects !== effects ||
            node.group !== group ||
            node.refracts != refracts ||
            node.shapeProvider !== shapeProvider ||
            node.backdrop != backdrop ||
            node.refractsContent != refractsContent

        node.backdrop = backdrop
        node.shapeProvider = shapeProvider
        if (node.nodeState !== nodeState) {
            nodeState.isMerged = node.nodeState.isMerged
            node.nodeState = nodeState
        }
        node.material = material
        node.pressedMaterial = pressedMaterial
        node.pressProgress = pressProgress
        node.effects = effects
        node.group = group
        node.refracts = refracts
        node.layerBlock = layerBlock
        if (node.exportedBackdrop != exportedBackdrop) {
            node.exportedBackdrop?.layerCoordinates = null
            node.exportedBackdrop = exportedBackdrop
        }
        node.onDrawSurface = onDrawSurface
        node.refractsContent = refractsContent
        node.interaction = interaction
        node.updateGlows(glows)
        node.updateTint(tint)
        node.updateExactClip(exactClip)
        if (rebuildEffects) node.invalidateDrawCache()
        // Nearly everything here is read while drawing, so the element draws again — in the layer it
        // actually draws into; see LiquifyNode.redraw.
        node.redraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "liquify"
        properties["material"] = material
        properties["pressedMaterial"] = pressedMaterial
        properties["tint"] = tint
        properties["backdrop"] = backdrop
        properties["layerBlock"] = layerBlock
        properties["exportedBackdrop"] = exportedBackdrop
        properties["refractsContent"] = refractsContent
        properties["exactClip"] = exactClip
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiquifyElement) return false
        return backdrop == other.backdrop &&
            shapeProvider === other.shapeProvider &&
            nodeState === other.nodeState &&
            material == other.material &&
            pressedMaterial == other.pressedMaterial &&
            pressProgress === other.pressProgress &&
            tint == other.tint &&
            effects === other.effects &&
            group === other.group &&
            refracts == other.refracts &&
            layerBlock === other.layerBlock &&
            exportedBackdrop == other.exportedBackdrop &&
            onDrawSurface === other.onDrawSurface &&
            refractsContent == other.refractsContent &&
            interaction === other.interaction &&
            glows == other.glows &&
            exactClip == other.exactClip
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + shapeProvider.hashCode()
        result = 31 * result + nodeState.hashCode()
        result = 31 * result + material.hashCode()
        result = 31 * result + (pressedMaterial?.hashCode() ?: 0)
        result = 31 * result + (pressProgress?.hashCode() ?: 0)
        result = 31 * result + tint.hashCode()
        result = 31 * result + (effects?.hashCode() ?: 0)
        result = 31 * result + (group?.hashCode() ?: 0)
        result = 31 * result + refracts.hashCode()
        result = 31 * result + (layerBlock?.hashCode() ?: 0)
        result = 31 * result + (exportedBackdrop?.hashCode() ?: 0)
        result = 31 * result + (onDrawSurface?.hashCode() ?: 0)
        result = 31 * result + refractsContent.hashCode()
        result = 31 * result + (interaction?.hashCode() ?: 0)
        result = 31 * result + glows.hashCode()
        result = 31 * result + exactClip.hashCode()
        return result
    }
}

internal class LiquifyNode(
    var backdrop: Backdrop,
    var shapeProvider: ShapeProvider,
    var nodeState: GlassNodeState,
    var material: GlassMaterial,
    var pressedMaterial: GlassMaterial?,
    var pressProgress: (() -> Float)?,
    tint: Color,
    var effects: (BackdropEffectScope.() -> Unit)?,
    var group: GroupDefaults?,
    var refracts: Boolean,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    var exportedBackdrop: LayerBackdrop?,
    var onDrawSurface: (DrawScope.() -> Unit)?,
    var refractsContent: Boolean,
    var interaction: GlassInteraction?,
    private var glows: Boolean,
    private var exactClip: Boolean
) : LayoutModifierNode,
    DrawModifierNode,
    GlobalPositionAwareModifierNode,
    ObserverModifierNode,
    CompositionLocalConsumerModifierNode,
    TraversableNode,
    Modifier.Node() {

    override val traverseKey: Any get() = LiquifyTraverseKey

    private val effectScope = object : ElementEffectScope() {
        override val shape: Shape get() = shapeProvider.innerShape
    }

    /**
     * The element's colour: laid over its own glass, or handed to its group as its share of the
     * merged body.
     */
    private var tint: Color = tint

    /**
     * Whether this element takes the group's glass and joins its body — decided in layout. An element
     * nested inside another glass element of the same group never does: it is a pane on a pane, and
     * merged into the body it would smear its colour across the element it sits on.
     */
    private var groupCandidate = false
    private var checkedGroup: LiquidGlassGroupState? = null
    private var nestedInGroup = false

    /** Runs whichever recipe this element is made of right now; allocated once. */
    private val effectsBlock: BackdropEffectScope.() -> Unit = {
        val own = effects
        val defaults = group
        when {
            // One colour has nothing to blur and nothing to bend: no chain is built, and the draw
            // lays the colour down itself.
            flatBackdrop != null -> Unit
            own != null -> own()
            groupCandidate && defaults != null -> {
                defaults.effects(this)
                merge(defaults.mergeAmount)
            }
            else -> {
                // The progress is read here only when the press changes the recipe. One that moves
                // nothing but the colour or the decoration — a pulsing shadow — then redraws without
                // rebuilding the chain on every frame.
                val pressed = pressedMaterial?.takeUnless { it.sameEffectsAs(material) }
                (this as BaseBackdropEffectScope)
                    .applyMaterial(material, pressed, if (pressed != null) pressFraction() else 0f)
            }
        }
    }

    /**
     * The backdrop, when it is one colour and nothing but the material works on it. Such glass comes
     * out as one colour as well, so it needs no layer to record the backdrop into and no chain to run
     * over it. An element with its own effects, one that bends its content and a member of a merging
     * group keep the long way: what they do to the backdrop is not known here.
     */
    private val flatBackdrop: ColorBackdrop?
        get() =
            if (effects == null && !groupCandidate && !refractsContent) backdrop as? ColorBackdrop
            else null

    /**
     * The layer the backdrop is recorded into and the chain runs on, made on first use: a pane over
     * one colour, or behind an opaque tint, never needs one.
     */
    private var graphicsLayer: GraphicsLayer? = null

    private fun obtainBackdropLayer(): GraphicsLayer =
        graphicsLayer ?: requireGraphicsContext().createGraphicsLayer().also { layer ->
            layer.renderEffect = effectScope.renderEffect
            graphicsLayer = layer
        }

    /** Backdrop, surface and content together with the lens over them, while [refractsContent]. */
    private var contentLayer: GraphicsLayer? = null

    // No offscreen compositing: the clip is all this layer is for, and a render node clips to its
    // outline — anti-aliased — without rendering the glass and the content into a texture first. The
    // outline is the shape's rounded rectangle, which costs nothing to clip to; see
    // ShapeProvider.clipShape.
    private val layoutLayerBlock: GraphicsLayerScope.() -> Unit = {
        clip = true
        shape = if (exactClip) shapeProvider.shape else shapeProvider.clipShape
    }

    // neverEqualPolicy: the coordinates object is mutated in place, so equality would hide movement.
    private var layoutCoordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())

    private var padding by mutableFloatStateOf(0f)

    /**
     * Read at the start of every draw and bumped by [redraw]. This node draws *inside* the layer it
     * places its content with, while `invalidateDraw()` reaches the layer around it — which draws the
     * inner one from its cache. A state read here makes the inner layer the observer, so a write
     * invalidates the very layer this draws into.
     */
    private var drawRevision by mutableIntStateOf(0)

    private val member = GlassMember()
    private var joinedGroup: LiquidGlassGroupState? = null

    /** Scratch for the radii handed to the group, resolved on every layout pass of a dragged member. */
    private val cornerRadiiScratch = FloatArray(4)

    /** The padding of the recording in the element's own pixels, for [recordBackdropBlock]. */
    private var recordPadding = 0f

    /** Records the backdrop as the element sees it, in its own pixels and at its own density. */
    private val recordBackdropBlock: DrawScope.() -> Unit = {
        val scope = this
        val canvas = drawContext.canvas
        val padding = recordPadding

        if (padding != 0f) canvas.translate(padding, padding)
        with(backdrop) {
            scope.drawBackdrop(
                density = scope,
                coordinates = layoutCoordinates,
                layerBlock = layerBlock
            )
        }
        if (padding != 0f) canvas.translate(-padding, -padding)
    }

    /**
     * Records the backdrop into the element's layer — the chain runs on it — and draws it. The layer
     * reaches past the element by the padding the chain asked for, so effects that read beyond its
     * edge read real content. A backdrop of one colour is laid down as it is; see [flatBackdrop].
     */
    private val drawBackdropLayer: DrawScope.() -> Unit = {
        val flat = flatBackdrop
        if (flat != null) {
            drawRect(glassColor(flat))
        } else {
            val layer = obtainBackdropLayer()
            val layerPadding = padding.toInt()
            recordPadding = layerPadding.toFloat()
            recordLayer(
                layer = layer,
                density = this@LiquifyNode.requireDensity(),
                size = IntSize(
                    ceil(size.width).toInt() + layerPadding * 2,
                    ceil(size.height).toInt() + layerPadding * 2
                ),
                block = recordBackdropBlock
            )
            layer.topLeft = IntOffset(-layerPadding, -layerPadding)
            drawLayer(layer)
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(IntOffset.Zero, layerBlock = layoutLayerBlock)
        }
    }

    override fun ContentDrawScope.draw() {
        observeRedraws()
        if (effectScope.update(density, fontScale, size, layoutDirection)) {
            updateEffects()
        }

        val merged = nodeState.isMerged
        val fill = currentTint()
        val contentLayer = contentLayer
        if (contentLayer != null && effectScope.contentEffect != null && !merged) {
            // Refracting the content: the backdrop, blurred and coloured in its own layer, the tint,
            // the surface and the content are laid down together, and the one lens runs over all of it.
            recordLayer(contentLayer, requireDensity()) {
                drawBackdropLayer()
                drawFill(fill)
                onDrawSurface?.invoke(this)
                this@draw.drawContent()
            }
            drawLayer(contentLayer)
        } else {
            // A merged member's glass, colour and surface belong to the group — drawn here as well they
            // would double the backdrop where members overlap and cut a seam across the bridge. An
            // opaque tint covers the glass completely, so its backdrop is not even recorded.
            if (!merged) {
                drawGlass(fill)
                onDrawSurface?.invoke(this)
            }
            drawContent()
        }

        exportedBackdrop?.graphicsLayer?.let { layer ->
            recordLayer(layer, requireDensity()) {
                if (!merged) {
                    drawGlass(fill)
                    onDrawSurface?.invoke(this)
                }
            }
        }
    }

    /**
     * The glass and its colour. The lens lays the colour down itself where it could take it in — see
     * BaseBackdropEffectScope.fillFolded — and then the glass is one draw instead of two.
     */
    private fun DrawScope.drawGlass(fill: Color) {
        if (fill.isSpecified && fill.alpha >= 1f) {
            drawFill(fill)
        } else {
            drawBackdropLayer()
            if (!effectScope.fillFolded) drawFill(fill)
        }
    }

    private fun DrawScope.drawFill(fill: Color) {
        if (fill.isSpecified && fill.alpha > 0f) drawRect(fill)
    }

    /**
     * The colour the lens may take in: the element's own tint, or its material's while no press
     * blends it towards another. A colour that moves with the press is drawn over the glass instead,
     * so the chain is not rebuilt on every frame of the press.
     */
    private fun foldableFill(): Color {
        if (tint.isSpecified) return tint
        val pressed = pressedMaterial
        return if (pressed == null || pressed.tint == material.tint) material.tint else Color.Unspecified
    }

    /**
     * The colour laid over the glass this frame: the element's own, or else its material's — blended
     * towards the pressed material's while held.
     */
    private fun currentTint(): Color {
        if (tint.isSpecified) return tint
        val pressed = pressedMaterial ?: return material.tint
        return lerpTint(material.tint, pressed.tint, pressFraction())
    }

    private fun pressFraction(): Float {
        if (pressedMaterial == null) return 0f
        return pressProgress?.invoke()?.fastCoerceIn(0f, 1f) ?: 0f
    }

    /**
     * A colour backdrop as this element's glass shows it: the material's vibrancy — blended towards
     * the pressed material's while held — on the one colour, where the chain runs it over every pixel
     * of a layer. Below API 31 nothing colours the backdrop on the long way either.
     */
    private fun glassColor(backdrop: ColorBackdrop): Color {
        if (!isRenderEffectSupported()) return backdrop.color
        val rest = material.saturation
        val pressed = pressedMaterial?.saturation ?: rest
        val saturation = if (pressed == rest) rest else lerp(rest, pressed, pressFraction())
        return ColorMatrices.saturate(backdrop.color, saturation)
    }

    /** Turns the touch light on or off, telling the group when it is a member — it draws that light. */
    fun updateGlows(glows: Boolean) {
        if (this.glows == glows) return
        this.glows = glows
        val group = joinedGroup ?: return
        member.glows = glows
        group.invalidate()
    }

    /** Switches the layer between the true outline and its rounded rectangle; see [exactClip]. */
    fun updateExactClip(exactClip: Boolean) {
        if (this.exactClip == exactClip) return
        this.exactClip = exactClip
        // The clip is set where the layer is placed.
        invalidatePlacement()
    }

    /**
     * Takes a new colour, telling the group when it is a member — recolouring moves nothing — and
     * handing it to the lens otherwise, which lays it over the glass.
     */
    fun updateTint(tint: Color) {
        if (this.tint == tint) return
        this.tint = tint
        val group = joinedGroup
        if (group == null) {
            invalidateDrawCache()
            return
        }
        member.tint = tint
        group.invalidate()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return

        if (backdrop.isCoordinatesDependent) {
            layoutCoordinates = coordinates
        } else if (layoutCoordinates != null) {
            layoutCoordinates = null
        }
        exportedBackdrop?.layerCoordinates = coordinates

        updateGroupMembership(coordinates)
    }

    /**
     * Decides — during layout, before anything is drawn — whether this element renders itself or
     * hands its geometry to the enclosing group. The decoration node draws *before* this one, so the
     * decision cannot wait for the draw pass.
     */
    private fun updateGroupMembership(coordinates: LayoutCoordinates) {
        val groupState =
            if (refracts && isRuntimeShaderSupported()) currentValueOf(LocalLiquidGlassGroup) else null
        if (groupState !== checkedGroup) {
            checkedGroup = groupState
            nestedInGroup = groupState != null && isNestedIn(groupState)
        }
        val candidate = groupState != null && group != null && !nestedInGroup
        if (candidate != groupCandidate) {
            groupCandidate = candidate
            // A member runs the group's glass, a pane its own material: a different chain.
            invalidateDrawCache()
        }

        val target = if (candidate) groupState else null
        val groupCoordinates = target?.coordinates
        val size = coordinates.size.toSize()
        if (target == null || groupCoordinates == null || size.width <= 0f || size.height <= 0f) {
            return leaveGroup()
        }

        val density = requireDensity()
        if (effectScope.update(density.density, density.fontScale, size, requireLayoutDirection())) {
            updateEffects()
        }

        val mergeRadius = effectScope.mergeRadius
        if (mergeRadius <= 0f) return leaveGroup()

        val topLeft = try {
            groupCoordinates.localPositionOf(coordinates)
        } catch (_: RuntimeException) {
            coordinates.positionInWindow() - groupCoordinates.positionInWindow()
        }

        member.mergeRadius = mergeRadius
        member.tint = tint
        member.interaction = interaction
        member.glows = glows
        val hasRadii = shapeProvider.innerShape
            .resolveCornerRadii(size, requireLayoutDirection(), density, cornerRadiiScratch)
        val moved = member.setGeometry(
            left = topLeft.x,
            top = topLeft.y,
            width = size.width,
            height = size.height,
            radii = if (hasRadii) cornerRadiiScratch else null
        )

        if (joinedGroup !== target) {
            joinedGroup?.detach(member)
            target.attach(member)
            joinedGroup = target
        } else if (moved) {
            target.invalidate()
        }
        nodeState.isMerged = true
    }

    /** Whether a glass element of the same group encloses this one. */
    private fun isNestedIn(groupState: LiquidGlassGroupState): Boolean {
        var nested = false
        traverseAncestors { ancestor ->
            if (ancestor.currentValueOf(LocalLiquidGlassGroup) === groupState) {
                nested = true
                false
            } else {
                true
            }
        }
        return nested
    }

    private fun leaveGroup() {
        joinedGroup?.let { group ->
            group.detach(member)
            joinedGroup = null
        }
        nodeState.isMerged = false
    }

    /** Draws this element again, in the layer it actually draws into; see [drawRevision]. */
    fun redraw() {
        drawRevision++
    }

    private fun observeRedraws() {
        drawRevision
    }

    override fun onObservedReadsChanged() {
        invalidateDrawCache()
    }

    fun invalidateDrawCache() {
        observeReads { updateEffects() }
    }

    private fun updateEffects() {
        if (!isRenderEffectSupported()) return

        effectScope.refracts = refracts
        effectScope.refractsContent = refractsContent
        effectScope.fill = foldableFill()
        effectScope.apply(effectsBlock)
        // A blur no lens took in, and then colour work none took in — there was no lens, or it could
        // not run — go in as the last links, where the platform applies them in the same draw that
        // composites the layer.
        effectScope.flushBlur()
        effectScope.flushColor()
        graphicsLayer?.renderEffect = effectScope.renderEffect
        if (refractsContent) {
            val layer = contentLayer
                ?: requireGraphicsContext().createGraphicsLayer().also { contentLayer = it }
            layer.renderEffect = effectScope.contentEffect
        } else {
            contentLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
            contentLayer = null
        }
        padding = effectScope.padding
    }

    override fun onAttach() {
        invalidateDrawCache()
    }

    override fun onDetach() {
        graphicsLayer?.let { layer ->
            requireGraphicsContext().releaseGraphicsLayer(layer)
            graphicsLayer = null
        }
        contentLayer?.let { layer ->
            requireGraphicsContext().releaseGraphicsLayer(layer)
            contentLayer = null
        }
        leaveGroup()
        checkedGroup = null
        nestedInGroup = false
        groupCandidate = false
        effectScope.reset()
        layoutCoordinates = null
        exportedBackdrop?.layerCoordinates = null
    }
}

private object LiquifyTraverseKey
