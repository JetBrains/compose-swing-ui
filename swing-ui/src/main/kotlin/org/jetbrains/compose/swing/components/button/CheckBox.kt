@file:JvmMultifileClass
@file:JvmName("ButtonComponentsKt")

package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.itemListener
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import java.awt.event.ActionListener
import javax.swing.JCheckBox

/**
 * A `JCheckBox`, a labeled box the user turns on and off. The box shows whatever [checked] holds, and
 * every toggle the user makes is reported through [onCheckedChange]. Boxes are independent of one
 * another, so any number of them can be checked at once.
 *
 * A toggle the caller does not answer with a matching [checked] does not stand: the box is back on the
 * declared state before the toggle is painted.
 *
 * @param text the text to display next to the checkbox
 * @param checked the checked state the box is held at
 * @param onCheckedChange callback invoked with the new checked state when the user toggles the box
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
    val mirror = rememberMirrorState(checked)
    CheckBoxNode(
        text = text,
        modifier =
            modifier.actionListener<JCheckBox> {
                mirror.report(isSelected, onCheckedChange)
            },
        checked = checked,
        mirror = mirror,
    )
}

/**
 * A [CheckBox] driven by a raw [ActionListener] instead of an `onCheckedChange` lambda. The
 * [actionListener] is attached as-is and removed on the same instance; pass a stable instance
 * (e.g. `remember {}`) to avoid churn.
 *
 * @param text the text to display next to the checkbox
 * @param checked the checked state the box is held at
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
    val mirror = rememberMirrorState(checked)
    CheckBoxNode(
        text = text,
        modifier =
            modifier
                .actionListener(actionListener)
                .itemListener<JCheckBox> { mirror.observed(isSelected) },
        checked = checked,
        mirror = mirror,
    )
}

/**
 * The `JCheckBox` node both [CheckBox] overloads render. [checked] is settled against the box through
 * [mirror] rather than applied on change: the user can toggle the box out from under the declaration, and
 * a declaration equal to the last one still has to stand.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun CheckBoxNode(
    text: @Nls String,
    modifier: SwingModifier,
    checked: Boolean,
    mirror: MirrorState<Boolean>,
) {
    SwingNode(
        factory = { JCheckBox() },
        modifier = modifier,
        update = {
            set(text) { this.text = it }
            declare(checked, mirror, JCheckBox::isSelected, JCheckBox::setSelected)
        },
    )
}
