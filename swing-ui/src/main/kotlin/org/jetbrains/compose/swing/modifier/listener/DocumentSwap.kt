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

/** A document listener whose own state describes the document it is registered on. */
internal interface DocumentMirror :
    DocumentListener,
    ModelSwapAware<Document>

/** Adds [documentListener] to the document the receiver holds, and follows it across a swap. */
internal fun JTextComponent.attachSwappableDocumentListener(documentListener: DocumentListener): Unit =
    TEXT_DOCUMENT.attach(this, documentListener)

/** [attachSwappableDocumentListener], settling [mirror] against every document it follows to. */
internal fun JTextComponent.attachSettlingDocumentListener(mirror: DocumentMirror): Unit =
    TEXT_DOCUMENT.attachSettling(this, mirror, mirror::adoptModelSwap)

/** Undoes either attach. */
internal fun JTextComponent.detachSwappableDocumentListener(documentListener: DocumentListener): Unit =
    TEXT_DOCUMENT.detach(this, documentListener)

/** Where a document listener is registered: whichever document the component holds now. */
internal val SWAPPABLE_DOCUMENT =
    ListenerRegistration<JTextComponent, DocumentListener>(
        JTextComponent::attachSwappableDocumentListener,
        JTextComponent::detachSwappableDocumentListener,
    )

/** [SWAPPABLE_DOCUMENT] for a listener that keeps state describing the document it sits on. */
internal val SETTLING_DOCUMENT =
    ListenerRegistration<JTextComponent, DocumentMirror>(
        JTextComponent::attachSettlingDocumentListener,
        JTextComponent::detachSwappableDocumentListener,
    )
