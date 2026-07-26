@file:JvmMultifileClass
@file:JvmName("TextComponentsKt")

package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.AppliedWrite
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.SwingNodeUpdater
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.documentListener
import org.jetbrains.compose.swing.rememberAppliedWrite
import javax.swing.JPasswordField
import javax.swing.event.DocumentListener
import javax.swing.text.Document
import javax.swing.text.Segment

/**
 * A composable wrapper for JPasswordField.
 *
 * The value is a [CharArray] of raw characters rather than a `String`, so a security-sensitive caller
 * controls every copy of the password.
 *
 * Array ownership: the array delivered to [onValueChange] is a fresh copy owned by the receiver,
 * free to retain or zero. The [value] array stays owned by the caller, read only through the next
 * recomposition; zeroing it once it stops being the current value is the caller's responsibility.
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive the
 * field with [PasswordField] and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the current text value, as raw characters
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param onValueChange callback invoked with the field's new characters when the field is edited;
 *   applying [value] is not itself reported
 * @param echoChar the masking character; `null` applies the look-and-feel's installed echo character,
 *   and the NUL character (U+0000) shows the text in clear text
 * @param columns the number of columns
 * @param editable whether the user can edit the text
 * @see PasswordField the [DocumentState]-driven overload for large or complex editors
 */
@Composable
public fun PasswordField(
    value: CharArray,
    modifier: SwingModifier = SwingModifier,
    onValueChange: (CharArray) -> Unit = {},
    echoChar: Char? = null,
    columns: Int = 0,
    editable: Boolean = true,
) {
    val callback = rememberUpdatedState(onValueChange)
    // A CharArray compares by identity, not content, so there is no value to mirror here the way a
    // String- or Int-valued field does - only whether a write of this wrapper's own is in flight.
    val applied = rememberAppliedWrite()
    // Deliver the raw characters by reading the document into a char array via a Segment, keeping
    // the password out of an unzeroable String.
    val listener =
        remember(applied) {
            documentChangeListener { event -> if (!applied.isWriting) callback.value(event.document.fullPassword()) }
        }
    PasswordFieldNode(
        value = value,
        applied = applied,
        modifier = modifier.documentListener(listener),
        echoChar = echoChar,
        columns = columns,
        editable = editable,
    )
}

/**
 * A composable wrapper for JPasswordField driven by a raw [DocumentListener] instead of an
 * `onValueChange` lambda. The [documentListener] is attached to the field's document as-is and removed
 * on the same instance; pass a stable instance (e.g. `remember {}`) to avoid churn. Being attached
 * as-is, it observes every change to that document, including the one that applies [value].
 *
 * The [value] array stays owned by the caller, read only through the next recomposition; zeroing
 * it once it stops being the current value is the caller's responsibility.
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive the
 * field with [PasswordField] and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the current text value, as raw characters
 * @param documentListener the listener notified of document edits
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param echoChar the masking character; `null` applies the look-and-feel's installed echo character,
 *   and the NUL character (U+0000) shows the text in clear text
 * @param columns the number of columns
 * @param editable whether the user can edit the text
 * @see PasswordField the [DocumentState]-driven overload for large or complex editors
 */
@Composable
public fun PasswordField(
    value: CharArray,
    documentListener: DocumentListener,
    modifier: SwingModifier = SwingModifier,
    echoChar: Char? = null,
    columns: Int = 0,
    editable: Boolean = true,
) {
    val applied = rememberAppliedWrite()
    PasswordFieldNode(
        value = value,
        applied = applied,
        modifier = modifier.documentListener(documentListener),
        echoChar = echoChar,
        columns = columns,
        editable = editable,
    )
}

/**
 * The `JPasswordField` node both character-array [PasswordField] overloads render. [value] is written
 * as [applied]'s own write - marking it lets a listener narrowed to the user's own edits (the lambda
 * overload's) stay silent for it, where the raw overload's caller-supplied listener, attached as-is,
 * observes it like any other edit.
 */
@Composable
private fun PasswordFieldNode(
    value: CharArray,
    applied: AppliedWrite,
    modifier: SwingModifier,
    echoChar: Char?,
    columns: Int,
    editable: Boolean,
) {
    PasswordFieldImpl(
        echoChar = echoChar,
        columns = columns,
        editable = editable,
        update = {
            // CharArray has identity equality, so `set(value)` runs on every recomposition; the
            // content compare against the live getPassword() is what actually guards the write and
            // prevents resetting the caret when the field already holds these characters.
            set(value) {
                applied.write {
                    if (!this.password.contentEquals(it)) this.text = String(it)
                }
            }
            applyModifier(modifier)
        },
    )
}

/**
 * A composable wrapper for JPasswordField driven by a [DocumentState]. The field renders the
 * state's own document, so masked text typed into the field and edits made through the state are the
 * same content, and the caret is kept two-way with [DocumentState.selection]. The state is the
 * single source of truth.
 *
 * The state models plain text: [DocumentState.text] materializes the password as an ordinary
 * `String`, which the caller cannot zero and which persists on the heap until garbage-collected.
 *
 * @param state the hoistable text state the field renders and drives.
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param echoChar the masking character; `null` applies the look-and-feel's installed echo character,
 *   and the NUL character (U+0000) shows the text in clear text.
 * @param columns the number of columns.
 * @param editable whether the user can edit the text.
 */
@Composable
public fun PasswordField(
    state: DocumentState,
    modifier: SwingModifier = SwingModifier,
    echoChar: Char? = null,
    columns: Int = 0,
    editable: Boolean = true,
) {
    PasswordFieldImpl(
        echoChar = echoChar,
        columns = columns,
        editable = editable,
        update = {
            applyModifier(modifier.documentStateBinding(state))
        },
    )
}

/**
 * Shared scaffolding for the [PasswordField] overloads: constructs the field with [columns], keeps
 * [echoChar] and [editable] applied, and threads each overload's own binding through [update].
 */
@Composable
private fun PasswordFieldImpl(
    echoChar: Char?,
    columns: Int,
    editable: Boolean,
    update: SwingNodeUpdater<DefaultMaskPasswordField>.() -> Unit,
) {
    SwingNode(
        factory = { DefaultMaskPasswordField(columns) },
        update = {
            update(columns) {
                this.columns = it
                revalidate()
            }
            set(echoChar) { this.echoChar = it ?: defaultEchoChar }
            set(editable) { this.isEditable = it }
            update()
        },
    )
}

/**
 * A `JPasswordField` that keeps the echo character its look and feel installed on it, so a null
 * `echoChar` declaration restores that mask instead of leaving a custom one applied. The character
 * lives on the field, which is the one thing every composition driving the field addresses.
 */
private class DefaultMaskPasswordField(
    columns: Int,
) : JPasswordField(columns) {
    /** The masking character the look and feel installed when the field was constructed. */
    val defaultEchoChar: Char = echoChar
}

/**
 * Reads the full text of the receiver [Document] into a fresh [CharArray] via a [Segment], keeping
 * the password out of an unzeroable `String`, so a security-sensitive caller can zero the returned
 * array after use.
 */
private fun Document.fullPassword(): CharArray {
    val segment = Segment().apply { isPartialReturn = false }
    getText(0, length, segment)
    return segment.array.copyOfRange(segment.offset, segment.offset + segment.count)
}
