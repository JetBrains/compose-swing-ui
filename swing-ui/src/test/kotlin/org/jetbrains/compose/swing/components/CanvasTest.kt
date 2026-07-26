package org.jetbrains.compose.swing.components

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.RepaintManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral tests for [Canvas]: repaint is snapshot-observed. We verify that the surface draws once
 * initially, redraws with the new value after a state read *in the composition* recomposes it,
 * automatically requests a repaint when state read *only inside* `onDraw` changes (no recomposition),
 * and stops drawing once removed from the composition.
 *
 * Painting is driven deterministically in the headless harness by forcing a paint pass against an
 * off-screen [BufferedImage] graphics, so every recorded value reflects exactly one [Canvas.onDraw]
 * invocation we triggered. The harness runs the whole test body, painting, and snapshot-change
 * callbacks on the single event dispatch thread.
 */
class CanvasTest {
    @Test
    fun drawsOnceInitiallyWithInitialValue() = runComposeSwingTest {
        val drawn = mutableListOf<Int>()
        setContent {
            Canvas(modifier = SwingModifier.testTag(CANVAS).preferredSize(SIZE)) { _, _, _ ->
                drawn += 1
            }
        }

        forcePaint(CANVAS)

        assertEquals(listOf(1), drawn, "Canvas should draw exactly once after the initial paint.")
    }

    @Test
    fun recompositionWithNewInputRedrawsWithNewValue() = runComposeSwingTest {
        var value by mutableIntStateOf(7)
        var lastDrawn = Int.MIN_VALUE
        var drawCount = 0
        setContent {
            // `value` is read HERE, in the composition, and captured into onDraw. Changing it
            // recomposes Canvas -> new onDraw lambda -> repaint() -> onDraw re-runs.
            val captured = value
            Canvas(modifier = SwingModifier.testTag(CANVAS).preferredSize(SIZE)) { _, _, _ ->
                lastDrawn = captured
                drawCount++
            }
        }

        forcePaint(CANVAS)
        assertEquals(7, lastDrawn, "Initial paint should draw the initial value.")
        assertEquals(1, drawCount, "the initial paint should draw exactly once")

        value = 42
        awaitIdle()
        // Recomposition installed a fresh onDraw and requested a repaint; force the paint to flush
        // it deterministically in headless mode.
        forcePaint(CANVAS)

        assertEquals(42, lastDrawn, "After recomposition the new value must be drawn.")
        assertEquals(2, drawCount, "A new onDraw should have produced a second draw.")
    }

    @Test
    fun stateReadOnlyInsideOnDrawIsObservedAndRequestsRepaint() = runComposeSwingTest {
        // `value` is NEVER read in the composition: only inside onDraw. So no recomposition can
        // happen when it changes — the only thing that can repaint the surface is the snapshot
        // observer wrapping onDraw. The lambda itself is stable (it captures the State delegate,
        // not a value), so Canvas() is skippable and never hands the surface a fresh onDraw.
        val value = mutableIntStateOf(7)
        var lastDrawn = Int.MIN_VALUE
        setContent {
            Canvas(modifier = SwingModifier.testTag(CANVAS).preferredSize(SIZE)) { _, _, _ ->
                lastDrawn = value.intValue
            }
        }

        val canvas = onNodeWithTag(CANVAS).fetch<JComponent>()
        // Record every repaint request the surface makes. Off-screen the RepaintManager never
        // services repaint(), and its dirty-region bookkeeping is gated on isShowing(); a recording
        // manager captures the request at addDirtyRegion regardless, so it works headless.
        var repaintRequests = 0
        installRepaintRecorder(canvas) { repaintRequests++ }

        // First paint starts the observer and tracks the read of `value` inside onDraw.
        forcePaint(canvas)
        assertEquals(7, lastDrawn, "Initial paint should draw the initial value.")
        repaintRequests = 0

        // Mutate the observed state on the EDT and pump apply notifications (awaitIdle does this).
        // No recomposition occurs; the observer must react by requesting a repaint of the surface.
        value.intValue = 42
        awaitIdle()

        assertTrue(
            repaintRequests > 0,
            "A state read only inside onDraw changed: the snapshot observer must have requested a " +
                "repaint of the surface, with no recomposition and no manual forcePaint. Observed " +
                "$repaintRequests repaint requests.",
        )

        // And when that requested repaint is serviced, onDraw re-runs and reads the NEW value —
        // proving the observation drives a real redraw, not a stale one.
        forcePaint(canvas)
        assertEquals(42, lastDrawn, "The serviced repaint must redraw with the new value.")
    }

    @Test
    fun canvasInsertedDuringRecompositionIsObservedAndRedraws() = runComposeSwingTest {
        // A Canvas inserted during a recomposition (rather than the initial composition) must still
        // adopt the composition owner's snapshot observer. The observer is stamped onto each node on the
        // applier's top-down insert pass, which precedes the node's update changes that copy it onto the
        // surface; stamping it on the bottom-up pass instead would copy a not-yet-set observer for a
        // recomposition insert, leaving the surface unobserved. `value` is read ONLY inside onDraw, so
        // the only thing that can repaint the surface is the observer — an unwired one requests no
        // repaint.
        val value = mutableIntStateOf(7)
        var present by mutableStateOf(false)
        var lastDrawn = Int.MIN_VALUE
        setContent {
            BoxPanel {
                Label(text = "anchor")
                if (present) {
                    Canvas(modifier = SwingModifier.testTag(CANVAS).preferredSize(SIZE)) { _, _, _ ->
                        lastDrawn = value.intValue
                    }
                }
            }
        }

        // Insert the Canvas via a recomposition (it was absent from the initial composition).
        present = true
        awaitIdle()

        val canvas = onNodeWithTag(CANVAS).fetch<JComponent>()
        var repaintRequests = 0
        installRepaintRecorder(canvas) { repaintRequests++ }

        forcePaint(canvas)
        assertEquals(7, lastDrawn, "Initial paint of the recomposition-inserted surface should draw the value.")
        repaintRequests = 0

        value.intValue = 42
        awaitIdle()

        assertTrue(
            repaintRequests > 0,
            "A state read only inside the onDraw of a Canvas inserted during recomposition changed: the " +
                "owner observer must have been wired to it and requested a repaint. Observed " +
                "$repaintRequests repaint requests.",
        )

        forcePaint(canvas)
        assertEquals(42, lastDrawn, "The serviced repaint must redraw the surface with the new value.")
    }

    @Test
    fun canvasFirstActivatedViaReusableContentHostIsObserved() = runComposeSwingTest {
        // A Canvas whose first appearance is a ReusableContentHost activation (active false -> true) is
        // first inserted during a recomposition, like any conditionally-introduced surface. Its observer
        // must be wired so a state read only inside onDraw repaints it.
        val value = mutableIntStateOf(7)
        var active by mutableStateOf(false)
        var lastDrawn = Int.MIN_VALUE
        setContent {
            BoxPanel {
                Label(text = "anchor")
                ReusableContentHost(active = active) {
                    Canvas(modifier = SwingModifier.testTag(CANVAS).preferredSize(SIZE)) { _, _, _ ->
                        lastDrawn = value.intValue
                    }
                }
            }
        }

        active = true
        awaitIdle()

        val canvas = onNodeWithTag(CANVAS).fetch<JComponent>()
        var repaintRequests = 0
        installRepaintRecorder(canvas) { repaintRequests++ }

        forcePaint(canvas)
        assertEquals(7, lastDrawn, "Initial paint after activation should draw the value.")
        repaintRequests = 0

        value.intValue = 42
        awaitIdle()

        assertTrue(
            repaintRequests > 0,
            "A Canvas first activated via ReusableContentHost must be observed: a state read only inside " +
                "its onDraw must request a repaint. Observed $repaintRequests repaint requests.",
        )
    }

    @Test
    fun removingCanvasDisposesNodeAndStopsDrawing() = runComposeSwingTest {
        var present by mutableStateOf(true)
        var drawCount = 0
        setContent {
            BoxPanel {
                Label(text = "anchor")
                if (present) {
                    Canvas(modifier = SwingModifier.testTag(CANVAS).preferredSize(SIZE)) { _, _, _ ->
                        drawCount++
                    }
                }
            }
        }

        val canvas = onNodeWithTag(CANVAS).fetch<JComponent>()
        forcePaint(canvas)
        assertEquals(1, drawCount, "the canvas should draw once before removal")

        present = false
        awaitIdle()

        onNodeWithTag(CANVAS).assertDoesNotExist()
        // The disposed component receives no further draws even if its old reference is painted.
        assertTrue(canvas.parent == null, "Removed canvas should be detached from the tree.")
        assertEquals(1, drawCount, "No further onDraw should occur after removal.")
    }

    @Test
    fun observerSurvivesOneCanvasDetachAndStillRepaintsTheOther() = runComposeSwingTest {
        // Two canvases share the composition owner's single observer and both read the SAME state only
        // inside onDraw (never in the composition, so a change can repaint only via the observer).
        // Removing the first canvas must NOT tear down the shared observer: a later change to the state
        // must still request a repaint of the surviving canvas.
        val value = mutableIntStateOf(7)
        var firstPresent by mutableStateOf(true)
        setContent {
            BoxPanel {
                if (firstPresent) {
                    Canvas(modifier = SwingModifier.testTag(FIRST).preferredSize(SIZE)) { _, _, _ ->
                        value.intValue
                    }
                }
                Canvas(modifier = SwingModifier.testTag(SECOND).preferredSize(SIZE)) { _, _, _ ->
                    value.intValue
                }
            }
        }

        val second = onNodeWithTag(SECOND).fetch<JComponent>()
        var secondRepaints = 0
        installRepaintRecorder(second) { secondRepaints++ }

        // Paint both so the observer tracks each one's read of `value`.
        forcePaint(FIRST)
        forcePaint(second)
        secondRepaints = 0

        // Detach the first canvas. Its node releases and forgets its own scope; the shared observer
        // keeps running for the second canvas.
        firstPresent = false
        awaitIdle()
        onNodeWithTag(FIRST).assertDoesNotExist()

        // A change to the still-observed state must repaint the surviving canvas — proving the shared
        // observer was not disposed by the first canvas's detach.
        value.intValue = 42
        awaitIdle()

        assertTrue(
            secondRepaints > 0,
            "After one canvas detached, a change to the shared observed state must still request a " +
                "repaint of the surviving canvas: the owner observer must outlive a single detach. " +
                "Observed $secondRepaints repaint requests.",
        )
    }

    private fun ComposeSwingTest.forcePaint(tag: String) {
        forcePaint(onNodeWithTag(tag).fetch<JComponent>())
    }

    private fun ComposeSwingTest.forcePaint(component: JComponent) {
        component.setSize(SIZE)
        val image = BufferedImage(SIZE.width, SIZE.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            component.paint(graphics)
        } finally {
            graphics.dispose()
        }
    }

    /**
     * Installs a [RepaintManager] that invokes [onRepaintRequest] on each repaint request targeting
     * [component]. `JComponent.repaint()` routes through `RepaintManager.addDirtyRegion`; intercepting
     * it captures the request before the manager's `isShowing()` gate would drop it off-screen, so it is
     * a reliable headless signal. Direct `paint(...)` passes (our [forcePaint]) bypass the manager, so
     * they never fire the callback.
     */
    private fun ComposeSwingTest.installRepaintRecorder(
        component: JComponent,
        onRepaintRequest: () -> Unit,
    ) {
        RepaintManager.setCurrentManager(
            object : RepaintManager() {
                override fun addDirtyRegion(
                    c: JComponent,
                    x: Int,
                    y: Int,
                    w: Int,
                    h: Int,
                ) {
                    if (c === component) onRepaintRequest()
                    super.addDirtyRegion(c, x, y, w, h)
                }
            },
        )
    }

    private companion object {
        const val CANVAS = "canvas-under-test"
        const val FIRST = "first-canvas"
        const val SECOND = "second-canvas"
        val SIZE = Dimension(64, 48)
    }
}
