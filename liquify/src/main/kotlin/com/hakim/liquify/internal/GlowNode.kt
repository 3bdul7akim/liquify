package com.hakim.liquify.internal

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import com.hakim.liquify.GlassInteraction

/**
 * One element's touch light: [GlassInteraction.modifier], or a `liquify` element's own.
 *
 * @param nodeState the element's glass, when this is its own light — so the light can stand down
 *   while the element is merged. `null` for a light added by hand, which always draws.
 * @param tint the element's own colour, which the light takes on; see [touchLightColor].
 */
internal class GlowElement(
    private val interaction: GlassInteraction,
    private val nodeState: GlassNodeState? = null,
    private val tint: Color = Color.Unspecified,
) : ModifierNodeElement<GlowNode>() {

    override fun create(): GlowNode = GlowNode(interaction, nodeState, tint)

    override fun update(node: GlowNode) {
        node.update(interaction, nodeState, tint)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassGlow"
        properties["tint"] = tint
    }

    override fun equals(other: Any?): Boolean =
        other is GlowElement &&
            other.interaction === interaction &&
            other.nodeState === nodeState &&
            other.tint == tint

    override fun hashCode(): Int {
        var result = System.identityHashCode(interaction)
        result = 31 * result + System.identityHashCode(nodeState)
        result = 31 * result + tint.hashCode()
        return result
    }
}

/**
 * Draws one element's touch light, over its glass and under its content, in the colour of the glass.
 *
 * It stands down while the element is a member of a merged group. The group lays the light into the
 * body itself, mixed through the bridges with the very weights it mixes the members' colours with —
 * drawn here, inside the member's own clip, it would stop dead at the member's outline while the body
 * and its colours carry on across the bridge.
 */
internal class GlowNode(
    source: GlassInteraction,
    private var nodeState: GlassNodeState?,
    tint: Color,
) : DrawModifierNode, Modifier.Node() {

    private var source: GlassInteraction = source

    /** The light's colour, worked out when the tint changes rather than on every frame of a press. */
    private var color: Color = touchLightColor(source.glowColor, tint)

    fun update(source: GlassInteraction, nodeState: GlassNodeState?, tint: Color) {
        this.source = source
        this.nodeState = nodeState
        color = touchLightColor(source.glowColor, tint)
    }

    override fun ContentDrawScope.draw() {
        if (nodeState?.isMerged != true) {
            with(source) { drawGlow(color) }
        }
        drawContent()
    }
}
