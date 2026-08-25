package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.window.LocalWindow
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.image.BufferedImage
import javax.swing.CellRendererPane
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A [androidx.compose.runtime.CompositionLocal] a host composition provides to whatever nests into it. */
internal val LocalGreeting = compositionLocalOf { "none" }

/**
 * Records what content reads on every pass it composes: the window it is under, the locals reaching it,
 * the owner it composes with, and a value it remembers - which is the same object for as long as one
 * composition lives and another object once the content is composed again.
 */
internal class CompositionRecorder {
    internal val recorded = mutableListOf<Window?>()

    val windows: List<Window?> get() = recorded

    var greeting: String? = null
        private set

    var owner: LifecycleOwner? = null
        private set

    var remembered: Any? = null
        private set

    @Composable
    fun Read() {
        recorded += LocalWindow.current
        greeting = LocalGreeting.current
        owner = LocalLifecycleOwner.current
        remembered = remember { Any() }
    }
}

/** A [LifecycleOwner] standing in for one a caller or a navigation library provides. */
internal class HostLifecycleOwner : LifecycleOwner {
    internal val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry
}

/**
 * A host composition inside a window: the context it publishes for content to nest into, and the
 * [LifecycleOwner] its own content composes with, which is what content hanging under it reads.
 */
internal class Host(
    val context: CompositionContext,
    val owner: LifecycleOwner,
    val handle: DisposableHandle,
)

/**
 * Composes a host composition into [frame] that provides a local and publishes its own context, so
 * content nesting into it is part of a composition of this window rather than of the window root.
 * Must be called on the EDT.
 */
internal suspend fun hostIn(frame: JFrame): Host {
    var published: CompositionContext? = null
    var owner: LifecycleOwner? = null
    val panel = JPanel().also { frame.contentPane.add(it) }
    val handle =
        panel.setContent {
            CompositionLocalProvider(LocalGreeting provides "from-host") {
                published = rememberCompositionContext()
                owner = LocalLifecycleOwner.current
                Label(text = "host")
            }
        }
    awaitUntil("the host composition publishes its context") { published != null }
    return Host(checkNotNull(published), checkNotNull(owner), handle)
}

/**
 * Paints [cell] the way a list paints one of its rows: through the [pane] that adopts the component
 * it is handed, into an image of its own rather than onto a screen. Must be called on the EDT.
 */
internal fun paintThrough(
    pane: CellRendererPane,
    cell: Component,
    host: Container,
) {
    val image = BufferedImage(CELL_SIDE, CELL_SIDE, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        pane.paintComponent(graphics, cell, host, 0, 0, CELL_SIDE, CELL_SIDE)
    } finally {
        graphics.dispose()
    }
}

/**
 * A realized, off-screen [JFrame] with a live peer. Packing realizes the peer without showing the
 * frame. Must be called on the EDT.
 */
internal fun realizedFrame(): JFrame = JFrame().apply {
    defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
    pack()
}

/** The text of every [JLabel] in [container]'s subtree, in tree order. Must be called on the EDT. */
internal fun labelTexts(container: Container): List<String> {
    val texts = mutableListOf<String>()

    fun visit(c: Container) {
        for (child in c.components) {
            if (child is JLabel) texts += child.text
            if (child is Container) visit(child)
        }
    }
    visit(container)
    return texts
}

/**
 * Suspends on the EDT until [condition] holds, yielding the EDT back between checks so a window's
 * real frame-clock timer can fire and its recomposer can mount and recompose content. A condition
 * that never holds fails the test at the deadline, naming [description], instead of hanging.
 */
internal suspend fun awaitUntil(
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

internal val SETTLE_TIMEOUT = 10.seconds

/** Spans many frame intervals, so a recomposer still running would have applied a change well inside it. */
internal val QUIET_PERIOD = 300.milliseconds

internal const val CELL_SIDE: Int = 40
