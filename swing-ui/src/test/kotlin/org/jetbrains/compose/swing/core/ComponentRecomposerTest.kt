package org.jetbrains.compose.swing.core

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.core.SwingFrameClock.Companion.displayRefreshRate
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Container
import java.awt.DisplayMode
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests for a recomposer hosted by a plain [java.awt.Component] - the one a caller creates
 * for a component with [SwingRecomposer.create] and disposes itself - and for a window sharing its
 * context with containers inside it.
 *
 * A component-hosted recomposer recomposes on the event queue, so the test body runs on the EDT and
 * yields it back between checks until a bounded deadline. The cases that need a window realize a real
 * off-screen [JFrame] and skip on a headless environment; the rest run either way, since a
 * component-hosted recomposer needs no window at all.
 */
class ComponentRecomposerTest {
    @Test
    fun aComponentOutsideAnyWindowComposesAndRecomposesOnItsOwnRecomposer() = runSwingTest {
        val composition = JPanel()
        assertNull(
            SwingUtilities.getWindowAncestor(composition),
            "the case under test is a container with no window anywhere above it",
        )

        val recomposer = SwingRecomposer.create(composition)
        var text by mutableStateOf("v0")
        var content: DisposableHandle? = null
        try {
            content = composition.setContent(parent = recomposer.compositionContext) { Label(text = text) }
            awaitUntil("the component-hosted recomposer composes its content") { labelTextOrNull(composition) == "v0" }

            text = "v1"
            awaitUntil("the component-hosted recomposer recomposes on a state change") {
                labelTextOrNull(composition) == "v1"
            }
        } finally {
            content?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun aDisposedRecomposerDisposesContentThatRegistersWithIt() = runSwingTest {
        val recomposer = SwingRecomposer.create(JPanel())
        recomposer.dispose()

        var disposals = 0
        recomposer.registerContentComposition(DisposableHandle { disposals++ })

        assertEquals(1, disposals, "content registering with a disposed recomposer is disposed on the spot")
    }

    @Test
    fun disposingTheRecomposerEndsItsScopeIdempotentlyAndLeavesNoListenerBehind() = runSwingTest {
        val composition = JPanel()
        val listenersBefore = composition.getPropertyChangeListeners(GRAPHICS_CONFIGURATION_PROPERTY).size

        val recomposer = SwingRecomposer.create(composition)
        var text by mutableStateOf("v0")
        var effectAlive = false
        var content: DisposableHandle? = null
        try {
            assertEquals(
                listenersBefore + 1,
                composition.getPropertyChangeListeners(GRAPHICS_CONFIGURATION_PROPERTY).size,
                "a recomposer follows its component across displays, so it listens on that component",
            )
            content =
                composition.setContent(parent = recomposer.compositionContext) {
                    Label(text = text)
                    LaunchedEffect(Unit) {
                        effectAlive = true
                        try {
                            awaitCancellation()
                        } finally {
                            effectAlive = false
                        }
                    }
                }
            awaitUntil("the recomposer's scope runs the content's effect") { effectAlive }

            recomposer.dispose()
            awaitUntil("disposing the recomposer cancels what its scope was running") { !effectAlive }
            assertEquals(
                listenersBefore,
                composition.getPropertyChangeListeners(GRAPHICS_CONFIGURATION_PROPERTY).size,
                "a disposed recomposer leaves no listener on its component",
            )

            // Nothing drives the content now. The quiet period spans many frame intervals, so a
            // recomposer still running would have applied the change well inside it.
            text = "v1"
            delay(QUIET_PERIOD)
            assertEquals("v0", labelTextOrNull(composition), "content of a disposed recomposer stops recomposing")

            recomposer.dispose()
            assertEquals(
                listenersBefore,
                composition.getPropertyChangeListeners(GRAPHICS_CONFIGURATION_PROPERTY).size,
                "a second dispose is a no-op, not a failure",
            )
        } finally {
            content?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun aComponentHostedRecomposerIsReachedOnlyByHoldingIt() = runSwingTest {
        // The recomposer is published nowhere on the component, so the self-first tree walk every
        // parentless setContent resolves through cannot find it.
        val host = JPanel()
        val descendant = JPanel().also { host.add(it) }

        val recomposer = SwingRecomposer.create(host)
        try {
            assertNull(
                host.findParentCompositionContext(),
                "creating a recomposer must not publish its context on the component",
            )
            assertNull(
                descendant.findParentCompositionContext(),
                "a descendant must not discover the recomposer created for its ancestor",
            )
        } finally {
            recomposer.dispose()
        }
    }

    @Test
    fun oneWindowHandsOutOneContextAndTearsItDownWhenItCloses() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        try {
            val context = frame.compositionContext()
            assertSame(context, frame.compositionContext(), "a window hands out one context on every call")

            val compositionA = childOf(frame)
            val compositionB = childOf(frame)
            val compositions =
                listOf(
                    compositionA.setContent { Label(text = "a") },
                    compositionB.setContent { Label(text = "b") },
                )
            try {
                awaitUntil("both content compositions render") {
                    labelTextOrNull(compositionA) == "a" && labelTextOrNull(compositionB) == "b"
                }
                assertSame(context, compositionA.findParentCompositionContext(), "composition A joined another context")
                assertSame(context, compositionB.findParentCompositionContext(), "composition B joined another context")
            } finally {
                compositions.forEach { it.dispose() }
            }

            // The window owns what it handed out, and releases it with no caller involved: disposing the
            // last content composition above emptied it, and a window's recomposer ends once nothing
            // composes under it. The close below is not what does it here - it has nothing left to reap.
            awaitUntil("emptying the window releases the recomposer it handed out") {
                frame.swingRecomposerOrNull() == null
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aContainerInsideAWindowJoinsThatWindowRatherThanMintingItsOwnRecomposer() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        try {
            val composition = childOf(frame)
            val listenersBefore = composition.getPropertyChangeListeners(GRAPHICS_CONFIGURATION_PROPERTY).size

            val content = composition.setContent { Label(text = "in-window") }
            try {
                awaitUntil("the in-window content composition renders") { labelTextOrNull(composition) == "in-window" }
                assertSame(
                    frame.compositionContext(),
                    composition.findParentCompositionContext(),
                    "a container in a window must join that window's context",
                )
                assertEquals(
                    listenersBefore,
                    composition.getPropertyChangeListeners(GRAPHICS_CONFIGURATION_PROPERTY).size,
                    "joining a window must create no recomposer of the container's own",
                )
            } finally {
                content.dispose()
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aComponentHostedRecomposerIsCadencedToTheRateItsComponentReports() = runSwingTest {
        // A test cannot move a component onto a display of a chosen refresh rate, so the component
        // reports one directly: the recomposer reads the rate through the component's
        // GraphicsConfiguration. The expectation is the cadence of a clock built for that reported
        // rate, never a fixed delay.
        val composition = ReportingDisplayPanel()
        composition.display = fakeDisplayConfiguration(FPS_120)

        val recomposer = SwingRecomposer.create(composition)
        try {
            assertEquals(FPS_120, composition.displayRefreshRate(), "the component under test reports its own rate")
            assertEquals(
                SwingUiDispatcher()
                    .frameClock
                    .apply {
                        pace(recomposer.recomposer)
                        setFramesPerSecond(composition.displayRefreshRate())
                    }.frameDelayMillis,
                recomposer.clock.frameDelayMillis,
                "the recomposer must pace its clock by the rate its component reports",
            )

            // Moving to a display of another rate is reported as a "graphicsConfiguration" change.
            composition.display = fakeDisplayConfiguration(FPS_60)
            assertEquals(
                SwingUiDispatcher()
                    .frameClock
                    .apply {
                        pace(recomposer.recomposer)
                        setFramesPerSecond(composition.displayRefreshRate())
                    }.frameDelayMillis,
                recomposer.clock.frameDelayMillis,
                "the recomposer must follow its component to a display of a different rate",
            )
        } finally {
            recomposer.dispose()
        }
    }

    /**
     * A realized, off-screen [JFrame] with a live peer. Packing realizes the peer without showing it,
     * so disposing it fires the `windowClosed` event the window teardown listens for. Must be called on
     * the EDT.
     */
    private fun realizedFrame(): JFrame = JFrame().apply {
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        setBounds(0, 0, FRAME_SIZE, FRAME_SIZE)
        pack()
    }

    /** Adds and returns a fresh child container inside [frame]'s content pane. Must be on the EDT. */
    private fun childOf(frame: JFrame): JPanel = JPanel().also { frame.contentPane.add(it) }

    /** The single [JLabel]'s text in [container]'s subtree, or `null` while none has mounted yet. */
    private fun labelTextOrNull(container: Container): String? {
        val labels = mutableListOf<JLabel>()

        fun visit(c: Container) {
            for (child in c.components) {
                if (child is JLabel) labels += child
                if (child is Container) visit(child)
            }
        }
        visit(container)
        return labels.singleOrNull()?.text
    }

    /**
     * Suspends on the EDT until [condition] holds, yielding the EDT back between checks so the
     * frame-clock timer can fire and the composition can mount and recompose. A condition that never
     * becomes true fails the test at the deadline, naming [description], instead of hanging.
     */
    private suspend fun awaitUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        try {
            withTimeout(SETTLE_TIMEOUT) {
                while (!condition()) {
                    yield()
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("Timed out after $SETTLE_TIMEOUT waiting until $description", timedOut)
        }
    }

    private companion object {
        const val GRAPHICS_CONFIGURATION_PROPERTY: String = "graphicsConfiguration"
        const val FRAME_SIZE: Int = 200
        const val FPS_60: Int = 60
        const val FPS_120: Int = 120
        val SETTLE_TIMEOUT = 10.seconds
        val QUIET_PERIOD = 250.milliseconds
    }
}

/**
 * A container that reports the [display] it is on and announces a change the way a component moved
 * between displays does. A component never on a display reports none, like a plain container outside
 * any window.
 */
private class ReportingDisplayPanel : JPanel() {
    var display: GraphicsConfiguration? = null
        set(value) {
            val previous = field
            field = value
            firePropertyChange("graphicsConfiguration", previous, value)
        }

    override fun getGraphicsConfiguration(): GraphicsConfiguration? = display
}

/** A [GraphicsConfiguration] on a device whose display mode reports [refreshRate] frames per second. */
private fun fakeDisplayConfiguration(refreshRate: Int): GraphicsConfiguration = mockk<GraphicsConfiguration> {
    every { device } returns
        mockk<GraphicsDevice> {
            every { displayMode } returns DisplayMode(0, 0, 0, refreshRate)
        }
}
