@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.util.Key
import org.jetbrains.compose.swing.util.get
import org.jetbrains.compose.swing.util.set
import javax.swing.JComponent

/**
 * Assigns this component a position in its container's keyboard focus-traversal order. Lower indices are reached
 * first when tabbing forward. Effective only inside a container that installs the index-order policy via
 * `orderedFocusTraversal`; components without an index follow the indexed ones in their on-screen order.
 *
 * @param index the traversal position.
 */
public fun SwingComponentStyleScope.focusTraversalIndex(index: Int): Unit = FocusTraversalIndexProperty.provide(index)

/**
 * The `JComponent` client-property key under which [focusTraversalIndex] stores a component's traversal position.
 * Read by the `orderedFocusTraversal` policy to order children.
 */
internal val FOCUS_TRAVERSAL_INDEX_KEY: Key<Int> = Key("org.jetbrains.compose.swing.focusTraversalIndex")

internal val FocusTraversalIndexDescriptor =
    ComponentPropertyDescriptor<JComponent, Int?>(
        name = "focusTraversalIndex",
        read = { it[FOCUS_TRAVERSAL_INDEX_KEY] },
        write = { component, value -> component[FOCUS_TRAVERSAL_INDEX_KEY] = value },
    )

private val FocusTraversalIndexProperty = stylePropertyOf(FocusTraversalIndexDescriptor)
