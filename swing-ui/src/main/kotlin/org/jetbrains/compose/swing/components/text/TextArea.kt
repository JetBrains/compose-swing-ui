@file:JvmMultifileClass
@file:JvmName("TextComponentsKt")

package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.documentListener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberAppliedValue
import javax.swing.JTextArea
import javax.swing.event.DocumentListener

/**
 * A composable wrapper for JTextArea.
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive the
 * area with the [DocumentState] overload ([TextArea]) and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the current text value
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param onValueChange callback invoked with the area's new text when the area is edited; applying
 *   [value] is not itself reported
 * @param rows the number of rows
 * @param columns the number of columns
 * @param editable whether the user can edit the text
 * @param lineWrap whether lines too long for the area's width are wrapped onto the next line; `false`
 *   by default, so long lines run past the width
 * @param wrapStyleWord whether wrapped lines break at word boundaries rather than at character
 *   boundaries; `false` by default, and only consulted while [lineWrap] is on
 * @see TextArea the [DocumentState]-driven overload for large or complex editors
 */
@Composable
public fun TextArea(
    value: String,
    modifier: SwingModifier = SwingModifier,
    onValueChange: (String) -> Unit = {},
    rows: Int = 0,
    columns: Int = 0,
    editable: Boolean = true,
    lineWrap: Boolean = false,
    wrapStyleWord: Boolean = false,
) {
    val callback = rememberUpdatedState(onValueChange)
    val applied = rememberAppliedValue(value)
    val listener = rememberUserEditListener(applied, callback)
    TextAreaNode(
        value = value,
        applied = applied,
        modifier = modifier.documentListener(listener),
        rows = rows,
        columns = columns,
        editable = editable,
        lineWrap = lineWrap,
        wrapStyleWord = wrapStyleWord,
    )
}

/**
 * A composable wrapper for JTextArea driven by a raw [DocumentListener] instead of an `onValueChange`
 * lambda. The [documentListener] is attached to the area's document as-is and removed on the same
 * instance; pass a stable instance (e.g. `remember {}`) to avoid churn. Being attached as-is, it
 * observes every change to that document, including the one that applies [value].
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
 * @see TextArea the [DocumentState]-driven overload for large or complex editors
 */
@Composable
public fun TextArea(
    value: String,
    documentListener: DocumentListener,
    modifier: SwingModifier = SwingModifier,
    rows: Int = 0,
    columns: Int = 0,
    editable: Boolean = true,
    lineWrap: Boolean = false,
    wrapStyleWord: Boolean = false,
) {
    val applied = rememberAppliedValue(value)
    TextAreaNode(
        value = value,
        applied = applied,
        modifier = modifier.documentListener(documentListener),
        rows = rows,
        columns = columns,
        editable = editable,
        lineWrap = lineWrap,
        wrapStyleWord = wrapStyleWord,
    )
}

/**
 * The `JTextArea` node both [TextArea] overloads render. [value] is settled through
 * [pushDeclaredText].
 */
@Composable
private fun TextAreaNode(
    value: String,
    applied: AppliedValue<String>,
    modifier: SwingModifier,
    rows: Int,
    columns: Int,
    editable: Boolean,
    lineWrap: Boolean,
    wrapStyleWord: Boolean,
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
            pushDeclaredText(value, applied)
            set(editable) { this.isEditable = it }
            set(lineWrap) { this.lineWrap = it }
            set(wrapStyleWord) { this.wrapStyleWord = it }
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
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param rows the number of rows.
 * @param columns the number of columns.
 * @param editable whether the user can edit the text.
 * @param lineWrap whether lines too long for the area's width are wrapped onto the next line; `false`
 *   by default, so long lines run past the width.
 * @param wrapStyleWord whether wrapped lines break at word boundaries rather than at character
 *   boundaries; `false` by default, and only consulted while [lineWrap] is on.
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
        },
    )
}
