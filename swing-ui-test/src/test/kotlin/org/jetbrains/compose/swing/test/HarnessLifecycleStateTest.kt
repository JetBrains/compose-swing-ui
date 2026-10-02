package org.jetbrains.compose.swing.test

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.rememberCompositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.core.findLifecycleOwner
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Behavioral tests for the lifecycle a test states to its content.
 *
 * A test's root stands in no window, so nothing about where it hangs can answer whether its content is
 * shown. The test says so itself, which is what makes work gated on the lifecycle - a collector, a
 * poller, an animation - reachable from a test at all.
 */
class HarnessLifecycleStateTest {
    @Test
    fun contentComposedByATestReadsTheStateThatTestStates() = runComposeSwingTest {
        var owner: LifecycleOwner? = null
        setContent {
            owner = LocalLifecycleOwner.current
            Label("content")
        }
        awaitIdle()

        val read = checkNotNull(owner) { "the content must have read an owner" }
        assertEquals(
            Lifecycle.State.STARTED,
            read.lifecycle.currentState,
            "a test's content must open on the state a test opens in",
        )

        lifecycleState = Lifecycle.State.RESUMED
        assertEquals(
            Lifecycle.State.RESUMED,
            read.lifecycle.currentState,
            "the owner a test's content reads must be the one that test moves",
        )

        lifecycleState = Lifecycle.State.DESTROYED
        assertEquals(
            Lifecycle.State.DESTROYED,
            read.lifecycle.currentState,
            "a test must be able to end its content's lifecycle and see what the content does",
        )
    }

    @Test
    fun nestedContentReadsTheHarnessOwnerAndLeavesItLiveWhenDisposed() = runComposeSwingTest {
        var rootOwner: LifecycleOwner? = null
        lateinit var parent: CompositionContext
        setContent {
            rootOwner = LocalLifecycleOwner.current
            parent = rememberCompositionContext()
            Label("root")
        }
        onNodeWithText("root").assertExists()
        val owned = assertNotNull(rootOwner, "root content must read an owner")
        assertSame(owned, root.findLifecycleOwner(), "root content reads the harness-owned lifecycle")
        assertEquals(Lifecycle.State.STARTED, owned.lifecycle.currentState)

        // A detached host has no ancestor publication: its owner must come from the named parent.
        val nested = JPanel()
        var nestedOwner: LifecycleOwner? = null
        val handle =
            nested.setContent(parent = parent) {
                nestedOwner = LocalLifecycleOwner.current
                Label("nested")
            }
        try {
            awaitIdle()
            assertEquals("nested", (nested.components.single() as JLabel).text, "independent content was mounted")
            val borrowed = assertNotNull(nestedOwner, "nested content must read an owner")
            assertSame(owned, borrowed, "nested content inherits the harness-owned lifecycle")

            lifecycleState = Lifecycle.State.RESUMED
            assertEquals(
                Lifecycle.State.RESUMED,
                borrowed.lifecycle.currentState,
                "the shared inherited owner follows the harness state",
            )

            handle.dispose()
            assertEquals(
                Lifecycle.State.RESUMED,
                owned.lifecycle.currentState,
                "nested disposal leaves its borrowed owner live",
            )
            onNodeWithText("root").assertExists()
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun aWindowComposedByATestStatesAnOwnerOfItsOwn() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var rootOwner: LifecycleOwner? = null
        var windowOwner: LifecycleOwner? = null
        setContent {
            rootOwner = LocalLifecycleOwner.current
            Window(onCloseRequest = {}, title = "harness-lifecycle-window", visible = false) {
                windowOwner = LocalLifecycleOwner.current
            }
        }
        awaitIdle()

        assertNotSame(
            checkNotNull(rootOwner) { "the root content must have read an owner" },
            checkNotNull(windowOwner) { "the window content must have read an owner" },
            "a window is a top-level window of its own, so its content answers for that window rather " +
                "than for the state a test states to the content composed into its root",
        )
    }
}
