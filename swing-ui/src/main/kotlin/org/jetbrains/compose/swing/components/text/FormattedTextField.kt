@file:JvmMultifileClass
@file:JvmName("TextComponentsKt")

package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.AppliedValue
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.constants.FocusLostBehavior
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.rememberAppliedValue
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
 * reported, so a callback that writes [value] back does not loop, and a [value] the field has already
 * committed is left alone rather than written again - the characters typed since that commit stay.
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
 * @param value the committed, typed value
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param formatterFactory the factory producing the field's formatter, or `null` for the default
 * @param onValueChange callback invoked with the parsed value when the field commits an edit;
 *   applying [value] is not itself reported
 * @param focusLostBehavior what to do with a partial edit when the field loses focus (a
 *   [FocusLostBehavior] `JFormattedTextField` constant)
 * @param columns the preferred width in columns; `0` sizes to the content
 * @param editable whether the user can edit the text
 */
@Composable
public fun FormattedTextField(
    value: Any?,
    modifier: SwingModifier = SwingModifier,
    formatterFactory: AbstractFormatterFactory? = null,
    onValueChange: (Any?) -> Unit = {},
    @FocusLostBehavior focusLostBehavior: Int = JFormattedTextField.COMMIT_OR_REVERT,
    columns: Int = 0,
    editable: Boolean = true,
) {
    val callback = rememberUpdatedState(onValueChange)
    val applied = rememberAppliedValue(value)
    // An event carrying equal values commits nothing: the field regenerates its characters from the value
    // and fires the property whether or not the value moved, and `PropertyChangeSupport` filters only the
    // equal pairs that are both non-null. There is no new committed value to carry.
    val listener =
        remember(applied) {
            PropertyChangeListener { event ->
                if (event.oldValue == event.newValue) return@PropertyChangeListener
                val committed = (event.source as JFormattedTextField).value
                if (applied.observed(committed)) callback.value(committed)
            }
        }
    FormattedTextFieldNode(
        value = value,
        applied = applied,
        modifier = modifier.propertyChangeListener("value", listener),
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
 * Installing a formatter re-renders the committed value through it, which replaces characters the user
 * has typed but not committed. A [formatterFactory] is installed whenever a different instance is
 * declared, so hold one instance across recompositions (e.g. `remember { ... }`) and supply a new one only
 * where the formatting is meant to change.
 *
 * @param value the committed, typed value
 * @param valuePropertyChangeListener the listener notified when the committed `value` changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param formatterFactory the factory producing the field's formatter, or `null` for the default
 * @param focusLostBehavior what to do with a partial edit when the field loses focus (a
 *   [FocusLostBehavior] `JFormattedTextField` constant)
 * @param columns the preferred width in columns; `0` sizes to the content
 * @param editable whether the user can edit the text
 */
@Composable
public fun FormattedTextField(
    value: Any?,
    valuePropertyChangeListener: PropertyChangeListener,
    modifier: SwingModifier = SwingModifier,
    formatterFactory: AbstractFormatterFactory? = null,
    @FocusLostBehavior focusLostBehavior: Int = JFormattedTextField.COMMIT_OR_REVERT,
    columns: Int = 0,
    editable: Boolean = true,
) {
    val applied = rememberAppliedValue(value)
    FormattedTextFieldNode(
        value = value,
        applied = applied,
        modifier = modifier.propertyChangeListener("value", valuePropertyChangeListener),
        formatterFactory = formatterFactory,
        focusLostBehavior = focusLostBehavior,
        columns = columns,
        editable = editable,
    )
}

/**
 * The `JFormattedTextField` node both [FormattedTextField] overloads render. [value] is pushed on change
 * only - unlike a declared selection or a scalar widget property, an un-adopted commit is not undone on
 * some later, unrelated recomposition: nothing here reads [applied]'s mirror to gate the push, so typing
 * is never fought without a fresh [value] declaring otherwise.
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
            set(value) { declared -> applied.settle(declared, { this.value }, { this.value = it }) {} }
            set(editable) { this.isEditable = it }
            applyModifier(modifier)
        },
    )
}
