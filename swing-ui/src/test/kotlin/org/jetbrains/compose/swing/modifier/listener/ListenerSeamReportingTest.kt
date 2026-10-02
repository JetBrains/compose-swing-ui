package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.event.HierarchyEvent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A callback handed to a listener modifier that throws is reported to the thread's uncaught-exception
 * handler where the library calls it, and what called it goes on: the rest of the notification, the rest of
 * the write that raised it, and the composition. Each case fails the gate that follows the throw, as the
 * harness does for any reported throw, and then checks the composition still applies the next pass.
 */
class ListenerSeamReportingTest {
    @Test
    fun aHierarchyCallbackInANestedCompositionThatThrowsWhenItsParentAddsAnAncestorIsReported() = runComposeSwingTest {
        var armed = false
        var attached by mutableStateOf(false)
        val nestedHost = JPanel()
        val nestedHierarchy = mutableListOf<Long>()
        lateinit var parentPanel: JPanel
        lateinit var context: CompositionContext
        setContent {
            context = rememberCompositionContext()
            SwingNode(factory = { JPanel().also { parentPanel = it } }) {
                if (attached) SwingNode(factory = { nestedHost })
            }
        }
        val nested =
            nestedHost.setContent(context) {
                Label(
                    "nested",
                    SwingModifier.hierarchyListener {
                        nestedHierarchy += it.changeFlags
                        if (armed) error("boom")
                    },
                )
            }
        awaitIdle()
        nestedHierarchy.clear()

        armed = true
        attached = true
        assertEquals("boom", assertFailsWith<IllegalStateException> { awaitIdle() }.message)
        assertSame(parentPanel, nestedHost.parent, "the parent pass added the ancestor")
        assertTrue(
            nestedHierarchy.any { it and HierarchyEvent.PARENT_CHANGED.toLong() != 0L },
            "the nested composition's callback was notified of the new ancestor",
        )

        armed = false
        attached = false
        awaitIdle()
        assertNull(nestedHost.parent, "the parent composition applies the pass after the throw")
        nested.dispose()
    }

    @Test
    fun aCallbackThatThrowsWhenTheWindowFirstShowsLeavesTheCompositionRecomposing() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var visible by mutableStateOf(false)
        var text by mutableStateOf("a")
        lateinit var label: JLabel
        setContent {
            Window(onCloseRequest = {}, visible = visible) {
                Label(
                    text,
                    SwingModifier.hierarchyListener {
                        label = it.component as JLabel
                        if (it.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && label.isShowing) {
                            error("boom")
                        }
                    },
                )
            }
        }

        visible = true
        assertEquals("boom", assertFailsWith<IllegalStateException> { awaitIdle() }.message)
        assertTrue(label.isShowing, "the window finished showing")

        text = "b"
        awaitIdle()
        assertEquals("b", label.text, "the composition applies the pass after the throw")
    }
}
