@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.constants.TreeSelectionMode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.rememberMirrorState
import org.jetbrains.compose.swing.node.settleWhenDue
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * A composable wrapper for `JTree`.
 *
 * The tree is described as data: [root] is the root value and [children] yields each value's child values,
 * walked recursively to build the displayed structure; [label] renders each value's row text (its
 * `toString` by default), and [nodeContent] renders a value's node as a composable of its own where a row
 * is more than text. The structure reflects the last composition - changing the data the accessors return
 * moves the tree on recompose. A tree follows the values its accessors answer with, so a value that
 * recomposes the tree is what moves it: mutating in place a child list a value already handed over leaves
 * the tree as it was until something else recomposes it. The child values a list hands over unchanged at
 * its front and at its back keep the nodes they had, staying open if they were open and selected if they
 * were selected while the rows around them shift; what lies between them settles by position, so there
 * openness and selection stay with the place rather than following the value. Selection is declared with
 * [selectedPaths] and expansion with [expandedPaths], each path expressed as the chain of child indices
 * from the root (so `[]` is the root, `[0]` its first child, `[0, 2]` that child's third child), and the
 * user's changes to either arrive through [onSelectionChange] and [onExpansionChange]. Place it in a
 * [org.jetbrains.compose.swing.components.layout.ScrollPane] to scroll.
 *
 * ```
 * ScrollPane {
 *     content {
 *         Tree(
 *             root = fileSystem,
 *             children = { it.entries },
 *             label = { it.name },
 *             selectedPaths = selection,
 *             onSelectionChange = { selection = it },
 *             expandedPaths = expansion,
 *             onExpansionChange = { expansion = it },
 *         )
 *     }
 * }
 * ```
 *
 * [onSelectionChange] and [onExpansionChange] report the user's changes only; new data reaching the
 * structure produces neither. A declared selection or expansion is the composition's state and is
 * re-applied on every pass: it survives a new structure, and a user change the caller does not adopt does
 * not stand. Undeclared, either belongs to the user alone - the library never imposes one, and what the
 * user reached survives a new structure as well, the nodes they closed as much as the ones they opened. A
 * node the new structure no longer has is the exception: it leaves the selection, and [onSelectionChange]
 * reports what is left of it.
 *
 * A selection and an expansion that cannot both stand settle the way a `JTree` settles them: a closed node
 * shows none of its descendants, so it holds the selection in place of the ones it hides, and
 * [onSelectionChange] reports what the tree was left with.
 *
 * [hasChildren] decides which values are branches. A value [children] yields nothing for is a leaf, with
 * no handle to click; declaring [hasChildren] lets such a value call itself a branch all the same, so the
 * user can ask for children the data does not hold yet and [onWillExpand] - or [onExpansionChange] - is
 * where fetching them starts. [onWillExpand] also decides whether a node opens at all: returning `false`
 * leaves it closed.
 *
 * Editing is a report, never a mutation. While [isEditable] is on the user can edit a node's text in
 * place, and committing it hands [onNodeEdit] the value edited, its index path, and what was entered; the
 * row goes on showing what the data says until a later composition supplies data that says otherwise, and
 * the tree [root] and [children] describe is never written to.
 *
 * @param root the root value of the tree
 * @param children yields the child values of a value, in display order
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param label renders a value's row text
 * @param hasChildren whether a value is a branch, asked for a value [children] yields none for; a value
 *   with children is a branch either way. `null` - the default - makes a childless value a leaf
 * @param selectedPaths the selected nodes as index paths from the root; `null` - the default - leaves the
 *   selection to the user
 * @param onSelectionChange callback invoked when the user changes the selection
 * @param expandedPaths the expanded nodes as index paths from the root; every other node is collapsed,
 *   except an ancestor of an expanded one, which stays expanded so that node is reachable. `null` - the
 *   default - leaves expansion to the tree and to the user
 * @param onExpansionChange callback invoked when the user expands or collapses a node, receiving every
 *   node that is then expanded
 * @param onWillExpand asked before a node opens - whether the user opened it or a declared expansion did
 *   - with the value and the index path of that node, and vetoes the expansion by returning `false`;
 *   `null` - the default - lets every expansion through
 * @param isEditable whether the user can edit a node's text in place
 * @param onNodeEdit callback invoked when an edit is committed, receiving the value edited, its index
 *   path, and the value entered; update the backing data from here so the next composition shows the edit
 * @param selectionMode how many nodes may be selected
 * @param rootVisible whether the root node is shown
 * @param showsRootHandles whether expand/collapse handles are shown for the top-level nodes;
 *   `null` leaves the choice to the installed look and feel
 * @param rowHeight the height of every row in pixels; `0` asks each node's rendering how tall it wants
 *   to be, which is what lets a composable node size itself. `null` - the default - leaves the height to
 *   the installed look and feel
 * @param visibleRowCount preferred number of visible rows (`JTree.setVisibleRowCount`)
 * @param toggleClickCount how many clicks on a node expand or collapse it; `0` for neither
 * @param nodeContent optional composable node rendered per row against a [TreeNodeScope]; `null` - the
 *   default - renders each node's [label] through the renderer the tree carries
 * @see javax.swing.JTree
 */
@Composable
public fun <T> Tree(
    root: T,
    children: (T) -> List<T>,
    modifier: SwingModifier = SwingModifier,
    label: (T) -> @Nls String = { it.toString() },
    hasChildren: ((T) -> Boolean)? = null,
    selectedPaths: Set<List<Int>>? = null,
    onSelectionChange: (Set<List<Int>>) -> Unit = {},
    expandedPaths: Set<List<Int>>? = null,
    onExpansionChange: (Set<List<Int>>) -> Unit = {},
    onWillExpand: ((value: T, path: List<Int>) -> Boolean)? = null,
    isEditable: Boolean = false,
    onNodeEdit: (value: T, path: List<Int>, newValue: Any?) -> Unit = { _, _, _ -> },
    @TreeSelectionMode selectionMode: Int = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
    rootVisible: Boolean = true,
    showsRootHandles: Boolean? = null,
    rowHeight: Int? = null,
    visibleRowCount: Int = 20,
    toggleClickCount: Int = 2,
    nodeContent: (@Composable TreeNodeScope.(value: T) -> Unit)? = null,
) {
    Tree(
        root = root,
        children = children,
        treeSelectionListener = rememberSelectionListener(onSelectionChange),
        modifier = modifier,
        label = label,
        hasChildren = hasChildren,
        selectedPaths = selectedPaths,
        expandedPaths = expandedPaths,
        treeExpansionListener = rememberExpansionListener(onExpansionChange),
        treeWillExpandListener = rememberWillExpandListener(onWillExpand),
        isEditable = isEditable,
        onNodeEdit = onNodeEdit,
        selectionMode = selectionMode,
        rootVisible = rootVisible,
        showsRootHandles = showsRootHandles,
        rowHeight = rowHeight,
        visibleRowCount = visibleRowCount,
        toggleClickCount = toggleClickCount,
        nodeContent = nodeContent,
    )
}

/**
 * A [Tree] driven by raw listeners instead of the `onSelectionChange`/`onExpansionChange`/`onWillExpand`
 * lambdas. A listener is notified of the user's changes only - the selection listener also of a selection
 * a new structure took away from the user - and is removed on the same instance; pass a stable instance
 * (e.g. `remember {}`) to avoid churn. The will-expand listener hears more than the user: it is announced
 * every expansion and every collapse, the ones a declaration applies as much as the ones the user asks
 * for, and vetoes the one it refuses by throwing an `ExpandVetoException`.
 *
 * @param root the root value of the tree
 * @param children yields the child values of a value, in display order
 * @param treeSelectionListener the listener notified of the user's selection changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param label renders a value's row text
 * @param hasChildren whether a value is a branch, asked for a value [children] yields none for; a value
 *   with children is a branch either way. `null` - the default - makes a childless value a leaf
 * @param selectedPaths the selected nodes as index paths from the root; `null` - the default - leaves the
 *   selection to the user
 * @param expandedPaths the expanded nodes as index paths from the root; every other node is collapsed,
 *   except an ancestor of an expanded one, which stays expanded so that node is reachable. `null` - the
 *   default - leaves expansion to the tree and to the user
 * @param treeExpansionListener the listener notified of the user's expansions and collapses; `null`
 *   installs none
 * @param treeWillExpandListener the listener announced each expansion and collapse before it happens;
 *   `null` installs none
 * @param isEditable whether the user can edit a node's text in place
 * @param onNodeEdit callback invoked when an edit is committed, receiving the value edited, its index
 *   path, and the value entered; update the backing data from here so the next composition shows the edit
 * @param selectionMode how many nodes may be selected
 * @param rootVisible whether the root node is shown
 * @param showsRootHandles whether expand/collapse handles are shown for the top-level nodes;
 *   `null` leaves the choice to the installed look and feel
 * @param rowHeight the height of every row in pixels; `0` asks each node's rendering how tall it wants
 *   to be, which is what lets a composable node size itself. `null` - the default - leaves the height to
 *   the installed look and feel
 * @param visibleRowCount preferred number of visible rows (`JTree.setVisibleRowCount`)
 * @param toggleClickCount how many clicks on a node expand or collapse it; `0` for neither
 * @param nodeContent optional composable node rendered per row against a [TreeNodeScope]; `null` - the
 *   default - renders each node's [label] through the renderer the tree carries
 * @see javax.swing.JTree
 */
@Composable
public fun <T> Tree(
    root: T,
    children: (T) -> List<T>,
    treeSelectionListener: TreeSelectionListener,
    modifier: SwingModifier = SwingModifier,
    label: (T) -> @Nls String = { it.toString() },
    hasChildren: ((T) -> Boolean)? = null,
    selectedPaths: Set<List<Int>>? = null,
    expandedPaths: Set<List<Int>>? = null,
    treeExpansionListener: TreeExpansionListener? = null,
    treeWillExpandListener: TreeWillExpandListener? = null,
    isEditable: Boolean = false,
    onNodeEdit: (value: T, path: List<Int>, newValue: Any?) -> Unit = { _, _, _ -> },
    @TreeSelectionMode selectionMode: Int = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
    rootVisible: Boolean = true,
    showsRootHandles: Boolean? = null,
    rowHeight: Int? = null,
    visibleRowCount: Int = 20,
    toggleClickCount: Int = 2,
    nodeContent: (@Composable TreeNodeScope.(value: T) -> Unit)? = null,
) {
    // The single conversion from nodeContent to a JTree cell renderer: one reused
    // ComposingTreeCellRenderer stamps a recycled composition per node. A null nodeContent renders the
    // nodes through the renderer the tree carries.
    val nodeRenderer = nodeContent?.let { rememberComposingTreeCellRenderer(it) }
    // The model carries the edit callback through a State, so a node reports to the callback this
    // composition last declared rather than to the one in force when the node was built.
    val currentNodeEdit = rememberUpdatedState(onNodeEdit)
    // The model this composable built, kept so a later structure reaches the nodes standing in the tree
    // instead of replacing them. It is answered against the model the tree carries, so a node handed a tree
    // built elsewhere - or one whose composable state has been rebuilt - starts from a model of its own.
    val builtModel = remember { arrayOfNulls<DeclaredTreeModel<T>>(1) }
    TreeNode(
        treeSelectionListener = treeSelectionListener,
        modifier = modifier,
        selectedPaths = selectedPaths,
        expandedPaths = expandedPaths,
        treeExpansionListener = treeExpansionListener,
        treeWillExpandListener = treeWillExpandListener,
        isEditable = isEditable,
        selectionMode = selectionMode,
        rootVisible = rootVisible,
        showsRootHandles = showsRootHandles,
        rowHeight = rowHeight,
        visibleRowCount = visibleRowCount,
        toggleClickCount = toggleClickCount,
        nodeRenderer = nodeRenderer,
    ) { mirrors ->
        set(TreeContent(root, children, label, hasChildren)) { content ->
            val declarations =
                TreeDeclarations(
                    mirrors = mirrors,
                    declaredSelection = selectedPaths,
                    declaredExpansion = expandedPaths,
                    target = treeSelectionListener,
                )
            val standing = builtModel[0]?.takeIf { it === model && it.accepts(content) }
            if (standing != null) {
                updateContent(declarations, standing, content)
            } else {
                val built = content.toModel(currentNodeEdit)
                builtModel[0] = built
                installModel(declarations, built)
            }
        }
    }
}

/**
 * A composable wrapper for `JTree` driven by a caller-owned [TreeModel].
 *
 * The [model] is displayed as-is: its own nodes and structure drive the tree, and the library never
 * mutates it. Supplying a new [model] instance swaps it into the tree on recomposition. Selection is
 * declared with [selectedPaths] and expansion with [expandedPaths], each path expressed as the chain of
 * child indices from the root (so `[]` is the root, `[0]` its first child, `[0, 2]` that child's third
 * child); the indices are resolved through the model's own accessors, so any [TreeModel] works. Both
 * survive a model swap, declared or not. Place it in a
 * [org.jetbrains.compose.swing.components.layout.ScrollPane] to scroll.
 *
 * ```
 * ScrollPane {
 *     content {
 *         Tree(
 *             model = fileSystemModel,
 *             selectedPaths = selection,
 *             onSelectionChange = { selection = it },
 *         )
 *     }
 * }
 * ```
 *
 * [onSelectionChange] and [onExpansionChange] report the user's changes only; installing a new [model]
 * produces neither. A declared selection or expansion is the composition's state and is re-applied on
 * every pass, so a user change the caller does not adopt does not stand; undeclared, either belongs to the
 * user alone and is never imposed - the nodes the user closed stay closed across a model swap as surely as
 * the ones they opened stay open. A node the new model does not have is the exception: it leaves the
 * selection, and [onSelectionChange] reports what is left of it.
 *
 * @param model the tree model to display; owned by the caller and never mutated by the library
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedPaths the selected nodes as index paths from the root; `null` - the default - leaves the
 *   selection to the user
 * @param onSelectionChange callback invoked when the user changes the selection
 * @param expandedPaths the expanded nodes as index paths from the root; every other node is collapsed,
 *   except an ancestor of an expanded one, which stays expanded so that node is reachable. `null` - the
 *   default - leaves expansion to the tree and to the user
 * @param onExpansionChange callback invoked when the user expands or collapses a node, receiving every
 *   node that is then expanded
 * @param selectionMode how many nodes may be selected
 * @param rootVisible whether the root node is shown
 * @param showsRootHandles whether expand/collapse handles are shown for the top-level nodes;
 *   `null` leaves the choice to the installed look and feel
 * @param rowHeight the height of every row in pixels; `0` asks each node's rendering how tall it wants
 *   to be. `null` - the default - leaves the height to the installed look and feel
 * @param visibleRowCount preferred number of visible rows (`JTree.setVisibleRowCount`)
 * @param toggleClickCount how many clicks on a node expand or collapse it; `0` for neither
 * @see javax.swing.JTree
 */
@Composable
public fun Tree(
    model: TreeModel,
    modifier: SwingModifier = SwingModifier,
    selectedPaths: Set<List<Int>>? = null,
    onSelectionChange: (Set<List<Int>>) -> Unit = {},
    expandedPaths: Set<List<Int>>? = null,
    onExpansionChange: (Set<List<Int>>) -> Unit = {},
    @TreeSelectionMode selectionMode: Int = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
    rootVisible: Boolean = true,
    showsRootHandles: Boolean? = null,
    rowHeight: Int? = null,
    visibleRowCount: Int = 20,
    toggleClickCount: Int = 2,
) {
    Tree(
        model = model,
        treeSelectionListener = rememberSelectionListener(onSelectionChange),
        modifier = modifier,
        selectedPaths = selectedPaths,
        expandedPaths = expandedPaths,
        treeExpansionListener = rememberExpansionListener(onExpansionChange),
        selectionMode = selectionMode,
        rootVisible = rootVisible,
        showsRootHandles = showsRootHandles,
        rowHeight = rowHeight,
        visibleRowCount = visibleRowCount,
        toggleClickCount = toggleClickCount,
    )
}

/**
 * A model-driven [Tree] driven by raw listeners instead of the `onSelectionChange`/`onExpansionChange`
 * lambdas. A listener is notified of the user's changes only - the selection listener also of a selection
 * a new model took away from the user - and is removed on the same instance; pass a stable instance (e.g.
 * `remember {}`) to avoid churn.
 *
 * The [model] is displayed as-is and never mutated by the library; a selection and an expansion survive a
 * model swap, declared or not.
 *
 * @param model the tree model to display; owned by the caller and never mutated by the library
 * @param treeSelectionListener the listener notified of the user's selection changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedPaths the selected nodes as index paths from the root; `null` - the default - leaves the
 *   selection to the user
 * @param expandedPaths the expanded nodes as index paths from the root; every other node is collapsed,
 *   except an ancestor of an expanded one, which stays expanded so that node is reachable. `null` - the
 *   default - leaves expansion to the tree and to the user
 * @param treeExpansionListener the listener notified of the user's expansions and collapses; `null`
 *   installs none
 * @param selectionMode how many nodes may be selected
 * @param rootVisible whether the root node is shown
 * @param showsRootHandles whether expand/collapse handles are shown for the top-level nodes;
 *   `null` leaves the choice to the installed look and feel
 * @param rowHeight the height of every row in pixels; `0` asks each node's rendering how tall it wants
 *   to be. `null` - the default - leaves the height to the installed look and feel
 * @param visibleRowCount preferred number of visible rows (`JTree.setVisibleRowCount`)
 * @param toggleClickCount how many clicks on a node expand or collapse it; `0` for neither
 * @see javax.swing.JTree
 */
@Composable
public fun Tree(
    model: TreeModel,
    treeSelectionListener: TreeSelectionListener,
    modifier: SwingModifier = SwingModifier,
    selectedPaths: Set<List<Int>>? = null,
    expandedPaths: Set<List<Int>>? = null,
    treeExpansionListener: TreeExpansionListener? = null,
    @TreeSelectionMode selectionMode: Int = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
    rootVisible: Boolean = true,
    showsRootHandles: Boolean? = null,
    rowHeight: Int? = null,
    visibleRowCount: Int = 20,
    toggleClickCount: Int = 2,
) {
    TreeNode(
        treeSelectionListener = treeSelectionListener,
        modifier = modifier,
        selectedPaths = selectedPaths,
        expandedPaths = expandedPaths,
        treeExpansionListener = treeExpansionListener,
        treeWillExpandListener = null,
        isEditable = false,
        selectionMode = selectionMode,
        rootVisible = rootVisible,
        showsRootHandles = showsRootHandles,
        rowHeight = rowHeight,
        visibleRowCount = visibleRowCount,
        toggleClickCount = toggleClickCount,
        nodeRenderer = null,
    ) { mirrors ->
        set(model) { newModel ->
            val declarations =
                TreeDeclarations(
                    mirrors = mirrors,
                    declaredSelection = selectedPaths,
                    declaredExpansion = expandedPaths,
                    target = treeSelectionListener,
                )
            installModel(declarations, newModel)
        }
    }
}

/**
 * A [Tree] driven by a [TreeState] instead of declared `selectedPaths`/`expandedPaths` and the
 * `onSelectionChange`/`onExpansionChange` lambdas. The state owns both facets: the nodes it holds are
 * what the tree shows selected and open, the user's own selecting and opening is written back into it,
 * and it is where a node is revealed from.
 *
 * ```
 * val state = rememberTreeState(initialExpandedPaths = setOf(emptyList()))
 *
 * ScrollPane {
 *     Tree(root = fileSystem, children = { it.entries }, state = state, modifier = SwingModifier.viewport())
 * }
 * Label("Selected: ${describe(state.selectedPaths)}")
 * ```
 *
 * @param root the root value of the tree
 * @param children yields the child values of a value, in display order
 * @param state the hoistable selection and expansion state the tree applies and reports into; see
 *   [TreeState]
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param label renders a value's row text
 * @param hasChildren whether a value is a branch, asked for a value [children] yields none for; a value
 *   with children is a branch either way. `null` - the default - makes a childless value a leaf
 * @param onWillExpand asked before a node opens - whether the user opened it or the state's expansion did
 *   - with the value and the index path of that node, and vetoes the expansion by returning `false`;
 *   `null` - the default - lets every expansion through
 * @param isEditable whether the user can edit a node's text in place
 * @param onNodeEdit callback invoked when an edit is committed, receiving the value edited, its index
 *   path, and the value entered; update the backing data from here so the next composition shows the edit
 * @param selectionMode how many nodes may be selected
 * @param rootVisible whether the root node is shown
 * @param showsRootHandles whether expand/collapse handles are shown for the top-level nodes;
 *   `null` leaves the choice to the installed look and feel
 * @param rowHeight the height of every row in pixels; `0` asks each node's rendering how tall it wants
 *   to be, which is what lets a composable node size itself. `null` - the default - leaves the height to
 *   the installed look and feel
 * @param visibleRowCount preferred number of visible rows (`JTree.setVisibleRowCount`)
 * @param toggleClickCount how many clicks on a node expand or collapse it; `0` for neither
 * @param nodeContent optional composable node rendered per row against a [TreeNodeScope]; `null` - the
 *   default - renders each node's [label] through the renderer the tree carries
 * @see javax.swing.JTree
 */
@Composable
public fun <T> Tree(
    root: T,
    children: (T) -> List<T>,
    state: TreeState,
    modifier: SwingModifier = SwingModifier,
    label: (T) -> @Nls String = { it.toString() },
    hasChildren: ((T) -> Boolean)? = null,
    onWillExpand: ((value: T, path: List<Int>) -> Boolean)? = null,
    isEditable: Boolean = false,
    onNodeEdit: (value: T, path: List<Int>, newValue: Any?) -> Unit = { _, _, _ -> },
    @TreeSelectionMode selectionMode: Int = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
    rootVisible: Boolean = true,
    showsRootHandles: Boolean? = null,
    rowHeight: Int? = null,
    visibleRowCount: Int = 20,
    toggleClickCount: Int = 2,
    nodeContent: (@Composable TreeNodeScope.(value: T) -> Unit)? = null,
) {
    Tree(
        root = root,
        children = children,
        modifier = modifier.treeStateBinding(state),
        label = label,
        hasChildren = hasChildren,
        selectedPaths = state.selectedPaths,
        onSelectionChange = { paths -> state.selectedPaths = paths },
        expandedPaths = state.expandedPaths,
        onExpansionChange = { paths -> state.expandedPaths = paths },
        onWillExpand = onWillExpand,
        isEditable = isEditable,
        onNodeEdit = onNodeEdit,
        selectionMode = selectionMode,
        rootVisible = rootVisible,
        showsRootHandles = showsRootHandles,
        rowHeight = rowHeight,
        visibleRowCount = visibleRowCount,
        toggleClickCount = toggleClickCount,
        nodeContent = nodeContent,
    )
}

/**
 * A model-driven [Tree] driven by a [TreeState] instead of declared `selectedPaths`/`expandedPaths` and
 * the `onSelectionChange`/`onExpansionChange` lambdas. The state owns both facets: the nodes it holds are
 * what the tree shows selected and open, the user's own selecting and opening is written back into it,
 * and it is where a node is revealed from.
 *
 * The [model] is displayed as-is and never mutated by the library; the selection and the expansion
 * survive a model swap.
 *
 * @param model the tree model to display; owned by the caller and never mutated by the library
 * @param state the hoistable selection and expansion state the tree applies and reports into; see
 *   [TreeState]
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectionMode how many nodes may be selected
 * @param rootVisible whether the root node is shown
 * @param showsRootHandles whether expand/collapse handles are shown for the top-level nodes;
 *   `null` leaves the choice to the installed look and feel
 * @param rowHeight the height of every row in pixels; `0` asks each node's rendering how tall it wants
 *   to be. `null` - the default - leaves the height to the installed look and feel
 * @param visibleRowCount preferred number of visible rows (`JTree.setVisibleRowCount`)
 * @param toggleClickCount how many clicks on a node expand or collapse it; `0` for neither
 * @see javax.swing.JTree
 */
@Composable
public fun Tree(
    model: TreeModel,
    state: TreeState,
    modifier: SwingModifier = SwingModifier,
    @TreeSelectionMode selectionMode: Int = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
    rootVisible: Boolean = true,
    showsRootHandles: Boolean? = null,
    rowHeight: Int? = null,
    visibleRowCount: Int = 20,
    toggleClickCount: Int = 2,
) {
    Tree(
        model = model,
        modifier = modifier.treeStateBinding(state),
        selectedPaths = state.selectedPaths,
        onSelectionChange = { paths -> state.selectedPaths = paths },
        expandedPaths = state.expandedPaths,
        onExpansionChange = { paths -> state.expandedPaths = paths },
        selectionMode = selectionMode,
        rootVisible = rootVisible,
        showsRootHandles = showsRootHandles,
        rowHeight = rowHeight,
        visibleRowCount = visibleRowCount,
        toggleClickCount = toggleClickCount,
    )
}

/**
 * The `JTree` node every [Tree] overload renders: all of it but the structure, which [installContent]
 * declares - values walked through child accessors in one family of overloads, the caller's own model in
 * the other. [installContent] is handed the tree's [TreeMirrors], since giving the tree a new structure is
 * one of the writes that changes both of the facets it settles.
 */
@Composable
private fun TreeNode(
    treeSelectionListener: TreeSelectionListener,
    modifier: SwingModifier,
    selectedPaths: Set<List<Int>>?,
    expandedPaths: Set<List<Int>>?,
    treeExpansionListener: TreeExpansionListener?,
    treeWillExpandListener: TreeWillExpandListener?,
    isEditable: Boolean,
    @TreeSelectionMode selectionMode: Int,
    rootVisible: Boolean,
    showsRootHandles: Boolean?,
    rowHeight: Int?,
    visibleRowCount: Int,
    toggleClickCount: Int,
    nodeRenderer: ComposingTreeCellRenderer<*>?,
    installContent: SwingNodeUpdater<JTree>.(TreeMirrors) -> Unit,
) {
    val selectionMirror = rememberMirrorState(selectedPaths)
    val expansionMirror = rememberMirrorState(expandedPaths)
    val mirrors = remember(selectionMirror, expansionMirror) { TreeMirrors(selectionMirror, expansionMirror) }
    // An event a write of the wrapper's own raised is never the user's, and the write reads the tree back
    // into its mirror once it has returned, so the walk that would answer with what the mirror is about to
    // be told anyway is skipped while one is in flight.
    val userSelectionListener =
        remember(selectionMirror, treeSelectionListener) {
            TreeSelectionListener { event ->
                if (selectionMirror.isWriting) return@TreeSelectionListener
                val tree = event.source as JTree
                selectionMirror.report(readSelection(tree, tree.model)) {
                    treeSelectionListener.valueChanged(event)
                }
            }
        }
    val userExpansionListener =
        remember(expansionMirror, treeExpansionListener) {
            object : TreeExpansionListener {
                override fun treeExpanded(event: TreeExpansionEvent) =
                    report(event) { target -> target.treeExpanded(event) }

                override fun treeCollapsed(event: TreeExpansionEvent) =
                    report(event) { target -> target.treeCollapsed(event) }

                private fun report(
                    event: TreeExpansionEvent,
                    deliver: (TreeExpansionListener) -> Unit,
                ) {
                    if (expansionMirror.isWriting) return
                    val tree = event.source as JTree
                    expansionMirror.report(readExpansion(tree, tree.model)) {
                        treeExpansionListener?.let(deliver)
                    }
                }
            }
        }

    SwingNode(
        // A tree starts on a rootless model, which the same pass replaces with the declared one. A tree
        // that has never had a root has no expansion of the user's to keep, which is how the first model
        // is told apart from a later one.
        factory = { JTree(DefaultTreeModel(null)) },
        update = {
            applyMirror(selectionMirror)
            applyMirror(expansionMirror)
            set(selectionMode) { mode ->
                settleNarrowing(selectionMirror, selectedPaths, treeSelectionListener) {
                    selectionModel.selectionMode = mode
                }
            }
            set(rootVisible) { visible ->
                settleNarrowing(selectionMirror, selectedPaths, treeSelectionListener) { isRootVisible = visible }
            }
            set(isEditable) { editable -> this.isEditable = editable }
            set(visibleRowCount) { count -> this.visibleRowCount = count }
            set(toggleClickCount) { clicks -> this.toggleClickCount = clicks }
            installContent(mirrors)
            // Redeclaring each mirror subscribes this composition to the user changing the tree's own
            // selection or expansion, and answers whether that mirror or its declaration has changed since
            // the last settling recorded the pair. Both are redeclared whatever either answers, so each
            // records the pair this pass makes: a mirror left unrecorded would keep answering for a pass
            // that is already over.
            //
            // The two are settled together rather than each through its own declare: what a tree shows is
            // the two combined - a node is only selectable where its ancestors are open - so applying one
            // without the other would leave the tree standing on a pairing neither declaration asked for.
            // Each mirror still sees the write as its own, which is what the nesting is for.
            val selectionChanged = selectionMirror.redeclare(selectedPaths)
            val expansionChanged = expansionMirror.redeclare(expandedPaths)
            // The mirrors hold what the settling left the tree on, so a change the user repeats is answered
            // every time they make it.
            settleWhenDue(
                selectionChanged || expansionChanged,
                {
                    TreeDeclarations(
                        mirrors = mirrors,
                        declaredSelection = selectedPaths,
                        declaredExpansion = expandedPaths,
                        target = treeSelectionListener,
                    )
                },
            ) { declarations ->
                settleSelection(declarations) { applyDeclarations(declarations, structureChanged = false) }
            }
            val treeModifier =
                modifier.treeListeners(userSelectionListener, userExpansionListener, treeWillExpandListener)
            applyModifier(treeModifier.uiOwnedProperties(showsRootHandles, rowHeight, nodeRenderer))
        },
    )
}

/**
 * Applies through [block] a property the tree answers by dropping nodes it can no longer hold, reporting to
 * [target] the ones the user loses to it where [declared] leaves the selection theirs, and leaves [mirror]
 * mirroring the selection the tree was left with.
 *
 * The read that records what survived the write stays outside any settlement of the mirror's, because what
 * it records is news: the tree has dropped nodes the composition may still declare, and the pass that puts
 * them back is the one this recording asks for. A settlement here would mark the loss as already answered
 * and leave a declared selection lying where the write dropped it.
 */
private fun JTree.settleNarrowing(
    mirror: MirrorState<Set<List<Int>>?>,
    declared: Set<List<Int>>?,
    target: TreeSelectionListener,
    block: () -> Unit,
) {
    narrowSelection(mirror, declared, target, block)
    mirror.observed(readSelection(this, model))
}

/**
 * Folds in the tree properties a `JTree` leaves to the UI delegate of its look and feel - the
 * root-handle choice, the row height, and the renderer each node is stamped through - as modifier
 * elements, each while the caller declares one and each dropped the moment its declaration goes back to
 * `null`.
 *
 * A `JTree` takes an explicit root-handle choice or row height as the client's own and stops accepting
 * the one its look and feel installs, which is what makes a folded-in element outrank the look and feel
 * while it stays in the chain; removing the element restores the value the tree carried before it was
 * folded in - the one its look and feel chose - through the same capture-on-attach, restore-on-detach
 * every modifier property follows. Detaching on release, reuse and deactivate as well as on withdrawal
 * is what gives a parked tree its look and feel's values back too.
 *
 * The renderer is handed back the other way round, because a tree's is not a value it carries but one
 * its UI delegate builds on demand and takes back on a look-and-feel change. The property folded in here
 * is the composable renderer alone: a tree rendering nodes through its own reads as none, so restoring
 * writes none, and the delegate builds a fresh renderer of the look and feel in force - exactly what a
 * tree that never carried a composable node is given.
 */
private fun SwingModifier.uiOwnedProperties(
    showsRootHandles: Boolean?,
    rowHeight: Int?,
    nodeRenderer: ComposingTreeCellRenderer<*>?,
): SwingModifier {
    var properties = this
    if (showsRootHandles != null) {
        properties =
            properties then
            propertyElement<JTree, Boolean>(
                showsRootHandles,
                read = { it.showsRootHandles },
                write = { tree, value -> tree.showsRootHandles = value },
            )
    }
    if (rowHeight != null) {
        properties =
            properties then
            propertyElement<JTree, Int>(
                rowHeight,
                read = { it.rowHeight },
                write = { tree, value -> tree.rowHeight = value },
            )
    }
    if (nodeRenderer != null) {
        properties =
            properties then
            propertyElement<JTree, TreeCellRenderer?>(
                nodeRenderer,
                read = { it.cellRenderer as? ComposingTreeCellRenderer<*> },
                write = { tree, value -> tree.cellRenderer = value },
            )
    }
    return properties
}
