package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertAskedForLayout
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import javax.swing.JList
import javax.swing.JTree
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests that a declared visible row count reaches the viewport a widget is scrolled in.
 *
 * The count is what the widget answers a scroll pane with for its viewport, and neither setter asks for
 * the layout pass that would act on the new answer: `JTree.setVisibleRowCount` marks the tree invalid
 * and schedules nothing, and `JList.setVisibleRowCount` only fires a property change - the look and feel
 * acts on it where the list wraps its rows, never in the vertical orientation a list carries by default.
 * So a count that changes in a recomposition leaves the pane at its old height unless the write asks for
 * the pass itself.
 */
class VisibleRowCountLayoutTest {
    private val fruit = listOf("apple", "pear", "plum", "cherry", "fig", "quince")

    @Test
    fun aTreesVisibleRowCountChangeAsksForALayoutPass() = runComposeSwingTest {
        var rows by mutableStateOf(4)
        setContent {
            Tree(root = "root", children = { emptyList<String>() }, label = { it }, visibleRowCount = rows)
        }
        awaitIdle()

        val tree = onNodeOfType<JTree>().fetch()
        withRecordedRepaints { recorded ->
            rows = 12
            awaitIdle()

            assertEquals(12, tree.visibleRowCount, "the declared count should reach the tree")
            recorded.assertAskedForLayout(tree, "a new declared visible row count")
        }
    }

    @Test
    fun aListsVisibleRowCountChangeAsksForALayoutPass() = runComposeSwingTest {
        var rows by mutableStateOf(4)
        setContent { ListBox(items = fruit, visibleRowCount = rows) }
        awaitIdle()

        val list = onNodeOfType<JList<*>>().fetch()
        withRecordedRepaints { recorded ->
            rows = 12
            awaitIdle()

            assertEquals(12, list.visibleRowCount, "the declared count should reach the list")
            recorded.assertAskedForLayout(list, "a new declared visible row count")
        }
    }
}
