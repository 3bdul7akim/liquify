package com.hakim.liquify

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

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

    var refractionHeight: Float = 0f
        private set

    var refractionAmount: Float = 0f
        private set

    var depthEffect: Float = 0f
        private set

    /** Records the parameters of a `lens()` call; the group builds the actual shader. */
    fun recordLens(refractionHeight: Float, refractionAmount: Float, depthEffect: Boolean) {
        this.refractionHeight = refractionHeight
        this.refractionAmount = refractionAmount
        this.depthEffect = if (depthEffect) 1f else 0f
    }

    override fun reset() {
        super.reset()
        refractionHeight = 0f
        refractionAmount = 0f
        depthEffect = 0f
    }

    fun resetLens() {
        refractionHeight = 0f
        refractionAmount = 0f
        depthEffect = 0f
    }
}
