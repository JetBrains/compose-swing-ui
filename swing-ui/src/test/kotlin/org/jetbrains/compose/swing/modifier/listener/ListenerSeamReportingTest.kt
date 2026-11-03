// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.NestedContentHost
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.interaction.onParent
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
import kotlin.test.assertNotSame
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
        val nestedHierarchy = mutableListOf<Long>()
        setContent {
            val host =
                remember {
                    movableContentOf {
                        NestedContentHost {
                            Label(
                                "nested",
                                SwingModifier.hierarchyListener {
                                    nestedHierarchy += it.changeFlags
                                    if (armed) error("boom")
                                },
                            )
                        }
                    }
                }
            Panel(SwingModifier.testTag("ancestor")) {
                if (attached) host()
            }
            if (!attached) host()
        }
        awaitIdle()
        val nestedHost = onNodeWithText("nested").onParent().fetch<JPanel>()
        val ancestor = onNodeWithTag("ancestor").fetch<JPanel>()
        nestedHierarchy.clear()

        armed = true
        attached = true
        assertFailsWith<IllegalStateException> { awaitIdle() }
        assertSame(ancestor, nestedHost.parent, "the parent pass added the ancestor")
        assertTrue(
            nestedHierarchy.any { it and HierarchyEvent.PARENT_CHANGED.toLong() != 0L },
            "the nested composition's callback was notified of the new ancestor",
        )

        armed = false
        attached = false
        awaitIdle()
        assertNotSame(ancestor, nestedHost.parent, "the parent composition applies the pass after the throw")
        assertEquals(0, ancestor.componentCount, "the ancestor no longer holds the host")
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
        assertFailsWith<IllegalStateException> { awaitIdle() }
        assertTrue(label.isShowing, "the window finished showing")

        text = "b"
        awaitIdle()
        assertEquals("b", label.text, "the composition applies the pass after the throw")
    }
}
