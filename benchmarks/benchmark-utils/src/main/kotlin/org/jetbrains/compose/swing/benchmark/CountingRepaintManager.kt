// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import java.awt.Component
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.RepaintManager
import javax.swing.SwingUtilities

/**
 * Records the revalidate and repaint requests that Swing makes during a toggle.
 *
 * Swing coalesces repaints, so the harness needs each request and the region it covers before coalescing hides them.
 * Only requests for components under [host] count toward the dirty area, because a detached or hidden component never
 * paints into the host. Swing accepts repaint requests from any thread, so the recorded regions are guarded.
 *
 * @property host the component whose area the dirty regions are measured against.
 * @param metrics returns the metrics that receive the requests, or null when no toggle is being measured.
 */
internal class CountingRepaintManager(
    val host: JComponent,
    private val metrics: () -> ToggleMetrics?,
) : RepaintManager() {
    private val dirty = mutableMapOf<JComponent, Rectangle>()

    override fun addInvalidComponent(component: JComponent) {
        metrics()?.add("revalidateRequests")
        super.addInvalidComponent(component)
    }

    override fun addDirtyRegion(
        component: JComponent,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        metrics()?.add("repaintRequests")
        if (width > 0 && height > 0) {
            val rectangle = Rectangle(x, y, width, height)
            synchronized(dirty) { dirty.merge(component, rectangle) { previous, next -> previous.union(next) } }
        }
        super.addDirtyRegion(component, x, y, width, height)
    }

    fun clear() {
        synchronized(dirty) { dirty.clear() }
    }

    fun dirtyRectangle(): Rectangle? {
        val snapshot = synchronized(dirty) { dirty.entries.map { it.key to it.value } }
        return snapshot
            .filter { (component, _) ->
                component.isShowing && (component === host || SwingUtilities.isDescendingFrom(component, host))
            }.mapNotNull { (component, rectangle) ->
                var visible = rectangle.intersection(Rectangle(0, 0, component.width, component.height))
                var current: Component = component
                while (current !== host && !visible.isEmpty) {
                    val parent = current.parent ?: return@mapNotNull null
                    visible =
                        SwingUtilities
                            .convertRectangle(current, visible, parent)
                            .intersection(Rectangle(0, 0, parent.width, parent.height))
                    current = parent
                }
                visible.takeUnless { it.isEmpty }
            }.reduceOrNull { result, rectangle -> result.union(rectangle) }
    }

    /** Counts the visible components whose clipped bounds meet [rectangle], the region the harness repaints. */
    fun componentsInDirtyRegion(rectangle: Rectangle): Long =
        componentTree(host)
            .count { component ->
                if (!component.isShowing) return@count false
                var visible =
                    SwingUtilities.convertRectangle(
                        component,
                        Rectangle(0, 0, component.width, component.height),
                        host,
                    )
                var ancestor = component.parent
                while (ancestor != null && !visible.isEmpty) {
                    val bounds =
                        SwingUtilities.convertRectangle(
                            ancestor,
                            Rectangle(0, 0, ancestor.width, ancestor.height),
                            host,
                        )
                    visible = visible.intersection(bounds)
                    ancestor = ancestor.parent
                }
                visible.intersects(rectangle)
            }.toLong()
}
