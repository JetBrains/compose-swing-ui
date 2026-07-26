package org.jetbrains.compose.swing.test.screenshot

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteractionCollection
import java.awt.Component
import java.awt.image.BufferedImage

/**
 * Renders the matched component, together with everything drawn inside it, to an off-screen image
 * exactly as it is currently laid out.
 *
 * The component must be displayed (laid out with a non-zero size); this is the same contract as
 * [assertIsDisplayed]. Call after the composition has settled so the captured image reflects the
 * latest state.
 *
 * @return an image whose width and height match the component's laid-out size.
 * @throws AssertionError if the component is not displayed.
 */
public fun SwingNodeInteraction<*>.captureToImage(): BufferedImage {
    assertIsDisplayed()
    return resolve().captureToImage()
}

/**
 * Renders each matched component to its own off-screen image, in depth-first pre-order.
 *
 * Each component must be displayed; see [SwingNodeInteraction.captureToImage].
 *
 * @return one image per matched component, ordered to match the collection's other accessors.
 * @throws AssertionError if any matched component is not displayed.
 */
public fun SwingNodeInteractionCollection<*>.captureToImages(): List<BufferedImage> =
    resolveAll().map { it.captureToImage() }

/**
 * Renders the whole composition root, together with everything drawn inside it, to an off-screen
 * image. Equivalent to capturing the node returned by [ComposeSwingTest.onRoot].
 *
 * Call after the composition has settled so the captured image reflects the latest state.
 *
 * @return an image whose width and height match the root's laid-out size.
 * @throws AssertionError if the root has a zero laid-out size.
 */
public fun ComposeSwingTest.captureToImage(): BufferedImage = onRoot().captureToImage()

/**
 * Renders an arbitrary, hand-built raw AWT/Swing component to an off-screen image at its own laid-out
 * size, independently of any composition.
 *
 * This is the raw-Swing counterpart of [SwingNodeInteraction.captureToImage]: it lets a test render a
 * hand-written reference component (e.g. a plain `JButton`) through the same off-screen pipeline used
 * to capture composed components, so the two images can be compared pixel-for-pixel. Give the
 * component the same bounds as the composed component you are comparing against before capturing it,
 * so both images share identical dimensions.
 *
 * @return an image whose width and height match the component's current size.
 * @throws AssertionError if the component has a zero size.
 */
public fun Component.captureToImage(): BufferedImage {
    if (width <= 0 || height <= 0) {
        throw AssertionError(
            "Cannot capture ${javaClass.simpleName}: it has zero laid-out size (${width}x$height).",
        )
    }
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        // printAll (not paintAll) renders a component and its descendants to an arbitrary Graphics
        // regardless of on-screen showing state; paintAll early-returns for a component with no
        // realized peer, leaving the image blank. The harness never realizes a window, so the print
        // path is what actually rasterizes the component's pixels off-screen.
        printAll(graphics)
    } finally {
        graphics.dispose()
    }
    return image
}
