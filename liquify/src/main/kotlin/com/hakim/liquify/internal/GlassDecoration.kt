/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.internal

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
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
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import com.hakim.liquify.GlassInnerShadow
import com.hakim.liquify.GlassMaterial
import com.hakim.liquify.GlassRim
import com.hakim.liquify.GlassShadow
import com.hakim.liquify.createShader
import com.hakim.liquify.isRenderEffectSupported
import com.hakim.liquify.isRuntimeShaderSupported
import com.hakim.liquify.lerpInnerShadow
import com.hakim.liquify.lerpRim
import com.hakim.liquify.lerpShadow
import com.hakim.liquify.outsetWidth
import com.hakim.liquify.resolvedColor
import com.hakim.liquify.resolvedFor
import kotlin.math.ceil

/**
 * Everything a pane wears *around* its glass: the drop shadow under it, the rim along its border and
 * the inner shadow over it — taken from its material, and blended towards its pressed material by
 * [pressProgress].
 *
 * One node rather than three. It draws in the order they used to nest in — shadow, then the glass
 * and the content, then the rim, then the inner shadow — and it sits *outside* the glass node, whose
 * layer clips to the shape: the shadow and a rim that reaches past the outline have to draw beyond
 * it.
 */
internal class GlassDecorationElement(
    val shapeProvider: ShapeProvider,
    val nodeState: GlassNodeState,
    val material: GlassMaterial,
    val pressedMaterial: GlassMaterial?,
    val pressProgress: (() -> Float)?,
    val darkTheme: Boolean
) : ModifierNodeElement<GlassDecorationNode>() {

    override fun create(): GlassDecorationNode = GlassDecorationNode(
        shapeProvider, nodeState, material, pressedMaterial, pressProgress, darkTheme
    )

    override fun update(node: GlassDecorationNode) {
        node.shapeProvider = shapeProvider
        if (node.nodeState !== nodeState) {
            nodeState.isMerged = node.nodeState.isMerged
            node.nodeState = nodeState
        }
        node.material = material
        node.pressedMaterial = pressedMaterial
        node.pressProgress = pressProgress
        node.darkTheme = darkTheme
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassDecoration"
        properties["rim"] = material.rim
        properties["shadow"] = material.shadow
        properties["innerShadow"] = material.innerShadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlassDecorationElement) return false
        return shapeProvider === other.shapeProvider &&
            nodeState === other.nodeState &&
            material == other.material &&
            pressedMaterial == other.pressedMaterial &&
            pressProgress === other.pressProgress &&
            darkTheme == other.darkTheme
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + nodeState.hashCode()
        result = 31 * result + material.hashCode()
        result = 31 * result + (pressedMaterial?.hashCode() ?: 0)
        result = 31 * result + (pressProgress?.hashCode() ?: 0)
        result = 31 * result + darkTheme.hashCode()
        return result
    }
}

internal class GlassDecorationNode(
    var shapeProvider: ShapeProvider,
    var nodeState: GlassNodeState,
    var material: GlassMaterial,
    var pressedMaterial: GlassMaterial?,
    var pressProgress: (() -> Float)?,
    var darkTheme: Boolean
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    // Each decoration keeps its own recording and only lays it down again when what it is made of
    // changed. Alpha and blend mode are layer properties, so fading any of them costs two writes.
    private var shadowLayer: GraphicsLayer? = null
    private val shadowBlur = BlurLayer()
    private val shadowPaint = Paint()
    private var shadowKey = 0L
    private var hasShadow = false

    private var rimLayer: GraphicsLayer? = null
    private val rimBlur = BlurLayer()
    private val rimPaint = Paint().apply { style = PaintingStyle.Stroke }
    private val rimShaders = RuntimeShaderCacheImpl()
    private var rimKey = 0L
    private var hasRim = false

    private var innerLayer: GraphicsLayer? = null
    private val innerPaint = Paint()
    private var innerRadius = Float.NaN
    private var innerKey = 0L
    private var hasInner = false

    private var clipPath: Path? = null

    override fun ContentDrawScope.draw() {
        // A merged member is grounded and rimmed by its group, along the combined outline; drawing
        // its own here would trace the seam that merging exists to dissolve.
        if (nodeState.isMerged) return drawContent()

        val rest = material
        val pressed = pressedMaterial
        // Read in the draw, so a press repaints the decoration without recomposing anything.
        val fraction = if (pressed == null) 0f else pressProgress?.invoke()?.fastCoerceIn(0f, 1f) ?: 0f
        val shadow = if (pressed == null) rest.shadow else lerpShadow(rest.shadow, pressed.shadow, fraction)
        val rim = if (pressed == null) rest.rim else lerpRim(rest.rim, pressed.rim, fraction)
        val innerShadow =
            if (pressed == null) rest.innerShadow
            else lerpInnerShadow(rest.innerShadow, pressed.innerShadow, fraction)

        if (shadow != null && shadow.alpha > 0f) drawShadow(shadow)
        drawContent()
        if (rim != null && rim.width.value > 0f && rim.alpha > 0f) drawRim(rim)
        if (innerShadow != null && innerShadow.alpha > 0f && isRenderEffectSupported()) {
            drawInnerShadow(innerShadow)
        }
    }

    private fun DrawScope.drawShadow(shadow: GlassShadow) {
        val layer = shadowLayer ?: requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }.also {
            shadowLayer = it
            hasShadow = false
        }

        val radius = shadow.radius.toPx()
        val offsetX = shadow.offset.x.toPx()
        val offsetY = shadow.offset.y.toPx()
        val color = shadow.resolvedColor(darkTheme)

        layer.alpha = shadow.alpha
        layer.blendMode = shadow.blendMode

        val key = shadowKey(color, radius, offsetX, offsetY)
        if (!hasShadow || key != shadowKey) {
            shadowKey = key
            hasShadow = true

            // Two radii of slack on each side: a Gaussian is effectively zero beyond that.
            val shadowSize = IntSize(
                ceil(size.width + radius * 4f + offsetX).toInt(),
                ceil(size.height + radius * 4f + offsetY).toInt()
            )
            val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
            shadowPaint.color = color
            val shape: DrawScope.() -> Unit = {
                translate(radius * 2f + offsetX, radius * 2f + offsetY) {
                    drawContext.canvas.drawOutline(outline, shadowPaint)
                }
            }
            // The shape is drawn sharp and blurred by the layer it is drawn into; see BlurLayer.
            val blurLayer = shadowBlur.blurredBy(requireGraphicsContext(), radius)
            shadowPaint.blur(if (blurLayer == null) radius else 0f)
            blurLayer?.record(shadowSize, shape)

            layer.record(shadowSize) {
                if (blurLayer != null) drawLayer(blurLayer) else shape()
                // Punch the element's own footprint back out, so the shadow never darkens the
                // backdrop seen through the glass.
                translate(radius * 2f, radius * 2f) {
                    drawContext.canvas.drawOutline(outline, ClearPaint)
                }
            }
        }

        translate(-radius * 2f, -radius * 2f) { drawLayer(layer) }
    }

    private fun DrawScope.drawRim(rim: GlassRim) {
        val layer = rimLayer ?: requireGraphicsContext().createGraphicsLayer().also {
            rimLayer = it
            hasRim = false
        }

        layer.alpha = rim.alpha
        layer.blendMode = rim.style.blendMode

        // How far this rim may reach past the outline, and the slack the recording needs for it and
        // for the blur, which softens the stroke outwards.
        val outset = rim.style.outsetWidth.toPx()
        val blurRadius = rim.blurRadius.toPx()
        val slack = ceil(1f + outset + blurRadius)

        val key = rimKey(rim)
        if (!hasRim || key != rimKey) {
            rimKey = key
            hasRim = true

            val recordSize = IntSize(
                ceil(size.width + slack * 2f).toInt(),
                ceil(size.height + slack * 2f).toInt()
            )
            val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
            // The stroke follows the curve; the clip that keeps it inside is the rounded rectangle,
            // which costs no mask — this layer is replayed on every frame its pane draws.
            val clip = shapeProvider.clipShape.createOutline(size, layoutDirection, this)
            val path = if (clip is Outline.Rounded) clipPath ?: Path().also { clipPath = it } else null

            val style = rim.style.resolvedFor(darkTheme)
            rimPaint.color = style.color
            // Doubled because half of the stroke is thrown away: clipped off outside the outline, or
            // — for a rim that straddles the border — spent on the far side of it.
            val width = rim.width.toPx().fastCoerceAtMost(size.minDimension / 2f)
            rimPaint.strokeWidth = ceil(maxOf(width, outset)) * 2f
            if (isRuntimeShaderSupported()) {
                rimPaint.setRuntimeShader(style.createShader(this, shapeProvider.innerShape, rimShaders))
            }
            val stroke: DrawScope.() -> Unit = {
                translate(slack, slack) { drawContext.canvas.drawOutline(outline, rimPaint) }
            }
            // A soft rim is stroked into a layer that blurs it, as the shadow is. The blur takes the
            // shader's colour along with the stroke — a colour that changes only slowly around the
            // border, so what softens is the stroke's edge, as with a mask filter.
            val blurLayer = rimBlur.blurredBy(requireGraphicsContext(), blurRadius)
            rimPaint.blur(if (blurLayer == null) blurRadius else 0f)
            blurLayer?.record(recordSize, stroke)
            // Offscreen keeps the blurred stroke as a texture: the blur runs when the rim is recorded.
            layer.compositingStrategy =
                if (blurLayer != null) CompositingStrategy.Offscreen else CompositingStrategy.Auto

            layer.record(recordSize) {
                val canvas = drawContext.canvas
                canvas.save()
                // Clipped to the outline, a wide rim grows inwards instead of bleeding past the
                // edge — and so does its blur, clipped after it. A style that wants to draw outside
                // says so with its outset width.
                if (outset <= 0f) {
                    canvas.translate(slack, slack)
                    canvas.clipOutline(clip, path)
                    canvas.translate(-slack, -slack)
                }
                if (blurLayer != null) drawLayer(blurLayer) else stroke()
                canvas.restore()
            }
        }

        translate(-slack, -slack) { drawLayer(layer) }
    }

    private fun DrawScope.drawInnerShadow(shadow: GlassInnerShadow) {
        val layer = innerLayer ?: requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }.also {
            innerLayer = it
            innerRadius = Float.NaN
            hasInner = false
        }

        val radius = shadow.radius.toPx()
        val offsetX = shadow.offset.x.toPx()
        val offsetY = shadow.offset.y.toPx()
        val color = shadow.resolvedColor(darkTheme)

        // Needed every frame, because the final draw is clipped to it too — but cached, so the shape
        // only rebuilds its path when it is resized or reshaped. The clip is the rounded rectangle,
        // which costs no mask on each of those frames; see ShapeProvider.clipShape.
        val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
        val clip = shapeProvider.clipShape.createOutline(size, layoutDirection, this)
        val path = if (clip is Outline.Rounded) clipPath ?: Path().also { clipPath = it } else null

        layer.alpha = shadow.alpha
        layer.blendMode = shadow.blendMode
        // The radius reaches the layer as a render effect and never touches the recording.
        if (innerRadius != radius) {
            layer.renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
            innerRadius = radius
        }

        val key = innerKey(color, offsetX, offsetY)
        if (!hasInner || key != innerKey) {
            innerKey = key
            hasInner = true
            innerPaint.color = color

            layer.record {
                val canvas = drawContext.canvas
                canvas.save()
                canvas.clipOutline(clip, path)
                canvas.drawOutline(outline, innerPaint)
                // Removing an offset copy of the shape leaves only the crescent along the inner edge.
                canvas.translate(offsetX, offsetY)
                canvas.drawOutline(outline, ClearPaint)
                canvas.translate(-offsetX, -offsetY)
                canvas.restore()
            }
        }

        val canvas = drawContext.canvas
        canvas.save()
        canvas.clipOutline(clip, path)
        drawLayer(layer)
        canvas.restore()
    }

    private fun DrawScope.shadowKey(color: Color, radius: Float, offsetX: Float, offsetY: Float): Long {
        var hash = geometryKey()
        hash = hash * 31L + color.hashCode()
        hash = hash * 31L + radius.toRawBits()
        hash = hash * 31L + offsetX.toRawBits()
        hash = hash * 31L + offsetY.toRawBits()
        return hash
    }

    private fun DrawScope.rimKey(rim: GlassRim): Long {
        var hash = geometryKey()
        hash = hash * 31L + rim.width.value.toRawBits()
        hash = hash * 31L + rim.blurRadius.value.toRawBits()
        hash = hash * 31L + rim.style.hashCode()
        // A style that resolves its colours against the theme hashes the same in both.
        hash = hash * 31L + if (darkTheme) 1L else 0L
        return hash
    }

    private fun DrawScope.innerKey(color: Color, offsetX: Float, offsetY: Float): Long {
        var hash = geometryKey()
        hash = hash * 31L + color.hashCode()
        hash = hash * 31L + offsetX.toRawBits()
        hash = hash * 31L + offsetY.toRawBits()
        return hash
    }

    private fun DrawScope.geometryKey(): Long {
        var hash = 17L
        hash = hash * 31L + size.width.toRawBits()
        hash = hash * 31L + size.height.toRawBits()
        hash = hash * 31L + density.toRawBits()
        hash = hash * 31L + layoutDirection.ordinal
        hash = hash * 31L + shapeProvider.innerShape.hashCode()
        return hash
    }

    override fun onDetach() {
        val context = requireGraphicsContext()
        shadowLayer?.let { context.releaseGraphicsLayer(it) }
        rimLayer?.let { context.releaseGraphicsLayer(it) }
        innerLayer?.let { context.releaseGraphicsLayer(it) }
        shadowBlur.release(context)
        rimBlur.release(context)
        shadowLayer = null
        rimLayer = null
        innerLayer = null
        hasShadow = false
        hasRim = false
        hasInner = false
        innerRadius = Float.NaN
        clipPath = null
        rimShaders.clear()
    }
}

private val ClearPaint: Paint = Paint().apply { blendMode = BlendMode.Clear }

/**
 * A layer that blurs what is recorded into it — the shape of a drop shadow, the stroke of a soft rim —
 * with a render effect, the same Gaussian a mask filter of that radius applies.
 *
 * It is drawn into the decoration's offscreen layer, whose texture keeps the blurred result, so the
 * blur runs when the decoration is recorded rather than on every frame its pane draws. That matters:
 * Android 14 keeps no render effect's result on older Qualcomm drivers and runs it again on every draw.
 *
 * Not a mask filter, the obvious tool: on a path, the renderer rasterises and blurs a mask on the GPU
 * for it. Measured on a mid-range phone (Adreno 642L, Android 14), the GPU memory held for those masks
 * was never handed back — some megabytes more with every screen of glass, until the app reached its
 * GPU budget and every frame got slower. Below API 31, which has no render effects, a mask filter is
 * still what blurs.
 */
private class BlurLayer {

    private var layer: GraphicsLayer? = null
    private var radius = Float.NaN

    /**
     * The layer, blurring by [radius] — or `null` for no blur, or where there is no render effect to
     * blur with. The effect is only replaced when the radius changes.
     */
    fun blurredBy(context: GraphicsContext, radius: Float): GraphicsLayer? {
        if (radius <= 0f || !isRenderEffectSupported()) return null
        val layer = layer ?: context.createGraphicsLayer().also { layer = it }
        if (this.radius != radius) {
            layer.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
            this.radius = radius
        }
        return layer
    }

    fun release(context: GraphicsContext) {
        layer?.let { context.releaseGraphicsLayer(it) }
        layer = null
        radius = Float.NaN
    }
}
