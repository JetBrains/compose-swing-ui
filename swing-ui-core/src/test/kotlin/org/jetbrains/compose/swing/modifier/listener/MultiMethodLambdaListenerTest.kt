// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ToolBar
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ComponentEvent
import java.awt.event.ContainerEvent
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JTextField
import javax.swing.JToolBar
import javax.swing.text.StyledEditorKit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A listener interface with several methods reaches a caller two ways: one lambda that every method
 * calls, or one lambda per method. Which of them a call selects is decided by whether it names a method.
 */
class MultiMethodLambdaListenerTest {
    @Test
    fun aCatchAllMouseLambdaRunsForEachOfTheInterfacesMethods() = runComposeSwingTest {
        var calls = 0
        setContent {
            Panel {
                TextField(
                    state = rememberDocumentState(remember { StyledEditorKit() }, ""),
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
            Panel {
                TextField(
                    state = rememberDocumentState(remember { StyledEditorKit() }, ""),
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
            Panel {
                TextField(
                    state = rememberDocumentState(remember { StyledEditorKit() }, ""),
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
            Panel {
                TextField(
                    state = rememberDocumentState(remember { StyledEditorKit() }, ""),
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
    fun aMouseMotionLambdaTellsADragFromAMove() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        var everything = 0
        setContent {
            Panel {
                TextField(
                    state = rememberDocumentState(remember { StyledEditorKit() }, ""),
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
            Panel {
                TextField(
                    state = rememberDocumentState(remember { StyledEditorKit() }, ""),
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
            Panel {
                TextField(
                    state = rememberDocumentState(remember { StyledEditorKit() }, ""),
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
            Panel(layout = PanelLayout.Border()) {
                ToolBar(
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
}
