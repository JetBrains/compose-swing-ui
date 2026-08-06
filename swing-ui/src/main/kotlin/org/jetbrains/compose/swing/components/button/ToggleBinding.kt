package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.itemListener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberAppliedValue
import java.awt.event.ActionListener
import java.awt.event.ItemListener
import javax.swing.AbstractButton

/**
 * Wires the reporting half of a two-state `AbstractButton` (`JCheckBox`, `JRadioButton`, `JToggleButton`, and their
 * menu-item counterparts). The widget reports its new selected state on every toggle, both its own and the user's, so
 * the listener tells them apart by value: a toggle landing on [selected] is the declaration arriving; anything else is
 * the user's own move, reported through [onSelectedChange].
 *
 * @return the [SwingModifier] to apply on top of the caller's own, and the [AppliedValue] to pass to [declareSelected]
 */
@Composable
internal fun rememberToggleReporting(
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
): Pair<SwingModifier, AppliedValue<Boolean>> {
    val callback = rememberUpdatedState(onSelectedChange)
    val applied = rememberAppliedValue(selected)
    val listener =
        remember(applied) {
            ActionListener { event ->
                val isSelected = (event.source as AbstractButton).isSelected
                if (applied.observed(isSelected)) callback.value(isSelected)
            }
        }
    return SwingModifier.actionListener(listener) to applied
}

/**
 * Wires the mirroring half of a two-state `AbstractButton` for a caller that supplies its own
 * [actionListener] and so keeps the action channel for itself: what is [selected] instead watches the
 * widget's own item channel, so a toggle the caller's listener does not adopt is still put back without
 * the wrapper taking a slot on the channel the caller was handed.
 *
 * @return the [SwingModifier] to apply on top of the caller's own - carrying [actionListener] as-is plus
 *   the mirror - and the [AppliedValue] to pass to [declareSelected]
 */
@Composable
internal fun rememberToggleMirroring(
    selected: Boolean,
    actionListener: ActionListener,
): Pair<SwingModifier, AppliedValue<Boolean>> {
    val applied = rememberAppliedValue(selected)
    val observing =
        remember(applied) {
            ItemListener { event -> applied.observed((event.source as AbstractButton).isSelected) }
        }
    return SwingModifier.actionListener(actionListener).itemListener(observing) to applied
}

/**
 * Settles the selected declaration a two-state `AbstractButton` node renders against the widget's own
 * `isSelected`/`setSelected` pair through [applied], as returned by [rememberToggleReporting] or
 * [rememberToggleMirroring].
 */
internal fun <C : AbstractButton> SwingNodeUpdater<C>.declareSelected(
    selected: Boolean,
    applied: AppliedValue<Boolean>,
) {
    declare(selected, applied, AbstractButton::isSelected, AbstractButton::setSelected)
}
