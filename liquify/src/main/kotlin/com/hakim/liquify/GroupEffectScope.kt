package com.hakim.liquify

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.hakim.liquify.internal.refractionAmountPx
import com.hakim.liquify.internal.refractionHeightPx

/**
 * Effect scope of a merged surface.
 *
 * Shape-independent effects — blur, vibrancy, tint — chain exactly as they do for an element. The
 * lens does not: a merged body has no single rounded rectangle to refract through, so the call only
 * records how each member is to be refracted — band, bend, dome and fringe — and the group's merge
 * program does the bending along the combined outline.
 */
internal class GroupEffectScope : BaseBackdropEffectScope() {

    override val shape: Shape get() = RectangleShape

    /** The material whose size rule refracts every member by its own size, or `null`. */
    private var lensMaterial: GlassMaterial? = null

    /** An explicit `lens()`: the same band and bend for every member. */
    private var fixedHeight = 0f
    private var fixedAmount = 0f

    var depthEffect: Float = 0f
        private set

    /** Whether the body fringes where it bends, as a single pane's lens does with it. */
    var chromaticAberration: Boolean = false
        private set

    override fun lensPx(
        refractionHeight: Float,
        refractionAmount: Float,
        depthEffect: Float,
        chromaticAberration: Boolean
    ) {
        if (!refracts || refractionHeight <= 0f || refractionAmount <= 0f) return
        lensMaterial = null
        fixedHeight = refractionHeight
        fixedAmount = refractionAmount
        this.depthEffect = depthEffect
        this.chromaticAberration = chromaticAberration
    }

    /** Records `material()`: each member is refracted as that material would refract it alone. */
    fun recordMaterial(material: GlassMaterial) {
        if (!refracts) return
        lensMaterial = material
        fixedHeight = 0f
        fixedAmount = 0f
        depthEffect = if (material.depthEffect) 1f else 0f
        chromaticAberration = material.chromaticAberration
    }

    /** The refracting band of a member [width] × [height] in pixels. */
    fun memberRefractionHeight(width: Float, height: Float): Float =
        lensMaterial?.refractionHeightPx(width, height, this) ?: fixedHeight

    /** The displacement of a member [width] × [height] in pixels. */
    fun memberRefractionAmount(width: Float, height: Float): Float =
        lensMaterial?.refractionAmountPx(width, height, this) ?: fixedAmount

    /** Everything the recorded lens is made of, for the group's uniform fingerprint. */
    fun lensFingerprint(): Long {
        var hash = (lensMaterial?.hashCode() ?: 0).toLong()
        hash = hash * 31L + fixedHeight.toRawBits()
        hash = hash * 31L + fixedAmount.toRawBits()
        hash = hash * 31L + depthEffect.toRawBits()
        hash = hash * 31L + if (chromaticAberration) 1L else 0L
        return hash
    }

    override fun reset() {
        super.reset()
        resetLens()
    }

    fun resetLens() {
        lensMaterial = null
        fixedHeight = 0f
        fixedAmount = 0f
        depthEffect = 0f
        chromaticAberration = false
    }
}
