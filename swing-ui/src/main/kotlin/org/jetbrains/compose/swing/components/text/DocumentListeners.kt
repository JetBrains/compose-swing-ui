package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.Document
import javax.swing.text.JTextComponent

/*
 * Internal document helpers for the text components: the registration site a text component's document
 * presents across document swaps, and the reads and writes the wrappers make against a document. The
 * listener helper hands the caller's own listener to the instance-based [listener] seam, so that seam's
 * by-identity contract applies unchanged - the instance the caller keeps is the instance attached and
 * the instance detached.
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

/** Reads the full text of [this] document. */
internal fun Document.fullText(): String = getText(0, length)

/**
 * Replaces the `[offset, offset + length)` region of [this] document with [text]. When the document is
 * an [AbstractDocument] (as `PlainDocument` and the default text-component documents are) the change is
 * applied through its atomic `replace`, so any installed `DocumentFilter` sees one replace; otherwise
 * it falls back to a `remove` followed by an `insertString`.
 */
internal fun Document.replaceSpan(
    offset: Int,
    length: Int,
    text: String,
) {
    when (this) {
        is AbstractDocument -> {
            replace(offset, length, text, null)
        }

        else -> {
            if (length > 0) remove(offset, length)
            if (text.isNotEmpty()) insertString(offset, text, null)
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
 * rewrote or refused it - settles silently: neither this listener nor [pushDeclaredText] reports what
 * the component actually holds back to [callback].
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
 * Settles [value] onto this text component's document: written where the component does not already
 * hold it, through [applied] so the write does not echo back as the user's own. Unlike a declared
 * selection or a scalar widget property, an un-adopted edit is not undone on some later, unrelated
 * recomposition - nothing here reads [applied]'s mirror to gate the push, so typing is never fought
 * without a fresh [value] declaring otherwise.
 */
internal fun <C : JTextComponent> SwingNodeUpdater<C>.pushDeclaredText(
    value: String,
    applied: AppliedValue<String>,
) {
    set(value) { declared ->
        applied.settle(declared, { document.fullText() }, { document.replaceSpan(0, document.length, it) }) {}
    }
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
