package org.jetbrains.compose.swing

import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import java.awt.Container
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities
import kotlin.test.assertEquals

/**
 * What a change asked [host] to repaint, against what it actually altered in [host]'s pixels.
 *
 * Swing serves a change by painting the regions it was asked for and nothing else, so a region the
 * change failed to ask for leaves the pixels it covers as they were. These state whether the regions
 * are enough ([assertShowsTheTruth]) and no more than enough ([assertIsMinimal]) without reading any
 * call the code makes.
 *
 * @property regions the regions the change asked for, in [host]'s coordinates.
 */
public class ServedRepaints internal constructor(
    private val host: Container,
    private val before: BufferedImage,
    private val after: BufferedImage,
    public val regions: List<Rectangle>,
) {
    /**
     * Asserts the regions bring [host] fully up to date: a viewer shown only those regions again sees
     * what a viewer shown the whole container would.
     *
     * @param change what was expected to provoke the repaints, named for the failure message.
     */
    public fun assertShowsTheTruth(change: String) {
        try {
            assertImagesPixelPerfect(after, servedOverBefore())
        } catch (stale: AssertionError) {
            throw AssertionError(
                "after $change the ${host.javaClass.simpleName} shows pixels no repaint corrected, " +
                    "having asked to repaint $regions. ${stale.message}",
                stale,
            )
        }
    }

    /**
     * Asserts the regions cover the pixels the change moved and no others: the union of what was asked
     * for is exactly the area where [host] came to look different.
     *
     * @param change what was expected to provoke the repaints, named for the failure message.
     */
    public fun assertIsMinimal(change: String) {
        val asked = regions.fold(null as Rectangle?) { union, region -> union?.union(region) ?: Rectangle(region) }
        assertEquals(
            differingPixelBounds(before, after),
            asked,
            "after $change the ${host.javaClass.simpleName} must ask to repaint the area it came to " +
                "look different over, and asked to repaint $regions",
        )
    }

    /** [before] with every asked-for region showing what [host] came to look like, as serving them would. */
    private fun servedOverBefore(): BufferedImage {
        val served = BufferedImage(before.width, before.height, before.type)
        served.setRGB(0, 0, before.width, before.height, before.argb(), 0, before.width)
        val canvas = Rectangle(0, 0, served.width, served.height)
        for (region in regions) {
            val visible = region.intersection(canvas)
            if (visible.isEmpty) continue
            val pixels = after.getRGB(visible.x, visible.y, visible.width, visible.height, null, 0, visible.width)
            served.setRGB(visible.x, visible.y, visible.width, visible.height, pixels, 0, visible.width)
        }
        return served
    }

    private fun BufferedImage.argb(): IntArray = getRGB(0, 0, width, height, null, 0, width)
}

/**
 * Renders [host], applies [change], and answers with what the repaints [change] asked for would show a
 * viewer.
 *
 * Only the requests [change] itself makes are recorded, while the state it is measured against is the
 * one [host] settles into - so a change asking for a region it computed before the layout it provoked
 * is caught rather than excused.
 *
 * [host] must carry a size. Must be called on the Event Dispatch Thread.
 */
public fun servedRepaintsOf(
    host: Container,
    change: () -> Unit,
): ServedRepaints {
    val before = host.captureToImage()
    val recorded = repaintsDuring(change = change)
    val regions =
        recorded.requests
            .filter { it.component === host || host.isAncestorOf(it.component) }
            .map { SwingUtilities.convertRectangle(it.component, it.region, host) }
    return ServedRepaints(host, before, host.captureToImage(), regions)
}
