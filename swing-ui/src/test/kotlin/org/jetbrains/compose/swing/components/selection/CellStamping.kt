package org.jetbrains.compose.swing.components.selection

import java.awt.Component
import java.awt.Container
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * Renders a cell as the widget owning [this] renderer would when it paints a row: returns the component
 * it would bound and paint. `-1` as [index] is what `JComboBox` passes for its display area.
 *
 * [list] is the list to render against, supplied by a widget rendering its own rows. A renderer reached
 * without one - a combo box's, whose rows belong to a popup list of its own - gets a bare list instead.
 */
internal fun ListCellRenderer<*>.stampCell(
    value: Any?,
    index: Int,
    isSelected: Boolean = false,
    cellHasFocus: Boolean = false,
    list: JList<*> = JList<Any?>(),
): Component {
    // The value stamped below is the widget's own item, which is what a renderer of `in T` is there to
    // render, so widening the receiver drops a bound this call cannot violate.
    @Suppress("UNCHECKED_CAST")
    val renderer = this as ListCellRenderer<Any?>
    return renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
}

/** Renders row [index] of this list through the renderer it carries, as the list does when painting. */
internal fun <T> JList<T>.stampCell(
    index: Int,
    isSelected: Boolean = false,
    cellHasFocus: Boolean = false,
): Component = cellRenderer.stampCell(model.getElementAt(index), index, isSelected, cellHasFocus, list = this)

/** Renders item [index] of this combo box through the renderer it carries, as its popup list does. */
internal fun <T> JComboBox<T>.stampCell(index: Int): Component = renderer.stampCell(getItemAt(index), index)

/** The text of the first [JLabel] anywhere in this component subtree, or `null` if there is none. */
internal fun Component.firstLabelText(): String? = firstLabel()?.text

/** The first [JLabel] anywhere in this component subtree, or `null` if there is none. */
internal fun Component.firstLabel(): JLabel? = when (this) {
    is JLabel -> this
    is Container -> components.firstNotNullOfOrNull { it.firstLabel() }
    else -> null
}
