package org.jetbrains.compose.swing.node

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.DeclaredNodesListener
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifierDiff
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A measuring parent holds no detached layout node of a child: it is declared to again as a node detaches, before
 * another node of the child's modifier runs, and once more as the pass ends only where something else changed. A
 * child that is released is declared without its nodes and its parent is not laid out for it; a manager that throws
 * at that declaration leaves the child released.
 */
class DetachedLayoutNodeDeclarationTest {
    @Test
    fun theParentHoldsNoDetachedLayoutNodeAsANodeThatStandsIsWritten() {
        val owner = TestCompositionOwner()
        val layout = RecordingMeasurementLayout()
        val child = attachedChild(owner, JButton("child"), layout)
        val held = mutableListOf<List<Boolean>>()
        val watch = { held += layout.attachedNodesLastDeclared() }
        child.applyModifierDiff(
            SwingModifier then WatchedLayoutNodeElement("a", watch) then
                RecordingLayoutNodeElement("leaving", mutableListOf()),
        )
        val standing = child.declaration.parentLayoutElements.first()
        held.clear()

        child.applyModifierDiff(SwingModifier then WatchedLayoutNodeElement("c", watch))

        assertEquals(listOf(listOf(true)), held, "the parent holds the standing node alone as it is written")
        assertEquals(listOf(standing), layout.declarations.last().elements)
        owner.dispose()
    }

    @Test
    fun theParentHoldsNoDetachedLayoutNodeAsAnotherNodeLeaves() {
        val owner = TestCompositionOwner()
        val layout = RecordingMeasurementLayout()
        val child = attachedChild(owner, JButton("child"), layout)
        val held = mutableListOf<List<Boolean>>()
        val watch = { held += layout.attachedNodesLastDeclared() }
        child.applyModifierDiff(
            SwingModifier then WatchedLayoutNodeElement("a", watch) then WatchedLayoutNodeElement("b", watch),
        )
        held.clear()

        child.applyModifierDiff(SwingModifier)

        assertEquals(
            listOf(listOf(true, true), listOf(true)),
            held,
            "the node declared last detaches first, and the parent holds the other alone as that one detaches",
        )
        assertEquals(emptyList(), layout.declarations.last().elements)
        owner.dispose()
    }

    @Test
    fun theParentHoldsNoDetachedLayoutNodeOnceAnElementOfAnotherKindTakesItsSlot() {
        val owner = TestCompositionOwner()
        val layout = RecordingMeasurementLayout()
        val child = attachedChild(owner, JButton("child"), layout)
        val held = mutableListOf<List<Boolean>>()
        val watch = { held += layout.attachedNodesLastDeclared() }
        child.applyModifierDiff(
            SwingModifier then ChainLayoutNodeElement("first", mutableListOf(), mutableListOf()) then
                WatchedLayoutNodeElement("a", watch),
        )
        held.clear()

        child.applyModifierDiff(
            SwingModifier then RecordingLayoutNodeElement("first", mutableListOf()) then
                WatchedLayoutNodeElement("c", watch),
        )

        assertEquals(listOf(listOf(true)), held, "the parent holds no detached node as the node after it is written")
        assertEquals(
            child.declaration.parentLayoutElements,
            layout.declarations.last().elements,
            "the parent is declared to again once the pass ends",
        )
        assertEquals(listOf(true, true), layout.attachedNodesLastDeclared(), "with the node that took the slot")
        owner.dispose()
    }

    @Test
    fun aLayoutNodeLeavingBesideOneThatStandsUnwrittenHasItsParentDeclaredToOnce() {
        val owner = TestCompositionOwner()
        val layout = RecordingMeasurementLayout()
        val child = attachedChild(owner, JButton("child"), layout)
        val events = mutableListOf<String>()
        child.applyModifierDiff(
            SwingModifier then RecordingLayoutNodeElement("a", events) then RecordingLayoutNodeElement("b", events),
        )
        val standing = child.declaration.parentLayoutElements.first()
        val declarations = layout.declarations.size

        child.applyModifierDiff(SwingModifier then RecordingLayoutNodeElement("a", events))

        assertEquals(declarations + 1, layout.declarations.size, "the parent must be declared to once")
        assertEquals(listOf(standing), layout.declarations.last().elements)
        owner.dispose()
    }

    @Test
    fun aDeactivatedComponentsParentIsDeclaredToWithoutItsLayoutNodesAndLaidOutAnewBeforeItLeaves() =
        runComposeSwingTest {
            var active by mutableStateOf(true)
            val inner = RecordingMeasurementLayout()
            val events = mutableListOf<String>()
            val padding = TestParentLayoutElement("padding")
            setContent {
                SwingNode(factory = { JPanel(EventRecordingLayout(inner, events)) }, content = {
                    ReusableContentHost(active) {
                        SwingNode(
                            factory = { JLabel() },
                            modifier = SwingModifier then padding then RecordingLayoutNodeElement("a", mutableListOf()),
                        )
                    }
                })
            }
            val declarations = inner.declarations.size
            events.clear()

            active = false
            awaitIdle()

            assertEquals(declarations + 1, inner.declarations.size, "the parent must be declared to once")
            assertEquals(listOf<ParentLayoutElement>(padding), inner.declarations.last().elements)
            assertEquals(
                listOf("declared", "invalidated", "removed"),
                events.distinct(),
                "the parent is laid out anew after it is declared to and before the component leaves it",
            )
        }

    @Test
    fun aReleasedSubtreeDeclaresTheElementsLeftToItsParentWithoutLayingItOut() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val layout = RecordingMeasurementLayout()
        val padding = TestParentLayoutElement("padding")
        setContent {
            if (shown) {
                SwingNode(factory = { JPanel() }, content = {
                    SwingNode(factory = { JPanel(layout) }, content = {
                        SwingNode(
                            factory = { JLabel() },
                            modifier =
                                SwingModifier then padding then RecordingLayoutNodeElement("a", mutableListOf()),
                        )
                    })
                })
            }
        }
        val declarations = layout.declarations.size
        val invalidations = layout.invalidations.size

        shown = false
        awaitIdle()

        assertEquals(declarations + 1, layout.declarations.size, "the parent must be declared to once")
        assertEquals(listOf<ParentLayoutElement>(padding), layout.declarations.last().elements)
        assertEquals(invalidations, layout.invalidations.size, "the parent is not laid out for a released component")
    }

    @Test
    fun aManagerThrowingAsAReleasedComponentIsDeclaredLeavesItReleased() {
        val child = NodeListeningLabel()
        var released = false

        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent {
                        SwingNode(factory = { JPanel(NodeRequiringLayout()) }, content = {
                            SwingNode(
                                factory = { child },
                                modifier = SwingModifier then RecordingLayoutNodeElement("a", mutableListOf()),
                                onRelease = { released = true },
                            )
                        })
                    }
                }
            }

        // Disposal runs as the test ends, and runTest rethrows a copy of its failure with the original as the cause.
        assertEquals(NODE_REQUIRED, (failure.cause ?: failure).message)
        assertEquals(emptyList(), child.handed.last(), "the component carries no node of the modifier")
        assertTrue(released, "the onRelease block of the component runs")
    }

    @Test
    fun aManagerThrowingAsADeactivatedComponentIsDeclaredLeavesItParked() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val child = NodeListeningLabel()
        var released = false
        setContent {
            SwingNode(factory = { JPanel(NodeRequiringLayout()) }, content = {
                ReusableContentHost(active) {
                    SwingNode(
                        factory = { child },
                        modifier = SwingModifier then RecordingLayoutNodeElement("a", mutableListOf()),
                        onRelease = { released = true },
                    )
                }
            })
        }

        active = false
        val failure = assertFailsWith<IllegalStateException> { awaitIdle() }

        assertEquals(NODE_REQUIRED, failure.message)
        assertEquals(emptyList(), child.handed.last(), "the component carries no node of the modifier")
        assertNull(child.parent, "the component leaves its parent")
        assertTrue(released, "the onRelease block of the component runs")
    }
}

private const val NODE_REQUIRED = "the declaration holds no layout node"

/** Refuses a declaration that holds no layout node, as a manager written to find its node among the elements does. */
private class NodeRequiringLayout : MeasurementLayoutManager by RecordingMeasurementLayout() {
    override fun declareComponentLayout(
        component: Component,
        parentData: Any?,
        elements: List<ParentLayoutElement>,
    ) = check(elements.any { it is ParentLayoutNode }) { NODE_REQUIRED }
}

/** Delegates to [inner] and appends what happens to it to [events], in order. */
private class EventRecordingLayout(
    private val inner: RecordingMeasurementLayout,
    private val events: MutableList<String>,
) : MeasurementLayoutManager by inner {
    override fun declareComponentLayout(
        component: Component,
        parentData: Any?,
        elements: List<ParentLayoutElement>,
    ) {
        events += "declared"
        inner.declareComponentLayout(component, parentData, elements)
    }

    override fun invalidateLayout(target: Container) {
        events += "invalidated"
        inner.invalidateLayout(target)
    }

    override fun removeLayoutComponent(component: Component) {
        events += "removed"
        inner.removeLayoutComponent(component)
    }
}

/** Records the nodes it is handed. */
private class NodeListeningLabel :
    JLabel(),
    DeclaredNodesListener {
    val handed = mutableListOf<List<SwingModifier.Node>>()

    override fun onDeclaredNodesChanged(nodes: List<SwingModifier.Node>) {
        handed += nodes
    }
}

/** Whether each element of the last declaration, all of them layout nodes, is attached. */
private fun RecordingMeasurementLayout.attachedNodesLastDeclared(): List<Boolean> =
    declarations.last().elements.map { (it as ParentLayoutNode).isAttached }

/** Declares a node that runs [watch] as the element writes it and as it detaches. */
private class WatchedLayoutNodeElement(
    private val value: String,
    private val watch: () -> Unit,
) : ParentLayoutNodeElement<WatchedLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    /** Keyed by value, so two declared together hold two slots. */
    override val key: Any get() = value

    override fun create(): WatchedLayoutNode = WatchedLayoutNode(watch)

    override fun update(node: WatchedLayoutNode) = watch()

    override fun equals(other: Any?): Boolean = other is WatchedLayoutNodeElement && other.value == value

    override fun hashCode(): Int = value.hashCode()
}

private class WatchedLayoutNode(
    private val watch: () -> Unit,
) : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun onDetach() = watch()
}
