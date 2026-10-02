package org.jetbrains.compose.swing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * [reportedUncaught] captures what is handed to the event dispatch thread's uncaught-exception handler,
 * which a [runSwingTest] body runs on. The reports here are handed to the handler directly.
 */
class SwingTestScopeTest {
    @Test
    fun returnsEveryReportedFailureInOrderAndRestoresTheHandler() = runSwingTest {
        val thread = Thread.currentThread()
        val enclosing = thread.uncaughtExceptionHandler
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")

        val reported =
            reportedUncaught {
                thread.uncaughtExceptionHandler.uncaughtException(thread, first)
                thread.uncaughtExceptionHandler.uncaughtException(thread, second)
            }

        assertEquals(listOf(first, second), reported)
        assertSame(enclosing, thread.uncaughtExceptionHandler)
    }

    @Test
    fun returnsNothingWhenNothingIsReported() = runSwingTest {
        assertEquals(emptyList(), reportedUncaught {})
    }

    @Test
    fun restoresThePreviousHandlerWhenTheBlockThrows() = runSwingTest {
        val thread = Thread.currentThread()
        val previous = Thread.UncaughtExceptionHandler { _, _ -> }
        val enclosing = thread.uncaughtExceptionHandler
        thread.uncaughtExceptionHandler = previous
        try {
            val failure = IllegalStateException("block failed")

            assertSame(failure, assertFailsWith<IllegalStateException> { reportedUncaught { throw failure } })
            assertSame(previous, thread.uncaughtExceptionHandler)
        } finally {
            thread.uncaughtExceptionHandler = enclosing
        }
    }
}
