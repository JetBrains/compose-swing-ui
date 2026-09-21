// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertAskedForLayout
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import javax.swing.JList
import javax.swing.JTree
import kotlin.test.Test

/**
 * Behavioral tests that a declared visible row count reaches the viewport a widget is scrolled in.
 *
 * The count is what the widget returns to a scroll pane for its viewport, and neither setter asks for
 * the layout pass that would act on the count the widget returns: `JTree.setVisibleRowCount` marks the tree invalid
 * and schedules nothing, and `JList.setVisibleRowCount` only fires a property change - the look and feel
 * acts on it where the list wraps its rows, never in the vertical orientation a list carries by default.
 * So a count that changes in a recomposition leaves the pane at its old height unless the write asks for
 * the pass itself.
 */
class VisibleRowCountLayoutTest {
    private val fruit = listOf("apple", "pear", "plum", "cherry", "fig", "quince")

    @Test
    fun aTreesVisibleRowCountChangeAsksForALayoutPass() = runComposeSwingTest {
        var rows by mutableStateOf(4)
        setContent {
            Tree(root = "root", children = { emptyList<String>() }, label = { it }, visibleRowCount = rows)
        }
        awaitIdle()

        val tree = onNodeOfType<JTree>().fetch()
        withRecordedRepaints { recorded ->
            rows = 12
            awaitIdle()

            onNodeOfType<JTree>().assertProperty(12, "the declared count should reach the tree") { visibleRowCount }
            recorded.assertAskedForLayout(tree, "a new declared visible row count")
        }
    }

    @Test
    fun aListsVisibleRowCountChangeAsksForALayoutPass() = runComposeSwingTest {
        var rows by mutableStateOf(4)
        setContent { ListBox(items = fruit, visibleRowCount = rows) }
        awaitIdle()

        val list = onNodeOfType<JList<*>>().fetch()
        withRecordedRepaints { recorded ->
            rows = 12
            awaitIdle()

            onNodeOfType<JList<*>>().assertProperty(12, "the declared count should reach the list") { visibleRowCount }
            recorded.assertAskedForLayout(list, "a new declared visible row count")
        }
    }
}
