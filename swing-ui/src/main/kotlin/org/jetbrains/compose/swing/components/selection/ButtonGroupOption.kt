package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.buttonGroup
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.itemListener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.rememberAppliedValue
import javax.swing.AbstractButton
import javax.swing.ButtonGroup

/**
 * The [ButtonGroup] every option of one grouped selection shares. It is stable for the lifetime of the
 * composable that declares it, so exclusion holds across recompositions while each option joins on its
 * first composition and leaves on removal.
 */
@Composable
internal fun rememberButtonGroup(): ButtonGroup = remember { ButtonGroup() }

/**
 * Wires one option of a controlled group selection and emits it through [content].
 *
 * The option's composition identity is its position, so the applier installs and uninstalls nodes to
 * match the declarations while each joins and leaves [group] with its membership element. Each listener
 * is installed once for the option's slot and reads the latest [onSelectionChange].
 *
 * The selection moves through the group, so an option can lose it without being touched: the member the
 * group clears raises no action event at all. The mirror therefore follows the button's own item
 * channel, which every member publishes its state on, while the action channel stays what reports the
 * user's pick.
 *
 * One option is the whole of what this wires. Which options there are is the scope contract of the
 * component declaring them, so the component collects them itself and calls this per option.
 *
 * @param group the group shared by every option of the same selection
 * @param index the option's zero-based position, which is what a user selection reports
 * @param modifier the modifier declared for this option
 * @param selected whether this option is the one the composition declares as selected
 * @param onSelectionChange callback invoked with [index] when the user selects this option
 * @param content receives the [SwingModifier] the option's node has to apply - [modifier], the
 *   listeners watching the option, and the membership that enrolls the node in [group] - along with the
 *   [AppliedValue] its node settles [selected] against
 */
@Composable
internal fun ButtonGroupOption(
    group: ButtonGroup,
    index: Int,
    modifier: SwingModifier,
    selected: Boolean,
    onSelectionChange: (Int) -> Unit,
    content: @Composable (SwingModifier, AppliedValue<Boolean>) -> Unit,
) {
    key(index) {
        val applied = rememberAppliedValue(selected)
        content(
            modifier
                .actionListener { event ->
                    if ((event.source as AbstractButton).isSelected) onSelectionChange(index)
                }.itemListener { event -> applied.observed((event.source as AbstractButton).isSelected) }
                .buttonGroup(group),
            applied,
        )
    }
}

/**
 * Moves this button to [selected] within [group], leaving it alone when it already is.
 *
 * Selecting is a plain write: the group clears the other members itself. Deselecting has to go through
 * the group, because a grouped button refuses `setSelected(false)` - its model asks the group, which
 * only ever moves the selection to another member and never withdraws it - so a controlled selection
 * could not be taken back at all. Clearing the group is safe here because only the member that
 * currently holds the selection ever reaches this branch.
 *
 * A programmatic selection does not fire the button's action listener, so reflecting a controlled
 * index never echoes back as a spurious selection callback.
 */
internal fun AbstractButton.applyGroupSelection(
    group: ButtonGroup,
    selected: Boolean,
) {
    if (isSelected == selected) return
    if (selected) isSelected = true else group.clearSelection()
}
