@file:JvmMultifileClass
@file:JvmName("ToolingKt")

package org.jetbrains.compose.swing.tooling

import org.jetbrains.compose.swing.core.checkEventDispatchThread
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.util.Key
import org.jetbrains.compose.swing.util.get
import org.jetbrains.compose.swing.util.set
import javax.swing.JComponent

/**
 * The node a composition stamped on this exact component, or `null` where none did.
 *
 * It is the live node: [SwingComponentNode.component] is this component, and [SwingComponentNode.modifier]
 * is the modifier chain the composition declares for it now - reading it needs no slot table walk, unlike
 * [findDeclaringGroup].
 *
 * It answers only for a component a composition stamped while [isDebugInspectorInfoEnabled] was on when
 * that component was inserted. Content mounted under a context a caller captured with
 * `rememberCompositionContext()` is reached on the next pass it takes for any reason, rather than at once
 * - the same caveat [isDebugInspectorInfoEnabled] documents.
 *
 * This reads the receiver alone, not its Swing ancestors, so a component that stands inside a composition
 * without being declared by it - one built by hand and added beside declared content - answers `null`
 * here even where [findCompositionData] answers with the composition it stands in.
 *
 * The stamp is a client property, so the receiver is a [JComponent]. A composition that declares a raw
 * [java.awt.Component] carries no bag to stamp; [findDeclaringGroup] is what answers with the node there.
 *
 * A component this answered for stops answering once the node that stamped it is released - the
 * composition removed the component, or the switch turned off, which re-inserts the content into fresh,
 * unstamped components.
 *
 * It is not a [androidx.compose.runtime.tooling.CompositionGroup]: [findDeclaringGroup] is what answers
 * with the group, for `sourceInfo`, `data` and `identity`.
 *
 * Must be called on the Event Dispatch Thread.
 */
public fun JComponent.composedNode(): SwingComponentNode? {
    checkEventDispatchThread()
    return this[NODE_KEY]
}

/**
 * Client property key under which the [SwingComponentNode] a composition declared for a component is
 * published on that same component. Written only while [isDebugInspectorInfoEnabled] is on when the node
 * is inserted, and cleared when the node that published it is released or deactivated.
 */
internal val NODE_KEY: Key<SwingComponentNode> = Key("org.jetbrains.compose.swing.node")
