package org.jetbrains.compose.swing.core

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIsNot
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * What a recomposition pass that throws costs a window: its recomposer. The failure is reported to the
 * event dispatch thread's uncaught-exception handler, the content the window held is torn down, and
 * content set afterwards composes on a fresh recomposer and keeps updating.
 *
 * What a failure must leave behind, and what must never be taken for one: a first composition that
 * throws leaves nothing of the composition it ran in standing, its snapshot observer withdrawn from the
 * global apply observers with the rest; a pass that throws part way through a modifier chain leaves
 * every slot that modifier holds standing, so the teardown it provokes runs through; and ending a
 * recomposer is reported to nobody.
 *
 * The cases that need a window realize a real [javax.swing.JFrame], so the window hands out and
 * replaces its recomposer on the production path. A failure is taken by a handler installed on the
 * event dispatch thread for the case's duration, so it is asserted on rather than printed.
 */
class WindowRecomposerFailureTest {
    @Test
    fun aPassThatThrowsIsReportedAndEndsTheWindowsRecomposer() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        val reported = mutableListOf<Throwable>()
        val thread = Thread.currentThread()
        val enclosingHandler = thread.uncaughtExceptionHandler
        thread.setUncaughtExceptionHandler { _, raised -> reported += raised }
        try {
            var broken by mutableStateOf(false)
            val first = JPanel().also { frame.contentPane.add(it) }
            first.setContent {
                Button(text = "break", onClick = { broken = true })
                if (broken) error("boom")
            }
            awaitUntil("the first content composes in the window") { first.componentCount == 1 }
            val ended = assertNotNull(frame.swingRecomposerOrNull(), "the window drives its content")

            // The pass this click schedules throws.
            (first.getComponent(0) as JButton).doClick()

            awaitUntil("the failed pass is reported to the thread's uncaught-exception handler") {
                reported.isNotEmpty()
            }
            val failure = reported.single()
            assertIsNot<ContainedCallerFailure>(failure, "a pass that throws is not a contained callback failure")
            assertEquals("boom", failure.message, "the failure reported is the one the pass raised")
            awaitUntil("the failed pass ends the window's recomposer") { frame.swingRecomposerOrNull() == null }
            assertTrue(ended.isDisposed, "the recomposer that failed is disposed")
            assertEquals(0, first.componentCount, "the content the window held is torn down")
            assertEquals(0, first.hierarchyListeners.size, "a torn-down content composition frees its container")

            var count by mutableIntStateOf(0)
            val second = JPanel().also { frame.contentPane.add(it) }
            second.setContent {
                Button(text = "inc", onClick = { count++ })
                Label(text = "$count")
            }
            awaitUntil("content set after the failure composes") { second.componentCount == 2 }
            assertNotSame(
                ended,
                frame.swingRecomposerOrNull(),
                "content set after the failure is given a fresh recomposer",
            )

            (second.getComponent(0) as JButton).doClick()
            awaitUntil("the fresh recomposer keeps the content updating") {
                (second.getComponent(1) as JLabel).text == "1"
            }
            assertEquals(listOf(failure), reported, "the failure is reported once")
        } finally {
            thread.setUncaughtExceptionHandler(enclosingHandler)
            frame.dispose()
        }
    }

    @Test
    fun anEffectThatThrowsIsReportedAndEndsTheWindowsRecomposer() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        val reported = mutableListOf<Throwable>()
        val thread = Thread.currentThread()
        val enclosingHandler = thread.uncaughtExceptionHandler
        thread.setUncaughtExceptionHandler { _, raised -> reported += raised }
        try {
            val first = JPanel().also { frame.contentPane.add(it) }
            first.setContent {
                Label(text = "content")
                LaunchedEffect(Unit) { error("boom") }
            }
            awaitUntil("the first content composes in the window") { first.componentCount == 1 }
            val ended = assertNotNull(frame.swingRecomposerOrNull(), "the window drives its content")

            awaitUntil("the effect's failure is reported to the thread's uncaught-exception handler") {
                reported.isNotEmpty()
            }
            val failure = reported.single()
            assertIsNot<ContainedCallerFailure>(failure, "an effect that throws is not a contained callback failure")
            assertEquals("boom", failure.message, "the failure reported is the one the effect raised")
            awaitUntil("the failed effect ends the window's recomposer") { frame.swingRecomposerOrNull() == null }
            assertTrue(ended.isDisposed, "the recomposer whose effect failed is disposed")

            var count by mutableIntStateOf(0)
            val second = JPanel().also { frame.contentPane.add(it) }
            second.setContent {
                Button(text = "inc", onClick = { count++ })
                Label(text = "$count")
            }
            awaitUntil("content set after the failure composes") { second.componentCount == 2 }
            assertNotSame(
                ended,
                frame.swingRecomposerOrNull(),
                "content set after the failure is given a fresh recomposer",
            )

            (second.getComponent(0) as JButton).doClick()
            awaitUntil("the fresh recomposer keeps the content updating") {
                (second.getComponent(1) as JLabel).text == "1"
            }
            assertEquals(listOf(failure), reported, "the failure is reported once")
        } finally {
            thread.setUncaughtExceptionHandler(enclosingHandler)
            frame.dispose()
        }
    }

    @Test
    fun aPassRefusedByASubscriptionSlotLeavesTheWindowsTeardownAbleToRun() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        val reported = mutableListOf<Throwable>()
        val thread = Thread.currentThread()
        val enclosingHandler = thread.uncaughtExceptionHandler
        thread.setUncaughtExceptionHandler { _, raised -> reported += raised }
        try {
            var refused by mutableStateOf(false)
            val panel = JPanel().also { frame.contentPane.add(it) }
            panel.setContent {
                Label(
                    text = "content",
                    modifier =
                        SwingModifier.then(if (refused) TextFieldOnlyElement() else LabelSubscriptionElement()),
                )
            }
            awaitUntil("the first content composes in the window") { panel.componentCount == 1 }
            val ended = assertNotNull(frame.swingRecomposerOrNull(), "the window drives its content")

            // The subscription position is handed an element of another kind, which a label is not the
            // target of. The slot cannot host it, so the pass throws part way through the modifier.
            refused = true

            awaitUntil("the refusal is reported to the thread's uncaught-exception handler") {
                reported.isNotEmpty()
            }
            val failure = reported.first()
            assertTrue(
                failure.message.orEmpty().contains("requires a ${JTextField::class.java.name} target"),
                "the failure reported is the refusal, but was: $failure",
            )

            // The failure ends the recomposer, and ending it disposes the content composing under it,
            // which takes the whole modifier apart. A slot the failed pass had already emptied fails that
            // teardown where it stands, ahead of everything the disposal does after it - cancelling the
            // recomposer, releasing the frame clock, ending the scope they run on.
            awaitUntil("the disposal cancels the runtime recomposer it ran ahead of") {
                ended.recomposer.currentState.value == Recomposer.State.ShutDown
            }
            assertEquals(0, panel.componentCount, "the content the window held is torn down")
            assertEquals(listOf(failure), reported, "the teardown reports nothing of its own")
        } finally {
            thread.setUncaughtExceptionHandler(enclosingHandler)
            frame.dispose()
        }
    }

    /** A subscription element a label is the target of, so it stands until the modifier is taken apart. */
    private class LabelSubscriptionElement : SwingModifier.NodeElement<JComponent, SwingModifier.Node<JComponent>>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val additive: Boolean get() = true

        override fun create(): SwingModifier.Node<JComponent> = SwingModifier.Node()

        override fun update(node: SwingModifier.Node<JComponent>) = Unit

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /** A subscription element no component but a text field is the target of. */
    private class TextFieldOnlyElement : SwingModifier.NodeElement<JTextField, SwingModifier.Node<JTextField>>() {
        override val targetType: Class<JTextField> get() = JTextField::class.java

        override val additive: Boolean get() = true

        override fun create(): SwingModifier.Node<JTextField> = SwingModifier.Node()

        override fun update(node: SwingModifier.Node<JTextField>) = Unit

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    @Test
    fun aFirstPassThatThrowsDisposesTheCompositionItRanIn() = runSwingTest {
        val host = JPanel()
        val recomposer = SwingRecomposer.create(host)
        // Built here rather than through setContent, because a first pass that throws hands its caller
        // no handle: the observer is only reachable from the composition setContent discards.
        val composition =
            SwingContentComposition.nested(recomposer.compositionContext) { owner ->
                SwingApplier(SwingNodeHolder(host).attachedTo(owner))
            }
        try {
            val observer =
                assertNotNull(composition.observer, "a nested content composition observes for the components in it")
            val watched = mutableStateOf(0)
            var answers = 0
            val scope = Any()
            val onChanged: (Any) -> Unit = { answers++ }

            fun observe() = observer.observeReads(scope, onChanged) { watched.value }

            observe()
            Snapshot.withMutableSnapshot { watched.value = 1 }
            assertEquals(1, answers, "the observer of a live content composition answers a change to what it read")

            val failure = assertFailsWith<IllegalStateException> { composition.setContent { error("boom") } }
            assertEquals("boom", failure.message, "the first pass's failure reaches the caller as it is")

            // Observed again, so the reads the disposal dropped stand once more: only being off the
            // global apply observers can keep the change below from reaching this observer.
            observe()
            Snapshot.withMutableSnapshot { watched.value = 2 }
            assertEquals(
                1,
                answers,
                "the observer of a composition whose first pass threw is off the global apply observers",
            )

            var stamped = false
            composition.recomposeSynchronously { stamped = true }
            assertFalse(stamped, "a composition whose first pass threw is disposed, so a stamp on it does nothing")
        } finally {
            composition.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun disposingARecomposerIsNotReportedAsAFailure() = runSwingTest {
        val panel = JPanel()
        val reported = mutableListOf<Throwable>()
        val thread = Thread.currentThread()
        val enclosingHandler = thread.uncaughtExceptionHandler
        thread.setUncaughtExceptionHandler { _, raised -> reported += raised }
        val recomposer = SwingRecomposer.create(panel)
        var caption by mutableStateOf("first")
        var content: DisposableHandle? = null
        try {
            content = panel.setContent(parent = recomposer.compositionContext) { Label(text = caption) }
            caption = "second"
            // Waiting for a pass leaves the runner suspended inside the recomposer's own loop, which is
            // where the cancellation the disposal below sends has to be told apart from a failure.
            awaitUntil("the recomposer recomposes the content it was given") {
                labelTexts(panel) == listOf("second")
            }

            recomposer.dispose()

            // The cancelled runner leaves the recomposer's loop on a turn of its own, queued by the
            // disposal above, and that is the turn a cancellation taken for a failure would be reported
            // from. The recomposer reaches ShutDown as that turn unwinds, so waiting for it waits for
            // the report that must not come; the yields hand the thread back for anything it queues.
            awaitUntil("the disposed recomposer's runner leaves its loop") {
                recomposer.recomposer.currentState.value == Recomposer.State.ShutDown
            }
            repeat(4) { yield() }
            assertTrue(reported.isEmpty(), "disposing a recomposer must report nothing, and reported: $reported")
        } finally {
            thread.setUncaughtExceptionHandler(enclosingHandler)
            content?.dispose()
            recomposer.dispose()
        }
    }
}
