// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.delay
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.SwingTestScope
import org.jetbrains.compose.swing.currentHotReloadErrors
import org.jetbrains.compose.swing.invalidateGroupsWithKey
import org.jetbrains.compose.swing.reportedUncaught
import org.jetbrains.compose.swing.saveAndLoadStateForHotReload
import org.jetbrains.compose.swing.withHotReload
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a recomposer does with a failure while hot reload is enabled. A composition error the runtime can
 * recover from leaves the recomposer registered with the runtime, and the next invalidation of the
 * redefined group recovers it. Any other apply-phase failure leaves it registered too, and only a
 * save-and-load reload recovers it. A `ComposeRuntimeError` ends the recomposer as it does without hot
 * reload.
 *
 * Every case runs for a recomposer a window owns and for one a caller obtained through
 * [SwingRecomposer.create].
 */
@ExclusiveWindowSystem
class HotReloadRecoveryTest {
    @TestFactory
    fun aRecompositionErrorKeepsTheRecomposerRegisteredUntilAnInvalidationRecoversIt(): List<DynamicTest> =
        perOwner { owner ->
            hotReloadCase(owner) { host, code ->
                val panel = host.setContent { ReloadableInParent(code) }
                awaitUntil("the content composes") { labelTexts(panel) == listOf("v1#v1@v1") }
                val recomposer = assertNotNull(host.recomposer)

                val failure = IllegalStateException("boom")
                code.compositionFailure = failure
                code.invalidate()
                awaitRecordedError(recomposer, failure)
                assertRegistered(recomposer)
                assertEquals(listOf("v1#v1@v1"), labelTexts(panel), "the failed pass leaves the content as it was")

                code.compositionFailure = null
                code.text = "v2"
                invalidateGroupsWithKey(reloadableGroupKey())
                awaitUntil("the invalidation composes the redefined group again and leaves its parent") {
                    labelTexts(panel) == listOf("v2#v2@v1")
                }
                awaitUntil("the recovered recomposer recomposes again") { recomposer.isRecomposing }
                assertFalse(recomposer.isDisposed, "the recovered recomposer is the one that failed")
            }
        }

    @TestFactory
    fun aFirstCompositionErrorKeepsTheRecomposerRegisteredUntilAnInvalidationRecoversIt(): List<DynamicTest> =
        perOwnerAndTiming { owner, timing ->
            hotReloadCase(owner) { host, code ->
                if (timing == FirstCompositionTiming.OnARunningRecomposer) host.startRunning()
                val failure = IllegalStateException("boom")
                code.compositionFailure = failure

                // The runtime records the error instead of throwing it at whoever composed.
                val panel = host.setContent { Reloadable(code) }
                val recomposer = assertNotNull(host.recomposer)
                awaitRecordedError(recomposer, failure)
                assertRegistered(recomposer)
                assertEquals(emptyList(), labelTexts(panel), "the failed first pass composes nothing")

                code.compositionFailure = null
                code.text = "v2"
                invalidateGroupsWithKey(reloadableGroupKey())
                awaitUntil("the invalidation composes the redefined content") {
                    labelTexts(panel) == listOf("v2#v2")
                }
                awaitUntil("the recovered recomposer recomposes again") { recomposer.isRecomposing }
                assertFalse(recomposer.isDisposed, "the recovered recomposer is the one that failed")
            }
        }

    @TestFactory
    fun anApplyFailureKeepsTheRecomposerRegisteredUntilASaveAndLoadReloadRecoversIt(): List<DynamicTest> =
        perOwner { owner ->
            hotReloadCase(owner) { host, code ->
                val panel = host.setContent { Reloadable(code) }
                awaitUntil("the content composes") { labelTexts(panel) == listOf("v1#v1") }
                val recomposer = assertNotNull(host.recomposer)

                val failure = IllegalStateException("boom")
                code.applyFailure = failure
                code.invalidate()
                awaitRecordedError(recomposer, failure)
                assertRegistered(recomposer)
                val recorded = currentHotReloadErrors().single { it.cause === failure }
                assertFalse(recorded.recoverable, "the runtime records an apply-phase failure as non-recoverable")

                code.applyFailure = null
                code.text = "v2"
                invalidateGroupsWithKey(reloadableGroupKey())
                delay(QUIET_PERIOD)
                assertEquals(
                    Recomposer.State.Inactive,
                    recomposer.recomposer.currentState.value,
                    "an invalidation skips a recomposer holding a non-recoverable error",
                )
                assertEquals(listOf("v1#v1"), labelTexts(panel), "an invalidation recomposes nothing there")

                saveAndLoadStateForHotReload()
                awaitUntil("the reload composes the redefined content, keeping what it remembered") {
                    labelTexts(panel) == listOf("v2#v1")
                }
                awaitUntil("the reloaded recomposer recomposes again") { recomposer.isRecomposing }
                assertFalse(recomposer.isDisposed, "the reloaded recomposer is the one that failed")
                assertTrue(currentHotReloadErrors().none { it.cause === failure }, "the reload clears the error")
            }
        }

    @TestFactory
    fun aComposeRuntimeErrorIsReportedOnceAndEndsTheRecomposer(): List<DynamicTest> = perOwner { owner ->
        withHotReload(enabled = true) {
            val host = RecomposerHost(owner)
            val code = ReloadableCode()
            try {
                val panel = host.setContent { Reloadable(code) }
                awaitUntil("the content composes") { labelTexts(panel) == listOf("v1#v1") }
                val recomposer = assertNotNull(host.recomposer)

                val failure = composeRuntimeError("broken invariant")
                val reported =
                    reportedUncaught {
                        code.compositionFailure = failure
                        code.invalidate()
                        awaitUntil("the failure ends the recomposer") {
                            recomposer.recomposer.currentState.value == Recomposer.State.ShutDown
                        }
                        assertTrue(recomposer.isDisposed, "the recomposer that failed is disposed")
                        assertFalse(
                            recomposer.recomposer.asRecomposerInfo() in Recomposer.runningRecomposers.value,
                            "an ended recomposer leaves the runtime's running set",
                        )

                        code.compositionFailure = null
                        code.text = "v2"
                        invalidateGroupsWithKey(reloadableGroupKey())
                        delay(QUIET_PERIOD)
                        assertTrue(
                            labelTexts(panel).none { it.startsWith("v2") },
                            "only a restart recovers an ended recomposer",
                        )
                    }
                assertReportedOnce(failure, reported, "the failure is reported once")
            } finally {
                host.dispose()
            }
        }
    }

    /**
     * Runs [case] with hot reload enabled on a fresh host of [owner]'s, and asserts that it reported
     * nothing to the thread's uncaught-exception handler: the runtime logs an error it records itself.
     */
    private inline fun SwingTestScope.hotReloadCase(
        owner: RecomposerOwner,
        case: (RecomposerHost, ReloadableCode) -> Unit,
    ) {
        withHotReload(enabled = true) {
            val host = RecomposerHost(owner)
            try {
                val reported = reportedUncaught { case(host, ReloadableCode()) }
                assertEquals(emptyList(), reported, "an error the runtime records is reported to nobody")
            } finally {
                host.dispose()
            }
        }
    }

    private suspend fun awaitRecordedError(
        recomposer: SwingRecomposer,
        failure: Throwable,
    ) {
        awaitUntil("the runtime records the error") { currentHotReloadErrors().any { it.cause === failure } }
        delay(QUIET_PERIOD)
        assertEquals(
            Recomposer.State.Inactive,
            recomposer.recomposer.currentState.value,
            "a recomposer holding an error waits for a reload",
        )
    }

    private fun assertRegistered(recomposer: SwingRecomposer) {
        assertFalse(recomposer.isDisposed, "a hot reload error leaves the recomposer undisposed")
        assertTrue(
            recomposer.recomposer.asRecomposerInfo() in Recomposer.runningRecomposers.value,
            "a hot reload error leaves the recomposer in the runtime's running set, where a reload reaches it",
        )
    }
}
