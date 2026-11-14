package com.hakim.liquify.internal

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import com.hakim.liquify.Backdrop
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.GlassInteraction

/**
 * The merged surface an element sits in, or `null` — outside a group, or in a group that does not
 * merge. Read by the glass node during layout.
 */
internal val LocalLiquidGlassGroup: ProvidableCompositionLocal<LiquidGlassGroupState?> =
    compositionLocalOf { null }

/** What a merging group hands its members, so they do not have to repeat it. */
internal class GroupDefaults(
    val backdrop: Backdrop,
    /** The glass of the whole body; a member runs it, then joins with [mergeAmount]. */
    val effects: BackdropEffectScope.() -> Unit,
    val mergeAmount: Float
)

internal val LocalGroupDefaults: ProvidableCompositionLocal<GroupDefaults?> =
    compositionLocalOf { null }

/**
 * The members of one merged surface and their live geometry.
 *
 * Members register during layout, so the group always renders the positions of the frame being
 * drawn — which is what keeps a bridge between two animating elements attached instead of trailing
 * a frame behind.
 */
@Stable
internal class LiquidGlassGroupState {

    val members: MutableList<GlassMember> = mutableListOf()

    /** The group's own coordinates; every member's geometry is relative to these. */
    var coordinates: LayoutCoordinates? = null

    /** Set by the group's draw node, so members can ask for a redraw when they move. */
    var onInvalidate: (() -> Unit)? = null

    fun attach(member: GlassMember) {
        if (members.none { it === member }) {
            members += member
            invalidate()
        }
    }

    fun detach(member: GlassMember) {
        if (members.removeAll { it === member }) invalidate()
    }

    fun invalidate() {
        onInvalidate?.invoke()
    }
}

/** One element's contribution to the merged distance field. */
internal class GlassMember {

    var centerX: Float = 0f
    var centerY: Float = 0f
    var halfWidth: Float = 0f
    var halfHeight: Float = 0f

    /** Corner radii, top-left, top-right, bottom-right, bottom-left. */
    val cornerRadii: FloatArray = FloatArray(4)

    /**
     * The box as drawn this frame — the one above, swollen by the stretch of a drag, which layout
     * never sees. Worked out by the group once per frame; everything it hands the GPU is built from
     * it, and [cornerRadii] are multiplied by [drawnRadiusScale] in it.
     */
    var drawnCenterX: Float = 0f
    var drawnCenterY: Float = 0f
    var drawnHalfWidth: Float = 0f
    var drawnHalfHeight: Float = 0f
    var drawnRadiusScale: Float = 1f

    /** Smoothing radius `k` in pixels: the reach over which this member fuses with its neighbours. */
    var mergeRadius: Float = 0f

    /**
     * Touch state, if any. The group applies the very swell factors a dragged member applies as a
     * layer scale, or the glass would come away from the content.
     */
    var interaction: GlassInteraction? = null

    /**
     * Colour this member contributes, or [Color.Unspecified]. Its alpha is the strength; the merge
     * program blends these with the weights it blends the distance fields with.
     */
    var tint: Color = Color.Unspecified

    /** How much of another member's colour has flowed into this one, `0..1`. */
    var flowAmount: Float = 0f

    /** The member whose colour [flowAmount] is, or `null`. */
    var flowSource: GlassMember? = null

    /**
     * Whether the member lights up under the finger. Its own light stands down while it is merged;
     * the group lays the light into the body instead, mixed through the bridges like [tint].
     */
    var glows: Boolean = false

    /**
     * This frame's touch light, worked out by the group: the lit point in group content space, how
     * far the light reaches and how strong it is — zero while the member is not held — and its colour.
     */
    var lightX: Float = 0f
    var lightY: Float = 0f
    var lightReach: Float = 0f
    var lightStrength: Float = 0f
    var lightColor: Color = Color.White

    fun setGeometry(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        radii: FloatArray?
    ): Boolean {
        val newCenterX = left + width / 2f
        val newCenterY = top + height / 2f
        val newHalfWidth = width / 2f
        val newHalfHeight = height / 2f

        var changed = newCenterX != centerX ||
            newCenterY != centerY ||
            newHalfWidth != halfWidth ||
            newHalfHeight != halfHeight

        centerX = newCenterX
        centerY = newCenterY
        halfWidth = newHalfWidth
        halfHeight = newHalfHeight

        if (radii != null) {
            for (i in 0..3) {
                val clamped = radii[i].coerceAtMost(minOf(newHalfWidth, newHalfHeight))
                if (cornerRadii[i] != clamped) {
                    cornerRadii[i] = clamped
                    changed = true
                }
            }
        }
        return changed
    }
}
