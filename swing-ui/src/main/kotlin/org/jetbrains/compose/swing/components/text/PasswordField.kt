@file:JvmMultifileClass
@file:JvmName("TextComponentsKt")

package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.documentListener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.rememberAppliedValue
import java.util.Arrays
import javax.swing.JPasswordField
import javax.swing.event.DocumentListener
import javax.swing.text.Document
import javax.swing.text.Segment

/**
 * A composable wrapper for JPasswordField.
 *
 * The value is a [CharArray] of raw characters rather than a `String`, keeping this wrapper's own
 * boundary - [value], [onValueChange], and the comparison it makes against the field's current
 * characters - free of an extra, unzeroable `String` copy of the password. Committing an edit still
 * goes through `JPasswordField.setText(String)`, which materializes the password as a `String` on its
 * way into the field. That copy, the characters this wrapper mirrors internally to settle a move away
 * from [value], and any copy Swing itself retains, are all outside what a caller can zero.
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
    val applied = rememberAppliedValue(value)
    // Deliver the raw characters by reading the document into a char array via a Segment, keeping
    // the password out of an unzeroable String. The array applied mirrors is retained as-is; the
    // callback is handed a distinct copy of its own, free to zero without corrupting that mirror.
    val listener =
        remember(applied) {
            documentChangeListener { event ->
                val current = event.document.fullPassword()
                if (applied.observed(current)) callback.value(current.copyOf())
            }
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
    val applied = rememberAppliedValue(value)
    // Feeds applied's mirror on every edit, alongside the caller's own raw listener, so the settlement
    // declarePassword makes keeps comparing against what the field currently holds rather than stale
    // characters from an edit nothing else observed.
    val mirror =
        remember(applied) {
            documentChangeListener { event -> applied.observed(event.document.fullPassword()) }
        }
    PasswordFieldNode(
        value = value,
        applied = applied,
        modifier = modifier.documentListener(documentListener).documentListener(mirror),
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
    applied: AppliedValue<CharArray>,
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
            declarePassword(value, applied)
            applyModifier(modifier)
        },
    )
}

/**
 * The pairing [declarePassword]'s `set` key needs: a [declared] value and the field's own [held]
 * characters, compared by content rather than the reference identity a [CharArray] otherwise has, so
 * the key changes exactly when either one does.
 */
private class PasswordSettlement(
    val declared: CharArray,
    val held: CharArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PasswordSettlement &&
                    declared.contentEquals(other.declared) &&
                    held.contentEquals(other.held)
            )

    override fun hashCode(): Int = 31 * declared.contentHashCode() + held.contentHashCode()
}

/**
 * Settles [value] onto this password field: written where the field does not already hold these
 * characters, through [applied] so the write does not echo back as the user's own. Reading
 * [AppliedValue.current] as part of the settlement key is what makes the field's own reported
 * characters an ordinary composition dependency, so a move away from [value] the caller does not adopt
 * is settled back on the very next pass rather than left standing until some later, unrelated
 * recomposition happens to run this again.
 *
 * `getPassword()` results read purely for the content comparison are zeroed once compared; the
 * characters [applied] mirrors afterward are not, and stay reachable, replaced rather than zeroed, until
 * the next settlement.
 */
private fun <C : JPasswordField> SwingNodeUpdater<C>.declarePassword(
    value: CharArray,
    applied: AppliedValue<CharArray>,
) {
    set(PasswordSettlement(value, applied.current)) { settlement ->
        val current = password
        try {
            if (!current.contentEquals(settlement.declared)) {
                applied.write { text = String(settlement.declared) }
            }
        } finally {
            Arrays.fill(current, '\u0000')
        }
        applied.observed(password)
    }
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
