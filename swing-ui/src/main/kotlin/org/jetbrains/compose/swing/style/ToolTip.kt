@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.PropertyCaptures
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ToolTipManager

/**
 * Sets the tooltip for the whole component.
 * `null` clears it.
 *
 * The last `toolTip` declaration wins across both overloads.
 * Use the per-location overload for a tooltip that depends on the pointer's location.
 * `ToolTipManager` controls when the tooltip appears and how long it stays.
 *
 * @param text the tooltip for the whole component.
 * @see javax.swing.JComponent.setToolTipText
 */
public fun SwingComponentStyleScope.toolTip(text: @Nls String?): Unit =
    ToolTipProperty.provide(ToolTipElement(text = text, textAt = null))

/**
 * Sets the tooltip for the pointer's location.
 * [text] returns `null` where no tooltip is available.
 *
 * [text] receives the pointer's [MouseEvent] before the tooltip appears, and its answer becomes the
 * component's tooltip text, so keep it free of side effects.
 * It runs on every pointer move, so keep it inexpensive.
 * These tooltips work even when the component has no plain tooltip text.
 *
 * @param text returns the tooltip at the event's location.
 *   It runs on the event dispatch thread and must not block.
 * @see javax.swing.JComponent.setToolTipText
 */
public fun SwingComponentStyleScope.toolTip(text: (event: MouseEvent) -> @Nls String?): Unit =
    ToolTipProperty.provide(ToolTipElement(text = null, textAt = text))

internal val ToolTipProperty = stylePropertyOf { element: ToolTipElement -> element }

/**
 * Backs both tooltip forms with one slot, so the last declaration owns it (see [toolTip]).
 * Exactly one of [text] and [textAt] is set - the constant written straight onto the component, or the
 * lambda asked for the tooltip under the pointer, read from the node's field so a fresh lambda each
 * recomposition is fine.
 *
 * Two elements are equal when they declare the same [text] and hold the *same* [textAt] - identity,
 * because a lambda is what it captures, and a fresh one each recomposition may answer differently.
 */
internal class ToolTipElement(
    private val text: @Nls String?,
    private val textAt: ((event: MouseEvent) -> @Nls String?)?,
) : SwingModifier.NodeElement<JComponent, ToolTipElement.Node>() {
    override val name: String get() = "toolTip"

    override val heldProperties: Set<String> get() = setOf("toolTipText", "accessibleDescription")

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
    class Node : SwingModifier.ComponentNode<JComponent>() {
        private var textAt: ((event: MouseEvent) -> @Nls String?)? = null
        private var captured: PropertyCaptures.Capture<*, *>? = null
        private var following = false
        private var pointerText: @Nls String? = null

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
                    pointerText = text
                    // A place carrying the tooltip already on the component leaves it alone: the pointer
                    // travels a place far more often than it leaves it.
                    if (text == component.toolTipText) return
                    holder?.modifierState?.refreshLocalConsumer(this@Node)
                }
            }

        override fun onAttach() {
            captured =
                checkNotNull(holder?.modifierState).captures().capture(
                    "toolTip",
                    component,
                    { it.toolTipText },
                    { component, value -> component.toolTipText = value },
                )
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
            } else {
                component.toolTipText = pointerText
                ToolTipManager.sharedInstance().registerComponent(component)
            }
        }

        override fun onDetach() {
            stopFollowing()
            captured?.release()
            captured = null
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
            pointerText = null
            component.removeMouseListener(pointer)
            component.removeMouseMotionListener(pointer)
        }
    }
}
