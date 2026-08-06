/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.DefaultCameraDistance
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.unit.Density
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A throwaway [GraphicsLayerScope] used to *read back* the transform a caller declared in a
 * `layerBlock`, so the backdrop can be drawn with that transform inverted.
 *
 * A glass element that is scaled or rotated must still sample the backdrop in unscaled screen
 * space, otherwise the refracted content would scale along with the element and the illusion of
 * looking *through* the glass breaks.
 */
internal class InverseLayerScope : GraphicsLayerScope {

    override var size: Size = Size.Unspecified
    override var density: Float = 1f
    override var fontScale: Float = 1f

    override var scaleX: Float = 1f
    override var scaleY: Float = 1f
    override var alpha: Float = 1f
    override var translationX: Float = 0f
    override var translationY: Float = 0f
    override var shadowElevation: Float = 0f
    override var ambientShadowColor: Color = DefaultShadowColor
    override var spotShadowColor: Color = DefaultShadowColor
    override var rotationX: Float = 0f
    override var rotationY: Float = 0f
    override var rotationZ: Float = 0f
    override var cameraDistance: Float = DefaultCameraDistance
    override var transformOrigin: TransformOrigin = TransformOrigin.Center
    override var shape: Shape = RectangleShape
    override var clip: Boolean = false
    override var renderEffect: RenderEffect? = null
    override var blendMode: BlendMode = BlendMode.SrcOver
    override var colorFilter: ColorFilter? = null
    override var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto

    private var matrix: Matrix? = null

    fun DrawTransform.inverseTransform(
        density: Density,
        layerBlock: GraphicsLayerScope.() -> Unit
    ) {
        this@InverseLayerScope.size = size
        this@InverseLayerScope.density = density.density
        fontScale = density.fontScale

        layerBlock()

        inverseTransformAtTopLeft(rotationZ, scaleX, scaleY)
    }

    fun reset() {
        size = Size.Unspecified
        density = 1f
        fontScale = 1f

        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        translationX = 0f
        translationY = 0f
        shadowElevation = 0f
        ambientShadowColor = DefaultShadowColor
        spotShadowColor = DefaultShadowColor
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
        cameraDistance = DefaultCameraDistance
        transformOrigin = TransformOrigin.Center
        shape = RectangleShape
        clip = false
        renderEffect = null
        blendMode = BlendMode.SrcOver
        colorFilter = null
        compositingStrategy = CompositingStrategy.Auto
    }

    private fun DrawTransform.inverseTransformAtTopLeft(
        rotationZ: Float,
        scaleX: Float,
        scaleY: Float
    ) {
        if (rotationZ == 0f) {
            if (scaleX != 0f && scaleY != 0f && (scaleX != 1f || scaleY != 1f)) {
                scale(1f / scaleX, 1f / scaleY, Offset.Zero)
            }
            return
        }

        val matrix = matrix ?: Matrix().also { this@InverseLayerScope.matrix = it }

        val radians = rotationZ * (PI / 180.0)
        val sinZ = sin(radians).toFloat()
        val cosZ = cos(radians).toFloat()

        val a00 = cosZ * scaleX
        val a01 = sinZ * scaleY
        val a10 = -sinZ * scaleX
        val a11 = cosZ * scaleY

        val determinant = a00 * a11 - a01 * a10
        if (determinant == 0f) return

        val inverseDeterminant = 1f / determinant
        matrix.reset()
        matrix[0, 0] = a11 * inverseDeterminant
        matrix[0, 1] = -a01 * inverseDeterminant
        matrix[1, 0] = -a10 * inverseDeterminant
        matrix[1, 1] = a00 * inverseDeterminant

        transform(matrix)
    }
}
