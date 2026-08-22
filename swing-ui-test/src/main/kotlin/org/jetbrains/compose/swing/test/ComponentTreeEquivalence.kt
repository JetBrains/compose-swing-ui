package org.jetbrains.compose.swing.test

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import javax.swing.AbstractButton
import javax.swing.BoxLayout
import javax.swing.CellRendererPane
import javax.swing.DefaultButtonModel
import javax.swing.DefaultRowSorter
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JFormattedTextField
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JList
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPasswordField
import javax.swing.JPopupMenu
import javax.swing.JProgressBar
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
import javax.swing.ListModel
import javax.swing.SpinnerDateModel
import javax.swing.SpinnerNumberModel
import javax.swing.plaf.basic.BasicToolBarUI
import javax.swing.table.TableColumn
import javax.swing.text.AbstractDocument
import javax.swing.text.DefaultCaret
import javax.swing.text.Highlighter
import javax.swing.text.JTextComponent
import javax.swing.text.html.HTMLDocument

/**
 * Asserts that two component trees describe the same user interface, laid out as they stand.
 *
 * The public form is
 * [assertTreeMatches][org.jetbrains.compose.swing.test.interaction.assertTreeMatches], whose KDoc
 * carries what is compared and what is deliberately not.
 *
 * Must be called on the Event Dispatch Thread.
 *
 * @param expected the reference tree.
 * @param actual the tree under test.
 * @param allowSubclasses when true, a class in [actual] matches the one in [expected] as long as it extends
 *   it; when false, the two classes must be equal. Applies to the widget type and to everything else
 *   compared by its class - the border, the layout manager and the collaborators compared that way.
 * @throws AssertionError naming the first differing property, its path from the root of the two trees,
 *   and a dump of both trees.
 */
internal fun assertComponentTreesEquivalent(
    expected: Component,
    actual: Component,
    allowSubclasses: Boolean = true,
) {
    val divergence =
        firstDivergence(expected, actual, rootSegment(expected), allowSubclasses, ROOT_PROPERTIES) ?: return
    throw AssertionError(
        "Component trees are not equivalent.\n" +
            "First difference at ${divergence.path}\n" +
            "  ${divergence.property}: expected <${divergence.expected}>, " +
            "actual <${divergence.actual}>\n" +
            "Expected tree:\n${dumpOf(expected)}" +
            "Actual tree:\n${dumpOf(actual)}",
    )
}

/**
 * State a widget of one kind holds, compared only where both nodes are that kind.
 *
 * [read] is handed a component [type] has already accepted, which is what makes its cast safe.
 */
private class WidgetState(
    private val type: Class<*>,
    val property: ComparedProperty,
) {
    fun appliesTo(
        expected: Component,
        actual: Component,
    ): Boolean = type.isInstance(expected) && type.isInstance(actual)
}

@Suppress("UNCHECKED_CAST")
private inline fun <reified T : Component> widgetState(
    name: String,
    crossinline read: (T) -> Any?,
): WidgetState = WidgetState(T::class.java, ComparedProperty(name) { read(it as T) })

/**
 * What every component and container holds, beyond the properties compared at each node.
 *
 * A collaborator a widget delegates to is compared by what it holds where the comparison reaches that: a
 * model by the elements, rows or value it stands for, a document by its text. One that holds nothing of
 * its own - a renderer, a caret, a transfer handler, a policy - carries no value equality, so it is
 * compared by its class the way a border is: two trees built apart hold two such objects and would never
 * match by identity. A value the caller hands to both trees, an icon among them, is compared as it
 * stands.
 */
private val COMMON_STATE: List<WidgetState> =
    listOf(
        widgetState<Component>("name") { it.name },
        widgetState<Component>("cursor") { if (it.isCursorSet) it.cursor else null },
        widgetState<Component>("focusable") { it.isFocusable },
        widgetState<Component>("componentOrientation") { it.componentOrientation },
        widgetState<Component>("preferredSize") { if (it.isPreferredSizeSet) it.preferredSize else null },
        widgetState<Component>("minimumSize") { if (it.isMinimumSizeSet) it.minimumSize else null },
        widgetState<Component>("maximumSize") { if (it.isMaximumSizeSet) it.maximumSize else null },
        widgetState<Container>("focusTraversalPolicy") { it.focusTraversalPolicy?.javaClass },
        widgetState<Container>("focusTraversalPolicyProvider") { it.isFocusTraversalPolicyProvider },
        widgetState<JComponent>("toolTipText") { it.toolTipText },
        widgetState<JComponent>("alignmentX") { it.alignmentX },
        widgetState<JComponent>("alignmentY") { it.alignmentY },
        widgetState<JComponent>("inputVerifier") { it.inputVerifier?.javaClass },
        widgetState<JComponent>("verifyInputWhenFocusTarget") { it.verifyInputWhenFocusTarget },
        widgetState<JComponent>("transferHandler") { it.transferHandler?.javaClass },
        widgetState<JComponent>("keyStrokes") { it.boundKeyStrokes() },
    )

/** What a stock layout manager holds, read from the container it lays out along with its constraints. */
private val LAYOUT_STATE: List<WidgetState> =
    listOf(
        widgetState<Container>("borderLayout.hgap") { (it.layout as? BorderLayout)?.hgap },
        widgetState<Container>("borderLayout.vgap") { (it.layout as? BorderLayout)?.vgap },
        widgetState<Container>("borderLayout.constraints") { container ->
            (container.layout as? BorderLayout)?.let { layout ->
                container.components.map { child -> layout.getConstraints(child) }
            }
        },
        widgetState<Container>("flowLayout.alignment") { (it.layout as? FlowLayout)?.alignment },
        widgetState<Container>("flowLayout.hgap") { (it.layout as? FlowLayout)?.hgap },
        widgetState<Container>("flowLayout.vgap") { (it.layout as? FlowLayout)?.vgap },
        widgetState<Container>("gridLayout.rows") { (it.layout as? GridLayout)?.rows },
        widgetState<Container>("gridLayout.columns") { (it.layout as? GridLayout)?.columns },
        widgetState<Container>("gridLayout.hgap") { (it.layout as? GridLayout)?.hgap },
        widgetState<Container>("gridLayout.vgap") { (it.layout as? GridLayout)?.vgap },
        widgetState<Container>("boxLayout.axis") { (it.layout as? BoxLayout)?.axis },
        widgetState<Container>("gridBagLayout.constraints") { container ->
            (container.layout as? GridBagLayout)?.let { layout ->
                container.components.map { child -> layout.getConstraints(child).describe() }
            }
        },
        widgetState<JLayeredPane>("layers") { pane -> pane.components.map { child -> pane.getLayer(child) } },
    )

/** What a label, a button and a menu item hold. */
private val LABEL_AND_BUTTON_STATE: List<WidgetState> =
    listOf(
        widgetState<JLabel>("icon") { it.icon },
        widgetState<JLabel>("horizontalAlignment") { it.horizontalAlignment },
        widgetState<JLabel>("verticalAlignment") { it.verticalAlignment },
        widgetState<JLabel>("horizontalTextPosition") { it.horizontalTextPosition },
        widgetState<JLabel>("verticalTextPosition") { it.verticalTextPosition },
        widgetState<JLabel>("iconTextGap") { it.iconTextGap },
        widgetState<JLabel>("displayedMnemonic") { it.displayedMnemonic },
        widgetState<JLabel>("displayedMnemonicIndex") { it.displayedMnemonicIndex },
        widgetState<JLabel>("labelFor") { it.labelFor?.javaClass },
        widgetState<AbstractButton>("selected") { it.isSelected },
        widgetState<AbstractButton>("icon") { it.icon },
        widgetState<AbstractButton>("pressedIcon") { it.pressedIcon },
        widgetState<AbstractButton>("selectedIcon") { it.selectedIcon },
        widgetState<AbstractButton>("rolloverIcon") { it.rolloverIcon },
        widgetState<AbstractButton>("rolloverSelectedIcon") { it.rolloverSelectedIcon },
        widgetState<AbstractButton>("horizontalAlignment") { it.horizontalAlignment },
        widgetState<AbstractButton>("verticalAlignment") { it.verticalAlignment },
        widgetState<AbstractButton>("horizontalTextPosition") { it.horizontalTextPosition },
        widgetState<AbstractButton>("verticalTextPosition") { it.verticalTextPosition },
        widgetState<AbstractButton>("iconTextGap") { it.iconTextGap },
        widgetState<AbstractButton>("margin") { it.margin },
        widgetState<AbstractButton>("mnemonic") { it.mnemonic },
        widgetState<AbstractButton>("displayedMnemonicIndex") { it.displayedMnemonicIndex },
        widgetState<AbstractButton>("contentAreaFilled") { it.isContentAreaFilled },
        widgetState<AbstractButton>("rolloverEnabled") { it.isRolloverEnabled },
        widgetState<AbstractButton>("focusPainted") { it.isFocusPainted },
        widgetState<AbstractButton>("borderPainted") { it.isBorderPainted },
        widgetState<AbstractButton>("actionCommand") { it.actionCommand },
        widgetState<AbstractButton>("buttonGroup") { (it.model as? DefaultButtonModel)?.group?.buttonCount },
        widgetState<JMenuItem>("accelerator") { it.accelerator },
    )

/** What a text component holds, including the document and the caret it is edited through. */
private val TEXT_STATE: List<WidgetState> =
    listOf(
        widgetState<JTextComponent>("editable") { it.isEditable },
        widgetState<JTextComponent>("margin") { it.margin },
        widgetState<JTextComponent>("caretColor") { it.caretColor },
        widgetState<JTextComponent>("selectionColor") { it.selectionColor },
        widgetState<JTextComponent>("selectedTextColor") { it.selectedTextColor },
        widgetState<JTextComponent>("disabledTextColor") { it.disabledTextColor },
        widgetState<JTextComponent>("caret") { it.caret?.javaClass },
        widgetState<JTextComponent>("caretBlinkRate") { it.caret?.blinkRate },
        widgetState<JTextComponent>("caretUpdatePolicy") { (it.caret as? DefaultCaret)?.updatePolicy },
        widgetState<JTextComponent>("caretPosition") { it.caretPosition },
        widgetState<JTextComponent>("selection") { it.selectionStart to it.selectionEnd },
        widgetState<JTextComponent>("navigationFilter") { it.navigationFilter?.javaClass },
        widgetState<JTextComponent>("focusAccelerator") { it.focusAccelerator },
        widgetState<JTextComponent>("documentFilter") { (it.document as? AbstractDocument)?.documentFilter?.javaClass },
        widgetState<JTextComponent>("highlights") { it.highlighter?.highlights?.map { range -> range.describe() } },
        widgetState<JTextField>("columns") { it.columns },
        widgetState<JTextField>("horizontalAlignment") { it.horizontalAlignment },
        widgetState<JPasswordField>("echoChar") { it.echoChar },
        widgetState<JTextArea>("rows") { it.rows },
        widgetState<JTextArea>("columns") { it.columns },
        widgetState<JTextArea>("lineWrap") { it.lineWrap },
        widgetState<JTextArea>("wrapStyleWord") { it.wrapStyleWord },
        widgetState<JTextArea>("tabSize") { it.tabSize },
        widgetState<JFormattedTextField>("focusLostBehavior") { it.focusLostBehavior },
        widgetState<JFormattedTextField>("formatterFactory") { it.formatterFactory?.javaClass },
        widgetState<JFormattedTextField>("value") { it.value },
        widgetState<JEditorPane>("contentType") { it.contentType },
        widgetState<JEditorPane>("editorKit") { it.editorKit?.javaClass },
        widgetState<JEditorPane>("documentBase") { (it.document as? HTMLDocument)?.base },
    )

/** What a widget the user picks a value in holds, from the range it offers to what stands selected. */
private val SELECTION_STATE: List<WidgetState> =
    listOf(
        widgetState<JComboBox<*>>("selectedIndex") { it.selectedIndex },
        widgetState<JComboBox<*>>("selectedItem") { it.selectedItem },
        widgetState<JComboBox<*>>("editable") { it.isEditable },
        widgetState<JComboBox<*>>("maximumRowCount") { it.maximumRowCount },
        widgetState<JComboBox<*>>("items") { it.model?.elements() },
        widgetState<JComboBox<*>>("renderer") { it.renderer?.javaClass },
        widgetState<JSlider>("value") { it.value },
        widgetState<JSlider>("range") { it.minimum to it.maximum },
        widgetState<JSlider>("orientation") { it.orientation },
        widgetState<JSlider>("inverted") { it.inverted },
        widgetState<JSlider>("paintTicks") { it.paintTicks },
        widgetState<JSlider>("paintLabels") { it.paintLabels },
        widgetState<JSlider>("snapToTicks") { it.snapToTicks },
        widgetState<JSlider>("tickSpacing") { it.majorTickSpacing to it.minorTickSpacing },
        widgetState<JSlider>("labels") { it.labelPositions() },
        widgetState<JSpinner>("value") { it.value },
        widgetState<JSpinner>("editor") { it.editor?.javaClass },
        widgetState<JSpinner>("stepSize") { (it.model as? SpinnerNumberModel)?.stepSize },
        widgetState<JSpinner>("range") { (it.model as? SpinnerNumberModel)?.let { m -> m.minimum to m.maximum } },
        widgetState<JSpinner>("dateRange") { (it.model as? SpinnerDateModel)?.let { m -> m.start to m.end } },
        widgetState<JSpinner>("calendarField") { (it.model as? SpinnerDateModel)?.calendarField },
        widgetState<JProgressBar>("value") { it.value },
        widgetState<JProgressBar>("range") { it.minimum to it.maximum },
        widgetState<JProgressBar>("orientation") { it.orientation },
        widgetState<JProgressBar>("indeterminate") { it.isIndeterminate },
        widgetState<JProgressBar>("stringPainted") { it.isStringPainted },
        widgetState<JProgressBar>("string") { it.string },
        widgetState<JProgressBar>("borderPainted") { it.isBorderPainted },
        widgetState<JList<*>>("itemCount") { it.model.size },
        widgetState<JList<*>>("items") { it.model.elements() },
        widgetState<JList<*>>("selectionMode") { it.selectionMode },
        widgetState<JList<*>>("visibleRowCount") { it.visibleRowCount },
        widgetState<JList<*>>("layoutOrientation") { it.layoutOrientation },
        widgetState<JList<*>>("fixedCellSize") { it.fixedCellWidth to it.fixedCellHeight },
        widgetState<JList<*>>("prototypeCellValue") { it.prototypeCellValue },
        widgetState<JList<*>>("cellRenderer") { it.cellRenderer?.javaClass },
        widgetState<JList<*>>("selectedIndices") { it.selectedIndices.toList() },
    )

/** What a table and a tree hold, including the columns and rows they are read through. */
private val TABLE_AND_TREE_STATE: List<WidgetState> =
    listOf(
        widgetState<JTable>("size") { it.rowCount to it.columnCount },
        widgetState<JTable>("cells") { table ->
            table.model?.let { model ->
                bounded(model.rowCount).map { row ->
                    bounded(model.columnCount).map { column -> model.getValueAt(row, column) }
                }
            }
        },
        widgetState<JTable>("columnNames") { table ->
            table.model?.let { model -> bounded(model.columnCount).map(model::getColumnName) }
        },
        widgetState<JTable>("selectionMode") { it.selectionModel.selectionMode },
        widgetState<JTable>("columnSelectionMode") { it.columnModel.selectionModel.selectionMode },
        widgetState<JTable>("autoResizeMode") { it.autoResizeMode },
        widgetState<JTable>("rowHeight") { it.rowHeight },
        widgetState<JTable>("fillsViewportHeight") { it.fillsViewportHeight },
        widgetState<JTable>("rowSorter") { it.rowSorter?.javaClass },
        widgetState<JTable>("sortKeys") { it.rowSorter?.sortKeys },
        widgetState<JTable>("sortableColumns") { table ->
            (table.rowSorter as? DefaultRowSorter<*, *>)?.let { sorter ->
                (0 until table.columnModel.columnCount).map(sorter::isSortable)
            }
        },
        widgetState<JTable>("columnHeaders") { table -> table.perColumn(TableColumn::getHeaderValue) },
        widgetState<JTable>("columnPreferredWidths") { table -> table.perColumn(TableColumn::getPreferredWidth) },
        widgetState<JTable>("columnMinWidths") { table -> table.perColumn(TableColumn::getMinWidth) },
        widgetState<JTable>("columnMaxWidths") { table -> table.perColumn(TableColumn::getMaxWidth) },
        widgetState<JTable>("columnOrder") { table -> table.perColumn(TableColumn::getModelIndex) },
        widgetState<JTable>("selectedRows") { it.selectedRows.toList() },
        widgetState<JTree>("rowCount") { it.rowCount },
        widgetState<JTree>("rowPaths") { tree -> bounded(tree.rowCount).map { tree.getPathForRow(it).toString() } },
        widgetState<JTree>("selectionMode") { it.selectionModel.selectionMode },
        widgetState<JTree>("editable") { it.isEditable },
        widgetState<JTree>("rowHeight") { it.rowHeight },
        widgetState<JTree>("visibleRowCount") { it.visibleRowCount },
        widgetState<JTree>("toggleClickCount") { it.toggleClickCount },
        widgetState<JTree>("rootVisible") { it.isRootVisible },
        widgetState<JTree>("showsRootHandles") { it.showsRootHandles },
        widgetState<JTree>("cellRenderer") { it.cellRenderer?.javaClass },
        widgetState<JTree>("selectionRows") { it.selectionRows?.toList().orEmpty() },
        widgetState<JTree>("selectionPaths") { it.selectionPaths?.map(Any::toString) },
        widgetState<JTree>("expandedRows") { tree -> (0 until tree.rowCount).filter(tree::isExpanded) },
    )

/** What a container built around other components holds about them. */
private val ARRANGEMENT_STATE: List<WidgetState> =
    listOf(
        widgetState<JTabbedPane>("selectedIndex") { it.selectedIndex },
        widgetState<JTabbedPane>("tabPlacement") { it.tabPlacement },
        widgetState<JTabbedPane>("tabLayoutPolicy") { it.tabLayoutPolicy },
        widgetState<JTabbedPane>("tabTitles") { pane -> pane.perTab(pane::getTitleAt) },
        widgetState<JTabbedPane>("tabIcons") { pane -> pane.perTab(pane::getIconAt) },
        widgetState<JTabbedPane>("tabToolTips") { pane -> pane.perTab(pane::getToolTipTextAt) },
        widgetState<JTabbedPane>("tabsEnabled") { pane -> pane.perTab(pane::isEnabledAt) },
        widgetState<JTabbedPane>("tabMnemonics") { pane -> pane.perTab(pane::getMnemonicAt) },
        widgetState<JTabbedPane>("tabMnemonicIndices") { pane -> pane.perTab(pane::getDisplayedMnemonicIndexAt) },
        widgetState<JTabbedPane>("tabBackgrounds") { pane -> pane.perTab(pane::getBackgroundAt) },
        widgetState<JTabbedPane>("tabForegrounds") { pane -> pane.perTab(pane::getForegroundAt) },
        widgetState<JSplitPane>("orientation") { it.orientation },
        widgetState<JSplitPane>("resizeWeight") { it.resizeWeight },
        widgetState<JSplitPane>("dividerSize") { it.dividerSize },
        widgetState<JSplitPane>("oneTouchExpandable") { it.isOneTouchExpandable },
        widgetState<JSplitPane>("continuousLayout") { it.isContinuousLayout },
        widgetState<JSplitPane>("dividerLocation") { it.dividerLocation },
        widgetState<JScrollPane>("verticalScrollBarPolicy") { it.verticalScrollBarPolicy },
        widgetState<JScrollPane>("horizontalScrollBarPolicy") { it.horizontalScrollBarPolicy },
        widgetState<JScrollPane>("viewportBorder") { it.viewportBorder?.javaClass },
        widgetState<JScrollPane>("wheelScrollingEnabled") { it.isWheelScrollingEnabled },
        widgetState<JScrollPane>("viewPosition") { it.viewport?.viewPosition },
        widgetState<JScrollPane>("parts") { it.scrollPaneParts() },
        widgetState<JInternalFrame>("title") { it.title },
        widgetState<JInternalFrame>("closable") { it.isClosable },
        widgetState<JInternalFrame>("resizable") { it.isResizable },
        widgetState<JInternalFrame>("maximizable") { it.isMaximizable },
        widgetState<JInternalFrame>("iconifiable") { it.isIconifiable },
        widgetState<JInternalFrame>("maximum") { it.isMaximum },
        widgetState<JInternalFrame>("icon") { it.isIcon },
        widgetState<JSeparator>("orientation") { it.orientation },
        widgetState<JToolBar.Separator>("separatorSize") { it.separatorSize },
        widgetState<JToolBar>("orientation") { it.orientation },
        widgetState<JToolBar>("floatable") { it.isFloatable },
        widgetState<JToolBar>("rollover") { it.isRollover },
        widgetState<JToolBar>("floating") { (it.ui as? BasicToolBarUI)?.isFloating },
        widgetState<JToolBar>("borderPainted") { it.isBorderPainted },
        widgetState<JMenuBar>("borderPainted") { it.isBorderPainted },
        widgetState<JPopupMenu>("borderPainted") { it.isBorderPainted },
    )

/**
 * What a widget answers assistive technology with, read last: the first read of a component's accessible
 * context builds it, and a combo box builds one by naming its own editor.
 */
private val ACCESSIBILITY_STATE: List<WidgetState> =
    listOf(
        widgetState<Component>("accessibleName") { it.accessibleContext?.accessibleName },
        widgetState<Component>("accessibleDescription") { it.accessibleContext?.accessibleDescription },
    )

/**
 * The state each widget holds, beyond the properties compared at every node.
 *
 * Arrays are compared as lists, because two equal selections are two different arrays.
 */
private val WIDGET_STATE: List<WidgetState> =
    COMMON_STATE + LAYOUT_STATE + LABEL_AND_BUTTON_STATE + TEXT_STATE + SELECTION_STATE +
        TABLE_AND_TREE_STATE + ARRANGEMENT_STATE + ACCESSIBILITY_STATE

/** One differing property, and where in the two trees it was found. */
private class Divergence(
    val path: String,
    val property: String,
    val expected: String,
    val actual: String,
)

/** A property both trees are compared on, as the name it is reported under and how it is read. */
private class ComparedProperty(
    val name: String,
    val read: (Component) -> Any?,
)

/**
 * What is compared at one node, in report order, with [placement] standing where the node's geometry is
 * compared.
 *
 * `children` comes last so a node that differs is reported by what differs about it rather than by its
 * child count, and so the walk only pairs children up once both nodes have as many.
 */
private fun comparedProperties(placement: ComparedProperty): List<ComparedProperty> =
    listOf(
        ComparedProperty("type") { it.javaClass },
        placement,
        ComparedProperty("visible") { it.isVisible },
        ComparedProperty("enabled") { it.isEnabled },
        ComparedProperty("opaque") { (it as? JComponent)?.isOpaque },
        ComparedProperty("font") { if (it.isFontSet) it.font else null },
        ComparedProperty("foreground") { if (it.isForegroundSet) it.foreground else null },
        ComparedProperty("background") { if (it.isBackgroundSet) it.background else null },
        ComparedProperty("insets") { (it as? Container)?.insets },
        ComparedProperty("border") { (it as? JComponent)?.border?.javaClass },
        ComparedProperty("layout") { (it as? Container)?.layout?.javaClass },
        ComparedProperty("text") { it.textOrNull() },
        ComparedProperty("children") { it.comparedChildren().size },
    )

/** A component's bounds are relative to its parent, which is what makes them comparable below the root. */
private val DESCENDANT_PROPERTIES: List<ComparedProperty> =
    comparedProperties(ComparedProperty("bounds") { it.bounds })

/**
 * The two roots stand in trees of their own - a composed node inside the tree that holds it, a reference
 * in nothing at all - so their positions say nothing about each other and only their size is compared.
 */
private val ROOT_PROPERTIES: List<ComparedProperty> =
    comparedProperties(ComparedProperty("size") { it.size })

/** The first difference between the two subtrees, in depth-first pre-order, or null when there is none. */
private fun firstDivergence(
    expected: Component,
    actual: Component,
    path: String,
    allowSubclasses: Boolean,
    compared: List<ComparedProperty> = DESCENDANT_PROPERTIES,
): Divergence? =
    divergentProperty(expected, actual, path, allowSubclasses, compared)
        ?: divergentChild(expected, actual, path, allowSubclasses)

/** The first of [compared] the two components disagree on, or null when they agree on all. */
private fun divergentProperty(
    expected: Component,
    actual: Component,
    path: String,
    allowSubclasses: Boolean,
    compared: List<ComparedProperty>,
): Divergence? {
    val properties =
        compared.asSequence() +
            WIDGET_STATE.asSequence().filter { it.appliesTo(expected, actual) }.map { it.property }
    return properties.firstNotNullOfOrNull { property ->
        val expectedValue = property.read(expected)
        val actualValue = property.read(actual)
        if (agree(expectedValue, actualValue, allowSubclasses)) {
            null
        } else {
            Divergence(path, property.name, render(expectedValue), render(actualValue))
        }
    }
}

/**
 * Whether the two read values stand for the same thing.
 *
 * A class is matched by assignability under [allowSubclasses], so a reference naming a JDK class matches
 * the subclass the library builds; everything else is matched by value.
 */
private fun agree(
    expected: Any?,
    actual: Any?,
    allowSubclasses: Boolean,
): Boolean =
    when {
        expected !is Class<*> || actual !is Class<*> -> expected == actual
        allowSubclasses -> expected.isAssignableFrom(actual)
        else -> expected == actual
    }

/** A read value as a failure message names it: a class by its short name, anything else by its own. */
private fun render(value: Any?): String = if (value is Class<*>) value.shortName else value.toString()

/**
 * The first difference below the two components, or null when there is none. Both hold as many children
 * as each other, which is what the `children` property established before this runs.
 */
private fun divergentChild(
    expected: Component,
    actual: Component,
    path: String,
    allowSubclasses: Boolean,
): Divergence? {
    val expectedChildren = expected.comparedChildren()
    val actualChildren = actual.comparedChildren()
    return expectedChildren.indices.firstNotNullOfOrNull { index ->
        val child = expectedChildren[index]
        firstDivergence(child, actualChildren[index], "$path > [$index]${child.javaClass.shortName}", allowSubclasses)
    }
}

/**
 * The children a comparison walks. A [CellRendererPane] holds none of them: it is where a list, a table
 * or a tree parks the one component it stamps every cell with, so what stands in it is whichever cell
 * was painted last rather than anything the interface declares.
 */
private fun Component.comparedChildren(): List<Component> =
    if (this is CellRendererPane) emptyList() else childComponents()

private fun rootSegment(component: Component): String = "root(${component.javaClass.shortName})"

/** A class's own short name, falling back to the last segment of its full name for an anonymous class. */
private val Class<*>.shortName: String
    get() = simpleName.ifEmpty { name.substringAfterLast('.') }

/**
 * The key strokes bound on this component itself, under the two conditions a binding is read without
 * building anything: asking for the window-wide map registers the component with the keyboard manager.
 */
private fun JComponent.boundKeyStrokes(): List<String> =
    listOf(JComponent.WHEN_FOCUSED, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .flatMap { condition -> getInputMap(condition)?.keys()?.map { "$condition:$it" }.orEmpty() }
        .sorted()

/**
 * Which of the slots a scroll pane holds a component in are filled. The components themselves are
 * compared where the walk reaches them as children; the slot each one stands in is what only the pane
 * knows.
 */
private fun JScrollPane.scrollPaneParts(): List<Boolean> =
    listOf(
        viewport?.view != null,
        rowHeader?.view != null,
        columnHeader?.view != null,
    ) + SCROLL_PANE_CORNERS.map { getCorner(it) != null }

private val SCROLL_PANE_CORNERS: List<String> =
    listOf(
        JScrollPane.UPPER_LEADING_CORNER,
        JScrollPane.UPPER_TRAILING_CORNER,
        JScrollPane.LOWER_LEADING_CORNER,
        JScrollPane.LOWER_TRAILING_CORNER,
    )

/** How much of a model the comparison reads, so a lazy or unbounded one cannot hang it. */
private const val MAX_COMPARED_ELEMENTS = 1000

/** The indices of the first [MAX_COMPARED_ELEMENTS] of [count]. */
private fun bounded(count: Int): IntRange = 0 until minOf(count, MAX_COMPARED_ELEMENTS)

/** What a list model stands for, which is what makes two models built apart comparable. */
private fun ListModel<*>.elements(): List<Any?> = bounded(size).map(::getElementAt)

private fun <T> JTable.perColumn(read: (TableColumn) -> T): List<T> =
    (0 until columnModel.columnCount).map { read(columnModel.getColumn(it)) }

private fun <T> JTabbedPane.perTab(read: (Int) -> T): List<T> = (0 until tabCount).map(read)

/** Grid bag constraints carry no equality of their own, so they are compared as what they say. */
private fun GridBagConstraints.describe(): String =
    "grid=($gridx,$gridy) span=($gridwidth,$gridheight) weight=($weightx,$weighty) " +
        "anchor=$anchor fill=$fill insets=$insets ipad=($ipadx,$ipady)"

/** A highlight carries no equality of its own, so it is compared as the range and painter it names. */
private fun Highlighter.Highlight.describe(): String = "$startOffset..$endOffset by ${painter?.javaClass?.name}"

/** The positions a slider holds a label at, which is what a label table is comparable on. */
private fun JSlider.labelPositions(): List<String>? =
    labelTable
        ?.keys()
        ?.toList()
        ?.map(Any::toString)
        ?.sorted()

private fun dumpOf(component: Component): String =
    (component as? Container)?.dumpTree() ?: (describeComponent(component) + "\n")
