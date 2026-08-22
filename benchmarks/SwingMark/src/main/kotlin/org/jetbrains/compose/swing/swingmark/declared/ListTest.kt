package org.jetbrains.compose.swing.swingmark.declared

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.ListState
import org.jetbrains.compose.swing.components.selection.rememberListState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.swingmark.fixtures.LIST_DISPLAY_STRING
import org.jetbrains.compose.swing.swingmark.harness.change
import javax.swing.JList

/**
 * `ListTest`: a list whose selection is walked one row at a time, each row scrolled into view.
 *
 * Selection is state on a [ListState]; scrolling is [ListState.revealIndex], the library's counterpart of
 * `ensureIndexIsVisible`. The original's loop starts at the list's selected index, which is `-1`, so its
 * last pass selects a row the list does not have; this walks the rows that exist.
 */
internal class ListTest : DeclaredTest() {
    override val testName: String = "Lists"

    private lateinit var state: ListState

    @Composable
    override fun Content() {
        state = rememberListState()
        // A flow panel filling the tab, with the scroller at the size it prefers inside it - the plain
        // JPanel the original's own test returns.
        FlowPanel {
            ScrollPane {
                ListBox(items = ITEMS, state = state, modifier = SwingModifier.viewport())
            }
        }
    }

    override fun runTest() {
        val list = widget(JList::class.java)
        repeat(ITEMS.size) { index ->
            change(
                apply = {
                    state.selectedIndices = setOf(index)
                    revealOnApply { check(state.revealIndex(index)) }
                },
                reached = { list.selectedIndex == index },
            )
        }
    }

    private companion object {
        const val ITEM_COUNT = 250
        val ITEMS: List<String> = List(ITEM_COUNT) { "$LIST_DISPLAY_STRING $it" }
    }
}
