@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

/**
 * Runs [onDocumentChange] for every change to the text component's `document` - an insertion, a removal
 * and a change of attributes alike. Requires a [JTextComponent] target, and observes the `document` the
 * component currently holds, following it when the component swaps one in - as a `JEditorPane` does
 * when its content type changes.
 *
 * [onDocumentChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again. To tell the three changes apart, declare them one by one instead.
 *
 * @param onDocumentChange receives the event, whose `offset` and `length` bound the affected range;
 *   writing to the document from it is refused while the change is being reported.
 * @return this chain with the document callback declared on it.
 * @see javax.swing.text.Document.addDocumentListener
 */
public fun SwingModifier.documentListener(onDocumentChange: (DocumentEvent) -> Unit): SwingModifier =
    documentListener(
        onInsert = onDocumentChange,
        onRemove = onDocumentChange,
        onChange = onDocumentChange,
    )

/**
 * Runs [onInsert] when text enters the text component's `document`, [onRemove] when text leaves it, and
 * [onChange] when its attributes change. Requires a [JTextComponent] target, and follows the
 * `document` the component holds across a swap.
 *
 * Each lambda is read when its event fires, so writing a fresh one on every recomposition registers
 * nothing again. A change left undeclared reports nowhere.
 *
 * Declaring none at all is refused.
 *
 * @param onInsert runs once the text is in the document.
 * @param onRemove runs once the text is gone, so the range the event bounds is the one it occupied.
 * @param onChange runs when the attributes over a range change, not when the text itself does.
 * @return this chain with the document callbacks declared on it.
 * @see javax.swing.text.Document.addDocumentListener
 */
public fun SwingModifier.documentListener(
    onInsert: (DocumentEvent) -> Unit = UNDECLARED,
    onRemove: (DocumentEvent) -> Unit = UNDECLARED,
    onChange: (DocumentEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared("documentListener", declared(onInsert) || declared(onRemove) || declared(onChange))
    return listener(DocumentCallbacks(onInsert, onRemove, onChange), DOCUMENT_CALLBACKS)
}

/**
 * Attaches a [DocumentListener] to the text component's `document` (`document.addDocumentListener`).
 * Requires a [JTextComponent] target (`JTextField`, `JTextArea`, ...). The listener observes the
 * `document` the component currently holds, following it when the component swaps one in.
 *
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the document listener declared on it.
 * @see javax.swing.text.Document.addDocumentListener
 */
public fun SwingModifier.documentListener(listener: DocumentListener): SwingModifier =
    listener(listener, SWAPPABLE_DOCUMENT)

/** The lambdas [documentListener] was declared with, as one value the built listener reads. */
private class DocumentCallbacks(
    val onInsert: (DocumentEvent) -> Unit,
    val onRemove: (DocumentEvent) -> Unit,
    val onChange: (DocumentEvent) -> Unit,
)

private val DOCUMENT_CALLBACKS =
    CallbackRegistration<JTextComponent, DocumentCallbacks, DocumentListener>(
        adapter = { current ->
            object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent): Unit = current().onInsert(event)

                override fun removeUpdate(event: DocumentEvent): Unit = current().onRemove(event)

                override fun changedUpdate(event: DocumentEvent): Unit = current().onChange(event)
            }
        },
        registration = SWAPPABLE_DOCUMENT,
    )
