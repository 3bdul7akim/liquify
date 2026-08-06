package com.hakim.liquify.internal

/**
 * The one piece of state the three nodes of a single `liquify` call have to agree on.
 *
 * When an element joins a merged group its glass, rim and shadow are all rendered by the group
 * instead, so those nodes must stand down. The backdrop node decides this during layout — before
 * anything is drawn — and the shadow and highlight nodes, which draw *before* it in the same
 * frame, read the decision here. That ordering is why this cannot simply live on the backdrop node.
 */
internal class GlassNodeState {

    var isMerged: Boolean = false
}
