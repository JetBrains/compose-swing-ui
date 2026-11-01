package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import kotlin.test.Test

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
        onNodeOfType<JTree>().assertProperty(
            true,
            "a value the data gives no children is a leaf",
        ) { model.isLeaf(pathTo(0).lastPathComponent) }

        tree.expandPath(tree.pathTo(0))
        onNodeOfType<JTree>().assertProperty(false, "and a leaf has nothing to open") { isExpanded(pathTo(0)) }
    }

    @Test
    fun aValueThatReportsChildrenIsABranchBeforeItHasAny() = runComposeSwingTest {
        setContent {
            Tree(root = sample, children = { it.children }, label = { it.name }, hasChildren = { it.directory })
        }

        val tree = onNodeOfType<JTree>().fetch()
        onNodeOfType<JTree>().assertProperty(
            false,
            "a value that reports children is a branch with none loaded",
        ) { model.isLeaf(pathTo(0).lastPathComponent) }

        tree.expandPath(tree.pathTo(0))
        onNodeOfType<JTree>().assertProperty(true, "which is what lets the user ask for them") { isExpanded(pathTo(0)) }
    }

    /**
     * The branch answer says nothing about a value the data gives children to: such a value is a branch
     * whatever the answer is, so a node built as a leaf has to widen to take the children it gains and
     * narrow again once they are gone.
     */
    @Test
    fun aLeafThatGainsChildrenShowsThemAndIsALeafAgainWhenTheyGo() = runComposeSwingTest {
        var note by mutableStateOf(FileEntry("note"))
        setContent {
            Tree(
                root = FileEntry("root", listOf(note)),
                children = { it.children },
                label = { it.name },
                hasChildren = { it.directory },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        onNodeOfType<JTree>().assertProperty(
            true,
            "a value the branch answer denies and the data gives no children is a leaf",
        ) { model.isLeaf(pathTo(0).lastPathComponent) }

        note = FileEntry("note", listOf(FileEntry("line")))
        awaitIdle()
        tree.expandPath(tree.pathTo(0))

        onNodeOfType<JTree>().assertProperty(
            false,
            "a value the data gives children is a branch however the branch answer answers for it",
        ) { model.isLeaf(pathTo(0).lastPathComponent) }
        onNodeOfType<JTree>().assertProperty(
            listOf("root", "note", "line"),
            "and the child it gained shows as a row under it",
        ) { rowLabels() }

        note = FileEntry("note")
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            listOf("root", "note"),
            "the child the data dropped leaves the rows",
        ) { rowLabels() }
        onNodeOfType<JTree>().assertProperty(
            true,
            "and the node it left is a leaf again",
        ) { model.isLeaf(pathTo(0).lastPathComponent) }
    }

    /**
     * Whether a node answers for its own leafness is settled when it is built, so content that withdraws
     * the branch answer - or makes one - asks for nodes of another shape than the ones standing.
     */
    @Test
    fun aTreeThatWithdrawsItsBranchAnswerGetsNodesOfTheRightShape() = runComposeSwingTest {
        var answersForBranches by mutableStateOf(true)
        setContent {
            val branchAnswer: ((FileEntry) -> Boolean)? = if (answersForBranches) ({ it.directory }) else null
            Tree(root = sample, children = { it.children }, label = { it.name }, hasChildren = branchAnswer)
        }

        val tree = onNodeOfType<JTree>().fetch()
        onNodeOfType<JTree>().assertProperty(
            false,
            "a childless directory is a branch while the caller answers for branches",
        ) { model.isLeaf(pathTo(0).lastPathComponent) }

        answersForBranches = false
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            true,
            "with the answer withdrawn a value the data gives no children is a leaf",
        ) { model.isLeaf(pathTo(0).lastPathComponent) }

        tree.expandPath(tree.pathTo(0))
        onNodeOfType<JTree>().assertProperty(false, "and a leaf has nothing to open") { isExpanded(pathTo(0)) }
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
        onNodeOfType<JTree>().assertProperty(0, "nothing is read yet") {
            model.getChildCount(pathTo(0).lastPathComponent)
        }

        tree.expandPath(tree.pathTo(0))
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            1,
            "the children the expansion read are under the node",
        ) { model.getChildCount(pathTo(0).lastPathComponent) }
        onNodeOfType<JTree>().assertProperty(
            "beach.png",
            "and each renders its own label",
        ) { model.getChild(pathTo(0).lastPathComponent, 0).toString() }
    }
}
