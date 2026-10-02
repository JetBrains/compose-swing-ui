package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/** A node attached to another composition attaches its modifier nodes again as its first pass attached them. */
class ModifierNodeReattachTest {
    @Test
    fun aMovedNodeReattachesInLifecycleOrderAndVisitsInDeclaredOrder() = runComposeSwingTest {
        val events = mutableListOf<String>()
        val sightings = mutableListOf<List<String>>()
        val visiting = VisitingNode(sightings)
        val modifier =
            SwingModifier
                .then(EventElement("property", events))
                .then(VisitingElement(visiting))
                .then(RecordingLayoutNodeElement("layout", events))
        val nested =
            composeInNestedComposition {
                SwingNode(factory = { JPanel(RecordingMeasurementLayout()) }) {
                    SwingNode(factory = { JPanel() }, modifier = modifier)
                }
            }
        assertEquals(
            listOf("create(layout)", "onAttach", "update(layout)", "onAttach property", "update property"),
            events,
            "the initial attach creates and attaches layout nodes before component nodes",
        )
        assertEquals(listOf(listOf("additive", "layout")), sightings, "the first pass visits from onAttach")
        events.clear()
        sightings.clear()

        nested.moveContentOut()
        awaitIdle()
        nested.dispose()

        assertEquals(
            listOf(
                "onDetach property",
                "onDetach",
                "onAttach",
                "update(layout)",
                "onAttach property",
                "update property",
            ),
            events,
            "the move detaches component nodes first and reattaches layout nodes first without recreating them",
        )
        assertEquals(listOf(listOf("additive", "layout")), sightings, "a move visits from onAttach as the first pass")
        assertEquals(
            listOf("additive", "layout"),
            buildList { visiting.visitDeclaredNodes { add(it.label) } },
            "after the move the public visit still follows declaration order",
        )
    }

    /** A property element keyed by [name] that records its node's lifecycle and its own updates. */
    private class EventElement(
        override val name: String,
        private val events: MutableList<String>,
    ) : SwingModifier.NodeElement<JComponent, EventNode>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val key: Any get() = name

        override fun create(): EventNode = EventNode(name, events)

        override fun update(node: EventNode) {
            events += "update $name"
        }

        override fun equals(other: Any?): Boolean =
            other is EventElement && name == other.name && events === other.events

        override fun hashCode(): Int = 31 * name.hashCode() + System.identityHashCode(events)
    }

    private class EventNode(
        private val name: String,
        private val events: MutableList<String>,
    ) : SwingModifier.ComponentNode<JComponent>() {
        override fun onAttach() {
            events += "onAttach $name"
        }

        override fun onDetach() {
            events += "onDetach $name"
        }
    }

    /** An additive element whose node records, from each `onAttach`, the declared nodes it visits. */
    private class VisitingElement(
        private val node: VisitingNode,
    ) : SwingModifier.NodeElement<JComponent, VisitingNode>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val additive: Boolean get() = true

        override fun create(): VisitingNode = node

        override fun update(node: VisitingNode) = Unit

        override fun equals(other: Any?): Boolean = other is VisitingElement && node === other.node

        override fun hashCode(): Int = System.identityHashCode(node)
    }

    private class VisitingNode(
        private val sightings: MutableList<List<String>>,
    ) : SwingModifier.ComponentNode<JComponent>() {
        override fun onAttach() {
            sightings.add(buildList { visitDeclaredNodes { add(it.label) } })
        }
    }
}

private val SwingModifier.Node.label: String get() = if (this is ParentLayoutNode) "layout" else "additive"
