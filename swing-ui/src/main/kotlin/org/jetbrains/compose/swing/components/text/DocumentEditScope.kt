package org.jetbrains.compose.swing.components.text

import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.text.TextRange
import javax.swing.text.AttributeSet
import javax.swing.text.Document

/**
 * A mutable view of a text document handed to [DocumentState.edit]. Calls made on it inside the
 * `edit { }` block are applied to the document as one compound change, so the field observes a single
 * settled edit rather than each intermediate step.
 *
 * Offsets address the buffer's current content and shift as edits are applied, exactly as they do
 * against the underlying document: an [insert] at offset 2 makes a following [delete] at offset 2
 * operate on the text after the insertion.
 *
 * Every call that writes text has an overload taking an [AttributeSet], the attributes the written run
 * carries - a bold word, a colored span, a link. They reach a document that models attributes: the
 * styled document a [TextPane] renders, or the one an editable [EditorPane]'s kit builds. A document
 * that holds characters alone - the plain text a [TextField] or a [TextArea] renders - ignores them, so
 * the text arrives unstyled rather than refused.
 *
 * @see javax.swing.text.Document
 */
public class DocumentEditScope internal constructor(
    private val document: Document,
) {
    /**
     * The selection to apply once the block completes: a function resolved against the buffer's document
     * at that point, once every edit in the block has been applied, or `null` to leave the caret where
     * the underlying document places it after the edits (its default follows the last insertion). The
     * last placement call in the block wins, so a [selectAll] followed by a [placeCaretAtEnd] leaves a
     * collapsed caret.
     */
    internal var pendingSelection: (() -> TextRange)? = null

    /**
     * The length of the buffered content.
     *
     * @see javax.swing.text.Document.getLength
     */
    public val length: Int get() = document.length

    /**
     * Inserts [text] at [offset], shifting following content right.
     *
     * @see javax.swing.text.Document.insertString
     */
    public fun insert(
        offset: Int,
        text: @Nls CharSequence,
    ) {
        document.insertString(offset, text.toString(), null)
    }

    /**
     * Inserts [text] carrying [attributes] at [offset], shifting following content right.
     *
     * @see javax.swing.text.Document.insertString
     */
    public fun insert(
        offset: Int,
        text: @Nls CharSequence,
        attributes: AttributeSet,
    ) {
        document.insertString(offset, text.toString(), attributes)
    }

    /**
     * Replaces the characters in `[start, end)` with [text].
     *
     * @see javax.swing.text.AbstractDocument.replace
     */
    public fun replace(
        start: Int,
        end: Int,
        text: @Nls CharSequence,
    ) {
        document.replaceSpan(start, end - start, text.toString())
    }

    /**
     * Replaces the characters in `[start, end)` with [text] carrying [attributes].
     *
     * @see javax.swing.text.AbstractDocument.replace
     */
    public fun replace(
        start: Int,
        end: Int,
        text: @Nls CharSequence,
        attributes: AttributeSet,
    ) {
        document.replaceSpan(start, end - start, text.toString(), attributes)
    }

    /**
     * Deletes the characters in `[start, end)`.
     *
     * @see javax.swing.text.Document.remove
     */
    public fun delete(
        start: Int,
        end: Int,
    ) {
        document.remove(start, end - start)
    }

    /**
     * Appends [text] to the end of the buffer.
     *
     * @see javax.swing.text.Document.insertString
     */
    public fun append(text: @Nls CharSequence) {
        document.insertString(document.length, text.toString(), null)
    }

    /**
     * Appends [text] carrying [attributes] to the end of the buffer.
     *
     * @see javax.swing.text.Document.insertString
     */
    public fun append(
        text: @Nls CharSequence,
        attributes: AttributeSet,
    ) {
        document.insertString(document.length, text.toString(), attributes)
    }

    /**
     * Replaces the whole buffer with [text].
     *
     * @see javax.swing.text.AbstractDocument.replace
     */
    public fun setText(text: @Nls CharSequence) {
        document.replaceSpan(0, document.length, text.toString())
    }

    /**
     * Replaces the whole buffer with [text] carrying [attributes].
     *
     * @see javax.swing.text.AbstractDocument.replace
     */
    public fun setText(
        text: @Nls CharSequence,
        attributes: AttributeSet,
    ) {
        document.replaceSpan(0, document.length, text.toString(), attributes)
    }

    /** Places the caret at the end of the buffer as it stands once the block completes. */
    public fun placeCaretAtEnd() {
        pendingSelection = { TextRange(document.length, document.length) }
    }

    /** Selects the whole buffer as it stands once the block completes. */
    public fun selectAll() {
        pendingSelection = { TextRange(0, document.length) }
    }
}
