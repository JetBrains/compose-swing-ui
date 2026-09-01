@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.core.onPlaceChanged
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.event.HierarchyEvent
import javax.swing.JButton
import javax.swing.JRootPane
import javax.swing.SwingUtilities

/**
 * Makes this button the default button of the window it is in - the one the look and feel's
 * activation keystroke, Enter in the look and feels the JDK ships, activates wherever the focus sits,
 * as long as the button is an enabled descendant of the root pane at that moment. A component that
 * consumes the activation event itself, a text pane among them, keeps it. The association follows the
 * button when it moves to another window, and is released when [default] is `false` or the modifier
 * leaves. Requires a `JButton` target.
 *
 * @param default whether this button is the window's default button.
 * @return this chain with the default-button association declared on it.
 * @see javax.swing.JRootPane.setDefaultButton
 */
public fun SwingModifier.defaultButton(default: Boolean = true): SwingModifier = this then DefaultButtonElement(default)

private class DefaultButtonElement(
    private val default: Boolean,
) : SwingModifier.NodeElement<JButton, DefaultButtonElement.Node>() {
    override val targetType: Class<JButton> get() = JButton::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.default = default
        node.apply()
    }

    override fun equals(other: Any?): Boolean = other is DefaultButtonElement && default == other.default

    override fun hashCode(): Int = default.hashCode()

    class Node : SwingModifier.Node<JButton>() {
        var default: Boolean = false
        private var placeChanges: DisposableHandle? = null

        /** The root pane this button is currently the default of, to release. */
        private var madeDefaultOn: JRootPane? = null

        override fun onAttach() {
            // The button reaches its root pane only once its ancestors are added, after the update that
            // declared the flag; a parent change anywhere above it reaches the button, so it is what
            // re-resolves the root pane.
            placeChanges = onPlaceChanged(component, HierarchyEvent.PARENT_CHANGED.toLong()) { apply() }
        }

        fun apply() {
            val rootPane = SwingUtilities.getRootPane(component)
            if (!default || rootPane == null) {
                release()
                return
            }
            if (madeDefaultOn !== rootPane) release()
            rootPane.defaultButton = component
            madeDefaultOn = rootPane
        }

        private fun release() {
            val rootPane = madeDefaultOn ?: return
            if (rootPane.defaultButton === component) rootPane.defaultButton = null
            madeDefaultOn = null
        }

        override fun onDetach() {
            placeChanges?.dispose()
            placeChanges = null
            release()
        }
    }
}
