package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.State
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.node.AppliedValue
import javax.swing.JTree
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/*
 * The structure a `Tree` stands on: the data a value-driven one describes it with, the model that data
 * builds, and how a later structure - or a later declaration - reaches a tree already showing one.
 *
 * Three roles carry one settling between them. The declarations are what the composition states this
 * pass - a selection, an expansion, and the listener a loss is reported to. The mirrors are what the
 * tree was last known to hold, one per facet, and are what let a listener tell the wrapper's own write
 * from a move the user made. The outcome is what the write left standing, which is not always what was
 * declared: a collapse takes over the selection it hides, and a structure the model no longer holds
 * drops the paths naming it.
 */

/**
 * What a tree settles its two declarations through, for the life of one node: the mirror of each facet, and
 * the nodes a collapse was last reported to have taken over.
 *
 * The mirrors travel together because one write moves both - installing a model, applying a collapse - and
 * each has to see the other's write in flight for its listener to tell the wrapper's doing from the user's.
 */
internal class TreeMirrors(
    val selection: AppliedValue<Set<List<Int>>?>,
    val expansion: AppliedValue<Set<List<Int>>?>,
) {
    /** The declared selection the nodes in [reportedNarrowing] were hidden out of. */
    private var narrowedDeclaration: Set<List<Int>>? = null

    /** The nodes of [narrowedDeclaration] the caller has already been told a collapse took over. */
    private var reportedNarrowing: Set<List<Int>> = emptySet()

    /**
     * The nodes of [declaredSelection] a collapse hid that the caller has not been told about: the ones
     * [hidden] names that a collapse standing over the same declaration did not already hide.
     *
     * A declaration arrives again on every pass and the loss happened once, so a tree left standing where
     * it was reports nothing more. A collapse that takes over further nodes is a further loss, and a node
     * that comes back into view is reported again the next time one hides it.
     */
    fun takeNarrowing(
        declaredSelection: Set<List<Int>>,
        hidden: Set<List<Int>>,
    ): Set<List<Int>> {
        if (declaredSelection != narrowedDeclaration) {
            narrowedDeclaration = declaredSelection
            reportedNarrowing = emptySet()
        }
        val unreported = hidden - reportedNarrowing
        reportedNarrowing = hidden
        return unreported
    }
}

/**
 * What one settling of a tree runs on: the mirrors it goes through, the two declarations to settle it on,
 * and the listener a selection loss is handed to.
 */
internal class TreeDeclarations(
    val mirrors: TreeMirrors,
    val declaredSelection: Set<List<Int>>?,
    val declaredExpansion: Set<List<Int>>?,
    val target: TreeSelectionListener,
)

/**
 * What one settling write left a tree on: the selected nodes a declared collapse took over, and what the
 * write can say of the nodes the tree is left showing open.
 */
internal class TreeSettleOutcome(
    val hiddenSelection: Set<List<Int>>,
    val expansion: SettledExpansion,
)

/**
 * What a settling write can say of the nodes a tree is left showing open. A write that names them spares
 * the walk of the structure that would name them again.
 */
internal sealed interface SettledExpansion {
    /** The write neither opened nor closed a node, and renamed none: the mirror still names them. */
    data object Standing : SettledExpansion

    /** The write left the tree showing exactly [open]. */
    class Named(
        val open: Set<List<Int>>,
    ) : SettledExpansion

    /** The write moved them without naming them, so the tree is what answers for them. */
    data object Unnamed : SettledExpansion
}

/**
 * Installs [newModel], keeping the nodes the declared selection names selected - or, where the caller
 * declared nothing, the nodes the user had - and reporting the nodes the new structure no longer has. See
 * [installNarrowing].
 *
 * Installing a model re-opens the root as well. Where the caller declared an expansion, it is asserted on
 * the new structure right away, so it stands from the moment the model is in. Where none was declared, the
 * expansion is not the library's to decide, and the expansion the tree held is put back whole instead - the
 * nodes that were open are opened and every other one is closed - so a node the user had collapsed does not
 * come back open. A tree that had no root yet has nothing retained, and keeps the expansion a `JTree` gives
 * a model it is handed.
 *
 * Both the selection narrowing and the expansion restore run as one write of each mirror in [declarations] -
 * installing a model can move both, and each mirror has to see its own write coming for its listener to
 * tell it apart from the user's.
 */
internal fun JTree.installModel(
    declarations: TreeDeclarations,
    newModel: TreeModel,
) {
    val declaredSelection = declarations.declaredSelection
    val target = declarations.target
    val hadRoot = model?.root != null
    val keptExpansion = readExpansion(this, model)
    val oldLead = leadSelectionPath
    // The nodes the tree has selected, alongside the index paths they resolve to in the model being
    // replaced: the paths name what a loss has to be reported as, and the indices are what the new model
    // is searched for.
    val selectedNodes = selectionPaths.orEmpty()
    val selectedIndices = selectedNodes.map { pathToIndices(model, it) }
    declarations.mirrors.expansion.write {
        declarations.mirrors.selection.installNarrowing(
            declared = declaredSelection,
            selection = { readSelection(this, model) },
            apply = { paths -> applySelection(this, model, paths) },
            report = { lost -> reportLostPaths(target, selectedNodes, selectedIndices, lost, oldLead) },
        ) {
            model = newModel
            (declarations.declaredExpansion ?: keptExpansion.takeIf { hadRoot })
                ?.let { applyExpansion(this, newModel, it) }
        }
    }
    declarations.mirrors.expansion.observed(readExpansion(this, model))
}

/**
 * Brings [content] onto the nodes [standing] already holds rather than onto a model of its own: the child
 * values a list hands over unchanged at its front and at its back go on being the nodes they were, what
 * lies between them settles by position, and only what the data dropped or gained leaves or joins the
 * list. A node that stays keeps its expansion and the selection reaching it, neither of which outlives a
 * model replaced whole.
 *
 * The declarations are re-asserted on the same write, so a node the structure has just gained is opened and
 * selected where they name it. What the write took off the selection is reported - see [settleSelection].
 */
internal fun <T> JTree.updateContent(
    declarations: TreeDeclarations,
    standing: DeclaredTreeModel<T>,
    content: TreeContent<T>,
) {
    val walkEvery = !standing.content.walksAlike(content)
    settleSelection(declarations) {
        val structureMoved = content.syncInto(standing, walkEvery)
        standing.content = content
        applyDeclarations(declarations, structureMoved)
    }
}

/**
 * Runs [settle] - the write that leaves the tree on what this pass declares - as a write of both mirrors in
 * [declarations], and hands its listener the nodes that left the selection on the way.
 *
 * Who owns the selection decides what is reported. Undeclared, it is the user's, and a node the write took
 * out of it is gone for good. Declared, it is the composition's state, re-asserted on every pass, and a
 * narrowing the widget makes for itself is not reported - all but the nodes a declared collapse hid, which
 * [settle] answers with: no pass can put them back while that collapse stands.
 *
 * An undeclared loss is what this write took away, so it is handed over as it happens. A narrowing is
 * handed over once per node it takes over - see [TreeMirrors.takeNarrowing].
 *
 * The expansion mirror is left on what [settle] says the write left open, and the structure is walked for
 * it only where the write cannot say - see [SettledExpansion].
 *
 * The listener is reached once the write has returned, so what it is told is final, and contained the way
 * every caller callback reached from a pass is.
 */
internal fun JTree.settleSelection(
    declarations: TreeDeclarations,
    settle: JTree.() -> TreeSettleOutcome,
) {
    val mirrors = declarations.mirrors
    val declaredSelection = declarations.declaredSelection
    val oldLead = leadSelectionPath
    // The nodes the tree has selected, alongside the index paths naming them in the structure being left:
    // a loss has to be reported as the paths the caller knows the nodes by. Only an undeclared selection
    // is measured against them, and naming every selected node is a walk of the model for each.
    val heldNodes = if (declaredSelection == null) selectionPaths.orEmpty() else emptyArray()
    val heldIndices = heldNodes.map { pathToIndices(model, it) }
    var outcome = TreeSettleOutcome(emptySet(), SettledExpansion.Standing)
    // The write and the read-backs that record what survived it are one settlement of each mirror, which
    // is what the nested brackets state. Each mirror's write stays inside its own bracket: a settlement
    // marks the moves it made as answered, and the write is what a listener reads to tell the wrapper's
    // doing from the user's.
    mirrors.expansion.settle openness@{
        mirrors.selection.settle {
            mirrors.expansion.write { mirrors.selection.write { outcome = settle() } }
            // What the write left is recorded as the answer to the declarations it applied, not as a move:
            // this pass asked for it and read it back, so there is nothing for a further pass to do about
            // it. Each settlement is closed against its own mirror, so the expansion's is named: inside
            // the selection's block it is the outer receiver.
            answered(readSelection(this@settleSelection, model))
            when (val settled = outcome.expansion) {
                // The write never touched the expansion, so the mirror already holds what the tree holds
                // and there is nothing to read back.
                SettledExpansion.Standing -> this@openness.unchanged()

                is SettledExpansion.Named -> this@openness.answered(settled.open)

                SettledExpansion.Unnamed -> this@openness.answered(readExpansion(this@settleSelection, model))
            }
        }
    }
    val left =
        if (declaredSelection == null) {
            selectionDropped(heldNodes, heldIndices)
        } else {
            resolveAll(mirrors.takeNarrowing(declaredSelection, outcome.hiddenSelection))
        }
    if (left.isEmpty()) return
    val indices = left.map { it.second }
    dispatchToCaller {
        reportLostPaths(declarations.target, left.map { it.first }.toTypedArray(), indices, indices.toSet(), oldLead)
    }
}

/**
 * The nodes among [heldNodes] the tree no longer has selected, each with the index path from [heldIndices]
 * that named it in the structure the write started from.
 */
private fun JTree.selectionDropped(
    heldNodes: Array<out TreePath>,
    heldIndices: List<List<Int>>,
): List<Pair<TreePath, List<Int>>> {
    val standing = selectionPaths.orEmpty().toHashSet()
    return heldNodes.indices.filter { heldNodes[it] !in standing }.map { heldNodes[it] to heldIndices[it] }
}

/** [indexPaths] as the nodes they name, dropping the ones the current structure no longer has. */
private fun JTree.resolveAll(indexPaths: Set<List<Int>>): List<Pair<TreePath, List<Int>>> =
    indexPaths.mapNotNull { indices -> resolvePath(model, indices)?.let { it to indices } }

/**
 * Re-asserts on the tree what [declarations] declares: a declaration is the composition's state, so a user
 * change the caller does not adopt is undone, while an undeclared selection or expansion is left standing.
 * Answers with the selected paths a declared collapse took over - see [applyVisibleSelection] - and with
 * the nodes the tree is left showing open. [structureMoved] says whether the write this runs inside took a
 * node out of the structure or put one in.
 *
 * The selection is applied after the expansion, because a tree drops the selection inside a subtree it is
 * asked to collapse; applied first, it would not outlast the expansion that follows.
 *
 * A declared expansion is what the tree stands on once it has been applied, so the selection is narrowed to
 * the nodes that expansion leaves showing. Where none is declared the expansion is the tree's own and a
 * selection reaching under a closed node opens it, which is what a `JTree` does with a selection it is given.
 */
internal fun JTree.applyDeclarations(
    declarations: TreeDeclarations,
    structureMoved: Boolean,
): TreeSettleOutcome {
    val declaredExpansion = declarations.declaredExpansion
    if (declaredExpansion == null) {
        val selected = applySelection(this, model, declarations.declaredSelection)
        // A tree opens what it must to show a node it is given to select, and a structure change renames by
        // index every node after the one it moved: either leaves the tree to answer for what it shows open.
        val expansion = if (selected || structureMoved) SettledExpansion.Unnamed else SettledExpansion.Standing
        return TreeSettleOutcome(emptySet(), expansion)
    }
    val opened = applyExpansion(this, model, declaredExpansion)
    val hidden = applyVisibleSelection(this, model, declarations.declaredSelection)
    return TreeSettleOutcome(hidden, opened?.let { SettledExpansion.Named(it) } ?: SettledExpansion.Unnamed)
}

/**
 * Re-applies [selectedPaths] as the tree's selection with every node a closed node hides replaced by that
 * closed node, and answers with the paths the replacement moved. Paths that no longer resolve against the
 * current structure are dropped, and a `null` declaration leaves the tree's selection alone.
 *
 * A tree shows no row for a node under a closed one and holds no selection it cannot show: closing a node
 * takes its selected descendants off the selection and puts the node itself on in their place. Declaring
 * such a pair asks for a tree that cannot exist, and the node that stands is the one a `JTree` reaches for
 * itself.
 */
private fun applyVisibleSelection(
    tree: JTree,
    model: TreeModel,
    selectedPaths: Set<List<Int>>?,
): Set<List<Int>> {
    if (selectedPaths == null) return emptySet()
    val hidden = LinkedHashSet<List<Int>>()
    val shown = LinkedHashSet<TreePath>()
    for (indices in selectedPaths.sortedWith(treeDocumentOrder)) {
        val resolved = resolvePath(model, indices) ?: continue
        val visible = tree.shownNodeOf(resolved)
        if (visible !== resolved) hidden.add(indices)
        shown.add(visible)
    }
    selectNodes(tree, shown.toList())
    return hidden
}

/**
 * The node the tree shows for [path]: [path] itself while every node above it is open, and otherwise the
 * highest closed node on the way to it, which is the deepest node of that chain the tree still has a row for.
 */
private fun JTree.shownNodeOf(path: TreePath): TreePath {
    var shown = path
    var above = path.parentPath
    // A node whose parent is open has every node above it open too, so its parent alone answers for a node
    // the tree shows; only one it does not is walked up to the closed node standing in its place.
    if (above != null && !isExpanded(above)) {
        while (above != null) {
            if (!isExpanded(above)) shown = above
            above = above.parentPath
        }
    }
    return shown
}

/**
 * The data one pass of a value-driven [Tree] describes its structure with: the root value, the accessors
 * that walk and label it, and the answer that decides which of its values are branches. Each part moves the
 * structure the tree shows, so a change to any of them is walked into the nodes.
 */
internal data class TreeContent<T>(
    val root: T,
    val children: (T) -> List<T>,
    val label: (T) -> @Nls String,
    val hasChildren: ((T) -> Boolean)?,
) {
    /**
     * The model this content renders as, reporting an edit committed on one of its nodes to [onNodeEdit].
     *
     * A node answers for its own leafness only while [hasChildren] is declared, which is what asking a
     * node whether it allows children expresses; without it a node is a leaf exactly when it has none.
     */
    fun toModel(onNodeEdit: State<(value: T, path: List<Int>, newValue: Any?) -> Unit>): DeclaredTreeModel<T> =
        DeclaredTreeModel(this, onNodeEdit)

    /**
     * Whether [other] reaches the same nodes from the same values: the accessors are compared by identity,
     * since two of them answer alike only where they are the same one.
     */
    fun walksAlike(other: TreeContent<T>): Boolean =
        children === other.children && label === other.label && hasChildren === other.hasChildren

    /**
     * Builds a [DefaultMutableTreeNode] tree from [value] by recursively visiting the child accessor. Each
     * node's user object is a [TreeNodeValue] pairing the value with the label applied to it: the label is
     * what the node renders as text through any renderer that asks a node for it, and the value is what a
     * composable node is handed. The returned node mirrors the data tree one-to-one in structure and child
     * order.
     *
     * A value the data gives children is a branch whatever the branch answer says of it - a node that
     * allows none could not hold the children it has.
     */
    fun buildNode(value: T): DefaultMutableTreeNode {
        val childValues = children(value)
        val branch = childValues.isNotEmpty() || (hasChildren?.invoke(value) ?: true)
        val node = DefaultMutableTreeNode(TreeNodeValue(value, label(value)), branch)
        for (child in childValues) {
            node.add(buildNode(child))
        }
        return node
    }

    /**
     * Walks this content into the nodes [model] holds: a node takes over the value it now stands for, a
     * child the data no longer has is taken out of its parent and one it gained is put in, each through the
     * model's own event, which is what carries the change into the rows the tree shows.
     *
     * A child value handed over unchanged - the same object - describes the subtree under it unchanged too,
     * and that whole subtree is passed over. Which holds of the accessors it was last walked with alone, so
     * [walkEvery] is what a new one asks for.
     *
     * Answers whether a node left the structure or joined it, which renames by index every node after it.
     */
    fun syncInto(
        model: DeclaredTreeModel<T>,
        walkEvery: Boolean,
    ): Boolean = syncNode(model, model.root as DefaultMutableTreeNode, root, walkEvery)

    private fun syncNode(
        model: DefaultTreeModel,
        node: DefaultMutableTreeNode,
        value: T,
        walkEvery: Boolean,
    ): Boolean {
        val carried = carriedBy(node)
        if (!walkEvery && carried.value === value) return false
        val childValues = children(value)
        val text = label(value)
        val branch = childValues.isNotEmpty() || (hasChildren?.invoke(value) ?: true)
        val shown = carried.toString() != text || node.allowsChildren != branch
        // A node that allows no children throws away the ones it holds, so it is widened before children
        // arrive and narrowed once the ones it had are gone.
        if (branch) node.allowsChildren = true
        val moved = syncChildren(model, node, childValues, walkEvery)
        if (!branch) node.allowsChildren = false
        carried.carry(value, text)
        if (shown) model.nodeChanged(node)
        return moved
    }

    /**
     * Settles [node]'s children on [childValues]. The children the data hands over unchanged at the front
     * and at the back keep their nodes; of what lies between, as many nodes as there are values left take
     * those values over, and the surplus on either side is removed or inserted.
     */
    private fun syncChildren(
        model: DefaultTreeModel,
        node: DefaultMutableTreeNode,
        childValues: List<T>,
        walkEvery: Boolean,
    ): Boolean {
        val had = node.childCount
        val has = childValues.size
        var head = 0
        while (head < had && head < has && carriedBy(node.getChildAt(head)).value === childValues[head]) {
            head++
        }
        var tail = 0
        while (
            head + tail < had &&
            head + tail < has &&
            carriedBy(node.getChildAt(had - 1 - tail)).value === childValues[has - 1 - tail]
        ) {
            tail++
        }
        val reused = minOf(had, has) - head - tail
        val firstStale = head + reused
        val removed = removeChildren(model, node, firstStale, had - tail - firstStale)
        val inserted = insertChildren(model, node, firstStale, childValues.subList(firstStale, has - tail))
        var moved = removed || inserted
        for (index in 0 until has) {
            if (syncNode(model, node.getChildAt(index) as DefaultMutableTreeNode, childValues[index], walkEvery)) {
                moved = true
            }
        }
        return moved
    }

    private fun removeChildren(
        model: DefaultTreeModel,
        node: DefaultMutableTreeNode,
        from: Int,
        count: Int,
    ): Boolean {
        if (count == 0) return false
        val positions = IntArray(count) { from + it }
        val taken = Array<Any>(count) { node.getChildAt(from + it) }
        repeat(count) { node.remove(from) }
        model.nodesWereRemoved(node, positions, taken)
        return true
    }

    private fun insertChildren(
        model: DefaultTreeModel,
        node: DefaultMutableTreeNode,
        from: Int,
        values: List<T>,
    ): Boolean {
        if (values.isEmpty()) return false
        for ((offset, value) in values.withIndex()) node.insert(buildNode(value), from + offset)
        model.nodesWereInserted(node, IntArray(values.size) { from + it })
        return true
    }

    /** The [TreeNodeValue] [node] carries; every node a value-driven [Tree] builds carries one. */
    private fun carriedBy(node: TreeNode): TreeNodeValue<T> {
        @Suppress("UNCHECKED_CAST")
        return (node as DefaultMutableTreeNode).userObject as TreeNodeValue<T>
    }
}

/**
 * The model a value-driven [Tree] builds from the caller's data, and walks each later structure into.
 *
 * An edit committed on a node is reported through [onNodeEdit] and changes nothing here: the node goes on
 * carrying the value it was built from, so the row follows the data alone and moves once a composition
 * supplies data that has moved. [onNodeEdit] is read through a [State], so the model reports to the callback
 * the composition last declared.
 */
internal class DeclaredTreeModel<T>(
    declared: TreeContent<T>,
    private val onNodeEdit: State<(value: T, path: List<Int>, newValue: Any?) -> Unit>,
) : DefaultTreeModel(declared.buildNode(declared.root), declared.hasChildren != null) {
    /** The content these nodes were last walked from. */
    var content: TreeContent<T> = declared

    /**
     * Whether [next] can be walked into these nodes. A node answers for its own leafness only while a branch
     * answer is declared, and that is settled when the model is built: content that withdraws the answer, or
     * makes one, asks for nodes of another shape and gets a model of its own.
     */
    fun accepts(next: TreeContent<T>): Boolean = (content.hasChildren == null) == (next.hasChildren == null)

    override fun valueForPathChanged(
        path: TreePath,
        newValue: Any?,
    ) {
        onNodeEdit.value(valueAt(path), pathToIndices(this, path), newValue)
    }
}

/**
 * The value the node at the end of [path] stands for. Every node a value-driven [Tree] builds carries a
 * [TreeNodeValue] holding a value of that tree's own element type, and these are the nodes of such a tree.
 */
internal fun <T> valueAt(path: TreePath): T {
    @Suppress("UNCHECKED_CAST")
    val carried = (path.lastPathComponent as DefaultMutableTreeNode).userObject as TreeNodeValue<T>
    return carried.value
}
