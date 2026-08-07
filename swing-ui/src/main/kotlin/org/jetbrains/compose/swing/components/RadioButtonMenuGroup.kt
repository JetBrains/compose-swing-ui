@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.selection.ButtonGroupOption
import org.jetbrains.compose.swing.components.selection.applyGroupSelection
import org.jetbrains.compose.swing.components.selection.rememberButtonGroup
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.node.declare
import javax.swing.JRadioButtonMenuItem
import javax.swing.KeyStroke

/**
 * A composable wrapper for a group of mutually exclusive radio button menu items backed by a shared
 * `ButtonGroup`, so at most one option is selected at a time.
 *
 * Declare the choices in [content]; each `option(...)` becomes a `JRadioButtonMenuItem` placed in the
 * surrounding menu in call order, among whatever other items that menu declares. The selected option
 * is controlled via [selectedIndex] (the zero-based position of the chosen `option`, or any
 * out-of-range value such as `-1` for no selection); selecting an option invokes [onSelectionChange]
 * with its index, while external [selectedIndex] changes move the selection to match.
 *
 * ```
 * Menu("View") {
 *     RadioButtonMenuGroup(selectedIndex = density, onSelectionChange = { density = it }) {
 *         option("Comfortable")
 *         option("Compact")
 *     }
 * }
 * ```
 *
 * @param selectedIndex the index of the selected option (controlled); an out-of-range value, such as
 *   `-1`, leaves every option unselected
 * @param onSelectionChange callback invoked with the option's index when the user selects it
 * @param content declares the options; see [RadioButtonMenuGroupScope]
 * @see javax.swing.ButtonGroup
 */
@Composable
public fun RadioButtonMenuGroup(
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    content: RadioButtonMenuGroupScope.() -> Unit,
) {
    // Collected fresh on every pass, so an option the caller stops declaring loses its menu item (see SwingNode).
    val scope = RadioButtonMenuGroupScopeImpl().apply(content)
    val group = rememberButtonGroup()

    scope.options.forEachIndexed { index, option ->
        val selected = index == selectedIndex
        ButtonGroupOption(group, index, option.modifier, selected, onSelectionChange) { optionModifier, applied ->
            MenuNode(
                factory = { JRadioButtonMenuItem() },
                update = {
                    set(option.text) { this.text = it }
                    set(option.accelerator) { this.accelerator = it }
                    declare(selected, applied, { isSelected }, { applyGroupSelection(group, it) })
                    applyModifier(optionModifier)
                },
            )
        }
    }
}

/**
 * Declarative choices of a [RadioButtonMenuGroup]. Each [option] call appends one radio button menu
 * item, in call order; its position is the index reported to [RadioButtonMenuGroup]'s
 * `onSelectionChange` and matched against `selectedIndex`.
 *
 * @see javax.swing.ButtonGroup
 */
public sealed interface RadioButtonMenuGroupScope {
    /**
     * Declares one choice.
     *
     * @param text the text of the menu item
     * @param modifier the [SwingModifier] applied to this option's menu item
     * @param accelerator the key combination that activates the item without navigating the menu
     *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
     * @see javax.swing.JRadioButtonMenuItem
     */
    public fun option(
        text: @Nls String,
        modifier: SwingModifier = SwingModifier,
        accelerator: KeyStroke? = null,
    )
}

/** One declared choice: its text, accelerator and the [SwingModifier] for its menu item. */
private class RadioButtonMenuOption(
    val text: @Nls String,
    val modifier: SwingModifier,
    val accelerator: KeyStroke?,
)

private class RadioButtonMenuGroupScopeImpl : RadioButtonMenuGroupScope {
    val options: MutableList<RadioButtonMenuOption> = ArrayList()

    override fun option(
        text: @Nls String,
        modifier: SwingModifier,
        accelerator: KeyStroke?,
    ) {
        options.add(RadioButtonMenuOption(text, modifier, accelerator))
    }
}
