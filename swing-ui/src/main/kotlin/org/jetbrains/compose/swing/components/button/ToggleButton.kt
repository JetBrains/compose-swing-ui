@file:JvmMultifileClass
@file:JvmName("ButtonComponentsKt")

package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.ActionListener
import javax.swing.JToggleButton

/**
 * A composable wrapper for `JToggleButton`, a two-state button that stays in until clicked again.
 *
 * The selected state is controlled via [selected] + [onSelectedChange]: the button shows whatever
 * [selected] holds, and a click toggles it, reporting the new state through [onSelectedChange]. A state
 * the caller pushes in is reflected without echoing back through the callback.
 *
 * ```
 * ToggleButton(text = "Bold", selected = bold, onSelectedChange = { bold = it })
 * ```
 *
 * @param text the text to display on the button
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selected whether the button is in its selected state
 * @param onSelectedChange callback invoked with the new selected state when the button is toggled
 * @see javax.swing.JToggleButton
 */
@Composable
public fun ToggleButton(
    text: String,
    modifier: SwingModifier = SwingModifier,
    selected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
) {
    val (reporting, applied) = rememberToggleReporting(selected, onSelectedChange)
    ToggleButtonNode(
        text = text,
        modifier = modifier.then(reporting),
        selected = selected,
        applied = applied,
    )
}

/**
 * A [ToggleButton] driven by a raw [ActionListener] instead of an `onSelectedChange` lambda. The
 * listener is attached as-is and removed on the same instance; pass a stable instance (e.g.
 * `remember {}`) to avoid churn.
 *
 * @param text the text to display on the button
 * @param actionListener the listener notified when the button is toggled
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selected whether the button is in its selected state
 * @see javax.swing.JToggleButton
 */
@Composable
public fun ToggleButton(
    text: String,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    selected: Boolean = false,
) {
    val (mirroring, applied) = rememberToggleMirroring(selected, actionListener)
    ToggleButtonNode(
        text = text,
        modifier = modifier.then(mirroring),
        selected = selected,
        applied = applied,
    )
}

/**
 * The `JToggleButton` node both [ToggleButton] overloads render. [selected] is settled against the button
 * through [applied] rather than applied on change: the user can toggle the button out from under the
 * declaration, and a declaration equal to the last one still has to stand.
 */
@Composable
private fun ToggleButtonNode(
    text: String,
    modifier: SwingModifier,
    selected: Boolean,
    applied: AppliedValue<Boolean>,
) {
    SwingNode(
        factory = { JToggleButton() },
        update = {
            set(text) { this.text = it }
            declareSelected(selected, applied)
            applyModifier(modifier)
        },
    )
}
