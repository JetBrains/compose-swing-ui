package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

/**
 * Folds in the row height a `JTable` leaves to the UI delegate of its look and feel as a modifier
 * element, while the caller declares one and dropped the moment the declaration goes back to `null`.
 *
 * A `JTable` takes an explicit row height as the client's own and stops accepting the one its look and
 * feel installs, which is what makes a folded-in element outrank the look and feel while it stays in the
 * modifier; removing the element restores the height the table carried before it was folded in - the one
 * its look and feel chose - through the same capture-on-attach, restore-on-detach every modifier
 * property follows. Detaching on release, reuse and deactivate as well as on withdrawal is what gives a
 * parked table its look and feel's height back too.
 *
 * One element carries the height for every [Table] overload, since a modifier element's slot is the
 * class of the accessor written here and two accessors would be two slots writing one property.
 */
private val RowHeightProperty =
    ComponentPropertyDescriptor<JTable, Int>(
        name = "rowHeight",
        read = { it.rowHeight },
        write = { table, height -> table.rowHeight = height },
    )

internal fun SwingModifier.tableRowHeight(rowHeight: Int?): SwingModifier =
    if (rowHeight == null) this else property(RowHeightProperty, rowHeight)

private val ShowsRootHandlesProperty =
    ComponentPropertyDescriptor<JTree, Boolean>(
        name = "showsRootHandles",
        read = { it.showsRootHandles },
        write = { tree, value -> tree.showsRootHandles = value },
    )

private val TreeRowHeightProperty =
    ComponentPropertyDescriptor<JTree, Int>(
        name = "rowHeight",
        read = { it.rowHeight },
        write = { tree, value -> tree.rowHeight = value },
    )

/**
 * Folds in the tree properties a `JTree` leaves to the UI delegate of its look and feel - the
 * root-handle choice and the row height - as modifier elements, each while the caller declares one and
 * each dropped the moment its declaration goes back to `null`.
 *
 * A `JTree` takes an explicit root-handle choice or row height as the client's own and stops accepting
 * the one its look and feel installs, which is what makes a folded-in element outrank the look and feel
 * while it stays in the modifier; removing the element restores the value the tree carried before it was
 * folded in, whether its factory or its look and feel set it, through the same capture-on-attach,
 * restore-on-detach every modifier property follows. Detaching on release, reuse and deactivate as well
 * as on withdrawal is what gives a parked tree those values back too.
 *
 * A declared node renderer is folded in through `rendererProperty`, whose own documentation states what
 * a withdrawal gives back.
 */
internal fun SwingModifier.uiOwnedProperties(
    showsRootHandles: Boolean?,
    rowHeight: Int?,
    nodeRenderer: ComposingTreeCellRenderer<*>?,
    rendererProperty: ComponentPropertyDescriptor<JTree, TreeCellRenderer?>,
): SwingModifier {
    var properties = this
    if (showsRootHandles != null) {
        properties = properties.property(ShowsRootHandlesProperty, showsRootHandles)
    }
    if (rowHeight != null) {
        properties = properties.property(TreeRowHeightProperty, rowHeight)
    }
    if (nodeRenderer != null) {
        properties = properties.property(rendererProperty, nodeRenderer)
    }
    return properties
}
