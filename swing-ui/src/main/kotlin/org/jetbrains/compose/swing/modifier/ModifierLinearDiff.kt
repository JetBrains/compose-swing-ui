package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.style.StyleElement
import org.jetbrains.compose.swing.style.collectsNothingUnder
import org.jetbrains.compose.swing.style.requireKindTarget
import java.awt.Component

/**
 * Diffs [modifier] onto this node unless the slots take it in place, which is what [linearDiff] answers -
 * and, for the part of a modifier that is read live rather than applied, is what hands the pass's own over.
 *
 * The two questions are not the same one asked twice. A modifier reaching here is one the caller declared
 * anew - a callback rebuilt for this pass is enough to make it that - and what this asks is whether applying
 * it takes more than writing each changed slot its new element.
 *
 * A node with no modifier state yet has applied no modifier, so its first declaration diffs; a node
 * whose state was reset - released, reused, parked - is in that same position and rebuilds from scratch. A node
 * whose last diff threw partway diffs its next declaration too.
 */
internal fun SwingNodeHolder<Component>.applyDeclaredModifier(modifier: SwingModifier) {
    val state = modifierState
    var needsDeclaredNodes = false
    if (state != null) {
        val held = state.startWrite()
        if (held !is UnfinishedPass) {
            val handedNodes = state.handedNodes
            val diff = linearDiff(held, modifier, state.records)
            if (!diff.aborted) {
                state.adopt(modifier)
                notifyDeclaredNodes(diff.needsDeclaredNodes, handedNodes)
                return
            }
            needsDeclaredNodes = diff.needsDeclaredNodes
        }
        state.restore(held)
    }
    applyModifierDiff(modifier, needsDeclaredNodes)
}

/**
 * What [linearDiff] has paired so far: the next physical record position [nextRecordIndex], whether the diff
 * [aborted], so no slot past that point is asked, and whether a slot it wrote holds a node the holder's
 * [DeclaredNodesListener] needs ([needsDeclaredNodes]), which hands the nodes over once the walk ends.
 */
@JvmInline
private value class LinearDiff private constructor(
    private val bits: Int,
) {
    companion object {
        private const val NEEDS_DECLARED_NODES = 1
        private const val RECORD_INDEX_SHIFT = 1

        inline val Start: LinearDiff get() = LinearDiff(0)
    }

    inline val nextRecordIndex: Int get() = (bits and Int.MAX_VALUE) ushr RECORD_INDEX_SHIFT

    inline val needsDeclaredNodes: Boolean get() = bits and NEEDS_DECLARED_NODES != 0

    inline val aborted: Boolean get() = bits < 0

    /**
     * One more record matched; [needsDeclaredNodes] where its write holds a node the listener needs, and from then
     * on.
     */
    fun next(
        nextRecordIndex: Int,
        needsDeclaredNodes: Boolean,
    ): LinearDiff {
        val needs = if (needsDeclaredNodes) NEEDS_DECLARED_NODES else bits and NEEDS_DECLARED_NODES
        return LinearDiff((nextRecordIndex shl RECORD_INDEX_SHIFT) or needs)
    }

    /** The walk parting ways here, keeping [needsDeclaredNodes]. */
    fun abort(): LinearDiff = LinearDiff(bits or Int.MIN_VALUE)

    /** The same position, [needsDeclaredNodes] from here on where the last write holds a node the listener needs. */
    fun withNeedsDeclaredNodes(needsDeclaredNodes: Boolean): LinearDiff =
        if (needsDeclaredNodes) LinearDiff(bits or NEEDS_DECLARED_NODES) else this
}

/**
 * Walks [applied] and [next] in lockstep from [diff], handing the slot behind each element of [applied] the
 * element [next] declares in its place, and answers how far it paired them, [aborted][LinearDiff.aborted] as soon as
 * the two modifiers differ in a way only the diff can apply. The diff that follows pairs every element with its slot
 * by key and by position, reusing as it goes.
 *
 * An element holding a record is paired with the next standing positional record of [records]: this walk follows
 * [SwingModifier.foldIn], so the records it reaches in order are the chain's. Every other element - a keyed one, or a
 * declaration read by the parent - is compared by equality, since a keyed record's write has every later keyed record
 * write again, which the diff alone orders.
 *
 * A record is handed an element only where it holds the very element [applied] declares, so a walk taking another
 * path than the fold writes no record that is not its own: it answers [LinearDiff.aborted] there instead, and the diff
 * behind it pairs everything correctly. A record the walk wrote before parting ways holds its new element, which the
 * diff then reuses; where [LinearDiff.needsDeclaredNodes], the diff hands the nodes over.
 */
private fun SwingNodeHolder<Component>.linearDiff(
    applied: SwingModifier,
    next: SwingModifier,
    records: NodeRecords?,
    diff: LinearDiff = LinearDiff.Start,
    last: Boolean = true,
): LinearDiff =
    when {
        applied is CombinedSwingModifier && next is CombinedSwingModifier -> {
            val outer = linearDiff(applied.outer, next.outer, records, diff, last = false)
            if (outer.aborted) outer else linearDiff(applied.inner, next.inner, records, outer, last)
        }

        last && applied is StyleElement<*, *> && next is StyleElement<*, *> && applied != next -> {
            reuseLastStyle(applied, next, records, diff)
        }

        applied is SwingModifier.Element && applied.isPositionalElement && next is SwingModifier.Element -> {
            diffElement(applied, next, records, diff)
        }

        else -> {
            if (applied == next) diff else diff.abort()
        }
    }

/**
 * Hands [next] the slot of [applied], the last element of the chain, in place. Nothing keyed is written after the
 * last element, so the slot is written as the diff would write it. A declaration that held no slot because it could
 * collect nothing is reused while [next] can collect nothing either. Answers [LinearDiff.aborted] where the node
 * holds more than one style slot or the slot holds another element.
 */
private fun SwingNodeHolder<Component>.reuseLastStyle(
    applied: StyleElement<*, *>,
    next: StyleElement<*, *>,
    records: NodeRecords?,
    diff: LinearDiff,
): LinearDiff {
    val state = modifierState
    val record = records?.get(ComponentNodeType, null, applied.key)
    return when {
        state == null || next.key != applied.key || state.firstStyle?.nextStyle != null -> {
            diff.abort()
        }

        record == null -> {
            if (next.collectsNothingUnder(this)) {
                requireKindTarget(next.kind, component)
                diff
            } else {
                diff.abort()
            }
        }

        record.element !== applied || !NodeRecord.canUpdate(record, next) -> {
            diff.abort()
        }

        else -> {
            diff.withNeedsDeclaredNodes(record.updateTo(next, component, owner?.diagnostics, WriteMode.ReuseEqual))
        }
    }
}

/**
 * Hands [next] to the next positional record from [LinearDiff.nextRecordIndex], where [applied] is
 * the element it holds. The record reuses an element that declares what [applied] does, and is written
 * in place with one unequal but of [applied]'s own class, as androidx's `NodeChain.updateNode` updates
 * a node in place. Answers [LinearDiff.aborted] where the record holds another element, or where [next]
 * is of another class. A parent-layout record written so that its parent lays the component out again
 * declares it again to a parent that receives the record's node; a record written with a node the holder's
 * [DeclaredNodesListener] needs has the nodes handed over once the walk ends.
 */
private fun SwingNodeHolder<Component>.diffElement(
    applied: SwingModifier.Element,
    next: SwingModifier.Element,
    records: NodeRecords?,
    diff: LinearDiff,
): LinearDiff {
    val nextRecordIndex = records?.nextPositionalIndex(diff.nextRecordIndex) ?: 0
    val record = if (records != null && nextRecordIndex < records.size) records[nextRecordIndex] else null
    return when {
        record == null || record.element !== applied || !next.isPositionalElement -> {
            diff.abort()
        }

        record.reuse(next) -> {
            diff.next(checkNotNull(records).indexAfter(record, nextRecordIndex), needsDeclaredNodes = false)
        }

        !NodeRecord.canUpdate(record, next) -> {
            diff.abort()
        }

        else -> {
            val invalidates =
                record.updateTo(
                    next,
                    component,
                    owner?.diagnostics,
                    mode = WriteMode.ReuseEqual,
                )
            if (record.updateChanges(this, invalidates).parentLayoutUpdated) {
                declaration.reapply()
            }
            diff.next(checkNotNull(records).indexAfter(record, nextRecordIndex), invalidates)
        }
    }
}
