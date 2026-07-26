package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Container
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel
import javax.swing.plaf.nimbus.NimbusLookAndFeel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for [ComboBox]'s composable `itemContent`. A `JComboBox` renderer is a
 * [javax.swing.ListCellRenderer] over an internal `JList`, so these stamp an item through the installed
 * renderer as the popup list would, and assert the realized composable cell. A `null` `itemContent`
 * renders items through the combo box's own renderer, on every composition; taking `itemContent` away
 * leaves an editable combo box's editor, and the value being typed into it, as they were.
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

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
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

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
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

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        val cell = stampItem(combo, index = 0)
        assertTrue(cell is JLabel, "the default combo renderer stamps a JLabel")
        assertEquals("a", (cell as JLabel).text, "the default renderer renders the item's toString")
    }

    @Test
    fun itemContentTakenAwayRestoresTheCombosOwnRenderer() = runComposeSwingTest {
        var composableCells by mutableStateOf(true)
        setContent {
            ComboBox(
                items = listOf("red", "green"),
                selectedIndex = 0,
                itemContent =
                    if (composableCells) {
                        { item -> FlowPanel { Label(item) } }
                    } else {
                        null
                    },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        val composedCell = stampItem(combo, index = 0)
        assertFalse(composedCell is JLabel, "a composable cell stamps the composed host, not the default JLabel")
        assertEquals("red", composedCell.firstLabelText(), "the composable cell should render item 0")

        composableCells = false
        awaitIdle()
        val defaultCell = stampItem(combo, index = 0)
        assertTrue(defaultCell is JLabel, "taking itemContent away should stamp the combo box's own JLabel renderer")
        assertEquals("red", (defaultCell as JLabel).text, "the restored renderer renders the item's toString")

        composableCells = true
        awaitIdle()
        assertEquals(
            "green",
            stampItem(combo, index = 1).firstLabelText(),
            "declaring itemContent again should stamp the composable cell",
        )
    }

    @Test
    fun everyWithdrawalRestoresTheCombosOwnRenderer() = runComposeSwingTest {
        var composableCells by mutableStateOf(true)
        setContent {
            ComboBox(
                items = listOf("red", "green"),
                selectedIndex = 0,
                itemContent =
                    if (composableCells) {
                        { item -> FlowPanel { Label(item) } }
                    } else {
                        null
                    },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()

        // The way back to the combo box's own renderer is captured afresh each time a composable cell
        // displaces it, so it survives any number of withdrawals rather than only the first.
        repeat(2) { cycle ->
            composableCells = false
            awaitIdle()
            val defaultCell = stampItem(combo, index = 0)
            assertTrue(defaultCell is JLabel, "withdrawal $cycle should stamp the combo box's own JLabel renderer")
            assertEquals("red", (defaultCell as JLabel).text, "withdrawal $cycle should render the item's toString")

            composableCells = true
            awaitIdle()
            assertEquals(
                "red",
                stampItem(combo, index = 0).firstLabelText(),
                "redeclaring itemContent after withdrawal $cycle should stamp the composable cell",
            )
        }
    }

    @Test
    fun itemContentTakenAwayKeepsTheEditorAndTheValueBeingTyped() = runComposeSwingTest {
        var composableCells by mutableStateOf(true)
        setContent {
            ComboBox(
                items = listOf("red", "green"),
                selectedIndex = 0,
                editable = true,
                itemContent =
                    if (composableCells) {
                        { item -> Label(item) }
                    } else {
                        null
                    },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        val editor = combo.editor
        // A value typed into the editor and not yet committed lives only in the editor: the combo
        // box still has "red" selected and the model knows nothing about it.
        (editor.editorComponent as JTextField).text = "purple"

        composableCells = false
        awaitIdle()

        assertSame(editor, combo.editor, "withdrawing a cell renderer must leave the editor in place")
        assertEquals(
            "purple",
            (combo.editor.editorComponent as JTextField).text,
            "the value being typed must survive a change that concerns rendering only",
        )
    }

    @Test
    fun takingItemContentAwayLeavesTheCompositionIntact() = runComposeSwingTest {
        var composableCells by mutableStateOf(true)
        var showCombo by mutableStateOf(true)
        setContent {
            if (showCombo) {
                ComboBox(
                    items = listOf("red", "green"),
                    selectedIndex = 0,
                    editable = true,
                    itemContent =
                        if (composableCells) {
                            { item -> Label(item) }
                        } else {
                            null
                        },
                )
            }
        }

        // An editable combo box holding a value that is in neither its model nor its selection is the
        // fullest state a cell renderer can be withdrawn from: the widget has an editor, a popup and a
        // display value of its own, all of which a rendering change has to leave standing.
        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        (combo.editor.editorComponent as JTextField).text = "purple"

        composableCells = false
        awaitIdle()

        // Withdrawing a composable cell renderer must touch the widget only. Discarding the combo box
        // afterwards rewrites the very slots the withdrawal ran from, and disposing the composition at
        // the end of the test rewrites all of them, so both fail outright if the withdrawal disturbed
        // the composition it ran inside.
        showCombo = false
        awaitIdle()
        onAllNodesOfType<JComboBox<*>>().assertCountEquals(0)
    }

    @Test
    fun theRestoredRendererFollowsLaterLookAndFeelChanges() = runComposeSwingTest {
        val hostLookAndFeel = UIManager.getLookAndFeel()
        UIManager.setLookAndFeel(MetalLookAndFeel())
        try {
            var composableCells by mutableStateOf(true)
            setContent {
                ComboBox(
                    items = listOf("red", "green"),
                    selectedIndex = 0,
                    itemContent =
                        if (composableCells) {
                            { item -> Label(item) }
                        } else {
                            null
                        },
                )
            }

            val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
            val composingRenderer: ListCellRenderer<*> = combo.renderer

            // A look-and-feel change updates every component's UI, which is what an application does
            // by walking its tree; a composable cell renderer is not the look and feel's, so the
            // incoming delegate keeps it and installs none of its own.
            UIManager.setLookAndFeel(NimbusLookAndFeel())
            SwingUtilities.updateComponentTreeUI(root)
            assertSame(composingRenderer, combo.renderer, "a look-and-feel change should keep the composable cell")

            composableCells = false
            awaitIdle()
            assertEquals(
                "red",
                (stampItem(combo, index = 0) as JLabel).text,
                "taking itemContent away should stamp the combo box's own renderer",
            )

            // What comes back is the combo box's own renderer, which is the look and feel's to
            // replace: the next look-and-feel change hands it the renderer of the look and feel in
            // force, exactly as on a combo box that never carried a composable cell.
            SwingUtilities.updateComponentTreeUI(root)
            assertEquals(
                JComboBox<String>().renderer.javaClass,
                combo.renderer.javaClass,
                "a look-and-feel change should give the combo box the renderer of the look and feel in force",
            )
        } finally {
            // The look and feel is process-wide state deciding how every later component renders, so
            // putting the host one back is what keeps every other test in this JVM measuring against
            // the look and feel it expects.
            UIManager.setLookAndFeel(hostLookAndFeel)
        }
    }
}

/** Stamps [index] through the combo box's installed cell renderer, as its popup list would. */
private fun <T> stampItem(
    combo: JComboBox<T>,
    index: Int,
): Component {
    // The value stamped below is the combo box's own item, which is what a renderer of `in T` is there
    // to render, so widening the receiver drops a bound this call cannot violate.
    @Suppress("UNCHECKED_CAST")
    val renderer = combo.renderer as ListCellRenderer<Any?>
    return renderer.getListCellRendererComponent(JList(), combo.getItemAt(index), index, false, false)
}

private fun Component.firstLabelText(): String? = firstLabel()?.text

private fun Component.firstLabel(): JLabel? = when (this) {
    is JLabel -> this
    is Container -> components.firstNotNullOfOrNull { it.firstLabel() }
    else -> null
}
