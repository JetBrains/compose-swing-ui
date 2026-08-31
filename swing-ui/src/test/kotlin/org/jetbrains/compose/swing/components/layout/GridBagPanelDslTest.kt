package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import kotlin.test.Test

/**
 * Behavioral tests for the [GridBagPanel] placement DSL.
 *
 * Every assertion reads the constraints Swing actually holds for a child
 * ([GridBagLayout.getConstraints]) rather than any internal bookkeeping, so the tests cover both halves
 * of the contract: the constraints a child declares reach the layout when that child is attached, and
 * they are re-applied when the declarations change while the children stay.
 */
class GridBagPanelDslTest {
    @Test
    fun anItemAppendsToTheChainWithoutRepeatingIt() {
        with(GridBagPanelScopeImpl) { assertDeclaredChainCarriedOnce { item(gridx = 0, gridy = 0) } }
    }

    @Test
    fun aChildDeclaringNoItemGetsTheDefaultConstraintsWhileSiblingsKeepTheirs() = runComposeSwingTest {
        setContent {
            GridBagPanel {
                Label(text = "placed", modifier = SwingModifier.item(gridx = 2, gridy = 1, ipadx = 9))
                Label(text = "loose")
            }
        }

        // GridBagLayout, not the panel, decides constraints for a child with no declared item: a child
        // declaring none is attached, not rejected.
        onNodeWithText("loose").assertLayoutConstraint(GridBagConstraints())

        onNodeWithText("placed").assertLayoutConstraint(cellAt(column = 2, row = 1, ipadx = 9))
    }

    @Test
    fun eachChildIsPlacedWithItsDeclaredConstraints() = runComposeSwingTest {
        setContent {
            GridBagPanel {
                Label(
                    text = "spelled out",
                    modifier =
                        SwingModifier.item(
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
                        ),
                )
                Label(
                    text = "second",
                    modifier = SwingModifier.item(gridx = 0, gridy = 0, fill = GridBagConstraints.BOTH),
                )
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
                // Only ipadx is declared. Reading it back non-zero proves this child's own constraints
                // reached the layout, so every other field is the parameter default, not the fallback
                // GridBagLayout invents for a child it was never given constraints for.
                Label(text = "mostly default", modifier = SwingModifier.item(ipadx = 7))
            }
        }

        onNodeWithText("mostly default").assertLayoutConstraint(GridBagConstraints().apply { ipadx = 7 })
    }

    @Test
    fun changingAChildsConstraintsReAppliesThem() = runComposeSwingTest {
        var stretch by mutableStateOf(false)
        setContent {
            GridBagPanel {
                Label(
                    text = "cell",
                    modifier = SwingModifier.item(gridx = 0, gridy = 0, weightx = if (stretch) 1.0 else 0.0),
                )
            }
        }

        onNodeWithText("cell").assertLayoutConstraint(cellAt(column = 0, weightx = 0.0))

        stretch = true
        awaitIdle()

        onNodeWithText("cell").assertLayoutConstraint(cellAt(column = 0, weightx = 1.0))
    }

    @Test
    fun aPlacementLeavingTheChainUnplacesItsChild() = runComposeSwingTest {
        var placed by mutableStateOf(true)
        setContent {
            GridBagPanel {
                Label(
                    text = "cell",
                    modifier = if (placed) SwingModifier.item(gridx = 3, gridy = 1, ipadx = 5) else SwingModifier,
                )
            }
        }

        onNodeWithText("cell").assertLayoutConstraint(cellAt(column = 3, row = 1, ipadx = 5))

        placed = false
        awaitIdle()

        // The child stays where it is among its siblings and loses only its placement, so the layout
        // holds nothing for it and answers with the defaults it invents for an unconstrained child.
        onNodeWithText("cell").assertLayoutConstraint(GridBagConstraints())
    }

    @Test
    fun droppingAChildRemovesItAndLeavesTheRestPlaced() = runComposeSwingTest {
        var showMiddle by mutableStateOf(true)
        setContent {
            GridBagPanel {
                Label(text = "first", modifier = SwingModifier.item(gridx = 0, gridy = 0))
                if (showMiddle) {
                    Label(text = "middle", modifier = SwingModifier.item(gridx = 1, gridy = 0))
                }
                Label(text = "last", modifier = SwingModifier.item(gridx = 2, gridy = 0))
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
    fun reorderingChildrenKeepsEachOnesConstraints() = runComposeSwingTest {
        var reversed by mutableStateOf(false)
        setContent {
            GridBagPanel {
                if (reversed) {
                    Label(text = "beta", modifier = SwingModifier.item(gridx = 1, gridy = 0, ipadx = 20))
                    Label(text = "alpha", modifier = SwingModifier.item(gridx = 0, gridy = 0, ipadx = 10))
                } else {
                    Label(text = "alpha", modifier = SwingModifier.item(gridx = 0, gridy = 0, ipadx = 10))
                    Label(text = "beta", modifier = SwingModifier.item(gridx = 1, gridy = 0, ipadx = 20))
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

/** The constraints of one cell of a grid-bag panel, every other field left at its default. */
private fun cellAt(
    column: Int,
    row: Int = 0,
    weightx: Double = 0.0,
    ipadx: Int = 0,
): GridBagConstraints = GridBagConstraints().apply {
    this.gridx = column
    this.gridy = row
    this.weightx = weightx
    this.ipadx = ipadx
}
