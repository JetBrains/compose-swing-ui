// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import java.awt.Color
import java.awt.Insets
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class BlurEffectTest {
    @Test
    fun effectsDifferingInOneRadiusAreNotEqual() {
        assertNotEquals(BlurEffect(5f, 10f), BlurEffect(6f, 10f), "a different radiusX")
        assertNotEquals(BlurEffect(5f, 10f), BlurEffect(5f, 11f), "a different radiusY")
    }

    @Test
    fun effectsOfOppositelySignedZeroRadiiAreNotEqual() {
        // Equal effects must hash alike, and the two zeros hash apart.
        assertNotEquals(BlurEffect(0f), BlurEffect(-0f))
    }

    @Test
    fun aLayerHandedAnEffectWithAnotherRadiusBlursAgain() {
        val layer = ImageLayer()
        layer.record(40, 40) { graphics ->
            graphics.color = Color.BLUE
            graphics.fillRect(0, 0, 40, 40)
            graphics.color = Color.RED
            graphics.fillRect(10, 10, 20, 20)
        }
        layer.renderEffect = BlurEffect(4f, 4f)
        val before = renderImage(40, 40) { graphics -> layer.draw(graphics) }

        layer.renderEffect = BlurEffect(4f, 8f)
        val after = renderImage(40, 40) { graphics -> layer.draw(graphics) }

        assertNotNull(differingPixelBounds(before, after), "a longer vertical radius blurs the recording again")
    }

    @Test
    fun aDecalBlurGivesEachScaleAskedInTurnItsOwnOutsets() {
        val blur = BlurEffect(8f, edgeTreatment = TileMode.Decal)

        val atTwo = blur.outsets(2.0)
        blur.outsets(3.0)
        val atTwoAgain = blur.outsets(2.0)

        assertEquals(Insets(40, 40, 40, 40), atTwo, "a decal blur at scale 2.0 reaches 40 pixels past the recording")
        assertEquals(Insets(40, 40, 40, 40), atTwoAgain, "asking for scale 3.0 in between must not keep its outsets")
    }

    @Test
    fun aClampedBlurOfASolidImageOffTheReductionGridStaysSolidToItsEdges() {
        // A radius this large reduces the image before it blurs it, and 37 x 29 is on no reduction's grid.
        val solid = BufferedImage(37, 29, BufferedImage.TYPE_INT_ARGB)
        solid.createGraphics().apply {
            color = Color.RED
            fillRect(0, 0, solid.width, solid.height)
            dispose()
        }

        val blurred = BlurEffect(20f).createOp(1.0).filter(solid, null)

        assertImagesPixelPerfect(solid, blurred)
    }
}
