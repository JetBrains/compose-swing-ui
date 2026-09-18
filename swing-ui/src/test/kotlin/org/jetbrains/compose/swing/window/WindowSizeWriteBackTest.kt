package org.jetbrains.compose.swing.window

import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Insets
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests asserting which reports of a window's size reach the driving state while the insets
 * its decorations take change - see [AppliedGeometry.sizeRequestInsets].
 *
 * A window manager changes a window's insets on a schedule of its own. The window here is never shown
 * and reports the insets the test gives it, so every change of insets and every report is the test's
 * own, under any window manager or none.
 *
 * Skipped in headless environments, where a window cannot be constructed at all.
 */
class WindowSizeWriteBackTest {
    @Test
    fun aDeclaredSizeStandsAgainstAReportOffByTheChangeInInsets() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = DECLARED_SIZE)
        val window = WindowWithInsets(state, GUESSED_INSETS)
        try {
            val sizes =
                valuesTakenDuring({ state.size }) {
                    window.applyState()
                    awaitIdle()
                    window.frame.decorationInsets = FRAMED_INSETS
                    // The toolkit may report the window off by the change more than once.
                    window.frame.resizeTo(DECLARED_SIZE.offByTheChangeInInsets())
                    awaitIdle()
                    window.frame.resizeTo(DECLARED_SIZE.offByTheChangeInInsets())
                    awaitIdle()
                }
            assertEquals(listOf(DECLARED_SIZE), sizes, "the state must hold the declared size throughout")
            assertEquals(DECLARED_SIZE, window.frame.size, "the window must be asked for the declared size again")
        } finally {
            window.dispose()
        }
    }

    @Test
    fun aSizeDeclaredOnceTheInsetsChangedStandsAgainstALateReportOfTheSizeItReplaced() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = DECLARED_SIZE)
        val window = WindowWithInsets(state, GUESSED_INSETS)
        try {
            val sizes =
                valuesTakenDuring({ state.size }) {
                    window.applyState()
                    window.frame.decorationInsets = FRAMED_INSETS
                    state.size = REDECLARED_SIZE
                    window.applyState()
                    window.frame.resizeTo(DECLARED_SIZE.offByTheChangeInInsets())
                    awaitIdle()
                    // The toolkit reports the replaced size at itself once it has resized the window back.
                    window.frame.resizeTo(DECLARED_SIZE)
                    awaitIdle()
                }
            assertEquals(listOf(DECLARED_SIZE, REDECLARED_SIZE), sizes, "the state must hold only the sizes declared")
            assertEquals(REDECLARED_SIZE, window.frame.size, "the window must be asked for the size declared last")

            window.frame.resizeTo(DECLARED_SIZE)
            awaitIdle()
            assertEquals(
                DECLARED_SIZE,
                state.size,
                "a resize to the replaced size after the toolkit reported it at itself must reach the state",
            )
        } finally {
            window.dispose()
        }
    }

    @Test
    fun aSizeDeclaredBeforeTheInsetsChangedStandsAgainstLateReportsOfItAndOfTheSizeItReplaced() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = DECLARED_SIZE)
        val window = WindowWithInsets(state, GUESSED_INSETS)
        try {
            val sizes =
                valuesTakenDuring({ state.size }) {
                    window.applyState()
                    state.size = REDECLARED_SIZE
                    window.applyState()
                    awaitIdle()
                    window.frame.decorationInsets = FRAMED_INSETS
                    window.frame.resizeTo(DECLARED_SIZE.offByTheChangeInInsets())
                    awaitIdle()
                    window.frame.resizeTo(REDECLARED_SIZE.offByTheChangeInInsets())
                    awaitIdle()
                }
            assertEquals(listOf(DECLARED_SIZE, REDECLARED_SIZE), sizes, "the state must hold only the sizes declared")
            assertEquals(REDECLARED_SIZE, window.frame.size, "the window must be asked for the size declared last")
        } finally {
            window.dispose()
        }
    }

    @Test
    fun aSizeAskedForBeforeTheWindowHadAPeerIsMatchedAroundTheInsetsThePeerStartsWith() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = DECLARED_SIZE)
        val window = WindowWithInsets(state, Insets(0, 0, 0, 0))
        try {
            window.applyState()
            awaitIdle()
            window.frame.decorationInsets = GUESSED_INSETS
            window.frame.addNotify()
            window.frame.decorationInsets = FRAMED_INSETS
            window.frame.resizeTo(DECLARED_SIZE.offByTheChangeInInsets())
            awaitIdle()
            assertEquals(DECLARED_SIZE, state.size, "the state must keep a size declared before the window had a peer")
        } finally {
            window.dispose()
        }
    }

    @Test
    fun aResizeToASizeReplacedBeforeTheWindowHadAPeerReachesTheState() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = DECLARED_SIZE)
        val window = WindowWithInsets(state, Insets(0, 0, 0, 0))
        try {
            window.applyState()
            state.size = REDECLARED_SIZE
            window.applyState()
            awaitIdle()
            window.frame.decorationInsets = GUESSED_INSETS
            window.frame.addNotify()
            window.frame.resizeTo(DECLARED_SIZE)
            awaitIdle()
            assertEquals(
                DECLARED_SIZE,
                state.size,
                "a resize to a size replaced before the window had a peer must reach the state",
            )
        } finally {
            window.dispose()
        }
    }

    @Test
    fun aResizeToASizeTheToolkitReportedLateReachesTheStateOnceAnotherIsDeclared() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = DECLARED_SIZE)
        val window = WindowWithInsets(state, GUESSED_INSETS)
        try {
            window.applyState()
            awaitIdle()
            window.frame.decorationInsets = FRAMED_INSETS
            window.frame.resizeTo(DECLARED_SIZE.offByTheChangeInInsets())
            awaitIdle()
            state.size = REDECLARED_SIZE
            window.applyState()
            awaitIdle()
            window.frame.resizeTo(DECLARED_SIZE)
            awaitIdle()
            assertEquals(
                DECLARED_SIZE,
                state.size,
                "a resize to a size declared earlier and already reported late must reach the state",
            )
        } finally {
            window.dispose()
        }
    }

    @Test
    fun aResizeBackToASizeDeclaredEarlierReachesTheState() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = DECLARED_SIZE)
        val window = WindowWithInsets(state, FRAMED_INSETS)
        try {
            window.applyState()
            state.size = REDECLARED_SIZE
            window.applyState()
            awaitIdle()
            window.frame.resizeTo(DECLARED_SIZE)
            awaitIdle()
            assertEquals(DECLARED_SIZE, state.size, "a resize to a size declared earlier must reach the state")
            assertEquals(DECLARED_SIZE, window.frame.size, "a resize to a size declared earlier must stand")
        } finally {
            window.dispose()
        }
    }
}

/** A frame that is never shown, reporting the insets the test gives it and kept in sync with [state]. */
private class WindowWithInsets(
    private val state: WindowState,
    insets: Insets,
) {
    val frame = FrameWithInsets().also { it.decorationInsets = insets }
    private val applied = AppliedGeometry()
    private val removeWriteBack =
        frame.installGeometryWriteBack(
            applied,
            setPosition = { state.position = it },
            setSize = { width, height -> state.size = Dimension(width, height) },
        )

    /** Pushes what [state] declares onto [frame], as the composition does once the declaration changes. */
    fun applyState() {
        frame.applyGeometry(state.position, state.width, state.height, applied)
    }

    fun dispose() {
        removeWriteBack()
        frame.dispose()
    }
}

private class FrameWithInsets : JFrame() {
    /** The insets this frame reports; null while the superclass constructs, which reads them first. */
    var decorationInsets: Insets? = null

    override fun getInsets(): Insets = decorationInsets ?: super.getInsets()
}

/** This size as the toolkit reports it once the insets change from [GUESSED_INSETS] to [FRAMED_INSETS]. */
private fun Dimension.offByTheChangeInInsets() = Dimension(
    width + FRAMED_INSETS.left + FRAMED_INSETS.right - GUESSED_INSETS.left - GUESSED_INSETS.right,
    height + FRAMED_INSETS.top + FRAMED_INSETS.bottom - GUESSED_INSETS.top - GUESSED_INSETS.bottom,
)

private val DECLARED_SIZE = Dimension(320, 240)

/** A size the composition declares once the window stands at [DECLARED_SIZE]. */
private val REDECLARED_SIZE = Dimension(500, 240)

/** The insets an X11 toolkit guesses a decorated window's decorations take before a window manager says. */
private val GUESSED_INSETS = Insets(25, 5, 5, 5)

/** The insets a window manager reports once it frames the window. */
private val FRAMED_INSETS = Insets(20, 1, 5, 1)
