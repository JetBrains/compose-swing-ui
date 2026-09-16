package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.orderedFocusTraversal
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import kotlin.test.Test
import kotlin.test.assertSame

/** Tab order through a column. The focus cycle's policy is asked directly, so no test takes focus. */
class ColumnFocusTraversalTest {
    @Test
    fun `an ordered focus cycle walks a column's unindexed children top to bottom`() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            setContent {
                Window(onCloseRequest = {}, title = "column-focus-traversal") {
                    Column(SwingModifier.testTag("column").orderedFocusTraversal()) {
                        TextField("a", onValueChange = {})
                        TextField("b", onValueChange = {})
                        TextField("c", onValueChange = {})
                    }
                }
            }

            val window = onWindowWithTitle("column-focus-traversal")
            val column = window.onNodeWithTag("column").fetch<Container>()
            val (a, b, c) = listOf("a", "b", "c").map { window.onNodeWithText(it).fetch<Component>() }
            val policy = column.focusTraversalPolicy
            assertSame(a, policy.getFirstComponent(column), "the top child starts the order")
            assertSame(b, policy.getComponentAfter(column, a), "Tab moves down the column")
            assertSame(c, policy.getComponentAfter(column, b), "Tab moves down the column")
            assertSame(c, policy.getLastComponent(column), "the bottom child ends the order")
            assertSame(b, policy.getComponentBefore(column, c), "Shift+Tab moves up the column")
        }
}
