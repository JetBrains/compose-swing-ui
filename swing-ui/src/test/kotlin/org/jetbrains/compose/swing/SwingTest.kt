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
 * there, which is not the test scope [runTest] would hand it.
 */
internal fun runSwingTest(
    context: CoroutineContext = EmptyCoroutineContext,
    timeout: Duration = 60.seconds,
    testBody: suspend CoroutineScope.() -> Unit,
): TestResult = runTest(context, timeout) { withContext(Dispatchers.Swing, testBody) }
