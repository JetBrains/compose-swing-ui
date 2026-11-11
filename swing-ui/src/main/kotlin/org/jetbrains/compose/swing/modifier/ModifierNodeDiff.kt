// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.core.collectFailure
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.util.fastFirstOrNull
import org.jetbrains.compose.swing.util.fastForEach
import org.jetbrains.compose.swing.util.fastForEachIn
import org.jetbrains.compose.swing.util.fastForEachIndexed
import java.awt.Component

/** The record lookups owned by one modifier element family, whose records are [R]. */
internal sealed class ModifierNodeType<R : NodeRecord<*, *>> {
    /** [record] as this family's record type; fails when the record belongs to another family. */
    abstract fun typed(record: NodeRecord<*, *>): R

    /** Fails for a [record] that does not belong to this family. */
    protected fun foreign(record: NodeRecord<*, *>): Nothing =
        error("${record::class.simpleName} does not belong to the ${this::class.simpleName} family")

    /**
     * Whether [records] holds a standing record of this family for [declaringNode].
     *
     * @param records the records searched.
     * @param declaringNode the node declaring the records, or `null` for the outer modifier.
     */
    fun hasRecords(
        records: NodeRecords?,
        declaringNode: SwingModifier.Node?,
    ): Boolean = records?.fastFirstOrNull { it.isStandingFor(this, declaringNode) } != null
}

/** Lookup for component elements. */
internal object ComponentNodeType : ModifierNodeType<ComponentNodeRecord<*, *>>() {
    override fun typed(record: NodeRecord<*, *>): ComponentNodeRecord<*, *> =
        if (record is ComponentNodeRecord<*, *>) record else foreign(record)

    /**
     * Whether [record] is a keyed declaration removed by [incoming].
     *
     * @param record the declaration whose removal is tested.
     * @param declaringNode the node declaring the records, or `null` for the outer modifier.
     * @param incoming the replacement declarations for that node.
     */
    fun departs(
        record: NodeRecord<*, *>,
        declaringNode: SwingModifier.Node?,
        incoming: List<SwingModifier.NodeElement<*, *>>,
    ): Boolean = record.isStandingFor(this, declaringNode) && record.key != null && !containsKey(incoming, record)

    private fun containsKey(
        incoming: List<SwingModifier.NodeElement<*, *>>,
        record: NodeRecord<*, *>,
    ): Boolean = incoming.fastFirstOrNull { it.key == record.key } != null

    /**
     * Whether [records] holds a standing component record for [declaringNode] whose node is detached.
     *
     * @param records the records searched.
     * @param declaringNode the node declaring the records, or `null` for the outer modifier.
     */
    fun hasDetached(
        records: NodeRecords?,
        declaringNode: SwingModifier.Node?,
    ): Boolean = records?.fastFirstOrNull { it.isStandingFor(this, declaringNode) && !it.node.isAttached } != null

    /**
     * Whether removing keyed declarations also removes layout nodes supplied to a consumer.
     *
     * @param records the records scanned for departing declarations that hold layout nodes.
     * @param declaringNode the node declaring the records, or `null` for the outer modifier.
     * @param incoming the replacement declarations for that node.
     */
    fun departingHoldsLayout(
        records: NodeRecords?,
        declaringNode: SwingModifier.Node?,
        incoming: List<SwingModifier.NodeElement<*, *>>,
    ): Boolean = records?.fastFirstOrNull { departs(it, declaringNode, incoming) && it.node.holdsLayoutNodes } != null

    /**
     * Refreshes this node's component declarations and returns whether a consumer needs invalidation.
     *
     * @param records the records holding the declarations to refresh.
     * @param holder the component holder supplying the component and its diagnostics.
     * @param declaringNode the node declaring the records, or `null` for the outer modifier.
     * @param mode whether equal declarations may be reused or must be written again.
     */
    fun refresh(
        records: NodeRecords?,
        holder: SwingNodeHolder<*>,
        declaringNode: SwingModifier.Node?,
        mode: WriteMode,
    ): Boolean {
        var invalidates = false
        records?.fastForEach { record ->
            if (record.isStandingFor(this, declaringNode) &&
                record.refresh(holder.component, holder.owner?.diagnostics, mode)
            ) {
                invalidates = true
            }
        }
        return invalidates
    }
}

/** Lookup for parent-layout elements. */
internal object ParentLayoutNodeType : ModifierNodeType<ParentLayoutNodeRecord>() {
    override fun typed(record: NodeRecord<*, *>): ParentLayoutNodeRecord =
        if (record is ParentLayoutNodeRecord) record else foreign(record)
}

/**
 * Updates the positional nodes of [partition] declared in [nodeType] by position within [nodeType], independently of
 * the other node type.
 * Removes excess nodes from the end and preserves compatible nodes. A replacement attaches before the old node
 * detaches; if attachment fails, the old record remains. Equal elements can be reused when [mode] permits it.
 *
 * @param holder the holder whose component the nodes target and whose owner supplies the diagnostics.
 * @param state the modifier state whose records are updated.
 * @param partition the partition whose positional elements are applied.
 * @param nodeType the family of positional elements to update.
 * @param mode whether equal declarations may be reused or must be written again.
 */
internal fun updateModifierNodes(
    holder: SwingNodeHolder<*>,
    state: SwingModifierState,
    partition: ModifierPartition,
    nodeType: ModifierNodeType<*>,
    mode: WriteMode,
): ModifierChanges {
    val diagnostics = holder.owner?.diagnostics
    val elements = partition.positional
    val families = partition.positionalFamilies
    val declared = partition.positionalCount(nodeType)
    var changes = state.records?.trimPositionalNodes(nodeType, declared, diagnostics) ?: ModifierChanges.None
    var nextRecordIndex = 0
    elements.fastForEachIndexed { index, element ->
        if (families[index] !== nodeType) return@fastForEachIndexed
        val current = state.records
        nextRecordIndex = current?.nextPositionalIndex(nextRecordIndex, nodeType) ?: 0
        val record = if (current != null && nextRecordIndex < current.size) current[nextRecordIndex] else null
        var standing = record
        when {
            record == null -> {
                standing = state.attach(holder, element, diagnostics)
                changes += ModifierChanges.NodesAddedOrRemoved
            }

            NodeRecord.canUpdate(record, element) -> {
                if (!mode.reuse(record, element)) changes += updateNode(holder, record, element, diagnostics)
            }

            else -> {
                standing = state.replace(holder, record, element, diagnostics)
                changes += ModifierChanges.NodesAddedOrRemoved
            }
        }
        nextRecordIndex = state.records?.indexAfter(checkNotNull(standing), nextRecordIndex) ?: 0
    }
    return changes
}

/** Marks and attaches a node for [element] in a new record, which stays standing only if attachment succeeds. */
private fun SwingModifierState.attach(
    holder: SwingNodeHolder<*>,
    element: SwingModifier.Element,
    diagnostics: SwingCompositionDiagnostics?,
): NodeRecord<*, *> {
    val attaching = NodeRecord.mark(element, holder)
    attaching.runAttachLifecycle(this, diagnostics)
    return attaching
}

/** Attaches a node for [element] before [record] detaches; if attachment fails, [record] remains. */
private fun SwingModifierState.replace(
    holder: SwingNodeHolder<*>,
    record: NodeRecord<*, *>,
    element: SwingModifier.Element,
    diagnostics: SwingCompositionDiagnostics?,
): NodeRecord<*, *> {
    val replacement = NodeRecord.mark(element, holder)
    try {
        replacement.runAttachLifecycle(this, diagnostics, record)
    } finally {
        if (replacement.isStanding) records?.detachAndRemove(record, diagnostics)
    }
    return replacement
}

private fun updateNode(
    holder: SwingNodeHolder<*>,
    record: NodeRecord<*, *>,
    element: SwingModifier.Element,
    diagnostics: SwingCompositionDiagnostics?,
): ModifierChanges {
    val invalidates = record.updateTo(element, holder.component, diagnostics, mode = WriteMode.ReuseEqual)
    return record.updateChanges(holder, invalidates)
}

/** Changes that require publishing updated nodes or parent-layout declarations. */
@JvmInline
internal value class ModifierChanges private constructor(
    private val bits: Int,
) {
    /** These changes together with [other]. */
    operator fun plus(other: ModifierChanges): ModifierChanges = ModifierChanges(bits or other.bits)

    /** Whether a node joined or left the chain. */
    inline val nodesAddedOrRemoved: Boolean get() = bits and NODES_ADDED_OR_REMOVED != 0

    /** Whether a node's listener reads nodes that were updated. */
    inline val listenerNeedsUpdatedNodes: Boolean get() = bits and LISTENER_NEEDS_UPDATED_NODES != 0

    /** Whether a node the parent reads was updated. */
    inline val parentLayoutUpdated: Boolean get() = bits and PARENT_LAYOUT_UPDATED != 0

    /** Whether the nodes declared to consumers have to be published again. */
    inline val needsDeclaredNodes: Boolean get() = nodesAddedOrRemoved || listenerNeedsUpdatedNodes

    companion object {
        private const val NODES_ADDED_OR_REMOVED: Int = 1
        private const val PARENT_LAYOUT_UPDATED: Int = 2
        private const val LISTENER_NEEDS_UPDATED_NODES: Int = 4

        /** No change. */
        val None: ModifierChanges = ModifierChanges(0)

        /** A node joined or left the chain. */
        val NodesAddedOrRemoved: ModifierChanges = ModifierChanges(NODES_ADDED_OR_REMOVED)

        /** A node the parent reads was updated. */
        val ParentLayoutUpdated: ModifierChanges = ModifierChanges(PARENT_LAYOUT_UPDATED)

        /** A node's listener reads nodes that were updated. */
        val ListenerNeedsUpdatedNodes: ModifierChanges = ModifierChanges(LISTENER_NEEDS_UPDATED_NODES)
    }
}

/**
 * Publishes a keyed component record before its attachment callback and first update.
 *
 * @param element the element the new record holds.
 * @param holder the holder whose modifier state receives the record.
 * @param key the key the record is held under.
 * @param declaringNode the node declaring the record, or `null` for the outer modifier.
 */
internal fun <T : Component, N : SwingModifier.ComponentNode<T>> attachElement(
    element: SwingModifier.NodeElement<T, N>,
    holder: SwingNodeHolder<*>,
    key: Any,
    declaringNode: SwingModifier.Node? = null,
): Boolean {
    val state = checkNotNull(holder.modifierState)
    val record = ComponentNodeRecord.mark(element, holder, key, declaringNode)
    return record.runAttachLifecycle(state, holder.owner?.diagnostics)
}

/**
 * Takes the departed keyed records apart together: every departed node runs its detach callback, in reverse order,
 * before any of them is marked detached, so a change event a restore fires never reaches a node already marked
 * detached. Then the outer declarations holding a released property are written again, and [rewriteReleased]
 * receives the released property names.
 */
private inline fun detachDeparted(
    records: NodeRecords?,
    incoming: List<SwingModifier.NodeElement<*, *>>,
    diagnostics: SwingCompositionDiagnostics?,
    declaringNode: SwingModifier.ComponentNode<*>?,
    crossinline rewriteReleased: (Set<String>) -> Unit,
): Boolean {
    val reversed = records?.asReversed().orEmpty()
    val first =
        run {
            reversed.fastForEachIndexed { index, record ->
                if (ComponentNodeType.departs(record, declaringNode, incoming)) return@run index
            }
            -1
        }
    if (records == null || first < 0) return false
    val departing = ArrayList<NodeRecord<*, *>>()
    var released: Set<String>? = null
    reversed.fastForEachIn(first until reversed.size) { record ->
        if (ComponentNodeType.departs(record, declaringNode, incoming)) {
            departing.add(record)
            if (declaringNode != null) {
                val held = record.heldProperties
                released = released?.plus(held) ?: held
            }
        }
    }
    departing.fastForEach { records.withdraw(it) }
    var failure = collectFailure(null) { departing.detachAll(diagnostics) }
    departing.fastForEach { record ->
        failure = collectFailure(failure) { records.detachAndRemove(record, diagnostics) }
    }
    released?.let { names ->
        failure =
            collectFailure(failure) {
                val modifier = declaringNode?.holder?.modifierState
                modifier?.reassertPropertySlotsFrom(
                    declaringNode.component,
                    diagnostics,
                    except = declaringNode,
                ) { it.element.affectsProperties(names) }
            }
        failure = collectFailure(failure) { rewriteReleased(names) }
    }
    failure?.let { throw it }
    return true
}

/**
 * The first key of [incoming] whose retained record is out of the order the committed keyed component records of
 * [declaringNode] last had, or `null` where the retained keys keep that order.
 */
private fun NodeRecords.firstMovedKey(
    declaringNode: SwingModifier.Node?,
    incoming: List<SwingModifier.NodeElement<*, *>>,
): Any? {
    var recordIndex = 0
    incoming.fastForEach { element ->
        val key = element.key
        if (get(ComponentNodeType, declaringNode, key) == null) return@fastForEach
        run {
            fastForEachIn(recordIndex until size) {
                if (it.isStandingFor(ComponentNodeType, declaringNode) && it.isCommitted && it.key != null) return@run
                recordIndex++
            }
        }
        if (recordIndex == size || this[recordIndex++].key != key) return key
    }
    return null
}

/**
 * Reconciles one declaring node's keyed component records, committing order only after success.
 *
 * @param incoming the keyed declarations of the declaring node, in declared order.
 * @param mode whether equal declarations may be reused or must be written again.
 * @param declaringNode the node declaring the records, or `null` for the outer modifier.
 * @param rewriteReleased receives the names of the properties released by departed declarations.
 * @param reconcileProperties reconciles the declarations that hold properties and returns the mode that follows them.
 */
internal inline fun SwingNodeHolder<*>.diffKeyedElements(
    incoming: List<SwingModifier.NodeElement<*, *>>,
    mode: WriteMode,
    declaringNode: SwingModifier.ComponentNode<*>? = null,
    crossinline rewriteReleased: (Set<String>) -> Unit = {},
    reconcileProperties: () -> WriteMode = { mode },
): Boolean {
    val state = checkNotNull(modifierState)
    val diagnostics = owner?.diagnostics
    var autoInvalidates = ComponentNodeType.departingHoldsLayout(state.records, declaringNode, incoming)
    val departed =
        detachDeparted(
            state.records,
            incoming,
            diagnostics,
            declaringNode,
            rewriteReleased,
        )
    val propertyMode = reconcileProperties()
    var updating =
        if (departed) {
            WriteMode.RewriteEqual
        } else {
            propertyMode
        }
    val moved = state.records?.firstMovedKey(declaringNode, incoming)
    incoming.fastForEach { element ->
        val key = element.key
        if (key == moved) updating = WriteMode.RewriteEqual
        val record = state.records?.get(ComponentNodeType, declaringNode, key)
        when {
            record == null -> {
                if (attachElement(element, this, key, declaringNode)) autoInvalidates = true
                updating = WriteMode.RewriteEqual
            }

            !NodeRecord.canUpdate(record, element) -> {
                state.records?.detachAndRemove(record, diagnostics)
                if (attachElement(element, this, key, declaringNode)) autoInvalidates = true
                updating = WriteMode.RewriteEqual
            }

            !updating.reuse(record, element) -> {
                if (record.updateTo(element, component, diagnostics, updating)) autoInvalidates = true
                updating = WriteMode.RewriteEqual
            }
        }
    }
    state.records?.order?.commit(declaringNode, incoming)
    return autoInvalidates
}

/**
 * Finds the next standing positional record without revisiting earlier positions.
 *
 * @param from the first position to inspect.
 * @param nodeType the family the record belongs to, or `null` for any.
 * @param declaringNode the node declaring the record, or `null` for the outer modifier.
 */
internal fun NodeRecords.nextPositionalIndex(
    from: Int,
    nodeType: ModifierNodeType<*>? = null,
    declaringNode: SwingModifier.Node? = null,
): Int {
    var index = from
    fastForEachIn(from until size) {
        if (it.key == null && it.isStandingFor(nodeType, declaringNode)) return index
        index++
    }
    return index
}

/**
 * Finds a record again only when callbacks changed its physical position.
 *
 * @param record the record whose position follows.
 * @param from the position [record] held before the callbacks ran.
 */
internal fun NodeRecords.indexAfter(
    record: NodeRecord<*, *>,
    from: Int,
): Int {
    if (from < size && this[from] === record) return from + 1
    val index = indexOfFirst { it === record }
    return if (index >= 0) index + 1 else minOf(from, size)
}

/**
 * Rewrites the selected property record and every later declaration owned by [declaringNode]. Returns whether
 * [from] selected a record.
 *
 * @param records the records searched, or `null` for none.
 * @param declaringNode the node declaring the records, or `null` for the outer modifier.
 * @param except the node whose records are skipped, or `null` to skip none.
 * @param rewrite writes one record under the given mode.
 * @param from selects the first record to rewrite.
 */
internal fun rewriteRecordsFrom(
    records: NodeRecords?,
    declaringNode: SwingModifier.Node?,
    except: SwingModifier.Node?,
    rewrite: (ComponentNodeRecord<*, *>, WriteMode) -> Unit,
    from: (ComponentNodeRecord<*, *>) -> Boolean,
): Boolean {
    var writing = false
    records?.standing(ComponentNodeType, declaringNode)?.fastForEach { standing ->
        if (standing.isStandingFor(ComponentNodeType, declaringNode)) {
            val record = ComponentNodeType.typed(standing)
            if (record.key != null && record.node !== except) {
                if (writing || from(record)) {
                    val mode = if (writing) WriteMode.RewriteEqual else WriteMode.ReuseEqual
                    writing = true
                    rewrite(record, mode)
                }
            }
        }
    }
    return writing
}
