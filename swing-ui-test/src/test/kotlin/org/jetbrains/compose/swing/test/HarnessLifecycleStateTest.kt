package org.jetbrains.compose.swing.test

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun everyContentCompositionOfATestReadsOneOwner() = runComposeSwingTest {
        var first: LifecycleOwner? = null
        var second: LifecycleOwner? = null
        setContent {
            first = LocalLifecycleOwner.current
            Label("one")
            second = LocalLifecycleOwner.current
            Label("two")
        }
        awaitIdle()

        assertSame(first, second, "content of one test stands under one owner")
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
