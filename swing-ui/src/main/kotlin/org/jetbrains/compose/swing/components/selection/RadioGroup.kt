@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.annotations.BoxAxis
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.style.RadioButtons
import org.jetbrains.compose.swing.components.style.RadioGroups
import org.jetbrains.compose.swing.components.style.SelectableStyleScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.NestedStyleProviderScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JRadioButton

/**
 * A set of mutually exclusive choices, backed by a shared `ButtonGroup`, so at most one option is
 * selected at a time.
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
 * A pick the caller does not answer with a matching [selectedIndex] is settled back onto the declared
 * one, so the option shown selected is the one the composition holds.
 *
 * @param selectedIndex the index of the selected option (controlled); an out-of-range value, such as
 *   `-1`, leaves every option unselected
 * @param onSelectionChange callback invoked with the option's index when the user selects it
 * @param modifier the [SwingModifier] applied to the group's panel
 * @param style the [SwingStyle] applied to the group's panel; it wins over what a style provided for [RadioGroups] sets
 * @param axis the axis along which the options are arranged (a [BoxAxis] `BoxLayout` value);
 *   `Y_AXIS` - the default - stacks them in a column
 * @param content declares the options; see [RadioGroupScope]
 * @see javax.swing.ButtonGroup
 */
@Composable
public fun RadioGroup(
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ContainerStyleScope> = SwingStyle,
    @BoxAxis axis: Int = BoxLayout.Y_AXIS,
    content: RadioGroupScope.() -> Unit,
) {
    // Collected fresh on every pass, so an option the caller stops declaring loses its button (see SwingNode).
    val scope = RadioGroupScopeImpl().apply(content)
    val group = rememberButtonGroup()

    val layout = PanelLayout.Box(axis = axis)

    SwingNode<JPanel>(
        factory = { JPanel() },
        modifier = modifier.styleable(RadioGroupKind, style),
        update = {
            layout.installOn(this)
        },
        content = {
            key(layout.javaClass) {
                scope.options.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    ButtonGroupOption(
                        group,
                        index,
                        option.modifier,
                        selected,
                        onSelectionChange,
                    ) { optionModifier, mirror ->
                        SwingNode(
                            factory = { JRadioButton() },
                            modifier = optionModifier.styleable(RadioOptionKind, selected, option.style),
                            update = {
                                set(option.text) { this.text = it }
                                init { addItemListener { mirror.observed(isSelected) } }
                                declare(selected, mirror, { isSelected }, { applyGroupSelection(group, it) })
                            },
                        )
                    }
                }
            }
        },
    )
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
     * @param style the [SwingStyle] applied to this option's radio button
     * @see javax.swing.JRadioButton
     */
    public fun option(
        text: @Nls String,
        modifier: SwingModifier = SwingModifier,
        style: SwingStyle<SelectableStyleScope> = SwingStyle,
    )
}

/** One declared choice: its label, the [SwingModifier] and the [SwingStyle] for its button. */
private class RadioOption(
    val text: @Nls String,
    val modifier: SwingModifier,
    val style: SwingStyle<SelectableStyleScope>,
)

private class RadioGroupScopeImpl : RadioGroupScope {
    val options: MutableList<RadioOption> = ArrayList()

    override fun option(
        text: @Nls String,
        modifier: SwingModifier,
        style: SwingStyle<SelectableStyleScope>,
    ) {
        options.add(RadioOption(text, modifier, style))
    }
}

/** [ContainerStyleScope] for RadioGroup styles. No component state is supplied. */
private val RadioGroupKind =
    nestingStyleKind<ContainerStyleScope, Unit>(
        { base, _ -> object : ContainerStyleScope, NestedStyleProviderScope by base {} },
    ) {
        applyNestedStyle(RadioGroups)
    }

/** [SelectableStyleScope] for RadioOption styles. The declared selected state supplies `isSelected`. */
private val RadioOptionKind =
    nestingStyleKind<SelectableStyleScope, Boolean>({ base, selected ->
        object : SelectableStyleScope, ComponentStyleScope by base {
            override val isSelected: Boolean get() = selected
        }
    }) {
        applyNestedStyle(RadioButtons)
    }
