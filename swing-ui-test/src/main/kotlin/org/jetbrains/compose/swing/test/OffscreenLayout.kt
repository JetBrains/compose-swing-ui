package org.jetbrains.compose.swing.test

import java.awt.Component
import java.awt.Container
import java.awt.Dimension

/**
 * Sizes this component to [size] and lays its whole subtree out synchronously, so every descendant
 * carries real bounds without anything being realized or shown.
 *
 * A menu's items are laid out too, in the popup that holds them - which is not in any container's
 * component array, and is therefore sized here to the size it takes when it is shown.
 *
 * [java.awt.Container.validate] cannot do this off-screen: it short-circuits on a container with no
 * native peer and assigns no child bounds, and the pass a `revalidate()` schedules is left to the
 * repaint manager, which may never run it. Each container is therefore laid out top-down - sized by
 * its parent's layout before it lays out its own children.
 *
 * The harness runs this pass over its own root before every assertion.
 *
 * Must be called on the Event Dispatch Thread.
 *
 * @param size the size this component is laid out at.
 */
internal fun Component.layoutOffscreen(size: Dimension) {
    this.size = size
    layoutSubtree(this)
}

/**
 * Lays [component] out, then each of its children, so a child is sized before it lays out its own.
 *
 * A menu's items are reached the way the tree walk reaches them, through [childComponents]. The popup
 * holding them is not in any container's component array, so no parent's layout ever sizes it: it is
 * given its own preferred size here, which is the size it takes when it is shown.
 */
private fun layoutSubtree(component: Component) {
    if (component !is Container) return
    component.doLayout()
    for (child in component.childComponents()) {
        if (child.parent !== component) child.size = child.preferredSize
        layoutSubtree(child)
    }
}
