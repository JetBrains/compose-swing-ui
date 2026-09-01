@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.binding
import javax.swing.AbstractButton
import javax.swing.ButtonGroup

/**
 * Enrolls this button in [group], so at most one of the buttons declared with it is selected. Requires
 * an `AbstractButton` target (a radio button, a toggle button, a checkbox, or a button menu item).
 *
 * ```
 * val group = remember { ButtonGroup() }
 * sizes.forEachIndexed { index, size ->
 *     RadioButton(
 *         text = size,
 *         modifier = SwingModifier.buttonGroup(group),
 *         selected = index == choice,
 *         onSelectedChange = { choice = index },
 *     )
 * }
 * ```
 *
 * The caller owns the group, and holding it stable - `remember { ButtonGroup() }` - is what keeps the
 * buttons declared with it one choice across recompositions. Membership follows the modifier: declaring
 * a different group moves the button to it, and the button leaves the group when the modifier leaves
 * the chain or the component leaves the composition or parks, so a departed button stops taking part
 * in the exclusion instead of lingering as a hidden member. One button belongs to one
 * group: declaring two on the same chain enrolls it in the last one.
 *
 * A group owns which of its members is selected and only ever moves that selection: a member holds it
 * until another one takes it, so declaring `selected = false` for every member leaves the one that has
 * it selected. A choice declared as an index, the empty choice included, is what
 * [RadioGroup][org.jetbrains.compose.swing.components.selection.RadioGroup] takes.
 *
 * @param group the group this button joins; its members need not be siblings, so one exclusion can span containers.
 * @return this chain with the group membership declared on it.
 * @see javax.swing.ButtonGroup.add
 */
public fun SwingModifier.buttonGroup(group: ButtonGroup): SwingModifier =
    binding(AbstractButton::class.java, group, ButtonGroup::add, ButtonGroup::remove)
