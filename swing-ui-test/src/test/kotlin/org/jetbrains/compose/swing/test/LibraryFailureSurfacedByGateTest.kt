package org.jetbrains.compose.swing.test

import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Pins the third case alongside [CallerFailureContainmentTest] and [CompositionFailureDiagnosticsTest]:
 * a throwable that reaches the event dispatch thread's own uncaught-exception handler and is not a
 * caller callback the library contained is the library's own failure, raised outside the recomposer's
 * coroutine entirely - a check the library defers to a later turn of the event queue is one real source
 * of one. The next gate that would otherwise settle over the resulting tree throws it instead of
 * returning normally.
 */
class LibraryFailureSurfacedByGateTest {
    @Test
    fun aFailureRaisedOnTheEdtOutsideTheRecomposerFailsTheNextGate() = runComposeSwingTest {
        setContent {}

        val posted = IllegalStateException("raised outside the recomposer")
        SwingUtilities.invokeLater { throw posted }

        val failure = assertFailsWith<IllegalStateException> { awaitIdle() }
        assertSame(posted, failure)

        // Thrown and forgotten, so the composition is free to settle normally afterward rather than
        // failing every further gate on the same failure.
        awaitIdle()
    }
}
