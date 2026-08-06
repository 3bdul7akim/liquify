/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.highlight

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceAtMost
import com.hakim.liquify.RuntimeShaderCacheImpl
import com.hakim.liquify.internal.GlassNodeState
import com.hakim.liquify.internal.ShapeProvider
import com.hakim.liquify.internal.blur
import com.hakim.liquify.internal.clipOutline
import com.hakim.liquify.internal.setRuntimeShader
import com.hakim.liquify.isRuntimeShaderSupported
import kotlin.math.ceil

internal class HighlightElement(
    val shapeProvider: ShapeProvider,
    val highlight: () -> Highlight?,
    val nodeState: GlassNodeState
) : ModifierNodeElement<HighlightNode>() {

    override fun create(): HighlightNode = HighlightNode(shapeProvider, highlight, nodeState)

    override fun update(node: HighlightNode) {
        node.shapeProvider = shapeProvider
        node.highlight = highlight
        if (node.nodeState !== nodeState) {
            nodeState.isMerged = node.nodeState.isMerged
            node.nodeState = nodeState
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "highlight"
        properties["shapeProvider"] = shapeProvider
        properties["highlight"] = highlight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HighlightElement) return false
        return shapeProvider == other.shapeProvider &&
            highlight == other.highlight &&
            nodeState === other.nodeState
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + highlight.hashCode()
        result = 31 * result + nodeState.hashCode()
        return result
    }
}

internal class HighlightNode(
    var shapeProvider: ShapeProvider,
    var highlight: () -> Highlight?,
    var nodeState: GlassNodeState
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var highlightLayer: GraphicsLayer? = null
    private val paint = Paint().apply { style = PaintingStyle.Stroke }
    private var clipPath: Path? = null
    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    override fun ContentDrawScope.draw() {
        val highlight = highlight()
        // A merged element's rim is drawn by the group along the combined outline; drawing it here
        // too would trace the seam that merging is meant to dissolve.
        if (highlight == null || highlight.width.value <= 0f || nodeState.isMerged) {
            return drawContent()
        }

        drawContent()

        val layer = highlightLayer ?: return

        // One pixel of slack on every side so the blurred stroke is not clipped by the layer.
        val safeSize = IntSize(ceil(size.width).toInt() + 2, ceil(size.height).toInt() + 2)
        val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
        val clipPath =
            if (outline is Outline.Rounded) clipPath ?: Path().also { clipPath = it } else null

        configurePaint(highlight)

        layer.alpha = highlight.alpha
        layer.blendMode = highlight.style.blendMode
        layer.record(safeSize) {
            translate(1f, 1f) {
                val canvas = drawContext.canvas
                canvas.save()
                // Clipping to the outline keeps the outer half of the stroke off the screen, so a
                // wide rim grows inwards instead of bleeding past the element's edge.
                canvas.clipOutline(outline, clipPath)
                canvas.drawOutline(outline, paint)
                canvas.restore()
            }
        }

        translate(-1f, -1f) {
            drawLayer(layer)
        }
    }

    override fun onAttach() {
        highlightLayer = requireGraphicsContext().createGraphicsLayer()
    }

    override fun onDetach() {
        highlightLayer?.let { layer ->
            requireGraphicsContext().releaseGraphicsLayer(layer)
            highlightLayer = null
        }
        clipPath = null
        runtimeShaderCache.clear()
    }

    private fun DrawScope.configurePaint(highlight: Highlight) {
        paint.color = highlight.style.color
        // Doubled because the outer half is clipped away by the outline above.
        paint.strokeWidth =
            ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f
        paint.blur(highlight.blurRadius.toPx())
        if (isRuntimeShaderSupported()) {
            val shader = with(highlight.style) {
                createShader(shapeProvider.innerShape, runtimeShaderCache)
            }
            paint.setRuntimeShader(shader)
        }
    }
}
