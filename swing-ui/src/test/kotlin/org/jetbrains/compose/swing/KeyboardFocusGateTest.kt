package org.jetbrains.compose.swing

import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.GraphicsEnvironment
import java.awt.Window
import javax.swing.JFrame
import javax.swing.JWindow
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pins how the keyboard-focus gate answers the one question it exists to answer: a window the window
 * system never makes the focused one is a capability this host withholds, so the test that needed it is
 * skipped rather than failed, and the skip says which window went unfocused. What a focused window then
 * does with the keyboard is asserted, not assumed, by the tests that gate on this - so a component that
 * fails to take the keyboard in a focused window still fails.
 *
 * The windows used here declare themselves unfocusable, which the window system declines to focus on every
 * host, so what the gate does at its deadline is observable whether or not this host grants focus at all.
 */
@ExclusiveWindowSystem
class KeyboardFocusGateTest {
    @Test
    fun aWindowTheSystemNeverFocusesSkipsTheTestInsteadOfFailingIt() {
        val thrown = gateAbortFor { JFrame(FRAME_TITLE).unfocusable() }

        assertEquals(
            assumptionFailure,
            thrown::class,
            "the abort must be the failed assumption the engine reports as a skipped test, not the " +
                "assertion failure a defect would raise, but was: $thrown",
        )
    }

    @Test
    fun theSkipNamesTheFrameByItsTitle() {
        val thrown = gateAbortFor { JFrame(FRAME_TITLE).unfocusable() }

        assertTrue(
            thrown.message.orEmpty().contains("'$FRAME_TITLE'"),
            "the skip must name the window that never became focused, but was: ${thrown.message}",
        )
    }

    @Test
    fun theSkipNamesATitlelessWindowByItsComponentName() {
        val thrown = gateAbortFor { JWindow().apply { name = WINDOW_NAME }.unfocusable() }

        assertTrue(
            thrown.message.orEmpty().contains("'$WINDOW_NAME'"),
            "the skip must name the window that never became focused, but was: ${thrown.message}",
        )
    }

    /**
     * A frame built without a title carries the empty string rather than no title at all, which is what
     * the composable `Window` leaves it at by default, and an empty string names nothing - so a frame
     * reaches the component-name fallback just as a window that cannot hold a title does.
     */
    @Test
    fun theSkipNamesATitlelessFrameByItsComponentName() {
        val thrown = gateAbortFor { JFrame().apply { name = FRAME_NAME }.unfocusable() }

        assertTrue(
            thrown.message.orEmpty().contains("'$FRAME_NAME'"),
            "the skip must name the window that never became focused, but was: ${thrown.message}",
        )
    }

    /**
     * Shows [this] as a window no host will focus. Declining focus is the window's own state rather than
     * the host's, so the gate runs its deadline down on every machine.
     */
    private fun <T : Window> T.unfocusable(): T = apply {
        focusableWindowState = false
        setSize(WINDOW_SIDE, WINDOW_SIDE)
        isVisible = true
    }

    /** Runs the gate over a window that never becomes focused and returns what it threw. */
    private fun gateAbortFor(createWindow: () -> Window): Throwable {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val outcome =
            runCatching {
                runComposeSwingTest {
                    val unfocusable = createWindow()
                    try {
                        assumeWindowBecomesFocused(unfocusable, timeout = SHORT_DEADLINE)
                    } finally {
                        unfocusable.dispose()
                    }
                }
            }
        return assertNotNull(
            outcome.exceptionOrNull(),
            "a window that never becomes focused must abort the test",
        )
    }

    private companion object {
        /**
         * The throwable a failed JUnit assumption carries - the one the engine reports as a skipped test.
         * Taken from JUnit itself rather than named, so this pins the outcome the report shows and not a
         * type the assumption API happens to use today.
         */
        val assumptionFailure: KClass<out Throwable> =
            requireNotNull(
                runCatching { assumeTrue(false) }.exceptionOrNull(),
            ) { "a failed assumption must throw" }::class

        /** Distinct from every other word in the skip message, so matching it can only match the title. */
        const val FRAME_TITLE = "keyboard-focus-gate-test"

        // Set explicitly on the windows the gate has to name by their component name, because the name
        // AWT generates carries a counter no assertion can predict.
        const val WINDOW_NAME = "titleless-gate-window"
        const val FRAME_NAME = "titleless-gate-frame"

        const val WINDOW_SIDE = 64

        /**
         * Long enough to be a real deadline the gate polls against, short enough that pinning its
         * expiry costs the suite nothing.
         */
        val SHORT_DEADLINE = 200.milliseconds
    }
}
