package org.jetbrains.compose.swing.core

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.observe
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.core.SwingFrameClock.Companion.displayRefreshRate
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.util.get
import org.jetbrains.compose.swing.window.LocalWindow
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.awaitApplication
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Container
import java.awt.DisplayMode
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.ColorModel
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests for a recomposer hosted by a plain [java.awt.Component] - the one a caller creates
 * for a component with [SwingRecomposer.create] and disposes itself - and for the boundary between it
 * and the recomposer a [java.awt.Window] owns - including the window that owns none, because its
 * content is part of an application composition declared elsewhere.
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
                "creating a recomposer must not stamp its context on the component",
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
        composition.display = FakeDisplayConfiguration(FPS_120)

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
            composition.display = FakeDisplayConfiguration(FPS_60)
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

    @Test
    fun aWindowComposingUnderAnApplicationIsRefusedARecomposerOfItsOwn() = runBlocking {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        var refusal: Throwable? = null
        var recomposerMinted = true
        var foundOnTheWindow: Recomposer? = null

        withTimeout(SETTLE_TIMEOUT) {
            awaitApplication {
                Window(onCloseRequest = ::exitApplication, visible = false) {
                    val peer = LocalWindow.current
                    LaunchedEffect(peer) {
                        // The application's effects run on the EDT, which is where a window is asked
                        // what recomposer it has.
                        refusal = runCatching { peer?.compositionContext() }.exceptionOrNull()
                        recomposerMinted = peer?.swingRecomposerOrNull() != null
                        foundOnTheWindow = peer?.findRecomposer()
                        exitApplication()
                    }
                }
            }
        }

        val thrown =
            assertNotNull(refusal, "a window whose content is the application's owns no recomposer to hand out")
        assertIs<IllegalStateException>(thrown, "the refusal must be reported as illegal state")
        assertTrue(
            "ApplicationScope" in thrown.message.orEmpty(),
            "the refusal must name where the recomposer really is, but was: ${thrown.message}",
        )
        assertFalse(
            recomposerMinted,
            "asking must not answer by creating a recomposer that drives nothing",
        )
        assertNull(
            foundOnTheWindow,
            "such a window publishes the application composition's own context, which names no " +
                "recomposer: ApplicationScope is what hands out the one driving it",
        )
    }

    /**
     * The application's own composition is registered on the recomposer its scope hands out. Needs no
     * window, so it holds where there is no display - which is where a recomposer of its own, driving
     * nothing, would go unnoticed.
     */
    @OptIn(ExperimentalComposeRuntimeApi::class)
    @Test
    fun anApplicationComposesOnTheRecomposerItsScopeHandsOut() = runBlocking {
        val registered = compositionCollector()
        var reported = 0

        withTimeout(SETTLE_TIMEOUT) {
            awaitApplication {
                LaunchedEffect(Unit) {
                    val handle = recomposer.observe(registered.observer)
                    try {
                        reported = registered.compositions.size
                    } finally {
                        handle.dispose()
                    }
                    exitApplication()
                }
            }
        }

        assertTrue(
            reported >= 1,
            "the application's own composition must be registered on the recomposer its scope hands out, " +
                "but that recomposer reported $reported compositions",
        )
    }

    @OptIn(ExperimentalComposeRuntimeApi::class)
    @Test
    fun aWindowDeclaredInAnApplicationComposesOnTheRecomposerItsScopeHandsOut() = runBlocking {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")

        // A composition registers with the recomposer at the root of its context chain. So a registration
        // arriving at an observer of ApplicationScope.recomposer exactly when a window is declared is what
        // says that window's content composes on that very recomposer: the scope hands out the one the
        // window runs on, not a recomposer of its own that drives nothing.
        val registered = compositionCollector()
        var beforeWindow = 0
        var afterWindow = 0

        withTimeout(SETTLE_TIMEOUT) {
            awaitApplication {
                var isWindowDeclared by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val handle = recomposer.observe(registered.observer)
                    try {
                        beforeWindow = registered.compositions.size
                        isWindowDeclared = true
                        awaitCancellation()
                    } finally {
                        handle.dispose()
                    }
                }

                if (isWindowDeclared) {
                    Window(onCloseRequest = ::exitApplication, visible = false) {
                        Label(text = "in-application")
                        LaunchedEffect(Unit) {
                            // The window's content is mounted by now, so its composition has registered.
                            afterWindow = registered.compositions.size
                            exitApplication()
                        }
                    }
                }
            }
        }

        assertTrue(
            beforeWindow >= 1,
            "the application's own composition must be registered on the recomposer its scope hands out, " +
                "but that recomposer reported $beforeWindow compositions",
        )
        assertTrue(
            afterWindow > beforeWindow,
            "declaring a window must register its content composition on the recomposer the application's " +
                "scope hands out, but that recomposer went from $beforeWindow compositions to $afterWindow",
        )
    }

    /** Collects the compositions a [CompositionRegistrationObserver] is told about, newest last. */
    private fun compositionCollector(): CompositionCollector = CompositionCollector()

    @Test
    fun aComponentFindsTheRecomposerDrivingTheWindowItStandsIn() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        try {
            val composition = childOf(frame)
            val content = composition.setContent { Label(text = "in-window") }
            try {
                awaitUntil("the content composition renders") { labelTextOrNull(composition) == "in-window" }
                val driving = assertNotNull(frame.swingRecomposerOrNull()).recomposer

                assertSame(
                    driving,
                    composition.findRecomposer(),
                    "a component must find the recomposer the window it stands in drives",
                )
                assertSame(
                    driving,
                    composition.components.single().findRecomposer(),
                    "and so must one nested deeper, since the walk reaches the window from anywhere below it",
                )
                assertSame(
                    driving,
                    frame.findRecomposer(),
                    "a window is a component, so it answers for itself",
                )
            } finally {
                content.dispose()
            }
        } finally {
            frame.dispose()
        }
    }

    /**
     * A `SwingNode` declaring `hostSubcompositions` stamps its component with a context taken from inside
     * the composition, which names no recomposer. The walk passes over such a stamp and carries on to
     * the window's own, so content nested through one still answers with the scope actually driving it.
     */
    @Test
    fun contentNestedThroughASubcompositionHostAnswersWithTheScopeDrivingIt() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        try {
            val composition = childOf(frame)
            val host = JPanel()
            val outer =
                composition.setContent {
                    val parentContext = rememberCompositionContext()
                    SwingNode(
                        factory = { host },
                        update = { hostSubcompositions(parentContext) },
                    )
                }
            var nested: DisposableHandle? = null
            try {
                awaitUntil("the host node is applied") { host.parent != null }
                assertIsNot<Recomposer>(
                    assertNotNull(host[COMPOSITION_KEY], "the host must carry the stamp it published"),
                    "the case under test needs that stamp to name no recomposer of its own",
                )

                nested = host.setContent { Label(text = "nested") }
                awaitUntil("the nested content composes") { labelTextOrNull(host) == "nested" }

                val driving = assertNotNull(frame.swingRecomposerOrNull()).recomposer
                assertSame(
                    driving,
                    host.components.single().findRecomposer(),
                    "the opaque stamp hides no scope of its own, so the window's is what drives this",
                )
            } finally {
                nested?.dispose()
                outer.dispose()
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun findingARecomposerNeverStartsOne() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        try {
            val composition = childOf(frame)

            assertNull(
                composition.findRecomposer(),
                "a window holding no composed content drives no recomposer to find",
            )
            assertNull(
                frame.swingRecomposerOrNull(),
                "and asking must not start one, which is what lets a tool ask of every window an " +
                    "application has open",
            )
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aComponentNoCompositionReachesFindsNoRecomposer() = runSwingTest {
        assertNull(
            JPanel().findRecomposer(),
            "a component carrying no composed content and standing in none has nothing driving it",
        )
    }

    /**
     * A recomposer a caller creates for a component is what that component's content composes on, so it
     * is what the component answers with - the window it happens to hang under drives its own content
     * and not this, and answers nothing here.
     */
    @Test
    fun aComponentFindsTheRecomposerItsCallerCreatedForIt() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        try {
            val composition = childOf(frame)
            val recomposer = SwingRecomposer.create(composition)
            var content: DisposableHandle? = null
            try {
                content = composition.setContent(parent = recomposer.compositionContext) { Label(text = "hello") }
                awaitUntil("the caller's recomposer composes its content") { labelTextOrNull(composition) == "hello" }

                assertSame(
                    recomposer.recomposer,
                    composition.findRecomposer(),
                    "the container answers with the recomposer its content was given, not with its window's",
                )
                assertSame(
                    recomposer.recomposer,
                    composition.components.single().findRecomposer(),
                    "and so does a component nested inside that content",
                )
                assertNull(
                    frame.swingRecomposerOrNull(),
                    "naming a recomposer of one's own leaves the window driving nothing, so the answer " +
                        "can only have come from the recomposer the caller created",
                )
            } finally {
                content?.dispose()
                recomposer.dispose()
            }
        } finally {
            frame.dispose()
        }
    }

    /** A component-hosted recomposer needs no window, and is found from a container hanging under none. */
    @Test
    fun aRecomposerIsFoundOnAComponentThatStandsInNoWindow() = runSwingTest {
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        var content: DisposableHandle? = null
        try {
            content = composition.setContent(parent = recomposer.compositionContext) { Label(text = "detached") }
            awaitUntil("the detached content composes") { labelTextOrNull(composition) == "detached" }

            assertSame(
                recomposer.recomposer,
                composition.components.single().findRecomposer(),
                "what drives a component's content is a fact about the content, not about any window",
            )
        } finally {
            content?.dispose()
            recomposer.dispose()
        }
    }

    /**
     * An owned window's Swing parent is the window that owns it, and no composition reaches across that
     * link. A dialog drives its own content, and one holding none answers with nothing rather than with
     * whatever its owner drives.
     */
    @Test
    fun anOwnedWindowAnswersForItselfRatherThanForItsOwner() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        try {
            val outer = childOf(frame).setContent { Label(text = "owner") }
            val empty = JDialog(frame).apply { pack() }
            val composed = JDialog(frame).apply { pack() }
            var inner: DisposableHandle? = null
            try {
                assertNotNull(frame.swingRecomposerOrNull(), "the owner must drive a composition to inherit")
                assertNull(
                    empty.findRecomposer(),
                    "a dialog holding no composed content answers with nothing, whatever its owner drives",
                )

                inner = composed.contentPane.setContent { Label(text = "owned") }
                awaitUntil("the dialog's content composes") { labelTextOrNull(composed.contentPane) == "owned" }
                val ownRecomposer = assertNotNull(composed.swingRecomposerOrNull()).recomposer

                assertSame(
                    ownRecomposer,
                    composed.contentPane.findRecomposer(),
                    "an owned window drives its own content, and that recomposer is what it answers with",
                )
                assertSame(ownRecomposer, composed.findRecomposer(), "and the window itself answers the same")
            } finally {
                inner?.dispose()
                composed.dispose()
                empty.dispose()
                outer.dispose()
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun findingARecomposerMustHappenOnTheEventDispatchThread() {
        val failure = assertFailsWith<IllegalStateException> { JPanel().findRecomposer() }
        assertTrue(
            failure.message.orEmpty().contains("Event Dispatch Thread"),
            "the read must name the thread it belongs to, but was: ${failure.message}",
        )
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

/** Every composition an observer has been told about and not yet been told was unregistered. */
@OptIn(ExperimentalComposeRuntimeApi::class)
private class CompositionCollector {
    val compositions: MutableList<ObservableComposition> = mutableListOf()

    val observer: CompositionRegistrationObserver =
        object : CompositionRegistrationObserver {
            override fun onCompositionRegistered(composition: ObservableComposition) {
                compositions += composition
            }

            override fun onCompositionUnregistered(composition: ObservableComposition) {
                compositions.remove(composition)
            }
        }
}
