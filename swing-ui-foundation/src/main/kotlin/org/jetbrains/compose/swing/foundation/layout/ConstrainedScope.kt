package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.layout.LayoutScopeMarker
import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * The scope of a container whose child stands between the constraints that container offers it and
 * its own measurement: a fill, a padding, an offset, an aspect ratio or a default minimum size each
 * narrows what reaches the child, states what the child plus its own space occupies, and places the child
 * inside that.
 *
 * [Row], [Column] and [Box] offer this to their content, so a child of any of them declares these alongside
 * what that container's own scope offers, and a custom [Layout] offers this alone.
 *
 * [layout] is a member of this scope, and every modifier built on it, such as [fillMaxWidth], [padding] or
 * [aspectRatio], takes this scope as its context, so that one declared on a child of a standard Swing layout
 * manager, which honors none of them, fails at compile time rather than at runtime. A modifier of your own
 * declares the same context and builds on [layout] or on the modifiers built on it:
 *
 * ```
 * context(scope: ConstrainedScope)
 * fun SwingModifier.halfWidth(): SwingModifier =
 *     layout { measurable, constraints ->
 *         val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth / 2 else constraints.maxWidth
 *         val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = maxWidth))
 *         layout(placeable.width, placeable.height) { placeable.place(0, 0) }
 *     }
 * ```
 */
@LayoutScopeMarker
public interface ConstrainedScope {
    /**
     * Declares [element] on the child: the [LayoutModifierNode] it creates measures the child between the
     * constraints this scope's container offers and the child's own measurement, and places it in the extent it
     * reports.
     *
     * Layout modifiers nest in declaration order: the first one declared is outermost and measures the next one.
     *
     * @param element the measurement and placement the child goes through.
     * @return this modifier with [element] declared on it.
     */
    public fun SwingModifier.layout(element: LayoutModifierNodeElement<*>): SwingModifier = this then element
}
