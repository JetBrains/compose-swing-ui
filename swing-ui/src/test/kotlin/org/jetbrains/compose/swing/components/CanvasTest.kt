package org.jetbrains.compose.swing.components

import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Container
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.RepaintManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for [Canvas]: repaint is snapshot-observed.
 *
 * Painting is forced against an off-screen [BufferedImage], so every recorded value reflects exactly
 * one triggered [Canvas.onDraw] invocation. The harness runs the whole test body, painting, and
 * snapshot-change callbacks on the single event dispatch thread.
 */
class CanvasTest {
    private var hostRepaintManager: RepaintManager? = null

    @BeforeTest
    fun rememberRepaintManager() {
        hostRepaintManager = RepaintManager.currentManager(null)
    }

    @AfterTest
    fun restoreRepaintManager() {
        // The repaint manager is process-wide, and a recorder left installed intercepts the repaints
        // of every later test. Restoring it keeps the recording local to the test that asked for it.
        RepaintManager.setCurrentManager(hostRepaintManager)
    }

    @Test
    fun drawsOnceInitiallyWithInitialValue() = runComposeSwingTest {
        val drawn = mutableListOf<Int>()
        setContent {
            Canvas(modifier = SwingModifier.testTag(CANVAS).preferredSize(SIZE)) { _, _, _ ->
                drawn += 1
            }
        }

        forcePaint(onNodeWithTag(CANVAS).fetch<JComponent>())

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

        val canvas = onNodeWithTag(CANVAS).fetch<JComponent>()
        forcePaint(canvas)
        assertEquals(7, lastDrawn, "Initial paint should draw the initial value.")
        assertEquals(1, drawCount, "the initial paint should draw exactly once")

        value = 42
        awaitIdle()
        forcePaint(canvas)

        assertEquals(42, lastDrawn, "After recomposition the new value must be drawn.")
        assertEquals(2, drawCount, "A new onDraw should have produced a second draw.")
    }

    @Test
    fun stateReadOnlyInsideOnDrawIsObservedAndRequestsRepaint() = runComposeSwingTest {
        // `value` is NEVER read in the composition: only inside onDraw. So no recomposition can
        // happen when it changes - the only thing that can repaint the surface is the snapshot
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

        // And when that requested repaint is serviced, onDraw re-runs and reads the NEW value -
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
        // the only thing that can repaint the surface is the observer - an unwired one requests no
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
    fun removingCanvasDetachesItAndStopsObservingItsReads() = runComposeSwingTest {
        // `value` is read ONLY inside onDraw, so the observer is the only thing that can repaint the
        // surface. Removing the canvas releases its node, which drops its tracked reads from the shared
        // observer: a later change to the state it used to read must reach it no more.
        val value = mutableIntStateOf(7)
        var present by mutableStateOf(true)
        var drawCount = 0
        setContent {
            BoxPanel {
                Label(text = "anchor")
                if (present) {
                    Canvas(modifier = SwingModifier.testTag(CANVAS).preferredSize(SIZE)) { _, _, _ ->
                        value.intValue
                        drawCount++
                    }
                }
            }
        }

        val canvas = onNodeWithTag(CANVAS).fetch<JComponent>()
        var repaintRequests = 0
        installRepaintRecorder(canvas) { repaintRequests++ }
        forcePaint(canvas)
        assertEquals(1, drawCount, "the canvas should draw once before removal")

        present = false
        awaitIdle()

        onNodeWithTag(CANVAS).assertDoesNotExist()
        assertTrue(canvas.parent == null, "Removed canvas should be detached from the tree.")

        repaintRequests = 0
        value.intValue = 42
        awaitIdle()

        assertEquals(
            0,
            repaintRequests,
            "A change to state the removed canvas used to read must request no repaint of it: its node " +
                "released, so the shared observer no longer tracks its reads.",
        )
        assertEquals(1, drawCount, "No further onDraw should occur after removal.")
    }

    @Test
    fun parkedCanvasStopsObservingItsReads() = runComposeSwingTest {
        // `value` is read ONLY inside onDraw, so the observer is the only thing that can repaint the
        // surface. Parking the canvas deactivates its node, which drops its tracked reads: while parked
        // it is driven by nothing, exactly like every other parked node whose listeners are detached.
        val value = mutableIntStateOf(7)
        var active by mutableStateOf(true)
        var drawCount = 0
        var lastDrawn = Int.MIN_VALUE
        setContent {
            BoxPanel {
                Label(text = "anchor")
                ReusableContentHost(active = active) {
                    Canvas(modifier = SwingModifier.testTag(CANVAS).preferredSize(SIZE)) { _, _, _ ->
                        lastDrawn = value.intValue
                        drawCount++
                    }
                }
            }
        }

        // Fetch while the canvas is still active: parking restores the modifier-applied properties, the
        // test tag among them, so the parked surface is no longer addressable by tag.
        val canvas = onNodeWithTag(CANVAS).fetch<JComponent>()
        var repaintRequests = 0
        installRepaintRecorder(canvas) { repaintRequests++ }

        // Paint once to track the read, then confirm the surface reacts while it is active - so the
        // silence asserted below is the parking, not an observer that never worked.
        forcePaint(canvas)
        assertEquals(7, lastDrawn, "The first paint should draw the initial value.")
        repaintRequests = 0
        value.intValue = 42
        awaitIdle()
        assertTrue(repaintRequests > 0, "An active canvas must repaint on a change to state its onDraw read.")

        active = false
        awaitIdle()

        // Count only what the write below causes: deactivation itself restores modifier-applied
        // properties, and those restores may request repaints of their own.
        repaintRequests = 0
        value.intValue = 43
        awaitIdle()

        assertEquals(
            0,
            repaintRequests,
            "A change to state a parked canvas last read must request no repaint of it: its node was " +
                "deactivated, so the shared observer no longer tracks its reads.",
        )
    }

    @Test
    fun reactivatedCanvasObservesItsReadsAgain() = runComposeSwingTest {
        // Dropping the tracked reads when a canvas is parked must not silence it for good: reactivation
        // repaints the surface, and that paint registers its reads with the shared observer again.
        val value = mutableIntStateOf(7)
        var active by mutableStateOf(true)
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

        val canvas = onNodeWithTag(CANVAS).fetch<JComponent>()
        forcePaint(canvas)

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        var repaintRequests = 0
        installRepaintRecorder(canvas) { repaintRequests++ }
        // Reads are registered at paint time, so flush the repaint reactivation requested.
        forcePaint(canvas)
        assertEquals(7, lastDrawn, "The reactivated surface should draw the current value.")
        repaintRequests = 0

        value.intValue = 42
        awaitIdle()

        assertTrue(
            repaintRequests > 0,
            "A reactivated canvas must observe its reads again: a change to state read only inside its " +
                "onDraw must request a repaint. Observed $repaintRequests repaint requests.",
        )

        forcePaint(canvas)
        assertEquals(42, lastDrawn, "The serviced repaint must redraw the reactivated surface with the new value.")
    }

    @Test
    fun reusedNodeDoesNotRepaintNewContentForTheReadsOfTheOld() = runComposeSwingTest {
        // The same node is recycled for new content: `ReusableContent` keeps the surface and hands it a
        // fresh onDraw reading a different state. Each state is read ONLY inside its own onDraw, so the
        // observer is the only thing that can repaint the surface. The recycled node must be driven by
        // what the new content reads, never by what the old one read.
        val readByOldContent = mutableIntStateOf(1)
        val readByNewContent = mutableIntStateOf(1)
        var reuseKey by mutableStateOf(0)
        setContent {
            BoxPanel {
                Label(text = "anchor")
                ReusableContent(reuseKey) {
                    val observed = if (reuseKey == 0) readByOldContent else readByNewContent
                    Canvas(modifier = SwingModifier.testTag(CANVAS).preferredSize(SIZE)) { _, _, _ ->
                        observed.intValue
                    }
                }
            }
        }

        val canvas = onNodeWithTag(CANVAS).fetch<JComponent>()
        var repaintRequests = 0
        installRepaintRecorder(canvas) { repaintRequests++ }
        // Paint the old content so the observer tracks its read.
        forcePaint(canvas)

        reuseKey = 1
        awaitIdle()

        assertSame(
            canvas,
            onNodeWithTag(CANVAS).fetch<JComponent>(),
            "ReusableContent should have recycled the surface rather than built a new one; a new surface " +
                "would leave this test measuring the wrong component.",
        )

        // Count only what the writes below cause: recycling installs the new onDraw, which repaints.
        repaintRequests = 0
        readByOldContent.intValue = 2
        awaitIdle()

        assertEquals(
            0,
            repaintRequests,
            "A change to state only the previous content read must request no repaint of the recycled " +
                "surface, including before the new content has painted for the first time.",
        )

        // The new content is live: once it paints, its own read drives the surface.
        forcePaint(canvas)
        repaintRequests = 0
        readByNewContent.intValue = 2
        awaitIdle()

        assertTrue(
            repaintRequests > 0,
            "The recycled surface must observe what the new content reads. Observed $repaintRequests " +
                "repaint requests.",
        )
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
        forcePaint(onNodeWithTag(FIRST).fetch<JComponent>())
        forcePaint(second)
        secondRepaints = 0

        // Detach the first canvas. Its node releases and forgets its own scope; the shared observer
        // keeps running for the second canvas.
        firstPresent = false
        awaitIdle()
        onNodeWithTag(FIRST).assertDoesNotExist()

        // A change to the still-observed state must repaint the surviving canvas - proving the shared
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

    /** Rasterizes [component] off-screen, which is what drives its `onDraw` deterministically headless. */
    private fun forcePaint(component: JComponent) {
        component.setSize(SIZE)
        val image = BufferedImage(SIZE.width, SIZE.height, TYPE)
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
    private fun installRepaintRecorder(
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
        const val TYPE = BufferedImage.TYPE_INT_ARGB
        val SIZE = Dimension(64, 48)
    }
}
