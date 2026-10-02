package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Job
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.node.ObserverModifierNode
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.TestMeasurementParentProtocol
import org.jetbrains.compose.swing.node.observeReads
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Component
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ModifierChainFailureTest {
    @Test
    fun failedDetachStillWithdrawsParentDeclarationAndNodeHandover() = runComposeSwingTest {
        val declarationFailure = IllegalArgumentException("parent declaration failed")
        val handoverFailure = UnsupportedOperationException("node handover failed")
        var failEmptyDeclaration = false
        val layout =
            object : RecordingMeasurementLayout() {
                override fun declareComponentLayout(
                    component: Component,
                    parentData: Any?,
                    elements: List<ParentLayoutElement>,
                ) {
                    super.declareComponentLayout(component, parentData, elements)
                    if (failEmptyDeclaration && elements.isEmpty()) throw declarationFailure
                }
            }
        val component =
            object : JPanel(), DeclaredNodesListener {
                var emptyHandovers = 0

                override fun onDeclaredNodesChanged(
                    componentNode: SwingComponentNode<*>,
                    nodes: List<SwingModifier.Node>,
                ) {
                    if (nodes.isEmpty()) {
                        emptyHandovers++
                        throw handoverFailure
                    }
                }
            }
        val node = ProbeNode()
        val layoutNode =
            object : ParentLayoutNode() {
                override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol
            }
        val element =
            object : ParentLayoutNodeElement<ParentLayoutNode>() {
                override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

                override fun create(): ParentLayoutNode = layoutNode

                override fun update(node: ParentLayoutNode) = Unit

                override fun equals(other: Any?): Boolean = this === other

                override fun hashCode(): Int = System.identityHashCode(this)
            }
        var active by mutableStateOf(true)
        setContent {
            SwingNode(factory = { JPanel(layout) }) {
                ReusableContentHost(active = active) {
                    SwingNode(
                        factory = { component },
                        modifier =
                            SwingModifier
                                .then(NodeOf(node))
                                .then(element),
                    )
                }
            }
        }
        assertEquals(listOf(layoutNode), layout.elementsOf(component))
        failEmptyDeclaration = true

        active = false
        val reported = assertFailsWith<UnsupportedOperationException> { awaitIdle() }

        assertSame(handoverFailure, reported)
        val declaration = reported.suppressed.single()
        assertIs<IllegalArgumentException>(declaration)
        assertEquals(declarationFailure.message, declaration.message)
        assertTrue(declaration.suppressed.isEmpty())
        assertEquals(listOf(true, true), node.peersAttachedOnDetach, "both declared nodes are attached")
        assertFalse(node.isAttached)
        assertFalse(layoutNode.isAttached)
        assertTrue(checkNotNull(node.attachedJob).isCancelled)
        val withdrawal = layout.declarations.last()
        assertTrue(withdrawal.elements.isEmpty(), "the measuring parent holds no retired node")
        assertEquals(1, component.emptyHandovers)
    }

    @TestFactory
    fun aFailingTeardownStepStillDetachesEveryNodeOfTheChain(): List<DynamicTest> {
        val detach = IllegalStateException("detach failed")
        val peer = IllegalArgumentException("peer detach failed")
        val reset = IllegalStateException("reset failed")
        val watched = mutableIntStateOf(0)
        val cases =
            mapOf(
                "onDetach throws" to
                    Teardown(
                        Trigger.Removal,
                        nodes =
                            listOf(
                                ProbeNode(detachFailure = peer, watched = watched),
                                ProbeNode(detachFailure = detach, watched = watched),
                            ),
                        primary = detach,
                        carried = setOf(detach, peer),
                        detachCalls = 2,
                    ),
                "onReset throws" to
                    Teardown(
                        Trigger.Reuse,
                        nodes = listOf(ProbeNode(), ProbeNode(resetFailure = reset)),
                        primary = reset,
                        detachCalls = 2,
                    ),
            )
        return cases.map { (case, teardown) -> DynamicTest.dynamicTest(case) { teardown.run() } }
    }

    private sealed interface Trigger {
        data object Removal : Trigger

        data object Reuse : Trigger
    }

    /**
     * A chain of [nodes] on [panel] torn down by [trigger], one step of which fails. The caller catches [primary] as
     * the root cause of what it receives (which failure is first is not pinned when it is null), and finds exactly the
     * failures of [carried] among the causes and suppressed, however they are wrapped. [detachCalls] of the nodes run
     * `onDetach`, each with every node of the chain still attached; all of them end detached, with their jobs
     * cancelled. Nodes that observe a state (removal only) are notified of a change made while they are attached,
     * and of none made after the chain is gone.
     * With [inspector] the debug inspector info is on for the run.
     */
    private class Teardown(
        val trigger: Trigger,
        val nodes: List<ProbeNode>,
        val primary: Throwable?,
        val detachCalls: Int,
        val carried: Set<Throwable> = setOfNotNull(primary),
        val inspector: Boolean = false,
    ) {
        private val panel = JPanel()
        private val watched = nodes.firstNotNullOfOrNull { it.watched }

        private val chain =
            nodes.fold<ProbeNode, SwingModifier>(SwingModifier) { modifier, node -> modifier.then(NodeOf(node)) }

        fun run() {
            SwingUtilities.invokeAndWait { isDebugInspectorInfoEnabled = inspector }
            try {
                remove()
            } finally {
                SwingUtilities.invokeAndWait { isDebugInspectorInfoEnabled = false }
            }
        }

        /** Takes the chain away from the composition, or parks it for reuse, and verifies what that throws. */
        private fun remove() = runComposeSwingTest {
            var present by mutableStateOf(true)
            setContent {
                val content: @Composable () -> Unit = { SwingNode(factory = { panel }, modifier = chain) }
                if (trigger == Trigger.Reuse) {
                    ReusableContentHost(active = present, content)
                } else if (present) {
                    content()
                }
            }

            watched?.let { state ->
                Snapshot.withMutableSnapshot { state.intValue = 1 }
                awaitIdle()
                assertEquals(List(nodes.size) { 1 }, nodes.map { it.observedChanges }, "attached nodes observe")
            }

            present = false
            verify(assertFailsWith<Throwable> { awaitIdle() })

            watched?.let { state ->
                Snapshot.withMutableSnapshot { state.intValue = 2 }
                awaitIdle()
                assertEquals(List(nodes.size) { 1 }, nodes.map { it.observedChanges }, "detached nodes observe nothing")
            }

            if (trigger == Trigger.Reuse) {
                assertTrue(nodes.all { it.resetCalls == 1 }, "no reset is skipped")
                assertTrue(nodes.all { it.peersAttachedOnReset == attachedPeers }, "every node resets with its peers")
                present = true
                awaitIdle()
                assertTrue(nodes.all { it.isAttached }, "the next pass attaches the chain again")
            }
        }

        private fun verify(thrown: Throwable) {
            if (primary != null) assertSame(primary, generateSequence(thrown) { it.cause }.last())
            val reached = thrown.failures().map { if (it in carried) it else it.cause }.toSet()
            assertEquals(carried, reached, "every failure reaches the caller, and nothing else does")
            assertEquals(detachCalls, nodes.sumOf { it.detachCalls })
            assertTrue(
                nodes.filter { it.detachCalls > 0 }.all { it.peersAttachedOnDetach == attachedPeers },
                "every detaching node has its peers attached",
            )
            assertTrue(nodes.none { it.isAttached })
            assertTrue(nodes.all { checkNotNull(it.attachedJob).isCancelled })
        }

        private val attachedPeers = List(nodes.size) { true }
    }

    private class ProbeNode(
        private val resetFailure: Throwable? = null,
        private val detachFailure: Throwable? = null,
        val watched: MutableIntState? = null,
    ) : SwingModifier.ComponentNode<JPanel>(),
        ObserverModifierNode {
        var attachedJob: Job? = null
        var observedChanges = 0
        var resetCalls = 0
        var detachCalls = 0
        var peersAttachedOnReset = emptyList<Boolean>()
        var peersAttachedOnDetach = emptyList<Boolean>()

        override fun onAttach() {
            attachedJob = coroutineScope.coroutineContext[Job]
            watched?.let { state -> observeReads { state.intValue } }
        }

        override fun onObservedReadsChanged() {
            observedChanges++
        }

        override fun onReset() {
            resetCalls++
            peersAttachedOnReset = declaredNodesAttached()
            resetFailure?.let { throw it }
        }

        override fun onDetach() {
            detachCalls++
            peersAttachedOnDetach = declaredNodesAttached()
            detachFailure?.let { throw it }
        }

        private fun declaredNodesAttached(): List<Boolean> {
            val attached = mutableListOf<Boolean>()
            visitDeclaredNodes { attached += it.isAttached }
            return attached
        }
    }

    private class NodeOf<N : SwingModifier.ComponentNode<JPanel>>(
        private val node: N,
    ) : SwingModifier.NodeElement<JPanel, N>() {
        override val targetType: Class<JPanel> get() = JPanel::class.java
        override val additive: Boolean get() = true

        override fun create(): N = node

        override fun update(node: N) = Unit

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }
}

/** This failure and every one under it, through causes and suppressed. */
private fun Throwable.failures(): Set<Throwable> =
    setOf(this) + (listOfNotNull(cause) + suppressed).flatMap { it.failures() }
