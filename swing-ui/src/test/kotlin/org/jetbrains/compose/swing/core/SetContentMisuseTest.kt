// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.setContentAsInteropHost
import org.jetbrains.compose.swing.window.LocalWindow
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Container
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Panel
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What `setContent` refuses, and what it must go on accepting.
 *
 * Two things make a call unanswerable rather than merely unusual. A container that already holds a live
 * content composition - composed, or still pending until the container reaches a window - is asked once
 * for the composition its contents nest into, and cannot give two answers. A named parent whose
 * recomposer has ended would never recompose what is composed under it. Both fail on the call.
 *
 * Each case that refuses is paired with the nearest legal call, because a check that fires on a legal
 * transition is worse than the misuse it guards against.
 */
class SetContentMisuseTest {
    @TestFactory
    fun aSetContentOnAContainerWithUndisposedContentIsRefused(): List<DynamicTest> = listOf(
        RefusalRow(
            name = "panel",
            container = JPanel(),
            composeFirst = { parent, text -> setContent(parent = parent) { Label(text = text) } },
            composeRefused = { parent, text -> setContent(parent = parent) { Label(text = text) } },
        ),
        menuBarUnderAHostPanel(),
        RefusalRow(
            name = "popup menu content over popup menu content",
            container = JPopupMenu(),
            composeFirst = { parent, text -> setMenuContent(parent, text) },
            composeRefused = { parent, text -> setMenuContent(parent, text) },
        ),
        RefusalRow(
            name = "popup component content over popup menu content",
            container = JPopupMenu(),
            composeFirst = { parent, text -> setMenuContent(parent, text) },
            composeRefused = { parent, text -> setComponentContent(parent, text) },
        ),
        RefusalRow(
            name = "popup menu content over popup component content",
            container = JPopupMenu(),
            composeFirst = { parent, text -> setComponentContent(parent, text) },
            composeRefused = { parent, text -> setMenuContent(parent, text) },
        ),
    ).map { row ->
        dynamicTest(row.name) {
            runSwingTest {
                val recomposer = SwingRecomposer.create(JPanel())
                val parent = recomposer.compositionContext
                val hostHandle = row.host?.setContent(parent = parent) {}
                try {
                    val first = row.first(parent, "first")
                    val composed = row.container.components.toList()
                    assertTrue(composed.isNotEmpty(), "the first content must be composed before the second is refused")

                    assertFailsWith<IllegalStateException> { row.refused(parent, "second") }
                    assertEquals(
                        composed,
                        row.container.components.toList(),
                        "a refused call must leave the composed content as it was",
                    )

                    first.dispose()
                    row.refused(parent, "third").dispose()
                } finally {
                    hostHandle?.dispose()
                    recomposer.dispose()
                }
            }
        }
    }

    @Test
    fun aContainerWhoseListenersWereSweptStillHoldsItsContent() = runSwingTest {
        val panel = JPanel()
        val recomposer = SwingRecomposer.create(panel)
        try {
            panel.setContent(parent = recomposer.compositionContext) { Label(text = "first") }

            // An ordinary Swing cleanup, written by code that knows nothing of this library. What is
            // mounted where is kept under a key nobody else holds, so a sweep takes none of it away.
            for (listener in panel.hierarchyListeners) panel.removeHierarchyListener(listener)

            assertSame(
                recomposer.recomposer,
                panel.findRecomposer(),
                "a swept container must still answer for the content standing in it",
            )
            assertFailsWith<IllegalStateException> {
                panel.setContent(parent = recomposer.compositionContext) { Label(text = "second") }
            }
            assertEquals(1, panel.componentCount, "a refused call must leave the first content mounted")
        } finally {
            recomposer.dispose()
        }
    }

    @Test
    fun aContainerWhoseContentCompositionIsStillPendingRefusesMoreContent() = runSwingTest {
        // In no window and under no host, so mounting is deferred and no context is published yet. The
        // pending content will compose the moment the panel reaches a window, so it stands in the way
        // exactly as a composed one does.
        val panel = JPanel()
        val pending = panel.setContent { Label(text = "first") }

        assertFailsWith<IllegalStateException> {
            panel.setContent { Label(text = "second") }
        }
        assertEquals(0, panel.componentCount, "a refused call must leave the pending content unmounted")

        // Disposing the pending handle frees the container, so the next call is accepted and defers as
        // the first one did.
        pending.dispose()
        val accepted = panel.setContent { Label(text = "third") }
        assertEquals(0, panel.componentCount, "accepted content on a detached container stays pending")
        accepted.dispose()
    }

    @Test
    fun aContainerWhoseCompositionWasDisposedTakesContentAgain() = runSwingTest {
        val panel = JPanel()
        val recomposer = SwingRecomposer.create(panel)
        try {
            val first = panel.setContent(parent = recomposer.compositionContext) { Label(text = "first") }
            first.dispose()

            // Only a live content composition stands in the way, so the container answers for this one now.
            panel.setContent(parent = recomposer.compositionContext) { Label(text = "second") }

            assertEquals(1, panel.componentCount, "the container should hold the content mounted second")
        } finally {
            recomposer.dispose()
        }
    }

    @TestFactory
    fun aContainerWhoseContentThrewOnItsFirstPassTakesContentAgain(): List<DynamicTest> = listOf(
        FirstPassRow(
            name = "panel",
            container = JPanel(),
            composeFailing = { parent -> setContent(parent = parent) { throw FirstPassFailure() } },
            composeAgain = { parent -> setContent(parent = parent) { Label(text = "second") } },
        ),
        FirstPassRow(
            name = "popup menu",
            container = JPopupMenu(),
            composeFailing = { parent -> setContent(parent) { throw FirstPassFailure() } },
            composeAgain = { parent -> setMenuContent(parent, "second") },
        ),
    ).map { row ->
        dynamicTest(row.name) {
            runSwingTest {
                val recomposer = SwingRecomposer.create(JPanel())
                // The failure shuts down the recomposer it composed under, so the next call names another.
                val next = SwingRecomposer.create(JPanel())
                try {
                    assertFailsWith<FirstPassFailure> { row.failing(recomposer.compositionContext) }
                    assertEquals(
                        0,
                        row.container.componentCount,
                        "a content composition that failed its first pass leaves the container empty",
                    )
                    assertEquals(
                        0,
                        row.container.hierarchyListeners.size,
                        "a content composition that failed its first pass leaves no listener behind",
                    )

                    // The failed call composed nothing and handed its caller no handle, so nothing blocks
                    // the next one.
                    val again = row.again(next.compositionContext)

                    assertEquals(
                        1,
                        row.container.componentCount,
                        "the container should have the content mounted second",
                    )
                    again.dispose()
                } finally {
                    next.dispose()
                    recomposer.dispose()
                }
            }
        }
    }

    @Test
    fun aContainerWhoseContentThrewAsItReachedAWindowTakesContentAgain() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            // No parent named and no window, so the content composition waits: the content composes
            // for the first time inside the hierarchy event the add below fires, and that pass throws.
            val panel = JPanel()
            var passes = 0
            val failure = IllegalStateException("boom")
            panel.setContent {
                passes++
                throw failure
            }
            assertEquals(0, passes, "a content composition waiting for a window composes nothing until it reaches one")

            assertSame(
                failure,
                assertFailsWith<IllegalStateException> { frame.contentPane.add(panel) },
                "the failure reaches whoever put the container in the window",
            )

            assertEquals(1, passes, "the content composes once, as its container arrives")
            assertEquals(
                0,
                panel.componentCount,
                "a content composition that threw on arrival leaves the container empty",
            )
            assertEquals(
                0,
                panel.hierarchyListeners.size,
                "a content composition that threw on arrival leaves no listener behind",
            )

            // The window is then free to hand out a fresh recomposer, which lets the container hold
            // content that goes on recomposing.
            awaitUntil("the recomposer that recorded the failure ends") { frame.swingRecomposerOrNull() == null }
            assertEquals(1, passes, "the content that threw is gone and never composes again")

            var caption by mutableStateOf("first")
            panel.setContent { Label(text = caption) }
            assertEquals(listOf("first"), labelTexts(panel), "the container takes content again")

            caption = "second"
            awaitUntil("the content mounted again recomposes") { labelTexts(panel) == listOf("second") }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aContainerWhoseContentThrewOnItsRejoinTakesContentAgain() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        val thread = Thread.currentThread()
        val enclosingHandler = thread.uncaughtExceptionHandler
        val reported = mutableListOf<Throwable>()
        try {
            // Composed in one window and then moved to another, which queues a rejoin that composes the
            // content again. The window the content reads is what makes it throw, so the rejoin's own
            // pass is the only one that can: a move states the new window nowhere but in the rejoin.
            val panel = JPanel().also { first.contentPane.add(it) }
            var passes = 0
            val failure = IllegalStateException("boom")
            panel.setContent {
                passes++
                if (LocalWindow.current === second) throw failure
                Label(text = "content")
            }
            assertEquals(listOf("content"), labelTexts(panel), "the content composes in the window it starts in")

            thread.setUncaughtExceptionHandler { _, raised -> reported += raised }
            second.contentPane.add(panel)

            // The rejoin runs behind the move, so its failure is raised under the event pump rather
            // than at any caller.
            awaitUntil("the failed rejoin is reported to the thread's uncaught-exception handler") {
                reported.isNotEmpty()
            }
            assertSame(failure, reported.single(), "the failure reported is the one the pass raised")
            assertEquals(emptyList(), labelTexts(panel), "the composition the move left behind is gone")
            assertEquals(
                0,
                panel.hierarchyListeners.size,
                "a rejoin that threw leaves no listener behind",
            )

            awaitUntil("the recomposer that recorded the failure ends") { second.swingRecomposerOrNull() == null }
            assertEquals(2, passes, "the content that threw is gone and never composes again")

            var caption by mutableStateOf("first")
            panel.setContent { Label(text = caption) }
            assertEquals(listOf("first"), labelTexts(panel), "the container takes content again")

            caption = "second"
            awaitUntil("the content mounted again recomposes") { labelTexts(panel) == listOf("second") }
        } finally {
            thread.setUncaughtExceptionHandler(enclosingHandler)
            second.dispose()
            first.dispose()
        }
    }

    @Test
    fun contentSetAfterAFirstPassThrewInTheSameEventIsRefusedAndTheRecomposerEnds() = runSwingTest {
        val panel = JPanel()
        val recomposer = SwingRecomposer.create(panel)
        try {
            // The failure lands in the event the recomposer was created in, before its coroutine has had
            // a turn: the recomposer must still end, and nothing may compose under it in between.
            val failure = IllegalStateException("boom")
            assertSame(
                failure,
                assertFailsWith<IllegalStateException> {
                    panel.setContent(parent = recomposer.compositionContext) { throw failure }
                },
                "the first pass's failure reaches the caller as it is",
            )

            var passes = 0
            assertFailsWith<IllegalArgumentException>("content set after the failure is refused") {
                JPanel().setContent(parent = recomposer.compositionContext) {
                    passes++
                    Label(text = "second")
                }
            }
            assertEquals(0, passes, "content refused after the failure never composes")

            awaitUntil("the recomposer that drove the failed pass ends") { recomposer.isDisposed }
        } finally {
            recomposer.dispose()
        }
    }

    @Test
    fun contentSetAfterAFirstPassThrewIsRefusedWhileAnEffectUnderTheRecomposerStillRuns() = runSwingTest {
        val panel = JPanel()
        val recomposer = SwingRecomposer.create(panel)
        try {
            // A running effect holds the recomposer's state until it has finished cancelling, which takes
            // turns of its own: the refusal must not wait for that.
            var effectRunning = false
            JPanel().setContent(parent = recomposer.compositionContext) {
                LaunchedEffect(Unit) {
                    effectRunning = true
                    awaitCancellation()
                }
            }
            awaitUntil("the sibling's effect is running") { effectRunning }

            assertFailsWith<IllegalStateException> {
                panel.setContent(parent = recomposer.compositionContext) { throw IllegalStateException("boom") }
            }

            var passes = 0
            assertFailsWith<IllegalArgumentException>("content set after the failure is refused") {
                JPanel().setContent(parent = recomposer.compositionContext) {
                    passes++
                    Label(text = "second")
                }
            }
            assertEquals(0, passes, "content refused after the failure never composes")

            awaitUntil("the recomposer that drove the failed pass ends") { recomposer.isDisposed }
        } finally {
            recomposer.dispose()
        }
    }

    @Test
    fun aRecomposerThatHasBeenDisposedIsRefusedAsAParent() = runSwingTest {
        val panel = JPanel()
        val recomposer = SwingRecomposer.create(panel)
        val ended = recomposer.compositionContext
        recomposer.dispose()

        assertFailsWith<IllegalArgumentException> {
            panel.setContent(parent = ended) { Label(text = "never composes") }
        }
        assertEquals(0, panel.componentCount, "a refused call must leave the container empty")
        assertEquals(0, panel.hierarchyListeners.size, "a refused call must leave no listener behind")
    }

    @Test
    fun aRecomposerThatIsStillLiveIsAcceptedAsAParent() = runSwingTest {
        val panel = JPanel()
        val recomposer = SwingRecomposer.create(panel)
        try {
            // A recomposer stands at Inactive between being created and its coroutine running, which is
            // not an ended one. Naming it on the call after create() is the transition the check must pass.
            panel.setContent(parent = recomposer.compositionContext) { Label(text = "composes") }

            assertEquals(1, panel.componentCount, "a live recomposer must compose the content it was named for")
        } finally {
            recomposer.dispose()
        }
    }

    @Test
    fun aRecomposerCancelledBeforeItEverRecomposedIsRefusedAsAParent() = runSwingTest {
        // A recomposer cancelled before it ever recomposed reaches an ended state through its effect
        // job completing rather than through the cancel itself, so this pins that a recomposer which
        // never ran is refused too - not only one that ran and was then stopped.
        val neverRan = Recomposer(Dispatchers.Swing)
        neverRan.cancel()

        val panel = JPanel()
        assertFailsWith<IllegalArgumentException> {
            panel.setContent(parent = neverRan) { Label(text = "never composes") }
        }
        assertEquals(0, panel.componentCount, "a refused call must leave the container empty")
    }

    @Test
    fun theContextOfAClosedWindowIsRefusedAsAParent() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = JFrame().apply { pack() }
        val ended = frame.compositionContext()

        // Disposing the window ends the recomposer it owns, which is what would otherwise leave this
        // context looking like a recomposer standing on its own. dispose() posts that event rather than
        // delivering it, so the queue is drained first: until it is, the recomposer is still live and
        // still findable, and refusing the call would be wrong.
        frame.dispose()
        var cycles = 0
        while (frame.swingRecomposerOrNull() != null && cycles++ < CLOSE_CYCLES) yield()
        assertNull(
            frame.swingRecomposerOrNull(),
            "the window must finish closing before this asserts what a closed window's context does",
        )

        val panel = JPanel()
        assertFailsWith<IllegalArgumentException> {
            panel.setContent(parent = ended) { Label(text = "never composes") }
        }
        assertEquals(0, panel.componentCount, "a refused call must leave the container empty")
    }

    @Test
    fun setContentAsInteropHostRefusesANonJComponentHost() = runSwingTest {
        // setContentAsInteropHost is typed as Container, not JComponent, to allow the call site
        // to pass any Container without an explicit cast. The require() inside enforces JComponent.
        val recomposer = SwingRecomposer.create(JPanel())
        try {
            val panel = Panel()
            assertFailsWith<IllegalArgumentException> {
                panel.setContentAsInteropHost(recomposer.compositionContext) { }
            }
            assertEquals(0, panel.componentCount, "a refused call must leave the host empty")
        } finally {
            recomposer.dispose()
        }
    }

    @Test
    fun windowSetContentRefusesAWindowWithNoRootPane() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // A bare java.awt.Frame is a Window but not a RootPaneContainer, so it has no content
        // pane on which to mount the composition. The precondition names the constraint.
        val frame = Frame()
        try {
            assertFailsWith<IllegalArgumentException> {
                frame.setContent { }
            }
            assertEquals(0, frame.componentCount, "a refused call must leave the window without content")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun windowSetContentRefusesAWindowWhoseContentPaneIsNotAJComponent() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // RootPaneContainer.getContentPane() is typed as Container, not JComponent. This replaces
        // the default JPanel content pane with a raw AWT Panel to confirm the second precondition.
        val dialog = JDialog()
        try {
            val pane = Panel()
            dialog.contentPane = pane
            assertFailsWith<IllegalArgumentException> {
                dialog.setContent { }
            }
            assertSame(pane, dialog.contentPane, "a refused call must leave the content pane in place")
        } finally {
            dialog.dispose()
        }
    }

    /** A menu bar in a host panel, whose content composes under the host's published context. */
    private fun menuBarUnderAHostPanel(): RefusalRow<JMenuBar> {
        val host = JPanel()
        val bar = JMenuBar().also { host.add(it) }
        return RefusalRow(
            name = "menu bar under a host panel with a named parent",
            container = bar,
            composeFirst = { _, text -> setContent { MenuItem(text = text, onClick = {}) } },
            composeRefused = { _, text -> setContent { MenuItem(text = text, onClick = {}) } },
            host = host,
        )
    }

    private fun JPopupMenu.setMenuContent(
        parent: CompositionContext,
        text: String,
    ): DisposableHandle = setContent(parent) { MenuItem(text = text, onClick = {}) }

    private fun JPopupMenu.setComponentContent(
        parent: CompositionContext,
        text: String,
    ): DisposableHandle {
        val component: JComponent = this
        return component.setContent(parent) { Label(text = text) }
    }

    private companion object {
        /** Event-dispatch cycles a window's own teardown may take after `dispose()` posts it. */
        const val CLOSE_CYCLES: Int = 50
    }
}

/**
 * A container under test, where [refused] must fail while the content [first] set is undisposed. A [host]
 * is composed under the same parent first, for a container that composes under its published context.
 */
private class RefusalRow<C : Container>(
    val name: String,
    val container: C,
    private val composeFirst: C.(CompositionContext, String) -> DisposableHandle,
    private val composeRefused: C.(CompositionContext, String) -> DisposableHandle,
    val host: JPanel? = null,
) {
    fun first(
        parent: CompositionContext,
        text: String,
    ): DisposableHandle = container.composeFirst(parent, text)

    fun refused(
        parent: CompositionContext,
        text: String,
    ): DisposableHandle = container.composeRefused(parent, text)
}

/** A container under test, where [failing] throws on its first pass and [again] then takes content. */
private class FirstPassRow<C : Container>(
    val name: String,
    val container: C,
    private val composeFailing: C.(CompositionContext) -> Unit,
    private val composeAgain: C.(CompositionContext) -> DisposableHandle,
) {
    fun failing(parent: CompositionContext) = container.composeFailing(parent)

    fun again(parent: CompositionContext): DisposableHandle = container.composeAgain(parent)
}

/** Thrown by content under test, so a test asserts the failure by its type rather than by its message. */
private class FirstPassFailure : RuntimeException()
