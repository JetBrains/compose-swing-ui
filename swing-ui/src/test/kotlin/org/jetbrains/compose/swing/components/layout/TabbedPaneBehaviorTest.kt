package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * End-to-end tests for [TabbedPane]. They assert observable behavior on the
 * rendered [JTabbedPane]: a tab declared in the composition is added (tabCount/title), a tab dropped
 * from the composition is removed dynamically through the node lifecycle (no per-tab effect), tab
 * attributes update via recomposition, and the controlled `selectedIndex` drives the selection while a
 * user change fires the callback. A declared tab header renders the tab in the strip, follows its own
 * state, sees the state hoisted around the pane, and goes away with its tab.
 */
class TabbedPaneBehaviorTest {
    /**
     * A pane wide enough for its whole tab strip. A click is aimed at a tab's position on the strip, so
     * a pane laid out too narrow for a tab leaves that click nothing to land on.
     */
    private val roomForTheStrip: SwingModifier = SwingModifier.preferredSize(600, 400)

    private fun titles(pane: JTabbedPane): List<String> = (0 until pane.tabCount).map { pane.getTitleAt(it) }

    /** The text of the single label a tab's header renders, or `null` when that tab renders no header. */
    private fun headerTextAt(
        pane: JTabbedPane,
        index: Int,
    ): String? {
        val host = pane.getTabComponentAt(index) as? Container ?: return null
        val labels = host.components.filterIsInstance<JLabel>()
        return labels.singleOrNull()?.text
    }

    private fun headers(pane: JTabbedPane): List<String?> = (0 until pane.tabCount).map { headerTextAt(pane, it) }

    /** The text of the single label each tab's body renders, in tab order. */
    private fun bodyTexts(pane: JTabbedPane): List<String> = (0 until pane.tabCount).map { bodyTextAt(pane, it) }

    private fun bodyTextAt(
        pane: JTabbedPane,
        index: Int,
    ): String {
        val body = pane.getComponentAt(index) as Container
        val labels = body.components.filterIsInstance<JLabel>()
        return labels.single().text
    }

    @Test
    fun tabsDeclaredInCompositionAreAdded() = runComposeSwingTest {
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                tab("General") { Label("g") }
                tab("Advanced") { Label("a") }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(2, pane.tabCount, "both declared tabs should be added")
        assertEquals(listOf("General", "Advanced"), titles(pane), "tab titles should match the declaration order")
    }

    @Test
    fun droppingATabFromCompositionRemovesItDynamically() = runComposeSwingTest {
        var showSecond by mutableStateOf(true)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                tab("General") { Label("g") }
                if (showSecond) {
                    tab("Advanced") { Label("a") }
                }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(2, pane.tabCount, "both tabs should be present before dropping one")
        assertEquals(listOf("General", "Advanced"), titles(pane), "tab titles should match before dropping one")

        // Dropping the tab from the composition must drive removeTabAt through the node lifecycle.
        showSecond = false
        awaitIdle()
        assertEquals(1, pane.tabCount, "dropped tab was not removed")
        assertEquals(listOf("General"), titles(pane), "only the surviving tab should remain after the drop")

        // Re-adding it brings the tab back.
        showSecond = true
        awaitIdle()
        assertEquals(listOf("General", "Advanced"), titles(pane), "re-adding should bring the dropped tab back")
    }

    @Test
    fun tabAttributesUpdateViaRecomposition() = runComposeSwingTest {
        var title by mutableStateOf("Old")
        var enabled by mutableStateOf(true)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                tab(title, enabled = enabled) { Label("body") }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(listOf("Old"), titles(pane), "the tab should start with its original title")
        assertEquals(true, pane.isEnabledAt(0), "the tab should start enabled")

        title = "New"
        enabled = false
        awaitIdle()
        assertEquals(listOf("New"), titles(pane), "the tab title should update on recomposition")
        assertFalse(pane.isEnabledAt(0), "tab enabled did not update on recomposition")
    }

    @Test
    fun selectedIndexIsControlledAndChangeCallbackFires() = runComposeSwingTest {
        val events = mutableListOf<Int>()
        var selected by mutableIntStateOf(0)
        setContent {
            TabbedPane(selectedIndex = selected, modifier = roomForTheStrip, onSelectedIndexChange = { events += it }) {
                tab("One") { Label("1") }
                tab("Two") { Label("2") }
                tab("Three") { Label("3") }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(0, pane.selectedIndex, "the pane should start on the controlled index")

        // Controlled: pushing a new selectedIndex selects that tab, and the composition asserting its
        // own declaration is not a change to report.
        selected = 2
        awaitIdle()
        assertEquals(2, pane.selectedIndex, "pushing a new selectedIndex should select that tab")
        assertEquals(emptyList(), events, "the composition's own selection should not reach the callback")

        // A user-driven change fires the callback with the new index.
        onNodeOfType<JTabbedPane>().performTabClick(1)
        assertEquals(listOf(1), events, "a user-driven change should fire the callback with the new index")
    }

    @Test
    fun theFirstCompositionLandsASelectedIndexOtherThanTheFirstTab() = runComposeSwingTest {
        // Index 0 is what a pane adopts on its own once the first tab arrives, so only a non-zero
        // opening index shows whether the declared selection actually reaches the pane.
        setContent {
            TabbedPane(selectedIndex = 2, onSelectedIndexChange = {}) {
                tab("One") { Label("1") }
                tab("Two") { Label("2") }
                tab("Three") { Label("3") }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(2, pane.selectedIndex, "the pane should open on the declared index, not on its own default")
    }

    @Test
    fun theStateInAnUnkeyedTabBodyBelongsToThePositionItWasDeclaredIn() = runComposeSwingTest {
        var leading by mutableStateOf(false)
        val created = intArrayOf(0)
        setContent {
            // Every tab is declared from one call site and none is given a key, so nothing but its
            // position distinguishes a tab from its siblings. Each body names the declaration that created
            // the state it holds, and the order in which that state was created, so a body that outlives
            // its declaration shows.
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                val declared = if (leading) listOf("added", "one", "two") else listOf("one", "two")
                declared.forEach { name -> tab(name) { Label(remember { "$name#${++created[0]}" }) } }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(listOf("one#1", "two#2"), bodyTexts(pane), "each body should hold what its declaration created")

        leading = true
        awaitIdle()
        assertEquals(listOf("added", "one", "two"), titles(pane), "the prepended declaration should title tab one")
        assertEquals(
            listOf("one#1", "two#2", "two#3"),
            bodyTexts(pane),
            "state belongs to the position: the standing positions keep theirs and only the new last one starts fresh",
        )
    }

    @Test
    fun aDeclaredHeaderRendersTheTabAndATabWithoutOneKeepsTheDefault() = runComposeSwingTest {
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                tab("General", header = { Label("custom") }) { Label("g") }
                tab("Advanced") { Label("a") }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        val host = pane.getTabComponentAt(0) as? Container
        assertNotNull(host, "a declared header should become the tab component")
        assertTrue(
            SwingUtilities.isDescendingFrom(onNodeWithText("custom").fetch<JLabel>(), host),
            "the header node should be hosted by the tab component",
        )
        assertNull(pane.getTabComponentAt(1), "a tab without a header should keep the default tab rendering")
        assertEquals("General", pane.getTitleAt(0), "the title should still name a tab that renders a header")
    }

    @Test
    fun aHeaderRerendersInPlaceWhenItsStateChanges() = runComposeSwingTest {
        var caption by mutableStateOf("One")
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                tab("General", header = { Label(caption) }) { Label("g") }
            }
        }

        val header = onNodeWithText("One").fetch<JLabel>()

        caption = "Two"
        awaitIdle()
        assertEquals("Two", header.text, "the header should follow the state it reads")
        assertSame(header, onNodeWithText("Two").fetch<JLabel>(), "the header should re-render, not remount")
    }

    @Test
    fun removingATabReleasesItsHeader() = runComposeSwingTest {
        var showSecond by mutableStateOf(true)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                tab("General", header = { Label("h1") }) { Label("g") }
                if (showSecond) {
                    tab("Advanced", header = { Label("h2") }) { Label("a") }
                }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        onNodeWithText("h2").assertExists()

        showSecond = false
        awaitIdle()
        assertEquals(1, pane.tabCount, "the dropped tab should be removed")
        onNodeWithText("h2").assertDoesNotExist()
        assertEquals("h1", headerTextAt(pane, 0), "the surviving tab should keep its own header")
    }

    @Test
    fun swappingTheDeclaredTabsKeepsEachHeaderWithItsTab() = runComposeSwingTest {
        var swapped by mutableStateOf(false)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                (if (swapped) listOf("B", "A") else listOf("A", "B")).forEach { label ->
                    tab(label, header = { Label("header $label") }) { Label("body $label") }
                }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(listOf("A", "B"), titles(pane), "the tabs should start in declaration order")
        assertEquals(listOf("header A", "header B"), headers(pane), "each header should render its own tab")

        swapped = true
        awaitIdle()
        assertEquals(listOf("B", "A"), titles(pane), "the swapped declarations should retitle the tabs")
        assertEquals(listOf("header B", "header A"), headers(pane), "each header should follow its tab")
    }

    @Test
    fun aHeaderSeesStateHoistedAroundTheTabbedPane() = runComposeSwingTest {
        setContent {
            CompositionLocalProvider(LocalCaption provides "hoisted") {
                TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                    tab("General", header = { Label(LocalCaption.current) }) { Label("g") }
                }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(
            "hoisted",
            headerTextAt(pane, 0),
            "a header should compose as a child of the composition enclosing the pane",
        )
    }
}

/**
 * A [androidx.compose.runtime.CompositionLocal] provided around the pane, used to prove a header
 * composes as a child of the enclosing composition. Declared top-level so it carries the `Local` prefix
 * expected of CompositionLocals while remaining file-private to this test.
 */
private val LocalCaption = compositionLocalOf { "unprovided" }
