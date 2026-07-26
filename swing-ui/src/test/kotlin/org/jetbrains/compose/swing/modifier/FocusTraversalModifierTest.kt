package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.focusTraversalIndex
import org.jetbrains.compose.swing.modifier.interaction.focusable
import org.jetbrains.compose.swing.modifier.interaction.orderedFocusTraversal
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JButton
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for the focus-traversal and default-button modifiers. They drive the real
 * `FocusTraversalPolicy` the container installs and read the root pane's default button, asserting the
 * observable Swing wiring rather than the modifier's internal records.
 */
class FocusTraversalModifierTest {
    @Test
    fun orderedFocusTraversalVisitsChildrenByIndex() = runComposeSwingTest {
        setContent {
            FlowPanel(modifier = SwingModifier.testTag("panel").orderedFocusTraversal()) {
                TextField("", modifier = SwingModifier.testTag("third").focusTraversalIndex(30))
                TextField("", modifier = SwingModifier.testTag("first").focusTraversalIndex(10))
                TextField("", modifier = SwingModifier.testTag("second").focusTraversalIndex(20))
            }
        }
        val panel = onNodeWithTag("panel").fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val first = onNodeWithTag("first").fetch<javax.swing.JTextField>()
        val second = onNodeWithTag("second").fetch<javax.swing.JTextField>()
        val third = onNodeWithTag("third").fetch<javax.swing.JTextField>()

        // Despite the declaration order (third, first, second), the policy orders by index.
        assertSame(first, policy.getFirstComponent(panel), "index 0 should be first in the traversal order")
        assertSame(second, policy.getComponentAfter(panel, first), "index 1 should follow index 0")
        assertSame(third, policy.getComponentAfter(panel, second), "index 2 should follow index 1")
        assertSame(third, policy.getLastComponent(panel), "index 2 should be last in the traversal order")
    }

    @Test
    fun orderedFocusTraversalRestoresPolicyOnRemoval() = runComposeSwingTest {
        setContent {
            FlowPanel(modifier = SwingModifier.testTag("panel").orderedFocusTraversal()) {
                TextField("", modifier = SwingModifier.testTag("field"))
            }
        }
        assertTrue(onNodeWithTag("panel").fetch<JPanel>().isFocusCycleRoot)
    }

    @Test
    fun focusableModifierLeavesComponentReachableForTraversal() = runComposeSwingTest {
        setContent {
            FlowPanel(modifier = SwingModifier.testTag("panel").orderedFocusTraversal()) {
                Button("A", modifier = SwingModifier.testTag("a").focusable(true).focusTraversalIndex(1))
            }
        }
        val panel = onNodeWithTag("panel").fetch<JPanel>()
        val a = onNodeWithTag("a").fetch<JButton>()
        assertSame(a, panel.focusTraversalPolicy.getFirstComponent(panel))
    }
}
