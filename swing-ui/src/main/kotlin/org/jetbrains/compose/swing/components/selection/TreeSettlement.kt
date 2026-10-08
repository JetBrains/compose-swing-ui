package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.util.fastFirstOrNull
import org.jetbrains.compose.swing.util.fastForEach
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

/*
 * How a later structure - or a later declaration - reaches a tree already showing one.
 *
 * Three roles carry one settling between them. The declarations are what the composition states this
 * pass - a selection, the state holding the expansion, and the listeners a change is reported to. The mirrors
 * are what the tree was last known to hold, one per facet, and are what let a listener tell the wrapper's
 * own write from a change the user made. The outcome is what the write left standing, which is not always
 * what was declared: a collapse takes over the selection it hides, and a structure the model no longer
 * holds drops the paths naming it.
 */

/**
 * What a tree settles through, for the life of one node: the mirror of each facet, and the expansion last
 * in sync with the [TreeState] holding it.
 *
 * The mirrors travel together because one write changes both - installing a model, applying a collapse -
 * and each has to see the other's write in flight for its listener to tell the wrapper's doing from the
 * user's.
 */
internal class TreeMirrors(
    val selection: MirrorState<Set<List<Int>>?>,
    val expansion: MirrorState<Set<List<Int>>>,
) {
    /**
     * The expansion last in sync between the tree and the [TreeState] holding it, `null` until one is
     * applied. Applying the state's expansion and writing the user's change back into the state both set
     * it, so the state's expansion is applied only when it differs from this: when it was assigned. A
     * revert assigned in the same frame as the user's change differs from it too, and is applied.
     */
    var appliedExpansion: Set<List<Int>>? = null
}

/**
 * What one settling of a tree runs on: the mirrors it goes through, the declared selection, the [state]
 * holding the expansion - `null` where the expansion is the user's - and the listener a selection loss is
 * handed to. Where no [state] holds the expansion, the nodes the tree opens to show a selection are reported
 * to the raw [expansionTarget] one event per node, and to [onExpansionChange] once, with the expansion they
 * leave.
 */
internal class TreeDeclarations(
    val mirrors: TreeMirrors,
    val declaredSelection: Set<List<Int>>?,
    val state: TreeState?,
    val target: TreeSelectionListener,
    val expansionTarget: TreeExpansionListener?,
    val onExpansionChange: ((Set<List<Int>>) -> Unit)?,
)

/**
 * What one settling write left a tree on: what the write can say of the nodes the tree is left showing
 * open, whether a collapse of the state's took over the selection the tree held when the settling began,
 * and the nodes that were closed above the selection the write gave the tree, read before the tree opened
 * them.
 */
internal class TreeSettleOutcome(
    val expansion: SettledExpansion,
    val collapseTookOver: Boolean,
    val closedAbove: List<TreePath>,
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
 * Installing a model re-opens the root as well, so the expansion is put back on the new structure: the one
 * the [TreeState] in [declarations] holds, or, without a state, the one the tree held, whole - the nodes
 * that were open are opened and every other one is closed, so a node the user had collapsed does not come
 * back open. A tree that had no root yet and no state has nothing retained, and keeps the expansion a
 * `JTree` gives a model it is handed. The selection goes back on after the expansion, and the tree opens
 * what it has to for that selection to show - reported as [settleSelection] reports it.
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
    val mirrors = declarations.mirrors
    val target = declarations.target
    // Read before the model is replaced, and only where it is what gets applied: a state already holds
    // what the new model is to show, and the walk this saves covers every node the old one held open.
    val restored =
        declarations.state?.expandedPaths ?: model?.root?.let { readExpansion(this, model) }
    val held = heldSelection(named = declarations.declaredSelection == null)
    var closed = emptyList<TreePath>()
    var open = emptySet<List<Int>>()
    mirrors.expansion.settle {
        mirrors.expansion.write {
            mirrors.selection.installNarrowing(
                declared = declarations.declaredSelection,
                selection = { readSelection(this@installModel, model) },
                apply = { paths ->
                    val resolved = resolveSelection(model, paths)
                    closed = closedAbove(resolved)
                    selectNodes(this@installModel, resolved)
                },
                report = { lost -> reportLostPaths(target, held.nodes, held.indices, lost, held.lead) },
                install = {
                    model = newModel
                    restored?.let { applyExpansion(this@installModel, newModel, it) }
                },
            )
        }
        open = readExpansion(this@installModel, model)
        answered(open)
        declarations.recordExpansion(model, open)
    }
    reportOpened(declarations, closed, open)
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
 *
 * An edit the tree is showing over a node this walk hands another value to ends here, so a commit only ever
 * reaches the value the editor was opened on. An edit over a node the walk leaves alone stands, and one
 * over a node it takes out of the structure the tree ends for itself.
 */
internal fun <T> JTree.updateContent(
    declarations: TreeDeclarations,
    standing: DeclaredTreeModel<T>,
    content: TreeContent<T>,
) {
    val walkEvery = !standing.content.walksAlike(content)
    settleSelection(declarations) {
        val edited = if (isEditing) editingPath else null
        val editedValue = edited?.let { valueAt<T>(it) }
        val structureChanged = content.syncInto(standing, walkEvery)
        // An editor names a node, and the walk hands a node over to the value that now stands for it
        // without the tree hearing of it: a node changed reaches `BasicTreeUI` as a repaint alone, so an
        // edit left standing would commit through `valueForPathChanged` against the value that took the
        // node over. The node is read back after the walk rather than predicted from the new data: a
        // value handed over unchanged keeps its own node whatever moved in front of it, so what the walk
        // did to that node decides.
        if (edited != null && isEditing && valueAt<T>(edited) != editedValue) cancelEditing()
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
 * narrowing the widget makes for itself is not reported - all but the nodes a collapse of the state's took
 * over, which [settle] answers with: the tree resolved that collapse the way it resolves the user's, and
 * the state takes the selection it was left with.
 *
 * The expansion mirror is left on what [settle] says the write left open, and the structure is walked for
 * it only where the write cannot say - see [SettledExpansion]. Where no [TreeState] holds the expansion it
 * is the user's, and the nodes the tree opened to show the selection are reported - see [reportOpened].
 *
 * Each listener is reached once the write has returned, so what it is told is final, and contained the way
 * every caller callback reached from a pass is.
 */
internal fun JTree.settleSelection(
    declarations: TreeDeclarations,
    settle: JTree.() -> TreeSettleOutcome,
) {
    val mirrors = declarations.mirrors
    val held = selectionPaths
    val lead = leadSelectionPath
    // A listener's failure cuts the write short and leaves this outcome standing, over a tree the write may
    // have partly changed: only reading the tree back names what it shows open then.
    var outcome = TreeSettleOutcome(SettledExpansion.Unnamed, collapseTookOver = false, closedAbove = emptyList())
    var open: Set<List<Int>>? = null
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
            open =
                when (val settled = outcome.expansion) {
                    SettledExpansion.Standing -> null
                    is SettledExpansion.Named -> settled.open
                    SettledExpansion.Unnamed -> readExpansion(this@settleSelection, model)
                }
            // The write never touched the expansion, so the mirror already holds what the tree holds and
            // there is nothing to read back.
            val settledOpen = open
            if (settledOpen == null) {
                this@openness.unchanged()
            } else {
                this@openness.answered(settledOpen)
                declarations.recordExpansion(model, settledOpen)
            }
        }
    }
    if (held != null && (declarations.declaredSelection == null || outcome.collapseTookOver)) {
        reportDropped(declarations.target, held, lead)
    }
    reportOpened(declarations, outcome.closedAbove, open)
}

/**
 * Re-asserts on the tree what [declarations] declares: a declared selection is the composition's state, so
 * a user change the caller does not adopt is undone, while an undeclared one is left standing. The
 * expansion a [TreeState] holds is applied where it was assigned since it was last in sync with the tree,
 * and where [structureChanged] says the write this runs inside took a node out of the structure or put one
 * in; without a state the expansion is the user's and is left standing.
 *
 * The expansion is applied before the selection, and a collapse that hides selected nodes is resolved the
 * way a `JTree` resolves the user's: the closed node takes over the selection. Where the declared selection
 * is the one the tree already held, that resolution stands and is answered with, for the state to take.
 * A selection declared afresh goes on after it, and the tree opens what it has to for that selection to
 * show, as it does with any selection it is given.
 */
internal fun JTree.applyDeclarations(
    declarations: TreeDeclarations,
    structureChanged: Boolean,
): TreeSettleOutcome {
    val resolved = declarations.declaredSelection?.let { resolveSelection(model, it) }
    val expansion =
        declarations.state?.expandedPaths?.takeIf {
            structureChanged || it != declarations.mirrors.appliedExpansion
        }
    var collapseTookOver = false
    var opened: Set<List<Int>>? = null
    if (expansion != null) {
        val before = selectionPaths.orEmpty()
        opened = applyExpansion(this, model, expansion)
        // A collapse took the selection over where it moved the selection off the nodes the tree held, and
        // those are the nodes declared. A structure change renames nodes by position, so the selection
        // declared by position is what goes back on there, never the nodes the tree held before.
        collapseTookOver =
            !structureChanged &&
            resolved != null &&
            !holdsExactly(before, selectionPaths.orEmpty().asList()) &&
            holdsExactly(before, resolved)
    }
    val closed = closedAbove(resolved.orEmpty())
    val selected = !collapseTookOver && resolved != null && selectNodes(this, resolved)
    // A tree opens what it must to show a node it is given to select, and a structure change renames by
    // index every node after the one it moved: either leaves the tree to answer for what it shows open.
    val settled =
        when {
            selected || structureChanged -> SettledExpansion.Unnamed
            opened != null -> SettledExpansion.Named(opened)
            expansion != null -> SettledExpansion.Unnamed
            else -> SettledExpansion.Standing
        }
    return TreeSettleOutcome(settled, collapseTookOver, closed)
}

/**
 * Records [open] - what a settling write left the tree showing open in [model] - as the expansion in sync
 * with the [TreeState] in this declaration, where there is one.
 *
 * A node the state names opens with every node above it, and those are not written back: the state holds
 * the expansion as it was assigned. Any other open node was opened by the tree itself, to show a node it was
 * given to select, and is written into the state the way the user's opening is. A node the state names and
 * the tree could not open stays named, so a structure that gains it opens it.
 */
private fun TreeDeclarations.recordExpansion(
    model: TreeModel,
    open: Set<List<Int>>,
) {
    val state = state ?: return
    val held = state.expandedPaths
    val synced =
        if (held.containsAll(open)) {
            held
        } else {
            val opensWithHeld = nodesToOpen(model, held).keys
            val added = open.filterNot { it in opensWithHeld }
            if (added.isEmpty()) held else held + added
        }
    mirrors.appliedExpansion = synced
    state.expandedPaths = synced
}

/**
 * Reports the nodes of [opened] the tree now shows open - a node a listener refused stays closed and is left
 * out. The raw [expansion listener][TreeDeclarations.expansionTarget] hears each of them, shallowest first,
 * as the event a tree fires for a node it opens. [TreeDeclarations.onExpansionChange] hears them once, with
 * [open], the expansion they leave: the event a tree fires per node would hand it the same expansion again
 * for every node.
 */
private fun JTree.reportOpened(
    declarations: TreeDeclarations,
    opened: List<TreePath>,
    open: Set<List<Int>>?,
) {
    if (opened.isEmpty()) return
    declarations.expansionTarget?.let { target ->
        dispatchToCaller {
            opened.fastForEach { node -> if (isExpanded(node)) target.treeExpanded(TreeExpansionEvent(this, node)) }
        }
    }
    val onExpansionChange = declarations.onExpansionChange ?: return
    if (open != null && opened.fastFirstOrNull { isExpanded(it) } != null) dispatchToCaller { onExpansionChange(open) }
}
