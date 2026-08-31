package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JCheckBox
import javax.swing.JEditorPane
import javax.swing.JList
import javax.swing.JScrollBar
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.event.HyperlinkEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every builder whose listener interface has one method takes that method's lambda. Each test drives the
 * channel the builder names and asserts the lambda declared for it ran.
 */
class SamLambdaListenerOverloadTest {
    @Test
    fun anItemLambdaReportsAToggle() = runComposeSwingTest {
        var reports = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JCheckBox() },
                    modifier = SwingModifier.itemListener { reports++ },
                )
            }
        }
        onNodeOfType<JCheckBox>().fetch<JCheckBox>().isSelected = true
        assertEquals(1, reports, "selecting the box reports over the item channel")
    }

    @Test
    fun aChangeLambdaReportsASliderMove() = runComposeSwingTest {
        var reports = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JSlider() },
                    modifier = SwingModifier.changeListener { reports++ },
                )
            }
        }
        onNodeOfType<JSlider>().fetch<JSlider>().value = 5
        assertEquals(1, reports, "moving the slider reports over the change channel")
    }

    @Test
    fun aCaretLambdaReportsAnEdit() = runComposeSwingTest {
        var reports = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextField() },
                    modifier = SwingModifier.caretListener { reports++ },
                )
            }
        }
        onNodeOfType<JTextField>().fetch<JTextField>().text = "ab"
        assertTrue(reports > 0, "typing moves the caret and reports over the caret channel")
    }

    @Test
    fun anAdjustmentLambdaReportsAScroll() = runComposeSwingTest {
        var reports = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JScrollBar() },
                    modifier = SwingModifier.adjustmentListener { reports++ },
                )
            }
        }
        onNodeOfType<JScrollBar>().fetch<JScrollBar>().value = 5
        assertTrue(reports > 0, "moving the scrollbar reports over the adjustment channel")
    }

    @Test
    fun aListSelectionLambdaReportsASelection() = runComposeSwingTest {
        var reports = 0
        setContent {
            FlowPanel {
                SwingNode(factory = {
                    JList(arrayOf("a", "b"))
                }, modifier = SwingModifier.listSelectionListener { reports++ })
            }
        }
        onNodeOfType<JList<*>>().fetch<JList<*>>().selectedIndex = 1
        assertTrue(reports > 0, "selecting a row reports over the list selection channel")
    }

    @Test
    fun aTreeSelectionLambdaReportsASelection() = runComposeSwingTest {
        var reports = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTree() },
                    modifier = SwingModifier.treeSelectionListener { reports++ },
                )
            }
        }
        onNodeOfType<JTree>().fetch<JTree>().setSelectionRow(1)
        assertTrue(reports > 0, "selecting a row reports over the tree selection channel")
    }

    @Test
    fun aPropertyChangeLambdaReportsEveryBoundProperty() = runComposeSwingTest {
        val seen = mutableListOf<String>()
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextField() },
                    modifier = SwingModifier.propertyChangeListener { seen += it.propertyName },
                )
            }
        }
        onNodeOfType<JTextField>().fetch<JTextField>().isEnabled = false
        assertTrue("enabled" in seen, "an unbound lambda hears every bound property")
    }

    @Test
    fun aHyperlinkLambdaHearsTheLinkEventTheEditorPanePublishes() = runComposeSwingTest {
        val seen = mutableListOf<HyperlinkEvent>()
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JEditorPane() },
                    modifier = SwingModifier.hyperlinkListener { seen += it },
                )
            }
        }

        val pane = onNodeOfType<JEditorPane>().fetch<JEditorPane>()
        assertEquals(1, pane.hyperlinkListeners.size, "the lambda is registered as one built listener")

        // A link event as the kit publishes one: the raw href as the description, and no resolved URL,
        // which is what a plain-text document carrying no base to resolve against gives.
        pane.fireHyperlinkUpdate(HyperlinkEvent(pane, HyperlinkEvent.EventType.ACTIVATED, null, "/q3/details"))

        assertEquals(1, seen.size, "the built listener hands the event to the lambda once")
        assertEquals(
            HyperlinkEvent.EventType.ACTIVATED,
            seen.single().eventType,
            "the lambda is handed the event that fired",
        )
        assertEquals("/q3/details", seen.single().description, "carrying the href the link named")
    }

    @Test
    fun aMouseWheelLambdaHearsTheTurnTheEventCarries() = runComposeSwingTest {
        val turns = mutableListOf<Int>()
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextField() },
                    modifier = SwingModifier.mouseWheelListener { turns += it.wheelRotation },
                )
            }
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        assertEquals(1, field.mouseWheelListeners.size, "the lambda is registered as one built listener")

        // The last argument is the wheel rotation, and no other argument of the event carries it, so
        // the turn reported back is only the declared one when the lambda is handed the event that fired.
        val event =
            MouseWheelEvent(
                field,
                MouseEvent.MOUSE_WHEEL,
                0L,
                0,
                0,
                0,
                0,
                false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL,
                1,
                3,
            )
        // A component that is neither focused nor showing is not reached by dispatchEvent, so the
        // listener the builder registered is invoked directly.
        field.mouseWheelListeners.forEach { it.mouseWheelMoved(event) }

        assertEquals(listOf(3), turns, "the built listener hands the event that fired to the lambda")
    }

    @Test
    fun aHierarchyLambdaHearsTheComponentReachItsParent() = runComposeSwingTest {
        var reports = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextField() },
                    modifier = SwingModifier.hierarchyListener { reports++ },
                )
            }
        }
        assertTrue(reports > 0, "being added to the panel reports over the hierarchy channel")
    }

    @Test
    fun aLambdaDeclaredAfreshKeepsTheListenerItRegistered() = runComposeSwingTest {
        var reports = 0
        var declared by mutableStateOf(1)
        setContent {
            FlowPanel {
                // Captured while the chain is built, so the value each lambda reports is the one its own
                // pass declared: a listener left holding the first lambda reports 1.
                val captured = declared
                SwingNode(
                    factory = { JSlider() },
                    modifier = SwingModifier.changeListener { reports += captured },
                    // Read in the update block, so a new value recomposes this node and rebuilds the chain
                    // with a lambda written afresh.
                    update = { set(declared) { toolTipText = it.toString() } },
                )
            }
        }

        val slider = onNodeOfType<JSlider>().fetch<JSlider>()
        val registered = slider.changeListeners.toList()

        declared = 2
        awaitIdle()

        assertEquals(
            registered.size,
            slider.changeListeners.size,
            "a fresh lambda registers no second listener",
        )
        assertTrue(
            registered.indices.all { registered[it] === slider.changeListeners[it] },
            "and leaves the listener object that was registered in place",
        )
        slider.value = 5
        assertEquals(2, reports, "while the lambda that runs is the one the latest pass declared")
    }
}
