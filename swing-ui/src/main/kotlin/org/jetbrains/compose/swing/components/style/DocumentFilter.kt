@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.modifier.PropertyCaptures
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.stylePropertyOf
import java.beans.PropertyChangeListener
import javax.swing.JFormattedTextField
import javax.swing.text.AbstractDocument
import javax.swing.text.DocumentFilter
import javax.swing.text.JTextComponent

/**
 * Installs [filter] on the text component's document so it can inspect, reject, or rewrite every
 * insert, remove, and replace before it is applied. A `null` filter clears any filter the style
 * installed before. Requires a document that is an [AbstractDocument]. A FormattedTextField is rejected:
 * its document filter belongs to its formatter, which returns it from
 * `JFormattedTextField.AbstractFormatter.getDocumentFilter` and reinstalls it whenever the field
 * reformats.
 *
 * The filter follows the component across document swaps: replacing the component's document - as a
 * `JEditorPane` does when it switches content type - moves the filter onto the new document so it
 * stays active. Every document is handed back the filter it carried before this one arrived on it: the
 * document being left on a swap, and the current one once the style stops setting it.
 *
 * ```
 * TextField(value = digits, onValueChange = { digits = it }, style = { documentFilter(DigitsOnlyFilter) })
 * ```
 *
 * @param filter the [DocumentFilter] to apply, or `null` to remove the installed filter.
 * @see javax.swing.text.AbstractDocument.setDocumentFilter
 */
public fun TextStyleScope.documentFilter(filter: DocumentFilter?): Unit = DocumentFilterProperty.provide(filter)

internal val DocumentFilterProperty = stylePropertyOf { filter: DocumentFilter? -> DocumentFilterElement(filter) }

/**
 * Re-applies a [DocumentFilter] to the text component's live document, and carries it across document
 * swaps with a `document`-property listener.
 *
 * Two elements are equal when they hold the *same* filter - identity, because a filter is a gate whose
 * answers are its own, so an equal-looking replacement is still a different gate.
 */
internal class DocumentFilterElement(
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

        private var captured: PropertyCaptures.Capture<*, *>? = null
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
            hold(document)
            val swapListener =
                PropertyChangeListener { event ->
                    captured?.release()
                    captured = null
                    (event.newValue as? AbstractDocument)?.let { arriving ->
                        hold(arriving)
                        holder?.modifierState?.refreshLocalConsumer(this)
                    }
                }
            component.addPropertyChangeListener("document", swapListener)
            this.swapListener = swapListener
            installed = true
        }

        private fun hold(document: AbstractDocument) {
            captured =
                checkNotNull(holder?.modifierState).captures().capture(
                    "documentFilter" to document,
                    component,
                    { document.documentFilter },
                    { _, value -> document.documentFilter = value },
                )
        }

        fun apply() {
            // Runs every pass so a changed or cleared filter lands.
            (component.document as? AbstractDocument)?.documentFilter = filter
        }

        override fun onDetach() {
            if (!installed) return
            swapListener?.let { component.removePropertyChangeListener("document", it) }
            swapListener = null
            captured?.release()
            captured = null
            installed = false
        }
    }
}
