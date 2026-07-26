package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.awt.GridLayout
import javax.swing.BoxLayout
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the panel composables. Each test asserts the observable Swing facts: the
 * panel is a [JPanel] carrying the expected [java.awt.LayoutManager], it hosts its declared children
 * as real AWT descendants, and add/remove of children across recomposition is reflected in the live
 * component tree (count and order).
 */
class PanelLayoutTest {
    private fun JPanel.childNames(): List<String?> = components.map { it.name }

    @Test
    fun panelUsesProvidedLayoutManagerAndHostsChildren() = runComposeSwingTest {
        setContent {
            Panel(modifier = SwingModifier.name("p"), layout = BorderLayout()) {
                Label("only", modifier = SwingModifier.name("child"))
            }
        }
        val panel = onNodeWithName("p").fetch<JPanel>()
        assertTrue(panel.layout is BorderLayout, "the panel should use the provided BorderLayout")
        assertEquals(1, panel.componentCount, "the panel should host its single child")
        assertEquals(listOf("child"), panel.childNames(), "the panel should host the declared child")
    }

    @Test
    fun flowPanelUsesFlowLayoutAndHostsChildrenInOrder() = runComposeSwingTest {
        setContent {
            FlowPanel(modifier = SwingModifier.name("fp")) {
                Label("a", modifier = SwingModifier.name("a"))
                Label("b", modifier = SwingModifier.name("b"))
            }
        }
        val panel = onNodeWithName("fp").fetch<JPanel>()
        assertTrue(panel.layout is FlowLayout, "the flow panel should use a FlowLayout")
        assertEquals(listOf("a", "b"), panel.childNames(), "the flow panel should host its children in order")
    }

    @Test
    fun boxPanelUsesBoxLayout() = runComposeSwingTest {
        setContent {
            BoxPanel(modifier = SwingModifier.name("bp"), axis = BoxLayout.X_AXIS) {
                Label("a", modifier = SwingModifier.name("a"))
            }
        }
        val panel = onNodeWithName("bp").fetch<JPanel>()
        assertTrue(panel.layout is BoxLayout, "the box panel should use a BoxLayout")
        assertEquals(1, panel.componentCount, "the box panel should host its single child")
    }

    @Test
    fun gridPanelUsesGridLayout() = runComposeSwingTest {
        setContent {
            GridPanel(modifier = SwingModifier.name("gp"), rows = 2, cols = 2) {
                Label("a", modifier = SwingModifier.name("a"))
                Label("b", modifier = SwingModifier.name("b"))
            }
        }
        val panel = onNodeWithName("gp").fetch<JPanel>()
        assertTrue(panel.layout is GridLayout, "the grid panel should use a GridLayout")
        assertEquals(listOf("a", "b"), panel.childNames(), "the grid panel should host its children in order")
    }

    @Test
    fun gridBagPanelUsesGridBagLayout() = runComposeSwingTest {
        setContent {
            GridBagPanel(modifier = SwingModifier.name("gbp")) {
                Label("a", modifier = SwingModifier.name("a"))
            }
        }
        val panel = onNodeWithName("gbp").fetch<JPanel>()
        assertTrue(panel.layout is GridBagLayout, "the grid-bag panel should use a GridBagLayout")
        assertEquals(1, panel.componentCount, "the grid-bag panel should host its single child")
    }

    @Test
    fun cardPanelUsesCardLayout() = runComposeSwingTest {
        setContent {
            CardPanel(modifier = SwingModifier.name("cp")) {
                Label("a", modifier = SwingModifier.name("a"))
            }
        }
        val panel = onNodeWithName("cp").fetch<JPanel>()
        assertTrue(panel.layout is CardLayout, "the card panel should use a CardLayout")
        assertEquals(1, panel.componentCount, "the card panel should host its single child")
    }

    @Test
    fun panelAddsAndRemovesChildrenAcrossRecomposition() = runComposeSwingTest {
        var showSecond by mutableStateOf(false)
        setContent {
            FlowPanel(modifier = SwingModifier.name("fp")) {
                Label("first", modifier = SwingModifier.name("first"))
                if (showSecond) Label("second", modifier = SwingModifier.name("second"))
            }
        }
        assertEquals(
            listOf("first"),
            onNodeWithName("fp").fetch<JPanel>().childNames(),
            "the panel should start with only the first child",
        )

        // Adding a child on recomposition attaches a new AWT descendant in declaration order.
        showSecond = true
        awaitIdle()
        assertEquals(
            listOf("first", "second"),
            onNodeWithName("fp").fetch<JPanel>().childNames(),
            "adding should attach the second child in order",
        )

        // Removing it detaches that descendant; the survivor stays.
        showSecond = false
        awaitIdle()
        assertEquals(
            listOf("first"),
            onNodeWithName("fp").fetch<JPanel>().childNames(),
            "removing should leave only the surviving child",
        )
        // The removed child is no longer an AWT descendant of the panel.
        assertNull(
            onNodeWithName("fp").fetch<JPanel>().components.firstOrNull {
                it.name == "second"
            },
            "the removed child should no longer be a descendant",
        )
    }

    @Test
    fun nestedPanelsHostTheirOwnChildren() = runComposeSwingTest {
        setContent {
            BoxPanel(modifier = SwingModifier.name("outer"), axis = BoxLayout.Y_AXIS) {
                FlowPanel(modifier = SwingModifier.name("inner")) {
                    Label("leaf", modifier = SwingModifier.name("leaf"))
                }
            }
        }
        val outer = onNodeWithName("outer").fetch<JPanel>()
        assertEquals(listOf("inner"), outer.childNames(), "the outer panel should host the inner panel")
        val inner = onNodeWithName("inner").fetch<JPanel>()
        assertEquals(listOf("leaf"), inner.childNames(), "the inner panel should host its own leaf child")
    }
}
