package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a [ListBox] lays its cells out and how large it draws them: a single column or cells wrapped into
 * columns or rows, and the width and height each cell is given.
 *
 * A list sizes itself by asking its renderer to measure a row, once per row. A prototype item collapses
 * that to one measurement every cell is then sized by, and a width or height in pixels states a size
 * outright; with a composable cell each avoided measurement is a nested composition not stamped. Each is
 * declared state and is applied whenever the declaration moves.
 */
class ListBoxSizingTest {
    private val items = listOf("alpha", "beta", "gamma")

    /** An item wide enough that a cell sized by it is plainly not a cell sized by the rows. */
    private val prototype = "a prototype item wider than every row"

    @Test
    fun undeclaredLayoutAndSizingLeaveTheListsOwnDefaults() = runComposeSwingTest {
        setContent { ListBox(items = items) }

        val list = onNodeOfType<JList<*>>().fetch()
        val own = JList<String>()
        assertEquals(own.layoutOrientation, list.layoutOrientation, "the layout should be a JList's own")
        assertEquals(own.prototypeCellValue, list.prototypeCellValue, "the prototype should be a JList's own")
        assertEquals(own.fixedCellWidth, list.fixedCellWidth, "the fixed cell width should be a JList's own")
        assertEquals(own.fixedCellHeight, list.fixedCellHeight, "the fixed cell height should be a JList's own")
    }

    @Test
    fun aDeclaredLayoutOrientationIsAppliedAndUpdatedInPlace() = runComposeSwingTest {
        var layoutOrientation by mutableStateOf(JList.VERTICAL_WRAP)
        setContent { ListBox(items = items, layoutOrientation = layoutOrientation) }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(JList.VERTICAL_WRAP, list.layoutOrientation, "the declared layout should reach the list")

        layoutOrientation = JList.HORIZONTAL_WRAP
        awaitIdle()
        assertEquals(JList.HORIZONTAL_WRAP, list.layoutOrientation, "a later layout should update the list in place")
    }

    @Test
    fun aDeclaredFixedSizeIsAppliedAndUpdatedInPlace() = runComposeSwingTest {
        var fixedCellWidth by mutableStateOf(120)
        var fixedCellHeight by mutableStateOf(24)
        setContent {
            ListBox(items = items, fixedCellWidth = fixedCellWidth, fixedCellHeight = fixedCellHeight)
        }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(120, list.fixedCellWidth, "the declared cell width should reach the list")
        assertEquals(24, list.fixedCellHeight, "the declared cell height too")

        fixedCellWidth = 200
        fixedCellHeight = 40
        awaitIdle()
        assertEquals(200, list.fixedCellWidth, "a later cell width should update the list in place")
        assertEquals(40, list.fixedCellHeight, "and a later cell height")
    }

    @Test
    fun aPrototypeSizesEveryCellFromOneMeasurementOfTheComposableCell() = runComposeSwingTest {
        setContent {
            ListBox(items = items, prototypeCellValue = prototype) { item ->
                FlowPanel { Label(item) }
            }
        }

        val list = onNodeOfType<JList<String>>().fetch()
        val measured = list.measurePrototypeCell()
        assertTrue(measured.width > 0, "the prototype's composable cell should measure to something")
        assertEquals(measured.width, list.fixedCellWidth, "every cell should be as wide as the prototype's cell")
        assertEquals(measured.height, list.fixedCellHeight, "and as tall as it")
    }

    @Test
    fun aDeclaredWidthStandsOverThePrototypesMeasurement() = runComposeSwingTest {
        setContent {
            ListBox(items = items, prototypeCellValue = prototype, fixedCellWidth = 300) { item ->
                FlowPanel { Label(item) }
            }
        }

        val list = onNodeOfType<JList<String>>().fetch()
        assertEquals(300, list.fixedCellWidth, "the width stated in pixels should stand")
        assertEquals(
            list.measurePrototypeCell().height,
            list.fixedCellHeight,
            "the height, stated nowhere, should still come from the prototype",
        )
    }

    @Test
    fun withdrawingADeclaredWidthGivesBackThePrototypesMeasurement() = runComposeSwingTest {
        var fixedCellWidth by mutableStateOf(300)
        setContent {
            ListBox(items = items, prototypeCellValue = prototype, fixedCellWidth = fixedCellWidth) { item ->
                FlowPanel { Label(item) }
            }
        }

        val list = onNodeOfType<JList<String>>().fetch()
        assertEquals(300, list.fixedCellWidth, "the width stated in pixels should stand")

        fixedCellWidth = -1
        awaitIdle()
        assertEquals(
            list.measurePrototypeCell().width,
            list.fixedCellWidth,
            "withdrawing the stated width should put the cells back on the prototype's measurement",
        )
    }

    @Test
    fun withdrawingThePrototypeGivesEveryRowItsOwnMeasurementBack() = runComposeSwingTest {
        var prototypeCellValue: String? by mutableStateOf(prototype)
        setContent {
            ListBox(items = items, prototypeCellValue = prototypeCellValue) { item ->
                FlowPanel { Label(item) }
            }
        }

        val list = onNodeOfType<JList<String>>().fetch()
        assertTrue(list.fixedCellWidth > 0, "the prototype should have fixed a cell width")

        prototypeCellValue = null
        awaitIdle()
        assertEquals(
            JList<String>().fixedCellWidth,
            list.fixedCellWidth,
            "withdrawing the prototype should leave the list measuring each row for itself",
        )
    }

    /**
     * The size this list's renderer reports for a cell rendering [prototype], measured the way the list
     * measures a prototype: through whichever renderer is installed, at row 0, unselected and unfocused.
     */
    private fun JList<String>.measurePrototypeCell() =
        cellRenderer.stampCell(value = prototype, index = 0, list = this).preferredSize
}
