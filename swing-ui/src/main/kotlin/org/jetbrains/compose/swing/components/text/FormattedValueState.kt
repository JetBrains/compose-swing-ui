@file:JvmMultifileClass
@file:JvmName("TextComponentsKt")

package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.binding
import java.beans.PropertyChangeListener
import java.text.ParseException
import javax.swing.JFormattedTextField

/**
 * A hoistable state holder for a [FormattedTextField]: the value the field holds, whether the
 * characters it currently shows parse, and the gesture that takes a typed edit on demand.
 *
 * [value] is two-way: assigning it renders that value through the field's formatter, and a value the
 * field commits - the user pressing Enter, the field losing focus, or [commit] - is written back into
 * it. The state is the single source of truth; there is no `onValueChange`.
 *
 * [value] and [isEditValid] are snapshot-observable: reading one inside a composable (or a
 * `snapshotFlow` collector) subscribes to later commits and to what the user types, so the reader
 * recomposes as the field moves. Gating a form's save button on [isEditValid] is what that buys.
 *
 * A state drives at most one field: declaring it on a second one moves it there and leaves the first
 * unbound.
 *
 * @see javax.swing.JFormattedTextField
 */
@Stable
public class FormattedValueState internal constructor(
    initialValue: Any?,
) {
    private var editValidState by mutableStateOf(true)

    // The field this state drives and observes, or null while unbound. Written only by the binding
    // modifier node, whose lifecycle owns the relationship.
    private var target: JFormattedTextField? = null

    private val editValidListener =
        PropertyChangeListener { event -> editValidState = (event.source as JFormattedTextField).isEditValid }

    /**
     * The value the field renders, typed as the formatter produces it (an `Int`, a `Date`, a `String`,
     * ...). Assigning it re-renders the field's characters from the new value, which replaces characters
     * the user has typed but not committed; a value the field itself commits is written back here.
     *
     * @see javax.swing.JFormattedTextField.setValue
     */
    public var value: Any? by mutableStateOf(initialValue)

    /**
     * Whether the characters the field currently shows parse. It moves with what the user types rather
     * than with what the field commits, so a part-typed edit reports `false` while [value] stands where
     * the last commit left it. `true` while no field renders this state.
     *
     * @see javax.swing.JFormattedTextField.isEditValid
     */
    public val isEditValid: Boolean get() = editValidState

    /**
     * Takes the field's current text as its value, and returns whether it was taken. A commit that lands
     * on a value the field did not hold before moves [value] onto it, like any other commit.
     *
     * Committing is a gesture rather than a declaration: it parses the text where it is called and
     * leaves nothing behind, so no later pass commits again and what the user types afterwards stands.
     * It is what an application event - a dialog's confirm button, a toolbar action, a form the user
     * submits - takes an edit with that the user typed but never entered.
     *
     * `false` means nothing was committed: no field renders this state, the field has no formatter to
     * parse the text with, or the text does not parse - the state [isEditValid] reports as it is
     * reached. The text the user typed stays either way; a commit that could not be made leaves the
     * field on the value it already held.
     *
     * @see javax.swing.JFormattedTextField.commitEdit
     */
    public fun commit(): Boolean {
        val bound = target?.takeIf { it.formatter != null } ?: return false
        return try {
            bound.commitEdit()
            true
        } catch (_: ParseException) {
            false
        }
    }

    /**
     * Starts driving and observing [component], taking over from the field this state drove before, if
     * any, so a state renders at most one field. The field's current edit validity is adopted as the
     * state's own.
     */
    internal fun bind(component: JFormattedTextField) {
        if (target === component) return
        target?.let { unbind(it) }
        target = component
        component.addPropertyChangeListener("editValid", editValidListener)
        editValidState = component.isEditValid
    }

    /**
     * Stops observing [component], leaving it on the value it holds. The value this state holds is kept,
     * so binding again renders it; edit validity belongs to the field and goes back to `true`.
     */
    internal fun unbind(component: JFormattedTextField) {
        component.removePropertyChangeListener("editValid", editValidListener)
        if (target !== component) return
        target = null
        editValidState = true
    }
}

/**
 * Creates and remembers a [FormattedValueState] holding [initialValue].
 *
 * A later change to [initialValue] neither recreates the state nor moves the field; drive the field
 * afterwards through the returned state's [FormattedValueState.value].
 *
 * @param initialValue the value the field starts on.
 */
@Composable
public fun rememberFormattedValueState(initialValue: Any? = null): FormattedValueState =
    remember { FormattedValueState(initialValue) }

/** Binds [state] to the composable's field through the modifier chain; see [binding]. */
internal fun SwingModifier.formattedValueStateBinding(state: FormattedValueState): SwingModifier =
    binding(
        JFormattedTextField::class.java,
        "formattedValueState",
        state,
        FormattedValueState::bind,
        FormattedValueState::unbind,
    )
