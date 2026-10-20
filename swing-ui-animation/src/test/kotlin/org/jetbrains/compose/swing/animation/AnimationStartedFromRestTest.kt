package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A container animation started by a click while nothing else awaits a frame animates from its first
 * frame.
 *
 * The container cases run on a real window, recomposer and frame clock: they take their targets in a
 * measure, which Swing runs in its own validation of a showing window, and what is under test is that
 * validation coming before the first frame of the animation the click started.
 */
class AnimationStartedFromRestTest {
    @TestFactory
    fun visibilityChangesFromRestPassThroughIntermediateWidths(): List<DynamicTest> =
        listOf(true to WIDE, false to 0).map { (from, atRest) ->
            dynamicTest(if (from) "exit from rest" else "enter from rest") {
                widthsAfterAClick(from = from, atRest = atRest) { shown ->
                    AnimatedVisibility(
                        visible = shown,
                        enter = expandHorizontally(tween(ANIMATION.inWholeMilliseconds.toInt())),
                        exit = shrinkHorizontally(tween(ANIMATION.inWholeMilliseconds.toInt())),
                    ) { Box(SwingModifier.preferredSize(WIDE, HEIGHT)) }
                }.assertPassesBetween(0, WIDE)
            }
        }

    @Test
    fun `a fade-only exit started from rest by a click keeps its content for the fade`() =
        widthsAfterAClick(from = true, atRest = WIDE) { shown ->
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(ANIMATION.inWholeMilliseconds.toInt())),
                exit = fadeOut(tween(ANIMATION.inWholeMilliseconds.toInt())),
            ) { Box(SwingModifier.preferredSize(WIDE, HEIGHT)) }
        }.let { widths ->
            val leftAt = widths.firstOrNull { (_, width) -> width != WIDE }?.first
            assertTrue(
                leftAt != null && leftAt >= ANIMATION / 2,
                "a fade-out must keep its content for the fade and then remove it, " +
                    "but it went (elapsed to width) $widths",
            )
        }

    @Test
    fun `an AnimatedContent size change started from rest by a click passes through intermediate widths`() =
        widthsAfterAClick(from = false, atRest = NARROW) { wide ->
            AnimatedContent(
                targetState = wide,
                transitionSpec = {
                    (
                        fadeIn(tween(ANIMATION.inWholeMilliseconds.toInt())) togetherWith
                            fadeOut(tween(ANIMATION.inWholeMilliseconds.toInt()))
                    ).using(SizeTransform { _, _ -> tween(ANIMATION.inWholeMilliseconds.toInt()) })
                },
            ) { isWide -> Box(SwingModifier.preferredSize(if (isWide) WIDE else NARROW, HEIGHT)) }
        }.assertPassesBetween(NARROW, WIDE)

    /**
     * Shows [content] at the start of a [Row] in a real window with the flag at [from], waits for it to be
     * laid out [atRest] wide and for the composition to come to rest, then flips the flag from a click.
     * Answers the width [content] takes in the row, read off the x of a label placed after it. It is
     * sampled on every turn of the event queue for twice the animation's length, and each change is paired
     * with the milliseconds since the click.
     */
    private fun widthsAfterAClick(
        from: Boolean,
        atRest: Int,
        content: @Composable RowScope.(flag: Boolean) -> Unit,
    ): List<Pair<Duration, Int>> {
        val widths = mutableListOf<Pair<Duration, Int>>()
        runSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display")
            var flag by mutableStateOf(from)
            var context: CompositionContext? = null
            val frame =
                JFrame("animation-started-from-rest").apply {
                    defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
                    setSize(600, 120)
                    isVisible = true
                }
            val pane = frame.contentPane as JComponent
            pane.addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        flag = !flag
                    }
                },
            )
            val handle =
                frame.setContent {
                    context = rememberCompositionContext()
                    Row {
                        content(flag)
                        Label("", SwingModifier.preferredSize(10, HEIGHT))
                    }
                }

            fun contentWidth(): Int? =
                pane.descendants().filterIsInstance<JLabel>().singleOrNull()?.let {
                    SwingUtilities.convertPoint(it.parent, it.location, pane).x
                }
            try {
                awaitUntil("the content is laid out") {
                    pane.isValid &&
                        pane.descendants().filterIsInstance<JLabel>().any { it.width > 0 && it.height > 0 } &&
                        contentWidth() == atRest
                }
                awaitUntil("no animation awaits a frame") {
                    val clock = checkNotNull(context).effectCoroutineContext[MonotonicFrameClock]
                    !checkNotNull(clock as? BroadcastFrameClock).hasAwaiters
                }

                val clickedAt = System.nanoTime()
                pane.toolkit.systemEventQueue.postEvent(
                    MouseEvent(pane, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false),
                )
                val until = clickedAt + (ANIMATION * 2).inWholeNanoseconds
                while (System.nanoTime() < until) {
                    val width = checkNotNull(contentWidth()) { "the label after the content left the row" }
                    if (widths.lastOrNull()?.second != width) {
                        widths += (System.nanoTime() - clickedAt).nanoseconds to width
                    }
                    yield()
                }
            } finally {
                handle.dispose()
                frame.dispose()
            }
        }
        return widths
    }

    private fun List<Pair<Duration, Int>>.assertPassesBetween(
        one: Int,
        other: Int,
    ) {
        val range = minOf(one, other) + 1 until maxOf(one, other)
        assertTrue(
            any { (_, width) -> width in range },
            "an animation started from rest must pass through widths between $one and $other, " +
                "but it went (elapsed to width) $this",
        )
    }

    private fun Container.descendants(): Sequence<Component> =
        components.asSequence().flatMap { child ->
            sequenceOf(child) + (child as? Container)?.descendants().orEmpty()
        }

    private suspend fun awaitUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        val timeout = 10.seconds
        try {
            withTimeout(timeout) {
                while (!condition()) yield()
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("Timed out after $timeout waiting until $description", timedOut)
        }
    }

    private companion object {
        val ANIMATION = 300.milliseconds
        const val NARROW: Int = 100
        const val WIDE: Int = 300
        const val HEIGHT: Int = 20
    }
}
