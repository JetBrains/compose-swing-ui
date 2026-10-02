package org.jetbrains.compose.swing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs [testBody] on the event dispatch thread, under a deadline.
 *
 * The library composes only on that thread, so a test driving a composition directly - rather than
 * through the harness, which stands there on the test's behalf - has to stand there itself. Blocking the
 * calling thread on the same work would run it under no deadline at all, so a test waiting for something
 * that never arrives holds the build until the whole run is killed, naming nothing.
 *
 * @param context added to the test coroutine, as [runTest] adds it
 * @param timeout the wall-clock deadline after which an unfinished [testBody] fails the test instead of
 * hanging it
 * @param testBody the test body, run on the event dispatch thread. It receives the scope it runs in
 * there, which is not the test scope [runTest] would hand it, as a [SwingTestScope].
 */
public fun runSwingTest(
    context: CoroutineContext = EmptyCoroutineContext,
    timeout: Duration = 60.seconds,
    testBody: suspend SwingTestScope.() -> Unit,
): TestResult = runTest(context, timeout) { withContext(Dispatchers.Swing) { SwingTestScopeImpl(this).testBody() } }

/** The scope a [runSwingTest] body runs in. It is the coroutine scope of the event dispatch thread. */
public sealed interface SwingTestScope : CoroutineScope

private class SwingTestScopeImpl(
    scope: CoroutineScope,
) : SwingTestScope,
    CoroutineScope by scope

/**
 * Runs [block] on the calling thread and returns every throwable that thread's uncaught-exception handler
 * received while it ran, in order. A failure the library reports instead of throws is observed here, and
 * fails nothing else.
 *
 * The thread's previous handler is restored when this returns, also when [block] throws.
 *
 * It is available inside a [runSwingTest] body, whose block runs on the event dispatch thread, the thread the
 * library reports from.
 *
 * @param block the code whose reported failures are captured
 * @return the throwables handed to the handler during [block], empty when there were none
 */
public inline fun SwingTestScope.reportedUncaught(block: () -> Unit): List<Throwable> {
    val thread = Thread.currentThread()
    val enclosing = thread.uncaughtExceptionHandler
    val reported = ArrayList<Throwable>()
    thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, failure -> reported += failure }
    try {
        block()
    } finally {
        thread.uncaughtExceptionHandler = enclosing
    }
    return reported
}
