package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.mockk
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.TestRecomposer
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Component
import java.awt.GraphicsEnvironment
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.AdjustmentListener
import java.awt.event.ComponentAdapter
import java.awt.event.ContainerAdapter
import java.awt.event.FocusAdapter
import java.awt.event.HierarchyListener
import java.awt.event.ItemListener
import java.awt.event.KeyAdapter
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelListener
import java.beans.PropertyChangeListener
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
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
 * Behavioral tests for the typed instance listener builders. They assert what an observer of the live
 * Swing component sees.
 */
class RawListenerModifierTest {
    private fun mousePressed(component: Component): MouseEvent =
        MouseEvent(component, MouseEvent.MOUSE_PRESSED, 0L, 0, 1, 1, 1, false)

    private fun mousePressListener(onPress: () -> Unit): MouseListener = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent): Unit = onPress()
    }

    @Test
    fun existingListenerInstanceFiresOnTheRealEvent() = runComposeSwingTest {
        var pressed = 0
        val listener = mousePressListener { pressed++ }
        setContent {
            Button("X", onClick = { }, modifier = SwingModifier.mouseListener(listener))
        }
        val button = onNodeOfType<JButton>().fetch()
        assertTrue(button.mouseListeners.any { it === listener }, "the exact listener instance should be registered")

        button.dispatchEvent(mousePressed(button))
        assertEquals(1, pressed, "the registered listener should fire once on a press")
    }

    @Test
    fun removingTheElementDetachesTheSameInstance() = runComposeSwingTest {
        var attached by mutableStateOf(true)
        var pressed = 0
        val listener = mousePressListener { pressed++ }
        setContent {
            Button(
                "X",
                onClick = { },
                modifier = if (attached) SwingModifier.mouseListener(listener) else SwingModifier,
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertTrue(button.mouseListeners.any { it === listener }, "the listener should be registered while present")

        attached = false
        awaitIdle()
        assertTrue(
            button.mouseListeners.none {
                it === listener
            },
            "the exact instance must be detached when its element leaves",
        )
        button.dispatchEvent(mousePressed(button))
        assertEquals(0, pressed, "the detached instance must not fire after its element leaves the chain")
    }

    @Test
    fun twoListenersOfTheSameTypeBothInstall() = runComposeSwingTest {
        var first = 0
        var second = 0
        val firstListener = mousePressListener { first++ }
        val secondListener = mousePressListener { second++ }
        setContent {
            Button(
                "X",
                onClick = { },
                modifier = SwingModifier.mouseListener(firstListener).mouseListener(secondListener),
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertTrue(button.mouseListeners.any { it === firstListener }, "the first listener should be installed")
        assertTrue(button.mouseListeners.any { it === secondListener }, "the second listener should be installed")

        button.dispatchEvent(mousePressed(button))
        assertEquals(1, first, "the first listener should fire once")
        assertEquals(1, second, "the second listener should fire once")
    }

    @Test
    fun swappingTheInstanceDetachesTheOldAndAttachesTheNew() = runComposeSwingTest {
        var useFirst by mutableStateOf(true)
        var first = 0
        var second = 0
        val firstListener = mousePressListener { first++ }
        val secondListener = mousePressListener { second++ }
        setContent {
            Button(
                "X",
                onClick = { },
                modifier = SwingModifier.mouseListener(if (useFirst) firstListener else secondListener),
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertTrue(
            button.mouseListeners.any { it === firstListener },
            "the first instance should be installed initially",
        )
        button.dispatchEvent(mousePressed(button))
        assertEquals(1, first, "the first instance should fire while installed")

        useFirst = false
        awaitIdle()
        assertTrue(button.mouseListeners.none { it === firstListener }, "swapping should detach the old instance")
        assertTrue(button.mouseListeners.any { it === secondListener }, "swapping should attach the new instance")

        button.dispatchEvent(mousePressed(button))
        assertEquals(1, first, "the swapped-out instance must no longer fire")
        assertEquals(1, second, "the swapped-in instance must fire")
    }

    @Test
    fun boundPropertyChangeListenerInstanceFiresOnlyOnItsProperty() = runComposeSwingTest {
        var seenNew: Any? = null
        var fired = 0
        val listener =
            PropertyChangeListener { event ->
                fired++
                seenNew = event.newValue
            }
        setContent {
            Label("X", modifier = SwingModifier.propertyChangeListener("enabled", listener))
        }
        val label = onNodeOfType<JLabel>().fetch()

        label.toolTipText = "changed"
        assertEquals(0, fired, "a different bound property must not notify the enabled-bound listener")

        label.isEnabled = false
        assertEquals(1, fired, "the enabled-bound listener should fire on its own property")
        assertEquals(false, seenNew, "the listener should receive the new property value")
    }

    @Test
    fun unboundPropertyChangeListenerInstanceFiresOncePerChangeOnAnyBoundProperty() = runComposeSwingTest {
        val seenProperties = mutableListOf<String?>()
        var fired = 0
        val listener =
            PropertyChangeListener { event ->
                fired++
                seenProperties += event.propertyName
            }
        setContent {
            Label("X", modifier = SwingModifier.propertyChangeListener(listener))
        }
        val label = onNodeOfType<JLabel>().fetch()
        // Joining the tree is itself a bound-property change, "ancestor"; only the changes below count.
        fired = 0
        seenProperties.clear()

        label.isEnabled = false
        assertEquals(1, fired, "the first bound-property change must notify the unbound listener once")

        label.toolTipText = "changed"
        assertEquals(2, fired, "a second, distinct bound-property change must notify it once more")

        assertTrue(
            "enabled" in seenProperties && "ToolTipText" in seenProperties,
            "the unbound listener must see both changed properties, but saw: $seenProperties",
        )
    }

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
        assertDeclaredChainCarriedOnce { adjustmentListener { } }
        assertDeclaredChainCarriedOnce { caretListener { } }
        assertDeclaredChainCarriedOnce { changeListener { } }
        assertDeclaredChainCarriedOnce { changeListener<JButton> { } }
        assertDeclaredChainCarriedOnce { changeListener(JButton::class) { } }
        assertDeclaredChainCarriedOnce { componentListener { } }
        assertDeclaredChainCarriedOnce { componentListener(onComponentResized = { }) }
        assertDeclaredChainCarriedOnce { containerListener { } }
        assertDeclaredChainCarriedOnce { containerListener(onComponentAdded = { }) }
        assertDeclaredChainCarriedOnce { documentListener { } }
        assertDeclaredChainCarriedOnce { documentListener(onInsert = { }) }
        assertDeclaredChainCarriedOnce { focusListener { } }
        assertDeclaredChainCarriedOnce { focusListener(onFocusGained = { }) }
        assertDeclaredChainCarriedOnce { hierarchyListener { } }
        assertDeclaredChainCarriedOnce { hyperlinkListener { } }
        assertDeclaredChainCarriedOnce { internalFrameListener { } }
        assertDeclaredChainCarriedOnce { internalFrameListener(onFrameOpened = { }) }
        assertDeclaredChainCarriedOnce { itemListener { } }
        assertDeclaredChainCarriedOnce { itemListener<JComboBox<String>> { } }
        assertDeclaredChainCarriedOnce { itemListener(JButton::class) { } }
        assertDeclaredChainCarriedOnce { keyListener { } }
        assertDeclaredChainCarriedOnce { keyListener(onKeyTyped = { }) }
        assertDeclaredChainCarriedOnce { listSelectionListener { } }
        assertDeclaredChainCarriedOnce { mouseListener { } }
        assertDeclaredChainCarriedOnce { mouseListener(onMouseClicked = { }) }
        assertDeclaredChainCarriedOnce { mouseMotionListener { } }
        assertDeclaredChainCarriedOnce { mouseMotionListener(onMouseDragged = { }) }
        assertDeclaredChainCarriedOnce { mouseWheelListener { } }
        assertDeclaredChainCarriedOnce { propertyChangeListener { } }
        assertDeclaredChainCarriedOnce { propertyChangeListener("enabled") { } }
        assertDeclaredChainCarriedOnce { propertyChangeListener<JButton>("enabled") { } }
        assertDeclaredChainCarriedOnce { propertyChangeListener("enabled", JButton::class) { } }
        assertDeclaredChainCarriedOnce { propertyChangeListener("enabled", PropertyChangeListener { }) }
        assertDeclaredChainCarriedOnce { treeExpansionListener { } }
        assertDeclaredChainCarriedOnce { treeExpansionListener(onTreeExpanded = { }) }
        assertDeclaredChainCarriedOnce { treeSelectionListener { } }
        assertDeclaredChainCarriedOnce { treeWillExpandListener { true } }
        assertDeclaredChainCarriedOnce { treeWillExpandListener(onWillExpand = { true }) }
    }

    @Test
    fun everyRawListenerBuilderAppendsToTheChainWithoutRepeatingIt() {
        val registration =
            ListenerRegistration<JButton, ActionListener>(
                name = "actionListener",
                { component, listener -> component.addActionListener(listener) },
                { component, listener -> component.removeActionListener(listener) },
            )
        val callbacks =
            CallbackRegistration<JButton, (ActionEvent) -> Unit, ActionListener>(
                { current -> ActionListener { event -> current()(event) } },
                registration,
            )

        assertDeclaredChainCarriedOnce { actionListener(ActionListener { }) }
        assertDeclaredChainCarriedOnce { adjustmentListener(AdjustmentListener { }) }
        assertDeclaredChainCarriedOnce { caretListener(CaretListener { }) }
        assertDeclaredChainCarriedOnce { changeListener(ChangeListener { }) }
        assertDeclaredChainCarriedOnce { componentListener(object : ComponentAdapter() {}) }
        assertDeclaredChainCarriedOnce { containerListener(object : ContainerAdapter() {}) }
        assertDeclaredChainCarriedOnce { documentListener(mockk<DocumentListener>(relaxed = true)) }
        assertDeclaredChainCarriedOnce { focusListener(object : FocusAdapter() {}) }
        assertDeclaredChainCarriedOnce { hierarchyListener(HierarchyListener { }) }
        assertDeclaredChainCarriedOnce { hyperlinkListener(HyperlinkListener { }) }
        assertDeclaredChainCarriedOnce { internalFrameListener(object : InternalFrameAdapter() {}) }
        assertDeclaredChainCarriedOnce { itemListener(ItemListener { }) }
        assertDeclaredChainCarriedOnce { keyListener(object : KeyAdapter() {}) }
        assertDeclaredChainCarriedOnce { listSelectionListener(ListSelectionListener { }) }
        assertDeclaredChainCarriedOnce { listener(ActionListener { }, registration) }
        assertDeclaredChainCarriedOnce { listener({ _: ActionEvent -> }, callbacks) }
        assertDeclaredChainCarriedOnce { listener(JButton::class, ActionListener { }, registration) }
        assertDeclaredChainCarriedOnce { listener(JButton::class, { _: ActionEvent -> }, callbacks) }
        assertDeclaredChainCarriedOnce { mouseListener(object : MouseAdapter() {}) }
        assertDeclaredChainCarriedOnce { mouseMotionListener(object : MouseMotionAdapter() {}) }
        assertDeclaredChainCarriedOnce { mouseWheelListener(MouseWheelListener { }) }
        assertDeclaredChainCarriedOnce { propertyChangeListener(PropertyChangeListener { }) }
        assertDeclaredChainCarriedOnce { treeExpansionListener(mockk<TreeExpansionListener>(relaxed = true)) }
        assertDeclaredChainCarriedOnce { treeSelectionListener(TreeSelectionListener { }) }
        assertDeclaredChainCarriedOnce { treeWillExpandListener(mockk<TreeWillExpandListener>(relaxed = true)) }
        assertDeclaredChainCarriedOnce { listener<JButton, ActionEvent, ActionListener>(JButton::class, callbacks) { } }
    }
}
