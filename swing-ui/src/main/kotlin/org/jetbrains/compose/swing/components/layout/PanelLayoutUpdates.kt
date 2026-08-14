package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.node.SwingNodeUpdater
import java.awt.LayoutManager
import javax.swing.JPanel

/**
 * Applies [value] to the panel's layout manager whenever it changes, then revalidates the panel so the
 * new geometry is laid out.
 *
 * [block] receives the manager as `this` and the new [value] as its argument. [L] is the manager type
 * the panel was built with, and the cast to it is checked. The manager is edited in place: it holds the
 * record of where each child was added, which replacing it would discard.
 *
 * The first composition is skipped, because the panel's factory already built the manager from the same
 * values.
 */
internal inline fun <reified L : LayoutManager, V> SwingNodeUpdater<JPanel>.updateLayout(
    value: V,
    crossinline block: L.(V) -> Unit,
): Unit =
    update(value) {
        (layout as L).block(it)
        revalidate()
    }
