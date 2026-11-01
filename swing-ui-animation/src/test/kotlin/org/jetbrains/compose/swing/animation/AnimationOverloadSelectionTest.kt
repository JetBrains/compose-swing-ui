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
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

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
            repeat(FRAMES_INTO_TRANSITION) { mainClock.advanceTimeByFrame() }

            onNodeWithTag(RIGHT).assertProperty(true, "the row did not allocate animated width before its sibling") {
                x in 1 until BODY_WIDTH
            }
            onNodeWithTag(RIGHT).assertProperty(0, "the row changed the cross-axis position") { y }
            onNodeWithTag(PARENT).assertProperty(BODY_HEIGHT, "the row changed the cross-axis size") {
                preferredSize.height
            }
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
            repeat(FRAMES_INTO_TRANSITION) { mainClock.advanceTimeByFrame() }

            onNodeWithTag(BELOW).assertProperty(0, "the column changed the cross-axis position") { x }
            onNodeWithTag(BELOW).assertProperty(
                true,
                "the column did not allocate animated height before its sibling",
            ) {
                y in 1 until BODY_HEIGHT
            }
            onNodeWithTag(PARENT).assertProperty(BODY_WIDTH, "the column changed the cross-axis size") {
                preferredSize.width
            }
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
            repeat(FRAMES_INTO_TRANSITION) { mainClock.advanceTimeByFrame() }

            onNodeWithTag(RIGHT).assertProperty(true, "the box did not allocate animated width before its sibling") {
                x in 1 until BODY_WIDTH
            }
            onNodeWithTag(BELOW).assertProperty(true, "the box did not allocate animated height before its sibling") {
                y in 1 until BODY_HEIGHT
            }
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
            repeat(FRAMES_INTO_TRANSITION) { mainClock.advanceTimeByFrame() }

            onNodeWithTag(CONTAINER).assertProperty(true, "the panel child did not report its animated width") {
                preferredSize.width in 1 until BODY_WIDTH
            }
            onNodeWithTag(CONTAINER).assertProperty(true, "the panel child did not report its animated height") {
                preferredSize.height in 1 until BODY_HEIGHT
            }
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

            onNodeWithTag(ANIMATED_CONTENT).assertProperty(true) { isVisible }
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
            repeat(FRAMES_INTO_TRANSITION) { mainClock.advanceTimeByFrame() }

            onNodeWithTag(CONTAINER).assertProperty(
                true,
                "the extracted composable did not mount its top-level container",
            ) {
                isVisible
            }
            onNodeWithTag(CONTAINER).assertProperty(
                true,
                "the extracted top-level overload did not animate its width",
            ) {
                preferredSize.width in 1 until BODY_WIDTH
            }
            onNodeWithTag(CONTAINER).assertProperty(
                true,
                "the extracted top-level overload did not animate its height",
            ) {
                preferredSize.height in 1 until BODY_HEIGHT
            }
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
