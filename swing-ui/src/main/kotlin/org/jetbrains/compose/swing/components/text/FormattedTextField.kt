@file:JvmMultifileClass
@file:JvmName("TextComponentsKt")

package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.constants.FocusLostBehavior
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberAppliedValue
import java.beans.PropertyChangeListener
import javax.swing.JFormattedTextField
import javax.swing.JFormattedTextField.AbstractFormatterFactory

/**
 * A composable wrapper for `JFormattedTextField`, for number, date, or masked input.
 *
 * The field parses and formats through [formatterFactory], which produces the formatter that maps
 * between the typed [value] and the displayed text (e.g. a `NumberFormatter`, a `DateFormatter`, or a
 * `MaskFormatter` for a fixed mask). With no factory the field falls back to the platform default,
 * which formats by the value's type.
 *
 * [value] is the committed, typed value (an `Int`, a `Date`, a `String`, ...); [onValueChange] fires
 * once per value the field commits from an edit, carrying the newly parsed value. Text the user types
 * that does not parse is not committed and produces no callback until it becomes valid. A commit that
 * leaves the value where it was carries nothing new and is not reported. Applying [value] is not itself
 * reported, so a callback that writes [value] back does not loop.
 *
 * This field is strictly controlled: a value the field commits that [onValueChange] does not answer
 * with a matching [value] is settled back onto the declared value on the very next pass, so the field
 * never ends up holding a value the caller has not adopted. A [value] the field has already committed
 * is left alone rather than written again - the characters typed since that commit stay.
 *
 * ```
 * FormattedTextField(
 *     value = amount,
 *     formatterFactory = remember {
 *         DefaultFormatterFactory(NumberFormatter().apply { valueClass = Int::class.javaObjectType })
 *     },
 *     onValueChange = { amount = it as Int },
 * )
 * ```
 *
 * A `NumberFormatter`'s `valueClass` decides the type [onValueChange] receives; set to
 * `Int::class.javaObjectType` here, it is what makes the committed value the `Int` the example casts to.
 *
 * Installing a formatter re-renders the committed value through it, which replaces characters the user
 * has typed but not committed. A [formatterFactory] is installed whenever a different instance is
 * declared, so hold one instance across recompositions (e.g. `remember { ... }`) and supply a new one only
 * where the formatting is meant to change.
 *
 * The text the user is part way through typing need not parse, and while it does not the field holds
 * its previous value: [onEditValidChange] reports that, so a form can mark the field or hold its submit
 * button back. Drive the field with the [FormattedValueState] overload to read that as state instead,
 * and to take a part-typed edit on demand rather than waiting for the field's own focus-lost behavior.
 *
 * @param value the committed, typed value
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param formatterFactory the factory producing the field's formatter, or `null` for the default
 * @param onValueChange callback invoked with the parsed value when the field commits an edit;
 *   applying [value] is not itself reported
 * @param onEditValidChange callback invoked with whether the text now parses, each time that changes
 * @param focusLostBehavior what to do with a partial edit when the field loses focus (a
 *   [FocusLostBehavior] `JFormattedTextField` constant)
 * @param columns the preferred width in columns; `0` sizes to the content
 * @param editable whether the user can edit the text
 * @see FormattedTextField the [FormattedValueState]-driven overload
 * @see javax.swing.JFormattedTextField
 */
@Composable
public fun FormattedTextField(
    value: Any?,
    modifier: SwingModifier = SwingModifier,
    formatterFactory: AbstractFormatterFactory? = null,
    onValueChange: (Any?) -> Unit = {},
    onEditValidChange: (Boolean) -> Unit = {},
    @FocusLostBehavior focusLostBehavior: Int = JFormattedTextField.COMMIT_OR_REVERT,
    columns: Int = 0,
    editable: Boolean = true,
) {
    val applied = rememberAppliedValue(value)
    FormattedTextFieldNode(
        value = value,
        applied = applied,
        modifier =
            modifier
                .onValueCommit { committed -> if (applied.observed(committed)) onValueChange(committed) }
                .onEditValidity(onEditValidChange),
        formatterFactory = formatterFactory,
        focusLostBehavior = focusLostBehavior,
        columns = columns,
        editable = editable,
    )
}

/**
 * A [FormattedTextField] driven by a raw [PropertyChangeListener] (bound to the `value` property)
 * instead of an `onValueChange` lambda. The listener is attached as-is and removed on the same
 * instance; pass a stable instance (e.g. `remember {}`) to avoid churn. Being attached as-is, it is
 * notified of every change to the `value` property, including the one that applies [value].
 *
 * This field is strictly controlled: a value the field commits that is not followed by [value] moving
 * to match is settled back onto the declared value on the very next pass, so the field never ends up
 * holding a value the caller has not adopted.
 *
 * Installing a formatter re-renders the committed value through it, which replaces characters the user
 * has typed but not committed. A [formatterFactory] is installed whenever a different instance is
 * declared, so hold one instance across recompositions (e.g. `remember { ... }`) and supply a new one only
 * where the formatting is meant to change.
 *
 * @param value the committed, typed value
 * @param valuePropertyChangeListener the listener notified when the committed `value` changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param formatterFactory the factory producing the field's formatter, or `null` for the default
 * @param onEditValidChange callback invoked with whether the text now parses, each time that changes
 * @param focusLostBehavior what to do with a partial edit when the field loses focus (a
 *   [FocusLostBehavior] `JFormattedTextField` constant)
 * @param columns the preferred width in columns; `0` sizes to the content
 * @param editable whether the user can edit the text
 * @see FormattedTextField the [FormattedValueState]-driven overload
 * @see javax.swing.JFormattedTextField
 */
@Composable
public fun FormattedTextField(
    value: Any?,
    valuePropertyChangeListener: PropertyChangeListener,
    modifier: SwingModifier = SwingModifier,
    formatterFactory: AbstractFormatterFactory? = null,
    onEditValidChange: (Boolean) -> Unit = {},
    @FocusLostBehavior focusLostBehavior: Int = JFormattedTextField.COMMIT_OR_REVERT,
    columns: Int = 0,
    editable: Boolean = true,
) {
    val applied = rememberAppliedValue(value)
    FormattedTextFieldNode(
        value = value,
        applied = applied,
        modifier =
            modifier
                .propertyChangeListener("value", valuePropertyChangeListener)
                .valueMirror(applied)
                .onEditValidity(onEditValidChange),
        formatterFactory = formatterFactory,
        focusLostBehavior = focusLostBehavior,
        columns = columns,
        editable = editable,
    )
}

/**
 * A [FormattedTextField] driven by a [FormattedValueState]. The field renders the state's value and
 * commits into it, and reports through the state whether the characters it currently shows parse. The
 * state is the single source of truth; there is no `onValueChange` and no `onEditValidChange`.
 *
 * ```
 * val amount = rememberFormattedValueState(10)
 * FormattedTextField(state = amount, formatterFactory = factory)
 * Button("Save", onClick = { if (amount.commit()) save(amount.value) })
 * ```
 *
 * Installing a formatter re-renders the committed value through it, which replaces characters the user
 * has typed but not committed. A [formatterFactory] is installed whenever a different instance is
 * declared, so hold one instance across recompositions (e.g. `remember { ... }`) and supply a new one only
 * where the formatting is meant to change.
 *
 * @param state the hoistable value state the field renders and drives
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param formatterFactory the factory producing the field's formatter, or `null` for the default
 * @param focusLostBehavior what to do with a partial edit when the field loses focus (a
 *   [FocusLostBehavior] `JFormattedTextField` constant)
 * @param columns the preferred width in columns; `0` sizes to the content
 * @param editable whether the user can edit the text
 * @see javax.swing.JFormattedTextField
 */
@Composable
public fun FormattedTextField(
    state: FormattedValueState,
    modifier: SwingModifier = SwingModifier,
    formatterFactory: AbstractFormatterFactory? = null,
    @FocusLostBehavior focusLostBehavior: Int = JFormattedTextField.COMMIT_OR_REVERT,
    columns: Int = 0,
    editable: Boolean = true,
) {
    val applied = rememberAppliedValue(state.value)
    FormattedTextFieldNode(
        value = state.value,
        applied = applied,
        modifier =
            modifier
                .onValueCommit { committed -> if (applied.observed(committed)) state.value = committed }
                .formattedValueStateBinding(state),
        formatterFactory = formatterFactory,
        focusLostBehavior = focusLostBehavior,
        columns = columns,
        editable = editable,
    )
}

/**
 * The `JFormattedTextField` node every [FormattedTextField] overload renders. [modifier] arrives
 * carrying the reporting each overload wires - the caller's own chain first.
 */
@Composable
private fun FormattedTextFieldNode(
    value: Any?,
    applied: AppliedValue<Any?>,
    modifier: SwingModifier,
    formatterFactory: AbstractFormatterFactory?,
    @FocusLostBehavior focusLostBehavior: Int,
    columns: Int,
    editable: Boolean,
) {
    SwingNode(
        factory = { JFormattedTextField() },
        update = {
            set(columns) {
                this.columns = it
                revalidate()
            }
            set(focusLostBehavior) { this.focusLostBehavior = it }
            set(formatterFactory) { this.setFormatterFactory(it) }
            // Writing a value reinstalls the formatter and regenerates the field's characters from it, so
            // a value the field has already committed is not written again: the characters the user has
            // typed since that commit survive a callback writing the committed value back.
            declare(value, applied, read = { this.value }, write = { this.value = it })
            set(editable) { this.isEditable = it }
            applyModifier(modifier)
        },
    )
}

/**
 * Runs [onCommit] with the value the field holds each time it commits a different one.
 *
 * An event carrying equal values commits nothing: the field regenerates its characters from the value and
 * fires the property whether or not the value moved, and `PropertyChangeSupport` filters only the equal
 * pairs that are both non-null.
 */
private fun SwingModifier.onValueCommit(onCommit: (Any?) -> Unit): SwingModifier =
    propertyChangeListener("value") { event ->
        // The field republishes its value on every commit attempt; only a value that moved is one to
        // report.
        if (event.oldValue != event.newValue) onCommit((event.source as JFormattedTextField).value)
    }

/**
 * Feeds [applied]'s mirror on every commit, alongside a caller's own raw listener, so the settlement the
 * node makes keeps comparing against the value the field holds now rather than a stale one from a commit
 * nothing else observed.
 */
private fun SwingModifier.valueMirror(applied: AppliedValue<Any?>): SwingModifier =
    propertyChangeListener("value") { event ->
        applied.observed((event.source as JFormattedTextField).value)
    }

/** Runs [onChange] with the field's edit validity each time the field reports it moved. */
private fun SwingModifier.onEditValidity(onChange: (Boolean) -> Unit): SwingModifier =
    propertyChangeListener("editValid") { event ->
        onChange((event.source as JFormattedTextField).isEditValid)
    }
