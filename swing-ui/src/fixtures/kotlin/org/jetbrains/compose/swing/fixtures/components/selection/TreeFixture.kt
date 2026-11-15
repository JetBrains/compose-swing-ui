// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.selection

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.components.defaultOf
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.ExpandVetoException
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/** The parameters [TreeFixture] varies. */
public data class TreeParameters(
    val root: String,
    val children: (String) -> List<String>,
    val label: (String) -> String,
    val hasChildren: ((String) -> Boolean)?,
    val selectedPaths: Set<List<Int>>?,
    val onSelectionChange: (Set<List<Int>>) -> Unit,
    val onExpansionChange: (Set<List<Int>>) -> Unit,
    val onWillExpand: ((value: String, path: List<Int>) -> Boolean)?,
    val isEditable: Boolean,
    val selectionMode: Int,
    val rootVisible: Boolean,
    val showsRootHandles: Boolean?,
    val rowHeight: Int?,
    val visibleRowCount: Int,
    val toggleClickCount: Int,
)

/**
 * A value-driven [Tree] with change callbacks against a raw [JTree] over a hand-built model and the same
 * listeners.
 *
 * Also holds the raw Swing helpers the other Tree fixtures share.
 */
public object TreeFixture : ComposableFixture<TreeParameters, JTree>(
    initial =
        TreeParameters(
            root = "Root before",
            children = { if (it.startsWith("Root")) listOf("Alpha", "Beta") else emptyList() },
            label = { it },
            hasChildren = { it.startsWith("Root") },
            selectedPaths = null,
            onSelectionChange = {},
            onExpansionChange = {},
            onWillExpand = { _, _ -> true },
            isEditable = false,
            selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
            rootVisible = true,
            showsRootHandles = false,
            rowHeight = 20,
            visibleRowCount = 20,
            toggleClickCount = 2,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<TreeParameters> =
        listOf(
            initial.copy(root = "Root after"),
            initial.copy(children = { if (it.startsWith("Root")) listOf("Delta", "Epsilon") else emptyList() }),
            initial.copy(label = { "Node: $it" }),
            initial.copy(hasChildren = { it.startsWith("Root") || it == "Alpha" }),
            initial.copy(selectedPaths = setOf(listOf(1))),
            initial.copy(onSelectionChange = {}),
            initial.copy(onExpansionChange = {}),
            initial.copy(onWillExpand = { _, path -> path.isEmpty() }),
            initial.copy(isEditable = true),
            initial.copy(selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION),
            initial.copy(rootVisible = false),
            initial.copy(showsRootHandles = true),
            initial.copy(rowHeight = 24),
            initial.copy(visibleRowCount = 4),
            initial.copy(toggleClickCount = 1),
        )

    @Composable
    override fun Content(parameters: TreeParameters): Unit =
        Content(
            parameters.root,
            parameters.children,
            parameters.label,
            parameters.hasChildren,
            parameters.selectedPaths,
            parameters.onSelectionChange,
            parameters.onExpansionChange,
            parameters.onWillExpand,
            parameters.isEditable,
            parameters.selectionMode,
            parameters.rootVisible,
            parameters.showsRootHandles,
            parameters.rowHeight,
            parameters.visibleRowCount,
            parameters.toggleClickCount,
        )

    /** Composes a [Tree] over the values [root] and [children] reach, reporting changes to the callbacks. */
    @Composable
    public fun Content(
        root: String,
        children: (String) -> List<String>,
        label: (String) -> String,
        hasChildren: ((String) -> Boolean)?,
        selectedPaths: Set<List<Int>>?,
        onSelectionChange: (Set<List<Int>>) -> Unit,
        onExpansionChange: (Set<List<Int>>) -> Unit,
        onWillExpand: ((value: String, path: List<Int>) -> Boolean)?,
        isEditable: Boolean,
        selectionMode: Int,
        rootVisible: Boolean,
        showsRootHandles: Boolean?,
        rowHeight: Int?,
        visibleRowCount: Int,
        toggleClickCount: Int,
    ) {
        Tree(
            root = root,
            children = children,
            label = label,
            hasChildren = hasChildren,
            selectedPaths = selectedPaths,
            onSelectionChange = onSelectionChange,
            onExpansionChange = onExpansionChange,
            onWillExpand = onWillExpand,
            isEditable = isEditable,
            selectionMode = selectionMode,
            rootVisible = rootVisible,
            showsRootHandles = showsRootHandles,
            rowHeight = rowHeight,
            visibleRowCount = visibleRowCount,
            toggleClickCount = toggleClickCount,
        )
    }

    /** Builds a [JTree] with no model before any of the fixture's values are applied. */
    override fun createRawSwing(): JTree =
        JTree(DefaultTreeModel(null)).also {
            it.putClientProperty(SHOWS_ROOT_HANDLES, it.showsRootHandles)
            it.putClientProperty(ROW_HEIGHT, it.rowHeight)
        }

    /**
     * Brings the model of [component] onto [parameters] and applies its display values and listeners. A
     * showsRootHandles or rowHeight that is withdrawn goes back to the one the tree was created with.
     */
    override fun updateRawSwing(
        component: JTree,
        parameters: TreeParameters,
    ) {
        val selectionReporter =
            component.treeSelectionListeners.firstNotNullOfOrNull { it as? SelectionReporter }
                ?: SelectionReporter(component) {}.also { component.addTreeSelectionListener(it) }
        val expansionReporter =
            component.treeExpansionListeners.firstNotNullOfOrNull { it as? ExpansionReporter }
                ?: ExpansionReporter(component) {}.also { component.addTreeExpansionListener(it) }
        val currentGate = keepGate(component, parameters.onWillExpand)
        selectionReporter.onChange = parameters.onSelectionChange
        expansionReporter.onChange = parameters.onExpansionChange
        selectionReporter.writing = true
        expansionReporter.writing = true
        currentGate?.writing = true
        try {
            syncModel(component, parameters.root, parameters.children, parameters.label, parameters.hasChildren)
            component.selectionModel.selectionMode = parameters.selectionMode
            component.isRootVisible = parameters.rootVisible
            component.showsRootHandles =
                parameters.showsRootHandles ?: component.defaultOf<Boolean>(SHOWS_ROOT_HANDLES)
            component.rowHeight = parameters.rowHeight ?: component.defaultOf<Int>(ROW_HEIGHT)
            if (component.visibleRowCount != parameters.visibleRowCount) {
                component.visibleRowCount = parameters.visibleRowCount
                // The setter only invalidates the tree; a scroll pane keeps its old room until revalidated.
                component.revalidate()
            }
            component.toggleClickCount = parameters.toggleClickCount
            component.isEditable = parameters.isEditable
            parameters.selectedPaths?.let { applySelection(component, it) }
        } finally {
            selectionReporter.writing = false
            expansionReporter.writing = false
            currentGate?.writing = false
        }
    }

    /**
     * The one [WillExpandGate] of [component] asking [onWillExpand], attached while a callback is declared and
     * removed when it is withdrawn; `null` where none is declared.
     */
    private fun keepGate(
        component: JTree,
        onWillExpand: ((value: String, path: List<Int>) -> Boolean)?,
    ): WillExpandGate? {
        val gate = component.treeWillExpandListeners.firstNotNullOfOrNull { it as? WillExpandGate }
        if (onWillExpand == null) {
            gate?.let(component::removeTreeWillExpandListener)
            return null
        }
        val kept = gate ?: WillExpandGate(component, onWillExpand).also { component.addTreeWillExpandListener(it) }
        kept.onWillExpand = onWillExpand
        return kept
    }

    /**
     * Brings [component] onto the tree the values [root] and [children] reach: a model with the same values and
     * branches keeps its nodes, so the selection and the expansion stay, and only the labels that changed are
     * reported; any other structure replaces the model.
     */
    private fun syncModel(
        component: JTree,
        root: String,
        children: (String) -> List<String>,
        label: (String) -> String,
        hasChildren: ((String) -> Boolean)?,
    ) {
        val model = component.model as? DefaultTreeModel
        val currentRoot = model?.root as? ValueNode
        if (model == null || currentRoot == null || !sameShape(currentRoot, root, children, hasChildren)) {
            component.model = valuesModel(root, children, label, hasChildren)
            return
        }

        fun relabel(node: ValueNode) {
            val text = label(node.value)
            if (node.userObject != text) {
                node.userObject = text
                model.nodeChanged(node)
            }
            List(node.childCount) { node.getChildAt(it) as ValueNode }.forEach { relabel(it) }
        }
        relabel(currentRoot)
    }

    /**
     * Whether [node] and everything below it are the raw Swing equivalent of the tree that [value], [children] and
     * [hasChildren] reach: the same values in the same order, and the same nodes allowing children. The labels are
     * not compared, as a node keeps its place when only its label changes.
     */
    private fun sameShape(
        node: ValueNode,
        value: String,
        children: (String) -> List<String>,
        hasChildren: ((String) -> Boolean)?,
    ): Boolean {
        val childValues = children(value)
        return node.value == value &&
            node.allowsChildren == (childValues.isNotEmpty() || hasChildren?.invoke(value) == true) &&
            node.childCount == childValues.size &&
            childValues.indices.all {
                sameShape(node.getChildAt(it) as ValueNode, childValues[it], children, hasChildren)
            }
    }

    private const val SHOWS_ROOT_HANDLES = "fixtureShowsRootHandles"
    private const val ROW_HEIGHT = "fixtureRowHeight"

    /**
     * A tree node that remembers the value it was built from.
     *
     * @property value the source value the node was built from, which stays when its label changes.
     * @param label the text the node displays, which may differ from [value].
     * @param allowsChildren whether the node may have children, whether or not it has any now.
     */
    public class ValueNode(
        public val value: String,
        label: String,
        allowsChildren: Boolean,
    ) : DefaultMutableTreeNode(label, allowsChildren)

    /** Reports the selection of [tree] as index paths to [onChange] whenever it changes, except while [writing]. */
    public class SelectionReporter(
        private val tree: JTree,
        public var onChange: (Set<List<Int>>) -> Unit,
    ) : TreeSelectionListener {
        public var writing: Boolean = false

        override fun valueChanged(event: TreeSelectionEvent) {
            if (!writing) onChange(selectedIndexPaths(tree))
        }
    }

    /**
     * Reports every expanded node of [tree] as index paths to [onChange] whenever one opens or closes, except
     * while [writing].
     */
    public class ExpansionReporter(
        private val tree: JTree,
        public var onChange: (Set<List<Int>>) -> Unit,
    ) : TreeExpansionListener {
        public var writing: Boolean = false

        override fun treeExpanded(event: TreeExpansionEvent) {
            if (!writing) onChange(expandedIndexPaths(tree))
        }

        override fun treeCollapsed(event: TreeExpansionEvent) {
            if (!writing) onChange(expandedIndexPaths(tree))
        }
    }

    /**
     * Vetoes the expansion of a [ValueNode] of [tree] when [onWillExpand] returns `false` for it, except while
     * [writing].
     */
    public class WillExpandGate(
        private val tree: JTree,
        public var onWillExpand: (value: String, path: List<Int>) -> Boolean,
    ) : TreeWillExpandListener {
        public var writing: Boolean = false

        override fun treeWillExpand(event: TreeExpansionEvent) {
            val node = event.path.lastPathComponent
            if (!writing && node is ValueNode && !onWillExpand(node.value, indexPath(tree.model, event.path))) {
                throw ExpandVetoException(event)
            }
        }

        override fun treeWillCollapse(event: TreeExpansionEvent): Unit = Unit
    }

    /**
     * Builds the model a hand-written program would build for the values [root] and [children] reach.
     *
     * @param root the value the root node is built from.
     * @param children the values a node's children are built from, in order, for the value of that node.
     * @param label the text a node displays for its value.
     * @param hasChildren whether the node of a value allows children even when [children] returns none, so that it
     *   shows as a branch that is empty now; `null` for none. A node with children always allows them.
     */
    public fun valuesModel(
        root: String,
        children: (String) -> List<String>,
        label: (String) -> String,
        hasChildren: ((String) -> Boolean)?,
    ): DefaultTreeModel {
        fun node(value: String): ValueNode {
            val childValues = children(value)
            val result = ValueNode(value, label(value), childValues.isNotEmpty() || hasChildren?.invoke(value) == true)
            childValues.forEach { result.add(node(it)) }
            return result
        }
        return DefaultTreeModel(node(root), true)
    }

    /** Builds a model with the value [root] over the two leaves [first] and [second]. */
    public fun sampleModel(
        root: String,
        first: String,
        second: String,
    ): TreeModel =
        DefaultTreeModel(
            DefaultMutableTreeNode(root).apply {
                add(DefaultMutableTreeNode(first))
                add(DefaultMutableTreeNode(second))
            },
        )

    /** Selects the nodes [paths] names in [tree], each a chain of child indices from the root. */
    public fun applySelection(
        tree: JTree,
        paths: Set<List<Int>>,
    ) {
        tree.selectionPaths = paths.mapNotNull { pathOf(tree.model, it) }.toTypedArray()
    }

    /** Collapses every node of [tree], then opens the nodes [paths] names. */
    public fun applyExpansion(
        tree: JTree,
        paths: Set<List<Int>>,
    ) {
        for (row in tree.rowCount - 1 downTo 0) tree.collapseRow(row)
        paths.forEach { path -> pathOf(tree.model, path)?.let(tree::expandPath) }
    }
}

/** The selected nodes of [tree] as index paths from the root. */
private fun selectedIndexPaths(tree: JTree): Set<List<Int>> =
    tree.selectionPaths
        .orEmpty()
        .map { indexPath(tree.model, it) }
        .toSet()

/** The open nodes of [tree] as index paths from the root. */
private fun expandedIndexPaths(tree: JTree): Set<List<Int>> {
    val root = TreePath(tree.model.root)
    val descendants = tree.getExpandedDescendants(root) ?: return emptySet()
    return (descendants.toList() + root).map { indexPath(tree.model, it) }.toSet()
}

/** The chain of child indices from the root of [model] to [path]. */
private fun indexPath(
    model: TreeModel,
    path: TreePath,
): List<Int> = path.path.toList().zipWithNext { parent, child -> model.getIndexOfChild(parent, child) }

/** The path in [model] the chain of child indices [indices] names, or `null` when it leaves the tree. */
private fun pathOf(
    model: TreeModel,
    indices: List<Int>,
): TreePath? {
    var current = model.root
    var path = TreePath(current)
    for (index in indices) {
        if (index !in 0 until model.getChildCount(current)) return null
        current = model.getChild(current, index)
        path = path.pathByAddingChild(current)
    }
    return path
}
