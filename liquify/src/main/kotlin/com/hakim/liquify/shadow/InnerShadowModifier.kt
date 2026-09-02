/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    val nodeState: GlassNodeState,
    val darkTheme: Boolean
) : ModifierNodeElement<InnerShadowNode>() {

    override fun create(): InnerShadowNode =
        InnerShadowNode(shapeProvider, shadow, nodeState, darkTheme)

    override fun update(node: InnerShadowNode) {
        node.shapeProvider = shapeProvider
        node.shadow = shadow
        if (node.nodeState !== nodeState) {
            nodeState.isMerged = node.nodeState.isMerged
            node.nodeState = nodeState
        }
        node.darkTheme = darkTheme
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "innerShadow"
        properties["shapeProvider"] = shapeProvider
        properties["shadow"] = shadow
        properties["darkTheme"] = darkTheme
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InnerShadowElement) return false
        return shapeProvider == other.shapeProvider &&
            shadow == other.shadow &&
            nodeState === other.nodeState &&
            darkTheme == other.darkTheme
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + shadow.hashCode()
        result = 31 * result + nodeState.hashCode()
        result = 31 * result + darkTheme.hashCode()
        return result
    }
}

internal class InnerShadowNode(
    var shapeProvider: ShapeProvider,
    var shadow: () -> InnerShadow?,
    var nodeState: GlassNodeState,
    var darkTheme: Boolean
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()
    private var clipPath: Path? = null
    private var appliedRadius = Float.NaN
    private var recordedKey = 0L
    private var hasRecording = false

    override fun ContentDrawScope.draw() {
        drawContent()

        if (!isRenderEffectSupported()) return
        val shadow = shadow()
        if (shadow == null || nodeState.isMerged) return

        val layer = shadowLayer ?: return

        val radius = shadow.radius.toPx()
        val offsetX = shadow.offset.x.toPx()
        val offsetY = shadow.offset.y.toPx()
        // As with the drop shadow: an unspecified colour is resolved against the theme here, where
        // it costs nothing, rather than by handing back a rebuilt InnerShadow every frame.
        val color = shadow.color.takeOrElse { InnerShadow.defaultColor(darkTheme) }

        // Needed on every frame, because the final draw is clipped to it as well — but taken from
        // the cache, so the shape only rebuilds its path when it is actually resized or reshaped.
        val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
        val clipPath =
            if (outline is Outline.Rounded) clipPath ?: Path().also { clipPath = it } else null

        // Layer properties, so opacity and blur can animate without rebuilding the crescent.
        layer.alpha = shadow.alpha
        layer.blendMode = shadow.blendMode
        if (appliedRadius != radius) {
            layer.renderEffect =
                if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
            appliedRadius = radius
        }

        // The radius is absent from the key: it reaches the layer as a render effect above and
        // never touches what was recorded.
        val key = recordingKey(color, offsetX, offsetY)
        if (!hasRecording || key != recordedKey) {
            recordedKey = key
            hasRecording = true

            paint.color = color

            layer.record {
                val canvas = drawContext.canvas
                canvas.save()
                canvas.clipOutline(outline, clipPath)
                canvas.drawOutline(outline, paint)
                // Removing an offset copy of the shape leaves only the crescent along the inner
                // edge.
                canvas.translate(offsetX, offsetY)
                canvas.drawOutline(outline, ShadowMaskPaint)
                canvas.translate(-offsetX, -offsetY)
                canvas.restore()
            }
        }

        val canvas = drawContext.canvas
        canvas.save()
        canvas.clipOutline(outline, clipPath)
        drawLayer(layer)
        canvas.restore()
    }

    /** What the recorded crescent is made of. Alpha, blend mode and blur radius stay out of it. */
    private fun DrawScope.recordingKey(
        color: Color,
        offsetX: Float,
        offsetY: Float
    ): Long {
        var hash = 17L
        hash = hash * 31L + size.width.toRawBits()
        hash = hash * 31L + size.height.toRawBits()
        hash = hash * 31L + density.toRawBits()
        hash = hash * 31L + layoutDirection.ordinal
        hash = hash * 31L + shapeProvider.shape.hashCode()
        hash = hash * 31L + color.hashCode()
        hash = hash * 31L + offsetX.toRawBits()
        hash = hash * 31L + offsetY.toRawBits()
        return hash
    }

    override fun onAttach() {
        shadowLayer = requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
        // A fresh layer holds no recording, whatever the key says.
        hasRecording = false
    }

    override fun onDetach() {
        shadowLayer?.let { layer ->
            requireGraphicsContext().releaseGraphicsLayer(layer)
            shadowLayer = null
        }
        clipPath = null
        appliedRadius = Float.NaN
        hasRecording = false
    }
}
