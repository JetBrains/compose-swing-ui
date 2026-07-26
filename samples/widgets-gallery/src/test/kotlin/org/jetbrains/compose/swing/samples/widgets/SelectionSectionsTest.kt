package org.jetbrains.compose.swing.samples.widgets

import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JRadioButton
import javax.swing.JTable
import javax.swing.JTree
import kotlin.test.Test

class SelectionSectionsTest {
    @Test
    fun theRadioGroupSelectsExactlyOneOption() =
        runComposeSwingTest {
            openSection("RadioGroup")

            onNodeWithText("Selected plan: Free", substring = true).assertExists()
            onNodeWithText("Selected size: Medium", substring = true).assertExists()

            onNodeWithText("Team").performClick()
            onNodeWithText("Selected plan: Team", substring = true).assertExists()
            onNodeWithText("Selected plan: Free", substring = true).assertDoesNotExist()
        }

    @Test
    fun theRadioGroupRendersOneButtonPerOption() =
        runComposeSwingTest {
            openSection("RadioGroup")

            onAllNodesOfType<JRadioButton>().assertCountEquals(VERTICAL_PLANS + HORIZONTAL_SIZES)
        }

    @Test
    fun theTableSelectionFeedsTheEcho() =
        runComposeSwingTest {
            openSection("Table")

            onNodeWithText("Selected: Ada Lovelace", substring = true).assertExists()

            val table = onNodeOfType<JTable>().fetch<JTable>()
            table.setRowSelectionInterval(2, 2)
            awaitIdle()
            onNodeWithText("Selected: Grace Hopper", substring = true).assertExists()
        }

    @Test
    fun theTableProjectsTypedRowsThroughItsColumns() =
        runComposeSwingTest {
            openSection("Table")

            val table = onNodeOfType<JTable>().fetch<JTable>()
            kotlin.test.assertEquals(TABLE_COLUMNS, table.columnCount)
            kotlin.test.assertEquals(TABLE_ROWS, table.rowCount)
            kotlin.test.assertEquals("Ada Lovelace", table.getValueAt(0, 0))
        }

    @Test
    fun theTreeSelectionResolvesBackToReadableNames() =
        runComposeSwingTest {
            openSection("Tree")

            onNodeWithText("Selected path: (none)", substring = true).assertExists()

            val tree = onNodeOfType<JTree>().fetch<JTree>()
            tree.setSelectionRow(0)
            awaitIdle()
            onNodeWithText("Selected path: Project", substring = true).assertExists()
        }

    private companion object {
        const val VERTICAL_PLANS = 4
        const val HORIZONTAL_SIZES = 3
        const val TABLE_COLUMNS = 3
        const val TABLE_ROWS = 4
    }
}
