package org.jetbrains.compose.swing.components.text

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.declare
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.Document
import javax.swing.text.JTextComponent

/*
 * Internal document helpers for the text components: the reads and writes the wrappers make against a
 * document, and the listener plumbing built on top of the swap-following seam in
 * SwappableDocumentListeners.kt.
 */

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
 * Reports a text component's current document text to [onUserEdit] for every edit, staying silent for an
 * edit that only echoes a write [applied] is making of its own - settling a declared value back onto the
 * component - so [onUserEdit] hears the user's own edits and nothing else. A declared value the component
 * cannot hold verbatim - an installed `DocumentFilter` rewrote or refused it - settles silently: neither
 * this listener nor [declareText] reports what the component actually holds back to [onUserEdit].
 */
internal fun SwingModifier.onTextEdit(
    applied: AppliedValue<String>,
    onUserEdit: (String) -> Unit,
): SwingModifier =
    onDocumentChange { event ->
        val text = event.document.fullText()
        if (applied.observed(text)) onUserEdit(text)
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
