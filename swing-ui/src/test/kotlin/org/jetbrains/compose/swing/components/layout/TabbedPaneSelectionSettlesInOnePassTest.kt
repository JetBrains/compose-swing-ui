package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.core.TracedTest
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTabbedPane
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which apply pass puts a [TabbedPane] on the tab its declaration names.
 *
 * One pass: the pass that declares a tab is the pass that puts the pane on it, whether the declaration
 * names a tab the strip already holds or one the same pass adds. A tab becomes a page of the pane only
 * after the pane's own update block has run, so the selection is settled at the end of the change pass
 * instead - the point at which the strip holds what the composition declares for it. The same settle
 * answers a pass that only grows the strip, which is what puts the pane on a standing declaration that
 * has just become one it can honor.
 *
 * The frames are driven by the test, which is what makes the passes countable: each frame carries one, and
 * the idle gate publishes a declaration without sending a frame of its own, so the frame that follows is
 * the apply pass that carries it.
 *
 * Each case states what its passes cost, because a selection read back says nothing about which route
 * settled it: a pane that reached the declared tab on a later pass, or that settled through a listener of
 * its own, would leave the same index behind.
 */
class TabbedPaneSelectionSettlesInOnePassTest : TracedTest() {
    @Test
    fun aTabAndTheSelectionNamingItAreSettledByThePassThatDeclaresBoth() = runComposeSwingTest {
        var grown by mutableStateOf(false)
        setContent {
            TabbedPane(selectedIndex = if (grown) ADDED_TAB else FIRST_TAB, onSelectedIndexChange = {}) {
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
        tracer.clear()
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            listOf(listOf("insert", "attach", "settle")),
            tracer.passes(),
            "one pass should put the page on the strip and settle the selection naming it, the settle " +
                "running once the page is attached: ${tracer.sections}",
        )
        assertEquals(GROWN_TABS, pane.tabCount, "the pass declaring the tab should leave it on the strip")
        assertEquals(
            ADDED_TAB,
            pane.selectedIndex,
            "the pass declaring a tab and the selection naming it should leave the pane on that tab, " +
                "not on the one it stood on before",
        )

        tracer.clear()
        mainClock.advanceTimeByFrame()

        assertEquals(ADDED_TAB, pane.selectedIndex, "the pane should stay on the tab that pass settled it on")
        assertEquals(
            emptyList(),
            tracer.passes(),
            "the pass that carried the declaration read the pane back inside its own settlement, so no " +
                "successor pass is bought to answer the change it made: ${tracer.sections}",
        )
    }

    @Test
    fun aSelectionNamingATabTheStripAlreadyHoldsIsSettledByThePassThatCarriesIt() = runComposeSwingTest {
        var moved by mutableStateOf(false)
        setContent {
            TabbedPane(selectedIndex = if (moved) SECOND_TAB else FIRST_TAB, onSelectedIndexChange = {}) {
                Label(text = "one", modifier = SwingModifier.tab("One"))
                Label(text = "two", modifier = SwingModifier.tab("Two"))
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(FIRST_TAB, pane.selectedIndex, "the pane should open on the tab it first names")

        moved = true
        tracer.clear()
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            SECOND_TAB,
            pane.selectedIndex,
            "a tab the strip already holds should be selected by the pass carrying the declaration",
        )
        assertEquals(
            listOf(listOf("settle")),
            tracer.passes(),
            "one pass, changing no container: a selection the strip can already answer is settled where " +
                "every other one is, at the end of the pass that carries it: ${tracer.sections}",
        )

        tracer.clear()
        mainClock.advanceTimeByFrame()

        assertEquals(SECOND_TAB, pane.selectedIndex, "the pane should stay on the tab that pass settled it on")
        assertEquals(
            emptyList(),
            tracer.passes(),
            "the settlement recorded what the pane was left holding, so nothing follows to absorb its " +
                "read-back: ${tracer.sections}",
        )
    }

    @Test
    fun aTabArrivingUnderAStandingDeclarationIsSelectedByThePassThatAddsIt() = runComposeSwingTest {
        var grown by mutableStateOf(false)
        setContent {
            // The declaration never moves and names no tab of the opening strip: only the content reads
            // `grown`, so the pass that grows the strip recomposes no scope that declares the selection.
            TabbedPane(selectedIndex = ADDED_TAB, onSelectedIndexChange = {}) {
                Label(text = "one", modifier = SwingModifier.tab("One"))
                Label(text = "two", modifier = SwingModifier.tab("Two"))
                if (grown) Label(text = "three", modifier = SwingModifier.tab("Three"))
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(
            FIRST_TAB,
            pane.selectedIndex,
            "a declaration naming no tab of the strip should leave the pane on the tab it fell back on",
        )

        grown = true
        tracer.clear()
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(
            ADDED_TAB,
            pane.selectedIndex,
            "the pass adding the tab the standing declaration names should leave the pane on it, not on " +
                "the fallback tab it stood on while that tab was missing",
        )
        assertEquals(
            listOf(listOf("insert", "attach", "settle")),
            tracer.passes(),
            "the settle is kept across passes, so the pass that only grows the strip runs it too: " +
                "${tracer.sections}",
        )
    }

    @Test
    fun aTabLeavingTheStripSettlesTheStandingDeclarationInOnePass() = runComposeSwingTest {
        var items by mutableStateOf(listOf("one", "two", "three"))
        setContent {
            // "three" is selected. Removing "two" moves it from index 2 to index 1, so the standing
            // declaration names a different index than it did before the pass.
            TabbedPane(selectedIndex = items.indexOf("three"), onSelectedIndexChange = {}) {
                for (item in items) {
                    key(item) {
                        Label(text = item, modifier = SwingModifier.tab(item.replaceFirstChar(Char::titlecase)))
                    }
                }
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(3, pane.tabCount)
        assertEquals(2, pane.selectedIndex)

        items = listOf("one", "three")
        tracer.clear()
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(2, pane.tabCount)
        assertEquals(1, pane.selectedIndex)
        assertEquals(
            listOf(listOf("remove", "settle")),
            tracer.passes(),
            "the pass removing a tab should settle the standing selection in one pass: ${tracer.sections}",
        )
    }

    @Test
    fun reorderingKeyedTabsSettlesTheStandingDeclarationInOnePass() = runComposeSwingTest {
        var items by mutableStateOf(listOf("one", "two", "three"))
        setContent {
            TabbedPane(selectedIndex = 1, onSelectedIndexChange = {}) {
                for (item in items) {
                    key(item) {
                        Label(text = item, modifier = SwingModifier.tab(item.replaceFirstChar(Char::titlecase)))
                    }
                }
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(3, pane.tabCount)
        assertEquals(1, pane.selectedIndex)

        items = listOf("one", "three", "two")
        tracer.clear()
        awaitIdle()
        mainClock.advanceTimeByFrame()

        assertEquals(1, pane.selectedIndex)
        assertEquals(
            listOf(listOf("move", "settle")),
            tracer.passes(),
            "the pass moving a tab should settle the selection in one pass: ${tracer.sections}",
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
