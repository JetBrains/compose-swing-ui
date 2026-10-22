package org.jetbrains.compose.swing.test

import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Pins the library's own failure, beside the reported callback throw [UncaughtFailureTest] pins and the
 * throw that ends recomposition [CompositionFailureDiagnosticsTest] pins: a throwable that reaches the event
 * dispatch thread's own uncaught-exception handler from outside the recomposer's coroutine entirely - a
 * check the library defers to a later turn of the event queue is one real source of one. The next gate
 * throws it instead of returning normally, as it throws the one that ends recomposition.
 */
class LibraryFailureSurfacedByGateTest {
    @Test
    fun aFailureRaisedOnTheEdtOutsideTheRecomposerFailsTheNextGate() = runComposeSwingTest {
        setContent {}

        val posted = IllegalStateException("raised outside the recomposer")
        SwingUtilities.invokeLater { throw posted }

        val failure = assertFailsWith<IllegalStateException> { awaitIdle() }
        assertSame(posted, failure)

        // Thrown and forgotten, so the composition is free to become idle normally afterward rather than
        // failing every further gate on the same failure.
        awaitIdle()
    }

    @Test
    fun aFailureRaisedWhileTheGateLaysTheRootOutFailsThatGate() = runComposeSwingTest {
        setContent {}

        // A nonempty event queue keeps AWT from ending the gate's wait by shutting down the EDT.
        // Reposting starts in doLayout because the gate lays out the root only after the queue drains.
        var reposting = true
        lateinit var repost: Runnable
        repost =
            Runnable {
                if (reposting) {
                    SwingUtilities.invokeLater(repost)
                }
            }
        val thrown = IllegalStateException("raised while the root was laid out")
        val failing =
            object : JComponent() {
                override fun doLayout() {
                    SwingUtilities.invokeLater(repost)
                    throw thrown
                }
            }
        root.add(failing)

        val failure =
            try {
                assertFailsWith<IllegalStateException> { awaitIdle() }
            } finally {
                reposting = false
                root.remove(failing)
            }
        assertSame(thrown, failure, "the gate surfaces the layout failure itself")
        awaitIdle()
    }
}
