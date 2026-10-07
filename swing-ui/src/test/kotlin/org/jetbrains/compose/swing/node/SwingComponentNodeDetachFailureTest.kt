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
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** What a [SwingComponentNodeListener] is told when it throws from `onDetached` as its node moves or is reused. */
class SwingComponentNodeDetachFailureTest {
    @Test
    fun aListenerThatThrowsOnDetachAsItsNodeMovesToAnotherCompositionIsToldItAttachedThere() = runComposeSwingTest {
        var inInner by mutableStateOf(true)
        val failure = object : IllegalStateException("The listener failed on detach") {}
        val events = ArrayList<Event>()
        var failsOnDetach = false
        val component =
            object : JPanel(), SwingComponentNodeListener {
                override fun onAttached(node: SwingComponentNode<*>) {
                    events += Event("attached", node)
                }

                override fun onDetached(node: SwingComponentNode<*>) {
                    events += Event("detached", node)
                    if (failsOnDetach) throw failure
                }
            }
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
                        SwingNode(
                            factory = { component },
                            update = { set(text) { name = it } },
                        )
                    }
                }
            SwingNode(factory = { host })
            SwingNode(factory = { destination }) { if (!inInner) content() }
        }
        awaitIdle()
        val handle = host.setContent(parent = context) { if (inInner) content() }
        awaitIdle()
        val node = events.single().node
        val state = mutableIntStateOf(0)
        val changes = ArrayList<SwingComponentNode<*>>()
        node.observeReads({ changes += it }) { state.intValue }
        failsOnDetach = true

        inInner = false
        val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
        assertSame(failure, thrown, "the listener's original failure reaches the caller")

        awaitIdle()

        assertEquals(
            listOf(Event("attached", node), Event("detached", node), Event("attached", node)),
            events,
            "the move completes and attaches the node to its destination",
        )
        assertSame(destination, component.parent, "the destination contains the moved component")
        handle.dispose()
        text = "after"
        awaitIdle()
        assertEquals("after", component.name, "the moved content still recomposes")
        node.observeReads({ changes += it }) { state.intValue }
        state.intValue = 1
        awaitIdle()
        assertEquals(listOf(node), changes, "the moved node observes through its destination")
        failsOnDetach = false
    }

    @Test
    fun aListenerThatThrowsOnDetachAsItsNodeIsReusedIsToldItAttachedAgain() = runComposeSwingTest {
        var key by mutableIntStateOf(0)
        val failure = object : IllegalStateException("The listener failed on detach") {}
        var failsOnDetach = false
        val events = ArrayList<Event>()
        val component =
            object : JPanel(), SwingComponentNodeListener {
                override fun onAttached(node: SwingComponentNode<*>) {
                    events += Event("attached", node)
                }

                override fun onDetached(node: SwingComponentNode<*>) {
                    events += Event("detached", node)
                    if (failsOnDetach) throw failure
                }
            }
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
        val node = events.single().node
        failsOnDetach = true

        key = 1
        val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }

        assertSame(failure, thrown, "the listener's original failure reaches the caller")
        awaitIdle()
        val watched = mutableIntStateOf(0)
        val changes = ArrayList<SwingComponentNode<*>>()
        node.observeReads({ changes += it }) { watched.intValue }
        watched.intValue = 1
        awaitIdle()
        assertEquals(listOf(node), changes, "the reused node still observes changes")
        assertEquals(
            listOf(Event("attached", node), Event("detached", node), Event("attached", node)),
            events,
            "the reused node is attached all the same, and the listener is told so",
        )

        failsOnDetach = false
        handle.dispose()

        assertEquals(
            listOf(
                Event("attached", node),
                Event("detached", node),
                Event("attached", node),
                Event("detached", node),
            ),
            events,
            "each attach pairs with one detach",
        )
    }
}
