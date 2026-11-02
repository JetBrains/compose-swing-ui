package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import javax.swing.JTable

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
