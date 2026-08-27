package org.jetbrains.compose.swing.window

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCompositionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.core.EventDispatchHook
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.singleWidget
import java.awt.Toolkit
import javax.swing.JCheckBox
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral coverage for the recomposer an application composition runs on: a change reported under it
 * is settled inside the event that made it, and a change only mirrored is settled from the toolkit's own
 * events - exactly as under a component's own recomposer.
 *
 * A window's content is a subcomposition of the application composition, so what an application-hosted
 * component reaches for its frame is the application's own dispatcher and clock. Content mounted under
 * the application's composition context reaches that same recomposer, which is why this needs no window,
 * no display and no focus.
 */
class ApplicationSettlingTest {
    @Test
    fun aChangeReportedUnderAnApplicationSettlesInsideTheEventThatMadeIt() = runBlocking {
        // Read inside the composition and asserted after it ends, so a settle that never arrives fails as
        // an assertion rather than as an exception thrown through the application's teardown.
        var selectedAtReturn = true

        // Bounded so an application that never exits fails fast instead of hanging the suite.
        withTimeout(WALL_CLOCK_TIMEOUT) {
            awaitApplication {
                val parent = rememberCompositionContext()
                LaunchedEffect(Unit) {
                    val composition = JPanel()
                    val mounted =
                        composition.setContent(parent = parent) {
                            CheckBox(text = "Word wrap", checked = false, onCheckedChange = {})
                        }
                    try {
                        val box = singleWidget(composition, JCheckBox::class.java)
                        box.doClick(0)
                        // Read with no cycle in between: the click's own settle is what has to leave the
                        // box here, not a pass a later event brings.
                        selectedAtReturn = box.isSelected
                    } finally {
                        mounted.dispose()
                        exitApplication()
                    }
                }
            }
        }

        assertFalse(
            selectedAtReturn,
            "a click the caller does not adopt must be off the box before the event that made it " +
                "returns, under an application composition as under a component's own recomposer",
        )
    }

    /**
     * A window declared under `application { }` composes on the application's own clock, and a change made
     * in it is settled ahead of the repaint it provokes only while that clock is one the hook settles.
     * An application names no component to take a toolkit from, so its clock is subscribed on the default
     * toolkit, which is the one the windows it declares are built on.
     *
     * The clocks a toolkit settles are counted through [EventDispatchHook.subscriberCount], which the
     * toolkit's own listeners cannot answer. Counting them needs no window, no display and no focus,
     * while a painting test of an application-declared window would need all three.
     */
    @Test
    fun anApplicationSubscribesItsClockToTheHookForAsLongAsItRuns() = runBlocking {
        val toolkit = Toolkit.getDefaultToolkit()
        // Counted on the event dispatch thread, where the hook is subscribed and withdrawn. The count
        // runs behind everything already queued there, so a window closed by an earlier test has
        // withdrawn its clock from the event its disposal posted by the time this reads.
        val standing = countClocksSettledBy(toolkit)
        var whileRunning = standing

        withTimeout(WALL_CLOCK_TIMEOUT) {
            awaitApplication {
                LaunchedEffect(Unit) {
                    whileRunning = EventDispatchHook.subscriberCount(toolkit)
                    exitApplication()
                }
            }
        }

        assertEquals(
            standing + 1,
            whileRunning,
            "an application's clock must be settled from the toolkit's events while the application runs, " +
                "or a change made in a window it declares is painted before the pass that puts the " +
                "declaration back",
        )
        assertEquals(
            standing,
            countClocksSettledBy(toolkit),
            "an application that has exited must leave nothing holding its clock for the life of the " +
                "toolkit",
        )
    }

    private suspend fun countClocksSettledBy(toolkit: Toolkit): Int =
        withContext(Dispatchers.Swing) { EventDispatchHook.subscriberCount(toolkit) }

    private companion object {
        val WALL_CLOCK_TIMEOUT = 10.seconds
    }
}
