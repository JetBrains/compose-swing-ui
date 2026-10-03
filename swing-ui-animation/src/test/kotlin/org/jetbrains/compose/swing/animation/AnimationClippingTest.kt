package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.tween
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
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.params.ParameterizedTest
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
            repeat(2) { driveOneFrame() }

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
            repeat(2) { driveOneFrame() }

            assertStockPanelPaint(CONTENT_B)
        }

    @Test
    fun `unscoped expand clip false is honored under a constrained box`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent { ConstrainedHost { UnscopedVisibility(visible, clip = false) } }
            mainClock.autoAdvance = false
            visible = true
            repeat(10) { driveOneFrame() }

            assertHostPixel(Color.RED, y = 30)
        }

    @Test
    fun `unscoped expand clips by default under a constrained box`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent { ConstrainedHost { UnscopedVisibility(visible) } }
            mainClock.autoAdvance = false
            visible = true
            repeat(10) { driveOneFrame() }

            assertHostPixel(Color.BLUE, y = 30)
        }

    @Test
    fun `unscoped content size transform clip false is honored under a constrained box`() =
        runComposeSwingTest {
            var state by mutableStateOf(false)
            setContent { ConstrainedHost { UnscopedContent(state, clip = false) } }
            mainClock.autoAdvance = false
            state = true
            repeat(10) { driveOneFrame() }

            assertHostPixel(Color.RED, y = 30)
        }

    @Test
    fun `unscoped content size transform clips by default under a constrained box`() =
        runComposeSwingTest {
            var state by mutableStateOf(false)
            setContent { ConstrainedHost { UnscopedContent(state, clip = true) } }
            mainClock.autoAdvance = false
            state = true
            repeat(10) { driveOneFrame() }

            assertHostPixel(Color.BLUE, y = 30)
        }

    @Test
    fun `an expansion with clipping disabled exposes child pixels inside a constrained foundation box`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Box(
                    modifier =
                        SwingModifier
                            .testTag(HOST)
                            .preferredSize(80, 40)
                            .maximumSize(80, 40)
                            .opaque(true)
                            .background(Color.BLUE),
                ) {
                    Column(SwingModifier.preferredSize(80, 40).maximumSize(80, 40)) {
                        this.AnimatedVisibility(
                            visible = visible,
                            enter = expandVertically(tween(DURATION), Alignment.Top, clip = false),
                            exit = ExitTransition.None,
                        ) {
                            RedBlock(80, 80, CONTENT)
                        }
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(10) { driveOneFrame() }

            val host = onNodeWithTag(HOST).fetch<JComponent>()
            val point = Point(host.insets.left + 10, host.insets.top + 20)
            assertEquals(
                Color.RED.rgb,
                host.captureToImage().getRGB(point.x, point.y),
                "host=${host.bounds}, insets=${host.insets}, point=$point",
            )
        }

    @Test
    fun `the default expansion clip keeps child pixels inside its constrained animated box`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Box(
                    modifier =
                        SwingModifier
                            .testTag(HOST)
                            .preferredSize(80, 40)
                            .maximumSize(80, 40)
                            .opaque(true)
                            .background(Color.BLUE),
                ) {
                    Column(SwingModifier.preferredSize(80, 40).maximumSize(80, 40)) {
                        this.AnimatedVisibility(
                            visible = visible,
                            enter = expandVertically(tween(DURATION), Alignment.Top),
                            exit = ExitTransition.None,
                        ) {
                            RedBlock(80, 80, CONTENT)
                        }
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(10) { driveOneFrame() }

            val host = onNodeWithTag(HOST).fetch<JComponent>()
            val point = Point(host.insets.left + 10, host.insets.top + 20)
            assertEquals(
                Color.BLUE.rgb,
                host.captureToImage().getRGB(point.x, point.y),
                "host=${host.bounds}, insets=${host.insets}, point=$point",
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
            repeat(10) { driveOneFrame() }

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
            repeat(2) { driveOneFrame() }

            assertContentOverlapsBelow(CONTENT)
            assertBelowPixel(Color.BLUE)
        }

    @Test
    fun `a shrinking child paints past its animated size when clipping is disabled`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                Column {
                    this.AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.testTag(ANIMATED).zIndex(1f),
                        enter = EnterTransition.None,
                        exit = shrinkVertically(tween(DURATION), Alignment.Top, clip = false),
                    ) {
                        RedBlock(80, 80, CONTENT)
                    }
                    BlueBlock()
                }
            }

            mainClock.autoAdvance = false
            visible = false
            repeat(10) { driveOneFrame() }

            assertContentOverlapsBelow(CONTENT)
            assertBelowPixel(Color.RED, y = 0)
        }

    @Test
    fun `a shrinking child stays clipped by default`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                Column {
                    this.AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.testTag(ANIMATED).zIndex(1f),
                        enter = EnterTransition.None,
                        exit = shrinkVertically(tween(DURATION), Alignment.Top),
                    ) {
                        RedBlock(80, 80, CONTENT)
                    }
                    BlueBlock()
                }
            }

            mainClock.autoAdvance = false
            visible = false
            repeat(10) { driveOneFrame() }

            assertContentOverlapsBelow(CONTENT)
            assertBelowPixel(Color.BLUE, y = 0)
        }

    @Test
    fun `animated content reserves overflow when its size transform disables clipping`() =
        runComposeSwingTest {
            var state by mutableStateOf(false)
            setContent {
                Column {
                    this.AnimatedContent(
                        targetState = state,
                        modifier = SwingModifier.testTag(ANIMATED).zIndex(1f),
                        transitionSpec = {
                            EnterTransition.None togetherWith ExitTransition.None using
                                SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(DURATION) })
                        },
                    ) { RedBlock(80, if (it) 80 else 20, "content-$it") }
                    BlueBlock()
                }
            }
            mainClock.autoAdvance = false
            state = true
            repeat(2) { driveOneFrame() }

            assertContentOverlapsBelow(CONTENT_B)
            assertBelowPixel(Color.RED)
        }

    @Test
    fun `animated content size transform clips by default`() =
        runComposeSwingTest {
            var state by mutableStateOf(false)
            setContent {
                Column {
                    this.AnimatedContent(
                        targetState = state,
                        modifier = SwingModifier.testTag(ANIMATED).zIndex(1f),
                        transitionSpec = {
                            EnterTransition.None togetherWith ExitTransition.None using
                                SizeTransform(sizeAnimationSpec = { _, _ -> tween(DURATION) })
                        },
                    ) { RedBlock(80, if (it) 80 else 20, "content-$it") }
                    BlueBlock()
                }
            }
            mainClock.autoAdvance = false
            state = true
            repeat(2) { driveOneFrame() }

            assertContentOverlapsBelow(CONTENT_B)
            assertBelowPixel(Color.BLUE)
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
            repeat(2) { driveOneFrame() }

            assertContentOverlapsBelow(CONTENT_B)
            assertEquals(
                Insets(0, 0, 0, 0),
                (onNodeWithTag(ANIMATED).fetch() as Decoratable).decoration.paintOutsets(),
                "the scale inside the clip took paint room",
            )
            assertBelowPixel(Color.BLUE)
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
        assertEquals(Color.RED.rgb, image.getRGB(inside.x, inside.y), "inside=$inside, content=$contentBounds")
        assertEquals(Color.BLUE.rgb, image.getRGB(outside.x, outside.y), "outside=$outside, content=$contentBounds")
    }

    private fun ComposeSwingTest.assertBelowPixel(
        expected: Color,
        y: Int = 1,
    ) {
        val below = onNodeWithTag(BELOW).fetch<JComponent>()
        val point = SwingUtilities.convertPoint(below, Point(below.width / 2, y), root)
        assertEquals(
            expected.rgb,
            captureToImage().getRGB(point.x, point.y),
            "below=${below.bounds}, point=$point",
        )
    }

    private fun ComposeSwingTest.assertHostPixel(
        expected: Color,
        y: Int = 20,
    ) {
        val host = onNodeWithTag(HOST).fetch<JComponent>()
        assertEquals(
            expected.rgb,
            host.captureToImage().getRGB(host.insets.left + 10, host.insets.top + y),
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
                .preferredSize(width, height)
                .opaque(true)
                .background(Color.RED),
    )
}

@Composable
private fun BlueBlock() {
    Label(
        text = "",
        modifier =
            SwingModifier
                .testTag(BELOW)
                .preferredSize(80, 20)
                .opaque(true)
                .background(Color.BLUE),
    )
}

@Composable
private fun StockHost(content: @Composable () -> Unit) {
    Panel(
        PanelLayout.Border(),
        modifier =
            SwingModifier
                .testTag(STOCK)
                .preferredSize(80, 80)
                .opaque(true)
                .background(Color.BLUE),
    ) {
        Panel(
            PanelLayout.Border(),
            modifier =
                SwingModifier
                    .north()
                    .testTag(HOST)
                    .preferredSize(80, 40)
                    .opaque(true)
                    .background(Color.BLUE),
        ) { content() }
    }
}

@Composable
private fun ConstrainedHost(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier =
            SwingModifier
                .testTag(HOST)
                .preferredSize(80, 40)
                .maximumSize(80, 40)
                .opaque(true)
                .background(Color.BLUE),
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
