package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Container
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Core recomposition behaviors driven through the real composition + frame-clock + apply pipeline:
 * state-driven text updates, conditional child insertion/removal, and keyed reordering without
 * dropping component identity.
 */
class RecompositionBasicsTest {
    @Test
    fun stateChangeUpdatesLabelText() = runComposeSwingTest {
        var name by mutableStateOf("world")
        setContent {
            Label(text = "Hello, $name")
        }

        onNodeWithText("Hello, world").assertExists().assertTextEquals("Hello, world")

        name = "compose"
        awaitIdle()

        onNodeWithText("Hello, world").assertDoesNotExist()
        onNodeWithText("Hello, compose").assertExists().assertTextEquals("Hello, compose")
    }

    @Test
    fun conditionalChildIsAddedAndRemoved() = runComposeSwingTest {
        var visible by mutableStateOf(false)
        setContent {
            BoxPanel {
                Label(text = "always")
                if (visible) Label(text = "conditional")
            }
        }

        onNodeWithText("always").assertExists()
        onNodeWithText("conditional").assertDoesNotExist()

        visible = true
        awaitIdle()
        onNodeWithText("conditional").assertExists()

        visible = false
        awaitIdle()
        onNodeWithText("conditional").assertDoesNotExist()
        // The unconditional sibling is unaffected by the add/remove churn.
        onNodeWithText("always").assertExists()
    }

    @Test
    fun keyedListReordersWithoutLosingComponents() = runComposeSwingTest {
        val items = mutableStateListOf("a", "b", "c")
        setContent {
            BoxPanel {
                for (item in items) {
                    key(item) { Label(text = item) }
                }
            }
        }

        // Capture identity of each label before reordering.
        val before = labelIdentitiesByText()
        assertEquals(listOf("a", "b", "c"), labelTextsInOrder(), "the labels should start in declaration order")

        // Reverse the list: same keys, new order. Keyed children keep their component instances.
        items.clear()
        items.addAll(listOf("c", "b", "a"))
        awaitIdle()

        val after = labelIdentitiesByText()
        assertEquals(listOf("c", "b", "a"), labelTextsInOrder(), "the labels should render in the reversed order")

        // Same set of component instances, just reordered — none recreated, none lost.
        assertEquals(before.keys, after.keys, "the same set of labels should remain after the reorder")
        for (text in before.keys) {
            check(before.getValue(text) === after.getValue(text)) {
                "Label \"$text\" was recreated across reorder instead of being moved."
            }
        }
    }

    @Test
    fun addingListItemKeepsExistingComponentIdentity() = runComposeSwingTest {
        val items = mutableStateListOf("x", "y")
        setContent {
            BoxPanel {
                for (item in items) {
                    key(item) { Label(text = item) }
                }
            }
        }
        val xBefore = labelIdentitiesByText().getValue("x")

        items.add("z")
        awaitIdle()

        onNodeWithText("z").assertExists()
        val xAfter = labelIdentitiesByText().getValue("x")
        check(xBefore === xAfter) { "Existing keyed item \"x\" was recreated when a sibling was added." }
    }

    private fun ComposeSwingTest.labelTextsInOrder(): List<String> = root.collectLabels().map { it.text }

    private fun ComposeSwingTest.labelIdentitiesByText(): Map<String, JLabel> =
        root.collectLabels().associateBy { it.text }
}

private fun Container.collectLabels(): List<JLabel> {
    val result = mutableListOf<JLabel>()

    fun visit(container: Container) {
        for (child in container.components) {
            if (child is JLabel) result += child
            if (child is Container) visit(child)
        }
    }
    visit(this)
    return result
}
