package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A value tree: each chapter yields its [children], and its [title] is what the row renders. */
private data class Chapter(
    val title: String,
    val children: List<Chapter> = emptyList(),
)

/**
 * Editing a node reports; it does not move anything. A [Tree] hands the committed edit to the caller with
 * the value edited and its index path, and the row goes on showing what the data says until a composition
 * supplies data that says otherwise.
 *
 * A tree commits an edit by handing the new value to its model, which is the last thing
 * `BasicTreeUI.completeEditing` does when the user finishes typing in a row, so committing that way
 * exercises the same path a real edit takes.
 */
class TreeEditingTest {
    private val sample =
        Chapter(
            "book",
            listOf(
                Chapter("intro"),
                Chapter("body", listOf(Chapter("first"), Chapter("second"))),
            ),
        )

    /** The text rendered by the node reached by following [indices] (child positions) from the root. */
    private fun JTree.labelAt(vararg indices: Int): String {
        var node = model.root as DefaultMutableTreeNode
        for (index in indices) {
            node = node.getChildAt(index) as DefaultMutableTreeNode
        }
        return node.userObject.toString()
    }

    @Test
    fun aTreeIsReadOnlyUntilEditingIsDeclared() = runComposeSwingTest {
        var editable by mutableStateOf(false)
        setContent {
            Tree(root = sample, children = { it.children }, label = { it.title }, isEditable = editable)
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertFalse(tree.isEditable, "a tree edits nothing until editing is declared")

        editable = true
        awaitIdle()

        assertTrue(tree.isEditable, "the declared editability reaches the tree")
        assertTrue(tree.isPathEditable(tree.pathTo(0)), "which is what opens a node's row to the user")
    }

    @Test
    fun committingAnEditReportsTheValueAndItsIndexPath() = runComposeSwingTest {
        val edits = mutableListOf<Triple<String, List<Int>, Any?>>()
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.title },
                isEditable = true,
                onNodeEdit = { value, path, newValue -> edits += Triple(value.title, path, newValue) },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.model.valueForPathChanged(tree.pathTo(1, 0), "opening")

        assertEquals(1, edits.size, "exactly one edit committed")
        assertEquals(
            Triple("first", listOf(1, 0), "opening"),
            edits.single(),
            "the value edited, its index path, and what was entered",
        )
        assertEquals("first", tree.labelAt(1, 0), "the row still shows what the data says")
    }

    @Test
    fun theRowFollowsTheDataTheCallerUpdates() = runComposeSwingTest {
        var title by mutableStateOf("first")
        setContent {
            Tree(
                root = Chapter("book", listOf(Chapter(title))),
                children = { it.children },
                label = { it.title },
                isEditable = true,
                onNodeEdit = { _, _, newValue -> title = newValue.toString() },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.model.valueForPathChanged(tree.pathTo(0), "opening")

        assertEquals("first", tree.labelAt(0), "the edit alone leaves the row where it was")

        awaitIdle()

        assertEquals("opening", tree.labelAt(0), "the data the caller updated is what moves it")
    }
}
