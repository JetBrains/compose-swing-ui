package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.node.SwingNodeHolder
import java.awt.Component

/**
 * Diffs [modifier] onto this node unless the slots take it in place, which is what [adoptDeclaration] answers -
 * and, for the part of a modifier that is read live rather than applied, is what hands the pass's own over.
 *
 * The two questions are not the same one asked twice. A modifier reaching here is one the caller declared
 * anew - a callback rebuilt for this pass is enough to make it that - and what this asks is whether applying
 * it takes more than writing each changed slot its new element.
 *
 * A node with no modifier state yet has applied no modifier, so its first declaration always diffs; a node
 * whose state was reset - released, reused, parked - is in that same position and rebuilds from scratch. A node
 * whose last diff threw partway diffs its next declaration too.
 */
internal fun SwingNodeHolder<Component>.applyDeclaredModifier(modifier: SwingModifier) {
    val state = modifierState
    var handsOver = false
    if (state != null) {
        val held = state.startWrite()
        if (held !is UnfinishedPass) {
            val adopted = adoptDeclaration(held, modifier, state.records)
            if (!adopted.diverged) {
                val handedNodes = state.handedNodes
                state.adopt(modifier)
                notifyDeclaredNodes(adopted.handsOver, handedNodes)
                return
            }
            handsOver = adopted.handsOver
        }
        state.restore(held)
    }
    applyModifierDiff(modifier, handsOver)
}

/**
 * What [adoptDeclaration] has paired so far: the next physical record position [nextRecordIndex], whether the modifiers
 * [diverged], so no slot past that point is asked, and whether a slot it wrote holds a node the holder's
 * [DeclaredNodesListener] needs ([handsOver]), which hands the nodes over once the walk ends.
 */
@JvmInline
private value class LinearDiff private constructor(
    private val bits: Int,
) {
    companion object {
        private const val HANDOVER_MASK = 1
        private const val RECORD_INDEX_SHIFT = 1

        inline val Start: LinearDiff get() = LinearDiff(0)
    }

    inline val nextRecordIndex: Int get() = (bits and Int.MAX_VALUE) ushr RECORD_INDEX_SHIFT

    inline val handsOver: Boolean get() = bits and HANDOVER_MASK != 0

    inline val diverged: Boolean get() = bits < 0

    /** One more record matched; [handsOver] where its write holds a node the listener needs, and from then on. */
    fun nextRecord(
        nextRecordIndex: Int,
        handsOver: Boolean,
    ): LinearDiff {
        val handover = if (handsOver) HANDOVER_MASK else bits and HANDOVER_MASK
        return LinearDiff((nextRecordIndex shl RECORD_INDEX_SHIFT) or handover)
    }

    /** The walk parting ways here, keeping [handsOver]. */
    fun diverge(): LinearDiff = LinearDiff(bits or Int.MIN_VALUE)
}

/**
 * Walks [applied] and [next] in lockstep from [adopted], handing the slot behind each element of [applied] the
 * element [next] declares in its place, and answers how far it paired them, [diverged][LinearDiff.diverged] as soon as
 * the two modifiers differ in a way only the diff can apply. The diff that follows pairs every element with its slot
 * by key and by position, adopting as it goes.
 *
 * An element holding a record is paired with the next standing positional record of [records]: this walk follows
 * [SwingModifier.foldIn], so the records it reaches in order are the chain's. Every other element - a keyed one, or a
 * declaration read by the parent - is compared by equality, since a keyed record's write has every later keyed record
 * write again, which the diff alone orders.
 *
 * A record is handed an element only where it holds the very element [applied] declares, so a walk taking another
 * path than the fold writes no record that is not its own: it answers [LinearDiff.diverged] there instead, and the diff
 * behind it pairs everything correctly. A record the walk wrote before parting ways holds its new element, which the
 * diff then adopts; where [LinearDiff.handsOver], the diff hands the nodes over.
 */
private fun SwingNodeHolder<Component>.adoptDeclaration(
    applied: SwingModifier,
    next: SwingModifier,
    records: NodeRecords?,
    adopted: LinearDiff = LinearDiff.Start,
): LinearDiff =
    when {
        applied is CombinedSwingModifier && next is CombinedSwingModifier -> {
            val outer = adoptDeclaration(applied.outer, next.outer, records, adopted)
            if (outer.diverged) outer else adoptDeclaration(applied.inner, next.inner, records, outer)
        }

        applied is SwingModifier.Element && applied.isPositionalElement && next is SwingModifier.Element -> {
            adoptElement(applied, next, records, adopted)
        }

        else -> {
            if (applied == next) adopted else adopted.diverge()
        }
    }

/**
 * Hands [next] to the next positional record from [LinearDiff.nextRecordIndex], where [applied] is
 * the element it holds. The record adopts an element that declares what [applied] does, and is written
 * in place with one unequal but of [applied]'s own class, as androidx's `NodeChain.updateNode` updates
 * a node in place. Answers [LinearDiff.diverged] where the record holds another element, or where [next]
 * is of another class. A parent-layout record written so that its parent lays the component out again
 * declares it again to a parent that receives the record's node; a record written with a node the holder's
 * [DeclaredNodesListener] needs has the nodes handed over once the walk ends.
 */
private fun SwingNodeHolder<Component>.adoptElement(
    applied: SwingModifier.Element,
    next: SwingModifier.Element,
    records: NodeRecords?,
    adopted: LinearDiff,
): LinearDiff {
    val nextRecordIndex = records?.nextPositionalIndex(adopted.nextRecordIndex) ?: 0
    val record = if (records != null && nextRecordIndex < records.size) records[nextRecordIndex] else null
    return when {
        record == null || record.element !== applied || !next.isPositionalElement -> {
            adopted.diverge()
        }

        record.adopt(next) -> {
            adopted.nextRecord(checkNotNull(records).indexAfter(record, nextRecordIndex), handsOver = false)
        }

        !NodeRecord.canRebind(record, next) -> {
            adopted.diverge()
        }

        else -> {
            val invalidates =
                record.rebindAndWrite(
                    next,
                    component,
                    owner?.diagnostics,
                    mode = WriteMode.AdoptEqual,
                )
            if (record.updateChanges(this, invalidates).parentLayoutUpdated) {
                declaration.reapply()
            }
            adopted.nextRecord(checkNotNull(records).indexAfter(record, nextRecordIndex), invalidates)
        }
    }
}
