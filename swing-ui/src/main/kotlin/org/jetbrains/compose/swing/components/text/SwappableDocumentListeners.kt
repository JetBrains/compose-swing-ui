package org.jetbrains.compose.swing.components.text

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.modifier.listener.liveCallbackListener
import org.jetbrains.compose.swing.node.AppliedValue
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Document
import javax.swing.text.JTextComponent

/*
 * The seam that keeps a document listener attached across a `document` property swap - as happens
 * when a `JEditorPane` switches content type - and the modifier-facing functions built on it.
 */

/**
 * Adds [documentListener] to the receiver's current document and starts following it across a
 * `document` property swap - as happens when a `JEditorPane` switches content type - until
 * [detachSwappableDocumentListener] removes it again.
 */
internal fun JTextComponent.attachSwappableDocumentListener(documentListener: DocumentListener) {
    document.addDocumentListener(documentListener)
    addPropertyChangeListener("document", DocumentSwapListener(documentListener))
}

/**
 * Undoes [attachSwappableDocumentListener]: removes [documentListener] from the document the receiver
 * holds at this moment and stops following further swaps.
 */
internal fun JTextComponent.detachSwappableDocumentListener(documentListener: DocumentListener) {
    document.removeDocumentListener(documentListener)
    documentSwapListenerFor(documentListener)?.let { removePropertyChangeListener("document", it) }
}

/**
 * Attaches [listener] to a `JTextComponent`'s document and keeps it on the document the component
 * currently holds, following a swap as [attachSwappableDocumentListener] does. Covers
 * `JEditorPane`/`JTextPane`, whose document is replaced when the content type changes.
 *
 * [listener] itself is the attached instance, so the seam's by-identity contract governs it: it joins the
 * document once and leaves it once.
 */
internal fun SwingModifier.swappableDocumentListener(listener: DocumentListener): SwingModifier =
    listener<JTextComponent, DocumentListener>(
        listener,
        JTextComponent::attachSwappableDocumentListener,
        JTextComponent::detachSwappableDocumentListener,
    )

/**
 * Feeds [applied]'s mirror the text a component's document holds after every edit and reports nothing.
 * It is what a component driven by a caller's own raw listener settles against: the mirror keeps moving
 * with the document, so [declareText] compares against the text the component holds now with no callback
 * in the picture. The listener rides the document the component currently holds, leaving an outgoing
 * document for the incoming one exactly as [swappableDocumentListener] does.
 *
 * Text shares the document's single listener list because that is all a `JTextComponent` has: the
 * component publishes its changes through its document, and there is no second model for a mirror to
 * ride the way a slider's rides its `BoundedRangeModel`.
 */
internal fun SwingModifier.textMirror(applied: AppliedValue<String>): SwingModifier =
    liveCallbackListener<JTextComponent, AppliedValue<String>, DocumentListener>(
        applied,
        { current -> documentChangeListener { event -> current().observed(event.document.fullText()) } },
        JTextComponent::attachSwappableDocumentListener,
        JTextComponent::detachSwappableDocumentListener,
    )

/**
 * Installs a library-built [DocumentListener] that runs [onChange] for every insert/remove/attribute
 * change, on the document the component currently holds: as [swappableDocumentListener] does, the
 * listener leaves an outgoing document for the incoming one when the `document` property changes.
 *
 * [onChange] is read live, so a lambda written inline needs no `remember`.
 */
internal fun SwingModifier.onDocumentChange(onChange: (DocumentEvent) -> Unit): SwingModifier =
    liveCallbackListener<JTextComponent, (DocumentEvent) -> Unit, DocumentListener>(
        onChange,
        { current -> documentChangeListener { current().invoke(it) } },
        JTextComponent::attachSwappableDocumentListener,
        JTextComponent::detachSwappableDocumentListener,
    )

/**
 * Re-homes [documentListener] across a component's document swap: it is removed from the outgoing
 * document and added to the incoming one, so one registration follows the component rather than staying
 * behind on a document nothing renders.
 */
private class DocumentSwapListener(
    val documentListener: DocumentListener,
) : PropertyChangeListener {
    override fun propertyChange(event: PropertyChangeEvent) {
        (event.oldValue as? Document)?.removeDocumentListener(documentListener)
        (event.newValue as? Document)?.addDocumentListener(documentListener)
    }
}

// The swap listener re-homing [documentListener] on this component. Held by the component rather than by
// the modifier chain, so the pairing survives every recomposition that rebuilds the chain and detach
// removes exactly the instance attach installed.
private fun JTextComponent.documentSwapListenerFor(documentListener: DocumentListener): DocumentSwapListener? =
    getPropertyChangeListeners("document")
        .filterIsInstance<DocumentSwapListener>()
        .firstOrNull { it.documentListener === documentListener }
