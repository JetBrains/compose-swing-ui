// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import io.mockk.mockk
import io.mockk.verify
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
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.test.Test

/** Mouse events miss what a placement layer that only clips cuts away from its child. */
class PlacementClipHitTestTest {
    @Test
    fun aClickInsideTheLayoutBoundsButOutsideAClipOnlyLayerMissesTheChild() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val onPressed = mockk<(MouseEvent) -> Unit>(relaxed = true)
            setWindowContent {
                Box {
                    Stage {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag("layered")
                                    .placementLayer { clip = true }
                                    .offset(30, 30)
                                    .size(100, 100)
                                    .mouseListener(onMousePressed = onPressed),
                        ) {}
                    }
                }
            }
            val canvas = window.onNodeWithTag("layered").fetch<JComponent>()

            // Inside the layout bounds, past the box the layer clips to: the offset's, which stands 30 up and left.
            stageInput.performMouseInput {
                click(canvas.unturnedAt(stageInput.fetch(), 85, 85))
            }
            verify(exactly = 0) { onPressed(any()) }

            stageInput.performMouseInput {
                click(canvas.unturnedAt(stageInput.fetch(), 20, 20))
            }
            verify(exactly = 1) { onPressed(any()) }
        }
}
