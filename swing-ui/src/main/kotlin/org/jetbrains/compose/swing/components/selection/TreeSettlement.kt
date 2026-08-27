package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.node.MirrorState
import javax.swing.JTree
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

/*
 * How a later structure - or a later declaration - reaches a tree already showing one.
 *
 * Three roles carry one settling between them. The declarations are what the composition states this
 * pass - a selection, an expansion, and the listener a loss is reported to. The mirrors are what the
 * tree was last known to hold, one per facet, and are what let a listener tell the wrapper's own write
 * from a change the user made. The outcome is what the write left standing, which is not always what was
 * declared: a collapse takes over the selection it hides, and a structure the model no longer holds
 * drops the paths naming it.
 */

/**
 * What a tree settles its two declarations through, for the life of one node: the mirror of each facet, and
 * the nodes a collapse was last reported to have taken over.
 *
 * The mirrors travel together because one write changes both - installing a model, applying a collapse -
 * and each has to see the other's write in flight for its listener to tell the wrapper's doing from the
 * user's.
 */
internal class TreeMirrors(
    val selection: MirrorState<Set<List<Int>>?>,
    val expansion: MirrorState<Set<List<Int>>?>,
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

    /** The write changed them without naming them, so the tree is what answers for them. */
    data object Unnamed : SettledExpansion
}

/**
 * Installs [newModel], keeping the nodes the declared selection names selected - or, where the caller
 * declared nothing, the nodes the user had - and reporting the nodes the new structure no longer has. See
 * [installNarrowing].
 *
 * Installing a model re-opens the root as well. Where the caller declared an expansion, it is asserted on
 * the new structure right away, so it stands from the moment the model is in, and the selection that goes
 * back on is narrowed to the nodes that expansion leaves showing - the pairing [applyDeclarations] settles
 * a standing tree on, reached here through the same [applyVisibleSelection], and reported the same way.
 * Where none was declared, the expansion is not the library's to decide, and the expansion the tree held is
 * put back whole instead - the nodes that were open are opened and every other one is closed - so a node
 * the user had collapsed does not come back open. A tree that had no root yet has nothing retained, and
 * keeps the expansion a `JTree` gives a model it is handed.
 *
 * Both the selection narrowing and the expansion restore run as one settlement of each mirror in
 * [declarations] - installing a model can change both, and each mirror has to see its own write coming for
 * its listener to tell it apart from the user's, and what the tree is left showing open is what this
 * install asked for and read back.
 */
internal fun JTree.installModel(
    declarations: TreeDeclarations,
    newModel: TreeModel,
) {
    val declaredExpansion = declarations.declaredExpansion
    val expansion = declarations.mirrors.expansion
    val hadRoot = model?.root != null
    // Read before the model is replaced, and only where it is what gets applied: a caller that declares
    // an expansion has already said what the new model is to show, and the walk this saves covers every
    // node the old one held open.
    val keptExpansion = if (declaredExpansion == null && hadRoot) readExpansion(this, model) else null
    val held = heldSelection(named = declarations.declaredSelection == null)
    val hidden =
        expansion.settle {
            val taken =
                applySelectionAcross(declarations, held) {
                    model = newModel
                    (declaredExpansion ?: keptExpansion)?.let { applyExpansion(this@installModel, newModel, it) }
                }
            answered(readExpansion(this@installModel, model))
            taken
        }
    reportNarrowing(declarations, hidden, held.lead)
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
        val structureChanged = content.syncInto(standing, walkEvery)
        standing.content = content
        applyDeclarations(declarations, structureChanged)
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
    val held = heldSelection(named = declaredSelection == null)
    var outcome = TreeSettleOutcome(emptySet(), SettledExpansion.Standing)
    // The write and the read-backs that record what survived it are one settlement of each mirror, which
    // is what the nested brackets state. Each mirror's write stays inside its own bracket: a settlement
    // marks the changes it made as answered, and the write is what a listener reads to tell the wrapper's
    // doing from the user's.
    mirrors.expansion.settle openness@{
        mirrors.selection.settle {
            mirrors.expansion.write { mirrors.selection.write { outcome = settle() } }
            // What the write left is recorded as the answer to the declarations it applied, not as news:
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
    if (declaredSelection == null) {
        reportDropped(declarations.target, held.nodes, held.indices, held.lead)
    } else {
        reportNarrowing(declarations, outcome.hiddenSelection, held.lead)
    }
}

/**
 * Re-asserts on the tree what [declarations] declares: a declaration is the composition's state, so a user
 * change the caller does not adopt is undone, while an undeclared selection or expansion is left standing.
 * Answers with the selected paths a declared collapse took over - see [applyVisibleSelection] - and with
 * the nodes the tree is left showing open. [structureChanged] says whether the write this runs inside took a
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
    structureChanged: Boolean,
): TreeSettleOutcome {
    val declaredExpansion = declarations.declaredExpansion
    if (declaredExpansion == null) {
        val selected = applySelection(this, model, declarations.declaredSelection)
        // A tree opens what it must to show a node it is given to select, and a structure change renames by
        // index every node after the one it moved: either leaves the tree to answer for what it shows open.
        val expansion = if (selected || structureChanged) SettledExpansion.Unnamed else SettledExpansion.Standing
        return TreeSettleOutcome(emptySet(), expansion)
    }
    val opened = applyExpansion(this, model, declaredExpansion)
    val hidden = applyVisibleSelection(this, model, declarations.declaredSelection)
    return TreeSettleOutcome(hidden, opened?.let { SettledExpansion.Named(it) } ?: SettledExpansion.Unnamed)
}

/**
 * Gives the tree new content through [install] and puts back the selection that should stand around it -
 * see [installNarrowing] - as a write of the expansion mirror too. Answers with the selected paths a
 * declared collapse took over, the way [applySelectionUnder] does, and hands a loss the new structure
 * cannot hold to the listener [declarations] names, reported as the nodes [held] was captured with.
 */
private fun JTree.applySelectionAcross(
    declarations: TreeDeclarations,
    held: HeldSelection,
    install: () -> Unit,
): Set<List<Int>> {
    var hidden = emptySet<List<Int>>()
    val target = declarations.target
    declarations.mirrors.expansion.write {
        declarations.mirrors.selection.installNarrowing(
            declared = declarations.declaredSelection,
            selection = { readSelection(this, model) },
            apply = { paths -> hidden = applySelectionUnder(declarations.declaredExpansion, paths) },
            report = { lost -> reportLostPaths(target, held.nodes, held.indices, lost, held.lead) },
            install = install,
        )
    }
    return hidden
}

/**
 * Re-applies [selectedPaths] as the tree's selection under [declaredExpansion], and answers with the paths
 * a closed node stood in for. Under a declared expansion the selection is narrowed to the nodes that
 * expansion leaves showing - see [applyVisibleSelection]; under the tree's own it is applied as it stands,
 * which lets the tree open what it must to show a node it is given, and takes nothing over.
 */
private fun JTree.applySelectionUnder(
    declaredExpansion: Set<List<Int>>?,
    selectedPaths: Set<List<Int>>?,
): Set<List<Int>> {
    if (declaredExpansion == null) {
        applySelection(this, model, selectedPaths)
        return emptySet()
    }
    return applyVisibleSelection(this, model, selectedPaths)
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
