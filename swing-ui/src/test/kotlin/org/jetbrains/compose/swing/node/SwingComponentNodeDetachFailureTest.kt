package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.ReusableComposeNode
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** What a [SwingComponentNodeListener] is told when it throws from `onDetached` as its node moves or is reused. */
class SwingComponentNodeDetachFailureTest {
    /** How a case moves the listener's node: [move] triggers it, and [end] then ends the composition the node is in. */
    private class Scenario(
        val move: () -> Unit,
        val end: suspend () -> Unit,
    )

    @TestFactory
    fun aListenerThatThrowsOnDetachIsReportedAndItsNodeIsToldItAttachedAgain(): List<DynamicTest> = listOf(
        detachFails("moves to another composition") { movesToAnotherComposition(it) },
        detachFails("moves into a new composition") { movesIntoANewComposition(it) },
        detachFails("is reused") { isReused(it) },
    )

    private suspend fun ComposeSwingTest.movesToAnotherComposition(component: ListeningPanel): Scenario {
        var inInner by mutableStateOf(true)
        var shown by mutableStateOf(true)
        var text by mutableStateOf("before")
        val host = JPanel()
        val destination = JPanel()
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember {
                    movableContentOf {
                        SwingNode(factory = { component }, update = { set(text) { name = it } })
                    }
                }
            SwingNode(factory = { host })
            SwingNode(factory = { destination }) { if (shown && !inInner) content() }
        }
        awaitIdle()
        val handle = host.setContent(parent = context) { if (inInner) content() }
        awaitIdle()
        return Scenario(move = { inInner = false }) {
            assertSame(destination, component.parent, "the destination contains the moved component")
            handle.dispose()
            text = "after"
            awaitIdle()
            assertEquals("after", component.name, "the moved content still recomposes")
            shown = false
            awaitIdle()
        }
    }

    private suspend fun ComposeSwingTest.movesIntoANewComposition(component: ListeningPanel): Scenario {
        var inInner by mutableStateOf(false)
        val host = JPanel()
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content = remember { movableContentOf { SwingNode(factory = { component }) } }
            SwingNode(factory = { host })
            if (!inInner) content()
        }
        awaitIdle()
        val handle = host.setContent(parent = context) { if (inInner) content() }
        awaitIdle()
        return Scenario(move = { inInner = true }, end = handle::dispose)
    }

    private suspend fun ComposeSwingTest.isReused(component: ListeningPanel): Scenario {
        var key by mutableIntStateOf(0)
        lateinit var context: CompositionContext
        setContent { context = rememberCompositionContext() }
        awaitIdle()
        val handle =
            JPanel().setContent(parent = context) {
                ReusableContent(key) {
                    ReusableComposeNode<SwingNodeHolder<JPanel>, SwingApplier>(
                        factory = { CreatedNodeHolder(component) },
                        update = {},
                    )
                }
            }
        awaitIdle()
        return Scenario(move = { key = 1 }, end = handle::dispose)
    }

    /**
     * A case named [case]: [setUp] attaches a [ListeningPanel] and returns how its node moves. The listener throws from
     * `onDetached` as it does, and the node must still attach where it went, observe reads there and detach with it.
     */
    private fun detachFails(
        case: String,
        setUp: suspend ComposeSwingTest.(ListeningPanel) -> Scenario,
    ): DynamicTest = DynamicTest.dynamicTest(case) {
        runComposeSwingTest {
            val component = ListeningPanel()
            val scenario = setUp(component)
            val node = component.events.single().node
            val watched = mutableIntStateOf(0)
            val changes = ArrayList<SwingComponentNode<*>>()
            node.observeReads({ changes += it }) { watched.intValue }
            component.failsOnDetach = true

            scenario.move()
            val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
            awaitIdle()

            assertSame(component.detachFailure, thrown, "the listener's own failure is reported")
            assertEquals(
                listOf(Event("attached", node), Event("detached", node), Event("attached", node)),
                component.events,
                "the move completes and the listener is told the node attached again",
            )
            node.observeReads({ changes += it }) { watched.intValue }
            watched.intValue = 1
            awaitIdle()
            assertEquals(listOf(node), changes, "the node observes in its new composition only")

            component.failsOnDetach = false
            scenario.end()

            assertEquals(
                listOf(
                    Event("attached", node),
                    Event("detached", node),
                    Event("attached", node),
                    Event("detached", node),
                ),
                component.events,
                "ending the composition the node is in detaches it, once",
            )
        }
    }
}
