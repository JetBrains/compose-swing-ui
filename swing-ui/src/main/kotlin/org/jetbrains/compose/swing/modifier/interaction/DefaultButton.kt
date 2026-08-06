@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JButton
import javax.swing.SwingUtilities

/**
 * Makes this button the window's default button - the one activated by pressing Enter regardless of
 * which component holds focus. The association is established against the surrounding window's root
 * pane once the tree is laid out; passing `false` releases it. Requires a `JButton` target.
 *
 * @param default whether this button is the window's default button.
 * @see javax.swing.JRootPane.setDefaultButton
 */
public fun SwingModifier.defaultButton(default: Boolean = true): SwingModifier = this then DefaultButtonElement(default)

private class DefaultButtonElement(
    private val default: Boolean,
) : SwingModifier.Element<JButton, DefaultButtonElement.Node> {
    override val targetType: Class<JButton> get() = JButton::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.default = default
        node.apply()
    }

    class Node : SwingModifier.Node<JButton>() {
        var default: Boolean = false

        fun apply() {
            val button = component
            val makeDefault = default
            // The root pane is reachable only after the button is attached; defer to the end of the
            // current layout batch so the walk to the root pane succeeds.
            SwingUtilities.invokeLater {
                val rootPane = SwingUtilities.getRootPane(button) ?: return@invokeLater
                if (makeDefault) {
                    rootPane.defaultButton = button
                } else if (rootPane.defaultButton === button) {
                    rootPane.defaultButton = null
                }
            }
        }

        override fun onDetach() {
            val button = component
            val rootPane = SwingUtilities.getRootPane(button) ?: return
            if (rootPane.defaultButton === button) rootPane.defaultButton = null
        }
    }
}
