package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import java.awt.Component
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

/**
 * The receiver a [Tree] node composes against: the row inputs the tree hands a `TreeCellRenderer` for
 * the node being stamped, exposed as read-only composition state so the node can lay itself out by
 * position, selection, expansion and focus.
 *
 * Mirrors the arguments of
 * [javax.swing.tree.TreeCellRenderer.getTreeCellRendererComponent]: [row] is the node's row, [isSelected]
 * whether it is selected, [isExpanded] whether its children are shown below it, [isLeaf] whether the
 * structure gives it children at all, and [hasFocus] whether it draws the focus decoration.
 *
 * @see javax.swing.tree.TreeCellRenderer.getTreeCellRendererComponent
 */
public sealed interface TreeNodeScope {
    /** The row the node being rendered occupies, counting the tree's displayed rows from the top. */
    public val row: Int

    /** Whether the node being rendered is selected. */
    public val isSelected: Boolean

    /** Whether the node being rendered is expanded, so its children are displayed below it. */
    public val isExpanded: Boolean

    /** Whether the node being rendered is a leaf - one the structure gives no children. */
    public val isLeaf: Boolean

    /** Whether the node being rendered currently draws the focus decoration. */
    public val hasFocus: Boolean
}

/**
 * The user object every node a value-driven [Tree] builds carries: the value the node stands for,
 * alongside the text its label renders for it.
 *
 * `toString` is that text, which is what a tree rendering nodes through the renderer it carries shows,
 * and what it reads out as the node's accessible text; a composable node reaches the value itself.
 */
internal class TreeNodeValue<T>(
    val value: T,
    private val text: String,
) {
    override fun toString(): String = text
}

/**
 * A [TreeCellRenderer] that paints each node through a real `@Composable` body, over the reused
 * [CellStampIsland] every such renderer stamps through.
 *
 * The component the node composes is what the tree is handed. The tree bounds it at the row it is
 * painting and lays it out there, and its preferred size is what the tree measures the row by - as long
 * as the tree asks, which it does for a `rowHeight` of `0`; a fixed row height is applied whatever the
 * node composes.
 *
 * The [currentNodeContent] is read through a [State] so a recomposition that supplies a fresh node
 * body is honoured without rebuilding the renderer or its island.
 *
 * @param parentContext the enclosing composition this renderer's node island joins.
 * @param currentNodeContent the always-current composable node body, invoked with the [TreeNodeScope]
 *   and the value the node stands for.
 */
internal class ComposingTreeCellRenderer<T>(
    parentContext: CompositionContext,
    private val currentNodeContent: State<@Composable TreeNodeScope.(value: T) -> Unit>,
) : TreeCellRenderer {
    // The row inputs, held as composition state so writing them invalidates the node body that reads
    // them. A single reused node body (null before the first stamp) keeps the size-1 pool the
    // rubber-stamp model expects.
    private val valueState = mutableStateOf<T?>(null)
    private var currentValue by valueState
    private val scope = MutableTreeNodeScope()

    private val island =
        CellStampIsland(
            parentContext,
            "A composable node renders a single component, and this one composes several. Compose them " +
                "into one container - a panel whose layout arranges them - and the tree renders that.",
        ) {
            TreeNodeCell(valueState, scope, currentNodeContent)
        }

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ): Component =
        island.stamp {
            currentValue = valueOf(value)
            scope.row = row
            scope.isSelected = selected
            scope.isExpanded = expanded
            scope.isLeaf = leaf
            scope.hasFocus = hasFocus
        }

    /** Disposes this renderer's node island; see [CellStampIsland.dispose]. */
    fun dispose(): Unit = island.dispose()

    /**
     * The value [node] stands for, or `null` for a node that carries none - the empty node a
     * composition holding nothing composes is what such a row is stamped with.
     */
    private fun valueOf(node: Any?): T? {
        val carried = (node as? DefaultMutableTreeNode)?.userObject
        if (carried !is TreeNodeValue<*>) return null
        // This renderer is installed by a value-driven Tree alone, and every node such a Tree builds
        // carries a TreeNodeValue holding a value of that Tree's own element type.
        @Suppress("UNCHECKED_CAST")
        val value = carried.value as T
        return value
    }
}

/**
 * The node body a [ComposingTreeCellRenderer]'s island composes. A `null` value is the degenerate empty
 * node before the first stamp, which composes no component at all.
 */
@Composable
private fun <T> TreeNodeCell(
    valueState: State<T?>,
    scope: TreeNodeScope,
    nodeContent: State<@Composable TreeNodeScope.(value: T) -> Unit>,
) {
    val value = valueState.value
    if (value != null) {
        scope.(nodeContent.value)(value)
    }
}

/** The mutable backing of [TreeNodeScope]; its fields are written once per stamp. */
private class MutableTreeNodeScope : TreeNodeScope {
    override var row: Int by mutableStateOf(-1)
    override var isSelected: Boolean by mutableStateOf(false)
    override var isExpanded: Boolean by mutableStateOf(false)
    override var isLeaf: Boolean by mutableStateOf(false)
    override var hasFocus: Boolean by mutableStateOf(false)
}

/**
 * Remembers a single [ComposingTreeCellRenderer] for [nodeContent], captured against the enclosing
 * composition so the node body joins it. The renderer is stable across recompositions - the current
 * [nodeContent] flows in through [rememberUpdatedState], so a recomposed node body is honoured without
 * rebuilding the renderer - and is disposed when it leaves the composition.
 *
 * Call from a `@Composable` scope that installs the returned renderer on a `JTree`.
 */
@Composable
internal fun <T> rememberComposingTreeCellRenderer(
    nodeContent:
        @Composable TreeNodeScope.(value: T) -> Unit,
): ComposingTreeCellRenderer<T> {
    val parentContext = rememberCompositionContext()
    val current = rememberUpdatedState(nodeContent)
    val renderer = remember(parentContext) { ComposingTreeCellRenderer(parentContext, current) }
    DisposableEffect(renderer) {
        onDispose { renderer.dispose() }
    }
    return renderer
}
