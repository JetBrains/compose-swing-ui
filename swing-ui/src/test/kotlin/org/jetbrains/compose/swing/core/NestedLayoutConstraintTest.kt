package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.GridBagPanel
import org.jetbrains.compose.swing.components.layout.GridPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
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
 * A container placed in a [BorderPanel] region takes that region for itself alone: a placement reaches
 * only the node whose own chain declares it, so each child of the inner panel is placed by that panel's
 * layout manager under the constraint that child declares.
 *
 * The stakes are more than a misplacement: a [GridBagPanel] handed a `"Center"` string rejects the
 * child outright, so a region reaching down into a nested panel would fail the composition itself.
 */
class NestedLayoutConstraintTest {
    @Test
    fun gridBagPanelInsideBorderPanelCenterPlacesItsChildrenByItsOwnLayout() = runComposeSwingTest {
        setContent {
            BorderPanel {
                GridBagPanel(modifier = SwingModifier.center()) {
                    Label(text = "one", modifier = SwingModifier.item(gridx = 0, gridy = 0))
                    Label(text = "two", modifier = SwingModifier.item(gridx = 0, gridy = 1))
                }
            }
        }

        // The panel itself takes the region; its children take the cells they declare.
        val panel = onNodeWithText("one").onParent()
        panel.assertLayoutConstraint(BorderLayout.CENTER)
        panel.onChildren().assertCountEquals(2)
        onNodeWithText("one").assertLayoutConstraint(gridBagCell(x = 0, y = 0))
        onNodeWithText("two").assertLayoutConstraint(gridBagCell(x = 0, y = 1))
    }

    @Test
    fun switchingTheCenterChildBetweenALeafAndANestedPanelPlacesWhicheverOneStands() = runComposeSwingTest {
        // The CENTER region toggles between a leaf Label and a nested GridBagPanel.
        var nested by mutableStateOf(false)
        setContent {
            BorderPanel {
                if (nested) {
                    GridBagPanel(modifier = SwingModifier.center()) {
                        Label(text = "nestedChild", modifier = SwingModifier.item(gridx = 0, gridy = 0))
                    }
                } else {
                    Label(text = "leaf", modifier = SwingModifier.center())
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
    fun panelsInBorderRegionsPlaceTheirOwnChildrenByTheirOwnLayout() = runComposeSwingTest {
        setContent {
            BorderPanel {
                GridPanel(rows = 1, cols = 2, modifier = SwingModifier.north()) {
                    Label(text = "g1")
                    Label(text = "g2")
                }
                GridBagPanel(modifier = SwingModifier.center()) {
                    Label(text = "b1", modifier = SwingModifier.item(gridx = 0, gridy = 0))
                    Label(text = "b2", modifier = SwingModifier.item(gridx = 0, gridy = 1))
                }
            }
        }

        // A GridLayout keeps no per-child constraint, so what it shows is simply both labels being
        // the grid panel's own children.
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

    private fun ComposeSwingTest.assertNestedChildPlacedByItsOwnPanel() {
        onNodeWithText("nestedChild").apply {
            onParent().assertLayoutConstraint(BorderLayout.CENTER)
            assertLayoutConstraint(gridBagCell(x = 0, y = 0))
        }
    }

    private fun gridBagCell(
        x: Int,
        y: Int,
    ): GridBagConstraints = GridBagConstraints().apply {
        gridx = x
        gridy = y
    }
}
