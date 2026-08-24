@file:JvmMultifileClass
@file:JvmName("ButtonComponentsKt")

package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.ActionListener
import javax.swing.JRadioButton

/**
 * A composable wrapper for JRadioButton.
 *
 * @param text the text to display next to the radio button
 * @param selected whether the radio button is selected
 * @param onSelectedChange callback invoked with the new selected state when the button is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.JRadioButton
 */
@Composable
public fun RadioButton(
    text: @Nls String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    val (reporting, applied) = rememberToggleReporting(selected, onSelectedChange)
    RadioButtonNode(
        text = text,
        modifier = modifier.then(reporting),
        selected = selected,
        applied = applied,
    )
}

/**
 * A composable wrapper for JRadioButton driven by a raw [ActionListener] instead of an
 * `onSelectedChange` lambda. The [actionListener] is attached as-is and removed on the same instance;
 * pass a stable instance (e.g. `remember {}`) to avoid churn.
 *
 * @param text the text to display next to the radio button
 * @param selected whether the radio button is selected
 * @param actionListener the listener notified when the radio button is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.JRadioButton
 */
@Composable
public fun RadioButton(
    text: @Nls String,
    selected: Boolean,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
) {
    val (mirroring, applied) = rememberToggleMirroring(selected, actionListener)
    RadioButtonNode(
        text = text,
        modifier = modifier.then(mirroring),
        selected = selected,
        applied = applied,
    )
}

/**
 * The `JRadioButton` node both [RadioButton] overloads render. [selected] is settled against the button
 * through [applied] rather than applied on change: the user can move the button out from under the
 * declaration, and a declaration equal to the last one still has to stand.
 */
@Composable
private fun RadioButtonNode(
    text: @Nls String,
    modifier: SwingModifier,
    selected: Boolean,
    applied: AppliedValue<Boolean>,
) {
    SwingNode(
        factory = { JRadioButton() },
        update = {
            set(text) { this.text = it }
            declareSelected(selected, applied)
            applyModifier(modifier)
        },
    )
}
