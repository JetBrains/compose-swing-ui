@file:JvmMultifileClass
@file:JvmName("TextComponentsKt")

package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.documentListener
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import javax.swing.JPasswordField
import javax.swing.event.DocumentListener
import javax.swing.text.Document
import javax.swing.text.Segment

/**
 * A composable wrapper for JPasswordField.
 *
 * The value is a [CharArray] of raw characters rather than a `String`, so [value], [onValueChange] and
 * the comparison this wrapper makes against the field's characters need no extra, unzeroable `String`
 * copy of the password. Committing an edit still makes one: `JPasswordField.setText` takes a `String`,
 * so the password is materialized as one on its way into the field. That copy, the characters this
 * wrapper goes on mirroring to settle a change away from [value], and any copy Swing itself retains are
 * all outside what a caller can zero.
 *
 * This field is strictly controlled: characters the field settles on that [onValueChange] does not
 * answer with a matching [value] are settled back onto the declared value on the very next pass, so the
 * field never ends up holding characters the caller has not adopted.
 *
 * Array ownership: the array delivered to [onValueChange] is a fresh copy owned by the receiver,
 * free to retain or zero. The [value] array stays owned by the caller, read only through the next
 * recomposition; zeroing it once it stops being the current value is the caller's responsibility.
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive the
 * field with [PasswordField] and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the current text value, as raw characters
 * @param onValueChange callback invoked with the field's new characters when the field is edited;
 *   applying [value] is not itself reported
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param echoChar the masking character; `null` applies the look-and-feel's installed echo character,
 *   and the NUL character (U+0000) shows the text in clear text
 * @param columns the number of columns
 * @param editable whether the user can edit the text
 * @see PasswordField the [DocumentState]-driven overload for large or complex editors
 * @see javax.swing.JPasswordField
 */
@Composable
public fun PasswordField(
    value: CharArray,
    onValueChange: (CharArray) -> Unit,
    modifier: SwingModifier = SwingModifier,
    echoChar: Char? = null,
    columns: Int = 0,
    editable: Boolean = true,
) {
    val mirror = rememberMirrorState(PasswordChars(value))
    PasswordFieldNode(
        value = value,
        mirror = mirror,
        // Deliver the raw characters by reading the document into a char array via a Segment, keeping
        // the password out of an unzeroable String. The array applied mirrors is retained as-is; the
        // callback is handed a distinct copy of its own, free to zero without corrupting that mirror.
        modifier = modifier.listener(PasswordEdit(mirror, onValueChange), PASSWORD_EDITS),
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
 * This field is strictly controlled: characters the field settles on that are not followed by [value]
 * moving to match are settled back onto the declared value on the very next pass, so the field never
 * ends up holding characters the caller has not adopted.
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
 * @see javax.swing.JPasswordField
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
    val mirror = rememberMirrorState(PasswordChars(value))
    PasswordFieldNode(
        value = value,
        mirror = mirror,
        modifier = modifier.documentListener(documentListener).passwordMirror(mirror),
        echoChar = echoChar,
        columns = columns,
        editable = editable,
    )
}

/**
 * The `JPasswordField` node both character-array [PasswordField] overloads render. [value] is settled
 * through [declarePassword].
 */
@Composable
private fun PasswordFieldNode(
    value: CharArray,
    mirror: MirrorState<PasswordChars>,
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
            declarePassword(value, mirror)
            applyModifier(modifier)
        },
    )
}

/**
 * Mirrors into [mirror] the characters the field holds after every edit, so the settlement
 * [declarePassword] makes compares against what the field currently holds rather than characters an edit
 * nothing else observed left behind.
 *
 * The listener rides the document the field currently holds, following a `document` property swap as the
 * text mirror does.
 */
private fun SwingModifier.passwordMirror(mirror: MirrorState<PasswordChars>): SwingModifier =
    listener(mirror, PASSWORD_MIRRORS)

/**
 * The characters a password field declares or holds, compared by content rather than the reference
 * identity a [CharArray] otherwise has, so a settlement runs exactly when the characters change.
 */
private class PasswordChars(
    val chars: CharArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PasswordChars && chars.contentEquals(other.chars))

    override fun hashCode(): Int = chars.contentHashCode()
}

/**
 * Declares [value] as this password field's characters, keeping [mirror] in sync with them through
 * [declare]: they are written where the field does not already hold them, through [mirror] so the write
 * does not echo back as the user's own, and characters the field settles on that the caller does not
 * answer with a matching [value] are settled back onto the declared ones on the pass that carries their
 * answer.
 *
 * `getPassword()` answers with a fresh array on every call and leaves zeroing it to whoever read it, and
 * a settlement reads one for each comparison it makes. Each read zeroes the array the read before it
 * produced, so the one array left standing is the last: the characters [mirror] goes on mirroring as
 * what the field held.
 */
private fun <C : JPasswordField> SwingNodeUpdater<C>.declarePassword(
    value: CharArray,
    mirror: MirrorState<PasswordChars>,
) {
    val lastRead = arrayOfNulls<CharArray>(1)
    declare(
        value = PasswordChars(value),
        mirror = mirror,
        read = {
            lastRead[0]?.fill('\u0000')
            PasswordChars(password).also { lastRead[0] = it.chars }
        },
        write = { text = String(it.chars) },
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
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param echoChar the masking character; `null` applies the look-and-feel's installed echo character,
 *   and the NUL character (U+0000) shows the text in clear text
 * @param columns the number of columns
 * @param editable whether the user can edit the text
 * @see javax.swing.JPasswordField
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

private val PASSWORD_MIRRORS =
    documentMirrorRegistration<MirrorState<PasswordChars>>(
        onEdit = { mirror, document ->
            if (!mirror.isWriting) mirror.observed(PasswordChars(document.fullPassword()))
        },
        onAdopt = { mirror, document -> mirror.observed(PasswordChars(document.fullPassword())) },
    )

/** What the `onValueChange`-driven overload declares, as one value the listener it registers reads. */
private class PasswordEdit(
    val mirror: MirrorState<PasswordChars>,
    val onValueChange: (CharArray) -> Unit,
)

// The array applied mirrors is retained as-is; the callback is handed a distinct copy of its own, free
// to zero without corrupting that mirror.
private val PASSWORD_EDITS =
    documentMirrorRegistration<PasswordEdit>(
        onEdit = { edit, document ->
            if (!edit.mirror.isWriting) {
                val current = document.fullPassword()
                if (edit.mirror.observed(PasswordChars(current))) edit.onValueChange(current.copyOf())
            }
        },
        onAdopt = { edit, document -> edit.mirror.observed(PasswordChars(document.fullPassword())) },
    )
