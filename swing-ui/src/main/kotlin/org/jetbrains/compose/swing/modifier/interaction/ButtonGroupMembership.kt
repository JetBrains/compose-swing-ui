// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * the modifier chain or the component leaves the composition or parks, so a departed button stops taking
 * part in the exclusion instead of lingering as a hidden member. One button belongs to one
 * group: declaring two on the same modifier chain enrolls it in the last one.
 *
 * A group owns which of its members is selected and only ever moves that selection: a member holds it
 * until another one takes it, so declaring `selected = false` for every member leaves the one that has
 * it selected, and a button declared selected takes the selection with it as it joins.
 *
 * The user's choice reports through the button they activated, a choice of the member already holding
 * the selection included: nothing moves, and that button reports the selection it holds. The member the
 * group clears reports nothing and keeps its own declaration, which takes the selection back where the
 * caller does not adopt the choice. A choice declared as an index, the empty choice included, is what
 * [RadioGroup][org.jetbrains.compose.swing.components.selection.RadioGroup] takes.
 *
 * @param group the group this button joins; its members need not be siblings, so one exclusion can span containers.
 * @return this modifier with the group membership declared on it.
 * @see javax.swing.ButtonGroup.add
 */
public fun SwingModifier.buttonGroup(group: ButtonGroup): SwingModifier =
    binding(AbstractButton::class.java, "buttonGroup", group, ::join, ButtonGroup::remove)

/**
 * Enrolls [button] in [group] holding the selection it was declared with. `ButtonGroup.add` clears a
 * button that arrives selected into a group that already holds a member, so the selection is written
 * back and the group moves it to the newcomer instead.
 *
 * Its mirror hears the clearing and the write back in that order and lands on the same value.
 */
private fun join(
    group: ButtonGroup,
    button: AbstractButton,
) {
    val selected = button.isSelected
    group.add(button)
    if (selected) button.isSelected = true
}
