package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionListener
import java.beans.PropertyChangeListener
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JEditorPane
import javax.swing.JFormattedTextField
import javax.swing.JList
import javax.swing.JPasswordField
import javax.swing.JRadioButton
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.JToggleButton
import javax.swing.JTree
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.ListSelectionListener
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.text.AbstractDocument
import javax.swing.text.JTextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the raw-listener composable overloads: each component that ships an
 * `onXxx` lambda also ships an overload taking the underlying Swing listener object. Each test asserts
 * the exact instance passed is registered on the live component through the matching `getXxxListeners()`
 * accessor (or, for the text components, on the field's document) - the observable proof the overload
 * wires the listener to the right registration site. A component whose callbacks carry the user's
 * changes only reaches its declared listener behind a gate that keeps the composition's own writes out
 * of it, so the instance cannot be registered directly; those overloads are asserted through the
 * notification instead - driving the widget the way the user would has to reach the instance passed.
 */
class RawComponentListenerOverloadTest {
    private fun docListener(): DocumentListener = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) = Unit

        override fun removeUpdate(e: DocumentEvent?) = Unit

        override fun changedUpdate(e: DocumentEvent?) = Unit
    }

    private fun JTextComponent.documentHas(listener: DocumentListener): Boolean {
        val document = document
        return document is AbstractDocument && document.documentListeners.any { it === listener }
    }

    @Test
    fun buttonActionListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = ActionListener { }
        setContent { Button("X", actionListener = listener) }
        assertTrue(
            onNodeOfType<JButton>().fetch().actionListeners.any { it === listener },
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun checkBoxActionListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = ActionListener { }
        setContent { CheckBox("X", actionListener = listener, checked = false) }
        assertTrue(
            onNodeOfType<JCheckBox>().fetch().actionListeners.any { it === listener },
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun radioButtonActionListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = ActionListener { }
        setContent { RadioButton("X", actionListener = listener) }
        assertTrue(
            onNodeOfType<JRadioButton>().fetch().actionListeners.any { it === listener },
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun toggleButtonActionListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = ActionListener { }
        setContent { ToggleButton("X", actionListener = listener) }
        assertTrue(
            onNodeOfType<JToggleButton>().fetch().actionListeners.any { it === listener },
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun comboBoxActionListenerOverloadNotifiesInstance() = runComposeSwingTest {
        var notified = false
        val listener = ActionListener { notified = true }
        setContent { ComboBox(items = listOf("a", "b"), actionListener = listener, selectedIndex = 0) }
        onNodeOfType<JComboBox<*>>().fetch<JComboBox<*>>().selectedIndex = 1
        assertTrue(notified, "driving the widget the way the user would should reach the declared listener")
    }

    @Test
    fun sliderChangeListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = ChangeListener { }
        setContent { Slider(value = 5, changeListener = listener) }
        assertTrue(
            onNodeOfType<JSlider>().fetch().changeListeners.any { it === listener },
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun spinnerChangeListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = ChangeListener { }
        setContent { Spinner(model = SpinnerNumberModel(1, 0, 10, 1), changeListener = listener) }
        assertTrue(
            onNodeOfType<JSpinner>().fetch().changeListeners.any { it === listener },
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun tabbedPaneChangeListenerOverloadNotifiesInstance() = runComposeSwingTest {
        var notified = 0
        val listener = ChangeListener { notified++ }
        setContent {
            TabbedPane(selectedIndex = 0, changeListener = listener) {
                tab("One") { }
                tab("Two") { }
            }
        }
        onNodeOfType<JTabbedPane>().fetch().selectedIndex = 1
        awaitIdle()
        assertEquals(1, notified, "the declared listener should be notified of the selection")
    }

    @Test
    fun splitPaneDividerListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = PropertyChangeListener { }
        setContent {
            SplitPane(dividerLocationListener = listener) {
                first { }
                second { }
            }
        }
        val pane = onNodeOfType<JSplitPane>().fetch()
        assertTrue(
            pane.getPropertyChangeListeners(JSplitPane.DIVIDER_LOCATION_PROPERTY).any { it === listener },
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun tableListSelectionListenerOverloadNotifiesInstance() = runComposeSwingTest {
        var notified = false
        val listener = ListSelectionListener { notified = true }
        setContent {
            ScrollPane {
                content {
                    Table(rows = listOf("a", "b"), listSelectionListener = listener) {
                        column("C") { it }
                    }
                }
            }
        }
        onNodeOfType<JTable>().fetch().setRowSelectionInterval(1, 1)
        assertTrue(notified, "driving the widget the way the user would should reach the declared listener")
    }

    @Test
    fun treeSelectionListenerOverloadNotifiesInstance() = runComposeSwingTest {
        var notified = false
        val listener = TreeSelectionListener { notified = true }
        setContent {
            ScrollPane {
                content {
                    Tree(root = "root", children = { emptyList() }, treeSelectionListener = listener)
                }
            }
        }
        onNodeOfType<JTree>().fetch().setSelectionRow(0)
        assertTrue(notified, "driving the widget the way the user would should reach the declared listener")
    }

    @Test
    fun listBoxSelectionListenerOverloadNotifiesInstance() = runComposeSwingTest {
        var notified = false
        val listener = ListSelectionListener { notified = true }
        setContent {
            ScrollPane {
                content {
                    ListBox(items = listOf("a", "b"), listSelectionListener = listener)
                }
            }
        }
        onNodeOfType<JList<*>>().fetch<JList<*>>().selectedIndex = 1
        assertTrue(notified, "driving the widget the way the user would should reach the declared listener")
    }

    @Test
    fun treeExpansionListenerOverloadNotifiesInstance() = runComposeSwingTest {
        var notified = false
        val listener =
            object : TreeExpansionListener {
                override fun treeExpanded(event: TreeExpansionEvent) {
                    notified = true
                }

                override fun treeCollapsed(event: TreeExpansionEvent) = Unit
            }
        setContent {
            ScrollPane {
                content {
                    Tree(
                        root = "root",
                        children = { if (it == "root") listOf("leaf") else emptyList() },
                        treeSelectionListener = TreeSelectionListener { },
                        treeExpansionListener = listener,
                    )
                }
            }
        }
        val tree = onNodeOfType<JTree>().fetch()
        tree.collapseRow(0)
        tree.expandRow(0)
        assertTrue(notified, "driving the widget the way the user would should reach the declared listener")
    }

    @Test
    fun formattedTextFieldValueListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = PropertyChangeListener { }
        setContent { FormattedTextField(value = 1, valuePropertyChangeListener = listener) }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        assertTrue(
            field.getPropertyChangeListeners("value").any { it === listener },
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun textFieldDocumentListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = docListener()
        setContent { TextField("hi", documentListener = listener) }
        assertTrue(
            onNodeOfType<JTextField>().fetch().documentHas(listener),
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun textAreaDocumentListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = docListener()
        setContent { TextArea("hi", documentListener = listener) }
        assertTrue(
            onNodeOfType<JTextArea>().fetch().documentHas(listener),
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun passwordFieldDocumentListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = docListener()
        setContent { PasswordField(value = charArrayOf('a'), documentListener = listener) }
        assertTrue(
            onNodeOfType<JPasswordField>().fetch().documentHas(listener),
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun editorPaneDocumentListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = docListener()
        setContent { EditorPane("hi", documentListener = listener) }
        assertTrue(
            onNodeOfType<JEditorPane>().fetch().documentHas(listener),
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun textPaneDocumentListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = docListener()
        setContent { TextPane("hi", documentListener = listener) }
        assertTrue(
            onNodeOfType<JTextPane>().fetch().documentHas(listener),
            "the declared listener instance should be registered on the widget",
        )
    }
}
