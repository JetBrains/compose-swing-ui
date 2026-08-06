package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A file-system-shaped value tree: a [name], the [children] known so far, and whether the entry is a
 * [directory] - which is what it knows about children before any have been read.
 */
private data class FileEntry(
    val name: String,
    val children: List<FileEntry> = emptyList(),
    val directory: Boolean = false,
)

/**
 * Which values are branches is the caller's to say. A value the data gives no children is a leaf, with
 * nothing to open, until the caller answers for it - and a value that calls itself a branch can be opened
 * before its children exist, which is where reading them starts.
 */
class TreeLazyChildrenTest {
    private val sample = FileEntry("root", listOf(FileEntry("photos", directory = true)))

    @Test
    fun aValueWithNoChildrenIsALeaf() = runComposeSwingTest {
        setContent {
            Tree(root = sample, children = { it.children }, label = { it.name })
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(
            tree.model.isLeaf(tree.pathTo(0).lastPathComponent),
            "a value the data gives no children is a leaf",
        )

        tree.expandPath(tree.pathTo(0))
        assertFalse(tree.isExpanded(tree.pathTo(0)), "and a leaf has nothing to open")
    }

    @Test
    fun aValueThatReportsChildrenIsABranchBeforeItHasAny() = runComposeSwingTest {
        setContent {
            Tree(root = sample, children = { it.children }, label = { it.name }, hasChildren = { it.directory })
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertFalse(
            tree.model.isLeaf(tree.pathTo(0).lastPathComponent),
            "a value that reports children is a branch with none loaded",
        )

        tree.expandPath(tree.pathTo(0))
        assertTrue(tree.isExpanded(tree.pathTo(0)), "which is what lets the user ask for them")
    }

    @Test
    fun theChildrenAnExpansionReadsShowUnderTheNode() = runComposeSwingTest {
        var contents by mutableStateOf(emptyList<String>())
        setContent {
            Tree(
                root =
                    FileEntry(
                        "root",
                        listOf(FileEntry("photos", contents.map { FileEntry(it) }, directory = true)),
                    ),
                children = { it.children },
                label = { it.name },
                hasChildren = { it.directory },
                onWillExpand = { value, _ ->
                    if (value.name == "photos") contents = listOf("beach.png")
                    true
                },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(0, tree.model.getChildCount(tree.pathTo(0).lastPathComponent), "nothing is read yet")

        tree.expandPath(tree.pathTo(0))
        awaitIdle()

        assertEquals(
            1,
            tree.model.getChildCount(tree.pathTo(0).lastPathComponent),
            "the children the expansion read are under the node",
        )
        assertEquals(
            "beach.png",
            tree.model.getChild(tree.pathTo(0).lastPathComponent, 0).toString(),
            "and each renders its own label",
        )
    }
}
