package org.jetbrains.compose.swing.components.text

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.DocumentMirror
import org.jetbrains.compose.swing.modifier.listener.SETTLING_DOCUMENT
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.MirrorState
import javax.swing.event.DocumentEvent
import javax.swing.text.Document
import javax.swing.text.JTextComponent

/**
 * Feeds [mirror]'s mirror the text a component's document holds after every edit and reports nothing.
 * It is what a component driven by a caller's own raw listener settles against: the mirror keeps up
 * with the document, so [declareText] compares against the text the component holds now with no callback
 * in the picture. The listener rides the document the component currently holds, leaving an outgoing
 * document for the incoming one.
 *
 * Text shares the document's single listener list because that is all a `JTextComponent` has: the
 * component publishes its changes through its document, and there is no second model for a mirror to
 * ride the way a slider's rides its `BoundedRangeModel`.
 */
internal fun SwingModifier.textMirror(mirror: MirrorState<String>): SwingModifier = listener(mirror, TEXT_MIRRORS)

private val TEXT_MIRRORS =
    documentMirrorRegistration<MirrorState<String>>(
        onEdit = { mirror, document -> if (!mirror.isWriting) mirror.observed(document.fullText()) },
        onAdopt = { mirror, document -> mirror.observed(document.fullText()) },
    )

/**
 * The registration a binding that describes a document's contents is registered on. [onEdit] runs for
 * every edit; [onAdopt] runs once the registration has followed the component to a document it was
 * handed, so what the binding holds describes the document the component holds now rather than the one it
 * left.
 *
 * A component is handed a document by its caller, so [onAdopt] records and reports nothing, exactly as
 * declaring a new document does. Both are stated: [onEdit] skips an edit made inside a write of the
 * binding's own, a distinction that has no meaning on a document the component was just handed. Such an
 * edit is the settle's to account for - the settle making that write reads the component back itself once
 * the write finishes - so [onEdit] skips it before materializing the document, which a long text would
 * otherwise pay for twice.
 *
 * Hold the result in a `val`: it is the registration the listener sits on, and a fresh one on every pass
 * would register the listener again each time.
 */
internal fun <C : Any> documentMirrorRegistration(
    onEdit: (current: C, document: Document) -> Unit,
    onAdopt: (current: C, document: Document) -> Unit,
): CallbackRegistration<JTextComponent, C, DocumentMirror> =
    CallbackRegistration(
        adapter = { current ->
            object : DocumentMirror {
                override fun insertUpdate(event: DocumentEvent): Unit = onEdit(current(), event.document)

                override fun removeUpdate(event: DocumentEvent): Unit = onEdit(current(), event.document)

                override fun changedUpdate(event: DocumentEvent): Unit = onEdit(current(), event.document)

                override fun adoptModelSwap(model: Document): Unit = onAdopt(current(), model)
            }
        },
        registration = SETTLING_DOCUMENT,
    )
