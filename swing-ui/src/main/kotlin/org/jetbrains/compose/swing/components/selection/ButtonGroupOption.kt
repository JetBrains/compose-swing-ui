package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import java.awt.event.ActionListener
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
 * Wires one option of a controlled group selection and emits it through [content], which receives the
 * [SwingModifier] the option's node has to apply: the declared [modifier], the listener reporting a user
 * selection, and the membership that enrolls the node in [group].
 *
 * The option's composition identity is its position, so the applier installs and uninstalls nodes to
 * match the declarations while each joins and leaves [group] with its membership element. The listener
 * is stable for the option's slot and reads the latest [onSelectionChange].
 *
 * One option is the whole of what this wires. Which options there are is the scope contract of the
 * component declaring them, so the component collects them itself and calls this per option.
 *
 * @param group the group shared by every option of the same selection
 * @param index the option's zero-based position, which is what a user selection reports
 * @param modifier the modifier declared for this option
 * @param onSelectionChange callback invoked with [index] when the user selects this option
 * @param content emits the option's node with the modifier it has to apply
 */
@Composable
internal fun ButtonGroupOption(
    group: ButtonGroup,
    index: Int,
    modifier: SwingModifier,
    onSelectionChange: (Int) -> Unit,
    content: @Composable (SwingModifier) -> Unit,
) {
    key(index) {
        val onSelectionChangeState = rememberUpdatedState(onSelectionChange)
        val listener =
            remember {
                ActionListener { event ->
                    if ((event.source as AbstractButton).isSelected) onSelectionChangeState.value(index)
                }
            }
        content(modifier.actionListener(listener).buttonGroupMembership(group))
    }
}

/**
 * Keeps this button in [group] for exactly as long as it is in the composition: it joins when the
 * element is applied and leaves when the option is dropped or the node is released, recycled or
 * parked, so a departed option stops taking part in the group's exclusion instead of lingering as a
 * hidden member.
 *
 * The group instance is stable for the lifetime of the composable that owns it, so an element always
 * carries the group its node already joined.
 */
internal fun SwingModifier.buttonGroupMembership(group: ButtonGroup): SwingModifier =
    this then ButtonGroupMembershipElement(group)

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

private class ButtonGroupMembershipElement(
    private val group: ButtonGroup,
) : SwingModifier.Element<AbstractButton, ButtonGroupMembershipElement.Node> {
    override val targetType: Class<AbstractButton> get() = AbstractButton::class.java

    override fun create(): Node = Node(group)

    override fun update(node: Node) = Unit

    class Node(
        private val group: ButtonGroup,
    ) : SwingModifier.Node<AbstractButton>() {
        override fun onAttach(): Unit = group.add(component)

        override fun onDetach(): Unit = group.remove(component)
    }
}
