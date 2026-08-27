package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.core.SwingRecomposer
import java.awt.Component
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mounts [content], takes the single widget it declares off [declared] as the user would, and asserts
 * the declaration is back on the widget after exactly [QUEUED_PUT_BACK_CYCLES] event-dispatch cycles.
 * The caller adopts nothing, so every change made here is one the composition puts back.
 *
 * This is for a change made outside any event the toolkit hands to its listeners, which is what a change
 * made here is, being made from the test rather than from a component dispatching an event. A dropped
 * value is the real gesture that lands this way: it arrives as a `SunDropTargetEvent`, which a component
 * returns on before it notifies the toolkit's listeners, so no frame is queued ahead of the repaint the
 * drop provokes and the put-back travels the event queue. A change the toolkit does announce - a click, a
 * keystroke, the motion events a continuous gesture arrives as - is settled before it is painted, and is
 * asserted through [assertUnadoptedChangeIsNeverPainted] instead, which states the stronger property this
 * cannot.
 *
 * This runs on a real [SwingRecomposer]. The test harness paces its own composition on a clock the test
 * drives by hand, which takes the cadence under test out of the picture.
 *
 * The timer that paces frame-driven work is asserted to stay stopped throughout, so the put-back is
 * pinned to the event queue rather than to how long the assertion waits.
 *
 * [content] is handed the report to call from the callback the component under test reports changes
 * through. Calling it adopts nothing - it only states that the widget told the caller it had changed,
 * which is what says the gesture landed rather than being one the widget ignores. It is what the
 * put-back is counted from, rather than the widget afterwards, because a widget settled inside the event
 * that changed it is back on [declared] by the time that event returns.
 *
 * @param type the class the widget under test is built as
 * @param declared the value [content] declares, which the widget must be holding again at the end
 * @param content the content under test, declaring exactly one component
 * @param change the user's own change, made on the widget itself
 * @param read the widget property [declared] is measured against
 */
internal suspend fun <C : Component> assertUnadoptedChangeIsPutBack(
    type: Class<C>,
    declared: Any?,
    content: @Composable (report: () -> Unit) -> Unit,
    change: (C) -> Unit,
    read: (C) -> Any?,
) {
    val composition = JPanel()
    val recomposer = SwingRecomposer.create(composition)
    var mounted: DisposableHandle? = null
    try {
        var reported = false
        // setContent composes and applies before it returns, so the widget is in the composition already
        // and there is nothing to wait for here.
        mounted = composition.setContent(parent = recomposer.compositionContext) { content { reported = true } }
        val widget = singleWidget(composition, type)
        assertEquals(declared, read(widget), "the widget must mount holding what the content declares")

        change(widget)
        assertTrue(
            reported,
            "the change must reach the ${type.simpleName} and be reported before the put-back is counted",
        )

        val cycles =
            awaitWithin(QUEUED_PUT_BACK_CYCLES + 1) {
                // Asserted on every cycle rather than once at the end: the timer stops itself as soon as
                // nothing awaits a frame, so a tick that came and went would leave nothing to find here.
                assertNothingIsPaced(recomposer)
                read(widget) == declared
            }
        assertEquals(
            QUEUED_PUT_BACK_CYCLES,
            cycles,
            "A change the caller does not adopt must come off the ${type.simpleName} on the cycles right " +
                "after the event that made it, and on exactly the cycles the queued path costs; it held " +
                "${read(widget)} at the end",
        )
    } finally {
        mounted?.dispose()
        recomposer.dispose()
    }
}

/**
 * Fails if [recomposer] has started the timer that paces frame-driven work.
 *
 * A put-back travels the event queue, and the content under test awaits no frame, so that timer must
 * never run.
 */
private fun assertNothingIsPaced(recomposer: SwingRecomposer) {
    assertFalse(
        recomposer.clock.isPacingFrameDrivenWork,
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
 * The event-dispatch cycles a change takes to come off a widget whose put-back travels the event queue:
 *
 * ```
 * cycle 0   the change        the widget reports it, and the mirror records it as snapshot state;
 *                             that write schedules an apply notification
 * cycle 1   the notification  the write is published to the composition, which invalidates the
 *                             component that reads the mirror and asks the clock for a frame
 * cycle 2   the frame         the pass recomposes and settles the declaration back onto the widget
 * ```
 *
 * The count is exact in both directions, which is what makes these cases say something. Fewer cycles
 * means the widget settles inside the event that changed it, and belongs to
 * [assertUnadoptedChangeIsNeverPainted] rather than here - that is a stronger property than any cycle
 * count, and a case left here would pass without stating it. More cycles means the put-back no longer
 * follows the event queue at all; paced by a frame clock instead, the same cases reach the declaration
 * after tens of thousands of cycles.
 */
private const val QUEUED_PUT_BACK_CYCLES: Int = 2
