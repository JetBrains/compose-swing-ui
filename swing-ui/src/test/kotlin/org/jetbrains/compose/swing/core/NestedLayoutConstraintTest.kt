package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.GridBagPanel
import org.jetbrains.compose.swing.components.layout.GridPanel
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.hasAnySibling
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.hasText
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import kotlin.test.Test

/**
 * A container [org.jetbrains.compose.swing.SwingNode] placed in a [BorderPanel] region reads that
 * region from `LocalSwingConstraint` for its OWN placement, and resets the local for its content, so
 * each of its children is placed by the inner panel's own layout manager under the constraint that
 * panel provides. Carrying the region down instead is not merely a misplacement: a [GridBagPanel]
 * handed a `"Center"` string rejects the child outright, so composition itself fails.
 *
 * Every assertion here reads the placement the parent's layout manager actually holds.
 */
class NestedLayoutConstraintTest {
    @Test
    fun gridBagPanelInsideBorderPanelCenterPlacesItsChildrenByItsOwnLayout() = runComposeSwingTest {
        setContent {
            BorderPanel {
                center {
                    GridBagPanel {
                        item(gridx = 0, gridy = 0) { Label(text = "one") }
                        item(gridx = 0, gridy = 1) { Label(text = "two") }
                    }
                }
            }
        }

        // The panel itself takes the region; its children take the cells their items declare.
        val panel = onNodeWithText("one").onParent()
        panel.assertLayoutConstraint(BorderLayout.CENTER)
        panel.onChildren().assertCountEquals(2)
        onNodeWithText("one").assertLayoutConstraint(gridBagCell(x = 0, y = 0))
        onNodeWithText("two").assertLayoutConstraint(gridBagCell(x = 0, y = 1))
    }

    @Test
    fun switchingBorderCenterBetweenLeafAndNestedPanelNeverCarriesStaleConstraint() = runComposeSwingTest {
        // The CENTER region toggles between a leaf Label and a nested GridBagPanel. Whether the
        // node is replaced or reused, it must not carry the region into the nested panel's child.
        var nested by mutableStateOf(false)
        setContent {
            BorderPanel {
                center {
                    if (nested) {
                        GridBagPanel {
                            item(gridx = 0, gridy = 0) { Label(text = "nestedChild") }
                        }
                    } else {
                        Label(text = "leaf")
                    }
                }
            }
        }

        val leaf = onNodeWithText("leaf")
        val nestedChild = onNodeWithText("nestedChild")

        leaf.assertLayoutConstraint(BorderLayout.CENTER)
        nestedChild.assertDoesNotExist()

        nested = true
        awaitIdle()
        leaf.assertDoesNotExist()
        assertNestedChildPlacedByItsOwnPanel()

        nested = false
        awaitIdle()
        nestedChild.assertDoesNotExist()
        leaf.assertLayoutConstraint(BorderLayout.CENTER)

        // Forward again, to prove the cycle is stable rather than correct only the first time.
        nested = true
        awaitIdle()
        leaf.assertDoesNotExist()
        assertNestedChildPlacedByItsOwnPanel()
    }

    @Test
    fun gridAndGridBagChildrenInsideBorderRegionsIgnoreTheRegionConstraint() = runComposeSwingTest {
        setContent {
            BorderPanel {
                north {
                    GridPanel(rows = 1, cols = 2) {
                        Label(text = "g1")
                        Label(text = "g2")
                    }
                }
                center {
                    GridBagPanel {
                        item(gridx = 0, gridy = 0) { Label(text = "b1") }
                        item(gridx = 0, gridy = 1) { Label(text = "b2") }
                    }
                }
            }
        }

        // A GridLayout keeps no per-child constraint, so what the region must not reach is
        // simply both labels being the grid panel's own children.
        val gridPanel = onNodeWithText("g1").onParent()
        gridPanel.assertLayoutConstraint(BorderLayout.NORTH)
        gridPanel.onChildren().assertCountEquals(2)
        onNodeWithText("g1").assert(hasAnySibling(hasText("g2")))

        val gridBagPanel = onNodeWithText("b1").onParent()
        gridBagPanel.assertLayoutConstraint(BorderLayout.CENTER)
        gridBagPanel.onChildren().assertCountEquals(2)
        onNodeWithText("b1").assertLayoutConstraint(gridBagCell(x = 0, y = 0))
        onNodeWithText("b2").assertLayoutConstraint(gridBagCell(x = 0, y = 1))
    }

    /** Asserts the nested panel holds the region, and its child holds the cell its item declares. */
    private fun ComposeSwingTest.assertNestedChildPlacedByItsOwnPanel() {
        onNodeWithText("nestedChild").apply {
            onParent().assertLayoutConstraint(BorderLayout.CENTER)
            assertLayoutConstraint(gridBagCell(x = 0, y = 0))
        }
    }

    /** The placement a `GridBagPanel` item declaring only cell ([x], [y]) puts its child under. */
    private fun gridBagCell(
        x: Int,
        y: Int,
    ): GridBagConstraints = GridBagConstraints().apply {
        gridx = x
        gridy = y
    }
}
