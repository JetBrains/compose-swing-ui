@file:JvmMultifileClass
@file:JvmName("ButtonComponentsKt")

package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.itemListener
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
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
 * @param selected whether the button is in its selected state
 * @param onSelectedChange callback invoked with the new selected state when the button is toggled
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.JToggleButton
 */
@Composable
public fun ToggleButton(
    text: @Nls String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    val mirror = rememberMirrorState(selected)
    ToggleButtonNode(
        text = text,
        modifier =
            modifier.actionListener<JToggleButton> {
                mirror.report(isSelected, onSelectedChange)
            },
        selected = selected,
        mirror = mirror,
    )
}

/**
 * A [ToggleButton] driven by a raw [ActionListener] instead of an `onSelectedChange` lambda. The
 * listener is attached as-is and removed on the same instance; pass a stable instance (e.g.
 * `remember {}`) to avoid churn.
 *
 * @param text the text to display on the button
 * @param selected whether the button is in its selected state
 * @param actionListener the listener notified when the button is toggled
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.JToggleButton
 */
@Composable
public fun ToggleButton(
    text: @Nls String,
    selected: Boolean,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
) {
    val mirror = rememberMirrorState(selected)
    ToggleButtonNode(
        text = text,
        modifier =
            modifier
                .actionListener(actionListener)
                .itemListener<JToggleButton> { mirror.observed(isSelected) },
        selected = selected,
        mirror = mirror,
    )
}

/**
 * The `JToggleButton` node both [ToggleButton] overloads render. [selected] is settled against the button
 * through [mirror] rather than applied on change: the user can toggle the button out from under the
 * declaration, and a declaration equal to the last one still has to stand.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun ToggleButtonNode(
    text: @Nls String,
    modifier: SwingModifier,
    selected: Boolean,
    mirror: MirrorState<Boolean>,
) {
    SwingNode(
        factory = { JToggleButton() },
        update = {
            set(text) { this.text = it }
            declare(selected, mirror, JToggleButton::isSelected, JToggleButton::setSelected)
            applyModifier(modifier)
        },
    )
}
