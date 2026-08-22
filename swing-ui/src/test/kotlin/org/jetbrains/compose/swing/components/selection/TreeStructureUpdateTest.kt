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

/** A value tree: each entry yields its [children], and its [name] is what the row renders. */
private data class Folder(
    val name: String,
    val children: List<Folder> = emptyList(),
)

/**
 * A new structure reaches the nodes the tree is already showing.
 *
 * The child values a list hands over unchanged at its front and at its back keep the nodes they had, so
 * what reaches such a node - whether it is open, whether it is selected - is the tree's own to keep, and
 * what lies between them settles by position. A node added at one end leaves every other node standing,
 * including the ones it pushes down a row.
 *
 * The assertions read the rows the tree shows rather than the model behind them: a node the model gained
 * without the tree being told would answer for itself in the model and have no row at all.
 *
 * These are the undeclared facets throughout: a declared expansion or selection names index paths, and a
 * node inserted above one of them moves what that path names, which is the caller's declaration to make.
 */
class TreeStructureUpdateTest {
    private val opened = Folder("opened", listOf(Folder("leaf")))
    private val start = Folder("root", listOf(opened, Folder("sibling")))

    @Test
    fun anOpenNodeStaysOpenWhenANodeIsAddedBeforeIt() = runComposeSwingTest {
        var root by mutableStateOf(start)
        setContent { Tree(root = root, children = { it.children }, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))
        assertEquals(
            listOf("root", "opened", "leaf", "sibling"),
            tree.rowLabels(),
            "the node the user opened shows its child as a row",
        )

        root = Folder("root", listOf(Folder("added")) + root.children)
        awaitIdle()

        assertEquals(
            listOf("root", "added", "opened", "leaf", "sibling"),
            tree.rowLabels(),
            "the added node takes a row of its own and pushes the rows below it down",
        )
        assertTrue(
            tree.isExpanded(tree.getPathForRow(2)),
            "the node the user opened is open where the structure put it",
        )
        assertFalse(tree.isExpanded(tree.getPathForRow(1)), "and the added node arrives closed")
    }

    @Test
    fun aSelectedNodeStaysSelectedWhenANodeIsAddedBeforeIt() = runComposeSwingTest {
        var root by mutableStateOf(start)
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(root = root, children = { it.children }, label = { it.name }, onSelectionChange = { received += it })
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPath = tree.pathTo(0)
        assertEquals(listOf(1), tree.selectionRows?.toList(), "the row the user selected is selected")
        received.clear()

        root = Folder("root", listOf(Folder("added")) + root.children)
        awaitIdle()

        assertEquals(listOf("opened"), tree.selectedLabels(), "the selected node is the one the data still has")
        assertEquals(
            listOf(2),
            tree.selectionRows?.toList(),
            "and it is selected on the row the added node pushed it down to",
        )
        assertEquals(emptyList(), received, "a structure change that takes no node away reports nothing")
    }

    @Test
    fun aNodeAddedDeeperLeavesTheOpenNodesAboveItAlone() = runComposeSwingTest {
        var root by mutableStateOf(start)
        setContent { Tree(root = root, children = { it.children }, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))

        root = Folder("root", listOf(Folder("opened", opened.children + Folder("late")), root.children[1]))
        awaitIdle()

        assertTrue(tree.isExpanded(tree.getPathForRow(1)), "the node whose children changed stays open")
        assertEquals(
            listOf("root", "opened", "leaf", "late", "sibling"),
            tree.rowLabels(),
            "and the child it gained shows as a row under it",
        )
    }

    @Test
    fun aRemovedNodeLeavesTheSelectionAndIsReportedOnce() = runComposeSwingTest {
        var root by mutableStateOf(start)
        val received = mutableListOf<Set<List<Int>>>()
        setContent {
            Tree(root = root, children = { it.children }, label = { it.name }, onSelectionChange = { received += it })
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(0), tree.pathTo(1))
        received.clear()

        root = Folder("root", listOf(root.children[1]))
        awaitIdle()

        assertEquals(listOf("root", "sibling"), tree.rowLabels(), "the node the data dropped leaves the rows")
        assertEquals(listOf(1), tree.selectionRows?.toList(), "the node the new structure still has stays selected")
        assertEquals(listOf(setOf(listOf(0))), received, "the node the user loses is reported once")
    }

    /**
     * A subtree whose value object is handed over unchanged is passed over, which holds only while the
     * accessors are the ones it was last walked with. An accessor that follows state of the caller's own -
     * a filter, an annotation on the label - is a new one each pass, and every node has to be walked
     * against it however little the data moved.
     */
    @Test
    fun aLabelThatFollowsCapturedStateReachesTheRowsWithTheRootUnchanged() = runComposeSwingTest {
        var suffix by mutableStateOf("")
        setContent {
            val current = suffix
            Tree(root = start, children = { it.children }, label = { it.name + current })
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertEquals(listOf("root", "opened", "sibling"), tree.rowLabels(), "each row reads what the label says")

        suffix = " *"
        awaitIdle()

        assertEquals(
            listOf("root *", "opened *", "sibling *"),
            tree.rowLabels(),
            "every row follows the label accessor even though no value object moved",
        )
    }

    @Test
    fun aRelabeledNodeShowsItsNewText() = runComposeSwingTest {
        var root by mutableStateOf(start)
        setContent { Tree(root = root, children = { it.children }, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))
        val measured = tree.getPathBounds(tree.pathTo(0))?.width

        root = Folder("root", listOf(Folder(LONGER_NAME, opened.children), root.children[1]))
        awaitIdle()

        assertEquals(
            listOf("root", LONGER_NAME, "leaf", "sibling"),
            tree.rowLabels(),
            "the row reads what the data says",
        )
        assertTrue(
            tree.getPathBounds(tree.pathTo(0))!!.width > measured!!,
            "the tree measured that row again, so the width it draws is the new text's",
        )
        assertTrue(tree.isExpanded(tree.pathTo(0)), "and the node it renamed is the node that was open")
    }
}

/** A label far wider than the one it replaces, so a row measured again is a row of another width. */
private const val LONGER_NAME = "opened, under a name several times as wide as the one before it"
