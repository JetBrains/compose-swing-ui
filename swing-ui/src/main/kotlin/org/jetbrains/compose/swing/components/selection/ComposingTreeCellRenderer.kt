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
import org.jetbrains.annotations.Nls
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
    value: T,
    text: @Nls String,
) {
    /** The value the node stands for. */
    var value: T = value
        private set

    private var text: @Nls String = text

    override fun toString(): String = text

    /**
     * Takes over [value] and the [text] rendered for it. The node holding this goes on holding it, which is
     * what keeps the row, the expansion and the selection reaching that node where they are.
     */
    fun carry(
        value: T,
        text: @Nls String,
    ) {
        this.value = value
        this.text = text
    }
}

/**
 * A [TreeCellRenderer] that paints each node through a real `@Composable` body, over the reused
 * [CellStampComposition] every such renderer stamps through.
 *
 * The component the node composes is what the tree is handed. The tree bounds it at the row it is
 * painting and lays it out there, and its preferred size is what the tree measures the row by - as long
 * as the tree asks, which it does for a `rowHeight` of `0`; a fixed row height is applied whatever the
 * node composes.
 *
 * The [currentNodeContent] is read through a [State] so a recomposition that supplies a fresh node
 * body is honored without rebuilding the renderer or its node composition.
 *
 * @param parentContext the enclosing composition this renderer's node composition joins.
 * @param currentNodeContent the always-current composable node body, invoked with the [TreeNodeScope]
 *   and the value the node stands for.
 */
internal class ComposingTreeCellRenderer<T>(
    parentContext: CompositionContext,
    private val currentNodeContent: State<@Composable TreeNodeScope.(value: T) -> Unit>,
) : TreeCellRenderer {
    // A single reused node body (null before the first stamp) keeps the size-1 pool the rubber-stamp
    // model expects.
    private val valueState = mutableStateOf<T?>(null)
    private var currentValue by valueState
    private val scope = MutableTreeNodeScope()

    private val cellComposition =
        CellStampComposition(
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
    ): Component {
        // A node the tree stamps carries the TreeNodeValue wrapper; the value it holds is the node's own
        // to be `null` or not. The wrapper's presence is what names a node, never the value's nullity.
        val carried = (value as? DefaultMutableTreeNode)?.userObject as? TreeNodeValue<*>
        return cellComposition.stamp(hasCell = carried != null) {
            currentValue = valueOf(carried)
            scope.row = row
            scope.isSelected = selected
            scope.isExpanded = expanded
            scope.isLeaf = leaf
            scope.hasFocus = hasFocus
        }
    }

    /** Disposes this renderer's node composition; see [CellStampComposition.dispose]. */
    fun dispose(): Unit = cellComposition.dispose()

    /**
     * The value [carried] holds. This renderer is installed by a value-driven Tree alone, and every node
     * such a Tree builds carries a TreeNodeValue holding a value of that Tree's own element type.
     */
    private fun valueOf(carried: TreeNodeValue<*>?): T? {
        @Suppress("UNCHECKED_CAST")
        return carried?.value as T?
    }
}

/**
 * The node body a [ComposingTreeCellRenderer] stamps. The node composition composes it only where the
 * stamp names a node, so [valueState] always holds that node's value here - itself `null` among the
 * values a node can hold.
 */
@Composable
private fun <T> TreeNodeCell(
    valueState: State<T?>,
    scope: TreeNodeScope,
    nodeContent: State<@Composable TreeNodeScope.(value: T) -> Unit>,
) {
    @Suppress("UNCHECKED_CAST")
    val value = valueState.value as T
    scope.(nodeContent.value)(value)
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
 * [nodeContent] flows in through [rememberUpdatedState], so a recomposed node body is honored without
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
