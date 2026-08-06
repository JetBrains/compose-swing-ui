package org.jetbrains.compose.swing.samples.widgets.selection

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.JTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectionSectionsTest {
    @Test
    fun theTableSelectionFeedsTheEcho() =
        runComposeSwingTest {
            openSection("Table")

            onNodeWithText("Selected: Ada Lovelace", substring = true).assertExists()

            val table = onNodeWithTag(PRIMARY_TABLE_TAG).fetch<JTable>()
            table.setRowSelectionInterval(2, 2)
            awaitIdle()
            onNodeWithText("Selected: Grace Hopper", substring = true).assertExists()
        }

    @Test
    fun theTableProjectsTypedRowsThroughItsColumns() =
        runComposeSwingTest {
            openSection("Table")

            val table = onNodeWithTag(PRIMARY_TABLE_TAG).fetch<JTable>()
            assertEquals(TABLE_COLUMNS, table.columnCount)
            assertEquals(TABLE_ROWS, table.rowCount)
            assertEquals("Ada Lovelace", table.getValueAt(0, 0))
        }

    @Test
    fun theTableSortsByAHeaderClickAndTheFilterHidesNonMatchingRows() =
        runComposeSwingTest {
            openSection("Table")

            onNodeWithText("Sort: unsorted", substring = true).assertExists()

            val sortableTable = onNodeWithTag(SORT_FILTER_TABLE_TAG).fetch<JTable>()
            sortableTable.rowSorter?.toggleSortOrder(0)
            awaitIdle()
            onNodeWithText("Sort: Title ASCENDING", substring = true).assertExists()

            onNodeWithTag(SORT_FILTER_TEXT_TAG).fetch<JTextField>().text = "bloch"
            awaitIdle()
            assertEquals(1, sortableTable.rowCount)
        }

    @Test
    fun theTreeSelectionResolvesBackToReadableNames() =
        runComposeSwingTest {
            openSection("Tree")

            onNodeWithText("Selected path: (none)", substring = true).assertExists()

            val tree = onNodeWithTag(PRIMARY_TREE_TAG).fetch<JTree>()
            tree.setSelectionRow(0)
            awaitIdle()
            onNodeWithText("Selected path: Project", substring = true).assertExists()
        }

    @Test
    fun expandAllOpensEveryNodeAndCollapseAllClosesThem() =
        runComposeSwingTest {
            openSection("Tree")

            val expandedTree = onNodeWithTag(EXPANSION_TREE_TAG).fetch<JTree>()
            val rowsWithOnlyTheRootOpen = expandedTree.rowCount

            onNodeWithText("Expand all").performClick()
            awaitIdle()
            assertTrue(
                expandedTree.rowCount > rowsWithOnlyTheRootOpen,
                "expanding every node should show more rows than just the root's own children",
            )

            onNodeWithText("Collapse all").performClick()
            awaitIdle()
            assertEquals(1, expandedTree.rowCount, "collapsing every node leaves only the root row visible")
        }

    private companion object {
        const val TABLE_COLUMNS = 3
        const val TABLE_ROWS = 4
    }
}
