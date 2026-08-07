@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.constants.BoxAxis
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import javax.swing.BoxLayout
import javax.swing.JRadioButton

/**
 * A composable wrapper for a group of mutually exclusive radio buttons backed by a shared
 * `ButtonGroup`, so at most one option is selected at a time.
 *
 * Declare the choices in [content]; each `option(...)` becomes a `JRadioButton` laid out in the group's
 * panel in call order. The selected option is controlled via [selectedIndex] (the zero-based position
 * of the chosen `option`, or any out-of-range value such as `-1` for no selection); clicking an option
 * selects it and invokes [onSelectionChange] with its index, while external [selectedIndex] changes
 * move the selection to match.
 *
 * ```
 * RadioGroup(selectedIndex = choice, onSelectionChange = { choice = it }) {
 *     option("Small")
 *     option("Medium")
 *     option("Large")
 * }
 * ```
 *
 * @param selectedIndex the index of the selected option (controlled); an out-of-range value, such as
 *   `-1`, leaves every option unselected
 * @param onSelectionChange callback invoked with the option's index when the user selects it
 * @param modifier the [SwingModifier] applied to the group's panel
 * @param axis the axis along which the options are arranged (a [BoxAxis] `BoxLayout` value)
 * @param content declares the options; see [RadioGroupScope]
 * @see javax.swing.ButtonGroup
 */
@Composable
public fun RadioGroup(
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: SwingModifier = SwingModifier,
    @BoxAxis axis: Int = BoxLayout.Y_AXIS,
    content: RadioGroupScope.() -> Unit,
) {
    // Collected fresh on every pass, so an option the caller stops declaring loses its button (see SwingNode).
    val scope = RadioGroupScopeImpl().apply(content)
    val group = rememberButtonGroup()

    BoxPanel(modifier = modifier, axis = axis) {
        scope.options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            ButtonGroupOption(group, index, option.modifier, selected, onSelectionChange) { optionModifier, applied ->
                SwingNode(
                    factory = { JRadioButton() },
                    update = {
                        set(option.text) { this.text = it }
                        declare(selected, applied, { isSelected }, { applyGroupSelection(group, it) })
                        applyModifier(optionModifier)
                    },
                )
            }
        }
    }
}

/**
 * Declarative choices of a [RadioGroup]. Each [option] call appends one radio button, in call order;
 * its position is the index reported to [RadioGroup]'s `onSelectionChange` and matched against
 * `selectedIndex`.
 *
 * @see javax.swing.ButtonGroup
 */
public sealed interface RadioGroupScope {
    /**
     * Declares one choice.
     *
     * @param text the label shown next to the radio button
     * @param modifier the [SwingModifier] applied to this option's radio button
     * @see javax.swing.JRadioButton
     */
    public fun option(
        text: @Nls String,
        modifier: SwingModifier = SwingModifier,
    )
}

/** One declared choice: its label and the [SwingModifier] for its button. */
private class RadioOption(
    val text: @Nls String,
    val modifier: SwingModifier,
)

private class RadioGroupScopeImpl : RadioGroupScope {
    val options: MutableList<RadioOption> = ArrayList()

    override fun option(
        text: @Nls String,
        modifier: SwingModifier,
    ) {
        options.add(RadioOption(text, modifier))
    }
}
