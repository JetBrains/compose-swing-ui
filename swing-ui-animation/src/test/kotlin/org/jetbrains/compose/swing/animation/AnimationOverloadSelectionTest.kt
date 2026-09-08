package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pins the parent-scoped defaults and the top-level overload at marked or receiver-less call sites. */
class AnimationOverloadSelectionTest {
    @Test
    fun `a row uses the width-only visibility default`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Row(modifier = SwingModifier.testTag(PARENT)) {
                    AnimatedVisibility(visible, modifier = SwingModifier.testTag(CONTAINER)) { Body() }
                    Label("right", modifier = SwingModifier.testTag(RIGHT).preferredSize(5, 5))
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_INTO_TRANSITION) { driveOneFrame() }

            val rightSibling = onNodeWithTag(RIGHT).fetch()
            assertTrue(
                rightSibling.x in 1 until BODY_WIDTH,
                "the row did not allocate animated width before its sibling: ${rightSibling.bounds}",
            )
            assertEquals(0, rightSibling.y, "the row changed the cross-axis position: ${rightSibling.bounds}")
            assertEquals(
                BODY_HEIGHT,
                onNodeWithTag(PARENT).fetch().preferredSize.height,
                "the row changed the cross-axis size",
            )
        }

    @Test
    fun `a column uses the height-only visibility default`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Column(modifier = SwingModifier.testTag(PARENT)) {
                    AnimatedVisibility(visible, modifier = SwingModifier.testTag(CONTAINER)) { Body() }
                    Label("below", modifier = SwingModifier.testTag(BELOW).preferredSize(5, 5))
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_INTO_TRANSITION) { driveOneFrame() }

            val belowSibling = onNodeWithTag(BELOW).fetch()
            assertEquals(0, belowSibling.x, "the column changed the cross-axis position: ${belowSibling.bounds}")
            assertTrue(
                belowSibling.y in 1 until BODY_HEIGHT,
                "the column did not allocate animated height before its sibling: ${belowSibling.bounds}",
            )
            assertEquals(
                BODY_WIDTH,
                onNodeWithTag(PARENT).fetch().preferredSize.width,
                "the column changed the cross-axis size",
            )
        }

    @Test
    fun `a box uses the two-dimensional constrained visibility default`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Column {
                    Row {
                        Box {
                            AnimatedVisibility(visible, modifier = SwingModifier.testTag(CONTAINER)) { Body() }
                        }
                        Label("right", modifier = SwingModifier.testTag(RIGHT).preferredSize(5, 5))
                    }
                    Label("below", modifier = SwingModifier.testTag(BELOW).preferredSize(5, 5))
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_INTO_TRANSITION) { driveOneFrame() }

            val rightSibling = onNodeWithTag(RIGHT).fetch()
            val belowSibling = onNodeWithTag(BELOW).fetch()
            assertTrue(
                rightSibling.x in 1 until BODY_WIDTH,
                "the box did not allocate animated width before its sibling: ${rightSibling.bounds}",
            )
            assertTrue(
                belowSibling.y in 1 until BODY_HEIGHT,
                "the box did not allocate animated height before its sibling: ${belowSibling.bounds}",
            )
        }

    @Test
    fun `a package-qualified visibility call in nested panel content uses the top-level overload`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(visible = true) {
                    Panel {
                        org.jetbrains.compose.swing.animation.AnimatedVisibility(
                            visible,
                            modifier = SwingModifier.testTag(CONTAINER),
                        ) { Body() }
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_INTO_TRANSITION) { driveOneFrame() }

            val preferredSize = onNodeWithTag(CONTAINER).fetch().preferredSize
            assertTrue(preferredSize.width in 1 until BODY_WIDTH, "the panel child did not report its animated width")
            assertTrue(
                preferredSize.height in 1 until BODY_HEIGHT,
                "the panel child did not report its animated height",
            )
        }

    @Test
    fun `a package-qualified target-state content call compiles under nested panel content`() =
        runComposeSwingTest {
            setContent {
                AnimatedVisibility(visible = true) {
                    Panel {
                        org.jetbrains.compose.swing.animation.AnimatedContent(
                            targetState = "panel state",
                            modifier = SwingModifier.testTag(ANIMATED_CONTENT),
                        ) { targetState ->
                            Label(targetState)
                        }
                    }
                }
            }

            assertTrue(onNodeWithTag(ANIMATED_CONTENT).fetch().isVisible)
        }

    @Test
    fun `an extracted composable uses the unscoped overload inside a row`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Row {
                    ExtractedVisibility(visible)
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_INTO_TRANSITION) { driveOneFrame() }

            val container = onNodeWithTag(CONTAINER).fetch()
            assertTrue(container.isVisible, "the extracted composable did not mount its top-level container")
            assertTrue(
                container.preferredSize.width in 1 until BODY_WIDTH,
                "the extracted top-level overload did not animate its width: ${container.preferredSize}",
            )
            assertTrue(
                container.preferredSize.height in 1 until BODY_HEIGHT,
                "the extracted top-level overload did not animate its height: ${container.preferredSize}",
            )
        }

    @Composable
    private fun Body() = Label("Animated", modifier = SwingModifier.preferredSize(BODY_WIDTH, BODY_HEIGHT))

    private companion object {
        const val CONTAINER = "animated-container"
        const val PARENT = "parent"
        const val RIGHT = "right"
        const val BELOW = "below"
        const val ANIMATED_CONTENT = "animated-content"
        const val BODY_WIDTH = 80
        const val BODY_HEIGHT = 40
        const val FRAMES_INTO_TRANSITION = 4
    }
}

@Composable
private fun ExtractedVisibility(visible: Boolean) {
    AnimatedVisibility(visible, modifier = SwingModifier.testTag("animated-container")) {
        Label("Animated", modifier = SwingModifier.preferredSize(80, 40))
    }
}
