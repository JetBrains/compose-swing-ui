package org.jetbrains.compose.swing.foundation.layout

import java.awt.Component
import java.awt.Container

/**
 * What [alignment] reads for the first child of [target] that is visible, or [Component.CENTER_ALIGNMENT]
 * where the container has no visible child to ask.
 *
 * A container built from this package reports its content's alignment rather than a fixed value of its own,
 * so the layout above it places it where it would have placed that content directly. A parent that lines
 * its children up on a shared alignment - `javax.swing.BoxLayout` - reserves room on both sides of that
 * line for every sibling, so a container answering a constant would sit off the line its content belongs
 * on and squeeze whichever siblings can stretch.
 *
 * A hidden child is passed over because it takes no space and is placed nowhere; letting it decide the
 * alignment would move the container for content that is not on screen.
 */
internal inline fun firstVisibleChildAlignment(
    target: Container,
    alignment: (Component) -> Float,
): Float {
    for (index in 0 until target.componentCount) {
        val child = target.getComponent(index)
        if (child.isVisible) return alignment(child)
    }
    return Component.CENTER_ALIGNMENT
}
