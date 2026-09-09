package org.jetbrains.compose.swing.foundation

import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.layout.CONTAINER_TAG
import org.jetbrains.compose.swing.foundation.layout.WINDOW_TITLE
import org.jetbrains.compose.swing.foundation.layout.dirtyRegion
import org.jetbrains.compose.swing.foundation.layout.isValidUpToTheValidateRoot
import org.jetbrains.compose.swing.foundation.layout.setWindowContent
import org.jetbrains.compose.swing.foundation.layout.windowContainer
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
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
                Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) {
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
                    modifier = SwingModifier.testTag("canvas").preferredSize(64, 48),
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
                Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48), renderingHints = null) {
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
                Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48).opaque(true)) {}
            }
            assertFalse(
                onNodeWithTag("canvas").fetch<JComponent>().isOpaque,
                "the surface covers only what onDraw paints, so it must not claim to cover its whole area",
            )
        }

    @Test
    fun updatingRenderingHintsRepaintsCanvas() =
        runComposeSwingTest {
            var hints by mutableStateOf<Map<RenderingHints.Key, Any>?>(DefaultCanvasRenderingHints)
            var drawCount = 0
            var observedTextAntialiasing: Any? = null
            setContent {
                Canvas(
                    modifier = SwingModifier.testTag("canvas").preferredSize(64, 48),
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
    fun drawsOnceInitiallyWithInitialValue() =
        runComposeSwingTest {
            val drawn = mutableListOf<Int>()
            setContent {
                Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) {
                    drawn += 1
                }
            }

            onNodeWithTag("canvas").captureToImage()

            assertEquals(listOf(1), drawn, "Canvas should draw exactly once after the initial paint.")
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
                Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) {
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
                Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) { drawRect(captured) }
            }
            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            assertEquals(
                Color.RED.rgb,
                onNodeWithTag("canvas").captureToImage().getRGB(32, 24),
                "the surface draws with the color read at composition",
            )

            withRecordedRepaints { repaints ->
                color = Color.BLUE
                awaitIdle()

                assertTrue(
                    repaints.repaintsOf(canvas) > 0,
                    "a new onDraw must request a repaint before anything paints",
                )
                assertEquals(
                    Color.BLUE.rgb,
                    onNodeWithTag("canvas").captureToImage().getRGB(32, 24),
                    "the repaint draws with the new onDraw's color",
                )
            }
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
                Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) {
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
                Canvas(modifier = SwingModifier.testTag(CONTAINER_TAG).preferredSize(64, 48)) { drawRect(color) }
            }
            val canvas = windowContainer()
            val node = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG)
            assertEquals(
                Color.RED.rgb,
                node.captureToImage().getRGB(32, 24),
                "the surface draws with the initial color",
            )
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
            assertEquals(
                Color.BLUE.rgb,
                node.captureToImage().getRGB(32, 24),
                "the surface draws with the changed color",
            )
        }

    @Test
    fun aCanvasInsertedIntoAShownPanelAsksForOneRepaint() =
        runComposeSwingTest {
            var present by mutableStateOf(false)
            setContent {
                Panel(PanelLayout.Box()) {
                    Label(text = "anchor")
                    if (present) {
                        Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) {}
                    }
                }
            }

            withRecordedRepaints { repaints ->
                present = true
                awaitIdle()

                val canvas = onNodeWithTag("canvas").fetch<JComponent>()
                assertEquals(
                    1,
                    repaints.repaintsOf(canvas),
                    "Installing the drawing is one repaint of the surface, not one per step of the install.",
                )
            }
        }

    @Test
    fun canvasInsertedDuringRecompositionIsObservedAndRedraws() =
        runComposeSwingTest {
            // A Canvas inserted during a recomposition (rather than the initial composition) must still
            // adopt the composition owner's snapshot observer. The observer is published on each node on the
            // applier's top-down insert pass, which precedes the node's update changes that copy it onto the
            // surface; publishing it on the bottom-up pass instead would copy a not-yet-set observer for a
            // recomposition insert, leaving the surface unobserved. `value` is read ONLY inside onDraw, so
            // the only thing that can repaint the surface is the observer - an unwired one requests no
            // repaint.
            val value = mutableIntStateOf(7)
            var present by mutableStateOf(false)
            var lastDrawn = Int.MIN_VALUE
            setContent {
                Panel(PanelLayout.Box()) {
                    Label(text = "anchor")
                    if (present) {
                        Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) {
                            lastDrawn = value.intValue
                        }
                    }
                }
            }

            // Insert the Canvas via a recomposition (it was absent from the initial composition).
            present = true
            awaitIdle()

            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            withRecordedRepaints { repaints ->

                canvas.captureToImage()
                assertEquals(7, lastDrawn, "Initial paint of the recomposition-inserted surface should draw the value.")
                repaints.forget()

                value.intValue = 42
                awaitIdle()

                assertTrue(
                    repaints.repaintsOf(canvas) > 0,
                    "A state read only inside the onDraw of a Canvas inserted during recomposition changed: the " +
                        "owner observer must have been wired to it and requested a repaint. Observed " +
                        "${repaints.repaintsOf(canvas)} repaint requests.",
                )

                canvas.captureToImage()
                assertEquals(42, lastDrawn, "The serviced repaint must redraw the surface with the new value.")
            }
        }

    @Test
    fun canvasFirstActivatedViaReusableContentHostIsObserved() =
        runComposeSwingTest {
            // A Canvas whose first appearance is a ReusableContentHost activation (active false -> true) is
            // first inserted during a recomposition, like any conditionally-introduced surface. Its observer
            // must be wired so a state read only inside onDraw repaints it.
            val value = mutableIntStateOf(7)
            var active by mutableStateOf(false)
            var lastDrawn = Int.MIN_VALUE
            setContent {
                Panel(PanelLayout.Box()) {
                    Label(text = "anchor")
                    ReusableContentHost(active = active) {
                        Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) {
                            lastDrawn = value.intValue
                        }
                    }
                }
            }

            active = true
            awaitIdle()

            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            withRecordedRepaints { repaints ->

                canvas.captureToImage()
                assertEquals(7, lastDrawn, "Initial paint after activation should draw the value.")
                repaints.forget()

                value.intValue = 42
                awaitIdle()

                assertTrue(
                    repaints.repaintsOf(canvas) > 0,
                    "A Canvas first activated via ReusableContentHost must be observed: a state read only inside " +
                        "its onDraw must request a repaint. Observed ${repaints.repaintsOf(canvas)} repaint requests.",
                )
            }
        }

    @Test
    fun removingCanvasDetachesItAndStopsObservingItsReads() =
        runComposeSwingTest {
            // `value` is read ONLY inside onDraw, so the observer is the only thing that can repaint the
            // surface. Removing the canvas releases its node, which drops its tracked reads from the shared
            // observer: a later change to the state it used to read must reach it no more.
            val value = mutableIntStateOf(7)
            var present by mutableStateOf(true)
            var drawCount = 0
            setContent {
                Panel(PanelLayout.Box()) {
                    Label(text = "anchor")
                    if (present) {
                        Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) {
                            value.intValue
                            drawCount++
                        }
                    }
                }
            }

            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            withRecordedRepaints { repaints ->
                canvas.captureToImage()
                assertEquals(1, drawCount, "the canvas should draw once before removal")

                present = false
                awaitIdle()

                onNodeWithTag("canvas").assertDoesNotExist()
                assertTrue(canvas.parent == null, "Removed canvas should be detached from the tree.")

                repaints.forget()
                value.intValue = 42
                awaitIdle()

                assertEquals(
                    0,
                    repaints.repaintsOf(canvas),
                    "A change to state the removed canvas used to read must request no repaint of it: its node " +
                        "released, so the shared observer no longer tracks its reads.",
                )
                assertEquals(1, drawCount, "No further onDraw should occur after removal.")
            }
        }

    @Test
    fun parkedCanvasStopsObservingItsReads() =
        runComposeSwingTest {
            // `value` is read ONLY inside onDraw, so the observer is the only thing that can repaint the
            // surface. Parking the canvas deactivates its node and detaches its component from the tree,
            // which drops its tracked reads: while parked it is driven by nothing, exactly like a removed
            // canvas.
            val value = mutableIntStateOf(7)
            var active by mutableStateOf(true)
            var drawCount = 0
            var lastDrawn = Int.MIN_VALUE
            setContent {
                Panel(PanelLayout.Box()) {
                    Label(text = "anchor")
                    ReusableContentHost(active = active) {
                        Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) {
                            lastDrawn = value.intValue
                            drawCount++
                        }
                    }
                }
            }

            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            withRecordedRepaints { repaints ->

                // Paint once to track the read, then confirm the surface reacts while it is active - so the
                // silence asserted below is the parking, not an observer that never worked.
                canvas.captureToImage()
                assertEquals(7, lastDrawn, "The first paint should draw the initial value.")
                repaints.forget()
                value.intValue = 42
                awaitIdle()
                assertTrue(
                    repaints.repaintsOf(canvas) > 0,
                    "An active canvas must repaint on a change to state its onDraw read.",
                )

                active = false
                awaitIdle()

                onNodeWithTag("canvas").assertDoesNotExist()
                assertTrue(canvas.parent == null, "A parked canvas is detached from the tree.")

                val drawsBeforeParkedPaint = drawCount
                repaints.forget()
                value.intValue = 43
                awaitIdle()

                assertEquals(
                    0,
                    repaints.repaintsOf(canvas),
                    "A change to state a parked canvas last read must request no repaint of it: its node was " +
                        "deactivated, so the shared observer no longer tracks its reads.",
                )
                assertEquals(drawsBeforeParkedPaint, drawCount, "No further onDraw should occur while parked.")
            }
        }

    @Test
    fun parkingDetachesTheCanvasAndReactivatingBuildsAFreshOneThatPaints() =
        runComposeSwingTest {
            // The control for the silence asserted while parked: the same paint pass over the parent that
            // draws an active canvas must draw nothing while the canvas is detached, and the fresh canvas
            // reactivation builds is drawn by that same paint pass once the composition drives it again.
            var active by mutableStateOf(true)
            var drawCount = 0
            setContent {
                Panel(PanelLayout.Box()) {
                    Label(text = "anchor")
                    ReusableContentHost(active = active) {
                        Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) {
                            drawCount++
                        }
                    }
                }
            }

            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            val parent = assertIs<JComponent>(canvas.parent, "The canvas is held by the panel it was composed in.")

            parent.captureToImage()
            assertEquals(1, drawCount, "A paint pass over the parent must draw the active canvas.")

            active = false
            awaitIdle()
            assertTrue(canvas.parent == null, "A parked canvas is detached from the tree.")

            parent.captureToImage()
            assertEquals(1, drawCount, "The same paint pass must not draw the detached canvas while it is parked.")

            active = true
            awaitIdle()
            val reactivated = onNodeWithTag("canvas").fetch<JComponent>()
            assertNotSame(canvas, reactivated, "reactivation builds a fresh canvas rather than reusing the parked one")

            parent.captureToImage()
            assertEquals(2, drawCount, "The fresh canvas is drawn by the paint pass over its parent.")
        }

    @Test
    fun reactivatedCanvasObservesItsReadsAgain() =
        runComposeSwingTest {
            // Reactivation builds a fresh canvas from the node's factory: that fresh canvas's own paint pass
            // registers its reads with the shared observer, wholly apart from the parked canvas's now-dropped
            // reads.
            val value = mutableIntStateOf(7)
            var active by mutableStateOf(true)
            var lastDrawn = Int.MIN_VALUE
            setContent {
                Panel(PanelLayout.Box()) {
                    Label(text = "anchor")
                    ReusableContentHost(active = active) {
                        Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) {
                            lastDrawn = value.intValue
                        }
                    }
                }
            }

            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            canvas.captureToImage()

            active = false
            awaitIdle()
            active = true
            awaitIdle()

            val reactivated = onNodeWithTag("canvas").fetch<JComponent>()
            assertNotSame(canvas, reactivated, "reactivation builds a fresh canvas rather than reusing the parked one")

            withRecordedRepaints { repaints ->
                reactivated.captureToImage()
                assertEquals(7, lastDrawn, "The fresh canvas should draw the current value.")
                repaints.forget()

                value.intValue = 42
                awaitIdle()

                assertTrue(
                    repaints.repaintsOf(reactivated) > 0,
                    "The fresh canvas must observe its reads: a change to state read only inside its onDraw must " +
                        "request a repaint. Observed ${repaints.repaintsOf(reactivated)} repaint requests.",
                )

                reactivated.captureToImage()
                assertEquals(42, lastDrawn, "The serviced repaint must redraw the fresh canvas with the new value.")
            }
        }

    @Test
    fun aKeyChangeStopsRepaintingTheDiscardedCanvasForTheReadsOfTheReplacement() =
        runComposeSwingTest {
            // A key change discards the old node and builds a fresh one for the new content: the fresh
            // canvas must be driven by what the new content reads, and the discarded one must be driven by
            // nothing, however each state is read ONLY inside its own onDraw.
            val readByOldContent = mutableIntStateOf(1)
            val readByNewContent = mutableIntStateOf(1)
            var reuseKey by mutableStateOf(0)
            setContent {
                Panel(PanelLayout.Box()) {
                    Label(text = "anchor")
                    ReusableContent(reuseKey) {
                        val observed = if (reuseKey == 0) readByOldContent else readByNewContent
                        Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) {
                            observed.intValue
                        }
                    }
                }
            }

            val original = onNodeWithTag("canvas").fetch<JComponent>()
            withRecordedRepaints { repaints ->
                original.captureToImage()

                reuseKey = 1
                awaitIdle()

                val replacement = onNodeWithTag("canvas").fetch<JComponent>()
                assertNotSame(
                    original,
                    replacement,
                    "a key change builds a fresh canvas rather than reusing the old one",
                )
                assertTrue(original.parent == null, "the discarded canvas is detached from the tree")

                repaints.forget()
                readByOldContent.intValue = 2
                awaitIdle()

                assertEquals(
                    0,
                    repaints.repaintsOf(original),
                    "A change to state only the discarded content read must request no repaint of it: its node " +
                        "was released, so the shared observer no longer tracks its reads.",
                )

                replacement.captureToImage()
                repaints.forget()
                readByNewContent.intValue = 2
                awaitIdle()

                assertTrue(
                    repaints.repaintsOf(replacement) > 0,
                    "The fresh canvas must observe what the new content reads. Observed " +
                        "${repaints.repaintsOf(replacement)} repaint requests.",
                )
            }
        }

    @Test
    fun observerSurvivesOneCanvasDetachAndStillRepaintsTheOther() =
        runComposeSwingTest {
            // Two canvases share the composition owner's single observer and both read the SAME state only
            // inside onDraw (never in the composition, so a change can repaint only via the observer).
            // Removing the first canvas must NOT tear down the shared observer: a later change to the state
            // must still request a repaint of the surviving canvas.
            val value = mutableIntStateOf(7)
            var firstPresent by mutableStateOf(true)
            setContent {
                Panel(PanelLayout.Box()) {
                    if (firstPresent) {
                        Canvas(modifier = SwingModifier.testTag("first").preferredSize(64, 48)) {
                            value.intValue
                        }
                    }
                    Canvas(modifier = SwingModifier.testTag("second").preferredSize(64, 48)) {
                        value.intValue
                    }
                }
            }

            val second = onNodeWithTag("second").fetch<JComponent>()
            withRecordedRepaints { repaints ->

                // Paint both so the observer tracks each one's read of `value`.
                onNodeWithTag("first").captureToImage()
                second.captureToImage()
                repaints.forget()

                // Detach the first canvas. Its node releases and forgets its own scope; the shared observer
                // keeps running for the second canvas.
                firstPresent = false
                awaitIdle()
                onNodeWithTag("first").assertDoesNotExist()

                // A change to the still-observed state must repaint the surviving canvas - proving the shared
                // observer was not disposed by the first canvas's detach.
                value.intValue = 42
                awaitIdle()

                assertTrue(
                    repaints.repaintsOf(second) > 0,
                    "After one canvas detached, a change to the shared observed state must still request a " +
                        "repaint of the surviving canvas: the owner observer must outlive a single detach. " +
                        "Observed ${repaints.repaintsOf(second)} repaint requests.",
                )
            }
        }

    @Test
    fun aDrawScopeKeptPastItsDrawRefusesToDraw() =
        runComposeSwingTest {
            var kept: DrawScope? = null
            setContent {
                Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(64, 48)) { kept = this }
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
