@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.AppliedValue
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.SwingNodeUpdater
import org.jetbrains.compose.swing.constants.TreeSelectionMode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.treeExpansionListener
import org.jetbrains.compose.swing.modifier.listener.treeSelectionListener
import org.jetbrains.compose.swing.modifier.propertyElement
import org.jetbrains.compose.swing.rememberAppliedValue
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * A composable wrapper for `JTree`.
 *
 * The tree is described as data: [root] is the root value and [children] yields each value's child
 * values, walked recursively to build the displayed structure; [label] renders each value's row text
 * (its `toString` by default). The structure reflects the last composition - changing the data the
 * accessors return rebuilds the tree on recompose. Selection is declared with [selectedPaths] and
 * expansion with [expandedPaths], each path expressed as the chain of child indices from the root (so
 * `[]` is the root, `[0]` its first child, `[0, 2]` that child's third child), and the user's changes to
 * either arrive through [onSelectionChange] and [onExpansionChange]. Place it in a [ScrollPane] to
 * scroll.
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
 * [onSelectionChange] and [onExpansionChange] report the user's changes only; rebuilding the structure
 * from new data produces neither. A declared selection or expansion is the composition's state and is
 * re-applied on every pass: it survives a rebuild, and a user change the caller does not adopt does not
 * stand. Undeclared, either belongs to the user alone - the library never imposes one, and what the
 * user reached survives a rebuild as well, the nodes they closed as much as the ones they opened. A node
 * the new structure no longer has is the exception: it leaves the selection, and [onSelectionChange]
 * reports what is left of it.
 *
 * @param root the root value of the tree
 * @param children yields the child values of a value, in display order
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param label renders a value's row text
 * @param selectedPaths the selected nodes as index paths from the root; `null` - the default - leaves the
 *   selection to the user
 * @param onSelectionChange callback invoked when the user changes the selection
 * @param expandedPaths the expanded nodes as index paths from the root; every other node is collapsed,
 *   except an ancestor of an expanded one, which stays expanded so that node is reachable. `null` - the
 *   default - leaves expansion to the tree and to the user
 * @param onExpansionChange callback invoked when the user expands or collapses a node, receiving every
 *   expanded node in document order
 * @param selectionMode how many nodes may be selected
 * @param rootVisible whether the root node is shown
 * @param showsRootHandles whether expand/collapse handles are shown for the top-level nodes;
 *   `null` leaves the choice to the installed look and feel
 */
@Composable
public fun <T> Tree(
    root: T,
    children: (T) -> List<T>,
    modifier: SwingModifier = SwingModifier,
    label: (T) -> String = { it.toString() },
    selectedPaths: List<List<Int>>? = null,
    onSelectionChange: (List<List<Int>>) -> Unit = {},
    expandedPaths: List<List<Int>>? = null,
    onExpansionChange: (List<List<Int>>) -> Unit = {},
    @TreeSelectionMode selectionMode: Int = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
    rootVisible: Boolean = true,
    showsRootHandles: Boolean? = null,
) {
    Tree(
        root = root,
        children = children,
        treeSelectionListener = rememberSelectionListener(onSelectionChange),
        modifier = modifier,
        label = label,
        selectedPaths = selectedPaths,
        expandedPaths = expandedPaths,
        treeExpansionListener = rememberExpansionListener(onExpansionChange),
        selectionMode = selectionMode,
        rootVisible = rootVisible,
        showsRootHandles = showsRootHandles,
    )
}

/**
 * A [Tree] driven by raw listeners instead of the `onSelectionChange`/`onExpansionChange` lambdas. A
 * listener is notified of the user's changes only - the selection listener also of a selection a new
 * structure took away from the user - and is removed on the same instance; pass a stable instance (e.g.
 * `remember {}`) to avoid churn.
 *
 * @param root the root value of the tree
 * @param children yields the child values of a value, in display order
 * @param treeSelectionListener the listener notified of the user's selection changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param label renders a value's row text
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
 */
@Composable
public fun <T> Tree(
    root: T,
    children: (T) -> List<T>,
    treeSelectionListener: TreeSelectionListener,
    modifier: SwingModifier = SwingModifier,
    label: (T) -> String = { it.toString() },
    selectedPaths: List<List<Int>>? = null,
    expandedPaths: List<List<Int>>? = null,
    treeExpansionListener: TreeExpansionListener? = null,
    @TreeSelectionMode selectionMode: Int = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
    rootVisible: Boolean = true,
    showsRootHandles: Boolean? = null,
) {
    TreeNode(
        treeSelectionListener = treeSelectionListener,
        modifier = modifier,
        selectedPaths = selectedPaths,
        expandedPaths = expandedPaths,
        treeExpansionListener = treeExpansionListener,
        selectionMode = selectionMode,
        rootVisible = rootVisible,
        showsRootHandles = showsRootHandles,
    ) { appliedSelection, appliedExpansion ->
        set(Triple(root, children, label)) { (rootValue, childrenOf, labelOf) ->
            installModel(
                TreeBindings(appliedSelection, appliedExpansion),
                DefaultTreeModel(buildNode(rootValue, childrenOf, labelOf)),
                selectedPaths,
                expandedPaths,
                treeSelectionListener,
            )
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
 * survive a model swap, declared or not. Place it in a [ScrollPane] to scroll.
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
 *   expanded node in document order
 * @param selectionMode how many nodes may be selected
 * @param rootVisible whether the root node is shown
 * @param showsRootHandles whether expand/collapse handles are shown for the top-level nodes;
 *   `null` leaves the choice to the installed look and feel
 */
@Composable
public fun Tree(
    model: TreeModel,
    modifier: SwingModifier = SwingModifier,
    selectedPaths: List<List<Int>>? = null,
    onSelectionChange: (List<List<Int>>) -> Unit = {},
    expandedPaths: List<List<Int>>? = null,
    onExpansionChange: (List<List<Int>>) -> Unit = {},
    @TreeSelectionMode selectionMode: Int = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
    rootVisible: Boolean = true,
    showsRootHandles: Boolean? = null,
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
 */
@Composable
public fun Tree(
    model: TreeModel,
    treeSelectionListener: TreeSelectionListener,
    modifier: SwingModifier = SwingModifier,
    selectedPaths: List<List<Int>>? = null,
    expandedPaths: List<List<Int>>? = null,
    treeExpansionListener: TreeExpansionListener? = null,
    @TreeSelectionMode selectionMode: Int = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
    rootVisible: Boolean = true,
    showsRootHandles: Boolean? = null,
) {
    TreeNode(
        treeSelectionListener = treeSelectionListener,
        modifier = modifier,
        selectedPaths = selectedPaths,
        expandedPaths = expandedPaths,
        treeExpansionListener = treeExpansionListener,
        selectionMode = selectionMode,
        rootVisible = rootVisible,
        showsRootHandles = showsRootHandles,
    ) { appliedSelection, appliedExpansion ->
        set(model) { newModel ->
            installModel(
                TreeBindings(appliedSelection, appliedExpansion),
                newModel,
                selectedPaths,
                expandedPaths,
                treeSelectionListener,
            )
        }
    }
}

/**
 * The `JTree` node every [Tree] overload renders: all of it but the structure, which [installContent]
 * declares - values walked through child accessors in one family of overloads, the caller's own model in
 * the other. [installContent] is handed the [AppliedValue]s mirroring the tree's selection and expansion,
 * since giving the tree a new structure is one of the writes that moves both.
 */
@Composable
private fun TreeNode(
    treeSelectionListener: TreeSelectionListener,
    modifier: SwingModifier,
    selectedPaths: List<List<Int>>?,
    expandedPaths: List<List<Int>>?,
    treeExpansionListener: TreeExpansionListener?,
    @TreeSelectionMode selectionMode: Int,
    rootVisible: Boolean,
    showsRootHandles: Boolean?,
    installContent: SwingNodeUpdater<JTree>.(
        AppliedValue<List<List<Int>>?>,
        AppliedValue<List<List<Int>>?>,
    ) -> Unit,
) {
    val appliedSelection = rememberAppliedValue(selectedPaths)
    val appliedExpansion = rememberAppliedValue(expandedPaths)
    val userSelectionListener =
        remember(appliedSelection, treeSelectionListener) {
            TreeSelectionListener { event ->
                val tree = event.source as JTree
                if (appliedSelection.observed(readSelection(tree, tree.model))) {
                    treeSelectionListener.valueChanged(event)
                }
            }
        }
    val userExpansionListener =
        remember(appliedExpansion, treeExpansionListener) {
            object : TreeExpansionListener {
                override fun treeExpanded(event: TreeExpansionEvent) =
                    report(event) { target -> target.treeExpanded(event) }

                override fun treeCollapsed(event: TreeExpansionEvent) =
                    report(event) { target -> target.treeCollapsed(event) }

                private fun report(
                    event: TreeExpansionEvent,
                    deliver: (TreeExpansionListener) -> Unit,
                ) {
                    val tree = event.source as JTree
                    if (appliedExpansion.observed(readExpansion(tree, tree.model))) {
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
            set(selectionMode) { mode ->
                narrowSelection(appliedSelection, selectedPaths, treeSelectionListener) {
                    selectionModel.selectionMode = mode
                }
            }
            set(rootVisible) { visible ->
                narrowSelection(appliedSelection, selectedPaths, treeSelectionListener) { isRootVisible = visible }
            }
            installContent(appliedSelection, appliedExpansion)
            // Reading both mirrors is what subscribes this composition to the user moving the tree's own
            // selection or expansion, so the pass that follows settles each against its declaration: a
            // change the caller adopts stands, and one it does not is written back over. Both reads have to
            // run every pass regardless of the other's outcome, so neither is short-circuited away.
            //
            // The two are applied together rather than each through its own declare: what a tree shows is
            // the two combined - a node is only selectable where its ancestors are open - so applying one
            // without the other would leave the tree standing on a pairing neither declaration asked for.
            // Each mirror still sees the write as its own, which is what the nesting is for.
            val heldSelection = appliedSelection.current
            val heldExpansion = appliedExpansion.current
            val selectionSettled = appliedSelection.isSettled(selectedPaths, heldSelection)
            val expansionSettled = appliedExpansion.isSettled(expandedPaths, heldExpansion)
            if (!selectionSettled || !expansionSettled) {
                reconcile {
                    appliedSelection.write {
                        appliedExpansion.write { applyDeclarations(selectedPaths, expandedPaths) }
                    }
                }
            }
            val treeModifier =
                modifier.treeListeners(userSelectionListener, userExpansionListener).rootHandles(showsRootHandles)
            applyModifier(treeModifier)
        },
    )
}

/**
 * Remembers a [TreeSelectionListener] that reports the tree's selection back through [onSelectionChange]
 * as index paths. A tree selection event's source is the `JTree` itself, so the selection is read back
 * from its model; [onSelectionChange] is tracked with [rememberUpdatedState] so the remembered listener
 * always calls the latest callback without being recreated.
 */
@Composable
private fun rememberSelectionListener(onSelectionChange: (List<List<Int>>) -> Unit): TreeSelectionListener {
    val callback = rememberUpdatedState(onSelectionChange)
    return remember {
        TreeSelectionListener { event ->
            val tree = event.source as JTree
            callback.value(readSelection(tree, tree.model))
        }
    }
}

/**
 * Remembers a [TreeExpansionListener] that reports every expanded node back through [onExpansionChange]
 * as index paths, after each expand and each collapse. An expansion event's source is the `JTree`
 * itself, so the expansion is read back from it; [onExpansionChange] is tracked with
 * [rememberUpdatedState] so the remembered listener always calls the latest callback without being
 * recreated.
 */
@Composable
private fun rememberExpansionListener(onExpansionChange: (List<List<Int>>) -> Unit): TreeExpansionListener {
    val callback = rememberUpdatedState(onExpansionChange)
    return remember {
        object : TreeExpansionListener {
            override fun treeExpanded(event: TreeExpansionEvent): Unit = report(event)

            override fun treeCollapsed(event: TreeExpansionEvent): Unit = report(event)

            private fun report(event: TreeExpansionEvent) {
                val tree = event.source as JTree
                callback.value(readExpansion(tree, tree.model))
            }
        }
    }
}

/**
 * Folds in the tree's root-handle choice as a modifier element while [declared] is non-null, and drops
 * the element the moment it goes back to `null`. A `JTree` takes an explicit choice as the client's own
 * and stops accepting the one its look and feel installs, which is what makes a folded-in element
 * outrank the look and feel while it stays in the chain; removing the element restores the choice the
 * tree carried before it was folded in - the one its look and feel chose - through the same
 * capture-on-attach, restore-on-detach every modifier property follows. Detaching on release, reuse and
 * deactivate as well as on withdrawal is what gives a recycled tree its look and feel's choice back too.
 */
private fun SwingModifier.rootHandles(declared: Boolean?): SwingModifier =
    if (declared == null) {
        this
    } else {
        this then
            propertyElement<JTree, Boolean>(
                declared,
                read = { it.showsRootHandles },
                write = { tree, value -> tree.showsRootHandles = value },
            )
    }

/**
 * The mirrors a tree settles its two declarations through: what is selected, and what is open.
 *
 * They travel together because installing a model can move both, and each has to see the other's write in
 * flight for its listener to tell the wrapper's doing from the user's.
 */
private class TreeBindings(
    val selection: AppliedValue<List<List<Int>>?>,
    val expansion: AppliedValue<List<List<Int>>?>,
)

/**
 * Installs [newModel], keeping the nodes [declaredSelection] names selected - or, where the caller declared
 * nothing, the nodes the user had - and reporting to [target] the nodes the new structure no longer has.
 * See [installNarrowing].
 *
 * Installing a model re-opens the root as well. Where the caller declared an expansion, it is asserted on
 * the new structure right away, so it stands from the moment the model is in. Where none was declared, the
 * expansion is not the library's to decide, and the expansion the tree held is put back whole instead - the
 * nodes that were open are opened and every other one is closed - so a node the user had collapsed does not
 * come back open. A tree that had no root yet has nothing retained, and keeps the expansion a `JTree` gives
 * a model it is handed.
 *
 * Both the selection narrowing and the expansion restore run as one write of each mirror in [bindings] -
 * installing a model can move both, and each mirror has to see its own write coming for its listener to
 * tell it apart from the user's.
 */
private fun JTree.installModel(
    bindings: TreeBindings,
    newModel: TreeModel,
    declaredSelection: List<List<Int>>?,
    declaredExpansion: List<List<Int>>?,
    target: TreeSelectionListener,
) {
    val appliedSelection = bindings.selection
    val appliedExpansion = bindings.expansion
    val hadRoot = model?.root != null
    val keptExpansion = readExpansion(this, model)
    val oldLead = leadSelectionPath
    // The nodes the tree has selected, alongside the index paths they resolve to in the model being
    // replaced: the paths name what a loss has to be reported as, and the indices are what the new model
    // is searched for.
    val selectedNodes = selectionPaths.orEmpty()
    val selectedIndices = selectedNodes.map { pathToIndices(model, it) }
    appliedExpansion.write {
        appliedSelection.installNarrowing(
            declared = declaredSelection,
            selection = { readSelection(this, model) },
            apply = { paths -> applySelection(this, model, paths) },
            report = { lost ->
                // A tree re-fires its selection model's event as its own, with itself as the source, and
                // that is the event a listener installed on the tree is handed. The nodes are the ones the
                // new structure took out of the selection, so none of them is a node the event adds.
                val nodes = selectedNodes.filterIndexed { position, _ -> selectedIndices[position] in lost }
                val removed = BooleanArray(nodes.size)
                target.valueChanged(
                    TreeSelectionEvent(this, nodes.toTypedArray(), removed, oldLead, leadSelectionPath),
                )
            },
        ) {
            model = newModel
            (declaredExpansion ?: keptExpansion.takeIf { hadRoot })?.let { applyExpansion(this, newModel, it) }
        }
    }
    appliedExpansion.observed(readExpansion(this, model))
}

/**
 * The tree's user-facing listeners as one chain: the selection listener and the expansion listener. Both
 * feed their mirror on every user change regardless of whether the caller declared a raw listener of its
 * own to forward to.
 */
private fun SwingModifier.treeListeners(
    selectionListener: TreeSelectionListener,
    expansionListener: TreeExpansionListener,
): SwingModifier = treeSelectionListener(selectionListener).treeExpansionListener(expansionListener)

/**
 * Builds a [DefaultMutableTreeNode] tree from [root] by recursively visiting [children]. Each node's
 * user object is [label] applied to the value (a `String`), so the default `JTree` renderer shows the
 * label with no per-node cast. The returned node mirrors the data tree one-to-one in structure and
 * child order.
 */
private fun <T> buildNode(
    value: T,
    children: (T) -> List<T>,
    label: (T) -> String,
): DefaultMutableTreeNode {
    val node = DefaultMutableTreeNode(label(value))
    for (child in children(value)) {
        node.add(buildNode(child, children, label))
    }
    return node
}
