@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.annotations.Nls
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
 * @param text the tooltip for the whole component; `ToolTipManager` decides when it appears and how long
 *   it stays.
 * @return this chain with the tooltip declared on it.
 * @see javax.swing.JComponent.setToolTipText
 */
public fun SwingModifier.toolTip(text: @Nls String?): SwingModifier =
    this then ToolTipElement(text = text, textAt = null)

/**
 * Sets the tooltip per pointer location: [text] is asked for the tooltip belonging to the place the
 * pointer is over, and answers `null` where the component has none there. Requires a `JComponent`
 * target.
 *
 * [text] is called with the [MouseEvent] the toolkit delivers, as the pointer travels the component and
 * ahead of the tooltip for that place being shown; its answer becomes the component's tooltip text from
 * then on. It runs on every pointer move, not only when a tooltip is about to appear, so an expensive
 * hit test behind it is paid continuously.
 *
 * The component is kept under `ToolTipManager` for as long as this declaration is in the chain, so a
 * component whose tooltip is declared this way alone shows one all the same.
 *
 * @param text answers the tooltip of the place the event points at; it runs on the event dispatch thread,
 *   so it has to answer without blocking.
 * @return this chain with the per-location tooltip declared on it.
 * @see javax.swing.JComponent.setToolTipText
 */
public fun SwingModifier.toolTip(text: (event: MouseEvent) -> @Nls String?): SwingModifier =
    this then ToolTipElement(text = null, textAt = text)

/**
 * Backs both tooltip forms with one slot, so the last declaration in the chain owns it (see [toolTip]).
 * Exactly one of [text] and [textAt] is set - the constant written straight onto the component, or the
 * lambda asked for the tooltip under the pointer, read from the node's field so a fresh lambda each
 * recomposition is fine.
 *
 * Two elements are equal when they declare the same [text] and hold the *same* [textAt] - identity,
 * because a lambda is what it captures, and a fresh one each recomposition may answer differently.
 */
private class ToolTipElement(
    private val text: @Nls String?,
    private val textAt: ((event: MouseEvent) -> @Nls String?)?,
) : SwingModifier.NodeElement<JComponent, ToolTipElement.Node>() {
    override val name: String get() = "toolTip"

    override val declaredValues: Map<String, Any?> get() = mapOf("text" to text)
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.apply(text, textAt)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ToolTipElement) return false
        if (textAt !== other.textAt) return false
        return text == other.text
    }

    override fun hashCode(): Int = 31 * (text?.hashCode() ?: 0) + System.identityHashCode(textAt)

    /**
     * The node backing [ToolTipElement]: it writes a constant tooltip onto the component, and for a
     * per-location declaration follows the pointer, publishing the tooltip of the place under it.
     */
    class Node : SwingModifier.Node<JComponent>() {
        private var textAt: ((event: MouseEvent) -> @Nls String?)? = null
        private var original: @Nls String? = null
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

        /** Applies the declared form, constant or per location. */
        fun apply(
            text: @Nls String?,
            textAt: ((event: MouseEvent) -> @Nls String?)?,
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
            // is what puts it under the manager.
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
