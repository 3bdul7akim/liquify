package com.hakim.liquify.internal

/**
 * The one piece of state the two nodes of a single `liquify` call have to agree on.
 *
 * When an element joins a merged group, its glass, rim and shadows are all rendered by the group, so
 * its own nodes must stand down. The glass node decides this during layout — before anything is
 * drawn — and the decoration node, which draws *before* it in the same frame, reads the decision
 * here. That ordering is why this cannot simply live on the glass node.
 */
internal class GlassNodeState {

    var isMerged: Boolean = false
}
