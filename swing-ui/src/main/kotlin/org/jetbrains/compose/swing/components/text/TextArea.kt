@file:JvmMultifileClass
@file:JvmName("TextComponentsKt")

package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.documentListener
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberMirrorState
import javax.swing.JTextArea
import javax.swing.event.DocumentListener

/**
 * A composable wrapper for JTextArea.
 *
 * This area is strictly controlled: text the area settles on that [onValueChange] does not answer with
 * a matching [value] is settled back onto the declared value on the very next pass, so the area never
 * ends up holding text the caller has not adopted. A callback that filters a keystroke rather than
 * adopting it leaves the caret where that keystroke would have gone.
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive the
 * area with the [DocumentState] overload ([TextArea]) and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the current text value
 * @param onValueChange callback invoked with the area's new text when the area is edited; applying
 *   [value] is not itself reported
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param rows the number of rows
 * @param columns the number of columns
 * @param editable whether the user can edit the text
 * @param lineWrap whether lines too long for the area's width are wrapped onto the next line; `false`
 *   by default, so long lines run past the width
 * @param wrapStyleWord whether wrapped lines break at word boundaries rather than at character
 *   boundaries; `false` by default, and only consulted while [lineWrap] is on
 * @param tabSize the number of characters a tab expands to, `8` by default
 * @see TextArea the [DocumentState]-driven overload for large or complex editors
 * @see javax.swing.JTextArea
 */
@Composable
public fun TextArea(
    value: @Nls String,
    onValueChange: (@Nls String) -> Unit,
    modifier: SwingModifier = SwingModifier,
    rows: Int = 0,
    columns: Int = 0,
    editable: Boolean = true,
    lineWrap: Boolean = false,
    wrapStyleWord: Boolean = false,
    tabSize: Int = DEFAULT_TAB_SIZE,
) {
    val mirror = rememberMirrorState(value)
    TextAreaNode(
        value = value,
        mirror = mirror,
        modifier = modifier.onTextEdit(mirror, onValueChange),
        rows = rows,
        columns = columns,
        editable = editable,
        lineWrap = lineWrap,
        wrapStyleWord = wrapStyleWord,
        tabSize = tabSize,
    )
}

/**
 * A composable wrapper for JTextArea driven by a raw [DocumentListener] instead of an `onValueChange`
 * lambda. The [documentListener] is attached to the area's document as-is and removed on the same
 * instance; pass a stable instance (e.g. `remember {}`) to avoid churn. Being attached as-is, it
 * observes every change to that document, including the one that applies [value].
 *
 * This area is strictly controlled: text the area settles on that is not followed by [value] moving to
 * match is settled back onto the declared value on the very next pass, so the area never ends up
 * holding text the caller has not adopted.
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive the
 * area with the [DocumentState] overload ([TextArea]) and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the current text value
 * @param documentListener the listener notified of document edits
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param rows the number of rows
 * @param columns the number of columns
 * @param editable whether the user can edit the text
 * @param lineWrap whether lines too long for the area's width are wrapped onto the next line; `false`
 *   by default, so long lines run past the width
 * @param wrapStyleWord whether wrapped lines break at word boundaries rather than at character
 *   boundaries; `false` by default, and only consulted while [lineWrap] is on
 * @param tabSize the number of characters a tab expands to, `8` by default
 * @see TextArea the [DocumentState]-driven overload for large or complex editors
 * @see javax.swing.JTextArea
 */
@Composable
public fun TextArea(
    value: @Nls String,
    documentListener: DocumentListener,
    modifier: SwingModifier = SwingModifier,
    rows: Int = 0,
    columns: Int = 0,
    editable: Boolean = true,
    lineWrap: Boolean = false,
    wrapStyleWord: Boolean = false,
    tabSize: Int = DEFAULT_TAB_SIZE,
) {
    val mirror = rememberMirrorState(value)
    TextAreaNode(
        value = value,
        mirror = mirror,
        modifier = modifier.documentListener(documentListener).textMirror(mirror),
        rows = rows,
        columns = columns,
        editable = editable,
        lineWrap = lineWrap,
        wrapStyleWord = wrapStyleWord,
        tabSize = tabSize,
    )
}

/**
 * The `JTextArea` node both [TextArea] overloads render. [value] is settled through [declareText].
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun TextAreaNode(
    value: @Nls String,
    mirror: MirrorState<String>,
    modifier: SwingModifier,
    rows: Int,
    columns: Int,
    editable: Boolean,
    lineWrap: Boolean,
    wrapStyleWord: Boolean,
    tabSize: Int,
) {
    SwingNode(
        factory = { JTextArea(rows, columns) },
        update = {
            update(rows) {
                this.rows = it
                revalidate()
            }
            update(columns) {
                this.columns = it
                revalidate()
            }
            declareText(value, mirror)
            set(editable) { this.isEditable = it }
            set(lineWrap) { this.lineWrap = it }
            set(wrapStyleWord) { this.wrapStyleWord = it }
            set(tabSize) { this.tabSize = it }
            applyModifier(modifier)
        },
    )
}

/**
 * A composable wrapper for JTextArea driven by a [DocumentState]. The area renders the state's own
 * document, so text typed into the area and edits made through the state are the same content, and the
 * caret is kept two-way with [DocumentState.selection]. The state is the single source of truth;
 * there is no `onValueChange`.
 *
 * @param state the hoistable text state the area renders and drives.
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param rows the number of rows
 * @param columns the number of columns
 * @param editable whether the user can edit the text
 * @param lineWrap whether lines too long for the area's width are wrapped onto the next line; `false`
 *   by default, so long lines run past the width
 * @param wrapStyleWord whether wrapped lines break at word boundaries rather than at character
 *   boundaries; `false` by default, and only consulted while [lineWrap] is on
 * @param tabSize the number of characters a tab expands to, `8` by default
 * @see javax.swing.JTextArea
 */
@Composable
public fun TextArea(
    state: DocumentState,
    modifier: SwingModifier = SwingModifier,
    rows: Int = 0,
    columns: Int = 0,
    editable: Boolean = true,
    lineWrap: Boolean = false,
    wrapStyleWord: Boolean = false,
    tabSize: Int = DEFAULT_TAB_SIZE,
) {
    SwingNode(
        factory = { JTextArea(rows, columns) },
        update = {
            update(rows) {
                this.rows = it
                revalidate()
            }
            update(columns) {
                this.columns = it
                revalidate()
            }
            set(editable) { this.isEditable = it }
            set(lineWrap) { this.lineWrap = it }
            set(wrapStyleWord) { this.wrapStyleWord = it }
            applyModifier(modifier.documentStateBinding(state))
            // A tab size is held by the document rather than by the area, so it is declared after the
            // modifier has installed the state's own document: a size written before that swap stays
            // behind on the document the swap discards. Keying it on the state as well re-declares it
            // onto the document a later state brings.
            set(state to tabSize) { (_, size) -> this.tabSize = size }
        },
    )
}

/** The tab width `JTextArea.getTabSize` reports by default, where the document names none. */
private const val DEFAULT_TAB_SIZE = 8
