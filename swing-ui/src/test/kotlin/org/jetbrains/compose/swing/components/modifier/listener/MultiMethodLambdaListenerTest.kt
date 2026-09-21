// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.modifier.listener

import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.desktop.DesktopPane
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JInternalFrame
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.JTree
import javax.swing.event.InternalFrameEvent
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyledEditorKit
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
            Panel {
                TextPane(
                    state = rememberDocumentState(remember { StyledEditorKit() }, ""),
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
            Panel {
                TextPane(
                    state = rememberDocumentState(remember { StyledEditorKit() }, ""),
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
            Panel {
                TextField(
                    state = rememberDocumentState(remember { StyledEditorKit() }, ""),
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
    fun aWillCollapseLambdaAnsweringFalseLeavesTheNodeOpen() = runComposeSwingTest {
        setContent {
            Panel {
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
            Panel {
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
            Panel {
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
            Panel {
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
            Panel {
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
    fun anInternalFrameLambdaTellsTheFramesChangesApart() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        var everything = 0
        setContent {
            Panel {
                DesktopPane {
                    InternalFrame(
                        title = "frame",
                        bounds = Rectangle(),
                        onClose = {},
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
                    ) {}
                }
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
            Panel {
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
