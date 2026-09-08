/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from androidx.compose.ui.graphics.RenderEffectTest in AndroidX's
 * ui-graphics; see this module's META-INF/NOTICE for the synced version.
 */

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
    fun testBlurEffectEquality() {
        val blur1 = BlurEffect(5f, 10f, TileMode.Clamp)
        val blur2 = BlurEffect(5f, 10f, TileMode.Clamp)
        assertEquals(blur1, blur2)
    }

    @Test
    fun testBlurEffectHashcode() {
        val blur1 = BlurEffect(5f, 10f, TileMode.Clamp)
        val blur2 = BlurEffect(5f, 10f, TileMode.Clamp)
        assertEquals(blur1.hashCode(), blur2.hashCode())
    }

    @Test
    fun testBlurEffectToString() {
        assertEquals(
            "BlurEffect(radiusX=5.0, radiusY=10.0, edgeTreatment=Clamp)",
            BlurEffect(5f, 10.0f, TileMode.Clamp).toString(),
        )
    }

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
