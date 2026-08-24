package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.core.SwingRecomposer
import java.awt.Component
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Mounts [content], moves the single widget it declares off [declared] as the user would, and asserts
 * the declaration is back on the widget within [PUT_BACK_CYCLES] event-dispatch cycles. The caller
 * adopts nothing, so every move made here is one the composition puts back.
 *
 * This runs on a real [SwingRecomposer]. The test harness paces its own composition on a clock the test
 * drives by hand, which takes the cadence under test out of the picture.
 *
 * The timer that paces frame-driven work is asserted to stay stopped throughout, so the put-back is
 * pinned to the event queue rather than to how long the assertion waits.
 *
 * [move] is asserted to land on the widget before the put-back is counted, so a gesture the widget
 * ignores fails here rather than passing for having nothing to undo.
 *
 * @param type the class the widget under test is built as
 * @param declared the value [content] declares, which the widget must be holding again at the end
 * @param content the content under test, declaring exactly one component
 * @param move the user's move, made on the widget itself
 * @param read the widget property [declared] is measured against
 */
internal suspend fun <C : Component> assertUnadoptedMoveIsPutBack(
    type: Class<C>,
    declared: Any?,
    content: @Composable () -> Unit,
    move: (C) -> Unit,
    read: (C) -> Any?,
) {
    val island = JPanel()
    val runtime = SwingRecomposer.create(island)
    var mounted: DisposableHandle? = null
    try {
        // setContent composes and applies before it returns, so the widget is on the island already and
        // there is nothing to wait for here.
        mounted = island.setContent(parent = runtime.compositionContext, content = content)
        val child =
            island.components.singleOrNull()
                ?: fail("The content must declare exactly one component, and declared ${island.componentCount}")
        assertTrue(
            type.isInstance(child),
            "The content must declare a ${type.simpleName}, and declared a ${child.javaClass.simpleName}",
        )
        val widget = type.cast(child)
        assertEquals(declared, read(widget), "the widget must mount holding what the content declares")

        move(widget)
        assertNotEquals(
            declared,
            read(widget),
            "the move must land on the ${type.simpleName} before the put-back is counted",
        )

        awaitWithin(PUT_BACK_CYCLES) {
            // Asserted on every cycle rather than once at the end: the timer stops itself as soon as
            // nothing awaits a frame, so a tick that came and went would leave nothing to find here.
            assertNothingIsPaced(runtime)
            read(widget) == declared
        }
            ?: fail(
                "A move the caller does not adopt must come off the ${type.simpleName} on the cycles right " +
                    "after the event that made it, not a frame interval later; it still held " +
                    "${read(widget)} after $PUT_BACK_CYCLES event-dispatch cycles",
            )
    } finally {
        mounted?.dispose()
        runtime.dispose()
    }
}

/**
 * Fails if [runtime] has started the timer that paces frame-driven work.
 *
 * A put-back travels the event queue, and the content under test awaits no frame, so that timer must
 * never run.
 */
private fun assertNothingIsPaced(runtime: SwingRecomposer) {
    assertFalse(
        runtime.clock.isPacingFrameDrivenWork,
        "a put-back must reach the widget on the event queue, and nothing here awaits a frame, so the " +
            "timer that paces frame-driven work must never start",
    )
}

/**
 * Yields the event dispatch thread up to [cycles] times - checking [condition] before each yield and
 * once more after the last one - and answers how many cycles it took to hold, or `null` if it never
 * did. The cycle count is the only bound: with no deadline behind it, a composition that never settles
 * fails on the count rather than on a wall-clock limit that varies with the machine.
 *
 * [condition] may assert. A failure it raises is passed on rather than read as a cycle where the
 * condition did not hold.
 */
private suspend fun awaitWithin(
    cycles: Int,
    condition: () -> Boolean,
): Int? {
    val schedule = 0..cycles
    for (cycle in schedule) {
        if (condition()) return cycle
        // Not after the last check: a yield nothing looks at again spends a cycle to learn nothing, and
        // puts the count past what this bound states.
        if (cycle < schedule.last) yield()
    }
    return null
}

/**
 * The event-dispatch cycles a move the caller does not adopt may take to come off the widget.
 *
 * The count is a property of the path the answer travels, which is the same for every widget:
 *
 * ```
 * cycle 0   the move          the widget reports it, and the mirror records it as snapshot state;
 *                             that write schedules an apply notification
 * cycle 1   the notification  the write is published to the composition, which invalidates the
 *                             component that reads the mirror and asks the clock for a frame
 * cycle 2   the frame         the pass recomposes and settles the declaration back onto the widget
 * ```
 *
 * Every widget measures two cycles. The cap is one above that, as room for a dispatch hop rather than a
 * tolerance to grow: a put-back that needs more cycles no longer follows the event queue, which is what
 * these tests exist to pin, so raising this constant would retire them rather than fix them. Paced by a
 * frame clock instead, the same tests reach the declaration after tens of thousands of cycles.
 */
private const val PUT_BACK_CYCLES: Int = 3
