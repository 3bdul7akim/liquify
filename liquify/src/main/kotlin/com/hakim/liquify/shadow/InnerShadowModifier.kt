/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import com.hakim.liquify.internal.GlassNodeState
import com.hakim.liquify.internal.ShapeProvider
import com.hakim.liquify.internal.clipOutline
import com.hakim.liquify.isRenderEffectSupported

internal class InnerShadowElement(
    val shapeProvider: ShapeProvider,
    val shadow: () -> InnerShadow?,
    val nodeState: GlassNodeState
) : ModifierNodeElement<InnerShadowNode>() {

    override fun create(): InnerShadowNode = InnerShadowNode(shapeProvider, shadow, nodeState)

    override fun update(node: InnerShadowNode) {
        node.shapeProvider = shapeProvider
        node.shadow = shadow
        if (node.nodeState !== nodeState) {
            nodeState.isMerged = node.nodeState.isMerged
            node.nodeState = nodeState
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "innerShadow"
        properties["shapeProvider"] = shapeProvider
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InnerShadowElement) return false
        return shapeProvider == other.shapeProvider &&
            shadow == other.shadow &&
            nodeState === other.nodeState
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + shadow.hashCode()
        result = 31 * result + nodeState.hashCode()
        return result
    }
}

internal class InnerShadowNode(
    var shapeProvider: ShapeProvider,
    var shadow: () -> InnerShadow?,
    var nodeState: GlassNodeState
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()
    private var clipPath: Path? = null
    private var appliedRadius = Float.NaN

    override fun ContentDrawScope.draw() {
        drawContent()

        if (!isRenderEffectSupported()) return
        val shadow = shadow()
        if (shadow == null || nodeState.isMerged) return

        val layer = shadowLayer ?: return

        val radius = shadow.radius.toPx()
        val offsetX = shadow.offset.x.toPx()
        val offsetY = shadow.offset.y.toPx()

        val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
        val clipPath =
            if (outline is Outline.Rounded) clipPath ?: Path().also { clipPath = it } else null

        paint.color = shadow.color

        layer.alpha = shadow.alpha
        layer.blendMode = shadow.blendMode
        if (appliedRadius != radius) {
            layer.renderEffect =
                if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
            appliedRadius = radius
        }
        layer.record {
            val canvas = drawContext.canvas
            canvas.save()
            canvas.clipOutline(outline, clipPath)
            canvas.drawOutline(outline, paint)
            // Removing an offset copy of the shape leaves only the crescent along the inner edge.
            canvas.translate(offsetX, offsetY)
            canvas.drawOutline(outline, ShadowMaskPaint)
            canvas.translate(-offsetX, -offsetY)
            canvas.restore()
        }

        val canvas = drawContext.canvas
        canvas.save()
        canvas.clipOutline(outline, clipPath)
        drawLayer(layer)
        canvas.restore()
    }

    override fun onAttach() {
        shadowLayer = requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    override fun onDetach() {
        shadowLayer?.let { layer ->
            requireGraphicsContext().releaseGraphicsLayer(layer)
            shadowLayer = null
        }
        clipPath = null
        appliedRadius = Float.NaN
    }
}
