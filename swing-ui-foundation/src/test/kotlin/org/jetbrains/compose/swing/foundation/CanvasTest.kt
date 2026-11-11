// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.KeyReadingElement
import org.jetbrains.compose.swing.assertPixelColor
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.layout.CONTAINER_TAG
import org.jetbrains.compose.swing.foundation.layout.WINDOW_TITLE
import org.jetbrains.compose.swing.foundation.layout.dirtyRegion
import org.jetbrains.compose.swing.foundation.layout.isValidUpToTheValidateRoot
import org.jetbrains.compose.swing.foundation.layout.setWindowContent
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for [Canvas]: repaint is snapshot-observed.
 *
 * Painting is driven by capturing a surface to an image, so every recorded value reflects exactly one
 * triggered [Canvas.onDraw] invocation. The harness runs the whole test body, painting, and
 * snapshot-change callbacks on the single event dispatch thread.
 */
class CanvasTest {
    @Test
    fun canvasAppliesDefaultRenderingHints() =
        runComposeSwingTest {
            var observedAntialiasing: Any? = null
            var observedStrokeControl: Any? = null
            setContent {
                Canvas(modifier = SwingModifier.testTag("canvas").styleable { preferredSize(64, 48) }) {
                    observedAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
                    observedStrokeControl = graphics.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL)
                }
            }
            onNodeWithTag("canvas").captureToImage()
            assertEquals(
                RenderingHints.VALUE_ANTIALIAS_ON,
                observedAntialiasing,
                "Canvas defaults to antialiased drawing.",
            )
            assertEquals(
                RenderingHints.VALUE_STROKE_PURE,
                observedStrokeControl,
                "Canvas defaults to pure stroke control.",
            )
        }

    @Test
    fun canvasAppliesCustomRenderingHints() =
        runComposeSwingTest {
            var observedTextAntialiasing: Any? = null
            val customHints = mapOf(RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            setContent {
                Canvas(
                    modifier = SwingModifier.testTag("canvas").styleable { preferredSize(64, 48) },
                    renderingHints = customHints,
                ) {
                    observedTextAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
                }
            }
            onNodeWithTag("canvas").captureToImage()
            assertEquals(
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                observedTextAntialiasing,
                "Canvas passes through the custom rendering hints it was given.",
            )
        }

    @Test
    fun canvasWithoutRenderingHintsKeepsTheGraphicsOwn() =
        runComposeSwingTest {
            var observedAntialiasing: Any? = null
            setContent {
                Canvas(
                    modifier = SwingModifier.testTag("canvas").styleable { preferredSize(64, 48) },
                    renderingHints = null,
                ) {
                    observedAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
                }
            }
            onNodeWithTag("canvas").captureToImage()
            assertEquals(
                RenderingHints.VALUE_ANTIALIAS_OFF,
                observedAntialiasing,
                "no rendering hints must leave the hints of the graphics the surface paints into",
            )
        }

    @Test
    fun canvasDeclaredOpaqueStillLetsWhatIsBehindShowThrough() =
        runComposeSwingTest {
            setContent {
                Canvas(
                    modifier =
                        SwingModifier.testTag("canvas").styleable {
                            preferredSize(64, 48)
                            opaque(true)
                        },
                ) {}
            }
            onNodeWithTag("canvas").assertProperty(
                false,
                "the surface covers only what onDraw paints, so it must not claim to cover its whole area",
            ) { isOpaque }
        }

    @Test
    fun updatingRenderingHintsRepaintsCanvas() =
        runComposeSwingTest {
            var hints by mutableStateOf<Map<RenderingHints.Key, Any>?>(DefaultCanvasRenderingHints)
            var drawCount = 0
            var observedTextAntialiasing: Any? = null
            setContent {
                Canvas(
                    modifier = SwingModifier.testTag("canvas").styleable { preferredSize(64, 48) },
                    renderingHints = hints,
                ) {
                    drawCount++
                    observedTextAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
                }
            }
            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            canvas.captureToImage()
            assertEquals(1, drawCount, "The first capture draws once.")

            withRecordedRepaints { repaints ->
                hints = mapOf(RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                awaitIdle()
                assertTrue(
                    repaints.repaintsOf(canvas) > 0,
                    "new rendering hints must request a repaint of the surface",
                )
                canvas.captureToImage()
                assertEquals(2, drawCount, "The repaint after changed hints draws again.")
                assertEquals(
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                    observedTextAntialiasing,
                    "The repaint draws with the new hints.",
                )
            }
        }

    @Test
    fun recompositionWithNewInputRedrawsWithNewValue() =
        runComposeSwingTest {
            var value by mutableIntStateOf(7)
            var lastDrawn = Int.MIN_VALUE
            var drawCount = 0
            setContent {
                // `value` is read HERE, in the composition, and captured into onDraw. Changing it
                // recomposes Canvas -> new onDraw lambda -> repaint() -> onDraw re-runs.
                val captured = value
                Canvas(modifier = SwingModifier.testTag("canvas").styleable { preferredSize(64, 48) }) {
                    lastDrawn = captured
                    drawCount++
                }
            }

            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            canvas.captureToImage()
            assertEquals(7, lastDrawn, "Initial paint should draw the initial value.")
            assertEquals(1, drawCount, "the initial paint should draw exactly once")

            value = 42
            awaitIdle()
            canvas.captureToImage()

            assertEquals(42, lastDrawn, "After recomposition the new value must be drawn.")
            assertEquals(2, drawCount, "A new onDraw should have produced a second draw.")
        }

    @Test
    fun aNewOnDrawRequestsARepaintAndDrawsTheNewPixels() =
        runComposeSwingTest {
            var color by mutableStateOf(Color.RED)
            setContent {
                // Read in the composition, so a change hands the surface a new onDraw and nothing else.
                val captured = color
                Canvas(
                    modifier = SwingModifier.testTag("canvas").styleable { preferredSize(64, 48) },
                ) { drawRect(captured) }
            }
            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            onNodeWithTag(
                "canvas",
            ).captureToImage().assertPixelColor(
                Color.RED,
                32,
                24,
                "the surface draws with the color read at composition",
            )

            withRecordedRepaints { repaints ->
                color = Color.BLUE
                awaitIdle()

                assertTrue(
                    repaints.repaintsOf(canvas) > 0,
                    "a new onDraw must request a repaint before anything paints",
                )
                onNodeWithTag(
                    "canvas",
                ).captureToImage().assertPixelColor(Color.BLUE, 32, 24, "the repaint draws with the new onDraw's color")
            }
        }

    /** A cost probe: counts key reads to pin that an in-place write skips the modifier diff. */
    @Test
    fun aNewOnDrawIsWrittenWithoutReadingAModifierKey() =
        runComposeSwingTest {
            var color by mutableStateOf(Color.RED)
            val keyed = KeyReadingElement()
            setContent {
                val captured = color
                Canvas(modifier = SwingModifier.testTag("canvas").then(keyed).styleable { preferredSize(64, 48) }) {
                    drawRect(captured)
                }
            }
            onNodeWithTag("canvas").captureToImage()
            val keyReads = keyed.keyReads

            color = Color.BLUE
            awaitIdle()

            onNodeWithTag("canvas").captureToImage().assertPixelColor(Color.BLUE, 32, 24)
            assertEquals(keyReads, keyed.keyReads, "a new onDraw must be written in place, leaving the rest undiffed")
        }

    @Test
    fun stateReadOnlyInsideOnDrawIsObservedAndRequestsRepaint() =
        runComposeSwingTest {
            // `value` is NEVER read in the composition: only inside onDraw. So no recomposition can
            // happen when it changes - the only thing that can repaint the surface is the snapshot
            // observer wrapping onDraw. The lambda itself is stable (it captures the State delegate,
            // not a value), so Canvas() is skippable and never hands the surface a fresh onDraw.
            val value = mutableIntStateOf(7)
            var lastDrawn = Int.MIN_VALUE
            setContent {
                Canvas(modifier = SwingModifier.testTag("canvas").styleable { preferredSize(64, 48) }) {
                    lastDrawn = value.intValue
                }
            }

            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            withRecordedRepaints { repaints ->

                // First paint starts the observer and tracks the read of `value` inside onDraw.
                canvas.captureToImage()
                assertEquals(7, lastDrawn, "Initial paint should draw the initial value.")
                repaints.forget()

                // Mutate the observed state on the EDT and pump apply notifications (awaitIdle does this).
                // No recomposition occurs; the observer must react by requesting a repaint of the surface.
                value.intValue = 42
                awaitIdle()

                assertTrue(
                    repaints.repaintsOf(canvas) > 0,
                    "A state read only inside onDraw changed: the snapshot observer must have requested a " +
                        "repaint of the surface, with no recomposition and no manual paint. Observed " +
                        "${repaints.repaintsOf(canvas)} repaint requests.",
                )

                // And when that requested repaint is serviced, onDraw re-runs and reads the NEW value -
                // proving the observation drives a real redraw, not a stale one.
                canvas.captureToImage()
                assertEquals(42, lastDrawn, "The serviced repaint must redraw with the new value.")
            }
        }

    /** A read made only while drawing repaints the surface alone: nothing is laid out again. */
    @Test
    fun aStateReadInOnDrawRepaintsWithoutLayingOutAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var color by mutableStateOf(Color.RED)
            setWindowContent {
                Canvas(
                    modifier = SwingModifier.testTag(CONTAINER_TAG).styleable { preferredSize(64, 48) },
                ) { drawRect(color) }
            }
            val canvas = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
            val node = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG)
            node.captureToImage().assertPixelColor(Color.RED, 32, 24, "the surface draws with the initial color")
            assertTrue(canvas.isValidUpToTheValidateRoot(), "the realized surface must start valid")

            color = Color.BLUE
            Snapshot.sendApplyNotifications()

            assertEquals(
                Rectangle(0, 0, canvas.width, canvas.height),
                canvas.dirtyRegion(),
                "a changed draw read must repaint the surface",
            )
            assertTrue(canvas.isValidUpToTheValidateRoot(), "a draw read must not invalidate anything")

            awaitIdle()
            node.captureToImage().assertPixelColor(Color.BLUE, 32, 24, "the surface draws with the changed color")
        }

    @Test
    fun aDrawScopeKeptPastItsDrawRefusesToDraw() =
        runComposeSwingTest {
            var kept: DrawScope? = null
            setContent {
                Canvas(modifier = SwingModifier.testTag("canvas").styleable { preferredSize(64, 48) }) { kept = this }
            }
            onNodeWithTag("canvas").captureToImage()

            val scope = assertNotNull(kept, "the canvas drew")
            val refusal = assertFailsWith<IllegalStateException> { scope.drawRect(Color.RED) }
            assertTrue(
                "outside the draw" in refusal.message.orEmpty(),
                "the refusal says the scope outlived its draw: ${refusal.message}",
            )
        }
}
