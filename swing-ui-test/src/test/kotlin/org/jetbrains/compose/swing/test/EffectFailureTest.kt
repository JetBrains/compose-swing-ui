package org.jetbrains.compose.swing.test

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.test.interaction.performClick
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Pins what a test sees when an effect throws - a [LaunchedEffect] body, or a coroutine launched in the scope
 * [rememberCoroutineScope] returns. The throw is raised in a coroutine of its own rather than in a pass, and
 * nothing recomposes after it, as after the throw [CompositionFailureDiagnosticsTest] pins. The gate that
 * drains the event queue after it throws it, once, as androidx's test environment throws an uncaught
 * coroutine exception from its next wait for idle; a test that calls no gate afterwards fails with it as it
 * ends.
 */
class EffectFailureTest {
    @Test
    fun aThrowFromAnEffectFailsTheNextGateAndLeavesLaterGatesAndActionsWorking() = runComposeSwingTest {
        var fail by mutableStateOf(false)
        var text by mutableStateOf("before")
        var clicks = 0
        setContent {
            LaunchedEffect(fail) { if (fail) error(EFFECT_FAILURE) }
            Label(text = text)
            Button(text = "press", onClick = { clicks++ })
        }

        fail = true
        val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
        assertEquals(EFFECT_FAILURE, thrown.message)

        // Nothing recomposes after the throw, and the gates return over what the composition last applied.
        text = "after"
        awaitIdle()
        onNodeWithText("before").assertExists()
        awaitEventsDelivered()
        // An action ends in a gate of its own, and what it drives is the widget, which outlives the
        // recomposer.
        onNodeWithText("press").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun aThrowFromAnEffectOfTheFirstCompositionFailsSetContent() = runComposeSwingTest {
        val thrown =
            assertFailsWith<IllegalStateException> {
                setContent { LaunchedEffect(Unit) { error(EFFECT_FAILURE) } }
            }

        assertEquals(EFFECT_FAILURE, thrown.message)
        awaitIdle()
    }

    @Test
    fun aThrowFromACoroutineLaunchedInARememberedScopeFailsTheNextGate() = runComposeSwingTest {
        lateinit var scope: CoroutineScope
        setContent { scope = rememberCoroutineScope() }

        scope.launch { throw UnsupportedOperationException(EFFECT_FAILURE) }
        val thrown = assertFailsWith<UnsupportedOperationException> { awaitIdle() }
        assertEquals(EFFECT_FAILURE, thrown.message)

        awaitIdle()
    }

    @Test
    fun aThrowOffTheEventDispatchThreadFailsTheGateThatDrainsAfterIt() {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { offTheEventDispatchThread ->
            runComposeSwingTest {
                val release = CompletableDeferred<Unit>()
                setContent {
                    LaunchedEffect(Unit) {
                        launch(offTheEventDispatchThread) {
                            release.await()
                            error(EFFECT_FAILURE)
                        }
                    }
                }

                release.complete(Unit)
                // The condition never becomes true: the wait ends on the throw, whenever the other thread
                // raises it.
                val thrown = assertFailsWith<IllegalStateException> { waitUntil(timeout = 10.seconds) { false } }
                assertEquals(EFFECT_FAILURE, thrown.message)
            }
        }
    }

    @Test
    fun aTestThatDoesNotExpectTheThrowFailsWithIt() {
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent { LaunchedEffect(Unit) { error(EFFECT_FAILURE) } }
                    awaitIdle()
                }
            }

        // runTest rethrows a copy of the failure with the original as its cause so the stack trace it
        // reports spans the coroutine boundary.
        assertEquals(EFFECT_FAILURE, (failure.cause ?: failure).message)
    }

    @Test
    fun aThrowWithNoGateAfterItFailsTheTestAsItEndsOnce() {
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    lateinit var scope: CoroutineScope
                    setContent { scope = rememberCoroutineScope() }

                    // Started undispatched, the coroutine has thrown by the time launch returns.
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { error(EFFECT_FAILURE) }
                }
            }

        assertEquals(EFFECT_FAILURE, (failure.cause ?: failure).message)
        assertEquals(emptyList(), failure.suppressed.toList(), "the throw is reported once")
    }

    @Test
    fun aThrowFromAnEffectCancelledAsTheTestEndsFailsTheTest() {
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent {
                        LaunchedEffect(Unit) {
                            try {
                                awaitCancellation()
                            } finally {
                                error(EFFECT_FAILURE)
                            }
                        }
                    }
                }
            }

        assertEquals(EFFECT_FAILURE, (failure.cause ?: failure).message)
    }

    @Test
    fun aCancelledEffectFailsNothingAndLeavesTheCompositionRecomposing() = runComposeSwingTest {
        var text by mutableStateOf("before")
        setContent {
            LaunchedEffect(Unit) { throw CancellationException("the effect stops") }
            Label(text = text)
        }

        text = "after"
        awaitIdle()
        onNodeWithText("after").assertExists()
    }

    @Test
    fun aHandlerInTheEffectContextReceivesTheThrowInPlaceOfTheGate() {
        val handled = mutableListOf<Throwable>()
        runComposeSwingTest(effectContext = CoroutineExceptionHandler { _, failure -> handled += failure }) {
            setContent { LaunchedEffect(Unit) { error(EFFECT_FAILURE) } }
            awaitIdle()
        }

        assertEquals(listOf(EFFECT_FAILURE), handled.map { it.message })
    }

    @Test
    fun aGateThatGivesUpNamesAThrowAHandlerInTheEffectContextTook() {
        runComposeSwingTest(effectContext = CoroutineExceptionHandler { _, _ -> }) {
            var text by mutableStateOf("before")
            setContent {
                LaunchedEffect(Unit) { error(EFFECT_FAILURE) }
                Label(text = text)
            }
            awaitIdle()

            text = "after"
            val report =
                assertFailsWith<ComposeTimeoutException> {
                    waitUntil(timeout = 100.milliseconds) { onAllNodesWithText("after").fetchSize() == 1 }
                }

            assertContains(report.message.orEmpty(), EFFECT_FAILURE)
        }
    }

    @Test
    fun aGateThatGivesUpNamesAThrowAHandlerTookFromAnotherThread() {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { offTheEventDispatchThread ->
            val handledFailure = CompletableDeferred<Throwable>()
            runComposeSwingTest(
                effectContext =
                    CoroutineExceptionHandler { _, failure ->
                        handledFailure.complete(failure)
                    },
            ) {
                var text by mutableStateOf("before")
                setContent {
                    LaunchedEffect(Unit) { launch(offTheEventDispatchThread) { error(EFFECT_FAILURE) } }
                    Label(text = text)
                }
                awaitIdle()
                assertEquals(EFFECT_FAILURE, handledFailure.await().message)
                awaitIdle()

                text = "after"
                val report =
                    assertFailsWith<ComposeTimeoutException> {
                        waitUntil(timeout = 500.milliseconds) { onAllNodesWithText("after").fetchSize() == 1 }
                    }

                assertContains(report.message.orEmpty(), EFFECT_FAILURE)
            }
        }
    }

    @Test
    fun advanceTimeUntilThrowsAThrowNoHandlerTookNotATimeout() = runComposeSwingTest {
        lateinit var scope: CoroutineScope
        setContent { scope = rememberCoroutineScope() }

        // The throw arrives while the condition runs, after the wait's last frame, at its deadline.
        val thrown =
            assertFailsWith<IllegalStateException> {
                mainClock.advanceTimeUntil(timeout = Duration.ZERO) {
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { error(EFFECT_FAILURE) }
                    false
                }
            }

        assertEquals(EFFECT_FAILURE, thrown.message)
        awaitIdle()
    }

    @Test
    fun waitUntilThrowsAThrowNoHandlerTookNotATimeout() = runComposeSwingTest {
        lateinit var scope: CoroutineScope
        setContent { scope = rememberCoroutineScope() }

        val thrown =
            assertFailsWith<IllegalStateException> {
                waitUntil(timeout = Duration.ZERO) {
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { error(EFFECT_FAILURE) }
                    false
                }
            }

        assertEquals(EFFECT_FAILURE, thrown.message)
        awaitIdle()
    }

    @Test
    fun aGateThatGivesUpNamesAThrowAnEarlierGateThrew() = runComposeSwingTest {
        var fail by mutableStateOf(false)
        var text by mutableStateOf("before")
        setContent {
            LaunchedEffect(fail) { if (fail) error(EFFECT_FAILURE) }
            Label(text = text)
        }
        fail = true
        assertFailsWith<IllegalStateException> { awaitIdle() }

        text = "after"
        val report =
            assertFailsWith<ComposeTimeoutException> {
                waitUntil(timeout = 100.milliseconds) { onAllNodesWithText("after").fetchSize() == 1 }
            }

        assertContains(report.message.orEmpty(), EFFECT_FAILURE)
    }

    @Test
    fun whatAHandlerInTheEffectContextThrowsFailsTheNextGate() {
        val rejecting = CoroutineExceptionHandler { _, failure -> throw UnsupportedOperationException(failure) }
        runComposeSwingTest(effectContext = rejecting) {
            val thrown =
                assertFailsWith<UnsupportedOperationException> {
                    setContent { LaunchedEffect(Unit) { error(EFFECT_FAILURE) } }
                }

            assertEquals(EFFECT_FAILURE, thrown.cause?.message)
        }
    }
}

private const val EFFECT_FAILURE = "the effect fails"
