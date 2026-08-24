package org.jetbrains.compose.swing.modifier.listener

import javax.swing.event.DocumentListener
import javax.swing.text.Document
import javax.swing.text.JTextComponent

// A text component publishes its changes through its document, and a JEditorPane swaps one in whenever
// its content type changes.
private val TEXT_DOCUMENT =
    SwappableModel<JTextComponent, Document, DocumentListener>(
        property = "document",
        modelType = Document::class.java,
        model = JTextComponent::getDocument,
        add = Document::addDocumentListener,
        remove = Document::removeDocumentListener,
    )

/** Adds [documentListener] to the document the receiver holds, and follows it across a swap. */
internal fun JTextComponent.attachSwappableDocumentListener(documentListener: DocumentListener): Unit =
    TEXT_DOCUMENT.attach(this, documentListener)

/** Undoes [attachSwappableDocumentListener]. */
internal fun JTextComponent.detachSwappableDocumentListener(documentListener: DocumentListener): Unit =
    TEXT_DOCUMENT.detach(this, documentListener)

/** The registration a document listener is registered on: whichever document the component holds now. */
internal val SWAPPABLE_DOCUMENT =
    ListenerRegistration<JTextComponent, DocumentListener>(
        JTextComponent::attachSwappableDocumentListener,
        JTextComponent::detachSwappableDocumentListener,
    )
