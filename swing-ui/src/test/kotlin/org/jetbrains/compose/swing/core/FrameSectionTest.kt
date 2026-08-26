package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Pins what the frame clock reports about a frame: the frame is named as a whole, and the changes it
 * applies are reported inside it, so what a declared change costs is the length of one section.
 *
 * The count that is pinned is the change pass, never the frame. A frame carrying nothing may follow the
 * one that carried the writes, and whether it does depends on when the recomposer wakes, so a frame
 * count is a race where a pass count is a contract.
 *
 * This runs on a real [SwingRecomposer] with its real clock, which is the only path through
 * [SwingFrameClock]; the test harness drives a clock of its own and never reaches it.
 */
class FrameSectionTest : TracedTest() {
    @Test
    fun aFrameNamesTheChangesItApplies() = runSwingTest {
        val island = JPanel()
        val runtime = SwingRecomposer.create(island)
        var text by mutableStateOf("v0")
        var content: DisposableHandle? = null
        try {
            content = island.setContent(parent = runtime.compositionContext) { Label(text = text) }
            awaitUntil("the content mounts") { labelTextOrNull(island) == "v0" }
            // The mount composes and applies synchronously, on the caller's own turn rather than on a
            // frame. What this case states is the frame a later write is carried through.
            tracer.clear()

            text = "v1"
            awaitUntil("the write reaches the widget") { labelTextOrNull(island) == "v1" }

            val sections = tracer.sections
            assertTrue(
                sections.any { it.name == "frame" },
                "a declared write is carried through a frame, which should be named: $sections",
            )
            val applied = sections.filter { it.name == "apply" }
            assertEquals(
                1,
                applied.size,
                "a declared write costs one change pass, which should be named: $sections",
            )
            assertTrue(
                "frame" in applied.single().enclosing,
                "a change pass is part of the frame that paid for it and should be reported inside it, " +
                    "but got ${applied.single()}",
            )
        } finally {
            content?.dispose()
            runtime.dispose()
        }
    }

    @Test
    fun everyWriteMadeUnderOneEventIsCarriedByOneChangePass() = runSwingTest {
        val island = JPanel()
        val runtime = SwingRecomposer.create(island)
        var first by mutableStateOf("a0")
        var second by mutableStateOf("b0")
        var third by mutableStateOf("c0")
        var content: DisposableHandle? = null
        try {
            content =
                island.setContent(parent = runtime.compositionContext) {
                    Label(text = first)
                    Label(text = second)
                    Label(text = third)
                }
            awaitUntil("the content mounts") { labelTexts(island) == listOf("a0", "b0", "c0") }
            tracer.clear()

            // No suspension point between the writes, so all three are made while the event dispatch
            // thread is handling a single event, exactly as a Swing listener would.
            first = "a1"
            second = "b1"
            third = "c1"
            awaitUntil("the writes reach their widgets") { labelTexts(island) == listOf("a1", "b1", "c1") }

            val applied = tracer.sections.filter { it.name == "apply" }
            assertEquals(
                1,
                applied.size,
                "writes made under one event should be carried by one change pass; a pass apiece writes " +
                    "every widget property three times and paints two states nobody declared: " +
                    "${tracer.sections}",
            )
            assertTrue(
                "frame" in applied.single().enclosing,
                "the pass carrying the batched writes runs inside a frame, but got ${applied.single()}",
            )
        } finally {
            content?.dispose()
            runtime.dispose()
        }
    }

    /**
     * Suspends on the EDT until [condition] holds, yielding the EDT back between checks so the
     * composition can make progress. A condition that never becomes true fails the test at the deadline,
     * naming [description], instead of hanging.
     */
    private suspend fun awaitUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        try {
            withTimeout(SETTLE_TIMEOUT) {
                while (!condition()) yield()
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("Timed out after $SETTLE_TIMEOUT waiting until $description", timedOut)
        }
    }

    /** The single [JLabel]'s text in [container]'s subtree, or `null` while none has mounted yet. */
    private fun labelTextOrNull(container: Container): String? = labelTexts(container).singleOrNull()

    /** The text of every [JLabel] in [container]'s subtree, in the order the containers hold them. */
    private fun labelTexts(container: Container): List<String> {
        val texts = mutableListOf<String>()

        fun visit(c: Container) {
            for (child in c.components) {
                if (child is JLabel) texts += child.text
                if (child is Container) visit(child)
            }
        }
        visit(container)
        return texts
    }

    private companion object {
        val SETTLE_TIMEOUT = 10.seconds
    }
}
