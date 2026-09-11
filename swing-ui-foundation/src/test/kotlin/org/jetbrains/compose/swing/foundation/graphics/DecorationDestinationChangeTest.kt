package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.test.Test

/**
 * A decoration whose content paints on another destination than before paints what a fresh one does: an
 * antialiased [SwingModifier.clip] or a [SwingModifier.shadow] records into a device-aligned surface, which a
 * [SwingModifier.blur] wraps around it while active. Once the wrapping step stops recording it - the blur reaches
 * zero, or the content is captured for the first time after painting offscreen - the wrapped step records aligned
 * to the destination directly, which must show what recording there afresh would. A [FakeScreen] stands for the
 * destination, so the scenario needs no display.
 */
class DecorationDestinationChangeTest {
    @Test
    fun anAntialiasedClipInsideABlurDroppedToZeroPaintsOnAScaledScreenWhatAFreshOneDoes() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(4)
            val size = 60
            setContent {
                Row {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            decorated {
                                SwingModifier
                                    .testTag("subject")
                                    .preferredSize(size, size)
                                    .blur(radius)
                                    .clip(CircleShape, antialias = true)
                                    .fill(Color.RED)
                            },
                    )
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            decorated {
                                SwingModifier
                                    .testTag("reference")
                                    .preferredSize(size, size)
                                    .clip(CircleShape, antialias = true)
                                    .fill(Color.RED)
                            },
                    )
                }
            }
            repeat(2) { onNodeWithTag("subject").captureToImage() }

            radius = 0
            awaitIdle()

            assertScaledScreenPaintsMatch(size)
        }

    @Test
    fun aShadowInsideABlurDroppedToZeroPaintsOnAScaledScreenWhatAFreshOneDoes() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(4)
            val size = 60
            setContent {
                Row {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            decorated {
                                SwingModifier
                                    .testTag("subject")
                                    .preferredSize(size, size)
                                    .blur(radius)
                                    .shadow(6, Color.BLACK, 3, 3)
                                    .fill(Color.RED)
                            },
                    )
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            decorated {
                                SwingModifier
                                    .testTag("reference")
                                    .preferredSize(size, size)
                                    .shadow(6, Color.BLACK, 3, 3)
                                    .fill(Color.RED)
                            },
                    )
                }
            }
            repeat(2) { onNodeWithTag("subject").captureToImage() }

            radius = 0
            awaitIdle()

            assertScaledScreenPaintsMatch(size)
        }

    @Test
    fun anAntialiasedClipCapturedOffscreenThenPaintedOnAScaledScreenPaintsWhatAFreshOneDoes() =
        runComposeSwingTest {
            val size = 60
            setContent {
                Row {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            decorated {
                                SwingModifier
                                    .testTag("subject")
                                    .preferredSize(size, size)
                                    .clip(CircleShape, antialias = true)
                                    .fill(Color.RED)
                            },
                    )
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            decorated {
                                SwingModifier
                                    .testTag("reference")
                                    .preferredSize(size, size)
                                    .clip(CircleShape, antialias = true)
                                    .fill(Color.RED)
                            },
                    )
                }
            }
            // The subject is captured offscreen first, as a window opened before it is shown would be; the
            // reference paints directly on the screen from the start.
            repeat(2) { onNodeWithTag("subject").captureToImage() }

            assertScaledScreenPaintsMatch(size)
        }

    /**
     * A destination change from removing a step, not from a property reaching a value that stops it recording:
     * a polygon-outline surface with a shadow and an antialiased clip, nested inside a blur, paints what a
     * surface that never had the blur declared does, once the blur leaves the steps and the frames before that
     * were painted on the scaled screen itself, as the reported sample painted them.
     */
    @Test
    fun aPolygonWithShadowAndClipInABlurRemovedFromItsStepsPaintsOnAScaledScreenWhatAFreshOneDoes() =
        runComposeSwingTest {
            var blurEnabled by mutableStateOf(true)
            val size = 60
            setContent {
                Row {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            SwingModifier
                                .testTag("subject")
                                .preferredSize(size, size)
                                .let { if (blurEnabled) it.blur(8) else it }
                                .shadow(6, Color.BLACK, 3, 3)
                                .clip(Triangle, antialias = true)
                                .fill(Color.RED),
                    )
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            SwingModifier
                                .testTag("reference")
                                .preferredSize(size, size)
                                .shadow(6, Color.BLACK, 3, 3)
                                .clip(Triangle, antialias = true)
                                .fill(Color.RED),
                    )
                }
            }
            val subject = onNodeWithTag("subject").fetch<JComponent>()
            repeat(2) { paintOnFakeScreen(subject) }

            blurEnabled = false
            awaitIdle()

            assertScaledScreenPaintsMatch(size)
        }

    /** A fresh 2x [FakeScreen] surface of [component]'s own size, painted once and disposed. */
    private fun paintOnFakeScreen(component: JComponent): BufferedImage {
        val surface = FakeScreen(2.0).surface(component.width, component.height)
        try {
            component.paint(surface.graphics)
        } finally {
            surface.graphics.dispose()
        }
        return surface.pixels
    }

    /** Paints the "subject" and "reference" tagged components onto their own surface of a 2x [FakeScreen], and
     * fails unless they are pixel-perfect equal. */
    private fun ComposeSwingTest.assertScaledScreenPaintsMatch(size: Int) {
        val screen = FakeScreen(2.0)
        val subject = onNodeWithTag("subject").fetch<JComponent>()
        val reference = onNodeWithTag("reference").fetch<JComponent>()
        val subjectSurface = screen.surface(size, size)
        try {
            subject.paint(subjectSurface.graphics)
        } finally {
            subjectSurface.graphics.dispose()
        }
        val referenceSurface = screen.surface(size, size)
        try {
            reference.paint(referenceSurface.graphics)
        } finally {
            referenceSurface.graphics.dispose()
        }
        assertImagesPixelPerfect(referenceSurface.pixels, subjectSurface.pixels)
    }

    private companion object {
        /** A triangular outline, neither a rectangle nor a rounded one. */
        val Triangle =
            Shape { width, height ->
                Path2D.Float().apply {
                    moveTo(width / 2f, 0f)
                    lineTo(width.toFloat(), height.toFloat())
                    lineTo(0f, height.toFloat())
                    closePath()
                }
            }
    }
}
