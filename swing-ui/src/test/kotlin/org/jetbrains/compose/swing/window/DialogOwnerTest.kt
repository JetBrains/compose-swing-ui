package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Window
import javax.swing.JDialog
import javax.swing.JFrame
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioural tests for the window a [Dialog] names as its owner: the named window owns the dialog
 * whichever window the dialog happens to be composed in, a dialog naming none takes the window it is
 * composed in, a recomposition naming another window re-owns the dialog, and the owner is the window a
 * [WindowPosition.CenteredOnOwner] position resolves against.
 *
 * A dialog takes its owner at construction, so a change of owner releases the dialog peer and builds a
 * replacement; the cases that change one assert on the peer the window query resolves afresh rather
 * than on the handle they started with.
 *
 * Realizing a dialog realizes the window that owns it, so every window the test names is released once
 * its assertions are done, keeping it out of the set of realized windows the other cases query.
 *
 * A dialog and its owner are realized peers, so these are skipped in headless environments.
 */
class DialogOwnerTest {
    @Test
    fun aNamedOwnerOwnsTheDialog() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val named = JFrame("dialog-named-owner")
        try {
            setContent {
                Dialog(onCloseRequest = {}, owner = named, title = "dialog-owned-by-name", visible = false) {}
            }

            assertSame(
                named,
                onWindowWithTitle("dialog-owned-by-name").fetch<JDialog>().owner,
                "a dialog must be owned by the window it names",
            )
        } finally {
            named.dispose()
        }
    }

    @Test
    fun aNamedOwnerStandsOverTheWindowTheDialogIsComposedIn() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val named = JFrame("dialog-owner-elsewhere")
        try {
            setContent {
                Window(onCloseRequest = {}, title = "dialog-owner-host", visible = false) {
                    Dialog(onCloseRequest = {}, owner = named, title = "dialog-owned-elsewhere", visible = false) {}
                    Dialog(onCloseRequest = {}, title = "dialog-owned-by-host", visible = false) {}
                }
            }

            val host = onWindowWithTitle("dialog-owner-host").fetch<JFrame>()
            assertSame(
                named,
                onWindowWithTitle("dialog-owned-elsewhere").fetch<JDialog>().owner,
                "a dialog naming its owner must be left alone by the window it is composed in",
            )
            assertSame(
                host,
                onWindowWithTitle("dialog-owned-by-host").fetch<JDialog>().owner,
                "a dialog naming no owner must be owned by the window it is composed in",
            )
        } finally {
            named.dispose()
        }
    }

    @Test
    fun theNamedOwnerFollowsTheDeclarationAcrossRecomposition() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = JFrame("dialog-named-owner-first")
        val second = JFrame("dialog-named-owner-second")
        try {
            var owner by mutableStateOf<Window?>(first)
            // The dialog is shown so that every peer an owner change builds is realized and can be queried
            // for: a dialog realizes a peer when it is shown or sized to its content, and a replacement
            // takes the concrete size the state holds by then rather than sizing itself, so a hidden dialog
            // rebuilt for another owner would stand peerless.
            setContent {
                Dialog(onCloseRequest = {}, owner = owner, title = "dialog-reowned-by-name") {}
            }

            val dialog = onWindowWithTitle("dialog-reowned-by-name")
            val realized = dialog.fetch<JDialog>()
            assertSame(first, realized.owner, "the dialog must be owned by the window the declaration names")

            owner = second
            awaitIdle()

            val reowned = dialog.fetch<JDialog>()
            assertSame(second, reowned.owner, "the dialog must be owned by the window the recomposition names")
            assertFalse(realized.isDisplayable, "the dialog the owner change replaced must be released")

            owner = null
            awaitIdle()

            assertNull(
                dialog.fetch<JDialog>().owner,
                "a dialog naming no owner, composed under no window, must be ownerless",
            )
            assertFalse(reowned.isDisplayable, "the dialog the withdrawn owner replaced must be released")
        } finally {
            first.dispose()
            second.dispose()
        }
    }

    @Test
    fun centeringOnTheOwnerResolvesAgainstTheNamedWindow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        // The named owner sits away from the screen's center on purpose: a dialog centered on the screen
        // instead of on the window it names lands elsewhere and fails the assertion.
        val named =
            JFrame("dialog-named-centering-owner").apply {
                setBounds(screen.x + 40, screen.y + 40, 480, 360)
                isVisible = true
            }
        try {
            // A window is centered on another's bounds only while that other is on screen; before that it
            // contributes nothing but its screen.
            waitUntil(timeoutMillis = NATIVE_EVENT_TIMEOUT_MILLIS) { named.isShowing }
            val state = DialogState(position = WindowPosition.CenteredOnOwner, size = Dimension(240, 160))
            setContent {
                Dialog(
                    onCloseRequest = {},
                    state = state,
                    owner = named,
                    title = "dialog-centered-on-named-owner",
                ) {}
            }

            val dialog = onWindowWithTitle("dialog-centered-on-named-owner").fetch<JDialog>()
            val ownerLocation = named.locationOnScreen
            assertNearOwner(
                Point(
                    ownerLocation.x + (named.width - dialog.width) / 2,
                    ownerLocation.y + (named.height - dialog.height) / 2,
                ),
                dialog.location,
                "a CenteredOnOwner position must center the dialog on the window the dialog names as its owner",
            )
        } finally {
            named.dispose()
        }
    }

    @Test
    fun aDialogLeavingTheCompositionLeavesTheWindowItNamedAsItsOwner() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val named = JFrame("dialog-owner-outliving")
        try {
            var composed by mutableStateOf(true)
            setContent {
                if (composed) {
                    Dialog(onCloseRequest = {}, owner = named, title = "dialog-transient", visible = false) {}
                }
            }

            val realized = onWindowWithTitle("dialog-transient").fetch<JDialog>()
            assertSame(named, realized.owner, "the dialog must be owned by the window it names")

            composed = false
            awaitIdle()

            onWindowWithTitle("dialog-transient").assertDoesNotExist()
            assertFalse(realized.isDisplayable, "a dialog leaving the composition must be released")
            assertTrue(
                named.isDisplayable,
                "a window a caller owns and named as the owner must outlive the dialog that named it",
            )
        } finally {
            named.dispose()
        }
    }
}

/**
 * Asserts that [actual] is [expected] up to [OWNER_POSITION_TOLERANCE_PIXELS], absorbing the pixel or
 * two a window manager may shave off a placement it honors.
 */
private fun assertNearOwner(
    expected: Point,
    actual: Point,
    message: String,
) {
    assertTrue(
        abs(actual.x - expected.x) <= OWNER_POSITION_TOLERANCE_PIXELS &&
            abs(actual.y - expected.y) <= OWNER_POSITION_TOLERANCE_PIXELS,
        "$message (expected around $expected, was $actual)",
    )
}

/**
 * Wall-clock deadline for conditions gated on native window-system notifications, which arrive with
 * real latency - including window-manager animations.
 */
private const val NATIVE_EVENT_TIMEOUT_MILLIS = 10_000L

/** Slack allowed on a realized placement, in pixels. */
private const val OWNER_POSITION_TOLERANCE_PIXELS = 4
