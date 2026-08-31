package org.jetbrains.compose.swing.node

import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import org.jetbrains.compose.swing.modifier.SwingModifier
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

    /**
     * The modifier chain the composition last declared for [component], and [SwingModifier] itself where
     * it declared none. Walk it with [SwingModifier.foldIn], and read each element's
     * [name][SwingModifier.NodeElement.name] and
     * [declaredValues][SwingModifier.NodeElement.declaredValues] to show what the component carries.
     *
     * It is the whole declared chain, placement included: an element saying where the component sits in
     * its parent stands in it alongside the ones saying what it looks like.
     *
     * It answers whatever the composition declared last, whether or not the pass that declared it had
     * anything to write, so it never lags the composition. Every node holds its chain whatever
     * [org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled] says; what that switch decides is
     * whether a tool can reach the node at all.
     */
    public val modifier: SwingModifier
}
