// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.core.OnDemandComposition
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JToolBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins which compositions the finders reach: the harness root, and content composed under the test's context
 * on a host the test does not hold under that root - a free panel, a composed popup menu - through the ordinary
 * finders, with no container to name.
 */
class ComposedRootFinderTest {
    private fun ComposeSwingTest.captureContext(): CompositionContext {
        var captured: CompositionContext? = null
        setContent { captured = rememberCompositionContext() }
        return checkNotNull(captured) { "setContent did not run" }
    }

    @Test
    fun aFreeHostComposedUnderTheHarnessContextIsFoundByType() = runComposeSwingTest {
        val context = captureContext()
        val host = JToolBar()
        host.setContent(parent = context) { Label(text = "on a free host") }

        assertSame(host, onNodeOfType<JToolBar>().fetch(), "the free host should be found with no container named")
        onNodeWithText("on a free host").assertExists()
    }

    @Test
    fun aComposedPopupMenuIsFoundWithoutEverBeingShown() = runComposeSwingTest {
        val context = captureContext()
        val popup = JPopupMenu()
        popup.setContent(parent = context) { MenuItem(text = "Paste", onClick = {}) }

        onNodeWithText("Paste").assertExists()
    }

    @Test
    fun aShownComposedPopupMenuIsFoundByARootFinder() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var context: CompositionContext? = null
        setContent {
            context = rememberCompositionContext()
            Window(onCloseRequest = {}, title = "Owner") { Label(text = "owner content") }
        }
        val popup = JPopupMenu()
        popup.setContent(parent = checkNotNull(context)) { MenuItem(text = "Paste", onClick = {}) }
        val owner = onWindowWithTitle("Owner").fetch<JFrame>()
        owner.isVisible = true
        popup.show(owner.contentPane, 0, 0)

        try {
            assertTrue(popup.isShowing, "the popup should be showing")
            onNodeWithText("Paste").assertExists()
            onNodeWithText("owner content").assertDoesNotExist()
        } finally {
            popup.isVisible = false
        }
    }

    @Test
    fun onAllRootsListsTheHarnessRootAndAFreeHost() = runComposeSwingTest {
        val context = captureContext()
        val host = JPanel()
        host.setContent(parent = context) { Label(text = "free") }

        val roots = onAllRoots()
        roots.assertCountEquals(2)
        assertEquals(listOf<Any>(root, host), roots.fetchAll<JPanel>(), "the harness root should come first")
    }

    @Test
    fun onRootFailsNamingOnAllRootsWhenThereAreSeveralRoots() = runComposeSwingTest {
        val context = captureContext()
        JPanel().setContent(parent = context) { Label(text = "free") }

        val failure = assertFailsWith<AssertionError> { onRoot().assertExists() }
        val message = failure.message.orEmpty()
        assertTrue("2 roots" in message, "the failure should name the count: $message")
        assertTrue("onAllRoots()" in message, "the failure should name onAllRoots(): $message")
    }

    @Test
    fun onRootStillMeansTheHarnessRootWhenItIsTheOnlyOne() = runComposeSwingTest {
        setContent { Label(text = "only") }

        assertSame(root, onRoot().fetch<JPanel>())
    }

    @Test
    fun aDisposedRootsContentIsNoLongerFound() = runComposeSwingTest {
        val context = captureContext()
        val host = JPanel()
        val handle = host.setContent(parent = context) { Label(text = "short lived") }
        onNodeWithText("short lived").assertExists()

        handle.dispose()

        onNodeWithText("short lived").assertDoesNotExist()
        onAllRoots().assertCountEquals(1)
    }

    @Test
    fun aNestedCompositionInsideTheHarnessTreeIsNotCountedTwice() = runComposeSwingTest {
        val context = captureContext()
        val inner = JPanel()
        root.add(inner)
        inner.setContent(parent = context) { Label(text = "inner") }

        onAllRoots().assertCountEquals(1)
        onAllNodesOfType<JLabel>().assertCountEquals(1)
        onNodeWithText("inner").assertExists()
    }

    @Test
    fun contentNestedUnderAnotherFreeRootIsFoundOnce() = runComposeSwingTest {
        val context = captureContext()
        val outer = JPanel()
        outer.setContent(parent = context) { Label(text = "outer") }
        val inner = JPanel()
        outer.add(inner)
        inner.setContent(parent = context) { Label(text = "inner") }

        onAllRoots().assertCountEquals(2)
        onAllNodesOfType<JLabel>().assertCountEquals(2)
        onNodeWithText("inner").assertExists()
        onNodeWithText("outer").assertExists()
    }

    @Test
    fun registeredContentInsideAWindowIsNotSearchedByTheRootFinders() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "Host") { Label(text = "in the window") } }

        onAllRoots().assertCountEquals(1)
        onNodeWithText("in the window").assertDoesNotExist()
        onWindowWithTitle("Host").onNodeWithText("in the window").assertExists()
    }

    @Test
    fun onDemandCompositionContentIsNotARoot() = runComposeSwingTest {
        val context = captureContext()
        val composition = OnDemandComposition(parent = context) { Label(text = "on demand") }

        onAllRoots().assertCountEquals(1)
        onNodeWithText("on demand").assertDoesNotExist()
        composition.dispose()
    }
}
