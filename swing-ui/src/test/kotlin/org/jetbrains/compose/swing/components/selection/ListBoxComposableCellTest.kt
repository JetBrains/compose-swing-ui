package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList
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
 * Behavioral tests for [ListBox]'s composable `itemContent`. They prove the rubber-stamp mechanism end
 * to end: stamping a row through the installed [javax.swing.ListCellRenderer] realizes the composable
 * cell into a real Swing subtree, that the same reused renderer restamps as items/selection change, and
 * that a `null` `itemContent` renders rows through the JList's own `toString` renderer - whether it is
 * `null` from the start or becomes `null` on a later composition.
 *
 * The cell's component lives outside the composition root - it is what the renderer hands the widget -
 * so these drive the renderer directly (as `JList` does when it paints a row) and inspect what it
 * returns.
 */
class ListBoxComposableCellTest {
    @Test
    fun itemContentRealizesAComposableCellPerRow() = runComposeSwingTest {
        setContent {
            ListBox(items = listOf("alpha", "beta", "gamma")) { item ->
                FlowPanel { Label(item) }
            }
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        // Stamp row 1 exactly as the JList's CellRendererPane would when painting it.
        val cell = list.stampCell(index = 1)

        assertEquals(
            "beta",
            cell.firstLabelText(),
            "the composable cell for row 1 should have realized a JLabel carrying that row's text",
        )
    }

    @Test
    fun theSameRendererRestampsAsRowsChange() = runComposeSwingTest {
        setContent {
            ListBox(items = listOf("one", "two", "three")) { item ->
                Label(item)
            }
        }

        val list = onNodeOfType<JList<String>>().fetch()
        assertEquals("one", list.stampCell(index = 0).firstLabelText(), "row 0 should render its item")
        // A single reused composition/host restamps for each row the widget asks to paint.
        assertEquals("three", list.stampCell(index = 2).firstLabelText(), "the reused cell should restamp row 2")
        assertEquals("two", list.stampCell(index = 1).firstLabelText(), "and restamp again for row 1")
    }

    @Test
    fun recomposedRowContentUpdatesTheCell() = runComposeSwingTest {
        var items by mutableStateOf(listOf("draft"))
        setContent {
            ListBox(items = items) { item ->
                Label(item)
            }
        }

        val list = onNodeOfType<JList<String>>().fetch()
        assertEquals("draft", list.stampCell(index = 0).firstLabelText(), "the initial item should render")

        // Changing the backing items recomposes the list and the cell restamps the new value for that row.
        items = listOf("final")
        awaitIdle()
        assertEquals(
            "final",
            list.stampCell(index = 0).firstLabelText(),
            "changing the item should restamp the cell with the new content",
        )
    }

    @Test
    fun cellScopeReflectsSelectionForTheStampedRow() = runComposeSwingTest {
        setContent {
            ListBox(items = listOf("x", "y")) { item ->
                Label(if (isSelected) "$item*" else item)
            }
        }

        val list = onNodeOfType<JList<String>>().fetch()
        assertEquals(
            "y",
            list.stampCell(index = 1, isSelected = false).firstLabelText(),
            "an unselected stamp should render the plain item",
        )
        assertEquals(
            "y*",
            list.stampCell(index = 1, isSelected = true).firstLabelText(),
            "a selected stamp should observe isSelected through the ListItemScope",
        )
    }

    @Test
    fun aNullItemRendersThroughTheCellBodyLikeAnyOther() = runComposeSwingTest {
        setContent {
            ListBox(items = listOf(null, "beta")) { item ->
                Label(item ?: "(none)")
            }
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<String?>>()
        assertEquals(
            "(none)",
            list.stampCell(index = 0).firstLabelText(),
            "a row whose item is null is a row the cell body renders",
        )
        assertEquals("beta", list.stampCell(index = 1).firstLabelText(), "the next row renders its own item")
    }

    @Test
    fun composableCellsWorkInsideAScrollPane() = runComposeSwingTest {
        // A composable cell island joins the enclosing composition, so it must not inherit the slot
        // attachment of the ScrollPane viewport that hosts the ListBox - otherwise the cell's own nodes
        // would try to install into that viewport as if the cell were its view. Selecting a
        // row synchronously stamps a cell during the enclosing composition's apply pass, which is exactly
        // when such leakage surfaces.
        setContent {
            ScrollPane {
                content {
                    ListBox(items = listOf("first", "second"), selectedIndices = setOf(0)) { item ->
                        FlowPanel { Label(item) }
                    }
                }
            }
        }

        val list = onNodeOfType<JList<String>>().fetch()
        assertEquals(
            "second",
            list.stampCell(index = 1).firstLabelText(),
            "a composable cell inside a ScrollPane should realize its row content, not leak the viewport slot",
        )
    }

    @Test
    fun aStampAfterTheRendererLeavesTheCompositionIsSafe() = runComposeSwingTest {
        var showList by mutableStateOf(true)
        setContent {
            if (showList) {
                ListBox(items = listOf("alpha", "beta")) { item ->
                    Label(item)
                }
            }
        }

        val list = onNodeOfType<JList<String>>().fetch()
        val composingRenderer = list.cellRenderer
        val cellBefore = composingRenderer.stampCell(value = "alpha", index = 0, list = list)

        // The renderer outlives its composition: whoever captured it - a widget, a popup of its own -
        // goes on invoking it while the window it belongs to is torn down, after the cell island is
        // disposed.
        showList = false
        awaitIdle()

        val cellAfter = composingRenderer.stampCell(value = "beta", index = 1, list = list)
        assertNotSame(
            cellBefore,
            cellAfter,
            "a disposed cell island must give up the component it composed",
        )
        assertNull(
            cellAfter.firstLabelText(),
            "a stamp on a disposed cell island must render an empty cell rather than a stale row",
        )
        assertTrue(
            list.stampCell(index = 1) is JLabel,
            "the list itself renders through its own renderer again once the composable cell is gone",
        )
    }

    @Test
    fun itemContentTakenAwayRestoresTheListsOwnRenderer() = runComposeSwingTest {
        var composableCells by mutableStateOf(true)
        setContent {
            ListBox(
                items = listOf("alpha", "beta"),
                itemContent =
                    if (composableCells) {
                        { item -> FlowPanel { Label(item) } }
                    } else {
                        null
                    },
            )
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        val composedCell = list.stampCell(index = 0)
        assertFalse(composedCell is JLabel, "a composable cell stamps what it composed, not the default JLabel")
        assertEquals("alpha", composedCell.firstLabelText(), "the composable cell should render row 0")

        composableCells = false
        awaitIdle()
        assertNull(
            list.cellRenderer as? ComposingListCellRenderer<*>,
            "taking itemContent away must leave the list's own renderer, not a composing one",
        )
        val defaultCell = list.stampCell(index = 0)
        assertTrue(defaultCell is JLabel, "the restored renderer stamps a JLabel")
        assertEquals("alpha", (defaultCell as JLabel).text, "the restored renderer renders the item's toString")

        composableCells = true
        awaitIdle()
        assertEquals(
            "beta",
            list.stampCell(index = 1).firstLabelText(),
            "declaring itemContent again should stamp the composable cell",
        )
    }

    @Test
    fun itemContentTakenAwayRestoresTheListsOwnRendererUnderAModel() = runComposeSwingTest {
        val model = DefaultListModel<String>().apply { addAll(listOf("alpha", "beta")) }
        var composableCells by mutableStateOf(true)
        setContent {
            ListBox(
                model = model,
                itemContent =
                    if (composableCells) {
                        { item -> FlowPanel { Label(item) } }
                    } else {
                        null
                    },
            )
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        assertFalse(list.stampCell(index = 0) is JLabel, "a composable cell stamps what it composed")

        composableCells = false
        awaitIdle()
        val defaultCell = list.stampCell(index = 0)
        assertTrue(defaultCell is JLabel, "taking itemContent away should stamp the list's own JLabel renderer")
        assertEquals("alpha", (defaultCell as JLabel).text, "the restored renderer renders the item's toString")

        composableCells = true
        awaitIdle()
        assertEquals(
            "beta",
            list.stampCell(index = 1).firstLabelText(),
            "declaring itemContent again should stamp the composable cell",
        )
    }

    @Test
    fun takingItemContentAwayLeavesTheCompositionIntact() = runComposeSwingTest {
        var composableCells by mutableStateOf(true)
        var showList by mutableStateOf(true)
        setContent {
            if (showList) {
                ListBox(
                    items = listOf("alpha", "beta"),
                    selectedIndices = setOf(0),
                    itemContent =
                        if (composableCells) {
                            { item -> Label(item) }
                        } else {
                            null
                        },
                )
            }
        }

        composableCells = false
        awaitIdle()

        // Withdrawing a composable cell renderer must touch the widget only. Discarding the list
        // afterwards rewrites the very slots the withdrawal ran from, and disposing the composition at
        // the end of the test rewrites all of them, so both fail outright if the withdrawal disturbed
        // the composition it ran inside.
        showList = false
        awaitIdle()
        onAllNodesOfType<JList<*>>().assertCountEquals(0)
    }

    @Test
    fun theRestoredRendererFollowsLaterLookAndFeelChanges() = runComposeSwingTest {
        val hostLookAndFeel = UIManager.getLookAndFeel()
        UIManager.setLookAndFeel(MetalLookAndFeel())
        try {
            var composableCells by mutableStateOf(true)
            setContent {
                ListBox(
                    items = listOf("alpha", "beta"),
                    itemContent =
                        if (composableCells) {
                            { item -> Label(item) }
                        } else {
                            null
                        },
                )
            }

            val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
            val composingRenderer: ListCellRenderer<*> = list.cellRenderer

            // A look-and-feel change updates every component's UI, which is what an application does
            // by walking its tree; a composable cell renderer is not the look and feel's, so the
            // incoming delegate keeps it and installs none of its own.
            UIManager.setLookAndFeel(NimbusLookAndFeel())
            SwingUtilities.updateComponentTreeUI(root)
            assertSame(
                composingRenderer,
                list.cellRenderer,
                "a look-and-feel change should keep the composable cell",
            )

            composableCells = false
            awaitIdle()
            assertEquals(
                "alpha",
                (list.stampCell(index = 0) as JLabel).text,
                "taking itemContent away should stamp the list's own renderer",
            )

            // What comes back is the list's own renderer, which is the look and feel's to replace:
            // the next look-and-feel change hands it the renderer of the look and feel in force,
            // exactly as on a list that never carried a composable cell.
            SwingUtilities.updateComponentTreeUI(root)
            assertEquals(
                JList<String>().cellRenderer.javaClass,
                list.cellRenderer.javaClass,
                "a look-and-feel change should give the list the renderer of the look and feel in force",
            )
        } finally {
            // The look and feel is process-wide state deciding how every later component renders, so
            // putting the host one back is what keeps every other test in this JVM measuring against
            // the look and feel it expects.
            UIManager.setLookAndFeel(hostLookAndFeel)
        }
    }

    @Test
    fun omittingItemContentKeepsTheDefaultRenderer() = runComposeSwingTest {
        setContent { ListBox(items = listOf("a", "b")) }

        val list = onNodeOfType<JList<*>>().fetch()
        // The default JList renderer is a DefaultListCellRenderer (a JLabel), NOT our composing host.
        assertNull(
            list.cellRenderer as? ComposingListCellRenderer<*>,
            "omitting itemContent must leave the JList default renderer, not install a composing renderer",
        )
        val cell = list.stampCell(index = 0)
        assertTrue(cell is JLabel, "the default renderer stamps a JLabel")
        assertEquals("a", (cell as JLabel).text, "the default renderer renders the item's toString")
    }
}
