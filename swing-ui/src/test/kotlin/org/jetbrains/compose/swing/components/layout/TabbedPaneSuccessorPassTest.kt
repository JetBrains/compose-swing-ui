package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTabbedPane
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which apply pass puts a [TabbedPane] on the tab its declaration names, when that tab is declared by the
 * same pass.
 *
 * A tab becomes a page of the pane only once the pass that declared it has applied the content, so a pass
 * declaring both a new tab and the selection naming it has no such tab to select while it settles: the
 * pane is left on the tab it already stands on, and the pass the strip's own growth sets off is the one
 * that settles the declaration. A selection naming a tab the strip already holds needs no successor: it
 * settles on the pass that carries it.
 *
 * The frames are driven by the test, which is what makes the passes countable: each frame carries one, and
 * the idle gate publishes a declaration without sending a frame of its own, so the frame that follows is
 * the apply pass that carries it.
 */
class TabbedPaneSuccessorPassTest {
    @Test
    fun aTabAndTheSelectionNamingItAreSettledByThePassTheStripsGrowthSetsOff() = runComposeSwingTest {
        var grown by mutableStateOf(false)
        setContent {
            TabbedPane(selectedIndex = if (grown) ADDED_TAB else FIRST_TAB) {
                Label(text = "one", modifier = SwingModifier.tab("One"))
                Label(text = "two", modifier = SwingModifier.tab("Two"))
                if (grown) Label(text = "three", modifier = SwingModifier.tab("Three"))
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(STANDING_TABS, pane.tabCount, "the pane should open on the tabs it first declares")
        assertEquals(FIRST_TAB, pane.selectedIndex, "the pane should open on the tab it first names")

        // The tab and the selection naming it are declared together, so a single frame carries both.
        grown = true
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(GROWN_TABS, pane.tabCount, "the pass declaring the tab should leave it on the strip")
        assertEquals(
            FIRST_TAB,
            pane.selectedIndex,
            "the pass declaring the tab has no such tab to select while it settles",
        )

        mainClock.advanceTimeByFrame()

        assertEquals(
            ADDED_TAB,
            pane.selectedIndex,
            "the pass the strip's growth sets off should settle the pane on the declared tab",
        )
    }

    @Test
    fun aSelectionNamingATabTheStripAlreadyHoldsIsSettledByThePassThatCarriesIt() = runComposeSwingTest {
        var moved by mutableStateOf(false)
        setContent {
            TabbedPane(selectedIndex = if (moved) SECOND_TAB else FIRST_TAB) {
                Label(text = "one", modifier = SwingModifier.tab("One"))
                Label(text = "two", modifier = SwingModifier.tab("Two"))
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(FIRST_TAB, pane.selectedIndex, "the pane should open on the tab it first names")

        moved = true
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            SECOND_TAB,
            pane.selectedIndex,
            "a tab the strip already holds should be selected by the pass carrying the declaration",
        )
    }

    private companion object {
        const val FIRST_TAB = 0
        const val SECOND_TAB = 1

        /** The tab the growing pass adds, and names as its selection in the same breath. */
        const val ADDED_TAB = 2

        /** The tabs the pane opens on. */
        const val STANDING_TABS = 2

        /** The tabs the pane holds once the strip has grown. */
        const val GROWN_TABS = 3
    }
}
