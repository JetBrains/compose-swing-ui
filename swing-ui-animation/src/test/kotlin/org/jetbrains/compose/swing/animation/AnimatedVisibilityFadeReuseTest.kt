package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.RecordedRepaints
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A fade out of an [AnimatedVisibility] shows the current content and paints its container once per frame. */
class AnimatedVisibilityFadeReuseTest {
    enum class FadeSequence(
        val reuses: Boolean = false,
    ) {
        AlphaOnly(reuses = true),
        StockChildRepaint,
        RepaintBeforeTheFrame,
    }

    @ExclusiveWindowSystem
    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("sequences")
    fun aFadeKeepsShowingTheCurrentContent(
        parent: FadeParent,
        sequence: FadeSequence,
    ) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val fixture = FadeFixture()
        mountFade(parent, fixture, inWindow = true)
        fixture.visible = false
        repeat(3) { driveFrame() }
        val leaf = fixture.leaf
        val tap = fixture.tap.node
        val painted = leaf.paints
        val frames = tap.paints

        val driven = signal(sequence, fixture)

        val content = leaf.bounds
        tap.shown.assertShows(versionColor(leaf.version), content.centerX.toInt(), content.centerY.toInt(), "the fade")
        assertTrue(tap.paints >= frames + driven, "the container did not paint the $driven frames it was driven")
        if (sequence.reuses) assertEquals(painted, leaf.paints, "frames that change only the alpha painted the content")
    }

    @ParameterizedTest
    @EnumSource(FadeParent::class)
    fun aFadePaintedOverMoreThanItRecordedShowsTheWholeContent(parent: FadeParent) =
        runComposeSwingTest {
            val fixture = FadeFixture()
            mountFade(parent, fixture)
            fixture.visible = false
            repeat(3) { driveOneFrame() }
            val container = fixture.container
            container.paintUnder(Rectangle(0, 0, 20, 20))

            val content = fixture.leaf.bounds
            val whole = container.paintUnder()

            whole.assertShows(
                versionColor(fixture.leaf.version),
                content.centerX.toInt(),
                content.centerY.toInt(),
                "the fade",
            )
        }

    @ExclusiveWindowSystem
    @ParameterizedTest(name = "{0}")
    @MethodSource("transitionCases")
    fun aTransitionPaintsItsContainerOncePerFrame(
        name: String,
        warmUpFrames: Int,
        content: @Composable (ConstrainedScope, Boolean, IntState, () -> Unit) -> Unit,
    ) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frameNumber = mutableIntStateOf(0)
        var active by mutableStateOf(false)
        var paints = 0
        setContent {
            Window(onCloseRequest = {}, title = "fade-reuse", state = WindowState(size = Dimension(240, 160))) {
                Box(
                    SwingModifier.testTag("frame").preferredSize(100, 80).background(Brush.of(Color.WHITE)),
                ) { content(this, active, frameNumber) { paints++ } }
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false
        active = true
        repeat(warmUpFrames) { driveOneFrame() }

        repeat(14) { index ->
            paints = 0
            frameNumber.intValue = index + 1
            driveOneFrame()
            assertEquals(1, paints, "$name frame $index paints the layers in one pass")
        }
    }

    private companion object {
        @JvmStatic
        fun sequences(): List<Arguments> =
            FadeParent.entries.flatMap { parent ->
                FadeSequence.entries.map { Arguments.of(Named.of(parent.name, parent), Named.of(it.name, it)) }
            }

        @JvmStatic
        fun transitionCases(): List<Arguments> =
            listOf<Triple<String, Int, @Composable (ConstrainedScope, Boolean, IntState, () -> Unit) -> Unit>>(
                Triple("fade over a label that changes every frame", 1) { _, active, frame, painted ->
                    AnimatedVisibility(
                        visible = active,
                        modifier = SwingModifier.drawBehind { painted() },
                        enter = fadeIn(tween(480)),
                    ) { FrameLabel(frame) }
                },
                Triple("fade over a changing label in a Foundation parent", 1) { scope, active, frame, painted ->
                    scope.AnimatedVisibility(
                        visible = active,
                        modifier = SwingModifier.drawBehind { painted() },
                        enter = fadeIn(tween(480)),
                    ) { FrameLabel(frame) }
                },
                Triple("default AnimatedContent", 2) { _, active, _, painted ->
                    AnimatedContent(
                        targetState = active,
                        modifier = SwingModifier.drawBehind { painted() },
                    ) { state ->
                        Canvas(SwingModifier.preferredSize(60, 40)) {
                            graphics.color = if (state) Color.BLUE else Color.RED
                            graphics.fillRect(0, 0, width, height)
                        }
                    }
                },
                Triple("fade with expansion", 5) { _, active, _, painted ->
                    AnimatedVisibility(
                        visible = active,
                        modifier = SwingModifier.drawBehind { painted() },
                        enter = fadeIn(tween(480)) + expandIn(tween(480)),
                    ) { BlueBox() }
                },
                Triple("fade with expansion in a Foundation parent", 5) { scope, active, _, painted ->
                    scope.AnimatedVisibility(
                        visible = active,
                        modifier = SwingModifier.drawBehind { painted() },
                        enter = fadeIn(tween(480)) + expandIn(tween(480)),
                    ) { BlueBox() }
                },
            ).map { (name, warmUpFrames, content) -> Arguments.of(name, warmUpFrames, content) }
    }
}

// Two digits throughout, so no frame changes the label's width and lays it out again.
@Composable
private fun FrameLabel(frame: IntState) = Label(text = "frame ${frame.intValue.toString().padStart(2, '0')}")

@Composable
private fun BlueBox() =
    Canvas(SwingModifier.preferredSize(60, 40)) {
        graphics.color = Color.BLUE
        graphics.fillRect(0, 0, width, height)
    }

private suspend fun ComposeSwingTest.signal(
    sequence: AnimatedVisibilityFadeReuseTest.FadeSequence,
    fixture: FadeFixture,
): Int {
    val leaf = fixture.leaf
    return when (sequence) {
        AnimatedVisibilityFadeReuseTest.FadeSequence.AlphaOnly -> {
            repeat(5) { driveFrame() }
            5
        }

        AnimatedVisibilityFadeReuseTest.FadeSequence.StockChildRepaint -> {
            leaf.version++
            leaf.repaint()
            driveFrame()
            1
        }

        // The frame arrives while the repaint is still queued: the recorder holds every request it gets.
        AnimatedVisibilityFadeReuseTest.FadeSequence.RepaintBeforeTheFrame -> {
            withRecordedRepaints(RecordedRepaints { _, _ -> }) {
                leaf.version++
                fixture.container.repaint()
                driveFrame()
            }
            1
        }
    }
}

private fun JComponent.paintUnder(clip: Rectangle? = null): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        clip?.let { graphics.clip = it }
        paint(graphics)
    } finally {
        graphics.dispose()
    }
    return image
}
