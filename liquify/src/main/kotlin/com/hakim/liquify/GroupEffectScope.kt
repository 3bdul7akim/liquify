package com.hakim.liquify

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.hakim.liquify.effects.refractionAmountForSize
import com.hakim.liquify.effects.refractionHeightForSize

/**
 * Effect scope of a merged surface.
 *
 * It behaves exactly like an element scope for everything that is shape independent — `blur()`,
 * `vibrancy()`, `tint()` and friends chain onto [renderEffect] unchanged. `lens()` is different:
 * a merged surface has no single rounded rectangle to refract through, so the call only records
 * its parameters here and the group node feeds them into the merged-field program together with
 * the rim light and the clipping mask.
 */
internal class GroupEffectScope : BaseBackdropEffectScope() {

    override val shape: Shape get() = RectangleShape

    /**
     * Whether the members are refracted by their own size (`lensBySize()`) rather than by the one
     * fixed pair below (`lens()`). See [memberRefractionHeight].
     */
    var refractionBySize: Boolean = false
        private set

    /** The band and the displacement of a fixed `lens()`, or the scales of a `lensBySize()`. */
    var refractionHeight: Float = 0f
        private set

    var refractionAmount: Float = 0f
        private set

    var depthEffect: Float = 0f
        private set

    /** Records the parameters of a `lens()` call; the group builds the actual shader. */
    fun recordLens(refractionHeight: Float, refractionAmount: Float, depthEffect: Boolean) {
        this.refractionBySize = false
        this.refractionHeight = refractionHeight
        this.refractionAmount = refractionAmount
        this.depthEffect = if (depthEffect) 1f else 0f
    }

    /** Records a `lensBySize()` call: every member is refracted by its own size, scaled by these. */
    fun recordLensBySize(heightScale: Float, amountScale: Float, depthEffect: Boolean) {
        this.refractionBySize = true
        this.refractionHeight = heightScale
        this.refractionAmount = amountScale
        this.depthEffect = if (depthEffect) 1f else 0f
    }

    /** The refracting band of a member [width] × [height] in pixels, under whichever lens was recorded. */
    fun memberRefractionHeight(width: Float, height: Float): Float =
        if (refractionBySize) refractionHeightForSize(width, height) * refractionHeight
        else refractionHeight

    /** The displacement of a member [width] × [height] in pixels, likewise. */
    fun memberRefractionAmount(width: Float, height: Float): Float =
        if (refractionBySize) refractionAmountForSize(width, height) * refractionAmount
        else refractionAmount

    override fun reset() {
        super.reset()
        resetLens()
    }

    fun resetLens() {
        refractionBySize = false
        refractionHeight = 0f
        refractionAmount = 0f
        depthEffect = 0f
    }
}
