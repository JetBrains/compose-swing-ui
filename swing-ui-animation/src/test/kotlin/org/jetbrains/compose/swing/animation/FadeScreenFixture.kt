package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.foundation.graphics.DecorationModifierNode
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.layout.ConstrainedPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class FadeParent(
    val modifier: SwingModifier = SwingModifier,
) {
    Foundation,
    Stock,
    Shadowed(SwingModifier.shadow(4, Color.BLACK)),
}

// Alternates, so every version differs from the one before.
internal fun versionColor(version: Int): Color = if (version % 2 == 0) Color.RED else Color.BLUE

internal class FadeLeaf : JComponent() {
    var version = 0
    var paints = 0

    override fun paintComponent(g: Graphics) {
        paints++
        g.color = versionColor(version)
        g.fillRect(0, 0, width, height)
    }
}

/** The outermost step of a fade's container: [shown] is what the container shows after the paints so far. */
internal class FadeTapNode : DecorationModifierNode<Component>() {
    private var surface: BufferedImage? = null
    var paints = 0
    val shown: BufferedImage get() = checkNotNull(surface) { "the container has not painted" }

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        paints++
        val kept =
            surface?.takeIf { it.width == width && it.height == height }
                ?: BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { surface = it }
        val area = graphics.clipBounds ?: Rectangle(width, height)
        val into = kept.createGraphics()
        try {
            into.composite = AlphaComposite.Clear
            into.fillRect(area.x, area.y, area.width, area.height)
            into.composite = AlphaComposite.SrcOver
            into.clip = area
            content(into, width, height)
        } finally {
            into.dispose()
        }
        graphics.drawImage(kept, 0, 0, null)
    }
}

internal class FadeTap : SwingModifier.NodeElement<Component, FadeTapNode>() {
    private val created = ArrayList<FadeTapNode>()
    val node: FadeTapNode get() = created.single()

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): FadeTapNode = FadeTapNode().also { created += it }

    override fun update(node: FadeTapNode) = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

internal class FadeFixture {
    val leaf = FadeLeaf()
    val tap = FadeTap()
    lateinit var host: JComponent
    var visible by mutableStateOf(true)
    val container: JComponent get() = leaf.parent as JComponent
}

/** Mounts [fixture]'s content in a fade out under [parent]; [FadeFixture.tap] is applied only in a showing window. */
internal suspend fun ComposeSwingTest.mountFade(
    parent: FadeParent,
    fixture: FadeFixture,
    inWindow: Boolean = false,
) {
    val fade: @Composable () -> Unit = {
        SwingNode(
            factory = {
                val host: JComponent =
                    if (parent == FadeParent.Stock) {
                        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
                    } else {
                        ConstrainedPanel()
                    }
                host.also { fixture.host = it }
            },
        ) {
            AnimatedVisibility(
                visible = fixture.visible,
                modifier = (if (inWindow) SwingModifier.then(fixture.tap) else SwingModifier).then(parent.modifier),
                enter = EnterTransition.None,
                exit = fadeOut(tween(480, easing = LinearEasing)),
            ) { SwingNode(factory = { fixture.leaf }, modifier = SwingModifier.preferredSize(60, 40)) }
        }
    }
    if (inWindow) {
        setContent {
            Window(onCloseRequest = {}, title = "fade", state = WindowState(size = Dimension(400, 300))) { fade() }
        }
        awaitIdle()
        awaitStandingStill(SwingUtilities.getWindowAncestor(fixture.host))
    } else {
        setContent(fade)
        awaitIdle()
        fixture.host.validate()
    }
    mainClock.autoAdvance = false
}

/** Drives one frame and delivers the paints and repaint requests it caused. */
internal suspend fun ComposeSwingTest.driveFrame() {
    driveOneFrame()
    awaitEventsDelivered()
}

private suspend fun ComposeSwingTest.awaitStandingStill(window: Component) {
    var lastReshape = System.nanoTime()
    val listener =
        object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                lastReshape = System.nanoTime()
            }

            override fun componentMoved(event: ComponentEvent) {
                lastReshape = System.nanoTime()
            }
        }
    window.addComponentListener(listener)
    try {
        waitUntil(timeout = 10.seconds) { System.nanoTime() - lastReshape >= 250.milliseconds.inWholeNanoseconds }
    } finally {
        window.removeComponentListener(listener)
    }
}

private fun dominantChannel(color: Color): Int = listOf(color.red, color.green, color.blue).let { it.indexOf(it.max()) }

internal fun BufferedImage.assertShows(
    color: Color,
    x: Int,
    y: Int,
    what: String,
) {
    val shown = Color(getRGB(x, y), true)
    assertTrue(
        shown.alpha > 0 && dominantChannel(shown) == dominantChannel(color),
        "$what shows $shown at ($x, $y), but the current version is $color",
    )
}
