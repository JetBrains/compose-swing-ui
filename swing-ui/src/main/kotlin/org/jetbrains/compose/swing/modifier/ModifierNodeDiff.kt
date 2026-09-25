package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.util.fastForEach
import java.util.EnumSet

/** The element and record types used to update one kind of modifier node. */
internal class ModifierNodeType(
    val elementType: Class<out SwingModifier.Element>,
    val recordType: Class<out NodeRecord<*, *>>,
)

internal val ComponentNodeType = ModifierNodeType(SwingModifier.NodeElement::class.java, ElementRecord::class.java)

internal val ParentLayoutNodeType = ModifierNodeType(ParentLayoutNodeElement::class.java, LayoutNodeRecord::class.java)

/**
 * Updates nodes by position within [nodeType], independently of the other node type.
 * Removes excess nodes from the end and preserves compatible nodes. A replacement attaches before the old node
 * detaches; if attachment fails, the old record remains. Equal elements can be adopted when [adoptable] is true.
 */
internal fun updateModifierNodes(
    holder: SwingNodeHolder<*>,
    state: SwingModifierState,
    elements: List<SwingModifier.Element>,
    nodeType: ModifierNodeType,
    adoptable: Boolean,
): EnumSet<ModifierChange> {
    val diagnostics = holder.owner?.diagnostics
    val records = state.chain
    var declared = 0
    elements.fastForEach { if (nodeType.elementType.isInstance(it)) declared++ }
    var existing = 0
    records.fastForEach { if (nodeType.recordType.isInstance(it)) existing++ }

    // Remove excess records before updating the retained nodes.
    val changes = EnumSet.noneOf(ModifierChange::class.java)
    var last = records.size
    while (existing > declared) {
        if (nodeType.recordType.isInstance(records[--last])) {
            records.removeAt(last).detach(diagnostics)
            changes.add(ModifierChange.NodesAddedOrRemoved)
            existing--
        }
    }

    var index = 0
    elements.fastForEach { element ->
        if (!nodeType.elementType.isInstance(element)) return@fastForEach
        while (index < records.size && !nodeType.recordType.isInstance(records[index])) index++
        val record = records.getOrNull(index)
        when {
            record == null -> {
                val attaching = NodeRecord.mark(element, holder)
                attaching.runAttachLifecycle(diagnostics, records)
                changes.add(ModifierChange.NodesAddedOrRemoved)
            }

            record.canRebind(element) -> {
                if (!adoptable || !record.adopt(element)) updateNode(changes, holder, record, element, diagnostics)
            }

            else -> {
                val replacement = NodeRecord.mark(element, holder)
                records[index] = replacement
                try {
                    replacement.runAttachLifecycle(diagnostics, {}, { records[index] = record })
                } finally {
                    if (records[index] === replacement) record.detach(diagnostics)
                }
                changes.add(ModifierChange.NodesAddedOrRemoved)
            }
        }
        index++
    }
    return changes
}

private fun updateNode(
    changes: EnumSet<ModifierChange>,
    holder: SwingNodeHolder<*>,
    record: NodeRecord<*, *>,
    element: SwingModifier.Element,
    diagnostics: SwingCompositionDiagnostics?,
) {
    val target = holder.component
    record.rebindAndWrite(element, target, diagnostics)
    // Each updated node is checked, even if an earlier one already requires notification.
    if ((target as? DeclaredNodesListener).takesWrite(record.node)) {
        changes.add(ModifierChange.ListenerNeedsUpdatedNodes)
    }
    if (ModifierChange.ParentLayoutUpdated !in changes && record is LayoutNodeRecord &&
        holder.declaration.parentReads(record.node)
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
