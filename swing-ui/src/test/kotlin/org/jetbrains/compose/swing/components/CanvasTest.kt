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
import kotlin.test.assertIs
import kotlin.test.assertNotSame
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
        // surface. Parking the canvas deactivates its node and detaches its component from the tree,
        // which drops its tracked reads: while parked it is driven by nothing, exactly like a removed
        // canvas.
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

        onNodeWithTag(CANVAS).assertDoesNotExist()
        assertTrue(canvas.parent == null, "A parked canvas is detached from the tree.")

        val drawsBeforeParkedPaint = drawCount
        repaintRequests = 0
        value.intValue = 43
        awaitIdle()

        assertEquals(
            0,
            repaintRequests,
            "A change to state a parked canvas last read must request no repaint of it: its node was " +
                "deactivated, so the shared observer no longer tracks its reads.",
        )
        assertEquals(drawsBeforeParkedPaint, drawCount, "No further onDraw should occur while parked.")
    }

    @Test
    fun parkingDetachesTheCanvasAndReactivatingBuildsAFreshOneThatPaints() = runComposeSwingTest {
        // The control for the silence asserted while parked: the same paint pass over the parent that
        // draws an active canvas must draw nothing while the canvas is detached, and the fresh canvas
        // reactivation builds is drawn by that same paint pass once the composition drives it again.
        var active by mutableStateOf(true)
        var drawCount = 0
        setContent {
            BoxPanel {
                Label(text = "anchor")
                ReusableContentHost(active = active) {
                    Canvas(modifier = SwingModifier.testTag(CANVAS).preferredSize(SIZE)) { _, _, _ ->
                        drawCount++
                    }
                }
            }
        }

        val canvas = onNodeWithTag(CANVAS).fetch<JComponent>()
        val parent = assertIs<JComponent>(canvas.parent, "The canvas is held by the panel it was composed in.")

        forcePaintTree(parent)
        assertEquals(1, drawCount, "A paint pass over the parent must draw the active canvas.")

        active = false
        awaitIdle()
        assertTrue(canvas.parent == null, "A parked canvas is detached from the tree.")

        forcePaintTree(parent)
        assertEquals(1, drawCount, "The same paint pass must not draw the detached canvas while it is parked.")

        active = true
        awaitIdle()
        val reactivated = onNodeWithTag(CANVAS).fetch<JComponent>()
        assertNotSame(canvas, reactivated, "reactivation builds a fresh canvas rather than reusing the parked one")

        forcePaintTree(parent)
        assertEquals(2, drawCount, "The fresh canvas is drawn by the paint pass over its parent.")
    }

    @Test
    fun reactivatedCanvasObservesItsReadsAgain() = runComposeSwingTest {
        // Reactivation builds a fresh canvas from the node's factory: that fresh canvas's own paint pass
        // registers its reads with the shared observer, wholly apart from the parked canvas's now-dropped
        // reads.
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

        val reactivated = onNodeWithTag(CANVAS).fetch<JComponent>()
        assertNotSame(canvas, reactivated, "reactivation builds a fresh canvas rather than reusing the parked one")

        var repaintRequests = 0
        installRepaintRecorder(reactivated) { repaintRequests++ }
        forcePaint(reactivated)
        assertEquals(7, lastDrawn, "The fresh canvas should draw the current value.")
        repaintRequests = 0

        value.intValue = 42
        awaitIdle()

        assertTrue(
            repaintRequests > 0,
            "The fresh canvas must observe its reads: a change to state read only inside its onDraw must " +
                "request a repaint. Observed $repaintRequests repaint requests.",
        )

        forcePaint(reactivated)
        assertEquals(42, lastDrawn, "The serviced repaint must redraw the fresh canvas with the new value.")
    }

    @Test
    fun aKeyChangeStopsRepaintingTheDiscardedCanvasForTheReadsOfTheReplacement() = runComposeSwingTest {
        // A key change discards the old node and builds a fresh one for the new content: the fresh
        // canvas must be driven by what the new content reads, and the discarded one must be driven by
        // nothing, however each state is read ONLY inside its own onDraw.
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

        val original = onNodeWithTag(CANVAS).fetch<JComponent>()
        var repaintRequestsForOriginal = 0
        installRepaintRecorder(original) { repaintRequestsForOriginal++ }
        forcePaint(original)

        reuseKey = 1
        awaitIdle()

        val replacement = onNodeWithTag(CANVAS).fetch<JComponent>()
        assertNotSame(original, replacement, "a key change builds a fresh canvas rather than reusing the old one")
        assertTrue(original.parent == null, "the discarded canvas is detached from the tree")

        repaintRequestsForOriginal = 0
        readByOldContent.intValue = 2
        awaitIdle()

        assertEquals(
            0,
            repaintRequestsForOriginal,
            "A change to state only the discarded content read must request no repaint of it: its node " +
                "was released, so the shared observer no longer tracks its reads.",
        )

        var repaintRequestsForReplacement = 0
        installRepaintRecorder(replacement) { repaintRequestsForReplacement++ }
        forcePaint(replacement)
        repaintRequestsForReplacement = 0
        readByNewContent.intValue = 2
        awaitIdle()

        assertTrue(
            repaintRequestsForReplacement > 0,
            "The fresh canvas must observe what the new content reads. Observed $repaintRequestsForReplacement " +
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

    /**
     * Lays [container] out at its preferred size and rasterizes it off-screen, so each of its children is
     * painted the way a parent paints them on screen - a hidden child among them being one the pass skips.
     * Off-screen there is no peer, so the layout is driven directly instead of through `validate`.
     */
    private fun forcePaintTree(container: JComponent) {
        container.size = container.preferredSize
        layOutTree(container)
        val image = BufferedImage(container.width.coerceAtLeast(1), container.height.coerceAtLeast(1), TYPE)
        val graphics = image.createGraphics()
        try {
            container.paint(graphics)
        } finally {
            graphics.dispose()
        }
    }

    /** Runs each container's layout manager, top down, giving every child the bounds a paint pass reads. */
    private fun layOutTree(container: Container) {
        container.doLayout()
        for (child in container.components) {
            if (child is Container) layOutTree(child)
        }
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
