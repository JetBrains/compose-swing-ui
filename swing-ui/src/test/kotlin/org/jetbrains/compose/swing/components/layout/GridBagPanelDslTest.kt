package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import kotlin.test.Test

/**
 * Behavioral tests for the [GridBagPanel] scope-based DSL.
 *
 * Every assertion reads the constraints Swing actually holds for a child
 * ([GridBagLayout.getConstraints]) rather than any internal bookkeeping, so the tests cover both halves
 * of the contract: the constraints an item declares reach the layout when its child is attached, and
 * they are re-applied when the declarations change while the children stay.
 */
class GridBagPanelDslTest {
    @Test
    fun eachItemIsPlacedWithItsDeclaredConstraints() = runComposeSwingTest {
        setContent {
            GridBagPanel {
                item(
                    gridx = 1,
                    gridy = 2,
                    gridwidth = 3,
                    gridheight = 4,
                    weightx = 0.25,
                    weighty = 0.75,
                    anchor = GridBagConstraints.LINE_END,
                    fill = GridBagConstraints.HORIZONTAL,
                    insets = Insets(1, 2, 3, 4),
                    ipadx = 5,
                    ipady = 6,
                ) {
                    Label(text = "spelled out")
                }
                item(gridx = 0, gridy = 0, fill = GridBagConstraints.BOTH) { Label(text = "second") }
            }
        }

        onNodeWithText("spelled out").assertLayoutConstraint(
            GridBagConstraints(
                1,
                2,
                3,
                4,
                0.25,
                0.75,
                GridBagConstraints.LINE_END,
                GridBagConstraints.HORIZONTAL,
                Insets(1, 2, 3, 4),
                5,
                6,
            ),
        )

        // A second item carries its own constraints, not the first one's.
        onNodeWithText("second").assertLayoutConstraint(
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                fill = GridBagConstraints.BOTH
            },
        )
    }

    @Test
    fun omittedFieldsFallBackToTheGridBagConstraintsDefaults() = runComposeSwingTest {
        setContent {
            GridBagPanel {
                // Only ipadx is declared. Reading it back non-zero proves this item's own constraints
                // reached the layout, so every other field is the parameter default rather than the
                // fallback GridBagLayout invents for a component it was never given constraints for.
                item(ipadx = 7) { Label(text = "mostly default") }
            }
        }

        onNodeWithText("mostly default").assertLayoutConstraint(GridBagConstraints().apply { ipadx = 7 })
    }

    @Test
    fun changingAnItemsConstraintsReAppliesThem() = runComposeSwingTest {
        var stretch by mutableStateOf(false)
        setContent {
            GridBagPanel {
                item(gridx = 0, gridy = 0, weightx = if (stretch) 1.0 else 0.0) { Label(text = "cell") }
            }
        }

        onNodeWithText("cell").assertLayoutConstraint(cellAt(column = 0, weightx = 0.0))

        stretch = true
        awaitIdle()

        onNodeWithText("cell").assertLayoutConstraint(cellAt(column = 0, weightx = 1.0))
    }

    @Test
    fun droppingAnItemRemovesItsChildAndLeavesTheRestPlaced() = runComposeSwingTest {
        var showMiddle by mutableStateOf(true)
        setContent {
            GridBagPanel {
                item(gridx = 0, gridy = 0) { Label(text = "first") }
                if (showMiddle) {
                    item(gridx = 1, gridy = 0) { Label(text = "middle") }
                }
                item(gridx = 2, gridy = 0) { Label(text = "last") }
            }
        }

        val first = onNodeWithText("first")
        first.onParent().onChildren().assertCountEquals(3)
        onNodeWithText("middle").assertLayoutConstraint(cellAt(column = 1))

        showMiddle = false
        awaitIdle()

        onNodeWithText("middle").assertDoesNotExist()
        first.onParent().onChildren().assertCountEquals(2)
        first.assertLayoutConstraint(cellAt(column = 0))
        onNodeWithText("last").assertLayoutConstraint(cellAt(column = 2))
    }

    @Test
    fun reorderingItemsKeepsEachChildsConstraints() = runComposeSwingTest {
        var reversed by mutableStateOf(false)
        setContent {
            GridBagPanel {
                if (reversed) {
                    item(gridx = 1, gridy = 0, ipadx = 20) { Label(text = "beta") }
                    item(gridx = 0, gridy = 0, ipadx = 10) { Label(text = "alpha") }
                } else {
                    item(gridx = 0, gridy = 0, ipadx = 10) { Label(text = "alpha") }
                    item(gridx = 1, gridy = 0, ipadx = 20) { Label(text = "beta") }
                }
            }
        }

        val alpha = onNodeWithText("alpha")
        val beta = onNodeWithText("beta")
        alpha.assertLayoutConstraint(cellAt(column = 0, ipadx = 10))
        beta.assertLayoutConstraint(cellAt(column = 1, ipadx = 20))

        reversed = true
        awaitIdle()

        alpha.onParent().onChildren().assertCountEquals(2)
        alpha.assertLayoutConstraint(cellAt(column = 0, ipadx = 10))
        beta.assertLayoutConstraint(cellAt(column = 1, ipadx = 20))
    }
}

/** The constraints of a cell in the top row of a grid-bag panel, every other field left at its default. */
private fun cellAt(
    column: Int,
    weightx: Double = 0.0,
    ipadx: Int = 0,
): GridBagConstraints = GridBagConstraints().apply {
    this.gridx = column
    this.gridy = 0
    this.weightx = weightx
    this.ipadx = ipadx
}
