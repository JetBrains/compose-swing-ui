package org.jetbrains.compose.swing.core

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.ColorModel
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
 * Behavioral tests for a composition runtime hosted by a plain [java.awt.Component] - the runtime a
 * caller creates for a component with [SwingRecomposer.create] and disposes itself - and for the
 * boundary between it and the runtime a [java.awt.Window] owns.
 *
 * A component-hosted runtime recomposes on the event queue, so the test body runs on the EDT and
 * yields it back between checks until a bounded deadline. The cases that need a window realize a real
 * off-screen [JFrame] and skip on a headless environment; the rest run either way, since a
 * component-hosted runtime needs no window at all.
 */
class ComponentRecomposerTest {
    @Test
    fun aComponentOutsideAnyWindowComposesAndRecomposesOnItsOwnRuntime() = runSwingTest {
        val island = JPanel()
        assertNull(
            SwingUtilities.getWindowAncestor(island),
            "the case under test is a container with no window anywhere above it",
        )

        val runtime = SwingRecomposer.create(island)
        var text by mutableStateOf("v0")
        var content: DisposableHandle? = null
        try {
            content = island.setContent(parent = runtime.compositionContext) { Label(text = text) }
            awaitUntil("the component-hosted runtime composes its content") { labelTextOrNull(island) == "v0" }

            text = "v1"
            awaitUntil("the component-hosted runtime recomposes on a state change") { labelTextOrNull(island) == "v1" }
        } finally {
            content?.dispose()
            runtime.dispose()
        }
    }

    @Test
    fun disposingTheRuntimeEndsItsScopeIdempotentlyAndLeavesNoListenerBehind() = runSwingTest {
        val island = JPanel()
        val listenersBefore = island.getPropertyChangeListeners(GRAPHICS_CONFIGURATION_PROPERTY).size

        val runtime = SwingRecomposer.create(island)
        var text by mutableStateOf("v0")
        var effectAlive = false
        var content: DisposableHandle? = null
        try {
            assertEquals(
                listenersBefore + 1,
                island.getPropertyChangeListeners(GRAPHICS_CONFIGURATION_PROPERTY).size,
                "a runtime follows its component across displays, so it listens on that component",
            )
            content =
                island.setContent(parent = runtime.compositionContext) {
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
            awaitUntil("the runtime's scope runs the content's effect") { effectAlive }

            runtime.dispose()
            awaitUntil("disposing the runtime cancels what its scope was running") { !effectAlive }
            assertEquals(
                listenersBefore,
                island.getPropertyChangeListeners(GRAPHICS_CONFIGURATION_PROPERTY).size,
                "a disposed runtime leaves no listener on its component",
            )

            // Nothing drives the content now. The quiet period spans many frame intervals, so a
            // recomposer still running would have applied the change well inside it.
            text = "v1"
            delay(QUIET_PERIOD)
            assertEquals("v0", labelTextOrNull(island), "content of a disposed runtime stops recomposing")

            runtime.dispose()
            assertEquals(
                listenersBefore,
                island.getPropertyChangeListeners(GRAPHICS_CONFIGURATION_PROPERTY).size,
                "a second dispose is a no-op, not a failure",
            )
        } finally {
            content?.dispose()
            runtime.dispose()
        }
    }

    @Test
    fun aComponentHostedRuntimeIsReachedOnlyByHoldingIt() = runSwingTest {
        // The runtime is published nowhere on the component, so the self-first tree walk every
        // parentless setContent resolves through cannot find it.
        val host = JPanel()
        val descendant = JPanel().also { host.add(it) }

        val runtime = SwingRecomposer.create(host)
        try {
            assertNull(
                host.findParentCompositionContext(),
                "creating a runtime must not stamp its context on the component",
            )
            assertNull(
                descendant.findParentCompositionContext(),
                "a descendant must not discover the runtime created for its ancestor",
            )
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun oneWindowHandsOutOneContextAndTearsItDownWhenItCloses() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        try {
            val context = frame.compositionContext()
            assertSame(context, frame.compositionContext(), "a window hands out one context on every call")

            val islandA = childOf(frame)
            val islandB = childOf(frame)
            val islands = listOf(islandA.setContent { Label(text = "a") }, islandB.setContent { Label(text = "b") })
            try {
                awaitUntil("both islands render") {
                    labelTextOrNull(islandA) == "a" && labelTextOrNull(islandB) == "b"
                }
                assertSame(context, islandA.findParentCompositionContext(), "island A joined another context")
                assertSame(context, islandB.findParentCompositionContext(), "island B joined another context")
            } finally {
                islands.forEach { it.dispose() }
            }

            // The window owns what it handed out: closing it releases the runtime with no caller involved.
            frame.dispose()
            awaitUntil("the closed window releases its runtime") { frame.recomposerOrNull() == null }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aContainerInsideAWindowJoinsThatWindowRatherThanMintingItsOwnRuntime() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        try {
            val island = childOf(frame)
            val listenersBefore = island.getPropertyChangeListeners(GRAPHICS_CONFIGURATION_PROPERTY).size

            val content = island.setContent { Label(text = "in-window") }
            try {
                awaitUntil("the in-window island renders") { labelTextOrNull(island) == "in-window" }
                assertSame(
                    frame.compositionContext(),
                    island.findParentCompositionContext(),
                    "a container in a window must join that window's context",
                )
                assertEquals(
                    listenersBefore,
                    island.getPropertyChangeListeners(GRAPHICS_CONFIGURATION_PROPERTY).size,
                    "joining a window must create no runtime of the container's own",
                )
            } finally {
                content.dispose()
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aComponentHostedRuntimeIsCadencedToTheRateItsComponentReports() = runSwingTest {
        // A test cannot move a component onto a display of a chosen refresh rate, so the component
        // reports one directly: the runtime reads the rate through the component's
        // GraphicsConfiguration. The expectation is the cadence of a clock built for that reported
        // rate, never a fixed delay.
        val island = ReportingDisplayPanel()
        island.display = FakeDisplayConfiguration(FPS_120)

        val runtime = SwingRecomposer.create(island)
        try {
            assertEquals(FPS_120, island.displayRefreshRate(), "the component under test reports its own rate")
            assertEquals(
                SwingFrameClock(runtime.recomposer, island.displayRefreshRate()).frameDelayMillis,
                runtime.clock.frameDelayMillis,
                "the runtime must pace its clock by the rate its component reports",
            )

            // Moving to a display of another rate is reported as a "graphicsConfiguration" change.
            island.display = FakeDisplayConfiguration(FPS_60)
            assertEquals(
                SwingFrameClock(runtime.recomposer, island.displayRefreshRate()).frameDelayMillis,
                runtime.clock.frameDelayMillis,
                "the runtime must follow its component to a display of a different rate",
            )
        } finally {
            runtime.dispose()
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

    /** Adds and returns a fresh island container inside [frame]'s content pane. Must be on the EDT. */
    private fun childOf(frame: JFrame): Container = JPanel().also { frame.contentPane.add(it) }

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
private class FakeDisplayConfiguration(
    refreshRate: Int,
) : GraphicsConfiguration() {
    private val device: GraphicsDevice = FakeDisplayDevice(refreshRate, this)

    override fun getDevice(): GraphicsDevice = device

    override fun getColorModel(): ColorModel = ColorModel.getRGBdefault()

    override fun getColorModel(transparency: Int): ColorModel = ColorModel.getRGBdefault()

    override fun getDefaultTransform(): AffineTransform = AffineTransform()

    override fun getNormalizingTransform(): AffineTransform = AffineTransform()

    override fun getBounds(): Rectangle = Rectangle(0, 0, FAKE_SCREEN_SIZE, FAKE_SCREEN_SIZE)
}

/** The screen device [FakeDisplayConfiguration] is on, reporting a display mode of [refreshRate] Hz. */
private class FakeDisplayDevice(
    private val refreshRate: Int,
    private val configuration: GraphicsConfiguration,
) : GraphicsDevice() {
    override fun getType(): Int = TYPE_RASTER_SCREEN

    override fun getIDstring(): String = "fake-display-${refreshRate}hz"

    override fun getConfigurations(): Array<GraphicsConfiguration> = arrayOf(configuration)

    override fun getDefaultConfiguration(): GraphicsConfiguration = configuration

    override fun getDisplayMode(): DisplayMode =
        DisplayMode(FAKE_SCREEN_SIZE, FAKE_SCREEN_SIZE, FAKE_BIT_DEPTH, refreshRate)
}

private const val FAKE_SCREEN_SIZE: Int = 1000
private const val FAKE_BIT_DEPTH: Int = 32
