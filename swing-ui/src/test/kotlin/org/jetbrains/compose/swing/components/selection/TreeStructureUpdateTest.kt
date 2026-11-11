// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import kotlin.test.Test
import kotlin.test.assertEquals

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
 * The selection and the expansion here are the user's throughout: a declared selection, or the expansion a
 * `TreeState` holds, names index paths, and a node inserted above one of them moves what that path names,
 * which is the caller's to answer for.
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
        onNodeOfType<JTree>().assertProperty(
            listOf("root", "opened", "leaf", "sibling"),
            "the node the user opened shows its child as a row",
        ) { rowLabels() }

        root = Folder("root", listOf(Folder("added")) + root.children)
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            listOf("root", "added", "opened", "leaf", "sibling"),
            "the added node takes a row of its own and pushes the rows below it down",
        ) { rowLabels() }
        onNodeOfType<JTree>().assertProperty(
            true,
            "the node the user opened is open where the structure put it",
        ) { isExpanded(getPathForRow(2)) }
        onNodeOfType<JTree>().assertProperty(
            false,
            "and the added node arrives closed",
        ) { isExpanded(getPathForRow(1)) }
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
        onNodeOfType<JTree>().assertProperty(
            listOf(1),
            "the row the user selected is selected",
        ) { selectionRows?.toList() }
        received.clear()

        root = Folder("root", listOf(Folder("added")) + root.children)
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            listOf("opened"),
            "the selected node is the one the data still has",
        ) { selectedLabels() }
        onNodeOfType<JTree>().assertProperty(
            listOf(2),
            "and it is selected on the row the added node pushed it down to",
        ) { selectionRows?.toList() }
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

        onNodeOfType<JTree>().assertProperty(
            true,
            "the node whose children changed stays open",
        ) { isExpanded(getPathForRow(1)) }
        onNodeOfType<JTree>().assertProperty(
            listOf("root", "opened", "leaf", "late", "sibling"),
            "and the child it gained shows as a row under it",
        ) { rowLabels() }
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

        onNodeOfType<JTree>().assertProperty(
            listOf("root", "sibling"),
            "the node the data dropped leaves the rows",
        ) { rowLabels() }
        onNodeOfType<JTree>().assertProperty(
            listOf(1),
            "the node the new structure still has stays selected",
        ) { selectionRows?.toList() }
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

        val tree = onNodeOfType<JTree>()
        tree.assertProperty(listOf("root", "opened", "sibling"), "each row reads what the label says") { rowLabels() }

        suffix = " *"
        awaitIdle()

        tree.assertProperty(
            listOf("root *", "opened *", "sibling *"),
            "every row follows the label accessor even though no value object moved",
        ) { rowLabels() }
    }

    @Test
    fun aRelabeledNodeShowsItsNewText() = runComposeSwingTest {
        var root by mutableStateOf(start)
        setContent { Tree(root = root, children = { it.children }, label = { it.name }) }

        val tree = onNodeOfType<JTree>().fetch()
        tree.expandPath(tree.pathTo(0))
        val measured = tree.getPathBounds(tree.pathTo(0))?.width

        // A label far wider than the one it replaces, so a row measured again is a row of another width.
        val longerName = "opened, under a name several times as wide as the one before it"
        root = Folder("root", listOf(Folder(longerName, opened.children), root.children[1]))
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            listOf("root", longerName, "leaf", "sibling"),
            "the row reads what the data says",
        ) { rowLabels() }
        onNodeOfType<JTree>().assertProperty(
            true,
            "the tree measured that row again, so the width it draws is the new text's",
        ) { getPathBounds(pathTo(0))!!.width > measured!! }
        onNodeOfType<JTree>().assertProperty(
            true,
            "and the node it renamed is the node that was open",
        ) { isExpanded(pathTo(0)) }
    }
}
