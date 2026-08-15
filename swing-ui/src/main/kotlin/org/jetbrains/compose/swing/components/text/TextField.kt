@file:JvmMultifileClass
@file:JvmName("TextComponentsKt")

package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.documentListener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberAppliedValue
import javax.swing.JTextField
import javax.swing.event.DocumentListener

/**
 * A composable wrapper for JTextField.
 *
 * This field is strictly controlled: if [onValueChange] does not answer with a matching [value], the
 * field is settled back onto the declared value on the very next pass. It never ends up holding text
 * the caller has not adopted. Settling back rewrites the whole document, which leaves the caret at its
 * end - a callback that filters a keystroke rather than adopting it sees the caret jump there on every
 * rejected edit.
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive
 * the field with the [DocumentState] overload and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the current text value
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param onValueChange callback invoked with the field's new text when the field is edited; applying
 *   [value] is not itself reported
 * @param columns the number of columns
 * @param editable whether the user can edit the text
 * @see TextField the [DocumentState]-driven overload for large or complex editors
 * @see javax.swing.JTextField
 */
@Composable
public fun TextField(
    value: @Nls String,
    modifier: SwingModifier = SwingModifier,
    onValueChange: (@Nls String) -> Unit = {},
    columns: Int = 0,
    editable: Boolean = true,
) {
    val applied = rememberAppliedValue(value)
    TextFieldNode(
        value = value,
        applied = applied,
        modifier = modifier.onTextEdit(applied, onValueChange),
        columns = columns,
        editable = editable,
    )
}

/**
 * A composable wrapper for JTextField driven by a raw [DocumentListener] instead of an `onValueChange`
 * lambda. The [documentListener] is attached to the field's document as-is and removed on the same
 * instance; pass a stable instance (e.g. `remember {}`) to avoid churn. It observes every change to
 * that document, including the one that applies [value].
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
 * @param columns the number of columns
 * @param editable whether the user can edit the text
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
    val applied = rememberAppliedValue(value)
    TextFieldNode(
        value = value,
        applied = applied,
        modifier = modifier.documentListener(documentListener).textMirror(applied),
        columns = columns,
        editable = editable,
    )
}

/**
 * The `JTextField` node both [TextField] overloads render. [value] is settled through [declareText].
 */
@Composable
private fun TextFieldNode(
    value: @Nls String,
    applied: AppliedValue<String>,
    modifier: SwingModifier,
    columns: Int,
    editable: Boolean,
) {
    SwingNode(
        factory = { JTextField(columns) },
        update = {
            update(columns) {
                this.columns = it
                revalidate()
            }
            declareText(value, applied)
            set(editable) { this.isEditable = it }
            applyModifier(modifier)
        },
    )
}

/**
 * A composable wrapper for JTextField driven by a [DocumentState]. The field renders the state's
 * own document, so text typed into the field and edits made through the state are the same content, and
 * the caret is kept two-way with [DocumentState.selection]. The state is the single source of
 * truth; there is no `onValueChange`.
 *
 * @param state the hoistable text state the field renders and drives.
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param columns the number of columns
 * @param editable whether the user can edit the text
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
        update = {
            update(columns) {
                this.columns = it
                revalidate()
            }
            set(editable) { this.isEditable = it }
            applyModifier(modifier.documentStateBinding(state))
        },
    )
}
