package org.jetbrains.compose.swing.components.text

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.MirrorState
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
 * edit that only echoes a write [mirror] is making of its own - settling a declared value back onto the
 * component - so [onUserEdit] hears the user's own edits and nothing else. A declared value the component
 * cannot hold verbatim - an installed `DocumentFilter` rewrote or refused it - settles silently: neither
 * this listener nor [declareText] reports what the component actually holds back to [onUserEdit].
 */
internal fun SwingModifier.onTextEdit(
    mirror: MirrorState<String>,
    onUserEdit: (String) -> Unit,
): SwingModifier = listener(TextEdit(mirror, onUserEdit), TEXT_EDITS)

/** What [onTextEdit] declares, as one value the listener it registers reads. */
private class TextEdit(
    val mirror: MirrorState<String>,
    val onUserEdit: (String) -> Unit,
)

private val TEXT_EDITS =
    documentMirrorRegistration<TextEdit>(
        onEdit = { edit, document ->
            if (!edit.mirror.isWriting) {
                val text = document.fullText()
                if (edit.mirror.observed(text)) edit.onUserEdit(text)
            }
        },
        onAdopt = { edit, document -> edit.mirror.observed(document.fullText()) },
    )

/**
 * Declares [value] as this text component's document text, keeping [mirror] in sync with it: the text
 * is written where the component does not already hold it, through [mirror] so the write does not echo
 * back as the user's own, and an edit the caller does not answer with a matching [value] is settled
 * back onto the declared text on the pass that carries their answer.
 *
 * Only the span that differs is written, so settling an edit back leaves the caret where it stands. It
 * is diffed against the text the settlement has already read, so writing it back costs no second
 * materialization of the document.
 */
internal fun <C : JTextComponent> SwingNodeUpdater<C>.declareText(
    value: String,
    mirror: MirrorState<String>,
) {
    declare(
        value,
        mirror,
        read = { document.fullText() },
        write = { held, declared -> document.replaceChangedSpan(held, declared) },
    )
}
