// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.components.text.rememberDocumentState
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
import javax.swing.text.StyledEditorKit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the `SwingModifier` listener functions on components: each test attaches a
 * Swing listener object with its modifier and asserts the exact instance passed is registered on the live
 * component through the matching `getXxxListeners()` accessor (or, for the text components, on the field's
 * document) - the observable proof the modifier wires the listener to the right registration site.
 * A component whose callbacks carry the user's changes only reaches its declared listener behind a gate
 * that keeps the composition's own writes out of it, so the instance cannot be registered directly; those
 * listeners are asserted through the notification instead - driving the widget the way the user would has to
 * reach the instance passed.
 */
class ComponentListenerModifierTest {
    private fun JTextComponent.documentHas(listener: DocumentListener): Boolean {
        val document = document
        return document is AbstractDocument && document.documentListeners.any { it === listener }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actionListenerOverloads")
    fun anActionListenerModifierRegistersTheInstance(content: @Composable (ActionListener) -> Unit) =
        runComposeSwingTest {
            val listener = ActionListener { }
            setContent { content(listener) }
            onNodeOfType<AbstractButton>().assertProperty(
                true,
                "the declared listener instance should be registered on the widget",
            ) { actionListeners.any { it === listener } }
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actionListenerOverloads")
    fun swappingTheActionListenerModifierDetachesTheOldInstance(content: @Composable (ActionListener) -> Unit) =
        runComposeSwingTest {
            val first = ActionListener { }
            val second = ActionListener { }
            var declared by mutableStateOf(first)
            setContent { content(declared) }
            declared = second
            awaitIdle()
            val button = onNodeOfType<AbstractButton>()
            button.assertProperty(true, "the newly declared listener should be registered") {
                actionListeners.any { it === second }
            }
            button.assertProperty(true, "the listener that left the declaration should be detached") {
                actionListeners.none { it === first }
            }
        }

    @Test
    fun comboBoxActionListenerModifierNotifiesInstance() = runComposeSwingTest {
        var notified = false
        val listener = ActionListener { notified = true }
        setContent {
            ComboBox(
                items = listOf("a", "b"),
                selectedItem = "a",
                onSelectionChange = {},
                modifier = SwingModifier.actionListener(listener),
            )
        }
        onNodeOfType<JComboBox<*>>().fetch<JComboBox<*>>().selectedIndex = 1
        assertTrue(notified, "driving the widget the way the user would should reach the declared listener")
    }

    @Test
    fun sliderChangeListenerModifierRegistersInstance() = runComposeSwingTest {
        val listener = ChangeListener { }
        setContent { Slider(value = 5, onValueChange = {}, modifier = SwingModifier.changeListener(listener)) }
        assertTrue(
            onNodeOfType<JSlider>().fetch().changeListeners.any { it === listener },
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun spinnerChangeListenerModifierRegistersInstance() = runComposeSwingTest {
        val listener = ChangeListener { }
        setContent {
            Spinner(model = SpinnerNumberModel(1, 0, 10, 1), modifier = SwingModifier.changeListener(listener))
        }
        assertTrue(
            onNodeOfType<JSpinner>().fetch().changeListeners.any { it === listener },
            "the declared listener instance should be registered on the widget",
        )
    }

    @Test
    fun tabbedPaneChangeListenerModifierNotifiesInstance() = runComposeSwingTest {
        var notified = 0
        val listener = ChangeListener { notified++ }
        setContent {
            TabbedPane(
                selectedIndex = 0,
                onSelectedIndexChange = {},
                modifier = SwingModifier.changeListener(listener),
            ) {
                Label("one", SwingModifier.tab("One"))
                Label("two", SwingModifier.tab("Two"))
            }
        }
        awaitIdle()
        notified = 0
        onNodeOfType<JTabbedPane>().performAddSelection(1)
        assertTrue(notified > 0, "the declared listener should be notified of the selection")
    }

    @Test
    fun splitPaneDividerListenerModifierRegistersInstance() = runComposeSwingTest {
        val listener = PropertyChangeListener { }
        setContent {
            SplitPane(modifier = SwingModifier.propertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, listener)) {
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
    fun treeSelectionListenerModifierNotifiesInstance() = runComposeSwingTest {
        var notified = false
        val listener = TreeSelectionListener { notified = true }
        setContent {
            ScrollPane {
                Viewport {
                    Tree(
                        root = "root",
                        children = { emptyList() },
                        modifier = SwingModifier.treeSelectionListener(listener),
                    )
                }
            }
        }
        onNodeOfType<JTree>().fetch().setSelectionRow(0)
        assertTrue(notified, "driving the widget the way the user would should reach the declared listener")
    }

    @Test
    fun listBoxSelectionListenerModifierNotifiesInstance() = runComposeSwingTest {
        var notified = false
        val listener = ListSelectionListener { notified = true }
        setContent {
            ScrollPane {
                Viewport { ListBox(items = listOf("a", "b"), modifier = SwingModifier.listSelectionListener(listener)) }
            }
        }
        onNodeOfType<JList<*>>().fetch<JList<*>>().selectedIndex = 1
        assertTrue(notified, "driving the widget the way the user would should reach the declared listener")
    }

    @Test
    fun treeExpansionListenerModifierNotifiesInstance() = runComposeSwingTest {
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
                        modifier = SwingModifier.treeExpansionListener(listener),
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
    fun formattedTextFieldValueListenerModifierRegistersInstance() = runComposeSwingTest {
        val listener = PropertyChangeListener { }
        setContent {
            FormattedTextField(
                value = 1,
                onValueChange = {},
                modifier = SwingModifier.propertyChangeListener("value", listener),
            )
        }
        val field = onNodeOfType<JFormattedTextField>().fetch()
        assertTrue(
            field.getPropertyChangeListeners("value").any { it === listener },
            "the declared listener instance should be registered on the widget",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentListenerOverloads")
    fun aDocumentListenerModifierRegistersTheInstanceOnTheDocument(content: @Composable (DocumentListener) -> Unit) =
        runComposeSwingTest {
            val listener = mockk<DocumentListener>(relaxed = true)
            setContent { content(listener) }
            onNodeOfType<JTextComponent>().assertProperty(
                true,
                "the declared listener instance should be registered on the widget",
            ) { documentHas(listener) }
        }

    @Test
    fun editorPaneHyperlinkListenerModifierRegistersInstance() = runComposeSwingTest {
        val listener = HyperlinkListener { }
        setContent { EditorPane("hi", onLinkActivate = {}, modifier = SwingModifier.hyperlinkListener(listener)) }
        assertTrue(
            listener in onNodeOfType<JEditorPane>().fetch().hyperlinkListeners,
            "the declared listener instance should be registered on the widget",
        )
    }

    companion object {
        @JvmStatic
        fun actionListenerOverloads(): List<Named<@Composable (ActionListener) -> Unit>> = listOf(
            Named.of<@Composable (ActionListener) -> Unit>("Button") {
                Button("X", onClick = {}, modifier = SwingModifier.actionListener(it))
            },
            Named.of<@Composable (ActionListener) -> Unit>("CheckBox") {
                CheckBox("X", checked = false, onCheckedChange = {}, modifier = SwingModifier.actionListener(it))
            },
            Named.of<@Composable (ActionListener) -> Unit>("RadioButton") {
                RadioButton("X", selected = false, onSelectedChange = {}, modifier = SwingModifier.actionListener(it))
            },
            Named.of<@Composable (ActionListener) -> Unit>("ToggleButton") {
                ToggleButton(
                    "X",
                    selected = false,
                    onSelectedChange = {},
                    modifier = SwingModifier.actionListener(it),
                )
            },
        )

        @JvmStatic
        fun documentListenerOverloads(): List<Named<@Composable (DocumentListener) -> Unit>> = listOf(
            Named.of<@Composable (DocumentListener) -> Unit>("TextField") {
                TextField("hi", onValueChange = {}, modifier = SwingModifier.documentListener(it))
            },
            Named.of<@Composable (DocumentListener) -> Unit>("TextArea") {
                TextArea("hi", onValueChange = {}, modifier = SwingModifier.documentListener(it))
            },
            Named.of<@Composable (DocumentListener) -> Unit>("PasswordField") {
                PasswordField(
                    value = charArrayOf('a'),
                    onValueChange = {},
                    modifier = SwingModifier.documentListener(it),
                )
            },
            Named.of<@Composable (DocumentListener) -> Unit>("TextPane") {
                TextPane(
                    state = rememberDocumentState(remember { StyledEditorKit() }, "hi"),
                    modifier = SwingModifier.documentListener(it),
                )
            },
        )
    }
}
