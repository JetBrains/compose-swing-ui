package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.job
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.menuItemTexts
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Component
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** What a subtree leaves attached, and what it releases, once the compositions it moved between are disposed. */
class MovedSubtreeDisposalTest {
    @TestFactory
    fun aFailedMoveDetachesEveryNodeOnDisposal(): List<DynamicTest> = listOf(
        "listener onDetached" to false,
        "listener onDetached" to true,
        "listener onAttached" to false,
        "modifier cancellation" to false,
    ).map { (failureAt, underNode) ->
        DynamicTest.dynamicTest("failure at $failureAt, under node = $underNode") {
            val top = Lifecycle()
            val child = Lifecycle()
            runComposeSwingTest {
                if (failureAt == "modifier cancellation") top.cancellationFailure = "top"
                val move =
                    movingIntoANestedComposition(
                        under = { moved ->
                            if (underNode) {
                                SwingNode(factory = { JPanel() }) { moved() }
                            } else {
                                moved()
                            }
                        },
                    ) { ListeningNode(top) { ListeningNode(child) } }

                when (failureAt) {
                    "listener onDetached" -> top.detachFailure = "top"
                    "listener onAttached" -> top.attachFailure = "top"
                }
                move.run()
                assertFailsWith(
                    if (failureAt == "modifier cancellation") {
                        RuntimeException::class
                    } else {
                        IllegalStateException::class
                    },
                    "the failure at $failureAt is reported",
                ) { awaitIdle() }
                top.detachFailure = null
                top.attachFailure = null
                move.nested.dispose()

                assertDetachedOncePerAttach(top, child)
                if (failureAt != "modifier cancellation") assertReleasedOnce(top, child)
            }
        }
    }

    @Test
    fun aMoveWhoseElementUpdateFailsKeepsThatFailureAndDetachesEveryNodeItNeverAttached() = runComposeSwingTest {
        val failure = IllegalStateException("update failed")
        val cancellations = List(2) { IllegalArgumentException("cancel-$it failed") }
        val never = List(2) { Peer() }
        var armed = false
        val modifier =
            SwingModifier
                .then(
                    PeerElement(Peer()) {
                        if (armed) {
                            never.zip(cancellations).forEach { (peer, cancelled) -> peer.failWhenCancelled(cancelled) }
                            throw failure
                        }
                    },
                ).then(PeerElement(never[0]))
                .then(PeerElement(never[1]))
        val move = movingIntoANestedComposition { SwingNode(factory = { JPanel() }, modifier = modifier) }

        armed = true
        move.run()
        val thrown = assertFailsWith<IllegalStateException>("the failure is reported") { awaitIdle() }
        move.nested.dispose()

        // The harness's own check of the interrupted insertion is thrown first and carries the update failure.
        val updateFailures = thrown.reachable().filter { it.message == failure.message }
        assertTrue(updateFailures.isNotEmpty(), "the update failure is reported: $thrown")
        assertTrue(thrown.suppressed.none { it.cause in cancellations }, "a cleanup failure does not replace it")
        assertTrue(
            updateFailures.any { carrier ->
                cancellations.all { cancelled ->
                    carrier.suppressed.any { suppressed -> suppressed.reachable().any { it === cancelled } }
                }
            },
            "each cleanup failure is suppressed on the update failure: $thrown",
        )
        assertTrue(never.none { it.isAttached }, "a node whose attach never ran is not left attached")
    }

    @Test
    fun aNodeWhoseListenerModifierAndReleaseCallbackAllThrowRunsEveryStepOnce() = runComposeSwingTest {
        val lifecycle = Lifecycle()
        val host = JPanel()
        lateinit var context: CompositionContext
        setContent {
            context = rememberCompositionContext()
            SwingNode(factory = { host })
        }
        awaitIdle()
        val content = host.setContent(parent = context) { ListeningNode(lifecycle) }
        awaitIdle()

        lifecycle.detachFailure = "listener"
        lifecycle.modifierDetachFailure = "modifier"
        lifecycle.releaseFailure = "release"
        content.dispose()
        val thrown = assertFailsWith<IllegalStateException>("the first failure is reported") { awaitIdle() }
        lifecycle.detachFailure = null
        lifecycle.modifierDetachFailure = null
        lifecycle.releaseFailure = null

        assertEquals("listener", thrown.message, "the first failure is the one the gate throws")
        assertEquals(
            listOf("modifier", "release"),
            thrown.suppressed.map { it.message },
            "each later failure is suppressed on it, in the order the steps run",
        )
        assertEquals(listOf("attached", "detached"), lifecycle.listenerEvents, "the listener is told once")
        assertEquals(listOf("onAttach", "onDetach"), lifecycle.modifierEvents, "the modifier node detaches once")
        assertReleasedOnce(lifecycle)
    }

    @Test
    fun anItemMovedToAnotherMenuStandsOnlyUnderTheMenuItJoined() = runComposeSwingTest {
        var inSecond by mutableStateOf(false)
        val first = JMenu("First")
        val second = JMenu("Second")
        val popup =
            composeMenu {
                val item = remember { movableContentOf { MenuNode(factory = { JMenuItem("Moved") }) } }
                MenuNode(factory = { first }) { if (!inSecond) item() }
                MenuNode(factory = { second }) { if (inSecond) item() }
            }
        assertEquals(listOf("First", "Second"), popup.menuItemTexts(), "both menus are composed before the move")

        inSecond = true
        awaitIdle()

        assertEquals(listOf("Moved"), second.menuItemTexts(), "the item is in the second menu")
        assertEquals(emptyList(), first.menuItemTexts(), "the item left the first menu")
    }

    private fun Throwable.reachable(): List<Throwable> =
        listOf(this) + (suppressed.toList() + listOfNotNull(cause)).flatMap { it.reachable() }

    private fun assertDetachedOncePerAttach(vararg lifecycles: Lifecycle) {
        lifecycles.forEach { lifecycle ->
            val node = checkNotNull(lifecycle.node) { "the component was attached" }
            assertFailsWith<IllegalStateException>("a detached node cannot observe in a composition") {
                node.observeReads({}) {}
            }
            assertEquals(
                lifecycle.listenerEvents.count { it == "attached" },
                lifecycle.listenerEvents.count { it == "detached" },
                "the listener is told one detach for each attach: ${lifecycle.listenerEvents}",
            )
            assertEquals(
                lifecycle.modifierEvents.count { it == "onAttach" },
                lifecycle.modifierEvents.count { it == "onDetach" },
                "the modifier node detaches once for each attach: ${lifecycle.modifierEvents}",
            )
        }
    }

    private fun assertReleasedOnce(vararg lifecycles: Lifecycle) {
        lifecycles.forEach { lifecycle ->
            assertEquals(1, lifecycle.releases, "the node's own release callback runs once")
        }
    }

    /** Content composed in the test's own composition, which [run] moves into [nested]. */
    private class Move(
        private val moved: () -> Unit,
        val nested: DisposableHandle,
    ) {
        fun run() = moved()
    }

    /** Composes [content] in the test's own composition, to move into a nested one, composed [under] a node there. */
    private suspend fun ComposeSwingTest.movingIntoANestedComposition(
        under: @Composable (moved: @Composable () -> Unit) -> Unit = { it() },
        content: @Composable () -> Unit,
    ): Move {
        var inNested by mutableStateOf(false)
        val host = JPanel()
        lateinit var context: CompositionContext
        lateinit var movable: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            movable = remember { movableContentOf(content) }
            SwingNode(factory = { host })
            if (!inNested) movable()
        }
        awaitIdle()
        val nested = host.setContent(parent = context) { if (inNested) under(movable) }
        awaitIdle()
        return Move({ inNested = true }, nested)
    }

    @Composable
    private fun ListeningNode(
        lifecycle: Lifecycle,
        content: @Composable () -> Unit = {},
    ) {
        SwingNode(
            factory = { ListeningPanel(lifecycle) },
            modifier = SwingModifier then LifecycleElement(lifecycle),
            onRelease = {
                lifecycle.releases++
                lifecycle.releaseFailure?.let { error(it) }
            },
            content = content,
        )
    }

    /** What one component's listener and modifier node were told, and the failures they are to throw. */
    private class Lifecycle {
        val listenerEvents = ArrayList<String>()
        val modifierEvents = ArrayList<String>()
        var node: SwingComponentNode<*>? = null
        var releases = 0
        var detachFailure: String? = null
        var attachFailure: String? = null
        var modifierDetachFailure: String? = null
        var cancellationFailure: String? = null
        var releaseFailure: String? = null

        fun attached(node: SwingComponentNode<*>) {
            listenerEvents += "attached"
            if (this.node == null) this.node = node
            attachFailure?.let { error(it) }
        }

        fun detached() {
            listenerEvents += "detached"
            detachFailure?.let { error(it) }
        }
    }

    private class ListeningPanel(
        private val lifecycle: Lifecycle,
    ) : JPanel(),
        SwingComponentNodeListener {
        override fun onAttached(node: SwingComponentNode<*>) = lifecycle.attached(node)

        override fun onDetached(node: SwingComponentNode<*>) = lifecycle.detached()
    }

    private class LifecycleElement(
        private val lifecycle: Lifecycle,
    ) : SwingModifier.NodeElement<Component, LifecycleNode>() {
        override val targetType: Class<Component> get() = Component::class.java

        override fun create(): LifecycleNode = LifecycleNode(lifecycle)

        override fun update(node: LifecycleNode) = Unit

        override fun equals(other: Any?): Boolean = other is LifecycleElement && lifecycle === other.lifecycle

        override fun hashCode(): Int = System.identityHashCode(lifecycle)
    }

    private class LifecycleNode(
        private val lifecycle: Lifecycle,
    ) : SwingModifier.ComponentNode<Component>() {
        override fun onAttach() {
            lifecycle.modifierEvents += "onAttach"
            lifecycle.cancellationFailure?.let { failure ->
                coroutineScope.coroutineContext.job.invokeOnCompletion { error(failure) }
            }
        }

        override fun onDetach() {
            lifecycle.modifierEvents += "onDetach"
            lifecycle.modifierDetachFailure?.let { error(it) }
        }
    }

    private class Peer : SwingModifier.ComponentNode<Component>() {
        /** Makes the next cancellation of this node's scope throw [failure]. */
        fun failWhenCancelled(failure: Throwable) {
            coroutineScope.coroutineContext.job.invokeOnCompletion { throw failure }
        }
    }

    private class PeerElement(
        private val peer: Peer,
        private val onUpdate: () -> Unit = {},
    ) : SwingModifier.NodeElement<Component, Peer>() {
        override val targetType: Class<Component> get() = Component::class.java
        override val additive: Boolean get() = true

        override fun create(): Peer = peer

        override fun update(node: Peer) = onUpdate()

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }
}
