package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JButton
import javax.swing.JToolBar
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral tests for [ToolBar]. They assert what an observer of the live [JToolBar] sees: the
 * declared items become its children in order, orientation and floatable map through, and items added
 * or removed in the composition appear and disappear from the tool bar.
 */
class ToolBarBehaviorTest {
    private fun ComposeSwingTest.toolBar(): JToolBar = onNodeOfType<JToolBar>().fetch()

    @Test
    fun declaredItemsBecomeToolBarChildrenInOrder() = runComposeSwingTest {
        setContent {
            ToolBar {
                Button(text = "New", onClick = {})
                Button(text = "Open", onClick = {})
            }
        }

        val bar = toolBar()
        val buttonTexts = bar.components.filterIsInstance<JButton>().map { it.text }
        assertEquals(listOf("New", "Open"), buttonTexts)
    }

    @Test
    fun orientationMapsThrough() = runComposeSwingTest {
        var orientation by mutableStateOf(SwingConstants.HORIZONTAL)
        setContent {
            ToolBar(orientation = orientation) {
                Label(text = "Item")
            }
        }

        val bar = toolBar()
        assertEquals(SwingConstants.HORIZONTAL, bar.orientation, "the tool bar should start horizontal")

        orientation = SwingConstants.VERTICAL
        awaitIdle()
        assertEquals(SwingConstants.VERTICAL, bar.orientation, "the orientation should map through to vertical")
    }

    @Test
    fun floatableMapsThrough() = runComposeSwingTest {
        var floatable by mutableStateOf(true)
        setContent {
            ToolBar(floatable = floatable) {
                Label(text = "Item")
            }
        }

        val bar = toolBar()
        assertTrue(bar.isFloatable, "the tool bar should start floatable")

        floatable = false
        awaitIdle()
        assertFalse(bar.isFloatable, "the floatable flag should map through to false")
    }

    @Test
    fun itemsAddedAndRemovedInCompositionAppearAndDisappear() = runComposeSwingTest {
        var showSecond by mutableStateOf(false)
        setContent {
            ToolBar {
                Button(text = "First", onClick = {})
                if (showSecond) {
                    Button(text = "Second", onClick = {})
                }
            }
        }

        val bar = toolBar()
        assertEquals(
            listOf("First"),
            bar.components.filterIsInstance<JButton>().map { it.text },
            "the tool bar should start with only the first item",
        )

        showSecond = true
        awaitIdle()
        assertEquals(
            listOf("First", "Second"),
            bar.components.filterIsInstance<JButton>().map { it.text },
            "adding should make the second item appear in order",
        )

        showSecond = false
        awaitIdle()
        assertEquals(
            listOf("First"),
            bar.components.filterIsInstance<JButton>().map { it.text },
            "removing should leave only the first item",
        )
    }
}
