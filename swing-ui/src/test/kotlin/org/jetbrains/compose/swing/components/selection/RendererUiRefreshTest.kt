package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.isCollected
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import org.jetbrains.compose.swing.withLookAndFeelDefault
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Color
import java.awt.Component
import java.lang.ref.WeakReference
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListCellRenderer
import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.basic.BasicListUI
import javax.swing.plaf.basic.ComboPopup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RendererUiRefreshTest {
    @Test
    fun aFailedRefreshIsRetriedBeforeWritingTheNextItem() = runComposeSwingTest {
        underMetal {
            setContent {
                ListBox(items = listOf("alpha", "beta")) { item ->
                    SwingNode(factory = { RefreshLabel() }, update = { set(item) { text = it } })
                }
            }
            val host = onNodeOfType<JList<*>>().fetch<JList<String>>()
            val renderer = host.cellRenderer
            val cell = host.renderCell(index = 0) as RefreshLabel
            val changed = ColorUIResource(Color.MAGENTA)
            withLookAndFeelDefault("Label.foreground", changed) {
                host.updateUI()
                cell.failNextRefresh = true
                assertFailsWith<IllegalStateException>("the cell should report the failed refresh") {
                    renderer.renderCell("beta", 1, list = host)
                }
                assertEquals("alpha", cell.text, "a failed refresh must not write the next item")
                assertSame(cell, renderer.renderCell("beta", 1, list = host), "a retry must reuse the composed cell")
                assertEquals(changed, cell.foreground, "the next render must retry the failed UI refresh")
                assertEquals("beta", cell.text, "a successful retry must write the next item")
            }
        }
    }

    @Test
    fun aNestedRenderDoesNotRefreshTheCellBeingComposed() = runComposeSwingTest {
        underMetal {
            lateinit var renderer: ListCellRenderer<*>
            val host = JList<String>()
            val nestedHost = JList<String>().apply { setUI(object : BasicListUI() {}) }
            var nestedCell: Component? = null
            setContent {
                renderer =
                    rememberListItemRenderer<String> { item ->
                        if (item == "beta") nestedCell = renderer.renderCell("ignored", 0, list = nestedHost)
                        SwingNode(factory = { JLabel() }, update = { set(item) { text = it } })
                    }
            }
            val cell = renderer.renderCell("alpha", 0, list = host)
            val own = ColorUIResource(Color.MAGENTA)
            cell.foreground = own
            assertSame(cell, renderer.renderCell("beta", 1, list = host), "the outer render must reuse its cell")
            assertSame(cell, nestedCell, "a nested render must return the cell being composed")
            assertEquals(own, cell.foreground, "a nested render must preserve the cell UI")
            assertEquals("beta", (cell as JLabel).text, "the outer render must keep its item")
        }
    }

    @Test
    fun aRenderedRendererAbandonedBeforeItsDisposalIsCollectable() {
        var reference: WeakReference<ListCellRenderer<*>>? = null
        runComposeSwingTest {
            assertFailsWith<IllegalStateException>("abandoning composition should report its failure") {
                setContent {
                    val renderer = rememberListItemRenderer<String> { item -> SwingNode(factory = { JLabel(item) }) }
                    renderer.renderCell("alpha", 0)
                    reference = WeakReference(renderer)
                    error("composition abandoned")
                }
            }
        }
        runComposeSwingTest {
            assertTrue(isCollected(checkNotNull(reference)), "the abandoned rendered renderer must be collectable")
        }
    }

    @TestFactory
    fun disposedRenderersDoNotRefreshTheirOldCells(): List<DynamicTest> = Widget.entries
        .filter { it != Widget.CallerList }
        .flatMap { widget ->
            listOf(false, true).map { failDisposal ->
                dynamicTest("$widget with failDisposal=$failDisposal") {
                    checkDisposedRefresh(failDisposal, widget)
                }
            }
        }

    @TestFactory
    fun cellsFollowTheirWidgetsUpdateUI(): List<DynamicTest> = Widget.entries.flatMap { widget ->
        listOf("updateUI", "replacement", "unchanged", "fresh host", "pending defaults").map { case ->
            dynamicTest("widget=$widget, case=$case") {
                checkWidgetRefresh(widget, case)
            }
        }
    }

    private fun checkWidgetRefresh(
        widget: Widget,
        case: String,
    ) = runComposeSwingTest {
        underMetal {
            var generation by mutableStateOf(0)
            var live: JLabel? = null
            val cellContent: @Composable (String) -> Unit = { item ->
                if (case == "replacement") {
                    key(generation) {
                        SwingNode(factory = { JLabel(item).also { live = it } })
                    }
                } else {
                    Label(item)
                }
            }
            setContent(refreshWidget(widget, cellContent))
            val host: JComponent =
                when (widget) {
                    Widget.List, Widget.CallerList -> onNodeOfType<JList<*>>().fetch()
                    Widget.Combo -> onNodeOfType<JComboBox<*>>().fetch()
                    Widget.Table -> onNodeOfType<JTable>().fetch()
                    Widget.Tree -> onNodeOfType<JTree>().fetch()
                }
            val previous = host.renderCellAt()
            val own = ColorUIResource(Color.CYAN)
            previous.foreground = own
            if (case == "replacement") {
                host.parent?.remove(host)
                generation++
                awaitIdle()
                assertNotSame<Component>(previous, checkNotNull(live), "a new key should replace the cell")
            }
            val changed = ColorUIResource(Color.MAGENTA)
            withLookAndFeelDefault("Label.foreground", changed) {
                if (case == "pending defaults") {
                    assertEquals(own, host.renderCellAt().foreground, "defaults alone must not refresh the cell")
                }
                if (case == "unchanged" || case == "fresh host") {
                    assertSame(previous, host.renderCellAt(case == "fresh host"), "the renderer should reuse its cell")
                    assertEquals(own, previous.foreground, "rendering without updateUI must preserve the cell's UI")
                }
                host.updateUI()
                val refreshed = host.renderCellAt()
                assertEquals(changed, refreshed.firstLabel()?.foreground, "widget updateUI must refresh its cell")
                assertEquals("alpha", refreshed.firstLabel()?.text, "refresh must preserve the rendered item")
                assertSame(
                    if (case == "replacement") checkNotNull(live) else previous,
                    refreshed,
                    "refresh must keep the current composed cell",
                )
            }
        }
    }

    private fun refreshWidget(
        widget: Widget,
        cellContent: @Composable (String) -> Unit,
    ): @Composable () -> Unit = when (widget) {
        Widget.List -> {
            {
                ListBox(items = listOf("alpha", "beta")) { cellContent(it) }
            }
        }

        Widget.CallerList -> {
            {
                val renderer = rememberListItemRenderer<String> { cellContent(it) }
                SwingNode(
                    factory = { JList(arrayOf("alpha", "beta")) },
                    modifier = SwingModifier.listItemRenderer(renderer),
                )
            }
        }

        Widget.Combo -> {
            {
                ComboBox(items = listOf("alpha", "beta"), selectedItem = "alpha", onSelectionChange = {}) {
                    cellContent(it)
                }
            }
        }

        Widget.Table -> {
            {
                Table(
                    rows = listOf("alpha", "beta"),
                    columns = { column("value", cellContent = { cellContent(it) }) { it } },
                )
            }
        }

        Widget.Tree -> {
            {
                Tree(root = "alpha", children = { emptyList<String>() }, label = { it }) { cellContent(it) }
            }
        }
    }

    private fun JComponent.renderCellAt(freshHost: Boolean = false): Component = when (this) {
        is JList<*> -> {
            cellRenderer.renderCell("alpha", 0, list = if (freshHost) JList(model) else this)
        }

        is JComboBox<*> -> {
            val popup = (accessibleContext.getAccessibleChild(0) as ComboPopup).list
            renderer.renderCell("alpha", 0, list = if (freshHost) JList(popup.model) else popup)
        }

        is JTable -> {
            getCellRenderer(0, 0).getTableCellRendererComponent(
                if (freshHost) JTable(model) else this,
                getValueAt(0, 0),
                false,
                false,
                0,
                0,
            )
        }

        is JTree -> {
            cellRenderer.getTreeCellRendererComponent(
                if (freshHost) JTree(model) else this,
                getPathForRow(0).lastPathComponent,
                false,
                false,
                true,
                0,
                false,
            )
        }

        else -> {
            error("unsupported widget: $this")
        }
    }

    private fun checkDisposedRefresh(
        failDisposal: Boolean,
        widget: Widget,
    ) = runComposeSwingTest {
        underMetal {
            var active by mutableStateOf(true)
            var renderer: ListCellRenderer<*>? = null
            val failure = IllegalStateException("disposal failed")
            setContent {
                if (active) {
                    renderer = disposalWidget(widget, if (failDisposal) failure else null)
                }
            }
            val host: JComponent
            val render: () -> Component
            when (widget) {
                Widget.List, Widget.CallerList, Widget.Combo -> {
                    if (widget == Widget.Combo) renderer = onNodeOfType<JComboBox<*>>().fetch().renderer
                    val list = JList<String>()
                    val cells = checkNotNull(renderer)
                    host = list
                    render = { cells.renderCell("alpha", 0, list = list) }
                }

                Widget.Table -> {
                    val table = onNodeOfType<JTable>().fetch()
                    val cells = table.getCellRenderer(0, 0)
                    host = table
                    render = { cells.getTableCellRendererComponent(table, "alpha", false, false, 0, 0) }
                }

                Widget.Tree -> {
                    val tree = onNodeOfType<JTree>().fetch()
                    host = tree
                    render = tree.captureRender()
                }
            }
            val cell = render() as RefreshLabel
            active = false
            if (failDisposal) {
                assertEquals(
                    failure.message,
                    assertFailsWith<IllegalStateException>("disposal should propagate the cell failure") {
                        awaitIdle()
                    }.message,
                    "the reported failure should come from cell disposal",
                )
            } else {
                awaitIdle()
            }
            val refreshes = cell.refreshes
            host.updateUI()
            assertNull(render().firstLabel(), "a disposed renderer must render no label")
            assertEquals(refreshes, cell.refreshes, "a disposed renderer must not refresh its old cell")
        }
    }

    @Composable
    private fun disposalWidget(
        widget: Widget,
        failure: IllegalStateException?,
    ): ListCellRenderer<*>? {
        when (widget) {
            Widget.List, Widget.CallerList -> {
                return rememberListItemRenderer<String> { DisposalCell(it, failure) }
            }

            Widget.Combo -> {
                ComboBox(
                    items = listOf("alpha"),
                    selectedItem = "alpha",
                    onSelectionChange = {},
                ) { DisposalCell(it, failure) }
            }

            Widget.Table -> {
                Table(
                    rows = listOf("alpha"),
                    columns = { column("value", cellContent = { DisposalCell(it, failure) }) { it } },
                )
            }

            Widget.Tree -> {
                Tree(
                    root = "alpha",
                    children = { emptyList<String>() },
                    label = { it },
                ) { DisposalCell(it, failure) }
            }
        }
        return null
    }

    private fun JTree.captureRender(): () -> Component {
        val cells = cellRenderer
        val node = getPathForRow(0).lastPathComponent
        return { cells.getTreeCellRendererComponent(this, node, false, false, true, 0, false) }
    }

    private enum class Widget { List, CallerList, Combo, Table, Tree }
}

@Composable
private fun DisposalCell(
    item: String,
    failure: IllegalStateException?,
) {
    SwingNode(factory = { RefreshLabel().apply { text = item } })
    DisposableEffect(Unit) {
        onDispose { if (failure != null) throw failure }
    }
}

private class RefreshLabel : JLabel() {
    var failNextRefresh = false
    var refreshes = 0

    override fun updateUI() {
        refreshes++
        if (failNextRefresh) {
            failNextRefresh = false
            error("refresh failed")
        }
        super.updateUI()
    }
}
