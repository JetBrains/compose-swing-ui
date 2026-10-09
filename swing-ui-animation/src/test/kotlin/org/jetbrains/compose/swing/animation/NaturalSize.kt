package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.BorderPanelScope
import org.jetbrains.compose.swing.components.layout.GridBagPanelScope
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.Dimension
import javax.swing.JComponent

/** The sizes, timings and scaffolding that the natural-size tests of [AnimatedVisibility] and [AnimatedContent] use. */
internal object NaturalSize {
    val PARENT = Dimension(80, 60)
    val OWN = Dimension(200, 120)

    const val CONTAINER = "container"
    const val CONTENT = "content"
    const val FRAMES = 3
    const val FRAME_MILLIS = 16
    const val LONG = 640
    const val DELAY = 1000
    const val RESIZE = 20
    const val CAP = 100
    const val WIDE = 320
    const val CELL = 320
    const val ENTER_MILLIS = 320

    fun ComposeSwingTest.container(): JComponent = onNodeWithTag(CONTAINER).fetch<JComponent>()

    fun ComposeSwingTest.content(): JComponent = onNodeWithTag(CONTENT).fetch<JComponent>()

    /** A flow row holding a border panel of [size], whose center is where the animated container sits. */
    @Composable
    fun ParentRegion(
        size: Dimension,
        modifier: SwingModifier = SwingModifier,
        content: @Composable BorderPanelScope.() -> Unit,
    ) {
        Panel(PanelLayout.Flow(), modifier) {
            Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(size), content = content)
        }
    }

    /** A flow row holding a grid-bag panel [CELL] wide and twice as high, which a container fills horizontally. */
    @Composable
    fun NarrowCell(
        modifier: SwingModifier = SwingModifier,
        content: @Composable GridBagPanelScope.() -> Unit,
    ) {
        Panel(PanelLayout.Flow(), modifier) {
            Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(CELL, 2 * CELL), content = content)
        }
    }
}
