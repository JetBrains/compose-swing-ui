package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.interaction.onSiblings
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the panel composables. Each test asserts the observable Swing facts: the
 * panel is a [JPanel] carrying the expected [java.awt.LayoutManager], it hosts its declared children
 * as real AWT descendants, and add/remove of children across recomposition is reflected in the live
 * component tree (count and order).
 */
class PanelLayoutTest {
    @Test
    fun anUndeclaredBorderPanelIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { BorderPanel { Label("a") } }
        onNodeWithText("a").onParent().assertTreeMatches(JPanel(BorderLayout()).apply { add(JLabel("a")) })
    }

    @Test
    fun anUndeclaredFlowPanelIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { FlowPanel { Label("a") } }
        onNodeWithText("a").onParent().assertTreeMatches(JPanel(FlowLayout()).apply { add(JLabel("a")) })
    }

    @Test
    fun anUndeclaredGridPanelIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { GridPanel { Label("a") } }
        onNodeWithText("a").onParent().assertTreeMatches(JPanel(GridLayout()).apply { add(JLabel("a")) })
    }

    @Test
    fun anUndeclaredBoxPanelIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { BoxPanel { Label("a") } }
        val reference =
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(JLabel("a"))
            }
        onNodeWithText("a").onParent().assertTreeMatches(reference)
    }

    @Test
    fun borderPanelUsesBorderLayoutAndHostsChildren() = runComposeSwingTest {
        setContent {
            BorderPanel {
                Label("only", SwingModifier.center())
            }
        }
        // A child reported to sit in a BorderLayout region is a child of a panel laid out by one.
        onNodeWithText("only").apply {
            assertLayoutConstraint(BorderLayout.CENTER)
            onSiblings().assertCountEquals(0)
        }
    }

    @Test
    fun flowPanelUsesFlowLayoutAndHostsChildrenInOrder() = runComposeSwingTest {
        setContent {
            FlowPanel {
                Label("a")
                Label("b")
            }
        }
        val panel = onNodeWithText("a").onParent()
        assertTrue(panel.fetch<JPanel>().layout is FlowLayout, "the flow panel should use a FlowLayout")
        panel.onChildren().assertCountEquals(2)
        panel.onChildAt(0).assertTextEquals("a")
        panel.onChildAt(1).assertTextEquals("b")
    }

    @Test
    fun boxPanelUsesBoxLayout() = runComposeSwingTest {
        setContent {
            BoxPanel(axis = BoxLayout.X_AXIS) {
                Label("a")
            }
        }
        val panel = onNodeWithText("a").onParent()
        assertTrue(panel.fetch<JPanel>().layout is BoxLayout, "the box panel should use a BoxLayout")
        panel.onChildren().assertCountEquals(1)
    }

    @Test
    fun aColumnStacksItsContentAndARowLinesItUp() = runComposeSwingTest {
        setContent {
            Column {
                Label("a")
                Label("b")
            }
            Row {
                Label("c")
                Label("d")
            }
        }

        val column = onNodeWithText("a").onParent()
        column.onChildren().assertCountEquals(2)
        column.onChildAt(0).assertTextEquals("a")
        column.onChildAt(1).assertTextEquals("b")
        val stacked = column.fetch<JPanel>().components.map { it.bounds }
        assertEquals(stacked[0].x, stacked[1].x, "a column should keep its content on one vertical line")
        assertTrue(
            stacked[0].y + stacked[0].height <= stacked[1].y,
            "a column should stack its content, each child below the one before it",
        )

        val row = onNodeWithText("c").onParent()
        row.onChildren().assertCountEquals(2)
        row.onChildAt(0).assertTextEquals("c")
        row.onChildAt(1).assertTextEquals("d")
        val linedUp = row.fetch<JPanel>().components.map { it.bounds }
        assertEquals(linedUp[0].y, linedUp[1].y, "a row should keep its content on one horizontal line")
        assertTrue(
            linedUp[0].x + linedUp[0].width <= linedUp[1].x,
            "a row should line its content up, each child after the one before it",
        )
    }

    @Test
    fun aColumnFollowsTheChildrenItDeclares() = runComposeSwingTest {
        var showSecond by mutableStateOf(false)
        setContent {
            Column {
                Label("first")
                if (showSecond) Label("second")
            }
        }

        val column = onNodeWithText("first").onParent()
        column.onChildren().assertCountEquals(1)

        showSecond = true
        awaitIdle()
        column.onChildren().assertCountEquals(2)
        column.onChildAt(0).assertTextEquals("first")
        column.onChildAt(1).assertTextEquals("second")

        showSecond = false
        awaitIdle()
        column.onChildren().assertCountEquals(1)
        column.onChildAt(0).assertTextEquals("first")
    }

    @Test
    fun gridPanelUsesGridLayout() = runComposeSwingTest {
        setContent {
            GridPanel(rows = 2, cols = 2) {
                Label("a")
                Label("b")
            }
        }
        val panel = onNodeWithText("a").onParent()
        assertTrue(panel.fetch<JPanel>().layout is GridLayout, "the grid panel should use a GridLayout")
        panel.onChildren().assertCountEquals(2)
        panel.onChildAt(0).assertTextEquals("a")
        panel.onChildAt(1).assertTextEquals("b")
    }

    @Test
    fun gridBagPanelUsesGridBagLayout() = runComposeSwingTest {
        setContent {
            GridBagPanel {
                Label("a", SwingModifier.item(gridx = 0, gridy = 0))
            }
        }
        // A child reported to sit in a grid-bag cell is a child of a panel laid out by one.
        val cell = onNodeWithText("a")
        cell.assertLayoutConstraint(
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
            },
        )
        assertTrue(
            cell.onParent().fetch<JPanel>().layout is GridBagLayout,
            "the grid-bag panel should use a GridBagLayout",
        )
        cell.onSiblings().assertCountEquals(0)
    }

    @Test
    fun cardPanelUsesCardLayout() = runComposeSwingTest {
        setContent {
            CardPanel(selectedCard = "only") {
                Label("a", SwingModifier.card("only"))
            }
        }
        assertTrue(
            onNodeWithText("a").onParent().fetch<JPanel>().layout is CardLayout,
            "the card panel should use a CardLayout",
        )
        onNodeWithText("a").onSiblings().assertCountEquals(0)
    }

    @Test
    fun panelAddsAndRemovesChildrenAcrossRecomposition() = runComposeSwingTest {
        var showSecond by mutableStateOf(false)
        setContent {
            FlowPanel {
                Label("first")
                if (showSecond) Label("second")
            }
        }
        val panel = onNodeWithText("first").onParent()
        panel.onChildren().assertCountEquals(1)

        // Adding a child on recomposition attaches a new AWT descendant in declaration order.
        showSecond = true
        awaitIdle()
        panel.onChildren().assertCountEquals(2)
        panel.onChildAt(0).assertTextEquals("first")
        panel.onChildAt(1).assertTextEquals("second")

        // Removing it detaches that descendant; the survivor stays.
        showSecond = false
        awaitIdle()
        panel.onChildren().assertCountEquals(1)
        panel.onChildAt(0).assertTextEquals("first")
        onNodeWithText("second").assertDoesNotExist()
    }

    @Test
    fun nestedPanelsHostTheirOwnChildren() = runComposeSwingTest {
        setContent {
            BoxPanel(axis = BoxLayout.Y_AXIS) {
                FlowPanel {
                    Label("leaf")
                }
            }
        }
        val inner = onNodeWithText("leaf").onParent()
        assertTrue(inner.fetch<JPanel>().layout is FlowLayout, "the inner panel should be the flow panel")
        inner.onChildren().assertCountEquals(1)

        val outer = inner.onParent()
        assertTrue(outer.fetch<JPanel>().layout is BoxLayout, "the outer panel should be the box panel")
        outer.onChildren().assertCountEquals(1)
        outer.onChildren().assertAll(SwingMatcher.isOfType<JPanel>())
    }
}
