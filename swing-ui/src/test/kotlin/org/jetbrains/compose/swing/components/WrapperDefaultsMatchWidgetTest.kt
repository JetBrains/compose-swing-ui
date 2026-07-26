package org.jetbrains.compose.swing.components

import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.layout.ToolBar
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JEditorPane
import javax.swing.JFormattedTextField
import javax.swing.JLabel
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
import javax.swing.JTextPane
import javax.swing.JToggleButton
import javax.swing.JToolBar
import javax.swing.JTree
import javax.swing.SpinnerNumberModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression guard that the components' wrappers leave their underlying Swing widgets at the SAME
 * default property values the bare widget has when constructed with no arguments. For each wrapper a
 * bare widget is constructed directly (e.g. `JTextField()`) and the same widget is realized through
 * the no-argument wrapper (e.g. `TextField("")`); the properties the wrapper sets from its
 * default-valued parameters are then asserted equal across the two, so a wrapper default can never
 * silently drift away from the widget's own default.
 */
class WrapperDefaultsMatchWidgetTest {
    @Test
    fun textFieldDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JTextField()
        setContent { TextField(value = "") }
        val wrapped = onNodeOfType<JTextField>().fetch<JTextField>()
        assertEquals(bare.columns, wrapped.columns, "columns")
    }

    @Test
    fun textAreaDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JTextArea()
        setContent { TextArea(value = "") }
        val wrapped = onNodeOfType<JTextArea>().fetch<JTextArea>()
        assertEquals(bare.rows, wrapped.rows, "rows")
        assertEquals(bare.columns, wrapped.columns, "columns")
    }

    @Test
    fun passwordFieldDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JPasswordField()
        setContent { PasswordField(value = CharArray(0)) }
        val wrapped = onNodeOfType<JPasswordField>().fetch<JPasswordField>()
        assertEquals(bare.columns, wrapped.columns, "columns")
        assertEquals(bare.echoChar, wrapped.echoChar, "echoChar")
    }

    @Test
    fun editorPaneDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JEditorPane()
        setContent { EditorPane(value = "") }
        val wrapped = onNodeOfType<JEditorPane>().fetch<JEditorPane>()
        assertEquals(bare.contentType, wrapped.contentType, "contentType")
        assertEquals(bare.isEditable, wrapped.isEditable, "editable")
    }

    @Test
    fun textPaneDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JTextPane()
        setContent { TextPane(value = "") }
        val wrapped = onNodeOfType<JTextPane>().fetch<JTextPane>()
        assertEquals(bare.isEditable, wrapped.isEditable, "editable")
    }

    @Test
    fun formattedTextFieldDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JFormattedTextField()
        setContent { FormattedTextField(value = null) }
        val wrapped = onNodeOfType<JFormattedTextField>().fetch<JFormattedTextField>()
        assertEquals(bare.columns, wrapped.columns, "columns")
        assertEquals(bare.focusLostBehavior, wrapped.focusLostBehavior, "focusLostBehavior")
    }

    @Test
    fun comboBoxDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JComboBox<String>()
        setContent { ComboBox(items = emptyList<String>()) }
        val wrapped = onNodeOfType<JComboBox<*>>().fetch<JComboBox<*>>()
        assertEquals(bare.selectedIndex, wrapped.selectedIndex, "selectedIndex")
    }

    @Test
    fun sliderDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JSlider()
        setContent { Slider(value = bare.value) }
        val wrapped = onNodeOfType<JSlider>().fetch<JSlider>()
        assertEquals(bare.minimum, wrapped.minimum, "minimum")
        assertEquals(bare.maximum, wrapped.maximum, "maximum")
    }

    @Test
    fun spinnerDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JSpinner().model as SpinnerNumberModel
        setContent { Spinner(rememberSpinnerState(value = bare.number.toInt())) }
        val wrapped = onNodeOfType<JSpinner>().fetch<JSpinner>().model as SpinnerNumberModel
        assertEquals(bare.value, wrapped.value, "value")
        assertEquals(bare.stepSize, wrapped.stepSize, "stepSize")
        assertEquals(bare.minimum, wrapped.minimum, "minimum")
        assertEquals(bare.maximum, wrapped.maximum, "maximum")
    }

    @Test
    fun progressBarDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JProgressBar()
        setContent { ProgressBar() }
        val wrapped = onNodeOfType<JProgressBar>().fetch<JProgressBar>()
        assertEquals(bare.minimum, wrapped.minimum, "minimum")
        assertEquals(bare.maximum, wrapped.maximum, "maximum")
        assertEquals(bare.value, wrapped.value, "value")
        assertEquals(bare.isIndeterminate, wrapped.isIndeterminate, "indeterminate")
    }

    @Test
    fun labelDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JLabel()
        setContent { Label(text = "") }
        val wrapped = onNodeOfType<JLabel>().fetch<JLabel>()
        assertEquals(bare.horizontalAlignment, wrapped.horizontalAlignment, "horizontalAlignment")
    }

    @Test
    fun separatorDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JSeparator()
        setContent { Separator() }
        val wrapped = onNodeOfType<JSeparator>().fetch<JSeparator>()
        assertEquals(bare.orientation, wrapped.orientation, "orientation")
    }

    @Test
    fun buttonDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JButton()
        setContent { Button(text = "") }
        val wrapped = onNodeOfType<JButton>().fetch<JButton>()
        assertEquals(bare.isEnabled, wrapped.isEnabled, "enabled")
    }

    @Test
    fun checkBoxDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JCheckBox()
        setContent { CheckBox(text = "") }
        val wrapped = onNodeOfType<JCheckBox>().fetch<JCheckBox>()
        assertEquals(bare.isSelected, wrapped.isSelected, "selected")
    }

    @Test
    fun radioButtonDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JRadioButton()
        setContent { RadioButton(text = "") }
        val wrapped = onNodeOfType<JRadioButton>().fetch<JRadioButton>()
        assertEquals(bare.isSelected, wrapped.isSelected, "selected")
    }

    @Test
    fun toggleButtonDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JToggleButton()
        setContent { ToggleButton(text = "") }
        val wrapped = onNodeOfType<JToggleButton>().fetch<JToggleButton>()
        assertEquals(bare.isSelected, wrapped.isSelected, "selected")
    }

    @Test
    fun toolBarDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JToolBar()
        setContent { ToolBar() }
        val wrapped = onNodeOfType<JToolBar>().fetch<JToolBar>()
        assertEquals(bare.orientation, wrapped.orientation, "orientation")
        assertEquals(bare.isFloatable, wrapped.isFloatable, "floatable")
    }

    @Test
    fun listBoxDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JList<String>()
        setContent { ListBox(items = emptyList<String>()) }
        val wrapped = onNodeOfType<JList<*>>().fetch<JList<*>>()
        assertEquals(bare.selectionMode, wrapped.selectionMode, "selectionMode")
        assertEquals(bare.visibleRowCount, wrapped.visibleRowCount, "visibleRowCount")
    }

    @Test
    fun tableSelectionModeMatchesBareWidget() = runComposeSwingTest {
        val bare = JTable()
        setContent {
            Table(rows = emptyList<String>()) { column("c") { it } }
        }
        val wrapped = onNodeOfType<JTable>().fetch<JTable>()
        assertEquals(
            bare.selectionModel.selectionMode,
            wrapped.selectionModel.selectionMode,
            "selectionMode",
        )
    }

    @Test
    fun treeSelectionAndHandleDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JTree()
        setContent { Tree(root = "r", children = { emptyList() }) }
        val wrapped = onNodeOfType<JTree>().fetch<JTree>()
        assertEquals(
            bare.selectionModel.selectionMode,
            wrapped.selectionModel.selectionMode,
            "selectionMode",
        )
        assertEquals(bare.isRootVisible, wrapped.isRootVisible, "rootVisible")
        assertEquals(bare.showsRootHandles, wrapped.showsRootHandles, "showsRootHandles")
    }

    @Test
    fun tabbedPaneDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JTabbedPane()
        setContent { TabbedPane(selectedIndex = -1) {} }
        val wrapped = onNodeOfType<JTabbedPane>().fetch<JTabbedPane>()
        assertEquals(bare.tabPlacement, wrapped.tabPlacement, "tabPlacement")
        assertEquals(bare.tabLayoutPolicy, wrapped.tabLayoutPolicy, "tabLayoutPolicy")
    }

    @Test
    fun splitPaneDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JSplitPane()
        setContent { SplitPane {} }
        val wrapped = onNodeOfType<JSplitPane>().fetch<JSplitPane>()
        assertEquals(bare.orientation, wrapped.orientation, "orientation")
        assertEquals(bare.resizeWeight, wrapped.resizeWeight, "resizeWeight")
    }

    @Test
    fun scrollPaneDefaultsMatchBareWidget() = runComposeSwingTest {
        val bare = JScrollPane()
        setContent { ScrollPane {} }
        val wrapped = onNodeOfType<JScrollPane>().fetch<JScrollPane>()
        assertEquals(
            bare.verticalScrollBarPolicy,
            wrapped.verticalScrollBarPolicy,
            "verticalScrollBarPolicy",
        )
        assertEquals(
            bare.horizontalScrollBarPolicy,
            wrapped.horizontalScrollBarPolicy,
            "horizontalScrollBarPolicy",
        )
    }
}
