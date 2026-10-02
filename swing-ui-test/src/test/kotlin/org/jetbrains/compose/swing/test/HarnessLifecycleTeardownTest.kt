package org.jetbrains.compose.swing.test

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.swing.core.findLifecycleOwner
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.node.SwingNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Container
import java.awt.event.HierarchyEvent
import javax.swing.JLabel
import javax.swing.JSlider
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HarnessLifecycleTeardownTest {
    /**
     * One test run: the failure its block ends with, the one [reported] to the thread's handler that no gate
     * threw, and those thrown by the owner's ON_DESTROY observer, the content's onDispose and a node's
     * onRelease; then the failures the test must end with - the first, with the rest suppressed onto it in
     * order - or none when it ends normally.
     */
    private class Case(
        val block: Throwable? = null,
        val reported: Throwable? = null,
        val destroy: Throwable? = null,
        val dispose: Throwable? = null,
        val release: Throwable? = null,
        val endsWith: List<Throwable> = emptyList(),
    )

    @TestFactory
    fun endsTheLifecycleFirstAndFailsWithTheFirstFailure(): List<DynamicTest> {
        val cases =
            listOf<Pair<String, () -> Case>>(
                "normal return" to { Case() },
                "failed block" to {
                    val block = UnsupportedOperationException("block")
                    Case(block = block, endsWith = listOf(block))
                },
                "cancelled block" to {
                    val block = CancellationException("cancel")
                    Case(block = block, endsWith = listOf(block))
                },
                "onDispose throw" to {
                    val dispose = AssertionError("dispose")
                    Case(dispose = dispose, endsWith = listOf(dispose))
                },
                "onRelease throw" to {
                    val release = IllegalStateException("release")
                    Case(release = release, endsWith = listOf(release))
                },
                "failed block then onDispose throw" to {
                    val block = UnsupportedOperationException("block")
                    val dispose = AssertionError("dispose")
                    Case(block = block, dispose = dispose, endsWith = listOf(block, dispose))
                },
                "ON_DESTROY throw then onDispose throw" to {
                    val destroy = AssertionError("destroy")
                    val dispose = AssertionError("dispose")
                    Case(destroy = destroy, dispose = dispose, endsWith = listOf(destroy, dispose))
                },
                "one throwable from ON_DESTROY and onDispose" to {
                    val shared = AssertionError("shared")
                    Case(destroy = shared, dispose = shared, endsWith = listOf(shared))
                },
                "reported throw then onRelease throw" to {
                    val reported = IllegalStateException("reported")
                    val release = IllegalStateException("release")
                    Case(reported = reported, release = release, endsWith = listOf(reported, release))
                },
                "reported throw then onDispose throw" to {
                    val reported = IllegalStateException("reported")
                    val dispose = AssertionError("dispose")
                    Case(reported = reported, dispose = dispose, endsWith = listOf(reported, dispose))
                },
            )
        return cases.map { (name, case) -> dynamicTest(name) { assertTeardown(case()) } }
    }

    @Test
    fun chainedCancellationCallbacksSettleBeforeRootIsDetached() {
        var settled = false
        var settledWhenRetired = false
        runComposeSwingTest {
            root.addHierarchyListener { event ->
                if (event.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong() != 0L && !root.isDisplayable) {
                    settledWhenRetired = settled
                }
            }
            setContent {
                LaunchedEffect(Unit) {
                    try {
                        awaitCancellation()
                    } finally {
                        SwingUtilities.invokeLater {
                            SwingUtilities.invokeLater { settled = true }
                        }
                    }
                }
            }
            awaitEventsDelivered()
        }
        assertTrue(settledWhenRetired, "cancellation callbacks must finish before the root is retired")
        assertTrue(settled)
    }

    /**
     * Runs a test as [case] describes and asserts the failure it ends with, and that however it ended the
     * lifecycle was destroyed once, before the content's callbacks ran, the effect was cancelled, each
     * callback ran once and the root was retired and the enclosing handler restored.
     */
    private fun assertTeardown(case: Case) {
        var enclosingHandler: Thread.UncaughtExceptionHandler? = null
        SwingUtilities.invokeAndWait { enclosingHandler = Thread.currentThread().uncaughtExceptionHandler }
        val run = Run(case)

        val expected = case.endsWith.firstOrNull()
        if (expected == null) {
            run.endTest()
        } else {
            val failure = assertFails(run::endTest)
            // runTest rethrows a copy of the failure with the original as its cause.
            val thrown = failure.cause ?: failure
            assertSame(expected, thrown)
            assertEquals(case.endsWith.drop(1), thrown.suppressed.toList())
        }

        SwingUtilities.invokeAndWait {
            assertEquals(Lifecycle.State.DESTROYED, run.owner.lifecycle.currentState)
            assertEquals(1, run.destroys)
            assertEquals(Lifecycle.State.DESTROYED, run.stateInDispose)
            assertEquals(Lifecycle.State.DESTROYED, run.stateInRelease)
            assertEquals(1, run.disposes)
            assertEquals(1, run.releases)
            assertTrue(run.effectEnded, "the effect is cancelled before teardown returns")
            assertFalse(run.testedRoot.isDisplayable)
            assertNull(run.testedRoot.findLifecycleOwner())
            assertSame(enclosingHandler, Thread.currentThread().uncaughtExceptionHandler)
        }
    }

    private class Run(
        private val case: Case,
    ) {
        lateinit var owner: LifecycleOwner
        lateinit var testedRoot: Container
        var destroys = 0
        var disposes = 0
        var releases = 0
        var effectEnded = false
        var stateInDispose: Lifecycle.State? = null
        var stateInRelease: Lifecycle.State? = null

        fun endTest() {
            val slider = JSlider()
            runComposeSwingTest {
                testedRoot = root
                setContent {
                    owner = LocalLifecycleOwner.current
                    case.reported?.let { reported ->
                        SwingNode(factory = { slider }, modifier = SwingModifier.changeListener { raise(reported) })
                    }
                    DisposableEffect(Unit) {
                        onDispose {
                            disposes++
                            stateInDispose = owner.lifecycle.currentState
                            raise(case.dispose)
                        }
                    }
                    LaunchedEffect(Unit) {
                        try {
                            awaitCancellation()
                        } finally {
                            effectEnded = true
                        }
                    }
                    SwingNode(
                        factory = { JLabel() },
                        onRelease = {
                            releases++
                            stateInRelease = owner.lifecycle.currentState
                            raise(case.release)
                        },
                    )
                }
                owner.lifecycle.addObserver(
                    LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_DESTROY) {
                            destroys++
                            raise(case.destroy)
                        }
                    },
                )
                awaitIdle()
                if (case.reported != null) slider.value = 75
                raise(case.block)
            }
        }

        private fun raise(failure: Throwable?) {
            if (failure != null) throw failure
        }
    }
}
