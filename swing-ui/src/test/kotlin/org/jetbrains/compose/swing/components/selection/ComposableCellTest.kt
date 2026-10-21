package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Component
import javax.swing.DefaultListModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
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
 * Composable cell behavior shared by lists, combo boxes, tables, and trees.
 * Cells render through the widget's installed renderer and reuse one component tree per cell body.
 */
class ComposableCellTest {
    @Test
    fun recomposedRowContentUpdatesTheCell() = runComposeSwingTest {
        var items by mutableStateOf(listOf("draft"))
        setContent {
            ListBox(items = items) { item ->
                Label(item)
            }
        }

        val list = onNodeOfType<JList<String>>().fetch()
        assertEquals("draft", list.renderCell(index = 0).firstLabelText(), "the initial item should render")

        // Changing the backing items recomposes the list and the cell rerenders the new value for that row.
        items = listOf("final")
        awaitIdle()
        assertEquals(
            "final",
            list.renderCell(index = 0).firstLabelText(),
            "changing the item should rerender the cell with the new content",
        )
    }

    @Test
    fun cellScopeReflectsSelectionForTheRenderedRow() = runComposeSwingTest {
        setContent {
            ListBox(items = listOf("x", "y")) { item ->
                Label(if (isSelected) "$item*" else item)
            }
        }

        val list = onNodeOfType<JList<String>>().fetch()
        assertEquals(
            "y",
            list.renderCell(index = 1, isSelected = false).firstLabelText(),
            "an unselected render should render the plain item",
        )
        assertEquals(
            "y*",
            list.renderCell(index = 1, isSelected = true).firstLabelText(),
            "a selected render should observe isSelected through the ListItemScope",
        )
    }

    @Test
    fun composableCellsWorkInsideAScrollPane() = runComposeSwingTest {
        // A cell composition joins the enclosing composition, and the cell's own nodes belong to the
        // renderer rather than to the pane the list is installed in: they render the row, they do not
        // install themselves as the viewport's view. Selecting a row synchronously renders a cell during
        // the enclosing composition's apply pass, which is exactly when such leakage surfaces.
        setContent {
            ScrollPane {
                Viewport {
                    ListBox(
                        items = listOf("first", "second"),
                        selectedIndices = setOf(0),
                    ) { item ->
                        Panel { Label(item) }
                    }
                }
            }
        }

        val list = onNodeOfType<JList<String>>().fetch()
        assertEquals(
            "second",
            list.renderCell(index = 1).firstLabelText(),
            "a composable cell inside a ScrollPane should realize its row content, not leak the viewport slot",
        )
    }

    @TestFactory
    fun itemContentTakenAwayRestoresTheListsOwnRenderer(): List<DynamicTest> = listOf(false, true).map { underModel ->
        dynamicTest(if (underModel) "under a model" else "under items") {
            runComposeSwingTest {
                val model = DefaultListModel<String>().apply { addAll(listOf("alpha", "beta")) }
                var composableCells by mutableStateOf(true)
                setContent {
                    val itemContent: (@Composable ListItemScope.(String) -> Unit)? =
                        if (composableCells) {
                            { item -> Panel { Label("<$item>") } }
                        } else {
                            null
                        }
                    if (underModel) {
                        ListBox(model = model, itemContent = itemContent)
                    } else {
                        ListBox(items = listOf("alpha", "beta"), itemContent = itemContent)
                    }
                }

                val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
                val composedCell = list.renderCell(index = 0)
                assertFalse(composedCell is JLabel, "a composable cell renders what it composed, not a JLabel")
                assertEquals("<alpha>", composedCell.firstLabelText(), "the composable cell should render row 0")

                composableCells = false
                awaitIdle()
                assertNull(
                    list.cellRenderer as? ComposingListCellRenderer<*>,
                    "taking itemContent away must leave the list's own renderer, not a composing one",
                )
                val defaultCell = list.renderCell(index = 0)
                assertTrue(defaultCell is JLabel, "the restored renderer renders a JLabel")
                assertEquals("alpha", (defaultCell as JLabel).text, "the restored renderer renders toString")

                composableCells = true
                awaitIdle()
                assertEquals(
                    "<beta>",
                    list.renderCell(index = 1).firstLabelText(),
                    "declaring itemContent again should render the composable cell",
                )
            }
        }
    }

    @TestFactory
    fun cellsRenderTheirItems(): List<DynamicTest> = renderedCellCases.map { case ->
        dynamicTest("${case.widget}: ${case.name}") {
            runComposeSwingTest {
                setContent { case.content() }
                val widget = cellWidget(case.widget)
                case.checks.forEach { check ->
                    assertEquals(
                        check.text,
                        widget.renderCellAt(check.index, case.column).firstLabelText(),
                        check.message,
                    )
                }
            }
        }
    }

    @TestFactory
    fun capturedRenderersAreSafeAfterRemoval(): List<DynamicTest> = listOf(
        DisposedCellCase(
            widget = CellWidget.List,
            content = { visible ->
                if (visible) {
                    ListBox(items = listOf("alpha", "beta")) { item ->
                        Label(item)
                    }
                }
            },
            first = "alpha",
            second = "beta",
            staleMessage = "a render on a disposed cell composition must render an empty cell rather than a stale row",
            restoredMessage = "the list itself renders through its own renderer again once the composable cell is gone",
        ),
        DisposedCellCase(
            widget = CellWidget.Combo,
            content = { visible ->
                if (visible) {
                    ComboBox(items = listOf("red", "green"), selectedItem = "red", onSelectionChange = {}) { item ->
                        Label(item)
                    }
                }
            },
            first = "red",
            second = "green",
            staleMessage = "a render on a disposed cell composition must render an empty cell rather than a stale item",
            restoredMessage =
                "the combo box itself renders through its own renderer again once " +
                    "the composable cell is gone",
        ),
    ).map { case ->
        dynamicTest(case.widget.name) {
            runComposeSwingTest {
                var visible by mutableStateOf(true)
                setContent { case.content(visible) }
                val widget = cellWidget(case.widget)
                // A popup can retain the renderer after the owning composition is disposed.
                val render = widget.captureRenderer()
                val before = render(case.first, 0)
                visible = false
                awaitIdle()
                val after = render(case.second, 1)
                assertNotSame(before, after, "a disposed cell composition must give up the component it composed")
                assertNull(after.firstLabelText(), case.staleMessage)
                assertTrue(widget.renderCellAt(1) is JLabel, case.restoredMessage)
            }
        }
    }

    @TestFactory
    fun withdrawingCellsLeavesTheCompositionIntact(): List<DynamicTest> = listOf(
        WithdrawalCase(CellWidget.List) { visible, enabled ->
            if (visible) {
                ListBox(
                    items = listOf("alpha", "beta"),
                    selectedIndices = setOf(0),
                    itemContent =
                        if (enabled) {
                            { item -> Label(item) }
                        } else {
                            null
                        },
                )
            }
        },
        WithdrawalCase(CellWidget.Table) { visible, enabled ->
            if (visible) {
                Table(
                    rows = people,
                    columns = {
                        column(
                            header = "Name",
                            cellContent =
                                if (enabled) {
                                    { row -> Label(row.name) }
                                } else {
                                    null
                                },
                        ) { it.name }
                    },
                    selectedRowIndices = setOf(0),
                )
            }
        },
    ).map { case ->
        dynamicTest(case.widget.name) {
            runComposeSwingTest {
                var visible by mutableStateOf(true)
                var enabled by mutableStateOf(true)
                setContent { case.content(visible, enabled) }
                enabled = false
                awaitIdle()
                // Removal and final disposal rewrite the slots that renderer withdrawal must leave intact.
                visible = false
                awaitIdle()
                when (case.widget) {
                    CellWidget.List -> onAllNodesOfType<JList<*>>().assertCountEquals(0)
                    CellWidget.Table -> onAllNodesOfType<JTable>().assertCountEquals(0)
                    else -> error("no withdrawal case for ${case.widget}")
                }
            }
        }
    }

    @TestFactory
    fun restoredRenderersFollowLookAndFeelChanges(): List<DynamicTest> = lookAndFeelCases.map { case ->
        dynamicTest(case.widget.name) {
            runComposeSwingTest {
                val hostLookAndFeel = UIManager.getLookAndFeel()
                UIManager.setLookAndFeel(MetalLookAndFeel())
                try {
                    var enabled by mutableStateOf(true)
                    setContent { case.content(enabled) }
                    val widget = cellWidget(case.widget)
                    val composingRenderer = widget.cellRenderer()
                    UIManager.setLookAndFeel(NimbusLookAndFeel())
                    // A new UI delegate keeps a renderer supplied by the composition.
                    SwingUtilities.updateComponentTreeUI(root)
                    assertSame(
                        composingRenderer,
                        widget.cellRenderer(),
                        "a look-and-feel change should keep the composable cell",
                    )
                    enabled = false
                    awaitIdle()
                    assertEquals(case.text, (widget.renderCellAt(0) as JLabel).text, case.restoredMessage)
                    SwingUtilities.updateComponentTreeUI(root)
                    assertEquals(
                        case.widget.defaultRendererClass(),
                        widget.cellRenderer().javaClass,
                        case.changedMessage,
                    )
                } finally {
                    UIManager.setLookAndFeel(hostLookAndFeel)
                }
            }
        }
    }

    @TestFactory
    fun omittedContentKeepsNativeRenderers(): List<DynamicTest> = listOf(
        NativeCellCase(
            widget = CellWidget.List,
            content = {
                ListBox(items = listOf("a", "b"))
            },
            text = "a",
            labelMessage = "the default renderer renders a JLabel",
            textMessage = "the default renderer renders the item's toString",
            rendererMessage =
                "omitting itemContent must leave the JList default renderer, not " +
                    "install a composing renderer",
        ),
        NativeCellCase(
            widget = CellWidget.Combo,
            content = {
                ComboBox(items = listOf("a", "b"), selectedItem = "a", onSelectionChange = {})
            },
            text = "a",
            labelMessage = "the default combo renderer renders a JLabel",
            textMessage = "the default renderer renders the item's toString",
            rendererMessage = null,
        ),
        NativeCellCase(
            widget = CellWidget.Tree,
            content = {
                Tree(root = cellTree, children = { it.children }, label = { it.name })
            },
            text = "root",
            labelMessage = "the tree's own renderer renders a JLabel",
            textMessage = "the tree's own renderer renders the node's label",
            rendererMessage = "omitting nodeContent must leave the tree's own renderer, not install a composing one",
        ),
    ).map { case ->
        dynamicTest(case.widget.name) {
            runComposeSwingTest {
                setContent { case.content() }
                val widget = cellWidget(case.widget)
                case.rendererMessage?.let { message ->
                    assertNull(
                        widget.cellRenderer().takeUnless { case.widget.defaultRendererClass().isInstance(it) },
                        message,
                    )
                }
                val cell = widget.renderCellAt(0)
                assertTrue(cell is JLabel, case.labelMessage)
                assertEquals(case.text, (cell as JLabel).text, case.textMessage)
            }
        }
    }

    private val renderedCellCases =
        listOf(
            RenderCase(
                widget = CellWidget.List,
                name = "itemContentRealizesAComposableCellPerRow",
                content = {
                    ListBox(items = listOf("alpha", "beta", "gamma")) { item ->
                        Panel { Label(item) }
                    }
                },
                checks =
                    listOf(
                        CellText(
                            "beta",
                            1,
                            "the composable cell for row 1 should have realized a JLabel carrying that row's text",
                        ),
                    ),
            ),
            RenderCase(
                widget = CellWidget.Combo,
                name = "itemContentRealizesAComposableCellPerItem",
                content = {
                    ComboBox(
                        items = listOf("red", "green", "blue"),
                        selectedItem = "red",
                        onSelectionChange = {},
                    ) { item ->
                        Panel { Label(item) }
                    }
                },
                checks =
                    listOf(
                        CellText("green", 1, "the cell should render item 1"),
                        CellText("blue", 2, "the reused cell should rerender item 2"),
                    ),
            ),
            RenderCase(
                widget = CellWidget.Table,
                name = "cellContentRealizesAComposableCellPerCell",
                content = {
                    Table(
                        rows = people,
                        columns = {
                            column("Name") { it.name }
                            column(
                                header = "Age",
                                cellContent = { row -> Panel { Label("${row.name}: ${row.age}") } },
                            ) { it.age }
                        },
                    )
                },
                checks =
                    listOf(
                        CellText(
                            "Ada: 36",
                            0,
                            "the composable cell should have realized a JLabel built from the row it belongs to",
                        ),
                        CellText("Alan: 41", 1, "the reused cell should rerender the next row"),
                    ),
                column = 1,
            ),
            RenderCase(
                widget = CellWidget.Tree,
                name = "nodeContentRealizesAComposableNodePerRow",
                content = {
                    Tree(
                        root = cellTree,
                        children = { it.children },
                        label = { it.name },
                    ) { value ->
                        Panel { Label("${value.name} (${value.children.size})") }
                    }
                },
                checks =
                    listOf(
                        CellText(
                            "root (2)",
                            0,
                            "the composable node for row 0 should have realized a JLabel " +
                                "built from that row's value",
                        ),
                        CellText("fruit (2)", 1, "the reused node should rerender the next row"),
                    ),
            ),
            RenderCase(
                widget = CellWidget.List,
                name = "theSameRendererRerendersAsRowsChange",
                content = {
                    ListBox(items = listOf("one", "two", "three")) { item ->
                        Label(item)
                    }
                },
                checks =
                    listOf(
                        CellText("one", 0, "row 0 should render its item"),
                        CellText("three", 2, "the reused cell should rerender row 2"),
                        CellText("two", 1, "and rerender again for row 1"),
                    ),
            ),
            RenderCase(
                widget = CellWidget.List,
                name = "aNullItemRendersThroughTheCellBodyLikeAnyOther",
                content = {
                    ListBox(items = listOf(null, "beta")) { item ->
                        Label(item ?: "(none)")
                    }
                },
                checks =
                    listOf(
                        CellText("(none)", 0, "a row whose item is null is a row the cell body renders"),
                        CellText("beta", 1, "the next row renders its own item"),
                    ),
            ),
            RenderCase(
                widget = CellWidget.Table,
                name = "aNullRowRendersThroughTheCellBodyLikeAnyOther",
                content = {
                    val rows: List<Person?> = listOf(null, Person("Alan", 41))

                    Table(
                        rows = rows,
                        columns = {
                            column(
                                header = "Name",
                                cellContent = { row -> Label(row?.name ?: "(none)") },
                            ) { it?.name }
                        },
                    )
                },
                checks =
                    listOf(
                        CellText("(none)", 0, "a row whose value is null is a row the cell body renders"),
                        CellText("Alan", 1, "the next row renders its own value"),
                    ),
            ),
            RenderCase(
                widget = CellWidget.Tree,
                name = "aNullValueRendersThroughTheNodeBodyLikeAnyOther",
                content = {
                    val root: CellBranch? = CellBranch("root")

                    Tree(
                        root = root,
                        children = { parent -> if (parent === root) listOf(null, CellBranch("leaf")) else emptyList() },
                        label = { it?.name ?: "(none)" },
                    ) { value ->
                        Label(value?.name ?: "(none)")
                    }
                },
                checks =
                    listOf(
                        CellText("(none)", 1, "a node whose value is null is a node the node body renders"),
                        CellText("leaf", 2, "the next node renders its own value"),
                    ),
            ),
        )

    private val lookAndFeelCases =
        listOf(
            LookAndFeelCase(
                widget = CellWidget.List,
                content = { enabled ->
                    ListBox(
                        items = listOf("alpha", "beta"),
                        itemContent =
                            if (enabled) {
                                { item -> Label(item) }
                            } else {
                                null
                            },
                    )
                },
                text = "alpha",
                restoredMessage = "taking itemContent away should render the list's own renderer",
                changedMessage =
                    "a look-and-feel change should give the list the renderer of the look and feel in force",
            ),
            LookAndFeelCase(
                widget = CellWidget.Combo,
                content = { enabled ->
                    ComboBox(
                        items = listOf("red", "green"),
                        selectedItem = "red",
                        onSelectionChange = {},
                        itemContent =
                            if (enabled) {
                                { item -> Label(item) }
                            } else {
                                null
                            },
                    )
                },
                text = "red",
                restoredMessage = "taking itemContent away should render the combo box's own renderer",
                changedMessage =
                    "a look-and-feel change should give the combo box the renderer of " +
                        "the look and feel in force",
            ),
        )

    private val people = listOf(Person("Ada", 36), Person("Alan", 41))
    private val cellTree =
        CellBranch(
            "root",
            listOf(
                CellBranch("fruit", listOf(CellBranch("apple"), CellBranch("pear"))),
                CellBranch("veg", listOf(CellBranch("carrot"))),
            ),
        )

    private fun ComposeSwingTest.cellWidget(widget: CellWidget): JComponent = when (widget) {
        CellWidget.List -> onNodeOfType<JList<*>>().fetch()
        CellWidget.Combo -> onNodeOfType<JComboBox<*>>().fetch()
        CellWidget.Table -> onNodeOfType<JTable>().fetch()
        CellWidget.Tree -> onNodeOfType<JTree>().fetch()
    }

    private fun JComponent.renderCellAt(
        index: Int,
        column: Int = 0,
    ): Component = when (this) {
        is JList<*> -> {
            renderCell(index)
        }

        is JComboBox<*> -> {
            renderCell(index)
        }

        is JTable -> {
            getCellRenderer(index, column).getTableCellRendererComponent(
                this,
                getValueAt(index, column),
                false,
                false,
                index,
                column,
            )
        }

        is JTree -> {
            val value = getPathForRow(index).lastPathComponent
            cellRenderer.getTreeCellRendererComponent(
                this,
                value,
                isRowSelected(index),
                isExpanded(index),
                model.isLeaf(value),
                index,
                hasFocus(),
            )
        }

        else -> {
            error("unsupported cell widget: $this")
        }
    }

    private fun JComponent.captureRenderer(): (String, Int) -> Component = when (this) {
        is JList<*> -> {
            val captured = cellRenderer
            { value, index -> captured.renderCell(value, index, list = this) }
        }

        is JComboBox<*> -> {
            val captured = renderer
            { value, index -> captured.renderCell(value, index) }
        }

        else -> {
            error("unsupported captured renderer: $this")
        }
    }

    private fun JComponent.cellRenderer(): Any = when (this) {
        is JList<*> -> cellRenderer
        is JComboBox<*> -> renderer
        is JTree -> cellRenderer
        else -> error("unsupported native renderer: $this")
    }

    private enum class CellWidget {
        List,
        Combo,
        Table,
        Tree,
        ;

        fun defaultRendererClass(): Class<*> = when (this) {
            List -> JList<String>().cellRenderer.javaClass
            Combo -> JComboBox<String>().renderer.javaClass
            Tree -> JTree().cellRenderer.javaClass
            Table -> error("table renderers belong to their columns")
        }
    }

    private data class CellBranch(
        val name: String,
        val children: List<CellBranch> = emptyList(),
    )

    private data class CellText(
        val text: String,
        val index: Int,
        val message: String,
    )

    private data class RenderCase(
        val widget: CellWidget,
        val name: String,
        val content: @Composable () -> Unit,
        val checks: List<CellText>,
        val column: Int = 0,
    )

    private data class DisposedCellCase(
        val widget: CellWidget,
        val content: @Composable (Boolean) -> Unit,
        val first: String,
        val second: String,
        val staleMessage: String,
        val restoredMessage: String,
    )

    private data class WithdrawalCase(
        val widget: CellWidget,
        val content: @Composable (Boolean, Boolean) -> Unit,
    )

    private data class LookAndFeelCase(
        val widget: CellWidget,
        val content: @Composable (Boolean) -> Unit,
        val text: String,
        val restoredMessage: String,
        val changedMessage: String,
    )

    private data class NativeCellCase(
        val widget: CellWidget,
        val content: @Composable () -> Unit,
        val text: String,
        val labelMessage: String,
        val textMessage: String,
        val rendererMessage: String?,
    )
}
