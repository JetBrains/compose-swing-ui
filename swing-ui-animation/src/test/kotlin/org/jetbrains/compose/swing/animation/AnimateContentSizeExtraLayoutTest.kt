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
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Dimension
import kotlin.test.assertEquals

/** Layouts in which nothing changed neither move nor finish an animated content size; only frames do. */
class AnimateContentSizeExtraLayoutTest {
    @ParameterizedTest(name = "{0}, {1} unchanged layouts")
    @MethodSource(UNCHANGED_LAYOUT_CASES)
    fun `unchanged layouts cannot change a content size animation or its finished listener`(
        resizes: FrameResizes,
        layouts: Int,
    ) = assertUnchangedLayoutsChangeNothing(resizes, layouts, ::finishedListenerScene)

    @ParameterizedTest(name = "{0} layouts before the first frame")
    @ValueSource(ints = [1, 5, 12])
    fun `layouts before a content size frame preserve the curve deadline and finished listener`(layouts: Int) =
        runComposeSwingTest {
            val finished = mutableListOf<Pair<Dimension, Dimension>>()
            setUpResizableBox(initialWidth = 80, finished) { tween(1000, easing = LinearEasing) }
            mainClock.autoAdvance = false
            assertRetargetedCurve(
                layouts,
                allocatedWidth = { onNodeWithTag("sibling").fetch().x },
                startWidth = 80,
                completion = { ended, target ->
                    val expected = if (ended) listOf(Dimension(80, 40) to target) else emptyList()
                    assertEquals(expected, finished, "the finished listener")
                },
            )
        }
}

private fun finishedListenerScene(): LayoutScene<List<Pair<Dimension, Dimension>>> {
    val finished = mutableListOf<Pair<Dimension, Dimension>>()
    return LayoutScene(
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
                            modifier = SwingModifier.testTag("body").preferredSize(if (changed) 160 else 80, 40),
                        )
                    }
                    Label("", modifier = SwingModifier.testTag("sibling").preferredSize(1, 1))
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
                Label("", modifier = SwingModifier.testTag("body").preferredSize(initialWidth, 40))
            }
            Label("", modifier = SwingModifier.testTag("sibling").preferredSize(1, 1))
        }
    }
}
