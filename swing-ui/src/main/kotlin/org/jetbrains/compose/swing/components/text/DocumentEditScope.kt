package org.jetbrains.compose.swing.components.text

import javax.swing.text.Document

/**
 * A mutable view of a text document handed to [DocumentState.edit]. Calls made on it inside the
 * `edit { }` block are applied to the document as one compound change, so the field observes a single
 * settled edit rather than each intermediate step.
 *
 * Offsets address the buffer's current content and shift as edits are applied, exactly as they do
 * against the underlying document: an [insert] at offset 2 makes a following [delete] at offset 2
 * operate on the text after the insertion.
 */
public class DocumentEditScope internal constructor(
    private val document: Document,
) {
    /**
     * The selection to apply once the block completes, or `null` to leave the caret where the underlying
     * document places it after the edits (its default follows the last insertion). The last placement
     * call in the block wins, so a [selectAll] followed by a [placeCaretAtEnd] leaves a collapsed caret.
     */
    internal var pendingSelection: TextRange? = null

    /** The length of the buffered content. */
    public val length: Int get() = document.length

    /** Inserts [text] at [offset], shifting following content right. */
    public fun insert(
        offset: Int,
        text: CharSequence,
    ) {
        document.insertString(offset, text.toString(), null)
    }

    /** Replaces the characters in `[start, end)` with [text]. */
    public fun replace(
        start: Int,
        end: Int,
        text: CharSequence,
    ) {
        document.replaceSpan(start, end - start, text.toString())
    }

    /** Deletes the characters in `[start, end)`. */
    public fun delete(
        start: Int,
        end: Int,
    ) {
        document.remove(start, end - start)
    }

    /** Appends [text] to the end of the buffer. */
    public fun append(text: CharSequence) {
        document.insertString(document.length, text.toString(), null)
    }

    /** Replaces the whole buffer with [text]. */
    public fun setText(text: CharSequence) {
        document.replaceSpan(0, document.length, text.toString())
    }

    /** Places the caret at the end of the buffer once the block completes. */
    public fun placeCaretAtEnd() {
        pendingSelection = TextRange(document.length, document.length)
    }

    /** Selects the whole buffer once the block completes. */
    public fun selectAll() {
        pendingSelection = TextRange(0, document.length)
    }
}
