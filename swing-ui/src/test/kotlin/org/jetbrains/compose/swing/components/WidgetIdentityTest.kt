package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.desktop.DesktopPane
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.layout.Glue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.RigidArea
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.Spacer
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.components.layout.Strut
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.layout.ToolBar
import org.jetbrains.compose.swing.components.layout.ToolBarSeparator
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.components.selection.rememberTableState
import org.jetbrains.compose.swing.components.style.borderPainted
import org.jetbrains.compose.swing.components.style.caret
import org.jetbrains.compose.swing.components.style.caretBlinkRate
import org.jetbrains.compose.swing.components.style.caretColor
import org.jetbrains.compose.swing.components.style.contentAreaFilled
import org.jetbrains.compose.swing.components.style.displayedMnemonicIndex
import org.jetbrains.compose.swing.components.style.highlights
import org.jetbrains.compose.swing.components.style.horizontalAlignment
import org.jetbrains.compose.swing.components.style.icon
import org.jetbrains.compose.swing.components.style.margin
import org.jetbrains.compose.swing.components.style.mnemonic
import org.jetbrains.compose.swing.components.style.rolloverEnabled
import org.jetbrains.compose.swing.components.style.rolloverIcon
import org.jetbrains.compose.swing.components.style.rolloverSelectedIcon
import org.jetbrains.compose.swing.components.style.selectedIcon
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.interaction.initialFocus
import org.jetbrains.compose.swing.modifier.interaction.inputVerifier
import org.jetbrains.compose.swing.modifier.key
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.bounds
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.location
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.size
import org.jetbrains.compose.swing.style.toolTip
import org.jetbrains.compose.swing.style.verifyInputWhenFocusTarget
import org.jetbrains.compose.swing.style.width
import org.jetbrains.compose.swing.style.x
import org.jetbrains.compose.swing.style.y
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.text.TextRange
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.image.BufferedImage
import javax.swing.AbstractButton
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFormattedTextField
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JList
import javax.swing.JPasswordField
import javax.swing.JProgressBar
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JToolBar
import javax.swing.JTree
import javax.swing.RowFilter
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableModel
import javax.swing.text.DefaultCaret
import javax.swing.text.DefaultHighlighter
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.JTextComponent
import javax.swing.text.PlainDocument
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class WidgetIdentityTest {
    class Case(
        val name: String,
        val initial: (SwingNodeInteraction<*>.() -> Unit)?,
        val applied: SwingNodeInteraction<*>.() -> Unit,
        val content: @Composable (Boolean) -> Unit,
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun changingAParameterKeepsTheWidget(case: Case) = runComposeSwingTest {
        // The root lays out nothing, so the geometry cases read the location, bounds and size their
        // modifiers declare.
        onRoot().fetch<Container>().layout = null
        var changed by mutableStateOf(false)
        setContent { case.content(changed) }
        val held = onRoot().onChild().fetch<JComponent>()
        case.initial?.invoke(onRoot().onChild())
        changed = true
        awaitIdle()
        case.applied(onRoot().onChild())
        assertSame(
            held,
            onRoot().onChild().fetch<JComponent>(),
            "${case.name} keeps its widget when a parameter changes",
        )
    }

    @Test
    fun droppingAWholeSizeAndThenAnAxisLeavesTheChildWhereItStood() = runComposeSwingTest {
        var declared by mutableIntStateOf(0)
        setContent {
            LayeredPane {
                Label(
                    text = "child",
                    modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER),
                    style = {
                        if (declared == 1) size(120, 40)
                        if (declared == 1 || declared == 2) width(10)
                    },
                )
            }
        }
        onNodeOfType<JLabel>().assertProperty(Dimension(0, 0)) { size }

        declared = 1
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(Dimension(10, 40)) { size }

        declared = 2
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(Dimension(10, 0)) { size }

        declared = 3
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(Dimension(0, 0)) { size }
    }

    companion object {
        private inline fun <reified T : Component> case(
            name: String,
            noinline initial: (SwingNodeInteraction<T>.() -> Unit)? = null,
            noinline applied: SwingNodeInteraction<T>.() -> Unit,
            noinline content: @Composable (Boolean) -> Unit,
        ): Case = Case(
            name,
            initial?.let { check -> { assertIsOfType<T>().check() } },
            { assertIsOfType<T>().applied() },
            content,
        )

        @JvmStatic
        fun cases(): List<Named<Case>> = listOf(
            BasicWidgetCases.cases(),
            ModelAndDocumentCases.cases(),
            ModifierCases.cases(),
            HighlightAndAppearanceCases.cases(),
            GeometryAndInteractionCases.cases(),
        ).flatten().map { Named.of(it.name, it) }
    }

    private object BasicWidgetCases {
        fun cases(): List<Case> = listOf(
            tableCases(),
            toggleButtonCases(),
            formattedTextFieldCases(),
            splitPaneCases(),
            glueCases(),
            sizingCases(),
            treeCases(),
        ).flatten()

        private fun tableCases(): List<Case> = listOf(
            case<JTable>(
                "Table",
                applied = {
                    assertProperty(30) { this.rowHeight }
                },
            ) {
                Table(rows = listOf("a"), columns = { column("name") { it } }, rowHeight = if (it) 30 else 20)
            },
            case<JList<*>>(
                "ListBox",
                applied = {
                    assertProperty(4) { this.visibleRowCount }
                },
            ) { ListBox(items = listOf("a"), visibleRowCount = if (it) 4 else 8) },
            case<JTree>(
                "Tree",
                applied = {
                    assertProperty(true) { this.isRootVisible }
                },
            ) { Tree(model = DefaultTreeModel(DefaultMutableTreeNode("root")), rootVisible = it) },
            case<JComboBox<*>>(
                "ComboBox",
                applied = {
                    assertProperty(true) { this.isEditable }
                },
            ) {
                ComboBox(items = listOf("a"), selectedItem = "a", onSelectionChange = {}, editable = it)
            },
            case<JLabel>(
                "Label",
                applied = {
                    assertProperty("b") { this.text }
                },
            ) { Label(if (it) "b" else "a") },
            case<AbstractButton>(
                "Button",
                applied = {
                    assertProperty("b") { this.text }
                },
            ) { Button(text = if (it) "b" else "a", onClick = {}) },
            case<AbstractButton>(
                "CheckBox",
                applied = {
                    assertProperty(true) { this.isSelected }
                },
            ) { CheckBox(text = "a", checked = it, onCheckedChange = {}) },
            case<AbstractButton>(
                "RadioButton",
                applied = {
                    assertProperty(true) { this.isSelected }
                },
            ) { RadioButton(text = "a", selected = it, onSelectedChange = {}) },
        )

        private fun toggleButtonCases(): List<Case> = listOf(
            case<AbstractButton>(
                "ToggleButton",
                applied = {
                    assertProperty(true) { this.isSelected }
                },
            ) { ToggleButton(text = "a", selected = it, onSelectedChange = {}) },
            case<JProgressBar>(
                "ProgressBar",
                applied = {
                    assertProperty(2) { this.value }
                },
            ) { ProgressBar(value = if (it) 2 else 1) },
            case<JSlider>(
                "Slider",
                applied = {
                    assertProperty(2) { this.value }
                },
            ) { Slider(value = if (it) 2 else 1, onValueChange = {}) },
            case<JSpinner>(
                "Spinner",
                applied = {
                    assertProperty(2) { this.value }
                },
            ) { Spinner(value = if (it) 2 else 1, onValueChange = {}) },
            case<JSeparator>(
                "Separator",
                applied = {
                    assertProperty(SwingConstants.VERTICAL) { this.orientation }
                },
            ) {
                Separator(orientation = if (it) SwingConstants.VERTICAL else SwingConstants.HORIZONTAL)
            },
            case<JTextComponent>(
                "TextField",
                applied = {
                    assertProperty("b") { this.text }
                },
            ) { TextField(value = if (it) "b" else "a", onValueChange = {}) },
            case<JTextComponent>(
                "TextArea",
                applied = {
                    assertProperty("b") { this.text }
                },
            ) { TextArea(value = if (it) "b" else "a", onValueChange = {}) },
            case<JPasswordField>(
                "PasswordField",
                applied = {
                    assertProperty("b") { this.password.concatToString() }
                },
            ) {
                PasswordField(value = if (it) charArrayOf('b') else charArrayOf('a'), onValueChange = {})
            },
        )

        private fun formattedTextFieldCases(): List<Case> = listOf(
            case<JFormattedTextField>(
                "FormattedTextField",
                applied = {
                    assertProperty(2) { this.value }
                },
            ) { FormattedTextField(value = if (it) 2 else 1, onValueChange = {}) },
            case<JTextComponent>(
                "EditorPane",
                applied = {
                    assertProperty("b") { this.text }
                },
            ) { EditorPane(markup = if (it) "b" else "a", onLinkActivate = {}) },
            case<JTextComponent>(
                "TextPane",
                applied = {
                    assertProperty("b") { this.text }
                },
            ) { TextPane(value = if (it) "b" else "a", onValueChange = {}) },
            case<JComponent>(
                "Layer",
                applied = {
                    assertProperty("b") { this.toolTipText }
                },
            ) { Layer(style = { toolTip(if (it) "b" else "a") }) {} },
            case<JComponent>(
                "RadioGroup",
                applied = {
                    onChildren()
                        .filterToOne(SwingMatcher.hasText("b"))
                        .assertIsOfType<JRadioButton>()
                        .assert(SwingMatcher.isSelected())
                },
            ) {
                RadioGroup(selectedIndex = if (it) 1 else 0, onSelectionChange = {}) {
                    option("a")
                    option("b")
                }
            },
            case<JComponent>(
                "Panel",
                applied = {
                    assertProperty("b") { this.toolTipText }
                },
            ) { Panel(style = { toolTip(if (it) "b" else "a") }) {} },
            case<JComponent>(
                "ScrollPane",
                applied = {
                    assertProperty("b") { this.toolTipText }
                },
            ) { ScrollPane(style = { toolTip(if (it) "b" else "a") }) {} },
        )

        private fun splitPaneCases(): List<Case> = listOf(
            case<JSplitPane>(
                "SplitPane",
                applied = {
                    assertProperty(20) { this.dividerLocation }
                },
            ) { SplitPane(dividerLocation = if (it) 20 else 10) {} },
            case<JTabbedPane>(
                "TabbedPane",
                applied = {
                    assertProperty(SwingConstants.BOTTOM) { this.tabPlacement }
                },
            ) {
                TabbedPane(
                    selectedIndex = -1,
                    onSelectedIndexChange = {},
                    tabPlacement = if (it) SwingConstants.BOTTOM else SwingConstants.TOP,
                ) {}
            },
            case<JToolBar>(
                "ToolBar",
                applied = {
                    assertProperty(true) { this.isRollover }
                },
            ) { ToolBar(floatable = false, rollover = it) {} },
            case<JToolBar.Separator>(
                "ToolBarSeparator",
                applied = {
                    assertProperty(Dimension(20, 10)) { this.separatorSize }
                },
            ) { ToolBarSeparator(size = Dimension(if (it) 20 else 10, 10)) },
            case<JComponent>(
                "RigidArea",
                applied = {
                    assertProperty(Dimension(20, 10)) { this.preferredSize }
                },
            ) { RigidArea(width = if (it) 20 else 10, height = 10) },
            case<JComponent>(
                "Spacer",
                applied = {
                    assertProperty(Dimension(20, 20)) { this.preferredSize }
                },
            ) { Spacer(size = if (it) 20 else 10) },
            case<JComponent>(
                "Strut",
                applied = {
                    assertProperty(Dimension(20, 0)) { this.preferredSize }
                },
            ) { Strut(orientation = SwingConstants.HORIZONTAL, size = if (it) 20 else 10) },
        )

        private fun glueCases(): List<Case> = listOf(
            case<JComponent>(
                "Glue",
                applied = {
                    assertProperty(0) { this.maximumSize.width }
                },
            ) { Glue(orientation = if (it) SwingConstants.VERTICAL else SwingConstants.HORIZONTAL) },
            case<JComponent>(
                "DesktopPane",
                applied = {
                    assertProperty("b") { this.toolTipText }
                },
            ) { DesktopPane(style = { toolTip(if (it) "b" else "a") }) {} },
            case<JComponent>(
                "LayeredPane",
                applied = {
                    assertProperty("b") { this.toolTipText }
                },
            ) { LayeredPane(style = { toolTip(if (it) "b" else "a") }) {} },
        )

        private fun sizingCases(): List<Case> = listOf(
            case<JTextField>(
                "TextField columns",
                applied = {
                    assertProperty(28) { this.columns }
                },
            ) { TextField(value = "", onValueChange = {}, columns = if (it) 28 else 8) },
            case<JTextArea>(
                "TextArea rows",
                applied = {
                    assertProperty(12) { this.rows }
                },
            ) { TextArea(value = "", onValueChange = {}, rows = if (it) 12 else 2) },
            case<JTable>(
                "Table autoResizeMode",
                applied = {
                    assertProperty(JTable.AUTO_RESIZE_OFF) { this.autoResizeMode }
                },
            ) {
                Table(
                    rows = listOf("a"),
                    columns = { column("name") { it } },
                    autoResizeMode = if (it) JTable.AUTO_RESIZE_OFF else JTable.AUTO_RESIZE_ALL_COLUMNS,
                )
            },
            case<JTable>(
                "Table fillsViewportHeight",
                applied = {
                    assertProperty(true) { this.fillsViewportHeight }
                },
            ) { Table(rows = listOf("a"), columns = { column("name") { it } }, fillsViewportHeight = it) },
            case<JScrollPane>(
                "ScrollPane wheelScrollingEnabled",
                applied = {
                    assertProperty(false) { this.isWheelScrollingEnabled }
                },
            ) { ScrollPane(wheelScrollingEnabled = !it) {} },
        )

        private fun treeCases(): List<Case> = listOf(
            case<JTree>(
                "Tree visibleRowCount",
                applied = {
                    assertProperty(5) { this.visibleRowCount }
                },
            ) {
                Tree(model = DefaultTreeModel(DefaultMutableTreeNode("root")), visibleRowCount = if (it) 5 else 12)
            },
            case<JTree>(
                "Tree toggleClickCount",
                applied = {
                    assertProperty(3) { this.toggleClickCount }
                },
            ) {
                Tree(model = DefaultTreeModel(DefaultMutableTreeNode("root")), toggleClickCount = if (it) 3 else 1)
            },
            case<JTree>(
                "Tree rowHeight",
                applied = {
                    assertProperty(31) { this.rowHeight }
                },
            ) {
                Tree(model = DefaultTreeModel(DefaultMutableTreeNode("root")), rowHeight = if (it) 31 else 24)
            },
            case<JTree>(
                "Tree editable",
                applied = {
                    assertProperty(true) { this.isEditable }
                },
            ) { Tree(root = "root", children = { emptyList() }, isEditable = it) },
        )
    }

    private object ModelAndDocumentCases {
        fun cases(): List<Case> = listOf(
            treeModelSwapCases(),
            tableRowsChangeCases(),
            tableComparatorChangeCases(),
            textFieldDocumentSwapCases(),
            editorPaneDocumentSwapCases(),
        ).flatten()

        private fun treeModelSwapCases(): List<Case> = listOf(
            run {
                val before = DefaultTreeModel(DefaultMutableTreeNode("a"))
                val after = DefaultTreeModel(DefaultMutableTreeNode("b"))
                case<JTree>(
                    "Tree model swap",
                    applied = {
                        assertProperty(after, "the declared model reaches the widget") { this.model }
                    },
                ) { changed ->
                    Tree(model = if (changed) after else before)
                }
            },
            run {
                val before = DefaultListModel<String>().apply { addElement("a") }
                val after = DefaultListModel<String>().apply { addElement("b") }
                case<JList<*>>(
                    "ListBox model swap",
                    applied = {
                        assertProperty(after, "the declared model reaches the widget") { this.model }
                    },
                ) { changed ->
                    ListBox(model = if (changed) after else before)
                }
            },
            run {
                val before = DefaultComboBoxModel(arrayOf("a"))
                val after = DefaultComboBoxModel(arrayOf("b"))
                case<JComboBox<*>>(
                    "ComboBox model swap",
                    applied = {
                        assertProperty(after, "the declared model reaches the widget") { this.model }
                    },
                ) { changed ->
                    ComboBox(model = if (changed) after else before)
                }
            },
            run {
                val before = DefaultTableModel(arrayOf(arrayOf<Any>("a")), arrayOf<Any>("name"))
                val after = DefaultTableModel(arrayOf(arrayOf<Any>("b")), arrayOf<Any>("name"))
                case<JTable>(
                    "Table model swap",
                    applied = {
                        assertProperty(after, "the declared model reaches the widget") { this.model }
                    },
                ) { changed ->
                    Table(model = if (changed) after else before)
                }
            },
        )

        private fun tableRowsChangeCases(): List<Case> = listOf(
            case<JTable>(
                "Table rows change",
                applied = {
                    assertProperty(listOf("b", "c")) {
                        this.let { table -> (0 until table.rowCount).map { table.getValueAt(it, 0) } }
                    }
                },
            ) {
                Table(rows = if (it) listOf("b", "c") else listOf("a"), columns = { column("name") { it } })
            },
            case<JList<*>>(
                "ListBox items change",
                applied = {
                    assertProperty(listOf("b", "c")) {
                        this.model.let { model -> (0 until model.size).map { model.getElementAt(it) } }
                    }
                },
            ) { ListBox(items = if (it) listOf("b", "c") else listOf("a")) },
            case<JComboBox<*>>(
                "ComboBox items change",
                applied = {
                    assertProperty(listOf("b", "c")) {
                        this.let { combo -> (0 until combo.itemCount).map { combo.getItemAt(it) } }
                    }
                },
            ) {
                ComboBox(items = if (it) listOf("b", "c") else listOf("a"), selectedItem = null, onSelectionChange = {})
            },
            case<JTable>(
                "Table filter change",
                applied = {
                    assertProperty("b") { this.getValueAt(0, 0) }
                },
            ) { changed ->
                val filter = remember(changed) { RowFilter.regexFilter<TableModel, Int>(if (changed) "b" else "a") }
                Table(
                    rows = listOf("a", "b"),
                    columns = { column("name") { it } },
                    sortable = true,
                    rowFilter = filter,
                )
            },
        )

        private fun tableComparatorChangeCases(): List<Case> = listOf(
            case<JTable>(
                "Table comparator change",
                applied = {
                    assertProperty(listOf("b", "a")) {
                        this.let { table -> (0 until table.rowCount).map { table.getValueAt(it, 0) } }
                    }
                },
            ) { changed ->
                val comparator =
                    remember(changed) {
                        Comparator<Any?> { a, b ->
                            if (changed) b.toString().compareTo(a.toString()) else a.toString().compareTo(b.toString())
                        }
                    }
                Table(
                    rows = listOf("a", "b"),
                    columns = { column("name", comparator = comparator) { it } },
                    state = rememberTableState(initialSortKeys = listOf(SortKey(0, SortOrder.ASCENDING))),
                    sortable = true,
                )
            },
            case<JTable>(
                "Table column rebuild",
                applied = {
                    assertProperty(2) { this.columnCount }
                },
            ) { changed ->
                Table(
                    rows = listOf("a"),
                    columns = {
                        column("name") { it }
                        if (changed) column("length") { it.length }
                    },
                )
            },
            case<JPasswordField>(
                "PasswordField echo char change",
                applied = {
                    assertProperty('#') { this.echoChar }
                },
            ) {
                PasswordField(value = charArrayOf('a'), onValueChange = {}, echoChar = if (it) '#' else '*')
            },
        )

        private fun textFieldDocumentSwapCases(): List<Case> = listOf(
            run {
                val before = PlainDocument()
                val after = PlainDocument()
                case<JTextComponent>(
                    "TextField document swap",
                    applied = {
                        assertProperty(after, "the declared document reaches the widget") {
                            this.document
                        }
                    },
                ) { changed ->
                    TextField(state = rememberDocumentState(if (changed) after else before))
                }
            },
            run {
                val before = PlainDocument()
                val after = PlainDocument()
                case<JTextComponent>(
                    "TextArea document swap",
                    applied = {
                        assertProperty(after, "the declared document reaches the widget") {
                            this.document
                        }
                    },
                ) { changed ->
                    TextArea(state = rememberDocumentState(if (changed) after else before))
                }
            },
            run {
                val before = PlainDocument()
                val after = PlainDocument()
                case<JTextComponent>(
                    "PasswordField document swap",
                    applied = {
                        assertProperty(after, "the declared document reaches the widget") {
                            this.document
                        }
                    },
                ) { changed ->
                    PasswordField(state = rememberDocumentState(if (changed) after else before))
                }
            },
        )

        private fun editorPaneDocumentSwapCases(): List<Case> = listOf(
            run {
                val before = PlainDocument()
                val after = PlainDocument()
                case<JTextComponent>(
                    "EditorPane document swap",
                    applied = {
                        assertProperty(after, "the declared document reaches the widget") {
                            this.document
                        }
                    },
                ) { changed ->
                    EditorPane(state = rememberDocumentState(if (changed) after else before))
                }
            },
            run {
                val before = DefaultStyledDocument()
                val after = DefaultStyledDocument()
                case<JTextComponent>(
                    "TextPane document swap",
                    applied = {
                        assertProperty(after, "the declared document reaches the widget") {
                            this.document
                        }
                    },
                ) { changed ->
                    TextPane(state = rememberDocumentState(if (changed) after else before))
                }
            },
            case<JPasswordField>(
                "PasswordField state-driven edit",
                applied = {
                    assertProperty("ab") { this.password.concatToString() }
                },
            ) { changed ->
                val state = rememberDocumentState("a")
                PasswordField(state = state)
                LaunchedEffect(changed) {
                    if (changed) state.edit { append("b") }
                }
            },
        )
    }

    private object ModifierCases {
        fun cases(): List<Case> = listOf(
            buttonTooltipRemovalCases(),
            labelTooltipRemovalCases(),
            modifierValueChangeAfterUnchangedElementCases(),
            displayedMnemonicIndexChangeCases(),
            modifierKeyChangeCases(),
            listenerRegistrationChangeCases(),
            horizontalAlignmentChangeCases(),
        ).flatten()

        private fun buttonTooltipRemovalCases(): List<Case> = listOf(
            case<JComponent>(
                "Button tooltip removal",
                initial = { assertProperty("tip") { toolTipText } },
                applied = { assertProperty(null) { this.toolTipText } },
            ) {
                Button("a", onClick = {}, style = { if (!it) toolTip("tip") })
            },
            case<JComponent>(
                "CheckBox tooltip removal",
                initial = { assertProperty("tip") { toolTipText } },
                applied = { assertProperty(null) { this.toolTipText } },
            ) {
                CheckBox(
                    "a",
                    checked = false,
                    onCheckedChange = {},
                    style = { if (!it) toolTip("tip") },
                )
            },
            case<JComponent>(
                "RadioButton tooltip removal",
                initial = { assertProperty("tip") { toolTipText } },
                applied = { assertProperty(null) { this.toolTipText } },
            ) {
                RadioButton(
                    "a",
                    selected = false,
                    onSelectedChange = {},
                    style = { if (!it) toolTip("tip") },
                )
            },
            case<JComponent>(
                "ToggleButton tooltip removal",
                initial = { assertProperty("tip") { toolTipText } },
                applied = { assertProperty(null) { this.toolTipText } },
            ) {
                ToggleButton(
                    "a",
                    selected = false,
                    onSelectedChange = {},
                    style = { if (!it) toolTip("tip") },
                )
            },
        )

        private fun labelTooltipRemovalCases(): List<Case> = listOf(
            case<JComponent>(
                "Label tooltip removal",
                initial = { assertProperty("tip") { toolTipText } },
                applied = { assertProperty(null) { this.toolTipText } },
            ) { Label("a", style = { if (!it) toolTip("tip") }) },
            case<JComponent>(
                "Custom property removal",
                initial = {
                    assertProperty("tip") { toolTipText }
                    assertProperty("derived from tip") { name }
                },
                applied = {
                    assertProperty(null) { this.toolTipText }
                    assertProperty("derived from null") { name }
                },
            ) { changed ->
                val descriptor =
                    remember {
                        ComponentPropertyDescriptor<JLabel, String?>(
                            name = "toolTipText",
                            read = { it.toolTipText },
                            write = { label, value ->
                                label.toolTipText = value
                                label.name = "derived from $value"
                            },
                        )
                    }
                Label(
                    "a",
                    modifier =
                        if (changed) {
                            SwingModifier
                        } else {
                            SwingModifier.property(descriptor, "tip", restores = RestorePolicy.DeclaredPropertyOnly)
                        },
                )
            },
            case<AbstractButton>(
                "Unchanged modifier redeclaration",
                applied = {
                    assertProperty("b") { this.text }
                    assertProperty(Color.GREEN) { background }
                    assertProperty("tip") { this.name }
                },
            ) {
                Button(
                    if (it) "b" else "a",
                    onClick = {},
                    modifier = SwingModifier.name("tip"),
                    style = { background(Color.GREEN) },
                )
            },
            case<JComponent>(
                "Modifier value change",
                applied = { assertProperty("second") { this.name } },
            ) {
                Button("a", onClick = {}, modifier = SwingModifier.name(if (it) "second" else "first"))
            },
        )

        private fun modifierValueChangeAfterUnchangedElementCases(): List<Case> = listOf(
            case<JComponent>(
                "Modifier value change after unchanged element",
                applied = {
                    assertProperty("second") { this.name }
                },
            ) {
                Button(
                    "a",
                    onClick = {},
                    modifier = SwingModifier.initialFocus().name(if (it) "second" else "first"),
                )
            },
            case<AbstractButton>(
                "Displayed mnemonic index addition",
                applied = {
                    assertProperty(5) { this.displayedMnemonicIndex }
                },
            ) {
                Button(
                    "Save As",
                    onClick = {},
                    style = {
                        mnemonic('A')
                        if (it) displayedMnemonicIndex(5)
                    },
                )
            },
            case<AbstractButton>(
                "Displayed mnemonic index removal",
                initial = {
                    assertProperty(5) { displayedMnemonicIndex }
                },
                applied = {
                    assertProperty(1) { this.displayedMnemonicIndex }
                },
            ) {
                Button(
                    "Save As",
                    onClick = {},
                    style = {
                        mnemonic('A')
                        if (!it) displayedMnemonicIndex(5)
                    },
                )
            },
        )

        private fun displayedMnemonicIndexChangeCases(): List<Case> = listOf(
            case<AbstractButton>(
                "Displayed mnemonic index change",
                applied = {
                    assertProperty(-1) { this.displayedMnemonicIndex }
                },
            ) {
                Button(
                    "Save As",
                    onClick = {},
                    style = {
                        mnemonic('A')
                        displayedMnemonicIndex(if (it) -1 else 5)
                    },
                )
            },
        )

        private fun modifierKeyChangeCases(): List<Case> = listOf(
            case<JComponent>(
                "Modifier key change",
                applied = {
                    assertProperty("tip") { this.name }
                },
            ) {
                Label("a", modifier = SwingModifier.key(it).name("tip"))
            },
            case<JComponent>(
                "Modifier key change with slot addition",
                applied = {
                    assertProperty("tip") { this.name }
                },
            ) {
                Label(
                    "a",
                    modifier = SwingModifier.key(it).let { modifier -> if (it) modifier.name("tip") else modifier },
                )
            },
            case<JComponent>(
                "Modifier key change with slot removal",
                initial = {
                    assertProperty("tip") { name }
                },
                applied = {
                    assertProperty(null) { this.name }
                },
            ) {
                Label(
                    "a",
                    modifier = SwingModifier.key(it).let { modifier -> if (it) modifier else modifier.name("tip") },
                )
            },
            case<JComponent>(
                "Unchanged modifier key with value change",
                applied = {
                    assertProperty(Color.BLUE) { this.background }
                },
            ) {
                Label(
                    "a",
                    modifier = SwingModifier.key("theme"),
                    style = { background(if (it) Color.BLUE else Color.GREEN) },
                )
            },
        )

        private fun listenerRegistrationChangeCases(): List<Case> = listOf(
            run {
                val listener = object : MouseAdapter() {}
                case<JButton>(
                    "Listener registration change",
                    applied = {
                        assertProperty(listOf(listener), "the listener moves to motion events") {
                            mouseMotionListeners.filter { it === listener }
                        }
                        assertProperty(emptyList(), "the old mouse registration is removed") {
                            mouseListeners.filter { it === listener }
                        }
                    },
                ) { changed ->
                    Button(
                        "a",
                        onClick = {},
                        modifier =
                            if (changed) {
                                SwingModifier.mouseMotionListener(listener)
                            } else {
                                SwingModifier.mouseListener(listener)
                            },
                    )
                }
            },
            case<JButton>(
                "Bound property callback registration change",
                applied = {
                    assertProperty(
                        JButton().getPropertyChangeListeners("background").size + 1,
                        "the new callback is registered",
                    ) {
                        getPropertyChangeListeners("background").size
                    }
                    assertProperty(
                        JButton().getPropertyChangeListeners("enabled").size,
                        "the old callback is removed",
                    ) {
                        getPropertyChangeListeners("enabled").size
                    }
                },
            ) {
                Button(
                    "a",
                    onClick = {},
                    modifier = SwingModifier.propertyChangeListener(if (it) "background" else "enabled") {},
                )
            },
        )

        private fun horizontalAlignmentChangeCases(): List<Case> = listOf(
            case<JLabel>(
                "Horizontal alignment change",
                applied = {
                    assertProperty(SwingConstants.RIGHT) { this.horizontalAlignment }
                },
            ) {
                Label(
                    "a",
                    style = { horizontalAlignment(if (it) SwingConstants.RIGHT else SwingConstants.LEFT) },
                )
            },
        )
    }

    private object HighlightAndAppearanceCases {
        fun cases(): List<Case> = listOf(
            highlightRangesChangeCases(),
            highlightsRemovalCases(),
            textChangeUnderStandingHighlightsCases(),
            opacityRemovalUnderStandingContentFillCases(),
            selectedIconRemovalCases(),
            rolloverIconWithSwitchCases(),
            rolloverIconRemovalCases(),
        ).flatten()

        private fun highlightRangesChangeCases(): List<Case> = listOf(
            case<JTextComponent>(
                "Highlight ranges change",
                applied = {
                    assertProperty(listOf(TextRange(6, 11))) {
                        this.highlighter.highlights.map { TextRange(it.startOffset, it.endOffset) }
                    }
                },
            ) {
                val painter = remember { DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW) }
                TextArea(
                    "hello world",
                    onValueChange = {},
                    style = {
                        highlights(
                            listOf(if (it) TextRange(6, 11) else TextRange(0, 5)),
                            painter,
                        )
                    },
                )
            },
            case<JTextComponent>(
                "Highlight state list mutation",
                applied = {
                    assertProperty(listOf(TextRange(0, 5), TextRange(6, 11))) {
                        this.highlighter.highlights.map { TextRange(it.startOffset, it.endOffset) }
                    }
                },
            ) { changed ->
                val ranges = remember { mutableStateListOf(TextRange(0, 5)) }
                val painter = remember { DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW) }
                TextArea(
                    "hello world",
                    onValueChange = {},
                    style = { highlights(ranges, painter) },
                )
                LaunchedEffect(changed) {
                    if (changed) ranges.add(TextRange(6, 11))
                }
            },
        )

        private fun highlightsRemovalCases(): List<Case> = listOf(
            case<JTextComponent>(
                "Highlights removal",
                initial = {
                    assertProperty(1) { highlighter.highlights.size }
                },
                applied = {
                    assertProperty(0) { this.highlighter.highlights.size }
                },
            ) { changed ->
                val painter = remember { DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW) }
                TextArea(
                    "hello world",
                    onValueChange = {},
                    style = { if (!changed) highlights(listOf(TextRange(0, 5)), painter) },
                )
            },
            case<JTextComponent>(
                "Highlight ranges emptied",
                applied = {
                    assertProperty(0) { this.highlighter.highlights.size }
                },
            ) {
                val painter = remember { DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW) }
                TextArea(
                    "hello world",
                    onValueChange = {},
                    style = { highlights(if (it) emptyList() else listOf(TextRange(0, 5)), painter) },
                )
            },
            case<JTextComponent>(
                "Highlight painter change",
                applied = {
                    assertProperty(Color.BLUE) {
                        val painter =
                            this
                                .highlighter.highlights
                                .single()
                                .painter
                        assertIs<DefaultHighlighter.DefaultHighlightPainter>(painter).color
                    }
                },
            ) { changed ->
                val painter =
                    remember(changed) {
                        DefaultHighlighter.DefaultHighlightPainter(if (changed) Color.BLUE else Color.YELLOW)
                    }
                TextArea(
                    "hello world",
                    onValueChange = {},
                    style = { highlights(listOf(TextRange(0, 5)), painter) },
                )
            },
        )

        private fun textChangeUnderStandingHighlightsCases(): List<Case> = listOf(
            case<JTextComponent>(
                "Text change under standing highlights",
                applied = {
                    assertProperty("hello there") { this.text }
                },
            ) {
                val painter = remember { DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW) }
                TextArea(
                    if (it) "hello there" else "hello world",
                    onValueChange = {},
                    style = { highlights(listOf(TextRange(6, 11)), painter) },
                )
            },
            case<JTextComponent>(
                "Text growth before standing highlights",
                applied = {
                    assertProperty("xhello world") { this.text }
                },
            ) {
                val painter = remember { DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW) }
                TextArea(
                    if (it) "xhello world" else "hello world",
                    onValueChange = {},
                    style = { highlights(listOf(TextRange(6, 11)), painter) },
                )
            },
            case<AbstractButton>(
                "Border painted removal",
                initial = {
                    assertProperty(false) { isBorderPainted }
                },
                applied = {
                    assertProperty(JButton().isBorderPainted) { this.isBorderPainted }
                },
            ) {
                Button("a", onClick = {}, style = { if (!it) borderPainted(false) })
            },
            case<AbstractButton>(
                "Content fill change under standing opacity",
                applied = {
                    assertProperty(false) { this.isContentAreaFilled }
                },
            ) {
                Button(
                    "a",
                    onClick = {},
                    style = {
                        contentAreaFilled(!it)
                        opaque(true)
                    },
                )
            },
        )

        private fun opacityRemovalUnderStandingContentFillCases(): List<Case> = listOf(
            case<AbstractButton>(
                "Opacity removal under standing content fill",
                initial = { assertProperty(true) { isOpaque } },
                applied = { assertProperty(false) { this.isOpaque } },
            ) {
                Button(
                    "a",
                    onClick = {},
                    style = {
                        contentAreaFilled(false)
                        if (!it) opaque(true)
                    },
                )
            },
            case<AbstractButton>(
                "Content fill removal",
                initial = { assertProperty(false) { isContentAreaFilled } },
                applied = { assertProperty(JButton().isContentAreaFilled) { this.isContentAreaFilled } },
            ) {
                Button("a", onClick = {}, style = { if (!it) contentAreaFilled(false) })
            },
            case<AbstractButton>(
                "Content fill addition under standing opacity",
                applied = { assertProperty(false) { this.isContentAreaFilled } },
            ) {
                Button(
                    "a",
                    onClick = {},
                    style = {
                        if (it) contentAreaFilled(false)
                        opaque(true)
                    },
                )
            },
            case<JTextComponent>(
                "Caret color change",
                applied = { assertProperty(Color.BLUE) { this.caretColor } },
            ) {
                TextField(
                    "a",
                    onValueChange = {},
                    style = { caretColor(if (it) Color.BLUE else Color.RED) },
                )
            },
            case<AbstractButton>(
                "Margin removal",
                initial = { assertProperty(Insets(1, 2, 3, 4)) { margin } },
                applied = { assertProperty(UIManager.getInsets("Button.margin")) { this.margin } },
            ) {
                Button(
                    "a",
                    onClick = {},
                    style = { if (!it) margin(Insets(1, 2, 3, 4)) },
                )
            },
        )

        private fun selectedIconRemovalCases(): List<Case> = listOf(
            run {
                val icon = ImageIcon(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
                case<AbstractButton>(
                    "Selected icon removal",
                    initial = { assertProperty(icon) { selectedIcon } },
                    applied = { assertProperty(null) { this.selectedIcon } },
                ) { changed ->
                    Button("a", {}, style = { if (!changed) selectedIcon(icon) })
                }
            },
            case<AbstractButton>(
                "Rollover enabled removal",
                initial = { assertProperty(true) { isRolloverEnabled } },
                applied = { assertProperty(JButton().isRolloverEnabled) { this.isRolloverEnabled } },
            ) {
                Button("a", onClick = {}, style = { if (!it) rolloverEnabled(true) })
            },
        )

        private fun rolloverIconWithSwitchCases(): List<Case> = listOf(
            run {
                val before = ImageIcon(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
                val after = ImageIcon(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
                case<AbstractButton>(
                    "Rollover icon change under standing switch",
                    applied = {
                        assertProperty(after, "the replacement icon reaches the button") { this.rolloverIcon }
                        assertProperty(false, "the standing rollover switch is preserved") { this.isRolloverEnabled }
                    },
                ) { changed ->
                    Button(
                        "a",
                        onClick = {},
                        style = {
                            rolloverEnabled(false)
                            rolloverIcon(if (changed) after else before)
                        },
                    )
                }
            },
            run {
                val icon = ImageIcon(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
                case<AbstractButton>(
                    "Rollover icon and switch removal",
                    initial = {
                        assertProperty(icon) { rolloverIcon }
                        assertProperty(false) { isRolloverEnabled }
                    },
                    applied = {
                        assertProperty(null) { this.rolloverIcon }
                        assertProperty(JButton().isRolloverEnabled) { this.isRolloverEnabled }
                    },
                ) { changed ->
                    Button(
                        "a",
                        onClick = {},
                        style = {
                            if (!changed) {
                                rolloverIcon(icon)
                                rolloverEnabled(false)
                            }
                        },
                    )
                }
            },
        )

        private fun rolloverIconRemovalCases(): List<Case> = listOf(
            run {
                val icon = ImageIcon(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
                case<AbstractButton>(
                    "Rollover icon removal",
                    initial = {
                        assertProperty(icon) { rolloverIcon }
                    },
                    applied = {
                        assertProperty(null) { this.rolloverIcon }
                    },
                ) { changed ->
                    Button(
                        "a",
                        onClick = {},
                        style = { if (!changed) rolloverIcon(icon) },
                    )
                }
            },
            run {
                val icon = ImageIcon(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
                case<AbstractButton>(
                    "Rollover selected icon removal",
                    initial = {
                        assertProperty(icon) { rolloverSelectedIcon }
                    },
                    applied = {
                        assertProperty(null) { this.rolloverSelectedIcon }
                    },
                ) { changed ->
                    CheckBox(
                        "a",
                        checked = true,
                        onCheckedChange = {},
                        style = { if (!changed) rolloverSelectedIcon(icon) },
                    )
                }
            },
            run {
                val icon = ImageIcon(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
                case<AbstractButton>(
                    "Icon removal",
                    initial = {
                        assertProperty(icon) { this.icon }
                    },
                    applied = {
                        assertProperty(null) { this.icon }
                    },
                ) { changed ->
                    Button("a", onClick = {}, style = { if (!changed) icon(icon) })
                }
            },
        )
    }

    private object GeometryAndInteractionCases {
        fun cases(): List<Case> = listOf(
            locationRemovalUnderStandingYCases(),
            boundsUnderStandingXCases(),
            sizeUnderStandingWidthCases(),
            geometryAndColorDeclarationReorderCases(),
            verifyInputWhenFocusTargetRemovalCases(),
            caretUnderStandingBlinkRateCases(),
            caretAndBlinkRateRemovalCases(),
        ).flatten()

        private fun locationRemovalUnderStandingYCases(): List<Case> = listOf(
            case<JComponent>(
                "Location removal under standing y",
                initial = {
                    assertProperty(Point(20, 50)) { location }
                },
                applied = {
                    assertProperty(Point(0, 50)) { this.location }
                },
            ) {
                Label(
                    "a",
                    style = {
                        if (!it) location(20, 30)
                        y(50)
                    },
                )
            },
        )

        private fun boundsUnderStandingXCases(): List<Case> = listOf(
            case<JComponent>(
                "Bounds addition under standing x",
                applied = {
                    assertProperty(Rectangle(10, 30, 100, 40)) { this.bounds }
                },
            ) {
                Label(
                    "a",
                    style = {
                        if (it) bounds(20, 30, 100, 40)
                        x(10)
                    },
                )
            },
            case<JComponent>(
                "Bounds change under standing x",
                applied = {
                    assertProperty(Rectangle(10, 60, 100, 40)) { this.bounds }
                },
            ) {
                Label(
                    "a",
                    style = {
                        bounds(20, if (it) 60 else 30, 100, 40)
                        x(10)
                    },
                )
            },
            case<JComponent>(
                "Bounds removal under standing x",
                initial = {
                    assertProperty(Rectangle(10, 30, 100, 40)) { bounds }
                },
                applied = {
                    assertProperty(Rectangle(10, 0, 0, 0)) { this.bounds }
                },
            ) {
                Label(
                    "a",
                    style = {
                        if (!it) bounds(20, 30, 100, 40)
                        x(10)
                    },
                )
            },
        )

        private fun sizeUnderStandingWidthCases(): List<Case> = listOf(
            case<JComponent>(
                "Size addition under standing width",
                applied = {
                    assertProperty(Dimension(10, 40)) { this.size }
                },
            ) {
                Label(
                    "a",
                    style = {
                        if (it) size(120, 40)
                        width(10)
                    },
                )
            },
        )

        private fun geometryAndColorDeclarationReorderCases(): List<Case> = listOf(
            case<JComponent>(
                "Geometry and color declaration reorder",
                initial = {
                    assertProperty(Dimension(10, 30)) { size }
                },
                applied = {
                    assertProperty(Dimension(20, 30)) { this.size }
                    assertProperty(Color.BLUE) { background }
                    assertProperty(Color.RED) { foreground }
                },
            ) {
                Label(
                    "a",
                    style = {
                        if (it) {
                            width(10)
                            size(20, 30)
                            background(Color.BLUE)
                            foreground(Color.RED)
                        } else {
                            size(20, 30)
                            width(10)
                            foreground(Color.RED)
                            background(Color.BLUE)
                        }
                    },
                )
            },
            case<JTextComponent>(
                "Initial focus removal",
                applied = {},
            ) {
                TextField("a", onValueChange = {}, modifier = if (it) SwingModifier else SwingModifier.initialFocus())
            },
            case<JComponent>(
                "Input verifier removal",
                initial = {
                    assertProperty(true) { inputVerifier != null }
                },
                applied = {
                    assertProperty(null) { this.inputVerifier }
                },
            ) {
                TextField(
                    "a",
                    onValueChange = {},
                    modifier = if (it) SwingModifier else SwingModifier.inputVerifier { false },
                )
            },
            case<JComponent>(
                "Verify input when focus target change",
                applied = {
                    assertProperty(true) { this.verifyInputWhenFocusTarget }
                },
            ) {
                TextField("a", onValueChange = {}, style = { verifyInputWhenFocusTarget(it) })
            },
        )

        private fun verifyInputWhenFocusTargetRemovalCases(): List<Case> = listOf(
            case<JComponent>(
                "Verify input when focus target removal",
                initial = {
                    assertProperty(false) { verifyInputWhenFocusTarget }
                },
                applied = {
                    assertProperty(true) { this.verifyInputWhenFocusTarget }
                },
            ) {
                TextField(
                    "a",
                    onValueChange = {},
                    style = { if (!it) verifyInputWhenFocusTarget(false) },
                )
            },
        )

        private fun caretUnderStandingBlinkRateCases(): List<Case> = listOf(
            run {
                val caret = DefaultCaret()
                case<JTextComponent>(
                    "Caret removal under standing blink rate",
                    initial = {
                        assertProperty(caret, "the declared caret reaches the field") { this.caret }
                        assertProperty(0, "the field carries the expected blink rate") { this.caret.blinkRate }
                    },
                    applied = {
                        assertProperty(false, "the removed caret is detached") { this.caret === caret }
                        assertProperty(0, "the field carries the expected blink rate") {
                            this.caret.blinkRate
                        }
                    },
                ) { changed ->
                    TextField(
                        "a",
                        onValueChange = {},
                        style = {
                            if (!changed) caret(caret)
                            caretBlinkRate(0)
                        },
                    )
                }
            },
            run {
                val caret = DefaultCaret()
                case<JTextComponent>(
                    "Caret addition under standing blink rate",
                    applied = {
                        assertProperty(caret, "the declared caret reaches the field") { this.caret }
                        assertProperty(0, "the field carries the expected blink rate") {
                            this.caret.blinkRate
                        }
                    },
                ) { changed ->
                    TextField(
                        "a",
                        onValueChange = {},
                        style = {
                            if (changed) caret(caret)
                            caretBlinkRate(0)
                        },
                    )
                }
            },
        )

        private fun caretAndBlinkRateRemovalCases(): List<Case> = listOf(
            run {
                val caret = DefaultCaret()
                case<JTextComponent>(
                    "Caret and blink rate removal",
                    initial = {
                        assertProperty(caret, "the declared caret reaches the field") { this.caret }
                        assertProperty(0, "the field carries the expected blink rate") { this.caret.blinkRate }
                    },
                    applied = {
                        assertProperty(false, "the removed caret is detached") { this.caret === caret }
                        assertProperty(
                            UIManager.getInt("TextField.caretBlinkRate"),
                            "the field carries the expected blink rate",
                        ) {
                            this.caret.blinkRate
                        }
                    },
                ) { changed ->
                    TextField(
                        "a",
                        onValueChange = {},
                        style = {
                            if (!changed) {
                                caret(caret)
                                caretBlinkRate(0)
                            }
                        },
                    )
                }
            },
        )
    }
}
