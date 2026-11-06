@file:JvmMultifileClass
@file:JvmName("MenuComponentsKt")

package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.selection.ButtonGroupOption
import org.jetbrains.compose.swing.components.selection.applyGroupSelection
import org.jetbrains.compose.swing.components.selection.rememberButtonGroup
import org.jetbrains.compose.swing.components.style.RadioButtonMenuItems
import org.jetbrains.compose.swing.components.style.SelectableStyleScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.style.AllComponents
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import javax.swing.JRadioButtonMenuItem
import javax.swing.KeyStroke

/**
 * A set of mutually exclusive choices in a menu, backed by a shared `ButtonGroup`, so at most one option
 * is selected at a time.
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
 * A pick the caller does not answer with a matching [selectedIndex] is settled back onto the declared
 * one, so the option shown selected is the one the composition holds.
 *
 * @param selectedIndex the index of the selected option (controlled); an out-of-range value, such as
 *   `-1`, leaves every option unselected
 * @param onSelectionChange callback invoked with the option's index when the user selects it
 * @param content declares the options; see [RadioButtonMenuGroupScope]
 * @see javax.swing.ButtonGroup
 */
@Composable
// Every option declares a chain of its own through the scope, so the group has no single item a
// modifier of its own could reach.
@Suppress("ModifierMissing")
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
        ButtonGroupOption(group, index, option.modifier, selected, onSelectionChange) { optionModifier, mirror ->
            val selectedState = rememberUpdatedState(selected)
            MenuNode(
                factory = { JRadioButtonMenuItem() },
                modifier = optionModifier.styleable(option.style, RadioButtonMenuOptionKind, selectedState),
                update = {
                    set(option.text) { this.text = it }
                    set(option.accelerator) { this.accelerator = it }
                    init { addItemListener { mirror.observed(isSelected) } }
                    declare(selected, mirror, { isSelected }, { applyGroupSelection(group, it) })
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
     * @param style the [SwingStyle] applied to this option's menu item
     * @param accelerator the key combination that activates the item without navigating the menu
     *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
     * @see javax.swing.JRadioButtonMenuItem
     */
    public fun option(
        text: @Nls String,
        modifier: SwingModifier = SwingModifier,
        style: SwingStyle<SelectableStyleScope> = SwingStyle,
        accelerator: KeyStroke? = null,
    )
}

/** One declared choice: its text, accelerator, and the [SwingModifier] and [SwingStyle] for its menu item. */
private class RadioButtonMenuOption(
    val text: @Nls String,
    val modifier: SwingModifier,
    val style: SwingStyle<SelectableStyleScope>,
    val accelerator: KeyStroke?,
)

private class RadioButtonMenuGroupScopeImpl : RadioButtonMenuGroupScope {
    val options: MutableList<RadioButtonMenuOption> = ArrayList()

    override fun option(
        text: @Nls String,
        modifier: SwingModifier,
        style: SwingStyle<SelectableStyleScope>,
        accelerator: KeyStroke?,
    ) {
        options.add(RadioButtonMenuOption(text, modifier, style, accelerator))
    }
}

private val RadioButtonMenuOptionKind =
    nestingStyleKind<SelectableStyleScope, State<Boolean>>({ base, selected ->
        object : SelectableStyleScope, ComponentStyleScope by base {
            override val isSelected: Boolean get() = selected.value
        }
    }) {
        applyNestedStyle(AllComponents)
        applyNestedStyle(RadioButtonMenuItems)
    }
