// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The column-layout channel of [Table]: the order the columns are in and how wide they are, held by a [TableState].
 *
 * Reordering and resizing columns are both live in a Swing table by default, and new columns rebuild the
 * column model from scratch - so a layout that is not put back is destroyed by any change of the declared
 * columns or of the model. The layout a state holds survives that rebuild; the user's own layout is carried
 * across the rebuild all the same, and is written to the state.
 *
 * A reorder is a mouse drag on a header cell and a resize a mouse drag on a divider, on the header the scroll
 * pane shows.
 */
class TableColumnLayoutTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41))

    /** The width each of [this] table's view columns is currently at, left to right. */
    private fun JTable.widths(): List<Int> = (0 until columnModel.columnCount).map { columnModel.getColumn(it).width }

    @Test
    fun aLayoutNeedsOneWidthPerColumn() {
        assertFailsWith<IllegalArgumentException>("the message should say what the layout is missing") {
            TableColumnLayout(modelIndices = listOf(0, 1), preferredWidths = listOf(120))
        }
    }

    /**
     * A layout is the two lists it names and nothing else: two layouts putting the same columns in the same
     * order at the same widths are the same layout. The library compares layouts to tell a real change from
     * a pass that changed nothing, and hands the caller only a layout it has not agreed on already.
     */
    @Test
    fun layoutsNamingTheSameColumnsAtTheSameWidthsAreEqual() {
        val layout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40))
        val same = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40))
        val reordered = TableColumnLayout(modelIndices = listOf(0, 1), preferredWidths = listOf(120, 40))
        val resized = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 41))

        assertEquals(same, layout, "the same columns at the same widths are the same layout")
        assertTrue(layout != reordered, "columns in another order are another layout")
        assertTrue(layout != resized, "columns at another width are another layout")
        assertTrue(layout != Any(), "only a layout is a layout")
        assertEquals(same.hashCode(), layout.hashCode(), "equal layouts must hash alike")
        // Layouts differing in either list hash apart. That is more than the hashCode contract asks - a
        // constant would satisfy it - and it is what makes the equality above observable in a hash-based
        // collection: both lists reach the hash, so neither the order nor the widths are dropped from it.
        // The values are fixed, and a list's hash is specified, so the three hashes are the same on every
        // JVM.
        assertTrue(
            layout.hashCode() != reordered.hashCode(),
            "columns in another order should hash apart, but both hashed ${layout.hashCode()}",
        )
        assertTrue(
            layout.hashCode() != resized.hashCode(),
            "columns at another width should hash apart, but both hashed ${layout.hashCode()}",
        )
        assertTrue(
            layout.toString().contains("modelIndices=[1, 0]") &&
                layout.toString().contains("preferredWidths=[120, 40]"),
            "a layout should describe itself by the columns and the widths it names, but was: $layout",
        )
    }

    @Test
    fun reorderingColumnsWritesTheLayoutTheyAreInToTheState() = runComposeSwingTest {
        val state = TableState()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    state = state,
                )
            }
        }

        dragColumn(from = 1, to = 0)
        awaitIdle()

        assertEquals(
            listOf(1, 0),
            state.columnLayout?.modelIndices,
            "the reordered columns should be written to the state",
        )
    }

    @Test
    fun resizingAColumnWritesTheLayoutItIsInToTheState() = runComposeSwingTest {
        val state = TableState()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    state = state,
                    autoResizeMode = JTable.AUTO_RESIZE_OFF,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        dragColumnDivider(position = 0, dx = 40)
        awaitIdle()

        val layout = state.columnLayout
        assertTrue(layout != null, "a resize should be written to the state")
        assertEquals(
            table.columnModel.getColumn(0).preferredWidth,
            layout.preferredWidths[0],
            "the resized column's preferred width should be written to the state",
        )
        assertEquals(listOf(0, 1), layout.modelIndices, "a resize should leave the order alone")
    }

    @Test
    fun aStatesLayoutSurvivesAColumnStructureChangeSilently() = runComposeSwingTest {
        var withCity by mutableStateOf(false)
        val layout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40))
        val state = TableState(initialColumnLayout = layout)
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                        if (withCity) column("City") { "Cambridge" }
                    },
                    state = state,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        withCity = true
        awaitIdle()

        assertEquals(3, table.columnModel.columnCount, "the new column should render")
        assertEquals(listOf(1, 0, 2), table.columnModel.modelIndices(), "the state's order should survive the rebuild")
        assertEquals(
            120,
            table.columnModel.getColumn(0).preferredWidth,
            "the state's width should survive the rebuild",
        )
        assertEquals(layout, state.columnLayout, "putting the state's layout back should write nothing into it")
    }

    @Test
    fun aUsersLayoutSurvivesAColumnStructureChange() = runComposeSwingTest {
        var withCity by mutableStateOf(false)
        val state = TableState()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                        if (withCity) column("City") { "Cambridge" }
                    },
                    state = state,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        dragColumn(from = 1, to = 0)
        awaitIdle()
        val before = state.columnLayout

        withCity = true
        awaitIdle()

        assertEquals(listOf(1, 0, 2), table.columnModel.modelIndices(), "the user's order should survive the rebuild")
        assertEquals(before, state.columnLayout, "a layout the rebuild could hold should write nothing to the state")
    }

    @Test
    fun aStructureChangeThatDropsAColumnKeepsTheSurvivingColumn() = runComposeSwingTest {
        var withAge by mutableStateOf(true)
        val state = TableState()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        if (withAge) column("Age") { it.age }
                    },
                    state = state,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        dragColumn(from = 1, to = 0)
        awaitIdle()
        assertEquals(listOf(1, 0), state.columnLayout?.modelIndices, "the drag should have written its layout")

        withAge = false
        awaitIdle()

        assertEquals(listOf(0), table.columnModel.modelIndices(), "only the surviving column should be left")
        assertEquals(
            listOf("Name"),
            (0 until table.columnModel.columnCount).map { table.columnModel.getColumn(it).headerValue },
            "the surviving column should be the one left in view",
        )
    }

    @Test
    fun aRowsOnlyRefreshLeavesTheColumnLayoutAndTheStateAlone() = runComposeSwingTest {
        var rows by mutableStateOf(people)
        val state = TableState()
        setContent {
            HeaderPane {
                Table(
                    rows = rows,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    state = state,
                    autoResizeMode = JTable.AUTO_RESIZE_OFF,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        dragColumn(from = 1, to = 0)
        dragColumnDivider(position = 0, dx = 40)
        awaitIdle()
        val order = table.columnModel.modelIndices()
        val widths = table.columnModel.preferredWidths()
        val before = state.columnLayout

        rows = people + Person("Grace", 50)
        awaitIdle()

        onNodeOfType<JTable>().assertProperty(
            order,
            "a rows-only refresh should keep the order",
        ) { columnModel.modelIndices() }
        onNodeOfType<JTable>().assertProperty(
            widths,
            "a rows-only refresh should keep the widths",
        ) { columnModel.preferredWidths() }
        assertEquals(before, state.columnLayout, "a rows-only refresh should write no column change")
    }

    @Test
    fun aTableResizeThatOnlyRedistributesWidthsIsSilent() = runComposeSwingTest {
        val state = TableState()
        setContent {
            HeaderPane {
                Table(
                    rows = people,
                    columns = {
                        column("Name") { it.name }
                        column("Age") { it.age }
                    },
                    state = state,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val before = state.columnLayout
        // A table spreads its width across its columns at every layout pass, which changes each column's
        // width without touching the layout the columns are in.
        table.setSize(table.width * 2, table.height)
        table.doLayout()
        awaitIdle()

        assertEquals(before, state.columnLayout, "redistributing widths across a wider table is no column change")
    }
}
