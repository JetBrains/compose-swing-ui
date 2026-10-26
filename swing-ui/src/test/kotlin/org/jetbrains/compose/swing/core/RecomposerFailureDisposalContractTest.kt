// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.reportedUncaught
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What a failure costs a recomposer while hot reload is disabled: it is disposed, and the failure is
 * reported exactly once. A failure after the first composition reaches the thread's uncaught-exception
 * handler, except a cancellation, which ends the recomposer and is reported to nobody. A first
 * composition's failure reaches whoever composed, and the recomposer that drove it ends on a later turn,
 * with no change needed to reach it. A caller's recomposer then refuses content as shut down; a window
 * composes content set afterwards on a recomposer of its own.
 *
 * A subclass names the [RecomposerOwner] its cases run for. A recomposition or effect failure in a window
 * is covered by [WindowRecomposerFailureTest], so those cases run for a caller's recomposer alone.
 */
abstract class RecomposerFailureDisposalContractTest {
    /** The owner of the recomposer every case here runs for. */
    internal abstract val owner: RecomposerOwner

    /**
     * A way content fails after its first composition: [arm] makes [Reloadable] raise its failure, which
     * reaches the uncaught-exception handler unless [isReported] is `false`. The case runs for each of
     * [owners].
     */
    private class LaterFailure(
        val name: String,
        val isReported: Boolean = true,
        val owners: List<RecomposerOwner> = RecomposerOwner.entries,
        val failure: () -> Throwable,
        val arm: ReloadableCode.(Throwable) -> Unit,
    )

    private val callerOnly = listOf(RecomposerOwner.Caller)

    private val laterFailures =
        listOf(
            LaterFailure("recomposition", owners = callerOnly, failure = { IllegalStateException("boom") }) {
                compositionFailure = it
            },
            LaterFailure("effect", owners = callerOnly, failure = { IllegalStateException("boom") }) {
                effectFailure = it
            },
            LaterFailure("apply", failure = { IllegalStateException("boom") }) { applyFailure = it },
            LaterFailure("compose runtime error", failure = { composeRuntimeError("broken invariant") }) {
                compositionFailure = it
            },
            LaterFailure(
                "recomposition cancellation",
                isReported = false,
                failure = { CancellationException("boom") },
            ) {
                compositionFailure = it
            },
            LaterFailure("apply cancellation", isReported = false, failure = { CancellationException("boom") }) {
                applyFailure = it
            },
        )

    @TestFactory
    fun aFailureAfterTheFirstCompositionIsReportedOnceAndEndsTheRecomposer(): List<DynamicTest> =
        laterFailures.filter { owner in it.owners }.flatMap { kind ->
            perOwner(listOf(owner)) { owner ->
                val host = RecomposerHost(owner)
                val code = ReloadableCode()
                try {
                    val panel = host.setContent { Reloadable(code) }
                    awaitUntil("the content composes") { labelTexts(panel) == listOf("v1#v1") }
                    val recomposer = assertNotNull(host.recomposer)

                    val failure = kind.failure()
                    val reported =
                        reportedUncaught {
                            kind.arm(code, failure)
                            code.invalidate()
                            awaitUntil("the failure ends the recomposer") { recomposer.isDisposed }

                            code.text = "v2"
                            code.invalidate()
                            delay(QUIET_PERIOD)
                        }
                    if (kind.isReported) {
                        assertReportedOnce(failure, reported, "the ${kind.name} failure is reported once")
                    } else {
                        assertEquals(emptyList(), reported, "the ${kind.name} is reported to nobody")
                    }
                    assertTrue(labelTexts(panel).none { it.startsWith("v2") }, "an ended recomposer recomposes nothing")
                    if (owner == RecomposerOwner.Window) {
                        assertEquals(emptyList(), labelTexts(panel), "the window's content is torn down")
                    }
                } finally {
                    host.dispose()
                }
            }.map { DynamicTest.dynamicTest("${kind.name}, ${it.displayName}", it.executable) }
        }

    @TestFactory
    fun aFirstCompositionFailureReachesTheCallerOnceAndEndsTheRecomposer(): List<DynamicTest> =
        // A caller's recomposer failing in the event that creates it is SetContentMisuseTest's case.
        perOwnerAndTiming(
            listOf(owner),
            timings =
                if (owner == RecomposerOwner.Caller) {
                    listOf(FirstCompositionTiming.OnARunningRecomposer)
                } else {
                    FirstCompositionTiming.entries
                },
        ) { owner, timing ->
            val host = RecomposerHost(owner)
            try {
                if (timing == FirstCompositionTiming.OnARunningRecomposer) host.startRunning()
                val failure = IllegalStateException("boom")
                val reported =
                    reportedUncaught {
                        val thrown = assertFailsWith<IllegalStateException> { host.setContent { throw failure } }
                        assertSame(failure, thrown, "the failure reaches whoever composed")
                        val recomposer = assertNotNull(host.recomposer)
                        awaitUntil("the recomposer that drove the failed composition ends") { recomposer.isDisposed }
                        if (owner == RecomposerOwner.Window) {
                            assertNull(
                                host.recomposer,
                                "the window holds no recomposer once the first composition threw",
                            )
                        }

                        var caption by mutableStateOf("first")
                        val passes = mockk<() -> Unit>(relaxed = true)
                        if (owner == RecomposerOwner.Caller) {
                            assertFailsWith<IllegalArgumentException>("the recomposer that ended hosts no content") {
                                host.setContent {
                                    passes()
                                    Label(text = caption)
                                }
                            }
                            verify(exactly = 0) { passes() }
                        } else {
                            val later =
                                host.setContent {
                                    passes()
                                    Label(text = caption)
                                }
                            assertEquals(listOf("first"), labelTexts(later), "content set after the failure composes")
                            caption = "second"
                            awaitUntil("the content set after the failure recomposes") {
                                labelTexts(later) == listOf("second")
                            }
                            delay(QUIET_PERIOD)
                            assertEquals(listOf("second"), labelTexts(later), "the content stays shown")
                        }
                    }
                assertEquals(emptyList(), reported, "a first composition's failure is reported to its caller alone")
            } finally {
                host.dispose()
            }
        }
}
