package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.interaction.performTabClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Container
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * End-to-end tests for [TabbedPane]. They assert observable behavior on the
 * rendered [JTabbedPane]: a child declaring a tab is added (tabCount/title), a child dropped
 * from the composition is removed dynamically through the node lifecycle (no per-tab effect), tab
 * attributes update via recomposition, and the controlled `selectedIndex` drives the selection while a
 * user change fires the callback. A declared tab header renders the tab in the strip, follows its own
 * state, sees the state hoisted around the pane, and goes away with its tab.
 *
 * They also assert the pane's side of the placement contract: a child declaring no tab is refused, any
 * number of children may declare one - identical declarations included - and replacing what fills a tab
 * across a recomposition leaves the tab standing. A pane holds its tabs in the order they are composed, so
 * reordering keyed declarations reorders the strip itself, carrying each tab's body, header and the
 * controlled selection with it.
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

    /** The text each tab's body is composed as, in tab order. */
    private fun bodyTexts(pane: JTabbedPane): List<String> = (0 until pane.tabCount).map { bodyTextAt(pane, it) }

    private fun bodyTextAt(
        pane: JTabbedPane,
        index: Int,
    ): String = (pane.getComponentAt(index) as JLabel).text

    @Test
    fun anUndeclaredTabbedPaneIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { TabbedPane(selectedIndex = -1, onSelectedIndexChange = {}) {} }
        onNodeOfType<JTabbedPane>().assertTreeMatches(JTabbedPane())
    }

    @Test
    fun tabsDeclaredInCompositionAreAdded() = runComposeSwingTest {
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label("g", SwingModifier.tab("General"))
                Label("a", SwingModifier.tab("Advanced"))
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
                Label("g", SwingModifier.tab("General"))
                if (showSecond) {
                    Label("a", SwingModifier.tab("Advanced"))
                }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(2, pane.tabCount, "both tabs should be present before dropping one")
        assertEquals(listOf("General", "Advanced"), titles(pane), "tab titles should match before dropping one")

        // Dropping the child from the composition must drive removeTabAt through the node lifecycle.
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
                Label("body", SwingModifier.tab(title, enabled = enabled))
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
            TabbedPane(selectedIndex = selected, onSelectedIndexChange = { events += it }, modifier = roomForTheStrip) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
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
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
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
                declared.forEach { name -> Label(remember { "$name#${++created[0]}" }, SwingModifier.tab(name)) }
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
    fun aKeyedTabKeepsItsBodyWhenATabIsDeclaredAheadOfIt() = runComposeSwingTest {
        var leading by mutableStateOf(false)
        val created = intArrayOf(0)
        setContent {
            // The same declarations as the unkeyed case above, each tab given a key of its own, so the
            // key is the only thing that has changed about where a body's state ends up.
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                val declared = if (leading) listOf("added", "one", "two") else listOf("one", "two")
                declared.forEach { name ->
                    key(name) { Label(remember { "$name#${++created[0]}" }, SwingModifier.tab(name)) }
                }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(listOf("one#1", "two#2"), bodyTexts(pane), "each body should hold what its declaration created")
        val standingBodies = listOf(pane.getComponentAt(0), pane.getComponentAt(1))

        leading = true
        awaitIdle()
        assertEquals(listOf("added", "one", "two"), titles(pane), "the prepended declaration should title tab one")
        assertEquals(
            listOf("added#3", "one#1", "two#2"),
            bodyTexts(pane),
            "state belongs to the key: the standing tabs keep theirs and only the new declaration starts fresh",
        )
        assertEquals(
            standingBodies,
            listOf(pane.getComponentAt(1), pane.getComponentAt(2)),
            "a keyed tab should carry the very body it was realized as into its new position",
        )
    }

    @Test
    fun aKeyedTabKeepsItsBodyWhenAnUnkeyedTabAheadOfItIsDropped() = runComposeSwingTest {
        // The unkeyed tab stands at position 0 and the keyed one names 0, so a shared identity space would
        // hand the survivor its predecessor's body.
        var showFirst by mutableStateOf(true)
        val created = intArrayOf(0)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                if (showFirst) {
                    Label(remember { "one#${++created[0]}" }, SwingModifier.tab("One"))
                }
                key(0) { Label(remember { "two#${++created[0]}" }, SwingModifier.tab("Two")) }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(listOf("one#1", "two#2"), bodyTexts(pane), "each body should hold what its declaration created")
        val keyedBody = pane.getComponentAt(1)

        showFirst = false
        awaitIdle()
        assertEquals(listOf("two#2"), bodyTexts(pane), "the keyed tab should keep the state its own body holds")
        assertSame(keyedBody, pane.getComponentAt(0), "the keyed tab should keep the very body it was realized as")
    }

    @Test
    fun reorderingKeyedTabsReordersTheStrip() = runComposeSwingTest {
        // Every tab is keyed, so a reordered declaration moves the tabs that stand instead of rebuilding
        // them, and the strip is what carries their order: a pane's tabs are laid out in the order the
        // pane holds them, so the position a tab is declared at is the position it is drawn at.
        var declared by mutableStateOf(listOf("A", "B", "C"))
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                declared.forEach { name -> key(name) { Label("body $name", SwingModifier.tab(name)) } }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(listOf("A", "B", "C"), titles(pane), "the tabs should start in declaration order")
        val standingBodies = List(pane.tabCount) { pane.getComponentAt(it) }

        declared = listOf("C", "B", "A")
        awaitIdle()
        assertEquals(listOf("C", "B", "A"), titles(pane), "the strip should read the order the composition declares")
        assertEquals(
            listOf("body C", "body B", "body A"),
            bodyTexts(pane),
            "each tab of the reordered strip should hold the body that declared it",
        )
        assertEquals(
            listOf(2, 1, 0),
            standingBodies.map { pane.indexOfComponent(it) },
            "a keyed tab should carry the very body it was realized as to the position it is declared at",
        )
    }

    @Test
    fun reorderingKeyedTabsLeavesThePaneOnTheDeclaredTab() = runComposeSwingTest {
        // The selection is controlled by index, so it names the tab standing at that position of the
        // reordered strip. Moving the tabs is the wrapper's own work, and none of the selections it
        // passes through along the way is the user picking a tab.
        val events = mutableListOf<Int>()
        var declared by mutableStateOf(listOf("A", "B", "C"))
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = { events += it }) {
                declared.forEach { name -> key(name) { Label("body $name", SwingModifier.tab(name)) } }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(0, pane.selectedIndex, "the pane should start on the controlled index")

        declared = listOf("C", "B", "A")
        awaitIdle()
        assertEquals(0, pane.selectedIndex, "the pane should be left on the tab the controlled index names")
        assertEquals(
            "body C",
            bodyTextAt(pane, pane.selectedIndex),
            "the selected tab should be the one the reordered declaration puts at the controlled index",
        )
        assertEquals(emptyList(), events, "reordering the tabs should not reach the callback as a user selection")
    }

    @Test
    fun aDeclaredHeaderRendersTheTabAndATabWithoutOneKeepsTheDefault() = runComposeSwingTest {
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label("g", SwingModifier.tab("General", header = { Label("custom") }))
                Label("a", SwingModifier.tab("Advanced"))
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
                Label("g", SwingModifier.tab("General", header = { Label(caption) }))
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
                Label("g", SwingModifier.tab("General", header = { Label("h1") }))
                if (showSecond) {
                    Label("a", SwingModifier.tab("Advanced", header = { Label("h2") }))
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
    fun withdrawingTheHeaderDeclarationTakesItOffTheStrip() = runComposeSwingTest {
        // The header leaves the tab's declaration while the tab itself stands: what the header drove is
        // taken back, the pane renders the tab's own title again, and the page stays the page it holds.
        var declared by mutableStateOf(true)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label(
                    "g",
                    SwingModifier.tab("General", header = if (declared) ({ Label("custom") }) else null),
                )
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals("custom", headerTextAt(pane, 0), "a declared header should render the tab")

        declared = false
        awaitIdle()
        assertNull(pane.getTabComponentAt(0), "a withdrawn declaration should take its header off the strip")
        onNodeWithText("custom").assertDoesNotExist()
        assertEquals(listOf("General"), titles(pane), "the pane should render the tab's own title again")
        assertEquals(1, pane.tabCount, "the page the pane was given stays the page it holds")

        declared = true
        awaitIdle()
        assertEquals("custom", headerTextAt(pane, 0), "redeclaring the header should render the tab with it again")
    }

    @Test
    fun swappingTheDeclaredTabsKeepsEachHeaderWithItsTab() = runComposeSwingTest {
        var swapped by mutableStateOf(false)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                (if (swapped) listOf("B", "A") else listOf("A", "B")).forEach { label ->
                    Label("body $label", SwingModifier.tab(label, header = { Label("header $label") }))
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
    fun reorderingKeyedTabsCarriesEachHeaderToItsTabsPosition() = runComposeSwingTest {
        // A keyed tab moves rather than being retitled in place, so its header travels with it: what the
        // strip renders at a position is the header of the tab declared there.
        var declared by mutableStateOf(listOf("A", "B", "C"))
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                declared.forEach { name ->
                    key(name) {
                        Label("body $name", SwingModifier.tab(name, header = { Label("header $name") }))
                    }
                }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(listOf("header A", "header B", "header C"), headers(pane), "each header should render its tab")

        declared = listOf("C", "B", "A")
        awaitIdle()
        assertEquals(listOf("C", "B", "A"), titles(pane), "the strip should read the order the composition declares")
        assertEquals(
            listOf("header C", "header B", "header A"),
            headers(pane),
            "each header should be rendered at the position its own tab is declared at",
        )
    }

    @Test
    fun aChildDeclaringNoTabIsRefused() = runComposeSwingTest {
        // A pane reaches its pages through insertTab and has no indexed children, so a child naming no
        // tab would be held by the pane and laid out by nobody.
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                        Label("no tab of its own")
                    }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue(
            message.contains("A JTabbedPane holds each child in one of its own regions"),
            "the failure should name the pane that refused the child: $message",
        )
        assertTrue(
            message.contains("Add SwingModifier.tab(title)."),
            "the failure should name the call that would place the child: $message",
        )
    }

    @Test
    fun twoChildrenDeclaringTheSameTabAreTwoTabs() = runComposeSwingTest {
        // A pane holds one tab per child and any number of them, so nothing about a tab is claimed
        // exclusively: two children declaring the very same tab are two tabs of that name, not a clash.
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label("first", SwingModifier.tab("Same"))
                Label("second", SwingModifier.tab("Same"))
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(2, pane.tabCount, "each child declaring a tab should be a tab of its own")
        assertEquals(listOf("Same", "Same"), titles(pane), "identically declared tabs should both stand")
        assertEquals(listOf("first", "second"), bodyTexts(pane), "each tab should hold the body that declared it")
    }

    @Test
    fun replacingWhatFillsATabAcrossARecompositionKeepsTheTab() = runComposeSwingTest {
        // The branches are different composables, so the pass that swaps them removes one child and
        // inserts the other while the composition holds the tab filled throughout.
        var asLabel by mutableStateOf(true)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                if (asLabel) {
                    Label("as a label", SwingModifier.tab("Body"))
                } else {
                    Button("as a button", onClick = { }, modifier = SwingModifier.tab("Body"))
                }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(1, pane.tabCount, "the declared tab should stand")
        assertEquals("as a label", (pane.getComponentAt(0) as JLabel).text, "the tab should hold the declared body")

        asLabel = false
        awaitIdle()
        assertEquals(1, pane.tabCount, "replacing what fills the tab should leave one tab, not two")
        assertEquals(
            "as a button",
            (pane.getComponentAt(0) as JButton).text,
            "the tab should hold the body the composition now declares",
        )

        asLabel = true
        awaitIdle()
        assertEquals(1, pane.tabCount, "swapping the body back should still leave one tab")
        assertEquals(
            "as a label",
            (pane.getComponentAt(0) as JLabel).text,
            "the tab should hold the body the composition declares again",
        )
    }

    @Test
    fun aHeaderSeesStateHoistedAroundTheTabbedPane() = runComposeSwingTest {
        setContent {
            CompositionLocalProvider(LocalCaption provides "hoisted") {
                TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                    Label("g", SwingModifier.tab("General", header = { Label(LocalCaption.current) }))
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
