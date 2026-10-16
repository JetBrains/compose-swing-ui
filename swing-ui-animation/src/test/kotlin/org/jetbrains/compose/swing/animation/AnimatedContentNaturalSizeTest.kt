package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.NaturalSize.CONTAINER
import org.jetbrains.compose.swing.animation.NaturalSize.LONG
import org.jetbrains.compose.swing.animation.NaturalSize.OWN
import org.jetbrains.compose.swing.animation.NaturalSize.PARENT
import org.jetbrains.compose.swing.animation.NaturalSize.ParentRegion
import org.jetbrains.compose.swing.animation.Stretch.CONTENT
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An [AnimatedContent] under Swing keeps its content within the container's assigned bounds during transitions, and
 * its size requests follow the content it holds.
 */
class AnimatedContentNaturalSizeTest {
    @ParameterizedTest(name = "{0}")
    @EnumSource(Replacement::class)
    fun `replacement content follows assigned bounds through size and null-transform transitions`(
        replacement: Replacement,
    ) = runComposeSwingTest {
        var state by mutableStateOf("small")
        var parentWidth by mutableIntStateOf(PARENT.width)
        setContent {
            ParentRegion(Dimension(parentWidth, PARENT.height)) {
                AnimatedContent(state, SwingModifier.center().testTag(CONTAINER), transitionSpec = {
                    if (targetState == "other") {
                        fadeIn(tween(LONG, LONG)) togetherWith fadeOut(tween(LONG, LONG)) using null
                    } else {
                        replacement.toBlock
                    }
                }) {
                    Label(
                        "",
                        modifier =
                            SwingModifier
                                .testTag(if (it == "block") CONTENT else "small")
                                .preferredSize(if (it == "block") OWN else Dimension(20, 10)),
                    )
                }
            }
        }
        mainClock.autoAdvance = false
        state = "block"
        repeat(3) { driveOneFrame() }
        if (replacement.interruptedByFade) state = "other"
        if (replacement.widensParent) parentWidth += PARENT.width / 4
        repeat(3) { driveOneFrame() }

        val assigned = Dimension(parentWidth, PARENT.height)
        assertWithinAssignedBounds(assigned, "while the replacement runs")
        assertSettledAt(assigned, contentRests = !replacement.interruptedByFade)
    }

    @Test
    fun `a size animation that is running continues through a transition with a null size transform`() =
        runComposeSwingTest {
            var state by mutableStateOf("small")
            val contentSizes = mapOf("small" to Dimension(20, 10), "block" to OWN, "other" to Dimension(60, 30))
            setContent {
                ParentRegion(PARENT) {
                    AnimatedContent(state, SwingModifier.center().testTag(CONTAINER), transitionSpec = {
                        if (targetState == "other") {
                            fadeIn(tween(LONG)) togetherWith fadeOut(tween(LONG)) using null
                        } else {
                            expandIn(tween(LONG)) togetherWith shrinkOut(tween(LONG))
                        }
                    }) {
                        Label("", modifier = SwingModifier.preferredSize(contentSizes.getValue(it)))
                    }
                }
            }
            mainClock.autoAdvance = false
            val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
            state = "block"
            repeat(6) { driveOneFrame() }
            val interrupted = container.preferredSize
            val other = contentSizes.getValue("other")
            assertTrue(
                interrupted.width > other.width && interrupted.height > other.height,
                "the request is still on its way to the first target: $interrupted",
            )

            state = "other"
            val requests = mutableListOf<Dimension>()
            repeat(4) {
                driveOneFrame()
                requests += container.preferredSize
            }

            assertEquals(
                listOf(Dimension(109, 64), Dimension(120, 71), Dimension(121, 71), Dimension(117, 68)),
                requests,
                "the request moves on from $interrupted instead of jumping to the content's own size",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(other, container.preferredSize, "the container takes the content's own size at rest")
        }

    /**
     * How the content that `AnimatedContent` shows for the `block` target replaces the small content, and what
     * happens to the container while it does.
     *
     * @property toBlock the transform to `block`.
     * @property interruptedByFade whether the target changes to `other` mid-transition, which fades with no size
     *   transform.
     * @property widensParent whether the stock parent gets wider mid-transition.
     */
    enum class Replacement(
        val toBlock: ContentTransform,
        val interruptedByFade: Boolean = false,
        val widensParent: Boolean = false,
    ) {
        SizeChange(expandingReplacement()),
        NullTransform(ContentTransform(fadeIn(tween(LONG, LONG)), fadeOut(), sizeTransform = null)),
        InterruptedNullTransform(expandingReplacement(), interruptedByFade = true),
        ParentResize(expandingReplacement(), widensParent = true),
    }
}

private fun expandingReplacement(): ContentTransform =
    expandIn(tween(LONG, LONG)) togetherWith shrinkOut(tween(LONG, LONG))
