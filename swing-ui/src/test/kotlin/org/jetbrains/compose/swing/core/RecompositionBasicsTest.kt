package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertSame

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

        val label = onNodeOfType<JLabel>()
        label.assertTextEquals("Hello, world")

        name = "compose"
        awaitIdle()

        label.assertTextEquals("Hello, compose")
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

        val always = onNodeWithText("always")
        val conditional = onNodeWithText("conditional")

        always.assertExists()
        conditional.assertDoesNotExist()

        visible = true
        awaitIdle()
        conditional.assertExists()

        visible = false
        awaitIdle()
        conditional.assertDoesNotExist()
        // The unconditional sibling is unaffected by the add/remove churn.
        always.assertExists()
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

        assertLabelsInOrder("a", "b", "c")
        // The live instances, so the reorder can be shown to move rather than rebuild them.
        val a = onNodeWithText("a").fetch<JLabel>()
        val b = onNodeWithText("b").fetch<JLabel>()
        val c = onNodeWithText("c").fetch<JLabel>()

        // Reverse the list: same keys, new order. Keyed children keep their component instances.
        items.clear()
        items.addAll(listOf("c", "b", "a"))
        awaitIdle()

        assertLabelsInOrder("c", "b", "a")
        assertSame(a, onNodeWithText("a").fetch<JLabel>(), "\"a\" must be moved across the reorder, not recreated")
        assertSame(b, onNodeWithText("b").fetch<JLabel>(), "\"b\" must be moved across the reorder, not recreated")
        assertSame(c, onNodeWithText("c").fetch<JLabel>(), "\"c\" must be moved across the reorder, not recreated")
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
        val xBefore = onNodeWithText("x").fetch<JLabel>()

        items.add("z")
        awaitIdle()

        assertLabelsInOrder("x", "y", "z")
        assertSame(
            xBefore,
            onNodeWithText("x").fetch<JLabel>(),
            "the existing keyed item \"x\" must survive a sibling being added",
        )
    }

    /** Asserts the composition holds exactly the labels reading [texts], in that order. */
    private fun ComposeSwingTest.assertLabelsInOrder(vararg texts: String) {
        val labels = onAllNodesOfType<JLabel>()
        labels.assertCountEquals(texts.size)
        texts.forEachIndexed { index, text ->
            labels[index].assertTextEquals(text)
        }
    }
}
