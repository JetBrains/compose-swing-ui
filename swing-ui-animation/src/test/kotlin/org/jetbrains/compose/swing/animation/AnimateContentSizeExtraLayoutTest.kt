package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.Layout
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import javax.swing.JLabel
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/** Layouts in which nothing changed neither move nor finish an animated content size; only frames do. */
class AnimateContentSizeExtraLayoutTest {
    @ParameterizedTest(name = "{0}, {1} unchanged layouts")
    @MethodSource(UNCHANGED_LAYOUT_CASES)
    fun `unchanged layouts cannot change a content size animation or its finished listener`(
        resizes: FrameResizes,
        layouts: Int,
    ) = assertUnchangedLayoutsChangeNothing(resizes, layouts, ::finishedListenerFixture)

    @ParameterizedTest(name = "{0} layouts before the first frame")
    @MethodSource("retargetedCurveCases")
    fun `layouts before a content size frame preserve the curve deadline and finished listener`(
        layouts: Int,
        target: Dimension,
        widthAt680: Int,
    ) = runComposeSwingTest {
        val finished = mutableListOf<Pair<Dimension, Dimension>>()
        setUpResizableBox(initialWidth = 80, finished) { tween(1000, easing = LinearEasing) }
        mainClock.autoAdvance = false
        assertRetargetedCurve(
            layouts,
            target = target,
            widthAt680 = widthAt680,
            completion = { ended ->
                val expected = if (ended) listOf(Dimension(80, 40) to target) else emptyList()
                assertEquals(expected, finished, "the finished listener")
            },
        )
    }

    companion object {
        /** The number of layouts, the size the body ends at after them, and the width 680 ms into the tween. */
        @JvmStatic
        fun retargetedCurveCases(): List<Arguments> =
            listOf(
                Arguments.of(1, Dimension(160, 80), 134),
                Arguments.of(5, Dimension(192, 96), 156),
                Arguments.of(12, Dimension(248, 124), 194),
            )
    }
}

private fun finishedListenerFixture(): LayoutFixture<List<Pair<Dimension, Dimension>>> {
    val finished = mutableListOf<Pair<Dimension, Dimension>>()
    return LayoutFixture(
        content = { changed ->
            Layout(
                measurePolicy = { measurables, _ ->
                    val animated = measurables[0].measure(Constraints())
                    val marker = measurables[1].measure(Constraints.fixed(1, 1))
                    layout(animated.width + 1, animated.height + 1) {
                        animated.place(0, 0)
                        marker.place(animated.width, animated.height)
                    }
                },
                content = {
                    Box(
                        SwingModifier
                            .testTag("animation")
                            .animateContentSize(tween(1000, easing = LinearEasing)) { start, end ->
                                finished += start to end
                            },
                    ) {
                        Label(
                            "",
                            modifier = SwingModifier.testTag("body"),
                            style = { preferredSize(if (changed) 160 else 80, 40) },
                        )
                    }
                    Label("", modifier = SwingModifier.testTag("sibling"), style = { preferredSize(1, 1) })
                },
            )
        },
        observe = {
            val marker = onNodeWithTag("sibling").fetch()
            LayoutObservation(Dimension(marker.x, marker.y), finished.toList())
        },
        assertSettled = { assertEquals(1, it.size, "the finished listener ran once") },
    )
}

private fun ComposeSwingTest.setUpResizableBox(
    initialWidth: Int,
    finished: MutableList<Pair<Dimension, Dimension>>,
    spec: () -> FiniteAnimationSpec<Dimension>,
) {
    setContent {
        Row {
            Box(
                modifier =
                    SwingModifier
                        .animateContentSize(spec()) { start, end -> finished += start to end }
                        .testTag("animation"),
            ) {
                Label("", modifier = SwingModifier.testTag("body"), style = { preferredSize(initialWidth, 40) })
            }
            Label("", modifier = SwingModifier.testTag("sibling"), style = { preferredSize(1, 1) })
        }
    }
}

/** [layouts] sizes, each larger than the one before. */
private fun growingSizes(layouts: Int): List<Dimension> = List(layouts) { Dimension(160 + it * 8, 80 + it * 4) }

/** Gives [body] each of [sizes] in turn, waiting for idle after each, and asserts it takes the size given. */
private suspend fun ComposeSwingTest.resizeBeforeFrame(
    body: JLabel,
    sizes: List<Dimension>,
) {
    for (size in sizes) {
        body.preferredSize = size
        body.revalidate()
        awaitIdle()
        assertEquals(size, body.size)
    }
}

/**
 * Asserts the curve of a 1000 ms linear tween on a fixture whose `body` was resized [layouts] times before the frame
 * that starts it, to a last size of [target].
 *
 * 680 ms after that frame the sibling after the animated box stands at x = [widthAt680], and it reaches the width
 * of [target] at the deadline, 1000 ms after the frame, and not 1 ms before. [completion] is told whether the
 * animation has ended.
 */
private suspend fun ComposeSwingTest.assertRetargetedCurve(
    layouts: Int,
    target: Dimension,
    widthAt680: Int,
    completion: (ended: Boolean) -> Unit,
) {
    val body = onNodeWithTag("body").fetch<JLabel>()
    resizeBeforeFrame(body, growingSizes(layouts))
    mainClock.advanceTimeByFrame()
    val firstFrame = mainClock.currentTime
    mainClock.advanceTimeByFrame(300.milliseconds)
    mainClock.advanceTimeByFrame(380.milliseconds)
    onNodeWithTag("sibling").assertProperty(widthAt680, "the width 680 ms into the tween") { x }
    assertEndsAt(
        firstFrame + 1000.milliseconds,
        stillRunning = { completion(false) },
        ended = {
            onNodeWithTag("sibling").assertProperty(target.width, "the width at the deadline") { x }
            completion(true)
        },
    )
}
