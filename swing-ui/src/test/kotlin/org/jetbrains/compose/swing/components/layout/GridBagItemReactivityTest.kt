package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.Insets
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * Every field of a [GridBagPanelScope] item is composition state: the constraints the panel holds for
 * an already-attached child are re-derived from the declaration on each pass, so an edited field
 * reaches the layout and an edit back to the first value reaches it again - with the child itself
 * staying attached throughout.
 */
class GridBagItemReactivityTest {
    @Test
    fun everyConstraintFieldOfAnItemFollowsItsDeclaration() = runComposeSwingTest {
        var stretched by mutableStateOf(false)
        setContent {
            GridBagPanel {
                item(
                    gridx = if (stretched) 2 else 0,
                    gridy = if (stretched) 3 else 0,
                    gridwidth = if (stretched) GridBagConstraints.REMAINDER else 1,
                    gridheight = if (stretched) 2 else 1,
                    weightx = if (stretched) 1.0 else 0.0,
                    weighty = if (stretched) 0.5 else 0.0,
                    anchor = if (stretched) GridBagConstraints.LINE_END else GridBagConstraints.CENTER,
                    fill = if (stretched) GridBagConstraints.BOTH else GridBagConstraints.NONE,
                    insets = if (stretched) Insets(4, 5, 6, 7) else Insets(0, 0, 0, 0),
                    ipadx = if (stretched) 8 else 0,
                    ipady = if (stretched) 9 else 0,
                ) { Label(text = "cell") }
            }
        }

        val packed =
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
            }
        val stretchedConstraints =
            GridBagConstraints(
                2,
                3,
                GridBagConstraints.REMAINDER,
                2,
                1.0,
                0.5,
                GridBagConstraints.LINE_END,
                GridBagConstraints.BOTH,
                Insets(4, 5, 6, 7),
                8,
                9,
            )
        val cell = onNodeWithText("cell")
        val child = cell.fetch<Component>()
        val panel = cell.onParent().fetch<JPanel>()
        val layout = panel.layout
        cell.assertLayoutConstraint(packed)

        stretched = true
        awaitIdle()
        cell.assertLayoutConstraint(stretchedConstraints)
        assertSame(child, cell.fetch(), "the child should stay attached across the edit")
        // The constraints are re-applied to the layout the panel already runs; a replaced manager
        // would have dropped the placement of every child it never saw attached.
        assertSame(layout, panel.layout, "the panel should keep the layout it started with")

        stretched = false
        awaitIdle()
        cell.assertLayoutConstraint(packed)
        assertSame(child, cell.fetch(), "the child should still be the same component")
    }

    @Test
    fun anItemsChildFollowsItsDeclarationWhileTheConstraintsStay() = runComposeSwingTest {
        var caption by mutableStateOf("first")
        setContent {
            GridBagPanel {
                item(gridx = 1, gridy = 1, ipadx = 12) { Label(text = caption) }
            }
        }

        val declared =
            GridBagConstraints().apply {
                gridx = 1
                gridy = 1
                ipadx = 12
            }
        onNodeWithText("first").assertLayoutConstraint(declared)

        caption = "second"
        awaitIdle()

        onNodeWithText("first").assertDoesNotExist()
        onNodeWithText("second").assertLayoutConstraint(declared)
    }

    @Test
    fun anItemThatEmitsNothingDoesNotShiftItsSiblingsPlacement() = runComposeSwingTest {
        var column by mutableStateOf(4)
        setContent {
            GridBagPanel {
                item(gridx = 0) { Label(text = "head") }
                // An item is free to emit nothing, so an item's position among the declarations is not
                // its child's position among the panel's children.
                item(gridx = 1) { }
                item(gridx = column) { Label(text = "tail") }
            }
        }

        onNodeWithText("head").assertLayoutConstraint(columnOf(0))
        onNodeWithText("tail").assertLayoutConstraint(columnOf(4))

        column = 6
        awaitIdle()

        onNodeWithText("tail").assertLayoutConstraint(columnOf(6))
        onNodeWithText("head").assertLayoutConstraint(columnOf(0))
    }
}

/** The constraints of an item that declares nothing but its column. */
private fun columnOf(column: Int): GridBagConstraints = GridBagConstraints().apply { gridx = column }
