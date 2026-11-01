package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.Composable
import io.mockk.mockk
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
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
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performAddSelection
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.event.ActionListener
import java.beans.PropertyChangeListener
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JEditorPane
import javax.swing.JFormattedTextField
import javax.swing.JList
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentListener
import javax.swing.event.HyperlinkListener
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
    private fun JTextComponent.documentHas(listener: DocumentListener): Boolean {
        val document = document
        return document is AbstractDocument && document.documentListeners.any { it === listener }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actionListenerOverloads")
    fun anActionListenerOverloadRegistersTheInstance(content: @Composable (ActionListener) -> Unit) =
        runComposeSwingTest {
            val listener = ActionListener { }
            setContent { content(listener) }
            onNodeOfType<AbstractButton>().assertProperty(
                true,
                "the declared listener instance should be registered on the widget",
            ) { actionListeners.any { it === listener } }
        }

    @Test
    fun comboBoxActionListenerOverloadNotifiesInstance() = runComposeSwingTest {
        var notified = false
        val listener = ActionListener { notified = true }
        setContent { ComboBox(items = listOf("a", "b"), selectedItem = "a", actionListener = listener) }
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
                Label("one", SwingModifier.tab("One"))
                Label("two", SwingModifier.tab("Two"))
            }
        }
        onNodeOfType<JTabbedPane>().performAddSelection(1)
        assertEquals(1, notified, "the declared listener should be notified of the selection")
    }

    @Test
    fun splitPaneDividerListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = PropertyChangeListener { }
        setContent {
            SplitPane(dividerLocationListener = listener) {
                Label("leading", SwingModifier.first())
                Label("trailing", SwingModifier.second())
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
                Viewport {
                    Table(
                        rows = listOf("a", "b"),
                        columns = { column("C") { it } },
                        listSelectionListener = listener,
                    )
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
                Viewport {
                    Tree(
                        root = "root",
                        children = { emptyList() },
                        treeSelectionListener = listener,
                    )
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
                Viewport { ListBox(items = listOf("a", "b"), listSelectionListener = listener) }
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
                Viewport {
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentListenerOverloads")
    fun aDocumentListenerOverloadRegistersTheInstanceOnTheDocument(content: @Composable (DocumentListener) -> Unit) =
        runComposeSwingTest {
            val listener = mockk<DocumentListener>(relaxed = true)
            setContent { content(listener) }
            onNodeOfType<JTextComponent>().assertProperty(
                true,
                "the declared listener instance should be registered on the widget",
            ) { documentHas(listener) }
        }

    @Test
    fun editorPaneHyperlinkListenerOverloadRegistersInstance() = runComposeSwingTest {
        val listener = HyperlinkListener { }
        setContent { EditorPane("hi", hyperlinkListener = listener) }
        assertTrue(
            listener in onNodeOfType<JEditorPane>().fetch().hyperlinkListeners,
            "the declared listener instance should be registered on the widget",
        )
    }

    companion object {
        @JvmStatic
        fun actionListenerOverloads(): List<Named<@Composable (ActionListener) -> Unit>> = listOf(
            Named.of<@Composable (ActionListener) -> Unit>("Button") { Button("X", actionListener = it) },
            Named.of<@Composable (ActionListener) -> Unit>("CheckBox") {
                CheckBox("X", checked = false, actionListener = it)
            },
            Named.of<@Composable (ActionListener) -> Unit>("RadioButton") {
                RadioButton("X", selected = false, actionListener = it)
            },
            Named.of<@Composable (ActionListener) -> Unit>("ToggleButton") {
                ToggleButton("X", selected = false, actionListener = it)
            },
        )

        @JvmStatic
        fun documentListenerOverloads(): List<Named<@Composable (DocumentListener) -> Unit>> = listOf(
            Named.of<@Composable (DocumentListener) -> Unit>("TextField") { TextField("hi", documentListener = it) },
            Named.of<@Composable (DocumentListener) -> Unit>("TextArea") { TextArea("hi", documentListener = it) },
            Named.of<@Composable (DocumentListener) -> Unit>("PasswordField") {
                PasswordField(value = charArrayOf('a'), documentListener = it)
            },
            Named.of<@Composable (DocumentListener) -> Unit>("TextPane") { TextPane("hi", documentListener = it) },
        )
    }
}
