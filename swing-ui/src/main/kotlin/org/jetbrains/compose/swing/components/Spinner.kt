@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.changeListener
import javax.swing.JSpinner
import javax.swing.SpinnerModel
import javax.swing.event.ChangeListener

/**
 * A composable wrapper for `JSpinner` driven by a [SpinnerState]. The spinner renders the state's own
 * model, so a step taken through the spinner and a value written through the state are the same content.
 * The state is the single source of truth; observe the spinner through [SpinnerState.value].
 *
 * ```
 * val spinner = rememberSpinnerState(initialValue = 3, min = 0, max = 10)
 * Spinner(spinner)
 * Label("Count is ${spinner.value}")
 * ```
 *
 * @param state the hoistable spinner state the spinner renders and drives.
 * @param modifier the [SwingModifier] applied to the underlying component.
 */
@Composable
public fun Spinner(
    state: SpinnerState,
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { JSpinner(state.model) },
        update = {
            // The model, not the state, is what the spinner renders. A state observes its model for as
            // long as the caller remembers it, while a value handed to set() is remembered by the node
            // as well - a second observer of the caller's state, dropped when the node is parked or
            // recycled.
            set(state.model) { this.model = it }
            applyModifier(modifier)
        },
    )
}

/**
 * A `JSpinner` over an arbitrary [model], driven by a raw [changeListener] instead of a [SpinnerState].
 * The [changeListener] is attached as-is and removed on the same instance; pass a stable instance (e.g.
 * `remember {}`) to avoid churn. This is the escape hatch for a custom `SpinnerModel`; prefer
 * [Spinner] with a [SpinnerState] for the numeric and list cases.
 *
 * @param model the model the spinner renders.
 * @param changeListener the listener notified when the spinner's value changes.
 * @param modifier the [SwingModifier] applied to the underlying component.
 */
@Composable
public fun Spinner(
    model: SpinnerModel,
    changeListener: ChangeListener,
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { JSpinner(model) },
        update = {
            set(model) { this.model = it }
            applyModifier(modifier.changeListener(changeListener))
        },
    )
}
