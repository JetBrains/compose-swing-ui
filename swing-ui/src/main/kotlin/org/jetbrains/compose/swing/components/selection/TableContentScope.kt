package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.LayoutScopeMarker
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.ExistingSwingNode
import javax.swing.JTable
import javax.swing.table.JTableHeader

/**
 * The receiver of a [Table]'s content, through which it configures the parts the table builds itself.
 *
 * A `JTable` builds its header itself; [Header] configures it. An `if`/`else` can select its
 * declaration while keeping the same header:
 *
 * ```
 * Table(rows = people, columns = { column("Name") { it.name } }) {
 *     if (compact) {
 *         Header(modifier = SwingModifier.background(surface), reorderingAllowed = false)
 *     } else {
 *         Header(modifier = SwingModifier.background(surfaceVariant))
 *     }
 * }
 * ```
 *
 * A table holds nothing besides its parts, so a child that declares none of them is refused, naming the
 * table and the calls that would place it. Each part is declared once: two [Header]s are refused too. A
 * part whose declaration goes away gets back the values its declaration replaced.
 *
 * @see javax.swing.JTable
 */
@LayoutScopeMarker
public sealed interface TableContentScope {
    /**
     * Configures the table's own header, the `JTableHeader` that shows the column titles. The header stands
     * where the table puts it: in the column header of the scroll pane whose viewport shows the table.
     *
     * @param modifier the [SwingModifier] applied to the table's `JTableHeader`
     * @param reorderingAllowed whether dragging a column header sideways reorders the columns; `true` - the
     *   default - lets the user reorder them
     * @param resizingAllowed whether dragging the divider between two column headers resizes the columns;
     *   `true` - the default - lets the user resize them
     * @see javax.swing.JTable.getTableHeader
     * @see javax.swing.table.JTableHeader.setReorderingAllowed
     * @see javax.swing.table.JTableHeader.setResizingAllowed
     */
    @Composable
    public fun Header(
        modifier: SwingModifier = SwingModifier,
        reorderingAllowed: Boolean = true,
        resizingAllowed: Boolean = true,
    )
}

/**
 * The parts a [Table] holds as its children, which it declares on its own node so that a child declaring none
 * of them is refused there.
 */
internal val TableRegions: ChildPlacement = ChildPlacement.Slots(HEADER_REGION)

/** The [TableContentScope] one [Table] hands its content. */
internal class TableContentScopeImpl : TableContentScope {
    private val headerDeclaration =
        movableContentOf<SwingModifier> { modifier ->
            ExistingSwingNode(claim = JTable::getTableHeader, modifier = modifier)
        }

    @Composable
    override fun Header(
        modifier: SwingModifier,
        reorderingAllowed: Boolean,
        resizingAllowed: Boolean,
    ) {
        headerDeclaration(
            modifier
                .slot(TableParentProtocol, HEADER_REGION)
                .property(ReorderingAllowedProperty, reorderingAllowed)
                .property(ResizingAllowedProperty, resizingAllowed),
        )
    }
}

private val TableParentProtocol = parentProtocolOf("JTable slot") { it is JTable }

private val ReorderingAllowedProperty =
    ComponentPropertyDescriptor<JTableHeader, Boolean>(
        name = "reorderingAllowed",
        read = { it.reorderingAllowed },
        write = { header, allowed -> header.reorderingAllowed = allowed },
    )

private val ResizingAllowedProperty =
    ComponentPropertyDescriptor<JTableHeader, Boolean>(
        name = "resizingAllowed",
        read = { it.resizingAllowed },
        write = { header, allowed -> header.resizingAllowed = allowed },
    )

/** The table's header, as a caller declares it and as an error about it prints. */
private const val HEADER_REGION: String = "Header()"
