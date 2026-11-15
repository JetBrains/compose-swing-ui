// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.listSelectionListener
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.event.ListSelectionListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `listSelectionListener` modifier on a [ListBox] hands the caller the widget's own selection events,
 * unfiltered: a drag across rows arrives as the run of adjusting events it is, followed by the settled
 * one. A caller that wants only the settled selection reads it from the list's state instead.
 *
 * A user's drag reaches a widget as a run of selection writes bracketed by `valueIsAdjusting`, so these
 * tests drive the widget's own selection model to stand in for one.
 */
class RawSelectionListenerTest {
    @Test
    fun aListSelectionListenerSeesTheAdjustingEventsOfADrag() = runComposeSwingTest {
        val adjusting = mutableListOf<Boolean>()
        val listener = ListSelectionListener { event -> adjusting += event.valueIsAdjusting }
        setContent {
            ListBox(
                items = listOf("red", "green", "blue"),
                modifier = SwingModifier.listSelectionListener(listener),
            )
        }

        val list = onNodeOfType<JList<*>>().fetch()
        adjusting.clear()

        val selection = list.selectionModel
        selection.valueIsAdjusting = true
        selection.setSelectionInterval(0, 0)
        selection.setSelectionInterval(0, 1)
        selection.valueIsAdjusting = false

        assertTrue(adjusting.any { it }, "the drag's adjusting events reach the listener")
        assertEquals(false, adjusting.last(), "the settled event reaches the listener")
    }

    @Test
    fun aListSelectionListenerSeesTheSettledEventOfADragEndingWhereItBegan() = runComposeSwingTest {
        val adjusting = mutableListOf<Boolean>()
        val listener = ListSelectionListener { event -> adjusting += event.valueIsAdjusting }
        setContent {
            ListBox(
                items = listOf("red", "green", "blue"),
                modifier = SwingModifier.listSelectionListener(listener),
            )
        }

        val selection = onNodeOfType<JList<*>>().fetch().selectionModel
        selection.setSelectionInterval(0, 0)
        adjusting.clear()

        selection.valueIsAdjusting = true
        selection.setSelectionInterval(1, 1)
        selection.setSelectionInterval(0, 0)
        selection.valueIsAdjusting = false

        assertEquals(false, adjusting.lastOrNull(), SETTLED_EVENT_OF_A_DRAG_ENDING_WHERE_IT_BEGAN)
    }
}

/**
 * What a drag ending on the row it began on owes the caller's own listener. The widget still raises the
 * settled event, so the caller still hears it: what reaches the listener follows the write depth alone,
 * as it does without a mirror, rather than whether the selection ended somewhere new.
 */
private const val SETTLED_EVENT_OF_A_DRAG_ENDING_WHERE_IT_BEGAN =
    "the settled event of a drag that ends on the row it began on reaches the listener"
