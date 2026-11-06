@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor.Companion.accessor
import org.jetbrains.compose.swing.modifier.PropertyCaptures
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.stylePropertyOf
import java.awt.Component
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * Renders the rows of the ListBox through [renderer].
 *
 * Takes a renderer written against the Swing interface, or one `rememberListItemRenderer` built from a
 * composable cell body. As in Swing, the renderer is handed whatever the list's own model holds. The
 * ListBox's own `itemContent` wins over a renderer a nested style sets, and the ListBox's own style wins
 * over `itemContent`.
 *
 * A style that stops setting it puts back the renderer the list carried before.
 *
 * @param renderer the renderer the list renders its rows through.
 * @see javax.swing.JList.setCellRenderer
 */
public fun ListBoxStyleScope.listItemRenderer(renderer: ListCellRenderer<*>): Unit =
    ListItemRendererProperty.provide(renderer.erased())

/**
 * Renders the items of the ComboBox, in its popup and its display area, through [renderer].
 *
 * Takes a renderer written against the Swing interface, or one `rememberListItemRenderer` built from a
 * composable cell body. As in Swing, the renderer is handed whatever the combo box's own model holds.
 * The ComboBox's own `itemContent` wins over a renderer a nested style sets, and the ComboBox's own style
 * wins over `itemContent`.
 *
 * A style that stops setting it puts back the renderer the combo box carried before.
 *
 * @param renderer the renderer the combo box renders its items through.
 * @see javax.swing.JComboBox.setRenderer
 */
public fun ComboBoxStyleScope.listItemRenderer(renderer: ListCellRenderer<*>): Unit =
    ListItemRendererProperty.provide(renderer.erased())

/**
 * The renderer a `JList` renders its rows through and the one a `JComboBox` renders its items through:
 * one property, reached through the accessor of whichever widget carries it.
 */
private val ListCellRendererDescriptor =
    ComponentPropertyDescriptor(
        "listItemRenderer",
        accessor<JList<Any?>, ListCellRenderer<in Any?>?>(
            read = { it.cellRenderer },
            write = { list, renderer -> list.cellRenderer = renderer },
        ),
        accessor<JComboBox<Any?>, ListCellRenderer<in Any?>?>(
            read = { it.renderer },
            write = { combo, renderer -> combo.renderer = renderer },
        ),
    )

internal val ListItemRendererProperty =
    stylePropertyOf { renderer: ListCellRenderer<in Any?> -> ListItemRendererElement(renderer) }

internal class ListItemRendererElement(
    private val renderer: ListCellRenderer<in Any?>,
) : SwingModifier.NodeElement<Component, ListItemRendererElement.Node>() {
    override val targetType: Class<Component> get() = Component::class.java

    override val name: String get() = ListCellRendererDescriptor.name

    override val key: Any get() = name

    override val declaredValues: Map<String, Any?> get() = mapOf(name to renderer)

    override fun create(): Node = Node()

    override fun update(node: Node): Unit = node.apply(renderer)

    override fun equals(other: Any?): Boolean = other is ListItemRendererElement && renderer === other.renderer

    override fun hashCode(): Int = System.identityHashCode(renderer)

    class Node : SwingModifier.ComponentNode<Component>() {
        private var captured: PropertyCaptures.Capture<*, *>? = null

        override fun onAttach() {
            val state = checkNotNull(holder?.modifierState)
            captured =
                state.captures().capture(
                    ListCellRendererDescriptor.name,
                    component,
                    ListCellRendererDescriptor.read,
                    ListCellRendererDescriptor.write,
                )
        }

        fun apply(renderer: ListCellRenderer<in Any?>) {
            ListCellRendererDescriptor.write(component, renderer)
        }

        override fun onDetach() {
            captured?.release()
            captured = null
        }
    }
}

/** This renderer over any item: the widget hands it whatever its own model holds. */
internal fun ListCellRenderer<*>.erased(): ListCellRenderer<in Any?> {
    // JList.setCellRenderer takes a ListCellRenderer<? super E>, and the descriptor names one component
    // type for every widget it serves.
    @Suppress("UNCHECKED_CAST")
    return this as ListCellRenderer<in Any?>
}
