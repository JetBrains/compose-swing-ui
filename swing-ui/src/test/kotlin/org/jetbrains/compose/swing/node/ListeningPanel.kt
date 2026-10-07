package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JPanel

/** One call a [ListeningPanel] was told: its [kind] and the [node] it names. */
internal data class Event(
    val kind: String,
    val node: SwingComponentNode<*>,
)

/** Records each node it is told attached or detached, throwing on detach where [failsOnDetach] holds. */
internal class ListeningPanel(
    var failsOnDetach: Boolean = false,
) : JPanel(),
    SwingComponentNodeListener {
    val events = ArrayList<Event>()

    override fun onAttached(node: SwingComponentNode<*>) {
        events += Event("attached", node)
    }

    override fun onDetached(node: SwingComponentNode<*>) {
        events += Event("detached", node)
        check(!failsOnDetach) { "The listener failed on detach" }
    }
}

/** Declares a node that hands itself to [created]. */
internal class PanelProbe(
    private val created: (SwingModifier.Node) -> Unit,
) : SwingModifier.NodeElement<ListeningPanel, PanelProbeNode>() {
    override val targetType: Class<ListeningPanel> get() = ListeningPanel::class.java

    override val name: String get() = "probe"

    override fun create(): PanelProbeNode = PanelProbeNode().also(created)

    override fun update(node: PanelProbeNode) = Unit

    override fun equals(other: Any?): Boolean = other is PanelProbe

    override fun hashCode(): Int = PanelProbe::class.hashCode()
}

internal class PanelProbeNode : SwingModifier.ComponentNode<ListeningPanel>()
