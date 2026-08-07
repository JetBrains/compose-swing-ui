@file:JvmMultifileClass
@file:JvmName("TextComponentsKt")

package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.constants.ContentType
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.binding
import org.jetbrains.compose.swing.text.TextRange
import java.io.StringReader
import javax.swing.JEditorPane
import javax.swing.event.CaretListener
import javax.swing.event.DocumentListener
import javax.swing.event.UndoableEditEvent
import javax.swing.text.Document
import javax.swing.text.EditorKit
import javax.swing.text.JTextComponent
import javax.swing.text.Segment
import javax.swing.undo.CompoundEdit
import javax.swing.undo.UndoManager

/**
 * A hoistable state holder for a text component that owns the [Document] the component renders. The
 * state and the bound component share one document, so an edit made through this state is what the
 * component displays, and text the user types into the component is what this state reports - there is
 * no value to keep in sync and no round-trip per keystroke. A caller who needs a document of their own
 * supplies it at [rememberDocumentState]; content is otherwise read through [text] and changed through
 * [edit].
 *
 * [text] and [selection] are snapshot-observable: reading them inside a composable (or a
 * `snapshotFlow` collector) subscribes to later edits, so the reader recomposes when the document or
 * the caret changes. [text] is materialized on demand from the document, so typing does not pay for a full
 * read until a caller actually asks for the whole text.
 *
 * @see javax.swing.text.Document
 */
public class DocumentState internal constructor(
    // The document this state owns and the bound component renders.
    internal val document: Document,
    // The kit that reads and renders the language [document] is written in, or null when no kit was
    // named for it and the bound component's own kit renders it.
    internal val editorKit: EditorKit? = null,
) : RememberObserver {
    // A generation counter bumped by [documentListener] on every insert/remove/attribute change. The
    // values derived from the document ([text], [canUndo], [canRedo]) are not mirrored into snapshot state
    // and recomputed on write; each is read straight from the document on demand, and its getter first
    // reads this counter to register the snapshot subscription. A document change bumps the counter and so
    // invalidates every lazy reader, which then recomputes. Mirroring [text] instead would materialize the
    // whole document string on every edit even when nothing reads it - the counter keeps reads lazy so a
    // large document is walked only when a caller actually asks for its content. Selection stays a small
    // fixed-size value, so it is mirrored directly in [selectionState] rather than read through the counter.
    private var generation by mutableIntStateOf(0)

    // The state's own selection value, snapshot-observable and synced two-way with the bound caret.
    private var selectionState by mutableStateOf(TextRange(document.length, document.length))

    // While a [recordAsOneEdit] block runs, the primitive document edits it makes are collected here
    // instead of being recorded individually, so the whole block is undone and redone as one step. It is
    // null outside a block.
    private var pendingCompoundEdit: CompoundEdit? = null

    // The component currently rendering this state's document, or null while unmounted. Selection
    // reads/writes go through its caret; before mount [selectionState] is applied on bind.
    private var component: JTextComponent? = null

    // Guards the selection feedback loop: a caret change whose result already matches what was last
    // synced is an echo of a state-to-caret write, so the caret listener leaves the state untouched.
    private var appliedSelection: TextRange = selectionState

    private val documentListener: DocumentListener =
        documentChangeListener { generation++ }

    private val caretListener =
        CaretListener { event ->
            val range = TextRange(event.mark, event.dot)
            if (range == appliedSelection) return@CaretListener
            appliedSelection = range
            selectionState = range
        }

    // The UndoManager records an edit in undoableEditHappened, which fires after the document's
    // insert/remove listeners. During a [recordAsOneEdit] block the edit is diverted into the pending
    // compound edit so a single logical change - a [Document.replace] is a remove plus an insert, and an
    // `edit { }` block may make many primitives - is undone and redone as one step.
    private val undoManager =
        object : UndoManager() {
            override fun undoableEditHappened(event: UndoableEditEvent) {
                val compound = pendingCompoundEdit
                if (compound != null) {
                    compound.addEdit(event.edit)
                } else {
                    super.undoableEditHappened(event)
                }
            }
        }

    init {
        document.addDocumentListener(documentListener)
        document.addUndoableEditListener(undoManager)
    }

    /**
     * The current text of the document. Reading registers a snapshot subscription to later edits;
     * assigning diffs the new value against the current content and applies only the changed span
     * through the document, leaving the surrounding text untouched.
     *
     * @see javax.swing.text.Document.getText
     */
    public var text: @Nls CharSequence
        get() {
            // Register the snapshot read of the generation before materializing, so a later document
            // change invalidates this reader without the text being mirrored eagerly.
            generation
            return document.readText()
        }
        set(value) {
            val current = document.readText()
            if (current.contentEquals(value)) return
            recordAsOneEdit { document.replaceChangedSpan(current, value) }
        }

    /**
     * The current selection, a directional [TextRange] over the caret. Reading registers a snapshot
     * subscription to caret changes; assigning moves the caret and selection of the bound component
     * (or, while unmounted, stores the value to apply when a component binds).
     *
     * A bound component settles the assigned range against the document it renders, and the settled
     * range is what this property then reports: assigning an offset past the document's end reports the
     * end.
     *
     * @see javax.swing.text.Caret.setDot
     */
    public var selection: TextRange
        get() = selectionState
        set(value) {
            val settled = component?.applySelection(value) ?: value
            selectionState = settled
            appliedSelection = settled
        }

    /**
     * Whether an [undo] is currently available; snapshot-observable.
     *
     * @see javax.swing.undo.UndoManager.canUndo
     */
    public val canUndo: Boolean
        get() {
            // Register the snapshot read of [generation]; an undo or redo edits the document and bumps
            // [generation] through [documentListener], so a snapshot reader of availability recomposes.
            generation
            return undoManager.canUndo()
        }

    /**
     * Whether a [redo] is currently available; snapshot-observable.
     *
     * @see javax.swing.undo.UndoManager.canRedo
     */
    public val canRedo: Boolean
        get() {
            generation
            return undoManager.canRedo()
        }

    /**
     * Applies a batch of edits to the document as one compound change. Insertions, replacements and
     * deletions made on the [DocumentEditScope] are committed together, then the caret is placed as the
     * block requested (its default rests after the last insertion).
     *
     * @see javax.swing.undo.CompoundEdit
     */
    public fun edit(block: DocumentEditScope.() -> Unit) {
        val buffer = DocumentEditScope(document)
        recordAsOneEdit { buffer.block() }
        buffer.pendingSelection?.let { resolveSelection -> selection = resolveSelection() }
    }

    /**
     * Reverts the most recent edit, if any.
     *
     * @see javax.swing.undo.UndoManager.undo
     */
    public fun undo() {
        if (undoManager.canUndo()) undoManager.undo()
    }

    /**
     * Reapplies the most recently undone edit, if any.
     *
     * @see javax.swing.undo.UndoManager.redo
     */
    public fun redo() {
        if (undoManager.canRedo()) undoManager.redo()
    }

    // Records every document mutation [block] makes as one undoable step. A single logical change can
    // reach the document as several primitive edits ([Document.replace] is a remove followed by an
    // insert, and an `edit { }` block may make many); collecting them into one [CompoundEdit] lets undo
    // revert the whole change at once. Re-entrant: a mutation made inside an active block joins that
    // block's compound rather than opening and committing its own.
    private fun recordAsOneEdit(block: () -> Unit) {
        val outer = pendingCompoundEdit
        val compound = outer ?: CompoundEdit()
        pendingCompoundEdit = compound
        try {
            block()
        } finally {
            if (outer == null) {
                pendingCompoundEdit = null
                compound.end()
                // A block that made no document change produces an empty compound edit worth nothing to
                // undo; only record one that actually holds edits.
                if (compound.canUndo()) undoManager.addEdit(compound)
            }
        }
    }

    /**
     * Installs this state's document into [target] and wires the two-way selection sync, so the state
     * and the component share one model until [unbind]. The caret's initial selection is set from the
     * state's stored value, and the range the caret settles on is what the state reports afterwards. If
     * this state already drives a different component, that component is unbound first, so a state
     * renders at most one component.
     *
     * A component is likewise owned by at most one [DocumentState]; handing that ownership from one
     * state to another is the binding element's job - its node unbinds the previous owner before
     * binding the new - so [bind] does not itself evict a different state from [target].
     */
    internal fun bind(target: JTextComponent) {
        if (component === target) return
        component?.let { unbind(it) }
        component = target
        target.document = document
        val settled = target.applySelection(selectionState)
        selectionState = settled
        appliedSelection = settled
        target.addCaretListener(caretListener)
    }

    /** Detaches the selection sync from [target], leaving the shared document in place. */
    internal fun unbind(target: JTextComponent) {
        target.removeCaretListener(caretListener)
        if (component === target) component = null
    }

    override fun onRemembered() {
        // The document, undo and caret listeners are attached at construction or on bind; entering the
        // composition adds nothing further.
    }

    // Detaches this state from its document. A caller-supplied document can outlive the state (it is
    // passed to rememberDocumentState and retained by the caller), so leaving the composition must
    // remove the document listener and the undo listener; otherwise the discarded state stays reachable
    // from the live document and its stale listeners keep firing.
    override fun onForgotten() {
        component?.let { unbind(it) }
        document.removeDocumentListener(documentListener)
        document.removeUndoableEditListener(undoManager)
    }

    override fun onAbandoned(): Unit = onForgotten()
}

/**
 * Binds [state] to the composable's text component through the modifier chain, so ownership of the
 * component follows the binding; see [binding].
 */
internal fun SwingModifier.documentStateBinding(state: DocumentState): SwingModifier =
    binding(JTextComponent::class.java, state, DocumentState::bind, DocumentState::unbind)

/**
 * Creates and remembers a [DocumentState] over a fresh document in the language [contentType] names,
 * seeded with [initialText].
 *
 * The content type picks the editor kit registered for it, which both builds the document and reads
 * [initialText] as source written in that language: `text/plain` yields a `PlainDocument` holding the
 * text as characters, `text/html` an `HTMLDocument` holding the markup parsed, `text/rtf` a
 * `StyledDocument` - the model a `JTextPane` requires. An [EditorPane] bound to the state renders it
 * through that same kit.
 *
 * A kit reads its own language and nothing else, so source it cannot make sense of contributes nothing:
 * a `text/rtf` state seeded with text that is not RTF starts empty rather than reporting it, an empty
 * result being what source that legitimately renders to nothing produces too.
 *
 * A later change to [initialText] neither recreates nor mutates the state; drive the field afterwards
 * through the returned state's [DocumentState.text], [DocumentState.edit] and related members. A later
 * change to [contentType] builds a new state in the new language, seeded from [initialText] as it
 * reads on that pass.
 *
 * @param initialText the source, written in [contentType]'s language, the document starts with.
 * @param contentType the MIME type naming the kit that builds and reads the document.
 * @throws IllegalArgumentException if no editor kit is registered for [contentType].
 * @see javax.swing.JEditorPane.createEditorKitForContentType
 */
@Composable
public fun rememberDocumentState(
    initialText: @Nls CharSequence = "",
    @ContentType contentType: String = "text/plain",
): DocumentState = remember(contentType) { documentStateOver(editorKitFor(contentType), initialText) }

/**
 * Creates and remembers a [DocumentState] over a fresh document built by [kit] and seeded by reading
 * [initialText] through it. Reach for this over the [contentType][rememberDocumentState] form when the
 * kit is configured - a style sheet of your own, a custom parser, a kit class the registry does not
 * name.
 *
 * The state is tied to the kit's identity: a different [kit] builds a new state. Pass a kit that
 * outlives a recomposition - one kept in a `remember` - so the state is rebuilt only when the kit
 * really changes.
 *
 * @param kit the editor kit that builds the document, reads [initialText] into it, and renders it.
 * @param initialText the source, written in the kit's language, the document starts with.
 * @see javax.swing.text.EditorKit.createDefaultDocument
 */
@Composable
public fun rememberDocumentState(
    kit: EditorKit,
    initialText: @Nls CharSequence = "",
): DocumentState = remember(kit) { documentStateOver(kit, initialText) }

/**
 * Creates and remembers a [DocumentState] over an existing [document], leaving its current content in
 * place. The bound field renders this exact document, so a caller keeping a reference to it observes the
 * same edits the state does.
 *
 * Name the [kit] that reads the document's language to have an [EditorPane] render it through that kit;
 * without one the pane renders the document through its own, which is plain text.
 *
 * The state is tied to the identity of both: a different [document] or [kit] builds a new state, the
 * field switches to rendering it, and the previous state releases what it held. Pass values that
 * outlive a recomposition - owned outside the composition or kept in a `remember` - so the state is
 * rebuilt only when what it renders really changes.
 *
 * @param document the document the state adopts and the field renders.
 * @param kit the editor kit that reads the document's language, or `null` to leave the choice to the
 *   field.
 * @see javax.swing.text.Document
 */
@Composable
public fun rememberDocumentState(
    document: Document,
    kit: EditorKit? = null,
): DocumentState = remember(document, kit) { DocumentState(document, kit) }

/**
 * The kit registered for [contentType]. The registry answers null for a type nothing is registered
 * for, which is a caller naming a language the runtime cannot read - reported rather than quietly
 * answered with plain text, which would render markup as characters.
 *
 * A media type carries its parameters after a `;` - `text/html; charset=UTF-8` - which the registry is
 * not keyed by, so they are dropped before the lookup. Nothing here reads a document out of a stream, so
 * the charset such a parameter names has nothing to decode.
 *
 * The registry hands out a fresh clone per call, so hold the result across recompositions - the caller
 * that installs it compares kits by identity.
 */
internal fun editorKitFor(
    @ContentType contentType: String,
): EditorKit {
    val mediaType = contentType.substringBefore(';').trim()
    return requireNotNull(JEditorPane.createEditorKitForContentType(mediaType)) {
        "No editor kit is registered for content type \"$mediaType\""
    }
}

// Builds the document [kit] creates for its own language and fills it by reading [initialText] through
// that kit, so source arrives parsed rather than as the characters that spell it.
private fun documentStateOver(
    kit: EditorKit,
    initialText: CharSequence,
): DocumentState {
    val document = kit.createDefaultDocument()
    if (initialText.isNotEmpty()) kit.read(StringReader(initialText.toString()), document, 0)
    return DocumentState(document, kit)
}

// Reads the whole document text through a reusable Segment, avoiding a defensive copy on the read path.
private fun Document.readText(): String {
    val segment = Segment().apply { isPartialReturn = false }
    getText(0, length, segment)
    return segment.toString()
}

// Applies [range] to the component's caret as a directional selection: the anchor lands on the range's
// start and the caret on its end, so a reversed range keeps its direction. setDot collapses any
// existing selection to the anchor, then moveDot extends the caret away from it to the range end.
// Answers the range the caret settled on, read back from the caret itself: a range reaching past the
// document, or one a navigation filter redirects, settles somewhere else than it asked for, and that
// landing place - not the request - is the selection the component has.
private fun JTextComponent.applySelection(range: TextRange): TextRange {
    val docLength = document.length
    val anchor = range.start.coerceIn(0, docLength)
    val dot = range.end.coerceIn(0, docLength)
    caret.setDot(anchor)
    caret.moveDot(dot)
    return TextRange(caret.mark, caret.dot)
}

// Applies the minimal changed span between [current] and [next] through document.replace, so a small
// edit to a large document rebuilds only the changed region rather than the whole gap buffer.
private fun Document.replaceChangedSpan(
    current: CharSequence,
    next: CharSequence,
) {
    val prefix = commonPrefixLength(current, next)
    val suffix = commonSuffixLength(current, next, prefix)
    val removeStart = prefix
    val removeEnd = current.length - suffix
    val inserted = next.subSequence(prefix, next.length - suffix)
    replaceSpan(removeStart, removeEnd - removeStart, inserted.toString())
}

private fun commonPrefixLength(
    a: CharSequence,
    b: CharSequence,
): Int {
    val max = minOf(a.length, b.length)
    var i = 0
    while (i < max && a[i] == b[i]) i++
    return i
}

private fun commonSuffixLength(
    a: CharSequence,
    b: CharSequence,
    prefix: Int,
): Int {
    val max = minOf(a.length, b.length) - prefix
    var i = 0
    while (i < max && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
    return i
}
