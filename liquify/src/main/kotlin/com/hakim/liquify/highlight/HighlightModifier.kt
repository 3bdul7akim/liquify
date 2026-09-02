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
    val nodeState: GlassNodeState,
    val darkTheme: Boolean
) : ModifierNodeElement<HighlightNode>() {

    override fun create(): HighlightNode =
        HighlightNode(shapeProvider, highlight, nodeState, darkTheme)

    override fun update(node: HighlightNode) {
        node.shapeProvider = shapeProvider
        node.highlight = highlight
        if (node.nodeState !== nodeState) {
            nodeState.isMerged = node.nodeState.isMerged
            node.nodeState = nodeState
        }
        node.darkTheme = darkTheme
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "highlight"
        properties["shapeProvider"] = shapeProvider
        properties["highlight"] = highlight
        properties["darkTheme"] = darkTheme
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HighlightElement) return false
        return shapeProvider == other.shapeProvider &&
            highlight == other.highlight &&
            nodeState === other.nodeState &&
            darkTheme == other.darkTheme
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + highlight.hashCode()
        result = 31 * result + nodeState.hashCode()
        result = 31 * result + darkTheme.hashCode()
        return result
    }
}

internal class HighlightNode(
    var shapeProvider: ShapeProvider,
    var highlight: () -> Highlight?,
    var nodeState: GlassNodeState,
    var darkTheme: Boolean
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var highlightLayer: GraphicsLayer? = null
    private val paint = Paint().apply { style = PaintingStyle.Stroke }
    private var clipPath: Path? = null
    private val runtimeShaderCache = RuntimeShaderCacheImpl()
    private var recordedKey = 0L
    private var hasRecording = false

    override fun ContentDrawScope.draw() {
        val highlight = highlight()
        // A merged element's rim is drawn by the group along the combined outline; drawing it here
        // too would trace the seam that merging is meant to dissolve.
        if (highlight == null || highlight.width.value <= 0f || nodeState.isMerged) {
            return drawContent()
        }

        drawContent()

        val layer = highlightLayer ?: return

        // Layer properties rather than recorded content, so a rim that only animates its opacity
        // costs two writes instead of a fresh stroke, an outline and a mask filter every frame.
        layer.alpha = highlight.alpha
        layer.blendMode = highlight.style.blendMode

        val key = recordingKey(highlight)
        if (!hasRecording || key != recordedKey) {
            recordedKey = key
            hasRecording = true

            // One pixel of slack on every side so the blurred stroke is not clipped by the layer.
            val safeSize = IntSize(ceil(size.width).toInt() + 2, ceil(size.height).toInt() + 2)
            val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
            val clipPath =
                if (outline is Outline.Rounded) clipPath ?: Path().also { clipPath = it } else null

            configurePaint(highlight)

            layer.record(safeSize) {
                translate(1f, 1f) {
                    val canvas = drawContext.canvas
                    canvas.save()
                    // Clipping to the outline keeps the outer half of the stroke off the screen, so
                    // a wide rim grows inwards instead of bleeding past the element's edge.
                    canvas.clipOutline(outline, clipPath)
                    canvas.drawOutline(outline, paint)
                    canvas.restore()
                }
            }
        }

        translate(-1f, -1f) {
            drawLayer(layer)
        }
    }

    /**
     * Everything the recorded stroke is made of, folded into one value.
     *
     * Alpha and blend mode are deliberately absent: they are applied to the layer and never reach
     * the recording, which is what lets a pulsing rim run without rebuilding anything. A pointer
     * driven style *is* in here through its own hash, so a [HighlightStyle.Dynamic] rim still
     * re-records as the finger moves.
     */
    private fun DrawScope.recordingKey(highlight: Highlight): Long {
        var hash = 17L
        hash = hash * 31L + size.width.toRawBits()
        hash = hash * 31L + size.height.toRawBits()
        hash = hash * 31L + density.toRawBits()
        hash = hash * 31L + layoutDirection.ordinal
        hash = hash * 31L + shapeProvider.shape.hashCode()
        hash = hash * 31L + highlight.width.value.toRawBits()
        hash = hash * 31L + highlight.blurRadius.value.toRawBits()
        hash = hash * 31L + highlight.style.hashCode()
        // A style that resolves its colours against the theme hashes the same in both, so the
        // theme itself has to be in here or a light/dark switch would keep the old stroke.
        hash = hash * 31L + if (darkTheme) 1L else 0L
        return hash
    }

    override fun onAttach() {
        highlightLayer = requireGraphicsContext().createGraphicsLayer()
        // A fresh layer holds no recording, whatever the key says.
        hasRecording = false
    }

    override fun onDetach() {
        highlightLayer?.let { layer ->
            requireGraphicsContext().releaseGraphicsLayer(layer)
            highlightLayer = null
        }
        clipPath = null
        hasRecording = false
        runtimeShaderCache.clear()
    }

    private fun DrawScope.configurePaint(highlight: Highlight) {
        // Most styles hand themselves straight back — a specular rim is white over anything. One
        // does not: HighlightStyle.TwSe flips with the surface it sits on, and this is where it
        // learns which that is. Only reached when the recording actually changes, so a style is
        // free to allocate here.
        val style = highlight.style.resolvedFor(darkTheme)

        paint.color = style.color
        // Doubled because the outer half is clipped away by the outline above.
        paint.strokeWidth =
            ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f
        paint.blur(highlight.blurRadius.toPx())
        if (isRuntimeShaderSupported()) {
            val shader = with(style) {
                createShader(shapeProvider.innerShape, runtimeShaderCache)
            }
            paint.setRuntimeShader(shader)
        }
    }
}
