// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * The root has no window, so its placement cannot tell whether the content is shown.
 * The harness supplies the lifecycle state for collectors, pollers, and animations in that content.
 */
class HarnessLifecycleStateTest {
    @Test
    fun contentReadsTheHarnessLifecycleState() = runComposeSwingTest {
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
    fun nestedContentReadsTheHarnessOwnerAndLeavesItResumedWhenDisposed() = runComposeSwingTest {
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
                "nested disposal leaves its borrowed owner resumed",
            )
            onNodeWithText("root").assertExists()
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun aWindowComposedByATestHasItsOwnLifecycleOwner() = runComposeSwingTest {
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
            "window content reads the window's lifecycle owner rather than the harness root's owner",
        )
    }
}
