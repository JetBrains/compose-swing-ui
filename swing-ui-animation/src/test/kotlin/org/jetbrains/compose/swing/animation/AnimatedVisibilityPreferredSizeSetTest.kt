package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.NaturalSize.CONTAINER
import org.jetbrains.compose.swing.animation.Stretch.TRANSITION_MILLIS
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An [AnimatedVisibility] whose container has a preferred size set is laid out by its parent in that size while the
 * transition still runs, and its fade paints once.
 */
class AnimatedVisibilityPreferredSizeSetTest {
    @Test
    fun `under a Foundation parent a container with a preferred size set expands frame by frame`() =
        runEnteringScene(
            scene = { visible ->
                Column {
                    AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.testTag(CONTAINER).preferredSize(120, 40),
                        enter =
                            fadeIn(tween(TRANSITION_MILLIS)) +
                                expandVertically(tween(TRANSITION_MILLIS, easing = LinearEasing)),
                        exit = ExitTransition.None,
                    ) {
                        Block(width = 120, height = 40)
                    }
                    Block(modifier = SwingModifier.testTag("below"), width = 120, height = 20)
                }
            },
        ) {
            val offsets =
                List(10) {
                    driveOneFrame()
                    onNodeWithTag("below").fetch().y
                }
            val moving = offsets.dropWhile { it == 0 }
            assertTrue(moving.isNotEmpty(), "the sibling never moved down: $offsets")
            assertEquals(moving.distinct().sorted(), moving, "the sibling did not move down steadily: $offsets")
            assertTrue(moving.last() < 40, "the sibling moved to its final offset at once: $offsets")
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(40, onNodeWithTag("below").fetch().y, "the sibling settled below the whole container")
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(HostingParent::class)
    fun `a container with a preferred size set paints one fade`(parent: HostingParent) =
        runEnteringScene(
            scene = { visible ->
                parent.Hosting {
                    FadingBlock(visible, SwingModifier.testTag("plain"))
                    FadingBlock(visible, SwingModifier.testTag("sized").preferredSize(120, 40))
                }
            },
            frames = 2,
        ) {
            val (plain, sized) = listOf("plain", "sized").map { onNodeWithTag(it).paintedAlpha() }
            assertEquals(plain, sized, "a preferred size set changed the fade")
            // An 8-bit alpha channel reads the held 0.5 back as 127 or 128 of 255; a fade painted twice reads 0.25.
            assertEquals(0.5f, sized, 1f / 255, "the fade was not painted once")
        }
}

/** The parents under which a fading container is laid out. */
enum class HostingParent {
    FoundationColumn {
        @Composable
        override fun Hosting(
            modifier: SwingModifier,
            content: @Composable () -> Unit,
        ) {
            Column(modifier = modifier) { content() }
        }
    },
    StockFlow {
        @Composable
        override fun Hosting(
            modifier: SwingModifier,
            content: @Composable () -> Unit,
        ) {
            Panel(PanelLayout.Flow(), modifier = modifier) { content() }
        }
    },
    ;

    @Composable
    abstract fun Hosting(
        modifier: SwingModifier = SwingModifier,
        content: @Composable () -> Unit,
    )
}

/** A fade held at half its opacity through every frame these tests send. */
@Composable
private fun FadingBlock(
    visible: Boolean,
    modifier: SwingModifier,
) = AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter = fadeIn(tween(durationMillis = TRANSITION_MILLIS, delayMillis = 1_000), initialAlpha = 0.5f),
    exit = ExitTransition.None,
) {
    Block(width = 120, height = 40)
}
