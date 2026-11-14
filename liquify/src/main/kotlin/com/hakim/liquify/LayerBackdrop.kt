/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import com.hakim.liquify.internal.InverseLayerScope
import com.hakim.liquify.internal.recordLayer

private val DefaultOnDraw: ContentDrawScope.() -> Unit = { drawContent() }

/**
 * A backdrop that captures whatever is marked with [Modifier.layerBackdrop] — the usual way to put
 * glass over real UI. Re-recorded every frame, so it tracks scrolling and animation.
 *
 * ```
 * val backdrop = rememberLayerBackdrop()
 *
 * Box {
 *     Image(painter, null, Modifier.fillMaxSize().layerBackdrop(backdrop))
 *     LiquifyTheme(backdrop = backdrop) { … }
 * }
 * ```
 *
 * @param drawContentFromLayer record the marked content once and draw the recording to the screen,
 *   instead of drawing the content and recording it a second time. Halves the cost of a full-screen
 *   content layer — but the layer is recorded at the node's size, so anything a child draws past
 *   those bounds is cropped. Right for a full-screen layer, wrong for a small one around controls
 *   that overshoot their box while pressed.
 * @param onDraw what is recorded; draw a background colour before `drawContent()` so glass over the
 *   system bars never samples transparent pixels.
 */
@Composable
public fun rememberLayerBackdrop(
    drawContentFromLayer: Boolean = false,
    onDraw: ContentDrawScope.() -> Unit = DefaultOnDraw
): LayerBackdrop {
    val graphicsLayer = rememberGraphicsLayer()
    return remember(graphicsLayer, drawContentFromLayer, onDraw) {
        LayerBackdrop(graphicsLayer, drawContentFromLayer, onDraw)
    }
}

/**
 * A [Backdrop] backed by a captured layer. Also an *export* target: hand one to `liquify`'s
 * `exportedBackdrop` and the element records its own glass into it, so a second element can refract
 * the first.
 */
@Stable
public class LayerBackdrop internal constructor(
    internal val graphicsLayer: GraphicsLayer,
    internal val drawContentFromLayer: Boolean,
    internal val onDraw: ContentDrawScope.() -> Unit
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)

    private var inverseLayerScope: InverseLayerScope? = null

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val coordinates = coordinates ?: return
        val layerCoordinates = layerCoordinates ?: return
        withTransform({
            if (layerBlock != null) {
                with(obtainInverseLayerScope()) { inverseTransform(density, layerBlock) }
            }
            val offset = try {
                layerCoordinates.localPositionOf(coordinates)
            } catch (_: RuntimeException) {
                // Thrown while the two are not in the same layout tree yet. Window coordinates keep the
                // frame usable; they are only wrong under an outer transform, which the next layout
                // pass resolves.
                coordinates.positionInWindow() - layerCoordinates.positionInWindow()
            }
            translate(-offset.x, -offset.y)
        }) {
            drawLayer(graphicsLayer)
        }
    }

    private fun obtainInverseLayerScope(): InverseLayerScope =
        inverseLayerScope?.apply { reset() } ?: InverseLayerScope().also { inverseLayerScope = it }
}

/** Marks this composable's drawing as the content of [backdrop]. */
public fun Modifier.layerBackdrop(backdrop: LayerBackdrop): Modifier =
    this then LayerBackdropElement(backdrop)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode = LayerBackdropNode(backdrop)

    override fun update(node: LayerBackdropNode) {
        if (node.backdrop != backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is LayerBackdropElement && backdrop == other.backdrop)

    override fun hashCode(): Int = backdrop.hashCode()
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    override fun ContentDrawScope.draw() {
        val layer = backdrop.graphicsLayer
        if (backdrop.drawContentFromLayer) {
            // One rasterisation instead of two: record first, then composite the finished layer.
            recordLayer(layer, requireDensity()) { backdrop.onDraw(this@draw) }
            drawLayer(layer)
        } else {
            drawContent()
            recordLayer(layer, requireDensity()) { backdrop.onDraw(this@draw) }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }

    override fun onDetach() {
        backdrop.layerCoordinates = null
    }
}
