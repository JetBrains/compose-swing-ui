package org.jetbrains.compose.swing.test

import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.components.Slider
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun anUnrelatedThrowablePostedToTheEdtIsNotCollected() = runComposeSwingTest {
        setContent {}

        // The test body runs on the event dispatch thread, so this reaches the exact channel the
        // library reports a contained caller failure through - unwrapped, as unrelated work posted to
        // the thread arrives.
        val thread = Thread.currentThread()
        thread.uncaughtExceptionHandler.uncaughtException(thread, RuntimeException("unrelated"))

        assertEquals(emptyList(), takeCallerFailures())
    }
}
