package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.constants.GridBagAnchor
import org.jetbrains.compose.swing.constants.GridBagFill
import java.awt.GridBagConstraints
import java.awt.Insets

/**
 * Declarative items of a [GridBagPanel]. Each [item] call appends one child, in call order, with the
 * [GridBagConstraints] that place it.
 *
 * An item hosts exactly one child; dropping an item (e.g. behind an `if`) removes its child. The
 * parameters carry `GridBagConstraints`' own field names and defaults, so a grid-bag layout written
 * against Swing transfers field for field.
 *
 * @see java.awt.GridBagLayout
 */
public sealed interface GridBagPanelScope {
    /**
     * Declares one item and the constraints its child is laid out under.
     *
     * [content] must emit exactly one component - the one the constraints apply to.
     *
     * @param gridx the cell holding the leading edge of the child's display area, the first cell in a
     *   row being `0`; `GridBagConstraints.RELATIVE` places the child immediately after the previously
     *   declared one
     * @param gridy the cell at the top of the child's display area, the topmost cell being `0`;
     *   `GridBagConstraints.RELATIVE` places the child just below the previously declared one
     * @param gridwidth the number of cells the display area spans in its row;
     *   `GridBagConstraints.REMAINDER` spans to the last cell in the row, `GridBagConstraints.RELATIVE`
     *   to the next to last
     * @param gridheight the number of cells the display area spans in its column;
     *   `GridBagConstraints.REMAINDER` spans to the last cell in the column,
     *   `GridBagConstraints.RELATIVE` to the next to last
     * @param weightx the share of extra horizontal space this child's column claims; a column of
     *   weight `0.0` receives none
     * @param weighty the share of extra vertical space this child's row claims; a row of weight `0.0`
     *   receives none
     * @param anchor where the child sits within its display area when the area is larger
     * @param fill whether and along which axes the child is resized to fill its display area
     * @param insets the external padding, the minimum space between the child and the edges of its
     *   display area
     * @param ipadx the internal horizontal padding: the child is at least its minimum width plus this
     *   many pixels wide
     * @param ipady the internal vertical padding: the child is at least its minimum height plus this
     *   many pixels tall
     * @param content the single composable placed in the cell
     * @see java.awt.GridBagConstraints
     */
    @Suppress("LongParameterList")
    // One parameter per GridBagConstraints field, under the field's own name and at the field's own
    // default, so a declaration names only the constraints it sets and a grid-bag layout written against
    // Swing carries over unchanged.
    public fun item(
        gridx: Int = GridBagConstraints.RELATIVE,
        gridy: Int = GridBagConstraints.RELATIVE,
        gridwidth: Int = 1,
        gridheight: Int = 1,
        weightx: Double = 0.0,
        weighty: Double = 0.0,
        @GridBagAnchor anchor: Int = GridBagConstraints.CENTER,
        @GridBagFill fill: Int = GridBagConstraints.NONE,
        insets: Insets = DefaultInsets,
        ipadx: Int = 0,
        ipady: Int = 0,
        content: @Composable () -> Unit,
    )
}

// GridBagConstraints' own default external padding, shared by every item that leaves `insets` unset.
// Sharing one instance is safe because GridBagLayout stores a deep copy of the constraints it is
// handed - insets included - so no item can reach, let alone mutate, the instance another item used.
private val DefaultInsets: Insets = Insets(0, 0, 0, 0)
