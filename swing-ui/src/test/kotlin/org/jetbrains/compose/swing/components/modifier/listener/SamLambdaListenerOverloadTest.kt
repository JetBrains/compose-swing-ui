// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.toolTip
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JEditorPane
import javax.swing.JList
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
    fun aChangeLambdaReportsASliderMove() = runComposeSwingTest {
        var reports = 0
        setContent {
            Panel {
                Slider(
                    value = 50,
                    onValueChange = {},
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
            Panel {
                TextField(
                    value = "",
                    onValueChange = {},
                    modifier = SwingModifier.caretListener { reports++ },
                )
            }
        }
        onNodeOfType<JTextField>().fetch<JTextField>().text = "ab"
        assertTrue(reports > 0, "typing moves the caret and reports over the caret channel")
    }

    @Test
    fun aListSelectionLambdaReportsASelection() = runComposeSwingTest {
        var reports = 0
        setContent {
            Panel {
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
            Panel {
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
    fun aHyperlinkLambdaHearsTheLinkEventTheEditorPanePublishes() = runComposeSwingTest {
        val seen = mutableListOf<HyperlinkEvent>()
        setContent {
            Panel {
                EditorPane(
                    state = rememberDocumentState(),
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
    fun aLambdaDeclaredAfreshKeepsTheListenerItRegistered() = runComposeSwingTest {
        var reports = 0
        var declared by mutableStateOf(1)
        setContent {
            Panel {
                // Captured while the chain is built, so the value each lambda reports is the one its own
                // pass declared: a listener left holding the first lambda reports 1.
                val captured = declared
                Slider(
                    value = 50,
                    onValueChange = {},
                    modifier = SwingModifier.changeListener { reports += captured },
                    // Captured above, so a new value recomposes this node and rebuilds the chain with a lambda
                    // written afresh.
                    style = { toolTip(captured.toString()) },
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
