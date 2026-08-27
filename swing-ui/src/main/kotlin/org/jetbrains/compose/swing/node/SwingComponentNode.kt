package org.jetbrains.compose.swing.node

import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import java.awt.Component

/**
 * The node a Swing composition stores for one declared component, read off a node group of its
 * [CompositionData]:
 *
 * ```
 * val component = (group.node as? SwingComponentNode)?.component
 * ```
 *
 * A group whose [CompositionGroup.node] is not a [SwingComponentNode] declared no Swing component - it is
 * a control-flow or call group, or a node of some other composition sharing the tree.
 *
 * [org.jetbrains.compose.swing.tooling.findDeclaringGroup] answers from the other end, starting at a
 * component and returning the group holding its node.
 */
public sealed interface SwingComponentNode {
    /**
     * The component this node holds, for as long as the composition holds the node.
     *
     * It is the live component: the composition still owns every property a declared parameter governs,
     * and writing one here is replaced on the next pass that applies it.
     */
    public val component: Component
}
