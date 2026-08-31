package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JColorChooser
import javax.swing.JInternalFrame
import javax.swing.JList
import javax.swing.JSlider
import javax.swing.JTree
import javax.swing.colorchooser.DefaultColorSelectionModel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import javax.swing.event.InternalFrameListener
import javax.swing.event.ListSelectionListener
import javax.swing.event.TreeSelectionListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Behavioral tests for the raw event-listener builders that attach an existing Swing listener object to
 * a component whose add/remove pair lives off `java.awt.Component`: `changeListener`,
 * `listSelectionListener`, `treeSelectionListener`, and `internalFrameListener`. Each asserts what an
 * observer of the live component sees - the exact instance is registered via the component's
 * `getXxxListeners()`, and it fires when the component publishes the real event.
 */
class RawEventListenerModifierTest {
    @Test
    fun changeListenerInstanceIsRegisteredAndFiresOnAChange() = runComposeSwingTest {
        var fired = 0
        val listener = ChangeListener { fired++ }
        setContent {
            Slider(value = 10, onValueChange = {}, modifier = SwingModifier.changeListener(listener))
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertTrue(
            slider.changeListeners.any { it === listener },
            "the listener instance should be registered on the slider",
        )

        slider.changeListeners.forEach { it.stateChanged(ChangeEvent(slider)) }
        assertEquals(1, fired, "the registered listener should fire once on a change")
    }

    @Test
    fun listSelectionListenerInstanceIsRegisteredAndFiresOnSelection() = runComposeSwingTest {
        var fired = 0
        val listener = ListSelectionListener { fired++ }
        setContent {
            ScrollPane {
                ListBox(
                    items = listOf("a", "b", "c"),
                    modifier = SwingModifier.listSelectionListener(listener).viewport(),
                )
            }
        }
        val list = onNodeOfType<JList<*>>().fetch<JList<*>>()
        assertTrue(
            list.listSelectionListeners.any { it === listener },
            "the listener instance should be registered on the list",
        )

        list.selectedIndex = 1
        awaitIdle()
        assertTrue(fired > 0, "the registered selection listener must fire on a selection change")
    }

    @Test
    fun treeSelectionListenerInstanceIsRegisteredAndFiresOnSelection() = runComposeSwingTest {
        var fired = 0
        val listener = TreeSelectionListener { fired++ }
        setContent {
            ScrollPane {
                Tree(
                    root = "root",
                    children = { if (it == "root") listOf("child") else emptyList() },
                    modifier = SwingModifier.treeSelectionListener(listener).viewport(),
                )
            }
        }
        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(
            tree.treeSelectionListeners.any { it === listener },
            "the listener instance should be registered on the tree",
        )

        tree.setSelectionRow(0)
        awaitIdle()
        assertTrue(fired > 0, "the registered tree-selection listener must fire on a selection change")
    }

    @Test
    fun internalFrameListenerInstanceIsRegisteredAndFiresOnTheRealEvent() = runComposeSwingTest {
        var closing = 0
        val listener: InternalFrameListener =
            object : InternalFrameAdapter() {
                override fun internalFrameClosing(event: InternalFrameEvent) {
                    closing++
                }
            }
        setContent {
            SwingNode(
                factory = { JInternalFrame("F", true, true, true, true).also { it.isVisible = true } },
                modifier = SwingModifier.internalFrameListener(listener),
            )
        }
        val frame = onNodeOfType<JInternalFrame>().fetch()
        assertTrue(
            frame.internalFrameListeners.any { it === listener },
            "the listener instance should be registered on the frame",
        )

        val event = InternalFrameEvent(frame, InternalFrameEvent.INTERNAL_FRAME_CLOSING)
        frame.internalFrameListeners.forEach { it.internalFrameClosing(event) }
        assertEquals(1, closing, "the registered listener should fire once on the closing event")
    }

    @Test
    fun changeListenerOnAColorChooserIsRegisteredOnItsSelectionModelAndFiresOnASelection() = runComposeSwingTest {
        var fired = 0
        val listener = ChangeListener { fired++ }
        setContent {
            SwingNode(
                factory = { JColorChooser() },
                modifier = SwingModifier.changeListener(listener),
            )
        }
        val chooser = onNodeOfType<JColorChooser>().fetch()
        val model = chooser.selectionModel as DefaultColorSelectionModel
        assertTrue(
            model.changeListeners.any { it === listener },
            "the listener instance should be registered on the chooser's selection model",
        )

        model.selectedColor = Color.RED
        awaitIdle()
        assertTrue(fired > 0, "the registered listener must fire when the chooser's selected color changes")
    }

    @Test
    fun changeListenerOnAComponentThatDoesNotFireChangeEventsIsRejected() = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalStateException> {
                setContent {
                    // A Label is a JComponent but not one of the change-firing widgets, so the
                    // changeListener target is wrong and the applier must reject it loudly rather
                    // than silently no-op.
                    Label("X", modifier = SwingModifier.changeListener(ChangeListener { }))
                }
                awaitIdle()
            }
        val message = error.message.orEmpty()
        assertTrue(
            "fires change events" in message,
            "the wrong-target error must explain the required change-firing target, but was: $message",
        )
    }
}
