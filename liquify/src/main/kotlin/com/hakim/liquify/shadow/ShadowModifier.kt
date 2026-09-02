/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    val nodeState: GlassNodeState,
    val darkTheme: Boolean
) : ModifierNodeElement<ShadowNode>() {

    override fun create(): ShadowNode = ShadowNode(shapeProvider, shadow, nodeState, darkTheme)

    override fun update(node: ShadowNode) {
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
        name = "shadow"
        properties["shapeProvider"] = shapeProvider
        properties["shadow"] = shadow
        properties["darkTheme"] = darkTheme
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShadowElement) return false
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

internal class ShadowNode(
    var shapeProvider: ShapeProvider,
    var shadow: () -> Shadow?,
    var nodeState: GlassNodeState,
    var darkTheme: Boolean
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()
    private var recordedKey = 0L
    private var hasRecording = false

    override fun ContentDrawScope.draw() {
        val shadow = shadow()
        // Merged elements are grounded by one shadow cast by the whole group.
        if (shadow == null || nodeState.isMerged) return drawContent()

        val layer = shadowLayer
        if (layer != null) {
            val radius = shadow.radius.toPx()
            val offsetX = shadow.offset.x.toPx()
            val offsetY = shadow.offset.y.toPx()
            // An unspecified colour means "whatever suits the theme" — black ink over a light UI,
            // and a pale halo over a dark one, where black ink would not be there at all. Resolved
            // here rather than by rebuilding the Shadow, so it costs nothing per frame: Color is a
            // value class, and takeOrElse is inline.
            val color = shadow.color.takeOrElse { Shadow.defaultColor(darkTheme) }

            // Layer properties rather than recorded content: a shadow that only fades in and out
            // costs two writes instead of a fresh outline, mask filter and recording per frame.
            layer.alpha = shadow.alpha
            layer.blendMode = shadow.blendMode

            val key = recordingKey(color, radius, offsetX, offsetY)
            if (!hasRecording || key != recordedKey) {
                recordedKey = key
                hasRecording = true

                // Two radii of slack on each side: a Gaussian is effectively zero beyond that.
                val shadowSize = IntSize(
                    ceil(size.width + radius * 4f + offsetX).toInt(),
                    ceil(size.height + radius * 4f + offsetY).toInt()
                )
                val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)

                paint.color = color
                paint.blur(radius)

                layer.record(shadowSize) {
                    translate(radius * 2f + offsetX, radius * 2f + offsetY) {
                        val canvas = drawContext.canvas
                        canvas.drawOutline(outline, paint)
                        // Punch the element's own footprint back out, so the shadow never darkens
                        // the backdrop that is visible through the glass.
                        canvas.translate(-offsetX, -offsetY)
                        canvas.drawOutline(outline, ShadowMaskPaint)
                        canvas.translate(offsetX, offsetY)
                    }
                }
            }

            translate(-radius * 2f, -radius * 2f) {
                drawLayer(layer)
            }
        }

        drawContent()
    }

    /**
     * Everything the recorded shadow is made of, folded into one value. Alpha and blend mode are
     * absent on purpose — they reach the layer, never the recording.
     */
    private fun DrawScope.recordingKey(
        color: Color,
        radius: Float,
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
        hash = hash * 31L + radius.toRawBits()
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
        hasRecording = false
    }
}

internal val ShadowMaskPaint: Paint = Paint().apply { blendMode = BlendMode.Clear }
