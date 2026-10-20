package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.assertPixelColor
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.BoxScope
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.requiredHeight
import org.jetbrains.compose.swing.foundation.layout.zIndex
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.maximumSize
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Insets
import java.awt.Point
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnimationClippingTest {
    @Test
    fun `unscoped visibility clips deliberately overflowing content at the stock panel bounds`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                StockHost { UnscopedVisibility(visible) }
            }
            mainClock.autoAdvance = false
            visible = true
            repeat(2) { mainClock.advanceTimeByFrame() }

            assertStockPanelPaint(CONTENT)
        }

    @ParameterizedTest(name = "clip = {0}")
    @ValueSource(booleans = [true, false])
    fun `unscoped content paints within the stock panel bounds whether or not it clips`(clip: Boolean) =
        runComposeSwingTest {
            var state by mutableStateOf(false)
            setContent {
                StockHost { UnscopedContent(state, clip = clip) }
            }
            mainClock.autoAdvance = false
            state = true
            repeat(2) { mainClock.advanceTimeByFrame() }

            assertStockPanelPaint(CONTENT_B)
        }

    @ParameterizedTest(name = "clip = {0}")
    @MethodSource("constrainedClipCases")
    fun `unscoped expand under a constrained box paints past the box only when clipping is disabled`(
        clip: Boolean,
        expected: Color,
    ) = runComposeSwingTest {
        var visible by mutableStateOf(false)
        setContent { ConstrainedHost { UnscopedVisibility(visible, clip = clip) } }
        mainClock.autoAdvance = false
        visible = true
        repeat(10) { mainClock.advanceTimeByFrame() }

        assertHostPixel(expected, y = 30)
    }

    @ParameterizedTest(name = "clip = {0}")
    @MethodSource("constrainedClipCases")
    fun `unscoped content size transform under a constrained box paints past the box only when clipping is disabled`(
        clip: Boolean,
        expected: Color,
    ) = runComposeSwingTest {
        var state by mutableStateOf(false)
        setContent { ConstrainedHost { UnscopedContent(state, clip = clip) } }
        mainClock.autoAdvance = false
        state = true
        repeat(10) { mainClock.advanceTimeByFrame() }

        assertHostPixel(expected, y = 30)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expandClipCases")
    fun `an expansion inside a constrained foundation box exposes child pixels only when clipping is disabled`(
        name: String,
        enter: EnterTransition,
        expected: Color,
    ) = runComposeSwingTest {
        var visible by mutableStateOf(false)
        setContent {
            Box(
                modifier =
                    SwingModifier
                        .testTag(HOST)
                        .opaque(true)
                        .styleable {
                            preferredSize(80, 40)
                            maximumSize(80, 40)
                            background(Color.BLUE)
                        },
            ) {
                Column(
                    modifier =
                        SwingModifier.styleable {
                            preferredSize(80, 40)
                            maximumSize(80, 40)
                        },
                ) {
                    this.AnimatedVisibility(
                        visible = visible,
                        enter = enter,
                        exit = ExitTransition.None,
                    ) {
                        RedBlock(80, 80, CONTENT)
                    }
                }
            }
        }

        mainClock.autoAdvance = false
        visible = true
        repeat(10) { mainClock.advanceTimeByFrame() }

        val host = onNodeWithTag(HOST).fetch<JComponent>()
        val point = Point(host.insets.left + 10, host.insets.top + 20)
        host.captureToImage().assertPixelColor(
            expected,
            point.x,
            point.y,
            "$name: host=${host.bounds}, insets=${host.insets}, point=$point",
        )
    }

    @Test
    fun `an expanding child paints past its animated size when clipping is disabled`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Column {
                    this.AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.testTag(ANIMATED).zIndex(1f),
                        enter = expandVertically(tween(DURATION), Alignment.Top, clip = false),
                        exit = ExitTransition.None,
                    ) {
                        RedBlock(80, 80, CONTENT)
                    }
                    BlueBlock()
                }
            }
            mainClock.autoAdvance = false
            visible = true
            repeat(10) { mainClock.advanceTimeByFrame() }

            assertContentOverlapsBelow(CONTENT)
            assertBelowPixel(Color.RED)
        }

    @Test
    fun `an expanding child stays clipped by default`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Column {
                    this.AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.testTag(ANIMATED).zIndex(1f),
                        enter = expandVertically(tween(DURATION), Alignment.Top),
                        exit = ExitTransition.None,
                    ) {
                        RedBlock(80, 80, CONTENT)
                    }
                    BlueBlock()
                }
            }
            mainClock.autoAdvance = false
            visible = true
            repeat(2) { mainClock.advanceTimeByFrame() }

            assertContentOverlapsBelow(CONTENT)
            assertBelowPixel(Color.BLUE)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shrinkClipCases")
    fun `a shrinking child paints past its animated size only when clipping is disabled`(
        name: String,
        exit: ExitTransition,
        expected: Color,
    ) = runComposeSwingTest {
        var visible by mutableStateOf(true)
        setContent {
            Column {
                this.AnimatedVisibility(
                    visible = visible,
                    modifier = SwingModifier.testTag(ANIMATED).zIndex(1f),
                    enter = EnterTransition.None,
                    exit = exit,
                ) {
                    RedBlock(80, 80, CONTENT)
                }
                BlueBlock()
            }
        }

        mainClock.autoAdvance = false
        visible = false
        repeat(10) { mainClock.advanceTimeByFrame() }

        assertContentOverlapsBelow(CONTENT)
        assertBelowPixel(expected, y = 0, message = name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sizeTransformClipCases")
    fun `animated content paints past its size transform only when clipping is disabled`(
        name: String,
        sizeTransform: SizeTransform,
        expected: Color,
    ) = runComposeSwingTest {
        var state by mutableStateOf(false)
        setContent {
            Column {
                this.AnimatedContent(
                    targetState = state,
                    modifier = SwingModifier.testTag(ANIMATED).zIndex(1f),
                    transitionSpec = {
                        EnterTransition.None togetherWith ExitTransition.None using
                            sizeTransform
                    },
                ) { RedBlock(80, if (it) 80 else 20, "content-$it") }
                BlueBlock()
            }
        }
        mainClock.autoAdvance = false
        state = true
        repeat(2) { mainClock.advanceTimeByFrame() }

        assertContentOverlapsBelow(CONTENT_B)
        assertBelowPixel(expected, message = name)
    }

    @Test
    fun `animated content scaled inside its default size clip takes no paint room`() =
        runComposeSwingTest {
            var state by mutableStateOf(false)
            setContent {
                Column {
                    this.AnimatedContent(
                        targetState = state,
                        modifier = SwingModifier.testTag(ANIMATED).zIndex(1f),
                        transitionSpec = {
                            scaleIn(tween(DURATION), initialScale = 1.5f) togetherWith ExitTransition.None using
                                SizeTransform(sizeAnimationSpec = { _, _ -> tween(DURATION) })
                        },
                    ) { RedBlock(80, if (it) 80 else 20, "content-$it") }
                    BlueBlock()
                }
            }
            mainClock.autoAdvance = false
            state = true
            repeat(2) { mainClock.advanceTimeByFrame() }

            assertContentOverlapsBelow(CONTENT_B)
            assertEquals(
                Insets(0, 0, 0, 0),
                (onNodeWithTag(ANIMATED).fetch() as Decoratable).decoration.paintOutsets(),
                "the scale inside the clip took paint room",
            )
            assertBelowPixel(Color.BLUE)
        }

    companion object {
        @JvmStatic
        fun constrainedClipCases() = listOf(Arguments.of(false, Color.RED), Arguments.of(true, Color.BLUE))

        @JvmStatic
        fun expandClipCases() =
            listOf(
                Arguments.of(
                    "clipping disabled",
                    expandVertically(tween(DURATION), Alignment.Top, clip = false),
                    Color.RED,
                ),
                Arguments.of("the default clip", expandVertically(tween(DURATION), Alignment.Top), Color.BLUE),
            )

        @JvmStatic
        fun shrinkClipCases() =
            listOf(
                Arguments.of(
                    "clipping disabled",
                    shrinkVertically(tween(DURATION), Alignment.Top, clip = false),
                    Color.RED,
                ),
                Arguments.of("the default clip", shrinkVertically(tween(DURATION), Alignment.Top), Color.BLUE),
            )

        @JvmStatic
        fun sizeTransformClipCases() =
            listOf(
                Arguments.of(
                    "clipping disabled",
                    SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(DURATION) }),
                    Color.RED,
                ),
                Arguments.of(
                    "the default clip",
                    SizeTransform(sizeAnimationSpec = { _, _ -> tween(DURATION) }),
                    Color.BLUE,
                ),
            )
    }

    private fun ComposeSwingTest.assertStockPanelPaint(contentTag: String) {
        val outer = onNodeWithTag(STOCK).fetch<JComponent>()
        val host = onNodeWithTag(HOST).fetch<JComponent>()
        val animated = onNodeWithTag(ANIMATED).fetch<JComponent>()
        val content = onNodeWithTag(contentTag).fetch<JComponent>()
        assertEquals(host.size, animated.size, "the animated container is not laid out at its host's size")
        val contentBounds = SwingUtilities.convertRectangle(content.parent, content.bounds, host)
        val insideHost = Point(10, host.height / 2)
        val belowHost = Point(10, host.height + (contentBounds.y + contentBounds.height - host.height) / 2)
        assertTrue(contentBounds.contains(insideHost), "content=$contentBounds, host=${host.bounds}")
        assertTrue(
            belowHost.y >= host.height && contentBounds.contains(belowHost),
            "the content does not reach past its host: content=$contentBounds, host=${host.bounds}",
        )
        val image = outer.captureToImage()
        val inside = SwingUtilities.convertPoint(host, insideHost, outer)
        val outside = SwingUtilities.convertPoint(host, belowHost, outer)
        image.assertPixelColor(Color.RED, inside.x, inside.y, "inside=$inside, content=$contentBounds")
        image.assertPixelColor(Color.BLUE, outside.x, outside.y, "outside=$outside, content=$contentBounds")
    }

    private fun ComposeSwingTest.assertBelowPixel(
        expected: Color,
        y: Int = 1,
        message: String = "pixel below the animated content",
    ) {
        val below = onNodeWithTag(BELOW).fetch<JComponent>()
        val point = SwingUtilities.convertPoint(below, Point(below.width / 2, y), root)
        captureToImage().assertPixelColor(
            expected,
            point.x,
            point.y,
            "$message: below=${below.bounds}, point=$point",
        )
    }

    private fun ComposeSwingTest.assertHostPixel(
        expected: Color,
        y: Int = 20,
    ) {
        val host = onNodeWithTag(HOST).fetch<JComponent>()
        host.captureToImage().assertPixelColor(
            expected,
            host.insets.left + 10,
            host.insets.top + y,
            "host=${host.bounds}, insets=${host.insets}",
        )
    }

    private fun ComposeSwingTest.assertContentOverlapsBelow(tag: String) {
        val content = onNodeWithTag(tag).fetch<JComponent>()
        val below = onNodeWithTag(BELOW).fetch<JComponent>()
        val contentBounds = SwingUtilities.convertRectangle(content.parent, content.bounds, root)
        val belowBounds = SwingUtilities.convertRectangle(below.parent, below.bounds, root)
        kotlin.test.assertTrue(
            contentBounds.intersects(belowBounds),
            "content at $contentBounds does not reach sibling at $belowBounds",
        )
    }
}

@Composable
private fun RedBlock(
    width: Int,
    height: Int,
    tag: String,
    modifier: SwingModifier = SwingModifier,
) {
    Label(
        text = "",
        modifier =
            modifier
                .testTag(tag)
                .opaque(true),
        style = {
            preferredSize(width, height)
            background(Color.RED)
        },
    )
}

@Composable
private fun BlueBlock() {
    Label(
        text = "",
        modifier =
            SwingModifier
                .testTag(BELOW)
                .opaque(true),
        style = {
            preferredSize(80, 20)
            background(Color.BLUE)
        },
    )
}

@Composable
private fun StockHost(content: @Composable () -> Unit) {
    Panel(
        PanelLayout.Border(),
        modifier =
            SwingModifier
                .testTag(STOCK)
                .opaque(true),
        style = {
            preferredSize(80, 80)
            background(Color.BLUE)
        },
    ) {
        Panel(
            PanelLayout.Border(),
            modifier =
                SwingModifier
                    .north()
                    .testTag(HOST)
                    .opaque(true),
            style = {
                preferredSize(80, 40)
                background(Color.BLUE)
            },
        ) { content() }
    }
}

@Composable
private fun ConstrainedHost(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier =
            SwingModifier
                .testTag(HOST)
                .opaque(true)
                .styleable {
                    preferredSize(80, 40)
                    maximumSize(80, 40)
                    background(Color.BLUE)
                },
        content = content,
    )
}

/** Called without a layout receiver so this uses the top-level, unscoped overload. */
@Composable
private fun UnscopedVisibility(
    visible: Boolean,
    clip: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = SwingModifier.testTag(ANIMATED),
        enter = expandVertically(tween(DURATION), Alignment.Top, clip = clip),
        exit = ExitTransition.None,
    ) {
        RedBlock(80, 80, CONTENT, SwingModifier.requiredHeight(80))
    }
}

/** Each content requires its height, so content taller than the space a parent grants overflows the container. */
@Composable
private fun UnscopedContent(
    state: Boolean,
    clip: Boolean,
) {
    AnimatedContent(
        targetState = state,
        modifier = SwingModifier.testTag(ANIMATED),
        transitionSpec = {
            EnterTransition.None togetherWith ExitTransition.None using
                SizeTransform(clip = clip, sizeAnimationSpec = { _, _ -> tween(DURATION) })
        },
    ) {
        val height = if (it) 80 else 20
        RedBlock(80, height, "content-$it", SwingModifier.requiredHeight(height))
    }
}

private const val ANIMATED = "animated"
private const val HOST = "host"
private const val STOCK = "stock"
private const val BELOW = "below"
private const val CONTENT = "content"
private const val CONTENT_B = "content-true"
private const val DURATION = 1_600
