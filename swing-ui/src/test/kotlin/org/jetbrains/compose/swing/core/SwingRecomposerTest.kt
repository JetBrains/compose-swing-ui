package org.jetbrains.compose.swing.core

import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import javax.swing.JPanel
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests the precedence [SwingRecomposer.create]'s `effectContext` parameter documents: the process-wide
 * [SwingUiSettings.motionDurationScale] is overridden by a [MotionDurationScale] the caller states on
 * `effectContext`, which in turn is overridden by the recomposer's own dispatcher and job - the two
 * elements a caller cannot state for a composition's effects.
 */
class SwingRecomposerTest {
    @Test
    fun `an effect reads the process-wide motion duration scale when the caller overrides nothing`() = runSwingTest {
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        var observed: MotionDurationScale? = null
        var content: DisposableHandle? = null
        try {
            content =
                composition.setContent(parent = recomposer.compositionContext) {
                    LaunchedEffect(Unit) { observed = coroutineContext[MotionDurationScale] }
                }
            awaitUntil("the effect reads a motion duration scale") { observed != null }

            assertSame(
                SwingUiSettings.motionDurationScale,
                observed,
                "with no override, the process-wide setting reaches the effect",
            )
        } finally {
            content?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun `an effectContext's motion duration scale overrides the process-wide setting`() = runSwingTest {
        val composition = JPanel()
        val override = FixedMotionDurationScale(0.25f)
        val recomposer = SwingRecomposer.create(composition, effectContext = override)
        var observed: MotionDurationScale? = null
        var content: DisposableHandle? = null
        try {
            content =
                composition.setContent(parent = recomposer.compositionContext) {
                    LaunchedEffect(Unit) { observed = coroutineContext[MotionDurationScale] }
                }
            awaitUntil("the effect reads a motion duration scale") { observed != null }

            assertSame(override, observed, "the caller's own scale wins over the process-wide one")
            assertEquals(0.25f, observed?.scaleFactor)
        } finally {
            content?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun `the recomposer's own dispatcher and job override what the effectContext states`() = runSwingTest {
        val composition = JPanel()
        val foreignJob = Job()
        // A dispatcher genuinely distinct from SwingUiDispatcher, standing in for whatever real
        // dispatcher a caller might state on effectContext; Dispatchers.Swing itself needs no
        // injection here since the assertion never depends on which concrete dispatcher this is.
        val foreignDispatcher = Dispatchers.Swing
        val recomposer =
            SwingRecomposer.create(composition, effectContext = foreignJob + foreignDispatcher)
        var observedDispatcher: ContinuationInterceptor? = null
        var observedJob: Job? = null
        var content: DisposableHandle? = null
        try {
            content =
                composition.setContent(parent = recomposer.compositionContext) {
                    LaunchedEffect(Unit) {
                        observedDispatcher = coroutineContext[ContinuationInterceptor]
                        observedJob = coroutineContext[Job]
                        awaitCancellation()
                    }
                }
            awaitUntil("the effect reads its dispatcher and job") { observedDispatcher != null }

            assertTrue(
                observedDispatcher is SwingUiDispatcher,
                "the recomposer's own dispatcher wins over one the caller states",
            )
            foreignJob.cancel()
            assertTrue(
                observedJob?.isActive == true,
                "the recomposer's own job wins over one the caller states: cancelling foreignJob does not " +
                    "cancel effects",
            )
        } finally {
            content?.dispose()
            recomposer.dispose()
            foreignJob.cancel()
        }
    }
}

/** A [MotionDurationScale] fixed at [scaleFactor], for stating an override a test can tell apart by identity. */
private class FixedMotionDurationScale(
    override val scaleFactor: Float,
) : MotionDurationScale
