package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.AnimationVector2D
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.SpringSpec
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * What an enter or exit transition describes: which slots a factory fills, how two transitions combine,
 * and how the single-axis factories map onto the two-axis ones.
 */
class EnterExitTransitionTest {
    @Test
    fun `combining with None returns the other transition`() {
        val fade = fadeIn()
        assertSame(fade, EnterTransition.None + fade)
        assertSame(fade, fade + EnterTransition.None)
        val out = fadeOut()
        assertSame(out, ExitTransition.None + out)
        assertSame(out, out + ExitTransition.None)
    }

    @Test
    fun `combining fills the slots of both transitions`() {
        val config = (fadeIn() + expandIn() + scaleIn()).config
        assertNotNull(config.fade)
        assertNotNull(config.changeSize)
        assertNotNull(config.scale)
        assertNull(config.slide)
    }

    @Test
    fun `combining the same slot twice keeps the right operand`() {
        val combined = fadeIn(initialAlpha = 0.2f) + fadeIn(initialAlpha = 0.8f)
        assertEquals(0.8f, combined.config.fade?.alpha)
        val exit = fadeOut(targetAlpha = 0.2f) + fadeOut(targetAlpha = 0.8f)
        assertEquals(0.8f, exit.config.fade?.alpha)
        val scaled = scaleIn(initialScale = 0.2f) + scaleIn(initialScale = 0.8f)
        assertEquals(0.8f, scaled.config.scale?.scale)
        val veiled = unveilIn(initialColor = Color.RED) + unveilIn(initialColor = Color.BLUE)
        assertEquals(Color.BLUE, veiled.config.veil?.initialColor)
    }

    @Test
    fun `transitions carrying equal configurations are equal`() {
        assertEquals(fadeIn(), fadeIn())
        assertEquals(fadeOut(), fadeOut())
        assertNotEquals(fadeIn(), fadeIn(initialAlpha = 0.5f))
        assertEquals(scaleIn(), scaleIn())
        assertEquals(scaleOut(), scaleOut())
        assertNotEquals(scaleIn(), scaleIn(initialScale = 0.5f))
        assertNotEquals(scaleIn(), scaleIn(transformOrigin = TransformOrigin(0f, 0f)))
    }

    @Test
    fun `a scale is applied around the middle of the content unless another pivot is named`() {
        assertEquals(TransformOrigin.Center, scaleIn().config.scale?.transformOrigin)
        assertEquals(TransformOrigin(0.5f, 0.5f), TransformOrigin.Center)
        val corner = TransformOrigin(1f, 0f)
        assertEquals(corner, scaleOut(transformOrigin = corner).config.scale?.transformOrigin)
    }

    @Test
    fun `an alpha outside the unit range reaches the transition unchanged`() {
        assertEquals(1.5f, fadeIn(initialAlpha = 1.5f).config.fade?.alpha)
        assertEquals(-0.1f, fadeIn(initialAlpha = -0.1f).config.fade?.alpha)
        assertEquals(1.5f, fadeOut(targetAlpha = 1.5f).config.fade?.alpha)
        assertEquals(-0.1f, fadeOut(targetAlpha = -0.1f).config.fade?.alpha)
    }

    @Test
    fun `an alpha above full opacity paints the content straight through`() =
        runComposeSwingTest {
            assertEquals(ContentColor.rgb, fadedPixel(initialAlpha = 1.5f))
        }

    @Test
    fun `an alpha below fully transparent paints none of the content`() =
        runComposeSwingTest {
            assertNotEquals(ContentColor.rgb, fadedPixel(initialAlpha = -0.5f))
        }

    @Test
    fun `slide factories offset by half the content by default`() {
        val fullSize = Dimension(100, 50)
        assertEquals(Point(-50, 0), slideInHorizontally().config.slide?.offsetFor(fullSize))
        assertEquals(Point(0, -25), slideInVertically().config.slide?.offsetFor(fullSize))
        assertEquals(Point(-50, 0), slideOutHorizontally().config.slide?.offsetFor(fullSize))
        assertEquals(Point(0, -25), slideOutVertically().config.slide?.offsetFor(fullSize))
    }

    @Test
    fun `expanding along one axis leaves the other axis full and centered`() {
        val fullSize = Dimension(100, 50)
        val horizontal = assertNotNull(expandHorizontally(initialWidth = { 7 }).config.changeSize)
        assertEquals(Dimension(7, 50), horizontal.size(fullSize))
        assertEquals(Alignment.CenterEnd, horizontal.alignment)

        val vertical = assertNotNull(expandVertically(initialHeight = { 9 }).config.changeSize)
        assertEquals(Dimension(100, 9), vertical.size(fullSize))
        assertEquals(Alignment.BottomCenter, vertical.alignment)
    }

    @Test
    fun `shrinking along one axis leaves the other axis full and centered`() {
        val fullSize = Dimension(100, 50)
        val horizontal = assertNotNull(shrinkHorizontally(targetWidth = { 7 }).config.changeSize)
        assertEquals(Dimension(7, 50), horizontal.size(fullSize))
        assertEquals(Alignment.CenterEnd, horizontal.alignment)

        val vertical = assertNotNull(shrinkVertically(targetHeight = { 9 }).config.changeSize)
        assertEquals(Dimension(100, 9), vertical.size(fullSize))
        assertEquals(Alignment.BottomCenter, vertical.alignment)
    }

    @Test
    fun `an axis factory anchors an alignment that names no edge in the middle`() {
        val horizontal = Alignment.Horizontal { _, _, _ -> 3 }
        val vertical = Alignment.Vertical { _, _ -> 3 }
        assertEquals(Alignment.Center, expandHorizontally(expandFrom = horizontal).config.changeSize?.alignment)
        assertEquals(Alignment.Center, expandVertically(expandFrom = vertical).config.changeSize?.alignment)
        assertEquals(Alignment.Center, shrinkHorizontally(shrinkTowards = horizontal).config.changeSize?.alignment)
        assertEquals(Alignment.Center, shrinkVertically(shrinkTowards = vertical).config.changeSize?.alignment)
    }

    @Test
    fun `expanding and shrinking start and end empty by default`() {
        val fullSize = Dimension(100, 50)
        assertEquals(Dimension(0, 0), expandIn().config.changeSize?.sizeFor(fullSize))
        assertEquals(Dimension(0, 0), shrinkOut().config.changeSize?.sizeFor(fullSize))
    }

    @Test
    fun `a veil runs between the color it is given and nothing at all`() {
        val cleared = Color(Color.RED.red, Color.RED.green, Color.RED.blue, 0)
        val unveil = assertNotNull(unveilIn(initialColor = Color.RED).config.veil)
        assertEquals(Color.RED, unveil.initialColor)
        assertEquals(cleared, unveil.targetColor, "an unveil left a scrim standing over the content it let in")

        val veil = assertNotNull(veilOut(targetColor = Color.RED).config.veil)
        assertEquals(cleared, veil.initialColor, "a veil started over content that nothing had veiled yet")
        assertEquals(Color.RED, veil.targetColor)
    }

    @Test
    fun `a size is rounded and floored at zero, an offset is only rounded`() {
        assertEquals(Dimension(2, 0), DimensionToVector.convertFromVector(AnimationVector2D(1.6f, -4f)))
        assertEquals(Point(2, -4), PointToVector.convertFromVector(AnimationVector2D(1.6f, -4.4f)))
        assertEquals(AnimationVector2D(100f, 50f), DimensionToVector.convertToVector(Dimension(100, 50)))
        assertEquals(AnimationVector2D(-3f, 4f), PointToVector.convertToVector(Point(-3, 4)))
    }

    @Test
    fun `every animated frame is a fresh instance`() {
        val vector = AnimationVector2D(3f, 4f)
        val first = DimensionToVector.convertFromVector(vector)
        val second = DimensionToVector.convertFromVector(vector)
        assertEquals(first, second)
        assertNotSame(first, second)
    }

    @Test
    fun `mutating a visibility threshold the API hands back leaves a later default untouched`() {
        dimensionVisibilityThreshold().setSize(50, 50)
        pointVisibilityThreshold().setLocation(50, 50)

        val size = expandIn().config.changeSize?.animationSpec as SpringSpec<*>
        assertEquals(Dimension(1, 1), size.visibilityThreshold)
        val offset = slideInHorizontally().config.slide?.animationSpec as SpringSpec<*>
        assertEquals(Point(1, 1), offset.visibilityThreshold)
    }

    /**
     * The pixel the container shows one frame into an enter that fades from [initialAlpha].
     *
     * The fade holds its initial opacity for the whole test, so what is captured is the value the factory
     * was handed rather than a sample of a curve.
     */
    private suspend fun ComposeSwingTest.fadedPixel(initialAlpha: Float): Int {
        var visible by mutableStateOf(false)
        setContent {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(HeldAlpha, initialAlpha = initialAlpha),
                exit = ExitTransition.None,
            ) {
                Label(
                    text = "",
                    modifier =
                        SwingModifier
                            .preferredSize(width = 40, height = 20)
                            .opaque(true)
                            .background(ContentColor),
                )
            }
        }

        mainClock.autoAdvance = false
        visible = true
        repeat(3) { driveOneFrame() }
        return animatedContainer().captureToImage().getRGB(2, 2)
    }
}

/** A fade that holds its initial opacity past every frame these tests send. */
private val HeldAlpha: FiniteAnimationSpec<Float> = tween(durationMillis = 320, delayMillis = 320)

/** A content that fills itself, so what the container painted can be read off one pixel. */
private val ContentColor = Color.BLUE
