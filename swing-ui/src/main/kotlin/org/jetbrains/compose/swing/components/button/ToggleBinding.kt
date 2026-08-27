package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.itemListener
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import java.awt.event.ActionListener
import javax.swing.AbstractButton

/**
 * Wires the reporting half of a two-state `AbstractButton` (`JCheckBox`, `JRadioButton`, `JToggleButton`, and their
 * menu-item counterparts). The widget reports its new selected state on every toggle, both its own and the user's, so
 * the listener tells them apart by value: a toggle landing on [selected] is the declaration arriving; anything else is
 * the user's own change, reported through [onSelectedChange].
 *
 * @return the [SwingModifier] to apply on top of the caller's own, and the [MirrorState] to pass to [declareSelected]
 */
@Composable
internal fun rememberToggleReporting(
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
): Pair<SwingModifier, MirrorState<Boolean>> {
    val mirror = rememberMirrorState(selected)
    val reporting =
        SwingModifier.actionListener { event ->
            mirror.report((event.source as AbstractButton).isSelected, onSelectedChange)
        }
    return reporting to mirror
}

/**
 * Wires the mirroring half of a two-state `AbstractButton` for a caller that supplies its own
 * [actionListener] and so keeps the action channel for itself: what is [selected] instead watches the
 * widget's own item channel, so a toggle the caller's listener does not adopt is still put back without
 * the wrapper taking a slot on the channel the caller was handed.
 *
 * @return the [SwingModifier] to apply on top of the caller's own - carrying [actionListener] as-is plus
 *   the mirror - and the [MirrorState] to pass to [declareSelected]
 */
@Composable
internal fun rememberToggleMirroring(
    selected: Boolean,
    actionListener: ActionListener,
): Pair<SwingModifier, MirrorState<Boolean>> {
    val mirror = rememberMirrorState(selected)
    return SwingModifier.actionListener(actionListener).toggleMirror(mirror) to mirror
}

/**
 * Feeds [mirror]'s mirror the button's selected state on every toggle, riding the item channel so the
 * action channel stays the caller's alone.
 */
private fun SwingModifier.toggleMirror(mirror: MirrorState<Boolean>): SwingModifier =
    itemListener { event -> mirror.observed((event.source as AbstractButton).isSelected) }

/**
 * Settles the selected declaration a two-state `AbstractButton` node renders against the widget's own
 * `isSelected`/`setSelected` pair through [mirror], as returned by [rememberToggleReporting] or
 * [rememberToggleMirroring].
 */
internal fun <C : AbstractButton> SwingNodeUpdater<C>.declareSelected(
    selected: Boolean,
    mirror: MirrorState<Boolean>,
) {
    declare(selected, mirror, AbstractButton::isSelected, AbstractButton::setSelected)
}
