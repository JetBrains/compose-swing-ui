package org.jetbrains.compose.swing.modifier

import androidx.collection.ObjectList
import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.util.fastForEach
import java.awt.Component
import java.util.EnumSet

/** The element type that identifies one kind of modifier node, whose records the shared store keeps together. */
internal class ModifierNodeType(
    val elementType: Class<out SwingModifier.Element>,
)

internal val ComponentNodeType = ModifierNodeType(SwingModifier.NodeElement::class.java)

internal val ParentLayoutNodeType = ModifierNodeType(ParentLayoutNodeElement::class.java)

/**
 * Updates nodes by position within [nodeType], independently of the other node type.
 * Removes excess nodes from the end and preserves compatible nodes. A replacement attaches before the old node
 * detaches; if attachment fails, the old record remains. Equal elements can be adopted when [mode] permits it.
 */
internal fun updateModifierNodes(
    holder: SwingNodeHolder<*>,
    state: SwingModifierState,
    elements: ObjectList<SwingModifier.Element>,
    nodeType: ModifierNodeType,
    mode: WriteMode,
): EnumSet<ModifierChange> {
    val diagnostics = holder.owner?.diagnostics
    val records = state.records
    var declared = 0
    elements.forEach { if (nodeType.elementType.isInstance(it)) declared++ }
    var existing = 0
    records?.forEach(nodeType) { if (it.key == null) existing++ }

    // Remove excess records before updating the retained nodes.
    val changes = EnumSet.noneOf(ModifierChange::class.java)
    records?.forEachReversed(nodeType) { record ->
        if (existing > declared && record.key == null) {
            records.detachAndRemove(record, diagnostics)
            changes.add(ModifierChange.NodesAddedOrRemoved)
            existing--
        }
    }

    var index = 0
    elements.forEach { element ->
        if (!nodeType.elementType.isInstance(element)) return@forEach
        val position = index++
        val record = state.records?.get(nodeType, null, null, position)
        when {
            record == null -> {
                state.attach(holder, element, diagnostics)
                changes.add(ModifierChange.NodesAddedOrRemoved)
            }

            record.canRebind(element) -> {
                if (!mode.adopt(record, element)) updateNode(changes, holder, record, element, diagnostics)
            }

            else -> {
                state.replace(holder, record, element, diagnostics)
                changes.add(ModifierChange.NodesAddedOrRemoved)
            }
        }
    }
    return changes
}

/** Marks and attaches a node for [element] in a new slot, which stays standing only if attachment succeeds. */
private fun SwingModifierState.attach(
    holder: SwingNodeHolder<*>,
    element: SwingModifier.Element,
    diagnostics: SwingCompositionDiagnostics?,
): NodeRecord<*, *> {
    val attaching = NodeRecord.mark(element, holder)
    attaching.runAttachLifecycle(
        diagnostics,
        { insert(attaching) },
        { records?.withdraw(attaching) },
    )
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
        replacement.runAttachLifecycle(
            diagnostics,
            { checkNotNull(records).insert(replacement, record) },
            {
                checkNotNull(records).insert(record, replacement)
                checkNotNull(records).withdraw(replacement)
            },
        )
    } finally {
        if (replacement.isStanding) records?.detachAndRemove(record, diagnostics)
    }
    return replacement
}

private fun updateNode(
    changes: EnumSet<ModifierChange>,
    holder: SwingNodeHolder<*>,
    record: NodeRecord<*, *>,
    element: SwingModifier.Element,
    diagnostics: SwingCompositionDiagnostics?,
) {
    val target = holder.component
    // Each updated node is checked, even if an earlier one already requires notification.
    if (record.rebindAndWrite(element, target, diagnostics, mode = WriteMode.AdoptEqual)) {
        changes.add(ModifierChange.ListenerNeedsUpdatedNodes)
    }
    if (ModifierChange.ParentLayoutUpdated !in changes && record.nodeType === ParentLayoutNodeType &&
        holder.declaration.parentReads(record.node as ParentLayoutNode)
    ) {
        changes.add(ModifierChange.ParentLayoutUpdated)
    }
}

/** Changes that require publishing updated nodes or parent-layout declarations. */
internal enum class ModifierChange {
    NodesAddedOrRemoved,
    ParentLayoutUpdated,
    ListenerNeedsUpdatedNodes,
}

internal val Set<ModifierChange>.needsDeclaredNodes: Boolean
    get() = ModifierChange.NodesAddedOrRemoved in this || ModifierChange.ListenerNeedsUpdatedNodes in this

/** Publishes a keyed component slot before its attachment callback and first update. */
internal fun <T : Component, N : SwingModifier.ComponentNode<T>> attachElement(
    element: SwingModifier.NodeElement<T, N>,
    holder: SwingNodeHolder<*>,
    key: Any,
    declaringNode: SwingModifier.Node? = null,
): Boolean {
    val state = checkNotNull(holder.modifierState)
    val record = ComponentNodeRecord.mark(element, holder, key, declaringNode)
    return record.runAttachLifecycle(
        holder.owner?.diagnostics,
        { state.insert(record) },
        { state.records?.withdraw(record) },
    )
}

/**
 * Takes the departed keyed slots apart together: every departed node runs its detach callback, in reverse order,
 * before any of them is marked detached, so a change event a restore fires never reaches a node already marked
 * detached. Then the outer declarations holding a released property are written again, and [rewriteReleased]
 * receives the released property names.
 */
private fun detachDeparted(
    records: NodeRecords?,
    incoming: ObjectList<SwingModifier.Element>,
    diagnostics: SwingCompositionDiagnostics?,
    declaringNode: SwingModifier.ComponentNode<*>?,
    rewriteReleased: (Set<String>) -> Unit,
): Boolean {
    var departing: List<NodeRecord<*, *>>? = null
    var released: Set<String>? = null
    records?.forEachReversed(ComponentNodeType, declaringNode) { record ->
        if (record.key != null && !incoming.any { (it as SwingModifier.NodeElement<*, *>).key == record.key }) {
            departing = departing?.plus(record) ?: listOf(record)
            if (declaringNode != null) {
                val held = (record.element as SwingModifier.NodeElement<*, *>).heldProperties
                released = released?.plus(held) ?: held
            }
        }
    }
    val departed = departing
    if (records != null && departed != null) {
        departed.fastForEach { records.withdraw(it) }
        try {
            departed.detachAll(diagnostics)
        } finally {
            departed.fastForEach { records.detachAndRemove(it, diagnostics) }
        }
        released?.let { names ->
            val modifier = declaringNode?.holder?.modifierState
            modifier?.rewritePropertySlotsFrom(declaringNode.component, diagnostics, except = declaringNode) { record ->
                record.element.heldProperties.any { it in names }
            }
            rewriteReleased(names)
        }
    }
    return departed != null
}

/**
 * The first key of [incoming] whose retained record stands out of the order the committed keyed component records of
 * [declaringNode] last had, or `null` where the retained keys keep that order.
 */
private fun NodeRecords.firstMovedKey(
    declaringNode: SwingModifier.Node?,
    incoming: ObjectList<SwingModifier.Element>,
): Any? {
    var next = 0

    fun nextRetainedKey(): Any? {
        while (next < incoming.size) {
            val key = (incoming[next++] as SwingModifier.NodeElement<*, *>).key
            if (get(ComponentNodeType, declaringNode, key) != null) return key
        }
        return null
    }
    var moved: Any? = null
    forEach(ComponentNodeType, declaringNode) { record ->
        if (moved == null && record.isCommitted && record.key != null) {
            val key = nextRetainedKey()
            if (key != record.key) moved = key
        }
    }
    return moved ?: nextRetainedKey()
}

/** Reconciles one declaring node's keyed component slots, committing order only after success. */
internal fun SwingNodeHolder<*>.diffKeyedElements(
    incoming: ObjectList<SwingModifier.Element>,
    mode: WriteMode,
    declaringNode: SwingModifier.ComponentNode<*>? = null,
    rewriteReleased: (Set<String>) -> Unit = {},
): Boolean {
    val state = checkNotNull(modifierState)
    val diagnostics = owner?.diagnostics
    var autoInvalidates = false
    state.records?.forEach(ComponentNodeType, declaringNode) { record ->
        if (record.key != null && !incoming.any { (it as SwingModifier.NodeElement<*, *>).key == record.key } &&
            record.node.holdsLayoutNodes
        ) {
            autoInvalidates = true
        }
    }
    var updating =
        if (detachDeparted(
                state.records,
                incoming,
                diagnostics,
                declaringNode,
                rewriteReleased,
            )
        ) {
            WriteMode.RewriteEqual
        } else {
            mode
        }
    val moved = state.records?.firstMovedKey(declaringNode, incoming)
    incoming.forEach { incomingElement ->
        val element = incomingElement as SwingModifier.NodeElement<*, *>
        val key = element.key
        if (key == moved) updating = WriteMode.RewriteEqual
        val record = state.records?.get(ComponentNodeType, declaringNode, key)
        when {
            record == null -> {
                if (attachElement(element, this, key, declaringNode)) autoInvalidates = true
                updating = WriteMode.RewriteEqual
            }

            !record.canRebind(element) -> {
                state.records?.detachAndRemove(record, diagnostics)
                if (attachElement(element, this, key, declaringNode)) autoInvalidates = true
                updating = WriteMode.RewriteEqual
            }

            !updating.adopt(record, element) -> {
                if (record.rebindAndWrite(element, component, diagnostics, updating)) autoInvalidates = true
                updating = WriteMode.RewriteEqual
            }
        }
    }
    state.records?.commitOrder(declaringNode, incoming)
    return autoInvalidates
}
