package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.binding
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.declare
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.Document
import javax.swing.text.JTextComponent

/*
 * Internal document helpers for the text components: the registration site a text component's document
 * presents across document swaps, and the reads and writes the wrappers make against a document.
 */

/**
 * Attaches [listener] to a `JTextComponent`'s document and keeps it on the document the component
 * currently holds: when the `document` property changes - as happens when a `JEditorPane` switches
 * content type - the same [listener] leaves the outgoing document and joins the incoming one. Detach
 * removes it from the document the component holds at that moment and stops following swaps. Covers
 * `JEditorPane`/`JTextPane`, whose document is replaced when the content type changes.
 *
 * [listener] itself is the attached instance, so the seam's by-identity contract governs it: it joins the
 * document once and leaves it once.
 */
internal fun SwingModifier.swappableDocumentListener(listener: DocumentListener): SwingModifier =
    listener<JTextComponent, DocumentListener>(
        listener,
        { component, documentListener ->
            component.document.addDocumentListener(documentListener)
            component.addPropertyChangeListener("document", DocumentSwapListener(documentListener))
        },
        { component, documentListener ->
            component.document.removeDocumentListener(documentListener)
            component.documentSwapListenerFor(documentListener)?.let {
                component.removePropertyChangeListener("document", it)
            }
        },
    )

/**
 * Attaches [mirror] to a text component's document as the binding's own observer of what the component
 * holds, kept on the document the component currently holds - it leaves an outgoing document for the
 * incoming one exactly as [swappableDocumentListener] does - and removed when the binding ends (see
 * [binding]).
 *
 * Text shares the document's single listener list because that is all a `JTextComponent` has: the
 * component publishes its changes through its document, and there is no second model for a mirror to
 * ride the way a slider's rides its `BoundedRangeModel`. Installing the mirror as part of the binding
 * rather than through the [documentListener][org.jetbrains.compose.swing.modifier.listener.documentListener]
 * seam is what keeps the caller's chain theirs - the listeners a caller declares are the listeners
 * the chain carries, and the mirror joins and leaves with the component instead.
 */
internal fun SwingModifier.textMirrorBinding(mirror: DocumentListener): SwingModifier =
    binding(
        JTextComponent::class.java,
        mirror,
        { documentListener, component ->
            component.document.addDocumentListener(documentListener)
            component.addPropertyChangeListener("document", DocumentSwapListener(documentListener))
        },
        { documentListener, component ->
            component.document.removeDocumentListener(documentListener)
            component.documentSwapListenerFor(documentListener)?.let {
                component.removePropertyChangeListener("document", it)
            }
        },
    )

internal fun Document.fullText(): String = getText(0, length)

/**
 * Replaces the `[offset, offset + length)` region of [this] document with [text], which carries
 * [attributes] where the document models them. When the document is an [AbstractDocument] (as
 * `PlainDocument` and the default text-component documents are) the change is applied through its
 * atomic `replace`, so any installed `DocumentFilter` sees one replace; otherwise it falls back to a
 * `remove` followed by an `insertString`.
 */
internal fun Document.replaceSpan(
    offset: Int,
    length: Int,
    text: String,
    attributes: AttributeSet? = null,
) {
    when (this) {
        is AbstractDocument -> {
            replace(offset, length, text, attributes)
        }

        else -> {
            if (length > 0) remove(offset, length)
            if (text.isNotEmpty()) insertString(offset, text, attributes)
        }
    }
}

/**
 * Builds a [DocumentListener] that runs [onChange] with the firing [DocumentEvent] for any
 * insert/remove/attribute change.
 */
internal fun documentChangeListener(onChange: (DocumentEvent) -> Unit): DocumentListener =
    object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent): Unit = onChange(e)

        override fun removeUpdate(e: DocumentEvent): Unit = onChange(e)

        override fun changedUpdate(e: DocumentEvent): Unit = onChange(e)
    }

/**
 * Remembers a [DocumentListener] that reports a text component's current document text to [callback] for
 * every edit, staying silent for an edit that only echoes a write [applied] is making of its own -
 * settling a declared value back onto the component - so [callback] hears the user's own edits and
 * nothing else. A declared value the component cannot hold verbatim - an installed `DocumentFilter`
 * rewrote or refused it - settles silently: neither this listener nor [declareText] reports what the
 * component actually holds back to [callback].
 */
@Composable
internal fun rememberUserEditListener(
    applied: AppliedValue<String>,
    callback: State<(String) -> Unit>,
): DocumentListener =
    remember(applied) {
        documentChangeListener { event ->
            val text = event.document.fullText()
            if (applied.observed(text)) callback.value(text)
        }
    }

/**
 * Remembers a [DocumentListener] that feeds [applied]'s mirror the text a component's document holds
 * after every edit and reports nothing. It is what a component driven by a caller's own raw listener
 * settles against: the mirror keeps moving with the document, so [declareText] compares against the
 * text the component holds now with no callback in the picture.
 */
@Composable
internal fun rememberTextMirrorListener(applied: AppliedValue<String>): DocumentListener =
    remember(applied) {
        documentChangeListener { event -> applied.observed(event.document.fullText()) }
    }

/**
 * Declares [value] as this text component's document text, keeping [applied] in sync with it: the text
 * is written where the component does not already hold it, through [applied] so the write does not echo
 * back as the user's own, and an edit the caller does not answer with a matching [value] is settled
 * back onto the declared text on the pass that carries their answer.
 */
internal fun <C : JTextComponent> SwingNodeUpdater<C>.declareText(
    value: String,
    applied: AppliedValue<String>,
) {
    declare(
        value,
        applied,
        read = { document.fullText() },
        write = { document.replaceSpan(0, document.length, it) },
    )
}

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
