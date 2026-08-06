@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ToolTipManager

/**
 * Sets `toolTipText` - the tooltip the component shows wherever the pointer rests on it; `null` clears
 * it. Requires a `JComponent` target.
 *
 * A component has one tooltip, so the last `toolTip` in a chain owns it, whichever of the two forms
 * declared it. For a tooltip that belongs to a place within the component - the cell, the node or the
 * shape under the pointer - declare the per-location form instead.
 *
 * @see javax.swing.JComponent.setToolTipText
 */
public fun SwingModifier.toolTip(text: String?): SwingModifier = this then ToolTipElement(text = text, textAt = null)

/**
 * Sets the tooltip per pointer location: [text] is asked for the tooltip belonging to the place the
 * pointer is over, and answers `null` where the component has none there. Requires a `JComponent`
 * target.
 *
 * [text] is called with the [MouseEvent] the toolkit is delivering, so the point it reads is in the
 * component's own coordinates - what a table cell, a tree node or a region of a canvas is looked up
 * by. It is asked as the pointer travels the component, ahead of the tooltip for that place being
 * shown, and its answer is the component's tooltip text from then on.
 *
 * The component is kept under `ToolTipManager` for as long as this declaration is in the chain, so a
 * component whose tooltip is declared this way alone shows one all the same.
 *
 * @see javax.swing.JComponent.setToolTipText
 */
public fun SwingModifier.toolTip(text: (event: MouseEvent) -> String?): SwingModifier =
    this then ToolTipElement(text = null, textAt = text)

/**
 * Backs both tooltip forms with one slot: a component has one tooltip, so the last declaration in the
 * chain owns it. Exactly one of [text] and [textAt] is set - the constant written straight onto the
 * component, or the lambda asked for the tooltip under the pointer, which the node reads from its
 * field so that a fresh lambda each recomposition is fine.
 */
private class ToolTipElement(
    private val text: String?,
    private val textAt: ((event: MouseEvent) -> String?)?,
) : SwingModifier.Element<JComponent, ToolTipElement.Node> {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.apply(text, textAt)
    }

    /**
     * The node backing [ToolTipElement]: it writes a constant tooltip onto the component, and for a
     * per-location declaration follows the pointer, publishing the tooltip of the place under it.
     */
    class Node : SwingModifier.Node<JComponent>() {
        private var textAt: ((event: MouseEvent) -> String?)? = null
        private var original: String? = null
        private var following = false

        // Publishes the tooltip of the place under the pointer as the component's tooltip text, the one
        // property ToolTipManager reads. It is installed once and never re-added, so every registration
        // the manager makes lands behind it and the manager reads a text already resolved for the event
        // it is handling.
        private val pointer =
            object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent): Unit = publish(e)

                override fun mouseMoved(e: MouseEvent): Unit = publish(e)

                private fun publish(e: MouseEvent) {
                    val text = textAt?.invoke(e)
                    // A place carrying the tooltip already on the component leaves it alone: the pointer
                    // travels a place far more often than it leaves it.
                    if (text == component.toolTipText) return
                    component.toolTipText = text
                    // Clearing the text takes the component back out of ToolTipManager, so the
                    // registration is renewed: the component stays under the manager for the next place
                    // that does carry a tooltip.
                    if (text == null) ToolTipManager.sharedInstance().registerComponent(component)
                }
            }

        override fun onAttach() {
            original = component.toolTipText
        }

        /** Applies the declared form, constant or per location. Called from the element's `update`. */
        fun apply(
            text: String?,
            textAt: ((event: MouseEvent) -> String?)?,
        ) {
            this.textAt = textAt
            if (textAt == null) {
                stopFollowing()
                component.toolTipText = text
            } else if (!following) {
                // Arriving over a tooltip this declaration did not write - a constant one it takes over
                // from, or one the component came with. The tooltip of the place under the pointer is
                // published as soon as the pointer reports where that is.
                component.toolTipText = null
                follow()
            }
        }

        override fun onDetach() {
            stopFollowing()
            // Putting the captured text back puts the component's registration with ToolTipManager back
            // as well: the manager follows the text property, and a component with no tooltip text is
            // not one it knows about.
            component.toolTipText = original
        }

        private fun follow() {
            following = true
            component.addMouseListener(pointer)
            component.addMouseMotionListener(pointer)
            // A tooltip that exists only per location leaves the text property null until the pointer
            // arrives, and a component with no tooltip text is not one ToolTipManager knows about; this
            // is what puts it under the manager. Registering also moves the manager's own listeners
            // behind the one installed above.
            ToolTipManager.sharedInstance().registerComponent(component)
        }

        private fun stopFollowing() {
            if (!following) return
            following = false
            component.removeMouseListener(pointer)
            component.removeMouseMotionListener(pointer)
        }
    }
}
