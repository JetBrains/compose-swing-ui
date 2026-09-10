package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.rememberCompositionContext
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.util.get
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Button
import java.awt.Container
import java.awt.GraphicsEnvironment
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIsNot
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests for [findRecomposer]: walking the component hierarchy to discover the recomposer
 * driving a component or window.
 */
class FindRecomposerTest {
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

    @Test
    fun aRawAwtComponentFindsNoRecomposer() = runSwingTest {
        // A raw java.awt.Button is neither a JComponent nor a Window. The walk's per-component
        // check has an else branch for these - they carry no composition and name no recomposer.
        assertNull(
            Button().findRecomposer(),
            "a raw AWT component carries no composition and answers with no recomposer",
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
     * A context captured with `rememberCompositionContext()` is no [Recomposer] and names none, so a
     * container given one as its parent has to be answered from the scope behind that context.
     */
    @Test
    fun contentComposingUnderACapturedContextAnswersWithTheRecomposerBehindIt() = runSwingTest {
        val host = JPanel()
        val recomposer = SwingRecomposer.create(host)
        var captured: CompositionContext? = null
        var outer: DisposableHandle? = null
        var nested: DisposableHandle? = null
        val joining = JPanel()
        try {
            outer =
                host.setContent(parent = recomposer.compositionContext) {
                    captured = rememberCompositionContext()
                    Label(text = "host")
                }
            awaitUntil("the host content composes and publishes its context") { captured != null }
            assertIsNot<Recomposer>(
                assertNotNull(captured),
                "the case under test needs a context that names no recomposer of its own",
            )

            nested = joining.setContent(parent = assertNotNull(captured)) { Label(text = "joined") }
            awaitUntil("the joined content composes") { labelTextOrNull(joining) == "joined" }

            assertSame(
                recomposer.recomposer,
                joining.findRecomposer(),
                "content joined to a captured context answers with the recomposer driving that context",
            )
        } finally {
            nested?.dispose()
            outer?.dispose()
            recomposer.dispose()
        }
    }

    /**
     * A window publishes what stands in it on its content pane, which a walk towards the ancestors never
     * reaches, so the window itself has to be answered from what it holds.
     */
    @Test
    fun aWindowAnswersForContentMountedUnderACallerNamedRecomposer() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display to realize a window")
        val frame = realizedFrame()
        try {
            val recomposer = SwingRecomposer.create(frame.contentPane)
            var content: DisposableHandle? = null
            try {
                content =
                    frame.contentPane.setContent(parent = recomposer.compositionContext) { Label(text = "named") }
                awaitUntil("the named content composes") { labelTextOrNull(frame.contentPane) == "named" }

                assertNull(
                    frame.swingRecomposerOrNull(),
                    "the case under test needs a window driving no recomposer of its own",
                )
                assertSame(
                    recomposer.recomposer,
                    frame.findRecomposer(),
                    "a window answers with what drives the content standing in it",
                )
            } finally {
                content?.dispose()
                recomposer.dispose()
            }
        } finally {
            frame.dispose()
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
        const val FRAME_SIZE: Int = 200
        val SETTLE_TIMEOUT = 10.seconds
    }
}
