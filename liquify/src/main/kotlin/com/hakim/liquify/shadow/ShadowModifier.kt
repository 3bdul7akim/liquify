/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import com.hakim.liquify.internal.GlassNodeState
import com.hakim.liquify.internal.ShapeProvider
import com.hakim.liquify.internal.blur
import kotlin.math.ceil

internal class ShadowElement(
    val shapeProvider: ShapeProvider,
    val shadow: () -> Shadow?,
    val nodeState: GlassNodeState
) : ModifierNodeElement<ShadowNode>() {

    override fun create(): ShadowNode = ShadowNode(shapeProvider, shadow, nodeState)

    override fun update(node: ShadowNode) {
        node.shapeProvider = shapeProvider
        node.shadow = shadow
        if (node.nodeState !== nodeState) {
            nodeState.isMerged = node.nodeState.isMerged
            node.nodeState = nodeState
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "shadow"
        properties["shapeProvider"] = shapeProvider
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShadowElement) return false
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

internal class ShadowNode(
    var shapeProvider: ShapeProvider,
    var shadow: () -> Shadow?,
    var nodeState: GlassNodeState
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()

    override fun ContentDrawScope.draw() {
        val shadow = shadow()
        // Merged elements are grounded by one shadow cast by the whole group.
        if (shadow == null || nodeState.isMerged) return drawContent()

        val layer = shadowLayer
        if (layer != null) {
            val radius = shadow.radius.toPx()
            val offsetX = shadow.offset.x.toPx()
            val offsetY = shadow.offset.y.toPx()

            // Two radii of slack on each side: a Gaussian is effectively zero beyond that.
            val shadowSize = IntSize(
                ceil(size.width + radius * 4f + offsetX).toInt(),
                ceil(size.height + radius * 4f + offsetY).toInt()
            )
            val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)

            paint.color = shadow.color
            paint.blur(radius)

            layer.alpha = shadow.alpha
            layer.blendMode = shadow.blendMode
            layer.record(shadowSize) {
                translate(radius * 2f + offsetX, radius * 2f + offsetY) {
                    val canvas = drawContext.canvas
                    canvas.drawOutline(outline, paint)
                    // Punch the element's own footprint back out, so the shadow never darkens the
                    // backdrop that is visible through the glass.
                    canvas.translate(-offsetX, -offsetY)
                    canvas.drawOutline(outline, ShadowMaskPaint)
                    canvas.translate(offsetX, offsetY)
                }
            }

            translate(-radius * 2f, -radius * 2f) {
                drawLayer(layer)
            }
        }

        drawContent()
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
    }
}

internal val ShadowMaskPaint: Paint = Paint().apply { blendMode = BlendMode.Clear }
