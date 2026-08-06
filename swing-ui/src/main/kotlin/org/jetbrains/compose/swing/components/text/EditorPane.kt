@file:JvmMultifileClass
@file:JvmName("TextComponentsKt")

package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.constants.ContentType
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberAppliedValue
import javax.swing.JEditorPane
import javax.swing.JTextPane
import javax.swing.event.DocumentListener
import javax.swing.text.Document
import javax.swing.text.html.HTMLDocument

/**
 * A composable wrapper for `JEditorPane`.
 *
 * [contentType] selects the kit the pane displays and edits through - plain text, HTML, or RTF - but
 * [value] and [onValueChange] always carry the pane's plain document text, never the kit's serialized
 * markup: under `text/html`, for instance, `value` is rendered as literal characters rather than parsed as
 * tags. For markup content, bind a document that already holds the structure through the [DocumentState]
 * overload ([EditorPane]) instead of round-tripping it as a `String`.
 *
 * Changing [contentType] re-renders [value] into the fresh document that content type's kit installs. The
 * binding is otherwise reactive in both directions - [value] is pushed onto the pane, and edits the user
 * makes are reported through [onValueChange].
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive the
 * pane with the [DocumentState] overload ([EditorPane]) and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the pane's plain document text, regardless of [contentType]
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param contentType the MIME type the pane displays and edits through (a [ContentType] MIME string)
 * @param onValueChange callback invoked with the pane's new text when the pane is edited; applying
 *   [value] is not itself reported
 * @param editable whether the user can edit the text
 * @see EditorPane the [DocumentState]-driven overload for large or complex editors
 */
@Composable
public fun EditorPane(
    value: String,
    modifier: SwingModifier = SwingModifier,
    @ContentType contentType: String = "text/plain",
    onValueChange: (String) -> Unit = {},
    editable: Boolean = true,
) {
    val callback = rememberUpdatedState(onValueChange)
    val applied = rememberAppliedValue(value)
    val listener = rememberUserEditListener(applied, callback)
    EditorPaneNode(
        value = value,
        applied = applied,
        modifier = modifier.swappableDocumentListener(listener),
        contentType = contentType,
        editable = editable,
    )
}

/**
 * A [EditorPane] driven by a raw [DocumentListener] instead of an `onValueChange` lambda. The listener
 * observes the document the pane currently holds and follows it when a [contentType] switch installs a
 * fresh one; pass a stable instance (e.g. `remember {}`) to avoid churn. Being attached as-is, it
 * observes every change to that document, including the one that applies [value].
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive the
 * pane with the [DocumentState] overload ([EditorPane]) and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the pane's plain document text, regardless of [contentType]
 * @param documentListener the listener notified of document edits
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param contentType the MIME type the pane displays and edits through (a [ContentType] MIME string)
 * @param editable whether the user can edit the text
 * @see EditorPane the [DocumentState]-driven overload for large or complex editors
 */
@Composable
public fun EditorPane(
    value: String,
    documentListener: DocumentListener,
    modifier: SwingModifier = SwingModifier,
    @ContentType contentType: String = "text/plain",
    editable: Boolean = true,
) {
    val applied = rememberAppliedValue(value)
    EditorPaneNode(
        value = value,
        applied = applied,
        modifier = modifier.swappableDocumentListener(documentListener),
        contentType = contentType,
        editable = editable,
    )
}

/**
 * The `JEditorPane` node both [EditorPane] overloads render. [value] is settled through
 * [pushDeclaredText], representing the pane's plain document text regardless of [contentType]. A
 * content-type switch is the one push run unconditionally regardless of [value] itself moving, since it
 * is what leaves the pane with a fresh, empty document to fill.
 */
@Composable
private fun EditorPaneNode(
    value: String,
    applied: AppliedValue<String>,
    modifier: SwingModifier,
    @ContentType contentType: String,
    editable: Boolean,
) {
    SwingNode(
        factory = { JEditorPane() },
        update = {
            // A content type installs the editor kit that interprets the text, together with that kit's
            // own empty document, so the text is rendered into that document after the switch - the
            // value it renders has not changed, but the document holding it has, so it is re-settled
            // regardless of whether the declaration itself moved since the last pass.
            set(contentType) { newContentType ->
                this.contentType = newContentType
                applied.settle(value, { document.fullText() }, { document.replaceSpan(0, document.length, it) }) {}
            }
            pushDeclaredText(value, applied)
            set(editable) { this.isEditable = it }
            applyModifier(modifier)
        },
    )
}

/**
 * A composable wrapper for `JEditorPane` driven by a [DocumentState]. The pane renders the state's
 * own document, so text typed into the pane and edits made through the state are the same content, and
 * the caret is kept two-way with [DocumentState.selection]. The state is the single source of
 * truth; there is no `onValueChange`.
 *
 * The pane's content type is derived from the state's document: an `HTMLDocument` renders as HTML and
 * any other document - including the `PlainDocument` `rememberDocumentState` creates by default -
 * renders as plain text. Handing the state a different document re-derives it. To render HTML, back the
 * state with a matching document, for example
 * `rememberDocumentState(document = remember { HTMLEditorKit().createDefaultDocument() })`.
 *
 * @param state the hoistable text state the pane renders and drives.
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param editable whether the user can edit the text.
 */
@Composable
public fun EditorPane(
    state: DocumentState,
    modifier: SwingModifier = SwingModifier,
    editable: Boolean = true,
) {
    SwingNode(
        factory = { JEditorPane() },
        update = {
            // Apply the content type the state's document is rendered as before binding the document:
            // JEditorPane derives the content type it reports from its editor kit, and assigning the
            // content type installs the kit registered for it - a look-and-feel replacement included -
            // together with that kit's own default document. Update blocks run in recorded order, so the
            // binding element installs the state's document over that default once the kit is in place.
            set(contentTypeOf(state.document)) { this.contentType = it }
            set(editable) { this.isEditable = it }
            applyModifier(modifier.documentStateBinding(state))
        },
    )
}

// The MIME content type a document is rendered as: an HTMLDocument holds markup and needs the kit whose
// views can read it, and every other document is rendered as plain text.
@ContentType
private fun contentTypeOf(document: Document): String = if (document is HTMLDocument) "text/html" else "text/plain"

/**
 * A composable wrapper for `JTextPane`, an editor over a styled document.
 *
 * The binding is reactive in both directions - [value] is pushed onto the pane, and edits the user
 * makes are reported through [onValueChange].
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive the
 * pane with the [DocumentState] overload ([TextPane]) and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the current text
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param onValueChange callback invoked with the pane's new text when the pane is edited; applying
 *   [value] is not itself reported
 * @param editable whether the user can edit the text
 * @see TextPane the [DocumentState]-driven overload for large or complex editors
 */
@Composable
public fun TextPane(
    value: String,
    modifier: SwingModifier = SwingModifier,
    onValueChange: (String) -> Unit = {},
    editable: Boolean = true,
) {
    val callback = rememberUpdatedState(onValueChange)
    val applied = rememberAppliedValue(value)
    val listener = rememberUserEditListener(applied, callback)
    TextPaneNode(
        value = value,
        applied = applied,
        modifier = modifier.swappableDocumentListener(listener),
        editable = editable,
    )
}

/**
 * A [TextPane] driven by a raw [DocumentListener] instead of an `onValueChange` lambda. The listener
 * observes the document the pane currently holds and follows it across a document swap; pass a stable
 * instance (e.g. `remember {}`) to avoid churn. Being attached as-is, it observes every change to that
 * document, including the one that applies [value].
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive the
 * pane with the [DocumentState] overload ([TextPane]) and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the current text
 * @param documentListener the listener notified of document edits
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param editable whether the user can edit the text
 * @see TextPane the [DocumentState]-driven overload for large or complex editors
 */
@Composable
public fun TextPane(
    value: String,
    documentListener: DocumentListener,
    modifier: SwingModifier = SwingModifier,
    editable: Boolean = true,
) {
    val applied = rememberAppliedValue(value)
    TextPaneNode(
        value = value,
        applied = applied,
        modifier = modifier.swappableDocumentListener(documentListener),
        editable = editable,
    )
}

/**
 * The `JTextPane` node both [TextPane] overloads render. [value] is settled through [pushDeclaredText].
 */
@Composable
private fun TextPaneNode(
    value: String,
    applied: AppliedValue<String>,
    modifier: SwingModifier,
    editable: Boolean,
) {
    SwingNode(
        factory = { JTextPane() },
        update = {
            pushDeclaredText(value, applied)
            set(editable) { this.isEditable = it }
            applyModifier(modifier)
        },
    )
}

/**
 * A composable wrapper for `JTextPane` driven by a [DocumentState]. The pane renders the state's
 * own document, so text typed into the pane and edits made through the state are the same content, and
 * the caret is kept two-way with [DocumentState.selection]. The state is the single source of
 * truth; there is no `onValueChange`.
 *
 * A `JTextPane` renders a styled document, so [state] must wrap a `StyledDocument` (e.g. a
 * `DefaultStyledDocument` passed to `rememberDocumentState(document = ...)`).
 *
 * @param state the hoistable text state, over a `StyledDocument`, the pane renders and drives.
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param editable whether the user can edit the text.
 */
@Composable
public fun TextPane(
    state: DocumentState,
    modifier: SwingModifier = SwingModifier,
    editable: Boolean = true,
) {
    SwingNode(
        factory = { JTextPane() },
        update = {
            set(editable) { this.isEditable = it }
            applyModifier(modifier.documentStateBinding(state))
        },
    )
}
