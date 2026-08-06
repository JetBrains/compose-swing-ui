package org.jetbrains.compose.swing.test

import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.components.Slider
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CallerFailureContainmentTest {
    @Test
    fun aContainedCallerFailureIsCollectedWithItsOriginalMessage() = runComposeSwingTest {
        val value = mutableStateOf(50)
        setContent {
            // A JSlider notifies its change listener synchronously from inside the setValue call that
            // applies a declared value, so a listener that throws here throws from inside the wrapper's
            // own write to its widget.
            Slider(value = value.value, changeListener = ChangeListener { throw IllegalStateException("boom") })
        }
        value.value = 75
        awaitIdle()

        val failures = takeCallerFailures()
        assertEquals(1, failures.size)
        assertEquals("boom", failures.single().message)
    }

    @Test
    fun anUnrelatedThrowablePostedToTheEdtIsNotCollectedAsACallerFailure() = runComposeSwingTest {
        setContent {}

        // The test body runs on the event dispatch thread, so this reaches the exact channel the
        // library reports a contained caller failure through - unwrapped, as a library failure raised
        // outside the recomposer's own coroutine arrives (see LibraryFailureSurfacedByGateTest).
        val thread = Thread.currentThread()
        val posted = RuntimeException("unrelated")
        thread.uncaughtExceptionHandler.uncaughtException(thread, posted)

        assertEquals(emptyList(), takeCallerFailures())
        val failure = assertFailsWith<RuntimeException> { awaitIdle() }
        assertEquals(posted, failure)
    }

    @Test
    fun aContainedCallerFailureNeverTakenFailsTheTestAtTeardown() {
        // takeCallerFailures() is opt-in; a test that provokes a contained failure and never takes it
        // must not pass silently, so this leaves the failure sitting in the list all the way to the
        // block's return and asserts on the teardown failure that results.
        val teardown =
            assertFailsWith<AssertionError> {
                runComposeSwingTest {
                    val value = mutableStateOf(50)
                    setContent {
                        Slider(
                            value = value.value,
                            changeListener = ChangeListener { throw IllegalStateException("boom") },
                        )
                    }
                    value.value = 75
                    awaitIdle()
                }
            }

        val message = teardown.message.orEmpty()
        assertTrue(
            // The tail is what distinguishes the teardown failure from the note a settle gate adds to
            // its own report when it finds contained failures alongside a composition it cannot settle.
            message.contains("The composition contained them and carried on; the test cannot."),
            "the teardown failure should report the unclaimed callback(s): $message",
        )
        // runTest rethrows a copy of the failure with the original as its cause so the stack trace it
        // reports spans the coroutine boundary, and the callbacks are suppressed onto that original.
        val reported = teardown.cause ?: teardown
        assertEquals("boom", reported.suppressed.single().message)
    }
}
