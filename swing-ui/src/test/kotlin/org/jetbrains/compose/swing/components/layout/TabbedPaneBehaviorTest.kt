package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTabbedPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * End-to-end tests for [TabbedPane] over a real [SwingApplier]. They assert observable behavior on the
 * rendered [JTabbedPane]: a tab declared in the composition is added (tabCount/title), a tab dropped
 * from the composition is removed dynamically through the node lifecycle (no per-tab effect), tab
 * attributes update via recomposition, and the controlled [selectedIndex] drives the selection while a
 * user change fires the callback.
 */
class TabbedPaneBehaviorTest {
    private fun titles(pane: JTabbedPane): List<String> = (0 until pane.tabCount).map { pane.getTitleAt(it) }

    @Test
    fun tabsDeclaredInCompositionAreAdded() = runComposeSwingTest {
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                tab("General") { Label("g") }
                tab("Advanced") { Label("a") }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch<JTabbedPane>()
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

        val pane = onNodeOfType<JTabbedPane>().fetch<JTabbedPane>()
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

        val pane = onNodeOfType<JTabbedPane>().fetch<JTabbedPane>()
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
            TabbedPane(selectedIndex = selected, onSelectedIndexChange = { events += it }) {
                tab("One") { Label("1") }
                tab("Two") { Label("2") }
                tab("Three") { Label("3") }
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch<JTabbedPane>()
        assertEquals(0, pane.selectedIndex, "the pane should start on the controlled index")

        // Controlled: pushing a new selectedIndex selects that tab.
        selected = 2
        awaitIdle()
        assertEquals(2, pane.selectedIndex, "pushing a new selectedIndex should select that tab")

        // A user-driven change fires the callback with the new index.
        events.clear()
        pane.selectedIndex = 1
        awaitIdle()
        assertEquals(listOf(1), events, "a user-driven change should fire the callback with the new index")
    }
}
