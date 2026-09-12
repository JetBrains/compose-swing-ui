package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.node.SwingNodeUpdater
import java.awt.LayoutManager
import javax.swing.JPanel

/**
 * Writes [value] to the panel's layout manager on the pass that builds the panel and on every pass that
 * changes it, then revalidates the panel so the new geometry is laid out.
 *
 * [block] receives the manager as `this` and the new [value] as its argument. [L] is the manager type
 * the panel was built with, and the cast to it is checked. The manager is edited in place: it holds
 * the record of where each child was added, which replacing it would discard.
 */
internal inline fun <reified L : LayoutManager, V> SwingNodeUpdater<out JPanel>.setOnLayout(
    value: V,
    crossinline block: L.(V) -> Unit,
): Unit =
    set(value) {
        (layout as L).block(it)
        revalidate()
    }

/**
 * Writes [value] to the panel's layout manager as [setOnLayout] does, but skips the pass that builds the
 * panel. Use it when the factory already built the manager from the same value.
 */
internal inline fun <reified L : LayoutManager, V> SwingNodeUpdater<out JPanel>.updateLayout(
    value: V,
    crossinline block: L.(V) -> Unit,
): Unit =
    update(value) {
        (layout as L).block(it)
        revalidate()
    }
