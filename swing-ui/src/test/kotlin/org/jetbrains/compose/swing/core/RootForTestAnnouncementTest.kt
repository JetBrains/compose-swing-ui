// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.OnDemandComposition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.ModifierInfo
import org.jetbrains.compose.swing.node.CreatedNodeHolder
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingComponentNodeListener
import org.jetbrains.compose.swing.reportedUncaught
import org.jetbrains.compose.swing.runSwingTest
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What a content composition tells the [SwingCompositionDiagnostics] of its parent's context: that it exists once it is
 * fully initialized, and that it is gone on every way it ends, exactly once.
 */
class RootForTestAnnouncementTest {
    @Test
    fun aRootIsAnnouncedAfterItIsAttachedAndBeforeItsContentComposes() = runSwingTest {
        val events = ArrayList<String>()
        val probe = RootProbe(events)
        val listener =
            RecordingListener(events) { root -> "created with ${(root.component as JPanel).componentCount} children" }
        val recomposer = Recomposer(Dispatchers.Swing + listener)

        val composition = mount(recomposer, probe)
        composition.setContent { Label(text = "content") }
        composition.dispose()
        recomposer.cancel()

        assertEquals(
            listOf("attached", "created with 0 children", "detached", "disposed"),
            events,
            "the root is announced attached and empty, and withdrawn after it detached",
        )
    }

    @Test
    fun aFailingCreatedCallbackWithdrawsTheRootAndKeepsItsFailure() = runSwingTest {
        val failure = IllegalStateException("created failed")
        val events = ArrayList<String>()
        val listener = RecordingListener(events, createdFailure = failure)
        val recomposer = Recomposer(Dispatchers.Swing + listener)

        val thrown = assertFailsWith<IllegalStateException> { mount(recomposer, RootProbe(events)) }
        recomposer.cancel()

        assertSame(failure, thrown, "the listener's own failure reaches the caller")
        assertEquals(listOf("attached", "created", "detached", "disposed"), events)
    }

    @Test
    fun aFailingFirstPassWithdrawsTheRootOnceAndKeepsItsFailure() = runSwingTest {
        val failure = IllegalStateException("content failed")
        val events = ArrayList<String>()
        val recomposer = Recomposer(Dispatchers.Swing + RecordingListener(events))
        val composition = mount(recomposer, RootProbe(events))

        val thrown = assertFailsWith<IllegalStateException> { composition.setContent { throw failure } }
        recomposer.cancel()

        assertSame(failure, thrown, "the first pass's failure reaches the caller")
        assertEquals(listOf("attached", "created", "detached", "disposed"), events)
    }

    @Test
    fun aFailingFirstPassWithdrawsTheRootEvenWhenShuttingTheRecomposerDownThrows() = runSwingTest {
        val failure = IllegalStateException("content failed")
        val shutdownFailure = IllegalStateException("completion handler failed")
        val events = ArrayList<String>()
        val recomposer = Recomposer(Dispatchers.Swing + RecordingListener(events))
        checkNotNull(recomposer.effectCoroutineContext[Job]).invokeOnCompletion { throw shutdownFailure }
        val composition = mount(recomposer, RootProbe(events))

        val thrown = assertFailsWith<IllegalStateException> { composition.setContent { throw failure } }

        assertSame(failure, thrown, "the first pass's failure reaches the caller")
        assertTrue(
            thrown.suppressed.any { it === shutdownFailure || it.cause === shutdownFailure },
            "the shutdown failure rides on it: ${thrown.suppressed.toList()}",
        )
        assertEquals(listOf("attached", "created", "detached", "disposed"), events)
    }

    @Test
    fun aFailingDisposalWithdrawsTheRootOnce() = runSwingTest {
        val failure = IllegalStateException("effect disposal failed")
        val events = ArrayList<String>()
        val recomposer = Recomposer(Dispatchers.Swing + RecordingListener(events))
        val composition = mount(recomposer, RootProbe(events))
        composition.setContent { DisposableEffect(Unit) { onDispose { throw failure } } }

        val thrown = assertFailsWith<IllegalStateException> { composition.dispose() }
        composition.dispose()
        recomposer.cancel()

        assertSame(failure, thrown, "the effect's own failure reaches the caller")
        assertEquals(listOf("attached", "created", "detached", "disposed"), events)
    }

    @Test
    fun aFailingDetachStillWithdrawsTheRoot() = runSwingTest {
        val failure = IllegalStateException("detach failed")
        val events = ArrayList<String>()
        val recomposer = Recomposer(Dispatchers.Swing + RecordingListener(events))
        val composition = mount(recomposer, RootProbe(events, detachFailure = failure))
        composition.setContent { Label(text = "content") }

        val reported = reportedUncaught { composition.dispose() }
        recomposer.cancel()

        assertEquals(listOf<Throwable>(failure), reported, "the root's own failure is reported, not lost")
        assertEquals(listOf("attached", "created", "detached", "disposed"), events)
    }

    @Test
    fun disposingAgainWithdrawsTheRootNoSecondTime() = runSwingTest {
        val events = ArrayList<String>()
        val recomposer = Recomposer(Dispatchers.Swing + RecordingListener(events))
        val composition = mount(recomposer, RootProbe(events))
        composition.setContent { Label(text = "content") }

        composition.dispose()
        composition.dispose()
        recomposer.cancel()

        assertEquals(listOf("attached", "created", "detached", "disposed"), events)
    }

    @Test
    fun anOnDemandCompositionAnnouncesNothing() = runSwingTest {
        val events = ArrayList<String>()
        val recomposer = Recomposer(Dispatchers.Swing + RecordingListener(events))

        val composition = OnDemandComposition(recomposer) { Label(text = "on demand") }
        composition.dispose()
        recomposer.cancel()

        assertEquals(emptyList(), events, "a composition that only renders on demand is never a root")
    }

    private fun mount(
        recomposer: Recomposer,
        probe: RootProbe,
    ): SwingContentComposition =
        SwingContentComposition.nested(recomposer, CreatedNodeHolder(probe), compositionDataHost = null, ::SwingApplier)

    /** Records one event per call into [events], and fails the created call with [createdFailure] where set. */
    private class RecordingListener(
        private val events: MutableList<String>,
        private val createdFailure: Throwable? = null,
        private val describeCreated: (RootForTest) -> String = { "created" },
    ) : SwingCompositionDiagnostics {
        override fun declaring(
            info: ModifierInfo,
            write: () -> Unit,
        ) = write()

        override fun restoring(
            info: ModifierInfo,
            restore: () -> Unit,
        ) = restore()

        override fun onRootForTestCreated(root: RootForTest) {
            events += describeCreated(root)
            createdFailure?.let { throw it }
        }

        override fun onRootForTestDisposed(root: RootForTest) {
            events += "disposed"
        }
    }

    /** A root that records its attach and detach into [events], failing the detach with [detachFailure] where set. */
    private class RootProbe(
        private val events: MutableList<String>,
        private val detachFailure: Throwable? = null,
    ) : JPanel(),
        SwingComponentNodeListener {
        override fun onAttached(node: SwingComponentNode<*>) {
            events += "attached"
        }

        override fun onDetached(node: SwingComponentNode<*>) {
            events += "detached"
            detachFailure?.let { throw it }
        }
    }
}
