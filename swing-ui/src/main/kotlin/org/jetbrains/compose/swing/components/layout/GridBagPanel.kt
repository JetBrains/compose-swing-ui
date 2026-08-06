@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import org.jetbrains.compose.swing.constants.GridBagAnchor
import org.jetbrains.compose.swing.constants.GridBagFill
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.LocalSwingConstraint
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.Objects
import javax.swing.JPanel

/**
 * A composable wrapper for JPanel with GridBagLayout, exposing each child together with the grid-bag
 * constraints that place it.
 *
 * Declare the cells you need in [block]:
 * ```
 * GridBagPanel {
 *     item(gridx = 0, gridy = 0, anchor = GridBagConstraints.LINE_END) { Label("Name") }
 *     item(gridx = 1, gridy = 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL) { Button("Pick") }
 * }
 * ```
 * An item hosts exactly one child, and the items lay out in declaration order. Constraints are
 * reactive: changing an item's arguments re-places its child, and dropping an item (e.g. behind an
 * `if`) removes it.
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param block declares the items; see [GridBagPanelScope]
 */
@Composable
public fun GridBagPanel(
    modifier: SwingModifier = SwingModifier,
    block: GridBagPanelScope.() -> Unit,
) {
    // Collected fresh on every pass, so an item the caller stops declaring loses its child (see SwingNode).
    val scope = GridBagPanelScopeImpl().apply(block)

    SwingNode(
        factory = { JPanel(GridBagLayout()) },
        update = {
            applyModifier(modifier)
        },
        content = {
            scope.items.forEachIndexed { index, item ->
                // Keyed by position, the identity a container's content gives its children (see SwingNode).
                key(index) {
                    CompositionLocalProvider(LocalSwingConstraint provides item.constraints) {
                        item.content()
                    }
                }
            }
        },
    )
}

/**
 * One declared item: the [constraints] its child is placed under for this composition, plus the child
 * itself.
 */
private class ItemDeclaration(
    val constraints: ItemConstraints,
    val content: @Composable () -> Unit,
)

/**
 * The placement one [GridBagPanelScope.item] declares, comparing equal to the placement an identical
 * declaration produces. `GridBagConstraints` compares by identity, and every pass builds the items
 * afresh, so value equality here is what lets an unchanged declaration reach the layout manager as the
 * placement it already holds.
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

private class GridBagPanelScopeImpl : GridBagPanelScope {
    val items: MutableList<ItemDeclaration> = ArrayList()

    override fun item(
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
        content: @Composable () -> Unit,
    ) {
        val constraints =
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
            }
        items.add(ItemDeclaration(constraints, content))
    }
}
