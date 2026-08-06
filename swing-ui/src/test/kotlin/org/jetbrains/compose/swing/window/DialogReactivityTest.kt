package org.jetbrains.compose.swing.window

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.onWindow
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Dialog
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Toolkit
import java.awt.Window
import java.awt.image.BufferedImage
import javax.swing.JDialog
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioural tests asserting that a [Dialog] declaration is reactive: mutating Compose state that
 * feeds a [Dialog] argument - or the [LocalWindow] the dialog is composed under - is reflected on the
 * realized [JDialog] once the change is applied.
 *
 * A visibility change is applied on a fresh event-dispatch tick (a modal show blocks inside a nested
 * event loop, so it can never run inline in an effect); the harness idle gate drains that tick, so an
 * `awaitIdle` is enough for the realized dialog to reflect the declared visibility. A dialog declared
 * modal here is composed `visible = false` and never shown, so no show blocks the driving thread.
 * Skipped in headless environments where no real peer can be realized.
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
        title = "dialog-title-test"
        awaitIdle()
        assertEquals("dialog-title-test", dialog.title, "the dialog title must follow the recomposed value back")
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
        assertFalse(dialog.isResizable, "the dialog must stop being resizable once resizable recomposes to false")
        resizable = true
        awaitIdle()
        assertTrue(dialog.isResizable, "the dialog must become resizable again once resizable recomposes back to true")
    }

    @Test
    fun alwaysOnTopReactsToRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        assumeTrue(
            Toolkit.getDefaultToolkit().isAlwaysOnTopSupported,
            "keeping a window above the others requires toolkit support for always-on-top",
        )
        var alwaysOnTop by mutableStateOf(true)
        setContent {
            Dialog(
                onCloseRequest = {},
                title = "dialog-always-on-top-test",
                visible = false,
                alwaysOnTop = alwaysOnTop,
            ) {}
        }
        val dialog = onWindow().fetch<JDialog>()
        assertTrue(dialog.isAlwaysOnTop, "the dialog must realize always on top while alwaysOnTop is declared true")
        alwaysOnTop = false
        awaitIdle()
        assertFalse(
            dialog.isAlwaysOnTop,
            "the dialog must stop being always on top once alwaysOnTop recomposes to false",
        )
        alwaysOnTop = true
        awaitIdle()
        assertTrue(
            dialog.isAlwaysOnTop,
            "the dialog must go back above the others once alwaysOnTop recomposes back to true",
        )
    }

    @Test
    fun iconImageReactsToRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val icon: Image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val replacementIcon: Image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        var iconImage by mutableStateOf<Image?>(icon)
        setContent {
            Dialog(onCloseRequest = {}, title = "dialog-icon-image-test", visible = false, iconImage = iconImage) {}
        }
        val dialog = onWindow().fetch<JDialog>()
        assertEquals(icon, dialog.iconImages.single(), "the declared image must become the dialog's icon")
        iconImage = replacementIcon
        awaitIdle()
        assertEquals(
            replacementIcon,
            dialog.iconImages.single(),
            "the dialog's icon must follow the recomposed image",
        )
        iconImage = null
        awaitIdle()
        assertTrue(
            dialog.iconImages.isEmpty(),
            "a null iconImage must clear the dialog's icon, restoring the platform default",
        )
        iconImage = icon
        awaitIdle()
        assertEquals(
            icon,
            dialog.iconImages.single(),
            "an image declared over a cleared icon must become the dialog's icon",
        )
    }

    @Test
    fun minimumSizeRaisesASmallerDeclaredSize() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = DialogState(size = Dimension(200, 150))
        setContent {
            Dialog(
                onCloseRequest = {},
                state = state,
                title = "dialog-minimum-size-test",
                minimumSize = Dimension(320, 240),
            ) {}
        }
        assertEquals(
            Dimension(320, 240),
            onWindow().fetch<JDialog>().size,
            "a declared size below the declared minimum size must be raised to that minimum",
        )
    }

    @Test
    fun minimumSizeReactsToRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var minimumSize by mutableStateOf<Dimension?>(Dimension(320, 240))
        val state = DialogState(size = Dimension(200, 150))
        setContent {
            Dialog(
                onCloseRequest = {},
                state = state,
                title = "dialog-minimum-size-release-test",
                minimumSize = minimumSize,
            ) {}
        }
        val dialog = onWindow().fetch<JDialog>()
        assertEquals(Dimension(320, 240), dialog.minimumSize, "the declared minimum size must reach the dialog")
        minimumSize = Dimension(400, 300)
        awaitIdle()
        assertEquals(
            Dimension(400, 300),
            dialog.minimumSize,
            "the dialog's minimum size must follow the recomposed value",
        )
        minimumSize = null
        awaitIdle()
        assertFalse(
            dialog.isMinimumSizeSet,
            "a null minimumSize must release the floor, leaving the minimum size to the dialog's layout",
        )
        minimumSize = Dimension(320, 240)
        awaitIdle()
        assertEquals(
            Dimension(320, 240),
            dialog.minimumSize,
            "a minimum size declared over a released floor must reach the dialog",
        )
    }

    @Test
    fun modalityReactsToRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        assumeTrue(
            Toolkit.getDefaultToolkit().isModalityTypeSupported(Dialog.ModalityType.APPLICATION_MODAL),
            "blocking the other windows of the application requires toolkit support for that modality",
        )
        var modality by mutableStateOf(Dialog.ModalityType.APPLICATION_MODAL)
        setContent {
            Dialog(onCloseRequest = {}, title = "dialog-modality-test", modality = modality, visible = false) {}
        }
        val dialog = onWindow().fetch<JDialog>()
        assertEquals(
            Dialog.ModalityType.APPLICATION_MODAL,
            dialog.modalityType,
            "the dialog must realize with its declared modality",
        )
        modality = Dialog.ModalityType.MODELESS
        awaitIdle()
        // Modality is written onto the live dialog, so the very peer that was realized carries the
        // recomposed value; a replacement peer would leave this handle behind.
        assertEquals(
            Dialog.ModalityType.MODELESS,
            dialog.modalityType,
            "the realized dialog must take the modality the recomposition declared",
        )

        modality = Dialog.ModalityType.APPLICATION_MODAL
        awaitIdle()
        assertEquals(
            Dialog.ModalityType.APPLICATION_MODAL,
            dialog.modalityType,
            "the realized dialog must take a modality the recomposition declares over a modeless one",
        )
    }

    @Test
    fun ownerFollowsTheEnclosingWindow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // Realizing a dialog realizes its owner, so the two candidate owners are released again once the
        // assertions are done, keeping them out of the set of realized windows the other tests query.
        val first = JFrame("dialog-owner-first")
        val second = JFrame("dialog-owner-second")
        try {
            var owner by mutableStateOf<Window?>(first)
            setContent {
                CompositionLocalProvider(LocalProvidableWindow provides owner) {
                    Dialog(onCloseRequest = {}, title = "dialog-owner-test") {}
                }
            }
            val dialog = onWindowWithTitle("dialog-owner-test")
            val realized = dialog.fetch<JDialog>()
            assertEquals(first, realized.owner, "the dialog must be owned by the window it is composed under")

            owner = second
            awaitIdle()

            val reowned = dialog.fetch<JDialog>()
            assertEquals(
                second,
                reowned.owner,
                "the dialog must be owned by the window the recomposition put it under",
            )
            assertFalse(realized.isDisplayable, "the dialog the owner change replaced must be released")

            owner = null
            awaitIdle()

            assertNull(
                dialog.fetch<JDialog>().owner,
                "withdrawing the owning window must leave the dialog ownerless",
            )
            assertFalse(reowned.isDisplayable, "the dialog the withdrawn owner replaced must be released")
        } finally {
            first.dispose()
            second.dispose()
        }
    }

    @Test
    fun aDialogRebuiltForANewOwnerKeepsItsGeometryAndContent() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // Realizing a dialog realizes its owner, so the candidate owner is released again once the
        // assertions are done, keeping it out of the set of realized windows the other tests query.
        val candidateOwner = JFrame("dialog-reowned-geometry-owner")
        try {
            val state = DialogState(size = Dimension(360, 260))
            var owner by mutableStateOf<Window?>(null)
            setContent {
                CompositionLocalProvider(LocalProvidableWindow provides owner) {
                    Dialog(onCloseRequest = {}, state = state, title = "dialog-reowned-geometry-test") {
                        Label(text = "dialog-reowned-content")
                    }
                }
            }
            val dialog = onWindowWithTitle("dialog-reowned-geometry-test")
            val realized = dialog.fetch<JDialog>()

            owner = candidateOwner
            awaitIdle()

            val replacement = dialog.fetch<JDialog>()
            assertNotSame(realized, replacement, "an owner change must realize a replacement dialog")
            assertEquals(
                Dimension(360, 260),
                replacement.size,
                "the size held in the state must be applied to the dialog that replaces the released one",
            )
            dialog.onNodeWithText("dialog-reowned-content").assertExists()

            replacement.size = Dimension(520, 420)
            waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { state.size == Dimension(520, 420) }
            assertEquals(
                Dimension(520, 420),
                state.size,
                "a resize of the replacement dialog must be written back into the state",
            )
        } finally {
            candidateOwner.dispose()
        }
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

/**
 * Wall-clock deadline for conditions gated on native window-system notifications (resizes), which
 * arrive with real latency.
 */
private const val NATIVE_EVENT_TIMEOUT_MILLIS = 10_000L
