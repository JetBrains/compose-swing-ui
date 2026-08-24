package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedMoveIsPutBack
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A [TabbedPane] reports the selection the user made, plus the one selection it cannot honor: where the
 * declared index names no tab of the strip the pane is left on a tab of its own, and the caller is told
 * which. Adding and removing tabs moves a `JTabbedPane`'s selection on its own, and the declared index is
 * asserted onto the pane by the composition; neither is an interaction, so neither reaches the callback.
 */
class TabbedPaneSelectionReportingTest {
    /**
     * A pane wide enough for its whole tab strip. A click is aimed at a tab's position on the strip, so
     * a pane laid out too narrow for a tab leaves that click nothing to land on.
     */
    private val roomForTheStrip: SwingModifier = SwingModifier.preferredSize(600, 400)

    /** Clicks the tab at [index] on the strip, as the user picking it, and settles the composition. */
    private suspend fun ComposeSwingTest.selectTab(index: Int) {
        onNodeOfType<JTabbedPane>().performTabClick(index)
    }

    @Test
    fun openingOnADeclaredTabReportsNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        setContent {
            TabbedPane(selectedIndex = 2, onSelectedIndexChange = { reported += it }) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
            }
        }

        assertEquals(2, onNodeOfType<JTabbedPane>().fetch().selectedIndex, "the pane should open on the declared tab")
        assertEquals(emptyList(), reported, "opening on the declared tab is not an interaction")
    }

    @Test
    fun pushingANewSelectedIndexReportsNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var selected by mutableIntStateOf(0)
        setContent {
            TabbedPane(selectedIndex = selected, onSelectedIndexChange = { reported += it }) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
            }
        }

        selected = 2
        awaitIdle()
        assertEquals(
            2,
            onNodeOfType<JTabbedPane>().fetch().selectedIndex,
            "the pane should follow the controlled index",
        )
        assertEquals(emptyList(), reported, "the composition's own selection must not come back as a change")
    }

    @Test
    fun aSelectionTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}, modifier = roomForTheStrip) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
            }
        }

        selectTab(1)

        // The pane is already settled back onto the declared selection by the time the click's own
        // recomposition finishes - not just once some later, unrelated recomposition happens to run.
        assertEquals(
            0,
            onNodeOfType<JTabbedPane>().fetch().selectedIndex,
            "an unadopted selection change does not stand",
        )
    }

    @Test
    fun growingTheStripAndMovingTheDeclaredSelectionReportsNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var leading by mutableStateOf(false)
        setContent {
            // The declared selection names the last tab throughout, so it moves from 1 to 2 in the same
            // pass that grows the strip: the pane both takes on a tab and is written to on its own
            // account, and neither is something the user did.
            TabbedPane(selectedIndex = if (leading) 2 else 1, onSelectedIndexChange = { reported += it }) {
                if (leading) Label("added", SwingModifier.tab("Added"))
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        reported.clear()
        leading = true
        awaitIdle()
        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(3, pane.tabCount, "the added tab should join the strip")
        assertEquals(2, pane.selectedIndex, "the pane should follow the declared selection")
        assertEquals(
            emptyList(),
            reported,
            "neither a tab joining the strip nor the pane's own write is an interaction",
        )
    }

    @Test
    fun aTabArrivingFromContentOnlyStateIsSelectedByTheStandingDeclarationAndReportsNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var showThird by mutableStateOf(false)
        setContent {
            // The declared selection names the third tab throughout - only the content lambda reads
            // showThird - so nothing about the declaration itself moves when that tab arrives. Settling
            // on it has to be driven by the strip growing alone, with no change in selectedIndex or the
            // mirror to also trigger it.
            TabbedPane(selectedIndex = 2, onSelectedIndexChange = { reported += it }) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                if (showThird) Label("3", SwingModifier.tab("Three"))
            }
        }

        reported.clear()
        showThird = true
        awaitIdle()
        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(3, pane.tabCount, "the added tab should join the strip")
        assertEquals(
            2,
            pane.selectedIndex,
            "the pane should land on the tab the standing declaration names, once that tab exists",
        )
        assertEquals(
            emptyList(),
            reported,
            "the declaration taking effect as soon as its tab arrives is not an interaction",
        )
    }

    @Test
    fun droppingTheDeclaredSelectedTabReportsTheTabTheStripIsLeftOn() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var showThird by mutableStateOf(true)
        var firstTitle by mutableStateOf("One")
        setContent {
            // The declared selection names the third tab throughout, so dropping that tab leaves the
            // declaration naming no tab of the strip at all: the pane falls back on a neighbor, and the
            // tab it is left on is one the composition never asked for.
            TabbedPane(selectedIndex = 2, onSelectedIndexChange = { reported += it }) {
                Label("1", SwingModifier.tab(firstTitle))
                Label("2", SwingModifier.tab("Two"))
                if (showThird) Label("3", SwingModifier.tab("Three"))
            }
        }

        reported.clear()
        showThird = false
        awaitIdle()
        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(2, pane.tabCount, "the dropped tab should leave the strip")
        assertEquals(1, pane.selectedIndex, "the pane falls back on the neighbor of the dropped tab")
        assertEquals(listOf(1), reported, "the tab the pane is left on has to reach the caller")

        // The caller is told once: the pane is on a tab the caller now knows about, so a later
        // recomposition that leaves it there has nothing to add.
        reported.clear()
        firstTitle = "Only"
        awaitIdle()
        assertEquals(1, pane.selectedIndex, "the pane should stay on the tab it fell back on")
        assertEquals(emptyList(), reported, "the tab the caller has already been told about is not reported again")
    }

    @Test
    fun anIndexPastTheStripLeavesThePaneOnATabOfItsOwnAndReportsIt() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        setContent {
            // An index the strip has never reached is the same standing declaration a dropped tab leaves
            // behind: it names no tab, so the pane keeps the one it selected for itself and the caller is
            // told which.
            TabbedPane(selectedIndex = 5, onSelectedIndexChange = { reported += it }) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        assertEquals(
            0,
            onNodeOfType<JTabbedPane>().fetch().selectedIndex,
            "the pane stays on the tab it selected for itself",
        )
        assertEquals(listOf(0), reported, "the tab the pane is left on has to reach the caller")
    }

    @Test
    fun anIndexBelowNoSelectionIsRejected() = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    TabbedPane(selectedIndex = -2, onSelectedIndexChange = {}) {
                        Label("1", SwingModifier.tab("One"))
                        Label("2", SwingModifier.tab("Two"))
                    }
                }
                awaitIdle()
            }
        assertTrue(
            "no selected tab or a non-negative tab index" in error.message.orEmpty(),
            "the error must name the selections a pane accepts, but was: ${error.message}",
        )
        assertTrue(
            "-2" in error.message.orEmpty(),
            "the error must name the rejected index, but was: ${error.message}",
        )
    }

    @Test
    fun anIndexBelowNoSelectionIsRejectedEvenOnAPaneWhoseStripNeverChanges() = runComposeSwingTest {
        // No tab ever joins this strip, so nothing here ever moves the declaration, the mirror or the
        // page count - the rejection has to come from the declaration itself, not from a settle it would
        // otherwise never trigger.
        assertFailsWith<IllegalArgumentException> {
            setContent { TabbedPane(selectedIndex = -2, onSelectedIndexChange = {}) {} }
            awaitIdle()
        }
    }

    @Test
    fun removingTheSelectedTabReportsNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var showThird by mutableStateOf(true)
        setContent {
            TabbedPane(selectedIndex = if (showThird) 2 else 1, onSelectedIndexChange = { reported += it }) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                if (showThird) Label("3", SwingModifier.tab("Three"))
            }
        }

        reported.clear()
        showThird = false
        awaitIdle()
        assertEquals(2, onNodeOfType<JTabbedPane>().fetch().tabCount, "the dropped tab should leave the strip")
        assertEquals(emptyList(), reported, "a tab leaving the strip is not an interaction")
    }

    @Test
    fun aSelectionMadeWhileTheDeclarationNamesNoTabIsReportedOnce() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var showThird by mutableStateOf(true)
        var firstTitle by mutableStateOf("One")
        setContent {
            TabbedPane(selectedIndex = 2, onSelectedIndexChange = { reported += it }, modifier = roomForTheStrip) {
                Label("1", SwingModifier.tab(firstTitle))
                Label("2", SwingModifier.tab("Two"))
                if (showThird) Label("3", SwingModifier.tab("Three"))
            }
        }

        showThird = false
        awaitIdle()
        reported.clear()

        // The declaration still names the tab that left, so the pane stays on a tab of its own. The user
        // picking another one is then the last word on where the pane is, and a later recomposition has
        // nothing to add to it.
        selectTab(0)
        assertEquals(listOf(0), reported, "the selection the user made should be reported")

        firstTitle = "Only"
        awaitIdle()
        assertEquals(listOf(0), reported, "the tab the user chose is not reported back a second time")
    }

    @Test
    fun aFallbackOntoATabTheUserOnceChoseIsStillReported() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var selected by mutableIntStateOf(0)
        var showThird by mutableStateOf(true)
        setContent {
            TabbedPane(
                selectedIndex = selected,
                onSelectedIndexChange = { reported += it },
                modifier = roomForTheStrip,
            ) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                if (showThird) Label("3", SwingModifier.tab("Three"))
            }
        }

        // The user visits the middle tab and the composition then declares the last one, so the tab the
        // strip falls back on below is one the caller has heard of before - but not one it has any reason
        // to believe the pane is on now.
        selectTab(1)
        selected = 2
        awaitIdle()
        reported.clear()

        showThird = false
        awaitIdle()
        assertEquals(
            1,
            onNodeOfType<JTabbedPane>().fetch().selectedIndex,
            "the pane falls back on the neighbor of the dropped tab",
        )
        assertEquals(listOf(1), reported, "the tab the pane is left on has to reach the caller however it got there")
    }

    @Test
    fun declaringNoSelectionLeavesTheStripWithNoneAndReportsNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        setContent {
            TabbedPane(selectedIndex = -1, onSelectedIndexChange = { reported += it }) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        assertEquals(
            -1,
            onNodeOfType<JTabbedPane>().fetch().selectedIndex,
            "a declared -1 leaves the strip with no tab selected",
        )
        assertEquals(emptyList(), reported, "the declaration the pane took is not an interaction")
    }

    @Test
    fun clearingAndRestoringTheDeclaredSelectionReportsNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var selected by mutableIntStateOf(1)
        setContent {
            TabbedPane(selectedIndex = selected, onSelectedIndexChange = { reported += it }) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        selected = -1
        awaitIdle()
        assertEquals(-1, pane.selectedIndex, "clearing the declared selection should clear the strip's")

        selected = 0
        awaitIdle()
        assertEquals(0, pane.selectedIndex, "declaring a tab again should put the strip back on one")
        assertEquals(emptyList(), reported, "the composition's own selection must not come back as a change")
    }

    @Test
    fun aSelectionMadeWhileNoneIsDeclaredIsReported() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        setContent {
            TabbedPane(selectedIndex = -1, onSelectedIndexChange = { reported += it }, modifier = roomForTheStrip) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        selectTab(1)
        assertEquals(listOf(1), reported, "the selection the user makes should be reported")
    }

    @Test
    fun aPaneWithNoTabsReportsNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = { reported += it }) {}
        }

        assertEquals(-1, onNodeOfType<JTabbedPane>().fetch().selectedIndex, "a strip with no tabs has no selection")
        assertEquals(emptyList(), reported, "a strip with no tabs leaves the caller nothing to hear about")
    }

    @Test
    fun disablingTheSelectedTabReportsNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var enabled by mutableStateOf(true)
        setContent {
            TabbedPane(selectedIndex = 1, onSelectedIndexChange = { reported += it }) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two", enabled = enabled))
            }
        }

        reported.clear()
        enabled = false
        awaitIdle()
        assertEquals(
            1,
            onNodeOfType<JTabbedPane>().fetch().selectedIndex,
            "the strip should keep the declared selection",
        )
        assertEquals(emptyList(), reported, "what a tab renders as is not a selection")
    }

    @Test
    fun selectingATabReportsIt() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = { reported += it }, modifier = roomForTheStrip) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
            }
        }

        selectTab(1)
        assertEquals(listOf(1), reported, "the selection the user makes should be reported")

        selectTab(2)
        assertEquals(listOf(1, 2), reported, "every later selection should be reported too")
    }

    @Test
    fun selectingATabAfterTheCompositionAssertedItsOwnReportsOnlyTheSelection() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var selected by mutableIntStateOf(0)
        setContent {
            TabbedPane(
                selectedIndex = selected,
                onSelectedIndexChange = { reported += it },
                modifier = roomForTheStrip,
            ) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
            }
        }

        selected = 2
        awaitIdle()
        selectTab(0)
        assertEquals(listOf(0), reported, "only the selection the user made should be reported")
    }

    @Test
    fun aSelectionMadeWhileTheCompositionIsApplyingItsChangesIsReported() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        val pane = arrayOfNulls<JTabbedPane>(1)
        var revision by mutableIntStateOf(0)
        setContent {
            val declaredRevision = revision
            // Ordered ahead of the pane's own trailing selection assert, so the selection lands while
            // the composition's changes are still reaching the pane: it stands in for a selection made
            // from a nested event dispatch during an apply. Only the pane's own writes are its echo; a
            // selection from anywhere else is the user's, whenever it arrives.
            SideEffect { if (declaredRevision > 0) pane[0]?.selectedIndex = 2 }
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = { reported += it }) {
                // The title carries the revision, so the pane itself recomposes in the same pass as the
                // side effect above.
                Label("1", SwingModifier.tab("One $declaredRevision"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
            }
        }

        val composed = onNodeOfType<JTabbedPane>().fetch()
        pane[0] = composed
        revision = 1
        awaitIdle()
        assertEquals(listOf(2), reported, "a selection the pane did not write is the user's and is reported")
        assertEquals(0, composed.selectedIndex, "the pane should still hold the controlled selection")
    }

    @Test
    fun anApplyThatFailsLeavesLaterSelectionsReported() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var failApply by mutableStateOf(false)
        setContent {
            // An apply that throws leaves this composition's changes half-delivered to the pane. Whatever
            // the pane was in the middle of, the selections the user makes afterwards still have to be
            // reported. Read here so the whole pane recomposes and the failing node's apply runs inside
            // the pass that re-settles the selection.
            val failing = failApply
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = { reported += it }, modifier = roomForTheStrip) {
                SwingNode(
                    factory = { JPanel() },
                    update = {
                        set(failing) { if (it) error("the apply of this node fails") }
                        applyModifier(SwingModifier.tab("One"))
                    },
                )
                Label("2", SwingModifier.tab("Two"))
            }
        }

        failApply = true
        awaitIdle()

        selectTab(1)
        assertEquals(listOf(1), reported, "a failed apply must not leave the user's selections unreported")
    }

    @Test
    fun aListenerThatFailsOnThePanesOwnWriteLeavesLaterSelectionsReported() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var selected by mutableIntStateOf(0)
        setContent {
            // A listener of the caller's own is notified of every change the pane publishes, including
            // the ones the pane's own writes publish. This one fails on the second tab, the tab the
            // composition selects below; what the user does afterwards still has to be reported.
            val failing =
                remember {
                    ChangeListener { event ->
                        if ((event.source as JTabbedPane).selectedIndex == 1) error("this listener fails")
                    }
                }
            TabbedPane(
                selectedIndex = selected,
                onSelectedIndexChange = { reported += it },
                modifier = roomForTheStrip.changeListener(failing),
            ) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        selected = 1
        awaitIdle()

        selectTab(0)
        assertEquals(listOf(0), reported, "a failing listener must not leave the user's selections unreported")
        val contained = takeCallerFailures()
        assertTrue(
            contained.all { "this listener fails" in it.message.orEmpty() },
            "the contained failures should be this listener's own, but were: $contained",
        )
        assertTrue(contained.isNotEmpty(), "the listener's failure reaches the test rather than going quiet")
    }

    @Test
    fun aListenerThatFailsOnTheFallbackReportLeavesLaterSelectionsApplied() = runComposeSwingTest {
        var selected by mutableIntStateOf(2)
        var showThird by mutableStateOf(true)
        var failing by mutableStateOf(false)
        setContent {
            val listener =
                remember {
                    ChangeListener { if (failing) error("the fallback report fails") }
                }
            TabbedPane(selectedIndex = selected, changeListener = listener) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                if (showThird) Label("3", SwingModifier.tab("Three"))
            }
        }

        failing = true
        // Dropping the declared tab without moving the index is what leaves the pane on a tab of its own
        // and makes the fallback reach this listener.
        showThird = false
        awaitIdle()
        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(1, pane.selectedIndex, "the pane falls back on the neighbor of the dropped tab")

        failing = false
        selected = 0
        awaitIdle()
        assertEquals(0, pane.selectedIndex, "a later declared selection must still reach the pane")

        val failures = takeCallerFailures()
        assertTrue(failures.isNotEmpty(), "the fallback report's failure is contained")
        assertTrue(
            failures.any { "the fallback report fails" in it.message.orEmpty() },
            "the contained failure should be the fallback report's own, but was: $failures",
        )
    }

    @Test
    fun theChangeListenerOverloadReportsOnlyTheSelection() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var selected by mutableIntStateOf(2)
        setContent {
            val listener =
                remember { ChangeListener { event -> reported += (event.source as JTabbedPane).selectedIndex } }
            TabbedPane(selectedIndex = selected, changeListener = listener, modifier = roomForTheStrip) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
            }
        }

        assertEquals(emptyList(), reported, "opening on the declared tab is not an interaction")

        selected = 1
        awaitIdle()
        assertEquals(emptyList(), reported, "the composition's own selection must not come back as a change")

        selectTab(0)
        assertEquals(listOf(0), reported, "the selection the user makes should reach the declared listener")
    }

    @Test
    fun aSelectionTheCallerDoesNotAdoptComesOffWithinEventCycles() = runSwingTest {
        assertUnadoptedMoveIsPutBack(
            type = JTabbedPane::class.java,
            declared = 0,
            content = {
                TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                    Label("g", SwingModifier.tab("General"))
                    Label("a", SwingModifier.tab("Advanced"))
                }
            },
            move = { it.selectedIndex = 1 },
            read = { it.selectedIndex },
        )
    }
}
