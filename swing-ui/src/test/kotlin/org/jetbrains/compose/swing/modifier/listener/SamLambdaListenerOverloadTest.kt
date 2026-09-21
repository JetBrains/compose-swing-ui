// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JCheckBox
import javax.swing.JScrollBar
import javax.swing.JTextField
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
            Panel {
                CheckBox(
                    text = "",
                    checked = false,
                    onCheckedChange = {},
                    modifier = SwingModifier.itemListener { reports++ },
                )
            }
        }
        onNodeOfType<JCheckBox>().fetch<JCheckBox>().isSelected = true
        assertEquals(1, reports, "selecting the box reports over the item channel")
    }

    @Test
    fun anAdjustmentLambdaReportsAScroll() = runComposeSwingTest {
        var reports = 0
        setContent {
            Panel {
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
    fun aPropertyChangeLambdaReportsEveryBoundProperty() = runComposeSwingTest {
        val seen = mutableListOf<String>()
        setContent {
            Panel {
                TextField(
                    value = "",
                    onValueChange = {},
                    modifier = SwingModifier.propertyChangeListener { seen += it.propertyName },
                )
            }
        }
        onNodeOfType<JTextField>().fetch<JTextField>().isEnabled = false
        assertTrue("enabled" in seen, "an unbound lambda hears every bound property")
    }

    @Test
    fun aMouseWheelLambdaHearsTheTurnTheEventCarries() = runComposeSwingTest {
        val turns = mutableListOf<Int>()
        setContent {
            Panel {
                TextField(
                    value = "",
                    onValueChange = {},
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
            Panel {
                TextField(
                    value = "",
                    onValueChange = {},
                    modifier = SwingModifier.hierarchyListener { reports++ },
                )
            }
        }
        assertTrue(reports > 0, "being added to the panel reports over the hierarchy channel")
    }
}
