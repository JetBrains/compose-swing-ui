// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.DeclaredNodesListener
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A throw from a callback the caller supplied for a node's lifecycle is reported to the uncaught-exception handler,
 * and the step it belongs to completes as if the callback had returned.
 */
class NodeLifecycleCallbackFailureTest {
    @Test
    fun aListenerThatThrowsFromOnDeclaredNodesChangedIsReportedAndTheModifierCompletes() = runComposeSwingTest {
        val probe = LifecycleProbe()
        var shown by mutableStateOf(true)
        var text by mutableStateOf("a")

        val attached =
            assertFailsWith<IllegalStateException> {
                setContent {
                    if (shown) {
                        SwingNode(
                            factory = { ThrowingDeclaredNodesPanel() },
                            modifier = SwingModifier.then(ProbeElement(probe, additive = true)),
                        )
                    }
                    Label(text)
                }
            }
        val panel = onNodeOfType<ThrowingDeclaredNodesPanel>().fetch()

        assertEquals("declared nodes failed", attached.message)
        assertTrue(probe.node.isAttached, "the node is attached as if the listener had returned")
        assertCompositionKeepsUpdating("b") { text = it }

        shown = false
        val released = assertFailsWith<IllegalStateException> { awaitIdle() }

        assertEquals("declared nodes failed", released.message)
        assertEquals(listOf(1, 0), panel.handedCounts, "the listener is handed the node, then the empty list")
        assertEquals(listOf("attach", "detach"), probe.events, "the node attached and detached once")
        assertFalse(probe.node.isAttached)
        assertCompositionKeepsUpdating("c") { text = it }
    }

    @TestFactory
    fun aReleaseCallbackThatThrowsIsReportedAndTheNodeIsReleased(): List<DynamicTest> = listOf(
        released("on removal") { present, content -> if (present) content() },
        released("when parked for reuse") { present, content -> ReusableContentHost(active = present, content) },
    )

    /**
     * A case named [case]: [host] composes the content while `present` holds and takes it away otherwise. The content
     * is a node whose release callback throws, beside a node that shows the composition keeps updating.
     */
    private fun released(
        case: String,
        host: @Composable (present: Boolean, content: @Composable () -> Unit) -> Unit,
    ): DynamicTest = DynamicTest.dynamicTest(case) {
        runComposeSwingTest {
            val failure = IllegalStateException("release failed")
            var releases = 0
            var present by mutableStateOf(true)
            var text by mutableStateOf("a")
            setContent {
                host(present) {
                    SwingNode(
                        factory = { ListeningPanel() },
                        onRelease = {
                            releases++
                            throw failure
                        },
                    )
                }
                Label(text)
            }
            val panel = onNodeOfType<ListeningPanel>().fetch()
            val node = panel.events.single().node

            present = false
            val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }

            assertSame(failure, thrown)
            assertEquals(1, releases, "the callback ran once")
            assertFalse(node.isAttached, "the node is released as if the callback had returned")
            assertEquals(listOf(Event("attached", node), Event("detached", node)), panel.events)
            assertNull(panel.parent)
            assertCompositionKeepsUpdating("b") { text = it }
            assertEquals(1, releases, "the next pass did not release again")
        }
    }

    /** Writes [value] through [setText] and checks that the label of the composition applies it. */
    private suspend fun ComposeSwingTest.assertCompositionKeepsUpdating(
        value: String,
        setText: (String) -> Unit,
    ) {
        setText(value)
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(value, "the composition keeps updating") { text }
    }

    private class ThrowingDeclaredNodesPanel :
        JPanel(),
        DeclaredNodesListener {
        val handedCounts = ArrayList<Int>()

        override fun onDeclaredNodesChanged(
            componentNode: SwingComponentNode<*>,
            nodes: List<SwingModifier.Node>,
        ) {
            handedCounts += nodes.size
            error("declared nodes failed")
        }
    }

    private class LifecycleProbe {
        val events = ArrayList<String>()
        lateinit var node: ProbeNode
    }

    private class ProbeNode(
        private val probe: LifecycleProbe,
    ) : SwingModifier.ComponentNode<JPanel>() {
        override fun onAttach() {
            probe.events += "attach"
        }

        override fun onReset() {
            probe.events += "reset"
        }

        override fun onDetach() {
            probe.events += "detach"
        }
    }

    private class ProbeElement(
        private val probe: LifecycleProbe,
        override val additive: Boolean = false,
    ) : SwingModifier.NodeElement<JPanel, ProbeNode>() {
        override val targetType: Class<JPanel> get() = JPanel::class.java

        override val name: String get() = "lifecycleProbe"

        override fun create(): ProbeNode = ProbeNode(probe).also { probe.node = it }

        override fun update(node: ProbeNode) = Unit

        override fun equals(other: Any?): Boolean = other is ProbeElement && other.probe === probe

        override fun hashCode(): Int = System.identityHashCode(probe)
    }
}
