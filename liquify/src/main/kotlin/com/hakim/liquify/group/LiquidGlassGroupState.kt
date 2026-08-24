package com.hakim.liquify.group

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import com.hakim.liquify.Backdrop
import com.hakim.liquify.BackdropEffectScope
import com.hakim.liquify.interaction.InteractiveHighlight

/**
 * The enclosing merged surface, if any.
 *
 * [liquify][com.hakim.liquify.liquify] reads this to find out whether a
 * [merge][com.hakim.liquify.effects.merge] call has anywhere to go. It is `null` outside a
 * [LiquidGlassGroup], which is what makes `merge()` degrade silently instead of failing.
 */
public val LocalLiquidGlassGroup: ProvidableCompositionLocal<LiquidGlassGroupState?> =
    compositionLocalOf { null }

/**
 * What a group tells its members so they do not have to repeat it.
 *
 * Members of one merged surface are, by definition, made of the same glass — declaring the
 * material once on the group and inheriting it is both less to write and harder to get wrong than
 * copying an effects block into every child.
 */
internal class GroupDefaults(
    val backdrop: Backdrop,
    val effects: BackdropEffectScope.() -> Unit,
    val mergeAmount: Float
)

internal val LocalGroupDefaults = compositionLocalOf<GroupDefaults?> { null }

/** Remembers the state of one merged surface. */
@Composable
public fun rememberLiquidGlassGroupState(): LiquidGlassGroupState =
    remember { LiquidGlassGroupState() }

/**
 * Holds the members of a merged surface and their live geometry.
 *
 * Members register themselves during layout, so the group always renders the positions of the
 * frame being drawn — which is what keeps the bridge between two animating elements attached
 * instead of trailing a frame behind.
 */
@Stable
public class LiquidGlassGroupState {

    internal val members: MutableList<GlassMember> = mutableListOf()

    /** Coordinates of the group itself; every member's geometry is expressed relative to these. */
    internal var coordinates: LayoutCoordinates? = null

    /** Set by the group's draw node so members can request a redraw when they move. */
    internal var onInvalidate: (() -> Unit)? = null

    internal fun attach(member: GlassMember) {
        if (members.none { it === member }) {
            members += member
            invalidate()
        }
    }

    internal fun detach(member: GlassMember) {
        if (members.removeAll { it === member }) {
            invalidate()
        }
    }

    internal fun invalidate() {
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

    /** Smoothing radius `k` in pixels; the reach over which this member fuses with its neighbours. */
    var mergeRadius: Float = 0f

    /**
     * The member's own effect block, used as the group's effect stack when the group does not
     * declare one of its own.
     */
    var effects: (BackdropEffectScope.() -> Unit)? = null

    /**
     * Touch state of this member, if it has any.
     *
     * The group reads it for the swell factors a dragged member applies as a layer scale, which
     * layout never sees — the merged field has to be grown by exactly the same amount or the glass
     * comes away from the content. The glow itself is drawn by the member, not from here.
     */
    var interaction: InteractiveHighlight? = null

    /**
     * Colour this member contributes to the merged surface, or [Color.Unspecified] for none.
     *
     * The alpha carries the strength of the tint. The merge program blends these across members
     * with the same weights it blends their distance fields with, so the colour follows the
     * geometry of the fusion rather than an axis through the group.
     */
    var tint: Color = Color.Unspecified

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
