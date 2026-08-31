package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ComponentEvent
import java.awt.event.ContainerEvent
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JInternalFrame
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.JToolBar
import javax.swing.JTree
import javax.swing.event.InternalFrameEvent
import javax.swing.text.SimpleAttributeSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A listener interface with several methods reaches a caller two ways: one lambda that every method
 * calls, or one lambda per method. Which of them a call selects is decided by whether it names a method.
 */
class MultiMethodLambdaListenerTest {
    @Test
    fun aCatchAllLambdaRunsForEveryMethodOfTheInterface() = runComposeSwingTest {
        var calls = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextPane() },
                    modifier = SwingModifier.documentListener { calls++ },
                )
            }
        }

        val pane = onNodeOfType<JTextPane>().fetch<JTextPane>()
        pane.document.insertString(0, "ab", null)
        pane.document.remove(0, 1)
        pane.styledDocument.setCharacterAttributes(0, 1, SimpleAttributeSet(), false)
        assertEquals(3, calls, "the insertion, the removal and the attribute change all reach it")
    }

    @Test
    fun aNamedMethodLambdaRunsOnlyForThatMethod() = runComposeSwingTest {
        // Every slot reports its own name: counts stay level when two slots are wired to each other's
        // method, since each of them runs exactly once.
        val reported = mutableListOf<String>()
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextPane() },
                    modifier =
                        SwingModifier.documentListener(
                            onInsert = { reported += "insert" },
                            onRemove = { reported += "remove" },
                            onChange = { reported += "change" },
                        ),
                )
            }
        }

        val pane = onNodeOfType<JTextPane>().fetch<JTextPane>()
        pane.document.insertString(0, "ab", null)
        pane.document.remove(0, 1)
        // A change of attributes is the only thing that reports a change rather than an edit, and a
        // styled document is what carries attributes.
        pane.styledDocument.setCharacterAttributes(0, 1, SimpleAttributeSet(), false)
        assertEquals(
            listOf("insert", "remove", "change"),
            reported,
            "the insertion, the removal and the attribute change each reach the lambda declared for it",
        )
    }

    @Test
    fun anUndeclaredMethodReportsNowhere() = runComposeSwingTest {
        var inserts = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextField() },
                    modifier = SwingModifier.documentListener(onInsert = { inserts++ }),
                )
            }
        }

        val document = onNodeOfType<JTextField>().fetch<JTextField>().document
        document.insertString(0, "ab", null)
        document.remove(0, 1)
        assertEquals(1, inserts, "a method left undeclared runs nothing, rather than another's lambda")
    }

    @Test
    fun aCatchAllMouseLambdaRunsForEachOfTheInterfacesMethods() = runComposeSwingTest {
        var calls = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextField() },
                    modifier = SwingModifier.mouseListener { calls++ },
                )
            }
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        val event = MouseEvent(field, MouseEvent.MOUSE_CLICKED, 0L, 0, 0, 0, 1, false)
        field.mouseListeners.forEach { listener ->
            listener.mouseClicked(event)
            listener.mousePressed(event)
            listener.mouseReleased(event)
            listener.mouseEntered(event)
            listener.mouseExited(event)
        }
        assertEquals(5, calls, "one interaction lambda stands for all five methods of the interface")
    }

    @Test
    fun aNamedMouseLambdaRunsOnlyForItsOwnMethod() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextField() },
                    modifier =
                        SwingModifier.mouseListener(
                            onMouseClicked = { reported += "clicked" },
                            onMousePressed = { reported += "pressed" },
                            onMouseReleased = { reported += "released" },
                            onMouseEntered = { reported += "entered" },
                            onMouseExited = { reported += "exited" },
                        ),
                )
            }
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        val event = MouseEvent(field, MouseEvent.MOUSE_CLICKED, 0L, 0, 0, 0, 1, false)
        field.mouseListeners.forEach { listener ->
            listener.mouseClicked(event)
            listener.mousePressed(event)
            listener.mouseReleased(event)
            listener.mouseEntered(event)
        }
        assertEquals(
            listOf("clicked", "pressed", "released", "entered"),
            reported,
            "each method reaches its own lambda, and the exit left unfired reaches none of them",
        )
    }

    @Test
    fun aFocusLambdaTellsTheTwoDirectionsApart() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextField() },
                    modifier =
                        SwingModifier.focusListener(
                            onFocusGained = { reported += "gained" },
                            onFocusLost = { reported += "lost" },
                        ),
                )
            }
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        val event = FocusEvent(field, FocusEvent.FOCUS_GAINED)
        field.focusListeners.forEach {
            it.focusGained(event)
            it.focusLost(event)
        }
        assertEquals(
            listOf("gained", "lost"),
            reported,
            "taking the focus and losing it each reach the lambda declared for it",
        )
    }

    @Test
    fun aCatchAllFocusLambdaRunsForBothDirections() = runComposeSwingTest {
        var calls = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextField() },
                    modifier = SwingModifier.focusListener { calls++ },
                )
            }
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        val event = FocusEvent(field, FocusEvent.FOCUS_GAINED)
        field.focusListeners.forEach {
            it.focusGained(event)
            it.focusLost(event)
        }
        assertEquals(2, calls, "both methods of the interface reach the one lambda standing for it")
    }

    @Test
    fun aWillCollapseLambdaAnsweringFalseLeavesTheNodeOpen() = runComposeSwingTest {
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTree() },
                    modifier = SwingModifier.treeWillExpandListener(onWillCollapse = { false }),
                )
            }
        }

        val tree = onNodeOfType<JTree>().fetch<JTree>()
        assertTrue(tree.isExpanded(0), "the tree starts with its root open")
        tree.collapseRow(0)
        assertTrue(tree.isExpanded(0), "and refusing the collapse leaves it open")
    }

    @Test
    fun aWillCollapseLambdaAnsweringTrueLetsTheNodeClose() = runComposeSwingTest {
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTree() },
                    modifier = SwingModifier.treeWillExpandListener(onWillCollapse = { true }),
                )
            }
        }

        val tree = onNodeOfType<JTree>().fetch<JTree>()
        tree.collapseRow(0)
        assertTrue(!tree.isExpanded(0), "allowing the collapse closes the node")
    }

    @Test
    fun aWillExpandLambdaAnsweringFalseLeavesTheNodeClosed() = runComposeSwingTest {
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTree() },
                    modifier =
                        SwingModifier.treeWillExpandListener(
                            onWillExpand = { false },
                            onWillCollapse = { true },
                        ),
                )
            }
        }

        val tree = onNodeOfType<JTree>().fetch<JTree>()
        assertTrue(!tree.isExpanded(1), "the tree starts with the root's first child closed")
        tree.expandRow(1)
        assertTrue(!tree.isExpanded(1), "and refusing the expansion leaves it closed")
        tree.collapseRow(0)
        assertTrue(!tree.isExpanded(0), "while the collapse, allowed by its own lambda, happens")
    }

    @Test
    fun aWillExpandLambdaAnsweringTrueLetsTheNodeOpen() = runComposeSwingTest {
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTree() },
                    modifier =
                        SwingModifier.treeWillExpandListener(
                            onWillExpand = { true },
                            onWillCollapse = { false },
                        ),
                )
            }
        }

        val tree = onNodeOfType<JTree>().fetch<JTree>()
        tree.expandRow(1)
        assertTrue(tree.isExpanded(1), "allowing the expansion opens the node")
        tree.collapseRow(0)
        assertTrue(tree.isExpanded(0), "while the collapse, refused by its own lambda, leaves it open")
    }

    @Test
    fun aCatchAllWillExpandLambdaAnswersBothDirections() = runComposeSwingTest {
        var calls = 0
        var answer = true
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTree() },
                    modifier =
                        SwingModifier.treeWillExpandListener {
                            calls++
                            answer
                        },
                )
            }
        }

        val tree = onNodeOfType<JTree>().fetch<JTree>()
        tree.collapseRow(0)
        assertTrue(!tree.isExpanded(0), "the one lambda allows the closing it is asked about")
        answer = false
        tree.expandRow(0)
        assertTrue(!tree.isExpanded(0), "and refusing the opening leaves the node closed")
        answer = true
        tree.expandRow(0)
        assertTrue(tree.isExpanded(0), "until it allows that too")
        assertEquals(3, calls, "both directions of the interface reach the one lambda standing for it")
    }

    @Test
    fun aMouseMotionLambdaTellsADragFromAMove() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        var everything = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextField() },
                    modifier =
                        SwingModifier
                            .mouseMotionListener(
                                onMouseDragged = { reported += "dragged" },
                                onMouseMoved = { reported += "moved" },
                            ).mouseMotionListener { everything++ },
                )
            }
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        val moved = MouseEvent(field, MouseEvent.MOUSE_MOVED, 0L, 0, 0, 0, 0, false)
        val dragged = MouseEvent(field, MouseEvent.MOUSE_DRAGGED, 0L, 0, 0, 0, 0, false)
        field.mouseMotionListeners.forEach {
            it.mouseMoved(moved)
            it.mouseDragged(dragged)
        }
        assertEquals(
            listOf("moved", "dragged"),
            reported,
            "the move and the drag each reach the lambda declared for it",
        )
        assertEquals(2, everything, "and both reach the one lambda standing for the whole interface")
    }

    @Test
    fun aKeyLambdaTellsThePressFromTheRelease() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        var everything = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextField() },
                    modifier =
                        SwingModifier
                            .keyListener(
                                onKeyPressed = { reported += "pressed" },
                                onKeyReleased = { reported += "released" },
                            ).keyListener { everything++ },
                )
            }
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        val press = KeyEvent(field, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_A, 'a')
        val release = KeyEvent(field, KeyEvent.KEY_RELEASED, 0L, 0, KeyEvent.VK_A, 'a')
        field.keyListeners.forEach {
            it.keyTyped(press)
            it.keyPressed(press)
            it.keyReleased(release)
        }
        assertEquals(
            listOf("pressed", "released"),
            reported,
            "the press and the release each reach the lambda declared for it, and the typing left " +
                "undeclared reaches neither",
        )
        assertEquals(
            3,
            everything,
            "and all three of the interface's methods reach the one lambda standing for it",
        )
    }

    @Test
    fun aComponentLambdaTellsAResizeFromAMove() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        var everything = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTextField() },
                    modifier =
                        SwingModifier
                            .componentListener(
                                onComponentResized = { reported += "resized" },
                                onComponentMoved = { reported += "moved" },
                                onComponentShown = { reported += "shown" },
                                onComponentHidden = { reported += "hidden" },
                            ).componentListener { everything++ },
                )
            }
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        // The panel lays the field out, which fires real geometry events; only what the synthetic ones
        // add is what this test is about.
        reported.clear()
        val everythingBefore = everything
        val resized = ComponentEvent(field, ComponentEvent.COMPONENT_RESIZED)
        val moved = ComponentEvent(field, ComponentEvent.COMPONENT_MOVED)
        val shown = ComponentEvent(field, ComponentEvent.COMPONENT_SHOWN)
        val hidden = ComponentEvent(field, ComponentEvent.COMPONENT_HIDDEN)
        field.componentListeners.forEach {
            it.componentResized(resized)
            it.componentMoved(moved)
            it.componentShown(shown)
        }
        assertEquals(
            listOf("resized", "moved", "shown"),
            reported,
            "the resize, the move and the showing each reach the lambda declared for it, while the " +
                "hiding left unfired reaches none of them",
        )
        field.componentListeners.forEach { it.componentHidden(hidden) }
        assertEquals(
            listOf("resized", "moved", "shown", "hidden"),
            reported,
            "and the hiding reaches its own lambda once it does run",
        )
        assertEquals(
            4,
            everything - everythingBefore,
            "and all four of the interface's methods reach the one lambda standing for it",
        )
    }

    @Test
    fun aContainerLambdaTellsAnAdditionFromARemoval() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        var everything = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JToolBar() },
                    modifier =
                        SwingModifier
                            .containerListener(
                                onComponentAdded = { reported += "added" },
                                onComponentRemoved = { reported += "removed" },
                            ).containerListener { everything++ },
                )
            }
        }

        val panel = onNodeOfType<JToolBar>().fetch<JToolBar>()
        val child = JTextField()
        // The composition fills the panel, which fires real container events; only what the synthetic
        // ones add is what this test is about.
        reported.clear()
        val everythingBefore = everything
        val addition = ContainerEvent(panel, ContainerEvent.COMPONENT_ADDED, child)
        val removal = ContainerEvent(panel, ContainerEvent.COMPONENT_REMOVED, child)
        panel.containerListeners.forEach {
            it.componentAdded(addition)
            it.componentRemoved(removal)
        }
        assertEquals(
            listOf("added", "removed"),
            reported,
            "the addition and the removal each reach the lambda declared for it",
        )
        assertEquals(
            2,
            everything - everythingBefore,
            "and both reach the one lambda standing for the whole interface",
        )
    }

    @Test
    fun anInternalFrameLambdaTellsTheFramesChangesApart() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        var everything = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JInternalFrame("frame") },
                    modifier =
                        SwingModifier
                            .internalFrameListener(
                                onFrameOpened = { reported += "opened" },
                                onFrameClosing = { reported += "closing" },
                                onFrameClosed = { reported += "closed" },
                                onFrameIconified = { reported += "iconified" },
                                onFrameDeiconified = { reported += "deiconified" },
                                onFrameActivated = { reported += "activated" },
                                onFrameDeactivated = { reported += "deactivated" },
                            ).internalFrameListener { everything++ },
                )
            }
        }

        val frame = onNodeOfType<JInternalFrame>().fetch<JInternalFrame>()
        val event = InternalFrameEvent(frame, InternalFrameEvent.INTERNAL_FRAME_OPENED)
        frame.internalFrameListeners.forEach {
            it.internalFrameOpened(event)
            it.internalFrameClosing(event)
            it.internalFrameClosed(event)
            it.internalFrameIconified(event)
            it.internalFrameDeiconified(event)
            it.internalFrameActivated(event)
            it.internalFrameDeactivated(event)
        }
        assertEquals(
            listOf("opened", "closing", "closed", "iconified", "deiconified", "activated", "deactivated"),
            reported,
            "each change reaches the lambda declared for it, in the order the changes were announced",
        )
        assertEquals(
            7,
            everything,
            "and all seven of the interface's methods reach the one lambda standing for it",
        )
    }

    @Test
    fun aTreeExpansionLambdaTellsAnOpeningFromAClosing() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        var everything = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JTree() },
                    modifier =
                        SwingModifier
                            .treeExpansionListener(
                                onTreeExpanded = { reported += "expanded" },
                                onTreeCollapsed = { reported += "collapsed" },
                            ).treeExpansionListener { everything++ },
                )
            }
        }

        val tree = onNodeOfType<JTree>().fetch<JTree>()
        tree.collapseRow(0)
        tree.expandRow(0)
        assertEquals(
            listOf("collapsed", "expanded"),
            reported,
            "the closing and the opening each reach the lambda declared for it",
        )
        assertEquals(2, everything, "and both reach the one lambda standing for the whole interface")
    }
}
