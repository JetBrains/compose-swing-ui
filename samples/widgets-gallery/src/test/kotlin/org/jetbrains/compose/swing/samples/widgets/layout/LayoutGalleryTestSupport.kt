package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.samples.widgets.ShowcaseMenuBar
import org.jetbrains.compose.swing.samples.widgets.ShowcaseShell
import org.jetbrains.compose.swing.samples.widgets.selectSection
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JMenuBar

/**
 * Opens [title] with the menu bar mounted over the shell, as the gallery's window has them: the two are compositions
 * of their own that share the switch of the alignment guides.
 */
internal suspend fun ComposeSwingTest.openSectionWithMenuBar(title: String) {
    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
    var guidesShown by mutableStateOf(true)
    setContent {
        Panel(PanelLayout.Border()) {
            SwingNode(factory = { JMenuBar() }, modifier = SwingModifier.north())
            ShowcaseShell(guidesShown)
        }
    }
    onNodeOfType<JMenuBar>().fetch<JMenuBar>().setContent {
        ShowcaseMenuBar(guidesShown, { guidesShown = it }, onExit = {})
    }
    awaitIdle()
    selectSection(title)
}

/** The rows of pixels in which [color] appears within the columns [xs]. */
internal fun BufferedImage.rowsWith(
    color: Color,
    xs: IntRange,
): List<Int> = (0 until height).filter { y -> xs.any { x -> getRGB(x, y) == color.rgb } }

/** The columns of pixels in which [color] appears within the rows [ys]. */
internal fun BufferedImage.columnsWith(
    color: Color,
    ys: IntRange,
): List<Int> = (0 until width).filter { x -> ys.any { y -> getRGB(x, y) == color.rgb } }

/**
 * The pixels of this capture that show the paint-outsets band, row by row: those that differ from [plain], the same
 * stage captured without guides, as the band painted over it does.
 */
internal fun BufferedImage.bandPixels(plain: BufferedImage): List<Point> {
    val banded = BufferedImage(plain.width, plain.height, plain.type)
    val graphics = banded.createGraphics()
    graphics.drawImage(plain, 0, 0, null)
    graphics.color = GuideMark.PaintOutsets.color
    graphics.fillRect(0, 0, banded.width, banded.height)
    graphics.dispose()
    return (0 until height).flatMap { y ->
        (0 until width)
            .filter { x -> getRGB(x, y) == banded.getRGB(x, y) && getRGB(x, y) != plain.getRGB(x, y) }
            .map { x -> Point(x, y) }
    }
}

/** This capture with [areas] painted over: what it shows outside them. */
internal fun BufferedImage.outside(areas: List<Rectangle>): BufferedImage {
    val masked = BufferedImage(width, height, type)
    val graphics = masked.createGraphics()
    graphics.drawImage(this, 0, 0, null)
    graphics.color = Color.BLACK
    areas.forEach(graphics::fill)
    graphics.dispose()
    return masked
}
