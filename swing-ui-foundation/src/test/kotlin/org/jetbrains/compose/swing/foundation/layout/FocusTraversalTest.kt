package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tab order through Foundation containers, which Swing's own layout-ordered traversal gives. Each traversal is
 * asked of the focus cycle's policy, so no test takes focus from the machine it runs on.
 */
class FocusTraversalTest {
    /**
     * A Tab skips a child the policy stops placing, as androidx's focus search skips a node its parent leaves unplaced,
     * and reaches it again once it is placed again.
     */
    @Test
    fun `a tab skips a child the policy stops placing`() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var placesSecond by mutableStateOf(true)
            setWindowContent {
                Panel(PanelLayout.Flow()) {
                    Field("before")
                    Layout(
                        content = {
                            Field("a")
                            Field("b")
                        },
                        measurePolicy = { measurables, constraints ->
                            val (first, second) = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
                            layout(first.width + second.width, maxOf(first.height, second.height)) {
                                first.place(0, 0)
                                if (placesSecond) second.place(first.width, 0)
                            }
                        },
                    )
                    Field("after")
                }
            }

            placesSecond = false
            awaitIdle()

            assertTabOrder("before", "a", "after", "before")
            assertShiftTabOrder("after", "a", "before", "after")

            placesSecond = true
            awaitIdle()

            assertTabOrder("before", "a", "b", "after", "before")
        }
}

@Composable
private fun Field(
    tag: String,
    modifier: SwingModifier = SwingModifier,
) = TextField("", onValueChange = {}, columns = 4, modifier = modifier.testTag(tag))

/** Asserts that Tab after Tab from the component tagged first in [tags] reaches the ones tagged after it, in order. */
private fun ComposeSwingTest.assertTabOrder(vararg tags: String) =
    assertWalk(tags) { root, component -> root.focusTraversalPolicy.getComponentAfter(root, component) }

/** Asserts that Shift+Tab after Shift+Tab from the component tagged first in [tags] reaches the ones after it. */
private fun ComposeSwingTest.assertShiftTabOrder(vararg tags: String) =
    assertWalk(tags) { root, component -> root.focusTraversalPolicy.getComponentBefore(root, component) }

private fun ComposeSwingTest.assertWalk(
    tags: Array<out String>,
    step: (Container, Component) -> Component,
) {
    val window = onWindowWithTitle(WINDOW_TITLE)
    val tagOf = tags.associateBy { window.onNodeWithTag(it).fetch() }
    var component = window.onNodeWithTag(tags.first()).fetch()
    val root = component.focusCycleRootAncestor
    val reached =
        List(tags.size) { index ->
            if (index > 0) component = step(root, component)
            tagOf[component] ?: component.toString()
        }
    assertEquals(tags.toList(), reached, "the traversal did not reach the components in order")
}
