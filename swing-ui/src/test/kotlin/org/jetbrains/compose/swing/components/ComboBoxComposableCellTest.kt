package org.jetbrains.compose.swing.components

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.selection.firstLabelText
import org.jetbrains.compose.swing.components.selection.stampCell
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
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
import kotlin.test.assertNotSame
import kotlin.test.assertNull
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
            ComboBox(items = listOf("red", "green", "blue"), selectedItem = "red") { item ->
                FlowPanel { Label(item) }
            }
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        assertEquals("green", combo.stampCell(index = 1).firstLabelText(), "the cell should render item 1")
        assertEquals("blue", combo.stampCell(index = 2).firstLabelText(), "the reused cell should restamp item 2")
    }

    @Test
    fun recomposedItemContentUpdatesTheCell() = runComposeSwingTest {
        var items by mutableStateOf(listOf("old"))
        setContent {
            ComboBox(items = items, selectedItem = items.first()) { item ->
                Label(item)
            }
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        assertEquals("old", combo.stampCell(index = 0).firstLabelText(), "the initial item should render")

        items = listOf("new")
        awaitIdle()
        assertEquals("new", combo.stampCell(index = 0).firstLabelText(), "changing the item should restamp the cell")
    }

    @Test
    fun theDisplayAreaOfAnUnselectedComboComposesNoCell() = runComposeSwingTest {
        setContent {
            ComboBox(items = listOf("red", "green"), selectedItem = null) { item ->
                Label(item)
            }
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        assertNull(
            combo.renderer.stampCell(value = null, index = -1).firstLabelText(),
            "a display area showing nothing should compose no cell",
        )
        assertEquals(
            "red",
            combo.stampCell(index = 0).firstLabelText(),
            "the items themselves still render through the cell body",
        )
    }

    @Test
    fun aStampAfterTheRendererLeavesTheCompositionIsSafe() = runComposeSwingTest {
        var showCombo by mutableStateOf(true)
        setContent {
            if (showCombo) {
                ComboBox(items = listOf("red", "green"), selectedItem = "red") { item ->
                    Label(item)
                }
            }
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        val composingRenderer = combo.renderer
        val cellBefore = composingRenderer.stampCell(value = "red", index = 0)

        // The renderer outlives its composition: the popup list of the combo box keeps the renderer it
        // was given and goes on invoking it while the window it belongs to is torn down, after the cell
        // island is disposed.
        showCombo = false
        awaitIdle()

        val cellAfter = composingRenderer.stampCell(value = "green", index = 1)
        assertNotSame(
            cellBefore,
            cellAfter,
            "a disposed cell island must give up the component it composed",
        )
        assertNull(
            cellAfter.firstLabelText(),
            "a stamp on a disposed cell island must render an empty cell rather than a stale item",
        )
        assertTrue(
            combo.stampCell(index = 1) is JLabel,
            "the combo box itself renders through its own renderer again once the composable cell is gone",
        )
    }

    @Test
    fun omittingItemContentKeepsTheDefaultRenderer() = runComposeSwingTest {
        setContent { ComboBox(items = listOf("a", "b"), selectedItem = "a") }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        val cell = combo.stampCell(index = 0)
        assertTrue(cell is JLabel, "the default combo renderer stamps a JLabel")
        assertEquals("a", (cell as JLabel).text, "the default renderer renders the item's toString")
    }

    @Test
    fun itemContentTakenAwayRestoresTheCombosOwnRenderer() = runComposeSwingTest {
        var composableCells by mutableStateOf(true)
        setContent {
            ComboBox(
                items = listOf("red", "green"),
                selectedItem = "red",
                itemContent =
                    if (composableCells) {
                        { item -> FlowPanel { Label(item) } }
                    } else {
                        null
                    },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        val composedCell = combo.stampCell(index = 0)
        assertFalse(composedCell is JLabel, "a composable cell stamps what it composed, not the default JLabel")
        assertEquals("red", composedCell.firstLabelText(), "the composable cell should render item 0")

        composableCells = false
        awaitIdle()
        val defaultCell = combo.stampCell(index = 0)
        assertTrue(defaultCell is JLabel, "taking itemContent away should stamp the combo box's own JLabel renderer")
        assertEquals("red", (defaultCell as JLabel).text, "the restored renderer renders the item's toString")

        composableCells = true
        awaitIdle()
        assertEquals(
            "green",
            combo.stampCell(index = 1).firstLabelText(),
            "declaring itemContent again should stamp the composable cell",
        )
    }

    @Test
    fun everyWithdrawalRestoresTheCombosOwnRenderer() = runComposeSwingTest {
        var composableCells by mutableStateOf(true)
        setContent {
            ComboBox(
                items = listOf("red", "green"),
                selectedItem = "red",
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
            val defaultCell = combo.stampCell(index = 0)
            assertTrue(defaultCell is JLabel, "withdrawal $cycle should stamp the combo box's own JLabel renderer")
            assertEquals("red", (defaultCell as JLabel).text, "withdrawal $cycle should render the item's toString")

            composableCells = true
            awaitIdle()
            assertEquals(
                "red",
                combo.stampCell(index = 0).firstLabelText(),
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
                selectedItem = "red",
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
                    selectedItem = "red",
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
                    selectedItem = "red",
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
                (combo.stampCell(index = 0) as JLabel).text,
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

    @Test
    fun aParkedComboRendersItsOwnCellsAndTheFreshOneStampsTheComposableCellAgain() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                ComboBox(items = listOf("red", "green"), selectedItem = "red") { item -> Label(item) }
            }
        }
        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        assertEquals("red", combo.stampCell(index = 0).firstLabelText(), "the composable cell should render item 0")

        active = false
        awaitIdle()

        // A parked combo box keeps painting through whatever renderer it carries once the cell island
        // behind its composable cell is gone: the renderer it rendered through before that cell is what
        // has to be back on it by then.
        val parked = combo.stampCell(index = 0)
        assertTrue(parked is JLabel, "a parked combo box should render items through the renderer of its own")
        assertEquals("red", (parked as JLabel).text, "the combo box's own renderer renders the item's toString")

        active = true
        awaitIdle()

        assertEquals(
            "green",
            onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>().stampCell(index = 1).firstLabelText(),
            "the fresh combo box should stamp the composable cell",
        )
    }
}
