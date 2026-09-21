// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.modifier.listener

import io.mockk.mockk
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.TestRecomposer
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.countTests
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.ActionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.ActionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.HyperlinkListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.HyperlinkListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.InternalFrameListenerChangeFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.InternalFrameListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.InternalFrameListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.ListSelectionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.ListSelectionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.ListSelectionListenerScopedFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeExpansionListenerCallbackFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeExpansionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeExpansionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeSelectionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeSelectionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeWillExpandListenerDirectionalFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeWillExpandListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeWillExpandListenerListenerFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.rawSwingTests
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.TestFactory
import java.awt.GraphicsEnvironment
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JTextField
import javax.swing.event.CaretListener
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentListener
import javax.swing.event.HyperlinkListener
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.ListSelectionListener
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.event.TreeWillExpandListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.awt.Button as AwtButton
import java.awt.List as AwtList
import java.awt.TextField as AwtTextField

/**
 * Behavioral tests for the widget-specific typed instance listener builders. They assert what an observer of the live
 * Swing component sees.
 */
class RawListenerModifierTest {
    @Test
    fun actionListenerOnAComponentThatFiresNoActionEventNamesTheKindsThatDo() = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Label("X", modifier = SwingModifier.actionListener(ActionListener { }))
                }
                awaitIdle()
            }
        val message = error.message.orEmpty()
        assertTrue(
            "AbstractButton" in message && "JTextField" in message && "JComboBox" in message,
            "the error must name the component kinds that fire action events, but was: $message",
        )
    }

    @Test
    fun actionListenerOnAButtonFiresOnAction() = runComposeSwingTest {
        var actions = 0
        val listener = ActionListener { actions++ }
        setContent {
            Button("X", onClick = { }, modifier = SwingModifier.actionListener(listener))
        }
        val button = onNodeOfType<JButton>().fetch()
        assertTrue(button.actionListeners.any { it === listener }, "the action listener instance should be registered")

        val event = ActionEvent(button, ActionEvent.ACTION_PERFORMED, "x")
        button.actionListeners.forEach { it.actionPerformed(event) }
        assertEquals(1, actions, "the registered action listener should fire once")
    }

    @Test
    fun actionListenerOnATextFieldFiresWhenTheFieldPostsItsAction() = runComposeSwingTest {
        var actions = 0
        val listener = ActionListener { actions++ }
        setContent {
            TextField("query", onValueChange = {}, modifier = SwingModifier.actionListener(listener))
        }
        val field = onNodeOfType<JTextField>().fetch()
        assertTrue(field.actionListeners.any { it === listener }, "the instance should be registered on the field")

        field.postActionEvent()
        assertEquals(1, actions, "the field's action event should reach the registered listener")
    }

    @Test
    fun actionListenerOnAComboBoxIsRegisteredOnTheBox() = runComposeSwingTest {
        val listener = ActionListener { }
        setContent {
            ComboBox(
                items = listOf("a", "b"),
                selectedItem = "a",
                onSelectionChange = {},
                modifier = SwingModifier.actionListener(listener),
            )
        }
        val box = onNodeOfType<JComboBox<*>>().fetch<JComboBox<*>>()
        assertTrue(box.actionListeners.any { it === listener }, "the instance should be registered on the combo box")
    }

    @Test
    fun actionListenerOnAFileChooserIsRegisteredOnTheChooser() = runComposeSwingTest {
        val listener = ActionListener { }
        setContent {
            SwingNode(
                factory = { JFileChooser() },
                modifier = SwingModifier.actionListener(listener),
            )
        }
        val chooser = onNodeOfType<JFileChooser>().fetch()
        assertTrue(
            chooser.actionListeners.any { it === listener },
            "the instance should be registered on the file chooser",
        )
    }

    @Test
    fun actionListenerOnRawAwtComponentsIsRegisteredOnEach() = runSwingTest {
        // These components need a native widget to get a peer, which the harness root never provides
        // (it stands under no window). A real window, realized but never shown, gives them one.
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val buttonListener = ActionListener { }
        val textFieldListener = ActionListener { }
        val listListener = ActionListener { }
        val frame = JFrame()
        val test = TestRecomposer(this)
        var handle: DisposableHandle? = null
        try {
            // Realized before composing, so each child gets its peer the moment it is added.
            frame.pack()
            val target = frame.contentPane as JComponent
            handle =
                target.setContent(parent = test.recomposer) {
                    SwingNode(
                        factory = { AwtButton() },
                        modifier = SwingModifier.actionListener(buttonListener),
                    )
                    SwingNode(
                        factory = { AwtTextField() },
                        modifier = SwingModifier.actionListener(textFieldListener),
                    )
                    SwingNode(
                        factory = { AwtList() },
                        modifier = SwingModifier.actionListener(listListener),
                    )
                }
            test.awaitIdle()

            val children = target.components
            val button = children.filterIsInstance<AwtButton>().single()
            val textField = children.filterIsInstance<AwtTextField>().single()
            val list = children.filterIsInstance<AwtList>().single()
            assertTrue(
                button.actionListeners.any { it === buttonListener },
                "the instance should be registered on the AWT button",
            )
            assertTrue(
                textField.actionListeners.any { it === textFieldListener },
                "the instance should be registered on the AWT text field",
            )
            assertTrue(
                list.actionListeners.any { it === listListener },
                "the instance should be registered on the AWT list",
            )
        } finally {
            handle?.dispose()
            frame.dispose()
            test.cancel()
        }
    }

    @Test
    fun everyLambdaListenerBuilderAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce { actionListener { } }
        assertDeclaredChainCarriedOnce { actionListener<JButton> { } }
        assertDeclaredChainCarriedOnce { actionListener(JButton::class) { } }
        assertDeclaredChainCarriedOnce { caretListener { } }
        assertDeclaredChainCarriedOnce { changeListener { } }
        assertDeclaredChainCarriedOnce { changeListener<JButton> { } }
        assertDeclaredChainCarriedOnce { changeListener(JButton::class) { } }
        assertDeclaredChainCarriedOnce { documentListener { } }
        assertDeclaredChainCarriedOnce { documentListener(onInsert = { }) }
        assertDeclaredChainCarriedOnce { hyperlinkListener { } }
        assertDeclaredChainCarriedOnce { internalFrameListener { } }
        assertDeclaredChainCarriedOnce { internalFrameListener(onFrameOpened = { }) }
        assertDeclaredChainCarriedOnce { listSelectionListener { } }
        assertDeclaredChainCarriedOnce { treeExpansionListener { } }
        assertDeclaredChainCarriedOnce { treeExpansionListener(onTreeExpanded = { }) }
        assertDeclaredChainCarriedOnce { treeSelectionListener { } }
        assertDeclaredChainCarriedOnce { treeWillExpandListener { true } }
        assertDeclaredChainCarriedOnce { treeWillExpandListener(onWillExpand = { true }) }
    }

    @Test
    fun everyRawListenerBuilderAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce { actionListener(ActionListener { }) }
        assertDeclaredChainCarriedOnce { caretListener(CaretListener { }) }
        assertDeclaredChainCarriedOnce { changeListener(ChangeListener { }) }
        assertDeclaredChainCarriedOnce { documentListener(mockk<DocumentListener>(relaxed = true)) }
        assertDeclaredChainCarriedOnce { hyperlinkListener(HyperlinkListener { }) }
        assertDeclaredChainCarriedOnce { internalFrameListener(object : InternalFrameAdapter() {}) }
        assertDeclaredChainCarriedOnce { listSelectionListener(ListSelectionListener { }) }
        assertDeclaredChainCarriedOnce { treeExpansionListener(mockk<TreeExpansionListener>(relaxed = true)) }
        assertDeclaredChainCarriedOnce { treeSelectionListener(TreeSelectionListener { }) }
        assertDeclaredChainCarriedOnce { treeWillExpandListener(mockk<TreeWillExpandListener>(relaxed = true)) }
    }

    @TestFactory
    fun actionListenerRawSwing() = ActionListenerFixture.rawSwingTests()

    @TestFactory
    fun actionListenerCounts() = ActionListenerFixture.countTests()

    @TestFactory
    fun actionListenerListenerRawSwing() = ActionListenerListenerFixture.rawSwingTests()

    @TestFactory
    fun actionListenerListenerCounts() = ActionListenerListenerFixture.countTests()

    @TestFactory
    fun hyperlinkListenerRawSwing() = HyperlinkListenerFixture.rawSwingTests()

    @TestFactory
    fun hyperlinkListenerCounts() = HyperlinkListenerFixture.countTests()

    @TestFactory
    fun hyperlinkListenerListenerRawSwing() = HyperlinkListenerListenerFixture.rawSwingTests()

    @TestFactory
    fun hyperlinkListenerListenerCounts() = HyperlinkListenerListenerFixture.countTests()

    @TestFactory
    fun listSelectionListenerRawSwing() = ListSelectionListenerFixture.rawSwingTests()

    @TestFactory
    fun listSelectionListenerCounts() = ListSelectionListenerFixture.countTests()

    @TestFactory
    fun listSelectionListenerListenerRawSwing() = ListSelectionListenerListenerFixture.rawSwingTests()

    @TestFactory
    fun listSelectionListenerListenerCounts() = ListSelectionListenerListenerFixture.countTests()

    @TestFactory
    fun listSelectionListenerScopedRawSwing() = ListSelectionListenerScopedFixture.rawSwingTests()

    @TestFactory
    fun listSelectionListenerScopedCounts() = ListSelectionListenerScopedFixture.countTests()

    @TestFactory
    fun internalFrameListenerChangeRawSwing() = InternalFrameListenerChangeFixture.rawSwingTests()

    @TestFactory
    fun internalFrameListenerChangeCounts() = InternalFrameListenerChangeFixture.countTests()

    @TestFactory
    fun internalFrameListenerListenerRawSwing() = InternalFrameListenerListenerFixture.rawSwingTests()

    @TestFactory
    fun internalFrameListenerListenerCounts() = InternalFrameListenerListenerFixture.countTests()

    @TestFactory
    fun treeExpansionListenerCallbackRawSwing() = TreeExpansionListenerCallbackFixture.rawSwingTests()

    @TestFactory
    fun treeExpansionListenerCallbackCounts() = TreeExpansionListenerCallbackFixture.countTests()

    @TestFactory
    fun treeExpansionListenerRawSwing() = TreeExpansionListenerFixture.rawSwingTests()

    @TestFactory
    fun treeExpansionListenerCounts() = TreeExpansionListenerFixture.countTests()

    @TestFactory
    fun treeExpansionListenerListenerRawSwing() = TreeExpansionListenerListenerFixture.rawSwingTests()

    @TestFactory
    fun treeExpansionListenerListenerCounts() = TreeExpansionListenerListenerFixture.countTests()

    @TestFactory
    fun treeSelectionListenerRawSwing() = TreeSelectionListenerFixture.rawSwingTests()

    @TestFactory
    fun treeSelectionListenerCounts() = TreeSelectionListenerFixture.countTests()

    @TestFactory
    fun treeSelectionListenerListenerRawSwing() = TreeSelectionListenerListenerFixture.rawSwingTests()

    @TestFactory
    fun treeSelectionListenerListenerCounts() = TreeSelectionListenerListenerFixture.countTests()

    @TestFactory
    fun treeWillExpandListenerListenerRawSwing() = TreeWillExpandListenerListenerFixture.rawSwingTests()

    @TestFactory
    fun treeWillExpandListenerListenerCounts() = TreeWillExpandListenerListenerFixture.countTests()

    @TestFactory
    fun internalFrameListenerRawSwing() = InternalFrameListenerFixture.rawSwingTests()

    @TestFactory
    fun internalFrameListenerCounts() = InternalFrameListenerFixture.countTests()

    @TestFactory
    fun treeWillExpandListenerRawSwing() = TreeWillExpandListenerFixture.rawSwingTests()

    @TestFactory
    fun treeWillExpandListenerCounts() = TreeWillExpandListenerFixture.countTests()

    @TestFactory
    fun treeWillExpandListenerDirectionalRawSwing() = TreeWillExpandListenerDirectionalFixture.rawSwingTests()

    @TestFactory
    fun treeWillExpandListenerDirectionalCounts() = TreeWillExpandListenerDirectionalFixture.countTests()
}
