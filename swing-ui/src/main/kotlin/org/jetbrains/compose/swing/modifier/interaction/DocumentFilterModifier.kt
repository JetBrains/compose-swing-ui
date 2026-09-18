@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.beans.PropertyChangeListener
import javax.swing.JFormattedTextField
import javax.swing.text.AbstractDocument
import javax.swing.text.DocumentFilter
import javax.swing.text.JTextComponent

/*
 * Document-filter SwingModifier - gates and rewrites edits to a text component before they reach its
 * document, the seam for masked, length-limited, or validated input.
 */

/**
 * Installs [filter] on the text component's document so it can inspect, reject, or rewrite every
 * insert, remove, and replace before it is applied. A `null` filter clears any filter the modifier
 * previously installed. Requires a [JTextComponent] target whose document is an [AbstractDocument].
 * A `JFormattedTextField` is rejected: its document filter belongs to its formatter, which returns
 * it from `JFormattedTextField.AbstractFormatter.getDocumentFilter` and reinstalls it whenever the
 * field reformats.
 *
 * The filter follows the component across document swaps: replacing the component's document - as a
 * `JEditorPane` does when it switches content type - moves the filter onto the new document so it
 * stays active. Every document is handed back the filter it carried before this one arrived on it: the
 * document being left on a swap, and the current one once the declaration goes.
 *
 * ```
 * TextField(
 *     value = digits,
 *     onValueChange = { digits = it },
 *     modifier = SwingModifier.documentFilter(DigitsOnlyFilter),
 * )
 * ```
 *
 * @param filter the [DocumentFilter] to apply, or `null` to remove the installed filter.
 * @return this modifier with the document filter declared on it.
 * @see javax.swing.text.AbstractDocument.setDocumentFilter
 */
public fun SwingModifier.documentFilter(filter: DocumentFilter?): SwingModifier =
    this then DocumentFilterElement(filter)

/**
 * Re-applies a [DocumentFilter] to the text component's live document, and carries it across document
 * swaps with a `document`-property listener.
 *
 * Two elements are equal when they hold the *same* filter - identity, because a filter is a gate whose
 * answers are its own, so an equal-looking replacement is still a different gate.
 */
private class DocumentFilterElement(
    private val filter: DocumentFilter?,
) : SwingModifier.NodeElement<JTextComponent, DocumentFilterElement.Node>() {
    override val name: String get() = "documentFilter"

    override val declaredValues: Map<String, Any?> get() = mapOf("documentFilter" to filter)
    override val targetType: Class<JTextComponent> get() = JTextComponent::class.java

    /**
     * The filter is written onto the component's document, and the component may have replaced that
     * document since - a `JEditorPane` does on every content-type change. What is put back is then the
     * arriving document's own filter, not the one the component carried when the element attached.
     */
    override val restores: RestorePolicy get() = RestorePolicy.None

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.filter = filter
        node.apply()
    }

    override fun equals(other: Any?): Boolean = other is DocumentFilterElement && filter === other.filter

    override fun hashCode(): Int = System.identityHashCode(filter)

    class Node : SwingModifier.ComponentNode<JTextComponent>() {
        var filter: DocumentFilter? = null

        private var restored: DocumentFilter? = null
        private var swapListener: PropertyChangeListener? = null
        private var installed = false

        override fun onAttach() {
            val component = component
            require(component !is JFormattedTextField) {
                "documentFilter cannot be declared on a ${component.javaClass.name}: the field puts back the " +
                    "filter its formatter returns from AbstractFormatter.getDocumentFilter every time it " +
                    "reformats, so a declared filter would stop gating edits without saying so"
            }
            val document = component.document as? AbstractDocument ?: return
            // Each document keeps a record of the filter it carried, handed back as the document leaves.
            restored = document.documentFilter
            val swapListener =
                PropertyChangeListener { event ->
                    (event.oldValue as? AbstractDocument)?.documentFilter = restored
                    val arriving = event.newValue as? AbstractDocument
                    restored = arriving?.documentFilter
                    arriving?.documentFilter = filter
                }
            component.addPropertyChangeListener("document", swapListener)
            this.swapListener = swapListener
            installed = true
        }

        fun apply() {
            // Re-apply on every pass so a changed or cleared filter lands; the swap listener reads the
            // node's current `filter` field for the next document swap.
            (component.document as? AbstractDocument)?.documentFilter = filter
        }

        override fun onDetach() {
            if (!installed) return
            swapListener?.let { component.removePropertyChangeListener("document", it) }
            swapListener = null
            (component.document as? AbstractDocument)?.documentFilter = restored
            installed = false
        }
    }
}
