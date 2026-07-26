package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Container
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for [ComboBox]'s composable `itemContent`. A `JComboBox` renderer is a
 * [javax.swing.ListCellRenderer] over an internal `JList`, so these stamp an item through the installed
 * renderer as the popup list would, and assert the realized composable cell. Omitting `itemContent`
 * keeps the default `toString` renderer.
 */
class ComboBoxComposableCellTest {
    @Test
    fun itemContentRealizesAComposableCellPerItem() = runComposeSwingTest {
        setContent {
            ComboBox(items = listOf("red", "green", "blue"), selectedIndex = 0) { item ->
                FlowPanel { Label(item) }
            }
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        assertEquals("green", stampItem(combo, index = 1).firstLabelText(), "the cell should render item 1")
        assertEquals("blue", stampItem(combo, index = 2).firstLabelText(), "the reused cell should restamp item 2")
    }

    @Test
    fun recomposedItemContentUpdatesTheCell() = runComposeSwingTest {
        var items by mutableStateOf(listOf("old"))
        setContent {
            ComboBox(items = items, selectedIndex = 0) { item ->
                Label(item)
            }
        }

        val combo = onNodeOfType<JComboBox<String>>().fetch<JComboBox<String>>()
        assertEquals("old", stampItem(combo, index = 0).firstLabelText(), "the initial item should render")

        items = listOf("new")
        awaitIdle()
        assertEquals("new", stampItem(combo, index = 0).firstLabelText(), "changing the item should restamp the cell")
    }

    @Test
    fun aStampAfterTheRendererLeavesTheCompositionIsSafe() = runComposeSwingTest {
        var showCombo by mutableStateOf(true)
        setContent {
            if (showCombo) {
                ComboBox(items = listOf("red", "green"), selectedIndex = 0) { item ->
                    Label(item)
                }
            }
        }

        val combo = onNodeOfType<JComboBox<String>>().fetch<JComboBox<String>>()
        val cellBefore = stampItem(combo, index = 0)

        // The JComboBox outlives its composition: its popup list keeps the renderer the widget
        // captured and Swing keeps dispatching queued focus/layout events against the widget while
        // its window is torn down, re-invoking that renderer after the cell island is disposed.
        showCombo = false
        awaitIdle()

        val cellAfter = stampItem(combo, index = 1)
        assertSame(
            cellBefore,
            cellAfter,
            "a stamp on a disposed cell island must be a no-op returning the reused host",
        )
    }

    @Test
    fun omittingItemContentKeepsTheDefaultRenderer() = runComposeSwingTest {
        setContent { ComboBox(items = listOf("a", "b"), selectedIndex = 0) }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<*>>()
        val cell = stampItem(combo, index = 0)
        assertTrue(cell is JLabel, "the default combo renderer stamps a JLabel")
        assertEquals("a", (cell as JLabel).text, "the default renderer renders the item's toString")
    }
}

/** Stamps [index] through the combo box's installed cell renderer, as its popup list would. */
@Suppress("UNCHECKED_CAST")
private fun <T> stampItem(
    combo: JComboBox<T>,
    index: Int,
): Component {
    val renderer = combo.renderer as ListCellRenderer<Any?>
    return renderer.getListCellRendererComponent(JList(), combo.getItemAt(index), index, false, false)
}

private fun Component.firstLabelText(): String? = firstLabel()?.text

private fun Component.firstLabel(): JLabel? = when (this) {
    is JLabel -> this
    is Container -> components.firstNotNullOfOrNull { it.firstLabel() }
    else -> null
}
