package org.jetbrains.compose.swing.test

import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JSlider
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pins what a test sees once a callback's throw has been reported to the event dispatch thread's
 * uncaught-exception handler: the next gate throws it as it was thrown, with later ones suppressed onto it,
 * and one no gate throws fails the test as it ends. The composition goes on after each.
 */
class UncaughtFailureTest {
    @Test
    fun waitUntilThrowsAFailureReportedByATrueCondition() = runComposeSwingTest {
        val failure = IllegalStateException("condition failed")
        val slider = mountSliderThrowing(failure)
        var fired = false

        val thrown =
            assertFailsWith<IllegalStateException> {
                waitUntil {
                    if (!fired) {
                        fired = true
                        slider.value = 75
                    }
                    true
                }
            }

        assertSame(failure, thrown)
        awaitIdle()
    }

    @Test
    fun aReportedThrowFailsTheNextGateAsItselfWhichThenLeavesTheCompositionGoingOn() = runComposeSwingTest {
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        val failures = ArrayDeque(listOf<Throwable>(first, second))
        val value = mutableStateOf(50)
        setContent {
            Slider(value = value.value, changeListener = ChangeListener { throw failures.removeFirst() })
        }

        value.value = 75
        assertSame(first, assertFailsWith<IllegalStateException> { awaitIdle() })
        assertEquals(emptyList(), first.suppressed.toList())

        // A gate that could never find the composition idle throws the report before it gives up.
        value.value = 25
        assertSame(second, assertFailsWith<IllegalArgumentException> { waitUntil(100.milliseconds) { false } })
        awaitIdle()
    }

    @Test
    fun laterThrowsAreSuppressedOntoTheFirst() = runComposeSwingTest {
        val value = mutableStateOf(50)
        setContent {
            Slider(value = value.value, changeListener = ChangeListener { throw IllegalStateException("first") })
            Slider(value = value.value, changeListener = ChangeListener { throw IllegalStateException("second") })
        }
        value.value = 75
        val failure = assertFailsWith<IllegalStateException> { awaitIdle() }

        assertEquals("first", failure.message)
        assertEquals("second", failure.suppressed.single().message)
    }

    @Test
    fun aThrowableReportedTwiceIsThrownOnceAndNotSuppressedOntoItself() = runComposeSwingTest {
        val failure = IllegalStateException("shared")
        val thread = Thread.currentThread()
        val handler = checkNotNull(thread.uncaughtExceptionHandler)

        handler.uncaughtException(thread, failure)
        handler.uncaughtException(thread, failure)

        assertSame(failure, assertFailsWith<IllegalStateException> { awaitIdle() })
        assertEquals(emptyList(), failure.suppressed.toList())
    }

    @Test
    fun aThrowNoGateThrowsFailsTheTestAtItsEnd() {
        val teardown =
            assertFailsWith<RuntimeException> {
                runComposeSwingTest {
                    setContent {}
                    val thread = Thread.currentThread()
                    thread.uncaughtExceptionHandler.uncaughtException(thread, IllegalStateException("boom"))
                }
            }

        // runTest rethrows a copy of the failure with the original as its cause.
        val reported = teardown.cause ?: teardown
        assertTrue(reported is IllegalStateException, "the failure should be the one reported, but was: $teardown")
        assertEquals("boom", reported.message)
    }

    /**
     * Mounts a slider whose change callback throws the given failures in turn, one per value change, and
     * returns it.
     */
    private suspend fun ComposeSwingTest.mountSliderThrowing(vararg failures: Throwable): JSlider {
        val pending = failures.toMutableList()
        val slider = JSlider()
        setContent {
            SwingNode(
                factory = { slider },
                modifier = SwingModifier.changeListener { throw pending.removeAt(0) },
            )
        }
        awaitIdle()
        return slider
    }
}
