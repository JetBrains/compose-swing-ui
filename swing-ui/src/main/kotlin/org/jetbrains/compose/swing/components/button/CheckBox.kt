@file:JvmMultifileClass
@file:JvmName("ButtonComponentsKt")

package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.ActionListener
import javax.swing.JCheckBox

/**
 * A composable wrapper for JCheckBox.
 *
 * @param text the text to display next to the checkbox
 * @param checked whether the checkbox is checked
 * @param onCheckedChange callback invoked when the checked state changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.JCheckBox
 */
@Composable
public fun CheckBox(
    text: @Nls String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    val (reporting, mirror) = rememberToggleReporting(checked, onCheckedChange)
    CheckBoxNode(
        text = text,
        modifier = modifier.then(reporting),
        checked = checked,
        mirror = mirror,
    )
}

/**
 * A composable wrapper for JCheckBox driven by a raw [ActionListener] instead of an `onCheckedChange`
 * lambda. The [actionListener] is attached as-is and removed on the same instance; pass a stable
 * instance (e.g. `remember {}`) to avoid churn.
 *
 * @param text the text to display next to the checkbox
 * @param checked whether the checkbox is checked
 * @param actionListener the listener notified when the checkbox is toggled
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.JCheckBox
 */
@Composable
public fun CheckBox(
    text: @Nls String,
    checked: Boolean,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
) {
    val (mirroring, mirror) = rememberToggleMirroring(checked, actionListener)
    CheckBoxNode(
        text = text,
        modifier = modifier.then(mirroring),
        checked = checked,
        mirror = mirror,
    )
}

/**
 * The `JCheckBox` node both [CheckBox] overloads render. [checked] is settled against the box through
 * [mirror] rather than applied on change: the user can toggle the box out from under the declaration, and
 * a declaration equal to the last one still has to stand.
 */
@Composable
private fun CheckBoxNode(
    text: @Nls String,
    modifier: SwingModifier,
    checked: Boolean,
    mirror: MirrorState<Boolean>,
) {
    SwingNode(
        factory = { JCheckBox() },
        update = {
            set(text) { this.text = it }
            declareSelected(checked, mirror)
            applyModifier(modifier)
        },
    )
}
