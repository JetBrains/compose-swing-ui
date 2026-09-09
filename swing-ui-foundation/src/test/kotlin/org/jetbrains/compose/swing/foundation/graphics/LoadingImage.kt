package org.jetbrains.compose.swing.foundation.graphics

import java.awt.Color
import java.awt.Image
import java.awt.Toolkit
import java.awt.image.ImageConsumer
import java.awt.image.ImageProducer
import java.awt.image.MemoryImageSource

/**
 * A [width] x [height] [image] filled with [color] that stays loading until [finishLoading].
 *
 * A decoded image can finish loading between `Graphics.drawImage` starting its load and checking whether it is done,
 * and then draws at once without asking its observer to repaint. This one is always still loading when it is drawn.
 * Draw and finish it on the event dispatch thread.
 */
internal class LoadingImage(
    width: Int,
    height: Int,
    color: Color,
) {
    private val pixels = MemoryImageSource(width, height, IntArray(width * height) { color.rgb }, 0, width)
    private val pending = mutableListOf<ImageConsumer>()

    val image: Image =
        Toolkit.getDefaultToolkit().createImage(
            object : ImageProducer by pixels {
                override fun startProduction(ic: ImageConsumer) {
                    pending += ic
                }
            },
        )

    /** Delivers the pixels to every load started so far, as a decoder finishing does. */
    fun finishLoading() {
        pending.forEach(pixels::startProduction)
        pending.clear()
    }
}
