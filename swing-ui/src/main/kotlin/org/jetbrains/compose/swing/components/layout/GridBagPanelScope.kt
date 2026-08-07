package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.constants.GridBagAnchor
import org.jetbrains.compose.swing.constants.GridBagFill
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import java.awt.GridBagConstraints
import java.awt.Insets
import java.util.Objects

/**
 * The receiver of a [GridBagPanel]'s content, through which a child declares the cell it occupies.
 *
 * Children are written plainly; what a child declares here rides along on its `modifier`:
 *
 * ```
 * GridBagPanel {
 *     Label(text = "Name", modifier = SwingModifier.item(gridx = 0, gridy = 0))
 *     Button(text = "Pick", modifier = SwingModifier.item(gridx = 1, gridy = 0, weightx = 1.0))
 * }
 * ```
 *
 * @see java.awt.GridBagLayout
 */
public sealed interface GridBagPanelScope {
    /**
     * Places the child in the cell these constraints describe. The parameters carry
     * `GridBagConstraints`' own field names and defaults, so a grid-bag layout written against Swing
     * transfers field for field.
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
     * @see java.awt.GridBagConstraints
     */
    @Suppress("LongParameterList")
    // One parameter per GridBagConstraints field, under the field's own name and at the field's own
    // default, so a declaration names only the constraints it sets and a grid-bag layout written against
    // Swing carries over unchanged.
    public fun SwingModifier.item(
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
    ): SwingModifier
}

/** The [GridBagPanelScope] every [GridBagPanel] hands its content. It holds nothing of its own. */
internal object GridBagPanelScopeImpl : GridBagPanelScope {
    override fun SwingModifier.item(
        gridx: Int,
        gridy: Int,
        gridwidth: Int,
        gridheight: Int,
        weightx: Double,
        weighty: Double,
        @GridBagAnchor anchor: Int,
        @GridBagFill fill: Int,
        insets: Insets,
        ipadx: Int,
        ipady: Int,
    ): SwingModifier =
        layoutConstraint(
            ItemConstraints().apply {
                this.gridx = gridx
                this.gridy = gridy
                this.gridwidth = gridwidth
                this.gridheight = gridheight
                this.weightx = weightx
                this.weighty = weighty
                this.anchor = anchor
                this.fill = fill
                this.insets = insets
                this.ipadx = ipadx
                this.ipady = ipady
            },
        )
}

/**
 * The placement one [GridBagPanelScope.item] declares, comparing equal to the placement an identical
 * declaration produces. `GridBagConstraints` compares by identity, so value equality here is what lets a
 * chain rebuilt from the same arguments reach the node as the placement it already holds.
 *
 * Passing it wherever a `GridBagConstraints` is expected is safe: `GridBagLayout` stores a deep copy of
 * what it is handed.
 */
private class ItemConstraints : GridBagConstraints() {
    override fun equals(other: Any?): Boolean =
        other is ItemConstraints && sameCell(other) && sameSpace(other) && samePadding(other)

    override fun hashCode(): Int =
        Objects.hash(gridx, gridy, gridwidth, gridheight, weightx, weighty, anchor, fill, insets, ipadx, ipady)

    private fun sameCell(other: GridBagConstraints): Boolean =
        gridx == other.gridx && gridy == other.gridy && gridwidth == other.gridwidth &&
            gridheight == other.gridheight

    private fun sameSpace(other: GridBagConstraints): Boolean =
        weightx == other.weightx && weighty == other.weighty && anchor == other.anchor &&
            fill == other.fill

    private fun samePadding(other: GridBagConstraints): Boolean =
        insets == other.insets && ipadx == other.ipadx && ipady == other.ipady
}

// GridBagConstraints' own default external padding, shared by every item that leaves `insets` unset.
// Sharing one instance is safe because GridBagLayout stores a deep copy of the constraints it is
// handed - insets included - so no item can reach, let alone mutate, the instance another item used.
private val DefaultInsets: Insets = Insets(0, 0, 0, 0)
