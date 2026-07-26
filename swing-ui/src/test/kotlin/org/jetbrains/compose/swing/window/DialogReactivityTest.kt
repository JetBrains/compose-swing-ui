package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onWindow
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JDialog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioural tests asserting that [Dialog] arguments are reactive: mutating Compose state that feeds
 * a [Dialog] argument is reflected on the realized [JDialog] once the change is applied.
 *
 * A visibility change is applied on a fresh event-dispatch tick (a modal show blocks inside a nested
 * event loop, so it can never run inline in an effect); the harness idle gate drains that tick, so an
 * `awaitIdle` is enough for the realized dialog to reflect the declared visibility. The dialog is
 * composed modeless so showing it never blocks the driving thread. Skipped in headless environments
 * where no real peer can be realized.
 */
class DialogReactivityTest {
    @Test
    fun titleReactsToRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var title by mutableStateOf("dialog-title-test")
        setContent { Dialog(onCloseRequest = {}, title = title) {} }
        val dialog = onWindow().fetch<JDialog>()
        assertEquals("dialog-title-test", dialog.title, "the dialog must realize with its declared title")
        title = "dialog-title-test-updated"
        awaitIdle()
        assertEquals("dialog-title-test-updated", dialog.title, "the dialog title must follow the recomposed value")
    }

    @Test
    fun visibleReactsToRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var visible by mutableStateOf(false)
        setContent { Dialog(onCloseRequest = {}, title = "dialog-visible-test", visible = visible) {} }
        onWindow().assertIsNotVisible()
        visible = true
        awaitIdle()
        onWindow().assertIsVisible()
    }

    @Test
    fun resizableReactsToRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var resizable by mutableStateOf(true)
        setContent { Dialog(onCloseRequest = {}, title = "dialog-resizable-test", resizable = resizable) {} }
        val dialog = onWindow().fetch<JDialog>()
        assertTrue(dialog.isResizable, "the dialog must realize resizable while resizable is declared true")
        resizable = false
        awaitIdle()
        assertTrue(!dialog.isResizable, "the dialog must stop being resizable once resizable recomposes to false")
    }

    @Test
    fun visibilityFollowsAToggleAcrossRecompositions() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var visible by mutableStateOf(true)
        setContent { Dialog(onCloseRequest = {}, title = "dialog-visible-toggle-test", visible = visible) {} }
        onWindow().assertIsVisible()
        visible = false
        awaitIdle()
        onWindow().assertIsNotVisible()
        visible = true
        awaitIdle()
        onWindow().assertIsVisible()
    }
}
