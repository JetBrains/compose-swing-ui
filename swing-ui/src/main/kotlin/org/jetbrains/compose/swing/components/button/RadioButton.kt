@file:JvmMultifileClass
@file:JvmName("ButtonComponentsKt")

package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.style.RadioButtons
import org.jetbrains.compose.swing.components.style.SelectableStyleScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import org.jetbrains.compose.swing.node.report
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import java.awt.event.ActionListener
import javax.swing.JRadioButton

/**
 * A `JRadioButton`, a labeled button showing whether it is chosen. The button shows whatever [selected]
 * holds, and every activation the user makes is reported through [onSelectedChange]. Standing alone it
 * clears itself on a second click; making several buttons one choice takes a `ButtonGroup`.
 * [buttonGroup][org.jetbrains.compose.swing.modifier.interaction.buttonGroup] enrolls a button in one,
 * and [RadioGroup][org.jetbrains.compose.swing.components.selection.RadioGroup] declares a whole choice.
 *
 * An activation the caller does not answer with a matching [selected] does not stand: the button is back
 * on the declared state before the click is painted.
 *
 * @param text the text to display next to the radio button
 * @param selected whether the radio button is selected
 * @param onSelectedChange callback invoked with the selected state when the button is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @see javax.swing.JRadioButton
 */
@Composable
public fun RadioButton(
    text: @Nls String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SelectableStyleScope> = SwingStyle,
) {
    val mirror = rememberMirrorState(selected)
    RadioButtonNode(
        text = text,
        modifier =
            modifier.actionListener<JRadioButton> {
                mirror.report { onSelectedChange(isSelected) }
            },
        style = style,
        selected = selected,
        mirror = mirror,
    )
}

/**
 * A [RadioButton] driven by a raw [ActionListener] instead of an `onSelectedChange` lambda. The
 * [actionListener] is attached as-is and removed on the same instance; pass a stable instance
 * (e.g. `remember {}`) to avoid churn.
 *
 * @param text the text to display next to the radio button
 * @param selected whether the radio button is selected
 * @param actionListener the listener notified when the radio button is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @see javax.swing.JRadioButton
 */
@Composable
public fun RadioButton(
    text: @Nls String,
    selected: Boolean,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SelectableStyleScope> = SwingStyle,
) {
    val mirror = rememberMirrorState(selected)
    RadioButtonNode(
        text = text,
        modifier =
            modifier.actionListener(actionListener),
        style = style,
        selected = selected,
        mirror = mirror,
    )
}

/** [SelectableStyleScope] for RadioButton styles. The declared selected state supplies `isSelected`. */
private val RadioButtonKind =
    nestingStyleKind<SelectableStyleScope, Boolean>({ base, selected ->
        object : SelectableStyleScope, ComponentStyleScope by base {
            override val isSelected: Boolean get() = selected
        }
    }) {
        applyNestedStyle(RadioButtons)
    }

/**
 * The `JRadioButton` node both [RadioButton] overloads render. [selected] is settled against the button
 * through [mirror] rather than applied on change: the user can take the button out from under the
 * declaration, and a declaration equal to the last one still has to stand.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun RadioButtonNode(
    text: @Nls String,
    modifier: SwingModifier,
    style: SwingStyle<SelectableStyleScope>,
    selected: Boolean,
    mirror: MirrorState<Boolean>,
) {
    SwingNode(
        factory = { JRadioButton() },
        modifier = modifier.styleable(RadioButtonKind, selected, style),
        update = {
            set(text) { this.text = it }
            init { addItemListener { mirror.observed(isSelected) } }
            declare(selected, mirror, JRadioButton::isSelected, JRadioButton::setSelected)
        },
    )
}
