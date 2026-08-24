/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.hakim.liquify.backdrops.LayerBackdrop
import com.hakim.liquify.group.GlassMember
import com.hakim.liquify.group.LiquidGlassGroupState
import com.hakim.liquify.group.LocalLiquidGlassGroup
import com.hakim.liquify.interaction.InteractiveHighlight
import com.hakim.liquify.internal.GlassNodeState
import com.hakim.liquify.internal.ShapeProvider
import com.hakim.liquify.internal.recordLayer
import com.hakim.liquify.internal.resolveCornerRadii

/**
 * The node behind [liquify]: it records the backdrop into an offscreen layer, applies the effect
 * chain to it and draws it beneath the element's own content.
 *
 * It is also what decides — during layout, before anything is drawn — whether the element renders
 * itself or hands its geometry to an enclosing
 * [LiquidGlassGroup][com.hakim.liquify.group.LiquidGlassGroup].
 */
internal class LiquifyElement(
    val backdrop: Backdrop,
    val shapeProvider: ShapeProvider,
    val effects: BackdropEffectScope.() -> Unit,
    val nodeState: GlassNodeState,
    val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    val exportedBackdrop: LayerBackdrop?,
    val onDrawBehind: (DrawScope.() -> Unit)?,
    val onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    val onDrawSurface: (DrawScope.() -> Unit)?,
    val onDrawFront: (DrawScope.() -> Unit)?,
    val interaction: InteractiveHighlight?
) : ModifierNodeElement<LiquifyNode>() {

    override fun create(): LiquifyNode = LiquifyNode(
        backdrop = backdrop,
        shapeProvider = shapeProvider,
        effects = effects,
        nodeState = nodeState,
        layerBlock = layerBlock,
        exportedBackdrop = exportedBackdrop,
        onDrawBehind = onDrawBehind,
        onDrawBackdrop = onDrawBackdrop,
        onDrawSurface = onDrawSurface,
        onDrawFront = onDrawFront,
        interaction = interaction
    )

    override fun update(node: LiquifyNode) {
        node.backdrop = backdrop
        node.shapeProvider = shapeProvider
        node.effects = effects
        if (node.nodeState !== nodeState) {
            nodeState.isMerged = node.nodeState.isMerged
            node.nodeState = nodeState
        }
        node.layerBlock = layerBlock
        if (node.exportedBackdrop != exportedBackdrop) {
            node.exportedBackdrop?.layerCoordinates = null
            node.exportedBackdrop = exportedBackdrop
        }
        node.onDrawBehind = onDrawBehind
        node.onDrawBackdrop = onDrawBackdrop
        node.onDrawSurface = onDrawSurface
        node.onDrawFront = onDrawFront
        node.interaction = interaction
        node.invalidateDrawCache()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "liquify"
        properties["backdrop"] = backdrop
        properties["shapeProvider"] = shapeProvider
        properties["effects"] = effects
        properties["layerBlock"] = layerBlock
        properties["exportedBackdrop"] = exportedBackdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiquifyElement) return false
        return backdrop == other.backdrop &&
            shapeProvider == other.shapeProvider &&
            effects == other.effects &&
            nodeState === other.nodeState &&
            layerBlock == other.layerBlock &&
            exportedBackdrop == other.exportedBackdrop &&
            onDrawBehind == other.onDrawBehind &&
            onDrawBackdrop == other.onDrawBackdrop &&
            onDrawSurface == other.onDrawSurface &&
            onDrawFront == other.onDrawFront &&
            interaction === other.interaction
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + shapeProvider.hashCode()
        result = 31 * result + effects.hashCode()
        result = 31 * result + nodeState.hashCode()
        result = 31 * result + (layerBlock?.hashCode() ?: 0)
        result = 31 * result + (exportedBackdrop?.hashCode() ?: 0)
        result = 31 * result + (onDrawBehind?.hashCode() ?: 0)
        result = 31 * result + onDrawBackdrop.hashCode()
        result = 31 * result + (onDrawSurface?.hashCode() ?: 0)
        result = 31 * result + (onDrawFront?.hashCode() ?: 0)
        result = 31 * result + (interaction?.hashCode() ?: 0)
        return result
    }
}

internal class LiquifyNode(
    var backdrop: Backdrop,
    var shapeProvider: ShapeProvider,
    var effects: BackdropEffectScope.() -> Unit,
    var nodeState: GlassNodeState,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    var exportedBackdrop: LayerBackdrop?,
    var onDrawBehind: (DrawScope.() -> Unit)?,
    var onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    var onDrawSurface: (DrawScope.() -> Unit)?,
    var onDrawFront: (DrawScope.() -> Unit)?,
    var interaction: InteractiveHighlight?
) : LayoutModifierNode,
    DrawModifierNode,
    GlobalPositionAwareModifierNode,
    ObserverModifierNode,
    CompositionLocalConsumerModifierNode,
    Modifier.Node() {

    private val effectScope = object : ElementEffectScope() {
        override val shape: Shape get() = shapeProvider.innerShape
    }

    private var graphicsLayer: GraphicsLayer? = null

    private val layoutLayerBlock: GraphicsLayerScope.() -> Unit = {
        clip = true
        shape = shapeProvider.shape
        compositingStrategy = CompositingStrategy.Offscreen
    }

    // neverEqualPolicy: the coordinates object is mutated in place by the layout system, so
    // structural equality would hide real movement.
    private var layoutCoordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())

    private var padding by mutableFloatStateOf(0f)

    private val member = GlassMember()
    private var joinedGroup: LiquidGlassGroupState? = null

    private val recordBackdropBlock: DrawScope.() -> Unit = {
        val canvas = drawContext.canvas
        val padding = padding

        if (padding != 0f) canvas.translate(padding, padding)
        onDrawBackdrop {
            with(backdrop) {
                drawBackdrop(
                    density = effectScope,
                    coordinates = layoutCoordinates,
                    layerBlock = layerBlock
                )
            }
        }
        if (padding != 0f) canvas.translate(-padding, -padding)
    }

    private val drawBackdropLayer: DrawScope.() -> Unit = {
        val layer = graphicsLayer
        if (layer != null) {
            val padding = padding
            recordLayer(
                layer = layer,
                density = effectScope,
                size = IntSize(
                    size.width.toInt() + padding.toInt() * 2,
                    size.height.toInt() + padding.toInt() * 2
                ),
                block = recordBackdropBlock
            )
            layer.topLeft =
                if (padding != 0f) IntOffset(-padding.toInt(), -padding.toInt()) else IntOffset.Zero
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
        if (effectScope.update(this)) {
            updateEffects()
        }

        onDrawBehind?.invoke(this)
        // A merged element's glass belongs to the group; drawing it here as well would double the
        // backdrop wherever two members overlap.
        if (!nodeState.isMerged) {
            drawBackdropLayer()
        }
        onDrawSurface?.invoke(this)
        drawContent()
        onDrawFront?.invoke(this)

        exportedBackdrop?.graphicsLayer?.let { layer ->
            recordLayer(layer, effectScope) {
                onDrawBehind?.invoke(this)
                if (!nodeState.isMerged) drawBackdropLayer()
                onDrawSurface?.invoke(this)
                onDrawFront?.invoke(this)
            }
        }
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
     * Decides here — during layout, before anything is drawn — whether this element renders itself
     * or hands its geometry to the enclosing group. The rim and shadow nodes draw *before* this
     * node does, so the decision has to be made outside the draw pass to avoid a frame of lag.
     */
    private fun updateGroupMembership(coordinates: LayoutCoordinates) {
        val group =
            if (isRuntimeShaderSupported()) currentValueOf(LocalLiquidGlassGroup) else null
        val groupCoordinates = group?.coordinates
        val size = coordinates.size.toSize()

        if (group == null || groupCoordinates == null || size.width <= 0f || size.height <= 0f) {
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
        member.tint = effectScope.mergeTintColor
        member.effects = effects
        // The group draws the illumination for the whole merged body, so it needs to know which
        // member is being touched and where.
        member.interaction = interaction
        val moved = member.setGeometry(
            left = topLeft.x,
            top = topLeft.y,
            width = size.width,
            height = size.height,
            radii = shapeProvider.innerShape.resolveCornerRadii(size, requireLayoutDirection(), density)
        )

        if (joinedGroup !== group) {
            joinedGroup?.detach(member)
            group.attach(member)
            joinedGroup = group
        } else if (moved) {
            group.invalidate()
        }
        nodeState.isMerged = true
    }

    private fun leaveGroup() {
        joinedGroup?.let { group ->
            group.detach(member)
            joinedGroup = null
        }
        nodeState.isMerged = false
    }

    override fun onObservedReadsChanged() {
        invalidateDrawCache()
    }

    fun invalidateDrawCache() {
        observeEffects()
    }

    private fun observeEffects() {
        observeReads { updateEffects() }
    }

    private fun updateEffects() {
        if (!isRenderEffectSupported()) return

        effectScope.apply(effects)
        graphicsLayer?.renderEffect = effectScope.renderEffect
        padding = effectScope.padding
        syncMergeTint()
    }

    /**
     * Pushes a changed merge tint to the group.
     *
     * Colour is not geometry: recolouring a member moves nothing, so no layout pass runs and
     * [updateGroupMembership] never fires. This is the one place where a new tint is known — the
     * effect block has just been re-evaluated — so the group is told from here instead.
     */
    private fun syncMergeTint() {
        val group = joinedGroup ?: return
        val tint = effectScope.mergeTintColor
        if (member.tint != tint) {
            member.tint = tint
            group.invalidate()
        }
    }

    override fun onAttach() {
        graphicsLayer = requireGraphicsContext().createGraphicsLayer()
        observeEffects()
    }

    override fun onDetach() {
        graphicsLayer?.let { layer ->
            requireGraphicsContext().releaseGraphicsLayer(layer)
            graphicsLayer = null
        }
        leaveGroup()
        effectScope.reset()
        layoutCoordinates = null
        exportedBackdrop?.layerCoordinates = null
    }
}
