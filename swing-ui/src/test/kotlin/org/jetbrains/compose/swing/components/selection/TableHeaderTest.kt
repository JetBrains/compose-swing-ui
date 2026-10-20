package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.style.TableHeaders
import org.jetbrains.compose.swing.failureOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performMouseDrag
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.event.MouseEvent
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import javax.swing.table.JTableHeader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The header a [Table] builds itself, configured through `Header` in the table's content: its modifier
 * and its two settings reach the table's own `JTableHeader`, and leaving gives the header its earlier
 * values back.
 *
 * A header drag is delivered to the header the scroll pane shows, laid out under the pane's declared size,
 * so the header's own UI resolves it as it resolves a user's.
 */
class TableHeaderTest {
    @Test
    fun theHeaderIsTheRowsTablesOwn() = runComposeSwingTest {
        setContent {
            Table(rows = listOf("Ada"), columns = { column("Name") { it } }) {
                Header(style = { background(Color.RED) })
            }
        }

        onNodeOfType<JTable>().assertProperty(
            Color.RED,
            "the modifier should reach the table's own header",
        ) { tableHeader.background }
    }

    @Test
    fun theHeaderReportsAnEventToTheListenerDeclaredOnItOnce() = runComposeSwingTest {
        var presses = 0
        setContent {
            Table(rows = listOf("Ada"), columns = { column("Name") { it } }) {
                Header(modifier = SwingModifier.mouseListener { if (it.id == MouseEvent.MOUSE_PRESSED) presses++ })
            }
        }

        val header = onNodeOfType<JTable>().fetch().tableHeader
        header.dispatchEvent(MouseEvent(header, MouseEvent.MOUSE_PRESSED, 0L, 0, 1, 1, 1, false))

        assertEquals(1, presses)
    }

    @Test
    fun theHeaderIsTheModelTablesOwn() = runComposeSwingTest {
        setContent {
            Table(model = DefaultTableModel(arrayOf(arrayOf<Any>("Ada")), arrayOf<Any>("Name"))) {
                Header(style = { background(Color.RED) })
            }
        }

        onNodeOfType<JTable>().assertProperty(
            Color.RED,
            "the modifier should reach the table's own header",
        ) { tableHeader.background }
    }

    @Test
    fun theDeclaredHeaderIsTheOneTheScrollPaneShows() = runComposeSwingTest {
        setContent {
            ScrollPane {
                Viewport {
                    Table(rows = listOf("Ada"), columns = { column("Name") { it } }) {
                        Header(style = { background(Color.RED) })
                    }
                }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val pane = onNodeOfType<JScrollPane>().fetch()
        assertSame(table.tableHeader, pane.columnHeader?.view, "the pane shows the table's own header")
        onNodeOfType<JTable>().assertProperty(
            Color.RED,
            "the modifier should reach the header it shows",
        ) { tableHeader.background }
    }

    @Test
    fun reorderingAllowedDecidesWhetherAColumnDragReordersTheColumns() = runComposeSwingTest {
        var reorderingAllowed by mutableStateOf(false)
        setContent { HeaderedTable(reorderingAllowed = reorderingAllowed) }

        val table = onNodeOfType<JTable>().fetch()
        val header = table.tableHeader
        assertFalse(header.reorderingAllowed, "the declared setting should reach the header")

        onNodeOfType<JTableHeader>().performMouseDrag(table.headerCenterOf(0), table.headerCenterOf(1))
        onNodeOfType<JTable>().assertProperty(
            listOf("Name", "Role"),
            "a drag should leave the columns where they are",
        ) { columnHeaders() }

        reorderingAllowed = true
        awaitIdle()
        assertSame(header, table.tableHeader, "the setting applies to the same header")
        assertTrue(header.reorderingAllowed, "the declared setting should reach the header")

        onNodeOfType<JTableHeader>().performMouseDrag(table.headerCenterOf(0), table.headerCenterOf(1))
        onNodeOfType<JTable>().assertProperty(
            listOf("Role", "Name"),
            "a drag should reorder the columns",
        ) { columnHeaders() }
    }

    @Test
    fun resizingAllowedDecidesWhetherADividerDragResizesAColumn() = runComposeSwingTest {
        var resizingAllowed by mutableStateOf(false)
        // Reordering is off as well: a header that does not resize takes a drag at a divider as a reorder.
        setContent { HeaderedTable(reorderingAllowed = false, resizingAllowed = resizingAllowed) }

        val table = onNodeOfType<JTable>().fetch()
        val header = table.tableHeader
        val column = table.columnModel.getColumn(0)
        val width = column.width
        assertFalse(header.resizingAllowed, "the declared setting should reach the header")

        onNodeOfType<JTableHeader>().performMouseDrag(table.dividerAfter(0), table.dividerAfter(0).movedBy(40))
        assertEquals(width, column.width, "a divider drag should leave the column as wide as it was")

        resizingAllowed = true
        awaitIdle()
        assertSame(header, table.tableHeader, "the setting applies to the same header")
        assertTrue(header.resizingAllowed, "the declared setting should reach the header")

        onNodeOfType<JTableHeader>().performMouseDrag(table.dividerAfter(0), table.dividerAfter(0).movedBy(40))
        assertEquals(width + 40, column.width, "a divider drag should resize the column")
    }

    @Test
    fun aHeaderWhoseDeclarationLeavesGetsItsEarlierValuesBack() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            Table(rows = listOf("Ada"), columns = { column("Name") { it } }) {
                if (declared) {
                    Header(
                        style = { background(Color.RED) },
                        reorderingAllowed = false,
                        resizingAllowed = false,
                    )
                }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val header = table.tableHeader
        val raw = JTable().tableHeader
        assertEquals(Color.RED, header.background, "initially: background")
        assertFalse(header.reorderingAllowed, "initially: reordering")
        assertFalse(header.resizingAllowed, "initially: resizing")

        declared = false
        awaitIdle()

        assertSame(header, table.tableHeader, "the header stays the table's own")
        assertEquals(raw.background, header.background, "the header gets the look and feel's background back")
        assertTrue(header.reorderingAllowed, "the header gets its reordering back")
        assertTrue(header.resizingAllowed, "the header gets its resizing back")
    }

    @Test
    fun switchingBetweenHeaderHelpersKeepsTheHeaderAndRestoresItsValuesOnRemoval() = runComposeSwingTest {
        var alternate by mutableStateOf(false)
        var declared by mutableStateOf(true)
        setContent {
            Table(rows = listOf("Ada"), columns = { column("Name") { it } }) {
                if (declared) {
                    if (alternate) BlueHeader() else RedHeader()
                }
            }
        }
        val table = onNodeOfType<JTable>().fetch()
        val header = table.tableHeader
        val raw = JTable().tableHeader
        assertEquals(Color.RED, header.background, "initially: background")
        assertFalse(header.reorderingAllowed, "initially: reordering")
        assertFalse(header.resizingAllowed, "initially: resizing")

        alternate = true
        awaitIdle()
        assertSame(header, table.tableHeader, "after switching to the blue helper: same header")
        assertEquals(Color.BLUE, header.background, "after switching to the blue helper: background")
        assertTrue(header.reorderingAllowed, "after switching to the blue helper: reordering")
        assertTrue(header.resizingAllowed, "after switching to the blue helper: resizing")

        alternate = false
        awaitIdle()
        assertSame(header, table.tableHeader, "after switching back to the red helper: same header")
        assertEquals(Color.RED, header.background, "after switching back to the red helper: background")
        assertFalse(header.reorderingAllowed, "after switching back to the red helper: reordering")
        assertFalse(header.resizingAllowed, "after switching back to the red helper: resizing")

        declared = false
        awaitIdle()
        assertSame(header, table.tableHeader, "after the header is removed: same header")
        assertEquals(raw.background, header.background, "after the header is removed: background")
        assertEquals(raw.reorderingAllowed, header.reorderingAllowed, "after the header is removed: reordering")
        assertEquals(raw.resizingAllowed, header.resizingAllowed, "after the header is removed: resizing")

        declared = true
        awaitIdle()
        assertSame(header, table.tableHeader, "after the header is declared again: same header")
        assertEquals(Color.RED, header.background, "after the header is declared again: background")
        assertFalse(header.reorderingAllowed, "after the header is declared again: reordering")
        assertFalse(header.resizingAllowed, "after the header is declared again: resizing")
    }

    @Test
    fun aParkedHeaderRestoresItsValuesAndReactivationAppliesTheNewDeclaration() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var alternate by mutableStateOf(false)
        setContent {
            Table(rows = listOf("Ada"), columns = { column("Name") { it } }) {
                ReusableContentHost(active = active) {
                    if (alternate) BlueHeader() else RedHeader()
                }
            }
        }
        val table = onNodeOfType<JTable>().fetch()
        val header = table.tableHeader
        val raw = JTable().tableHeader
        assertEquals(Color.RED, header.background, "initially: background")
        assertFalse(header.reorderingAllowed, "initially: reordering")
        assertFalse(header.resizingAllowed, "initially: resizing")

        active = false
        awaitIdle()
        assertSame(header, table.tableHeader, "after parking: same header")
        assertEquals(raw.background, header.background, "after parking: background")
        assertEquals(raw.reorderingAllowed, header.reorderingAllowed, "after parking: reordering")
        assertEquals(raw.resizingAllowed, header.resizingAllowed, "after parking: resizing")

        alternate = true
        active = true
        awaitIdle()
        assertSame(header, table.tableHeader, "after reactivating with the blue helper: same header")
        assertEquals(Color.BLUE, header.background, "after reactivating with the blue helper: background")
        assertTrue(header.reorderingAllowed, "after reactivating with the blue helper: reordering")
        assertTrue(header.resizingAllowed, "after reactivating with the blue helper: resizing")

        active = false
        awaitIdle()
        assertEquals(raw.background, header.background, "after parking again: background")
        alternate = false
        active = true
        awaitIdle()
        assertSame(header, table.tableHeader, "after reactivating with the red helper: same header")
        assertEquals(Color.RED, header.background, "after reactivating with the red helper: background")
        assertFalse(header.reorderingAllowed, "after reactivating with the red helper: reordering")
        assertFalse(header.resizingAllowed, "after reactivating with the red helper: resizing")
    }

    @Test
    fun twoHeadersAreRefusedAsTwoClaimsOfOneHeader() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Table(rows = listOf("Ada"), columns = { column("Name") { it } }) {
                        RedHeader()
                        BlueHeader()
                    }
                }
                awaitIdle()
            }

        assertTrue(
            "Header() is declared twice at once in one JTable" in message,
            "the refusal should say the header is declared twice: $message",
        )
    }

    @Test
    fun movingOneTablesHeaderDeclarationLeavesAnotherTablesDeclarationAlone() = runComposeSwingTest {
        var alternate by mutableStateOf(false)
        setContent {
            Table(rows = listOf("Ada"), columns = { column("Name") { it } }) {
                if (alternate) BlueHeader() else RedHeader()
            }
            Table(rows = listOf("Grace"), columns = { column("Name") { it } }) { RedHeader() }
        }
        val tables = onAllNodesOfType<JTable>().fetchAll()
        val first = tables.first { it.getValueAt(0, 0) == "Ada" }.tableHeader
        val second = tables.first { it.getValueAt(0, 0) == "Grace" }.tableHeader

        alternate = true
        awaitIdle()
        assertEquals(Color.BLUE, first.background, "the moved header should take the new declaration's background")
        assertTrue(first.reorderingAllowed, "the moved header should take the new declaration's reordering")
        assertEquals(Color.RED, second.background, "the other table's header should keep its background")
        assertFalse(second.reorderingAllowed, "the other table's header should keep its reordering")
        assertFalse(second.resizingAllowed, "the other table's header should keep its resizing")

        alternate = false
        awaitIdle()
        assertEquals(Color.RED, first.background, "the header moved back should take its first declaration again")
        assertEquals(Color.RED, second.background, "and the other table's header should still be unchanged")
    }

    @Test
    fun aMovedHeaderAppliesItsCurrentStyleAndKeepsTheProvidedStyleBelowIt() = runComposeSwingTest {
        var alternate by mutableStateOf(false)
        var background by mutableStateOf(Color.RED)
        var inherited by mutableStateOf(Color.GREEN)
        var explicit by mutableStateOf(true)
        setContent {
            Panel(style = { provideNestedStyle(TableHeaders) { background(inherited) } }) {
                Table(rows = listOf("Ada"), columns = { column("Name") { it } }) {
                    val style =
                        if (explicit) SwingStyle<SwingComponentStyleScope> { background(background) } else SwingStyle
                    if (alternate) BlueHeader(style) else RedHeader(style)
                }
            }
        }
        val header = onNodeOfType<JTable>().fetch().tableHeader
        assertEquals(Color.RED, header.background, "the header should start with its first declaration")

        alternate = true
        background = Color.BLUE
        inherited = Color.YELLOW
        awaitIdle()
        assertSame(header, onNodeOfType<JTable>().fetch().tableHeader, "the moved declaration should keep the header")
        assertEquals(Color.BLUE, header.background, "the moved declaration should apply the style declared now")

        background = Color.MAGENTA
        awaitIdle()
        assertEquals(Color.MAGENTA, header.background, "a later style change should reach the moved header")

        explicit = false
        awaitIdle()
        assertEquals(Color.YELLOW, header.background, "withdrawal should expose the provided style")
        inherited = Color.CYAN
        awaitIdle()
        assertEquals(Color.CYAN, header.background, "the provided style should keep updating")
        alternate = false
        inherited = Color.GREEN
        awaitIdle()
        assertEquals(Color.GREEN, header.background, "moving back should read the provided style again")
    }

    @Test
    fun aChildThatIsNotAPartIsRefused() = runComposeSwingTest {
        assertFailsWith<IllegalStateException>("the refusal should name the table and the call") {
            setContent {
                Table(rows = listOf("Ada"), columns = { column("Name") { it } }) {
                    Label(text = "loose")
                }
            }
        }
    }
}

/** A two-column table the scroll pane lays out at a fixed size, with its header declared. */
@Composable
private fun HeaderedTable(
    reorderingAllowed: Boolean = true,
    resizingAllowed: Boolean = true,
) {
    HeaderPane {
        Table(
            rows = listOf("Ada"),
            columns = {
                column("Name") { it }
                column("Role") { it }
            },
            autoResizeMode = JTable.AUTO_RESIZE_OFF,
        ) {
            Header(reorderingAllowed = reorderingAllowed, resizingAllowed = resizingAllowed)
        }
    }
}

@Composable
private fun TableContentScope.RedHeader(
    style: SwingStyle<SwingComponentStyleScope> = SwingStyle { background(Color.RED) },
) {
    Header(style = style, reorderingAllowed = false, resizingAllowed = false)
}

@Composable
private fun TableContentScope.BlueHeader(
    style: SwingStyle<SwingComponentStyleScope> = SwingStyle { background(Color.BLUE) },
) {
    Header(style = style, reorderingAllowed = true, resizingAllowed = true)
}
