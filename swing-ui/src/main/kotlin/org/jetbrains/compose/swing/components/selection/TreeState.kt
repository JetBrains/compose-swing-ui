@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.binding
import javax.swing.JTree

/**
 * A hoistable state holder for what a [Tree] has selected and what it has open, carrying the gesture that
 * brings one of its nodes into view.
 *
 * Each path is the chain of child indices from the root, so `[]` is the root, `[0]` its first child, and
 * `[0, 2]` that child's third child.
 *
 * [selectedPaths] and [expandedPaths] are two-way: assigning either applies it to the tree, and the user
 * selecting a node or opening one writes the change back here. Both are snapshot-observable, so reading
 * one inside a composable (or a `snapshotFlow` collector) subscribes to the user's later changes as well.
 *
 * What this state names is the composition's own and is re-applied on every pass: a tree driven by a
 * state stands on exactly the nodes the state holds, so a state starting on the empty expansion opens
 * nothing - start it on `setOf(emptyList())` for a tree that opens on its root. A node the structure does
 * not have is left out of the tree while it goes on being named here - a structure that has it again
 * shows it selected, or open.
 *
 * [revealPath] brings one node into view when the application decides to - a search hit, a node a load
 * has just filled in:
 *
 * ```
 * val state = rememberTreeState(initialExpandedPaths = setOf(emptyList()))
 *
 * Button("Find", onClick = { state.revealPath(search(query)) })
 * ScrollPane {
 *     Tree(root = root, children = ::childrenOf, state = state, modifier = SwingModifier.viewport())
 * }
 * ```
 *
 * [rowCount], [isExpanded] and [shownSelectedPaths] answer for the tree instead of for what this state
 * holds. Each reads the bound tree where it is called, so what it reports is what the tree stands on -
 * which is not always what was declared, since a structure can drop a node and a closed node cannot show
 * its descendants selected. They are not snapshot state, so reading one subscribes to nothing; a composable
 * that has to follow the user reads [selectedPaths] and [expandedPaths]. An unbound state has no tree to
 * answer for and reports no rows, nothing open and nothing selected.
 *
 * A state drives at most one tree: passing it to a second one moves it there and leaves the first
 * unbound.
 *
 * @param initialSelectedPaths the nodes selected until the caller or the user moves the selection.
 * @param initialExpandedPaths the nodes open until the caller or the user opens or closes one.
 * @see javax.swing.JTree
 */
@Stable
public class TreeState
    @RememberInComposition
    constructor(
        initialSelectedPaths: Set<List<Int>> = emptySet(),
        initialExpandedPaths: Set<List<Int>> = emptySet(),
    ) {
        /**
         * The selected nodes as index paths from the root.
         *
         * @see javax.swing.JTree.setSelectionPaths
         */
        public var selectedPaths: Set<List<Int>> by mutableStateOf(initialSelectedPaths)

        /**
         * The open nodes as index paths from the root; every other node is collapsed, except an ancestor of
         * an open one, which stays open so that node is reachable.
         *
         * @see javax.swing.JTree.expandPath
         */
        public var expandedPaths: Set<List<Int>> by mutableStateOf(initialExpandedPaths)

        // The tree this state drives, or null when unbound. Only the binding modifier node writes it,
        // whose lifecycle owns the relationship.
        private var target: JTree? = null

        /**
         * How many rows the tree shows: every node whose ancestors are all open, and the root itself while
         * it is shown. `0` while no tree is bound.
         *
         * @see javax.swing.JTree.getRowCount
         */
        public val rowCount: Int get() = target?.rowCount ?: 0

        /**
         * The nodes the tree has selected, as index paths from the root. Empty while no tree is bound.
         *
         * @see javax.swing.JTree.getSelectionPaths
         */
        public val shownSelectedPaths: Set<List<Int>>
            get() {
                val tree = target ?: return emptySet()
                return readSelection(tree, tree.model)
            }

        /**
         * Whether the tree shows the children of the node [path] names below it. `false` for a node no
         * structure the tree currently shows has, for a node under a closed one, and while no tree is bound.
         *
         * @see javax.swing.JTree.isExpanded
         */
        public fun isExpanded(path: List<Int>): Boolean {
            val tree = target ?: return false
            return resolvePath(tree.model, path)?.let(tree::isExpanded) == true
        }

        /**
         * Brings the node [path] names into view, opening every ancestor that hides it, and returns whether
         * it was reached.
         *
         * Revealing is a gesture rather than a declaration: it scrolls where it is called and leaves nothing
         * behind, so no later pass scrolls back and where the user scrolls afterwards stands. The ancestors
         * it opens are a change to what the tree shows, and arrive in [expandedPaths] like the user's own.
         *
         * A node is revealed once the tree holds it, which is what an effect keyed on the data runs after:
         * the structure a click declares reaches the tree on the composition that click triggers.
         *
         * `false` means nothing was revealed: no tree is bound, or the structure the tree currently shows has
         * no such node. `true` means the tree was asked to show it, which scrolls the pane the tree is in;
         * a tree in no scroll pane has nowhere to scroll and is left with the node's ancestors open.
         *
         * @see javax.swing.JTree.scrollPathToVisible
         */
        public fun revealPath(path: List<Int>): Boolean {
            val tree = target ?: return false
            val resolved = resolvePath(tree.model, path)
            resolved?.let(tree::scrollPathToVisible)
            return resolved != null
        }

        internal fun bind(tree: JTree) {
            target = tree
        }

        internal fun unbind(tree: JTree) {
            if (target === tree) target = null
        }
    }

/**
 * Creates and remembers a [TreeState] starting on [initialSelectedPaths] and [initialExpandedPaths].
 *
 * A later change to either neither recreates the state nor moves the tree; select and open afterwards
 * through the returned state's [TreeState.selectedPaths] and [TreeState.expandedPaths].
 *
 * @param initialSelectedPaths the nodes selected until the caller or the user moves the selection.
 * @param initialExpandedPaths the nodes open until the caller or the user opens or closes one.
 */
@Composable
public fun rememberTreeState(
    initialSelectedPaths: Set<List<Int>> = emptySet(),
    initialExpandedPaths: Set<List<Int>> = emptySet(),
): TreeState = remember { TreeState(initialSelectedPaths, initialExpandedPaths) }

/** Binds [state] to the composable's tree through the modifier chain; see [binding]. */
internal fun SwingModifier.treeStateBinding(state: TreeState): SwingModifier =
    binding(JTree::class.java, state, TreeState::bind, TreeState::unbind)
