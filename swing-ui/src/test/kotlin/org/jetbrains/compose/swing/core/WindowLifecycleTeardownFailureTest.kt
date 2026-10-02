package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.LocalWindow
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.GraphicsEnvironment
import javax.swing.RootPaneContainer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import java.awt.Window as AwtWindow

/**
 * What happens to the [LifecycleOwner] a window's content reads when something fails as the window leaves the
 * composition: the owner still ends at [Lifecycle.State.DESTROYED], and the window releases its peer and its
 * listeners.
 */
class WindowLifecycleTeardownFailureTest {
    @TestFactory
    fun aWindowLeavingWhileATeardownStepFailsStillEndsItsOwnerAndReleasesItsPeer(): List<DynamicTest> = listOf(
        leaves("its content's effect disposal throws") { _, failure ->
            DisposableEffect(Unit) { onDispose { throw failure } }
        },
        leaves("a mounted root's listener removal throws") { window, failure ->
            DisposableEffect(window) {
                val mounted = FailingRemovalPanel(listenerRemoval = failure)
                (window as RootPaneContainer).layeredPane.add(mounted)
                mounted.setContent {}
                onDispose {}
            }
        },
    )

    private fun leaves(
        name: String,
        content: @Composable (window: AwtWindow, failure: Throwable) -> Unit,
    ) = DynamicTest.dynamicTest(name) {
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var declared by mutableStateOf(true)
            var owner: LifecycleOwner? = null
            var peer: AwtWindow? = null
            val thrown = object : IllegalStateException(name) {}
            setContent {
                if (declared) {
                    Window(onCloseRequest = {}, visible = false) {
                        owner = LocalLifecycleOwner.current
                        val window = checkNotNull(LocalWindow.current)
                        peer = window
                        content(window, thrown)
                    }
                }
            }
            awaitIdle()
            val window = assertNotNull(peer)
            assertTrue(window.isDisplayable)
            assertTrue(window.windowListeners.isNotEmpty())

            declared = false

            assertSame(thrown, assertFailsWith<IllegalStateException> { awaitIdle() })
            assertEquals(Lifecycle.State.DESTROYED, assertNotNull(owner).lifecycle.currentState)
            assertFalse(window.isDisplayable, "the window's peer is disposed whichever step failed")
            assertTrue(window.windowListeners.isEmpty(), "the window keeps none of the listeners its wiring installed")
        }
    }
}
