@file:JvmMultifileClass
@file:JvmName("ButtonComponentsKt")

package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.style.ButtonStyleScope
import org.jetbrains.compose.swing.components.style.Buttons
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.AllComponents
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import java.awt.event.ActionListener
import javax.swing.JButton

/**
 * A `JButton` push button labeled [text], which calls [onClick] each time the button is activated.
 * Activation is an event and nothing more: the button carries no value for the caller to hold.
 *
 * @param text the text to display on the button
 * @param onClick callback to be invoked when the button is clicked
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @see javax.swing.JButton
 */
@Composable
public fun Button(
    text: @Nls String,
    onClick: () -> Unit,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ButtonStyleScope> = SwingStyle,
) {
    ButtonNode(text = text, modifier = modifier.actionListener { onClick() }, style = style)
}

/**
 * A [Button] driven by a raw [ActionListener] instead of an `onClick` lambda.
 *
 * The [actionListener] is attached as-is and removed on the same instance; pass a stable instance
 * (e.g. `remember {}`) to avoid a detach/re-attach on every recomposition.
 *
 * @param text the text to display on the button
 * @param actionListener the listener notified when the button is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @see javax.swing.JButton
 */
@Composable
public fun Button(
    text: @Nls String,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ButtonStyleScope> = SwingStyle,
) {
    ButtonNode(text = text, modifier = modifier.actionListener(actionListener), style = style)
}

private val ButtonKind =
    nestingStyleKind<ButtonStyleScope, Unit>({ base, _ -> object : ButtonStyleScope, ComponentStyleScope by base {} }) {
        applyNestedStyle(AllComponents)
        applyNestedStyle(Buttons)
    }

/**
 * The `JButton` node both [Button] overloads render; [modifier] already carries the button's wiring.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun ButtonNode(
    text: @Nls String,
    modifier: SwingModifier,
    style: SwingStyle<ButtonStyleScope>,
) {
    SwingNode(
        factory = { JButton() },
        modifier = modifier.styleable(style, ButtonKind),
        update = {
            set(text) { this.text = it }
        },
    )
}
