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
import kotlinx.coroutines.job
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.core.SwingRecomposer
import org.jetbrains.compose.swing.core.awaitUntil
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.accessibility.displayedMnemonicIndex
import org.jetbrains.compose.swing.node.ObserverModifierNode
import org.jetbrains.compose.swing.node.RecordingLayoutNodeElement
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.TestMeasurementParentProtocol
import org.jetbrains.compose.swing.node.TestParentLayoutElement
import org.jetbrains.compose.swing.node.observeReads
import org.jetbrains.compose.swing.reportedUncaught
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Component
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.coroutines.EmptyCoroutineContext
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
        val refusal = IllegalStateException("restore refused")
        val cancellations = List(3) { IllegalStateException("cancel-$it") }
        val listener = IllegalStateException("property listener failed")
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
                "restore is refused" to
                    Teardown(
                        Trigger.Dispose(RefusingRestore(refusal, refusals = 1)),
                        nodes = List(2) { ProbeNode() },
                        primary = refusal,
                        detachCalls = 1,
                    ),
                "job cancellation fails" to
                    Teardown(
                        Trigger.Dispose(),
                        nodes = cancellations.map { ProbeNode(cancellationFailure = it) },
                        primary = null,
                        carried = cancellations.toSet(),
                        detachCalls = 3,
                    ),
                "inspector property listener throws" to
                    Teardown(
                        Trigger.Removal,
                        nodes = listOf(ProbeNode()),
                        primary = listener,
                        detachCalls = 1,
                        inspector = true,
                    ),
            )
        return cases.map { (case, teardown) -> DynamicTest.dynamicTest(case) { teardown.run() } }
    }

    private sealed interface Trigger {
        data object Removal : Trigger

        data object Reuse : Trigger

        class Dispose(
            val diagnostics: SwingCompositionDiagnostics? = null,
        ) : Trigger
    }

    /**
     * A chain of [nodes] on [panel] torn down by [trigger], one step of which fails. The caller catches [primary] as
     * the root cause of what it receives (which failure is first is not pinned when it is null), and finds exactly the
     * failures of [carried] among the causes and suppressed, however they are wrapped. [detachCalls] of the nodes run
     * `onDetach`, each with every node of the chain still attached; all of them end detached, with their jobs
     * cancelled. Nodes that observe a state (removal only) are notified of a change made while they are attached,
     * and of none made after the chain is gone.
     * With [inspector] the debug inspector info is on for the run, and a property listener of [panel] that throws
     * [primary] is told which node the panel stops holding.
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

        init {
            if (inspector) {
                panel.addPropertyChangeListener {
                    if (it.oldValue is SwingComponentNode<*> && it.newValue == null) throw checkNotNull(primary)
                }
            }
        }

        private val chain =
            nodes.fold<ProbeNode, SwingModifier>(SwingModifier) { modifier, node -> modifier.then(NodeOf(node)) }

        fun run() {
            SwingUtilities.invokeAndWait { isDebugInspectorInfoEnabled = inspector }
            try {
                if (trigger is Trigger.Dispose) dispose(trigger.diagnostics) else remove()
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

        /** Disposes the chain's composition and verifies what that throws, which no uncaught-exception handler sees. */
        private fun dispose(diagnostics: SwingCompositionDiagnostics?) = runSwingTest {
            val recomposer = SwingRecomposer.create(JPanel(), effectContext = diagnostics ?: EmptyCoroutineContext)
            val content =
                JPanel().setContent(parent = recomposer.compositionContext) {
                    SwingNode(factory = { panel }, modifier = chain)
                }
            try {
                val reported = reportedUncaught { verify(assertFailsWith<Throwable> { content.dispose() }) }
                assertEquals(emptyList(), reported)
            } finally {
                content.dispose()
                recomposer.dispose()
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

    @Test
    fun aFailureDetachingAReleasedSubtreesNodesStillWithdrawsItsLayoutNodesFromTheParent() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val layout = RecordingMeasurementLayout()
        val padding = TestParentLayoutElement("padding")
        val node = ProbeNode(cancellationFailure = IllegalStateException("cancel failed"))
        setContent {
            if (shown) {
                SwingNode(factory = { JPanel() }, content = {
                    SwingNode(factory = { JPanel(layout) }, content = {
                        SwingNode(
                            factory = { JPanel() },
                            modifier =
                                SwingModifier then padding then RecordingLayoutNodeElement("a", mutableListOf()) then
                                    NodeOf(node),
                        )
                    })
                })
            }
        }
        val declarations = layout.declarations.size

        shown = false
        assertFailsWith<RuntimeException> { awaitIdle() }

        assertEquals(1, node.detachCalls)
        assertFalse(node.isAttached)
        assertEquals(declarations + 1, layout.declarations.size, "the parent is declared to without the nodes")
        assertEquals(listOf<ParentLayoutElement>(padding), layout.declarations.last().elements)
    }

    @Test
    fun aNodeWhoseDetachIsRefusedAndWhoseCancellationFailsReportsTheRefusal() = runSwingTest {
        val refusal = IllegalStateException("restore refused")
        val cancellation = IllegalArgumentException("cancel failed")
        val recomposer = SwingRecomposer.create(JPanel(), effectContext = RefusingRestore(refusal))
        var present by mutableStateOf(true)
        val node = ProbeNode(cancellationFailure = cancellation)
        val content =
            JPanel().setContent(parent = recomposer.compositionContext) {
                SwingNode(
                    factory = { JPanel() },
                    modifier = if (present) SwingModifier.then(NodeOf(node)) else SwingModifier,
                )
            }
        try {
            val reported =
                reportedUncaught {
                    present = false
                    awaitUntil("the failed runtime has been disposed") { recomposer.isDisposed }
                }

            val failures = reported.single().failures()
            assertTrue(refusal in failures, "the refusal is reported")
            assertTrue(cancellation in failures, "the cancellation's failure rides on it")
            assertFalse(node.isAttached)
        } finally {
            content.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun aNodeRefusedOnAttachThrowsTheRefusalWithTheRollbacksRepeatOfItSuppressed() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    SwingNode(factory = { JPanel() }, modifier = SwingModifier.displayedMnemonicIndex(0))
                }
            }
        assertEquals(
            listOf(failure.message),
            failure.suppressed.map { it.message },
            "the rollback's repeat of the refusal is suppressed on it",
        )
    }

    @Test
    fun aThrowFromOnDetachDuringTheRollbackOfAFailedOnAttachIsSuppressedOnTheAttachFailure() = runComposeSwingTest {
        val attachFailure = IllegalStateException("attach failed")
        val detachFailure = UnsupportedOperationException("detach failed")
        val node = AttachRefusingNode(attachFailure, detachFailure)
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    SwingNode(
                        factory = { JPanel() },
                        modifier = SwingModifier.then(NodeOf(node)),
                    )
                }
            }
        assertSame(attachFailure, failure)
        assertEquals(listOf(detachFailure), failure.suppressed.toList())
        assertEquals(1, node.detachCalls)
        assertFalse(node.isAttached)
        assertTrue(checkNotNull(node.attachedJob).isCancelled, "the node is detached whatever onDetach threw")
    }

    private class RefusingRestore(
        private val refusal: Throwable,
        private var refusals: Int = Int.MAX_VALUE,
    ) : SwingCompositionDiagnostics {
        override fun declaring(
            component: Component,
            node: SwingModifier.ComponentNode<*>,
            element: SwingModifier.NodeElement<*, *>,
            write: () -> Unit,
        ) = write()

        override fun restoring(
            component: Component,
            node: SwingModifier.ComponentNode<*>,
            restore: () -> Unit,
        ) {
            if (refusals-- > 0) throw refusal
            restore()
        }
    }

    private class ProbeNode(
        private val resetFailure: Throwable? = null,
        private val detachFailure: Throwable? = null,
        private val cancellationFailure: Throwable? = null,
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
            cancellationFailure?.let { failure ->
                coroutineScope.coroutineContext.job.invokeOnCompletion { throw failure }
            }
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

    private class AttachRefusingNode(
        private val attachFailure: Throwable,
        private val detachFailure: Throwable,
    ) : SwingModifier.ComponentNode<JPanel>() {
        var attachedJob: Job? = null
        var detachCalls = 0

        override fun onAttach() {
            attachedJob = coroutineScope.coroutineContext[Job]
            throw attachFailure
        }

        override fun onDetach() {
            detachCalls++
            throw detachFailure
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
