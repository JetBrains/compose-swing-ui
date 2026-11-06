package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.foundation.graphics.TapElement
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.layout.ConstrainedPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.assertTrue

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

internal class FadeFixture {
    val leaf = FadeLeaf()
    val tap = TapElement()
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
            ) { SwingNode(factory = { fixture.leaf }, modifier = SwingModifier.styleable { preferredSize(60, 40) }) }
        }
    }
    if (inWindow) {
        setContent {
            Window(onCloseRequest = {}, title = "fade", state = WindowState(size = Dimension(400, 300))) { fade() }
        }
        awaitIdle()
        onWindowWithTitle("fade").awaitWindowSystemIdle()
    } else {
        setContent(fade)
        awaitIdle()
        fixture.host.validate()
    }
    mainClock.autoAdvance = false
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
