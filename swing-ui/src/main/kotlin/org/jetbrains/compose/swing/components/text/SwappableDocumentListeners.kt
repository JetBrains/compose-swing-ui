package org.jetbrains.compose.swing.components.text

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.ModelSwapAware
import org.jetbrains.compose.swing.modifier.listener.SWAPPABLE_DOCUMENT
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.AppliedValue
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Document
import javax.swing.text.JTextComponent

/**
 * Feeds [applied]'s mirror the text a component's document holds after every edit and reports nothing.
 * It is what a component driven by a caller's own raw listener settles against: the mirror keeps moving
 * with the document, so [declareText] compares against the text the component holds now with no callback
 * in the picture. The listener rides the document the component currently holds, leaving an outgoing
 * document for the incoming one.
 *
 * Text shares the document's single listener list because that is all a `JTextComponent` has: the
 * component publishes its changes through its document, and there is no second model for a mirror to
 * ride the way a slider's rides its `BoundedRangeModel`.
 */
internal fun SwingModifier.textMirror(applied: AppliedValue<String>): SwingModifier = listener(applied, TEXT_MIRRORS)

private val TEXT_MIRRORS =
    documentMirrorRegistration<AppliedValue<String>>(
        onEdit = { applied, document -> applied.observed(document.fullText()) },
    )

/**
 * The registration a binding that describes a document's contents is registered on. [onEdit] runs for every
 * edit; [onAdopt] runs once the registration has followed the component to a document it was handed, so
 * what the binding holds describes the document the component holds now rather than the one it left.
 *
 * A component is handed a document by its caller, so [onAdopt] records and reports nothing, exactly as
 * declaring a new document does. [onEdit] is the one that reaches a caller's own callback, and defaults
 * to serving both for a binding that only records.
 *
 * Hold the result in a `val`: it is the registration the registration sits on, and a fresh one on every pass
 * would register the listener again each time.
 */
internal fun <C : Any> documentMirrorRegistration(
    onEdit: (current: C, document: Document) -> Unit,
    onAdopt: (current: C, document: Document) -> Unit = onEdit,
): CallbackRegistration<JTextComponent, C, DocumentListener> =
    CallbackRegistration(
        adapter = { current ->
            object : DocumentListener, ModelSwapAware {
                override fun insertUpdate(event: DocumentEvent): Unit = onEdit(current(), event.document)

                override fun removeUpdate(event: DocumentEvent): Unit = onEdit(current(), event.document)

                override fun changedUpdate(event: DocumentEvent): Unit = onEdit(current(), event.document)

                override fun adoptModelSwap(model: Any): Unit = onAdopt(current(), model as Document)
            }
        },
        registration = SWAPPABLE_DOCUMENT,
    )
