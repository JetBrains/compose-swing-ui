package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.Layout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import javax.swing.JComponent

/** A bounded parent for deferred transform tests that read pixels beyond the content's own extent. */
@Composable
internal fun DeferredViewport(content: @Composable ConstrainedScope.() -> Unit) =
    Layout(
        measurePolicy = { measurables, _ ->
            val placeable =
                measurables.single().measure(
                    Constraints(minWidth = 160, maxWidth = 160, minHeight = 100, maxHeight = 100),
                )
            layout(160, 100) {
                placeable.place(0, 0)
            }
        },
        content = { Box(content = content) },
    )

/** The Foundation box that paints the animated container's placement layer. */
internal fun ComposeSwingTest.deferredContainer() = onRoot().onChild()

/** The animated container inside the Foundation box used by deferred tests. */
internal fun ComposeSwingTest.deferredAnimatedContainer() = onNodeWithTag("deferredVisibility")

/** The content of the one animated container the test mounts. */
internal fun ComposeSwingTest.content() = deferredAnimatedContainer().fetch<JComponent>().getComponent(0)

/** The left edge of the blue deferred content as painted through its Foundation placement layer. */
internal fun ComposeSwingTest.paintedDeferredContentLeft(): Int {
    val image = deferredContainer().captureToImage()
    for (x in 0 until image.width) {
        for (y in 0 until image.height) {
            val color = Color(image.getRGB(x, y), true)
            if (color.alpha != 0 && color.blue > color.red + 80 && color.blue > color.green + 80) {
                return x
            }
        }
    }
    error("deferred content was not painted in ${image.width}x${image.height}")
}

/** The color the pixel ([x], [y]) of the content is painted in, with what is left of its opacity. */
internal fun ComposeSwingTest.sampledContent(
    x: Int = DEFERRED_SAMPLE,
    y: Int = DEFERRED_SAMPLE,
) = Color(deferredContainer().captureToImage().getRGB(x, y), true)

/** A content that fills itself, so what the transform leaves of it can be read off one pixel. */
@Composable
internal fun DeferredBlock() =
    Label(
        text = "",
        modifier =
            SwingModifier
                .preferredSize(width = DEFERRED_BLOCK_WIDTH, height = DEFERRED_BLOCK_HEIGHT)
                .opaque(true)
                .background(DeferredBlockColor),
    )

/** The color the content fills itself with. */
internal val DeferredBlockColor = Color(40, 80, 200)

/** The pixel of the content the tests read, well inside it whatever the placement does. */
internal const val DEFERRED_SAMPLE = 4

/** How large a hand-driven phase paints the content, small enough to clear the pixel a corner is read at. */
internal const val HALF_SCALE = 0.5f

/** How wide the content is, and so what a transform's fraction of the full size is taken over. */
internal const val DEFERRED_BLOCK_WIDTH = 40

/** How tall the content is. */
internal const val DEFERRED_BLOCK_HEIGHT = 20
