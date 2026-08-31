@file:JvmMultifileClass
@file:JvmName("TextComponentsKt")

package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.documentListener
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberMirrorState
import javax.swing.JTextField
import javax.swing.event.DocumentListener

/**
 * A single line of editable plain text. The `JTextField` shows the text [value] declares, and every
 * edit reports the field's whole text through [onValueChange].
 *
 * This field is strictly controlled: if [onValueChange] does not answer with a matching [value], the
 * field is settled back onto the declared value on the very next pass. It never ends up holding text
 * the caller has not adopted. A callback that filters a keystroke rather than adopting it leaves the
 * caret where that keystroke would have gone.
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive
 * the field with the [DocumentState] overload and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the current text value
 * @param onValueChange callback invoked with the field's new text when the field is edited; applying
 *   [value] is not itself reported
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param columns the preferred width in columns; `0` by default, taking the width from the text the
 *   field holds
 * @param editable whether the user can type into the field; `true` by default
 * @see TextField the [DocumentState]-driven overload for large or complex editors
 * @see javax.swing.JTextField
 */
@Composable
public fun TextField(
    value: @Nls String,
    onValueChange: (@Nls String) -> Unit,
    modifier: SwingModifier = SwingModifier,
    columns: Int = 0,
    editable: Boolean = true,
) {
    val mirror = rememberMirrorState(value)
    TextFieldNode(
        value = value,
        mirror = mirror,
        modifier = modifier.onTextEdit(mirror, onValueChange),
        columns = columns,
        editable = editable,
    )
}

/**
 * A [TextField] driven by a raw [DocumentListener] instead of an `onValueChange` lambda. The
 * [documentListener] is attached to the field's document as-is and removed on the same instance; pass
 * a stable instance (e.g. `remember {}`) to avoid churn. It observes every change to that document,
 * including the one that applies [value].
 *
 * This field is strictly controlled: if text the field settles on is not followed by [value] moving to
 * match, it is settled back onto the declared value on the very next pass. It never ends up holding
 * text the caller has not adopted.
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive
 * the field with the [DocumentState] overload and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the current text value
 * @param documentListener the listener notified of document edits
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param columns the preferred width in columns; `0` by default, taking the width from the text the
 *   field holds
 * @param editable whether the user can type into the field; `true` by default
 * @see TextField the [DocumentState]-driven overload for large or complex editors
 * @see javax.swing.JTextField
 */
@Composable
public fun TextField(
    value: @Nls String,
    documentListener: DocumentListener,
    modifier: SwingModifier = SwingModifier,
    columns: Int = 0,
    editable: Boolean = true,
) {
    val mirror = rememberMirrorState(value)
    TextFieldNode(
        value = value,
        mirror = mirror,
        modifier = modifier.documentListener(documentListener).textMirror(mirror),
        columns = columns,
        editable = editable,
    )
}

/**
 * The `JTextField` node both [TextField] overloads render. [value] is settled through [declareText].
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun TextFieldNode(
    value: @Nls String,
    mirror: MirrorState<String>,
    modifier: SwingModifier,
    columns: Int,
    editable: Boolean,
) {
    SwingNode(
        factory = { JTextField(columns) },
        modifier = modifier,
        update = {
            update(columns) {
                this.columns = it
                revalidate()
            }
            declareText(value, mirror)
            set(editable) { this.isEditable = it }
        },
    )
}

/**
 * A [TextField] driven by a [DocumentState]. The field renders the state's own document, so text
 * typed into the field and edits made through the state are the same content, and the caret is kept
 * two-way with [DocumentState.selection]. The state is the single source of truth; there is no
 * `onValueChange`.
 *
 * @param state the hoistable text state the field renders and drives.
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param columns the preferred width in columns; `0` by default, taking the width from the text the
 *   field holds
 * @param editable whether the user can type into the field; `true` by default
 * @see javax.swing.JTextField
 */
@Composable
public fun TextField(
    state: DocumentState,
    modifier: SwingModifier = SwingModifier,
    columns: Int = 0,
    editable: Boolean = true,
) {
    SwingNode(
        factory = { JTextField(columns) },
        modifier = modifier.documentStateBinding(state),
        update = {
            update(columns) {
                this.columns = it
                revalidate()
            }
            set(editable) { this.isEditable = it }
        },
    )
}
