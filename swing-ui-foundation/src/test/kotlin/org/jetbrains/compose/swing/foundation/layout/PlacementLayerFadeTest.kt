package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.blur
import org.jetbrains.compose.swing.foundation.graphics.renderImage
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A fading placement layer fades everything the steps inside it paint, past its content's box too. */
class PlacementLayerFadeTest {
    @Test
    fun aLayoutModifierNodeReleasesTheFadeBufferWhenItsComponentLeaves() =
        runComposeSwingTest {
            val layer = mutableStateOf<(PlacementLayerScope.() -> Unit)?>({ alpha = 0.5f })
            val element = CapturingPlacementReadLayerElement(layer)
            var present by mutableStateOf(true)
            setContent {
                Row {
                    if (present) {
                        Box(
                            modifier =
                                SwingModifier
                                    .testTag("layered")
                                    .preferredSize(40, 40)
                                    .then(element)
                                    .background(Brush.of(Color.RED)),
                        )
                    }
                }
            }
            onNodeWithTag("layered").captureToImage()
            assertTrue(element.node.layerOrNull?.hasFadeContent == true, "the fade recorded a buffer")

            present = false
            awaitIdle()

            assertFalse(element.node.layerOrNull?.hasFadeContent == true, "the released buffer is not held")
        }

    @Test
    fun aFadeReachesAShadowInsideIt() = assertFadeReachesTheEffectInside { SwingModifier.shadow(4, Color.BLACK, 3, 2) }

    @Test
    fun aFadeReachesABlurInsideIt() = assertFadeReachesTheEffectInside { SwingModifier.blur(4) }

    @Test
    fun aFadeReachesAScaleInsideIt() =
        assertFadeReachesTheEffectInside {
            SwingModifier.placementLayer {
                scaleX = 2f
                scaleY = 2f
            }
        }

    /**
     * What [inner] paints under the fade is what it paints without it, drawn at half its alpha, on graphics with no
     * clip and on graphics clipped to the component, as Swing paints it.
     */
    private fun assertFadeReachesTheEffectInside(inner: () -> SwingModifier) =
        runComposeSwingTest {
            setContent {
                Row {
                    val fades =
                        listOf(
                            "plain" to SwingModifier,
                            "faded" to SwingModifier.placementLayer { alpha = 0.5f },
                        )
                    for ((tag, fade) in fades) {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag(tag)
                                    .preferredSize(20, 20)
                                    .then(fade)
                                    .then(inner()),
                        ) {
                            drawRect(Color.RED)
                        }
                    }
                }
            }
            val unfaded = onNodeWithTag("plain").captureToImage()
            assertTrue(unfaded.width > 20, "the effect paints past the content")

            assertImagesPixelPerfect(halved(unfaded), onNodeWithTag("faded").captureToImage())
            assertImagesPixelPerfect(halved(paintClipped("plain")), paintClipped("faded"))
        }

    /** [image] drawn at half its alpha. */
    private fun halved(image: BufferedImage): BufferedImage =
        renderImage(image.width, image.height) {
            it.composite = AlphaComposite.SrcOver.derive(0.5f)
            it.drawImage(image, 0, 0, null)
        }

    /** What the component tagged [tag] paints on graphics clipped to its bounds, as Swing paints it. */
    private fun ComposeSwingTest.paintClipped(tag: String): BufferedImage {
        val component = onNodeWithTag(tag).fetch<JComponent>()
        return renderImage(component.width, component.height) {
            it.clipRect(0, 0, component.width, component.height)
            component.paint(it)
        }
    }
}
