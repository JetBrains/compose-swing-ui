// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.performMouseInput
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertTrue

/** The pointer entering and leaving what a rotating or scaling placement layer paints is reported once per crossing. */
class PlacementHitTestCrossingTest {
    /**
     * A drag from a turned child onto its container and onto a plain sibling, and the pointer coming back, reports
     * one enter and one exit to the container per crossing, and an exit after each enter to the sibling.
     */
    @Test
    fun aDragOffATurnedChildReportsEachCrossingOnce() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val container = mockk<(MouseEvent) -> Unit>(relaxed = true)
            val sibling = mockk<(MouseEvent) -> Unit>(relaxed = true)
            val siblingEvents = mutableListOf<MouseEvent>()
            every { sibling(capture(siblingEvents)) } just Runs
            setUpCrossingTargets(container, sibling)
            // A turn about the center leaves the center where it is; the container's last third holds no child.
            val overTurned =
                window.onNodeWithTag("layered").fetch<JComponent>().unturnedAt(stageInput.fetch(), 20, 20)
            val overSibling = window.onNodeWithTag("other").fetch<JComponent>().unturnedAt(stageInput.fetch(), 20, 20)
            val overContainer =
                window.onNodeWithTag("outer").fetch<JComponent>().unturnedAt(stageInput.fetch(), 100, 20)

            stageInput.performMouseInput { moveTo(overContainer) }
            stageInput.performMouseInput { moveTo(overTurned) }
            stageInput.performMouseInput {
                press(MouseEvent.BUTTON1)
            }
            stageInput.performMouseInput { moveTo(overContainer) }
            stageInput.performMouseInput { moveTo(overSibling) }
            stageInput.performMouseInput { release(MouseEvent.BUTTON1) }
            stageInput.performMouseInput { moveTo(overContainer) }
            stageInput.performMouseInput { moveTo(overTurned) }

            verifySequence {
                repeat(3) {
                    container(match { event -> event.id == MouseEvent.MOUSE_ENTERED })
                    container(match { event -> event.id == MouseEvent.MOUSE_EXITED })
                }
            }
            // AWT reads a drag handed to the press target at the target's own point, and may report the sibling
            // crossed again meanwhile.
            val heard = siblingEvents.map { it.id }
            val crossings = listOf(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_EXITED)
            val alternating =
                heard.isNotEmpty() && heard.size % 2 == 0 && heard.indices.all { heard[it] == crossings[it % 2] }
            assertTrue(alternating, "the sibling hears an exit after each enter, but heard $heard")
        }

    private suspend fun ComposeSwingTest.setUpCrossingTargets(
        container: (MouseEvent) -> Unit,
        sibling: (MouseEvent) -> Unit,
    ) {
        setWindowContent {
            Box {
                Stage {
                    Row(
                        modifier =
                            SwingModifier
                                .testTag("outer")
                                .size(120, 40)
                                .mouseListener(
                                    onMouseEntered = container,
                                    onMouseExited = container,
                                ),
                    ) {
                        Canvas(
                            modifier = SwingModifier.testTag("layered").size(40, 40).placementLayer { rotationZ = 45f },
                        ) {}
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag("other")
                                    .size(40, 40)
                                    .mouseListener(
                                        onMouseEntered = sibling,
                                        onMouseExited = sibling,
                                    ),
                        ) {}
                    }
                }
            }
        }
    }

    /** A child turned, straightened and turned again hears the pointer enter it again. */
    @Test
    fun aChildTurnedAgainHearsThePointerEnterAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val onEntered = mockk<(MouseEvent) -> Unit>(relaxed = true)
            var rotation by mutableFloatStateOf(45f)
            setWindowContent {
                Box {
                    Stage {
                        Row {
                            Canvas(
                                modifier =
                                    SwingModifier
                                        .testTag("layered")
                                        .size(40, 40)
                                        .placementLayer { rotationZ = rotation }
                                        .mouseListener(onMouseEntered = onEntered),
                            ) {}
                            Canvas(modifier = SwingModifier.testTag("other").size(40, 40)) {}
                        }
                    }
                }
            }
            // A turn about the center leaves the center where it is.
            val overTurned =
                window.onNodeWithTag("layered").fetch<JComponent>().unturnedAt(stageInput.fetch(), 20, 20)
            val overOther = window.onNodeWithTag("other").fetch<JComponent>().unturnedAt(stageInput.fetch(), 20, 20)

            stageInput.performMouseInput { moveTo(overTurned) }
            verify(exactly = 1) { onEntered(any()) }

            rotation = 0f
            awaitIdle()
            stageInput.performMouseInput { moveTo(overOther) }
            rotation = 45f
            awaitIdle()
            stageInput.performMouseInput { moveTo(overTurned) }

            verify(exactly = 2) { onEntered(any()) }
        }

    /** A turned child hears the pointer leave it for a sibling, once. */
    @Test
    fun aTurnedChildHearsThePointerExitWhenItLeavesForASibling() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val onExited = mockk<(MouseEvent) -> Unit>(relaxed = true)
            setWindowContent {
                Box {
                    Stage {
                        Row {
                            Canvas(
                                modifier =
                                    SwingModifier
                                        .testTag("layered")
                                        .size(40, 40)
                                        .placementLayer { rotationZ = 45f }
                                        .mouseListener(onMouseExited = onExited),
                            ) {}
                            Canvas(modifier = SwingModifier.testTag("other").size(40, 40)) {}
                        }
                    }
                }
            }
            // A turn about the center leaves the center where it is.
            val overTurned =
                window.onNodeWithTag("layered").fetch<JComponent>().unturnedAt(stageInput.fetch(), 20, 20)
            val overOther = window.onNodeWithTag("other").fetch<JComponent>().unturnedAt(stageInput.fetch(), 20, 20)

            stageInput.performMouseInput { moveTo(overTurned) }
            verify(exactly = 0) { onExited(any()) }

            stageInput.performMouseInput { moveTo(overOther) }

            verify(exactly = 1) { onExited(match { event -> event.id == MouseEvent.MOUSE_EXITED }) }
        }
}
