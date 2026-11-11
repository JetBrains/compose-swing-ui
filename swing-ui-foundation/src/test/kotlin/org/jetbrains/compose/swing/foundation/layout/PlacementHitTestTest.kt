// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.test.interaction.click
import org.jetbrains.compose.swing.test.interaction.performMouseInput
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Mouse events reach what a rotating or scaling placement layer paints under the pointer. Pointer tests post events
 * to the window where Swing's own search for the component under the pointer matters.
 */
class PlacementHitTestTest {
    @Test
    fun aPressInsideTheTurnedOutlineHitsAtTheUnturnedPointAndOneOnlyInsideTheUnturnedBoxMisses() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val onPressed = mockk<(MouseEvent) -> Unit>(relaxed = true)
            val pressed = slot<MouseEvent>()
            every { onPressed(capture(pressed)) } just Runs
            setWindowContent {
                Box {
                    Stage {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag("layered")
                                    .size(100, 100)
                                    .placementLayer { rotationZ = 45f }
                                    .mouseListener(onMousePressed = onPressed),
                        ) {}
                    }
                }
            }
            val canvas = window.onNodeWithTag("layered").fetch<JComponent>()
            val turn = turn(100, 100, rotation = 45f)

            // Near the unturned top-left corner, which the eighth turn puts above the unturned box. The canvas reads
            // its layout bounds' origin where its paint outsets ends.
            val unturned = Point(6 - canvas.paintBounds.x, 6 - canvas.paintBounds.y)
            stageInput.performMouseInput {
                click(
                    canvas.paintedAt(
                        stageInput.fetch(),
                        canvas,
                        unturned.x.toDouble(),
                        unturned.y.toDouble(),
                        turn,
                    ),
                )
            }
            verify(exactly = 1) { onPressed(any()) }
            val point = pressed.captured.point
            assertTrue(point.distance(unturned) <= 1.5, "the press lands at the unturned point $unturned: $point")

            // Near the unturned top-left corner, which the eighth turn leaves outside the diamond.
            stageInput.performMouseInput {
                click(canvas.unturnedAt(stageInput.fetch(), 4, 4))
            }
            verify(exactly = 1) { onPressed(any()) }
        }

    @Test
    fun aButtonInATurnedLayerFiresOnlyInsideItsTurnedOutline() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val onClick = mockk<() -> Unit>(relaxed = true)
            setWindowContent {
                Box {
                    Stage {
                        Box(
                            modifier =
                                SwingModifier.testTag("layered").size(120, 40).placementLayer { rotationZ = 90f },
                        ) {
                            Button("Go", onClick, SwingModifier.testTag("inner").fillMaxSize())
                        }
                    }
                }
            }
            val layered = window.onNodeWithTag("layered").fetch<JComponent>()
            val button = window.onNodeWithTag("inner").fetch<JComponent>()
            val turn = turn(120, 40, rotation = 90f)

            // Near the unturned left edge, which the quarter turn puts above the unturned box.
            stageInput.performMouseInput {
                click(layered.paintedAt(stageInput.fetch(), button, 10.0, 20.0, turn))
            }
            verify(exactly = 1) { onClick() }

            // Near the unturned left edge, which the quarter turn leaves outside the button.
            stageInput.performMouseInput {
                click(layered.unturnedAt(stageInput.fetch(), 10, 20))
            }
            verify(exactly = 1) { onClick() }
        }

    /** A turn inside a turn: the button fires where the two layers together paint it. */
    @Test
    fun aButtonTurnedInsideATurnedBoxFiresWhereBothTurnsPaintIt() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val onClick = mockk<() -> Unit>(relaxed = true)
            setWindowContent {
                Box {
                    Stage {
                        Box(
                            modifier =
                                SwingModifier.testTag("layered").size(120, 40).placementLayer { rotationZ = 90f },
                        ) {
                            Box(modifier = SwingModifier.fillMaxSize().placementLayer { rotationZ = 90f }) {
                                Button("Go", onClick, SwingModifier.testTag("inner").size(40, 40))
                            }
                        }
                    }
                }
            }
            val layered = window.onNodeWithTag("layered").fetch<JComponent>()
            val button = window.onNodeWithTag("inner").fetch<JComponent>()

            // The two quarter turns about one center make a half turn, which puts the button at the far end.
            stageInput.performMouseInput {
                click(layered.paintedAt(stageInput.fetch(), button, 20.0, 20.0, turn(120, 40, rotation = 180f)))
            }
            verify(exactly = 1) { onClick() }

            stageInput.performMouseInput {
                click(layered.unturnedAt(stageInput.fetch(), 20, 20))
            }
            verify(exactly = 1) { onClick() }
        }
}
